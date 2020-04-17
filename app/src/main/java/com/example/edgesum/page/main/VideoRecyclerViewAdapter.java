package com.example.edgesum.page.main;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.example.edgesum.R;
import com.example.edgesum.data.VideoViewModel;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.page.main.VideoFragment.OnListFragmentInteractionListener;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.summariser.SummariserIntentService;
import com.example.edgesum.util.video.viewholderprocessor.VideoViewHolderProcessor;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Video} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class VideoRecyclerViewAdapter extends RecyclerView.Adapter<VideoRecyclerViewAdapter.VideoViewHolder> {
    private static final String TAG = VideoRecyclerViewAdapter.class.getSimpleName();
    private final OnListFragmentInteractionListener listFragmentInteractionListener;
    private final String BUTTON_ACTION_TEXT;
    private final TransferCallback transferCallback;

    private Context context;
    private List<Video> videos;
    private SelectionTracker<Long> tracker;
    private VideoViewHolderProcessor videoViewHolderProcessor;
    private VideoViewModel viewModel;

    VideoRecyclerViewAdapter(OnListFragmentInteractionListener listener,
                             Context context,
                             String buttonText,
                             VideoViewHolderProcessor videoViewHolderProcessor,
                             VideoViewModel videoViewModel,
                             TransferCallback transferCallback) {
        this.listFragmentInteractionListener = listener;
        this.context = context;
        this.BUTTON_ACTION_TEXT = buttonText;
        this.videoViewHolderProcessor = videoViewHolderProcessor;
        this.viewModel = videoViewModel;
        this.transferCallback = transferCallback;
        setHasStableIds(true);
    }

    public void setTracker(SelectionTracker<Long> tracker) {
        this.tracker = tracker;
    }

    void sendVideos(Selection<Long> positions) {
        if (transferCallback.isConnected()) {
            for (Long pos : positions) {
                transferCallback.addToTransferQueue(videos.get(pos.intValue()), Command.SUMMARISE);
            }
            transferCallback.nextTransfer();
        } else {
            for (Long pos : positions) {
                Video video = videos.get(pos.intValue());
                EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
                EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

                final String output = String.format("%s/%s", FileManager.getSummarisedDirPath(), video.getName());
                Intent summariseIntent = new Intent(context, SummariserIntentService.class);
                summariseIntent.putExtra(SummariserIntentService.VIDEO_KEY, video);
                summariseIntent.putExtra(SummariserIntentService.OUTPUT_KEY, output);
                summariseIntent.putExtra(SummariserIntentService.TYPE_KEY, SummariserIntentService.LOCAL_TYPE);
                context.startService(summariseIntent);
            }
        }
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.video_list_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final VideoViewHolder holder, final int position) {
        holder.video = videos.get(position);
        holder.thumbnailView.setImageBitmap(getThumbnail(videos.get(position).getId()));
        holder.videoFileNameView.setText(videos.get(position).getName());
        holder.actionButton.setText(BUTTON_ACTION_TEXT);

        videoViewHolderProcessor.process(context, viewModel, holder, position);

        holder.view.setOnClickListener(v -> {
            if (null != listFragmentInteractionListener) {
                // Notify the active callbacks interface (the activity, if the
                // fragment is attached to one) that an item has been selected.
                listFragmentInteractionListener.onListFragmentInteraction(holder.video);
            }
        });

        if (tracker.isSelected(getItemId(position))) {
            holder.layout.setBackgroundResource(android.R.color.darker_gray);
        } else {
            holder.layout.setBackgroundResource(android.R.color.transparent);
        }
    }

    public void uploadWithTransferUtility(String path) {

        TransferUtility transferUtility =
                TransferUtility.builder()
                        .context(context)
                        .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                        .s3Client(new AmazonS3Client(AWSMobileClient.getInstance()))
                        .build();

        File file = new File(path);

        if (file.exists()) {
            String name = file.getName();
            final String userPrivatePath = String.format("private/%s", AWSMobileClient.getInstance().getIdentityId());
            final String S3Key = String.format("%s/%s", userPrivatePath, name);
            TransferObserver uploadObserver =
                    transferUtility.upload(
                            S3Key,
                            file);

            // Attach a listener to the observer to get state update and progress notifications
            uploadObserver.setTransferListener(new TransferListener() {

                @Override
                public void onStateChanged(int id, TransferState state) {
                    if (TransferState.COMPLETED == state) {
                        // Handle a completed upload.
                        Toast.makeText(context, "Uploaded", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDonef = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDonef;

                    Log.d("UploadToS3", "ID:" + id + " bytesCurrent: " + bytesCurrent
                            + " bytesTotal: " + bytesTotal + " " + percentDone + "%");
                }

                @Override
                public void onError(int id, Exception ex) {
                    // Handle errors
                }

            });

            // If you prefer to poll for the data, instead of attaching a
            // listener, check for the state and progress in the observer.
            if (TransferState.COMPLETED == uploadObserver.getState()) {
                // Handle a completed upload.
            }

            Log.d("UploadToS3", "Bytes Transferred: " + uploadObserver.getBytesTransferred());
            Log.d("UploadToS3", "Bytes Total: " + uploadObserver.getBytesTotal());
        } else {
            Log.d("UploadToS3Failed", "File does not exist");
        }
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private Bitmap getThumbnail(String id) {
        Bitmap thumbnail = MediaStore.Video.Thumbnails.getThumbnail(
                this.context.getContentResolver(),
                Integer.parseInt(id),
                MediaStore.Video.Thumbnails.MICRO_KIND,
                null);
        return thumbnail;
    }

    public void setVideos(List<Video> videos) {
        this.videos = videos;
        notifyDataSetChanged();
    }


    public class VideoViewHolder extends RecyclerView.ViewHolder {
        public final View view;
        public final ImageView thumbnailView;
        public final TextView videoFileNameView;
        public final Button actionButton;
        public Video video;
        final LinearLayout layout;

        public VideoViewHolder(View view) {
            super(view);
            this.view = view;
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            videoFileNameView = (TextView) view.findViewById(R.id.content);
            actionButton = (Button) view.findViewById(R.id.actionButton);
            layout = itemView.findViewById(R.id.video_row);
        }

        public ItemDetailsLookup.ItemDetails<Long> getItemDetails() {
            return new ItemDetailsLookup.ItemDetails<Long>() {
                @Override
                public int getPosition() {
                    return getAdapterPosition();
                }

                @Nullable
                @Override
                public Long getSelectionKey() {
                    return getItemId();
                }
            };
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString() + " '" + videoFileNameView.getText() + "'";
        }
    }
}
