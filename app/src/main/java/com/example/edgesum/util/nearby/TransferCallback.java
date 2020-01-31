package com.example.edgesum.util.nearby;

public interface TransferCallback {
    void sendFile(String videoPath, Command command);
    boolean isConnected();
}
