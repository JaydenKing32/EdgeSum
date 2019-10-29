package com.example.myfirstapp.util.video.summariser;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.example.myfirstapp.R;
import com.example.myfirstapp.event.AddEvent;
import com.example.myfirstapp.event.RemoveEvent;
import com.example.myfirstapp.event.Type;
import com.example.myfirstapp.model.Video;
import com.example.myfirstapp.util.file.FileManager;
import com.example.myfirstapp.util.video.clouduploader.S3Uploader;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class SummariserIntentService extends IntentService {

    private String TAG = SummariserIntentService.class.getSimpleName();

    public SummariserIntentService() {
        super("SummariserIntentService");
    }

    public SummariserIntentService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.i(TAG, "onHandleIntent");
        final Video video = intent.getParcelableExtra("video");
        final String output = intent.getStringExtra("outputPath");

        Log.i(TAG, video.toString());
        Log.i(TAG, output);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        double noise = pref.getInt(getString(R.string.noise_key), (int) Summariser.DEFAULT_NOISE);
        double duration = pref.getInt(getString(R.string.duration_key), (int) Summariser.DEFAULT_DURATION * 10) / 10.0;
        int quality = pref.getInt(getString(R.string.quality_key), Summariser.DEFAULT_QUALITY);
        Speed speed = Speed.valueOf(pref.getString(getString(R.string.encoding_speed_key), Summariser.DEFAULT_SPEED.name()));

        Summariser summariser = Summariser.createSummariser(video.getData(), noise, duration, quality, speed, output);
        boolean isVideo = summariser.summarise();

        if (isVideo) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, video.getName());
            values.put(MediaStore.Video.Media.MIME_TYPE, video.getMimeType());
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "player");
            values.put(MediaStore.Images.Media.DESCRIPTION, "");
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(MediaStore.Video.Media.DATA, new File(output).getAbsolutePath());
            getApplicationContext().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            EventBus.getDefault().post(new AddEvent(video, Type.SUMMARISED));
        }
        EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));

        MediaScannerConnection.scanFile(getApplicationContext(), new String[]{FileManager.summarisedVideosFolderPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            public void onScanCompleted(String path, Uri uri) {
                Log.i(TAG, "Scanned " + path + ":");
                Log.i(TAG, "-> uri=" + uri);
                File rawFootageVideoPath = new File(video.getData());
//                rawFootageVideoPath.delete();
//                MediaScannerConnection.scanFile(getApplicationContext(), new String[]{rawFootageVideoPath.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
//                    public void onScanCompleted(String path, Uri uri) {
//                        Log.i(TAG, "Scanned " + path + ":");
//                        Log.i(TAG, "-> uri=" + uri);
//                    }
//                });
            }
        });


    }
}
