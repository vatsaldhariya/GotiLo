package com.example.gotilo.screen;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.gotilo.R;
import com.example.gotilo.api.ApiCaller;
import com.example.gotilo.model.LoginModel;
import com.example.gotilo.utils.DataPref; // Added DataPref import
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoToRegister;
    private ProgressBar progressBar;
    private ApiCaller apiCaller;
    private static final int LOCATION_PERMISSION_CODE = 101;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        try {
            setContentView(R.layout.activity_login);

            // Initialize Views
            etEmail = findViewById(R.id.etLoginEmail);
            etPassword = findViewById(R.id.etLoginPassword);
            btnLogin = findViewById(R.id.btnLogin);
            tvGoToRegister = findViewById(R.id.tvGoToRegister);
            progressBar = findViewById(R.id.loginProgressBar);

            // Initialize API Caller
            apiCaller = new ApiCaller();

            // Login Button Click
            btnLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        attemptLogin();
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Crash Prevented: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("LOGIN_CRASH", "Error in button click", e);
                    }
                }
            });

            // Go to Register Page Click
            tvGoToRegister.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    startActivity(intent);
                    finish();
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Screen Load Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        // Add to LoginActivity.java -> onCreate()
        checkAndRequestLocationPermissions();

    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // ... your input validation ...

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        // 1. Fetch the latest FCM Token for this physical device
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String fcmToken = "";
                    if (task.isSuccessful() && task.getResult() != null) {
                        fcmToken = task.getResult();
                    }

                    // 2. Generate or retrieve the Device ID (using your custom generator or Android ID)
                    String deviceId = RegisterActivity.generateDeviceId(email.split("@")[0]);
                    // OR: String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

                    // 3. Call your API or update Firestore directly
                    loginUserInDatabase(email, password, fcmToken, deviceId);
                });
    }

    private void loginUserInDatabase(String email, String password, String fcmToken, String deviceId) {
        // If calling Retrofit Login API, pass fcmToken and deviceId in your LoginModel
        // Once login is successful, ALWAYS update the fcmToken in Firestore under this user's document:
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(deviceId) // or userRecord.uid depending on your structure
                .update("fcmToken", fcmToken, "deviceId", deviceId)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                    finish();
                });
    }

    private void checkAndRequestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else {
            checkIfGpsIsEnabled();
        }
    }

    private void checkIfGpsIsEnabled() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gpsEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            Log.e("GOTILO_GPS", "Failed to check GPS status", e);
        }

        if (!gpsEnabled) {
            Toast.makeText(this, "Please turn ON Location Services (GPS)", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkIfGpsIsEnabled();
        }
    }
}