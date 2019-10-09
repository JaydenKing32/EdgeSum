package com.example.myfirstapp.page.main;

import android.content.Context;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.example.myfirstapp.R;
import com.example.myfirstapp.data.VideoViewModel;
import com.example.myfirstapp.model.Video;
import com.example.myfirstapp.page.main.VideoFragment.OnListFragmentInteractionListener;
import com.example.myfirstapp.util.video.viewholderprocessor.NullVideoViewHolderProcess;
import com.example.myfirstapp.util.video.viewholderprocessor.VideoViewHolderProcessor;

import java.io.File;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that can display a {@link Video} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 * TODO: Replace the implementation with code for your data type.
 */
public class VideoRecyclerViewAdapter extends RecyclerView.Adapter<VideoRecyclerViewAdapter.VideoViewHolder> {
    private String TAG = VideoRecyclerViewAdapter.class.getSimpleName();
    private List<Video> videos;
    private final OnListFragmentInteractionListener listFragmentInteractionListener;
    private Context context;
    private final String BUTTON_ACTION_TEXT;

    private VideoViewHolderProcessor videoViewHolderProcessor;

    private VideoViewModel viewModel;

    public VideoRecyclerViewAdapter(OnListFragmentInteractionListener listener, Context context, String buttonText) {
        this.listFragmentInteractionListener = listener;
        this.context = context;
        this.BUTTON_ACTION_TEXT = buttonText;
        this.videoViewHolderProcessor = new NullVideoViewHolderProcess();
    }

    public VideoRecyclerViewAdapter(
            OnListFragmentInteractionListener listener,
            Context context,
            String buttonText,
            VideoViewHolderProcessor videoViewHolderProcessor) {
        this(listener, context, buttonText);
        this.videoViewHolderProcessor = videoViewHolderProcessor;
    }

    public VideoRecyclerViewAdapter(
            OnListFragmentInteractionListener listener,
            Context context,
            String buttonText,
            VideoViewHolderProcessor videoViewHolderProcessor,
            VideoViewModel videoViewModel) {
        this(listener, context, buttonText);
        this.videoViewHolderProcessor = videoViewHolderProcessor;
        this.viewModel = videoViewModel;
    }



    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_videos, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final VideoViewHolder holder, final int position) {
        holder.video = videos.get(position);
        holder.thumbnailView.setImageBitmap(getThumbnail(videos.get(position).getId()));
        holder.videoFileNameView.setText(videos.get(position).getName());
        holder.actionButton.setText(BUTTON_ACTION_TEXT);

        videoViewHolderProcessor.process(context, viewModel, holder, position);

        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != listFragmentInteractionListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    listFragmentInteractionListener.onListFragmentInteraction(holder.video);
                }
                System.out.println("View onClick");
                System.out.println(position);
            }
        });
        final VideoRecyclerViewAdapter a = this;


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

        public VideoViewHolder(View view) {
            super(view);
            this.view = view;
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            videoFileNameView = (TextView) view.findViewById(R.id.content);
            actionButton = (Button) view.findViewById(R.id.actionButton);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + videoFileNameView.getText() + "'";
        }
    }


}
