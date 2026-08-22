package com.hyper.market.installer;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class RootApkInstaller {
    private static final String INSTALL = "pm install -r --user 0 ";

    public void install(List<File> files) throws IOException {
        Process process = new ProcessBuilder("su", "-c", command(files))
                .redirectErrorStream(true).start();
        String output;
        try {
            output = readOutput(process.getInputStream());
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.contains("Failure")) {
                throw new IOException("Root 安装失败（" + exitCode + "）：" + output.trim());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Root 安装被中断", exception);
        }
    }

    private String command(List<File> files) throws IOException {
        if (files.isEmpty()) throw new IOException("没有可安装的 APK");
        StringBuilder command = new StringBuilder(INSTALL);
        for (File file : files) {
            if (!file.isFile()) throw new IOException("安装包不存在：" + file);
            command.append(' ').append(quote(file.getAbsolutePath()));
        }
        return command.toString();
    }

    private String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    private String readOutput(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toString(java.nio.charset.StandardCharsets.UTF_8.name());
    }
}
