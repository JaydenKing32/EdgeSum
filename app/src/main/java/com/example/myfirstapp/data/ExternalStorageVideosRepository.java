package com.example.myfirstapp.data;

import android.content.Context;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.example.myfirstapp.model.Video;
import com.example.myfirstapp.util.file.FileManager;
import com.example.myfirstapp.util.video.VideoManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExternalStorageVideosRepository implements VideosRepository {

    Context context;
    private final String TAG;
    private final String PATH;

    private File videoDirectory;

    private List<Video> videos = new ArrayList<>();


    public ExternalStorageVideosRepository(Context context, String tag, String path) {
        this.context = context;
        this.TAG = tag;
        this.PATH = path;
        final File externalStoragePublicMovieDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (PATH == null) {
            Log.i("ExternalStorageVideosRepository", "null");
        }
        videoDirectory = new File(externalStoragePublicMovieDirectory, PATH);

        FileManager.makeDirectory(externalStoragePublicMovieDirectory, PATH);
    }

    @Override
    public MutableLiveData<List<Video>> getVideos() {
        String[] proj = {MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE};

//        return VideoManager.getAllVideoFromExternalStorageFolder(context, proj, videoDirectory);

        videos = VideoManager.getAllVideosFromExternalStorage(context, proj);
        MutableLiveData<List<Video>> result = new MutableLiveData<>();
        result.setValue(videos);

        return result;
    }

    @Override
    public void insert(Video video) {
        videos.add(video);
    }

    @Override
    public void delete(int position) {
        videos.remove(position);
    }
}
