package com.hyper.market.installer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import com.hyper.market.model.ApkArtifact;
import com.hyper.market.model.MarketAppInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class DownloadArchive {
    private static final String ARCHIVE_DIRECTORY = "Download/HyperMarket";

    private DownloadArchive() { }

    public static SavedLocation save(Context context, MarketAppInfo app, ApkArtifact artifact,
                                     File source) throws IOException {
        return save(context, app, artifact.getName(), source, false);
    }

    public static SavedLocation save(Context context, MarketAppInfo app, String artifactName,
                                     File source) throws IOException {
        return save(context, app, artifactName, source, false);
    }

    public static SavedLocation save(Context context, MarketAppInfo app, String artifactName,
                                     File source, boolean grouped) throws IOException {
        String name = archiveName(app, artifactName, grouped);
        String directory = grouped ? ARCHIVE_DIRECTORY + "/" + groupDirectory(app)
                : ARCHIVE_DIRECTORY;
        if (Build.VERSION.SDK_INT >= 29) {
            return saveMediaStore(context, directory, name, source);
        }
        return saveLegacy(context, directory, name, source);
    }

    @RequiresApi(29)
    private static SavedLocation saveMediaStore(Context context, String directory, String name,
                                                File source) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, directory);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("无法在 Download 目录创建安装包");
        }
        try {
            copy(source, resolver.openOutputStream(uri));
            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, published, null, null) != 1) {
                throw new IOException("无法发布 Download 中的安装包：" + name);
            }
            return new SavedLocation(uri.toString(), source.length());
        } catch (IOException | RuntimeException exception) {
            resolver.delete(uri, null, null);
            throw new IOException("保存安装包失败：" + name, exception);
        }
    }

    private static SavedLocation saveLegacy(Context context, String relativeDirectory,
                                            String name, File source) throws IOException {
        File downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null) throw new IOException("无法获取 Download 目录");
        File directory = new File(downloads, relativeDirectory.substring("Download/".length()));
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建 Download/HyperMarket 目录");
        }
        File target = new File(directory, name);
        copy(source, new FileOutputStream(target));
        return new SavedLocation(target.getAbsolutePath(), target.length());
    }

    private static void copy(File source, OutputStream output) throws IOException {
        if (output == null) {
            throw new IOException("无法打开安装包保存目标");
        }
        try (InputStream input = new FileInputStream(source); OutputStream destination = output) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                destination.write(buffer, 0, count);
            }
        }
    }

    private static String archiveName(MarketAppInfo app, String artifactName, boolean grouped) {
        if (grouped) {
            String normalized = sanitize(artifactName);
            return normalized.endsWith(".apk") ? normalized : normalized + ".apk";
        }
        String version = app.getVersionName().isEmpty()
                ? String.valueOf(app.getVersionCode()) : app.getVersionName();
        return sanitize(app.getDisplayName() + "-" + version) + ".apk";
    }

    private static String groupDirectory(MarketAppInfo app) {
        return sanitize(app.getPackageName() + "-" + app.getVersionCode());
    }

    private static String sanitize(String value) {
        String normalized = value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("安装包名称为空");
        }
        return normalized;
    }

    public static final class SavedLocation {
        private final String location;
        private final long size;

        public SavedLocation(String location, long size) {
            this.location = location;
            this.size = size;
        }

        public String getLocation() { return location; }
        public long getSize() { return size; }
    }
}
