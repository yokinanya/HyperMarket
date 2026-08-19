package com.hyper.market.installer;

import com.github.sisong.sfpatcher;

import java.io.File;
import java.io.IOException;

public final class DeltaPatcher {
    private static final int PATCH_VERSION = 3;

    public File apply(File patch, File base, File output) throws IOException {
        if (!patch.isFile()) throw new IOException("增量补丁不存在：" + patch);
        if (!base.isFile()) throw new IOException("增量更新基包不存在：" + base);
        if (output.exists() && !output.delete()) {
            throw new IOException("无法覆盖增量更新输出文件：" + output);
        }
        try {
            int status = sfpatcher.getInstance().patch(
                    patch.getAbsolutePath(), base.getAbsolutePath(), output.getAbsolutePath());
            if (status != 0) throw new IOException("增量合成失败，返回码：" + status);
        } catch (RuntimeException exception) {
            throw new IOException("增量更新引擎执行失败：" + exception.getMessage(), exception);
        }
        if (!output.isFile() || output.length() == 0) {
            throw new IOException("增量合成产生了空 APK");
        }
        return output;
    }

    public void verifyVersion(int version) throws IOException {
        if (version != PATCH_VERSION) {
            throw new IOException("不支持的增量补丁版本：" + version);
        }
    }
}
