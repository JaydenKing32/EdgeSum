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
import com.example.edgesum.util.nearby.NearbyFragment;
import com.example.edgesum.util.video.VideoManager;
import com.example.edgesum.util.video.summariser.SummariserIntentService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static final Set<String> downloadedVideos = new HashSet<>();
    private final WeakReference<NearbyFragment> nearbyFragment;

    public DownloadTestVideosTask(NearbyFragment nearbyFragment, Context context) {
        super(context);
        this.nearbyFragment = new WeakReference<>(nearbyFragment);

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

            if (video != null) {
                if (nearbyFragment.isConnected()) {
                    EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                    nearbyFragment.queueVideo(video, Command.SUMMARISE);
                    nearbyFragment.sendToEarliestEndpoint();
                } else {
                    EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));

                    final String output = String.format("%s/%s", FileManager.getSummarisedDirPath(), video.getName());
                    Intent summariseIntent = new Intent(context, SummariserIntentService.class);
                    summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, video);
                    summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, output);
                    summariseIntent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.LOCAL_TYPE);
                    context.startService(summariseIntent);
                }
            }
        };
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Log.v(TAG, "Starting DownloadTestVideosTask");
        DashModel dash = DashModel.blackvue();
        //List<String> allVideos = dash.getFilenames();
        List<String> allVideos = Arrays.asList(testVideos);

        if (allVideos == null || allVideos.size() == 0) {
            Log.e(TAG, "Couldn't download videos");
            return null;
        }
        List<String> newVideos = new ArrayList<>(CollectionUtils.disjunction(allVideos, downloadedVideos));
        newVideos.sort(Comparator.comparing(String::toString));

        if (newVideos.size() != 0) {
            // Get oldest new video, allVideos should already be sorted
            String toDownload = newVideos.get(0);
            downloadedVideos.add(toDownload);
            Context context = weakReference.get();
            DashDownloadManager downloadManager = DashDownloadManager.getInstance(context, downloadCallback);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                start = Instant.now();
            }

            dash.downloadVideo(toDownload, downloadManager, context);
        } else {
            Log.d(TAG, "No new videos");
            nearbyFragment.get().stopDashDownload();
        }
        return null;
    }
}
