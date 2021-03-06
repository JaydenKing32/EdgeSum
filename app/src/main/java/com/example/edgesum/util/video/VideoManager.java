package com.example.edgesum.util.video;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore.Video.Media;
import android.util.Log;

import com.example.edgesum.model.Video;
import com.example.edgesum.util.devicestorage.DeviceExternalStorage;
import com.example.edgesum.util.file.FileManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VideoManager {
    private final static String TAG = VideoManager.class.getSimpleName();

    private VideoManager() {
    }

    public static List<Video> getAllVideoFromExternalStorageFolder(Context context, File file) {
        Log.v(TAG, "getAllVideoFromExternalStorageFolder");
        String[] projection = {
                Media._ID,
                Media.DATA,
                Media.DISPLAY_NAME,
                Media.SIZE,
                Media.MIME_TYPE
        };
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            String selection = Media.DATA + " LIKE ? ";
            String[] selectionArgs = new String[]{"%" + file.getAbsolutePath() + "%"};
            Log.d("VideoManager", file.getAbsolutePath());
            return getVideosFromExternalStorage(context, projection, selection, selectionArgs, null);
        }
        return new ArrayList<>();
    }

    static List<Video> getVideosFromDir(Context context, String dirPath) {
        File dir = new File(dirPath);
        return getVideosFromDir(context, dir);
    }

    private static List<Video> getVideosFromDir(Context context, File dir) {
        if (!dir.isDirectory()) {
            Log.e(TAG, String.format("%s is not a directory", dir.getAbsolutePath()));
            return null;
        }
        Log.v(TAG, String.format("Retrieving videos from %s", dir.getAbsolutePath()));

        File[] videoFiles = dir.listFiles();
        if (videoFiles == null) {
            Log.e(TAG, String.format("Could not access contents of %s", dir.getAbsolutePath()));
            return null;
        }

        List<String> vidPaths = Arrays.stream(videoFiles)
                .map(File::getAbsolutePath).filter(FileManager::isMp4).collect(Collectors.toList());
        List<Video> videos = new ArrayList<>();
        for (String vidPath : vidPaths) {
            videos.add(getVideoFromPath(context, vidPath));
        }
        return videos;
    }

    private static Video getVideoFromFile(Context context, File file) {
        if (file == null) {
            Log.e(TAG, "Null file");
            return null;
        }
        String[] projection = {
                Media._ID,
                Media.DATA,
                Media.DISPLAY_NAME,
                Media.SIZE,
                Media.MIME_TYPE
        };
        String selection = Media.DATA + "=?";
        String[] selectionArgs = new String[]{file.getAbsolutePath()};
        Cursor videoCursor = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, null);

        if (videoCursor == null || !videoCursor.moveToFirst()) {
            Log.d(TAG, "videoCursor is null");
            return null;
        }
        Video video = videoFromCursor(videoCursor);
        videoCursor.close();

        if (video == null) {
            Log.v(TAG, String.format("Video (%s) is null", file.getAbsolutePath()));
        }

        return video;
    }

    public static Video getVideoFromPath(Context context, String path) {
        Log.v(TAG, String.format("Retrieving video from %s", path));
        Video video = getVideoFromFile(context, new File(path));

        if (video != null) {
            return video;
        } else {
            ContentValues values = new ContentValues();
            values.put(Media.TITLE, FilenameUtils.getBaseName(path));
            values.put(Media.MIME_TYPE, String.format("video/%s", FilenameUtils.getExtension(path).toLowerCase()));
            values.put(Media.DISPLAY_NAME, "player");
            values.put(Media.DESCRIPTION, "");
            values.put(Media.DATE_ADDED, System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(Media.DATE_TAKEN, System.currentTimeMillis());
            }
            values.put(Media.DATA, path);
            context.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);

            return getVideoFromFile(context, new File(path));
        }
    }

    public static List<Video> getAllVideosFromExternalStorage(Context context, String[] projection) {
        Log.v(TAG, "getAllVideosFromExternalStorage");
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            return getVideosFromExternalStorage(context, projection, null, null, null);
        }
        return new ArrayList<>();
    }

    private static List<Video> getVideosFromExternalStorage(Context context, String[] projection, String selection,
                                                            String[] selectionArgs, String sortOrder) {
        Cursor videoCursor = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder);

        if (videoCursor != null) {
            List<Video> videos = new ArrayList<>(videoCursor.getCount());
            getVideosFromCursor(videoCursor, videos);
            videoCursor.close();
            Log.d(TAG, String.format("%d videos return", videos.size()));
            return videos;
        } else {
            return null;
        }
    }

    private static void getVideosFromCursor(Cursor videoCursor, List<Video> videos) {
        boolean cursorIsNotEmpty = videoCursor.moveToFirst();
        if (cursorIsNotEmpty) {
            do {
                Video video = videoFromCursor(videoCursor);
                if (video != null) {
                    videos.add(video);
                    Log.d(TAG, video.toString());
                } else {
                    Log.e(TAG, "Video is null");
                }
            } while (videoCursor.moveToNext());
        }
    }

    private static Video videoFromCursor(Cursor cursor) {
        Log.v(TAG, "videoFromCursor");
        Video video = null;
        try {
            String id = cursor.getString(cursor.getColumnIndex(Media._ID));
            String name = cursor.getString(cursor.getColumnIndex(Media.DISPLAY_NAME));
            String data = cursor.getString(cursor.getColumnIndex(Media.DATA));
            BigInteger size = new BigInteger(cursor.getString(cursor.getColumnIndex(Media.SIZE)));
            String mimeType = cursor.getString(cursor.getColumnIndex(Media.MIME_TYPE));
            video = new Video(id, name, data, mimeType, size);
        } catch (Exception e) {
           Log.e(TAG, "videoFromCursor error: \n%s");
        }
        return video;
    }
}
