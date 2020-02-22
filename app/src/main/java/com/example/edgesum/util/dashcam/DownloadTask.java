package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;
import java.util.List;

public class DownloadTask extends AsyncTask<String, Void, List<String>> {
    private final WeakReference<Context> weakReference;

    public DownloadTask(Context context) {
        weakReference = new WeakReference<>(context);
    }

    @Override
    protected List<String> doInBackground(String... strings) {
        // Dride
        return DashTools.downloadAll("http://192.168.1.254/DCIM/MOVIE/", "", DashTools::getDrideFilenames,
                weakReference.get());

        // BlackVue
//        return downloadAll("http://10.99.77.1/", "Record/", this::getBlackvueFilenames, weakReference.get());
    }
}
