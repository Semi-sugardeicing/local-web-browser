package com.sugardeicing.localserver;

import java.util.ArrayList;
import java.util.List;

/** 首页列表里的一行：分组标题、站点、单独导入的文件 */
public class Item {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_SITE = 1;
    public static final int TYPE_FILE = 2;

    public final int type;
    public String name = "";
    public String meta = "";
    public String count = "";
    public String siteId = "";
    public String index = "";
    public String url = "";

    private Item(int type) {
        this.type = type;
    }

    public static Item header(String title) {
        Item it = new Item(TYPE_HEADER);
        it.name = title;
        return it;
    }

    public static Item site(String id, String name, String index, int pages, long size) {
        Item it = new Item(TYPE_SITE);
        it.siteId = id;
        it.name = name;
        it.index = index == null ? "" : index;
        StringBuilder sb = new StringBuilder();
        sb.append(it.index.isEmpty() ? "没有首页" : it.index);
        sb.append(" · ").append(pages).append(" 个页面");
        if (size > 0) sb.append(" · ").append(Format.size(size));
        it.meta = sb.toString();
        return it;
    }

    public static Item file(String name, String url, long size, long time) {
        Item it = new Item(TYPE_FILE);
        it.name = name;
        it.url = url;
        it.meta = Format.size(size) + " · " + Format.when(time);
        return it;
    }

    /** 列表是「标题 + 行」，给适配器用一个扁平数组 */
    public static List<Item> build(List<Item> sites, List<Item> files) {
        List<Item> all = new ArrayList<>();
        if (!sites.isEmpty()) {
            Item h = header("站点");
            h.count = sites.size() + " 个";
            all.add(h);
            all.addAll(sites);
        }
        if (!files.isEmpty()) {
            Item h = header("单独导入的文件");
            h.count = files.size() + " 个";
            all.add(h);
            all.addAll(files);
        }
        return all;
    }
}
