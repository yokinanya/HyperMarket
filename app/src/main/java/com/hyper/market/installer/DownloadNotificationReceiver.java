package com.hyper.market.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public final class DownloadNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_PAUSE = "com.hyper.market.action.DOWNLOAD_PAUSE";
    public static final String ACTION_RESUME = "com.hyper.market.action.DOWNLOAD_RESUME";
    public static final String ACTION_CANCEL = "com.hyper.market.action.DOWNLOAD_CANCEL";

    @Override
    public void onReceive(Context context, Intent intent) {
        DownloadControl control = DownloadTaskRegistry.current();
        String action = intent.getAction();
        if (control != null) {
            DownloadTaskRegistry.applyCurrent(action);
            if (ACTION_PAUSE.equals(action)) {
                com.hyper.market.InstallUiStateStore.pauseCurrent();
                com.hyper.market.DownloadService.Companion.setProgressNotificationVisible(
                        context, false);
            } else if (ACTION_RESUME.equals(action)) {
                com.hyper.market.InstallUiStateStore.resumeCurrent();
                com.hyper.market.DownloadService.Companion.setProgressNotificationVisible(
                        context, true);
            } else {
                DownloadNotification.cancelOngoing(context);
            }
            return;
        }
        if (!DownloadTaskStore.hasTask(context)) {
            DownloadNotification.failure(context, "下载任务已结束，无法执行操作");
            return;
        }
        DownloadTaskRegistry.requestForNextTask(action);
        DownloadTaskStore.recordCommand(context, action);
        ContextCompat.startForegroundService(
                context,
                new Intent(context, com.hyper.market.DownloadService.class)
                        .setAction(com.hyper.market.DownloadService.CONTROL_ACTION));
    }
}
