package com.gotilo.simtool.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.gotilo.simtool.api.ApiService;
import com.gotilo.simtool.api.RetrofitClient;
import com.gotilo.simtool.model.AppBlockRequest;
import com.gotilo.simtool.utils.DataPref;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class YourAccessibilityService extends AccessibilityService {

    private static final String TAG = "GotiloAppBlocker";
    private static YourAccessibilityService instance;
    private DataPref pref;

    public static YourAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        pref = new DataPref(this);

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.DEFAULT |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        this.setServiceInfo(info);

        Log.d(TAG, "Gotilo Accessibility Service Connected & Configured with Global Shield!");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        if (pref == null) pref = new DataPref(this);

        String currentPackage = event.getPackageName().toString();
        String currentClass = event.getClassName() != null ? event.getClassName().toString() : "";
        int eventType = event.getEventType();

        // ====================================================================
        // STEP A: AUTOMATIC CALIBRATION
        // ====================================================================
        if (pref.isPowerMenuCalibrationActive()) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (currentPackage.equals(getPackageName())) return;

                boolean hasPowerText = checkForPowerKeywords(getRootInActiveWindow());

                if (hasPowerText || currentClass.toLowerCase().contains("globalactions") || currentClass.toLowerCase().contains("power") || currentClass.toLowerCase().contains("chooser")) {
                    pref.savePowerMenuSignature(currentPackage, currentClass);
                    pref.setPowerMenuCalibrationActive(false);

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        Toast.makeText(getApplicationContext(), "✓ Power Menu Calibrated Automatically!", Toast.LENGTH_SHORT).show();
                    }, 200);

                    Log.d(TAG, "Calibrated Power Menu: Pkg=" + currentPackage + ", Class=" + currentClass);
                    return;
                }
            }
        }

        // ====================================================================
        // STEP B: PREVENT STATUS BAR, NAVIGATION BAR, & POWER MENU ON LOCK SCREEN
        // ====================================================================

        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean isSystemLocked = (keyguardManager != null && keyguardManager.isKeyguardLocked());
        boolean isKioskActive = (pref != null && pref.isKioskLocked());

        if (isSystemLocked || isKioskActive) {

            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                return;
            }

            String savedPkg = pref.getPowerMenuPackage();
            String savedClass = pref.getPowerMenuClass();
            String lowerClass = currentClass.toLowerCase();
            String lowerPkg = currentPackage.toLowerCase();

            // Allow biometrics (fingerprint/face)
            if (lowerPkg.contains("biometric") || lowerPkg.contains("fingerprint") || lowerPkg.contains("face")) {
                return;
            }

            // 🚨 PIN/PASSWORD BLOCKER (From previous step)
            if (isKioskActive && lowerPkg.contains("systemui") && (
                    lowerClass.contains("bouncer") ||
                            lowerClass.contains("keyguard") ||
                            lowerClass.contains("pattern") ||
                            lowerClass.contains("password") ||
                            lowerClass.contains("pin") ||
                            lowerClass.contains("lockscreen") ||
                            lowerClass.contains("credential")
            )) {

                Log.w(TAG, "🚨 Intruder reached System Password Screen! RUTHLESS INSTANT LOCK.");

                // 1. Immediately turn off the screen
                turnOffScreenWithAdmin();

                // 2. Force Kiosk Activity back to the front over the lock screen
                Intent kioskIntent = new Intent(this, com.gotilo.simtool.screen.KioskActivity.class);
                kioskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(kioskIntent);

                return;
            }

            boolean isPowerMenuIntrusion = false;
            boolean isStatusBarIntrusion = false;

            // 1. Detect Power Menu
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (!savedPkg.isEmpty() && currentPackage.equals(savedPkg)) {
                    if (savedClass.isEmpty() || currentClass.equals(savedClass) ||
                            lowerClass.contains("dialog") ||
                            lowerClass.contains("globalactions") ||
                            lowerClass.contains("chooser")) {
                        isPowerMenuIntrusion = true;
                    }
                }

                if (!isPowerMenuIntrusion && (currentPackage.equals("android") ||
                        currentPackage.contains("systemui") || currentPackage.contains("miui") ||
                        currentPackage.contains("coloros"))) {

                    if (checkForPowerKeywords(getRootInActiveWindow()) ||
                            lowerClass.contains("globalactions") ||
                            lowerClass.contains("power")) {
                        isPowerMenuIntrusion = true;
                    }
                }
            }

            // 2. Detect Status Bar / Notification Shade
            if (currentPackage.equals("com.android.systemui") && !currentClass.contains("KioskActivity")) {

                // Instant Trap for Quick Settings taps
                if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    String text = event.getText() != null ? event.getText().toString().toLowerCase() : "";
                    String desc = event.getContentDescription() != null ? event.getContentDescription().toString().toLowerCase() : "";

                    if (desc.contains("wifi") || desc.contains("wi-fi") || desc.contains("airplane") ||
                            desc.contains("location") || desc.contains("bluetooth") || desc.contains("internet") ||
                            desc.contains("data") || desc.contains("hotspot") || desc.contains("do not disturb") ||
                            text.contains("wifi") || text.contains("wi-fi") || text.contains("airplane")) {

                        Log.w(TAG, "🚨 Quick Setting Tapped! RUTHLESS INSTANT LOCK.");
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        turnOffScreenWithAdmin();
                        return;
                    }
                }

                // Only trigger status bar intrusion if it's explicitly a notification/shade class
                if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    String text = event.getText() != null ? event.getText().toString().toLowerCase() : "";

                    // Limit this check to strictly known panel classes to avoid hitting biometric dialogs
                    if (lowerClass.contains("notificationshade") ||
                            lowerClass.contains("notificationpanel") ||
                            lowerClass.contains("quicksettings") ||
                            text.contains("quick settings")) {
                        isStatusBarIntrusion = true;
                    }
                }
            }

            // 3A. Execute INSTANT Defense for Power Menu
            if (isPowerMenuIntrusion) {
                Log.w(TAG, "🚨 Power Menu Intrusion! Instantly turning off screen.");
                performGlobalAction(GLOBAL_ACTION_BACK);
                new Handler(Looper.getMainLooper()).postDelayed(this::turnOffScreenWithAdmin, 50);
                return;
            }

            // 3B. Execute HYBRID Defense for Status Bar
            if (isStatusBarIntrusion) {
                Log.w(TAG, "⚠️ Status Bar movement detected. FIGHTING THE SWIPE!");
                Handler handler = new Handler(Looper.getMainLooper());

                for (int i = 0; i <= 200; i += 40) {
                    handler.postDelayed(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
                        }
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }, i);
                }

                handler.postDelayed(() -> {
                    KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        Log.w(TAG, "🚨 Intrusion Confirmed! Turning off screen.");
                        turnOffScreenWithAdmin();
                    }
                }, 250);
                return;
            }
        }

        // ====================================================================
        // STEP C: APP LOCKER (DYNAMIC LIST)
        // ====================================================================
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (!pref.isAppLockActive()) return;

            // Fetch the user's dynamic block list
            java.util.Set<String> dynamicBlockedPackages = pref.getBlockedApps();

            if (dynamicBlockedPackages.contains(currentPackage)) {
                Log.w(TAG, "🚨 Restricted app opened! Forcing exit to Home Screen.");
                performGlobalAction(GLOBAL_ACTION_HOME);
                Toast.makeText(this, "Gotilo Security: App Access Restricted", Toast.LENGTH_SHORT).show();
                reportBlockedAppToApi(currentPackage);
            }
        }
    }

    /**
     * Attempts to turn off the display instantly using Device Admin.
     * Falls back to Accessibility Service lock if Device Admin is not granted.
     */
    private void turnOffScreenWithAdmin() {
        android.app.admin.DevicePolicyManager dpm =
                (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName adminComponent =
                new android.content.ComponentName(this, com.gotilo.simtool.service.MyAdminReceiver.class);

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow(); // Instantly turns off the screen
                Log.d(TAG, "Screen turned off via Device Admin.");
                return;
            } catch (SecurityException e) {
                Log.e(TAG, "Device Admin lock failed: " + e.getMessage());
            }
        }

        // Fallback to Accessibility lock if Admin fails or isn't enabled
        Log.w(TAG, "Falling back to Accessibility Service to lock screen.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        }
    }

    private boolean checkForPowerKeywords(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        // REMOVED "emergency" to prevent conflict with the lock screen's emergency call button
        if (text != null) {
            String lower = text.toString().toLowerCase();
            if (lower.contains("power off") || lower.contains("restart") ||
                    lower.contains("reboot") || lower.contains("shut down") ||
                    lower.contains("power menu")) {
                return true;
            }
        }
        if (desc != null) {
            String lower = desc.toString().toLowerCase();
            if (lower.contains("power off") || lower.contains("restart") ||
                    lower.contains("reboot") || lower.contains("shut down") ||
                    lower.contains("power menu")) {
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (checkForPowerKeywords(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private void reportBlockedAppToApi(String packageName) {
        String deviceId = pref.getDeviceId();
        if (deviceId == null) return;

        String appName = getReadableAppName(packageName);
        AppBlockRequest request = new AppBlockRequest(deviceId, appName, packageName);
        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);

        apiService.logBlockedApp(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
            }
        });
    }

    private String getReadableAppName(String packageName) {
        PackageManager pm = getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @Override
    public void onInterrupt() {
        Log.e(TAG, "Accessibility Service Interrupted");
    }
}