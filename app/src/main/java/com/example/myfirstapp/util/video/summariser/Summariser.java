package com.example.myfirstapp.util.video.summariser;

import android.util.Log;

import com.arthenica.mobileffmpeg.FFmpeg;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Pattern;

public class Summariser {

    private final String TAG = Summariser.class.getSimpleName();

    static final float DEFAULT_NOISE = 60;
    static final float DEFAULT_DURATION = 2;
    static final int DEFAULT_QUALITY = 23;
    static final Speed DEFAULT_SPEED = Speed.medium;

    private final String freezeFilePath = "/sdcard/Movies/rawFootage/freeze.txt";

    private final String filename;
    private final double noise;
    private final double duration;
    private final int quality;
    private final Speed speed;
    private final String outFile;

    public static Summariser createSummariser(String filename, double noise, double duration, int quality, Speed speed, String outFile) {
        return new Summariser(filename, noise, duration, quality, speed, outFile);
    }

    private Summariser(String filename, double noise, double duration, int quality, Speed speed, String outFile) {
        this.filename = filename;
        this.noise = noise;
        this.quality = quality;
        this.speed = speed;
        this.duration = duration;
        this.outFile = outFile;
    }

    /**
     * @return true if a summary video is created, false if no video is created
     */
    public boolean summarise() {
        ArrayList<Double[]> activeTimes = getActiveTimes();
        ArrayList<String> ffmpegArgs = new ArrayList<>();

        if (activeTimes == null) {
            // Testing purposes: Video file is completely active, so just copy it
            try {
                Log.i(TAG, "Whole video is active");
                Files.copy(new File(filename).toPath(), new File(sumFilename()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        ActivtySections activtySections;

        switch (activeTimes.size()) {
            case 0:
                // Video file is completely inactive, so ignore it, don't copy it
                Log.i(TAG, "No activity detected");
//                System.exit(0);
//                try {
//                    Files.copy(new File(filename).toPath(), new File(sumFilename()).toPath(),
//                            StandardCopyOption.REPLACE_EXISTING);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                activtySections = ActivtySections.NONE;
                return false;
            case 1:
                // One active scene found, extract that scene and copy it
                Double[] times = activeTimes.get(0);
                ffmpegArgs = getArgumentsOneScene(times[0], times[1]);
                Log.i(TAG, "One active section found");
                activtySections = ActivtySections.ONE;
                break;
            default:
                // Multiple active scenes found, extract them and combine them into a summarised video
                ffmpegArgs = getArgumentsMultipleScenes(activeTimes);
                Log.i(TAG, String.format("%d active sections found", activeTimes.size()));
                activtySections = ActivtySections.MANY;
        }
        if (activtySections != ActivtySections.NONE) {
            executeFfmpeg(ffmpegArgs);
        }
        return true;
    }

    private void executeFfmpeg(ArrayList<String> ffmpegArgs) {
        Log.i(TAG, String.format("Running ffmpeg with:\n  %s", String.join(" ", ffmpegArgs)));
        FFmpeg.execute(ffmpegArgs.stream().toArray(String[]::new));
    }

    private ArrayList<String> getArgumentsOneScene(Double start, Double end) {
        return new ArrayList<>(Arrays.asList(
                "-y", // Skip prompts
                "-ss", start.toString(),
                "-to", end.toString(),
                "-i", filename,
                "-c", "copy",
                sumFilename()
        ));
    }

    // https://superuser.com/a/1230097/911563
    private ArrayList<String> getArgumentsMultipleScenes(ArrayList<Double[]> activeTimes) {
        ArrayList<String> ffmpegArgs = new ArrayList<>(Arrays.asList(
                "-y", // Skip prompts
                "-i", filename,
                "-filter_complex"
        ));
        StringBuilder filter = new StringBuilder();

        for (int i = 0; i < activeTimes.size(); i++) {
            filter.append(String.format(
                    "[0:v]trim=%1$f:%2$f,setpts=PTS-STARTPTS[v%3$d];" +
                            "[0:a]atrim=%1$f:%2$f,asetpts=PTS-STARTPTS[a%3$d];",
                    activeTimes.get(i)[0], activeTimes.get(i)[1], i));
        }
        for (int i = 0; i < activeTimes.size(); i++) {
            filter.append(String.format("[v%1$d][a%1$d]", i));
        }
        filter.append(String.format("concat=n=%d:v=1:a=1[outv][outa]", activeTimes.size()));

        ffmpegArgs.addAll(new ArrayList<>(Arrays.asList(
                filter.toString(),
                "-map", "[outv]",
                "-map", "[outa]",
                "-crf", Integer.toString(quality), // Set quality
                "-preset", speed.name(), // Set speed
                sumFilename()
        )));

        return ffmpegArgs;
    }

    private ArrayList<Double[]> getActiveTimes() {
        detectFreeze();
        File freeze = new File(freezeFilePath);

        if (!freeze.exists() || freeze.length() == 0) {  // No inactive sections
            Log.i("No freeze", "freeze");
            return null;
        }
        Scanner sc = null;

        try {
            sc = new Scanner(freeze);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
        ArrayList<Double[]> activeTimes = new ArrayList<>();
        Pattern gotoEquals = Pattern.compile(".*=");

        sc.nextLine(); // Skip first line
        double start_time = sc.skip(gotoEquals).nextDouble();

        if (start_time != 0) { // Video starts active
            activeTimes.add(new Double[]{0.0, start_time});
        }
        while (sc.hasNextLine()) {
            String start_prefix = sc.findInLine("freeze_end");

            if (start_prefix != null) {
                Double[] times = new Double[2];
                sc.skip(gotoEquals);
                times[0] = sc.nextDouble();

                sc.nextLine(); // Go to next line

                if (sc.hasNextLine()) {
                    sc.nextLine(); // Skip line
                    times[1] = sc.skip(gotoEquals).nextDouble();
                    sc.nextLine();
                } else { // Active until end
                    times[1] = getVideoDuration();
                }

                if (times[0] < times[1]) { // Make sure start time is before end time
                    activeTimes.add(times);
                }
            } else {
                sc.nextLine();
            }
        }
        return activeTimes;
    }

    private void detectFreeze() {
        FFmpeg.execute(String.format("-i %s -vf %s -f null -",
                filename,
                String.format("freezedetect=n=-%fdB:d=%f,metadata=mode=print:file=%s", noise, duration, freezeFilePath))
        );
    }

    private Double getVideoDuration() {
        Long ms = FFmpeg.getMediaInformation(filename).getDuration();
        return ms / 1000.0;
    }

    private String sumFilename() {
        if (outFile == null) {
            return String.format("%s-sum.%s",
                    FilenameUtils.getBaseName(filename),
                    FilenameUtils.getExtension(filename));
        } else {
            return outFile;
        }
    }

    private enum ActivtySections {
        NONE, ONE, MANY;
    }
}


