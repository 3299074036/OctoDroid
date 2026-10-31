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
import android.graphics.Typeface;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.model.Trend;
import com.vdurmont.emoji.EmojiParser;

public class TrendAdapter extends RootAdapter<Trend, TrendAdapter.ViewHolder> {
    private final @StringRes int mStarsTemplate;

    public TrendAdapter(Context context, @StringRes int starsTemplate) {
        super(context);
        mStarsTemplate = starsTemplate;
    }

    @Override
    public ViewHolder onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.row_trend, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Trend trend) {
        // 0.1.0 排名号：adapter 位置减去 header（Home 内的时间范围选择行）即为行号，
        // 再 +1；前 3 名用现行主色加粗，其余用次要文字色。独立 TrendingActivity 无 header，
        // 位置即行号，同一算法适用。
        int adapterPos = holder.getBindingAdapterPosition();
        int rank = adapterPos == RecyclerView.NO_POSITION
                ? -1 : adapterPos - (hasHeaderView() ? 1 : 0) + 1;
        if (rank > 0) {
            holder.tvRank.setText(String.valueOf(rank));
            boolean top = rank <= 3;
            holder.tvRank.setTypeface(null, top ? Typeface.BOLD : Typeface.NORMAL);
            // 0.1.20 D-09: 排名颜色走 UiUtils.rankOrSelectedColor。
            holder.tvRank.setTextColor(
                    com.gh4a.utils.UiUtils.rankOrSelectedColor(mContext, top));
        } else {
            holder.tvRank.setText("");
        }

        String owner = trend.getRepoOwner();
        String name = trend.getRepoName();

        SpannableStringBuilder title = new SpannableStringBuilder();
        title.append(owner).append("/").append(name);
        title.setSpan(new StyleSpan(Typeface.BOLD), 0, owner.length(), 0);
        holder.tvTitle.setText(title);

        String desc = trend.getDescription();
        holder.tvDesc.setText(desc != null ? EmojiParser.parseToUnicode(desc) : null);

        String lang = trend.getLanguage();
        if (TextUtils.isEmpty(lang)) {
            holder.tvLang.setText(R.string.unknown);
        } else {
            holder.tvLang.setText(lang);
        }
        holder.tvStars.setText(mContext.getString(mStarsTemplate,
                trend.getNewStars(), trend.getStars()));
        holder.tvForks.setText(String.valueOf(trend.getForks()));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private ViewHolder(View view) {
            super(view);
            tvRank = view.findViewById(R.id.tv_rank);
            tvTitle = view.findViewById(R.id.tv_title);
            tvDesc = view.findViewById(R.id.tv_desc);
            tvLang = view.findViewById(R.id.tv_lang);
            tvStars = view.findViewById(R.id.tv_stars);
            tvForks = view.findViewById(R.id.tv_forks);
        }

        private final TextView tvRank;
        private final TextView tvTitle;
        private final TextView tvDesc;
        private final TextView tvLang;
        private final TextView tvStars;
        private final TextView tvForks;
    }
}
