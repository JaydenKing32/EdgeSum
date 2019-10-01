package com.example.myfirstapp.data;

import androidx.lifecycle.LiveData;

import com.example.myfirstapp.model.Video;

import java.util.List;

/**
 * Contract for the data store of videos.
 */
public interface VideosRepository {

    LiveData<List<Video>> getVideos();

    void insert(Video video);

    void delete(int position);
}
