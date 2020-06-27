package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

public class DownloadAllTask extends DownloadTask<DashName, Void, List<String>> {
    private static final String TAG = DownloadAllTask.class.getSimpleName();

    public DownloadAllTask(Context context) {
        super(context);

        this.downloadCallback = (video) -> EventBus.getDefault().post(new AddEvent(video, Type.RAW));
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
        return dash.downloadAll(downloadCallback, weakReference.get());
    }
}
