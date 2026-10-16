package com.gotilo.simtool.model;

import com.google.gson.annotations.SerializedName;

public class MediaModel {
    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("mediaType")
    private String mediaType;

    @SerializedName("frontUrl")
    private String frontUrl;

    @SerializedName("backUrl")
    private String backUrl;

    @SerializedName("audioUrl")
    private String audioUrl;

    @SerializedName("videoFrontUrl")
    private String videoFrontUrl;

    @SerializedName("videoBackUrl")
    private String videoBackUrl;

    @SerializedName("command_used")
    private String commandUsed;

    public MediaModel() {}

    public MediaModel(String deviceId, String mediaType, String frontUrl, String backUrl, String audioUrl, String videoFrontUrl, String videoBackUrl, String commandUsed) {
        this.deviceId = deviceId;
        this.mediaType = mediaType;
        this.frontUrl = frontUrl;
        this.backUrl = backUrl;
        this.audioUrl = audioUrl;
        this.videoFrontUrl = videoFrontUrl;
        this.videoBackUrl = videoBackUrl;
        this.commandUsed = commandUsed;
    }

    public String getDeviceId() { return deviceId; }
    public String getMediaType() { return mediaType; }
    public String getFrontUrl() { return frontUrl; }
    public String getBackUrl() { return backUrl; }
    public String getAudioUrl() { return audioUrl; }
    public String getVideoFrontUrl() { return videoFrontUrl; }
    public String getVideoBackUrl() { return videoBackUrl; }
    public String getCommandUsed() { return commandUsed; }
}