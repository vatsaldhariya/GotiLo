package com.example.gotilo.model;

import com.google.gson.annotations.SerializedName;

public class RegisterModel {

    // --- Data you SEND to the server ---
    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("name")
    private String name;
    @SerializedName("fcmToken")
    private String fcmToken;

    // --- Data you RECEIVE from the server ---
    @SerializedName("message")
    private String message;

    @SerializedName("uid")
    private String uid;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("error")
    private String error;

    // Constructor for sending data
    public RegisterModel(String email, String password, String name, String fcmToken, String deviceId) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.fcmToken = fcmToken;
        this.deviceId = deviceId;
    }

    // Getters for Request (Optional)

    public String getName() { return name; }
    public String getMessage() { return message; }
    public String getUid() { return uid; }
    public String getDeviceId() {
        return deviceId;
    }
}