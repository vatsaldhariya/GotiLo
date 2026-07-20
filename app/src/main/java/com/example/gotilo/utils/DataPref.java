package com.example.gotilo.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class DataPref {

    private static final String PREF_NAME = "GotiloDataPref";
    private static final String KEY_UID = "USER_UID";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public DataPref(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    // 1. Save the UID when they log in
    public void saveUid(String uid) {
        editor.putString(KEY_UID, uid);
        editor.apply();
    }

    // 2. Get the UID (Returns null if they are not logged in)
    public String getUid() {
        return sharedPreferences.getString(KEY_UID, null);
    }

    // 3. Clear the UID when they log out
    public void clear() {
        editor.clear();
        editor.apply();
    }
}