package com.gotilo.simtool.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.gotilo.simtool.api.ApiService;
import com.gotilo.simtool.api.RetrofitClient;
import com.gotilo.simtool.model.LocationModel;
import com.gotilo.simtool.screen.TransparentActivity;
import com.gotilo.simtool.utils.DataPref;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "GotiloFCM";
    private static final String NOTIF_CHANNEL_ID = "GotiloFCMChannel";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "Push message received in pure background! Data payload: " + remoteMessage.getData());

        // 1. Acquire WakeLock to keep the CPU awake
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Gotilo:BackgroundWakeLock");
            wakeLock.acquire(10000L); // Hold for exactly 10 seconds
        }

        try {
            // Inside MyFirebaseMessagingService.java -> onMessageReceived()

            if (remoteMessage.getData().size() > 0) {
                String action = remoteMessage.getData().get("action");
                Log.d(TAG, "Command recognized: " + action);

                DataPref pref = new DataPref(this);

                // 👇 ADD THIS BLOCK TO CATCH THE LOCK COMMANDS 👇
                // 👇 COMMAND INTERCEPTION BLOCK 👇
                if ("LOCK_APPS".equals(action)) {
                    pref.setAppLockActive(true);
                    Log.d(TAG, "App Lock ON");
                    showBackgroundNotification("Gotilo Security", "Social apps are now locked.");
                    return;
                }
                else if ("UNLOCK_APPS".equals(action)) {
                    pref.setAppLockActive(false);
                    Log.d(TAG, "App Lock OFF");
                    showBackgroundNotification("Gotilo Security", "Social apps are now unlocked.");
                    return;
                }
                else if ("LOCK_SCREEN".equals(action)) {
                    Log.d(TAG, "🚨 Remote Lock Command Received!");
                    pref.setKioskLocked(true);

                    // Directly launch the Kiosk screen from the background using Overlay privileges
                    Intent kioskIntent = new Intent(this, com.gotilo.simtool.screen.KioskActivity.class);
                    kioskIntent.putExtra("REASON", "API_REMOTE_LOCK");
                    kioskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(kioskIntent);
                    return;
                }
                else if ("UNLOCK_SCREEN".equals(action)) {
                    Log.d(TAG, "✅ Remote Unlock Command Received!");
                    pref.setKioskLocked(false);

                    // Trigger the static unlock method inside the active KioskActivity
                    com.gotilo.simtool.screen.KioskActivity.remoteUnlock();
                    return;
                }
                // 👆 ========================================== 👆
                // 👆 ========================================== 👆

                // 1. One-time fetch doesn't require a continuous Foreground Service
                if ("FETCH_LOCATION".equals(action)) {
                    fetchdevicelocation();
                }
                // 2. Continuous tracking and camera commands
                else if ("CAPTURE_BOTH".equals(action) ||
                        "LIVE_TRACKING".equals(action) ||
                        "CAPTURE_AUDIO".equals(action) ||
                        "CAPTURE_VIDEO".equals(action) ||
                        "STOP_TRACKING".equals(action) ||
                        "STOP_LOCATION".equals(action) ||
                        "PLAY_SOUND".equals(action) ||
                        "STOP_SOUND".equals(action)) {

                    // Check if we have the overlay permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                        Intent activityIntent = new Intent(this, TransparentActivity.class);
                        activityIntent.putExtra("ACTION", action);
                        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(activityIntent);
                    } else {
                        Log.e(TAG, "Cannot execute command: Overlay permission missing.");
                    }
                }
            }
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    // =========================================================
    // STANDALONE NOTIFICATION METHOD
    // =========================================================
    private void showBackgroundNotification(String title, String messageText) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIF_CHANNEL_ID, "Gotilo FCM Commands", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(com.gotilo.simtool.R.mipmap.ic_launcher) // Ensure this is your actual app icon
                .setContentTitle(title)
                .setContentText(messageText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), notification);
        }
    }

    @Override
    public void onNewToken(@NonNull String newToken) {
        super.onNewToken(newToken);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        FirebaseFirestore.getInstance()
                .collection("users").document(deviceId)
                .update("fcmToken", newToken);
    }

    private String getAddressFromCoords(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            Log.w(TAG, "Geocoder error: " + e.getMessage());
        }
        return "Unknown Address";
    }

    @SuppressLint("MissingPermission")
    private void fetchdevicelocation() {
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);
        final boolean[] locationSent = {false};

        locationClient.getLastLocation().addOnSuccessListener(lastLocation -> {
            if (lastLocation != null && !locationSent[0]) {
                locationSent[0] = true;
                fireSaveLocationApi(lastLocation.getLatitude(), lastLocation.getLongitude(),
                        getAddressFromCoords(lastLocation.getLatitude(), lastLocation.getLongitude()));
            }
        });

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMaxUpdates(1).build();
        HandlerThread handlerThread = new HandlerThread("LocationFetchThread");
        handlerThread.start();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                locationClient.removeLocationUpdates(this);
                handlerThread.quitSafely();

                Location location = locationResult.getLastLocation();
                if (location != null && !locationSent[0]) {
                    locationSent[0] = true;
                    fireSaveLocationApi(location.getLatitude(), location.getLongitude(), getAddressFromCoords(location.getLatitude(), location.getLongitude()));
                }
            }
        };

        locationClient.requestLocationUpdates(locationRequest, locationCallback, handlerThread.getLooper());
    }

    private void fireSaveLocationApi(double latitude, double longitude, String address) {
        DataPref pref = new DataPref(this);
        LocationModel request = new LocationModel(pref.getUid(), pref.getDeviceId(), latitude, longitude, address, "normal", System.currentTimeMillis());

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        apiService.saveLocation(request).enqueue(new retrofit2.Callback<LocationModel>() {
            @Override
            public void onResponse(@NonNull Call<LocationModel> call, @NonNull retrofit2.Response<LocationModel> response) {
                Log.d(TAG, "Successfully saved location to backend!");
            }
            @Override
            public void onFailure(@NonNull Call<LocationModel> call, @NonNull Throwable t) {
                Log.e(TAG, "API call failed", t);
            }
        });
    }
}