package com.hyper.market.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hyper.market.R;
import com.hyper.market.model.MarketAppInfo;

public final class DetailScreen {
    public interface Actions {
        void back();
        void download(MarketAppInfo app);
    }

    private final Context context;
    private final Actions actions;
    private final LinearLayout content;
    private final TextView status;
    private MarketAppInfo app;

    private DetailScreen(Context context, MarketAppInfo app, Actions actions) {
        this.context = context;
        this.app = app;
        this.actions = actions;
        content = UiFactory.column(context);
        status = UiFactory.secondary(context, "正在加载详情…");
    }

    public static DetailScreen create(Context context, MarketAppInfo app, Actions actions) {
        return new DetailScreen(context, app, actions);
    }

    public View getView() {
        content.setPadding(UiFactory.dp(context, 22), UiFactory.dp(context, 24),
                UiFactory.dp(context, 22), UiFactory.dp(context, 32));
        content.setBackgroundColor(context.getColor(R.color.background));
        rebuild();
        return UiFactory.scroll(context, content);
    }

    private void rebuild() {
        content.removeAllViews();
        content.addView(createToolbar());
        content.addView(createHeader());
        content.addView(status);
        content.addView(UiFactory.section(context, "应用介绍"));
        content.addView(infoCard());
        content.addView(UiFactory.section(context, "更新日志"));
        content.addView(logCard());
        content.addView(UiFactory.section(context, "评论与评分"));
        content.addView(emptyCard("评论接口已保留，加载后会显示评分和评论内容"));
        content.addView(UiFactory.section(context, "优惠活动"));
        content.addView(emptyCard("暂无优惠活动"));
    }

    public void showDetail(MarketAppInfo detail) {
        app = detail;
        status.setText("详情已加载");
        rebuild();
    }

    public void showError(String message) {
        status.setText("加载详情失败: " + message);
    }

    public void showDownloadStatus(String message) {
        status.setText(message);
    }

    private View createToolbar() {
        LinearLayout bar = UiFactory.row(context);
        Button back = UiFactory.button(context, "‹  返回");
        back.setOnClickListener(view -> actions.back());
        bar.addView(back);
        return bar;
    }

    private View createHeader() {
        LinearLayout card = UiFactory.card(context);
        TextView icon = UiFactory.text(context, initial(), 32, R.color.text_primary);
        icon.setGravity(android.view.Gravity.CENTER);
        icon.setTextColor(android.graphics.Color.WHITE);
        icon.setBackgroundColor(0xFF97533F);
        card.addView(icon, new LinearLayout.LayoutParams(-1, UiFactory.dp(context, 86)));
        card.addView(UiFactory.title(context, app.getDisplayName()));
        card.addView(UiFactory.secondary(context, app.getPackageName()));
        card.addView(UiFactory.secondary(context, "版本 " + app.getVersionName()
                + "  ·  " + sizeText(app.getApkSize()) + "  ·  评分 " + app.getRatingScore()));
        LinearLayout actionsRow = UiFactory.row(context);
        Button download = UiFactory.button(context, "下载并安装");
        download.setOnClickListener(view -> actions.download(app));
        Button save = UiFactory.button(context, "保存");
        actionsRow.addView(download, new LinearLayout.LayoutParams(0, -2, 1));
        actionsRow.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(actionsRow);
        UiFactory.margin(card, context, 16, 0);
        return card;
    }

    private View infoCard() {
        return emptyCard(app.getChangeLog().isEmpty() ? "暂无应用介绍" : app.getChangeLog());
    }

    private View logCard() {
        return emptyCard(app.getChangeLog().isEmpty() ? "暂无更新日志" : app.getChangeLog());
    }

    private View emptyCard(String message) {
        LinearLayout card = UiFactory.card(context);
        card.addView(UiFactory.secondary(context, message));
        return card;
    }

    private String initial() {
        String name = app.getDisplayName();
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }

    private String sizeText(long bytes) {
        if (bytes <= 0) {
            return "大小未知";
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
