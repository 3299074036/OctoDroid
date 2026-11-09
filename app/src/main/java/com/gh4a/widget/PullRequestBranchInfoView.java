package com.gh4a.widget;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.StringRes;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.utils.StringUtils;
import com.gh4a.utils.UiUtils;
import com.meisolsson.githubsdk.model.PullRequestMarker;
import com.meisolsson.githubsdk.model.git.GitReference;

public class PullRequestBranchInfoView extends RelativeLayout implements View.OnClickListener {
    private final TextView mSourceBranchView;
    private final TextView mTargetBranchView;
    private final int mAccentColor;

    private PullRequestMarker mSourceMarker;
    private PullRequestMarker mTargetMarker;

    public PullRequestBranchInfoView(Context context) {
        this(context, null);
    }

    public PullRequestBranchInfoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullRequestBranchInfoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        inflate(context, R.layout.view_pull_request_branch_info, this);

        mAccentColor = UiUtils.resolveColor(getContext(), android.R.attr.textColorLink);
        mSourceBranchView = findViewById(R.id.tv_pr_from);
        mSourceBranchView.setOnClickListener(this);
        mTargetBranchView = findViewById(R.id.tv_pr_to);
        mTargetBranchView.setOnClickListener(this);
    }

    public void bind(PullRequestMarker sourceMarker, PullRequestMarker targetMarker,
            GitReference sourceReference) {
        mSourceMarker = sourceMarker;
        mTargetMarker = targetMarker;
        formatMarkerText(mSourceBranchView, R.string.pull_request_from, sourceMarker,
                sourceReference != null);
        formatMarkerText(mTargetBranchView, R.string.pull_request_to, targetMarker, true);
    }

    private void formatMarkerText(TextView view, @StringRes int formatResId,
            final PullRequestMarker marker, boolean makeClickable) {
        SpannableStringBuilder builder = StringUtils.applyBoldTags(getContext().getString(formatResId));
        int pos = builder.toString().indexOf("[ref]");
        if (pos >= 0) {
            // 156：head/base 缺失时 marker 为 null，分支名留空、不跳转。
            String label = "";
            if (marker != null) {
                label = TextUtils.isEmpty(marker.label()) ? marker.ref() : marker.label();
                if (label == null) {
                    label = "";
                }
            }
            builder.replace(pos, pos + 5, label);
            if (marker != null && marker.repo() != null && makeClickable) {
                builder.setSpan(
                        new ForegroundColorSpan(mAccentColor), pos, pos + label.length(), 0);
                view.setClickable(true);
            } else {
                view.setClickable(false);
            }
        }

        view.setText(builder);
    }

    @Override
    public void onClick(View v) {
        PullRequestMarker marker = v.getId() == R.id.tv_pr_from ? mSourceMarker : mTargetMarker;
        // 156：marker 可空（head/base 缺失时 bind 传入 null），不跳转。
        if (marker == null || marker.repo() == null) {
            return;
        }
        Intent intent = RepositoryActivity.makeIntent(getContext(), marker.repo(),
                marker.ref());
        getContext().startActivity(intent);
    }
}
