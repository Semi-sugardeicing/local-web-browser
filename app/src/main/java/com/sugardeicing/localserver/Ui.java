package com.sugardeicing.localserver;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 原生界面的一点点公共件：顶栏、分组、行、开关。
 * 全部用系统控件画，不走浏览器内核，所以再怎么样都不会白屏。
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Activity a, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                a.getResources().getDisplayMetrics());
    }

    private static GradientDrawable round(Activity a, int fill, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(a, radiusDp));
        return d;
    }

    /** 整页骨架：深色底 + 顶栏 + 可滚动内容。返回内容容器。 */
    public static LinearLayout scaffold(Activity a, String title, Runnable onBack) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Prefs.bg(a));
        a.setContentView(root);
        statusBar(a);
        fit(a, root);

        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 12), dp(a, 14), dp(a, 12), dp(a, 10));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView back = new TextView(a);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(Prefs.fg(a));
        back.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44));
        bar.addView(back, blp);
        if (onBack != null) back.setOnClickListener(v -> onBack.run());

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(Prefs.fg(a));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(a, 44), 1f);
        bar.addView(t, tlp);

        View spacer = new View(a);
        bar.addView(spacer, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));

        ScrollView sc = new ScrollView(a);
        sc.setFillViewport(true);
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 48));
        sc.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return content;
    }

    /** 把系统状态栏 / 导航栏的高度让出来，按钮就不会跟系统栏抢位置。 */
    public static void fit(final Activity a, final View root) {
        final int padLeft = root.getPaddingLeft();
        final int padTop = root.getPaddingTop();
        final int padRight = root.getPaddingRight();
        final int padBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sb = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(padLeft, padTop + sb.top, padRight, padBottom + sb.bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    public static void head(Activity a, LinearLayout parent, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextSize(12);
        v.setTextColor(Prefs.muted(a));
        v.setPadding(dp(a, 4), dp(a, 18), dp(a, 4), dp(a, 8));
        parent.addView(v);
    }

    public static LinearLayout card(Activity a, LinearLayout parent) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(a, Prefs.card(a), 14));
        parent.addView(c, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    /** 一行：左边标题+说明，右边可选文字或箭头，可点 */
    public static LinearLayout row(Activity a, LinearLayout parent, String title, String desc,
                                   String right, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14));

        LinearLayout text = new LinearLayout(a);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(a);
        t1.setText(title);
        t1.setTextSize(15 * Prefs.fsScale(a));
        t1.setTextColor(Prefs.fg(a));
        text.addView(t1);
        if (desc != null && !desc.isEmpty()) {
            TextView t2 = new TextView(a);
            t2.setText(desc);
            t2.setTextSize(12.5f * Prefs.fsScale(a));
            t2.setTextColor(Prefs.muted(a));
            t2.setLineSpacing(dp(a, 3), 1f);
            t2.setPadding(0, dp(a, 3), 0, 0);
            text.addView(t2);
        }
        r.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (right != null && !right.isEmpty()) {
            TextView rv = new TextView(a);
            rv.setText(right);
            rv.setTextSize(13 * Prefs.fsScale(a));
            rv.setTextColor(Prefs.muted(a));
            rv.setPadding(dp(a, 10), 0, 0, 0);
            r.addView(rv);
        }
        if (click != null) {
            r.setClickable(true);
            r.setFocusable(true);
            r.setOnClickListener(click);
        }
        parent.addView(r, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        divider(a, parent);
        return r;
    }

    public static void divider(Activity a, LinearLayout parent) {
        View v = new View(a);
        v.setBackgroundColor(Prefs.line(a));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(a, 0.6f)));
        lp.leftMargin = dp(a, 16);
        lp.rightMargin = dp(a, 16);
        parent.addView(v, lp);
    }

    /** 开关行 */
    public static void toggle(final Activity a, LinearLayout parent, String title, String desc,
                              boolean checked, final boolean[] holder) {
        final String[] label = {checked ? "开" : "关"};
        final LinearLayout r = row(a, parent, title, desc, label[0], null);
        final TextView rv = (TextView) r.getChildAt(1);
        r.setOnClickListener(v -> {
            boolean now = "开".equals(rv.getText().toString());
            boolean next = !now;
            label[0] = next ? "开" : "关";
            rv.setText(label[0]);
            holder[0] = next;
        });
    }

    public static void gap(Activity a, LinearLayout parent, int dp) {
        View v = new View(a);
        parent.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, dp)));
    }

    public static TextView hint(Activity a, LinearLayout parent, String text) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextSize(12 * Prefs.fsScale(a));
        v.setTextColor(Prefs.muted(a));
        v.setLineSpacing(dp(a, 3), 1f);
        v.setPadding(dp(a, 4), dp(a, 10), dp(a, 4), 0);
        parent.addView(v);
        return v;
    }

    public static LinearLayout button(Activity a, LinearLayout parent, String text,
                                      boolean primary, View.OnClickListener click) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, dp(a, 22), 0, 0);
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        int pBg = primary ? (Prefs.isDark(a) ? 0xFFEDEEF0 : 0xFF17181C) : Prefs.card(a);
        int pFg = primary ? (Prefs.isDark(a) ? 0xFF141519 : 0xFFF1F2F4) : Prefs.fg(a);
        b.setTextColor(pFg);
        b.setBackground(round(a, pBg, 14));
        b.setClickable(true);
        b.setOnClickListener(click);
        bar.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 52)));
        parent.addView(bar);
        return bar;
    }

    public static void statusBar(Activity a) {
        a.getWindow().setStatusBarColor(Prefs.bg(a));
        a.getWindow().setNavigationBarColor(Prefs.bg(a));
        View decor = a.getWindow().getDecorView();
        int flags = 0;
        if (!Prefs.isDark(a)) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }
}
