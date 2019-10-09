package com.example.myfirstapp.event;

import com.example.myfirstapp.model.Video;

public class AddEvent extends VideoEvent {

    public AddEvent(Video video, Type type) {
        super(video, type);
    }
}
