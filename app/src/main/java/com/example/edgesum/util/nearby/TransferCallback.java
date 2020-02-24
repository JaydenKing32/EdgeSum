package com.example.edgesum.util.nearby;

import com.example.edgesum.model.Video;

public interface TransferCallback {
    void addToTransferQueue(Video video, Command command);

    void initialTransfer();

    void sendFileToAll(String videoPath, Command command);

    void sendFile(Message message, Endpoint toEndpoint);

    void sendCommandMessage(Command command, String filename);

    boolean isConnected();
}
