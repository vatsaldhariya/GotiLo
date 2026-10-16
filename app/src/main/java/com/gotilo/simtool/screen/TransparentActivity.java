package com.gotilo.simtool.screen;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.gotilo.simtool.service.AlarmService;
import com.gotilo.simtool.service.LiveTrackingService;
import com.gotilo.simtool.service.MediaCaptureService;
import com.gotilo.simtool.utils.DataPref;

public class TransparentActivity extends Activity {

    private static final String TAG = "TransparentActivity";
    private boolean hasFiredService = false; // Prevent double-execution
    private String pendingAction = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "TransparentActivity created! Waking up screen...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        // Force the screen to turn on and stay on during execution
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        pendingAction = getIntent().getStringExtra("ACTION");
        Log.d(TAG, "Action queued: " + pendingAction);
    }

    // 🚀 THE FIX: Wait for the OS to officially grant visual focus to the app
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && !hasFiredService && pendingAction != null) {
            Log.w(TAG, "✅ Activity officially gained screen focus! Firing FGS now...");
            hasFiredService = true;

            startRequestedService(pendingAction);

            // Safely close the activity 2.5 seconds later to give the camera plenty of time to bind
            new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2500);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 🛡️ FALLBACK: If the phone is deeply asleep in a pocket and refuses to wake the screen 
        // completely to grant focus, we brute-force it after 1.5 seconds.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!hasFiredService && pendingAction != null) {
                Log.w(TAG, "⚠️ Window focus timeout. Brute-forcing service start...");
                hasFiredService = true;

                startRequestedService(pendingAction);
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 2500);
            }
        }, 1500);
    }

    private void startRequestedService(String action) {
        try {
            switch (action) {

                // ==========================================
                // 🔒 NEW: REMOTE SCREEN LOCK COMMANDS
                // ==========================================
                case "LOCK_SCREEN":
                    new DataPref(this).setKioskLocked(true);
                    Intent kioskIntent = new Intent(this, KioskActivity.class);
                    kioskIntent.putExtra("REASON", "API_REMOTE_LOCK");
                    // Ensure it clears old tasks and acts as a fresh overlay
                    kioskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(kioskIntent);
                    Log.d(TAG, "Device Screen Locked remotely");
                    Toast.makeText(this, "Gotilo Security: Screen Locked", Toast.LENGTH_LONG).show();
                    break;

                case "UNLOCK_SCREEN":
                    new DataPref(this).setKioskLocked(false);
                    // Call the static helper method in KioskActivity to kill it
                    KioskActivity.remoteUnlock();
                    Log.d(TAG, "Device Screen Unlocked remotely");
                    Toast.makeText(this, "Gotilo Security: Screen Unlocked", Toast.LENGTH_LONG).show();
                    break;
                // ==========================================

                case "LOCK_APPS":
                    new DataPref(this).setAppLockActive(true);
                    Log.d(TAG, "App Lock ON executed via TransparentActivity");
                    Toast.makeText(this, "Gotilo Security: Apps Locked", Toast.LENGTH_SHORT).show();
                    break;

                case "UNLOCK_APPS":
                    new DataPref(this).setAppLockActive(false);
                    Log.d(TAG, "App Lock OFF executed via TransparentActivity");
                    Toast.makeText(this, "Gotilo Security: Apps Unlocked", Toast.LENGTH_SHORT).show();
                    break;

                case "CAPTURE_OFFLINE":
                    Intent offlineIntent = new Intent(this, MediaCaptureService.class);
                    offlineIntent.putExtra("MEDIA_COMMAND", "CAPTURE_OFFLINE");
                    ContextCompat.startForegroundService(this, offlineIntent);
                    break;

                case "CAPTURE_BOTH":
                case "CAPTURE_AUDIO":
                case "CAPTURE_VIDEO":
                    Intent mediaIntent = new Intent(this, MediaCaptureService.class);
                    mediaIntent.putExtra("MEDIA_COMMAND", action);
                    ContextCompat.startForegroundService(this, mediaIntent);
                    break;

                case "LIVE_TRACKING":
                    Intent startTrackIntent = new Intent(this, LiveTrackingService.class);
                    startTrackIntent.putExtra("ACTION", "LIVE_TRACKING");
                    ContextCompat.startForegroundService(this, startTrackIntent);
                    break;

                case "STOP_TRACKING":
                case "STOP_LOCATION":
                    Intent stopTrackIntent = new Intent(this, LiveTrackingService.class);
                    stopTrackIntent.putExtra("ACTION", "STOP_TRACKING");
                    startService(stopTrackIntent);
                    break;

                case "SYNC_RECOVERY":
                    Intent recoveryIntent = new Intent(this, MediaCaptureService.class);
                    recoveryIntent.putExtra("MEDIA_COMMAND", "SYNC_RECOVERY");
                    ContextCompat.startForegroundService(this, recoveryIntent);
                    break;

                case "SIM_CHANGED_RECOVERY":
                    Intent simIntent = new Intent(this, MediaCaptureService.class);
                    simIntent.putExtra("MEDIA_COMMAND", "SYNC_RECOVERY");
                    simIntent.putExtra("EVENT_REASON", "🚨 SIM CARD CHANGED");
                    ContextCompat.startForegroundService(this, simIntent);
                    break;

                case "WRONG_PASSWORD_RECOVERY":
                    Intent wrongPassIntent = new Intent(this, MediaCaptureService.class);
                    wrongPassIntent.putExtra("MEDIA_COMMAND", "SYNC_RECOVERY");
                    wrongPassIntent.putExtra("EVENT_REASON", "🚨 3 WRONG PASSWORD ATTEMPTS");
                    ContextCompat.startForegroundService(this, wrongPassIntent);
                    break;

                case "PLAY_SOUND":
                    Intent startAlarmIntent = new Intent(this, AlarmService.class);
                    startAlarmIntent.setAction("START_ALARM");
                    ContextCompat.startForegroundService(this, startAlarmIntent);
                    break;

                case "STOP_SOUND":
                    Intent stopAlarmIntent = new Intent(this, AlarmService.class);
                    stopAlarmIntent.setAction("STOP_ALARM");
                    startService(stopAlarmIntent);
                    break;

                default:
                    Log.w(TAG, "Unknown action received: " + action);
            }
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL ERROR starting service: " + e.getMessage(), e);
        }
    }
}