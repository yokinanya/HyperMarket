package com.hyper.market.installer;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class RootApkInstaller {
    private static final String SINGLE_INSTALL = "pm install -r --user 0 ";
    private static final String MULTI_INSTALL = "pm install-multi-package -r --user 0";

    public void install(List<File> files) throws IOException {
        Process process = new ProcessBuilder("su", "-c", command(files))
                .redirectErrorStream(true).start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes());
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
        StringBuilder command = new StringBuilder(files.size() == 1 ? SINGLE_INSTALL : MULTI_INSTALL);
        for (File file : files) {
            if (!file.isFile()) throw new IOException("安装包不存在：" + file);
            command.append(' ').append(quote(file.getAbsolutePath()));
        }
        return command.toString();
    }

    private String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
