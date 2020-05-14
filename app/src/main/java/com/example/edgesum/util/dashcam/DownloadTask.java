package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.os.AsyncTask;

import com.example.edgesum.model.Video;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.function.Consumer;

abstract class DownloadTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {
    final WeakReference<Context> weakReference;
    Consumer<Video> downloadCallback;
    Instant start;

    DownloadTask(Context context) {
        weakReference = new WeakReference<>(context);
    }
}
