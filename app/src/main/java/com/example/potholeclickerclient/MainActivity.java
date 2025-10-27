package com.example.potholeclickerclient;

import android.content.Intent;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.potholeclickerclient.tools.CsvManager;
import com.example.potholeclickerclient.tools.LocationManager;

import javax.inject.Inject;
import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    // Manager classes
    @Inject protected LocationManager locationManager;
    @Inject protected CsvManager csvManager;

    private TextView deviceName;
    private ImageView playButton;
    private TextView frameCounter;

    private TextView potholeCounter;
    private TextView speedbumpCounter;
    private TextView manholeCounter;
    private TextView otherCounter;

    // State
    private boolean isTracking = false;
    private int potHoleCount = 0;
    private int speedBumpCount = 0;
    private int manHoleCount = 0;
    private int otherCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        setupViews();
        setupListeners();

        locationManager.requestLocationPermission();
        if(!csvManager.isCsvFileChosen())
        {
            csvManager.createNewCsvFile(createCsvLauncher);
        }
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

        View.OnClickListener l = this::onButtonClick;
        findViewById(R.id.btnPotHole).setOnClickListener(l);
        findViewById(R.id.btnSpeedBump).setOnClickListener(l);
        findViewById(R.id.btnManHole).setOnClickListener(l);
        findViewById(R.id.btnOther).setOnClickListener(l);

        playButton.setOnClickListener(v -> {
            if (isTracking) {

            } else {

            }
        });
    }

    private void showPopupMenu(View v) {
        PopupMenu popup = new PopupMenu(MainActivity.this, v);
        popup.getMenuInflater().inflate(R.menu.settings_popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if(itemId == R.id.choose_file){
                csvManager.createNewCsvFile(createCsvLauncher);
            } else if(itemId == R.id.select_device) {
                Intent intent = new Intent(this, DeviceListActivity.class);
                deviceListLauncher.launch(intent);
            }
            return true;
        });
        popup.show();
    }

    private void onButtonClick(View v) {
        long ts = System.currentTimeMillis();

        String type;
        int id = v.getId();
        if (id == R.id.btnPotHole) {
            type = "pothole";
            potHoleCount++;
            potholeCounter.setText(String.valueOf(potHoleCount));
        } else if (id == R.id.btnSpeedBump) {
            type = "speed_bump";
            speedBumpCount++;
            speedbumpCounter.setText(String.valueOf(speedBumpCount));
        } else if (id == R.id.btnManHole) {
            type = "manhole";
            manHoleCount++;
            manholeCounter.setText(String.valueOf(manHoleCount));
        } else if (id == R.id.btnOther) {
            type = "other";
            otherCount++;
            otherCounter.setText(String.valueOf(otherCount));
        }
        else return;

        locationManager.getCurrentLocation(location -> {
            Double lat = location != null ? location.getLatitude() : null;
            Double lon = location != null ? location.getLongitude() : null;
            csvManager.appendEvent(type, ts, lat, lon);

            if (location == null) {
                Toast.makeText(this, "Location unavailable; saved without coords.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===== Launchers =====
    private final ActivityResultLauncher<String> createCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                csvManager.handleCreateCsvResult(uri);
            });

    private final ActivityResultLauncher<Intent> deviceListLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String deviceName = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_NAME);
                    String deviceAddress = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

                    if (deviceName != null && deviceAddress != null) {
                        Toast.makeText(this, "Device selected: " + deviceName, Toast.LENGTH_SHORT).show();
                        this.deviceName.setText(deviceName);
                        this.playButton.setVisibility(View.VISIBLE);
                        this.frameCounter.setVisibility(View.VISIBLE);
                        this.frameCounter.setText("0 frames");
                    }
                }
            });
}
