package com.example.myfirstapp.util.video;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VidDownloading {
    public static List<String> getLastVideos() {
        String baseUrl = "http://192.168.1.254/DCIM/MOVIE/";
        Document doc = null;

        try {
            doc = Jsoup.connect(baseUrl).get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<String> allFiles = new ArrayList<>();
        assert doc != null;

        for (Element file : doc.select("td:eq(0) > a")) {
            if (file.text().endsWith("MP4")) {
                allFiles.add(file.text());
            }
        }
        allFiles.sort(Comparator.comparing(String::toString));
        int last_n = 2;
        List<String> lastFiles = allFiles.subList(Math.max(allFiles.size(), 0) - last_n, allFiles.size());

        for (String filename : lastFiles) {
            try {
                FileUtils.copyURLToFile(
                        new URL(baseUrl + filename),
                        new File("/sdcard/Movies/rawFootage/" + filename)
                );
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return lastFiles;
    }
}
