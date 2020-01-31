package com.example.edgesum.page.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.page.authentication.AuthenticationActivity;
import com.example.edgesum.page.setting.SettingsActivity;
import com.example.edgesum.util.Injection;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.nearby.NearbyFragment;
import com.example.edgesum.util.video.VideoManager;
import com.example.edgesum.util.video.summariser.SummariserIntentService;
import com.example.edgesum.util.video.videoeventhandler.ProcessingVideosEventHandler;
import com.example.edgesum.util.video.videoeventhandler.RawFootageEventHandler;
import com.example.edgesum.util.video.videoeventhandler.SummarisedVideosEventHandler;
import com.example.edgesum.util.video.viewholderprocessor.ProcessingVideosViewHolderProcessor;
import com.example.edgesum.util.video.viewholderprocessor.RawFootageViewHolderProcessor;
import com.example.edgesum.util.video.viewholderprocessor.SummarisedVideosViewHolderProcessor;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                ActionButton.ADD, new RawFootageEventHandler(rawFootageRepository));
        processingFragment = VideoFragment.newInstance(1, new ProcessingVideosViewHolderProcessor(),
                ActionButton.REMOVE, new ProcessingVideosEventHandler(processingVideosRepository));
        summarisedVideoFragment = VideoFragment.newInstance(1, new SummarisedVideosViewHolderProcessor(),
                ActionButton.UPLOAD, new SummarisedVideosEventHandler(summarisedVideosRepository));

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
                DownloadTask mDownloadTask = new DownloadTask();
                mDownloadTask.execute("http://192.168.1.254/DCIM/MOVIE/");
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

    // Need AsyncTask to perform network operations as they are not permitted in the main thread
    @SuppressLint("StaticFieldLeak")
    public class DownloadTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... strings) {
            // Dride
            return downloadAll("http://192.168.1.254/DCIM/MOVIE/", "", this::getDrideFilenames);

            // BlackVue
//            return downloadAll("http://10.99.77.1/", "Record/", this::getBlackvueFilenames);
        }

        private List<String> downloadAll(String baseUrl, String upFolder,
                                         Function<String, List<String>> getFilenameFunc) {
            List<String> allFiles = getFilenameFunc.apply(baseUrl);

            if (allFiles == null) {
                return null;
            }
            int last_n = 2;
            List<String> lastFiles = allFiles.subList(Math.max(allFiles.size(), 0) - last_n, allFiles.size());
            String rawFootagePath = Environment.getExternalStorageDirectory().getPath() + "/Movies/rawFootage/";

            for (String filename : lastFiles) {
                downloadVideo(baseUrl, upFolder, rawFootagePath, filename);
            }
            Log.i(TAG, "All downloads complete");
            return lastFiles;
        }

        private void downloadVideo(String baseUrl, String upFolder, String downFolder, String filename) {
            try {
                File videoFile = new File(downFolder + filename);
                Log.d(TAG, "Started downloading: " + filename);
                FileUtils.copyURLToFile(
                        new URL(baseUrl + upFolder + filename),
                        videoFile
                );
                /*
                New files aren't immediately added to the MediaStore database, so it's necessary manually trigger it
                Tried using sendBroadcast, but that doesn't guarantee that it will be immediately added.
                Using MediaScannerConnection does ensure that the new file is added to the database before it is queried
                 */
                // https://stackoverflow.com/a/5814533/8031185
                MediaScannerConnection.scanFile(MainActivity.this,
                        new String[]{videoFile.getAbsolutePath()}, null,
                        (path, uri) -> {
                            String[] projection = {MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DATA,
                                    MediaStore.Video.Media.DISPLAY_NAME,
                                    MediaStore.Video.Media.SIZE,
                                    MediaStore.Video.Media.MIME_TYPE
                            };
                            String selection = MediaStore.Video.Media.DATA + "=?";
                            String[] selectionArgs = new String[]{videoFile.getAbsolutePath()};
                            Cursor videoCursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    projection, selection, selectionArgs, null);

                            assert videoCursor != null;
                            videoCursor.moveToFirst();
                            Video video = VideoManager.videoFromCursor(videoCursor);
                            EventBus.getDefault().post(new AddEvent(video, Type.RAW));
                            videoCursor.close();
                            Log.d(TAG, "Finished downloading: " + filename);
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private List<String> getDrideFilenames(String url) {
            Document doc = null;

            try {
                doc = Jsoup.connect(url).get();
            } catch (SocketTimeoutException | ConnectException e) {
                Log.e(TAG, "Could not connect to dashcam");
                return null;
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
            return allFiles;
        }

        private List<String> getBlackvueFilenames(String url) {
            Document doc = null;

            try {
                doc = Jsoup.connect(url + "blackvue_vod.cgi").get();
            } catch (SocketTimeoutException | ConnectException e) {
                Log.e(TAG, "Could not connect to dashcam");
                return null;
            } catch (IOException e) {
                e.printStackTrace();
            }
            List<String> allFiles = new ArrayList<>();
            assert doc != null;

            String raw = doc.select("body").text();
            Pattern pat = Pattern.compile(Pattern.quote("Record/") + "(.*?)" + Pattern.quote(",s:"));
            Matcher match = pat.matcher(raw);

            while (match.find()) {
                allFiles.add(match.group(1));
            }

            allFiles.sort(Comparator.comparing(String::toString));
            return allFiles;
        }
    }
}
