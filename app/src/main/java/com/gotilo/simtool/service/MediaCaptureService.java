package com.gotilo.simtool.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.camera.video.FallbackStrategy;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.gotilo.simtool.api.ApiCaller;
import com.gotilo.simtool.model.EventModel;
import com.gotilo.simtool.model.MediaModel;
import com.gotilo.simtool.model.SimModel;
import com.gotilo.simtool.utils.DataPref;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MediaCaptureService extends LifecycleService {

    private static final String TAG = "MediaCapture";
    public static final String CHANNEL_ID = "MediaServiceChannel";
    private boolean isAllowedByAndroid = true;

    // 🛑 State variables to prevent the Camera/Audio Race Condition
    private boolean isCapturingOffline = false;
    private Intent pendingUploadIntent = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int fgsTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fgsTypes |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                }
                startForeground(101, buildNotification(), fgsTypes);
            } else {
                startForeground(101, buildNotification());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Foreground service start blocked by Android OS: " + e.getMessage());
            isAllowedByAndroid = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        android.os.UserManager userManager = (android.os.UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager != null && !userManager.isUserUnlocked()) {
            Log.e(TAG, "Device just rebooted and is still PIN locked. Firebase cannot run yet.");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isAllowedByAndroid || intent == null) {
            return START_NOT_STICKY;
        }

        String command = intent.getStringExtra("MEDIA_COMMAND");

        if ("CAPTURE_OFFLINE".equals(command)) {
            Log.w(TAG, "Starting Offline Capture Sequence...");
            isCapturingOffline = true;
            startOfflineCaptureSequence();
            return START_NOT_STICKY;

        } else if ("UPLOAD_OFFLINE".equals(command)) {
            // 🛑 If offline capture is currently running, queue the upload!
            if (isCapturingOffline) {
                Log.w(TAG, "⚠️ UPLOAD DELAYED: Offline capture is still running. Queuing upload until it finishes!");
                pendingUploadIntent = intent;
            } else {
                Log.d(TAG, "No capture running. Starting upload immediately.");
                startOfflineUploadSequence(intent);
            }
            return START_NOT_STICKY;

        } else if ("CAPTURE_BOTH".equals(command) || "CAPTURE_PHOTO".equals(command)) {
            startDualCaptureSequence(command);
            return START_NOT_STICKY;

        } else if ("CAPTURE_AUDIO".equals(command) || "RECORD_AUDIO".equals(command)) {
            startAudioCaptureSequence(command);
            return START_NOT_STICKY;

        } else if ("CAPTURE_VIDEO".equals(command)) { // 👈 ADD THIS BLOCK
            startVideoRecordingSequence(command);
            return START_NOT_STICKY;

        } else if ("SYNC_RECOVERY".equals(command)) {
            // 👇 Grab the dynamic reason from the intent
            String eventReason = intent.getStringExtra("EVENT_REASON");
            if (eventReason == null || eventReason.isEmpty()) {
                eventReason = "SYSTEM_BOOT";
            }

            // 👇 Pass it to the method
            startRecoverySequence(command, eventReason);
            return START_NOT_STICKY;
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    // ========================================================================
    // 1. OFFLINE CAPTURE (Saves to SharedPreferences, Does Not Upload)
    // ========================================================================
    @SuppressLint("MissingPermission")
    private void startOfflineCaptureSequence() {
        File frontFile = new File(getExternalFilesDir(null), "front_offline_" + System.currentTimeMillis() + ".jpg");
        File rearFile = new File(getExternalFilesDir(null), "rear_offline_" + System.currentTimeMillis() + ".jpg");
        File frontVideo = new File(getExternalFilesDir(null), "front_vid_off_" + System.currentTimeMillis() + ".mp4");
        File rearVideo = new File(getExternalFilesDir(null), "rear_vid_off_" + System.currentTimeMillis() + ".mp4");
        File audioFile = new File(getExternalFilesDir(null), "audio_offline_" + System.currentTimeMillis() + ".m4a");

        Log.d(TAG, "OFFLINE: Capturing Front Camera...");
        captureSingleLens(CameraSelector.LENS_FACING_FRONT, frontFile, () -> {
            Log.d(TAG, "OFFLINE: Capturing Rear Camera...");
            captureSingleLens(CameraSelector.LENS_FACING_BACK, rearFile, () -> {
                Log.d(TAG, "OFFLINE: Recording Front Video...");
                recordVideoSingleLens(CameraSelector.LENS_FACING_FRONT, frontVideo, () -> {
                    Log.d(TAG, "OFFLINE: Recording Rear Video...");
                    recordVideoSingleLens(CameraSelector.LENS_FACING_BACK, rearVideo, () -> {
                        Log.d(TAG, "OFFLINE: Starting 10s Audio Recording...");
                        recordAudioOnly(audioFile, () -> {

                            Log.d(TAG, "OFFLINE: Fetching Location...");
                            FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);

                            locationClient.getLastLocation().addOnCompleteListener(task -> {
                                SharedPreferences prefs = getSharedPreferences("GotiloAppPrefs", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();

                                editor.putString("OFFLINE_FRONT", frontFile.getAbsolutePath());
                                editor.putString("OFFLINE_REAR", rearFile.getAbsolutePath());
                                editor.putString("OFFLINE_FRONT_VID", frontVideo.getAbsolutePath());
                                editor.putString("OFFLINE_REAR_VID", rearVideo.getAbsolutePath());
                                editor.putString("OFFLINE_AUDIO", audioFile.getAbsolutePath());

                                if (task.isSuccessful() && task.getResult() != null) {
                                    Location loc = task.getResult();
                                    editor.putFloat("OFFLINE_LAT", (float) loc.getLatitude());
                                    editor.putFloat("OFFLINE_LNG", (float) loc.getLongitude());
                                    editor.putString("OFFLINE_ADDR", getAddressFromCoords(loc.getLatitude(), loc.getLongitude()));
                                }

                                editor.apply();
                                Log.d(TAG, "OFFLINE: All data saved locally to SharedPreferences!");

                                isCapturingOffline = false;
                                if (pendingUploadIntent != null) {
                                    Log.w(TAG, "🚀 Executing queued offline upload now that capture is complete!");
                                    startOfflineUploadSequence(pendingUploadIntent);
                                    pendingUploadIntent = null;
                                } else {
                                    stopSelf();
                                }
                            });
                        });
                    });
                });
            });
        });
    }

    // ========================================================================
    // 2. OFFLINE UPLOAD (Reads from SharedPreferences -> Uploads -> API)
    // ========================================================================
    // ========================================================================
// 2. OFFLINE UPLOAD (Reads from SharedPreferences -> Uploads -> API)
// ========================================================================
    private void startOfflineUploadSequence(Intent intent) {
        SharedPreferences prefs = getSharedPreferences("GotiloAppPrefs", Context.MODE_PRIVATE);

        String frontPath = prefs.getString("OFFLINE_FRONT", null);
        String rearPath = prefs.getString("OFFLINE_REAR", null);
        String frontVidPath = prefs.getString("OFFLINE_FRONT_VID", null);
        String rearVidPath = prefs.getString("OFFLINE_REAR_VID", null);
        String audioPath = prefs.getString("OFFLINE_AUDIO", null);

        float lat = prefs.getFloat("OFFLINE_LAT", 0f);
        float lng = prefs.getFloat("OFFLINE_LNG", 0f);
        String address = prefs.getString("OFFLINE_ADDR", "Unknown Address");

        String newCarrier = intent.getStringExtra("NEW_CARRIER");
        String newSerial = intent.getStringExtra("NEW_SERIAL");
        String newPhone = intent.getStringExtra("NEW_PHONE");

        String finalDeviceId = getDeviceIdSafe();

        File frontFile = (frontPath != null) ? new File(frontPath) : new File("");
        File rearFile = (rearPath != null) ? new File(rearPath) : new File("");
        File frontVideo = (frontVidPath != null) ? new File(frontVidPath) : new File("");
        File rearVideo = (rearVidPath != null) ? new File(rearVidPath) : new File("");
        File audioFile = (audioPath != null) ? new File(audioPath) : new File("");

        Log.d(TAG, "UPLOAD: Starting upload of offline saved media...");

        uploadFileHelper(finalDeviceId, frontFile, frontUrl -> {
            uploadFileHelper(finalDeviceId, rearFile, rearUrl -> {
                uploadFileHelper(finalDeviceId, frontVideo, frontVidUrl -> {
                    uploadFileHelper(finalDeviceId, rearVideo, rearVidUrl -> {
                        uploadFileHelper(finalDeviceId, audioFile, audioUrl -> {

                            boolean allUploadsFailed = (frontUrl == null && rearUrl == null && frontVidUrl == null && rearVidUrl == null && audioUrl == null);

                            if (allUploadsFailed) {
                                Log.e(TAG, "UPLOAD FAILED: Firebase timed out. Firing API with text data anyway!");
                            }

                            ApiCaller apiCaller = new ApiCaller();

                            String finalFront = (frontUrl != null) ? frontUrl : "upload_failed";
                            String finalRear = (rearUrl != null) ? rearUrl : "upload_failed";
                            String finalFrontVid = (frontVidUrl != null) ? frontVidUrl : "upload_failed";
                            String finalRearVid = (rearVidUrl != null) ? rearVidUrl : "upload_failed";
                            String finalAudio = (audioUrl != null) ? audioUrl : "upload_failed";

                            // 1. Send Media and Location to Server
                            apiCaller.saveEventData(finalDeviceId, "SIM_SWAP_DETECTED", finalFront, finalRear, finalAudio, finalFrontVid, finalRearVid, (double) lat, (double) lng, address, new Callback<EventModel>() {
                                @Override
                                public void onResponse(@NonNull Call<EventModel> call, @NonNull Response<EventModel> response) {
                                    Log.d(TAG, "UPLOAD: API Event data sent successfully.");
                                }

                                @Override
                                public void onFailure(@NonNull Call<EventModel> call, @NonNull Throwable t) {
                                    Log.e(TAG, "UPLOAD: API Event data failed.", t);
                                }
                            });

                            // 2. Save the New SIM Info to Server
                            if (newCarrier != null && newSerial != null) {
                                apiCaller.saveSimInfo(finalDeviceId, newCarrier, newSerial, newPhone, new Callback<SimModel>() {
                                    @Override
                                    public void onResponse(Call<SimModel> call, Response<SimModel> response) {
                                    }

                                    @Override
                                    public void onFailure(Call<SimModel> call, Throwable t) {
                                    }
                                });
                            }

                            // 3. Selective Cleanup: DO NOT call prefs.edit().clear()
                            if (allUploadsFailed) {
                                Log.d(TAG, "UPLOAD: Files kept locally for next retry.");
                                stopSelf();
                            } else {
                                if (frontFile.exists()) frontFile.delete();
                                if (rearFile.exists()) rearFile.delete();
                                if (frontVideo.exists()) frontVideo.delete();
                                if (rearVideo.exists()) rearVideo.delete();
                                if (audioFile.exists()) audioFile.delete();

                                // ✅ Remove only the offline capture keys
                                prefs.edit()
                                        .remove("OFFLINE_FRONT")
                                        .remove("OFFLINE_REAR")
                                        .remove("OFFLINE_FRONT_VID")
                                        .remove("OFFLINE_REAR_VID")
                                        .remove("OFFLINE_AUDIO")
                                        .remove("OFFLINE_LAT")
                                        .remove("OFFLINE_LNG")
                                        .remove("OFFLINE_ADDR")
                                        .apply();

                                stopSelf();
                            }
                        });
                    });
                });
            });
        });
    }

    // ========================================================================
    // 3. STANDARD DUAL CAPTURE
    // ========================================================================
    private void startDualCaptureSequence(String commandName) {
        File frontFile = new File(getExternalFilesDir(null), "front_" + System.currentTimeMillis() + ".jpg");
        File rearFile = new File(getExternalFilesDir(null), "rear_" + System.currentTimeMillis() + ".jpg");
        String finalDeviceId = getDeviceIdSafe();

        Log.d(TAG, "Capturing Front Camera...");
        captureSingleLens(CameraSelector.LENS_FACING_FRONT, frontFile, () -> {
            Log.d(TAG, "Front Camera success. Capturing Rear Camera...");
            captureSingleLens(CameraSelector.LENS_FACING_BACK, rearFile, () -> {
                Log.d(TAG, "Both cameras captured successfully. Starting Uploads...");

                uploadFileHelper(finalDeviceId, frontFile, frontUrl -> {
                    uploadFileHelper(finalDeviceId, rearFile, rearUrl -> {
                        // 👇 FIXED: Now properly routes to saveMedia API
                        fireSaveMediaApi(finalDeviceId, "image", frontUrl, rearUrl, null, null, null, commandName, () -> {
                            if (frontFile.exists()) frontFile.delete();
                            if (rearFile.exists()) rearFile.delete();
                            stopSelf();
                        });
                    });
                });
            });
        });
    }

    // ========================================================================
    // 4. STANDARD AUDIO CAPTURE
    // ========================================================================
    private void startAudioCaptureSequence(String commandName) {
        File audioFile = new File(getExternalFilesDir(null), "audio_" + System.currentTimeMillis() + ".m4a");
        String finalDeviceId = getDeviceIdSafe();

        Log.d(TAG, "Audio recording started...");
        recordAudioOnly(audioFile, () -> {
            Log.d(TAG, "Audio recording finished. Uploading...");
            uploadFileHelper(finalDeviceId, audioFile, audioUrl -> {
                // 👇 FIXED: Now properly routes to saveMedia API
                fireSaveMediaApi(finalDeviceId, "audio", null, null, audioUrl, null, null, commandName, () -> {
                    if (audioFile.exists()) audioFile.delete();
                    stopSelf();
                });
            });
        });
    }

    // ========================================================================
    // STANDARD VIDEO CAPTURE (FCM COMMAND)
    // ========================================================================
    private void startVideoRecordingSequence(String commandName) {
        File frontVideo = new File(getExternalFilesDir(null), "front_vid_" + System.currentTimeMillis() + ".mp4");
        File rearVideo = new File(getExternalFilesDir(null), "rear_vid_" + System.currentTimeMillis() + ".mp4");
        String finalDeviceId = getDeviceIdSafe();

        Log.d(TAG, "Capturing Front Video...");
        recordVideoSingleLens(CameraSelector.LENS_FACING_FRONT, frontVideo, () -> {
            Log.d(TAG, "Capturing Rear Video...");
            recordVideoSingleLens(CameraSelector.LENS_FACING_BACK, rearVideo, () -> {
                Log.d(TAG, "Both videos captured successfully. Starting Uploads...");

                uploadFileHelper(finalDeviceId, frontVideo, frontVidUrl -> {
                    uploadFileHelper(finalDeviceId, rearVideo, rearVidUrl -> {
                        // 👇 FIXED: Now properly routes to saveMedia API
                        fireSaveMediaApi(finalDeviceId, "video", null, null, null, frontVidUrl, rearVidUrl, commandName, () -> {
                            if (frontVideo.exists()) frontVideo.delete();
                            if (rearVideo.exists()) rearVideo.delete();
                            stopSelf();
                        });
                    });
                });
            });
        });
    }

    // ========================================================================
    // 5. MASTER RECOVERY CAPTURE
    // ========================================================================
    @SuppressLint("MissingPermission")
    // 👇 Add 'String eventReason' here
    private void startRecoverySequence(String commandName, String eventReason) {
        File frontFile = new File(getExternalFilesDir(null), "front_" + System.currentTimeMillis() + ".jpg");
        File rearFile = new File(getExternalFilesDir(null), "rear_" + System.currentTimeMillis() + ".jpg");
        File frontVideo = new File(getExternalFilesDir(null), "front_vid_" + System.currentTimeMillis() + ".mp4");
        File rearVideo = new File(getExternalFilesDir(null), "rear_vid_" + System.currentTimeMillis() + ".mp4");
        File audioFile = new File(getExternalFilesDir(null), "audio_" + System.currentTimeMillis() + ".m4a");
        String finalDeviceId = getDeviceIdSafe();

        Log.d(TAG, "RECOVERY: Capturing Front Camera...");
        captureSingleLens(CameraSelector.LENS_FACING_FRONT, frontFile, () -> {
            Log.d(TAG, "RECOVERY: Capturing Rear Camera...");
            captureSingleLens(CameraSelector.LENS_FACING_BACK, rearFile, () -> {
                Log.d(TAG, "RECOVERY: Capturing Front Video...");
                recordVideoSingleLens(CameraSelector.LENS_FACING_FRONT, frontVideo, () -> {
                    Log.d(TAG, "RECOVERY: Capturing Rear Video...");
                    recordVideoSingleLens(CameraSelector.LENS_FACING_BACK, rearVideo, () -> {
                        Log.d(TAG, "RECOVERY: Starting 10s Audio Recording...");
                        recordAudioOnly(audioFile, () -> {
                            Log.d(TAG, "RECOVERY: All media captured. Uploading to Storage...");

                            uploadFileHelper(finalDeviceId, frontFile, frontUrl -> {
                                uploadFileHelper(finalDeviceId, rearFile, rearUrl -> {
                                    uploadFileHelper(finalDeviceId, frontVideo, frontVidUrl -> {
                                        uploadFileHelper(finalDeviceId, rearVideo, rearVidUrl -> {
                                            uploadFileHelper(finalDeviceId, audioFile, audioUrl -> {

                                                Log.d(TAG, "RECOVERY: Storage uploads complete. Fetching Location...");
                                                FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);

                                                locationClient.getLastLocation().addOnCompleteListener(task -> {
                                                    Double lat = null;
                                                    Double lng = null;
                                                    String address = "Address unavailable";

                                                    if (task.isSuccessful() && task.getResult() != null) {
                                                        Location loc = task.getResult();
                                                        lat = loc.getLatitude();
                                                        lng = loc.getLongitude();
                                                        address = getAddressFromCoords(lat, lng);
                                                    }

                                                    Log.d(TAG, "RECOVERY: Firing Event API...");
                                                    ApiCaller apiCaller = new ApiCaller();
                                                    apiCaller.saveEventData(finalDeviceId, eventReason, frontUrl, rearUrl, audioUrl, frontVidUrl, rearVidUrl, lat, lng, address, new Callback<EventModel>() {
                                                        @Override
                                                        public void onResponse(@NonNull Call<EventModel> call, @NonNull Response<EventModel> response) {
                                                            Log.d(TAG, eventReason + " Event Data saved successfully!");
                                                            if (frontFile.exists())
                                                                frontFile.delete();
                                                            if (rearFile.exists())
                                                                rearFile.delete();
                                                            if (frontVideo.exists())
                                                                frontVideo.delete();
                                                            if (rearVideo.exists())
                                                                rearVideo.delete();
                                                            if (audioFile.exists())
                                                                audioFile.delete();
                                                            stopSelf();
                                                        }

                                                        @Override
                                                        public void onFailure(@NonNull Call<EventModel> call, @NonNull Throwable t) {
                                                            Log.e(TAG, "Failed to save Boot Event Data", t);
                                                            stopSelf();
                                                        }
                                                    });
                                                });
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    // ========================================================================
    // CORE UTILITIES
    // ========================================================================
    private String getDeviceIdSafe() {
        DataPref pref = new DataPref(this);
        String deviceId = pref.getDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        return deviceId;
    }

    private void captureSingleLens(int lensFacing, File photoFile, Runnable onSuccess) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                ImageCapture imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                // 🚀 THE FIX: Create a headless "Dummy Preview"
                // This tricks the OS into keeping the camera fully awake in the background
                // and allows the lens to focus and balance lighting before snapping!
                androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
                preview.setSurfaceProvider(request -> {
                    android.graphics.SurfaceTexture surfaceTexture = new android.graphics.SurfaceTexture(0);
                    surfaceTexture.setDefaultBufferSize(request.getResolution().getWidth(), request.getResolution().getHeight());
                    android.view.Surface surface = new android.view.Surface(surfaceTexture);
                    request.provideSurface(surface, ContextCompat.getMainExecutor(this), result -> {
                        surface.release();
                        surfaceTexture.release();
                    });
                });

                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

                // Bind BOTH the Dummy Preview and the ImageCapture
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                Log.d(TAG, "Camera bound. Warming up lens for 1.2 seconds...");

                // 🚀 Now wait 1.2 seconds for the camera to fully initialize before taking the shot.
                // The Dummy Preview prevents the OS from killing the camera during this wait.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

                    imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            Log.d(TAG, "Photo saved successfully! Size: " + photoFile.length() + " bytes");
                            cameraProvider.unbindAll();
                            onSuccess.run();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Log.e(TAG, "Camera Capture failed for lens: " + lensFacing, exception);
                            cameraProvider.unbindAll();
                            onSuccess.run();
                        }
                    });
                }, 1200);

            } catch (Exception e) {
                Log.e(TAG, "Camera Setup Failed", e);
                onSuccess.run();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void recordVideoSingleLens(int lensFacing, File videoFile, Runnable onSuccess) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();

                // 🚀 THE FIX: Force Quality.SD (480p). This drastically shrinks the file size!
                // Fallback allows it to go slightly higher only if the camera physically cannot shoot SD.
                QualitySelector qualitySelector = QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD));
                Recorder recorder = new Recorder.Builder().setQualitySelector(qualitySelector).build();

                VideoCapture<Recorder> videoCapture = VideoCapture.withOutput(recorder);
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

                cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture);

                FileOutputOptions fileOutputOptions = new FileOutputOptions.Builder(videoFile).build();

                final Recording[] activeRecording = new Recording[1];

                @SuppressLint("MissingPermission")
                androidx.camera.video.PendingRecording pendingRecording = videoCapture.getOutput()
                        .prepareRecording(this, fileOutputOptions)
                        .withAudioEnabled();

                activeRecording[0] = pendingRecording.start(ContextCompat.getMainExecutor(this), event -> {

                    if (event instanceof VideoRecordEvent.Start) {
                        Log.d(TAG, "Camera warmed up. Recording 5-second SD video now...");

                        // Stop recording automatically after 5 seconds
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (activeRecording[0] != null) {
                                activeRecording[0].stop();
                            }
                        }, 5000);

                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;

                        if (finalizeEvent.hasError()) {
                            Log.e(TAG, "Video recording error: " + finalizeEvent.getCause());
                        } else {
                            Log.d(TAG, "SD Video saved successfully! Size: " + (videoFile.length() / 1024) + " KB");
                        }

                        cameraProvider.unbindAll();
                        onSuccess.run();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Video Camera Setup Failed", e);
                onSuccess.run();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private interface UploadCallback {
        void onUploadComplete(String downloadUrl);
    }

    private void uploadFileHelper(String deviceId, File file, UploadCallback callback) {
        if (file == null || !file.exists()) {
            callback.onUploadComplete(null);
            return;
        }

        StorageReference storageRef = FirebaseStorage.getInstance().getReference().child("media/" + deviceId + "/" + file.getName());
        com.google.firebase.storage.UploadTask uploadTask = storageRef.putFile(Uri.fromFile(file));

        // 🚀 THE FIX: Print the live upload progress to Logcat!
        uploadTask.addOnProgressListener(taskSnapshot -> {
            double progress = (100.0 * taskSnapshot.getBytesTransferred()) / Math.max(1, taskSnapshot.getTotalByteCount());
            Log.d(TAG, "🚀 UPLOADING [" + file.getName() + "]: " + String.format("%.1f", progress) + "%");
        });

        // 🛑 Force a timeout so Firebase cannot freeze the API chain
        final boolean[] isResolved = {false};
        Handler timeoutHandler = new Handler(Looper.getMainLooper());

        Runnable timeoutRunnable = () -> {
            if (!isResolved[0]) {
                isResolved[0] = true;
                Log.e(TAG, "UPLOAD TIMEOUT: Firebase hung waiting for network on " + file.getName());
                uploadTask.cancel(); // Abort the Firebase task
                callback.onUploadComplete(null); // Move to the next step anyway
            }
        };

        // 90 seconds for large media, 45 seconds for photos
        long timeoutDuration = file.getName().endsWith(".mp4") || file.getName().endsWith(".m4a") ? 90000 : 45000;
        timeoutHandler.postDelayed(timeoutRunnable, timeoutDuration);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                if (!isResolved[0]) {
                    isResolved[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    callback.onUploadComplete(uri.toString());
                }
            }).addOnFailureListener(e -> {
                if (!isResolved[0]) {
                    isResolved[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    callback.onUploadComplete(null);
                }
            });
        }).addOnFailureListener(e -> {
            if (!isResolved[0]) {
                isResolved[0] = true;
                timeoutHandler.removeCallbacks(timeoutRunnable);
                callback.onUploadComplete(null);
            }
        });
    }

    private void fireSaveMediaApi(String deviceId, String mediaType, String frontUrl, String backUrl, String audioUrl, String videoFrontUrl, String videoBackUrl, String commandUsed, Runnable onComplete) {
        ApiCaller apiCaller = new ApiCaller();
        apiCaller.saveMedia(deviceId, mediaType, frontUrl, backUrl, audioUrl, videoFrontUrl, videoBackUrl, commandUsed, new Callback<MediaModel>() {
            @Override
            public void onResponse(@NonNull Call<MediaModel> call, @NonNull Response<MediaModel> response) {
                onComplete.run();
            }

            @Override
            public void onFailure(@NonNull Call<MediaModel> call, @NonNull Throwable t) {
                onComplete.run();
            }
        });
    }

    private void recordAudioOnly(File audioFile, Runnable onSuccess) {
        MediaRecorder recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(this) : new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    recorder.stop();
                    recorder.release();
                    onSuccess.run();
                } catch (Exception e) {
                    onSuccess.run();
                }
            }, 10000); // 10 seconds
        } catch (Exception e) {
            onSuccess.run();
        }
    }

    private String getAddressFromCoords(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                return addresses.get(0).getAddressLine(0);
            }
        } catch (IOException e) {
            Log.w(TAG, "Geocoder error: " + e.getMessage());
        }
        return "Unknown Address";
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Gotilo Security").setContentText("Capturing security data...").setSmallIcon(android.R.drawable.ic_dialog_info).setPriority(NotificationCompat.PRIORITY_MIN).build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Security Capture", NotificationManager.IMPORTANCE_MIN);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @SuppressLint("MissingPermission")
    private void fireSaveEventApi(String deviceId, String reason, String frontUrl, String backUrl, String audioUrl, String videoFrontUrl, String videoBackUrl, Runnable onComplete) {
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);

        locationClient.getLastLocation().addOnCompleteListener(task -> {
            Double lat = null;
            Double lng = null;
            String address = "Address unavailable";

            if (task.isSuccessful() && task.getResult() != null) {
                Location loc = task.getResult();
                lat = loc.getLatitude();
                lng = loc.getLongitude();
                address = getAddressFromCoords(lat, lng);
            }

            Log.d(TAG, "Firing Event API for: " + reason);
            ApiCaller apiCaller = new ApiCaller();
            apiCaller.saveEventData(deviceId, reason, frontUrl, backUrl, audioUrl, videoFrontUrl, videoBackUrl, lat, lng, address, new Callback<EventModel>() {
                @Override
                public void onResponse(@NonNull Call<EventModel> call, @NonNull Response<EventModel> response) {
                    Log.d(TAG, "App media successfully saved to Event API!");
                    onComplete.run();
                }

                @Override
                public void onFailure(@NonNull Call<EventModel> call, @NonNull Throwable t) {
                    Log.e(TAG, "Failed to save app media to Event API.", t);
                    onComplete.run();
                }
            });
        });
    }
}