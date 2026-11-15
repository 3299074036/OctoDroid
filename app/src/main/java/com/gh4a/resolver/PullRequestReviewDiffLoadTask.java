package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.ReviewActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;

import java.util.Optional;

import io.reactivex.Single;

public class PullRequestReviewDiffLoadTask extends UrlLoadTask {
    @VisibleForTesting
    protected final String mRepoOwner;
    @VisibleForTesting
    protected final String mRepoName;
    @VisibleForTesting
    protected final DiffHighlightId mDiffId;
    @VisibleForTesting
    protected final int mPullRequestNumber;

    public PullRequestReviewDiffLoadTask(FragmentActivity activity, Uri urlToResolve,
            String repoOwner, String repoName, DiffHighlightId diffId, int pullRequestNumber) {
        super(activity, urlToResolve);
        mRepoOwner = repoOwner;
        mRepoName = repoName;
        mDiffId = diffId;
        mPullRequestNumber = pullRequestNumber;
    }

    @Override
    protected Single<Optional<Intent>> getSingle() {
        var service = ServiceFactory.getForFullPagedLists(PullRequestReviewCommentService.class, false);
        final PullRequestReviewService reviewService =
                ServiceFactory.get(PullRequestReviewService.class, false);
        long diffCommentId = Long.parseLong(mDiffId.fileHash);

        return ApiHelpers.PageIterator
                .first(page -> service.getPullRequestComments(mRepoOwner, mRepoName, mPullRequestNumber, page),
                        c -> c.id() != null && c.id() == diffCommentId)
                .flatMap(commentOpt -> {
                    // T-014：匹配到的 comment 若 pullRequestReviewId 为 null，
                    // 无 review 可跳，直接返回空结果（不是错误页）。
                    if (!commentOpt.isPresent() || commentOpt.get().pullRequestReviewId() == null) {
                        return Single.<Optional<Review>>just(Optional.empty());
                    }
                    return RxUtils.mapToSingle(commentOpt, comment -> {
                        long reviewId = comment.pullRequestReviewId();
                        return reviewService.getReview(mRepoOwner, mRepoName, mPullRequestNumber, reviewId)
                                .map(ApiHelpers::throwOnFailure);
                    });
                })
                .map(reviewOpt -> reviewOpt.map(review -> ReviewActivity.makeIntent(
                        mActivity, mRepoOwner, mRepoName, mPullRequestNumber, review,
                            new IntentUtils.InitialCommentMarker(diffCommentId)))
                );
    }
}
