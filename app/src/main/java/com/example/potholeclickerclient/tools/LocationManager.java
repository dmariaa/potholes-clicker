package com.example.potholeclickerclient.tools;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import android.location.Location;

import android.os.Looper;
import android.widget.Toast;

public class LocationManager {
    private static final int REQ_LOCATION = 1001;
    private final Context context;
    private final FusedLocationProviderClient fusedLocationClient;

    private Location lastKnownLocation;
    private LocationCallback locationCallback;


    public interface LocationResultListener {
        void onLocationResult(Location location);
    }

    public LocationManager(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    public boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        if (!hasLocationPermission()) return;

        // Prevent creating multiple callbacks
        if (locationCallback != null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                if (locationResult.getLastLocation() != null) {
                    lastKnownLocation = locationResult.getLastLocation();
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null; // Clean up
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked before calling
    public void getCurrentLocation(LocationResultListener listener) {
        if (!hasLocationPermission()) {
            Toast.makeText(context, "Location permission not granted.", Toast.LENGTH_SHORT).show();
            listener.onLocationResult(null);
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
