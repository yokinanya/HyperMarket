package com.hyper.market.ui;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.hyper.market.R;

public final class UpdatesScreen {
    public interface Actions {
        void checkUpdates();
        void openManualUpdate();
        void openSavedPackages();
        void openIgnoredApps();
        void openHistory();
    }

    private final Context context;
    private final Actions actions;
    private final LinearLayout content;

    private UpdatesScreen(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
        content = UiFactory.column(context);
    }

    public static UpdatesScreen create(Context context, Actions actions) {
        return new UpdatesScreen(context, actions);
    }

    public View getView() {
        content.setPadding(UiFactory.dp(context, 22), UiFactory.dp(context, 28),
                UiFactory.dp(context, 22), UiFactory.dp(context, 28));
        content.setBackgroundColor(context.getColor(R.color.background));
        content.addView(UiFactory.secondary(context, "应用管理"));
        content.addView(UiFactory.title(context, "更新"));
        content.addView(createCheckCard());
        content.addView(UiFactory.section(context, "更新工具"));
        content.addView(toolCard("手动更新", "按包名与 versionCode 查询单个应用的更新",
                view -> actions.openManualUpdate()));
        content.addView(toolCard("保存的安装包", "管理已下载的安装包",
                view -> actions.openSavedPackages()));
        content.addView(toolCard("忽略的更新", "查看并恢复已忽略的应用更新",
                view -> actions.openIgnoredApps()));
        content.addView(toolCard("更新历史", "查看通过本应用完成的安装与更新",
                view -> actions.openHistory()));
        return UiFactory.scroll(context, content);
    }

    private View createCheckCard() {
        LinearLayout card = UiFactory.card(context);
        card.addView(UiFactory.text(context, "可更新应用", 18, R.color.text_primary));
        card.addView(UiFactory.secondary(context, "需要授予应用列表权限才能检查更新"));
        Button check = UiFactory.button(context, "检查更新");
        check.setOnClickListener(view -> actions.checkUpdates());
        card.addView(check);
        UiFactory.margin(card, context, 16, 0);
        return card;
    }

    private View toolCard(String title, String summary, View.OnClickListener listener) {
        LinearLayout card = UiFactory.card(context);
        card.setOnClickListener(listener);
        card.addView(UiFactory.text(context, title, 16, R.color.text_primary));
        card.addView(UiFactory.secondary(context, summary));
        return card;
    }
}
