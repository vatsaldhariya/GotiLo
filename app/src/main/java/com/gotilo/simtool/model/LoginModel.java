package com.gotilo.simtool.model;

import com.google.gson.annotations.SerializedName;

public class LoginModel {
    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("fcmToken")
    private String fcmToken;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("message")
    private String message;

    @SerializedName("uid")
    private String uid;

    @SerializedName("userData")
    private UserData userData;

    // 1. Empty Constructor
    public LoginModel() {}

    // 2. Updated Constructor for sending data to backend
    public LoginModel(String email, String password, String fcmToken, String deviceId) {
        this.email = email;
        this.password = password;
        this.fcmToken = fcmToken;
        this.deviceId = deviceId;
    }

    // Getters
    public String getMessage() { return message; }
    public String getUid() { return uid; }
    public UserData getUserData() { return userData; }

    // ==========================================
    // INNER CLASS: Matches the "userData" JSON block
    // ==========================================
    public static class UserData {
        @SerializedName("deviceId")
        private String deviceId;

        @SerializedName("email")
        private String email;

        // 👇 ADDED THIS: Now Retrofit can read the user's name from the database!
        @SerializedName("name")
        private String name;

        public String getDeviceId() {
            return deviceId;
        }

        public String getEmail() {
            return email;
        }

        // 👇 ADDED THIS: Getter for the name
        public String getName() {
            return name;
        }
    }
}