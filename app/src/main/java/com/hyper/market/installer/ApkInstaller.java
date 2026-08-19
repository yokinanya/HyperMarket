package com.hyper.market.installer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInstaller;
import android.os.Build;

import com.hyper.market.InstallResultReceiver;
import com.hyper.market.model.ApkArtifact;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ApkInstaller {
    private static final String ACTION_INSTALL_STATUS = "com.hyper.market.action.INSTALL_STATUS";
    public boolean install(Context context, List<File> files, List<ApkArtifact> artifacts,
                           InstallOptions options) throws IOException {
        if (files.isEmpty() || files.size() != artifacts.size()) {
            throw new IOException("Install files and artifact metadata do not match");
        }
        if ("第三方安装器".equals(options.getInstallerMode())) {
            new ExternalApkInstaller().install(context, files, artifacts, options);
            return false;
        }
        if ("Root 静默安装".equals(options.getInstallerMode())) {
            new RootApkInstaller().install(files);
            return true;
        }
        if ("Shizuku 静默安装".equals(options.getInstallerMode())) {
            try {
                PackageInstaller installer = ShizukuBridge.packageInstaller(context);
                installSession(context, installer, files, artifacts, options);
                return false;
            } catch (Exception exception) {
                throw new IOException("Shizuku 安装失败: " + exception.getMessage(), exception);
            }
        }
        verifyUnknownSourcesPermission(context);
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        installSession(context, installer, files, artifacts, options);
        return false;
    }

    private void installSession(Context context, PackageInstaller installer, List<File> files,
                                List<ApkArtifact> artifacts, InstallOptions options) throws IOException {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(options.getPackageName());
        params.setSize(files.stream().mapToLong(File::length).sum());
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        if (Build.VERSION.SDK_INT >= 31 && options.isNoUserAction()) {
            params.setRequireUserAction(2);
            params.setInstallScenario(1);
        }
        int sessionId = installer.createSession(params);
        writeSession(installer, sessionId, files, artifacts);
        commit(context, installer, sessionId, options, files, artifacts);
    }

    private void writeSession(PackageInstaller installer, int sessionId, List<File> files,
                              List<ApkArtifact> artifacts) throws IOException {
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            for (int index = 0; index < files.size(); index++) {
                writeFile(session, files.get(index), artifacts.get(index));
            }
        } catch (IOException | RuntimeException exception) {
            abandon(installer, sessionId);
            throw new IOException("Cannot write install session " + sessionId, exception);
        }
    }

    private void writeFile(PackageInstaller.Session session, File file, ApkArtifact artifact)
            throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             java.io.OutputStream output = session.openWrite(artifact.getName(), 0, file.length())) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            session.fsync(output);
        }
    }

    private void commit(Context context, PackageInstaller installer, int sessionId,
                        InstallOptions options, List<File> files, List<ApkArtifact> artifacts)
            throws IOException {
        Intent intent = new Intent(context, InstallResultReceiver.class).setAction(ACTION_INSTALL_STATUS)
                .putExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME, options.getPackageName())
                .putExtra(InstallResultReceiver.EXTRA_DISPLAY_NAME, options.getDisplayName())
                .putExtra(InstallResultReceiver.EXTRA_VERSION_NAME, options.getVersionName())
                .putExtra(InstallResultReceiver.EXTRA_VERSION_CODE, options.getVersionCode())
                .putExtra(InstallResultReceiver.EXTRA_ICON_URL, options.getIconUrl())
                .putExtra(InstallResultReceiver.EXTRA_FIRST_INSTALL, options.isFirstInstall())
                .putExtra(InstallResultReceiver.EXTRA_SAVE_TO_DOWNLOADS, options.isSaveToDownloads())
                .putExtra(InstallResultReceiver.EXTRA_DELETE_AFTER_INSTALL, options.isDeleteAfterInstall())
                .putStringArrayListExtra(InstallResultReceiver.EXTRA_FILES, paths(files))
                .putStringArrayListExtra(InstallResultReceiver.EXTRA_ARTIFACT_NAMES, artifactNames(artifacts));
        PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            session.commit(pending.getIntentSender());
        }
    }

    private void abandon(PackageInstaller installer, int sessionId) {
        installer.abandonSession(sessionId);
    }

    private void verifyUnknownSourcesPermission(Context context) throws IOException {
        if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
            throw new IOException("请先允许本应用安装未知来源应用");
        }
    }

    private ArrayList<String> paths(List<File> files) {
        ArrayList<String> result = new ArrayList<>();
        for (File file : files) result.add(file.getAbsolutePath());
        return result;
    }

    private ArrayList<String> artifactNames(List<ApkArtifact> artifacts) {
        ArrayList<String> result = new ArrayList<>();
        for (ApkArtifact artifact : artifacts) result.add(artifact.getName());
        return result;
    }
}
