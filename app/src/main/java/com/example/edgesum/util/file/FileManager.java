package com.example.edgesum.util.file;

import android.os.Environment;
import android.util.Log;

import com.example.edgesum.util.devicestorage.DeviceExternalStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class FileManager {
    private static final String TAG = FileManager.class.getSimpleName();

    public static final String RAW_FOOTAGE_DIR_NAME = "rawFootage";
    private static final String SUMMARISED_DIR_NAME = "summarised";
    private static final String NEARBY_DIR_NAME = "nearby";
    private static final String SPLIT_DIR_NAME = "split";
    private static final String SPLIT_SUM_DIR_NAME = String.format("%s-sum", SPLIT_DIR_NAME);

    private static final File MOVIE_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    private static final File DOWN_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private static final File RAW_FOOTAGE_DIR = new File(MOVIE_DIR, RAW_FOOTAGE_DIR_NAME);
    private static final File SUMMARISED_DIR = new File(MOVIE_DIR, SUMMARISED_DIR_NAME);
    private static final File NEARBY_DIR = new File(DOWN_DIR, NEARBY_DIR_NAME);
    private static final File SPLIT_DIR = new File(MOVIE_DIR, SPLIT_DIR_NAME);
    private static final File SPLIT_SUM_DIR = new File(MOVIE_DIR, SPLIT_SUM_DIR_NAME);

    private static final List<File> DIRS = Arrays.asList(
            RAW_FOOTAGE_DIR, SUMMARISED_DIR, NEARBY_DIR, SPLIT_DIR, SPLIT_SUM_DIR);

    public static String getRawFootageDirPath() {
        return RAW_FOOTAGE_DIR.getAbsolutePath();
    }

    public static String getSummarisedDirPath() {
        return SUMMARISED_DIR.getAbsolutePath();
    }

    public static String getNearbyDirPath() {
        return NEARBY_DIR.getAbsolutePath();
    }

    public static String getSplitDirPath() {
        return SPLIT_DIR.getAbsolutePath();
    }

    public static String getSplitDirPath(String subDir) {
        return makeDirectory(SPLIT_DIR, subDir).getAbsolutePath();
    }

    public static String getSplitSumDirPath() {
        return SPLIT_SUM_DIR.getAbsolutePath();
    }

    public static String getSplitSumDirPath(String subDir) {
        return makeDirectory(SPLIT_SUM_DIR, subDir).getAbsolutePath();
    }

    public static void initialiseDirectories() {
        for (File dir : DIRS) {
            makeDirectory(dir);
        }
    }

    private static File makeDirectory(File dirPath) {
        if (DeviceExternalStorage.externalStorageIsWritable()) {
            Log.v(TAG, "External storage is readable");
            try {
                if (!dirPath.exists()) {
                    if (dirPath.mkdirs()) {
                        Log.v(TAG, String.format("Created new directory: %s", dirPath));
                        return dirPath;
                    } else {
                        Log.e(TAG, String.format("Failed to create new directory: %s", dirPath));
                    }
                } else {
                    Log.v(TAG, String.format("Directory already exists: %s", dirPath));
                    return dirPath;
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "External storage is not readable");
        }
        return null;
    }

    private static File makeDirectory(File dir, String subDirName) {
        return makeDirectory(new File(dir, subDirName));
    }

    public static void cleanVideoDirectories() {
        for (File dir : DIRS) {
            cleanDirectory(dir);
        }
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
}
