package com.hyper.market.installer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class DownloadTaskStore {
    private static final String PREFERENCES = "download_task_state";
    private static final String KEY_APPS = "apps";
    private static final String KEY_COMMAND = "command";
    private static final String KEY_PROFILE = "profileOverrides";
    private static final String[] STRING_KEYS = {
            "apps", "installerMode", "customInstallerPackage", "profileSource", "profileOverrides"
    };
    private static final String[] BOOLEAN_KEYS = {
            "showSystemApps", "incrementalUpdates", "removeSearchAds", "removeQuickApps",
            "removeReservationApps", "showPromotions", "showComments", "showSameDeveloper",
            "optimizeNames", "xiaomiIslandOptimization", "noUserAction", "saveToDownloads"
    };

    private DownloadTaskStore() { }

    public static void save(Context context, Intent task) {
        SharedPreferences.Editor editor = preferences(context).edit();
        for (String key : STRING_KEYS) {
            editor.putString(key, task.getStringExtra(key));
        }
        for (String key : BOOLEAN_KEYS) {
            editor.putBoolean(key, task.getBooleanExtra(key, false));
        }
        editor.putInt("startPage", task.getIntExtra("startPage", 0));
        editor.apply();
    }

    public static boolean hasTask(Context context) {
        return preferences(context).getString(KEY_APPS, "").length() > 0;
    }

    public static Intent restore(Context context) {
        SharedPreferences values = preferences(context);
        if (!hasTask(context)) {
            throw new IllegalStateException("没有可恢复的下载任务");
        }
        Intent task = new Intent(context, com.hyper.market.DownloadService.class);
        for (String key : STRING_KEYS) {
            String value = values.getString(key, null);
            if (value != null) task.putExtra(key, value);
        }
        for (String key : BOOLEAN_KEYS) {
            task.putExtra(key, values.getBoolean(key, false));
        }
        task.putExtra("startPage", values.getInt("startPage", 0));
        return task;
    }

    public static void recordCommand(Context context, String command) {
        preferences(context).edit().putString(KEY_COMMAND, command).apply();
    }

    public static String consumeCommand(Context context) {
        SharedPreferences values = preferences(context);
        String command = values.getString(KEY_COMMAND, "");
        if (!command.isEmpty()) values.edit().remove(KEY_COMMAND).apply();
        return command;
    }

    public static void clear(Context context) {
        preferences(context).edit().clear().apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
