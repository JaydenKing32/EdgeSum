package com.example.edgesum.util.dashcam;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.example.edgesum.util.file.FileManager;

import java.io.File;

/**
 * Might not work with stock android OS
 * Open Settings, select Apps, enable "Show system apps" via the options menu (top-right 3 vertical dots), find and
 * open CaptivePortalLogin, press "Force stop"
 * <p>
 * Enter the following commands via a terminal connected to the phone
 * adb shell settings put global captive_portal_detection_enabled 0
 * adb shell settings put global captive_portal_server localhost
 * adb shell settings put global captive_portal_mode 0
 * <p>
 * https://forum.xda-developers.com/showpost.php?p=79844242&postcount=2
 * https://stackoverflow.com/questions/42492136/force-android-to-use-wifi-network-with-no-internet
 * https://stackoverflow.com/questions/37218510/android-6-0-1-force-wifi-connection-with-no-internet-access
 */
class DashDownloadManager {
    private static final String TAG = DashDownloadManager.class.getSimpleName();
    private final MediaScannerConnection.OnScanCompletedListener downloadCallback;
    private long downloadId;

    // https://stackoverflow.com/a/46328681/8031185
    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            context.unregisterReceiver(onDownloadComplete);
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
                                new String[]{videoFile.getAbsolutePath()}, null, downloadCallback);
                    } catch (Exception e) {
                        Log.e(TAG, String.format("Download error: \n%s", e.getMessage()));
                    }
                }
            }
        }
    };

    DashDownloadManager(MediaScannerConnection.OnScanCompletedListener downloadCallback) {
        this.downloadCallback = downloadCallback;
    }

    void startDownload(String url, Context context) {
        Log.v(TAG, String.format("Started downloading: %s", url));
        Uri requestUri = Uri.parse(url);

        DownloadManager.Request request = new DownloadManager.Request(requestUri);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES,
                String.format("%s%s", FileManager.RAW_FOOTAGE_VIDEO_FOLDER_NAME, requestUri.getLastPathSegment()));
        request.allowScanningByMediaScanner();

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadManager != null) {
            context.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            downloadId = downloadManager.enqueue(request);
        }
    }
}
