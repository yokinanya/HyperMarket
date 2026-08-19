package com.hyper.market.ui;

import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.hyper.market.R;

public final class SettingsScreen {
    public interface Actions {
        void openInstaller();
        void openDeviceProfile();
        void openAbout();
    }

    private final Context context;
    private final Actions actions;
    private final LinearLayout content;

    private SettingsScreen(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
        content = UiFactory.column(context);
    }

    public static SettingsScreen create(Context context, Actions actions) {
        return new SettingsScreen(context, actions);
    }

    public View getView() {
        content.setPadding(UiFactory.dp(context, 22), UiFactory.dp(context, 28),
                UiFactory.dp(context, 22), UiFactory.dp(context, 28));
        content.setBackgroundColor(context.getColor(R.color.background));
        content.addView(UiFactory.secondary(context, "HyperMarket"));
        content.addView(UiFactory.title(context, "设置"));
        content.addView(UiFactory.section(context, "通用"));
        content.addView(switchCard("去除推广应用", "隐藏搜索结果中标记为推广的 App", true));
        content.addView(switchCard("优化应用名称", "移除推广语，名称本身含横线的可能会误裁", true));
        content.addView(switchCard("显示评论", "在应用详情页显示用户评论", true));
        content.addView(UiFactory.section(context, "安装方式"));
        content.addView(linkCard("安装方式", "选择系统、Root、Shizuku 或第三方包安装器",
                view -> actions.openInstaller()));
        content.addView(linkCard("设备信息", "请求使用的设备指纹与版本参数",
                view -> actions.openDeviceProfile()));
        content.addView(UiFactory.section(context, "关于"));
        content.addView(linkCard("关于本软件", "版本信息与项目地址",
                view -> actions.openAbout()));
        return UiFactory.scroll(context, content);
    }

    private View switchCard(String title, String summary, boolean checked) {
        LinearLayout card = UiFactory.card(context);
        LinearLayout row = UiFactory.row(context);
        LinearLayout text = UiFactory.column(context);
        text.addView(UiFactory.text(context, title, 16, R.color.text_primary));
        text.addView(UiFactory.secondary(context, summary));
        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(this::onSettingChanged);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(toggle);
        card.addView(row);
        return card;
    }

    private View linkCard(String title, String summary, View.OnClickListener listener) {
        LinearLayout card = UiFactory.card(context);
        card.setOnClickListener(listener);
        card.addView(UiFactory.text(context, title + "  ›", 16, R.color.text_primary));
        card.addView(UiFactory.secondary(context, summary));
        return card;
    }

    private void onSettingChanged(CompoundButton button, boolean checked) {
        button.setContentDescription(checked ? "已开启" : "已关闭");
    }
}
