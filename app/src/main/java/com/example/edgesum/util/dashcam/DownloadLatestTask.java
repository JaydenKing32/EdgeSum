package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.VideoManager;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class DownloadLatestTask extends DownloadTask<Void, Void, Boolean> {
    private static final String TAG = DownloadLatestTask.class.getSimpleName();
    private static String lastVideo = "";

    public DownloadLatestTask(TransferCallback transferCallback, Context context) {
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

            Video video = VideoManager.getVideoFromFile(weakReference.get(), new File(path));
            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
            transferCallback.addToTransferQueue(video, Command.SUMMARISE);
            transferCallback.initialTransfer();
        };
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        Log.v(TAG, "Starting DownloadLatestTask");
        DashModel dash = DashModel.blackvue();
        List<String> videos = dash.getFilenames();

        // TODO very basic, improve to account for recording speed being faster than download speed.
        //  Need to keep track of downloaded videos, could just use an ArrayList, but that would cause poor performance
        if (videos != null && videos.size() != 0) {
            String last = videos.get(videos.size() - 1);

            // Don't download the same video twice.
            if (!lastVideo.equals(last)) {
                lastVideo = last;
                DashDownloadManager downloadManager = new DashDownloadManager(downloadCallback);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    start = Instant.now();
                }
                dash.downloadVideo(last, downloadManager, weakReference.get());
                return true;
            } else {
                Log.d(TAG, "No new videos");
                return false;
            }
        } else {
            Log.e(TAG, "Couldn't download videos");
            return false;
        }
    }
}
