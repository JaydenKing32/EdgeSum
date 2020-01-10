package com.example.edgesum.util.video.viewholderprocessor;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Toast;

import com.example.edgesum.data.VideoViewModel;
import com.example.edgesum.model.Video;
import com.example.edgesum.page.main.VideoRecyclerViewAdapter;
import com.example.edgesum.util.video.clouduploader.S3Uploader;

public class SummarisedVideosViewHolderProcessor implements VideoViewHolderProcessor {
    @Override
    public void process(Context context, VideoViewModel vm, VideoRecyclerViewAdapter.VideoViewHolder viewHolder, int position) {

        viewHolder.actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Video video = viewHolder.video;
                final String path = video.getData();



                S3Uploader s3 = new S3Uploader();
                s3.upload(context, path);

                Toast.makeText(context, "Add to upload queue", Toast.LENGTH_SHORT).show();
            }
        });

        if (!viewHolder.video.isVisible()) {
            viewHolder.view.setBackgroundColor(Color.parseColor("#e7eecc"));
            viewHolder.actionButton.setEnabled(false);
        }
    }
}
