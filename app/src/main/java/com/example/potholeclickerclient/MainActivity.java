package com.example.potholeclickerclient;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 1001;
    private FusedLocationProviderClient fused;

    // ===== CSV (SAF) state =====
    private static final String PREFS = "csv_prefs";
    private static final String KEY_CSV_URI = "csv_uri";
    private static final String FILE_DEFAULT_NAME = "logfile.csv";
    private @Nullable Uri csvUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        fused = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        View.OnClickListener l = this::onButtonClick;
        findViewById(R.id.btnPotHole).setOnClickListener(l);
        findViewById(R.id.btnSpeedBump).setOnClickListener(l);
        findViewById(R.id.btnManHole).setOnClickListener(l);
        findViewById(R.id.btnOther).setOnClickListener(l);

        // Let user pick/create CSV (ensure your layout has a button with this id)
        findViewById(R.id.btnChooseFile).setOnClickListener(v -> createCsvLauncher.launch(FILE_DEFAULT_NAME));

        ensureLocationPermission();
        ensureCsvChosen();
    }

    private void onButtonClick(View v) {
        String type;
        int id = v.getId();
        if (id == R.id.btnPotHole) type = "pothole";
        else if (id == R.id.btnSpeedBump) type = "speed_bump";
        else if (id == R.id.btnManHole) type = "manhole";
        else if (id == R.id.btnOther) type = "other";
        else return;

        long ts = System.currentTimeMillis();
        captureLocationAndSave(type, ts);
    }

    @SuppressLint("MissingPermission")      // Permissions checked with hasLocationPermission
    private void captureLocationAndSave(String type, long ts) {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission needed to save coordinates.", Toast.LENGTH_SHORT).show();
            ensureLocationPermission();
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(loc -> {
                    if (loc != null) {
                        saveEventToCsv(type, ts, loc.getLatitude(), loc.getLongitude());
                    } else {
                        fused.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) {
                                saveEventToCsv(type, ts, last.getLatitude(), last.getLongitude());
                            } else {
                                Toast.makeText(this, "Location unavailable; saved without coords.", Toast.LENGTH_SHORT).show();
                                saveEventToCsv(type, ts, null, null);
                            }
                        }).addOnFailureListener(e -> saveEventToCsv(type, ts, null, null));
                    }
                })
                .addOnFailureListener(e -> saveEventToCsv(type, ts, null, null));
    }

    private void saveEventToCsv(String type, long ts, @Nullable Double lat, @Nullable Double lon) {
        if (csvUri == null) csvUri = loadCsvUri();
        if (csvUri == null) {
            Toast.makeText(this, "Choose a CSV location first.", Toast.LENGTH_SHORT).show();
            ensureCsvChosen();
            return;
        }
        String latStr = lat == null ? "" : String.valueOf(lat);
        String lonStr = lon == null ? "" : String.valueOf(lon);
        appendCsv(csvUri, type + "," + ts + "," + latStr + "," + lonStr);
    }

    // ===== SAF helpers =====
    private void saveCsvUri(Uri uri) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_CSV_URI, uri.toString()).apply();
    }

    @Nullable private Uri loadCsvUri() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String s = sp.getString(KEY_CSV_URI, null);
        return s == null ? null : Uri.parse(s);
    }

    private void ensureCsvChosen() {
        if (csvUri == null) csvUri = loadCsvUri();
        if (csvUri == null) {
            createCsvLauncher.launch(FILE_DEFAULT_NAME);
        }
    }

    private void writeCsvHeaderIfEmpty(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            int available = (is == null) ? 0 : is.available();
            if (available == 0) {
                try (OutputStream os = getContentResolver().openOutputStream(uri, "wa");
                     OutputStreamWriter w = new OutputStreamWriter(os)) {
                    w.write("type,timestamp,lat,lon\n");
                }
            }
        } catch (IOException ignored) {}
    }

    private void appendCsv(Uri uri, String line) {
        try (OutputStream os = getContentResolver().openOutputStream(uri, "wa");
             OutputStreamWriter w = new OutputStreamWriter(os)) {
            w.write(line);
            if (!line.endsWith("\n")) w.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to write CSV.", Toast.LENGTH_SHORT).show();
        }
    }

    // ===== Permission helpers =====
    private void ensureLocationPermission() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQ_LOCATION
            );
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // No-op; captureLocationAndSave() checks permission each time
    }

    // ===== Launchers =====
    private final ActivityResultLauncher<String> createCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    csvUri = uri;
                    saveCsvUri(uri);
                    writeCsvHeaderIfEmpty(uri);
                    Toast.makeText(this, "CSV created.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String[]> openCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    csvUri = uri;
                    saveCsvUri(uri);
                    Toast.makeText(this, "CSV selected.", Toast.LENGTH_SHORT).show();
                }
            });
}
