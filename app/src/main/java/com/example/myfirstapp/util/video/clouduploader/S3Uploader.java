package com.example.myfirstapp.util.video.clouduploader;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.example.myfirstapp.event.RemoveByPathEvent;
import com.example.myfirstapp.event.RemoveEvent;
import com.example.myfirstapp.event.Type;
import com.example.myfirstapp.model.Video;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class S3Uploader implements CloudUploader {
    @Override
    public void upload(Context context, String videoPath) {
        uploadWithTransferUtility(context, videoPath);
    }

    private void uploadWithTransferUtility(final Context context, final String path) {

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
                        EventBus.getDefault().post(new RemoveByPathEvent(path, Type.SUMMARISED));
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

}
