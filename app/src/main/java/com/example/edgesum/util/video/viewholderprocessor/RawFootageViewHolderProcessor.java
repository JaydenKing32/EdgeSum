package com.example.edgesum.util.video.viewholderprocessor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.example.edgesum.R;
import com.example.edgesum.data.VideoViewModel;
import com.example.edgesum.event.AddEvent;
import com.example.edgesum.event.RemoveEvent;
import com.example.edgesum.event.Type;
import com.example.edgesum.model.Video;
import com.example.edgesum.page.main.VideoRecyclerViewAdapter;
import com.example.edgesum.util.file.FileManager;
import com.example.edgesum.util.network.FileTransferService;
import com.example.edgesum.util.video.summariser.SummariserIntentService;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

public class RawFootageViewHolderProcessor implements VideoViewHolderProcessor {

    @Override
    public void process(final Context context, final VideoViewModel vm,  final VideoRecyclerViewAdapter.VideoViewHolder viewHolder, final int position) {
        viewHolder.actionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Video video = viewHolder.video;
                final String path = video.getData();
                final File pathFile = new File(path);
                final String pathName = pathFile.getName();
                final String output =
                        String.format("%s/%s", FileManager.summarisedVideosFolderPath(), pathName);



//                Intent summariseIntent = new Intent(context, SummariserIntentService.class);
//                summariseIntent.putExtra("video", video);
//                summariseIntent.putExtra("outputPath", output);
//                context.getApplicationContext().startService(summariseIntent);
//                EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
//                EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));

//                Intent serviceIntent = new Intent(context, FileTransferService.class);
//                serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
//                serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, pathFile.toURI().toString());
//                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
//                        info.groupOwnerAddress.getHostAddress());
//                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, WIFI_P2P_PORT);
//                context.startService(serviceIntent);




//                Toast.makeText(context, "Add to processing queue", Toast.LENGTH_SHORT).show();

                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
                String test = pref.getString(context.getString(R.string.group_owner_ip_key), "0");
                Toast.makeText(context, test, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
