package com.example.myfirstapp.util.video.videoeventhandler;

import android.util.Log;

import com.example.myfirstapp.data.ProcessingVideosRepository;
import com.example.myfirstapp.data.VideosRepository;
import com.example.myfirstapp.event.AddEvent;
import com.example.myfirstapp.event.RemoveEvent;
import com.example.myfirstapp.event.Type;

import org.greenrobot.eventbus.Subscribe;

public class ProcessingVideosEventHandler implements VideoEventHandler {

    private String TAG = ProcessingVideosEventHandler.class.getSimpleName();

    public VideosRepository repository;

    public ProcessingVideosEventHandler(VideosRepository repository) {
        this.repository = repository;
    }

    @Subscribe
    @Override
    public void onAdd(AddEvent event) {

        if (event.type == Type.PROCESSING) {
            Log.i(TAG, "onAdd");
            repository.insert(event.video);
        }
    }

    @Subscribe
    @Override
    public void onRemove(RemoveEvent event) {
        if (event.type == Type.PROCESSING) {
            Log.i(TAG, "onAdd");
            repository.delete(event.video.getData());
        }
    }

}
