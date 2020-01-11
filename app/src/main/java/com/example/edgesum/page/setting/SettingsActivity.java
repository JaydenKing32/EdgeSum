package com.example.edgesum.page.setting;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toolbar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.example.edgesum.R;
import com.example.edgesum.page.viewuploadedvideos.ViewUploadedVideosActivity;

public class SettingsActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.setDisplayHomeAsUpEnabled(true);
//        }

        Toolbar toolbar = findViewById(R.id.wifi_toolbar);
    }


    public static class SettingsFragment extends PreferenceFragmentCompat {
        final String TAG = SettingsActivity.class.getSimpleName();

        Context context;

        private final String viewSummarisedVideo = "view_summarised_video";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            Preference videoSummarisedVideoPreference = findPreference(viewSummarisedVideo);
//            Log.i(TAG, videoSummarisedVideoPreference.getKey() + "");

            context = getActivity().getApplicationContext();
            videoSummarisedVideoPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Log.i(TAG, "View summarised videos");
                    Intent i = new Intent(getContext(), ViewUploadedVideosActivity.class);
                    startActivity(i);
                    return false;
                }
            });

        }
    }
}