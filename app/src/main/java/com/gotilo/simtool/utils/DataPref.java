package com.gotilo.simtool.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.util.HashSet;
import java.util.Set;

public class DataPref {

    private static final String PREF_NAME = "GotiloDataPref";
    private static final String KEY_UID = "USER_UID";

    private final SharedPreferences sharedPreferences;

    public DataPref(Context context) {
        // 🔥 FIX: Standard SharedPreferences. 100% reliable across app kills/swipes.
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveName(String name) {
        sharedPreferences.edit().putString("USER_NAME", name).apply();
    }

    public String getName() {
        return sharedPreferences.getString("USER_NAME", "User");
    }

    public void saveEmail(String email) {
        sharedPreferences.edit().putString("USER_EMAIL", email).apply();
    }

    public String getEmail() {
        return sharedPreferences.getString("USER_EMAIL", "");
    }

    public void saveFcmToken(String token) {
        sharedPreferences.edit().putString("FCM_TOKEN", token).apply();
    }

//    public String getFcmToken() {
//        return sharedPreferences.getString("FCM_TOKEN", null);
//    }

    public void saveDeviceId(String deviceId) {
        sharedPreferences.edit().putString("DEVICE_ID", deviceId).apply();
    }

    public String getDeviceId() {
        return sharedPreferences.getString("DEVICE_ID", null);
    }

    public void saveUid(String uid) {
        sharedPreferences.edit().putString(KEY_UID, uid).apply();
    }

    public String getUid() {
        return sharedPreferences.getString(KEY_UID, null);
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
    }

    public void setPendingRecovery(boolean isPending) {
        sharedPreferences.edit().putBoolean("PENDING_RECOVERY", isPending).apply();
    }

    public boolean isPendingRecovery() {
        return sharedPreferences.getBoolean("PENDING_RECOVERY", false);
    }

    public void saveBaselineLocation(double lat, double lng) {
        sharedPreferences.edit().putString("BASE_LAT", String.valueOf(lat)).putString("BASE_LNG", String.valueOf(lng)).apply();
    }

    public Location getBaselineLocation() {
        String latStr = sharedPreferences.getString("BASE_LAT", null);
        String lngStr = sharedPreferences.getString("BASE_LNG", null);
        if (latStr != null && lngStr != null) {
            Location loc = new Location("baseline");
            loc.setLatitude(Double.parseDouble(latStr));
            loc.setLongitude(Double.parseDouble(lngStr));
            return loc;
        }
        return null;
    }

    public void setKioskLocked(boolean isLocked) {
        sharedPreferences.edit().putBoolean("IS_KIOSK_LOCKED", isLocked).apply();
    }

    public boolean isKioskLocked() {
        return sharedPreferences.getBoolean("IS_KIOSK_LOCKED", false);
    }

    public void saveLastSimId(String simId) {
        sharedPreferences.edit().putString("LAST_SIM_ID", simId).apply();
    }

    public String getLastSimId() {
        return sharedPreferences.getString("LAST_SIM_ID", "NONE");
    }

    public void setSimWasRemoved(boolean wasRemoved) {
        sharedPreferences.edit().putBoolean("SIM_WAS_REMOVED", wasRemoved).apply();
    }

    public boolean getSimWasRemoved() {
        return sharedPreferences.getBoolean("SIM_WAS_REMOVED", false);
    }

    public void setAppLockActive(boolean isActive) {
        sharedPreferences.edit().putBoolean("app_lock_active", isActive).apply();
    }

    public boolean isAppLockActive() {
        return sharedPreferences.getBoolean("app_lock_active", false);
    }

    public void setPowerMenuCalibrationActive(boolean active) {
        sharedPreferences.edit().putBoolean("is_calibrating_power_menu", active).apply();
    }

    public boolean isPowerMenuCalibrationActive() {
        return sharedPreferences.getBoolean("is_calibrating_power_menu", false);
    }

    public void savePowerMenuSignature(String packageName, String className) {
        sharedPreferences.edit().putString("power_menu_package", packageName).putString("power_menu_class", className)
                .putBoolean("is_power_menu_calibrated", true).apply();
    }

    public String getPowerMenuPackage() {
        return sharedPreferences.getString("power_menu_package", "");
    }

    public String getPowerMenuClass() {
        return sharedPreferences.getString("power_menu_class", "");
    }

    public boolean isPowerMenuCalibrated() {
        return sharedPreferences.getBoolean("is_power_menu_calibrated", false);
    }

    public void saveBlockedApps(Set<String> packageNames) {
        sharedPreferences.edit().putStringSet("blocked_apps", packageNames).apply();
    }

    public Set<String> getBlockedApps() {
        return sharedPreferences.getStringSet("blocked_apps", new HashSet<>());
    }
}