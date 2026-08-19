package com.hyper.market.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hyper.market.R;
import com.hyper.market.model.MarketAppInfo;

import java.util.List;
public final class HomeScreen {
    public interface Actions {
        void openSearch(String keyword);
        void openUpdates();
        void openSaved();
        void openSettings();
        void openApp(MarketAppInfo app);
    }

    private final Context context;
    private final LinearLayout featured;
    private final Actions actions;

    private HomeScreen(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
        featured = UiFactory.column(context);
    }

    public static HomeScreen create(Context context, Actions actions) {
        HomeScreen screen = new HomeScreen(context, actions);
        screen.build();
        return screen;
    }

    public View getView() {
        LinearLayout content = UiFactory.column(context);
        content.setPadding(UiFactory.dp(context, 22), UiFactory.dp(context, 28),
                UiFactory.dp(context, 22), UiFactory.dp(context, 28));
        content.setBackgroundColor(context.getColor(R.color.background));
        content.addView(UiFactory.secondary(context, "MIUI 社区维护版"));
        content.addView(UiFactory.title(context, "应用商店"));
        content.addView(UiFactory.secondary(context, "浏览、搜索并安装小米市场中的应用"));
        content.addView(createSearchCard());
        content.addView(UiFactory.section(context, "快捷入口"));
        content.addView(createQuickActions());
        content.addView(UiFactory.section(context, "精选应用"));
        content.addView(featured);
        content.addView(createStatusCard());
        return UiFactory.scroll(context, content);
    }

    public void showFeatured(List<MarketAppInfo> apps) {
        featured.removeAllViews();
        if (apps.isEmpty()) {
            featured.addView(UiFactory.secondary(context, "暂无精选内容，请使用搜索功能"));
            return;
        }
        for (MarketAppInfo app : apps) {
            featured.addView(AppRowView.create(context, app, actions::openApp));
            addGap(featured, 8);
        }
    }

    public void showFeaturedError(String message) {
        featured.removeAllViews();
        featured.addView(UiFactory.secondary(context, "精选加载失败: " + message));
    }

    private void build() {
        featured.addView(UiFactory.secondary(context, "正在读取精选应用…"));
    }

    private View createSearchCard() {
        LinearLayout card = UiFactory.card(context);
        TextView title = UiFactory.text(context, "搜索应用", 17, R.color.text_primary);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);
        card.addView(UiFactory.secondary(context, "支持应用名称、包名和关键词"));
        LinearLayout bar = UiFactory.row(context);
        EditText input = UiFactory.input(context, "搜索应用");
        Button search = UiFactory.button(context, "搜索");
        search.setOnClickListener(view -> openSearch(input.getText().toString()));
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(search);
        card.addView(bar);
        UiFactory.margin(card, context, 18, 0);
        return card;
    }

    private View createQuickActions() {
        LinearLayout actions = UiFactory.row(context);
        actions.addView(action("更新", "检查应用更新", view -> this.actions.openUpdates()), weightParams());
        actions.addView(action("安装包", "管理已下载文件", view -> this.actions.openSaved()), weightParams());
        actions.addView(action("设置", "安装与设备选项", view -> this.actions.openSettings()), weightParams());
        return actions;
    }

    private View action(String title, String summary, View.OnClickListener listener) {
        LinearLayout card = UiFactory.card(context);
        TextView titleView = UiFactory.text(context, title, 16, R.color.text_primary);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(titleView);
        card.addView(UiFactory.secondary(context, summary));
        if (listener != null) {
            card.setOnClickListener(listener);
        }
        return card;
    }

    private View createStatusCard() {
        LinearLayout card = UiFactory.card(context);
        card.addView(UiFactory.text(context, "维护版状态", 16, R.color.text_primary));
        card.addView(UiFactory.secondary(context,
                "当前版本保留原应用的数据接口与安装流程，界面正在逐步恢复。"));
        UiFactory.margin(card, context, 24, 0);
        return card;
    }

    private void openSearch(String keyword) {
        actions.openSearch(keyword == null ? "" : keyword.trim());
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(UiFactory.dp(context, 4), 0, UiFactory.dp(context, 4), 0);
        return params;
    }

    private void addGap(LinearLayout parent, int height) {
        View gap = new View(context);
        parent.addView(gap, new LinearLayout.LayoutParams(1, UiFactory.dp(context, height)));
    }

}
