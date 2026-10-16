package com.gotilo.simtool.model;

public class ForgotPasswordModel {
    private String email;
    private String role;
    private boolean success;
    private String message;

    // Constructor for making the request
    public ForgotPasswordModel(String email, String role) {
        this.email = email;
        this.role = role;
    }

    // Getters for handling the response
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}