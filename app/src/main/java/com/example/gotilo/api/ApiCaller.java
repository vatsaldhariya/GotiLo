package com.example.gotilo.api;

import com.example.gotilo.model.LocationModel;
import com.example.gotilo.model.LoginModel;
import com.example.gotilo.model.RegisterModel;

import retrofit2.Call;
import retrofit2.Callback;

public class ApiCaller {
    private final ApiService apiService;

    public ApiCaller() {
        apiService = RetrofitClient.getClient().create(ApiService.class);
    }

    public ApiService getApiService() {
        return apiService;
    }

    public void registerUser(String email, String password, String name, String fcmToken, String deviceId, Callback<RegisterModel> callback) {
        RegisterModel request = new RegisterModel(email, password, name, fcmToken, deviceId);
        Call<RegisterModel> call = apiService.registerUser(request);
        call.enqueue(callback);
    }

    public void loginUser(String email, String password, Callback<LoginModel> callback) {
        LoginModel request = new LoginModel(email, password);
        Call<LoginModel> call = apiService.loginUser(request);
        call.enqueue(callback);
    }

    public void saveLocation(String userId, String deviceId, double lat, double lng, String address, Callback<LocationModel> callback) {
        // Pass System.currentTimeMillis() as the 7th parameter:
        LocationModel request = new LocationModel(userId, deviceId, lat, lng, address, "normal", System.currentTimeMillis());
        Call<LocationModel> call = apiService.saveLocation(request);
        call.enqueue(callback);
    }
}