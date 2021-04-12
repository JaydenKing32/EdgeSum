package com.example.edgesum.page.authentication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.SignInUIOptions;
import com.amazonaws.mobile.client.UserStateDetails;
import com.example.edgesum.R;
import com.example.edgesum.page.main.MainActivity;

public class AuthenticationActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    // Based off https://aws.amazon.com/blogs/mobile/building-an-android-app-with-aws-amplify-part-1/.

    private final String TAG = AuthenticationActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (checkPermissions()) {
            initialise();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        for (int grant : grantResults) {
            if (grant != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission denied");
                return;
            }
        }
        Log.i(TAG, "Permission granted");
        initialise();
    }

    private void initialise() {
        // Initialise the instance. Implement callback that will called when there a result on initialisation or error.
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {

            @Override
            public void onResult(UserStateDetails userStateDetails) {
                Log.i(TAG, userStateDetails.getUserState().toString());

                // Decide the flow based on the user state.
                switch (userStateDetails.getUserState()) {
                    case SIGNED_IN:
                        // If user have successfully sign in, direct user to the MainActivity.
                        Intent i = new Intent(AuthenticationActivity.this, MainActivity.class);
                        startActivity(i);
                        break;
                    case SIGNED_OUT:
                        // If the user have sign out, then show the sign in screen
                        showSignIn();
                        break;
                    default:
                        // Default case which could possibly mean error, we will sign the user out and show the sign
                        // in screen.
                        AWSMobileClient.getInstance().signOut();
                        showSignIn();
                        break;
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, e.toString());
            }
        });
    }

    private void showSignIn() {
        // Attempt to show the drop-in authentication screen that Amplify provides.
        try {
            AWSMobileClient.getInstance().showSignIn(this,
                    SignInUIOptions.builder()
                            .nextActivity(MainActivity.class)
                            .canCancel(true)
                            .build());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private boolean checkPermissions() {
        final int REQUEST_PERMISSIONS = 1;
        String[] PERMISSIONS = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE
        };

        if (!hasPermissions(PERMISSIONS)) {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
            return false;
        } else {
            return true;
        }
    }

    private boolean hasPermissions(String[] permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }
}