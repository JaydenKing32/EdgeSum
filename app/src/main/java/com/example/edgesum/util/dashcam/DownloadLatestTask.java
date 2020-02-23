package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.VideoManager;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;

public class DownloadLatestTask extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = DownloadLatestTask.class.getSimpleName();
    private static String lastVideo = "";
    private final WeakReference<Context> weakReference;
    private final MediaScannerConnection.OnScanCompletedListener downloadCallback;

    public DownloadLatestTask(TransferCallback transferCallback, Context context) {
        weakReference = new WeakReference<>(context);

        downloadCallback = (path, uri) -> {
            Log.d(TAG, String.format("Finished downloading: %s", uri.getLastPathSegment()));
            Video video = VideoManager.getVideoFromFile(context, new File(path));
            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
            transferCallback.addToTransferQueue(video, Command.SUMMARISE);
            transferCallback.nextTransfer();
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
