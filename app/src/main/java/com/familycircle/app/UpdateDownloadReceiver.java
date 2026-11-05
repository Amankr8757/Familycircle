package com.familycircle.app;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (GitHubUpdateManager.ACTION_INSTALL.equals(intent.getAction())) {
            GitHubUpdateManager.installDownloadedUpdate(context);
            return;
        }
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            GitHubUpdateManager.onDownloadComplete(context, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L));
        }
    }
}
