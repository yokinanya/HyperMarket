package com.hyper.market.installer;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.hyper.market.model.ApkArtifact;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ExternalApkInstaller {
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";

    public void install(Context context, List<File> files, List<ApkArtifact> artifacts,
                        InstallOptions options) throws IOException {
        validate(files, artifacts, options);
        List<Uri> uris = resolveUris(context, files);
        List<Intent> intents = createIntents(context, uris, options.getCustomInstallerPackage());
        grantUris(context, options.getCustomInstallerPackage(), uris);
        ensureHandler(context, intents);
        ExternalInstallActivity.launch(context, intents, options, files, artifacts);
    }

    private void validate(List<File> files, List<ApkArtifact> artifacts,
                          InstallOptions options) throws IOException {
        if (files.isEmpty() || files.size() != artifacts.size()) {
            throw new IOException("第三方安装器的安装包与元数据不匹配");
        }
        if (isBlank(options.getPackageName())) {
            throw new IOException("第三方安装器缺少应用包名");
        }
        if (isBlank(options.getCustomInstallerPackage())) {
            throw new IOException("请先选择第三方包安装器");
        }
    }

    private List<Uri> resolveUris(Context context, List<File> files) throws IOException {
        List<Uri> uris = new ArrayList<>(files.size());
        for (File file : files) {
            if (!file.isFile()) {
                throw new IOException("第三方安装器找不到安装包：" + file);
            }
            uris.add(toUri(context, file));
        }
        return uris;
    }

    private Uri toUri(Context context, File file) throws IOException {
        return FileProvider.getUriForFile(
                context, context.getPackageName() + FILE_PROVIDER_SUFFIX, file);
    }

    private List<Intent> createIntents(Context context, List<Uri> uris, String installerPackage) {
        if (uris.size() == 1) {
            return java.util.Arrays.asList(
                    createSingleIntent(context, uris.get(0), Intent.ACTION_VIEW, installerPackage),
                    createSingleIntent(context, uris.get(0), Intent.ACTION_INSTALL_PACKAGE,
                            installerPackage));
        }
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(APK_MIME)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris))
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{APK_MIME});
        configureIntent(context, intent, uris, installerPackage);
        return java.util.Collections.singletonList(intent);
    }

    private Intent createSingleIntent(Context context, Uri uri, String action,
                                      String installerPackage) {
        Intent intent = new Intent(action).setDataAndType(uri, APK_MIME);
        configureIntent(context, intent, java.util.Collections.singletonList(uri), installerPackage);
        return intent;
    }

    private void configureIntent(Context context, Intent intent, List<Uri> uris,
                                 String installerPackage) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClipData(clipData(context, uris));
        if (!isBlank(installerPackage)) {
            intent.setPackage(installerPackage);
        }
    }

    private ClipData clipData(Context context, List<Uri> uris) {
        ClipData clipData = ClipData.newUri(context.getContentResolver(), "APK", uris.get(0));
        for (int index = 1; index < uris.size(); index++) {
            clipData.addItem(new ClipData.Item(uris.get(index)));
        }
        return clipData;
    }

    private void grantUris(Context context, String installerPackage, List<Uri> uris) {
        if (isBlank(installerPackage)) {
            return;
        }
        for (Uri uri : uris) {
            context.grantUriPermission(installerPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private void ensureHandler(Context context, List<Intent> intents) throws IOException {
        PackageManager packageManager = context.getPackageManager();
        boolean hasHandler = intents.stream().anyMatch(intent ->
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null);
        if (!hasHandler) {
            throw new IOException("没有可处理 APK 的第三方安装器：" +
                    (intents.get(0).getPackage() == null ? "系统选择器" : intents.get(0).getPackage()));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
