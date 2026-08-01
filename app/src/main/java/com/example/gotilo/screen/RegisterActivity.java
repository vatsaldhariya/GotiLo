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
import com.example.gotilo.model.RegisterModel;
import com.example.gotilo.utils.DataPref;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvGoToLogin;
    private ProgressBar progressBar;
    private ApiCaller apiCaller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 1. Check if user is already logged in
        DataPref dataPref = new DataPref(this);
        String savedUid = dataPref.getUid();

        if (savedUid != null) {
            Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // 2. Initialize UI Views
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);
        progressBar = findViewById(R.id.progressBar);

        // 3. Initialize API Service
        apiCaller = new ApiCaller();

        // 4. Set Event Listeners
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegistration();
            }
        });

        tvGoToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    private void attemptRegistration() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Input Validation Checks
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

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email format");
            etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password too short");
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            etPassword.requestFocus();
            return;
        }

        // Disable button & show spinner to signal work in progress
        btnRegister.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        // Fetch FCM Push Token before hitting the Backend
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String fcmToken = "";
                    if (task.isSuccessful() && task.getResult() != null) {
                        fcmToken = task.getResult();
                        Log.d("GotiloFCM", "Token retrieved: " + fcmToken);
                    } else {
                        Log.e("GotiloFCM", "Token fetch failed, registering without token", task.getException());
                    }

                    // Execute Database / Server registration call
                    registerUserInDatabase(name, email, password, fcmToken);
                });
    }

    private void registerUserInDatabase(String name, String email, String password, String fcmToken) {
        String deviceId = generateDeviceId(name);

        // Save deviceId permanently into phone memory so your app always knows its ID!
        DataPref dataPref = new DataPref(RegisterActivity.this);
        dataPref.saveDeviceId(deviceId);

        // Call your existing helper method inside ApiCaller
        apiCaller.registerUser(email, password, name, fcmToken, deviceId, new Callback<RegisterModel>() {
            @Override
            public void onResponse(Call<RegisterModel> call, Response<RegisterModel> response) {
                if (response.isSuccessful()) {
                    DataPref prefs = new DataPref(RegisterActivity.this);
                    prefs.saveUid(response.body().getUid());
                    // Always guarantee the FCM token is linked to this deviceId in Firestore!
                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(deviceId)
                            .update("fcmToken", fcmToken)
                            .addOnCompleteListener(task -> {
                                progressBar.setVisibility(View.GONE);
                                btnRegister.setEnabled(true);

                                Toast.makeText(RegisterActivity.this, "Registration Successful! Device ID: " + deviceId, Toast.LENGTH_LONG).show();

                                Intent intent = new Intent(RegisterActivity.this, DashboardActivity.class);
                                startActivity(intent);
                                finish();
                            });
                } else {
                    progressBar.setVisibility(View.GONE);
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterActivity.this, "Registration failed: " + response.message(), Toast.LENGTH_SHORT).show();
                    Log.e("GotiloRegister", "Server response code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<RegisterModel> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("GotiloRegister", "API connection failed", t);
            }
        });
    }

    public static String generateDeviceId(String name) {
        String cleanName = (name == null || name.trim().isEmpty())
                ? "User"
                : name.trim().replaceAll("\\s+", "");

        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder randomLetters = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 5; i++) {
            int index = random.nextInt(alphabet.length());
            randomLetters.append(alphabet.charAt(index));
        }

        return cleanName + randomLetters.toString();
    }
}