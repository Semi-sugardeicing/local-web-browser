package com.sugardeicing.localserver;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 把用户选的文件/文件夹复制进 App 私有目录，之后的页面都从这里读 */
public final class FileStore {

    private FileStore() {}

    /** 文件夹导入的站点都放在这里，一个站点一个子目录，结构原样 */
    public static File siteDir(Context ctx) {
        return new File(ctx.getFilesDir(), "site");
    }

    /** 单独导入的文件 */
    public static File importedDir(Context ctx) {
        return new File(ctx.getFilesDir(), "imported");
    }

    public static void ensureDirs(Context ctx) {
        File a = siteDir(ctx);
        File b = importedDir(ctx);
        if (!a.exists() && !a.mkdirs()) Logger.log("建目录失败：" + a.getName());
        if (!b.exists() && !b.mkdirs()) Logger.log("建目录失败：" + b.getName());
    }

    /** 从 content:// 复制一个文件进来，返回落地后的文件名 */
    public static String copySingle(Context ctx, Uri uri) throws IOException {
        ensureDirs(ctx);
        String name = displayName(ctx, uri);
        if (name == null || name.isEmpty()) name = "导入文件-" + System.currentTimeMillis();
        File dest = uniqueFile(importedDir(ctx), safeName(name));
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("打不开这个文件");
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            Logger.log("导入 " + dest.getName() + " · " + (total / 1024) + " KB");
        }
        return dest.getName();
    }

    /**
     * 从文件夹（SAF 树）整个复制进来，保留目录结构，包括所有子文件夹。
     * 返回站点目录名（也就是用户看到的「站点名」）。
     */
    public static String copyTree(Context ctx, Uri treeUri) throws IOException {
        ensureDirs(ctx);
        String rootName = safeName(folderDisplayName(ctx, treeUri));
        if (rootName == null || rootName.isEmpty()) rootName = "站点-" + System.currentTimeMillis();
        String site = "s" + System.currentTimeMillis() + "-" + rootName;
        File root = new File(siteDir(ctx), site);
        if (!root.mkdirs()) {
            Logger.log("建站点目录失败");
            throw new IOException("建目录失败");
        }
        ContentResolver cr = ctx.getContentResolver();
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        int[] count = new int[]{0};
        walk(cr, DocumentsContract.buildDocumentUriUsingTree(treeUri, docId), root, count, 0);
        Logger.log("导入文件夹「" + rootName + "」· " + count[0] + " 个文件");
        if (count[0] == 0) {
            deleteRec(root);
            throw new IOException("这个文件夹里没有可读的文件，换一个试试（部分系统会把下载目录挡住）");
        }
        return site;
    }

    private static void walk(ContentResolver cr, Uri parentDoc, File outDir, int[] count, int depth) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentDoc, DocumentsContract.getDocumentId(parentDoc));
        String[] proj = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor c = cr.query(children, proj, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = safeName(c.getString(1));
                String mime = c.getString(2);
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(parentDoc, id);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    File sub = new File(outDir, name);
                    if (!sub.exists() && !sub.mkdirs()) continue;
                    if (depth < 12) walk(cr, docUri, sub, count, depth + 1);
                } else {
                    File target = uniqueFile(outDir, name);
                    try (InputStream in = cr.openInputStream(docUri);
                         OutputStream out = new FileOutputStream(target)) {
                        if (in == null) continue;
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        count[0]++;
                    } catch (Exception e) {
                        Logger.log("跳过 " + name + " · " + e.getMessage());
                        //noinspection ResultOfMethodCallIgnored
                        target.delete();
                    }
                }
            }
        } catch (Exception e) {
            Logger.log("读取文件夹失败 · " + e.getMessage());
        }
    }

    /** 站点列表：每个文件夹一个站点，带首页文件和页面数 */
    public static String listSites(Context ctx) {
        JSONArray arr = new JSONArray();
        File[] dirs = siteDir(ctx).listFiles();
        if (dirs == null) dirs = new File[0];
        Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File d : dirs) {
            if (!d.isDirectory()) continue;
            try {
                List<File> pages = new ArrayList<>();
                countPages(d, pages, 0);
                String index = findIndex(d, 0);
                JSONObject o = new JSONObject();
                o.put("id", d.getName());
                o.put("name", displaySiteName(d.getName()));
                o.put("index", index == null ? "" : index);
                o.put("pages", pages.size());
                o.put("size", dirSize(d));
                o.put("time", d.lastModified());
                arr.put(o);
            } catch (Exception e) {
                Logger.log("读站点失败 " + d.getName());
            }
        }
        return arr.toString();
    }

    /** 单独导入的文件 */
    /** 删掉一个导入的站点（整文件夹） */
    public static boolean deleteSite(Context ctx, String id) {
        if (id == null || id.isEmpty() || id.contains("/") || id.contains("..")) return false;
        File d = new File(siteDir(ctx), id);
        try {
            if (!d.getCanonicalPath().startsWith(siteDir(ctx).getCanonicalPath())) return false;
            deleteTree(d);
            Logger.log("删除站点 " + id);
            return true;
        } catch (Exception e) {
            Logger.log("删站点失败 " + id + " " + e);
            return false;
        }
    }

    /** 删掉一个单独导入的文件 */
    public static boolean deleteImported(Context ctx, String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("..")) return false;
        File f = new File(importedDir(ctx), name);
        try {
            if (!f.getCanonicalPath().startsWith(importedDir(ctx).getCanonicalPath())) return false;
            boolean ok = f.delete();
            Logger.log("删除文件 " + name + " " + ok);
            return ok;
        } catch (Exception e) {
            Logger.log("删文件失败 " + name + " " + e);
            return false;
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public static String listImported(Context ctx) {
        JSONArray arr = new JSONArray();
        File[] files = importedDir(ctx).listFiles();
        if (files == null) files = new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            if (!f.isFile()) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("name", f.getName());
                o.put("url", "imported/" + Uri.encode(f.getName()));
                o.put("size", f.length());
                o.put("time", f.lastModified());
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    /** 某个站点里一层目录的内容；rel 是相对站点根目录的路径，空表示根 */
    public static String listChildren(Context ctx, String site, String rel) {
        JSONObject out = new JSONObject();
        try {
            File root = new File(siteDir(ctx), safeName(site));
            File dir = rel == null || rel.isEmpty() ? root : new File(root, rel.replace("..", ""));
            if (!dir.exists() || !dir.isDirectory()) dir = root;
            JSONArray folders = new JSONArray();
            JSONArray files = new JSONArray();
            File[] kids = dir.listFiles();
            if (kids != null) {
                List<File> list = new ArrayList<>(Arrays.asList(kids));
                Collections.sort(list, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                String relPath = rel == null ? "" : rel;
                for (File f : list) {
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName());
                    if (f.isDirectory()) {
                        List<File> pages = new ArrayList<>();
                        countPages(f, pages, 0);
                        o.put("pages", pages.size());
                        o.put("path", f.getAbsolutePath().substring(rootOf(root).length()).replace('\\', '/').replaceFirst("^/", ""));
                        folders.put(o);
                    } else {
                        o.put("size", f.length());
                        String p = relPath.isEmpty() ? f.getName() : relPath + "/" + f.getName();
                        o.put("url", "fs/" + site + "/" + p.replace(" ", "%20"));
                        o.put("index", f.getName().toLowerCase().endsWith(".html"));
                        files.put(o);
                    }
                }
            }
            out.put("site", site);
            out.put("path", rel == null ? "" : rel);
            out.put("folders", folders);
            out.put("files", files);
        } catch (Exception e) {
            Logger.log("列目录失败 · " + e.getMessage());
        }
        return out.toString();
    }

    private static String rootOf(File siteRoot) {
        String p = siteRoot.getAbsolutePath().replace('\\', '/');
        return p.endsWith("/") ? p : p + "/";
    }

    private static void countPages(File dir, List<File> out, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null || depth > 12) return;
        for (File f : kids) {
            if (f.isDirectory()) countPages(f, out, depth + 1);
            else if (f.getName().toLowerCase().endsWith(".html") || f.getName().toLowerCase().endsWith(".htm"))
                out.add(f);
        }
    }

    private static String findIndex(File dir, int depth) {
        File idx = new File(dir, "index.html");
        if (idx.isFile()) return "index.html";
        File idx2 = new File(dir, "index.htm");
        if (idx2.isFile()) return "index.htm";
        File[] kids = dir.listFiles();
        if (kids == null || depth > 12) return null;
        Arrays.sort(kids);
        for (File f : kids) if (f.isDirectory()) {
            String r = findIndex(f, depth + 1);
            if (r != null) return f.getName() + "/" + r;
        }
        for (File f : kids) {
            String n = f.getName().toLowerCase();
            if (f.isFile() && (n.endsWith(".html") || n.endsWith(".htm"))) return f.getName();
        }
        return null;
    }

    private static long dirSize(File d) {
        long t = 0;
        File[] kids = d.listFiles();
        if (kids == null) return 0;
        for (File f : kids) t += f.isDirectory() ? dirSize(f) : f.length();
        return t;
    }

    /** 目录名是 s<时间戳>-<原文件夹名>，显示时把前缀去掉 */
    private static String displaySiteName(String dirName) {
        int i = dirName.indexOf('-');
        return i >= 0 && i < dirName.length() - 1 ? dirName.substring(i + 1) : dirName;
    }

    private static String folderDisplayName(Context ctx, Uri treeUri) {
        try (Cursor c = ctx.getContentResolver().query(treeUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        String id = DocumentsContract.getTreeDocumentId(treeUri);
        int i = id.lastIndexOf(':');
        return i >= 0 && i < id.length() - 1 ? id.substring(i + 1) : id;
    }

    public static String displayName(Context ctx, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = ctx.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) return c.getString(idx);
                }
            } catch (Exception ignored) {
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? null : last.substring(last.lastIndexOf('/') + 1);
    }

    public static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }

    private static String safeName(String name) {
        if (name == null) return "未命名";
        return name.replaceAll("[\\\\/:*?\"<>|\\u0000]", "_");
    }

    /** 恢复出厂：只动 App 自己的目录 */
    public static void wipe(Context ctx) {
        deleteRec(siteDir(ctx));
        deleteRec(importedDir(ctx));
        ensureDirs(ctx);
        Logger.log("已清除导入的文件");
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
