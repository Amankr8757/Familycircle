package com.familycircle.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class SharingBootReceiver extends BroadcastReceiver {
    private static final String TAG = "FamilyBoot";
    private static final String STATUS_CHANNEL = "sharing_status";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("FamilyCircle", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("sharingEnabled", false) || prefs.getString("familyId", "").isEmpty()) return;

        boolean hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasBackgroundLocation = Build.VERSION.SDK_INT < 29
                || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasLocation || !hasBackgroundLocation) {
            postResumeNotice(context, "Open Family Circle and allow location access all the time to resume after reboot.");
            return;
        }

        long pauseUntil = prefs.getLong("pauseUntil", 0L);
        Intent service = new Intent(context, LocationShareService.class);
        if (pauseUntil > System.currentTimeMillis()) {
            service.setAction(LocationShareService.ACTION_PAUSE);
            service.putExtra(LocationShareService.EXTRA_PAUSE_UNTIL, pauseUntil);
        } else {
            service.setAction(LocationShareService.ACTION_START);
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not resume location service after reboot", error);
            postResumeNotice(context, "Open Family Circle to resume location sharing.");
        }
    }

    private void postResumeNotice(Context context, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(STATUS_CHANNEL, "Location sharing status", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 501, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notice = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, STATUS_CHANNEL) : new Notification.Builder(context);
        notice.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Location sharing needs attention")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pending).setAutoCancel(true);
        manager.notify(501, notice.build());
    }
}
