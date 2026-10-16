package com.gotilo.simtool.model;

import com.google.gson.annotations.SerializedName;

public class SimModel {
    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("carrierName")
    private String carrierName;

    @SerializedName("simSerial")
    private String simSerial;

    @SerializedName("phoneNumber")
    private String phoneNumber;

    public SimModel(String deviceId, String carrierName, String simSerial, String phoneNumber) {
        this.deviceId = deviceId;
        this.carrierName = carrierName;
        this.simSerial = simSerial;
        this.phoneNumber = phoneNumber;
    }

    // --- GETTER METHODS ---
    public String getCarrierName() {
        return carrierName != null ? carrierName : "";
    }

    public String getSimSerial() {
        return simSerial != null ? simSerial : "";
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }
}