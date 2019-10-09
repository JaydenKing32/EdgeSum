package com.example.myfirstapp.util.video.clouduploader;

import android.content.Context;

import com.example.myfirstapp.model.Video;

public interface CloudUploader {

    void upload(Context context, String videoPath);
}
