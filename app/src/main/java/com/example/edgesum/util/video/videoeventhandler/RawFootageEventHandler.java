package com.example.edgesum.util.video.videoeventhandler;

import android.util.Log;

import com.example.edgesum.data.VideosRepository;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;

import org.greenrobot.eventbus.Subscribe;

public class RawFootageEventHandler implements VideoEventHandler {

    private String TAG = RawFootageEventHandler.class.getSimpleName();

    public VideosRepository repository;

    public RawFootageEventHandler(VideosRepository repository) {

        this.repository = repository;
    }

    @Subscribe
    @Override
    public void onAdd(AddEvent event) {
        if (event.type == Type.RAW) {
            Log.v(TAG, "onAdd");
            repository.insert(event.video);
        }
    }

    @Subscribe
    @Override
    public void onRemove(RemoveEvent event) {
        if (event.type == Type.RAW) {
            Log.v(TAG, "onRemove");
            repository.delete(event.video.getData());
        }
    }

}
