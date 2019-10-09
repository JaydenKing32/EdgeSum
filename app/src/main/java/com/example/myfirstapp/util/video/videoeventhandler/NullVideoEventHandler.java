package com.example.myfirstapp.util.video.videoeventhandler;

import com.example.myfirstapp.event.AddEvent;
import com.example.myfirstapp.event.RemoveEvent;

import org.greenrobot.eventbus.Subscribe;

public class NullVideoEventHandler implements VideoEventHandler {

    @Subscribe
    @Override
    public void onAdd(AddEvent event) {

    }

    @Override
    public void onRemove(RemoveEvent event) {

    }
}
