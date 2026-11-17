package com.sugardeicing.localserver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** 首页列表：分组标题 + 站点 / 文件行，全部用系统自带控件画 */
public class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnClick {
        void onSite(Item item);

        void onFile(Item item);
    }

    private final List<Item> items;
    private final OnClick onClick;

    public RowAdapter(List<Item> items, OnClick onClick) {
        this.items = items;
        this.onClick = onClick;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == Item.TYPE_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_header, parent, false));
        }
        return new RowHolder(inf.inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item it = items.get(position);
        if (holder instanceof HeaderHolder) {
            HeaderHolder h = (HeaderHolder) holder;
            android.content.Context ctx = h.itemView.getContext();
            h.title.setTextColor(Prefs.fg(ctx));
            h.count.setTextColor(Prefs.muted(ctx));
            h.title.setText(it.name);
            h.count.setText(it.count);
            h.count.setVisibility(it.count == null || it.count.isEmpty() ? View.GONE : View.VISIBLE);
            return;
        }
        RowHolder h = (RowHolder) holder;
        android.content.Context ctx = h.itemView.getContext();
        h.name.setTextColor(Prefs.fg(ctx));
        h.meta.setTextColor(Prefs.muted(ctx));
        if (h.go != null) h.go.setTextColor(Prefs.muted(ctx));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Prefs.card(ctx));
        bg.setCornerRadius(Ui.dp((android.app.Activity) ctx, 14));
        h.itemView.setBackground(bg);
        h.name.setTextSize(15 * Prefs.fsScale(ctx));
        h.meta.setTextSize(12.5f * Prefs.fsScale(ctx));
        h.name.setText(it.name);
        h.meta.setText(it.meta);
        // 站点给一条橙色竖条，文件不给，一眼分得清
        h.bar.setVisibility(it.type == Item.TYPE_SITE ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> {
            if (it.type == Item.TYPE_SITE) onClick.onSite(it);
            else onClick.onFile(it);
        });
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView count;

        HeaderHolder(View v) {
            super(v);
            title = v.findViewById(R.id.headTitle);
            count = v.findViewById(R.id.headCount);
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView go;
        final View bar;

        RowHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            meta = v.findViewById(R.id.meta);
            bar = v.findViewById(R.id.bar);
            go = v.findViewById(R.id.go);
        }
    }
}
