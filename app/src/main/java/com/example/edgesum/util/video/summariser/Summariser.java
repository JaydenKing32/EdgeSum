package com.example.edgesum.util.video.summariser;

import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.example.edgesum.util.file.FileManager;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Pattern;

class Summariser {
    private final String TAG = Summariser.class.getSimpleName();
    static final float DEFAULT_NOISE = 60;
    static final float DEFAULT_DURATION = 2;
    static final int DEFAULT_QUALITY = 23;
    static final Speed DEFAULT_SPEED = Speed.medium;
    private final String freezeFilePath = String.format("%s/freeze.txt", FileManager.rawFootageFolderPath());

    private final String filename;
    private final double noise;
    private final double duration;
    private final int quality;
    private final Speed speed;
    private final String outFile;

    static Summariser createSummariser(String filename, double noise, double duration, int quality,
                                       Speed speed, String outFile) {
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
    boolean summarise() {
        Instant start = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            start = Instant.now();
        }

        // Don't suppress ffmpeg-mobile logs, seems to interfere with FFmpeg.getMediaInformation
        // Config.setLogLevel(Level.AV_LOG_WARNING);

        ArrayList<Double[]> activeTimes = getActiveTimes();
        ArrayList<String> ffmpegArgs = new ArrayList<>();

        if (activeTimes == null) {
            // Testing purposes: Video file is completely active, so just copy it
            try {
                Log.w(TAG, "Whole video is active");
                FileManager.copy(new File(filename), new File(sumFilename()));
                printResult(start);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        ActivitySections activitySections;

        switch (activeTimes.size()) {
            case 0:
                // Video file is completely inactive, so ignore it, don't copy it
                Log.w(TAG, "No activity detected");
//                System.exit(0);
//                try {
//                    Files.copy(new File(filename).toPath(), new File(sumFilename()).toPath(),
//                            StandardCopyOption.REPLACE_EXISTING);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                activitySections = ActivitySections.NONE;
                printResult(start);
                return false;
            case 1:
                // One active scene found, extract that scene and copy it
                Double[] times = activeTimes.get(0);
                ffmpegArgs = getArgumentsOneScene(times[0], times[1]);
                Log.w(TAG, "One active section found");
                activitySections = ActivitySections.ONE;
                break;
            default:
                // Multiple active scenes found, extract them and combine them into a summarised video
                ffmpegArgs = getArgumentsMultipleScenes(activeTimes);
                Log.w(TAG, String.format("%d active sections found", activeTimes.size()));
                activitySections = ActivitySections.MANY;
        }
        if (activitySections != ActivitySections.NONE) {
            executeFfmpeg(ffmpegArgs);
        }

        printResult(start);
        return true;
    }

    private void printResult(Instant start) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.w(TAG, String.format(
                    "Summarisation completed\n" +
                            "  filename: %s\n" +
                            "  time: %ss\n" +
                            "  noise tolerance: %.2f\n" +
                            "  quality: %d\n" +
                            "  speed: %s",
                    String.format("%s.%s", FilenameUtils.getBaseName(filename), FilenameUtils.getExtension(filename)),
                    DurationFormatUtils.formatDuration(Duration.between(start, Instant.now()).toMillis(), "ss.SSS"),
                    noise,
                    quality,
                    speed
            ));
        } else {
            Log.w(TAG, String.format(
                    "Summarisation completed\n" +
                            "  filename: %s\n" +
                            "  noise tolerance: %.2f\n" +
                            "  quality: %d\n" +
                            "  speed: %s",
                    String.format("%s.%s", FilenameUtils.getBaseName(filename), FilenameUtils.getExtension(filename)),
                    noise,
                    quality,
                    speed
            ));
        }
    }

    private void executeFfmpeg(ArrayList<String> ffmpegArgs) {
        Log.i(TAG, String.format("Running ffmpeg with:\n  %s", TextUtils.join(" ", ffmpegArgs)));
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
            Log.d(TAG, "No freeze file");
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
        MediaInformation info = FFmpeg.getMediaInformation(filename);

        if (info != null && info.getDuration() != null) {
            return info.getDuration() / 1000.0;
        } else {
            Log.e(TAG, String.format("ffmpeg-mobile error, could not retrieve duration of %s", filename));
            return 0.0;
        }
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

    private enum ActivitySections {
        NONE,
        ONE,
        MANY
    }
}
