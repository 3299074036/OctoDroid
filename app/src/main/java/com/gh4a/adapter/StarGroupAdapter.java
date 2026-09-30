package com.gh4a.adapter;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.R;

import java.util.ArrayList;
import java.util.List;

public class StarGroupAdapter extends RootAdapter<StarGroupAdapter.Row, StarGroupAdapter.ViewHolder> {
    public static class Row {
        public static final int KIND_GROUP = 0;
        public static final int KIND_REPO = 1;

        public final int kind;
        public final String title;
        public final String subtitle;
        public final String key;

        public Row(int kind, String title, String subtitle, String key) {
            this.kind = kind;
            this.title = title;
            this.subtitle = subtitle;
            this.key = key;
        }
    }

    public StarGroupAdapter(Context context) {
        super(context);
    }

    public void setRows(List<Row> rows) {
        clear();
        addAll(new ArrayList<>(rows));
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_star_group, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Row row) {
        holder.ivIcon.setImageResource(row.kind == Row.KIND_GROUP
                ? R.drawable.icon_bookmark : R.drawable.icon_repositories);
        holder.tvTitle.setText(row.title);
        holder.tvSubtitle.setText(row.subtitle);
        holder.tvSubtitle.setVisibility(
                row.subtitle == null || row.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvTitle;
        private final TextView tvSubtitle;

        public ViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_icon);
            tvTitle = v.findViewById(R.id.tv_title);
            tvSubtitle = v.findViewById(R.id.tv_subtitle);
        }
    }
}
