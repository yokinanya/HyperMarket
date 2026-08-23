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
import java.lang.reflect.Field;
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
                installSession(new InstallRequest(
                        context, installer, new InstallPayload(files, artifacts, options)));
                return false;
            } catch (Exception exception) {
                throw new IOException("Shizuku 安装失败: " + exception.getMessage(), exception);
            }
        }
        verifyUnknownSourcesPermission(context);
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        installSession(new InstallRequest(
                context, installer, new InstallPayload(files, artifacts, options)));
        return false;
    }

    private void installSession(InstallRequest request) throws IOException {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        InstallOptions options = request.payload.options;
        params.setAppPackageName(options.getPackageName());
        params.setSize(request.payload.files.stream().mapToLong(File::length).sum());
        params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        if (!options.isFirstInstall()) enableReplacement(params);
        if (Build.VERSION.SDK_INT >= 31 && options.isNoUserAction()) {
            params.setRequireUserAction(
                    PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            params.setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST);
        }
        int sessionId = request.installer.createSession(params);
        writeSession(request, sessionId);
        commit(request, sessionId);
    }

    private void enableReplacement(PackageInstaller.SessionParams params) throws IOException {
        try {
            Field flags = PackageInstaller.SessionParams.class.getDeclaredField("installFlags");
            Field replacement = PackageManager.class.getDeclaredField("INSTALL_REPLACE_EXISTING");
            flags.setAccessible(true);
            replacement.setAccessible(true);
            flags.setInt(params, flags.getInt(params) | replacement.getInt(null));
        } catch (ReflectiveOperationException exception) {
            throw new IOException("无法启用应用更新安装标志", exception);
        }
    }

    private void writeSession(InstallRequest request, int sessionId) throws IOException {
        try (PackageInstaller.Session session = openSession(request, sessionId)) {
            for (int index = 0; index < request.payload.files.size(); index++) {
                writeFile(session, request.payload.files.get(index),
                        request.payload.artifacts.get(index));
            }
        } catch (IOException | RuntimeException exception) {
            abandon(request.installer, sessionId);
            throw new IOException("Cannot write install session " + sessionId + ": "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage(), exception);
        }
    }

    private PackageInstaller.Session openSession(InstallRequest request, int sessionId)
            throws IOException {
        if (!"Shizuku 静默安装".equals(request.payload.options.getInstallerMode())) {
            return request.installer.openSession(sessionId);
        }
        try {
            return ShizukuBridge.openSession(request.installer, sessionId);
        } catch (Exception exception) {
            throw new IOException("Cannot proxy Shizuku install session " + sessionId, exception);
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

    private void commit(InstallRequest request, int sessionId) throws IOException {
        InstallPayload payload = request.payload;
        InstallOptions options = payload.options;
        Intent intent = new Intent(request.context, InstallResultReceiver.class)
                .setAction(ACTION_INSTALL_STATUS)
                .putExtra(InstallResultReceiver.EXTRA_PACKAGE_NAME, options.getPackageName())
                .putExtra(InstallResultReceiver.EXTRA_DISPLAY_NAME, options.getDisplayName())
                .putExtra(InstallResultReceiver.EXTRA_VERSION_NAME, options.getVersionName())
                .putExtra(InstallResultReceiver.EXTRA_VERSION_CODE, options.getVersionCode())
                .putExtra(InstallResultReceiver.EXTRA_ICON_URL, options.getIconUrl())
                .putExtra(InstallResultReceiver.EXTRA_FIRST_INSTALL, options.isFirstInstall())
                .putExtra(InstallResultReceiver.EXTRA_SAVE_TO_DOWNLOADS, options.isSaveToDownloads())
                .putStringArrayListExtra(InstallResultReceiver.EXTRA_FILES, paths(payload.files))
                .putStringArrayListExtra(
                        InstallResultReceiver.EXTRA_ARTIFACT_NAMES, artifactNames(payload.artifacts));
        PendingIntent pending = PendingIntent.getBroadcast(request.context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        try (PackageInstaller.Session session = openSession(request, sessionId)) {
            session.commit(pending.getIntentSender());
        }
    }

    private void abandon(PackageInstaller installer, int sessionId) {
        installer.abandonSession(sessionId);
    }

    private void verifyUnknownSourcesPermission(Context context) throws IOException {
        if (!context.getPackageManager().canRequestPackageInstalls()) {
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

    private static final class InstallPayload {
        private final List<File> files;
        private final List<ApkArtifact> artifacts;
        private final InstallOptions options;

        private InstallPayload(
                List<File> files, List<ApkArtifact> artifacts, InstallOptions options) {
            this.files = files;
            this.artifacts = artifacts;
            this.options = options;
        }
    }

    private static final class InstallRequest {
        private final Context context;
        private final PackageInstaller installer;
        private final InstallPayload payload;

        private InstallRequest(
                Context context, PackageInstaller installer, InstallPayload payload) {
            this.context = context;
            this.installer = installer;
            this.payload = payload;
        }
    }
}
