package com.example.edgesum.util.video.viewholderprocessor;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.edgesum.data.VideoViewModel;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.page.main.VideoRecyclerViewAdapter;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.FfmpegTools;
import com.example.edgesum.util.video.VideoManager;
import com.example.edgesum.util.video.summariser.SummariserIntentService;

import org.apache.commons.io.FilenameUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.List;

public class RawFootageViewHolderProcessor implements VideoViewHolderProcessor {
    private static final String TAG = RawFootageViewHolderProcessor.class.getSimpleName();
    private TransferCallback transferCallback;

    public RawFootageViewHolderProcessor(TransferCallback transferCallback) {
        this.transferCallback = transferCallback;
    }

    @Override
    public void process(final Context context, final VideoViewModel vm,
                        final VideoRecyclerViewAdapter.VideoViewHolder viewHolder, final int position) {
        viewHolder.actionButton.setOnClickListener(view -> {
            final Video video = viewHolder.video;
            final String filePath = video.getData();
            final File pathFile = new File(filePath);
            final String pathName = pathFile.getName();
            final String output = String.format("%s/%s", FileManager.getSummarisedDirPath(), pathName);
            final String baseVideoName = FilenameUtils.getBaseName(pathName);

            int splitNum = 4;
            FfmpegTools.splitVideo(context, filePath, splitNum);
            List<Video> videos = VideoManager.getVideosFromDir(context,
                    new File(FileManager.getSplitDirPath(baseVideoName)));

            if (videos == null || videos.size() == 0) {
                Log.e(TAG, String.format("Could not split %s", pathName));
                return;
            }

            for (Video vid : videos) {
                Intent summariseIntent = new Intent(context, SummariserIntentService.class);
                summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, vid);
                summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, String.format("%s/%s",
                        FileManager.getSplitSumDirPath(baseVideoName), vid.getName()));
                summariseIntent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.LOCAL_TYPE);
                summariseIntent.putExtra(SummariserIntentService.SPLIT_PARENT_KEY, video.getName());
                summariseIntent.putExtra(SummariserIntentService.SPLIT_NUM_KEY, splitNum);
                context.getApplicationContext().startService(summariseIntent);
            }
            EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
            EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

//            if (!transferCallback.isConnected()) {
//                Intent summariseIntent = new Intent(context, SummariserIntentService.class);
//                summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, video);
//                summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, output);
//                summariseIntent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.LOCAL_TYPE);
//                context.getApplicationContext().startService(summariseIntent);
//
//                EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
//                EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
//
//                Toast.makeText(context, "Add to processing queue", Toast.LENGTH_SHORT).show();
//            } else {
//                transferCallback.addToTransferQueue(video, Command.SUMMARISE);
//                transferCallback.nextTransfer();
//
//                Toast.makeText(context, "Transferring to connected devices", Toast.LENGTH_SHORT).show();
//            }
        });
    }
}
