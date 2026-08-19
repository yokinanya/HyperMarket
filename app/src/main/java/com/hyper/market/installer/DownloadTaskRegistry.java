package com.hyper.market.installer;

public final class DownloadTaskRegistry {
    private static DownloadControl current;
    private static String pendingAction;

    private DownloadTaskRegistry() { }

    public static synchronized DownloadControl begin() {
        if (current != null) throw new IllegalStateException("已有下载任务正在运行");
        current = new DownloadControl();
        if (pendingAction != null) {
            applyAction(current, pendingAction);
            pendingAction = null;
        }
        return current;
    }

    public static synchronized DownloadControl current() {
        return current;
    }

    public static synchronized void finish(DownloadControl control) {
        if (current == control) current = null;
    }

    public static synchronized void applyCurrent(String action) {
        if (current == null) throw new IllegalStateException("当前没有可控制的下载任务");
        applyAction(current, action);
    }

    public static synchronized void requestForNextTask(String action) {
        validateAction(action);
        pendingAction = action;
    }

    public static synchronized void clearPendingAction() {
        pendingAction = null;
    }

    private static void applyAction(DownloadControl control, String action) {
        validateAction(action);
        if (DownloadNotificationReceiver.ACTION_PAUSE.equals(action)) control.pause();
        else if (DownloadNotificationReceiver.ACTION_RESUME.equals(action)) control.resume();
        else control.cancel();
    }

    private static void validateAction(String action) {
        if (!DownloadNotificationReceiver.ACTION_PAUSE.equals(action)
                && !DownloadNotificationReceiver.ACTION_RESUME.equals(action)
                && !DownloadNotificationReceiver.ACTION_CANCEL.equals(action)) {
            throw new IllegalArgumentException("未知下载操作：" + action);
        }
    }
}
