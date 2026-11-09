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
        // 0.2.2：下载完成后显示文件大小与 SHA-256 前 16 位（供人工核对；
        // 完整哈希在记录里，旧记录没有则不显示）。
        if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
            if (item.record.size > 0) {
                subtitle += " · " + android.text.format.Formatter.formatFileSize(
                        mContext, item.record.size);
            }
            if (item.record.sha256 != null && !item.record.sha256.isEmpty()) {
                String shortHash = item.record.sha256.length() > 16
                        ? item.record.sha256.substring(0, 16) + "…" : item.record.sha256;
                subtitle += " · SHA-256: " + shortHash;
            }
        }
        holder.tvSubtitle.setText(subtitle);
        holder.tvTime.setText(StringUtils.formatRelativeTime(mContext, item.record.time, true));
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
