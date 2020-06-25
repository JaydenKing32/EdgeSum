package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class DownloadAllTask extends DownloadTask<DashName, Void, List<String>> {
    private static final String TAG = DownloadAllTask.class.getSimpleName();

    public DownloadAllTask(Context context) {
        super(context);

        this.downloadCallback = (video) -> {
            long duration = Duration.between(start, Instant.now()).toMillis();
            String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
            Log.w(TAG, String.format("Completed downloading %s in %ss", video.getName(), time));

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
        start = Instant.now();
        return dash.downloadAll(downloadCallback, weakReference.get());
    }
}
