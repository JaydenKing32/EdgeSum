package com.example.edgesum.util.nearby;

public interface TransferCallback {
    void addVideo(String videoPath);

    void returnVideo(String videoPath);

    void nextTransfer();

    void sendCommandMessageToAll(Message.Command command, String filename);

    void sendCommandMessage(Message.Command command, String filename, String toEndpoint);

    void stopDashDownload();

    boolean isConnected();

    void printPreferences(boolean autoDown);

    void handleSegment(String videoName);
}
