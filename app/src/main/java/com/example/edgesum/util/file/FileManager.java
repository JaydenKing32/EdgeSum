package com.example.edgesum.util.file;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

import com.example.edgesum.util.devicestorage.DeviceExternalStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileManager {
    private static final String TAG = FileManager.class.getSimpleName();
    private static final File MOVIE_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    private static final File DOWN_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

    public static final String SUMMARISED_VIDEO_FOLDER_NAME = "summarised/";
    public static final String RAW_FOOTAGE_VIDEO_FOLDER_NAME = "rawFootage/";
    public static final String NEARBY_FOLDER_NAME = "nearby/";
    public static final File RAW_FOOTAGE_VIDEOS_PATH = new File(MOVIE_DIR, RAW_FOOTAGE_VIDEO_FOLDER_NAME);
    public final static File SUMMARISED_VIDEOS_PATH = new File(MOVIE_DIR, SUMMARISED_VIDEO_FOLDER_NAME);
    public final static File NEARBY_VIDEOS_PATH = new File(DOWN_DIR, NEARBY_FOLDER_NAME);

    private FileManager() {
    }

    public static String rawFootageFolderPath() {
        return RAW_FOOTAGE_VIDEOS_PATH.getAbsolutePath();
    }

    public static String summarisedVideosFolderPath() {
        return SUMMARISED_VIDEOS_PATH.getAbsolutePath();
    }

    public static void makeDirectory(Context context, File dirPath, String dirName) {
        File newDirectory = new File(dirPath, dirName);
        if (DeviceExternalStorage.externalStorageIsWritable()) {
            Log.v(TAG, "External storage is readable");
            try {
                if (!newDirectory.exists()) {
                    if (newDirectory.mkdirs()) {
                        Log.v(TAG, String.format("Created new directory: %s", dirPath));
                        MediaScannerConnection.scanFile(context, new String[]{newDirectory.getAbsolutePath()}, null,
                                (path, uri) -> Log.v(TAG, String.format("Scanned %s\n  -> uri=%s", path, uri)));
                    } else {
                        Log.e(TAG, String.format("Failed to create new directory: %s", dirPath));
                    }
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "External storage is not readable");
        }
    }

    // https://stackoverflow.com/a/9293885/8031185
    public static void copy(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source)) {
            try (OutputStream out = new FileOutputStream(dest)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static void cleanVideoDirectories() {
        cleanDirectory(RAW_FOOTAGE_VIDEOS_PATH);
        cleanDirectory(SUMMARISED_VIDEOS_PATH);
        cleanDirectory(NEARBY_VIDEOS_PATH);
    }

    private static void cleanDirectory(File dir) {
        if (!dir.exists()) {
            Log.e(TAG, "Directory does not exist: %s");
        }

        if (!dir.isDirectory()) {
            Log.e(TAG, String.format("Attempt to clean a non-directory: %s", dir.getAbsolutePath()));
        }

        File[] files = dir.listFiles();

        if (files != null) {
            for (File video : files) {
                String videoPath = video.getAbsolutePath();

                if (video.delete()) {
                    Log.v(TAG, String.format("Video deleted: %s", videoPath));
                } else {
                    Log.e(TAG, String.format("Failed video deletion: %s", videoPath));
                }
            }
        }
    }
}
