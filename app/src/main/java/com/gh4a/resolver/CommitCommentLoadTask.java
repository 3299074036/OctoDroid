package com.gh4a.resolver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.CommitDiffViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.FileUtils;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommitService;

import java.util.List;
import java.util.Optional;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

public class CommitCommentLoadTask extends UrlLoadTask {
    @VisibleForTesting
    protected final String mRepoOwner;
    @VisibleForTesting
    protected final String mRepoName;
    @VisibleForTesting
    protected final String mCommitSha;
    @VisibleForTesting
    protected final IntentUtils.InitialCommentMarker mMarker;

    public CommitCommentLoadTask(FragmentActivity activity, Uri urlToResolve, String repoOwner,
            String repoName, String commitSha, IntentUtils.InitialCommentMarker marker) {
        super(activity, urlToResolve);
        mRepoOwner = repoOwner;
        mRepoName = repoName;
        mCommitSha = commitSha;
        mMarker = marker;
    }

    @Override
    protected Single<Optional<Intent>> getSingle() {
        return load(mActivity, mRepoOwner, mRepoName, mCommitSha, mMarker);
    }

    public static Single<Optional<Intent>> load(Context context,
            String repoOwner, String repoName, String commitSha,
            IntentUtils.InitialCommentMarker marker) {
        RepositoryCommitService commitService = ServiceFactory.get(RepositoryCommitService.class, false);
        var commentService = ServiceFactory.getForFullPagedLists(RepositoryCommentService.class, false);

        Single<Commit> commitSingle = commitService.getCommit(repoOwner, repoName, commitSha)
                .map(ApiHelpers::throwOnFailure)
                .subscribeOn(Schedulers.io())
                .cache(); // single is used multiple times -> avoid refetching data
        Single<List<GitComment>> commentSingle = ApiHelpers.PageIterator
                .toSingle(page -> commentService.getCommitComments(repoOwner, repoName, commitSha, page))
                .subscribeOn(Schedulers.io())
                .cache(); // single is used multiple times -> avoid refetching data

        Single<Optional<GitHubFile>> fileSingle = commentSingle
                // 166：c.id() 为可空 Long，matches(long, Date) 会拆箱。
                .compose(RxUtils.filterAndMapToFirst(c -> c.id() != null && marker.matches(c.id(), c.createdAt())))
                .zipWith(commitSingle, (comment, commit) -> {
                    // 208：commit/files() 可空，commitFile/filename 可空。
                    if (comment.isPresent() && commit != null && commit.files() != null) {
                        for (GitHubFile commitFile : commit.files()) {
                            if (commitFile != null && commitFile.filename() != null
                                    && commitFile.filename().equals(comment.get().path())) {
                                return Optional.of(commitFile);
                            }
                        }
                    }
                    return Optional.empty();
                });

        return Single.zip(commitSingle, commentSingle, fileSingle, (commit, comments, fileOpt) -> {
            GitHubFile file = fileOpt.orElse(null);
            if (file != null && !FileUtils.isImage(file.filename())) {
                return Optional.of(CommitDiffViewerActivity.makeIntent(context,
                        repoOwner, repoName, commitSha, file.filename(), file.patch(),
                        comments, -1, -1, false, marker));
            } else if (file == null) {
                return Optional.of(
                        CommitActivity.makeIntent(context, repoOwner, repoName, commitSha, marker));
            }
            return Optional.empty();
        });
    }
}
