// In C:/Users/david.maria/Documents/python-projects/potholes-clicker/app/src/main/java/com/example/potholeclickerclient/ble/DeviceListAdapter.java

package com.example.potholeclickerclient.ble;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.potholeclickerclient.databinding.ItemDeviceBinding; // Auto-generated from item_device.xml
import com.st.blue_sdk.models.Node;

public class DeviceListAdapter extends ListAdapter<Node, DeviceListAdapter.DeviceViewHolder> {

    // 1. Interface for handling clicks
    public interface OnDeviceClickListener {
        void onDeviceClick(Node node);
    }

    private final OnDeviceClickListener clickListener;

    // 2. Constructor: Takes a listener to handle item clicks
    public DeviceListAdapter(@NonNull OnDeviceClickListener clickListener) {
        super(NODE_DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    // 3. Creates the ViewHolder by inflating the item_device.xml layout
    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use ViewBinding to inflate the layout
        ItemDeviceBinding binding = ItemDeviceBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new DeviceViewHolder(binding);
    }

    // 4. Binds data from a Node object to the views in the ViewHolder
    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Node currentNode = getItem(position);
        holder.bind(currentNode, clickListener);
    }

    // 5. The ViewHolder class that holds the views for a single item
    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final ItemDeviceBinding binding; // ViewBinding for item_device.xml

        public DeviceViewHolder(ItemDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Node node, OnDeviceClickListener clickListener) {
            // Set the text for the device name and address
            binding.tvDeviceName.setText(node.getFriendlyName());
            binding.tvDeviceAddress.setText(node.getDevice().getAddress());

            // Set the click listener on the root view of the item
            itemView.setOnClickListener(v -> clickListener.onDeviceClick(node));
        }
    }

    // 6. DiffUtil.ItemCallback: Helps the ListAdapter efficiently update the list
    private static final DiffUtil.ItemCallback<Node> NODE_DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Node>() {
                @Override
                public boolean areItemsTheSame(@NonNull Node oldItem, @NonNull Node newItem) {
                    // Devices are the same if their addresses match
                    return oldItem.getDevice().getAddress().equals(newItem.getDevice().getAddress());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Node oldItem, @NonNull Node newItem) {
                    // Check if the content (e.g., name) has changed
                    return oldItem.getDevice().getAddress().equals(newItem.getDevice().getAddress());
                }
            };
}

