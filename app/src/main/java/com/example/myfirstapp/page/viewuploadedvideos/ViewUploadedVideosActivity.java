package com.example.myfirstapp.page.viewuploadedvideos;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.example.myfirstapp.R;
import com.example.myfirstapp.page.viewuploadedvideos.ui.viewuploadedvideos.ViewUploadedVideosFragment;

public class ViewUploadedVideosActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_uploaded_videos_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, ViewUploadedVideosFragment.newInstance())
                    .commitNow();
        }
    }
}
