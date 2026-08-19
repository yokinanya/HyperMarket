package com.hyper.market.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hyper.market.R;
import com.hyper.market.model.MarketAppInfo;

import java.util.function.Consumer;

public final class AppRowView {
    private AppRowView() { }

    public static LinearLayout create(Context context, MarketAppInfo app, Consumer<MarketAppInfo> onClick) {
        LinearLayout row = UiFactory.row(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiFactory.dp(context, 14), UiFactory.dp(context, 12),
                UiFactory.dp(context, 10), UiFactory.dp(context, 12));
        row.setBackground(background(context));
        row.setOnClickListener(view -> onClick.accept(app));

        TextView icon = icon(context, app.getDisplayName());
        row.addView(icon, new LinearLayout.LayoutParams(UiFactory.dp(context, 52), UiFactory.dp(context, 52)));

        LinearLayout info = UiFactory.column(context);
        info.setPadding(UiFactory.dp(context, 14), 0, UiFactory.dp(context, 8), 0);
        info.addView(UiFactory.text(context, app.getDisplayName(), 16, R.color.text_primary));
        info.addView(UiFactory.secondary(context, app.getPackageName()));
        info.addView(UiFactory.secondary(context, versionText(app)));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = UiFactory.text(context, "›", 28, R.color.text_secondary);
        row.addView(arrow, new LinearLayout.LayoutParams(UiFactory.dp(context, 28), -2));
        return row;
    }

    private static String versionText(MarketAppInfo app) {
        String rating = app.getRatingScore() > 0 ? " · 评分 " + app.getRatingScore() : "";
        return "版本 " + app.getVersionName() + rating;
    }

    private static TextView icon(Context context, String name) {
        TextView icon = UiFactory.text(context, initial(name), 22, R.color.text_primary);
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(null, android.graphics.Typeface.BOLD);
        icon.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{0xFFB56D59, 0xFF7F3F32});
        background.setShape(GradientDrawable.OVAL);
        icon.setBackground(background);
        return icon;
    }

    private static String initial(String name) {
        String value = name == null ? "?" : name.trim();
        return value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase();
    }

    private static GradientDrawable background(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(context.getColor(R.color.surface));
        background.setCornerRadius(UiFactory.dp(context, 18));
        background.setStroke(UiFactory.dp(context, 1), 0x142B211D);
        return background;
    }
}
