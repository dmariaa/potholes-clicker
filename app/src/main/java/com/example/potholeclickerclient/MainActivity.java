package com.example.potholeclickerclient;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    private MainViewModel viewModel;

    private TextView deviceName;
    private ImageView playButton;
    private TextView frameCounter;

    private TextView potholeCounter;
    private TextView speedbumpCounter;
    private TextView manholeCounter;
    private TextView otherCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        setContentView(R.layout.activity_main);

        setupViews();
        setupListeners();
        setupObservers();

        viewModel.onActivityCreated();
    }

    private void setupViews() {
        deviceName = findViewById(R.id.deviceName);
        playButton = findViewById(R.id.playButton);

        frameCounter = findViewById(R.id.frameCounter);
        potholeCounter = findViewById(R.id.pothole_counter);
        speedbumpCounter = findViewById(R.id.speedbump_counter); // Assuming you create these IDs
        manholeCounter = findViewById(R.id.manhole_counter);     // in your XML layout for the
        otherCounter = findViewById(R.id.other_counter);         // other buttons as well.

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupListeners() {
        findViewById(R.id.btnSettings).setOnClickListener(this::showPopupMenu);

        // View.OnClickListener l = this::onButtonClick;
        findViewById(R.id.btnPotHole).setOnClickListener(v -> viewModel.onEventButtonClick(EventType.POTHOLE));
        findViewById(R.id.btnSpeedBump).setOnClickListener(v -> viewModel.onEventButtonClick(EventType.SPEED_BUMP));
        findViewById(R.id.btnManHole).setOnClickListener(v -> viewModel.onEventButtonClick(EventType.MANHOLE));
        findViewById(R.id.btnOther).setOnClickListener(v-> viewModel.onEventButtonClick(EventType.OTHER));
        findViewById(R.id.playButton).setOnClickListener(v -> viewModel.onPlayStopClick());
    }

    private void setupObservers() {
        viewModel.getSessionModel().getPotholeCount().observe(this, count ->
                potholeCounter.setText(String.valueOf(count)));
        viewModel.getSessionModel().getSpeedBumpCount().observe(this, count ->
                speedbumpCounter.setText(String.valueOf(count)));
        viewModel.getSessionModel().getManholeCount().observe(this, count ->
                manholeCounter.setText(String.valueOf(count)));
        viewModel.getSessionModel().getOtherCount().observe(this, count ->
                otherCounter.setText(String.valueOf(count)));
        // viewModel.getDeviceFeaturesViewModel().getFrameCounter().observe(this, count -> frameCounter.setText(getResources().getString(R.string.frame_count, count)));
        viewModel.getSessionModel().getFrameCount().observe(this, count ->
                frameCounter.setText(getResources().getString(R.string.frame_count, count)));

        viewModel.getToastMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                viewModel.onToastShown(); // Reset the event
            }
        });

        viewModel.getLaunchCreateCsvFile().observe(this, shouldLaunch -> {
            if (shouldLaunch) {
                // createCsvLauncher.launch(viewModel.getFileDefaultName());
                createCsvLauncher.launch(null);
                viewModel.onCsvFileLauncherTriggered(); // Reset the event
            }
        });

        viewModel.getRequestLocationPermission().observe(this, shouldRequest -> {
            if(shouldRequest) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                viewModel.onLocationPermissionLauncherTriggered();
            }
        });

        viewModel.isPlayButtonEnabled().observe(this, isEnabled -> {
            if (!isEnabled) {
                this.playButton.setImageResource(R.drawable.play_disabled);
                this.playButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            }
        });

        viewModel.isTracking().observe(this, isTracking -> {
            if (isTracking) {
                this.playButton.setImageResource(R.drawable.stop); // You need to add this drawable
                this.playButton.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else {
                this.playButton.setImageResource(R.drawable.play_enabled);
                this.playButton.setColorFilter(ContextCompat.getColor(this, R.color.green));
            }
        });

        viewModel.getSelectedDeviceName().observe(this, deviceName -> {
            if(deviceName == null) return;
            this.showSensor(deviceName, true);
            Toast.makeText(this, "Device selected: " + deviceName, Toast.LENGTH_SHORT).show();
        });
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);
        popup.getMenuInflater().inflate(R.menu.settings_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if(itemId == R.id.choose_file){
                viewModel.requestNewCsvFile();
            } else if(itemId == R.id.select_device) {
                Intent intent = new Intent(this, DeviceListActivity.class);
                deviceListLauncher.launch(intent);
            }
            return true;
        });
        popup.show();
    }

    private void showSensor(String sensorName, boolean show) {
        if(show) {
            this.deviceName.setText(sensorName);
            this.playButton.setVisibility(View.VISIBLE);
            this.frameCounter.setVisibility(View.VISIBLE);
            this.frameCounter.setText(getResources().getString(R.string.frame_count, 0));
        } else {
            this.deviceName.setText("");
            this.playButton.setVisibility(View.INVISIBLE);
            this.frameCounter.setVisibility(View.INVISIBLE);
            this.frameCounter.setText(getResources().getString(R.string.frame_count, 0));
        }
    }

    // ===== Launchers =====
    private final ActivityResultLauncher<Uri> createCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                viewModel.onNewFolderSelected(uri);
            });

    private final ActivityResultLauncher<Intent> deviceListLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String deviceName = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_NAME);
                    String deviceAddress = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

                    if (deviceName != null && deviceAddress != null) {
                        viewModel.onDeviceSelected(deviceName, deviceAddress);
                    }
                }
            });

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Location permission granted.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
                }
            });
}
