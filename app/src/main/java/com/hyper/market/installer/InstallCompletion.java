package com.hyper.market.installer;

import android.content.Context;

import com.hyper.market.UpdateStore;
import com.hyper.market.SavedPackageArtifact;
import com.hyper.market.model.MarketAppInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class InstallCompletion {
    private InstallCompletion() { }

    public static void complete(Context context, InstallOptions options, List<File> files,
                                List<String> artifactNames) throws IOException {
        if (files.size() != artifactNames.size()) {
            throw new IOException("安装结果文件与元数据不匹配");
        }
        MarketAppInfo app = new MarketAppInfo.Builder()
                .packageName(options.getPackageName())
                .displayName(options.getDisplayName())
                .versionName(options.getVersionName())
                .versionCode(options.getVersionCode())
                .iconUrl(options.getIconUrl())
                .build();
        UpdateStore store = new UpdateStore(context);
        if (options.isSaveToDownloads()) {
            saveFiles(context, store, app, files, artifactNames);
        }
        if (options.isDeleteAfterInstall()) {
            deleteFiles(files);
        }
        store.recordHistory(app, options.isFirstInstall());
    }

    private static void saveFiles(Context context, UpdateStore store, MarketAppInfo app,
                                  List<File> files, List<String> names) throws IOException {
        List<SavedPackageArtifact> artifacts = new java.util.ArrayList<>(files.size());
        for (int index = 0; index < files.size(); index++) {
            DownloadArchive.SavedLocation saved = DownloadArchive.save(
                    context, app, names.get(index), files.get(index), files.size() > 1);
            artifacts.add(new SavedPackageArtifact(
                    saved.getLocation(), names.get(index), saved.getSize()));
        }
        store.recordSavedPackageGroup(app, artifacts);
    }

    private static void deleteFiles(List<File> files) throws IOException {
        for (File file : files) {
            if (file.exists() && !file.delete()) {
                throw new IOException("无法删除安装包：" + file.getAbsolutePath());
            }
        }
    }
}
