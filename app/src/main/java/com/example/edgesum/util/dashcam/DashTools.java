package com.example.edgesum.util.dashcam;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.video.VideoManager;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DashTools {
    private static final String TAG = DashTools.class.getSimpleName();

    static List<String> downloadAll(DashModel dash, Context context) {
        List<String> allFiles = dash.getFilenameFunc.apply(dash.baseUrl);

        if (allFiles == null) {
            return null;
        }
        int last_n = 2;
        List<String> lastFiles = allFiles.subList(Math.max(allFiles.size(), 0) - last_n, allFiles.size());

        for (String filename : lastFiles) {
            downloadVideo(dash.videoDirUrl, FileManager.RAW_FOOTAGE_VIDEOS_PATH.getAbsolutePath(), filename, context);
        }
        Log.i(TAG, "All downloads complete");
        return lastFiles;
    }

    static void downloadVideo(String videoDirUrl, String downloadDir, String filename, Context context) {
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

    static List<String> getDrideFilenames(String url) {
        Document doc = null;

        try {
            doc = Jsoup.connect(url).get();
        } catch (SocketTimeoutException | ConnectException e) {
            Log.e(TAG, "Could not connect to dashcam");
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> allFiles = new ArrayList<>();

        for (Element file : doc.select("td:eq(0) > a")) {
            if (file.text().endsWith("MP4")) {
                allFiles.add(file.text());
            }
        }
        allFiles.sort(Comparator.comparing(String::toString));
        return allFiles;
    }

    static List<String> getBlackvueFilenames(String url) {
        Document doc = null;

        try {
            doc = Jsoup.connect(url + "blackvue_vod.cgi").get();
        } catch (SocketTimeoutException | ConnectException e) {
            Log.e(TAG, "Could not connect to dashcam");
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> allFiles = new ArrayList<>();

        String raw = doc.select("body").text();
        Pattern pat = Pattern.compile(Pattern.quote("Record/") + "(.*?)" + Pattern.quote(",s:"));
        Matcher match = pat.matcher(raw);

        while (match.find()) {
            allFiles.add(match.group(1));
        }

        allFiles.sort(Comparator.comparing(String::toString));
        return allFiles;
    }
}
