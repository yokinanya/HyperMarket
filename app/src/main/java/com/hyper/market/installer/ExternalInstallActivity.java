package com.hyper.market.installer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.hyper.market.model.ApkArtifact;
import com.hyper.market.model.MarketAppInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ExternalInstallActivity extends Activity {
    private static final int REQUEST_INSTALL = 42;
    private static final int VERIFY_ATTEMPTS = 20;
    private static final long VERIFY_INTERVAL_MS = 500L;
    private static final String EXTRA_INTENTS = "external_install_intents";
    private static final String EXTRA_PACKAGE = "external_package_name";
    private static final String EXTRA_DISPLAY = "external_display_name";
    private static final String EXTRA_VERSION = "external_version_name";
    private static final String EXTRA_CODE = "external_version_code";
    private static final String EXTRA_FIRST = "external_first_install";
    private static final String EXTRA_ICON = "external_icon_url";
    private static final String EXTRA_SAVE = "external_save_to_downloads";
    private static final String EXTRA_FILES = "external_files";
    private static final String EXTRA_ARTIFACTS = "external_artifacts";

    public static void launch(android.content.Context context, java.util.List<Intent> intents,
                              InstallOptions options, List<File> files,
                              List<ApkArtifact> artifacts) {
        java.util.ArrayList<Intent> parcelableIntents = new java.util.ArrayList<>(intents);
        Intent relay = new Intent(context, ExternalInstallActivity.class)
                .putParcelableArrayListExtra(EXTRA_INTENTS, parcelableIntents)
                .putExtra(EXTRA_PACKAGE, options.getPackageName())
                .putExtra(EXTRA_DISPLAY, options.getDisplayName())
                .putExtra(EXTRA_VERSION, options.getVersionName())
                .putExtra(EXTRA_CODE, options.getVersionCode())
                .putExtra(EXTRA_FIRST, options.isFirstInstall())
                .putExtra(EXTRA_ICON, options.getIconUrl())
                .putExtra(EXTRA_SAVE, options.isSaveToDownloads())
                .putStringArrayListExtra(EXTRA_FILES, paths(files))
                .putStringArrayListExtra(EXTRA_ARTIFACTS, artifactNames(artifacts))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(relay);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        java.util.ArrayList<Intent> intents = getIntent().getParcelableArrayListExtra(EXTRA_INTENTS);
        if (intents == null || intents.isEmpty()) {
            throw new IllegalStateException("第三方安装器调用缺少安装 Intent");
        }
        startIntent(intents, 0);
    }

    private void startIntent(java.util.ArrayList<Intent> intents, int index) {
        try {
            startActivityForResult(intents.get(index), REQUEST_INSTALL);
        } catch (android.content.ActivityNotFoundException exception) {
            if (index + 1 < intents.size()) {
                startIntent(intents, index + 1);
                return;
            }
            throw exception;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL) return;
        verifyInstalled(resultCode, 0);
    }

    private void verifyInstalled(int resultCode, int attempt) {
        if (isTargetVersionInstalled()) {
            try {
                recordSuccess();
                com.hyper.market.InstallUiStateStore.complete(requiredPackageName());
                DownloadNotification.complete(this, getIntent().getStringExtra(EXTRA_DISPLAY));
            } catch (IOException | RuntimeException exception) {
                com.hyper.market.InstallUiStateStore.failure(
                        requiredPackageName(), exception.getMessage() == null
                                ? "安装包处理失败" : exception.getMessage());
                DownloadNotification.failure(this, "安装包处理失败: " + exception.getMessage());
                Toast.makeText(this, "安装成功，但安装包处理失败", Toast.LENGTH_LONG).show();
            }
            finish();
            return;
        }
        if (attempt < VERIFY_ATTEMPTS) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> verifyInstalled(resultCode, attempt + 1), VERIFY_INTERVAL_MS);
            return;
        }
        cleanupFiles();
        String message = resultCode == RESULT_OK
                ? "第三方安装器返回成功，但目标应用版本未更新"
                : "第三方安装器取消或未完成安装";
        com.hyper.market.InstallUiStateStore.failure(requiredPackageName(), message);
        DownloadNotification.failure(this, message);
        Toast.makeText(this, "第三方安装器未完成安装", Toast.LENGTH_LONG).show();
        finish();
    }

    private boolean isTargetVersionInstalled() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(
                    requiredPackageName(), 0);
            long installedVersion = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            return installedVersion >= getIntent().getLongExtra(EXTRA_CODE, 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private void recordSuccess() throws IOException {
        ArrayList<String> pathValues = getIntent().getStringArrayListExtra(EXTRA_FILES);
        ArrayList<String> artifactValues = getIntent().getStringArrayListExtra(EXTRA_ARTIFACTS);
        if (pathValues == null || artifactValues == null || pathValues.size() != artifactValues.size()) {
            throw new IOException("第三方安装器结果缺少安装包元数据");
        }
        List<File> files = new ArrayList<>(pathValues.size());
        for (String path : pathValues) files.add(new File(path));
        MarketAppInfo app = new MarketAppInfo.Builder()
                .packageName(getIntent().getStringExtra(EXTRA_PACKAGE))
                .displayName(getIntent().getStringExtra(EXTRA_DISPLAY))
                .versionName(getIntent().getStringExtra(EXTRA_VERSION))
                .versionCode(getIntent().getLongExtra(EXTRA_CODE, 0))
                .build();
        InstallOptions options = new InstallOptions(
                "第三方安装器",
                app.getPackageName(),
                app.getDisplayName(),
                app.getVersionName(),
                app.getVersionCode(),
                getIntent().getBooleanExtra(EXTRA_FIRST, false),
                false,
                getIntent().getBooleanExtra(EXTRA_SAVE, true),
                "",
                getIntent().getStringExtra(EXTRA_ICON));
        InstallCompletion.complete(this, options, files, artifactValues);
        cleanupFiles(files);
        DownloadNotification.complete(this, app.getDisplayName());
    }

    private void cleanupFiles() {
        ArrayList<String> paths = getIntent().getStringArrayListExtra(EXTRA_FILES);
        if (paths == null) return;
        List<File> files = new ArrayList<>(paths.size());
        for (String path : paths) files.add(new File(path));
        cleanupFiles(files);
    }

    private void cleanupFiles(List<File> files) {
        for (File file : files) {
            if (file.isFile() && !file.delete()) {
                DownloadNotification.failure(this, "无法清理临时安装包: " + file.getName());
            }
        }
    }

    private static ArrayList<String> paths(List<File> files) {
        ArrayList<String> values = new ArrayList<>(files.size());
        for (File file : files) values.add(file.getAbsolutePath());
        return values;
    }

    private static ArrayList<String> artifactNames(List<ApkArtifact> artifacts) {
        ArrayList<String> values = new ArrayList<>(artifacts.size());
        for (ApkArtifact artifact : artifacts) values.add(artifact.getName());
        return values;
    }

    private String requiredPackageName() {
        String value = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("第三方安装结果缺少应用包名");
        }
        return value;
    }
}
