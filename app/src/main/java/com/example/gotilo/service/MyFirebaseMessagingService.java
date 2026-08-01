package com.example.gotilo.service;

import android.annotation.SuppressLint;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;

import com.example.gotilo.api.ApiService;
import com.example.gotilo.api.RetrofitClient;
import com.example.gotilo.model.LocationModel;
import com.example.gotilo.utils.DataPref;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "GotiloFCM";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "Push message received! Data payload: " + remoteMessage.getData());

        if (remoteMessage.getData().size() > 0) {
            String action = remoteMessage.getData().get("action");
            if ("FETCH_LOCATION".equals(action)) {
                Log.d(TAG, "FETCH_LOCATION command recognized. Waking up GPS...");
                fetchdevicelocation();
            }
        }
    }

    @Override
    public void onNewToken(@NonNull String newToken) {
        super.onNewToken(newToken);
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        FirebaseFirestore.getInstance()
                .collection("users").document(deviceId)
                .update("fcmToken", newToken)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Token updated in Firestore"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update token", e));
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

        // 1. Check Last Known Location as a fallback, but give fresh GPS 4 seconds to win first
        locationClient.getLastLocation().addOnSuccessListener(lastLocation -> {
            if (lastLocation != null) {
                Log.d(TAG, "Last Known Location cached: " + lastLocation.getLatitude() + ", " + lastLocation.getLongitude());

                // Wait 4.5 seconds; if fresh GPS haven't arrived by then, send the cached location
                new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!locationSent[0]) {
                        locationSent[0] = true;
                        Log.d(TAG, "Fresh GPS timed out. Using cached Last Known Location.");
                        fireSaveLocationApi(lastLocation.getLatitude(), lastLocation.getLongitude(),
                                getAddressFromCoords(lastLocation.getLatitude(), lastLocation.getLongitude()));
                    }
                }, 4500L);
            }
        });

        // 2. Request a fresh high-accuracy location update on a background thread
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000L)
                .setMaxUpdates(1)
                .build();

        HandlerThread handlerThread = new HandlerThread("LocationFetchThread");
        handlerThread.start();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                locationClient.removeLocationUpdates(this);
                handlerThread.quitSafely(); // Clean up thread

                Location location = locationResult.getLastLocation();
                if (location != null && !locationSent[0]) {
                    locationSent[0] = true;
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    String address = getAddressFromCoords(latitude, longitude);

                    Log.d(TAG, "Fresh Location Acquired: " + latitude + ", " + longitude);
                    fireSaveLocationApi(latitude, longitude, address);
                }
            }
        };

        locationClient.requestLocationUpdates(locationRequest, locationCallback, handlerThread.getLooper());
    }
    private void fireSaveLocationApi(double latitude, double longitude, String address) {
        DataPref pref = new DataPref(this);
        String uid = pref.getUid();
        String deviceId = pref.getDeviceId();

        // 1. Build the LocationModel payload using the new 7-argument constructor
        // Passing System.currentTimeMillis() ensures a fresh timestamp on every upload!
        LocationModel request = new LocationModel(
                uid,
                deviceId,
                latitude,
                longitude,
                address,
                "normal",
                System.currentTimeMillis()
        );

        // 2. Obtain the ApiService from your RetrofitClient
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

        // 3. Fire the API call asynchronously
        Call<LocationModel> call = apiService.saveLocation(request);
        call.enqueue(new retrofit2.Callback<LocationModel>() {
            @Override
            public void onResponse(@NonNull Call<LocationModel> call, @NonNull retrofit2.Response<LocationModel> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Successfully saved location to backend! Lat: " +
                            response.body().getLatitude() + ", Lng: " + response.body().getLongitude());
                } else {
                    Log.e(TAG, "Failed to save location. HTTP Status Code: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<LocationModel> call, @NonNull Throwable t) {
                Log.e(TAG, "API call failed due to network error: " + t.getMessage(), t);
            }
        });
    }

}