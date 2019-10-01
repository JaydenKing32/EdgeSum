package com.example.myfirstapp.data;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.myfirstapp.model.Video;

import java.util.ArrayList;
import java.util.List;

public class ProcessingVideosRepository implements VideosRepository {

    Context context;

    List<Video> videos = new ArrayList<>();

    public ProcessingVideosRepository(Context context) {
        this.context = context;
    }

    @Override
    public MutableLiveData<List<Video>> getVideos() {
        List<Video> videos = new ArrayList<>();
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
