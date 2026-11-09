/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.adapter;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filterable;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.Repository;

import java.util.Locale;

public class RepositoryAdapter extends RootAdapter<Repository, RepositoryAdapter.ViewHolder>
        implements Filterable {
    public RepositoryAdapter(Context context) {
        super(context);
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_repo, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Repository repository) {
        holder.tvTitle.setText(ApiHelpers.formatRepoName(mContext, repository));

        if (!StringUtils.isBlank(repository.description())) {
            holder.tvDesc.setVisibility(View.VISIBLE);
            holder.tvDesc.setText(StringUtils.parseEmojiToUnicode(repository.description()));
        } else {
            holder.tvDesc.setVisibility(View.GONE);
        }

        // 0.4.0 像素重排：原型仓库行 meta 为单行“语言  ★ 星  ⑂ Fork  体积”，
        // private/fork 改为 meta 前缀词，不再分栏显示。
        // 166：isPrivate()/isFork() 为可空 Boolean，size() 为可空 Integer，直接拆箱即 NPE；
        // stargazersCount()/forksCount() 为 null 时 StringBuilder 会拼出字面 "null"，一并按 0 计。
        StringBuilder meta = new StringBuilder();
        if (Boolean.TRUE.equals(repository.isPrivate())) {
            meta.append(mContext.getString(R.string.repo_type_private)).append("  ·  ");
        }
        if (Boolean.TRUE.equals(repository.isFork())) {
            meta.append(mContext.getString(R.string.fork)).append("  ·  ");
        }
        meta.append(repository.language() != null
                ? repository.language() : mContext.getString(R.string.unknown));
        meta.append("  ★ ").append(ApiHelpers.orZero(repository.stargazersCount()));
        meta.append("  ⑂ ").append(ApiHelpers.orZero(repository.forksCount()));
        meta.append("  ").append(Formatter.formatFileSize(mContext, 1024L * ApiHelpers.orZero(repository.size())));
        holder.tvMeta.setText(meta.toString());
    }

    @Override
    protected boolean isFiltered(CharSequence filter, Repository repo) {
        String lcFilter = filter.toString().toLowerCase(Locale.getDefault());
        // 166：name() 可空，无名仓库不过滤命中。
        if (repo.name() == null) {
            return false;
        }
        String name = repo.name().toLowerCase(Locale.getDefault());
        return name.contains(lcFilter);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private ViewHolder(View view) {
            super(view);
            tvTitle = view.findViewById(R.id.tv_title);
            tvDesc = view.findViewById(R.id.tv_desc);
            tvMeta = view.findViewById(R.id.tv_meta);
        }

        private final TextView tvTitle;
        private final TextView tvDesc;
        private final TextView tvMeta;
    }
}
