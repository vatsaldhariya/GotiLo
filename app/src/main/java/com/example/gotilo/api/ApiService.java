package com.example.gotilo.api;

// 1. ADD THESE IMPORTS to pull the files from your 'model' folder
import com.example.gotilo.model.LocationModel;
import com.example.gotilo.model.LoginModel;
import com.example.gotilo.model.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    // 2. ADD THE < > BRACKETS to prevent the Retrofit parameterization crash

    // Your Register Endpoint
    @POST(appConstants.ENDPOINT_REGISTER)
    Call<RegisterRequest> registerUser(@Body RegisterRequest request);

    // Your Login Endpoint
    @POST(appConstants.ENDPOINT_LOGIN)
    Call<LoginModel> loginUser(@Body LoginModel request);

    // Your NEW Location Endpoint
    @POST(appConstants.ENDPOINT_LOCATION)
    Call<LocationModel> saveLocation(@Body LocationModel request);
}