package com.example.edgesum.util.nearby;

import com.example.edgesum.model.Video;

class Message {
    final Video video;
    final Command command;

    Message(Video video, Command command) {
        this.video = video;
        this.command = command;
    }
}
