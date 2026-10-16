package com.gotilo.simtool.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.gotilo.simtool.R; // Ensure this imports your app's R file

public class AlarmService extends Service {
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int originalVolume;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if ("START_ALARM".equals(action)) {
                startForeground(999, createNotification());
                playAlarm();
            } else if ("STOP_ALARM".equals(action)) {
                stopAlarm();
                stopSelf(); // Kills the service and removes the notification
            }
        }
        return START_NOT_STICKY;
    }

    private void playAlarm() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) return;

        try {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            // Save current volume to restore it later, then set to max
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);

            // Point to your custom audio file in res/raw/alram_alaram.wav
            Uri customAlarmUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.alert_alarm);

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, customAlarmUri);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            mediaPlayer.setLooping(true); // Loops continuously until STOP_ALARM is called
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e("AlarmService", "Error playing custom alarm", e);
        }
    }

    private void stopAlarm() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // Restore the user's original volume setting
        if (audioManager != null) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
        }
    }

    private Notification createNotification() {
        String channelId = "AlarmServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Security Alarm", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Gotilo Security")
                .setContentText("Emergency alarm is currently sounding.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }
}