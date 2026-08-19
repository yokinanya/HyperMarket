package com.hyper.market.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import com.hyper.market.R;

public final class UiFactory {
    private static final int PADDING = 20;
    private static final int CARD_RADIUS = 24;
    private static final int TEXT_PRIMARY = R.color.text_primary;
    private static final int TEXT_SECONDARY = R.color.text_secondary;

    private UiFactory() { }

    public static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static ScrollView scroll(Context context, View content) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content);
        return scroll;
    }

    public static TextView text(Context context, String value, float size, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(context.getColor(color));
        text.setFontFeatureSettings("kern");
        return text;
    }

    public static TextView title(Context context, String value) {
        TextView title = text(context, value, 28, TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(context, 4), 0, dp(context, 8));
        return title;
    }

    public static TextView section(Context context, String value) {
        TextView section = text(context, value, 17, TEXT_PRIMARY);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setPadding(0, dp(context, 20), 0, dp(context, 10));
        return section;
    }

    public static EditText input(Context context, String hint) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setTextSize(16);
        input.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 4));
        input.setBackground(cardBackground(context, R.color.surface));
        return input;
    }

    public static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(context.getColor(TEXT_PRIMARY));
        button.setMinHeight(dp(context, 44));
        return button;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = column(context);
        card.setPadding(dp(context, PADDING), dp(context, PADDING), dp(context, PADDING), dp(context, PADDING));
        card.setBackground(cardBackground(context, R.color.surface));
        return card;
    }

    public static TextView secondary(Context context, String value) {
        TextView text = text(context, value, 14, TEXT_SECONDARY);
        text.setLineSpacing(0, 1.15f);
        return text;
    }

    public static View divider(Context context) {
        Space divider = new Space(context);
        divider.setBackgroundColor(Color.argb(30, 40, 30, 25));
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return divider;
    }

    public static void margin(View view, Context context, int top, int bottom) {
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(-1, -2);
        params.topMargin = dp(context, top);
        params.bottomMargin = dp(context, bottom);
        view.setLayoutParams(params);
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable cardBackground(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(context.getColor(color));
        drawable.setCornerRadius(dp(context, CARD_RADIUS));
        drawable.setStroke(dp(context, 1), Color.argb(18, 50, 35, 25));
        return drawable;
    }
}
