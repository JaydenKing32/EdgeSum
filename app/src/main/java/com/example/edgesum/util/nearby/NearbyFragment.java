package com.example.edgesum.util.nearby;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.example.edgesum.R;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveByNameEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.dashcam.DashDownloadManager;
import com.example.edgesum.util.dashcam.DownloadTestVideosTask;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.Message.Command;
import com.example.edgesum.util.video.FfmpegTools;
import com.example.edgesum.util.video.VideoManager;
import com.example.edgesum.util.video.summariser.SummariserIntentService;
import com.example.edgesum.util.video.summariser.SummariserPrefs;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.jaredrummler.android.device.DeviceName;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class NearbyFragment extends Fragment implements DeviceCallback, TransferCallback {
    private static final String TAG = NearbyFragment.class.getSimpleName();
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final String SERVICE_ID = "com.example.edgesum";
    private static final String LOCAL_NAME_KEY = "LOCAL_NAME";
    private static final Algorithm DEFAULT_ALGORITHM = Algorithm.best;
    private final PayloadCallback payloadCallback = new ReceiveFilePayloadCallback();
    private final Queue<Message> transferQueue = new LinkedList<>();
    private final Queue<Endpoint> endpointQueue = new LinkedList<>();
    private final LinkedHashMap<String, LinkedHashMap<String, Video>> videoSegments = new LinkedHashMap<>();
    // Dashcam isn't able to handle concurrent downloads, leads to a very high rate of download errors.
    // Just use a single thread for downloading
    private final ScheduledExecutorService downloadTaskExecutor = Executors.newSingleThreadScheduledExecutor();

    // https://stackoverflow.com/questions/36351417/how-to-inflate-hashmapstring-listitems-into-the-recyclerview
    // https://stackoverflow.com/questions/50809619/on-the-adapter-class-how-to-get-key-and-value-from-hashmap
    // https://stackoverflow.com/questions/38142819/make-a-list-of-hashmap-type-in-recycler-view-adapter
    private final LinkedHashMap<String, Endpoint> discoveredEndpoints = new LinkedHashMap<>();
    private ConnectionsClient connectionsClient;
    protected DeviceListAdapter deviceAdapter;
    protected String localName = null;

    private int transferCount = 0;
    private OnFragmentInteractionListener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        discoveredEndpoints.add(new Endpoint("testing1", "testing1", true));
//        discoveredEndpoints.add(new Endpoint("testing2", "testing2", false));
        deviceAdapter = new DeviceListAdapter(getContext(), discoveredEndpoints, this);

        Context context = getContext();
        if (context != null) {
            connectionsClient = Nearby.getConnectionsClient(context);
            setLocalName(context);
        }
    }

    private void setLocalName(Context context) {
        if (localName == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(LOCAL_NAME_KEY, Context.MODE_PRIVATE);
            String uniqueId = sharedPrefs.getString(LOCAL_NAME_KEY, null);
            if (uniqueId == null) {
                uniqueId = RandomStringUtils.randomAlphanumeric(8);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(LOCAL_NAME_KEY, uniqueId);
                editor.apply();
            }
            localName = String.format("%s [%s]", DeviceName.getDeviceName(), uniqueId);
        }
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    Log.d(TAG, String.format("Found endpoint %s: %s", endpointId, info.getEndpointName()));

                    if (!discoveredEndpoints.containsKey(endpointId)) {
                        discoveredEndpoints.put(endpointId, new Endpoint(endpointId, info.getEndpointName()));
                        deviceAdapter.notifyItemInserted(discoveredEndpoints.size() - 1);
                    }
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    // A previously discovered endpoint has gone away.
                    Log.d(TAG, String.format("Lost endpoint %s", endpointId));

                    if (discoveredEndpoints.containsKey(endpointId)) {
                        discoveredEndpoints.remove(endpointId);
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                    Log.d(TAG, String.format("Initiated connection with %s: %s",
                            endpointId, connectionInfo.getEndpointName()));

                    if (!discoveredEndpoints.containsKey(endpointId)) {
                        discoveredEndpoints.put(endpointId, new Endpoint(endpointId, connectionInfo.getEndpointName()));
                        deviceAdapter.notifyItemInserted(discoveredEndpoints.size() - 1);
                    }

                    Context context = getContext();
                    if (context != null) {
                        new AlertDialog.Builder(context)
                                .setTitle("Accept connection to " + connectionInfo.getEndpointName())
                                .setMessage("Confirm the code matches on both devices: " + connectionInfo.getAuthenticationToken())
                                .setPositiveButton(android.R.string.ok,
                                        (DialogInterface dialog, int which) ->
                                                // The user confirmed, so we can accept the connection.
                                                connectionsClient.acceptConnection(endpointId, payloadCallback))
                                .setNegativeButton(android.R.string.cancel,
                                        (DialogInterface dialog, int which) ->
                                                // The user canceled, so we should reject the connection.
                                                connectionsClient.rejectConnection(endpointId))
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                    }
                }

                @Override
                public void onConnectionResult(@NonNull String endpointId, ConnectionResolution result) {
                    switch (result.getStatus().getStatusCode()) {
                        case ConnectionsStatusCodes.STATUS_OK:
                            // We're connected! Can now start sending and receiving data.
                            Log.i(TAG, String.format("Connected to %s", endpointId));
                            Endpoint endpoint = discoveredEndpoints.get(endpointId);

                            if (endpoint != null) {
                                endpoint.connected = true;
                                deviceAdapter.notifyDataSetChanged();
                            }
                            break;
                        case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                            // The connection was rejected by one or both sides.
                            Log.i(TAG, String.format("Connection rejected by %s", endpointId));
                            break;
                        case ConnectionsStatusCodes.STATUS_ERROR:
                            // The connection broke before it was able to be accepted.
                            Log.e(TAG, "Connection error");
                            break;
                        default:
                            // Unknown status code
                    }
                }

                @Override
                public void onDisconnected(@NonNull String endpointId) {
                    // We've been disconnected from this endpoint. No more data can be sent or received.
                    Log.d(TAG, String.format("Disconnected from %s", endpointId));
                    Endpoint endpoint = discoveredEndpoints.get(endpointId);

                    if (endpoint != null) {
                        endpoint.connected = false;
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            };

    protected void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener((Void unused) -> Log.d(TAG, "Started advertising"))
                .addOnFailureListener((Exception e) -> {
                    Log.e(TAG, "Advertisement failure");
                    e.printStackTrace();
                });
    }

    protected void startDiscovery() {
        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener((Void unused) -> Log.d(TAG, "Started discovering"))
                .addOnFailureListener((Exception e) -> {
                    Log.e(TAG, "Discovery failure");
                    e.printStackTrace();
                });
    }

    protected void stopAdvertising() {
        Log.d(TAG, "Stopped advertising");
        connectionsClient.stopAdvertising();
    }

    protected void stopDiscovery() {
        Log.d(TAG, "Stopped discovering");
        connectionsClient.stopDiscovery();
    }

    // https://stackoverflow.com/a/8232889/8031185
    protected void startDashDownload() {
        Log.w(TAG, "Started downloading from dashcam");
        int delay = 20;
        printPreferences(true);
        Log.w(TAG, String.format("Download delay: %ds", delay));
        downloadTaskExecutor.scheduleAtFixedRate(() -> new DownloadTestVideosTask(
                this, getContext()).execute(), 0, delay, TimeUnit.SECONDS);
    }

    public void stopDashDownload() {
        Log.w(TAG, "Stopped downloading from dashcam");
        downloadTaskExecutor.shutdownNow();

        Context context = getContext();
        if (context != null) {
            DashDownloadManager.unregisterReceiver(context);
        }
    }

    private List<Endpoint> getConnectedEndpoints() {
        return discoveredEndpoints.values().stream().filter(e -> e.connected).collect(Collectors.toList());
    }

    private int getConnectedCount() {
        // Should never come close to INT_MAX endpoints, but count() returns a long, should be fine to cast down to int
        return (int) discoveredEndpoints.values().stream().filter(e -> e.connected).count();
    }

    private void queueVideo(Video video, Command command) {
        transferQueue.add(new Message(video, command));
    }

    @Override
    public void addVideo(Video video) {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean segmentationEnabled = pref.getBoolean(getString(R.string.enable_segment_key), false);
        boolean autoSegmentation = pref.getBoolean(getString(R.string.auto_segment_key), false);
        int segNum = autoSegmentation ?
                getConnectedCount() :
                pref.getInt(getString(R.string.manual_segment_key), -1);

        if (segmentationEnabled) {
            if (segNum > 2) {
                int segCount = splitAndQueue(video.getData(), segNum);
                if (segCount != segNum) {
                    Log.w(TAG, String.format("Number of segmented videos (%d) does not match intended value (%d)",
                            segCount, segNum));
                }
                return;
            } else {
                Log.i(TAG, String.format("Segmentation count too low (%d), just summarizing whole video instead",
                        segNum));
            }
        }
        queueVideo(video, Command.SUMMARISE);
    }

    @Override
    public void returnVideo(Video video) {
        List<Endpoint> endpoints = getConnectedEndpoints();
        Message message = new Message(video, Command.RETURN);

        // Workers should only have a single connection to the master endpoint
        if (endpoints.size() == 1) {
            sendFile(message, endpoints.get(0));
        } else {
            Log.e(TAG, "Non-worker attempting to return a video");
        }
    }

    @Override
    public void printPreferences(boolean autoDown) {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }
        StringJoiner prefMessage = new StringJoiner("\n  ");
        prefMessage.add("Preferences:");

        // Add segmentation prefs to SummariserPrefs?
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String algorithmKey = getString(R.string.scheduling_algorithm_key);
        Algorithm selectedAlgorithm = Algorithm.valueOf(pref.getString(algorithmKey, DEFAULT_ALGORITHM.name()));
        boolean segmentationEnabled = pref.getBoolean(getString(R.string.enable_segment_key), false);
        boolean autoSegmentation = pref.getBoolean(getString(R.string.auto_segment_key), false);
        int segNum = autoSegmentation ?
                getConnectedCount() :
                pref.getInt(getString(R.string.manual_segment_key), -1);
        SummariserPrefs sumPref = SummariserPrefs.extractPreferences(context);

        prefMessage.add(String.format("Auto download: %s", autoDown));
        prefMessage.add(String.format("Algorithm: %s", selectedAlgorithm.name()));
        prefMessage.add(String.format("Segmentation: %s", segmentationEnabled));
        prefMessage.add(String.format("Auto segmentation: %s", autoSegmentation));
        prefMessage.add(String.format("Segment number: %s", segNum));
        prefMessage.add(String.format(Locale.ENGLISH, "Noise tolerance: %.2f", sumPref.noise));
        prefMessage.add(String.format("Freeze duration: %s", sumPref.duration));
        prefMessage.add(String.format(Locale.ENGLISH, "Quality: %d", sumPref.quality));
        prefMessage.add(String.format("Speed: %s", sumPref.speed));

        Log.w(TAG, prefMessage.toString());
    }

    private int splitAndQueue(String videoPath, int segNum) {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return -1;
        }

        String baseVideoName = FilenameUtils.getBaseName(videoPath);
        List<Video> videos = FfmpegTools.splitAndReturn(context, videoPath, segNum);

        if (videos == null || videos.size() == 0) {
            Log.e(TAG, String.format("Could not split %s", baseVideoName));
            return -1;
        }
        for (Video segment : videos) {
            queueVideo(segment, Command.SUMMARISE_SEGMENT);
            queueVideoSegment(baseVideoName, segment);
        }
        return videos.size();
    }

    // Add segments when sending, remove segments when receiving. Empty list means all segments received.
    private void queueVideoSegment(String baseName, Video segment) {
        LinkedHashMap<String, Video> segmentMap = videoSegments.get(baseName);

        if (segmentMap == null) {
            segmentMap = new LinkedHashMap<>();
            videoSegments.put(baseName, segmentMap);
        }

        segmentMap.put(segment.getName(), segment);
    }

    private void transferToAllEndpoints() {
        List<Endpoint> connectedEndpoints = getConnectedEndpoints();

        if (connectedEndpoints.size() == 0) {
            Log.e(TAG, "Not connected to any devices");
            return;
        }

        for (Endpoint toEndpoint : connectedEndpoints) {
            if (transferQueue.isEmpty()) {
                Log.i(TAG, "Transfer queue is empty");
                break;
            }
            Message message = transferQueue.remove();

            if (message != null) {
                sendFile(message, toEndpoint);
            }
        }
    }

    /**
     * @return the endpoint which has completed the most summarisations
     */
    private Endpoint getBestEndpoint() {
        List<Endpoint> connectedEndpoints = getConnectedEndpoints();
        int maxComplete = Integer.MIN_VALUE;
        int minJobs = Integer.MAX_VALUE;
        Endpoint best = null;

        for (Endpoint endpoint : connectedEndpoints) {
            if (!endpoint.isActive() && endpoint.completeCount > maxComplete) {
                maxComplete = endpoint.completeCount;
                best = endpoint;
            }
        }

        if (best != null) {
            return best;
        }

        // No free workers, so just choose the one with the most completed jobs, use job queue length as tiebreaker
        for (Endpoint endpoint : connectedEndpoints) {
            if (endpoint.completeCount > maxComplete ||
                    (endpoint.completeCount == maxComplete && endpoint.getJobCount() < minJobs)) {
                maxComplete = endpoint.completeCount;
                minJobs = endpoint.getJobCount();
                best = endpoint;
            }
        }

        return best;
    }

    /**
     * @return an inactive endpoint with the most completed summarisations, or the endpoint with the shortest job queue
     */
    private Endpoint getFastestEndpoint() {
        List<Endpoint> connectedEndpoints = getConnectedEndpoints();
        int maxComplete = Integer.MIN_VALUE;
        int minJobs = Integer.MAX_VALUE;
        Endpoint fastest = null;

        for (Endpoint endpoint : connectedEndpoints) {
            if (!endpoint.isActive() && endpoint.completeCount > maxComplete) {
                maxComplete = endpoint.completeCount;
                fastest = endpoint;
            }
        }

        if (fastest != null) {
            return fastest;
        }

        // No free workers, so just choose the one with the shortest job queue, use completion count as tiebreaker
        for (Endpoint endpoint : connectedEndpoints) {
            if (endpoint.getJobCount() < minJobs ||
                    (endpoint.getJobCount() == minJobs && endpoint.completeCount > maxComplete)) {
                maxComplete = endpoint.completeCount;
                minJobs = endpoint.getJobCount();
                fastest = endpoint;
            }
        }

        return fastest;
    }

    /**
     * @return the endpoint with the smallest job queue
     */
    private Endpoint getLeastBusyEndpoint() {
        return getConnectedEndpoints().stream().min(Comparator.comparing(Endpoint::getJobCount)).orElse(null);
    }

    /**
     * send messages to endpoints in the order that endpoints have completed jobs
     */
    private void transferToEarliestEndpoint() {
        int connectedCount = getConnectedCount();

        if (transferCount < connectedCount && transferQueue.size() >= connectedCount) {
            transferToAllEndpoints();
        } else if (transferCount >= connectedCount) {
            if (transferQueue.isEmpty()) {
                Log.i(TAG, "Transfer queue is empty");
                return;
            }
            Message message = transferQueue.remove();

            if (endpointQueue.isEmpty()) {
                Log.i(TAG, "Endpoint queue is empty");
                sendFile(message, getLeastBusyEndpoint());
            } else {
                Endpoint toEndpoint = endpointQueue.remove();
                sendFile(message, toEndpoint);
            }
        } else {
            Log.d(TAG, "Less queued transfers than connected endpoints");
        }
    }

    @Override
    public void nextTransfer() {
        if (transferQueue.isEmpty()) {
            Log.i(TAG, "Transfer queue is empty");
            return;
        }

        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String algorithmKey = getString(R.string.scheduling_algorithm_key);
        Algorithm selectedAlgorithm = Algorithm.valueOf(pref.getString(algorithmKey, DEFAULT_ALGORITHM.name()));
        Log.v(TAG, String.format("nextTransfer with selected algorithm: %s", selectedAlgorithm.name()));

        switch (selectedAlgorithm) {
            case best:
                sendFile(transferQueue.remove(), getBestEndpoint());
                break;
            case fastest:
                sendFile(transferQueue.remove(), getFastestEndpoint());
                break;
            case least_busy:
                sendFile(transferQueue.remove(), getLeastBusyEndpoint());
                break;
            case ordered:
                transferToEarliestEndpoint();
            case simple_return:
                // nextTransfer should only be called during the initial transfer with simple_return,
                //  all subsequent transfers will be handled by nextTransferOrQuickReturn
                int connectedCount = getConnectedCount();

                if (transferCount < connectedCount && transferQueue.size() >= connectedCount) {
                    transferToAllEndpoints();
                } else {
                    Log.d(TAG, "In nextTransfer with simple_return; transferQueue is too small to start");
                }
                break;
            default:
        }
    }

    private void nextTransferOrQuickReturn(Context context, String toEndpointId) {
        boolean quickReturn = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(getString(R.string.scheduling_algorithm_key), "")
                .equals(getString(R.string.simple_return_algorithm_key));

        if (quickReturn) {
            Log.v(TAG, String.format("Quick return to %s", toEndpointId));
            nextTransferTo(toEndpointId);
        } else {
            nextTransfer();
        }
    }

    private void nextTransferTo(String toEndpointId) {
        if (getConnectedEndpoints().size() == 0) {
            Log.e(TAG, "Not connected to any devices");
            return;
        }

        if (transferQueue.isEmpty()) {
            Log.i(TAG, "Transfer queue is empty");
            return;
        }

        Message message = transferQueue.remove();
        Endpoint toEndpoint = discoveredEndpoints.get(toEndpointId);
        sendFile(message, toEndpoint);
    }

    private void sendFile(Message message, Endpoint toEndpoint) {
        if (message == null || toEndpoint == null) {
            Log.e(TAG, "No message or endpoint selected");
            return;
        }

        transferCount++;
        File fileToSend = new File(message.video.getData());
        Uri uri = Uri.fromFile(fileToSend);
        Payload filePayload = null;
        Context context = getContext();

        if (context == null) {
            Log.e(TAG, "No context");
            return;
        }

        try {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                filePayload = Payload.fromFile(pfd);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (filePayload == null) {
            Log.e(TAG, String.format("Could not create file payload for %s", message.video));
            return;
        }
        Log.w(TAG, String.format("Sending %s to %s", message.video.getName(), toEndpoint.name));

        // Construct a message mapping the ID of the file payload to the desired filename and command.
        // Also include summarisation preferences for summarisation commands
        String bytesMessage;
        if (Message.isSummarise(message.command)) {
            SummariserPrefs prefs = SummariserPrefs.extractPreferences(context);
            bytesMessage = String.format("%s:%s:%s:%s_%s_%s_%s",
                    message.command, filePayload.getId(), uri.getLastPathSegment(),
                    prefs.noise, prefs.duration, prefs.quality, prefs.speed
            );
        } else {
            bytesMessage = String.format("%s:%s:%s", message.command, filePayload.getId(), uri.getLastPathSegment());
        }

        // Send the filename message as a bytes payload.
        // Master will send to all workers, workers will just send to master
        Payload filenameBytesPayload = Payload.fromBytes(bytesMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpoint.id, filenameBytesPayload);

        // Finally, send the file payload.
        connectionsClient.sendPayload(toEndpoint.id, filePayload);
        toEndpoint.addJob(uri.getLastPathSegment());
    }

    @Override
    public void sendCommandMessageToAll(Command command, String filename) {
        String commandMessage = String.format("%s:%s", command, filename);
        Payload filenameBytesPayload = Payload.fromBytes(commandMessage.getBytes(UTF_8));

        // Only sent from worker to master, might be better to make bidirectional
        List<String> connectedEndpointIds = discoveredEndpoints.values().stream()
                .filter(e -> e.connected)
                .map(e -> e.id)
                .collect(Collectors.toList());
        connectionsClient.sendPayload(connectedEndpointIds, filenameBytesPayload);
    }

    @Override
    public void sendCommandMessage(Command command, String filename, String toEndpointId) {
        String commandMessage = String.format("%s:%s", command, filename);
        Payload filenameBytesPayload = Payload.fromBytes(commandMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpointId, filenameBytesPayload);
    }

    @Override
    public boolean isConnected() {
        return discoveredEndpoints.values().stream().anyMatch(e -> e.connected);
    }

    @Override
    public void connectEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Selected '%s'", endpoint));
        if (!endpoint.connected) {
            connectionsClient.requestConnection(localName, endpoint.id, connectionLifecycleCallback)
                    .addOnSuccessListener(
                            (Void unused) -> {
                                // We successfully requested a connection. Now both sides
                                // must accept before the connection is established.
                                Log.d(TAG, String.format("Requested connection with %s", endpoint));
                            })
                    .addOnFailureListener(
                            (Exception e) -> {
                                // Nearby Connections failed to request the connection.
                                Log.e(TAG, "Endpoint failure");
                                e.printStackTrace();
                            });
        } else {
            Log.d(TAG, String.format("'%s' is already connected", endpoint));
        }
    }

    @Override
    public void disconnectEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Disconnected from '%s'", endpoint));

        connectionsClient.disconnectFromEndpoint(endpoint.id);
        endpoint.connected = false;
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void removeEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Removed %s", endpoint));

        discoveredEndpoints.remove(endpoint.id);
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            listener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(String name);
    }

    class ReceiveFilePayloadCallback extends PayloadCallback {
        private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, String> filePayloadFilenames = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Command> filePayloadCommands = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, SummariserPrefs> filePayloadPrefs = new SimpleArrayMap<>();
        private final SimpleArrayMap<Long, Instant> startTimes = new SimpleArrayMap<>();

        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            Log.d(TAG, String.format("onPayloadReceived(endpointId=%s, payload=%s)", endpointId, payload));

            if (payload.getType() == Payload.Type.BYTES) {
                String message = new String(Objects.requireNonNull(payload.asBytes()), UTF_8);
                String[] parts = message.split(":");
                long payloadId;
                String videoName;
                String videoPath;
                Video video;
                Endpoint fromEndpoint;

                Context context = getContext();
                if (context == null) {
                    Log.e(TAG, "No context");
                    return;
                }

                switch (Command.valueOf(parts[0])) {
                    case ERROR:
                        //
                        break;
                    case SUMMARISE:
                    case SUMMARISE_SEGMENT:
                        Log.v(TAG, String.format("Started downloading %s", message));
                        payloadId = addPayloadFilename(parts);
                        startTimes.put(payloadId, Instant.now());

                        processFilePayload(payloadId, endpointId);
                        break;
                    case RETURN:
                        videoName = parts[2];
                        Log.v(TAG, String.format("Started downloading %s", videoName));
                        payloadId = addPayloadFilename(parts);
                        startTimes.put(payloadId, Instant.now());

                        fromEndpoint = discoveredEndpoints.get(endpointId);
                        if (fromEndpoint == null) {
                            Log.e(TAG, String.format("Failed to retrieve endpoint %s", endpointId));
                            return;
                        }

                        fromEndpoint.completeCount++;
                        fromEndpoint.removeJob(videoName);
                        endpointQueue.add(fromEndpoint);

                        processFilePayload(payloadId, endpointId);
                        nextTransferOrQuickReturn(context, endpointId);
                        break;
                    case COMPLETE:
                        videoName = parts[1];
                        Log.d(TAG, String.format("Endpoint %s has finished downloading %s", endpointId, videoName));

                        if (!isSegmentedVideo(videoName)) {
                            videoPath = String.format("%s/%s", FileManager.getRawFootageDirPath(), videoName);
                            video = VideoManager.getVideoFromPath(context, videoPath);

                            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
                        }

                        break;
                    case NO_ACTIVITY:
                        videoName = parts[1];
                        Log.d(TAG, String.format("%s contained no activity", videoName));

                        fromEndpoint = discoveredEndpoints.get(endpointId);
                        if (fromEndpoint == null) {
                            Log.e(TAG, String.format("Failed to retrieve endpoint %s", endpointId));
                            return;
                        }

                        fromEndpoint.completeCount++;
                        fromEndpoint.removeJob(videoName);
                        endpointQueue.add(fromEndpoint);

                        if (!isSegmentedVideo(videoName)) {
                            videoPath = String.format("%s/%s", FileManager.getRawFootageDirPath(), videoName);
                            video = VideoManager.getVideoFromPath(context, videoPath);
                            EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));
                        } else {
                            handleSegment(videoName);
                        }

                        nextTransferOrQuickReturn(context, endpointId);
                        break;
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.getId(), payload);
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is command:payloadId:filename:preferences.
         */
        private long addPayloadFilename(String[] message) {
            Command command = Command.valueOf(message[0]);
            long payloadId = Long.parseLong(message[1]);
            String filename = message[2];
            filePayloadFilenames.put(payloadId, filename);
            filePayloadCommands.put(payloadId, command);

            if (Message.isSummarise(command)) {
                filePayloadPrefs.put(payloadId, new SummariserPrefs(message[3]));
            }

            return payloadId;
        }

        private boolean isSegmentedVideo(String videoName) {
            String baseVideoName = FfmpegTools.getBaseName(videoName);
            return videoSegments.containsKey(baseVideoName);
        }

        private boolean handleSegment(String videoName) {
            if (videoSegments.size() == 0) { // Not master device
                return false;
            }

            String baseVideoName = FfmpegTools.getBaseName(videoName);
            LinkedHashMap<String, Video> vidMap = videoSegments.get(baseVideoName);
            if (vidMap == null) {
                Log.e(TAG, "Couldn't retrieve video map");
                return false;
            }
            if (vidMap.size() == 0) { // Video segment map is already empty,
                return true;
            }

            Video video = vidMap.remove(videoName);
            if (video == null) {
                Log.e(TAG, "Couldn't retrieve video");
                return false;
            }

            if (vidMap.size() == 0) {
                Log.d(TAG, String.format("Received all summarised video segments of %s", baseVideoName));
                String parentName = String.format("%s.%s", baseVideoName, FilenameUtils.getExtension(video.getName()));
                String outPath = FfmpegTools.mergeVideos(parentName);

                if (outPath == null) {
                    Log.e(TAG, "Couldn't merge videos");
                    return false;
                }

                if (!outPath.equals(FfmpegTools.NO_VIDEO)) {
                    Context context = getContext();
                    if (context == null) {
                        Log.e(TAG, "No context");
                        return false;
                    }

                    video.insertMediaValues(context, outPath);
                    Video mergedVideo = VideoManager.getVideoFromPath(context, outPath);
                    EventBus.getDefault().post(new AddEvent(mergedVideo, Type.SUMMARISED));
                }

                EventBus.getDefault().post(new RemoveByNameEvent(parentName, Type.RAW));
                return true;
            } else {
                Log.v(TAG, String.format("Received a segment of %s", baseVideoName));
                return false;
            }
        }

        private void processFilePayload(long payloadId, String fromEndpointId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);
            Command command = filePayloadCommands.get(payloadId);

            if (filePayload != null && filename != null && command != null) {
                long duration = Duration.between(startTimes.remove(payloadId), Instant.now()).toMillis();
                String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
                Log.w(TAG, String.format("Completed downloading %s in %ss", filename, time));

                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);
                filePayloadCommands.remove(payloadId);

                if (Message.isSummarise(command)) {
                    sendCommandMessage(Command.COMPLETE, filename, fromEndpointId);
                }
                // Get the received file (which will be in the Downloads folder)
                Payload.File payload = filePayload.asFile();
                File payloadFile = payload != null ? payload.asJavaFile() : null;

                if (payloadFile == null) {
                    Log.e(TAG, String.format("Could not create file payload for %s", filename));
                    return;
                }
                // Rename the file.
                File videoFile = new File(payloadFile.getParentFile(), filename);
                if (!payloadFile.renameTo(videoFile)) {
                    Log.e(TAG, String.format("Could not rename received file as %s", filename));
                    return;
                }

                if (Message.isSummarise(command)) {
                    SummariserPrefs prefs = filePayloadPrefs.remove(payloadId);
                    if (prefs == null) {
                        Log.e(TAG, "Failed to retrieve summarisation preferences");
                        return;
                    }

                    String outPath = (command.equals(Command.SUMMARISE_SEGMENT)) ?
                            String.format("%s/%s", FileManager.getSegmentSumDirPath(), videoFile.getName()) :
                            String.format("%s/%s", FileManager.getSummarisedDirPath(), videoFile.getName());
                    Log.d(TAG, String.format("Summarising %s", filename));
                    summarise(getContext(), videoFile, prefs, outPath);

                } else if (command.equals(Command.RETURN)) {
                    boolean isSeg = isSegmentedVideo(filename);
                    String videoDestPath = (isSeg) ?
                            String.format("%s/%s", FileManager.getSegmentSumSubDirPath(filename), filename) :
                            String.format("%s/%s", FileManager.getSummarisedDirPath(), filename);

                    File videoDest = new File(videoDestPath);
                    try {
                        FileManager.copy(videoFile, videoDest);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (isSeg) {
                        if (handleSegment(filename)) {
                            String baseName = FfmpegTools.getBaseName(filename);
                            Log.v(TAG, String.format("Removing video segment map for %s", baseName));
                            videoSegments.remove(baseName);
                        }
                        return;
                    }

                    Context context = getContext();
                    if (context == null) {
                        Log.e(TAG, "No context");
                        return;
                    }

                    Video video = VideoManager.getVideoFromPath(context, videoDestPath);
                    EventBus.getDefault().post(new AddEvent(video, Type.SUMMARISED));
                    EventBus.getDefault().post(new RemoveByNameEvent(filename, Type.PROCESSING));
                }
            }
        }

        private void summarise(Context context, File videoFile, SummariserPrefs prefs, String outPath) {
            Video video = VideoManager.getVideoFromPath(context, videoFile.getAbsolutePath());
            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

            Intent intent = new Intent(context, SummariserIntentService.class);
            intent.putExtra(SummariserIntentService.VIDEO_KEY, video);
            intent.putExtra(SummariserIntentService.OUTPUT_KEY, outPath);
            intent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.NETWORK_TYPE);
            intent.putExtra(SummariserPrefs.NOISE_KEY, prefs.noise);
            intent.putExtra(SummariserPrefs.DURATION_KEY, prefs.duration);
            intent.putExtra(SummariserPrefs.QUALITY_KEY, prefs.quality);
            intent.putExtra(SummariserPrefs.ENCODING_SPEED_KEY, prefs.speed.name());
            context.startService(intent);
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            int progress = (int) (100.0 * (update.getBytesTransferred() / (double) update.getTotalBytes()));
            Log.v(TAG, String.format("Transfer to endpoint %s: %d%%", endpointId, progress));

            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                long payloadId = update.getPayloadId();
                Payload payload = incomingFilePayloads.remove(payloadId);
                completedFilePayloads.put(payloadId, payload);

                if (payload != null && payload.getType() == Payload.Type.FILE) {
                    processFilePayload(payloadId, endpointId);
                }
            }
        }
    }
}
