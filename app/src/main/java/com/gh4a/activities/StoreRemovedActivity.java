package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.fragment.StorePrefs;
import com.gh4a.fragment.StoreViews;

import java.util.List;

/**
 * 软件商店的已下架记录页：本地记录的被移除仓库、原因与日期。
 * 记录来源：详情页取 Release 失败且为 404 时记一条（仓库已删除或设为私有）。
 * 本地记录初始为空，不放示例数据。
 */
public class StoreRemovedActivity extends BaseActivity {
    public static Intent makeIntent(Context context) {
        return new Intent(context, StoreRemovedActivity.class);
    }

    private LinearLayout mContent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        render();
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.store_removed);
    }

    @Override
    public void onRefresh() {
        render();
        super.onRefresh();
    }

    private void render() {
        mContent.removeAllViews();
        TextView note = StoreViews.text(this, 13, false);
        note.setText(R.string.store_removed_note);
        note.setTextColor(StoreViews.secondaryText(this));
        int side = StoreViews.dp(this, 16);
        note.setPadding(side, StoreViews.dp(this, 12), side, 0);
        mContent.addView(note);
        List<StorePrefs.RemovedEntry> entries = StorePrefs.getRemoved(this);
        if (entries.isEmpty()) {
            mContent.addView(StoreViews.infoText(this,
                    getString(R.string.store_removed_empty)));
            return;
        }
        for (StorePrefs.RemovedEntry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(side, StoreViews.dp(this, 12), side, StoreViews.dp(this, 12));
            TextView name = StoreViews.text(this, 15, true);
            name.setText(entry.name);
            row.addView(name);
            TextView reason = StoreViews.text(this, 14, false);
            reason.setText(entry.reason);
            reason.setTextColor(StoreViews.secondaryText(this));
            row.addView(reason);
            TextView date = StoreViews.text(this, 13, false);
            date.setText(entry.date);
            date.setTextColor(StoreViews.secondaryText(this));
            row.addView(date);
            mContent.addView(row);
            mContent.addView(StoreViews.divider(this));
        }
    }
}
