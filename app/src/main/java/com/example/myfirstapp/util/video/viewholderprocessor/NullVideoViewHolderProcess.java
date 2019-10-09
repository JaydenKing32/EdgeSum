package com.example.myfirstapp.util.video.viewholderprocessor;

import android.content.Context;

import com.example.myfirstapp.data.VideoViewModel;
import com.example.myfirstapp.page.main.VideoRecyclerViewAdapter;

public class NullVideoViewHolderProcess implements VideoViewHolderProcessor {
    @Override
    public void process(Context context, final VideoViewModel vm, VideoRecyclerViewAdapter.VideoViewHolder viewHolder, int position) {

    }
}
