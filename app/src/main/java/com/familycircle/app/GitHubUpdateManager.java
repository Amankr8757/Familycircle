package com.familycircle.app;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads signed APK assets from the configured public GitHub Releases repository. */
public final class GitHubUpdateManager {
    public interface Callback { void onResult(String message); }

    static final String PREFS = "FamilyCircle";
    static final String KEY_DOWNLOAD_ID = "githubUpdateDownloadId";
    static final String KEY_TAG = "githubUpdateTag";
    static final String ACTION_INSTALL = "com.familycircle.app.INSTALL_GITHUB_UPDATE";
    static final String CHANNEL = "github_app_updates_v1";
    private static final int NOTIFICATION_ID = 7301;
    private static final String TAG = "GitHubUpdater";

    private GitHubUpdateManager() { }

    public static void checkAndDownload(Context context, boolean userRequested, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String repo = app.getString(R.string.github_repository).trim();
            if (repo.isEmpty() || repo.contains("YOUR_GITHUB")) {
                result(app, callback, userRequested ? "Set github_repository before checking updates." : null);
                return;
            }
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(20_000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "FamilyCircle-Android");
                int code = connection.getResponseCode();
                if (code == 404) { result(app, callback, userRequested ? "No GitHub release found yet." : null); return; }
                if (code != 200) { result(app, callback, userRequested ? "Could not check GitHub update (HTTP " + code + ")." : null); return; }
                StringBuilder text = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line; while ((line = reader.readLine()) != null) text.append(line);
                }
                JSONObject release = new JSONObject(text.toString());
                String tag = release.optString("tag_name", "");
                if (compareVersions(tag, BuildConfig.VERSION_NAME) <= 0) {
                    result(app, callback, userRequested ? "Family Circle is up to date." : null);
                    return;
                }
                String assetUrl = findApkAsset(release.optJSONArray("assets"));
                if (assetUrl == null) { result(app, callback, userRequested ? "Release " + tag + " has no APK asset." : null); return; }
                SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                long savedId = prefs.getLong(KEY_DOWNLOAD_ID, -1L);
                String savedTag = prefs.getString(KEY_TAG, "");
                int status = downloadStatus(app, savedId);
                if (tag.equals(savedTag) && status == DownloadManager.STATUS_SUCCESSFUL) {
                    postInstallNotification(app, tag);
                    result(app, callback, userRequested ? "Update " + tag + " is ready to install." : null);
                    return;
                }
                if (tag.equals(savedTag) && status == DownloadManager.STATUS_RUNNING) {
                    result(app, callback, userRequested ? "Update " + tag + " is downloading." : null);
                    return;
                }
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(assetUrl));
                request.setTitle("Family Circle " + tag);
                request.setDescription("Downloading update…");
                request.setMimeType("application/vnd.android.package-archive");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "FamilyCircle-" + tag.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk");
                long id = ((DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_TAG, tag).apply();
                postDownloadingNotification(app, tag);
                result(app, callback, userRequested ? "Update " + tag + " is downloading." : null);
            } catch (Exception error) {
                Log.w(TAG, "GitHub update check failed", error);
                result(app, callback, userRequested ? "Could not check GitHub for updates." : null);
            }
        }, "github-update-check").start();
    }

    static void onDownloadComplete(Context context, long downloadId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (downloadId != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return;
        if (downloadStatus(context, downloadId) == DownloadManager.STATUS_SUCCESSFUL) postInstallNotification(context, prefs.getString(KEY_TAG, "update"));
        else postStatusNotification(context, "Update download failed", "Open Family Circle and try the update again.");
    }

    static void installDownloadedUpdate(Context context) {
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            return;
        }
        long id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DOWNLOAD_ID, -1L);
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri apk = manager.getUriForDownloadedFile(id);
        if (apk == null) { postStatusNotification(context, "Update file unavailable", "Download the update again from Family Circle."); return; }
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apk, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(install);
    }

    private static String findApkAsset(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && asset.optString("name", "").toLowerCase().endsWith(".apk")) return asset.optString("browser_download_url", null);
        }
        return null;
    }

    private static int compareVersions(String release, String installed) {
        String[] left = release.replaceFirst("^[vV]", "").split("[.-]");
        String[] right = installed.replaceFirst("^[vV]", "").split("[.-]");
        int limit = Math.max(left.length, right.length);
        for (int i = 0; i < limit; i++) {
            int a = i < left.length ? number(left[i]) : 0;
            int b = i < right.length ? number(right[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }

    private static int number(String input) { try { return Integer.parseInt(input.replaceAll("\\D.*", "")); } catch (Exception ignored) { return 0; } }
    private static int downloadStatus(Context context, long id) {
        if (id < 0) return -1;
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return -1;
            return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        }
    }

    private static void result(Context context, Callback callback, String message) {
        if (callback == null || message == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResult(message));
    }

    private static void postDownloadingNotification(Context context, String tag) { postStatusNotification(context, "Update available · " + tag, "Downloading the new Family Circle version…"); }
    private static void postInstallNotification(Context context, String tag) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel(manager);
        Intent install = new Intent(context, UpdateDownloadReceiver.class).setAction(ACTION_INSTALL);
        PendingIntent action = PendingIntent.getBroadcast(context, 91, install, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = builder(context, "Family Circle " + tag + " is ready", "Tap Install to update now.")
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Install update", action).build());
        manager.notify(NOTIFICATION_ID, notification.build());
    }
    private static void postStatusNotification(Context context, String title, String body) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel(manager);
        manager.notify(NOTIFICATION_ID, builder(context, title, body).build());
    }
    private static Notification.Builder builder(Context context, String title, String body) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body));
        if (Build.VERSION.SDK_INT < 26) builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE).setPriority(Notification.PRIORITY_HIGH);
        return builder;
    }
    private static void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Family Circle update downloads and install prompts.");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }
}
