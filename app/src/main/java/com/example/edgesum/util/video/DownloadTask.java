package com.example.edgesum.util.video;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;

import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.util.file.FileManager;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadTask extends AsyncTask<String, Void, List<String>> {
    private static final String TAG = DownloadTask.class.getSimpleName();
    private final WeakReference<Context> weakReference;

    public DownloadTask(Context context) {
        weakReference = new WeakReference<>(context);
    }

    @Override
    protected List<String> doInBackground(String... strings) {
        // Dride
        return downloadAll("http://192.168.1.254/DCIM/MOVIE/", "", this::getDrideFilenames);

        // BlackVue
//        return downloadAll("http://10.99.77.1/", "Record/", this::getBlackvueFilenames);
    }

    private List<String> downloadAll(String baseUrl, String upFolder, Function<String, List<String>> getFilenameFunc) {
        List<String> allFiles = getFilenameFunc.apply(baseUrl);

        if (allFiles == null) {
            return null;
        }
        int last_n = 2;
        List<String> lastFiles = allFiles.subList(Math.max(allFiles.size(), 0) - last_n, allFiles.size());

        for (String filename : lastFiles) {
            downloadVideo(baseUrl, upFolder, FileManager.RAW_FOOTAGE_VIDEOS_PATH.getAbsolutePath(), filename);
        }
        Log.i(TAG, "All downloads complete");
        return lastFiles;
    }

    private void downloadVideo(String baseUrl, String upFolder, String downFolder, String filename) {
        try {
            File videoFile = new File(String.format("%s/%s", downFolder, filename));
            Log.d(TAG, "Started downloading: " + filename);
            FileUtils.copyURLToFile(
                    new URL(baseUrl + upFolder + filename),
                    videoFile
            );
            Context context = weakReference.get();

            if (context != null) {
                // New files aren't immediately added to the MediaStore database, so it's necessary manually trigger it
                // Tried using sendBroadcast, but that doesn't guarantee that it will be immediately added.
                // MediaScannerConnection ensures that the new file is added to the database before it is queried
                // https://stackoverflow.com/a/5814533/8031185
                MediaScannerConnection.scanFile(context,
                        new String[]{videoFile.getAbsolutePath()}, null,
                        (path, uri) -> {
                            String[] projection = {MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DATA,
                                    MediaStore.Video.Media.DISPLAY_NAME,
                                    MediaStore.Video.Media.SIZE,
                                    MediaStore.Video.Media.MIME_TYPE
                            };
                            String selection = MediaStore.Video.Media.DATA + "=?";
                            String[] selectionArgs = new String[]{videoFile.getAbsolutePath()};
                            Cursor videoCursor = context.getContentResolver().query(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    projection, selection, selectionArgs, null);

                            videoCursor.moveToFirst();
                            Video video = VideoManager.videoFromCursor(videoCursor);
                            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                            videoCursor.close();
                            Log.d(TAG, "Finished downloading: " + filename);
                        });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getDrideFilenames(String url) {
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

    private List<String> getBlackvueFilenames(String url) {
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
