package com.sugardeicing.localserver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页：全部用系统自带控件画，不经过浏览器内核。
 * 只有点进某个网页时，才交给 ReaderActivity 的 WebView 去渲染。
 */
public class MainActivity extends Activity implements RowAdapter.OnClick {

    private static final int REQ_FILE = 1001;
    private static final int REQ_TREE = 1002;

    private final List<Item> items = new ArrayList<>();
    private RowAdapter adapter;
    private RecyclerView listView;
    private TextView sub;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 先装崩溃钩子，再干别的：万一哪里出错，日志里至少有一条
        installCrashHook();
        Logger.init(this);
        try {
            FileStore.ensureDirs(this);
        } catch (Throwable t) {
            Logger.log("建目录失败：" + t);
        }
        setContentView(R.layout.activity_main);
        Ui.statusBar(this);
        Ui.fit(this, findViewById(R.id.root));
        applyTheme();

        listView = findViewById(R.id.list);
        sub = findViewById(R.id.sub);
        if (listView != null) {
            listView.setLayoutManager(new LinearLayoutManager(this));
            adapter = new RowAdapter(items, this);
            listView.setAdapter(adapter);
            if (Prefs.swipe(this)) attachSwipe();
        }

        View btnAdd = findViewById(R.id.btnAdd);
        if (btnAdd != null) btnAdd.setOnClickListener(v -> showAddDialog());
        View gear = findViewById(R.id.btnSettings);
        if (gear != null) {
            gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }

        Logger.log("启动 · " + getString(R.string.app_name));
        refresh();
        handleExternalIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTheme();
        if (listView != null && Prefs.swipe(this)) {
            attachSwipe();
        }
        refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    /** 别的 App 里点「用本地 HTTP 服务打开」时把文件收进来 */
    private void handleExternalIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (!"content".equals(uri.getScheme())) return;
        importOne(uri);
    }

    /** 左滑一行：从列表里移除（真删）。内置的两页不给删。 */
    private void attachSwipe() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a,
                                  @NonNull RecyclerView.ViewHolder b) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos < 0 || pos >= items.size()) {
                    adapter.notifyDataSetChanged();
                    return;
                }
                Item it = items.get(pos);
                if (it.type == Item.TYPE_HEADER) {
                    adapter.notifyItemChanged(pos);
                    return;
                }
                if (it.siteId != null && it.siteId.startsWith("asset:")) {
                    adapter.notifyItemChanged(pos);
                    toast("内置页面不能删");
                    return;
                }
                String what = it.name;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("从列表里移除？")
                        .setMessage(what + "\n\n文件会一起删掉，删了要找回来得重新导入。")
                        .setNegativeButton("取消", (d, w) -> adapter.notifyItemChanged(pos))
                        .setPositiveButton("移除", (d, w) -> {
                            boolean ok;
                            if (it.type == Item.TYPE_SITE) {
                                ok = FileStore.deleteSite(MainActivity.this, it.siteId);
                            } else {
                                ok = FileStore.deleteImported(MainActivity.this, it.url);
                            }
                            if (!ok) toast("没删掉，去开发者 → 日志里看看");
                            refresh();
                        })
                        .setOnCancelListener(d -> adapter.notifyItemChanged(pos))
                        .show();
            }
        });
        helper.attachToRecyclerView(listView);
    }

    /* ---------------- 列表 ---------------- */

    private void applyTheme() {
        int fg = Prefs.fg(this);
        int muted = Prefs.muted(this);
        android.view.View root = findViewById(R.id.root);
        if (root != null) root.setBackgroundColor(Prefs.bg(this));
        android.widget.TextView title = findViewById(R.id.title);
        if (title != null) title.setTextColor(fg);
        android.widget.TextView gear = findViewById(R.id.btnSettings);
        if (gear != null) gear.setTextColor(fg);
        android.widget.TextView sub = findViewById(R.id.sub);
        if (sub != null) sub.setTextColor(muted);
        android.widget.TextView add = findViewById(R.id.btnAdd);
        if (add != null) {
            int fill = Prefs.isDark(this) ? 0xFFEDEEF0 : 0xFF17181C;
            int text = Prefs.isDark(this) ? 0xFF141519 : 0xFFF1F2F4;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(fill);
            bg.setCornerRadius(Ui.dp(this, 14));
            add.setBackground(bg);
            add.setTextColor(text);
        }
    }

    private void refresh() {
        new Thread(() -> {
            List<Item> list = new ArrayList<>();
            // 内置：使用说明 + 示例站点，装在包里，删不掉
            list.add(Item.site("asset:guide", "使用说明", "index.html", 1, 0));
            list.add(Item.site("asset:demo", "示例站点", "index.html", 3, 0));
            try {
                JSONArray sites = new JSONArray(FileStore.listSites(this));
                for (int i = 0; i < sites.length(); i++) {
                    JSONObject o = sites.getJSONObject(i);
                    list.add(Item.site(o.optString("id"), o.optString("name"), o.optString("index"),
                            o.optInt("pages"), o.optLong("size")));
                }
            } catch (Exception e) {
                Logger.log("读站点失败：" + e);
            }
            List<Item> files = new ArrayList<>();
            try {
                JSONArray arr = new JSONArray(FileStore.listImported(this));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    files.add(Item.file(o.optString("name"), o.optString("url"),
                            o.optLong("size"), o.optLong("time")));
                }
            } catch (Exception e) {
                Logger.log("读文件失败：" + e);
            }
            final List<Item> all = Item.build(list, files);
            runOnUiThread(() -> {
                items.clear();
                items.addAll(all);
                if (adapter != null) adapter.notifyDataSetChanged();
                if (sub != null) {
                    sub.setText(items.isEmpty()
                            ? "还没有导入网页，点下面的按钮加一个。"
                            : list.size() + " 个站点、" + files.size() + " 个文件，全在本机。");
                }
            });
        }).start();
    }

    @Override
    public void onSite(Item item) {
        if (item.siteId != null && item.siteId.startsWith("asset:")) {
            String dir = item.siteId.substring("asset:".length());
            startActivity(ReaderActivity.intent(this, "asset/builtin/" + dir + "/index.html", item.name));
            return;
        }
        String index = item.index == null ? "" : item.index;
        if (index.isEmpty()) {
            toast("这个站点里没有页面文件");
            return;
        }
        startActivity(ReaderActivity.intent(this, "fs/" + item.siteId + "/" + index, item.name));
    }

    @Override
    public void onFile(Item item) {
        startActivity(ReaderActivity.intent(this, item.url, item.name));
    }

    /* ---------------- 添加文件 ---------------- */

    private void showAddDialog() {
        if (busy) {
            toast("正在导入，等这一次弄完");
            return;
        }
        final String[] options = {"选择文件夹（连子文件夹一起导进来，最稳）", "选择一个文件", "粘贴一个地址"};
        new AlertDialog.Builder(this)
                .setTitle("添加文件")
                .setItems(options, (d, which) -> {
                    if (which == 0) pickFolder();
                    else if (which == 1) pickFile();
                    else askPaste();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TREE);
        } catch (ActivityNotFoundException e) {
            toast("这台手机没有文件选择器");
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (ActivityNotFoundException e) {
            toast("这台手机没有文件选择器");
        }
    }

    private void askPaste() {
        final EditText input = new EditText(this);
        input.setHint("content:// 或 file:///storage/…");
        input.setTextSize(14);
        new AlertDialog.Builder(this)
                .setTitle("粘贴地址")
                .setView(input)
                .setPositiveButton("导入", (d, w) -> {
                    String s = input.getText() == null ? "" : input.getText().toString().trim();
                    if (s.isEmpty()) {
                        toast("没填地址");
                        return;
                    }
                    if (s.startsWith("content://")) {
                        importOne(Uri.parse(s));
                    } else if (s.startsWith("file://")) {
                        importPath(Uri.parse(s).getPath());
                    } else {
                        toast("只认 content:// 或 file:// 开头的地址");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        if (requestCode == REQ_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                Logger.log("记授权失败（不影响这次导入）· " + e.getMessage());
            }
            importTree(uri);
        } else if (requestCode == REQ_FILE) {
            importOne(uri);
        }
    }

    private void importTree(final Uri treeUri) {
        busy = true;
        toast("正在复制这个文件夹…");
        new Thread(() -> {
            String msg;
            try {
                String site = FileStore.copyTree(this, treeUri);
                Logger.log("导入完成 " + site);
                msg = "导入好了";
            } catch (Exception e) {
                Logger.log("导入文件夹失败：" + e);
                msg = "没有导入成功：" + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> {
                busy = false;
                toast(m);
                refresh();
            });
        }).start();
    }

    private void importOne(final Uri uri) {
        busy = true;
        new Thread(() -> {
            String msg;
            try {
                String name = FileStore.copySingle(this, uri);
                msg = "已导入 " + name;
            } catch (Exception e) {
                Logger.log("导入文件失败：" + e);
                msg = "没有导入成功：" + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> {
                busy = false;
                toast(m);
                refresh();
            });
        }).start();
    }

    /** file:// 地址没有 content 授权，只能按路径直读（有权限才行） */
    private void importPath(final String path) {
        if (path == null || path.isEmpty()) {
            toast("地址里没有路径");
            return;
        }
        busy = true;
        new Thread(() -> {
            String msg;
            File src = new File(path);
            try {
                File dir = FileStore.importedDir(this);
                FileStore.ensureDirs(this);
                File dst = FileStore.uniqueFile(dir, src.getName());
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                Logger.log("按路径导入 " + dst.getName());
                msg = "已导入 " + dst.getName();
            } catch (Exception e) {
                Logger.log("按路径导入失败：" + e);
                msg = "没读到这个文件（安卓 11 以上不能直接读路径，请改用「选择一个文件」）";
            }
            final String m = msg;
            runOnUiThread(() -> {
                busy = false;
                toast(m);
                refresh();
            });
        }).start();
    }

    private void toast(String msg) {
        try {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /* ---------------- 崩溃钩子 ---------------- */

    /** 崩了先把栈同步落盘，再交给系统默认处理器（该弹的崩溃框照常弹） */
    private void installCrashHook() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((th, ex) -> {
            try {
                Logger.log("崩溃 · 线程 " + (th == null ? "?" : th.getName())
                        + " · " + ex.getClass().getName() + " · " + ex.getMessage());
                StackTraceElement[] st = ex.getStackTrace();
                for (int i = 0; i < Math.min(14, st.length); i++) Logger.crash(st[i].toString());
                Throwable c = ex.getCause();
                if (c != null) {
                    Logger.crash("Caused by: " + c.getClass().getName() + " · " + c.getMessage());
                    StackTraceElement[] cs = c.getStackTrace();
                    for (int i = 0; i < Math.min(6, cs.length); i++) Logger.crash("  " + cs[i].toString());
                }
            } catch (Throwable ignored) {
            }
            if (prev != null) prev.uncaughtException(th, ex);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}
