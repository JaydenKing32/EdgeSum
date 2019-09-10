package com.example.myfirstapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.amazonaws.mobile.client.AWSMobileClient;

public class MainActivity extends AppCompatActivity {

    private RecyclerView videoRecyclerView;
    private VideoAdapter videoAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set the toobar as the app bar for this Activity.
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setUpRecyclerView();
    }

    private void setUpRecyclerView() {
        // Get handle to the RecyclerView in the layout.
        videoRecyclerView = findViewById(R.id.recyclerview);
        // Create adapter and set it as the adapter for the recycler view.
        videoAdapter = new VideoAdapter(this);
        videoRecyclerView.setAdapter(videoAdapter);
        // Set manager layout for the recycler view.
        videoRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // This method gets called when a option in the App bar gets selected.

        // Define logic on how to handle each option item selected.
        switch (item.getItemId()) {
            case R.id.action_settings:
                Log.i("Info", "Setting button clicked");

                // Create intent to go to SettingsActivity
                Intent settingsIntent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_logout:
                Log.i("Info", "Logout button clicked");
                AWSMobileClient.getInstance().signOut();
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


}
