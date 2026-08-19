package com.hyper.market.installer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.hyper.market.MainActivity;
import com.hyper.market.R;

public final class DownloadNotification {
    private static final String CHANNEL_ID = "package_install";
    private static final String ALERT_CHANNEL_ID = "install_alerts";
    private static final int NOTIFICATION_ID = 4101;

    public static int notificationId() { return NOTIFICATION_ID; }

    private DownloadNotification() { }

    public static void begin(Context context, String name) {
        post(context, "正在处理 " + name, true);
    }

    public static void update(Context context, String status) {
        post(context, status, true);
    }

    public static void complete(Context context, String name) {
        post(context, "已完成：" + name, false);
    }

    public static void failure(Context context, String message) {
        post(context, "处理失败：" + message, false);
    }

    public static void refresh(Context context) {
        post(context, "下载任务控制", true);
    }

    public static Notification foreground(Context context, String content) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("系统通知服务不可用");
        createChannel(context, manager);
        return build(context, content, true, CHANNEL_ID);
    }

    private static void post(Context context, String content, boolean ongoing) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("系统通知服务不可用");
        createChannel(context, manager);
        String channel = ongoing ? CHANNEL_ID : ALERT_CHANNEL_ID;
        manager.notify(NOTIFICATION_ID, build(context, content, ongoing, channel));
    }

    private static Notification build(Context context, String content, boolean ongoing,
                                      String channelId) {
        PendingIntent intent = PendingIntent.getActivity(
                context, NOTIFICATION_ID, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(content)
                .setContentIntent(intent)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        DownloadControl control = DownloadTaskRegistry.current();
        if (ongoing && control != null) {
            String action = control.isPaused()
                    ? DownloadNotificationReceiver.ACTION_RESUME
                    : DownloadNotificationReceiver.ACTION_PAUSE;
            builder.addAction(action(context, action, control.isPaused() ? "继续" : "暂停"));
            builder.addAction(action(context, DownloadNotificationReceiver.ACTION_CANCEL, "取消"));
        }
        Notification notification = builder.build();
        boolean islandEnabled = new com.hyper.market.SettingsStore(context)
                .read().getXiaomiIslandOptimization();
        MiuiFocusBridge.apply(context, notification, context.getString(R.string.app_name), content,
                islandEnabled);
        return notification;
    }

    private static NotificationCompat.Action action(Context context, String action, String title) {
        Intent intent = new Intent(context, DownloadNotificationReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action.Builder(0, title, pending).build();
    }

    private static void createChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, context.getString(R.string.install_channel_name),
                    NotificationManager.IMPORTANCE_LOW));
            manager.createNotificationChannel(new NotificationChannel(
                    ALERT_CHANNEL_ID, context.getString(R.string.install_alert_channel_name),
                    NotificationManager.IMPORTANCE_HIGH));
        }
    }
}
