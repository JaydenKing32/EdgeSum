package com.example.myfirstapp.util.video;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.util.Log;

import com.example.myfirstapp.model.Video;
import com.example.myfirstapp.util.devicestorage.DeviceExternalStorage;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


public class VideoManager {

    private VideoManager() {

    }

    public static List<Video> getAllVideoFromExternalStorageFolder(Context context, String[] projection, File file) {
        Log.i("VideoManager", "getAllVideoFromExternalStorageFolder");
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            String selection=MediaStore.Video.Media.DATA +" LIKE ? ";
            String[] selectionArgs=new String[]{"%" + "storage/emulated/0/Movies" + "%"};
            Log.i("VideoManager", file.getAbsolutePath());
            return getVideosFromExternalStorage(context, projection, selection, selectionArgs, null);
        }
        return new ArrayList<>();
    }


    public static List<Video> getAllVideosFromExternalStorage(Context context, String[] projection) {
        Log.i("VideoManager", "getAllVideosFromExternalStorage");
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            return getVideosFromExternalStorage(context, projection, null, null, null);
        }
        return new ArrayList<>();
    }

    private static List<Video> getVideosFromExternalStorage(Context context, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor videoCursor = context.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder);
        List<Video> videos = new ArrayList<>(videoCursor.getCount());
        getVideosFromCursor(videoCursor, videos);
        videoCursor.close();
        Log.i("VideoManager", String.format("%d videos return", videos.size()));
        return videos;
    }

    private static void getVideosFromCursor(Cursor videoCursor, List<Video> videos) {
        boolean cursorIsNotEmpty = videoCursor.moveToFirst();
        if (cursorIsNotEmpty) {
            do {
                Video video = videoFromCursor(videoCursor);
                videos.add(video);
                Log.i("Video toString", video.toString());
            } while (videoCursor.moveToNext());
        }
    }

    private static Video videoFromCursor(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media._ID));
        String name = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME));
        String data = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA));
        BigInteger size = new BigInteger(cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.SIZE)));
        Video video = new Video(id, name, data, size);
        return video;
    }
}
