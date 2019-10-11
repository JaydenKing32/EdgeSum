package com.example.myfirstapp.util.video.summariser;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.arthenica.mobileffmpeg.FFmpeg;

import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Summariser {

    private final String TAG = Summariser.class.getSimpleName();

    private final String freezeFilePath = "/sdcard/Movies/rawFootage/freeze.txt";

    private final String filename;
    private final double noise;
    private final double duration;
    private final boolean verbose;
    private final String outFile;

    public static Summariser createSummariser(String filename, double noise, double duration, String outFile, boolean verbose) {
        return new Summariser(filename, noise, duration, outFile, verbose);
    }


    private Summariser(String filename, double noise, double duration, String outFile, boolean verbose) {
        this.filename = filename;
        this.noise = noise;
        this.duration = duration;
        this.outFile = outFile;
        this.verbose = verbose;
    }

    public void summarise() {
        ArrayList<Double[]> activeTimes = getActiveTimes();
        ArrayList<String> ffmpegArgs = new ArrayList<>();

        if (activeTimes == null) {
            // Testing purposes: Video file is completely active, so just copy it
            /*try {
                System.out.println("Whole video is active");
                Files.copy(new File(filename).toPath(), new File(sumFilename()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }*/
            return;
        }

        ActivtySections activtySections;

        switch (activeTimes.size()) {
            case 0:
                System.out.println("No activity detected");
//                System.exit(0);
                try {
                    Files.copy(new File(filename).toPath(), new File(sumFilename()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                activtySections = ActivtySections.NONE;
                break;
            case 1:
                Double[] times = activeTimes.get(0);
                ffmpegArgs = getArgumentsOneScene(times[0], times[1]);
                System.out.println("One active section found");
                activtySections = ActivtySections.ONE;
                break;
            default:
                ffmpegArgs = getArgumentsMultipleScenes(activeTimes);
                System.out.println(String.format("%d active sections found", activeTimes.size()));
                activtySections = ActivtySections.MANY;
        }
        if (activtySections != ActivtySections.NONE) {
            executeFfmpeg(ffmpegArgs);
        }
//        if (verbose) {
//            echoFfmpegOutput(runFfmpeg(ffmpegArgs));
//        } else {
//            getFfmpegOutput(runFfmpeg(ffmpegArgs));
//        }
    }

    private void executeFfmpeg(ArrayList<String> ffmpegArgs) {
        FFmpeg.execute(ffmpegArgs.stream().toArray(String[]::new));
    }

    private ArrayList<String> getArgumentsOneScene(Double start, Double end) {
        return new ArrayList<>(Arrays.asList(
//                "ffmpeg",
                "-y", // Skip prompts
                "-i", filename,
                "-ss", start.toString(),
                "-to", end.toString(),
                "-c", "copy",
                sumFilename()
        ));
    }

    // https://superuser.com/a/1230097/911563
    private ArrayList<String> getArgumentsMultipleScenes(ArrayList<Double[]> activeTimes) {
        ArrayList<String> ffmpegArgs = new ArrayList<>(Arrays.asList(
//                "ffmpeg",
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
                "-crf", "18", // Set quality
                sumFilename()
        )));

        return ffmpegArgs;
    }

    private ArrayList<Double[]> getActiveTimes() {
//        Process ffmpegProcess = runFfmpeg(new ArrayList<>(Arrays.asList(
//                "ffmpeg",
//                "-i", filename,
//                "-vf", String.format("freezedetect=n=-%fdB:d=%f,metadata=mode=print:file=freeze.txt", noise, duration),
//                "-f", "null", "-")));
        detectFreeze();
//        if (verbose) {
//            echoFfmpegOutput(ffmpegProcess);
//        } else {
//            getFfmpegOutput(ffmpegProcess);
//        }

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
                String.format("freezedetect=n=-%fdB:d=%f,metadata=mode=print:file=%s", noise, duration, freezeFilePath)));
    }

    private Process runFfmpeg(ArrayList<String> args) {
        ProcessBuilder build = new ProcessBuilder(args).redirectErrorStream(true);

        try {
            return build.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        return null;
    }

    // https://stackoverflow.com/a/13991171/8031185
    private void echoFfmpegOutput(Process process) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private ArrayList<String> getFfmpegOutput(Process process) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        ArrayList<String> output = new ArrayList<>();
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return output;
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


