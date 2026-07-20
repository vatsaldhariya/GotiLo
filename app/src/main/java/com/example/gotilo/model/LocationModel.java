package com.example.gotilo.model;

import com.google.gson.annotations.SerializedName;

public class LocationModel {

    // ==========================================
    // Data we SEND to your Node.js backend
    // ==========================================
    @SerializedName("userId")
    private String userId;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("name")
    private String name;

    @SerializedName("trackingType")
    private String trackingType;

    // ==========================================
    // Data we RECEIVE back from your backend
    // ==========================================
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    // 1. Required empty constructor (Prevents Gson crashes)
    public LocationModel() {}

    // 2. Constructor for packaging the GPS data to send
    public LocationModel(String userId, double latitude, double longitude) {
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;

        // These match the optional fields in your Node.js backend
        this.name = "My Android Phone";
        this.trackingType = "ONCE";
    }

    // 3. Getters to read the success message from the server
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}