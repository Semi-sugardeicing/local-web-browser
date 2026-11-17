package com.sugardeicing.localserver;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 文件大小、时间的显示（跟设计稿一致的写法） */
public final class Format {

    private static final SimpleDateFormat DAY = new SimpleDateFormat("M 月 d 日", Locale.CHINA);
    private static final SimpleDateFormat HOUR = new SimpleDateFormat("HH:mm", Locale.CHINA);

    private Format() {}

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return Math.max(1, bytes / 1024) + " KB";
        double mb = bytes / (1024.0 * 1024.0);
        return (mb >= 10 ? String.valueOf(Math.round(mb)) : String.format(Locale.CHINA, "%.1f", mb)) + " MB";
    }

    public static String when(long time) {
        if (time <= 0) return "";
        long now = System.currentTimeMillis();
        long dayStart = now - (now % (24L * 3600L * 1000L));
        if (time >= dayStart) return "今天 " + HOUR.format(new Date(time));
        if (time >= dayStart - 24L * 3600L * 1000L) return "昨天 " + HOUR.format(new Date(time));
        return DAY.format(new Date(time));
    }
}
