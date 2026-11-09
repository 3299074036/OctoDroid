package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.PullRequestDiffViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;

import java.util.List;

import io.reactivex.Single;

public class PullRequestDiffLoadTask extends DiffLoadTask {
    private final int mPullRequestNumber;
    private final int mPage;

    public PullRequestDiffLoadTask(FragmentActivity activity, Uri urlToResolve,
            String repoOwner, String repoName, DiffHighlightId diffId, int pullRequestNumber, int page) {
        super(activity, urlToResolve, repoOwner, repoName, diffId);
        mPullRequestNumber = pullRequestNumber;
        mPage = page;
    }

    @Override
    protected @NonNull Intent getLaunchIntent(String sha, @NonNull GitHubFile file, DiffHighlightId diffId) {
        return PullRequestDiffViewerActivity.makeIntent(mActivity, mRepoOwner,
                mRepoName, mPullRequestNumber, sha, file.filename(), file.patch(),
                null, -1, diffId.startLine, diffId.endLine, diffId.right, null);
    }

    @NonNull
    @Override
    protected Intent getFallbackIntent(String sha) {
        return PullRequestActivity.makeIntent(mActivity, mRepoOwner, mRepoName,
                mPullRequestNumber, mPage, null);
    }

    @Override
    protected Single<String> getSha() {
        PullRequestService service = ServiceFactory.get(PullRequestService.class, false);
        return service.getPullRequest(mRepoOwner, mRepoName, mPullRequestNumber)
                .map(ApiHelpers::throwOnFailure)
                .map(pr -> {
                    // 156：head/sha 可空；缺失时抛错，由 UrlLoadTask 接住走
                    // getFallbackIntent（浏览器打开 PR）而非 NPE 崩溃。
                    if (pr.head() == null || pr.head().sha() == null) {
                        throw new IllegalStateException(
                                "Pull request head sha is missing");
                    }
                    return pr.head().sha();
                });
    }

    @Override
    protected Single<List<GitHubFile>> getFiles() {
        var service = ServiceFactory.getForFullPagedLists(PullRequestService.class, false);
        return ApiHelpers.PageIterator
                .toSingle(page -> service.getPullRequestFiles(mRepoOwner, mRepoName, mPullRequestNumber, page));
    }
}
