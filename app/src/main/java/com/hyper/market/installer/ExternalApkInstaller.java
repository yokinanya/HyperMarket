package com.hyper.market.installer;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.hyper.market.UpdateStore;
import com.hyper.market.SavedPackageArtifact;
import com.hyper.market.model.ApkArtifact;
import com.hyper.market.model.MarketAppInfo;

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
        MarketAppInfo app = appFrom(options);
        List<Uri> uris = saveAndResolveUris(context, app, files, artifacts);
        List<Intent> intents = createIntents(context, uris, options.getCustomInstallerPackage());
        grantUris(context, options.getCustomInstallerPackage(), uris);
        ensureHandler(context, intents);
        ExternalInstallActivity.launch(context, intents, options);
    }

    private void validate(List<File> files, List<ApkArtifact> artifacts,
                          InstallOptions options) throws IOException {
        if (files.isEmpty() || files.size() != artifacts.size()) {
            throw new IOException("第三方安装器的安装包与元数据不匹配");
        }
        if (options.getPackageName().isBlank()) {
            throw new IOException("第三方安装器缺少应用包名");
        }
        if (options.getCustomInstallerPackage().isBlank()) {
            throw new IOException("请先选择第三方包安装器");
        }
    }

    private MarketAppInfo appFrom(InstallOptions options) {
        return new MarketAppInfo.Builder()
                .packageName(options.getPackageName())
                .displayName(options.getDisplayName())
                .versionName(options.getVersionName())
                .versionCode(options.getVersionCode())
                .iconUrl(options.getIconUrl())
                .build();
    }

    private List<Uri> saveAndResolveUris(Context context, MarketAppInfo app, List<File> files,
                                         List<ApkArtifact> artifacts) throws IOException {
        UpdateStore store = new UpdateStore(context);
        List<Uri> uris = new ArrayList<>(files.size());
        List<SavedPackageArtifact> savedArtifacts = new ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            DownloadArchive.SavedLocation saved = DownloadArchive.save(
                    context, app, artifacts.get(index).getName(), files.get(index), files.size() > 1);
            savedArtifacts.add(new SavedPackageArtifact(
                    saved.getLocation(), artifacts.get(index).getName(), saved.getSize()));
            uris.add(toUri(context, saved.getLocation()));
        }
        store.recordSavedPackageGroup(app, savedArtifacts);
        return uris;
    }

    private Uri toUri(Context context, String location) throws IOException {
        Uri parsed = Uri.parse(location);
        if ("content".equals(parsed.getScheme())) {
            return parsed;
        }
        File file = new File(location);
        if (!file.isFile()) {
            throw new IOException("第三方安装器找不到安装包：" + location);
        }
        return FileProvider.getUriForFile(
                context, context.getPackageName() + FILE_PROVIDER_SUFFIX, file);
    }

    private List<Intent> createIntents(Context context, List<Uri> uris, String installerPackage) {
        if (uris.size() == 1) {
            return List.of(
                    createSingleIntent(context, uris.get(0), Intent.ACTION_VIEW, installerPackage),
                    createSingleIntent(context, uris.get(0), Intent.ACTION_INSTALL_PACKAGE,
                            installerPackage));
        }
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(APK_MIME)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris))
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{APK_MIME});
        configureIntent(context, intent, uris, installerPackage);
        return List.of(intent);
    }

    private Intent createSingleIntent(Context context, Uri uri, String action,
                                      String installerPackage) {
        Intent intent = new Intent(action).setDataAndType(uri, APK_MIME);
        configureIntent(context, intent, List.of(uri), installerPackage);
        return intent;
    }

    private void configureIntent(Context context, Intent intent, List<Uri> uris,
                                 String installerPackage) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClipData(clipData(context, uris));
        if (!installerPackage.isBlank()) {
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
        if (installerPackage.isBlank()) {
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
}
