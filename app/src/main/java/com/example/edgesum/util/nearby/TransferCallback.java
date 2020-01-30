package com.example.edgesum.util.nearby;

import android.view.View;

public interface TransferCallback {
    void sendFile(View view, String videoPath);
    boolean isConnected();
}
