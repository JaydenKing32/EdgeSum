package com.example.edgesum.page.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.edgesum.R;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.permissions.PermissionsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LoadingActivity extends AppCompatActivity {
    private static String TAG = LoadingActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 2;
    private static final int MY_PERMISSIONS_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);
        MediaScannerConnection.scanFile(this, new String[]{Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            public void onScanCompleted(String path, Uri uri) {
                Log.i(TAG, "Scanned " + path + ":");
                Log.i(TAG, "-> uri=" + uri);
            }
        });

        MediaScannerConnection.scanFile(this, new String[]{FileManager.summarisedVideosFolderPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            public void onScanCompleted(String path, Uri uri) {
                Log.i(TAG, "Scanned " + path + ":");
                Log.i(TAG, "-> uri=" + uri);
            }
        });
        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        boolean requireToAskForPermissions = false;
        List<String> requiredPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (!PermissionsManager.checkIfContextHavePermission(this, permission)) {
                requireToAskForPermissions = true;
                requiredPermissions.add(permission);
            }
        }
        if (requireToAskForPermissions)
            requestActivityPermissions(requiredPermissions.toArray(new String[requiredPermissions.size()]),
                    MY_PERMISSIONS_REQUEST);
        else
            moveToMainActivity();
    }

    private void moveToMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, Integer.toString(requestCode));
        Log.i(TAG, Arrays.toString(permissions));
        Log.i(TAG, Arrays.toString(grantResults));
        switch (requestCode) {
            case 100: {
                boolean permissionAllowed = true;
                for (int permissionCode : grantResults) {
                    if (permissionCode != PackageManager.PERMISSION_GRANTED)
                        permissionAllowed = false;
                }
                if (permissionAllowed) {
                    //If user presses allow
                    Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
                    moveToMainActivity();
                } else {
                    //If user presses deny
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void requestActivityPermissions(String[] permissions, int permissionCode) {

        PermissionsManager.requestPermissionForActivity(this,
                permissions,
                permissionCode);
    }
}
