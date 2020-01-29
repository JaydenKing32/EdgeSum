package com.example.edgesum.util.nearby;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.edgesum.R;

import java.util.List;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {
    private final List<Endpoint> endpoints;
    private final LayoutInflater inflater;
    private final DeviceCallback deviceCallback;

    DeviceListAdapter(Context context, List<Endpoint> endpoints, DeviceCallback deviceCallback) {
        this.inflater = LayoutInflater.from(context);
        this.endpoints = endpoints;
        this.deviceCallback = deviceCallback;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = inflater.inflate(R.layout.device_list_item, parent, false);
        return new DeviceViewHolder(itemView, this);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Endpoint current = endpoints.get(position);

        String deviceName = current.name;
        holder.deviceName.setText(deviceName);

        if (current.connected) {
            holder.connectionStatus.setImageResource(R.drawable.connected_status);
        } else {
            holder.connectionStatus.setImageResource(R.drawable.disconnected_status);
        }
    }

    @Override
    public int getItemCount() {
        return endpoints.size();
    }

    class DeviceViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final DeviceListAdapter adapter;
        final TextView deviceName;
        final ImageView connectionStatus;

        DeviceViewHolder(View itemView, DeviceListAdapter adapter) {
            super(itemView);
            this.deviceName = itemView.findViewById(R.id.device_item_text);
            this.connectionStatus = itemView.findViewById(R.id.connection_status);
            this.adapter = adapter;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            int pos = getLayoutPosition();
            Endpoint device = endpoints.get(pos);
            Log.d(DeviceViewHolder.class.getSimpleName(), String.format("Clicked on '%s'", device));
            deviceCallback.onDeviceSelection(device);
        }
    }
}
