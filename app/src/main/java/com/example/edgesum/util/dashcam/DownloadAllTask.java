package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.video.VideoManager;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class DownloadAllTask extends DownloadTask<DashName, Void, List<String>> {
    private static final String TAG = DownloadAllTask.class.getSimpleName();

    public DownloadAllTask(Context context) {
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
        };
    }

    @Override
    protected List<String> doInBackground(DashName... dashNames) {
        DashName name = dashNames[0];
        DashModel dash;

        switch (name) {
            case DRIDE:
                dash = DashModel.dride();
                break;
            case BLACKVUE:
                dash = DashModel.blackvue();
                break;
            default:
                Log.e(TAG, "Dashcam model not specified");
                return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            start = Instant.now();
        }
        return dash.downloadAll(downloadCallback, weakReference.get());
    }
}
