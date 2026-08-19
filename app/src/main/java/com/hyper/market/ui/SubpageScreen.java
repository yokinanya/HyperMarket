package com.hyper.market.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.hyper.market.R;

import java.io.File;
import java.util.function.BiConsumer;

public final class SubpageScreen {
    private SubpageScreen() { }

    public static View simple(Context context, String title, String summary, Runnable back) {
        LinearLayout content = page(context, title, summary, back);
        LinearLayout card = UiFactory.card(context);
        card.addView(UiFactory.secondary(context, "暂无内容"));
        content.addView(card);
        return UiFactory.scroll(context, content);
    }

    public static View savedPackages(Context context, Runnable back) {
        LinearLayout content = page(context, "保存的安装包", "管理已下载的安装包", back);
        LinearLayout card = UiFactory.card(context);
        File directory = new File(context.getFilesDir(), "downloads");
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            card.addView(UiFactory.secondary(context, "暂无保存的安装包"));
        } else {
            for (File file : files) {
                card.addView(UiFactory.text(context, file.getName(), 15, R.color.text_primary));
                card.addView(UiFactory.secondary(context, formatSize(file.length())));
            }
        }
        content.addView(card);
        return UiFactory.scroll(context, content);
    }

    public static View manualUpdate(Context context, Runnable back, BiConsumer<String, String> request) {
        LinearLayout content = page(context, "手动更新", "按包名与版本号查询单个应用的更新", back);
        EditText packageInput = UiFactory.input(context, "包名（packageName）");
        EditText versionInput = UiFactory.input(context, "版本号（versionCode）");
        Button submit = UiFactory.button(context, "请求");
        submit.setOnClickListener(view -> request.accept(packageInput.getText().toString().trim(),
                versionInput.getText().toString().trim()));
        content.addView(packageInput);
        content.addView(versionInput);
        content.addView(submit);
        content.addView(UiFactory.card(context));
        return UiFactory.scroll(context, content);
    }

    public static View installer(Context context, Runnable back) {
        LinearLayout content = page(context, "安装方式", "选择安装器与安装包保存选项", back);
        content.addView(option(context, "标准安装", "普通应用走系统确认，具备系统权限时自动静默安装", true));
        content.addView(option(context, "Root 静默安装", "需要 root 权限", false));
        content.addView(option(context, "Shizuku 静默安装", "需要 Shizuku 正在运行并已授权", false));
        content.addView(option(context, "第三方包安装器", "选择能够处理安装包的应用", false));
        content.addView(option(context, "增量更新", "可用时下载补丁包并合成为 APK", false));
        content.addView(option(context, "安装包保存至 Download", "安装时同时将安装包保存到系统 Download 目录", false));
        return UiFactory.scroll(context, content);
    }

    public static View deviceProfile(Context context, Runnable back) {
        LinearLayout content = page(context, "设备信息", "请求使用的设备指纹与版本参数", back);
        LinearLayout card = UiFactory.card(context);
        addInfo(card, "厂商", Build.MANUFACTURER);
        addInfo(card, "型号", Build.MODEL);
        addInfo(card, "Android", Build.VERSION.RELEASE);
        addInfo(card, "SDK", String.valueOf(Build.VERSION.SDK_INT));
        addInfo(card, "产品", Build.PRODUCT);
        content.addView(card);
        return UiFactory.scroll(context, content);
    }

    public static View about(Context context, Runnable back) {
        LinearLayout content = page(context, "关于", "版本信息与项目地址", back);
        LinearLayout card = UiFactory.card(context);
        card.addView(UiFactory.title(context, "HyperMarket Rebuilt"));
        card.addView(UiFactory.secondary(context, "按原 APK 行为重建的社区维护版本\n版本 0.1.0-rebuilt"));
        content.addView(card);
        content.addView(UiFactory.section(context, "开放开源代码许可"));
        content.addView(UiFactory.card(context));
        content.addView(UiFactory.secondary(context,
                "JetBrains Compose Multiplatform\nKoin\nKtor\nCoil\nShizuku\nAndroidHiddenApiBypass\nComposeMediaPlayer\nHyperNotification"));
        return UiFactory.scroll(context, content);
    }

    private static LinearLayout page(Context context, String title, String summary, Runnable back) {
        LinearLayout content = UiFactory.column(context);
        content.setPadding(UiFactory.dp(context, 22), UiFactory.dp(context, 24),
                UiFactory.dp(context, 22), UiFactory.dp(context, 32));
        content.setBackgroundColor(context.getColor(R.color.background));
        Button backButton = UiFactory.button(context, "‹  返回");
        backButton.setOnClickListener(view -> back.run());
        content.addView(backButton);
        content.addView(UiFactory.title(context, title));
        content.addView(UiFactory.secondary(context, summary));
        return content;
    }

    private static View option(Context context, String title, String summary, boolean checked) {
        LinearLayout card = UiFactory.card(context);
        LinearLayout row = UiFactory.row(context);
        LinearLayout text = UiFactory.column(context);
        text.addView(UiFactory.text(context, title, 16, R.color.text_primary));
        text.addView(UiFactory.secondary(context, summary));
        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(toggle);
        card.addView(row);
        return card;
    }

    private static void addInfo(LinearLayout card, String key, String value) {
        card.addView(UiFactory.text(card.getContext(), key, 14, R.color.text_secondary));
        card.addView(UiFactory.text(card.getContext(), value, 16, R.color.text_primary));
        card.addView(UiFactory.divider(card.getContext()));
    }

    private static String formatSize(long bytes) {
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
