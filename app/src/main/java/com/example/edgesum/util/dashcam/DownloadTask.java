package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;

import static com.example.edgesum.util.dashcam.DashModel.drideBaseUrl;

public class DownloadTask extends AsyncTask<DashName, Void, List<String>> {
    private static final String TAG = DownloadTask.class.getSimpleName();
    private final WeakReference<Context> weakReference;

    public DownloadTask(Context context) {
        weakReference = new WeakReference<>(context);
    }

    @Override
    protected List<String> doInBackground(DashName... dashNames) {
        DashName name = dashNames[0];
        DashModel dash;

        switch (name) {
            case DRIDE:
//                dash = new DashModel(DashName.DRIDE, DashModel.drideBaseUrl, DashModel.drideBaseUrl,
//                        DashTools::getDrideFilenames);
                DashDownloadManager ddm = new DashDownloadManager(weakReference.get());
                List<String> videos = DashTools.getDrideFilenames();

                if (videos != null && videos.size() != 0) {
                    String last = videos.get(videos.size() - 1);
                    ddm.startDownload(drideBaseUrl + last);
                } else {
                    Log.e(TAG, "Connection error");
                }

                break;
            case BLACKVUE:
                dash = new DashModel(DashName.BLACKVUE, DashModel.blackvueBaseUrl, DashModel.blackvueVideoUrl,
                        DashTools::getBlackvueFilenames);
                break;
            default:
                Log.e(TAG, "Dashcam model not specified");
                return null;
        }

//        return dash.downloadAll(weakReference.get());
        return null;
    }
}
