package com.sugardeicing.localserver;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日志（0.1.5）
 *
 * 三条硬规矩：
 *  1. 一写就落盘：每行写完立刻 flush + 关流，崩溃时最后一行一定在文件里；
 *  2. 大小封顶：会自然滚动，超过 512 KB 只留下最新的那一半（先切尾再写）；
 *  3. 上限里含崩溃段：崩溃栈是最要紧的，不能被日常日志挤掉。
 *
 */
public final class Logger {

    public static final String TAG = "LocalHttp";
    private static final long MAX_BYTES = 512L * 1024L;
    private static final long KEEP_BYTES = 256L * 1024L;
    private static final String FILE_NAME = "app.log";

    private static final SimpleDateFormat TS = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA);

    private static File file;
    private static boolean enabled = true;
    private static long written = 0;        // 这一进程已经写进去的字节，免得每次都 stat 文件

    private Logger() {}

    public static void init(Context ctx) {
        file = new File(ctx.getFilesDir(), FILE_NAME);
        enabled = true;
        if (file.exists()) written = file.length();
    }

    /** 普通日志：写一行，立刻落盘 */
    public static synchronized void log(String line) {
        if (file == null) return;
        Log.d(TAG, line);
        if (!enabled) return;
        write(TS.format(new Date()) + "  " + line + "\n");
    }

    /** 崩溃栈：不管日志开关，永远记，写完立刻落盘 */
    public static synchronized void crash(String line) {
        if (file == null) return;
        Log.e(TAG, line);
        write(TS.format(new Date()) + "  CRASH " + line + "\n");
    }

    /** 写之前先看大小：要超了就先把旧的一半切掉，保证最新这行一定进得去 */
    private static void write(String row) {
        try {
            byte[] bytes = row.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (written + bytes.length > MAX_BYTES) {
                shrinkToKEEP();
            }
            FileOutputStream fos = new FileOutputStream(file, true);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            try {
                bos.write(bytes);
                bos.flush();
                fos.getFD().sync();     // 真正落到磁盘，进程被杀也不丢
            } finally {
                bos.close();
            }
            written += bytes.length;
        } catch (Throwable t) {
            Log.w(TAG, "日志写入失败", t);
        }
    }

    private static void shrinkToKEEP() {
        try {
            if (file == null || !file.exists()) { written = 0; return; }
            byte[] all = java.nio.file.Files.readAllBytes(file.toPath());
            if (all.length <= KEEP_BYTES) { written = all.length; return; }
            int from = (int) (all.length - KEEP_BYTES);
            // 从这条中间往后挪到下一个换行，别把半行切出来
            while (from < all.length && all[from] != '\n') from++;
            if (from >= all.length) from = (int) (all.length - KEEP_BYTES);
            byte[] keep = java.util.Arrays.copyOfRange(all, from, all.length);
            File tmp = new File(file.getParentFile(), FILE_NAME + ".tmp");
            FileOutputStream fos = new FileOutputStream(tmp, false);
            try {
                fos.write(keep);
                fos.getFD().sync();
            } finally {
                fos.close();
            }
            if (!tmp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
            written = keep.length;
        } catch (Throwable t) {
            Log.w(TAG, "日志滚动失败", t);
        }
    }

    /** 读出来给开发者页面看 */
    public static synchronized String read() {
        if (file == null || !file.exists()) return "";
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "读取日志失败：" + e.getMessage();
        }
    }

    /** 给开发者页面用的条数提示 */
    public static synchronized int lines() {
        String all = read();
        if (all.isEmpty()) return 0;
        int n = 1;
        for (int i = 0; i < all.length(); i++) if (all.charAt(i) == '\n') n++;
        return n;
    }

    public static synchronized void clear() {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        written = 0;
    }

    public static synchronized void setEnabled(boolean on) {
        enabled = on;
    }

    /** 导出日志：写到 公共下载目录/LocalHttp/。安卓 14 上走 MediaStore 不需要权限 */
    public static String exportToPublic(Context ctx) {
        String content = read();
        String name = "LocalHttp-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(new Date()) + ".txt";
        try {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/LocalHttp");
            android.net.Uri uri = ctx.getContentResolver().insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return "保存失败";
            try (java.io.OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) return "保存失败";
                os.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            log("导出日志 → 下载/LocalHttp/" + name);
            return "已存到 手机存储/Download/LocalHttp/" + name;
        } catch (Exception e) {
            log("导出日志失败：" + e.getMessage());
            return "保存失败：" + e.getMessage();
        }
    }

    /** 当前系统是不是深色 */
    public static boolean systemDark(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
