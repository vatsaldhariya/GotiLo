package com.gotilo.simtool.screen;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.gotilo.simtool.R;
import com.gotilo.simtool.api.ApiCaller;
import com.gotilo.simtool.utils.DataPref;
import com.gotilo.simtool.utils.PermissionNavigator;


public class DashboardActivity extends AppCompatActivity {

    private TextView tvUserName, tvUserEmail, tvAvatarInitial;

    // 👇 1. DECLARE ApiCaller HERE
    private ApiCaller apiCaller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

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