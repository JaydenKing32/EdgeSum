package com.example.myfirstapp.event;

import com.example.myfirstapp.model.Video;

public class RemoveByPathEvent {

    public final String path;
    public final Type type;

    public RemoveByPathEvent(String path, Type type) {
        this.path = path;
        this.type = type;
    }
}
