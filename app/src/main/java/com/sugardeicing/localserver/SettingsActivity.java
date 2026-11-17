package com.sugardeicing.localserver;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 设置：外观、网页、关于。开发者选项要连点版本号 7 次才出现。 */
public class SettingsActivity extends Activity {

    private LinearLayout content;
    private boolean[] swHolder;
    private int taps = 0;
    private long lastTap = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Logger.init(this);
        Ui.statusBar(this);
        content = Ui.scaffold(this, "设置", this::finish);
        build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (swHolder != null) Prefs.setSwipe(this, swHolder[0]);
    }

    private void build() {
        content.removeAllViews();
        final Activity a = this;

        Ui.head(a, content, "外观");
        LinearLayout c1 = Ui.card(a, content);
        Ui.row(a, c1, "主题", "默认暗色。选「自动」跟随系统", null, null);
        c1.addView(seg(new String[]{"自动", "亮色", "暗色"},
                new String[]{"auto", "light", "dark"}, Prefs.theme(a), true,
                val -> {
                    Prefs.setTheme(a, val);
                    a.recreate();
                }));

        if (Prefs.dev(a)) {
            Ui.row(a, c1, "字号", "试验中。只影响 App 自己的界面，不改网页字号", null, null);
            c1.addView(seg(new String[]{"标准", "大", "更大"},
                    new String[]{"md", "lg", "xl"}, Prefs.fs(a), false,
                    val -> {
                        Prefs.setFs(a, val);
                        a.recreate();
                    }));
        }

        Ui.head(a, content, "网页");
        LinearLayout c2 = Ui.card(a, content);
        final boolean[] zoom = {Prefs.zoom(a)};
        final LinearLayout zoomRow = Ui.row(a, c2, "允许双指缩放网页",
                "默认锁定。开启后网页可用手势缩放，界面不受影响", zoom[0] ? "开" : "关", null);
        zoomRow.setOnClickListener(v -> {
            zoom[0] = !zoom[0];
            Prefs.setZoom(a, zoom[0]);
            ((TextView) zoomRow.getChildAt(1)).setText(zoom[0] ? "开" : "关");
            Logger.log("双指缩放 " + (zoom[0] ? "开" : "关"));
        });

        if (Prefs.dev(a)) {
            Ui.head(a, content, "开发者");
            LinearLayout c3 = Ui.card(a, content);
            swHolder = new boolean[]{Prefs.swipe(a)};
            Ui.toggle(a, c3, "允许左滑删除", "默认关。开了以后首页每行左滑可以删掉", swHolder[0], swHolder);
            Ui.row(a, c3, "开发者选项", "试验功能、日志、环境自检", "›",
                    v -> startActivity(new Intent(a, DevActivity.class)));
        }

        Ui.head(a, content, "关于");
        LinearLayout c4 = Ui.card(a, content);
        Ui.row(a, c4, getString(R.string.app_name), "本地网页浏览器，不联网也能翻", null, null);

        final LinearLayout verRow = Ui.row(a, c4, "版本",
                getString(R.string.version_label), null, null);
        verRow.setOnClickListener(v -> onVersionTap());

        Ui.row(a, c4, "当前系统",
                android.os.Build.VERSION.RELEASE + "（API " + android.os.Build.VERSION.SDK_INT + "）",
                null, null);

        if (!Prefs.dev(a)) {
            Ui.hint(a, content, "本版本以体验版形式提供，功能与接口仍可能调整。");
        }
    }

    /** 三段选择器 */
    private LinearLayout seg(String[] names, String[] vals, String current, boolean topGap,
                             java.util.function.Consumer<String> onPick) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(Ui.dp(this, 16), topGap ? 0 : Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 16));
        for (int i = 0; i < names.length; i++) {
            final String val = vals[i];
            boolean on = val.equals(current);
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            t.setTextColor(on ? Prefs.fg(this) : Prefs.muted(this));
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setColor(on ? (Prefs.isDark(this) ? 0xFF0F1013 : 0xFFE8E9EC) : 0x00000000);
            d.setCornerRadius(Ui.dp(this, 9));
            t.setBackground(d);
            t.setClickable(true);
            t.setOnClickListener(v -> onPick.accept(val));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = Ui.dp(this, 8);
            wrap.addView(t, lp);
        }
        return wrap;
    }

    private void onVersionTap() {
        long now = System.currentTimeMillis();
        if (now - lastTap > 800) taps = 0;
        lastTap = now;
        taps++;
        if (Prefs.dev(this)) {
            toast("开发者选项已开启");
            return;
        }
        if (taps >= 7) {
            Prefs.setDev(this, true);
            Logger.log("开发者选项已开启");
            toast("已进入开发者模式");
            recreate();
        } else if (taps >= 4) {
            toast("再点 " + (7 - taps) + " 次即可进入开发者模式");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
