package com.example.myfirstapp.event;

import com.example.myfirstapp.model.Video;

public class RemoveEvent extends VideoEvent {

    public RemoveEvent(Video video, Type type) {
        super(video, type);
    }
}
