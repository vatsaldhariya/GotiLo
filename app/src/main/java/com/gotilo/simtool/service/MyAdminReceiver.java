package com.gotilo.simtool.service;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;

public class MyAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "MyAdminReceiver";

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Toast.makeText(context, "Gotilo Device Admin Enabled", Toast.LENGTH_SHORT).show();

        // 🚀 Call the method here so it applies the second they grant permission
        disableLockScreenFeatures(context);
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Toast.makeText(context, "Gotilo Device Admin Disabled", Toast.LENGTH_SHORT).show();
    }

    // 👇 Safely wrapped method to prevent Android 13/14+ crashes
    public void disableLockScreenFeatures(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(context, MyAdminReceiver.class);

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            try {
                // Disables notifications and secure features on the lock screen natively
                int flags = DevicePolicyManager.KEYGUARD_DISABLE_SECURE_NOTIFICATIONS
                        | DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA
                        | DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS;

                dpm.setKeyguardDisabledFeatures(adminComponent, flags);
                Toast.makeText(context, "Native Lock Screen Shield Active \uD83D\uDEE1\uFE0F", Toast.LENGTH_SHORT).show();

            } catch (SecurityException e) {
                // Catches the crash on newer Android versions.
                // Device Admin is still granted successfully for your other features!
                Log.e(TAG, "OS restricted disabling keyguard features: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error applying lock screen shield: " + e.getMessage());
            }
        }
    }
}