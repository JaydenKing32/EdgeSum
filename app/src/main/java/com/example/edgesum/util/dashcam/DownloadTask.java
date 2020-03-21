package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;
import java.time.Instant;

abstract class DownloadTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
    final WeakReference<Context> weakReference;
    MediaScannerConnection.OnScanCompletedListener downloadCallback;
    Instant start;

    DownloadTask(Context context) {
        weakReference = new WeakReference<>(context);
    }
}
