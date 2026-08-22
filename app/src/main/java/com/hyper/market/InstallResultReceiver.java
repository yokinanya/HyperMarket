package com.hyper.market;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import com.hyper.market.installer.InstallCompletion;
import com.hyper.market.installer.InstallOptions;
import com.hyper.market.installer.DownloadNotification;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class InstallResultReceiver extends BroadcastReceiver {
    public static final String EXTRA_PACKAGE_NAME = "install_package_name";
    public static final String EXTRA_DISPLAY_NAME = "install_display_name";
    public static final String EXTRA_VERSION_NAME = "install_version_name";
    public static final String EXTRA_VERSION_CODE = "install_version_code";
    public static final String EXTRA_ICON_URL = "install_icon_url";
    public static final String EXTRA_FIRST_INSTALL = "install_first_install";
    public static final String EXTRA_SAVE_TO_DOWNLOADS = "install_save_to_downloads";
    public static final String EXTRA_FILES = "install_files";
    public static final String EXTRA_ARTIFACT_NAMES = "install_artifact_names";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra("android.content.pm.extra.STATUS", -1);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            InstallUiStateStore.awaiting(required(intent, EXTRA_PACKAGE_NAME));
            openUserAction(context, intent);
            return;
        }
        String message = intent.getStringExtra("android.content.pm.extra.STATUS_MESSAGE");
        if (status == 0) {
            try {
                complete(context, intent);
                InstallUiStateStore.complete(required(intent, EXTRA_PACKAGE_NAME));
                DownloadNotification.complete(context, displayName(intent));
            } catch (IOException | RuntimeException exception) {
                String processingMessage = "安装完成，但安装包处理失败: " + exception.getMessage();
                DownloadNotification.failure(context, processingMessage);
                Toast.makeText(context, processingMessage, Toast.LENGTH_LONG).show();
            }
        } else {
            InstallUiStateStore.failure(required(intent, EXTRA_PACKAGE_NAME),
                    message == null ? "安装失败" : message);
            DownloadNotification.failure(context, "安装失败: " + message);
            Toast.makeText(context, "安装失败: " + message, Toast.LENGTH_LONG).show();
        }
    }

    private void openUserAction(Context context, Intent result) {
        Intent confirmation = result.getParcelableExtra("android.intent.extra.INTENT");
        if (confirmation == null) {
            throw new IllegalStateException("系统要求用户确认安装，但没有返回确认页面");
        }
        confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(confirmation);
    }

    private void complete(Context context, Intent intent) throws IOException {
        ArrayList<String> pathValues = intent.getStringArrayListExtra(EXTRA_FILES);
        ArrayList<String> nameValues = intent.getStringArrayListExtra(EXTRA_ARTIFACT_NAMES);
        if (pathValues == null || nameValues == null) {
            throw new IOException("安装结果缺少安装包路径");
        }
        List<File> files = new ArrayList<>();
        for (String path : pathValues) files.add(new File(path));
        InstallOptions options = new InstallOptions(
                "标准安装",
                required(intent, EXTRA_PACKAGE_NAME),
                required(intent, EXTRA_DISPLAY_NAME),
                required(intent, EXTRA_VERSION_NAME),
                intent.getLongExtra(EXTRA_VERSION_CODE, 0),
                intent.getBooleanExtra(EXTRA_FIRST_INSTALL, false),
                false,
                intent.getBooleanExtra(EXTRA_SAVE_TO_DOWNLOADS, false),
                "",
                intent.getStringExtra(EXTRA_ICON_URL));
        InstallCompletion.complete(context, options, files, nameValues);
    }

    private String required(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        if (isBlank(value)) throw new IllegalStateException("缺少安装结果字段：" + key);
        return value;
    }

    private String displayName(Intent intent) {
        String value = intent.getStringExtra(EXTRA_DISPLAY_NAME);
        return isBlank(value) ? required(intent, EXTRA_PACKAGE_NAME) : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
