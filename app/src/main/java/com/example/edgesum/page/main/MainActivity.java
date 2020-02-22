package com.example.edgesum.page.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.example.edgesum.R;
import com.example.edgesum.data.VideosRepository;
import com.example.edgesum.model.Video;
import com.example.edgesum.page.authentication.AuthenticationActivity;
import com.example.edgesum.page.setting.SettingsActivity;
import com.example.edgesum.util.Injection;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.NearbyFragment;
import com.example.edgesum.util.dashcam.DownloadTask;
import com.example.edgesum.util.video.summariser.SummariserIntentService;
import com.example.edgesum.util.video.videoeventhandler.ProcessingVideosEventHandler;
import com.example.edgesum.util.video.videoeventhandler.RawFootageEventHandler;
import com.example.edgesum.util.video.videoeventhandler.SummarisedVideosEventHandler;
import com.example.edgesum.util.video.viewholderprocessor.ProcessingVideosViewHolderProcessor;
import com.example.edgesum.util.video.viewholderprocessor.RawFootageViewHolderProcessor;
import com.example.edgesum.util.video.viewholderprocessor.SummarisedVideosViewHolderProcessor;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;

public class MainActivity extends AppCompatActivity implements VideoFragment.OnListFragmentInteractionListener,
        NearbyFragment.OnFragmentInteractionListener {
    private final String TAG = MainActivity.class.getSimpleName();

    VideoFragment rawFootageFragment;
    VideoFragment processingFragment;
    VideoFragment summarisedVideoFragment;
    ConnectionFragment connectionFragment;

    final FragmentManager supportFragmentManager = getSupportFragmentManager();
    Fragment activeFragment;


    private BottomNavigationView.OnNavigationItemSelectedListener bottomNavigationOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_raw_footage:
                    showNewFragmentAndHideOldFragment(rawFootageFragment);
                    return true;
                case R.id.navigation_processing:
                    showNewFragmentAndHideOldFragment(processingFragment);
                    return true;
                case R.id.navigation_summarised_videos:
                    showNewFragmentAndHideOldFragment(summarisedVideoFragment);
                    return true;
            }
            return false;
        }
    };

    private void showNewFragmentAndHideOldFragment(Fragment newFragment) {
        supportFragmentManager.beginTransaction().hide(activeFragment).show(newFragment).commit();
        setActiveFragment(newFragment);
    }

    private void setActiveFragment(Fragment newFragment) {
        activeFragment = newFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        startAwsS3TransferService();

        // Set the toolbar as the app bar for this Activity.
        setToolBarAsTheAppBar();

        setUpBottomNavigation();

        setUpFragments();

        File externalStoragePublicMovieDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        makeRawFootageDirectory(externalStoragePublicMovieDirectory);
        makeSummarisedVideosDirectory(externalStoragePublicMovieDirectory);
        checkPermissions();
    }

    private void startAwsS3TransferService() {
        getApplicationContext().startService(new Intent(getApplicationContext(), TransferService.class));
    }

    private void makeRawFootageDirectory(File path) {
        final String rawFootageDirectoryName = "rawFootage/";
        FileManager.makeDirectory(this.getApplicationContext(), path, rawFootageDirectoryName);
    }

    private void makeSummarisedVideosDirectory(File path) {
        final String rawFootageDirectoryName = "summarised/";
        FileManager.makeDirectory(this.getApplicationContext(), path, rawFootageDirectoryName);
    }


    private void setToolBarAsTheAppBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }


    private void setUpFragments() {
        VideosRepository rawFootageRepository = Injection.getExternalVideoRepository(this, "",
                FileManager.RAW_FOOTAGE_VIDEOS_PATH.getAbsolutePath());
        VideosRepository processingVideosRepository = Injection.getProcessingVideosRespository(this);
        VideosRepository summarisedVideosRepository = Injection.getExternalVideoRepository(this, "",
                FileManager.SUMMARISED_VIDEOS_PATH.getAbsolutePath());

        connectionFragment = ConnectionFragment.newInstance();
        SummariserIntentService.transferCallback = connectionFragment;
        rawFootageFragment = VideoFragment.newInstance(1, new RawFootageViewHolderProcessor(connectionFragment),
                ActionButton.ADD, new RawFootageEventHandler(rawFootageRepository), connectionFragment);
        processingFragment = VideoFragment.newInstance(1, new ProcessingVideosViewHolderProcessor(),
                ActionButton.REMOVE, new ProcessingVideosEventHandler(processingVideosRepository), connectionFragment);
        summarisedVideoFragment = VideoFragment.newInstance(1, new SummarisedVideosViewHolderProcessor(),
                ActionButton.UPLOAD, new SummarisedVideosEventHandler(summarisedVideosRepository), connectionFragment);

        supportFragmentManager.beginTransaction().add(R.id.main_container, connectionFragment, "4").hide(connectionFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, summarisedVideoFragment, "3").hide(summarisedVideoFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, processingFragment, "2").hide(processingFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, rawFootageFragment, "1").commit();

        rawFootageFragment.setRepository(rawFootageRepository);
        processingFragment.setRepository(processingVideosRepository);
        summarisedVideoFragment.setRepository(summarisedVideosRepository);

        setActiveFragment(rawFootageFragment);
    }

    private void setUpBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.navigation);
        bottomNavigation.setOnNavigationItemSelectedListener(bottomNavigationOnNavigationItemSelectedListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // This method gets called when a option in the App bar gets selected.

        // Define logic on how to handle each option item selected.
        switch (item.getItemId()) {
            case R.id.action_connect:
                Log.i(TAG, "Connect button clicked");
                showNewFragmentAndHideOldFragment(connectionFragment);
                return true;
            case R.id.action_download:
                Log.i(TAG, "Download button clicked");
                DownloadTask mDownloadTask = new DownloadTask(this);
                mDownloadTask.execute();
                return true;
            case R.id.action_settings:
                Log.i(TAG, "Setting button clicked");
                goToSettingsActivity();
                return true;
            case R.id.action_logout:
                Log.i(TAG, "Logout button clicked");
                signOut();
                Intent i = new Intent(MainActivity.this, AuthenticationActivity.class);
                startActivity(i);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void signOut() {
        AWSMobileClient.getInstance().signOut();
    }

    private void goToSettingsActivity() {
        Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
        startActivity(settingsIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public void onListFragmentInteraction(Video item) {

    }

    private void checkPermissions() {
        final int REQUEST_PERMISSIONS = 1;
        String[] PERMISSIONS = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        if (hasPermissions()) {
            requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS);
        }
    }

    private boolean hasPermissions(String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onFragmentInteraction(String name) {

    }
}
