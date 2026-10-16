package com.gotilo.simtool.utils;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.gotilo.simtool.screen.TransparentActivity;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "GotiloBoot";

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "System Broadcast Received: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences("GotiloAppPrefs", Context.MODE_PRIVATE);

            long bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime();
            long lastHandledBoot = prefs.getLong("LAST_HANDLED_BOOT_TIME", 0);

            if (Math.abs(bootTime - lastHandledBoot) < 10000) {
                Log.d(TAG, "Boot event already handled for this restart. Skipping duplicate.");
                return;
            }

            prefs.edit().putLong("LAST_HANDLED_BOOT_TIME", bootTime).apply();

            Log.w(TAG, "Device booted up! Bouncing through TransparentActivity to gain Camera permission...");
            triggerBootRecovery(context);
        }
    }

    private void triggerBootRecovery(Context context) {
        Intent activityIntent = new Intent(context, TransparentActivity.class);
        activityIntent.putExtra("ACTION", "SYNC_RECOVERY");
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        try {
            // 🚀 THE FIX: Use a PendingIntent to bypass Android 14 Background Activity restrictions
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    1001,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            pendingIntent.send();
            Log.d(TAG, "PendingIntent fired successfully to wake TransparentActivity.");
        } catch (Exception e) {
            Log.e(TAG, "PendingIntent failed, attempting direct startActivity...", e);
            try {
                context.startActivity(activityIntent);
            } catch (Exception ex) {
                Log.e(TAG, "Total failure launching TransparentActivity from BootReceiver", ex);
            }
        }
    }
}