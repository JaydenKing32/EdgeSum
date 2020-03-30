package com.example.edgesum.util.video.videoeventhandler;

import android.util.Log;

import com.example.edgesum.data.VideosRepository;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveByPathEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;

import org.greenrobot.eventbus.Subscribe;

public class SummarisedVideosEventHandler implements VideoEventHandler {

    private String TAG = SummarisedVideosEventHandler.class.getSimpleName();

    public VideosRepository repository;

    public SummarisedVideosEventHandler(VideosRepository repository) {
        this.repository = repository;
    }

    @Subscribe
    @Override
    public void onAdd(AddEvent event) {

        if (event.type == Type.SUMMARISED) {
            Log.v(TAG, "onAdd");
            repository.insert(event.video);
        }
    }

    @Subscribe
    public void onRemoveByPath(RemoveByPathEvent event) {

        if (event.type == Type.SUMMARISED) {
            Log.v(TAG, "onRemoveByPath");
            repository.delete(event.path);
        }
    }

    @Subscribe
    @Override
    public void onRemove(RemoveEvent event) {

    }
}
