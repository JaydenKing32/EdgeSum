package com.example.edgesum.util.nearby;

import android.content.Context;

import com.example.edgesum.model.Video;

public interface TransferCallback {
    void queueVideo(Video video, Message.Command command);

    int splitAndQueue(Context context, String videoPath);

    void initialTransfer();

    void nextTransfer();

    void sendCommandMessageToAll(Message.Command command, String filename);

    void sendCommandMessage(Message.Command command, String filename, String toEndpoint);

    boolean isConnected();
}
