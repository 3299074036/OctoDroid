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
import com.gh4a.fragment.StoreApi;
import com.gh4a.fragment.StoreRepo;
import com.gh4a.fragment.StoreViews;
import com.gh4a.utils.DiagTrace;
import com.gh4a.utils.UiUtils;

import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

/**
 * 软件商店的最新入库／最近更新列表页（与首页同口径，点行进详情）。
 */
public class StoreListActivity extends BaseActivity {
    public static final String MODE_NEWEST = "newest";
    public static final String MODE_RECENT = "recent";

    public static Intent makeIntent(Context context, String mode) {
        return new Intent(context, StoreListActivity.class).putExtra("mode", mode);
    }

    private String mMode;
    private LinearLayout mContent;
    private final CompositeDisposable mDisposables = new CompositeDisposable();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMode = getIntent().getStringExtra("mode");
        if (mMode == null) {
            mMode = MODE_RECENT;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        load();
    }

    @Override
    protected void onDestroy() {
        mDisposables.clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(MODE_NEWEST.equals(mMode)
                ? R.string.store_newest : R.string.store_recent_updated);
    }

    @Override
    public void onRefresh() {
        load();
        super.onRefresh();
    }

    private void load() {
        mContent.removeAllViews();
        mContent.addView(StoreViews.infoText(this, getString(R.string.loading_msg)));
        mDisposables.clear();
        Single<List<StoreRepo>> single = MODE_NEWEST.equals(mMode)
                ? StoreApi.loadListNewest() : StoreApi.loadListRecent();
        mDisposables.add(single.subscribe(items -> {
            mContent.removeAllViews();
            if (items == null || items.isEmpty()) {
                mContent.addView(StoreViews.infoText(this,
                        getString(R.string.store_empty_result)));
                return;
            }
            for (StoreRepo item : items) {
                final StoreRepo repo = item;
                mContent.addView(StoreViews.repoRow(this, item, v -> startActivity(
                        RepositoryActivity.makeIntent(StoreListActivity.this,
                                repo.owner(), repo.name()))));
                mContent.addView(StoreViews.divider(this));
            }
        }, error -> {
            DiagTrace.log(this, "STORE", "list fail " + StoreApi.describeError(error));
            mContent.removeAllViews();
            TextView errorView = StoreViews.infoText(this,
                    getString(R.string.store_fetch_failed));
            UiUtils.setSelectableBackground(errorView);
            errorView.setOnClickListener(v -> load());
            mContent.addView(errorView);
        }));
    }
}
