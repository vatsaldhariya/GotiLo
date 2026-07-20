package com.example.gotilo.screen;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gotilo.R;
import com.example.gotilo.api.ApiCaller;
import com.example.gotilo.model.RegisterRequest; // Or RegisterRequest based on what you named it earlier!

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvGoToLogin; // <-- NEW
    private ProgressBar progressBar;
    private ApiCaller apiCaller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // ---> NEW: Check if the user is already logged in! <---
        com.example.gotilo.utils.DataPref dataPref = new com.example.gotilo.utils.DataPref(this);
        String savedUid = dataPref.getUid();

        if (savedUid != null) {
            // They are already logged in! Send them straight to the Dashboard.
            Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
            intent.putExtra("LOGGED_IN_UID", savedUid);
            startActivity(intent);
            finish();
            return; // Stop running the rest of the RegisterActivity code
        }

        // Initialize Views
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin); // <-- NEW
        progressBar = findViewById(R.id.progressBar);

        // Initialize API Caller
        apiCaller = new ApiCaller();

        // Register Button Click
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegistration();
            }
        });

        // NEW: Go to Login Page Click
        tvGoToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
                finish(); // This closes the register screen so hitting the "Back" button doesn't trap the user
            }
        });
    }

    private void attemptRegistration() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Basic Validation
        if (name.isEmpty()) {
            etName.setError("Name is required");
            etName.requestFocus();
            return;
        }
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
        btnRegister.setEnabled(false);

        // Make the API Call
        apiCaller.registerUser(email, password, name, new Callback<RegisterRequest>() {
            @Override
            public void onResponse(Call<RegisterRequest> call, Response<RegisterRequest> response) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    // Registration Success
                    String msg = response.body().getMessage();
                    Toast.makeText(RegisterActivity.this, "Success: " + msg, Toast.LENGTH_LONG).show();

                    // Automatically take them to the Login screen after successful registration
                    Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
                    startActivity(intent);
                    finish();

                } else {
                    // Server error
                    Toast.makeText(RegisterActivity.this, "Registration Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<RegisterRequest> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);

                // Network failure
                Toast.makeText(RegisterActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}