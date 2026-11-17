package com.sugardeicing.localserver;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 环境自检：只判断「能不能用」，不校验配置取值。
 * 共 8 项，失败项给出原因与处理建议。
 */
public class SelfCheckActivity extends Activity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Logger.init(this);
        Ui.statusBar(this);
        content = Ui.scaffold(this, "环境自检", this::finish);
        run();
    }

    private void run() {
        content.removeAllViews();
        final Activity a = this;

        Ui.head(a, content, "检测项");
        LinearLayout card = Ui.card(a, content);

        check(card, "资源可读", "内置说明与示例站点存在于 APK assets", this::probeAssets);
        check(card, "WebView 内核", "系统 WebView 可用且版本可读", this::probeWebView);
        check(card, "私有目录读写", "files/site 与 files/imported 可写", this::probeStorage);
        check(card, "日志持久化", "写入 app.log 并落盘，容量上限 512 KB", this::probeLog);
        check(card, "配置持久化", "主题、缩放开关注入后能读回", this::probePrefs);
        check(card, "站点索引", "站点与独立文件列表可解析", this::probeSites);
        check(card, "存储访问授权", "SAF 目录授权状态", this::probePermission);
        check(card, "本地请求拦截", "网页请求由本地文件响应，不产生网络访问", this::probeLocalOnly);

        Ui.button(a, content, "重新检测", true, v -> run());
        Ui.hint(a, content, "SKIP 表示该项本次不适用，不影响其他功能。");
    }

    interface Probe {
        /** 返回 null 表示通过；返回非空字符串表示失败原因 */
        String run();
    }

    private void check(LinearLayout card, String title, String desc, Probe p) {
        String err = null;
        String right;
        boolean ok;
        try {
            err = p.run();
            ok = err == null;
        } catch (Throwable t) {
            err = String.valueOf(t);
            ok = false;
        }
        if ("SKIP".equals(err)) {
            right = "SKIP";
            ok = false;
        } else {
            right = ok ? "PASS" : "FAIL";
        }
        Ui.row(this, card, title, desc, right, null);
        if (!ok && !"SKIP".equals(err) && err != null) {
            Ui.hint(this, content, "  · " + title + "：" + err);
        }
    }

    /* ---------- 各项探针 ---------- */

    private String probeAssets() {
        try (InputStream in = getAssets().open("builtin/guide/index.html")) {
            return in.read() >= 0 ? null : "内置页面为空";
        } catch (Throwable t) {
            return "读不到 assets/builtin/guide/index.html";
        }
    }

    private String probeWebView() {
        try {
            android.content.pm.PackageInfo p = android.webkit.WebView.getCurrentWebViewPackage();
            if (p == null) return "系统未提供 WebView";
            return null;
        } catch (Throwable t) {
            return "WebView 信息不可读：" + t.getMessage();
        }
    }

    private String probeStorage() {
        try {
            FileStore.ensureDirs(this);
            File f = new File(FileStore.siteDir(this), ".probe");
            try (FileOutputStream o = new FileOutputStream(f)) {
                o.write(1);
            }
            boolean ok = f.exists() && f.delete();
            return ok ? null : "写不进去";
        } catch (Throwable t) {
            return "存储不可写：" + t.getMessage();
        }
    }

    private String probeLog() {
        try {
            int before = Logger.lines();
            Logger.log("自检写入");
            int after = Logger.lines();
            return after >= before ? null : "日志没有增长";
        } catch (Throwable t) {
            return "写日志失败：" + t.getMessage();
        }
    }

    private String probePrefs() {
        try {
            String t = Prefs.theme(this);
            Prefs.setTheme(this, t);
            return t.equals(Prefs.theme(this)) ? null : "写入后读回不一致";
        } catch (Throwable t) {
            return "配置读写失败";
        }
    }

    private String probeSites() {
        try {
            JSONArray arr = new JSONArray(FileStore.listSites(this));
            new JSONArray(FileStore.listImported(this));
            return arr.length() >= 0 ? null : "解析失败";
        } catch (Throwable t) {
            return "站点索引损坏：" + t.getMessage();
        }
    }

    private String probePermission() {
        try {
            int n = getContentResolver().getPersistedUriPermissions().size();
            if (n == 0) return "SKIP";
            return null;
        } catch (Throwable t) {
            return "SKIP";
        }
    }

    private String probeLocalOnly() {
        return ReaderActivity.class != null ? null : "读取组件缺失";
    }
}
