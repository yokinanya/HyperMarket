package com.hyper.market.installer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;

import com.hyper.market.MainActivity;
import com.hyper.market.R;

public final class DownloadNotification {
    private static final String CHANNEL_ID = "package_install";
    private static final String ALERT_CHANNEL_ID = "install_alerts";
    private static final String RESULT_CHANNEL_ID = "install_results";
    private static final int NOTIFICATION_ID = 4101;
    private static final int ALERT_NOTIFICATION_ID = 4102;
    private static final int RESULT_NOTIFICATION_ID = 4103;
    private static final int ISLAND_NOTIFICATION_ID = 4104;
    private static final long UPDATE_INTERVAL_MS = 500L;
    private static long lastUpdateAt;
    private static String lastUpdateContent;
    private static volatile boolean islandEnabled;
    private static volatile String currentTaskName;

    public static int notificationId() { return NOTIFICATION_ID; }

    private DownloadNotification() { }

    public static void begin(Context context, String name) {
        currentTaskName = name;
        islandEnabled = resolveIslandEnabled(context);
        post(context, "正在处理 " + name, true);
        postIsland(context, name);
    }

    public static void update(Context context, String status) {
        DownloadControl control = DownloadTaskRegistry.current();
        if (control == null || control.isPaused()) {
            hideOngoing(context);
            return;
        }
        post(context, status, true);
    }

    public static void complete(Context context, String name) {
        cancelOngoing(context);
        postResult(context, "安装完成：" + name);
    }

    public static void installationComplete(Context context, String name) {
        postResult(context, "安装完成：" + name);
    }

    public static void failure(Context context, String message) {
        post(context, "处理失败：" + message, false);
    }

    public static void refresh(Context context) {
        DownloadControl control = DownloadTaskRegistry.current();
        if (control == null || control.isPaused()) {
            hideOngoing(context);
            return;
        }
        postIsland(context, currentTaskName);
        post(context, lastUpdateContent == null ? "正在下载…" : lastUpdateContent, true);
    }

    public static void cancel(Context context) {
        cancelOngoing(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(ALERT_NOTIFICATION_ID);
            manager.cancel(RESULT_NOTIFICATION_ID);
            manager.cancel(ISLAND_NOTIFICATION_ID);
        }
    }

    public static void cancelOngoing(Context context) {
        hideOngoing(context);
        lastUpdateContent = null;
        currentTaskName = null;
    }

    public static void hideOngoing(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
            manager.cancel(ISLAND_NOTIFICATION_ID);
        }
    }

    public static Notification foreground(Context context, String content) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("系统通知服务不可用");
        createChannel(context, manager);
        islandEnabled = resolveIslandEnabled(context);
        return build(context, content, true, CHANNEL_ID);
    }

    private static void post(Context context, String content, boolean ongoing) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("系统通知服务不可用");
        createChannel(context, manager);
        if (ongoing) {
            long now = SystemClock.elapsedRealtime();
            if (content.equals(lastUpdateContent) ||
                    (isProgress(content) && isProgress(lastUpdateContent) &&
                            now - lastUpdateAt < UPDATE_INTERVAL_MS)) {
                return;
            }
            lastUpdateAt = now;
            lastUpdateContent = content;
            manager.notify(NOTIFICATION_ID, build(context, content, true, CHANNEL_ID));
            return;
        }
        manager.cancel(NOTIFICATION_ID);
        manager.cancel(ISLAND_NOTIFICATION_ID);
        lastUpdateContent = null;
        manager.notify(ALERT_NOTIFICATION_ID, build(context, content, false, ALERT_CHANNEL_ID));
        currentTaskName = null;
    }

    private static void postResult(Context context, String content) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("系统通知服务不可用");
        createChannel(context, manager);
        manager.notify(RESULT_NOTIFICATION_ID, build(context, content, false, RESULT_CHANNEL_ID));
    }

    private static Notification build(Context context, String content, boolean ongoing,
                                      String channelId) {
        PendingIntent intent = PendingIntent.getActivity(
                context, NOTIFICATION_ID, new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
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
        return notification;
    }

    private static void postIsland(Context context, String name) {
        if (!islandEnabled || name == null) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("系统通知服务不可用");
        createChannel(context, manager);
        Notification notification = build(
                context, "正在下载 " + name, true, CHANNEL_ID);
        if (MiuiFocusBridge.apply(context, notification, name, "正在下载", true, false)) {
            manager.notify(ISLAND_NOTIFICATION_ID, notification);
        }
    }

    private static boolean resolveIslandEnabled(Context context) {
        com.hyper.market.AppSettings settings = new com.hyper.market.SettingsStore(context).read();
        return settings.getXiaomiIslandOptimization()
                && !"第三方安装器".equals(settings.getInstallerMode());
    }

    private static boolean isProgress(String content) {
        return content != null && (content.startsWith("正在下载") ||
                content.startsWith("正在下载增量补丁"));
    }

    private static NotificationCompat.Action action(Context context, String action, String title) {
        Intent intent = new Intent(context, DownloadNotificationReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action.Builder(0, title, pending).build();
    }

    private static void createChannel(Context context, NotificationManager manager) {
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.install_channel_name),
                NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(
                ALERT_CHANNEL_ID, context.getString(R.string.install_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(
                RESULT_CHANNEL_ID, context.getString(R.string.install_result_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
    }
}
