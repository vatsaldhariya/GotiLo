package com.gotilo.simtool.screen;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.gotilo.simtool.R;
import com.gotilo.simtool.api.ApiCaller; // MUST BE IMPORTED
import com.gotilo.simtool.utils.DataPref;
import com.gotilo.simtool.utils.PermissionNavigator;

import retrofit2.Callback;

public class DashboardActivity extends AppCompatActivity {

    private Button btnLogout;
    private TextView tvUserName, tvUserEmail, tvAvatarInitial;

    // 👇 1. DECLARE ApiCaller HERE
    private ApiCaller apiCaller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize Views
        btnLogout = findViewById(R.id.btnLogout);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);

        // 👇 2. INITIALIZE ApiCaller HERE
        apiCaller = new ApiCaller();

        // Grab the User Info from DataPref
        DataPref dataPref = new DataPref(this);
        String name = dataPref.getName();
        String email = dataPref.getEmail();

        // Display the Data on the screen
        if (name != null && !name.isEmpty() && !name.equals("User")) {
            tvUserName.setText(name);
        } else {
            tvUserName.setText("Welcome Back!");
        }

        if (email != null && !email.isEmpty()) {
            tvUserEmail.setText(email);
            tvAvatarInitial.setText(String.valueOf(email.charAt(0)).toUpperCase());
        } else {
            tvUserEmail.setText("No email found.");
        }

        // Logout Button Click
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 👇 NOW WE PASS BOTH THE UID AND DEVICE_ID
                apiCaller.logoutUser(dataPref.getUid(), dataPref.getDeviceId(), new Callback<Void>() {
                    @Override
                    public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                        dataPref.clear();
                        Log.d("GOTILO_AUTH", "Session cleared. Logging out.");

                        Intent intent = new Intent(DashboardActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        Toast.makeText(DashboardActivity.this, "Network Error. Could not disconnect node.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        checkAndNavigatePermissions();
    }

    private void checkAndNavigatePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable 'Display over other apps'", Toast.LENGTH_LONG).show();
            PermissionNavigator.openOverlaySettings(this);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "Please disable Battery Optimization for background sync", Toast.LENGTH_LONG).show();
                PermissionNavigator.openBatteryOptimizationSettings(this);
                return;
            }
        }
    }
}