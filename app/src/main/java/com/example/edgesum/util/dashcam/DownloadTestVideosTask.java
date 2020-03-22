package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.VideoManager;
import com.example.edgesum.util.video.summariser.SummariserIntentService;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

public class DownloadTestVideosTask extends DownloadTask<Void, Void, Void> {
    private static final String TAG = DownloadTestVideosTask.class.getSimpleName();
    private static final String[] testVideos = {
            "20200312_150643.mp4",
            "20200312_150744.mp4",
            "20200312_150844.mp4",
            "20200312_150944.mp4",
            "20200312_151044.mp4",
            "20200312_193528.mp4",
            "20200312_193628.mp4",
            "20200312_193728.mp4",
            "20200312_193828.mp4",
            "20200312_193928.mp4",
            "20200312_194129.mp4",
            "20200312_194229.mp4",
            "20200312_194330.mp4",
            "20200312_194730.mp4",
            "20200312_194830.mp4",
            "20200312_194930.mp4",
            "20200312_195230.mp4",
            "20200312_195330.mp4",
            "20200312_195430.mp4"
    };
    private static int counter = 0;

    public DownloadTestVideosTask(TransferCallback transferCallback, Context context) {
        super(context);

        downloadCallback = (path, uri) -> {
            String videoName = path.substring(path.lastIndexOf('/') + 1);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                long duration = Duration.between(start, Instant.now()).toMillis();
                String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
                Log.w(TAG, String.format("Completed downloading %s in %ss", videoName, time));
            } else {
                Log.d(TAG, String.format("Completed downloading %s", videoName));
            }
            Video video = VideoManager.getVideoFromFile(context, new File(path));

            if (transferCallback.isConnected()) {
                EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                transferCallback.addToTransferQueue(video, Command.SUMMARISE);
                transferCallback.initialTransfer();
            } else {
                EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));

                final String output = String.format("%s/%s", FileManager.summarisedVideosFolderPath(), video.getName());
                Intent summariseIntent = new Intent(context, SummariserIntentService.class);
                summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, video);
                summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, output);
                summariseIntent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.LOCAL_TYPE);
                context.startService(summariseIntent);
            }
        };
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Log.v(TAG, "Starting DownloadLatestTask");
        DashModel dash = DashModel.blackvue();
        String videoName = testVideos[counter++];
        DashDownloadManager downloadManager = new DashDownloadManager(downloadCallback);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            start = Instant.now();
        }
        dash.downloadVideo(videoName, downloadManager, weakReference.get());
        return null;
    }
}
