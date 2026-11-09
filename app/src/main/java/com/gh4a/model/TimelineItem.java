package com.gh4a.model;

import android.content.Context;
import android.content.Intent;

import com.gh4a.activities.PullRequestDiffViewerActivity;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class TimelineItem {
    public static final Comparator<TimelineItem> COMPARATOR = (lhs, rhs) -> {
        if (lhs.getCreatedAt() == null) {
            return 1;
        }
        if (rhs.getCreatedAt() == null) {
            return -1;
        }
        return lhs.getCreatedAt().compareTo(rhs.getCreatedAt());
    };

    public static TimelineItem fromIssueEvent(IssueEvent event) {
        if (event.event() == IssueEventType.Commented) {
            return new TimelineComment(event.toComment());
        } else {
            return new TimelineEvent(event);
        }
    }

    public static class TimelineComment extends TimelineItem {
        private static final Pattern PULL_REQUEST_PATTERN =
                Pattern.compile(".*github\\.com/repos/([^/]+)/([^/]+)/pulls/(\\d+)");

        @NonNull
        private GitHubCommentBase comment;

        @Nullable
        public final GitHubFile file;

        private Diff diff;

        public TimelineComment(@NonNull GitHubCommentBase comment) {
            this.comment = comment;
            this.file = null;
        }

        public TimelineComment(@NonNull ReviewComment comment, @Nullable GitHubFile file) {
            this.comment = comment;
            this.file = file;
        }

        public GitHubCommentBase comment() {
            return comment;
        }

        public ReviewComment getReviewComment() {
            return comment instanceof ReviewComment ? (ReviewComment) comment : null;
        }

        public void setReactions(Reactions reactions) {
            comment = comment.withReactions(reactions);
        }

        public boolean hasFilePatch() {
            return file != null && file.patch() != null;
        }

        @Nullable
        @Override
        public User getUser() {
            return comment.user();
        }

        public Diff getParentDiff() {
            return diff;
        }

        @Override
        public Date getCreatedAt() {
            return comment.createdAt();
        }

        @Nullable
        public Intent makeDiffIntent(Context context) {
            return makeDiffIntent(context, -1, false);
        }

        @Nullable
        public Intent makeDiffIntent(Context context, int line, boolean isRightNumber) {
            ReviewComment reviewComment = getReviewComment();
            if (file == null || reviewComment == null) {
                return null;
            }

            // 122：pullRequestUrl() 可空，Pattern.matcher(null) 即 NPE
            if (reviewComment.pullRequestUrl() == null) {
                return null;
            }
            Matcher matcher = PULL_REQUEST_PATTERN.matcher(reviewComment.pullRequestUrl());
            if (matcher.matches()) {
                String repoOwner = matcher.group(1);
                String repoName = matcher.group(2);
                int pullRequestNumber = Integer.parseInt(matcher.group(3));

                // 234：reviewComment.id() 为 Long 可空，InitialCommentMarker
                // 形参为 long；line == -1 路径是活的（DiffViewHolder:223 与
                // TimelineItemAdapter:131 均调单参版，内部 line = -1），
                // 缺 id 时直接拆箱即 NPE；与 117 号在 EventListFragment
                // :178/210 的同款守卫一致，缺 id 时不建 marker。
                IntentUtils.InitialCommentMarker initialComment = line == -1
                        && reviewComment.id() != null
                        ? new IntentUtils.InitialCommentMarker(reviewComment.id()) : null;

                return PullRequestDiffViewerActivity.makeIntent(context, repoOwner, repoName,
                        pullRequestNumber, reviewComment.commitId(), reviewComment.path(),
                        file.patch(), null, -1, line, line, isRightNumber, initialComment);
            }

            return null;
        }
    }

    public static class TimelineEvent extends TimelineItem {
        @NonNull
        public final IssueEvent event;

        public TimelineEvent(@NonNull IssueEvent event) {
            this.event = event;
        }

        @Nullable
        @Override
        public User getUser() {
            if (event.assigner() != null) {
                return event.assigner();
            }
            return event.actor();
        }

        @Override
        public Date getCreatedAt() {
            return event.createdAt();
        }
    }

    public static class TimelineReview extends TimelineItem {
        @NonNull
        private final Review review;

        @NonNull
        private final Map<String, Diff> diffHunksBySpecialId = new HashMap<>();

        public TimelineReview(@NonNull Review review) {
            this.review = review;
        }

        public Review review() {
            return review;
        }

        @Nullable
        @Override
        public User getUser() {
            return review.user();
        }

        @Override
        public Date getCreatedAt() {
            return review.submittedAt();
        }

        public Collection<Diff> getDiffHunks() {
            return diffHunksBySpecialId.values();
        }

        /**
         * Add the specified comment and its matching commit file to correct diff hunk inside of
         * this review.
         * NOTE: This method expects to be called first with comments from this review in a sorted
         * order.
         *
         * @param comment The comment to add to this review.
         * @param file The commit file in which the comment was created.
         * @param addNewDiffHunk {@code true} if new diff hunk should be created if it's not found.
         */
        public void addComment(@NonNull ReviewComment comment, @Nullable GitHubFile file,
                boolean addNewDiffHunk) {
            // Comments are grouped by a special diff hunk id which is a combination of these 3
            // fields. By using this id we can display comments and their replies together under
            // a single diff hunk.
            // NOTE: Using this id is not correct in all of the possible cases (Comments created
            // with "Start new conversation" are incorrect). Sadly the GitHub API doesn't provide
            // better information than that so this is all that we can rely on.
            String id = Diff.getDiffHunkId(comment);

            Diff diffHunk = diffHunksBySpecialId.get(id);
            if (diffHunk == null) {
                if (addNewDiffHunk) {
                    diffHunk = new Diff(new TimelineComment(comment, file));
                    diffHunksBySpecialId.put(id, diffHunk);
                }
            } else {
                if (diffHunk.isReply()) {
                    // To match how GitHub web interface works diff hunks with this property set to
                    // true should display only the initial comments.
                    return;
                }

                // 122：comment.createdAt() 可空，Date.after(null) 即 NPE
                if (diffHunk.getCreatedAt() != null && comment.createdAt() != null &&
                        diffHunk.getCreatedAt().after(comment.createdAt())) {
                    // Because we are adding all of the comments in order based on their creation
                    // date and also first add only comments from the initial review we know that
                    // if something comes out of order then our initial comment was a reply.
                    diffHunk.setIsReply(true);
                } else {
                    TimelineComment timelineComment = new TimelineComment(comment, file);
                    timelineComment.diff = diffHunk;
                    diffHunk.comments.add(timelineComment);
                }
            }
        }
    }

    public static class Diff extends TimelineItem implements Comparable<Diff> {
        @NonNull
        public final List<TimelineComment> comments = new ArrayList<>();

        private boolean mIsReply;

        /**
         * Returns special id that is used to group comments together with all replies to them
         * under the same diff hunk.
         *
         * @param comment The comment for which to return the special id.
         */
        public static String getDiffHunkId(ReviewComment comment) {
            String originalCommitId = comment.originalCommitId();
            String path = comment.path();
            Integer originalPosition = comment.originalPosition();
            if (originalCommitId == null || path == null || originalPosition == null) {
                // 235：三元组任一字段为 null 时字符串拼接产生字面 "null"，
                // 全 null 的评论一律得到同一 id "nullnullnull"，在
                // diffHunksBySpecialId 里命中同一条目，多条无关评论被分进
                // 同一个 hunk、回复归属错乱；回落到评论自身 id（id 再缺则
                // 用对象标识区分，保证每条评论独立成组）。
                Long id = comment.id();
                return "comment:" + (id != null
                        ? String.valueOf(id) : System.identityHashCode(comment));
            }
            return originalCommitId + path + originalPosition;
        }

        public Diff(TimelineComment timelineComment) {
            timelineComment.diff = this;
            comments.add(timelineComment);
        }

        /**
         * Indicates whether this diff hunk corresponds to an initial review comment which was
         * created as a reply to different review comment.
         * <p>
         * This property is used to match how GitHub web interface works. These comments are special
         * because for them reply button is not displayed and they are the only comments that should
         * be visible in the corresponding diff hunk.
         */
        public boolean isReply() {
            return mIsReply;
        }

        /**
         * Mark that this diff hunk corresponds to an initial review comment that was created as
         * reply to different review comment.
         */
        public void setIsReply(boolean isReply) {
            mIsReply = isReply;
        }

        @NonNull
        public TimelineComment getInitialTimelineComment() {
            TimelineComment timelineComment = comments.get(0);

            if (timelineComment == null) {
                throw new AssertionError("Missing required initial comment.");
            }

            return timelineComment;
        }

        @NonNull
        public ReviewComment getInitialComment() {
            TimelineComment comment = getInitialTimelineComment();

            if (comment.getReviewComment() == null) {
                throw new AssertionError("Missing required initial commit comment.");
            }

            return comment.getReviewComment();
        }

        @Nullable
        @Override
        public User getUser() {
            return null;
        }

        @Override
        public Date getCreatedAt() {
            return getInitialComment().createdAt();
        }

        @Override
        public int compareTo(@NonNull Diff other) {
            ReviewComment comment = getInitialComment();
            ReviewComment otherComment = other.getInitialComment();

            // 122：path() 可空，compareTo 即 NPE；originalPosition() 为
            // Integer 可空，< 比较会拆箱 NPE（null 排最后，与 createdAt 的口径一致）
            String path = comment.path();
            String otherPath = otherComment.path();
            int byPath;
            if (path == null && otherPath == null) {
                byPath = 0;
            } else if (path == null) {
                byPath = 1;
            } else if (otherPath == null) {
                byPath = -1;
            } else {
                byPath = path.compareTo(otherPath);
            }
            if (byPath != 0) {
                return byPath;
            }

            // Then by line numbers
            int position = comment.originalPosition() != null ? comment.originalPosition() : Integer.MAX_VALUE;
            int otherPosition = otherComment.originalPosition() != null
                    ? otherComment.originalPosition() : Integer.MAX_VALUE;
            if (position < otherPosition) {
                return -1;
            }
            if (position > otherPosition) {
                return 1;
            }

            Date createdAt = getCreatedAt();
            Date otherCreatedAt = other.getCreatedAt();

            if (createdAt == null && otherCreatedAt == null) {
                return 0;
            }

            // Null created date means that the diff is for a pending review, place these after
            // other diffs.
            if (createdAt == null) {
                return 1;
            }
            if (otherCreatedAt == null) {
                return -1;
            }

            return createdAt.compareTo(otherCreatedAt);
        }
    }

    public static class Reply extends TimelineItem {
        @NonNull
        public final TimelineComment timelineComment;

        public Reply(@NonNull TimelineComment timelineComment) {
            this.timelineComment = timelineComment;
        }

        @Nullable
        @Override
        public User getUser() {
            return null;
        }

        @Override
        public Date getCreatedAt() {
            return null;
        }
    }

    @Nullable
    public abstract User getUser();

    @Nullable
    public abstract Date getCreatedAt();
}
