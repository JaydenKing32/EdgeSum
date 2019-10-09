package com.example.myfirstapp.util.video.viewholderprocessor;

import android.content.Context;

import com.example.myfirstapp.data.VideoViewModel;
import com.example.myfirstapp.page.main.VideoRecyclerViewAdapter;

public interface VideoViewHolderProcessor {


    void process(Context context, VideoViewModel vm, VideoRecyclerViewAdapter.VideoViewHolder viewHolder, int position);

}
