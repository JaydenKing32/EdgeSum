package com.example.edgesum.util.nearby;

import com.example.edgesum.model.Video;

public interface TransferCallback {
    void addToTransferQueue(Video video, Command command);

    void addVideoSegment(String baseName, Video video);

    void initialTransfer();

    void nextTransfer();

    void sendCommandMessageToAll(Command command, String filename);

    void sendCommandMessage(Command command, String filename, String toEndpoint);

    boolean isConnected();
}
