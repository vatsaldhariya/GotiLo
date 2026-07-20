package com.example.gotilo.model;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {

    // --- Data you SEND to the server ---
    @SerializedName("email")
    private String email;

    @SerializedName("password")
    private String password;

    @SerializedName("name")
    private String name;

    // --- Data you RECEIVE from the server ---
    @SerializedName("message")
    private String message;

    @SerializedName("uid")
    private String uid;

    @SerializedName("error")
    private String error;

    // Constructor for sending data
    public RegisterRequest(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    // Getters for Request (Optional)

    public String getName() { return name; }
    public String getMessage() { return message; }
    public String getUid() { return uid; }
}