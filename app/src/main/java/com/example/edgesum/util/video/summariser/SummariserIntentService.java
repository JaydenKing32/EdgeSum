package com.example.edgesum.util.video.summariser;

import android.app.IntentService;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveByNameEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.FfmpegTools;
import com.example.edgesum.util.video.VideoManager;

import org.apache.commons.io.FilenameUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class SummariserIntentService extends IntentService {
    private static final String TAG = SummariserIntentService.class.getSimpleName();

    public static final String VIDEO_KEY = "video";
    public static final String OUTPUT_KEY = "outputPath";
    public static final String TYPE_KEY = "type";
    public static final String LOCAL_TYPE = "local";
    public static final String NETWORK_TYPE = "network";
    public static final String SPLIT_PARENT_KEY = "splitParent";
    public static final String SPLIT_NUM_KEY = "splitNum";

    public static TransferCallback transferCallback;
    private static int sumCount = 0;

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
        String splitParent = intent.getStringExtra(SPLIT_PARENT_KEY);

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
            video.insertMediaValues(getApplicationContext(), new File(output).getAbsolutePath());

            if (splitParent == null) {
                EventBus.getDefault().post(new AddEvent(video, Type.SUMMARISED));
            }
            if (type.equals(NETWORK_TYPE)) {
                transferCallback.addToTransferQueue(video, Command.RETURN);
                transferCallback.nextTransfer();
            }
        } else if (type.equals(NETWORK_TYPE)) {
            transferCallback.sendCommandMessageToAll(Command.NO_ACTIVITY, video.getName());
        }
        if (splitParent == null) {
            EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));
        } else {
            // TODO replace sumCount later, probably use a list in NearbyFragment
            sumCount++;
            int splitNum = intent.getIntExtra(SPLIT_NUM_KEY, -1);

            if (splitNum == sumCount) {
                sumCount = 0;
                String baseName = video.getName().substring(0, video.getName().lastIndexOf('_'));
                String parentName = String.format("%s.%s", baseName, FilenameUtils.getExtension(video.getName()));
                String outPath = FfmpegTools.mergeVideos(parentName);

                if (outPath != null) {
                    video.insertMediaValues(getApplicationContext(), outPath);
                    Video mergedVideo = VideoManager.getVideoFromFile(getApplicationContext(), new File(outPath));
                    EventBus.getDefault().post(new AddEvent(mergedVideo, Type.SUMMARISED));
                    EventBus.getDefault().post(new RemoveByNameEvent(parentName, Type.PROCESSING));
                }
            }
        }

        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{FileManager.getSummarisedDirPath()}, null, (path, uri) -> {
                    Log.d(TAG, String.format("Scanned %s\n  -> uri=%s", path, uri));
                    // Delete raw video
//                    File rawFootageVideoPath = new File(video.getData());
//                    rawFootageVideoPath.delete();
                });
    }
}
