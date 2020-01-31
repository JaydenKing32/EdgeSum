package com.example.edgesum.util.nearby;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.RemoveFirstEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
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

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.example.edgesum.util.nearby.Endpoint.getConnectedEndpointIds;
import static com.example.edgesum.util.nearby.Endpoint.getIndexById;
import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class NearbyFragment extends Fragment implements DeviceCallback, TransferCallback {
    private static final String TAG = NearbyFragment.class.getSimpleName();
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private static final String SERVICE_ID = "com.example.edgesum";
    private static final String LOCAL_NAME = DeviceName.getDeviceName();

    protected RecyclerView.Adapter deviceAdapter;
    private List<Endpoint> discoveredEndpoints = new ArrayList<>();
    private ConnectionsClient connectionsClient;
    private final PayloadCallback payloadCallback = new ReceiveFilePayloadCallback();

    OnFragmentInteractionListener listener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        discoveredEndpoints.add(new Endpoint("testing1", "testing1", true));
//        discoveredEndpoints.add(new Endpoint("testing2", "testing2", false));
        deviceAdapter = new DeviceListAdapter(getContext(), discoveredEndpoints, this);
        connectionsClient = Nearby.getConnectionsClient(getContext());
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
                    Log.i(TAG, String.format("Found endpoint %s: %s", endpointId, info.getEndpointName()));
                    discoveredEndpoints.add(new Endpoint(endpointId, info.getEndpointName()));
                    deviceAdapter.notifyItemInserted(discoveredEndpoints.size() - 1);
                }

                @Override
                public void onEndpointLost(@NonNull String endpointId) {
                    // A previously discovered endpoint has gone away.
                    Log.i(TAG, String.format("Lost endpoint %s", endpointId));
                    int index = getIndexById(discoveredEndpoints, endpointId);

                    if (index >= 0) {
                        discoveredEndpoints.remove(index);
                        deviceAdapter.notifyItemRemoved(index);
                    }
                }
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
                    Log.d(TAG, String.format("Initiated connection with %s: %s",
                            endpointId, connectionInfo.getEndpointName()));

                    if (getIndexById(discoveredEndpoints, endpointId) == -1) {
                        discoveredEndpoints.add(new Endpoint(endpointId, connectionInfo.getEndpointName()));
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
                            int index = getIndexById(discoveredEndpoints, endpointId);

                            if (index >= 0) {
                                discoveredEndpoints.get(index).connected = true;
                                deviceAdapter.notifyItemChanged(index);
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
                    // We've been disconnected from this endpoint. No more data can be
                    // sent or received.
                    Log.i(TAG, String.format("Disconnected from %s", endpointId));
                    int index = getIndexById(discoveredEndpoints, endpointId);

                    if (index >= 0) {
                        discoveredEndpoints.get(index).connected = false;
                        deviceAdapter.notifyItemChanged(index);
                    }
                }
            };

    protected void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(LOCAL_NAME, SERVICE_ID, connectionLifecycleCallback, advertisingOptions)
                .addOnSuccessListener((Void unused) -> {
                    Log.i(TAG, "Started advertising");
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
                    Log.i(TAG, "Started discovering");
                })
                .addOnFailureListener((Exception e) -> {
                    Log.e(TAG, "Discovery failure");
                    e.printStackTrace();
                });
    }

    protected void stopAdvertising() {
        Log.i(TAG, "Stopped advertising");
        connectionsClient.stopAdvertising();
    }

    protected void stopDiscovery() {
        Log.i(TAG, "Stopped discovering");
        connectionsClient.stopDiscovery();
    }

    @Override
    public void sendFile(String videoPath, Command command) {
        // Could return boolean based on transfer success
        if (videoPath == null) {
            Log.e(TAG, "No video file selected");
            return;
        }
        ArrayList<String> toEndpointIds = getConnectedEndpointIds(discoveredEndpoints);

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

    private void sendCompleteMessage(String filename) {
        // Currently just works for worker responding to master, could make it work as a master response
        String filenameMessage = String.format("%s:%s", Command.COM, filename);
        Payload filenameBytesPayload = Payload.fromBytes(filenameMessage.getBytes(UTF_8));
        connectionsClient.sendPayload(getConnectedEndpointIds(discoveredEndpoints), filenameBytesPayload);
    }

    @Override
    public boolean isConnected() {
        for (Endpoint endpoint : discoveredEndpoints) {
            if (endpoint.connected) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void connectEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("Selected '%s'", endpoint));
        if (!endpoint.connected) {
            connectionsClient.requestConnection(LOCAL_NAME, endpoint.id, connectionLifecycleCallback)
                    .addOnSuccessListener(
                            (Void unused) -> {
                                // We successfully requested a connection. Now both sides
                                // must accept before the connection is established.
                                Log.i(TAG, String.format("Requested connection with %s", endpoint));
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
        Log.i(TAG, String.format("Disconnected from '%s'", endpoint));

        connectionsClient.disconnectFromEndpoint(endpoint.id);
        endpoint.connected = false;
        int index = getIndexById(discoveredEndpoints, endpoint.id);
        deviceAdapter.notifyItemChanged(index);
    }

    @Override
    public void removeEndpoint(Endpoint endpoint) {
        Log.i(TAG, String.format("Removed %s", endpoint));
        int index = getIndexById(discoveredEndpoints, endpoint.id);

        if (index >= 0) {
            discoveredEndpoints.remove(index);
            deviceAdapter.notifyItemRemoved(index);
        }
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
        private final SimpleArrayMap<Long, Command> filePayloadTypes = new SimpleArrayMap<>();

        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            Log.d(TAG, String.format("onPayloadReceived(endpointId=%s, payload=%s)", endpointId, payload));

            if (payload.getType() == Payload.Type.BYTES) {
                String message = new String(Objects.requireNonNull(payload.asBytes()), UTF_8);
                String[] parts = message.split(":");
                long payloadId;

                switch (Command.valueOf(parts[0])) {
                    case ERR:
                        //
                        break;
                    case SUM:
                    case RET:
                        Log.i(TAG, String.format("Started downloading %s", message));
                        payloadId = addPayloadFilename(message);
                        processFilePayload(payloadId);
                        break;
                    case COM:
                        String videoName = parts[1];
                        Log.d(TAG, String.format("Endpoint %s has finished downloading %s", endpointId, videoName));
                        String videoPath = String.format("%s/%s", FileManager.rawFootageFolderPath(), videoName);
                        Video video =
                                VideoManager.getVideoFromFile(NearbyFragment.this.getContext(), new File(videoPath));
                        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
                        break;
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                // Add this to our tracking map, so that we can retrieve the payload later.
                incomingFilePayloads.put(payload.getId(), payload);
            }
        }

        /**
         * Extracts the payloadId and filename from the message and stores it in the
         * filePayloadFilenames map. The format is payloadId:filename.
         */
        private long addPayloadFilename(String payloadFilenameMessage) {
            String[] parts = payloadFilenameMessage.split(":");
            Command type = Command.valueOf(parts[0]);
            long payloadId = Long.parseLong(parts[1]);
            String filename = parts[2];
            filePayloadFilenames.put(payloadId, filename);
            filePayloadTypes.put(payloadId, type);
            return payloadId;
        }

        private void processFilePayload(long payloadId) {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            Payload filePayload = completedFilePayloads.get(payloadId);
            String filename = filePayloadFilenames.get(payloadId);
            Command type = filePayloadTypes.get(payloadId);

            if (filePayload != null && filename != null && type != null) {
                Log.i(TAG, String.format("Completed downloading %s", filename));
                completedFilePayloads.remove(payloadId);
                filePayloadFilenames.remove(payloadId);
                filePayloadTypes.remove(payloadId);

                if (type.equals(Command.SUM)) {
                    NearbyFragment.this.sendCompleteMessage(filename);
                }
                // Get the received file (which will be in the Downloads folder)
                File payloadFile = Objects.requireNonNull(filePayload.asFile()).asJavaFile();

                if (payloadFile != null) {
                    // Rename the file.
                    File videoFile = new File(payloadFile.getParentFile(), filename);
                    if (!payloadFile.renameTo(videoFile)) {
                        Log.e(TAG, String.format("Could not rename received file as %s", filename));
                    } else {
                        if (type.equals(Command.SUM)) {
                            Log.d(TAG, String.format("Summarising %s", filename));
                            summarise(getContext(), videoFile);
                        } else if (type.equals(Command.RET)) {
                            File videoDest = new File(String.format("%s/%s",
                                    FileManager.summarisedVideosFolderPath(), videoFile.getName()));
                            try {
                                FileManager.copy(videoFile, videoDest);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            MediaScannerConnection.scanFile(getContext(),
                                    new String[]{videoDest.getAbsolutePath()}, null, (path, uri) -> {
                                        Video video = VideoManager.getVideoFromFile(getContext(), videoDest);
                                        EventBus.getDefault().post(new AddEvent(video, Type.SUMMARISED));
                                        EventBus.getDefault().post(new RemoveFirstEvent(Type.PROCESSING));
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
                    processFilePayload(payloadId);
                }
            }
        }
    }
}
