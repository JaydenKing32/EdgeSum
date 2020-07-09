package com.example.edgesum.util.video.clouduploader;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.services.s3.AmazonS3Client;
import com.example.edgesum.event.RemoveByPathEvent;
import com.example.edgesum.event.Type;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

public class S3Uploader implements CloudUploader {
    private static final String TAG = S3Uploader.class.getSimpleName();
    private static Instant start;

    @Override
    public void upload(Context context, String videoPath) {
        start = Instant.now();
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
                        Toast.makeText(context, String.format("Upload of %s complete", name), Toast.LENGTH_SHORT).show();
                        EventBus.getDefault().post(new RemoveByPathEvent(path, Type.SUMMARISED));

                        String time = DurationFormatUtils.formatDuration(
                                Duration.between(start, Instant.now()).toMillis(), "ss.SSS");
                        Log.w(TAG, String.format("Uploaded %s in %ss", name, time));
                    }
                }

                @Override
                public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
                    float percentDoneF = ((float) bytesCurrent / (float) bytesTotal) * 100;
                    int percentDone = (int) percentDoneF;

                    Log.d(TAG, String.format("ID: %d, bytesCurrent: %d, bytesTotal: %d, percentDone: %d",
                            id, bytesCurrent, bytesTotal, percentDone));
                }

                @Override
                public void onError(int id, Exception ex) {
                    Log.e(TAG, String.format("Upload error: \n%s", ex.getMessage()));
                }
            });

            // If you prefer to poll for the data, instead of attaching a
            // listener, check for the state and progress in the observer.
            // if (TransferState.COMPLETED == uploadObserver.getState())

            Log.d("UploadToS3", "Bytes Transferred: " + uploadObserver.getBytesTransferred());
            Log.d("UploadToS3", "Bytes Total: " + uploadObserver.getBytesTotal());
        } else {
            Log.d("UploadToS3Failed", "File does not exist");
        }
    }
}
