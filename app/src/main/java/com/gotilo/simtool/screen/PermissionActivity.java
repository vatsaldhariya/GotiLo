package com.gotilo.simtool.screen;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gotilo.simtool.R;
import com.gotilo.simtool.service.MyAdminReceiver;
import com.gotilo.simtool.service.YourAccessibilityService;
import com.gotilo.simtool.utils.DataPref;

public class PermissionActivity extends AppCompatActivity {

    private android.app.Dialog lockScreenDialog;

    private DataPref pref;
    private Button btnStandardPerms, btnSmsPerms, btnOverlayPerm, btnBatteryPerm, btnAppLockerPerm, btnDeviceAdminPerm, btnContinue;
    private View cardAppSelection;
    private Button btnSelectApps;

    // Constants for ActivityResult Tracking
    private static final int REQ_CODE_OVERLAY = 2001;
    private static final int REQ_CODE_BATTERY = 2002;
    private static final int REQ_CODE_ACCESSIBILITY = 2003;
    private static final int REQ_CODE_DEVICE_ADMIN = 2004;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        pref = new DataPref(this);

        btnStandardPerms = findViewById(R.id.btnStandardPerms);
        btnSmsPerms = findViewById(R.id.btnSmsPerms);
        btnOverlayPerm = findViewById(R.id.btnOverlayPerm);
        btnBatteryPerm = findViewById(R.id.btnBatteryPerm);
        btnAppLockerPerm = findViewById(R.id.btnAppLockerPerm);
        btnDeviceAdminPerm = findViewById(R.id.btnDeviceAdminPerm);
        btnContinue = findViewById(R.id.btnContinue);
        cardAppSelection = findViewById(R.id.cardAppSelection);
        btnSelectApps = findViewById(R.id.btnSelectApps);

        // 1. Standard Permissions
        btnStandardPerms.setOnClickListener(v -> requestStandardPermissions());

        // 2. SMS Command Permissions
        btnSmsPerms.setOnClickListener(v -> requestSmsPermissions());

        // 3. Display Over Other Apps (Overlay)
        btnOverlayPerm.setOnClickListener(v -> requestOverlayPermission());

        // 4. Disable Battery Optimization
        btnBatteryPerm.setOnClickListener(v -> requestBatteryPermission());

        // 5. App Locker (Accessibility)
        btnAppLockerPerm.setOnClickListener(v -> requestAccessibilityPermission());

        // Select Apps Screen
        btnSelectApps.setOnClickListener(v -> {
            Intent intent = new Intent(PermissionActivity.this, AppSelectionActivity.class);
            startActivity(intent);
        });

        // 6. Device Admin Permission
        btnDeviceAdminPerm.setOnClickListener(v -> requestDeviceAdminPermission());

        // Continue
        btnContinue.setOnClickListener(v -> navigateToDashboard());

        checkLockScreenSecurity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkLockScreenSecurity();

        // Add a slight delay to allow Android OS databases to update before checking
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAllPermissionsAndNavigate, 200);
    }

    // ==========================================
    // PERMISSION REQUEST METHODS
    // ==========================================

    private void requestStandardPermissions() {
        String[] perms;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS
            };
        } else {
            perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            };
        }
        ActivityCompat.requestPermissions(this, perms, 100);
    }

    private void requestSmsPermissions() {
        String[] smsPerms = new String[]{
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS
        };
        ActivityCompat.requestPermissions(this, smsPerms, 103);
    }

    private void requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please select 'Allow all the time' for live tracking.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 101);
            }
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_CODE_OVERLAY);
            } catch (Exception e) {
                // Fallback for some Chinese ROMs that crash on the standard intent
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                startActivityForResult(intent, REQ_CODE_OVERLAY);
            }
        }
    }

    private void requestBatteryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_CODE_BATTERY);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivityForResult(intent, REQ_CODE_BATTERY);
                }
            }
        }
    }

    private void requestAccessibilityPermission() {
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityProminentDisclosure();
        }
    }

    private void requestDeviceAdminPermission() {
        if (!isDeviceAdminActive()) {
            showDeviceAdminProminentDisclosure();
        }
    }

    // ==========================================
    // PROMINENT DISCLOSURE DIALOGS (CUSTOM UI)
    // ==========================================

    private void showAccessibilityProminentDisclosure() {
        String title = "Accessibility Service Required";
        String message = "Gotilo requires the Accessibility Service API to protect your device from theft.\n\n" +
                "We use this service to:\n" +
                "• Detect and block intruders from opening the Power Menu so they cannot turn off your phone.\n" +
                "• Block access to the Status Bar/Quick Settings so they cannot disable Wi-Fi or Location.\n" +
                "• Restrict access to your locked apps.\n\n" +
                "Gotilo does not use this service to collect, store, or share your personal data.";

        showCustomProminentDisclosure(title, message, "Agree", () -> {
            // User agreed, now take them to settings
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, REQ_CODE_ACCESSIBILITY);
            Toast.makeText(this, "Please find 'Gotilo App Locker' and turn it ON.", Toast.LENGTH_LONG).show();
        });
    }

    private void showDeviceAdminProminentDisclosure() {
        String title = "Device Admin Required";
        String message = "Gotilo requires Device Administrator privileges to secure your phone.\n\n" +
                "We use this permission strictly to instantly lock your device screen if unauthorized access, tampering, or theft is detected. This prevents thieves from bypassing your security.\n\n" +
                "To uninstall the app later, you must disable this permission first.";

        showCustomProminentDisclosure(title, message, "Enable", () -> {
            // User agreed, request the system permission
            ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Gotilo needs to force-lock the screen if an intruder tries to tamper with the device.");
            startActivityForResult(intent, REQ_CODE_DEVICE_ADMIN);
        });
    }

    // Helper method to build and show the gorgeous custom dialog
    private void showCustomProminentDisclosure(String title, String message, String positiveBtnText, Runnable onAgree) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_prominent_disclosure);
        dialog.setCancelable(false);

        // Make the default dialog window transparent so our custom rounded corners show up
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        android.widget.TextView tvTitle = dialog.findViewById(R.id.dialogTitle);
        android.widget.TextView tvMessage = dialog.findViewById(R.id.dialogMessage);
        com.google.android.material.button.MaterialButton btnCancel = dialog.findViewById(R.id.btnDialogCancel);
        com.google.android.material.button.MaterialButton btnAgree = dialog.findViewById(R.id.btnDialogAgree);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnAgree.setText(positiveBtnText);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAgree.setOnClickListener(v -> {
            dialog.dismiss();
            onAgree.run(); // Executes the intent logic passed into the method
        });

        dialog.show();
    }

    // ==========================================
    // RESULT HANDLERS
    // ==========================================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            requestBackgroundLocation();
        }
        // Always trigger a UI check after standard permission dialogs close
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAllPermissionsAndNavigate, 200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // This is crucial! When the user returns from ANY settings screen, we MUST re-check UI.
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAllPermissionsAndNavigate, 200);
    }

    // ==========================================
    // CHECKERS & UI UPDATERS
    // ==========================================

    private boolean isAccessibilityServiceEnabled() {
        String expectedComponentName = new ComponentName(this, YourAccessibilityService.class).flattenToString();
        String enabledServices = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        if (enabledServices != null) {
            TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
            colonSplitter.setString(enabledServices);
            while (colonSplitter.hasNext()) {
                String componentName = colonSplitter.next();
                if (componentName.equalsIgnoreCase(expectedComponentName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(adminComponent);
    }

    private void checkAllPermissionsAndNavigate() {
        boolean allGranted = true;

        android.content.res.ColorStateList colorSuccess = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"));
        android.content.res.ColorStateList colorDisabled = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#9E9E9E"));

        // 1. Standard Permissions
        boolean hasStandard = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                hasStandard = false;
            }
        }

        if (hasStandard) {
            btnStandardPerms.setText("✓ Standard Permissions Granted");
            btnStandardPerms.setEnabled(false);
            btnStandardPerms.setBackgroundTintList(colorSuccess);
        } else {
            allGranted = false;
        }

        // 2. SMS Permissions
        boolean hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        if (hasSms) {
            btnSmsPerms.setText("✓ SMS Permissions Granted");
            btnSmsPerms.setEnabled(false);
            btnSmsPerms.setBackgroundTintList(colorSuccess);
        } else {
            allGranted = false;
        }

        // 3. Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                btnOverlayPerm.setText("✓ Overlay Permission Granted");
                btnOverlayPerm.setEnabled(false);
                btnOverlayPerm.setBackgroundTintList(colorSuccess);
            } else {
                allGranted = false;
            }
        }

        // 4. Battery Optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                btnBatteryPerm.setText("✓ Battery Saver Disabled");
                btnBatteryPerm.setEnabled(false);
                btnBatteryPerm.setBackgroundTintList(colorSuccess);
            } else {
                allGranted = false;
            }
        }

        // 5. App Locker (Accessibility)
        if (isAccessibilityServiceEnabled()) {
            btnAppLockerPerm.setText("✓ App Locker Enabled");
            btnAppLockerPerm.setEnabled(false);
            btnAppLockerPerm.setBackgroundTintList(colorSuccess);
            cardAppSelection.setVisibility(View.VISIBLE);
        } else {
            allGranted = false;
            cardAppSelection.setVisibility(View.GONE);
        }

        // 6. Device Admin
        if (isDeviceAdminActive()) {
            btnDeviceAdminPerm.setText("✓ Device Admin Active");
            btnDeviceAdminPerm.setEnabled(false);
            btnDeviceAdminPerm.setBackgroundTintList(colorSuccess);
        } else {
            allGranted = false;
        }

        // 7. Continue Button Logic
        if (allGranted) {
            btnContinue.setEnabled(true);
            btnContinue.setText("All Set! Continuing...");
            btnContinue.setBackgroundTintList(null);
        } else {
            btnContinue.setEnabled(false);
            btnContinue.setText("Grant All Permissions to Continue");
            btnContinue.setBackgroundTintList(colorDisabled);
        }
    }

    private void checkLockScreenSecurity() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

        if (keyguardManager != null && !keyguardManager.isDeviceSecure()) {
            if (lockScreenDialog != null && lockScreenDialog.isShowing()) {
                return;
            }

            lockScreenDialog = new android.app.Dialog(this);
            lockScreenDialog.setContentView(R.layout.dialog_lock_screen);
            lockScreenDialog.setCancelable(false);

            if (lockScreenDialog.getWindow() != null) {
                lockScreenDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                lockScreenDialog.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            Button btnExit = lockScreenDialog.findViewById(R.id.btnDialogExit);
            Button btnSetPassword = lockScreenDialog.findViewById(R.id.btnDialogSetPassword);

            btnExit.setOnClickListener(v -> {
                lockScreenDialog.dismiss();
                finish();
            });

            btnSetPassword.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(intent);
                lockScreenDialog.dismiss();
            });

            lockScreenDialog.show();
        } else {
            if (lockScreenDialog != null && lockScreenDialog.isShowing()) {
                lockScreenDialog.dismiss();
            }
        }
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(PermissionActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}