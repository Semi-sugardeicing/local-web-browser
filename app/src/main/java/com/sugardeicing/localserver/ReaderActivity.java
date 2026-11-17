package com.sugardeicing.localserver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 看网页的那一层：把导入进来的网页放进 WebView 里渲染。
 * 用的是系统自带的 WebView，不存在自己伪造域名再拦截的那套东西。
 */
public class ReaderActivity extends android.app.Activity {

    public static final String EXTRA_URL = "url";     // fs/<站点>/<相对路径> 或 imported/<文件名>
    public static final String EXTRA_TITLE = "title";

    private static final String HOST = "https://localhttp.invalid/site/";

    private WebView web;
    private String url = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);
        Ui.statusBar(this);
        Ui.fit(this, findViewById(R.id.root));

        url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null) url = "";
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) title = "网页";
        ((android.widget.TextView) findViewById(R.id.title)).setText(title);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReload).setOnClickListener(v -> {
            if (web != null) web.reload();
        });

        web = findViewById(R.id.web);
        if (web == null) return;
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        // 默认锁死缩放（跟设计稿一致），想放大在设置里开——那一版还没做
        boolean zoom = Prefs.zoom(this);
        web.getSettings().setSupportZoom(zoom);
        web.getSettings().setBuiltInZoomControls(zoom);
        web.getSettings().setDisplayZoomControls(false);
        web.setBackgroundColor(0xFF0F1013);

        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                if (m != null) {
                    Logger.log("[网页] " + m.messageLevel() + " " + m.sourceId() + ":" + m.lineNumber()
                            + " " + m.message());
                }
                return false;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u == null) return null;
                String p = u.getPath();
                if (p == null || !p.startsWith("/site/")) return null;
                String rel = p.substring("/site/".length());
                Logger.log("REQ " + rel);
                return serve(rel);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    int code = error == null ? -1 : error.getErrorCode();
                    String desc = error == null ? "" : String.valueOf(error.getDescription());
                    Logger.log("ERR 页面没打开 " + code + " · " + desc + " · " + url);
                    showError(code, desc);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                boolean crashed = detail != null && detail.didCrash();
                Logger.log("渲染进程没了 · " + (crashed ? "崩了" : "被系统回收"));
                showError(crashed ? -100 : -101, crashed ? "内核进程崩溃" : "内核进程被系统回收");
                return true;
            }
        });

        web.loadUrl(HOST + url);
    }

    /** 从 App 自己的目录里把文件读出来交给 WebView */
    private WebResourceResponse serve(String rel) {
        try {
            if (rel.startsWith("asset/")) {
                String ap = rel.substring("asset/".length());
                Map<String, String> h = new HashMap<>();
                h.put("Cache-Control", "no-store");
                InputStream ain = getAssets().open(ap);
                return new WebResourceResponse(mime(ap), "UTF-8", 200, "OK", h, ain);
            }
            File root;
            String path = rel;
            if (rel.startsWith("fs/")) {
                String rest = rel.substring(3);
                int slash = rest.indexOf('/');
                if (slash < 0) return null;
                String site = rest.substring(0, slash);
                path = rest.substring(slash + 1);
                root = new File(FileStore.siteDir(this), site);
            } else if (rel.startsWith("imported/")) {
                root = new File(FileStore.importedDir(this), "");
                path = rel.substring("imported/".length());
            } else {
                return null;
            }
            path = Uri.decode(path);
            File target = new File(root, path);
            // 不许跳出这个站点的目录
            if (!target.getCanonicalPath().startsWith(root.getCanonicalPath())) return null;
            if (!target.isFile()) {
                Logger.log("MISS " + rel);
                return notFound();
            }
            InputStream in = new FileInputStream(target);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse(mime(target.getName()), "UTF-8", 200, "OK", headers, in);
        } catch (Exception e) {
            Logger.log("读取失败 " + rel + " · " + e);
            return null;
        }
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                new HashMap<>(), new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js") || n.endsWith(".mjs")) return "application/javascript";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".otf")) return "font/otf";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".txt") || n.endsWith(".md")) return "text/plain";
        return "application/octet-stream";
    }

    /** 打不开时给一句人话，而不是系统那张白页 */
    private void showError(int code, String desc) {
        if (web == null) return;
        String why;
        if (code == -6) why = "文件没找到（目录可能被删了，或者链接指错了）";
        else if (code == -2 || code == -13) why = "连不上本机文件（重开一次这个页面试试）";
        else if (code == -100) why = "内核进程崩溃了（重新打开 App 就行）";
        else if (code == -101) why = "内核进程被系统回收了（重新打开 App 就行）";
        else why = "没能打开这一页";
        String page = "<html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#0F1013;color:#F2F3F5;font-family:sans-serif;margin:0;"
                + "padding:28px 22px;line-height:1.75}h1{font-size:20px;margin:0 0 14px}"
                + "p{color:#9AA1AC;font-size:14px}.box{background:#191B1F;border-radius:14px;"
                + "padding:16px;margin-top:18px;font-size:13px;color:#9AA1AC;word-break:break-all}</style></head>"
                + "<body><h1>" + why + "</h1>"
                + "<p>左上角返回上一页，或者点左上角箭头退出重来。</p>"
                + "<div class='box'>页面：" + esc(url) + "<br>错误码：" + code + " · " + esc(desc) + "</div>"
                + "</body></html>";
        web.loadDataWithBaseURL("about:blank", page, "text/html", "UTF-8", null);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.setWebChromeClient(null);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    static Intent intent(android.content.Context ctx, String url, String title) {
        Intent i = new Intent(ctx, ReaderActivity.class);
        i.putExtra(EXTRA_URL, url);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }
}
