package com.example.edgesum.util.video.clouduploader;

import android.content.Context;

import com.example.edgesum.model.Video;

public interface CloudUploader {

    void upload(Context context, String videoPath);
}
