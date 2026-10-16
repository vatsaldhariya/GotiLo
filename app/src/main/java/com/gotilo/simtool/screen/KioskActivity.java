package com.gotilo.simtool.screen;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gotilo.simtool.R;
import com.gotilo.simtool.service.MyAdminReceiver;
import com.gotilo.simtool.service.YourAccessibilityService;
import com.gotilo.simtool.utils.DataPref;

public class KioskActivity extends AppCompatActivity {

    private static final String TAG = "GotiloKiosk";
    private static KioskActivity activeInstance = null;

    private boolean isUnlocking = false;
    private boolean isShowingSystemUI = false;
    private WindowManager windowManager;
    private View topOverlayView, bottomOverlayView;
    private DataPref pref;


    public static void remoteUnlock() {
        if (activeInstance != null) {
            activeInstance.unlockDevice();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activeInstance = this;
        pref = new DataPref(this);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Keep screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Expand layout into the gesture areas
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {}
        });

        setContentView(R.layout.activity_kiosk);

        disableSystemBars();
        injectSystemOverlays();
    }

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void injectSystemOverlays() {
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) return;

            int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;

            // Common Touch Listener to catch swipes immediately
            View.OnTouchListener edgeTouchListener = (v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    Log.w(TAG, "🚨 System edge touch intercepted! Instantly locking screen.");
                    performInstantLock();
                    return true; // Consume touch, preventing status bar drag or unpin swipe
                }
                return false;
            };

            // Top Shield (Status Bar)
            WindowManager.LayoutParams topParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    150, // Slightly larger to catch the drag early
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            topParams.gravity = Gravity.TOP | Gravity.START;
            topOverlayView = new View(this);
            topOverlayView.setBackgroundColor(0x00000000);
            topOverlayView.setOnTouchListener(edgeTouchListener);

            // Bottom Shield (Navigation Bar / Swipe Unpin Area)
            WindowManager.LayoutParams bottomParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    180, // High enough to intercept the bottom swipe up gesture
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            bottomParams.gravity = Gravity.BOTTOM | Gravity.START;
            bottomOverlayView = new View(this);
            bottomOverlayView.setBackgroundColor(0x00000000);
            bottomOverlayView.setOnTouchListener(edgeTouchListener);

            windowManager.addView(topOverlayView, topParams);
            windowManager.addView(bottomOverlayView, bottomParams);
            Log.d(TAG, "Touch-blocking window overlays successfully injected.");

        } catch (Exception e) {
            Log.e(TAG, "Failed to inject system overlays: " + e.getMessage());
        }
    }

    private void removeSystemOverlays() {
        try {
            if (windowManager != null) {
                if (topOverlayView != null) {
                    windowManager.removeView(topOverlayView);
                    topOverlayView = null;
                }
                if (bottomOverlayView != null) {
                    windowManager.removeView(bottomOverlayView);
                    bottomOverlayView = null;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        isShowingSystemUI = false;
        disableSystemBars();
        safeStartLockTask();
        if (topOverlayView == null || bottomOverlayView == null) {
            injectSystemOverlays();
        }
    }

    private void safeStartLockTask() {
        findViewById(android.R.id.content).post(() -> {
            try {
                startLockTask();
            } catch (Exception ignored) {}
        });
    }

    public void unlockDevice() {
        isUnlocking = true;
        pref.setKioskLocked(false);
        removeSystemOverlays();

        try {
            stopLockTask();
        } catch (Exception ignored) {}

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        activeInstance = null;
        finish();
    }

    private void disableSystemBars() {
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Strict fallback for older devices to prevent drags
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    // --- 🚀 INSTANT LOCK VIA BOTH ADMIN AND ACCESSIBILITY ---
    private void performInstantLock() {
        // 1. Fire Device Admin Lock
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, MyAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow();
                Log.d(TAG, "Screen locked via Device Admin.");
            } catch (SecurityException e) {
                Log.e(TAG, "Device Admin lock failed: " + e.getMessage());
            }
        }

        // 2. Fire Accessibility Lock simultaneously
        YourAccessibilityService accessibilityService = YourAccessibilityService.getInstance();
        if (accessibilityService != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
            Log.d(TAG, "Screen locked via Accessibility Service.");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // If the window loses focus, but the user hasn't legitimately unlocked the app
        // (meaning the system unpin dialog popped up or they successfully unpinned)
        if (!hasFocus && !isUnlocking && !isShowingSystemUI) {
            Log.w(TAG, "🚨 Possible Unpin attempt detected (Focus Lost)! Forcing screen lock.");
            performInstantLock();

            // Re-hide system bars just in case the unpin action brought them back
            disableSystemBars();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 1. If we intentionally showed UI (like the offline key dialog or Wi-Fi settings), let touches pass normally.
        if (isShowingSystemUI) {
            return super.dispatchTouchEvent(ev);
        }

        // 2. Get the absolute Y coordinate of the current touch on the screen
        float currentY = ev.getRawY();

        // 3. Get the absolute real height of the screen (including nav bar areas)
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int screenHeight = metrics.heightPixels;

        // 4. Define your restricted zones (matching your overlay heights)
        int topRestrictedZone = 150;
        int bottomRestrictedZone = screenHeight - 180;

        // 5. If the touch moves into the top or bottom restricted zones at ANY point during the drag
        if (currentY <= topRestrictedZone || currentY >= bottomRestrictedZone) {

            // Only trigger if it's an active touch or movement to avoid spamming the lock command
            if (ev.getAction() == MotionEvent.ACTION_MOVE || ev.getAction() == MotionEvent.ACTION_DOWN) {
                Log.w(TAG, "🚨 Drag intrusion detected crossing into restricted zone! Locking screen.");
                performInstantLock();
            }

            // Return true to CONSUME the touch. This instantly stops the drag/swipe dead in its tracks.
            return true;
        }

        // 6. Otherwise, let the touch proceed normally in the safe center zone
        return super.dispatchTouchEvent(ev);
    }

    private void showOfflineKeyDialog() {
        isShowingSystemUI = true;
        removeSystemOverlays();

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_offline_unlock, null);
        EditText editKey = dialogView.findViewById(R.id.editOfflineKey);
        TextView tvError = dialogView.findViewById(R.id.tvDialogError);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        Button btnUnlock = dialogView.findViewById(R.id.btnDialogUnlock);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            isShowingSystemUI = false;
            injectSystemOverlays();
        });

        btnUnlock.setOnClickListener(v -> {
            String enteredKey = editKey.getText().toString().trim();
            if ("123456".equals(enteredKey)) {
                dialog.dismiss();
                isShowingSystemUI = false;
                unlockDevice();
            } else {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("🚨 Invalid Release Key! Please try again.");
                editKey.setText("");
            }
        });

        dialog.setOnDismissListener(d -> {
            isShowingSystemUI = false;
            if (isUnlocking) removeSystemOverlays();
            else injectSystemOverlays();
        });
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeSystemOverlays();
        if (activeInstance == this) {
            activeInstance = null;
        }
    }
}