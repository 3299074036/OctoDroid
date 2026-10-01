package com.gh4a.adapter;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.fragment.DownloadListFragment;
import com.gh4a.utils.StringUtils;

import java.util.Date;

public class DownloadListAdapter
        extends RootAdapter<DownloadListFragment.Item, DownloadListAdapter.ViewHolder> {
    public DownloadListAdapter(Context context) {
        super(context);
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_recent_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, DownloadListFragment.Item item) {
        holder.ivIcon.setImageResource(R.drawable.download_small);
        holder.tvTitle.setText(item.record.fileName);

        String status;
        switch (item.status) {
            case DownloadManager.STATUS_SUCCESSFUL:
                status = mContext.getString(R.string.download_status_completed);
                break;
            case DownloadManager.STATUS_FAILED:
                status = mContext.getString(R.string.download_status_failed);
                break;
            default:
                status = mContext.getString(R.string.download_status_downloading);
                break;
        }
        String subtitle = item.record.description != null && !item.record.description.isEmpty()
                ? status + " · " + item.record.description
                : status;
        holder.tvSubtitle.setText(subtitle);
        holder.tvTime.setText(StringUtils.formatRelativeTime(mContext, new Date(item.record.time), true));
    }

    /** Queries DownloadManager for the live status of one download id. */
    public static int queryStatus(Context context, long downloadId) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor c = dm.query(query)) {
            if (c != null && c.moveToFirst()) {
                return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            }
        } catch (Exception ignored) {
        }
        return DownloadManager.STATUS_FAILED;
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
