package com.example.edgesum.util.nearby;

import android.view.View;

public interface TransferCallback {
    void sendFile(View view, String videoPath, Command command);
    boolean isConnected();
}
