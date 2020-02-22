package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.Command;
import com.example.edgesum.util.nearby.TransferCallback;
import com.example.edgesum.util.video.VideoManager;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.function.Supplier;

class DashModel {
    private static final String TAG = DashModel.class.getSimpleName();

    static final String drideBaseUrl = "http://192.168.1.254/DCIM/MOVIE/";
    static final String blackvueBaseUrl = "http://10.99.77.1/";
    static final String blackvueVideoUrl = blackvueBaseUrl + "Record/";

    final DashName dashName;
    final String baseUrl;
    final String videoDirUrl;
    final Supplier<List<String>> getFilenameFunc;

    DashModel(DashName dashName, String baseUrl, String videoDirUrl, Supplier<List<String>> getFilenameFunc) {
        this.dashName = dashName;
        this.baseUrl = baseUrl;
        this.videoDirUrl = videoDirUrl;
        this.getFilenameFunc = getFilenameFunc;
    }

    List<String> downloadAll(Context context) {
        List<String> allFiles = getFilenameFunc.get();

        if (allFiles == null) {
            return null;
        }
        int last_n = 2;
        List<String> lastFiles = allFiles.subList(Math.max(allFiles.size(), 0) - last_n, allFiles.size());

        for (String filename : lastFiles) {
            downloadVideo(FileManager.RAW_FOOTAGE_VIDEOS_PATH.getAbsolutePath(), filename, context);
        }
        Log.d(TAG, "All downloads complete");
        return lastFiles;
    }

    void downloadVideo(String downloadDir, String filename, Context context) {
        try {
            File videoFile = new File(String.format("%s/%s", downloadDir, filename));
            Log.d(TAG, "Started downloading: " + filename);
            FileUtils.copyURLToFile(
                    new URL(videoDirUrl + filename),
                    videoFile
            );

            if (context != null) {
                // New files aren't immediately added to the MediaStore database, so it's necessary manually trigger it
                // Tried using sendBroadcast, but that doesn't guarantee that it will be immediately added.
                // MediaScannerConnection ensures that the new file is added to the database before it is queried
                // https://stackoverflow.com/a/5814533/8031185
                MediaScannerConnection.scanFile(context,
                        new String[]{videoFile.getAbsolutePath()}, null, (path, uri) -> {
                            Video video = VideoManager.getVideoFromFile(context, new File(path));
                            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                            Log.d(TAG, "Finished downloading: " + filename);
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void downloadAndSend(String downloadDir, String filename, TransferCallback transferCallback, Context context) {
        try {
            File videoFile = new File(String.format("%s/%s", downloadDir, filename));
            Log.d(TAG, "Started downloading: " + filename);
            FileUtils.copyURLToFile(
                    new URL(videoDirUrl + filename),
                    videoFile
            );

            if (context != null) {
                MediaScannerConnection.scanFile(context,
                        new String[]{videoFile.getAbsolutePath()}, null, (path, uri) -> {
                            Video video = VideoManager.getVideoFromFile(context, new File(path));
                            Log.d(TAG, "Finished downloading: " + filename);
                            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                            transferCallback.addToTransferQueue(video, Command.SUMMARISE);
                            transferCallback.nextTransfer();
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
