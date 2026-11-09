package com.gh4a.adapter;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.User;

import java.util.Date;

public class ReleaseAssetAdapter extends RootAdapter<ReleaseAsset, ReleaseAssetAdapter.ViewHolder> {
    public ReleaseAssetAdapter(Context context) {
        super(context);
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_download, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, ReleaseAsset asset) {
        holder.tvTitle.setText(asset.name());
        if (!StringUtils.isBlank(asset.label())) {
            holder.tvDesc.setVisibility(View.VISIBLE);
            holder.tvDesc.setText(asset.label());
        } else {
            holder.tvDesc.setVisibility(View.GONE);
        }

        holder.tvCreatedAt.setText(mContext.getString(R.string.download_created,
                StringUtils.formatRelativeTime(mContext, asset.createdAt(), true)));

        if (asset instanceof SourceCodeAsset) {
            // synthetic entries for the auto-generated source archives (#1683):
            // size and download count are unknown, so don't show them
            holder.tvSize.setVisibility(View.GONE);
            holder.tvDownloads.setVisibility(View.GONE);
        } else {
            holder.tvSize.setVisibility(View.VISIBLE);
            // 218：size() 为 Integer 可空，formatFileSize 形参为 long，
            // 缺 size 时直接拆箱即 NPE；downloadCount() 缺失时
            // String.valueOf 会显示字面 "null"，改为隐藏该栏。
            holder.tvSize.setText(Formatter.formatFileSize(mContext,
                    ApiHelpers.orZero(asset.size())));
            if (asset.downloadCount() == null) {
                holder.tvDownloads.setVisibility(View.GONE);
            } else {
                holder.tvDownloads.setVisibility(View.VISIBLE);
                holder.tvDownloads.setText(String.valueOf(asset.downloadCount()));
            }
        }
    }

    // Synthetic release asset representing the auto-generated source code archives
    // (zipball/tarball). The GitHub API doesn't include them in the release's asset
    // list (#1683), so we add them ourselves. ReleaseAsset has no builder, hence the subclass.
    public static class SourceCodeAsset extends ReleaseAsset {
        private final String mName;
        private final String mUrl;
        private final String mContentType;
        private final Date mCreatedAt;

        public SourceCodeAsset(String name, String url, String contentType, Date createdAt) {
            mName = name;
            mUrl = url;
            mContentType = contentType;
            mCreatedAt = createdAt;
        }

        @Override public String url() { return mUrl; }
        @Override public String name() { return mName; }
        @Override public String label() { return null; }
        @Override public String state() { return "uploaded"; }
        @Override public Long id() { return 0L; }
        @Override public Integer size() { return 0; }
        @Override public User uploader() { return null; }
        @Override public String browserDownloadUrl() { return mUrl; }
        @Override public String contentType() { return mContentType; }
        @Override public Integer downloadCount() { return 0; }
        @Override public Date createdAt() { return mCreatedAt; }
        @Override public Date updatedAt() { return mCreatedAt; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mName);
            dest.writeString(mUrl);
            dest.writeString(mContentType);
            dest.writeLong(mCreatedAt != null ? mCreatedAt.getTime() : 0);
        }

        // B-L12：补 CREATOR（之前只有 writeToParcel 没有 CREATOR，
        // lint ParcelCreator 报错；跨进程/跨 Intent 传递会抛异常）。
        // 注：报告称此类是死代码是误判——ReleaseInfoActivity 用它补
        // zipball/tarball 两条合成记录，故补 CREATOR 而不是删除。
        protected SourceCodeAsset(Parcel in) {
            mName = in.readString();
            mUrl = in.readString();
            mContentType = in.readString();
            long t = in.readLong();
            mCreatedAt = t != 0 ? new Date(t) : null;
        }

        public static final Parcelable.Creator<SourceCodeAsset> CREATOR =
                new Parcelable.Creator<SourceCodeAsset>() {
            @Override
            public SourceCodeAsset createFromParcel(Parcel in) {
                return new SourceCodeAsset(in);
            }

            @Override
            public SourceCodeAsset[] newArray(int size) {
                return new SourceCodeAsset[size];
            }
        };
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tv_title);
            tvDesc = view.findViewById(R.id.tv_desc);
            tvCreatedAt = view.findViewById(R.id.tv_created_at);
            tvSize = view.findViewById(R.id.tv_size);
            tvDownloads = view.findViewById(R.id.tv_downloads);
        }

        private final TextView tvTitle;
        private final TextView tvDesc;
        private final TextView tvSize;
        private final TextView tvDownloads;
        private final TextView tvCreatedAt;
    }
}
