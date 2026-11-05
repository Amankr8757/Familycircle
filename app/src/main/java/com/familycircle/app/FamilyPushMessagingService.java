package com.familycircle.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FamilyPushMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL = "family_updates_sound_v2";
    private static final String TAG = "FamilyPush";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        saveToken(token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        java.util.Map<String, String> data = message.getData();
        String title = data.get("title");
        String body = data.get("body");
        if (title == null || title.trim().isEmpty()) title = "Family Circle update";
        if (body == null || body.trim().isEmpty()) body = "There is a new family update.";
        String eventId = data.get("eventId");
        if (eventId == null || eventId.trim().isEmpty()) eventId = message.getMessageId();
        if (eventId != null && eventId.trim().length() > 0 && data.get("eventType") != null && data.get("eventType").startsWith("safe_")) {
            android.content.SharedPreferences prefs = getSharedPreferences("FamilyCircle", MODE_PRIVATE);
            String seenKey = "safeAreaEventSeen:" + eventId;
            if (prefs.getBoolean(seenKey, false)) return;
            prefs.edit().putBoolean(seenKey, true).apply();
        }
        showNotification(title, body, eventId);
    }

    private void saveToken(String token) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || token == null || token.isEmpty()) return;
        FirebaseDatabase.getInstance(getString(R.string.firebase_database_url))
                .getReference("pushTokens").child(uid).setValue(token)
                .addOnFailureListener(error -> Log.w(TAG, "Token registration failed", error));
    }

    private void showNotification(String title, String body, String messageId) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            Log.w(TAG, "NotificationManager is unavailable");
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Notification permission is not granted; skipping family update notification");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "Family alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Sound and vibration alerts for family location and battery updates.");
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 140, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        notification.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(tap).setAutoCancel(true);
        if (Build.VERSION.SDK_INT < 26) notification.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE).setPriority(Notification.PRIORITY_HIGH);
        int id = messageId == null || messageId.trim().isEmpty()
                ? (int) (System.currentTimeMillis() & 0x7fffffffL)
                : messageId.hashCode() & 0x7fffffff;
        manager.notify(id, notification.build());
    }
}
