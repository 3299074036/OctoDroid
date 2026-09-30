package com.gh4a.adapter;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.utils.RecentHistoryManager;
import com.gh4a.utils.StringUtils;

import java.util.Date;

public class RecentHistoryAdapter extends RootAdapter<RecentHistoryManager.Entry, RecentHistoryAdapter.ViewHolder> {
    public RecentHistoryAdapter(Context context) {
        super(context);
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_recent_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, RecentHistoryManager.Entry entry) {
        int iconRes;
        String title = entry.owner + "/" + entry.repo;
        String subtitle;
        switch (entry.type) {
            case RecentHistoryManager.TYPE_ISSUE:
                iconRes = R.drawable.icon_issues;
                subtitle = mContext.getString(R.string.recent_history_issue, entry.number);
                break;
            case RecentHistoryManager.TYPE_PR:
                iconRes = R.drawable.icon_pull_request;
                subtitle = mContext.getString(R.string.recent_history_pr, entry.number);
                break;
            default:
                iconRes = R.drawable.icon_repositories;
                subtitle = mContext.getString(R.string.recent_history_repo);
                break;
        }
        holder.ivIcon.setImageResource(iconRes);
        holder.tvTitle.setText(title);
        holder.tvSubtitle.setText(subtitle);
        holder.tvTime.setText(StringUtils.formatRelativeTime(mContext, new Date(entry.time), true));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final TextView tvTime;

        public ViewHolder(View v) {
            super(v);
            ivIcon = v.findViewById(R.id.iv_icon);
            tvTitle = v.findViewById(R.id.tv_title);
            tvSubtitle = v.findViewById(R.id.tv_subtitle);
            tvTime = v.findViewById(R.id.tv_time);
        }
    }
}
