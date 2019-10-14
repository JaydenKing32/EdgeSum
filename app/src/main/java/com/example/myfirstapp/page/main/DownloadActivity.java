package com.example.myfirstapp.page.main;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.myfirstapp.R;

import static com.example.myfirstapp.util.video.VidDownloading.getLastVideos;

public class DownloadActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        System.out.println("testing");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download);
        getLastVideos();
    }
}
