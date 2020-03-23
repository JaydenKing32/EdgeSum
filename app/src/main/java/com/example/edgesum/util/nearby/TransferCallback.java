package com.example.edgesum.util.nearby;

import com.example.edgesum.model.Video;

public interface TransferCallback {
    void addToTransferQueue(Video video, Command command);

    void initialTransfer();

    void addOrSend(Video video, Command command);

    void sendFileToAll(String videoPath, Command command);

    void sendFile(Message message, Endpoint toEndpoint);

    void sendCommandMessageToAll(Command command, String filename);

    void sendCommandMessage(Command command, String filename, String toEndpoint);

    boolean isConnected();
}
