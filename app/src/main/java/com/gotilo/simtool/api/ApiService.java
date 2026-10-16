package com.gotilo.simtool.api;

import com.gotilo.simtool.model.AppBlockRequest;
import com.gotilo.simtool.model.ForgotPasswordModel;
import com.gotilo.simtool.model.LocationModel;
import com.gotilo.simtool.model.LoginModel;
import com.gotilo.simtool.model.LogoutModel;
import com.gotilo.simtool.model.MediaModel;
import com.gotilo.simtool.model.EventModel;
import com.gotilo.simtool.model.SimModel;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST(AppConstants.ENDPOINT_LOGIN)
    Call<LoginModel> loginUser(@Body LoginModel request);

    @POST("saveLocation")
    Call<LocationModel> saveLocation(@Body LocationModel request);

    @POST("saveMedia")
    Call<MediaModel> saveMedia(@Body MediaModel request);

    // NEW EVENT ENDPOINT
    @POST("saveEventData")
    Call<EventModel> saveEventData(@Body EventModel request);

    @POST("saveSimInfo")
    Call<SimModel> saveSimInfo(@Body SimModel request);

    @POST("logBlockedApp")
    Call<ResponseBody> logBlockedApp(@Body AppBlockRequest request);

    @POST("forgot-password-request")
    Call<ForgotPasswordModel> requestPasswordReset(@Body ForgotPasswordModel request);

    @POST(AppConstants.ENDPOINT_LOGOUT)
    Call<Void> logoutUser(@Body LogoutModel request);
}