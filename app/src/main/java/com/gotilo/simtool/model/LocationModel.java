package com.gotilo.simtool.model;

import com.google.gson.annotations.SerializedName;

public class LocationModel {

    @SerializedName("uid")
    private String uid;

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("address")
    private String address;

    @SerializedName("alert_status")
    private String alertStatus;

    // ADD THIS FIELD:
    @SerializedName("timestamp")
    private long timestamp;

    // Empty constructor (required for Gson/Firebase serialization)
    public LocationModel() {}

    // Complete 7-argument constructor (Updated with timestamp)
    public LocationModel(String uid, String deviceId, double latitude, double longitude, String address, String alertStatus, long timestamp) {
        this.uid = uid;
        this.deviceId = deviceId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.alertStatus = alertStatus;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAlertStatus() {
        return alertStatus;
    }

    public void setAlertStatus(String alertStatus) {
        this.alertStatus = alertStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}