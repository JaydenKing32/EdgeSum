package com.example.edgesum.util.dashcam;

import java.util.List;
import java.util.function.Supplier;

class DashModel {
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
}
