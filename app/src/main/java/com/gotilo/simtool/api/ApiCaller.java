package com.gotilo.simtool.api;

import com.gotilo.simtool.model.EventModel;
import com.gotilo.simtool.model.ForgotPasswordModel;
import com.gotilo.simtool.model.LocationModel;
import com.gotilo.simtool.model.LoginModel;
import com.gotilo.simtool.model.LogoutModel;
import com.gotilo.simtool.model.MediaModel;
import com.gotilo.simtool.model.SimModel;

import retrofit2.Call;
import retrofit2.Callback;

public class ApiCaller {
    private final ApiService apiService;

    public ApiCaller() {
        apiService = RetrofitClient.getClient().create(ApiService.class);
    }

    public void loginUser(String email, String password, String fcmToken, String deviceId, Callback<LoginModel> callback) {
        LoginModel request = new LoginModel(email, password, fcmToken, deviceId);
        Call<LoginModel> call = apiService.loginUser(request);
        call.enqueue(callback);
    }

    // 👇 Logout Method
    public void logoutUser(String uid, String deviceId, retrofit2.Callback<Void> callback) {
        LogoutModel request = new LogoutModel(uid, deviceId);
        retrofit2.Call<Void> call = apiService.logoutUser(request);
        call.enqueue(callback);
    }

    // 👇 RESTORED THIS! (You accidentally deleted this method)
    public void saveLocation(String userId, String deviceId, double lat, double lng, String address, Callback<LocationModel> callback) {
        LocationModel request = new LocationModel(userId, deviceId, lat, lng, address, "normal", System.currentTimeMillis());
        Call<LocationModel> call = apiService.saveLocation(request);
        call.enqueue(callback);
    }

    public void saveMedia(String deviceId, String mediaType, String frontUrl, String backUrl, String audioUrl, String videoFrontUrl, String videoBackUrl, String commandUsed, Callback<MediaModel> callback) {
        MediaModel request = new MediaModel(deviceId, mediaType, frontUrl, backUrl, audioUrl, videoFrontUrl, videoBackUrl, commandUsed);
        Call<MediaModel> call = apiService.saveMedia(request);
        call.enqueue(callback);
    }

    public void saveEventData(String deviceId, String reason, String frontUrl, String backUrl, String audioUrl, String videoFrontUrl, String videoBackUrl, Double lat, Double lng, String address, Callback<EventModel> callback) {
        EventModel request = new EventModel(deviceId, reason, frontUrl, backUrl, audioUrl, videoFrontUrl, videoBackUrl, lat, lng, address);
        Call<EventModel> call = apiService.saveEventData(request);
        call.enqueue(callback);
    }

    public void saveSimInfo(String deviceId, String carrierName, String simSerial, String phoneNumber, Callback<com.gotilo.simtool.model.SimModel> callback) {
        SimModel request = new SimModel(deviceId, carrierName, simSerial, phoneNumber);
        apiService.saveSimInfo(request).enqueue(callback);
    }

    public void requestPasswordReset(String email, String role, Callback<ForgotPasswordModel> callback) {
        ForgotPasswordModel request = new ForgotPasswordModel(email, role);
        apiService.requestPasswordReset(request).enqueue(callback);
    }
}