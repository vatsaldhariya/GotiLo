package com.example.gotilo.model;

import com.google.gson.annotations.SerializedName;

public class LoginModel {
    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("message")
    private String message;

    @SerializedName("uid")
    private String uid;

    // 1. Empty Constructor (Add this!)
    public LoginModel() {}

    // 2. Constructor for sending data
    public LoginModel(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getMessage() { return message; }
    public String getUid() { return uid; }
}