package com.example.edgesum.page.connect;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.example.edgesum.R;


public class ConnectActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
    }

    public void startConnect(View view) {
        TextView status = findViewById(R.id.connection_status);
        status.setText("Connecting");
    }
}
