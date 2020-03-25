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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DownloadLatestTask extends DownloadTask<Void, Void, Void> {
    private static final String TAG = DownloadLatestTask.class.getSimpleName();
    private static final Set<String> downloadedVideos = new HashSet<>();

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

            if (transferCallback.isConnected()) {
                EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                transferCallback.addToTransferQueue(video, Command.SUMMARISE);
                transferCallback.nextTransfer();
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
        List<String> allVideos = dash.getFilenames();

        if (allVideos == null || allVideos.size() == 0) {
            Log.e(TAG, "Couldn't download videos");
            return null;
        }
        List<String> newVideos = new ArrayList<>(CollectionUtils.disjunction(allVideos, downloadedVideos));
        newVideos.sort(Comparator.comparing(String::toString));

        if (newVideos.size() != 0) {
            // Get oldest new video
            String toDownload = newVideos.get(0);
            downloadedVideos.add(toDownload);
            DashDownloadManager downloadManager = new DashDownloadManager(downloadCallback);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                start = Instant.now();
            }

            dash.downloadVideo(toDownload, downloadManager, weakReference.get());
        } else {
            Log.d(TAG, "No new videos");
        }
        return null;
    }
}
