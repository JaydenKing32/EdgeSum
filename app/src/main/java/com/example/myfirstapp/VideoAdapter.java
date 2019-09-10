package com.example.myfirstapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;


public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
    private List<String> videos = new ArrayList<>();
    private LayoutInflater mInflater;


    // data is passed into the constructor
    VideoAdapter(Context context) {
        this.mInflater = LayoutInflater.from(context);
        for (int i = 0; i < 100; i++) {
            videos.add("Video" + i + ".mp4");
        }

    }

    // inflates the row layout from xml when needed
    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.processing_videos_layout, parent, false);
        return new VideoViewHolder(view);
    }

    // binds the data to the TextView in each row
    @Override
    public void onBindViewHolder(VideoViewHolder holder, int position) {
        holder.bindData(videos.get(position));
    }

    // total number of rows
    @Override
    public int getItemCount() {
        return videos.size();
    }

    // resets the list with a new set of data
    public void setItems(List<String> items) {
        videos = items;
    }

    // stores and recycles views as they are scrolled off screen
    class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView txt_name;
        TextView txt_description;

        VideoViewHolder(View itemView) {
            super(itemView);
            txt_name = itemView.findViewById(R.id.txt_name);
            txt_description = itemView.findViewById(R.id.txt_description);
        }

        void bindData(String item) {
            txt_name.setText(item);
        }
    }
}

