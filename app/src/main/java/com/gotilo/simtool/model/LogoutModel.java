package com.gotilo.simtool.model;

import com.google.gson.annotations.SerializedName;

public class LogoutModel {

    @SerializedName("uid")
    private String uid;

    @SerializedName("deviceId")
    private String deviceId;

    public LogoutModel(String uid, String deviceId) {
        this.uid = uid;
        this.deviceId = deviceId;
    }
}