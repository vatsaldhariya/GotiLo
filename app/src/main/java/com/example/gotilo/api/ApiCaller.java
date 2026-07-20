package com.example.gotilo.api;
import com.example.gotilo.model.LocationModel;
import com.example.gotilo.model.LoginModel;
import com.example.gotilo.model.RegisterRequest;

import retrofit2.Call;
import retrofit2.Callback;

public class ApiCaller {
    private ApiService apiService;

    public ApiCaller() {
        // Initialize the service
        apiService = RetrofitClient.getClient().create(ApiService.class);
    }

    public void registerUser(String email, String password, String name, Callback<RegisterRequest> callback) {
        RegisterRequest request = new RegisterRequest(email, password, name);
        Call<RegisterRequest> call = apiService.registerUser(request);

        // enqueue() runs the request asynchronously on a background thread
        call.enqueue(callback);
    }

    public void loginUser(String email, String password, Callback callback) {
        LoginModel request = new LoginModel(email, password);
        Call call = apiService.loginUser(request);
        call.enqueue(callback);
    }

    // ---> ADD THIS ENTIRE BLOCK <---
    public void saveLocation(String userId, double lat, double lng, Callback callback) {
        // 1. Package the data using the LocationModel we created earlier
        LocationModel request = new LocationModel(userId, lat, lng);

        // 2. Call the endpoint defined in ApiService
        Call call = apiService.saveLocation(request);

        // 3. Put it in the queue to execute
        call.enqueue(callback);

    }


}