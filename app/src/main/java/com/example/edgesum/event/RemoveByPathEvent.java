package com.example.edgesum.event;

import com.example.edgesum.model.Video;

public class RemoveByPathEvent {

    public final String path;
    public final Type type;

    public RemoveByPathEvent(String path, Type type) {
        this.path = path;
        this.type = type;
    }
}
