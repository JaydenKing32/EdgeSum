package com.example.edgesum.util.dashcam;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.video.VideoManager;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

class DashDownloadManager {
    private static final String TAG = DashDownloadManager.class.getSimpleName();
    private final Context context;
    private long downloadId;

    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            if (downloadManager != null) {
                // https://stackoverflow.com/a/46328681/8031185
                Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadId));

                if (cursor != null) {
                    try {
                        cursor.moveToFirst();
                        String fileUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                        File videoFile = new File(Uri.parse(fileUri).getPath());

                        MediaScannerConnection.scanFile(context,
                                new String[]{videoFile.getAbsolutePath()}, null, (path, uri) -> {
                                    Video video = VideoManager.getVideoFromFile(context, new File(path));
                                    Log.d(TAG, "Finished downloading: " + uri.getLastPathSegment());
                                    EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                                });
                    } catch (Exception e) {
                        Log.e(TAG, String.format("Download error: \n%s", e.getMessage()));
                    }
                }
            }
        }
    };

    DashDownloadManager(Context context) {
        this.context = context;
        this.context.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // https://stackoverflow.com/questions/42492136/force-android-to-use-wifi-network-with-no-internet
        // https://stackoverflow.com/questions/37218510/android-6-0-1-force-wifi-connection-with-no-internet-access

        /*
        adb shell pm disable com.android.captiveportallogin
        adb shell settings put global captive_portal_detection_enabled 0
        adb shell settings put global captive_portal_server localhost
        adb shell settings put global captive_portal_mode 0
        */
        // https://forum.xda-developers.com/showpost.php?p=79844242&postcount=2

//        Settings.Global.putInt(context.getContentResolver(), "captive_portal_detection_enabled", 0);
//        bindToNetwork();
    }

    private void bindToNetwork() {
        ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (conManager != null) {
            conManager.bindProcessToNetwork(conManager.getActiveNetwork());
        }
    }

    void startDownload(String url) {
        Log.v(TAG, String.format("Started downloading: %s", url));
        Uri requestUri = Uri.parse(url);

        DownloadManager.Request request = new DownloadManager.Request(requestUri);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, requestUri.getLastPathSegment());
        request.allowScanningByMediaScanner();

        DownloadManager downloadManager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadManager != null) {
            downloadId = downloadManager.enqueue(request);
        }
    }
}
