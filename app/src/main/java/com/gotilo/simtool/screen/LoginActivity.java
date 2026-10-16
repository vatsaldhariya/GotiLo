package com.gotilo.simtool.screen;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gotilo.simtool.R;
import com.gotilo.simtool.api.ApiCaller;
import com.gotilo.simtool.model.ForgotPasswordModel;
import com.gotilo.simtool.model.LoginModel;
import com.gotilo.simtool.utils.DataPref;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvForgotPassword;
    private ProgressBar progressBar;
    private ApiCaller apiCaller;
    private static final int LOCATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_login);

            DataPref dataPref = new DataPref(this);
            if (dataPref.getUid() != null && !dataPref.getUid().isEmpty()) {
                // User is already logged in! Skip this screen.
                Intent intent = new Intent(this, DashboardActivity.class);
                startActivity(intent);
                finish();
                return; // Stop loading the login screen
            }

            // Initialize Views
            tilEmail = findViewById(R.id.tilLoginEmail);
            tilPassword = findViewById(R.id.tilLoginPassword);
            etEmail = findViewById(R.id.etLoginEmail);
            etPassword = findViewById(R.id.etLoginPassword);
            btnLogin = findViewById(R.id.btnLogin);
            progressBar = findViewById(R.id.loginProgressBar);
            tvForgotPassword = findViewById(R.id.tvForgotPassword);

            apiCaller = new ApiCaller();

            btnLogin.setOnClickListener(v -> {
                try {
                    attemptLogin();
                } catch (Exception e) {
                    Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("LOGIN_CRASH", "Error in button click", e);
                }
            });

            if (tvForgotPassword != null) {
                tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
            }

            // Start visual entry animations
            animateScreenEntry();

        } catch (Exception e) {
            Toast.makeText(this, "Screen Load Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        checkAndRequestLocationPermissions();
    }

    // ==========================================
    // VISUAL EFFECTS & ANIMATIONS
    // ==========================================
    private void animateScreenEntry() {
        View logoBadge = findViewById(R.id.logoBadge);
        View tvTitle = findViewById(R.id.tvTitle);
        View tvSubtitle = findViewById(R.id.tvSubtitle);
        View formContainer = findViewById(R.id.formContainer);
        View btnLoginWrapper = findViewById(R.id.btnLoginWrapper);

        // Set initial transparent and offset states
        logoBadge.setAlpha(0f);
        logoBadge.setScaleX(0.5f);
        logoBadge.setScaleY(0.5f);

        tvTitle.setAlpha(0f);
        tvTitle.setTranslationY(30f);

        tvSubtitle.setAlpha(0f);
        tvSubtitle.setTranslationY(30f);

        formContainer.setAlpha(0f);
        formContainer.setTranslationY(50f);

        btnLoginWrapper.setAlpha(0f);
        btnLoginWrapper.setTranslationY(50f);

        // Chain the animations with slight delays for a cascading effect
        logoBadge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).setInterpolator(new OvershootInterpolator()).start();
        tvTitle.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(100).start();
        tvSubtitle.animate().alpha(1f).translationY(0f).setDuration(500).setStartDelay(150).start();
        formContainer.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(250).start();
        btnLoginWrapper.animate().alpha(1f).translationY(0f).setDuration(600).setStartDelay(350).start();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            // Shrink button slightly, fade it, remove text, show spinner
            btnLogin.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.8f).setDuration(150).start();
            btnLogin.setText("");
            btnLogin.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setAlpha(0f);
            progressBar.animate().alpha(1f).setDuration(200).start();
        } else {
            // Restore button size, text, and hide spinner
            btnLogin.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start();
            btnLogin.setText("Sign In");
            btnLogin.setEnabled(true);
            progressBar.animate().alpha(0f).setDuration(200).withEndAction(() -> progressBar.setVisibility(View.GONE)).start();
        }
    }

    // ==========================================
    // FORGOT PASSWORD DIALOG LOGIC
    // ==========================================
    private void showForgotPasswordDialog() {
        String enteredEmail = etEmail.getText().toString().trim();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password");
        builder.setMessage("Enter your registered email address. A password reset notification will be sent to the Master Admin.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setPadding(50, 40, 50, 40);

        if (!TextUtils.isEmpty(enteredEmail)) {
            input.setText(enteredEmail);
            input.setSelection(enteredEmail.length());
        }
        builder.setView(input);

        builder.setPositiveButton("Submit", (dialog, which) -> {
            String email = input.getText().toString().trim();
            if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(LoginActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            performPasswordReset(email);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void performPasswordReset(String email) {
        String userRole = "USER";
        Toast.makeText(this, "Sending request...", Toast.LENGTH_SHORT).show();

        apiCaller.requestPasswordReset(email, userRole, new Callback<ForgotPasswordModel>() {
            @Override
            public void onResponse(@NonNull Call<ForgotPasswordModel> call, @NonNull Response<ForgotPasswordModel> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().isSuccess()) {
                        Toast.makeText(LoginActivity.this, "Request sent! The Admin has been notified.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, response.body().getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    String errorMsg = parseBackendError(response);
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ForgotPasswordModel> call, @NonNull Throwable t) {
                Toast.makeText(LoginActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ==========================================
    // LOGIN LOGIC
    // ==========================================
    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 1. Reset previous errors
        tilEmail.setError(null);
        tilPassword.setError(null);
        boolean isValid = true;

        // 2. Client-side Validation
        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Please enter a valid email address");
            isValid = false;
        }

        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            isValid = false;
        }

        if (!isValid) {
            return; // Stop execution if validation fails
        }

        // Trigger the visual loading state
        showLoading(true);

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    String fcmToken = "";
                    if (task.isSuccessful() && task.getResult() != null) {
                        fcmToken = task.getResult();
                    }

                    DataPref dataPref = new DataPref(LoginActivity.this);
                    String deviceId = dataPref.getDeviceId();

                    if (deviceId == null || deviceId.trim().isEmpty()) {
                        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                    }

                    loginUserInDatabase(email, password, fcmToken, deviceId);
                });
    }

    private void loginUserInDatabase(String email, String password, String fcmToken, String deviceId) {
        apiCaller.loginUser(email, password, fcmToken, deviceId, new Callback<LoginModel>() {
            @Override
            public void onResponse(@NonNull Call<LoginModel> call, @NonNull Response<LoginModel> response) {

                showLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    DataPref prefs = new DataPref(LoginActivity.this);

                    prefs.saveUid(response.body().getUid());
                    prefs.saveEmail(email);

                    // 👇 FIX: SAVE THE FCM TOKEN AND DEVICE ID TO LOCAL PREFS
                    prefs.saveFcmToken(fcmToken);
                    prefs.saveDeviceId(deviceId);

                    if (response.body().getUserData() != null) {
                        if (response.body().getUserData().getName() != null) {
                            prefs.saveName(response.body().getUserData().getName());
                        }
                    }

                    Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
                    finish();

                } else {
                    String backendError = parseBackendError(response);
                    if (backendError.toLowerCase().contains("email") || backendError.toLowerCase().contains("user")) {
                        tilEmail.setError(backendError);
                    } else if (backendError.toLowerCase().contains("password") || backendError.toLowerCase().contains("credentials")) {
                        tilPassword.setError(backendError);
                    } else {
                        Toast.makeText(LoginActivity.this, backendError, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<LoginModel> call, @NonNull Throwable t) {
                showLoading(false);
                Toast.makeText(LoginActivity.this, "Network Error. Please check your connection.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Helper method to extract the actual error message sent from your API Backend.
     */
    private String parseBackendError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                String errorBodyStr = response.errorBody().string();
                JSONObject jsonObject = new JSONObject(errorBodyStr);

                if (jsonObject.has("message")) {
                    return jsonObject.getString("message");
                } else if (jsonObject.has("error")) {
                    return jsonObject.getString("error");
                }
            }
        } catch (Exception e) {
            Log.e("API_ERROR_PARSE", "Failed to parse error body", e);
        }
        return "Login failed: Invalid credentials or server error";
    }

    // ==========================================
    // PERMISSIONS LOGIC
    // ==========================================
    private void checkAndRequestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_CODE);
        } else {
            checkIfGpsIsEnabled();
        }
    }

    private void checkIfGpsIsEnabled() {
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gpsEnabled = false;
        try {
            gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            Log.e("GOTILO_GPS", "Failed to check GPS status", e);
        }

        if (!gpsEnabled) {
            Toast.makeText(this, "Please turn ON Location Services (GPS)", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkIfGpsIsEnabled();
        }
    }
}