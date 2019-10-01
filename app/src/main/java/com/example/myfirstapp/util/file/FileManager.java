package com.example.myfirstapp.util.file;

import android.os.Environment;
import android.util.Log;

import com.example.myfirstapp.util.devicestorage.DeviceExternalStorage;

import java.io.File;

public class FileManager {

    private FileManager() {

    }



    public static void makeDirectory(File path, String directoryName) {
        File newDirectory = new File(path, directoryName);
        if (DeviceExternalStorage.externalStorageIsWritable()) {
            Log.i("External storage", "Is readable");
            try {
                if (!newDirectory.exists()) {
                    boolean folderCreated = newDirectory.mkdirs();
                    Log.i("Folder created", Boolean.toString(folderCreated));
                }
            } catch (SecurityException e) {
                Log.i("Security exception", e.getMessage());
            }
        } else {
            Log.i("External storage", "Not readable");
        }
    }
}
