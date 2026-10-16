package com.gotilo.simtool.utils; // Make sure this matches your folder structure (utils or receiver)

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.gotilo.simtool.service.AlarmService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class SmsCommandReceiver extends BroadcastReceiver {

    private static final String TAG = "GotiloSmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Log.d(TAG, "📩 SMS_RECEIVED Broadcast Hit! Reading data...");

            Bundle bundle = intent.getExtras();
            if (bundle == null) return;

            try {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus == null) return;

                for (Object pdu : pdus) {
                    SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                    String senderNumber = smsMessage.getOriginatingAddress();
                    String messageBody = smsMessage.getMessageBody();

                    if (messageBody != null) {
                        messageBody = messageBody.trim();
                    }

                    Log.d(TAG, "Incoming SMS from: " + senderNumber + " | Body: " + messageBody);

                    DataPref pref = new DataPref(context);
                    if (!pref.isKioskLocked()) {
                        Log.w(TAG, "⚠️ Kiosk mode is OFF in DataPref! (Command will still run because return is commented out)");
                        // return; // <-- Keep this commented out while testing!
                    }

                    if (messageBody == null) continue;

                    // 1. Convert to uppercase to make it case-insensitive
                    String upperCaseBody = messageBody.toUpperCase();

                    // 2. Use .contains() so hidden carrier characters don't break the command
                    // Command 1: Play Alarm
                    if (upperCaseBody.contains("GOTILO_ALARM")) {
                        Log.w(TAG, "🚨 Offline Command: Play Alarm");
                        Intent alarmIntent = new Intent(context, AlarmService.class);
                        alarmIntent.setAction("START_ALARM");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(alarmIntent);
                        } else {
                            context.startService(alarmIntent);
                        }
                        abortBroadcast();
                        break;
                    }

                    // Command 2: Stop Alarm
                    else if (upperCaseBody.contains("GOTILO_STOP_ALARM")) {
                        Log.w(TAG, "🛑 Offline Command: Stop Alarm");
                        Intent alarmIntent = new Intent(context, AlarmService.class);
                        alarmIntent.setAction("STOP_ALARM");
                        context.startService(alarmIntent);
                        abortBroadcast();
                        break;
                    }

                    // Command 3: Location Reply
                    else if (upperCaseBody.contains("GOTILO_LOCATION")) {
                        Log.w(TAG, "📍 Offline Command: Location Request");

                        // Prevent the BroadcastReceiver from being killed before the async location fetch finishes
                        final PendingResult pendingResult = goAsync();
                        fetchAndReplyLocation(context, senderNumber, pendingResult);

                        abortBroadcast();
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing incoming SMS", e);
            }
        }
    }

    private void fetchAndReplyLocation(Context context, String recipientNumber, PendingResult pendingResult) {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                sendSmsReply(recipientNumber, "Gotilo: Location permission denied on device.");
                if (pendingResult != null) pendingResult.finish();
                return;
            }

            FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
            CancellationTokenSource tokenSource = new CancellationTokenSource();

            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();

                            // 1. Try to get the real address from coordinates
                            String address = getAddressFromCoords(context, lat, lng);

                            // 2. Generate a clickable Google Maps link
                            String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;

                            // 3. Build the SMS message securely
                            StringBuilder msg = new StringBuilder("Gotilo Recovery:\n");

                            if (address != null && !address.isEmpty()) {
                                msg.append("📍 ").append(address).append("\n");
                            } else {
                                msg.append("📍 Address lookup failed (Device Offline)\n");
                            }

                            msg.append("Acc: ").append(location.getAccuracy()).append("m\n");
                            msg.append("Map: ").append(mapsLink);

                            sendSmsReply(recipientNumber, msg.toString());
                        } else {
                            sendSmsReply(recipientNumber, "Gotilo: GPS fix unavailable right now.");
                        }

                        // Tell OS the background work is done
                        if (pendingResult != null) pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {
                        sendSmsReply(recipientNumber, "Gotilo: Failed to get GPS.");
                        if (pendingResult != null) pendingResult.finish();
                    });

        } catch (Exception e) {
            Log.e(TAG, "Location fetch failed", e);
            sendSmsReply(recipientNumber, "Gotilo: Error fetching location.");
            if (pendingResult != null) pendingResult.finish();
        }
    }

    // --- HELPER METHOD TO CONVERT LAT/LNG TO STREET ADDRESS ---
    private String getAddressFromCoords(Context context, double lat, double lng) {
        android.location.Geocoder geocoder = new android.location.Geocoder(context, java.util.Locale.getDefault());
        try {
            // Get a maximum of 1 address result
            java.util.List<android.location.Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                // Return the full formatted address line
                return addresses.get(0).getAddressLine(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Geocoder failed (likely offline): " + e.getMessage());
        }
        return null;
    }

    private void sendSmsReply(String recipientNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();

            // 🚀 FIX: Split the long message (with emojis/newlines) into multiple parts
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);

            // Send as a multipart message so the carrier doesn't drop it
            smsManager.sendMultipartTextMessage(recipientNumber, null, parts, null, null);

            Log.d(TAG, "SMS Reply Sent to " + recipientNumber + " in " + parts.size() + " parts.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS reply", e);
        }
    }
}