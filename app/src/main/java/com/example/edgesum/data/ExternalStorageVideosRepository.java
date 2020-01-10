package com.example.edgesum.data;

import android.content.Context;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.video.VideoManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExternalStorageVideosRepository implements VideosRepository {

    Context context;
    private final String TAG;
    private final String PATH;

    private File videoDirectory;

    private List<Video> videos = new ArrayList<>();
    MutableLiveData<List<Video>> result = new MutableLiveData<>();


    public ExternalStorageVideosRepository(Context context, String tag, String path) {
        this.context = context;
        this.TAG = tag;
        this.PATH = path;
        if (PATH == null) {
            Log.i("ExternalStorageVideosRepository", "null");
        }

        videoDirectory = new File(PATH);
        Log.i("Repo", videoDirectory.getAbsolutePath());
//        FileManager.makeDirectory(context, externalStoragePublicMovieDirectory, PATH);
    }

    @Override
    public MutableLiveData<List<Video>> getVideos() {

        videos = VideoManager.getAllVideoFromExternalStorageFolder(context.getApplicationContext(), videoDirectory);
        result.setValue(videos);
        return result;
    }

    @Override
    public void insert(Video video) {
//        videos.add(video);

        videos = VideoManager.getAllVideoFromExternalStorageFolder(context.getApplicationContext(), videoDirectory);
        result.postValue(videos);
    }

    @Override
    public void delete(int position) {
        videos.remove(position);
        result.postValue(videos);
    }

    @Override
    public void delete(String path) {
        videos = videos.stream().filter(e -> !e.getData().equalsIgnoreCase(path)).collect(Collectors.toList());
        result.postValue(videos);
    }

    @Override
    public void update(Video video, int position) {
        videos.set(position, video);
        result.postValue(videos);
    }

}