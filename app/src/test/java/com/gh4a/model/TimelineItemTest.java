package com.gh4a.model;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.activities.PullRequestDiffViewerActivity;
import com.gh4a.model.TimelineItem.Diff;
import com.gh4a.model.TimelineItem.Reply;
import com.gh4a.model.TimelineItem.TimelineComment;
import com.gh4a.model.TimelineItem.TimelineEvent;
import com.gh4a.model.TimelineItem.TimelineReview;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.GitHubCommentBase;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class TimelineItemTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static ReviewComment reviewComment(String commitId, String path,
            Integer position, Long id, Date createdAt, String prUrl) {
        ReviewComment c = mock(ReviewComment.class);
        when(c.originalCommitId()).thenReturn(commitId);
        when(c.path()).thenReturn(path);
        when(c.originalPosition()).thenReturn(position);
        when(c.id()).thenReturn(id);
        when(c.createdAt()).thenReturn(createdAt);
        when(c.pullRequestUrl()).thenReturn(prUrl);
        when(c.commitId()).thenReturn(commitId);
        return c;
    }

    private static ReviewComment reviewComment(String commitId, String path,
            Integer position, Date createdAt) {
        return reviewComment(commitId, path, position, 42L, createdAt, null);
    }

    private static GitHubFile file(String patch) {
        GitHubFile f = mock(GitHubFile.class);
        when(f.patch()).thenReturn(patch);
        return f;
    }

    private static User user(String login) {
        User u = mock(User.class);
        when(u.login()).thenReturn(login);
        return u;
    }

    // ---- COMPARATOR ----

    @Test
    public void comparator_lhsNullDateSortsAfter() {
        GitHubCommentBase nullDate = mock(GitHubCommentBase.class);
        when(nullDate.createdAt()).thenReturn(null);
        TimelineComment a = new TimelineComment(nullDate);
        GitHubCommentBase cb = mock(GitHubCommentBase.class);
        when(cb.createdAt()).thenReturn(new Date(1000L));
        TimelineComment b2 = new TimelineComment(cb);
        assertTrue(TimelineItem.COMPARATOR.compare(a, b2) > 0);
        assertTrue(TimelineItem.COMPARATOR.compare(b2, a) < 0);
    }

    @Test
    public void comparator_bothNullDates_lhsFirstBranchWins() {
        TimelineComment a = new TimelineComment(mock(GitHubCommentBase.class));
        TimelineComment b = new TimelineComment(mock(GitHubCommentBase.class));
        assertEquals(1, TimelineItem.COMPARATOR.compare(a, b));
    }

    @Test
    public void comparator_ordersByDate() {
        GitHubCommentBase early = mock(GitHubCommentBase.class);
        when(early.createdAt()).thenReturn(new Date(1000L));
        GitHubCommentBase late = mock(GitHubCommentBase.class);
        when(late.createdAt()).thenReturn(new Date(2000L));
        TimelineComment a = new TimelineComment(early);
        TimelineComment b = new TimelineComment(late);
        assertTrue(TimelineItem.COMPARATOR.compare(a, b) < 0);
        assertTrue(TimelineItem.COMPARATOR.compare(b, a) > 0);
        assertEquals(0, TimelineItem.COMPARATOR.compare(a, a));
    }

    @Test
    public void comparator_sortList_nullDatesLast() {
        GitHubCommentBase dated = mock(GitHubCommentBase.class);
        when(dated.createdAt()).thenReturn(new Date(1000L));
        List<TimelineItem> items = new ArrayList<>();
        items.add(new TimelineComment(mock(GitHubCommentBase.class)));
        items.add(new TimelineComment(dated));
        Collections.sort(items, TimelineItem.COMPARATOR);
        assertSame(dated, ((TimelineComment) items.get(0)).comment());
    }

    // ---- fromIssueEvent ----

    @Test
    public void fromIssueEvent_commented_returnsTimelineComment() {
        IssueEvent event = mock(IssueEvent.class);
        when(event.event()).thenReturn(IssueEventType.Commented);
        GitHubComment comment = mock(GitHubComment.class);
        when(event.toComment()).thenReturn(comment);
        TimelineItem item = TimelineItem.fromIssueEvent(event);
        assertTrue(item instanceof TimelineComment);
        assertSame(comment, ((TimelineComment) item).comment());
    }

    @Test
    public void fromIssueEvent_otherType_returnsTimelineEvent() {
        IssueEvent event = mock(IssueEvent.class);
        when(event.event()).thenReturn(IssueEventType.Closed);
        TimelineItem item = TimelineItem.fromIssueEvent(event);
        assertTrue(item instanceof TimelineEvent);
        assertSame(event, ((TimelineEvent) item).event);
    }

    @Test
    public void fromIssueEvent_nullType_returnsTimelineEvent() {
        IssueEvent event = mock(IssueEvent.class);
        when(event.event()).thenReturn(null);
        assertTrue(TimelineItem.fromIssueEvent(event) instanceof TimelineEvent);
    }

    // ---- TimelineComment ----

    @Test
    public void timelineComment_plainComment_getReviewCommentNull() {
        GitHubCommentBase c = mock(GitHubCommentBase.class);
        TimelineComment tc = new TimelineComment(c);
        assertNull(tc.getReviewComment());
        assertNull(tc.file);
        assertNull(tc.getParentDiff());
    }

    @Test
    public void timelineComment_reviewComment_getReviewComment() {
        ReviewComment c = reviewComment("abc", "a.java", 1, new Date(1000L));
        TimelineComment tc = new TimelineComment(c, file("@@ patch"));
        assertSame(c, tc.getReviewComment());
        assertNotNull(tc.file);
    }

    @Test
    public void timelineComment_setReactions_replacesComment() {
        GitHubCommentBase original = mock(GitHubCommentBase.class);
        GitHubCommentBase withReactions = mock(GitHubCommentBase.class);
        when(original.withReactions(mock(Reactions.class))).thenReturn(withReactions);
        Reactions reactions = mock(Reactions.class);
        when(original.withReactions(reactions)).thenReturn(withReactions);
        TimelineComment tc = new TimelineComment(original);
        tc.setReactions(reactions);
        assertSame(withReactions, tc.comment());
    }

    @Test
    public void timelineComment_hasFilePatch_states() {
        assertFalse(new TimelineComment(mock(GitHubCommentBase.class)).hasFilePatch());
        ReviewComment c = reviewComment("abc", "a.java", 1, new Date(1000L));
        assertFalse(new TimelineComment(c, file(null)).hasFilePatch());
        assertTrue(new TimelineComment(c, file("@@ -1 +1 @@")).hasFilePatch());
    }

    @Test
    public void timelineComment_getUserAndCreatedAt_delegate() {
        GitHubCommentBase c = mock(GitHubCommentBase.class);
        User u = user("octocat");
        Date d = new Date(5000L);
        when(c.user()).thenReturn(u);
        when(c.createdAt()).thenReturn(d);
        TimelineComment tc = new TimelineComment(c);
        assertSame(u, tc.getUser());
        assertEquals(d, tc.getCreatedAt());
    }

    @Test
    public void timelineComment_getUserNull_passThrough() {
        GitHubCommentBase c = mock(GitHubCommentBase.class);
        when(c.user()).thenReturn(null);
        assertNull(new TimelineComment(c).getUser());
    }

    // ---- makeDiffIntent ----

    private static ReviewComment diffComment() {
        return reviewComment("sha123", "src/Main.java", 3, 7L, new Date(1000L),
                "https://api.github.com/repos/octocat/Hello-World/pulls/42");
    }

    @Test
    public void makeDiffIntent_noFile_returnsNull() {
        ReviewComment c = diffComment();
        TimelineComment tc = new TimelineComment(c, null);
        assertNull(tc.makeDiffIntent(context()));
    }

    @Test
    public void makeDiffIntent_plainCommentNoFile_returnsNull() {
        // A plain GitHubCommentBase can only be wrapped by the 1-arg
        // constructor (the 2-arg one requires a ReviewComment), so the
        // file-null path is what returns null here.
        TimelineComment tc = new TimelineComment(mock(GitHubCommentBase.class));
        assertNull(tc.makeDiffIntent(context()));
    }

    @Test
    public void makeDiffIntent_nullPullRequestUrl_returnsNull() {
        ReviewComment c = reviewComment("sha", "a.java", 1, 1L, new Date(1L), null);
        TimelineComment tc = new TimelineComment(c, file("p"));
        assertNull(tc.makeDiffIntent(context()));
    }

    @Test
    public void makeDiffIntent_nonMatchingUrl_returnsNull() {
        ReviewComment c = reviewComment("sha", "a.java", 1, 1L, new Date(1L),
                "https://github.com/octocat/Hello-World/issues/42");
        TimelineComment tc = new TimelineComment(c, file("p"));
        assertNull(tc.makeDiffIntent(context()));
    }

    @Test
    public void makeDiffIntent_matchingUrl_returnsIntentWithExtras() {
        TimelineComment tc = new TimelineComment(diffComment(), file("@@ patch"));
        Intent intent = tc.makeDiffIntent(context());
        assertNotNull(intent);
        assertEquals(PullRequestDiffViewerActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("Hello-World", intent.getStringExtra("repo"));
        assertEquals(42, intent.getIntExtra("number", -1));
        assertEquals("src/Main.java", intent.getStringExtra("path"));
        assertEquals("@@ patch", intent.getStringExtra("diff"));
        assertNotNull(intent.getParcelableExtra("initial_comment"));
    }

    @Test
    public void makeDiffIntent_withLine_noInitialCommentMarker() {
        TimelineComment tc = new TimelineComment(diffComment(), file("@@ patch"));
        Intent intent = tc.makeDiffIntent(context(), 5, true);
        assertNotNull(intent);
        assertNull(intent.getParcelableExtra("initial_comment"));
        assertEquals(5, intent.getIntExtra("highlight_start", -99));
    }

    @Test
    public void makeDiffIntent_nullCommentId_noMarkerButIntentBuilt() {
        ReviewComment c = reviewComment("sha123", "src/Main.java", 3, null, new Date(1000L),
                "https://api.github.com/repos/octocat/Hello-World/pulls/42");
        TimelineComment tc = new TimelineComment(c, file("@@ patch"));
        Intent intent = tc.makeDiffIntent(context());
        assertNotNull(intent);
        assertNull(intent.getParcelableExtra("initial_comment"));
    }

    @Test
    public void makeDiffIntent_apiGithubUrl_matches() {
        // pullRequestUrl() is the API URL; the pattern's "github.com/repos/"
        // segment matches it because "api.github.com" ends in "github.com".
        ReviewComment c = reviewComment("sha", "a.java", 1, 1L, new Date(1L),
                "https://api.github.com/repos/octocat/Hello-World/pulls/42");
        TimelineComment tc = new TimelineComment(c, file("p"));
        assertNotNull(tc.makeDiffIntent(context()));
    }

    @Test
    public void makeDiffIntent_webPullUrl_doesNotMatch() {
        // Web UI URLs use /pull/N without /repos/ and /pulls/, so they do not match.
        ReviewComment c = reviewComment("sha", "a.java", 1, 1L, new Date(1L),
                "https://github.com/octocat/Hello-World/pull/42");
        TimelineComment tc = new TimelineComment(c, file("p"));
        assertNull(tc.makeDiffIntent(context()));
    }

    // ---- TimelineEvent ----

    @Test
    public void timelineEvent_getUser_prefersAssigner() {
        IssueEvent e = mock(IssueEvent.class);
        User assigner = user("assigner");
        User actor = user("actor");
        when(e.assigner()).thenReturn(assigner);
        when(e.actor()).thenReturn(actor);
        assertSame(assigner, new TimelineEvent(e).getUser());
    }

    @Test
    public void timelineEvent_getUser_fallsBackToActor() {
        IssueEvent e = mock(IssueEvent.class);
        User actor = user("actor");
        when(e.assigner()).thenReturn(null);
        when(e.actor()).thenReturn(actor);
        assertSame(actor, new TimelineEvent(e).getUser());
    }

    @Test
    public void timelineEvent_getUser_bothNull() {
        IssueEvent e = mock(IssueEvent.class);
        assertNull(new TimelineEvent(e).getUser());
    }

    @Test
    public void timelineEvent_getCreatedAt_delegates() {
        IssueEvent e = mock(IssueEvent.class);
        Date d = new Date(7000L);
        when(e.createdAt()).thenReturn(d);
        assertEquals(d, new TimelineEvent(e).getCreatedAt());
    }

    // ---- TimelineReview / addComment ----

    private static Review review(User u, Date submittedAt) {
        Review r = mock(Review.class);
        when(r.user()).thenReturn(u);
        when(r.submittedAt()).thenReturn(submittedAt);
        return r;
    }

    @Test
    public void timelineReview_accessors() {
        User u = user("rev");
        Date d = new Date(9000L);
        TimelineReview tr = new TimelineReview(review(u, d));
        assertSame(u, tr.getUser());
        assertEquals(d, tr.getCreatedAt());
        assertTrue(tr.getDiffHunks().isEmpty());
    }

    @Test
    public void timelineReview_review_returnsReview() {
        Review r = review(user("rev"), new Date(9000L));
        TimelineReview tr = new TimelineReview(r);
        assertSame(r, tr.review());
    }

    @Test
    public void addComment_newHunkCreatedWhenAllowed() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(1000L)), file("p"), true);
        assertEquals(1, tr.getDiffHunks().size());
        Diff hunk = tr.getDiffHunks().iterator().next();
        assertEquals(1, hunk.comments.size());
        assertFalse(hunk.isReply());
    }

    @Test
    public void addComment_newHunkNotCreatedWhenDisallowed() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(1000L)), file("p"), false);
        assertTrue(tr.getDiffHunks().isEmpty());
    }

    @Test
    public void addComment_sameHunkSecondCommentAppended() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(1000L)), file("p"), true);
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(2000L)), file("p"), true);
        assertEquals(1, tr.getDiffHunks().size());
        Diff hunk = tr.getDiffHunks().iterator().next();
        assertEquals(2, hunk.comments.size());
        assertFalse(hunk.isReply());
        // Second comment's back-reference points at the hunk.
        assertSame(hunk, hunk.comments.get(1).getParentDiff());
    }

    @Test
    public void addComment_outOfOrderSecondComment_marksReply() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(2000L)), file("p"), true);
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(1000L)), file("p"), true);
        Diff hunk = tr.getDiffHunks().iterator().next();
        assertTrue(hunk.isReply());
        assertEquals(1, hunk.comments.size());
    }

    @Test
    public void addComment_replyHunk_ignoresFurtherComments() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(2000L)), file("p"), true);
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(1000L)), file("p"), true);
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(3000L)), file("p"), true);
        Diff hunk = tr.getDiffHunks().iterator().next();
        assertTrue(hunk.isReply());
        assertEquals(1, hunk.comments.size());
    }

    @Test
    public void addComment_nullCreatedAtDates_noReplyMarking() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, null), file("p"), true);
        tr.addComment(reviewComment("c1", "a.java", 1, null), file("p"), true);
        Diff hunk = tr.getDiffHunks().iterator().next();
        assertFalse(hunk.isReply());
        assertEquals(2, hunk.comments.size());
    }

    @Test
    public void addComment_firstDatedSecondNullDate_noReplyMarking() {
        // Line 229: diffHunk.getCreatedAt() non-null but the second
        // comment's createdAt() null, so the second operand of the &&
        // chain is false and no reply is marked.
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(2000L)), file("p"), true);
        tr.addComment(reviewComment("c1", "a.java", 1, null), file("p"), true);
        Diff hunk = tr.getDiffHunks().iterator().next();
        assertFalse(hunk.isReply());
        assertEquals(2, hunk.comments.size());
    }

    @Test
    public void addComment_differentHunksSeparate() {
        TimelineReview tr = new TimelineReview(review(null, null));
        tr.addComment(reviewComment("c1", "a.java", 1, new Date(1000L)), file("p"), true);
        tr.addComment(reviewComment("c1", "b.java", 1, new Date(1000L)), file("p"), true);
        assertEquals(2, tr.getDiffHunks().size());
    }

    // ---- Diff.getDiffHunkId ----

    @Test
    public void getDiffHunkId_normal_concat() {
        assertEquals("abc" + "a.java" + 3,
                Diff.getDiffHunkId(reviewComment("abc", "a.java", 3, new Date(1L))));
    }

    @Test
    public void getDiffHunkId_allNullFields_usesCommentId() {
        ReviewComment c = reviewComment(null, null, null, 99L, null, null);
        assertEquals("comment:99", Diff.getDiffHunkId(c));
    }

    @Test
    public void getDiffHunkId_allNullFieldsAndId_distinctPerObject() {
        ReviewComment c1 = reviewComment(null, null, null, null, null, null);
        ReviewComment c2 = reviewComment(null, null, null, null, null, null);
        assertNotEquals(Diff.getDiffHunkId(c1), Diff.getDiffHunkId(c2));
        assertTrue(Diff.getDiffHunkId(c1).startsWith("comment:"));
    }

    @Test
    public void getDiffHunkId_oneNullField_usesCommentId() {
        // Only path is null: still falls back to the comment id.
        ReviewComment c = reviewComment("abc", null, 3, 55L, null, null);
        assertEquals("comment:55", Diff.getDiffHunkId(c));
    }

    @Test
    public void getDiffHunkId_originalPositionNull_usesCommentId() {
        // Line 260: originalCommitId and path non-null but
        // originalPosition null, so the third operand of the ||
        // chain is the one that forces the comment-id fallback.
        ReviewComment c = reviewComment("abc", "a.java", null, 77L, null, null);
        assertEquals("comment:77", Diff.getDiffHunkId(c));
    }

    // ---- Diff ----

    private static Diff diffWith(ReviewComment c) {
        return new Diff(new TimelineComment(c, file("p")));
    }

    @Test
    public void diff_getUserNull_getCreatedAtDelegates() {
        Date d = new Date(4000L);
        Diff diff = diffWith(reviewComment("c", "a.java", 1, d));
        assertNull(diff.getUser());
        assertEquals(d, diff.getCreatedAt());
    }

    @Test
    public void diff_getInitialComment_returnsReviewComment() {
        ReviewComment c = reviewComment("c", "a.java", 1, new Date(1L));
        assertSame(c, diffWith(c).getInitialComment());
        assertSame(c, diffWith(c).getInitialTimelineComment().getReviewComment());
    }

    @Test
    public void diff_getInitialComment_plainComment_throwsAssertionError() {
        Diff diff = new Diff(new TimelineComment(mock(GitHubCommentBase.class)));
        try {
            diff.getInitialComment();
            org.junit.Assert.fail("expected AssertionError");
        } catch (AssertionError expected) {
            assertEquals("Missing required initial commit comment.", expected.getMessage());
        }
    }

    @Test
    public void diff_isReplyFlag() {
        Diff diff = diffWith(reviewComment("c", "a.java", 1, new Date(1L)));
        assertFalse(diff.isReply());
        diff.setIsReply(true);
        assertTrue(diff.isReply());
    }

    @Test
    public void diff_compareTo_byPath() {
        Diff a = diffWith(reviewComment("c", "a.java", 1, new Date(1L)));
        Diff b = diffWith(reviewComment("c", "b.java", 1, new Date(1L)));
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(diffWith(reviewComment("c", "a.java", 1, new Date(1L)))));
    }

    @Test
    public void diff_compareTo_nullPathsSortLast() {
        Diff nullPath = diffWith(reviewComment("c", null, 1, new Date(1L)));
        Diff named = diffWith(reviewComment("c", "a.java", 1, new Date(1L)));
        assertTrue(nullPath.compareTo(named) > 0);
        assertTrue(named.compareTo(nullPath) < 0);
        assertEquals(0, nullPath.compareTo(diffWith(reviewComment("c", null, 1, new Date(1L)))));
    }

    @Test
    public void diff_compareTo_byPositionNullSortsLast() {
        Diff withPos = diffWith(reviewComment("c", "a.java", 1, new Date(1L)));
        Diff nullPos = diffWith(reviewComment("c", "a.java", null, new Date(1L)));
        assertTrue(withPos.compareTo(nullPos) < 0);
        assertTrue(nullPos.compareTo(withPos) > 0);
    }

    @Test
    public void diff_compareTo_byCreatedAt() {
        Diff early = diffWith(reviewComment("c", "a.java", 1, new Date(1000L)));
        Diff late = diffWith(reviewComment("c", "a.java", 1, new Date(2000L)));
        assertTrue(early.compareTo(late) < 0);
        assertTrue(late.compareTo(early) > 0);
        assertEquals(0, early.compareTo(diffWith(reviewComment("c", "a.java", 1, new Date(1000L)))));
    }

    @Test
    public void diff_compareTo_nullCreatedAtSortsLast() {
        Diff dated = diffWith(reviewComment("c", "a.java", 1, new Date(1000L)));
        Diff undated = diffWith(reviewComment("c", "a.java", 1, null));
        assertTrue(undated.compareTo(dated) > 0);
        assertTrue(dated.compareTo(undated) < 0);
        assertEquals(0, undated.compareTo(diffWith(reviewComment("c", "a.java", 1, null))));
    }

    // ---- Reply ----

    @Test
    public void reply_userAndCreatedAtNull() {
        TimelineComment tc = new TimelineComment(mock(GitHubCommentBase.class));
        Reply reply = new Reply(tc);
        assertSame(tc, reply.timelineComment);
        assertNull(reply.getUser());
        assertNull(reply.getCreatedAt());
    }
}
