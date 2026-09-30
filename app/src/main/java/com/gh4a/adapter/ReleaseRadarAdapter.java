package com.gh4a.adapter;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.fragment.ReleaseRadarFragment;
import com.gh4a.utils.StringUtils;

import java.util.Date;

public class ReleaseRadarAdapter
        extends RootAdapter<ReleaseRadarFragment.RadarItem, ReleaseRadarAdapter.ViewHolder> {
    public ReleaseRadarAdapter(Context context) {
        super(context);
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_release_radar, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, ReleaseRadarFragment.RadarItem item) {
        String name = item.releaseName;
        if (TextUtils.isEmpty(name)) {
            name = item.tagName;
        }
        holder.tvRepo.setText(item.owner + "/" + item.repo);
        holder.tvRelease.setText(name);
        holder.tvTag.setText(item.tagName);

        long time = item.publishedAt != 0 ? item.publishedAt : item.createdAt;
        holder.tvTime.setText(StringUtils.formatRelativeTime(mContext, new Date(time), true));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRepo;
        private final TextView tvRelease;
        private final TextView tvTag;
        private final TextView tvTime;

        public ViewHolder(View v) {
            super(v);
            tvRepo = v.findViewById(R.id.tv_repo);
            tvRelease = v.findViewById(R.id.tv_release);
            tvTag = v.findViewById(R.id.tv_tag);
            tvTime = v.findViewById(R.id.tv_time);
        }
    }
}
