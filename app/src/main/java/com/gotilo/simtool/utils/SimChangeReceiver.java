package com.gotilo.simtool.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.gotilo.simtool.screen.TransparentActivity;
import com.gotilo.simtool.service.MediaCaptureService;

import java.util.List;

public class SimChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "SimChangeReceiver";

    @SuppressLint("MissingPermission")
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // 2-minute Boot Window to ignore fake ABSENT signals from the OS modem
        boolean isDeviceBooting = SystemClock.elapsedRealtime() < 120000;

        if ("android.intent.action.SIM_STATE_CHANGED".equals(action) ||
                "android.telephony.action.SIM_CARD_STATE_CHANGED".equals(action)) {

            String state = intent.getStringExtra("ss");
            SharedPreferences prefs = context.getSharedPreferences("GotiloAppPrefs", Context.MODE_PRIVATE);

            // 🛑 1. SIM CARD PULLED OUT
            if ("ABSENT".equals(state)) {
                if (isDeviceBooting) {
                    Log.d(TAG, "Ignoring fake SIM ABSENT state during phone boot phase.");
                    return;
                }

                boolean alreadyRemoved = prefs.getBoolean("SIM_WAS_REMOVED", false);
                if (!alreadyRemoved) {
                    Log.w(TAG, "🚨 SIM CARD REMOVED! Capturing media locally...");
                    prefs.edit().putBoolean("SIM_WAS_REMOVED", true).apply();
                    triggerOfflineCapture(context);
                }
                return;
            }

            // 🟢 2. SIM INSERTED OR NETWORK READY
            if ("LOADED".equals(state) || "READY".equals(state)) {
                PendingResult pendingResult = goAsync();
                checkSimDetailsAndUpload(context, prefs, isDeviceBooting, pendingResult);
            }
        }
    }

    @SuppressLint({"MissingPermission", "HardwareIds"})
    private void checkSimDetailsAndUpload(Context context, SharedPreferences prefs, boolean isDeviceBooting, PendingResult pendingResult) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                String carrierName = "Unknown Carrier";
                String simSerial = "Unknown";
                String phoneNumber = "Unknown Number"; // <-- Added dynamic variable

                if (tm != null) {
                    carrierName = tm.getSimOperatorName();
                    simSerial = tm.getSimOperator();
                }

                // 👇 FETCH THE PHONE NUMBER LOGIC 👇
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        // Method 1: Subscription Manager (Best for Android 5.1+)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            SubscriptionManager subManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                            if (subManager != null) {
                                List<SubscriptionInfo> subInfoList = subManager.getActiveSubscriptionInfoList();
                                if (subInfoList != null) {
                                    for (SubscriptionInfo info : subInfoList) {
                                        String num = info.getNumber();
                                        if (num != null && !num.trim().isEmpty()) {
                                            phoneNumber = num;
                                            break; // Stop after finding the first valid number
                                        }
                                    }
                                }
                            }
                        }

                        // Method 2: TelephonyManager Fallback (If SubscriptionManager fails)
                        if ("Unknown Number".equals(phoneNumber) && tm != null) {
                            String line1 = tm.getLine1Number();
                            if (line1 != null && !line1.trim().isEmpty()) {
                                phoneNumber = line1;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to extract phone number: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Cannot read phone number: Missing READ_PHONE_NUMBERS permission.");
                }
                // 👆 ============================= 👆

                if (carrierName == null || carrierName.isEmpty()) carrierName = "Unknown Carrier";
                if (simSerial == null || simSerial.isEmpty()) simSerial = "Unknown";

                DataPref dataPref = new DataPref(context);
                String savedSim = dataPref.getLastSimId();
                boolean isNewSimOperator = false;

                // Detect true offline SIM swaps
                if (savedSim == null || savedSim.isEmpty() || "NONE".equals(savedSim)) {
                    dataPref.saveLastSimId(simSerial); // First run registration
                } else if (!savedSim.equals(simSerial) && !"Unknown".equals(simSerial)) {
                    Log.e(TAG, "🚨 REAL SIM SWAP DETECTED! Carrier changed from " + savedSim + " to " + simSerial);
                    dataPref.saveLastSimId(simSerial);
                    isNewSimOperator = true;
                }

                boolean simWasRemoved = prefs.getBoolean("SIM_WAS_REMOVED", false);

                // Ignore if it's just a normal phone boot or regular network reconnect
                if (isDeviceBooting && !isNewSimOperator) {
                    Log.d(TAG, "Normal device boot: Same SIM confirmed. SimChangeReceiver standing down.");
                    prefs.edit().putBoolean("SIM_WAS_REMOVED", false).apply();
                    return;
                }

                // ONLY fire the SIM SWAP event if the SIM was removed or replaced
                if (simWasRemoved || isNewSimOperator) {
                    Log.w(TAG, "Triggering SIM SWAP API Upload Sequence!");
                    prefs.edit().putBoolean("SIM_WAS_REMOVED", false).apply();

                    // 👇 Pass the actual retrieved phone number instead of the hardcoded string 👇
                    triggerOfflineUpload(context, carrierName, simSerial, phoneNumber);
                } else {
                    Log.d(TAG, "Normal network cycle. Same SIM detected.");
                    prefs.edit().putBoolean("SIM_WAS_REMOVED", false).apply();
                }

            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
            }
        }, 5000);
    }

    private void triggerOfflineCapture(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            Intent activityIntent = new Intent(context, TransparentActivity.class);
            activityIntent.putExtra("ACTION", "CAPTURE_OFFLINE");
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            try {
                context.startActivity(activityIntent);
            } catch (Exception e) {
                startMediaServiceDirectly(context, "CAPTURE_OFFLINE", null, null, null);
            }
        } else {
            startMediaServiceDirectly(context, "CAPTURE_OFFLINE", null, null, null);
        }
    }

    private void triggerOfflineUpload(Context context, String carrier, String serial, String phone) {
        startMediaServiceDirectly(context, "UPLOAD_OFFLINE", carrier, serial, phone);
    }

    private void startMediaServiceDirectly(Context context, String command, String carrier, String serial, String phone) {
        Intent serviceIntent = new Intent(context, MediaCaptureService.class);
        serviceIntent.putExtra("MEDIA_COMMAND", command);
        if (carrier != null) serviceIntent.putExtra("NEW_CARRIER", carrier);
        if (serial != null) serviceIntent.putExtra("NEW_SERIAL", serial);
        if (phone != null) serviceIntent.putExtra("NEW_PHONE", phone);

        try {
            ContextCompat.startForegroundService(context, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MediaCaptureService", e);
        }
    }
}