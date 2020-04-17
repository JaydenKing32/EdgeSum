package com.example.edgesum.util.video.videoeventhandler;

import android.util.Log;

import com.example.edgesum.data.VideosRepository;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveByNameEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.RemoveFirstEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.util.file.FileManager;

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
            Log.v(TAG, "onAdd");
            repository.insert(event.video);
        }
    }

    @Subscribe
    @Override
    public void onRemove(RemoveEvent event) {
        if (event.type == Type.PROCESSING) {
            Log.v(TAG, "onRemove");
            repository.delete(event.video.getData());
        }
    }

    @Subscribe
    public void removeFirst(RemoveFirstEvent event) {
        if (event.type == Type.PROCESSING) {
            Log.v(TAG, "removeFirst");
            repository.delete(0);
        }
    }

    @Subscribe
    public void removeByName(RemoveByNameEvent event) {
        if (event.type == Type.PROCESSING) {
            Log.v(TAG, "removeByName");
            String path = String.format("%s/%s", FileManager.getRawFootageDirPath(), event.name);
            repository.delete(path);
        }
    }
}
