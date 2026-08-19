package com.hyper.market.installer;

import com.hyper.market.model.ApkArtifact;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;

public final class FileDownloader {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    public FileDownloader() { }

    public File download(File directory, ApkArtifact artifact) throws IOException {
        return download(directory, artifact, new DownloadControl(), null);
    }

    public File download(File directory, ApkArtifact artifact, DownloadControl control)
            throws IOException {
        return download(directory, artifact, control, null);
    }

    public File download(File directory, ApkArtifact artifact, DownloadControl control,
                         DownloadControl.ProgressListener listener) throws IOException {
        return download(directory, safeName(artifact.getName()), artifact.getUrl(),
                artifact.getSize(), artifact.getHash(), control, listener);
    }

    public File downloadDelta(File directory, ApkArtifact artifact) throws IOException {
        return downloadDelta(directory, artifact, new DownloadControl(), null);
    }

    public File downloadDelta(File directory, ApkArtifact artifact, DownloadControl control,
                              DownloadControl.ProgressListener listener) throws IOException {
        return download(directory, safeName(artifact.getName()) + ".patch", artifact.getDiffUrl(),
                artifact.getDiffSize(), artifact.getDiffHash(), control, listener);
    }

    public void verify(File file, ApkArtifact artifact) throws IOException {
        verifySize(file, artifact.getName(), artifact.getSize());
        verifyChecksum(file, artifact.getName(), artifact.getHash());
    }

    private File download(File directory, String fileName, String url, long expectedSize,
                          String expectedHash, DownloadControl control,
                          DownloadControl.ProgressListener listener) throws IOException {
        control.throwIfCancelled();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Cannot create download directory: " + directory);
        }
        File target = new File(directory, fileName);
        File partial = new File(directory, fileName + ".part");
        if (isComplete(partial, fileName, expectedSize, expectedHash)) {
            move(partial, target);
            return target;
        }
        long offset = partial.isFile() ? partial.length() : 0;
        HttpURLConnection connection = open(url);
        try {
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Download HTTP " + status + " for " + url);
            }
            boolean append = offset > 0 && status == HttpURLConnection.HTTP_PARTIAL;
            if (!append) {
                offset = 0;
                if (partial.exists() && !partial.delete()) {
                    throw new IOException("无法重置未完成的安装包：" + partial);
                }
            }
            writeBody(connection.getInputStream(), partial, offset, expectedSize, control, listener);
            move(partial, target);
            verifySize(target, fileName, expectedSize);
            verifyChecksum(target, fileName, expectedHash);
            return target;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "HyperMarketRebuilt/0.1");
        return connection;
    }

    private void writeBody(InputStream input, File target, long offset, long expectedSize,
                           DownloadControl control,
                           DownloadControl.ProgressListener listener) throws IOException {
        try (InputStream source = input;
             FileOutputStream output = new FileOutputStream(target, offset > 0)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            long downloaded = offset;
            while ((count = source.read(buffer)) != -1) {
                control.awaitIfPaused();
                output.write(buffer, 0, count);
                downloaded += count;
                if (listener != null) listener.onProgress(downloaded, expectedSize);
            }
        }
    }

    private boolean isComplete(File partial, String name, long expectedSize, String expectedHash)
            throws IOException {
        if (!partial.isFile()) return false;
        if (expectedSize <= 0 || partial.length() != expectedSize) return false;
        try {
            verifyChecksum(partial, name, expectedHash);
            return true;
        } catch (IOException exception) {
            if (!partial.delete()) {
                throw new IOException("无法删除校验失败的临时文件：" + partial, exception);
            }
            return false;
        }
    }

    private void move(File source, File target) throws IOException {
        if (target.exists() && !target.delete()) {
            throw new IOException("无法替换旧安装包：" + target.getAbsolutePath());
        }
        if (!source.renameTo(target)) {
            throw new IOException("无法完成安装包下载：" + target.getAbsolutePath());
        }
    }

    private void verifySize(File file, String name, long expected) throws IOException {
        if (expected > 0 && file.length() != expected) {
            throw new IOException("Downloaded size mismatch for " + name
                    + ": expected " + expected + ", actual " + file.length());
        }
    }

    private void verifyChecksum(File file, String name, String expected) throws IOException {
        if (expected == null || expected.isBlank()) return;
        String algorithm = expected.length() == 64 ? "SHA-256" : "MD5";
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream input = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            String actual = hex(digest.digest());
            if (!actual.equals(expected.toLowerCase(Locale.ROOT))) {
                throw new IOException("Checksum mismatch for " + name);
            }
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("不支持摘要算法：" + algorithm, exception);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }

    private String safeName(String name) {
        String normalized = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Artifact name is empty");
        }
        return normalized;
    }
}
