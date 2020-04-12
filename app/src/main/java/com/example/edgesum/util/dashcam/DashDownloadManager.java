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
import java.util.HashSet;
import java.util.Set;

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
public class DashDownloadManager {
    private static final String TAG = DashDownloadManager.class.getSimpleName();
    private static DashDownloadManager downloadManager = null;
    private static MediaScannerConnection.OnScanCompletedListener downloadCallback;
    private static Set<Long> downloadIds = new HashSet<>();

    // https://stackoverflow.com/a/46328681/8031185
    private static BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            if (downloadManager != null) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

                if (downloadIds.contains(id)) {
                    downloadIds.remove(id);
                    // https://stackoverflow.com/a/46328681/8031185
                    // https://stackoverflow.com/q/21477493/8031185
                    // https://stackoverflow.com/a/33192273/8031185
                    // https://stackoverflow.com/q/8937817/8031185
                    Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id));

                    if (cursor.moveToFirst()) {
                        try {
                            int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));

                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                String uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                String path = Uri.parse(uri).getPath();

                                if (path != null) {
                                    File videoFile = new File(path);

                                    MediaScannerConnection.scanFile(context, new String[]{videoFile.getAbsolutePath()},
                                            null, downloadCallback);
                                    Log.v(TAG, String.format("Successfully downloaded: %s", path));
                                    return;
                                } else {
                                    Log.e(TAG, "Path is null");
                                }
                            } else {
                                Log.e(TAG, "Download was not successful");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, String.format("Download error: \n%s", e.getMessage()));
                        }
                    } else {
                        Log.e(TAG, "Cursor failed to move");
                    }
                } else {
                    Log.e(TAG, "Not expected download ID");
                }
            } else {
                Log.e(TAG, "Download manager is null");
            }
            Log.e(TAG, "Download error");
        }
    };

    private DashDownloadManager(Context context, MediaScannerConnection.OnScanCompletedListener callback) {
        downloadCallback = callback;
        context.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    static DashDownloadManager getInstance(Context context, MediaScannerConnection.OnScanCompletedListener callback) {
        if (downloadManager != null) {
            return downloadManager;
        }
        return new DashDownloadManager(context, callback);
    }

    void startDownload(String url, Context context) {
        Log.v(TAG, String.format("Started downloading: %s", url.substring(url.lastIndexOf('/') + 1)));
        Uri requestUri = Uri.parse(url);

        DownloadManager.Request request = new DownloadManager.Request(requestUri);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES,
                String.format("%s%s", FileManager.RAW_FOOTAGE_VIDEO_FOLDER_NAME, requestUri.getLastPathSegment()));
        request.allowScanningByMediaScanner();

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        if (downloadManager != null) {
            downloadIds.add(downloadManager.enqueue(request));
        }
    }

    public static void unregisterReceiver(Context context) {
        try {
            context.unregisterReceiver(onDownloadComplete);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Receiver is already unregistered");
        }
    }
}
