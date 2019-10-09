package com.example.myfirstapp.util.video.videoeventhandler;

import com.example.myfirstapp.event.AddEvent;
import com.example.myfirstapp.event.RemoveEvent;

import org.greenrobot.eventbus.Subscribe;

public interface VideoEventHandler {

    void onAdd(AddEvent event);

    void onRemove(RemoveEvent event);
}
