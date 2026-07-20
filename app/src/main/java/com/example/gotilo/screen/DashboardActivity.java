package com.example.gotilo.screen;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.gotilo.R;
import com.example.gotilo.api.ApiCaller;
import com.example.gotilo.model.LocationModel;
import com.example.gotilo.utils.DataPref;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    private Button btnLogout, btnFetchLocation;
    private TextView tvLocation;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_CODE = 100;

    // We will store the user's UID here
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize Views
        btnLogout = findViewById(R.id.btnLogout);
        btnFetchLocation = findViewById(R.id.btnFetchLocation);
        tvLocation = findViewById(R.id.tvLocation);

        // Initialize the Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // ---> NEW: Grab the UID directly from DataPref! <---
        DataPref dataPref = new DataPref(this);
        myUid = dataPref.getUid();

        // LOG IT TO LOGCAT
        Log.d("GOTILO_AUTH", "Dashboard Opened. Saved UID is: " + myUid);

        // SHOW IT ON SCREEN SO YOU CAN SEE IT WORK
        Toast.makeText(this, "Active UID: " + myUid, Toast.LENGTH_SHORT).show();


        // Fetch Location Button Click
        btnFetchLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndFetchLocation();
            }
        });

        // Logout Button Click
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Erase the saved UID from DataPref
                dataPref.clear();
                Log.d("GOTILO_AUTH", "UID cleared. Logging out.");

                // Send them back to Login
                Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void checkPermissionAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchActualLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchActualLocation();
            } else {
                Toast.makeText(this, "Permission Denied. Cannot fetch location.", Toast.LENGTH_SHORT).show();
                tvLocation.setText("Location: Permission Denied");
            }
        }
    }
    private void fetchActualLocation() {
        tvLocation.setText("Location: Requesting fresh GPS fix...");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        com.google.android.gms.location.CurrentLocationRequest currentLocationRequest =
                new com.google.android.gms.location.CurrentLocationRequest.Builder()
                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                        .setDurationMillis(10000)
                        .build();

        fusedLocationClient.getCurrentLocation(currentLocationRequest, null)
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();

                            // 1. LOG THE LAT & LNG COMBINATION
                            Log.d("GEO_LOCATION", "Fetched Lat: " + latitude + ", Lng: " + longitude);

                            // 2. CONVERT LAT & LNG INTO A READABLE ADDRESS USING GEOCODER
                            String fullAddress = getReadableAddress(latitude, longitude);

                            // 3. DISPLAY ON SCREEN & LOG THE FULL GEO-DETAILS
                            String locationText = "Lat: " + latitude + "\nLng: " + longitude + "\n\nAddress:\n" + fullAddress;
                            tvLocation.setText(locationText);

                            Log.d("GEO_LOCATION", "Resolved Address: " + fullAddress);
                            Toast.makeText(DashboardActivity.this, "Geolocation Fetched Successfully!", Toast.LENGTH_SHORT).show();

                        } else {
                            tvLocation.setText("Location: GPS timed out. Ensure Location services are turned ON in device settings.");
                            Log.e("GEO_LOCATION", "GPS returned null location.");
                        }
                    }
                })
                .addOnFailureListener(this, e -> {
                    tvLocation.setText("Location Error: " + e.getMessage());
                    Log.e("GEO_LOCATION", "Failed to fetch geolocation", e);
                });
    }

    // Helper method to turn Latitude & Longitude into a street/city address
    private String getReadableAddress(double latitude, double longitude) {
        try {
            android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.getDefault());
            java.util.List<android.location.Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();

                // Extract address lines if available
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    sb.append(address.getAddressLine(i)).append("\n");
                }

                return sb.toString().trim();
            }
        } catch (Exception e) {
            Log.e("GEO_LOCATION", "Geocoder failed to translate coordinates", e);
        }
        return "Coordinates found, but address lookup failed.";
    }
}