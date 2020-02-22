package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.TransferCallback;

import java.lang.ref.WeakReference;
import java.util.List;

public class DownloadLatestTask extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = DownloadLatestTask.class.getSimpleName();
    private static String lastVideo = "";
    private final WeakReference<Context> weakReference;
    private final TransferCallback transferCallback;

    public DownloadLatestTask(TransferCallback transferCallback, Context context) {
        this.transferCallback = transferCallback;
        this.weakReference = new WeakReference<>(context);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        Log.v(TAG, "Starting DownloadLatestTask");
        List<String> videos = DashTools.getDrideFilenames();

        // TODO very basic, improve to account for recording speed being faster than download speed.
        //  Need to keep track of downloaded videos, could just use an ArrayList, but that would cause poor performance
        if (videos != null && videos.size() != 0) {
            String last = videos.get(videos.size() - 1);

            // Don't download the same video twice.
            if (!lastVideo.equals(last)) {
                Log.d(TAG, "Started downloading: " + last);
                lastVideo = last;
                DashModel dash = new DashModel(DashName.DRIDE, DashModel.drideBaseUrl, DashModel.drideBaseUrl,
                        DashTools::getDrideFilenames);
                dash.downloadAndSend(FileManager.RAW_FOOTAGE_VIDEOS_PATH.getAbsolutePath(), last, transferCallback,
                        weakReference.get());
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
