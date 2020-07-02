package com.example.edgesum.util.video.viewholderprocessor;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.example.edgesum.data.VideoViewModel;
import com.example.edgesum.page.main.VideoRecyclerViewAdapter;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.summariser.SummariserIntentService;

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
            final String videoPath = viewHolder.video.getData();
            Log.v(TAG, String.format("User selected %s", videoPath));

            if (transferCallback.isConnected()) {
                transferCallback.addVideo(videoPath);
                transferCallback.nextTransfer();

//                EventBus.getDefault().post(new AddEvent(videoPath, Type.PROCESSING));
//                EventBus.getDefault().post(new RemoveEvent(videoPath, Type.RAW));

                Toast.makeText(context, "Transferring to connected devices", Toast.LENGTH_SHORT).show();
            } else {
                final String output = String.format("%s/%s",
                        FileManager.getSummarisedDirPath(), FileManager.getFilenameFromPath(videoPath));

                Intent summariseIntent = new Intent(context, SummariserIntentService.class);
                summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, videoPath);
                summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, output);
                summariseIntent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.LOCAL_TYPE);
                context.getApplicationContext().startService(summariseIntent);

//                EventBus.getDefault().post(new AddEvent(videoPath, Type.PROCESSING));
//                EventBus.getDefault().post(new RemoveEvent(videoPath, Type.RAW));

                Toast.makeText(context, "Add to processing queue", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
