package com.viewer.so.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 最近文件列表适配器。
 */
public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.VH> {

    public interface OnClick {
        void onClick(MainActivity.RecentItem item);
    }

    private final List<MainActivity.RecentItem> data = new ArrayList<>();
    private final OnClick onClick;
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public RecentAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<MainActivity.RecentItem> items) {
        data.clear();
        if (items != null) data.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MainActivity.RecentItem item = data.get(position);
        h.name.setText(item.name);

        StringBuilder sub = new StringBuilder();
        try {
            File f = new File(item.path);
            sub.append(Utils.humanSize(f.length())).append("  ·  ");
            sub.append(JsonUtils.getExtension(item.name).toUpperCase(Locale.getDefault()));
        } catch (Throwable ignored) {}
        if (item.time > 0) {
            sub.append("  ·  ").append(fmt.format(new Date(item.time)));
        }
        h.meta.setText(sub.toString());

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView meta;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.item_name);
            meta = v.findViewById(R.id.item_meta);
        }
    }
}