package com.sugardeicing.localserver;

import android.content.Context;
import android.content.SharedPreferences;

/** 一点点设置，全部存在一张 SharedPreferences 里，重开还在。 */
public final class Prefs {

    private static final String NAME = "localhttp";
    private static final String K_THEME = "theme";     // auto / light / dark
    private static final String K_ZOOM = "zoom";       // 网页允许双指缩放
    private static final String K_DEV = "dev";         // 开发者选项解锁
    private static final String K_FS = "fs";           // md / lg / xl
    private static final String K_SWIPE = "swipe";     // 左滑删除（开发者里开）

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String theme(Context c) {
        return sp(c).getString(K_THEME, "dark");
    }

    public static void setTheme(Context c, String v) {
        sp(c).edit().putString(K_THEME, v).apply();
    }

    public static boolean isDark(Context c) {
        String t = theme(c);
        if ("light".equals(t)) return false;
        if ("dark".equals(t)) return true;
        try {
            int mode = c.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable e) {
            return true;
        }
    }

    public static int bg(Context c) {
        return isDark(c) ? 0xFF0F1013 : 0xFFF1F2F4;
    }

    public static int fg(Context c) {
        return isDark(c) ? 0xFFEDEEF0 : 0xFF17181C;
    }

    public static int muted(Context c) {
        return isDark(c) ? 0xFF9AA1AC : 0xFF5C6270;
    }

    public static int card(Context c) {
        return isDark(c) ? 0xFF191B1F : 0xFFFFFFFF;
    }

    public static int line(Context c) {
        return isDark(c) ? 0xFF2A2D33 : 0xFFDFDFE2;
    }

    public static boolean zoom(Context c) {
        return sp(c).getBoolean(K_ZOOM, false);
    }

    public static void setZoom(Context c, boolean v) {
        sp(c).edit().putBoolean(K_ZOOM, v).apply();
    }

    public static boolean swipe(Context c) {
        return sp(c).getBoolean(K_SWIPE, false);
    }

    public static void setSwipe(Context c, boolean v) {
        sp(c).edit().putBoolean(K_SWIPE, v).apply();
    }

    public static boolean dev(Context c) {
        return sp(c).getBoolean(K_DEV, false);
    }

    public static void setDev(Context c, boolean v) {
        sp(c).edit().putBoolean(K_DEV, v).apply();
    }

    public static String fs(Context c) {
        return sp(c).getString(K_FS, "md");
    }

    public static void setFs(Context c, String v) {
        sp(c).edit().putString(K_FS, v).apply();
    }

    public static float fsScale(Context c) {
        String v = fs(c);
        if ("lg".equals(v)) return 1.12f;
        if ("xl".equals(v)) return 1.25f;
        return 1f;
    }

    public static void clear(Context c) {
        sp(c).edit().clear().apply();
    }
}
