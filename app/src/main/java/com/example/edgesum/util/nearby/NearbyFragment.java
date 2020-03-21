package com.example.edgesum.util.nearby;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveByNameEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.dashcam.DownloadTestVideosTask;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.video.VideoManager;
import com.example.edgesum.util.video.summariser.SummariserIntentService;
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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
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
    private final PayloadCallback payloadCallback = new ReceiveFilePayloadCallback();
    private final Queue<Message> transferQueue = new LinkedList<>();
    // Dashcam isn't able to handle concurrent downloads, leads to a very high rate of download errors.
    // Just use a single thread for downloading
    private final ScheduledExecutorService downloadTaskExecutor = Executors.newSingleThreadScheduledExecutor();

    // https://stackoverflow.com/questions/36351417/how-to-inflate-hashmapstring-listitems-into-the-recyclerview
    // https://stackoverflow.com/questions/50809619/on-the-adapter-class-how-to-get-key-and-value-from-hashmap
    // https://stackoverflow.com/questions/38142819/make-a-list-of-hashmap-type-in-recycler-view-adapter
    private final LinkedHashMap<String, Endpoint> discoveredEndpoints = new LinkedHashMap<>();
    private ConnectionsClient connectionsClient;
    protected RecyclerView.Adapter deviceAdapter;
    protected String localName = null;

    OnFragmentInteractionListener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        discoveredEndpoints.add(new Endpoint("testing1", "testing1", true));
//        discoveredEndpoints.add(new Endpoint("testing2", "testing2", false));
        deviceAdapter = new DeviceListAdapter(getContext(), discoveredEndpoints, this);
        connectionsClient = Nearby.getConnectionsClient(getContext());
        setLocalName(getContext());
    }

    private void setLocalName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @SuppressLint("MissingPermission")
            String sn = Build.getSerial();
            localName = String.format("%s [%s]", DeviceName.getDeviceName(), sn.substring(sn.length() - 4));
        } else if (localName == null) {
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

                    new AlertDialog.Builder(getContext())
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
                .addOnSuccessListener((Void unused) -> {
                    Log.d(TAG, "Started advertising");
                })
                .addOnFailureListener((Exception e) -> {
                    Log.e(TAG, "Advertisement failure");
                    e.printStackTrace();
                });
    }

    protected void startDiscovery() {
        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener((Void unused) -> {
                    Log.d(TAG, "Started discovering");
                })
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
        downloadTaskExecutor.scheduleAtFixedRate(() ->
                new DownloadTestVideosTask(this, getContext()).execute(), 0, 1, TimeUnit.MINUTES);
    }

    protected void stopDashDownload() {
        downloadTaskExecutor.shutdownNow();
    }

    @Override
    public void addToTransferQueue(Video video, Command command) {
        transferQueue.add(new Message(video, command));
    }

    @Override
    public void initialTransfer() {
        List<Endpoint> connectedEndpoints =
                discoveredEndpoints.values().stream().filter(e -> e.connected).collect(Collectors.toList());

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

    private void nextTransfer(String toEndpointId) {
        if (!transferQueue.isEmpty()) {
            Message message = transferQueue.remove();

            if (message != null) {
                Endpoint toEndpoint = discoveredEndpoints.get(toEndpointId);
                sendFile(message, toEndpoint);
            }
        } else {
            Log.i(TAG, "Transfer queue is empty");
        }
    }

    @Override
    public void sendFileToAll(String videoPath, Command command) {
        if (videoPath == null) {
            Log.e(TAG, "No video file selected");
            return;
        }
        List<String> toEndpointIds = discoveredEndpoints.values().stream()
                .filter(e -> e.connected).map(e -> e.id).collect(Collectors.toList());

        if (toEndpointIds.size() <= 0) {
            Log.e(TAG, "No connected endpoints");
            return;
        }
        File fileToSend = new File(videoPath);
        Uri uri = Uri.fromFile(fileToSend);
        Payload filePayload = null;

        try {
            ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                filePayload = Payload.fromFile(pfd);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (filePayload == null) {
            Log.e(TAG, String.format("Could not create file payload for %s", videoPath));
            return;
        }
        // Construct a simple message mapping the ID of the file payload to the desired filename.
        String filenameMessage = String.format("%s:%s:%s", command, filePayload.getId(), uri.getLastPathSegment());

        // Send the filename message as a bytes payload.
        // Master will send to all workers, workers will just send to master
        Payload filenameBytesPayload = Payload.fromBytes(filenameMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpointIds, filenameBytesPayload);

        // Finally, send the file payload.
        connectionsClient.sendPayload(toEndpointIds, filePayload);
    }

    @Override
    public void sendFile(Message message, Endpoint toEndpoint) {
        File fileToSend = new File(message.video.getData());
        Uri uri = Uri.fromFile(fileToSend);
        Payload filePayload = null;

        try {
            ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
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

        // Construct a simple message mapping the ID of the file payload to the desired filename.
        String bytesMessage = String.format("%s:%s:%s", message.command, filePayload.getId(), uri.getLastPathSegment());

        // Send the filename message as a bytes payload.
        // Master will send to all workers, workers will just send to master
        Payload filenameBytesPayload = Payload.fromBytes(bytesMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(toEndpoint.id, filenameBytesPayload);

        // Finally, send the file payload.
        connectionsClient.sendPayload(toEndpoint.id, filePayload);
    }

    @Override
    public void sendCommandMessageToAll(Command command, String filename) {
        String commandMessage = String.format("%s:%s", command, filename);
        Payload filenameBytesPayload = Payload.fromBytes(commandMessage.getBytes(UTF_8));

        // Only sent from worker to master, might be better to make bidirectional
        List<String> connectedEndpoints = discoveredEndpoints.values().stream()
                .filter(e -> e.connected).map(e -> e.id).collect(Collectors.toList());
        connectionsClient.sendPayload(connectedEndpoints, filenameBytesPayload);
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

                switch (Command.valueOf(parts[0])) {
                    case ERROR:
                        //
                        break;
                    case SUMMARISE:
                        Log.v(TAG, String.format("Started downloading %s", message));
                        payloadId = addPayloadFilename(message);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startTimes.put(payloadId, Instant.now());
                        }

                        processFilePayload(payloadId, endpointId);
                        break;
                    case RETURN:
                        Log.v(TAG, String.format("Started downloading %s", message));
                        payloadId = addPayloadFilename(message);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startTimes.put(payloadId, Instant.now());
                        }

                        processFilePayload(payloadId, endpointId);
                        nextTransfer(endpointId);
                        break;
                    case COMPLETE:
                        videoName = parts[1];
                        Log.d(TAG, String.format("Endpoint %s has finished downloading %s", endpointId, videoName));
                        videoPath = String.format("%s/%s", FileManager.rawFootageFolderPath(), videoName);
                        video = VideoManager.getVideoFromFile(getContext(), new File(videoPath));
                        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
                        break;
                    case NO_ACTIVITY:
                        videoName = parts[1];
                        Log.d(TAG, String.format("%s contained no activity", videoName));
                        videoPath = String.format("%s/%s", FileManager.rawFootageFolderPath(), videoName);
                        video = VideoManager.getVideoFromFile(getContext(), new File(videoPath));
                        EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));
                        nextTransfer(endpointId);
                        break;
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.getId(), payload);
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is command:payloadId:filename.
         */
        private long addPayloadFilename(String payloadFilenameMessage) {
            String[] parts = payloadFilenameMessage.split(":");
            Command command = Command.valueOf(parts[0]);
            long payloadId = Long.parseLong(parts[1]);
            String filename = parts[2];
            filePayloadFilenames.put(payloadId, filename);
            filePayloadCommands.put(payloadId, command);
            return payloadId;
        }

        private void processFilePayload(long payloadId, String fromEndpointId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);
            Command command = filePayloadCommands.get(payloadId);

            if (filePayload != null && filename != null && command != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long duration = Duration.between(startTimes.remove(payloadId), Instant.now()).toMillis();
                    String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
                    Log.w(TAG, String.format("Completed downloading %s in %ss", filename, time));
                } else {
                    Log.w(TAG, String.format("Completed downloading %s", filename));
                }
                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);
                filePayloadCommands.remove(payloadId);

                if (command.equals(Command.SUMMARISE)) {
                    sendCommandMessage(Command.COMPLETE, filename, fromEndpointId);
                }
                // Get the received file (which will be in the Downloads folder)
                File payloadFile = Objects.requireNonNull(filePayload.asFile()).asJavaFile();

                if (payloadFile != null) {
                    // Rename the file.
                    File videoFile = new File(payloadFile.getParentFile(), filename);
                    if (!payloadFile.renameTo(videoFile)) {
                        Log.e(TAG, String.format("Could not rename received file as %s", filename));
                    } else {
                        if (command.equals(Command.SUMMARISE)) {
                            Log.d(TAG, String.format("Summarising %s", filename));
                            summarise(getContext(), videoFile);
                        } else if (command.equals(Command.RETURN)) {
                            File videoDest = new File(String.format("%s/%s",
                                    FileManager.summarisedVideosFolderPath(), filename));
                            try {
                                FileManager.copy(videoFile, videoDest);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            MediaScannerConnection.scanFile(getContext(),
                                    new String[]{videoDest.getAbsolutePath()}, null, (path, uri) -> {
                                        Video video = VideoManager.getVideoFromFile(getContext(), videoDest);
                                        EventBus.getDefault().post(new AddEvent(video, Type.SUMMARISED));
                                        EventBus.getDefault().post(new RemoveByNameEvent(filename, Type.PROCESSING));
                                    });
                        }
                    }
                } else {
                    Log.e(TAG, String.format("Could not create file payload for %s", filename));
                }
            }
        }

        private void summarise(Context context, File videoFile) {
            MediaScannerConnection.scanFile(context, new String[]{videoFile.getAbsolutePath()}, null,
                    (path, uri) -> {
                        Video video = VideoManager.getVideoFromFile(context, videoFile);
                        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

                        final String output = String.format("%s/%s",
                                FileManager.summarisedVideosFolderPath(), videoFile.getName());
                        Intent summariseIntent = new Intent(context, SummariserIntentService.class);
                        summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, video);
                        summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, output);
                        summariseIntent.putExtra(SummariserIntentService.TYPE_KEY,
                                SummariserIntentService.NETWORK_TYPE);
                        context.startService(summariseIntent);
                    });
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            int progress = (int) (100.0 * (update.getBytesTransferred() / (double) update.getTotalBytes()));
            Log.d(TAG, String.format("Transfer to endpoint %s: %d%%", endpointId, progress));

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
