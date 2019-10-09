package com.example.myfirstapp.util.video.videoeventhandler;

import android.util.Log;

import com.example.myfirstapp.data.VideosRepository;
import com.example.myfirstapp.event.AddEvent;
import com.example.myfirstapp.event.RemoveByPathEvent;
import com.example.myfirstapp.event.RemoveEvent;
import com.example.myfirstapp.event.Type;

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
            Log.i(TAG, "onAdd");
            repository.insert(event.video);
        }
    }

    @Subscribe
    public void onRemoveByPath(RemoveByPathEvent event) {

        if (event.type == Type.SUMMARISED) {
            Log.i(TAG, "onRemoveByPath");
            repository.delete(event.path);
        }
    }

    @Subscribe
    @Override
    public void onRemove(RemoveEvent event) {

    }
}
