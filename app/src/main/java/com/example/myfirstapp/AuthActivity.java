package com.example.myfirstapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobile.client.Callback;
import com.amazonaws.mobile.client.SignInUIOptions;
import com.amazonaws.mobile.client.UserStateDetails;

public class AuthActivity extends AppCompatActivity {

    // Based off https://aws.amazon.com/blogs/mobile/building-an-android-app-with-aws-amplify-part-1/.

    private final String TAG = AuthActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        // Initialise the instance. Implement callback that will called when there a result on initialisation or error.
        AWSMobileClient.getInstance().initialize(getApplicationContext(), new Callback<UserStateDetails>() {

            @Override
            public void onResult(UserStateDetails userStateDetails) {
                Log.i(TAG, userStateDetails.getUserState().toString());

                // Decide the flow based on the user state.
                switch (userStateDetails.getUserState()){
                    case SIGNED_IN:
                        // If user have successfully sign in, direct user to the MainActivity.
                        Intent i = new Intent(AuthActivity.this, MainActivity.class);
                        startActivity(i);
                        break;
                    case SIGNED_OUT:
                        // If the user have sign out, then show the sign in screen
                        showSignIn();
                        break;
                    default:
                        // Default case which could possibly mean error, we will sign the user out and show the sign in screen.
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
                    SignInUIOptions.builder().nextActivity(MainActivity.class).build());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

}
