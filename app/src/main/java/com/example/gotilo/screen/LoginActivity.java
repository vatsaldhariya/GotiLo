package com.example.gotilo.screen;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gotilo.R;
import com.example.gotilo.api.ApiCaller;
import com.example.gotilo.model.LoginModel;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoToRegister;
    private ProgressBar progressBar;
    private ApiCaller apiCaller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_login);

            // Initialize Views
            etEmail = findViewById(R.id.etLoginEmail);
            etPassword = findViewById(R.id.etLoginPassword);
            btnLogin = findViewById(R.id.btnLogin);
            tvGoToRegister = findViewById(R.id.tvGoToRegister);
            progressBar = findViewById(R.id.loginProgressBar);

            // Initialize API Caller
            apiCaller = new ApiCaller();

            // Login Button Click
            btnLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        attemptLogin();
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this, "Crash Prevented: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("LOGIN_CRASH", "Error in button click", e);
                    }
                }
            });

            // Go to Register Page Click
            tvGoToRegister.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    startActivity(intent);
                    finish();
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Screen Load Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Basic Validation
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            etEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        // Show loading state
        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);

        // Make the API Call
        apiCaller.loginUser(email, password, new Callback<LoginModel>() {
            @Override
            public void onResponse(Call<LoginModel> call, Response<LoginModel> response) {
                try {
                    progressBar.setVisibility(View.GONE);
                    btnLogin.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null) {
                        String msg = response.body().getMessage();
                        if (msg == null) msg = "Login Successful!";
                        Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();

                        String userUid = response.body().getUid();

                        // ---> NEW: Save the UID permanently in DataPref <---
                        com.example.gotilo.utils.DataPref dataPref = new com.example.gotilo.utils.DataPref(LoginActivity.this);
                        dataPref.saveUid(userUid);

                        // Navigate to Dashboard
                        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                        intent.putExtra("LOGGED_IN_UID", userUid);
                        startActivity(intent);
                        finish();

                    } else {
                        // Server error (e.g., wrong password or user doesn't exist)
                        Toast.makeText(LoginActivity.this, "Server Error Code: " + response.code(), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(LoginActivity.this, "Response Parse Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<LoginModel> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnLogin.setEnabled(true);

                // Network failure
                Toast.makeText(LoginActivity.this, "Network/API Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("LOGIN_API_ERROR", "Error: ", t);
            }
        });
    }
}