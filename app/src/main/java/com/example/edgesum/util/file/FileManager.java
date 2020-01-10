package com.example.edgesum.util.file;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.example.edgesum.util.devicestorage.DeviceExternalStorage;

import java.io.File;

public class FileManager {
    private static File externalStoragePublicMovieDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    public static final String SUMMARISED_VIDEO_FOLDER_NAME = "summarised/";
    public static final String RAW_FOOTAGE_VIDEO_FOLDER_NAME = "rawFootage/";
    public static final File RAW_FOOTAGE_VIDEOS_PATH = new File(externalStoragePublicMovieDirectory, RAW_FOOTAGE_VIDEO_FOLDER_NAME);
    public final static File SUMMARISED_VIDEOS_PATH = new File(externalStoragePublicMovieDirectory, SUMMARISED_VIDEO_FOLDER_NAME);

    private FileManager() {

    }

    public static String addQuotes(String s) {
        String strWithQuotes = String.format("\"%s\"", s);
        return strWithQuotes;
    }

    public static String summarisedVideosFolderPath() {
        return SUMMARISED_VIDEOS_PATH.getAbsolutePath();
    }

    public static void makeDirectory(Context context, File path, String directoryName) {
        File newDirectory = new File(path, directoryName);
        if (DeviceExternalStorage.externalStorageIsWritable()) {
            Log.i("External storage", "Is readable");
            try {
                if (!newDirectory.exists()) {
                    boolean folderCreated = newDirectory.mkdirs();
                    Log.i("Folder created", Boolean.toString(folderCreated));
                    MediaScannerConnection.scanFile(context, new String[]{newDirectory.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });
                }
            } catch (SecurityException e) {
                Log.i("Security exception", e.getMessage());
            }
        } else {
            Log.i("External storage", "Not readable");
        }
    }
}
