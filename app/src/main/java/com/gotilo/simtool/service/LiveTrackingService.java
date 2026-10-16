package com.gotilo.simtool.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.gotilo.simtool.R;
import com.gotilo.simtool.utils.DataPref;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class LiveTrackingService extends Service {

    private static final String TAG = "LiveTracking";
    public static final String CHANNEL_ID = "LiveTrackingChannel";
    private static final int NOTIFICATION_ID = 102;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private DatabaseReference liveRef;
    private boolean isTracking = false;

    // Smart Throttle Variables
    private Location baselineLocation;
    private long lastDbWriteTime = 0;
    private DataPref dataPref;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        dataPref = new DataPref(this);

        // Load the last saved baseline if the service restarted
        baselineLocation = dataPref.getBaselineLocation();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                Location currentLocation = locationResult.getLastLocation();

                if (currentLocation != null) {
                    handleSmartUpload(currentLocation);
                }
            }
        };
    }

    private void handleSmartUpload(Location currentLocation) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastWrite = currentTime - lastDbWriteTime;

        // If we have no baseline at all, save it and upload immediately
        if (baselineLocation == null) {
            baselineLocation = currentLocation;
            dataPref.saveBaselineLocation(currentLocation.getLatitude(), currentLocation.getLongitude());

            Log.d(TAG, "New Baseline Established. Initial Upload.");
            pushToFirebase(currentLocation.getLatitude(), currentLocation.getLongitude());
            lastDbWriteTime = currentTime;
            return;
        }

        // Calculate how far the phone has moved from the baseline in meters
        float distanceInMeters = currentLocation.distanceTo(baselineLocation);

        if (distanceInMeters > 20) {
            // 🚀 FAST MODE: Phone moved more than 20 meters.
            // Upload every 1 second and establish a new baseline.
            if (timeSinceLastWrite >= 1000) {
                Log.d(TAG, "Target Moving (" + distanceInMeters + "m). Uploading 1s interval.");
                pushToFirebase(currentLocation.getLatitude(), currentLocation.getLongitude());

                // Update baseline so we track the new center point
                baselineLocation = currentLocation;
                dataPref.saveBaselineLocation(currentLocation.getLatitude(), currentLocation.getLongitude());
                lastDbWriteTime = currentTime;
            }
        } else {
            // 🐢 SLOW MODE: Phone is stationary (within 20 meters).
            // Throttle uploads to every 7 seconds to save DB writes and battery.
            if (timeSinceLastWrite >= 7000) {
                Log.d(TAG, "Target Stationary. Uploading 7s interval.");
                pushToFirebase(currentLocation.getLatitude(), currentLocation.getLongitude());
                lastDbWriteTime = currentTime;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getStringExtra("ACTION") : null;
        Log.d(TAG, "onStartCommand received action: " + action);

        // Handle Stop Command
        if ("STOP_TRACKING".equals(action) || "STOP_LOCATION".equals(action)) {
            Log.d(TAG, "Stopping Live Tracking Service...");
            stopTracking();
            return START_NOT_STICKY;
        }

        // Initialize Firebase Reference for current device
        String deviceId = dataPref.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }

        // Sanitize for Realtime Database
        String safeDeviceId = deviceId.replaceAll("[.#$\\[\\]]", "_");

        liveRef = FirebaseDatabase.getInstance()
                .getReference("live_tracking")
                .child(safeDeviceId);

        // Promote to Foreground Service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service: " + e.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isTracking) {
            startLocationUpdates();
        }

        return START_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                handleSmartUpload(location);
            }
        });

        // 1-Second Update Loop (Hardware stays active, but uploads are throttled)
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(1000L)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            isTracking = true;
            Log.d(TAG, "Live tracking updates started.");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing: " + e.getMessage());
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates: " + e.getMessage());
            stopSelf();
        }
    }

    private void pushToFirebase(double lat, double lng) {
        if (liveRef == null) return;

        Map<String, Object> map = new HashMap<>();
        map.put("latitude", lat);
        map.put("longitude", lng);
        map.put("updatedAt", System.currentTimeMillis());

        liveRef.setValue(map).addOnFailureListener(e ->
                Log.e(TAG, "Failed to update Firebase RTDB", e)
        );
    }

    private void stopTracking() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates removed.");
        }
        isTracking = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Gotilo Active")
                .setContentText("Live tracking service is running...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Location Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used for continuous real-time background tracking");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }
}