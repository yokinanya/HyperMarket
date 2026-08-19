package com.hyper.market.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hyper.market.R;
import com.hyper.market.model.MarketAppInfo;

import java.util.List;

public final class SearchScreen {
    public interface Actions {
        void search(String keyword);
        void openApp(MarketAppInfo app);
    }

    private final Context context;
    private final Actions actions;
    private final EditText input;
    private final TextView status;
    private final LinearLayout results;

    private SearchScreen(Context context, Actions actions) {
        this.context = context;
        this.actions = actions;
        input = UiFactory.input(context, "搜索应用");
        status = UiFactory.secondary(context, "输入关键词搜索应用");
        results = UiFactory.column(context);
    }

    public static SearchScreen create(Context context, Actions actions) {
        return new SearchScreen(context, actions);
    }

    public View getView() {
        LinearLayout content = UiFactory.column(context);
        content.setPadding(UiFactory.dp(context, 22), UiFactory.dp(context, 28),
                UiFactory.dp(context, 22), UiFactory.dp(context, 28));
        content.setBackgroundColor(context.getColor(R.color.background));
        content.addView(UiFactory.secondary(context, "应用市场"));
        content.addView(UiFactory.title(context, "搜索"));
        content.addView(createSearchBar());
        content.addView(createHistoryCard());
        content.addView(status);
        content.addView(results);
        return UiFactory.scroll(context, content);
    }

    public void setKeyword(String keyword) {
        input.setText(keyword);
        input.setSelection(input.length());
    }

    public void showResults(List<MarketAppInfo> apps) {
        results.removeAllViews();
        status.setText(apps.isEmpty() ? "未找到结果" : "找到 " + apps.size() + " 个应用");
        for (MarketAppInfo app : apps) {
            results.addView(AppRowView.create(context, app, actions::openApp));
            View gap = new View(context);
            results.addView(gap, new LinearLayout.LayoutParams(1, UiFactory.dp(context, 8)));
        }
    }

    public void showStatus(String text) {
        status.setText(text);
    }

    private View createSearchBar() {
        LinearLayout bar = UiFactory.row(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button button = UiFactory.button(context, "搜索");
        button.setOnClickListener(view -> actions.search(input.getText().toString().trim()));
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(button);
        return bar;
    }

    private View createHistoryCard() {
        LinearLayout card = UiFactory.card(context);
        card.addView(UiFactory.text(context, "搜索提示", 16, R.color.text_primary));
        card.addView(UiFactory.secondary(context, "可以输入应用名称或完整包名，例如 com.android.settings"));
        UiFactory.margin(card, context, 16, 16);
        return card;
    }
}
