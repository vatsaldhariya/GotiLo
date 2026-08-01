package com.example.gotilo.api;

import com.example.gotilo.model.LocationModel;
import com.example.gotilo.model.LoginModel;
import com.example.gotilo.model.RegisterModel;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST(AppConstants.ENDPOINT_REGISTER)
    Call<RegisterModel> registerUser(@Body RegisterModel request);

    @POST(AppConstants.ENDPOINT_LOGIN)
    Call<LoginModel> loginUser(@Body LoginModel request);

    @POST("saveLocation")
    Call<LocationModel> saveLocation(
            @Body LocationModel request
    );
}