package com.example.edgesum.util.dashcam;

import android.content.Context;

import java.util.List;
import java.util.function.Supplier;

class DashModel {
    static final String drideBaseUrl = "http://192.168.1.254/DCIM/MOVIE/";
    static final String blackvueBaseUrl = "http://10.99.77.1/";
    static final String blackvueVideoUrl = blackvueBaseUrl + "Record/";

    private final DashName dashName;
    private final String baseUrl;
    private final String videoDirUrl;
    private final Supplier<List<String>> getFilenameFunc;

    private DashModel(DashName dashName, String baseUrl, String videoDirUrl, Supplier<List<String>> getFilenameFunc) {
        this.dashName = dashName;
        this.baseUrl = baseUrl;
        this.videoDirUrl = videoDirUrl;
        this.getFilenameFunc = getFilenameFunc;
    }

    static DashModel dride() {
        return new DashModel(DashName.DRIDE, drideBaseUrl, drideBaseUrl, DashTools::getDrideFilenames);
    }

    static DashModel blackvue() {
        return new DashModel(DashName.BLACKVUE, blackvueBaseUrl, blackvueVideoUrl, DashTools::getBlackvueFilenames);
    }

    List<String> downloadAll(DashDownloadManager downloadManager, Context context) {
        List<String> allFiles = getFilenameFunc.get();

        if (allFiles == null) {
            return null;
        }
        int last_n = 2;
        List<String> lastFiles = allFiles.subList(Math.max(allFiles.size(), 0) - last_n, allFiles.size());

        for (String filename : lastFiles) {
            downloadVideo(filename, downloadManager, context);
        }
        return lastFiles;
    }

    void downloadVideo(String filename, DashDownloadManager downloadManager, Context context) {
        downloadManager.startDownload(String.format("%s%s", videoDirUrl, filename), context);
    }

    List<String> getFilenames() {
        return getFilenameFunc.get();
    }
}
