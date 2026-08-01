package com.example.gotilo.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import com.example.gotilo.api.ApiCaller;
import com.example.gotilo.model.LocationModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LocationSender {

    private static final String TAG = "LocationSender";

    /**
     * Grabs a single high-accuracy GPS fix and sends it to the backend API.
     */
    public static void sendCurrentLocation(Context context) {
        // 1. Verify permissions before requesting GPS
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot send location: ACCESS_FINE_LOCATION permission missing.");
            return;
        }

        // 2. Fetch stored IDs
        DataPref dataPref = new DataPref(context);
        String userId = dataPref.getUid();
        String deviceId = dataPref.getDeviceId();

        if (userId == null || deviceId == null) {
            Log.e(TAG, "Cannot send location: UID or Device ID is missing.");
            return;
        }

        // 3. Request a single fresh GPS coordinate
        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();
                        String address = getReadableAddress(context, lat, lng);

                        Log.d(TAG, "GPS Fix Acquired -> Lat: " + lat + ", Lng: " + lng);
                        postToApi(userId, deviceId, lat, lng, address);
                    } else {
                        Log.w(TAG, "GPS timed out or location unavailable.");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching location", e));
    }

    private static void postToApi(String userId, String deviceId, double lat, double lng, String address) {
        ApiCaller apiCaller = new ApiCaller();
        apiCaller.saveLocation(userId, deviceId, lat, lng, address, new Callback<LocationModel>() {
            @Override
            public void onResponse(Call<LocationModel> call, Response<LocationModel> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Successfully POSTed location to Express API!");
                } else {
                    Log.e(TAG, "Server returned error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<LocationModel> call, Throwable t) {
                Log.e(TAG, "Network error posting location", t);
            }
        });
    }

    private static String getReadableAddress(Context context, double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    sb.append(address.getAddressLine(i)).append(" ");
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder failed", e);
        }
        return "Unknown Address";
    }
}