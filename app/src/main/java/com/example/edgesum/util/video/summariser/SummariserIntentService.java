package com.example.edgesum.util.video.summariser;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.example.edgesum.R;
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
        Log.i(TAG, "onHandleIntent");
        final Video video = intent.getParcelableExtra(VIDEO_KEY);
        final String output = intent.getStringExtra(OUTPUT_KEY);
        final String type = intent.getStringExtra(TYPE_KEY);

        Log.i(TAG, video.toString());
        Log.i(TAG, output);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        double noise = pref.getInt(getString(R.string.noise_key), (int) Summariser.DEFAULT_NOISE);
        double duration = pref.getInt(getString(R.string.duration_key),
                (int) Summariser.DEFAULT_DURATION * 10) / 10.0;
        int quality = pref.getInt(getString(R.string.quality_key), Summariser.DEFAULT_QUALITY);
        Speed speed = Speed.valueOf(pref.getString(getString(R.string.encoding_speed_key),
                Summariser.DEFAULT_SPEED.name()));

        Summariser summariser = Summariser.createSummariser(video.getData(), noise, duration, quality, speed, output);
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
                transferCallback.sendFile(sumOut, Command.RET);
            }
        }
        EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));

        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{FileManager.summarisedVideosFolderPath()}, null,
                (path, uri) -> {
                    Log.i(TAG, "Scanned " + path + ":");
                    Log.i(TAG, "-> uri=" + uri);
                    File rawFootageVideoPath = new File(video.getData());
//                rawFootageVideoPath.delete();
//                MediaScannerConnection.scanFile(getApplicationContext(), new String[]{rawFootageVideoPath
//                .getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
//                    public void onScanCompleted(String path, Uri uri) {
//                        Log.i(TAG, "Scanned " + path + ":");
//                        Log.i(TAG, "-> uri=" + uri);
//                    }
//                });
                });


    }
}
