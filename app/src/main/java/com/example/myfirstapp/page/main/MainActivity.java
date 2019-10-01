package com.example.myfirstapp.page.main;

import android.Manifest;
import android.content.Intent;
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
import com.example.myfirstapp.R;
import com.example.myfirstapp.data.VideosRepository;
import com.example.myfirstapp.model.Video;
import com.example.myfirstapp.page.authentication.AuthenticationActivity;
import com.example.myfirstapp.page.setting.SettingsActivity;
import com.example.myfirstapp.util.Injection;
import com.example.myfirstapp.util.file.FileManager;
import com.example.myfirstapp.util.permissions.PermissionsManager;
import com.example.myfirstapp.util.video.viewholderprocessor.NullVideoViewHolderProcess;
import com.example.myfirstapp.util.video.viewholderprocessor.ProcessingVideosViewHolderProcessor;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;

public class MainActivity
        extends AppCompatActivity
        implements
        VideoFragment.OnListFragmentInteractionListener {

    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 2;

    final VideoFragment rawFootageFragment = VideoFragment.newInstance(1, new NullVideoViewHolderProcess(), ActionButton.ADD);
    final VideoFragment processingFragment = VideoFragment.newInstance(1, new ProcessingVideosViewHolderProcessor(), ActionButton.REMOVE);
    final VideoFragment summarisedVideoFragment = VideoFragment.newInstance(1, new NullVideoViewHolderProcess(), ActionButton.UPLOAD);

    final FragmentManager supportFragmentManager = getSupportFragmentManager();
    Fragment activeFragment = rawFootageFragment;


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

        requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
        startAwsS3TransferService();


        // Set the toolbar as the app bar for this Activity.
        setUpAppBar();

        setUpBottomNavigation();

        setUpFragments();





        File externalStoragePublicMovieDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        makeRawFootageDirectory(externalStoragePublicMovieDirectory);

    }

    private void startAwsS3TransferService() {
        getApplicationContext().startService(new Intent(getApplicationContext(), TransferService.class));
    }

    private void makeRawFootageDirectory(File path) {
        final String rawFootageDirectoryName = "rawFootage/";
        FileManager.makeDirectory(path, rawFootageDirectoryName);
    }


    private void setUpAppBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void requestPermission(String permission, int permissionCode) {
        if (!PermissionsManager.checkIfContextHavePermission(this, permission)) {
            final String[] permissionToRequest = {permission};
            PermissionsManager.requestPermissionForActivity(this, permissionToRequest, permissionCode);
        }
    }


    private void setUpFragments() {
        supportFragmentManager.beginTransaction().add(R.id.main_container, summarisedVideoFragment, "3").hide(summarisedVideoFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, processingFragment, "2").hide(processingFragment).commit();
        supportFragmentManager.beginTransaction().add(R.id.main_container, rawFootageFragment, "1").commit();

        VideosRepository rawFootageRepository = Injection.getExternalVideoRepository(this, "", "");
        VideosRepository processingVideosRepository = Injection.getProcessingVideosRespository(this);
        VideosRepository summarisedVideosRepository = Injection.getExternalVideoRepository(this, "", "");

        rawFootageFragment.setRepository(rawFootageRepository);
        processingFragment.setRepository(processingVideosRepository);
        summarisedVideoFragment.setRepository(summarisedVideosRepository);
    }

    private void setUpBottomNavigation() {
        BottomNavigationView bottomNavigation = (BottomNavigationView) findViewById(R.id.navigation);
        bottomNavigation.setOnNavigationItemSelectedListener(bottomNavigationOnNavigationItemSelectedListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // This method gets called when a option in the App bar gets selected.

        // Define logic on how to handle each option item selected.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Log.i("Info", "Setting button clicked");
                goToSettingsActivity();
                return true;
            case R.id.action_logout:
                Log.i("Info", "Logout button clicked");
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
}
