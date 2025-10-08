package com.example.potholeclickerclient;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.potholeclickerclient.ble.DeviceListAdapter; // We will create this next
import com.example.potholeclickerclient.ble.DeviceScanViewModel;
import com.example.potholeclickerclient.databinding.ActivityDeviceListBinding; // ViewBinding class

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class DeviceListActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 1001;
    public static final String EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME";
    public static final String EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS";

    private DeviceScanViewModel viewModel;
    private ActivityDeviceListBinding binding;
    private DeviceListAdapter deviceListAdapter;

    private String[] permissions = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_CONNECT,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(DeviceScanViewModel.class);

        setupRecyclerView();
        observeViewModelState();

        checkAndRequestPermissions();
    }

    private void startBleScan() {
        viewModel.startScan();
    }

    private void setupRecyclerView() {
        // The adapter needs a listener to handle clicks on list items
        deviceListAdapter = new DeviceListAdapter(node -> {
            // This code runs when a device is clicked
            Toast.makeText(this, "Selected: " + node.getFriendlyName(), Toast.LENGTH_SHORT).show();

            // Prepare the result to send back to MainActivity
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_DEVICE_NAME, node.getFriendlyName());
            resultIntent.putExtra(EXTRA_DEVICE_ADDRESS, node.getDevice().getAddress());
            setResult(RESULT_OK, resultIntent);

            // Close this activity and return to the previous one
            finish();
        });

        binding.rvDevices.setAdapter(deviceListAdapter);
    }

    private void observeViewModelState() {
        // THE SIMPLE, JAVA-FRIENDLY WAY:
        // Observe the loading state
        viewModel.isLoading().observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        // Observe the list of discovered devices
        viewModel.getScanBleDevices().observe(this, nodes -> {
            // The ListAdapter will handle the diffing and updating the UI
            deviceListAdapter.submitList(nodes);
        });
    }

    // ---- BLUETOOTH PERMISSIONS REQUEST ----
    private void debugPerms() {
        for (String p : permissions) {
            int res = ContextCompat.checkSelfPermission(this, p);
            Log.d("Perms", p + " = " + (res == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
        }
    }

    private boolean hasBluetoothPermissions() {
        Log.d("DeviceListActivity", "Checking permissions...");
        boolean hasAllPermission = true;
        for (String p : permissions) {
            if (ActivityCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                hasAllPermission = false;
                break;
            }
        }
        return hasAllPermission;
    }

    private void ensureBluetoothPermissions() {
        if (!hasBluetoothPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, 1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void checkAndRequestPermissions() {
        debugPerms();

        if(!hasBluetoothPermissions()) {
            Toast.makeText(this, "Location permission needed to save coordinates.", Toast.LENGTH_SHORT).show();
            ensureBluetoothPermissions();
            return;
        }

        Log.d("DeviceListActivity", "All permissions are already granted.");
        startBleScan();
    }
}
