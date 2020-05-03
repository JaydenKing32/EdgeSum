package com.example.edgesum.util.nearby;

import android.content.Context;

import com.example.edgesum.model.Video;

public interface TransferCallback {
    void queueVideo(Video video, Command command);

    void splitAndQueue(Context context, String videoPath);

    void initialTransfer();

    void nextTransfer();

    void sendCommandMessageToAll(Command command, String filename);

    void sendCommandMessage(Command command, String filename, String toEndpoint);

    boolean isConnected();
}
