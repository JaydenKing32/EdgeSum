package com.example.edgesum.util.video.summariser;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class SummariserIntentService extends IntentService {
    private String TAG = SummariserIntentService.class.getSimpleName();

    public static final String VIDEO_KEY = "video";
    public static final String OUTPUT_KEY = "outputPath";
    public static final String TYPE_KEY = "type";
    public static final String LOCAL_TYPE = "local";
    public static final String NETWORK_TYPE = "network";

    public static TransferCallback transferCallback;

    public SummariserIntentService() {
        super("SummariserIntentService");
    }

    public SummariserIntentService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.v(TAG, "onHandleIntent");

        if (intent == null) {
            Log.e(TAG, "Null intent");
            return;
        }
        Video video = intent.getParcelableExtra(VIDEO_KEY);
        String output = intent.getStringExtra(OUTPUT_KEY);
        String type = intent.getStringExtra(TYPE_KEY);

        if (video == null) {
            Log.e(TAG, "Video not specified");
            return;
        }
        if (output == null) {
            Log.e(TAG, "Output not specified");
            return;
        }
        if (type == null) {
            type = LOCAL_TYPE;
        }
        Log.d(TAG, video.toString());
        Log.d(TAG, output);

        SummariserPrefs prefs = SummariserPrefs.extractExtras(this, intent);
        Summariser summariser = Summariser.createSummariser(video.getData(),
                prefs.noise, prefs.duration, prefs.quality, prefs.speed, output);
        boolean isVideo = summariser.summarise();

        if (isVideo) {
            String sumOut = new File(output).getAbsolutePath();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, video.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, video.getMimeType());
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "player");
            values.put(MediaStore.Images.Media.DESCRIPTION, "");
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            }
            values.put(MediaStore.Video.Media.DATA, sumOut);
            getApplicationContext().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            EventBus.getDefault().post(new AddEvent(video, Type.SUMMARISED));

            if (type.equals(NETWORK_TYPE)) {
                transferCallback.addToTransferQueue(video, Command.RETURN);
                transferCallback.nextTransfer();
            }
        } else if (type.equals(NETWORK_TYPE)) {
            transferCallback.sendCommandMessageToAll(Command.NO_ACTIVITY, video.getName());
        }
        EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));

        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{FileManager.summarisedVideosFolderPath()}, null, (path, uri) -> {
                    Log.d(TAG, String.format("Scanned %s\n  -> uri=%s", path, uri));
                    // Delete raw video
//                    File rawFootageVideoPath = new File(video.getData());
//                    rawFootageVideoPath.delete();
                });
    }
}
