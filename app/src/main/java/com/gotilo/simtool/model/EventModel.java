package com.gotilo.simtool.model;

import com.google.gson.annotations.SerializedName;

public class EventModel {

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("reason")
    private String reason;

    @SerializedName("frontUrl")
    private String frontUrl;

    @SerializedName("backUrl")
    private String backUrl;

    @SerializedName("audioUrl")
    private String audioUrl;

    // 👇 NEW: Added video URL fields
    @SerializedName("videoFrontUrl")
    private String videoFrontUrl;

    @SerializedName("videoBackUrl")
    private String videoBackUrl;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("address")
    private String address;

    public EventModel() {}

    // 👇 NEW: Constructor now accepts all 10 parameters
    public EventModel(String deviceId, String reason, String frontUrl, String backUrl, String audioUrl,
                      String videoFrontUrl, String videoBackUrl,
                      Double latitude, Double longitude, String address) {
        this.deviceId = deviceId;
        this.reason = reason;
        this.frontUrl = frontUrl;
        this.backUrl = backUrl;
        this.audioUrl = audioUrl;
        this.videoFrontUrl = videoFrontUrl;
        this.videoBackUrl = videoBackUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    // Getters
    public String getDeviceId() { return deviceId; }
    public String getReason() { return reason; }
    public String getFrontUrl() { return frontUrl; }
    public String getBackUrl() { return backUrl; }
    public String getAudioUrl() { return audioUrl; }
    public String getVideoFrontUrl() { return videoFrontUrl; }
    public String getVideoBackUrl() { return videoBackUrl; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getAddress() { return address; }
}