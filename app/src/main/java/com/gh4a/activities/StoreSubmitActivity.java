package com.gh4a.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.fragment.StoreApi;
import com.gh4a.fragment.StorePrefs;
import com.gh4a.fragment.StoreRepo;
import com.gh4a.fragment.StoreViews;
import com.gh4a.utils.DiagTrace;
import com.gh4a.utils.UiUtils;
import com.meisolsson.githubsdk.model.Repository;

import java.util.List;

import io.reactivex.disposables.CompositeDisposable;

/**
 * 软件商店的提交仓库页：输入 GitHub 地址 → 拉取仓库与最新 Release 预览 →
 * 加入本地关注。单机版无站方人工审核，预览通过即加入本地关注并定期
 * 在商店列表中出现（关注本身不改变商店的搜索口径）。
 */
public class StoreSubmitActivity extends BaseActivity {
    public static Intent makeIntent(Context context) {
        return new Intent(context, StoreSubmitActivity.class);
    }

    private LinearLayout mContent;
    private LinearLayout mPreviewContainer;
    private LinearLayout mFollowedContainer;
    private EditText mUrlInput;
    private final CompositeDisposable mDisposables = new CompositeDisposable();

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

        int side = StoreViews.dp(this, 16);
        TextView label = StoreViews.text(this, 13, false);
        label.setText(R.string.store_submit_url_label);
        label.setTextColor(StoreViews.secondaryText(this));
        label.setPadding(side, StoreViews.dp(this, 14), side, StoreViews.dp(this, 8));
        mContent.addView(label);
        mUrlInput = new EditText(this);
        mUrlInput.setHint(R.string.store_submit_url_hint);
        mUrlInput.setSingleLine(true);
        mUrlInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(side, 0, side, 0);
        mContent.addView(mUrlInput, inputLp);
        Button previewButton = new Button(this);
        previewButton.setText(R.string.store_fetch_preview);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLp.setMargins(side, StoreViews.dp(this, 12), side, 0);
        mContent.addView(previewButton, buttonLp);
        previewButton.setOnClickListener(v -> fetchPreview());
        mUrlInput.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                fetchPreview();
                return true;
            }
            return false;
        });

        mPreviewContainer = new LinearLayout(this);
        mPreviewContainer.setOrientation(LinearLayout.VERTICAL);
        mContent.addView(mPreviewContainer);

        mContent.addView(StoreViews.sectionHeader(this,
                getString(R.string.store_followed_section)));
        mFollowedContainer = new LinearLayout(this);
        mFollowedContainer.setOrientation(LinearLayout.VERTICAL);
        mContent.addView(mFollowedContainer);
        TextView note = StoreViews.text(this, 13, false);
        note.setText(R.string.store_submit_note);
        note.setTextColor(StoreViews.secondaryText(this));
        note.setPadding(side, StoreViews.dp(this, 12), side, StoreViews.dp(this, 12));
        mContent.addView(note);
        renderFollowed();
    }

    @Override
    protected void onDestroy() {
        mDisposables.clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.store_submit);
    }

    @Override
    public void onRefresh() {
        renderFollowed();
        super.onRefresh();
    }

    /** 解析输入：接受完整地址或 owner/repo，返回 [owner, repo] 或 null。 */
    private static String[] parseOwnerRepo(String input) {
        if (TextUtils.isEmpty(input)) {
            return null;
        }
        String s = input.trim();
        String lower = s.toLowerCase();
        String prefix = null;
        if (lower.startsWith("https://github.com/")) {
            prefix = s.substring("https://github.com/".length());
        } else if (lower.startsWith("http://github.com/")) {
            prefix = s.substring("http://github.com/".length());
        } else if (lower.startsWith("github.com/")) {
            prefix = s.substring("github.com/".length());
        } else {
            prefix = s;
        }
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (prefix.endsWith(".git")) {
            prefix = prefix.substring(0, prefix.length() - 4);
        }
        String[] parts = prefix.split("/");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return parts;
    }

    private void fetchPreview() {
        String[] ownerRepo = parseOwnerRepo(mUrlInput.getText().toString());
        if (ownerRepo == null) {
            Toast.makeText(this, R.string.store_invalid_repo, Toast.LENGTH_SHORT).show();
            return;
        }
        final String owner = ownerRepo[0];
        final String repoName = ownerRepo[1];
        mPreviewContainer.removeAllViews();
        mPreviewContainer.addView(StoreViews.infoText(this, getString(R.string.loading_msg)));
        mDisposables.add(StoreApi.fetchRepo(owner, repoName)
                .flatMap(repo -> StoreApi.latestStoreRepo(repo))
                .subscribe(item -> renderPreview(item),
                        error -> {
                            DiagTrace.log(this, "STORE",
                                    "submit fail " + StoreApi.describeError(error));
                            mPreviewContainer.removeAllViews();
                            TextView errorView = StoreViews.infoText(this,
                                    getString(R.string.store_fetch_failed));
                            UiUtils.setSelectableBackground(errorView);
                            errorView.setOnClickListener(v -> fetchPreview());
                            mPreviewContainer.addView(errorView);
                        }));
    }

    private void renderPreview(final StoreRepo item) {
        mPreviewContainer.removeAllViews();
        mPreviewContainer.addView(StoreViews.sectionHeader(this,
                getString(R.string.store_submit_preview)));
        TextView name = StoreViews.text(this, 16, true);
        name.setText(item.fullName());
        int side = StoreViews.dp(this, 16);
        name.setPadding(side, 0, side, 0);
        mPreviewContainer.addView(name);
        if (!TextUtils.isEmpty(item.description)) {
            TextView desc = StoreViews.text(this, 14, false);
            desc.setText(item.description);
            desc.setTextColor(StoreViews.secondaryText(this));
            desc.setPadding(side, StoreViews.dp(this, 4), side, 0);
            mPreviewContainer.addView(desc);
        }
        TextView meta = StoreViews.text(this, 13, false);
        StringBuilder metaText = new StringBuilder("★ ")
                .append(StoreViews.formatStars(item.stars()))
                .append(" · ").append(item.language != null
                        ? item.language : getString(R.string.unknown));
        if (!item.version().isEmpty()) {
            metaText.append(" · ").append(item.version());
        }
        meta.setText(metaText.toString());
        meta.setTextColor(StoreViews.secondaryText(this));
        meta.setPadding(side, StoreViews.dp(this, 6), side, 0);
        mPreviewContainer.addView(meta);
        TextView assets = StoreViews.text(this, 13, false);
        assets.setText(item.hasAssets()
                ? getString(R.string.store_preview_assets, item.assetCount)
                : getString(R.string.store_no_release));
        assets.setTextColor(StoreViews.secondaryText(this));
        assets.setPadding(side, StoreViews.dp(this, 4), side, 0);
        mPreviewContainer.addView(assets);
        final String fullName = item.fullName();
        boolean followed = StorePrefs.isFollowed(this, fullName);
        Button followButton = new Button(this);
        followButton.setText(followed ? R.string.store_followed : R.string.store_follow);
        followButton.setEnabled(!followed);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonLp.setMargins(side, StoreViews.dp(this, 12), side, 0);
        mPreviewContainer.addView(followButton, buttonLp);
        followButton.setOnClickListener(v -> {
            StorePrefs.addFollowed(this, fullName);
            Toast.makeText(this, R.string.store_added_followed, Toast.LENGTH_SHORT).show();
            followButton.setText(R.string.store_followed);
            followButton.setEnabled(false);
            renderFollowed();
        });
    }

    private void renderFollowed() {
        mFollowedContainer.removeAllViews();
        List<String> followed = StorePrefs.getFollowed(this);
        if (followed.isEmpty()) {
            mFollowedContainer.addView(StoreViews.infoText(this,
                    getString(R.string.store_followed_empty)));
            return;
        }
        for (final String fullName : followed) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int side = StoreViews.dp(this, 16);
            row.setPadding(side, StoreViews.dp(this, 12), side, StoreViews.dp(this, 12));
            UiUtils.setSelectableBackground(row);
            TextView name = StoreViews.text(this, 15, false);
            name.setText(fullName);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);
            TextView remove = StoreViews.text(this, 14, false);
            remove.setText(R.string.store_unfollow);
            remove.setTextColor(StoreViews.primaryColor(this));
            remove.setOnClickListener(v -> {
                StorePrefs.removeFollowed(this, fullName);
                Toast.makeText(this, R.string.store_removed_followed,
                        Toast.LENGTH_SHORT).show();
                renderFollowed();
            });
            row.addView(remove);
            row.setOnClickListener(v -> {
                String[] parts = fullName.split("/");
                if (parts.length == 2) {
                    startActivity(RepositoryActivity.makeIntent(
                            StoreSubmitActivity.this, parts[0], parts[1]));
                }
            });
            mFollowedContainer.addView(row);
            mFollowedContainer.addView(StoreViews.divider(this));
        }
    }
}
