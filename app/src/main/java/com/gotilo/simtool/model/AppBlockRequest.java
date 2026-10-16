package com.gotilo.simtool.model;

public class AppBlockRequest {
    private String deviceId;
    private String appName;
    private String packageName;

    public AppBlockRequest(String deviceId, String appName, String packageName) {
        this.deviceId = deviceId;
        this.appName = appName;
        this.packageName = packageName;
    }

    public String getDeviceId() { return deviceId; }
    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
}