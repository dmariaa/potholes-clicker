package com.example.potholeclickerclient.tools;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class LocationManager {
    private static final int REQ_LOCATION = 1001;
    private final Activity activity; // Use Activity for permission requests
    private final FusedLocationProviderClient fusedLocationClient;

    public interface LocationResultListener {
        void onLocationResult(Location location);
    }

    public LocationManager(Activity activity) {
        this.activity = activity;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    public void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQ_LOCATION
        );
    }

    public boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // Permissions are checked before calling
    public void getCurrentLocation(LocationResultListener listener) {
        if (!hasLocationPermission()) {
            Toast.makeText(activity, "Location permission not granted.", Toast.LENGTH_SHORT).show();
            requestLocationPermission();
            return;
        }

        CancellationTokenSource cts = new CancellationTokenSource();
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        listener.onLocationResult(location);
                    } else {
                        // Fallback to last known location
                        fusedLocationClient.getLastLocation().addOnSuccessListener(lastLocation -> {
                            // Listener can handle null if both fail
                            listener.onLocationResult(lastLocation);
                        });
                    }
                })
                .addOnFailureListener(e -> listener.onLocationResult(null));
    }
}
