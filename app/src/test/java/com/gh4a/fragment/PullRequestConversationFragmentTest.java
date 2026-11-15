package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.MenuItem;

import androidx.appcompat.view.menu.MenuBuilder;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.EditIssueCommentActivity;
import com.gh4a.activities.EditPullRequestCommentActivity;
import com.gh4a.model.StatusWrapper;
import com.gh4a.widget.CommitStatusBox;
import com.meisolsson.githubsdk.model.CheckRun;
import com.meisolsson.githubsdk.model.CheckRunsResponse;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.ReviewState;
import com.meisolsson.githubsdk.model.Status;
import com.meisolsson.githubsdk.model.StatusState;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.PullRequestMarker;
import com.meisolsson.githubsdk.model.git.GitReference;
import com.meisolsson.githubsdk.model.request.git.CreateGitReference;
import com.meisolsson.githubsdk.service.checks.ChecksService;
import com.meisolsson.githubsdk.service.git.GitService;
import com.meisolsson.githubsdk.service.issues.IssueCommentService;
import com.meisolsson.githubsdk.service.issues.IssueTimelineService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;
import com.meisolsson.githubsdk.service.repositories.RepositoryStatusService;
import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.model.TimelineItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.BooleanSupplier;

import io.reactivex.Observable;
import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** PullRequestConversationFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class PullRequestConversationFragmentTest {

    /** Minimal concrete BaseActivity: IssueFragmentBase.onViewCreated needs one. */
    public static class TestBaseActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // BaseActivity.onStart throws unless a content view was set.
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    public static class NoLoad extends PullRequestConversationFragment {
        @Override
        protected List<TimelineItem> onGetInitialData() {
            return null;
        }

        @Override
        protected Observable<List<TimelineItem>> onCreateDataObservable(
                boolean bypassCache) {
            return Observable.never();
        }

        @Override
        protected Single<List<TimelineItem>> onCreateDataSingle(boolean bypassCache) {
            return Single.never();
        }
    }

    private static Issue issue(Integer number, IssueState state) {
        return Issue.builder().number(number).state(state)
                .url("https://api.github.com/repos/o/r/issues/1").build();
    }

    private static PullRequest pr(Integer number, IssueState state, Boolean merged) {
        // draft()/merged() 均为可空 Boolean；T-112 后 assignHighlightColor
        // 用 Boolean.TRUE.equals 判空，不再拆箱（此前 fixtures 必须显式 set）。
        PullRequest.Builder b = PullRequest.builder().number(number).state(state)
                .draft(false);
        if (merged != null) {
            b.merged(merged);
        }
        return b.build();
    }

    private static NoLoad attached(PullRequest pullRequest, Issue issueArg) {
        NoLoad f = new NoLoad();
        f.setArguments(PullRequestConversationFragment.newInstance(
                pullRequest, issueArg, false, null).getArguments());
        FragmentTestHelper.attachTo(TestBaseActivity.class, f);
        return f;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Field findField(Class<?> cls, String name) throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object invokePrivate(Object target, String name,
            Class<?>[] types, Object[] args) {
        try {
            Method m = findMethod(target.getClass(), name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>[] types)
            throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    @Test
    public void newInstance_withBaseRepo_usesBaseOwnerAndName() {
        Repository repo = Repository.builder()
                .owner(User.builder().login("octocat").build()).name("hello").build();
        PullRequestMarker marker = mock(PullRequestMarker.class);
        when(marker.repo()).thenReturn(repo);
        PullRequest pull = mock(PullRequest.class);
        when(pull.base()).thenReturn(marker);
        PullRequestConversationFragment f = PullRequestConversationFragment.newInstance(
                pull, issue(1, IssueState.Open), false, null);
        assertEquals("octocat", f.getArguments().getString("owner"));
        assertEquals("hello", f.getArguments().getString("repo"));
        assertNotNull(f.getArguments().getParcelable("pr"));
    }

    @Test
    public void newInstance_withoutBaseRepo_fallsBackToIssueUrl() {
        PullRequest pull = mock(PullRequest.class);
        when(pull.base()).thenReturn(null);
        PullRequestConversationFragment f = PullRequestConversationFragment.newInstance(
                pull, issue(1, IssueState.Open), false, null);
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
    }

    @Test
    public void getCommentEditorHintResId_isPullRequestCommentHint() {
        assertEquals(R.string.pull_request_comment_hint,
                new PullRequestConversationFragment().getCommentEditorHintResId());
    }

    @Test
    public void onCreateDataSingle_returnsNonNullWithoutNetwork() {
        PullRequestConversationFragment bare = new PullRequestConversationFragment();
        setField(bare, "mIssue", issue(1, IssueState.Open));
        setField(bare, "mRepoOwner", "octocat");
        setField(bare, "mRepoName", "hello");
        setField(bare, "mPullRequest", pr(1, IssueState.Open, false));
        assertNotNull(bare.onCreateDataSingle(false));
        assertNotNull(bare.onCreateDataSingle(true));
    }

    @Test
    public void doDeleteComment_reviewComment_returnsNonNullSingle() {
        PullRequestConversationFragment bare = new PullRequestConversationFragment();
        setField(bare, "mRepoOwner", "octocat");
        setField(bare, "mRepoName", "hello");
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(5L);
        assertNotNull(bare.doDeleteComment(comment));
    }

    @Test
    public void doDeleteComment_issueComment_returnsNonNullSingle() {
        PullRequestConversationFragment bare = new PullRequestConversationFragment();
        setField(bare, "mRepoOwner", "octocat");
        setField(bare, "mRepoName", "hello");
        assertNotNull(bare.doDeleteComment(
                GitHubComment.builder().id(6L).build()));
    }

    @Test
    public void updateState_closedPr_updatesIssueAndPrState() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        f.updateState(pr(1, IssueState.Closed, false));
        Issue updatedIssue = (Issue) getField(f, "mIssue");
        PullRequest updatedPr = (PullRequest) getField(f, "mPullRequest");
        assertEquals(IssueState.Closed, updatedIssue.state());
        assertEquals(IssueState.Closed, updatedPr.state());
    }

    private static Object getField(Object target, String name) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---- compareUpdatedAtDesc (private static) ----

    private static int compareDates(Date lhs, Date rhs) {
        return (int) invokePrivate(new PullRequestConversationFragment(),
                "compareUpdatedAtDesc",
                new Class<?>[] { Date.class, Date.class }, new Object[] { lhs, rhs });
    }

    @Test
    public void compareUpdatedAtDesc_bothNull_returnsZero() {
        assertEquals(0, compareDates(null, null));
    }

    @Test
    public void compareUpdatedAtDesc_lhsNull_returnsOne() {
        assertEquals(1, compareDates(null, new Date(1000)));
    }

    @Test
    public void compareUpdatedAtDesc_rhsNull_returnsMinusOne() {
        assertEquals(-1, compareDates(new Date(1000), null));
    }

    @Test
    public void compareUpdatedAtDesc_laterFirst_sortsDesc() {
        // Newer dates sort first: compare(newer, older) < 0.
        assertTrue(compareDates(new Date(2000), new Date(1000)) < 0);
        assertTrue(compareDates(new Date(1000), new Date(2000)) > 0);
        assertEquals(0, compareDates(new Date(1000), new Date(1000)));
    }

    // ---- STATUS_AND_CONTEXT_COMPARATOR (private static field) ----

    @SuppressWarnings("unchecked")
    private static Comparator<Status> statusComparator() {
        try {
            Field f = PullRequestConversationFragment.class
                    .getDeclaredField("STATUS_AND_CONTEXT_COMPARATOR");
            f.setAccessible(true);
            return (Comparator<Status>) f.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Status status(StatusState state, String context) {
        Status s = mock(Status.class);
        when(s.state()).thenReturn(state);
        when(s.context()).thenReturn(context);
        return s;
    }

    @Test
    public void statusComparator_successBeforeError() {
        Comparator<Status> c = statusComparator();
        assertTrue(c.compare(status(StatusState.Success, "a"),
                status(StatusState.Error, "a")) > 0);
        assertTrue(c.compare(status(StatusState.Error, "a"),
                status(StatusState.Success, "a")) < 0);
    }

    @Test
    public void statusComparator_errorAndFailureSameSeverity_fallsToContext() {
        Comparator<Status> c = statusComparator();
        assertTrue(c.compare(status(StatusState.Error, "a"),
                status(StatusState.Failure, "b")) < 0);
        assertEquals(0, c.compare(status(StatusState.Error, "a"),
                status(StatusState.Failure, "a")));
    }

    @Test
    public void statusComparator_pendingAndExpectedDefaultSeverity() {
        Comparator<Status> c = statusComparator();
        // Pending/Expected/null state all map to severity 1.
        assertEquals(0, c.compare(status(StatusState.Pending, "a"),
                status(StatusState.Expected, "a")));
        assertEquals(0, c.compare(status(null, "a"),
                status(StatusState.Pending, "a")));
    }

    @Test
    public void statusComparator_nullStatusSeverityOne() {
        Comparator<Status> c = statusComparator();
        assertTrue(c.compare(null, status(StatusState.Success, "a")) < 0);
        assertTrue(c.compare(status(StatusState.Success, "a"), null) > 0);
    }

    @Test
    public void statusComparator_nullContextsSortLast() {
        Comparator<Status> c = statusComparator();
        assertEquals(0, c.compare(status(StatusState.Success, null),
                status(StatusState.Success, null)));
        assertTrue(c.compare(status(StatusState.Success, null),
                status(StatusState.Success, "a")) > 0);
        assertTrue(c.compare(status(StatusState.Success, "a"),
                status(StatusState.Success, null)) < 0);
    }

    // ---- removeRedundantClosedEvent (private) ----

    private static IssueEvent eventOf(IssueEventType type) {
        IssueEvent e = mock(IssueEvent.class);
        when(e.event()).thenReturn(type);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static List<IssueEvent> removeRedundant(
            PullRequestConversationFragment f, List<IssueEvent> events) {
        return (List<IssueEvent>) invokePrivate(f, "removeRedundantClosedEvent",
                new Class<?>[] { List.class }, new Object[] { events });
    }

    @Test
    public void removeRedundantClosedEvent_removesClosedAfterMerged() {
        PullRequestConversationFragment f = new PullRequestConversationFragment();
        List<IssueEvent> events = new ArrayList<>(List.of(
                eventOf(IssueEventType.Commented),
                eventOf(IssueEventType.Merged),
                eventOf(IssueEventType.Closed),
                eventOf(IssueEventType.Commented)));
        List<IssueEvent> result = removeRedundant(f, events);
        assertEquals(3, result.size());
        assertEquals(IssueEventType.Commented, result.get(0).event());
        assertEquals(IssueEventType.Merged, result.get(1).event());
        assertEquals(IssueEventType.Commented, result.get(2).event());
    }

    @Test
    public void removeRedundantClosedEvent_closedBeforeMergedKept() {
        PullRequestConversationFragment f = new PullRequestConversationFragment();
        List<IssueEvent> events = new ArrayList<>(List.of(
                eventOf(IssueEventType.Closed),
                eventOf(IssueEventType.Reopened),
                eventOf(IssueEventType.Merged)));
        List<IssueEvent> result = removeRedundant(f, events);
        assertEquals(3, result.size());
    }

    @Test
    public void removeRedundantClosedEvent_noMerged_unchanged() {
        PullRequestConversationFragment f = new PullRequestConversationFragment();
        List<IssueEvent> events = new ArrayList<>(List.of(
                eventOf(IssueEventType.Closed),
                eventOf(IssueEventType.Commented)));
        assertEquals(2, removeRedundant(f, events).size());
    }

    @Test
    public void removeRedundantClosedEvent_emptyList_returnsEmpty() {
        PullRequestConversationFragment f = new PullRequestConversationFragment();
        assertEquals(0, removeRedundant(f, new ArrayList<>()).size());
    }

    @Test
    public void assignHighlightColor_mergedClosedDraftOpen_doNotCrash() {
        NoLoad f = attached(pr(1, IssueState.Open, true), issue(1, IssueState.Open));
        f.assignHighlightColor();
        setField(f, "mPullRequest", pr(1, IssueState.Closed, false));
        f.assignHighlightColor();
        PullRequest draft = PullRequest.builder().number(1).state(IssueState.Open)
                .merged(false).draft(true).build();
        setField(f, "mPullRequest", draft);
        f.assignHighlightColor();
        setField(f, "mPullRequest", pr(1, IssueState.Open, false));
        f.assignHighlightColor();
    }

    @Test
    public void assignHighlightColor_nullMergedAndDraft_doNotCrash() {
        // T-112：merged()/draft() 为 null 时不抛，按 open 配色走。
        PullRequest nulls = PullRequest.builder().number(1).state(IssueState.Open).build();
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        setField(f, "mPullRequest", nulls);
        f.assignHighlightColor();
    }

    // ---- batch 2 helpers ----

    @SuppressWarnings("unchecked")
    private static <T> Page<T> pageOf(List<T> items) {
        Page<T> page = mock(Page.class);
        when(page.items()).thenReturn(items);
        when(page.next()).thenReturn(null);
        return page;
    }

    private static Review review(long id, ReviewState state, String body, Date submittedAt) {
        Review r = mock(Review.class);
        when(r.id()).thenReturn(id);
        when(r.state()).thenReturn(state);
        when(r.body()).thenReturn(body);
        when(r.submittedAt()).thenReturn(submittedAt);
        return r;
    }

    private static ReviewComment reviewComment(long id, Long reviewId) {
        ReviewComment rc = mock(ReviewComment.class);
        when(rc.id()).thenReturn(id);
        when(rc.pullRequestReviewId()).thenReturn(reviewId);
        when(rc.path()).thenReturn("a.java");
        when(rc.originalCommitId()).thenReturn("abc");
        when(rc.originalPosition()).thenReturn(3);
        when(rc.createdAt()).thenReturn(new Date(1700000000000L));
        when(rc.bodyHtml()).thenReturn("<p>c</p>");
        when(rc.body()).thenReturn("c");
        return rc;
    }

    private static IssueEvent eventOf(IssueEventType type, Date createdAt) {
        IssueEvent e = mock(IssueEvent.class);
        when(e.event()).thenReturn(type);
        when(e.createdAt()).thenReturn(createdAt);
        return e;
    }

    private static IssueEvent commentedEvent(Date createdAt) {
        IssueEvent e = eventOf(IssueEventType.Commented, createdAt);
        when(e.toComment()).thenReturn(GitHubComment.builder().id(1L)
                .createdAt(createdAt).bodyHtml("<p>hi</p>").build());
        return e;
    }

    private static Repository repo(String ownerLogin, String name) {
        return Repository.builder()
                .owner(User.builder().login(ownerLogin).build()).name(name).build();
    }

    private static PullRequestMarker marker(String ref, String sha, Repository repo) {
        PullRequestMarker m = mock(PullRequestMarker.class);
        when(m.ref()).thenReturn(ref);
        when(m.sha()).thenReturn(sha);
        when(m.label()).thenReturn(null);
        when(m.repo()).thenReturn(repo);
        return m;
    }

    private static PullRequest prWithHead(IssueState state, PullRequestMarker head) {
        return PullRequest.builder().number(1).state(state).merged(false).draft(false)
                .head(head).build();
    }

    private static PullRequest mockPr(IssueState state, boolean merged,
            PullRequestMarker head, PullRequestMarker base) {
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(1);
        when(pr.state()).thenReturn(state);
        when(pr.merged()).thenReturn(merged);
        when(pr.draft()).thenReturn(false);
        when(pr.head()).thenReturn(head);
        when(pr.base()).thenReturn(base);
        return pr;
    }

    private static PullRequestConversationFragment bare() {
        PullRequestConversationFragment f = new PullRequestConversationFragment();
        setField(f, "mIssue", issue(1, IssueState.Open));
        setField(f, "mRepoOwner", "octocat");
        setField(f, "mRepoName", "hello");
        setField(f, "mPullRequest", pr(1, IssueState.Open, false));
        return f;
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static void waitFor(BooleanSupplier cond, String what) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            idle();
            if (cond.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        idle();
        if (!cond.getAsBoolean()) {
            throw new AssertionError("timeout waiting for " + what);
        }
    }

    private static MenuBuilder menuFor(NoLoad f) {
        MenuBuilder menu = new MenuBuilder(f.getActivity());
        f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
        return menu;
    }

    private static CommitStatusBox statusBox(NoLoad f) {
        View header = (View) getField(f, "mListHeaderView");
        return header.findViewById(R.id.commit_status_box);
    }

    private static int statusRowCount(CommitStatusBox box) {
        try {
            Field c = CommitStatusBox.class.getDeclaredField("mStatusContainer");
            c.setAccessible(true);
            return ((android.view.ViewGroup) c.get(box)).getChildCount();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void stubDataServices(MockedStatic<ServiceFactory> mocked,
            IssueTimelineService timeline, PullRequestReviewService reviews,
            PullRequestReviewCommentService comments, boolean bypass) {
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                IssueTimelineService.class, bypass)).thenReturn(timeline);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                PullRequestReviewService.class, bypass)).thenReturn(reviews);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                PullRequestReviewCommentService.class, bypass)).thenReturn(comments);
    }

    private static IssueTimelineService timelineService(List<IssueEvent> events) {
        Page<IssueEvent> page = pageOf(events);
        IssueTimelineService s = mock(IssueTimelineService.class);
        when(s.getTimeline(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        return s;
    }

    private static PullRequestReviewService reviewService(List<Review> reviews,
            List<ReviewComment> pendingComments) {
        Page<Review> reviewsPage = pageOf(reviews);
        Page<ReviewComment> pendingPage = pageOf(pendingComments);
        PullRequestReviewService s = mock(PullRequestReviewService.class);
        when(s.getReviews(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(reviewsPage)));
        when(s.getReviewComments(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(pendingPage)));
        return s;
    }

    private static PullRequestReviewCommentService commentService(List<ReviewComment> comments) {
        Page<ReviewComment> page = pageOf(comments);
        PullRequestReviewCommentService s = mock(PullRequestReviewCommentService.class);
        when(s.getPullRequestComments(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        return s;
    }

    // ---- onCreateOptionsMenu / onOptionsItemSelected ----

    @Test
    public void onCreateOptionsMenu_openPrNoHead_removesDeleteBranch() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        assertNull(menuFor(f).findItem(R.id.delete_branch));
    }

    @Test
    public void onCreateOptionsMenu_closedPrHeadNotLoaded_deleteBranchHiddenRestoreTitle() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        setField(f, "mPullRequest", mockPr(IssueState.Closed, false,
                marker("feature", "sha1", repo("octocat", "hello")), null));
        setField(f, "mHasLoadedHeadReference", false);
        setField(f, "mHeadReference", null);
        MenuBuilder menu = menuFor(f);
        MenuItem item = menu.findItem(R.id.delete_branch);
        assertNotNull(item);
        assertFalse(item.isVisible());
        assertEquals(f.getString(R.string.restore_branch), item.getTitle().toString());
    }

    @Test
    public void onCreateOptionsMenu_closedPrHeadLoadedRefPresent_deleteBranchVisible() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        setField(f, "mPullRequest", mockPr(IssueState.Closed, false,
                marker("feature", "sha1", repo("octocat", "hello")), null));
        setField(f, "mHasLoadedHeadReference", true);
        setField(f, "mHeadReference", mock(GitReference.class));
        MenuItem item = menuFor(f).findItem(R.id.delete_branch);
        assertNotNull(item);
        assertTrue(item.isVisible());
    }

    @Test
    public void onCreateOptionsMenu_nullPr_removesDeleteBranch() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        setField(f, "mPullRequest", null);
        assertNull(menuFor(f).findItem(R.id.delete_branch));
    }

    @Test
    public void onOptionsItemSelected_deleteBranch_showsConfirmDialog() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        setField(f, "mPullRequest", mockPr(IssueState.Closed, false,
                marker("feature", "sha1", repo("octocat", "hello")), null));
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.delete_branch);
        f.onOptionsItemSelected(item);
        f.getParentFragmentManager().executePendingTransactions();
        assertNotNull(f.getParentFragmentManager().findFragmentByTag("prbranchconfirm"));
    }

    @Test
    public void onOptionsItemSelected_unknownItem_fallsThroughToSuper() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(0x7f999999);
        assertFalse(f.onOptionsItemSelected(item));
    }

    // ---- onRefresh / bindSpecialViews / fillStatus ----

    @Test
    public void onRefresh_resetsHeadReferenceAndHidesStatusBox() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        setField(f, "mHeadReference", mock(GitReference.class));
        setField(f, "mHasLoadedHeadReference", true);
        f.onRefresh();
        assertNull(getField(f, "mHeadReference"));
        assertEquals(false, getField(f, "mHasLoadedHeadReference"));
        assertEquals(View.GONE, statusBox(f).getVisibility());
    }

    @Test
    public void bindSpecialViews_withHeadAndBase_showsBranchContainer() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        setField(f, "mPullRequest", mockPr(IssueState.Open, false,
                marker("feature", "sha1", repo("octocat", "hello")),
                marker("main", "sha0", repo("octocat", "hello"))));
        invokePrivate(f, "bindSpecialViews", new Class<?>[]{View.class},
                new Object[]{getField(f, "mListHeaderView")});
        View container = ((View) getField(f, "mListHeaderView"))
                .findViewById(R.id.branch_container);
        assertEquals(View.VISIBLE, container.getVisibility());
    }

    @Test
    public void bindSpecialViews_nullMarkers_showsContainerWithoutCrash() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        setField(f, "mPullRequest", mockPr(IssueState.Open, false, null, null));
        invokePrivate(f, "bindSpecialViews", new Class<?>[]{View.class},
                new Object[]{getField(f, "mListHeaderView")});
        View container = ((View) getField(f, "mListHeaderView"))
                .findViewById(R.id.branch_container);
        assertEquals(View.VISIBLE, container.getVisibility());
    }

    @Test
    public void fillStatus_emptyUnknownMergeable_hidesBox() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        invokePrivate(f, "fillStatus", new Class<?>[]{List.class},
                new Object[]{new ArrayList<StatusWrapper>()});
        assertEquals(View.GONE, statusBox(f).getVisibility());
    }

    @Test
    public void fillStatus_withStatus_showsBoxAndRow() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        Status s = mock(Status.class);
        when(s.context()).thenReturn("ci");
        when(s.description()).thenReturn("ok");
        when(s.state()).thenReturn(StatusState.Success);
        List<StatusWrapper> wrappers = new ArrayList<>();
        wrappers.add(new StatusWrapper(s));
        invokePrivate(f, "fillStatus", new Class<?>[]{List.class},
                new Object[]{wrappers});
        assertEquals(1, statusRowCount(statusBox(f)));
    }

    // ---- onCreateDataSingle data flow ----

    @Test
    public void onCreateDataSingle_fullFlow_returnsSortedItems() {
        PullRequestConversationFragment f = bare();
        List<IssueEvent> events = List.of(
                commentedEvent(new Date(1000)),
                eventOf(IssueEventType.Merged, new Date(2000)),
                eventOf(IssueEventType.Closed, new Date(3000)),
                eventOf(IssueEventType.Mentioned, new Date(400)),
                eventOf(IssueEventType.Labeled, new Date(4000)));
        List<Review> reviews = List.of(
                review(10L, ReviewState.Approved, "LGTM", new Date(1500)),
                review(11L, ReviewState.Commented, "", new Date(2500)),
                review(12L, ReviewState.Pending, null, new Date(3500)));
        ReviewComment attachedComment = reviewComment(21L, 10L);
        ReviewComment orphan = reviewComment(22L, null);
        ReviewComment pendingComment = reviewComment(23L, null);
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(events),
                    reviewService(reviews, List.of(pendingComment)),
                    commentService(List.of(attachedComment, orphan)), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(6, items.size());
            assertTrue(items.get(0) instanceof TimelineItem.TimelineComment);
            assertTrue(items.get(1) instanceof TimelineItem.TimelineReview);
            assertEquals(1, ((TimelineItem.TimelineReview) items.get(1))
                    .getDiffHunks().size());
            assertTrue(items.get(2) instanceof TimelineItem.TimelineEvent);
            assertTrue(items.get(3) instanceof TimelineItem.TimelineReview);
            assertEquals(1, ((TimelineItem.TimelineReview) items.get(3))
                    .getDiffHunks().size());
            assertTrue(items.get(4) instanceof TimelineItem.TimelineEvent);
            assertTrue(items.get(5) instanceof TimelineItem.TimelineComment);
        }
    }

    @Test
    public void onCreateDataSingle_pendingReviewLoadsItsComments() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(12L, ReviewState.Pending, null, new Date(3500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of(reviewComment(23L, null))),
                    commentService(List.of()), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
            assertTrue(items.get(0) instanceof TimelineItem.TimelineReview);
            assertEquals(1, ((TimelineItem.TimelineReview) items.get(0))
                    .getDiffHunks().size());
        }
    }

    @Test
    public void onCreateDataSingle_commentedEmptyReviewDropped() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(11L, ReviewState.Commented, "", new Date(2500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of()),
                    commentService(List.of()), false);
            assertEquals(0, f.onCreateDataSingle(false).blockingGet().size());
        }
    }

    @Test
    public void onCreateDataSingle_reviewCommentWithoutReviewBecomesTimelineComment() {
        PullRequestConversationFragment f = bare();
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(List.of(), List.of()),
                    commentService(List.of(reviewComment(22L, null))), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
            assertTrue(items.get(0) instanceof TimelineItem.TimelineComment);
        }
    }

    @Test
    public void onCreateDataSingle_uninterestingEventFiltered() {
        PullRequestConversationFragment f = bare();
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked,
                    timelineService(List.of(eventOf(IssueEventType.Mentioned, new Date(400)))),
                    reviewService(List.of(), List.of()),
                    commentService(List.of()), false);
            assertEquals(0, f.onCreateDataSingle(false).blockingGet().size());
        }
    }

    @Test
    public void onCreateDataSingle_reviewCommentAttachesToReviewByHunk() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(10L, ReviewState.Approved, "LGTM", new Date(1500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of()),
                    commentService(List.of(reviewComment(21L, 10L))), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
            assertEquals(1, ((TimelineItem.TimelineReview) items.get(0))
                    .getDiffHunks().size());
        }
    }

    @Test
    public void onCreateDataSingle_bypassCache_usesBypassFlag() {
        PullRequestConversationFragment f = bare();
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(List.of(), List.of()),
                    commentService(List.of()), true);
            assertEquals(0, f.onCreateDataSingle(true).blockingGet().size());
        }
    }

    // ---- editComment / doDeleteComment / onConfirmed(base) ----

    @Test
    public void editComment_reviewComment_launchesEditPullRequestComment() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(5L);
        when(comment.body()).thenReturn("b");
        f.editComment(comment);
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(f.getActivity()).getNextStartedActivityForResult();
        assertNotNull(started);
        assertEquals(EditPullRequestCommentActivity.class.getName(),
                started.intent.getComponent().getClassName());
    }

    @Test
    public void editComment_issueComment_launchesEditIssueComment() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        f.editComment(GitHubComment.builder().id(6L).body("b").build());
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(f.getActivity()).getNextStartedActivityForResult();
        assertNotNull(started);
        assertEquals(EditIssueCommentActivity.class.getName(),
                started.intent.getComponent().getClassName());
    }

    @Test
    public void doDeleteComment_reviewComment_deletesViaService() {
        PullRequestConversationFragment f = bare();
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.deleteComment(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(
                    PullRequestReviewCommentService.class, false)).thenReturn(svc);
            ReviewComment comment = mock(ReviewComment.class);
            when(comment.id()).thenReturn(5L);
            assertTrue(f.doDeleteComment(comment).blockingGet().isSuccessful());
            verify(svc).deleteComment("octocat", "hello", 5L);
        }
    }

    @Test
    public void doDeleteComment_issueComment_deletesViaService() {
        PullRequestConversationFragment f = bare();
        IssueCommentService svc = mock(IssueCommentService.class);
        when(svc.deleteIssueComment(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(
                    IssueCommentService.class, false)).thenReturn(svc);
            assertTrue(f.doDeleteComment(
                    GitHubComment.builder().id(6L).build()).blockingGet().isSuccessful());
            verify(svc).deleteIssueComment("octocat", "hello", 6L);
        }
    }

    @Test
    public void onConfirmed_otherTag_deletesCommentViaBase() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        IssueCommentService svc = mock(IssueCommentService.class);
        when(svc.deleteIssueComment(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(
                    IssueCommentService.class, false)).thenReturn(svc);
            f.onConfirmed("other-tag", GitHubComment.builder().id(7L).build());
            verify(svc, Mockito.timeout(3000)).deleteIssueComment("o", "r", 7L);
        }
    }

    // ---- branch confirm dialog / restore / delete ----

    @Test
    public void showDeleteRestoreBranchConfirmDialog_restore_showsDialog() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        invokePrivate(f, "showDeleteRestoreBranchConfirmDialog",
                new Class<?>[]{boolean.class}, new Object[]{true});
        f.getParentFragmentManager().executePendingTransactions();
        assertNotNull(f.getParentFragmentManager().findFragmentByTag("prbranchconfirm"));
    }

    @Test
    public void showDeleteRestoreBranchConfirmDialog_delete_showsDialog() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        invokePrivate(f, "showDeleteRestoreBranchConfirmDialog",
                new Class<?>[]{boolean.class}, new Object[]{false});
        f.getParentFragmentManager().executePendingTransactions();
        assertNotNull(f.getParentFragmentManager().findFragmentByTag("prbranchconfirm"));
    }

    @Test
    public void onConfirmed_restoreWithNullHead_doesNothing() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        Bundle data = new Bundle();
        data.putBoolean("restore", true);
        f.onConfirmed("prbranchconfirm", data);
        assertNull(getField(f, "mHeadReference"));
    }

    @Test
    public void onConfirmed_restoreSuccess_setsHeadReference() {
        GitService gitService = mock(GitService.class);
        GitReference getRef = mock(GitReference.class);
        GitReference createdRef = mock(GitReference.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(getRef)));
        when(gitService.createGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any(CreateGitReference.class)))
                .thenReturn(Single.just(Response.success(createdRef)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            waitFor(() -> getRef.equals(getField(f, "mHeadReference")), "head ref load");
            Bundle data = new Bundle();
            data.putBoolean("restore", true);
            f.onConfirmed("prbranchconfirm", data);
            waitFor(() -> createdRef.equals(getField(f, "mHeadReference")),
                    "restore sets head ref");
            verify(gitService).createGitReference(ArgumentMatchers.eq("octocat"),
                    ArgumentMatchers.eq("hello"),
                    ArgumentMatchers.any(CreateGitReference.class));
        }
    }

    @Test
    public void onConfirmed_deleteSuccess_clearsHeadReference() {
        GitService gitService = mock(GitService.class);
        GitReference getRef = mock(GitReference.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(getRef)));
        when(gitService.deleteGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            waitFor(() -> getRef.equals(getField(f, "mHeadReference")), "head ref load");
            Bundle data = new Bundle();
            data.putBoolean("restore", false);
            f.onConfirmed("prbranchconfirm", data);
            waitFor(() -> getField(f, "mHeadReference") == null, "delete clears head ref");
            verify(gitService).deleteGitReference(ArgumentMatchers.eq("octocat"),
                    ArgumentMatchers.eq("hello"), ArgumentMatchers.eq("heads/feature"));
        }
    }

    @Test
    public void onConfirmed_restoreWithNullHeadRepo_doesNothing() {
        GitService gitService = mock(GitService.class);
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", null);
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            Bundle data = new Bundle();
            data.putBoolean("restore", true);
            f.onConfirmed("prbranchconfirm", data);
            idle();
            verify(gitService, Mockito.never()).createGitReference(
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                    ArgumentMatchers.any(CreateGitReference.class));
        }
    }

    @Test
    public void onConfirmed_deleteWithNullHeadRepo_doesNothing() {
        GitService gitService = mock(GitService.class);
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", null);
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            Bundle data = new Bundle();
            data.putBoolean("restore", false);
            f.onConfirmed("prbranchconfirm", data);
            idle();
            verify(gitService, Mockito.never()).deleteGitReference(
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyString());
        }
    }

    // ---- loadHeadReference ----

    @Test
    public void loadHeadReference_nullHead_returnsWithoutLoad() {
        PullRequestConversationFragment f = bare();
        invokePrivate(f, "loadHeadReference", new Class<?>[]{boolean.class},
                new Object[]{false});
        assertEquals(false, getField(f, "mHasLoadedHeadReference"));
    }

    @Test
    public void loadHeadReference_repoNull_setsLoadedEmpty() {
        PullRequestMarker head = marker("feature", "sha1", null);
        NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
        assertEquals(true, getField(f, "mHasLoadedHeadReference"));
        assertNull(getField(f, "mHeadReference"));
    }

    @Test
    public void loadHeadReference_success_setsReference() {
        GitService gitService = mock(GitService.class);
        GitReference ref = mock(GitReference.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(ref)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            waitFor(() -> ref.equals(getField(f, "mHeadReference")), "head ref load");
            assertEquals(true, getField(f, "mHasLoadedHeadReference"));
        }
    }

    @Test
    public void loadHeadReference_notFound_setsLoadedEmpty() {
        GitService gitService = mock(GitService.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.error(404,
                        ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            waitFor(() -> (Boolean) getField(f, "mHasLoadedHeadReference"),
                    "head ref load completes");
            assertNull(getField(f, "mHeadReference"));
        }
    }

    // ---- loadCommitStatusesIfOpen ----

    @Test
    public void loadCommitStatusesIfOpen_closedPr_returnsEarly() {
        PullRequestConversationFragment f = bare();
        setField(f, "mPullRequest", pr(1, IssueState.Closed, false));
        invokePrivate(f, "loadCommitStatusesIfOpen", new Class<?>[]{boolean.class},
                new Object[]{false});
        // 无 header、无网络调用，直接返回。
    }

    @Test
    public void loadCommitStatusesIfOpen_openPrNoHead_hidesBox() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        assertEquals(View.GONE, statusBox(f).getVisibility());
    }

    private static Status status(StatusState state, String context, Date updatedAt) {
        Status s = mock(Status.class);
        when(s.state()).thenReturn(state);
        when(s.context()).thenReturn(context);
        when(s.description()).thenReturn("d");
        when(s.updatedAt()).thenReturn(updatedAt);
        return s;
    }

    private static CheckRun checkRun(String name, Date startedAt, Date completedAt,
            com.meisolsson.githubsdk.model.CheckRunState state, String title) {
        CheckRun run = mock(CheckRun.class);
        when(run.name()).thenReturn(name);
        when(run.startedAt()).thenReturn(startedAt);
        when(run.completedAt()).thenReturn(completedAt);
        when(run.state()).thenReturn(state);
        when(run.conclusion()).thenReturn(null);
        when(run.detailsUrl()).thenReturn(null);
        CheckRun.Output output = mock(CheckRun.Output.class);
        when(output.title()).thenReturn(title);
        when(run.output()).thenReturn(output);
        return run;
    }

    @Test
    public void loadCommitStatusesIfOpen_openPrWithSha_dedupsAndShowsBox() {
        RepositoryStatusService statusService = mock(RepositoryStatusService.class);
        List<Status> statuses = List.of(
                status(StatusState.Success, "ci", new Date(3000)),
                status(StatusState.Failure, "ci", new Date(1000)),
                status(StatusState.Pending, null, new Date(2000)));
        Page<Status> statusesPage = pageOf(statuses);
        when(statusService.getStatuses(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.eq("abc123"), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(statusesPage)));
        ChecksService checksService = mock(ChecksService.class);
        CheckRunsResponse runsResponse = mock(CheckRunsResponse.class);
        List<CheckRun> runs = List.of(
                checkRun("build", new Date(2000), new Date(2500),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "All good"),
                checkRun("build", new Date(1000), new Date(1500), null, null),
                checkRun("lint", new Date(5000), new Date(5500),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "Lint ok"),
                checkRun("lint", null, new Date(6000),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "Lint ok"),
                checkRun("x", null, new Date(120),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "X old"),
                checkRun("x", new Date(100), new Date(150),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "X new"),
                checkRun("y", new Date(100), new Date(150),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "Y old"),
                checkRun("y", new Date(200), new Date(250),
                        com.meisolsson.githubsdk.model.CheckRunState.Completed, "Y new"));
        when(runsResponse.checkRuns()).thenReturn(runs);
        when(checksService.getCheckRunsForRef(ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(), ArgumentMatchers.eq("abc123")))
                .thenReturn(Single.just(Response.success(runsResponse)));
        GitService gitService = mock(GitService.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(mock(GitReference.class))));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    RepositoryStatusService.class, false)).thenReturn(statusService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    RepositoryStatusService.class, true)).thenReturn(statusService);
            mocked.when(() -> ServiceFactory.get(ChecksService.class, false))
                    .thenReturn(checksService);
            mocked.when(() -> ServiceFactory.get(ChecksService.class, true))
                    .thenReturn(checksService);
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "abc123", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Open, head), issue(1, IssueState.Open));
            waitFor(() -> statusRowCount(statusBox(f)) == 6, "status rows render");
            assertEquals(View.VISIBLE, statusBox(f).getVisibility());
        }
    }

    // ---- misc ----

    @Test
    public void updateState_mergedPr_updatesMergedFlag() {
        NoLoad f = attached(pr(1, IssueState.Open, false), issue(1, IssueState.Open));
        f.updateState(pr(1, IssueState.Open, true));
        PullRequest updatedPr = (PullRequest) getField(f, "mPullRequest");
        assertTrue(updatedPr.merged());
    }

    @Test
    public void newInstance_baseRepoOwnerNull_fallsBackToIssueUrl() {
        PullRequest pull = mock(PullRequest.class);
        PullRequestMarker baseMarker = mock(PullRequestMarker.class);
        when(baseMarker.repo()).thenReturn(Repository.builder().name("hello").build());
        when(pull.base()).thenReturn(baseMarker);
        PullRequestConversationFragment f = PullRequestConversationFragment.newInstance(
                pull, issue(1, IssueState.Open), false, null);
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
    }

    // ---- residual lines: 161 / 345 / 347 / 429 / 450 ----

    @Test
    public void onOptionsItemSelected_deleteBranchWithHeadRef_showsDeleteDialog() {
        // 161 行：mHeadReference != null 时走删除文案分支。
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        setField(f, "mPullRequest", mockPr(IssueState.Closed, false,
                marker("feature", "sha1", repo("octocat", "hello")), null));
        setField(f, "mHeadReference", mock(GitReference.class));
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.delete_branch);
        f.onOptionsItemSelected(item);
        f.getParentFragmentManager().executePendingTransactions();
        assertNotNull(f.getParentFragmentManager().findFragmentByTag("prbranchconfirm"));
    }

    @Test
    public void editComment_mergedPr_usesMergedHighlight() {
        // 345 行：merged 分支的高亮色。
        NoLoad f = attached(pr(1, IssueState.Open, true), issue(1, IssueState.Open));
        setField(f, "mPullRequest", mockPr(IssueState.Open, true, null, null));
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(5L);
        when(comment.body()).thenReturn("b");
        f.editComment(comment);
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(f.getActivity()).getNextStartedActivityForResult();
        assertNotNull(started);
        assertEquals(EditPullRequestCommentActivity.class.getName(),
                started.intent.getComponent().getClassName());
    }

    @Test
    public void editComment_closedPr_usesClosedHighlight() {
        // 347 行：closed 分支的高亮色。
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        setField(f, "mPullRequest", mockPr(IssueState.Closed, false, null, null));
        f.editComment(GitHubComment.builder().id(6L).body("b").build());
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(f.getActivity()).getNextStartedActivityForResult();
        assertNotNull(started);
        assertEquals(EditIssueCommentActivity.class.getName(),
                started.intent.getComponent().getClassName());
    }

    @Test
    public void onConfirmed_restoreError_keepsHeadReference() {
        // 429 行：恢复分支失败走 error lambda。
        GitService gitService = mock(GitService.class);
        GitReference getRef = mock(GitReference.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(getRef)));
        when(gitService.createGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.any(CreateGitReference.class)))
                .thenReturn(Single.error(new RuntimeException("restore failed")));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            waitFor(() -> getRef.equals(getField(f, "mHeadReference")), "head ref load");
            Bundle data = new Bundle();
            data.putBoolean("restore", true);
            f.onConfirmed("prbranchconfirm", data);
            verify(gitService, Mockito.timeout(3000)).createGitReference(
                    ArgumentMatchers.eq("octocat"), ArgumentMatchers.eq("hello"),
                    ArgumentMatchers.any(CreateGitReference.class));
            // 错误 snackbar 超时消失后 retryWhen 才把错误传回 error 消费者。
            Shadows.shadowOf(Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofSeconds(10));
            idle();
            assertEquals(getRef, getField(f, "mHeadReference"));
        }
    }

    @Test
    public void onConfirmed_deleteError_keepsHeadReference() {
        // 450 行：删除分支失败走 error lambda。
        GitService gitService = mock(GitService.class);
        GitReference getRef = mock(GitReference.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(getRef)));
        when(gitService.deleteGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.error(new RuntimeException("delete failed")));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1", repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            waitFor(() -> getRef.equals(getField(f, "mHeadReference")), "head ref load");
            Bundle data = new Bundle();
            data.putBoolean("restore", false);
            f.onConfirmed("prbranchconfirm", data);
            verify(gitService, Mockito.timeout(3000)).deleteGitReference(
                    ArgumentMatchers.eq("octocat"), ArgumentMatchers.eq("hello"),
                    ArgumentMatchers.eq("heads/feature"));
            // 错误 snackbar 超时消失后 retryWhen 才把错误传回 error 消费者。
            Shadows.shadowOf(Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofSeconds(10));
            idle();
            assertEquals(getRef, getField(f, "mHeadReference"));
        }
    }

    // ---- residual branches ----

    @Test
    public void onRefresh_nullHeader_closedPr_skipsFillStatus() {
        NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
        setField(f, "mListHeaderView", null);
        f.onRefresh();
        assertNull(getField(f, "mHeadReference"));
    }

    @Test
    public void onConfirmed_restoreWithNullOwner_doesNothing() {
        GitService gitService = mock(GitService.class);
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1",
                    Repository.builder().name("hello").build());
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            Bundle data = new Bundle();
            data.putBoolean("restore", true);
            f.onConfirmed("prbranchconfirm", data);
            idle();
            verify(gitService, Mockito.never()).createGitReference(
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                    ArgumentMatchers.any(CreateGitReference.class));
        }
    }

    @Test
    public void onConfirmed_deleteWithNullHead_doesNothing() {
        GitService gitService = mock(GitService.class);
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            NoLoad f = attached(pr(1, IssueState.Closed, false), issue(1, IssueState.Closed));
            Bundle data = new Bundle();
            data.putBoolean("restore", false);
            f.onConfirmed("prbranchconfirm", data);
            idle();
            verify(gitService, Mockito.never()).deleteGitReference(
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyString());
        }
    }

    @Test
    public void onConfirmed_deleteWithNullOwner_doesNothing() {
        GitService gitService = mock(GitService.class);
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", "sha1",
                    Repository.builder().name("hello").build());
            NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
            Bundle data = new Bundle();
            data.putBoolean("restore", false);
            f.onConfirmed("prbranchconfirm", data);
            idle();
            verify(gitService, Mockito.never()).deleteGitReference(
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyString());
        }
    }

    @Test
    public void loadCommitStatusesIfOpen_openPrHeadShaNull_hidesBox() {
        GitService gitService = mock(GitService.class);
        when(gitService.getGitReference(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
                .thenReturn(Single.just(Response.success(mock(GitReference.class))));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(GitService.class, false)).thenReturn(gitService);
            mocked.when(() -> ServiceFactory.get(GitService.class, true)).thenReturn(gitService);
            PullRequestMarker head = marker("feature", null, repo("octocat", "hello"));
            NoLoad f = attached(prWithHead(IssueState.Open, head), issue(1, IssueState.Open));
            assertEquals(View.GONE, statusBox(f).getVisibility());
        }
    }

    @Test
    public void loadHeadReference_repoOwnerNull_setsLoadedEmpty() {
        PullRequestMarker head = marker("feature", "sha1",
                Repository.builder().name("hello").build());
        NoLoad f = attached(prWithHead(IssueState.Closed, head), issue(1, IssueState.Closed));
        assertEquals(true, getField(f, "mHasLoadedHeadReference"));
        assertNull(getField(f, "mHeadReference"));
    }

    @Test
    public void onCreateDataSingle_pendingReviewNullId_filteredOut() {
        PullRequestConversationFragment f = bare();
        Review pending = mock(Review.class);
        when(pending.state()).thenReturn(ReviewState.Pending);
        when(pending.id()).thenReturn(null);
        when(pending.submittedAt()).thenReturn(new Date(3500));
        PullRequestReviewService reviews = reviewService(List.of(pending), List.of());
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviews, commentService(List.of()), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            // 无 id 的 pending 审阅仍是审阅项（不过滤），只是不取它的评论。
            assertEquals(1, items.size());
            assertTrue(items.get(0) instanceof TimelineItem.TimelineReview);
            assertEquals(0, ((TimelineItem.TimelineReview) items.get(0))
                    .getDiffHunks().size());
            verify(reviews, Mockito.never()).getReviewComments(
                    ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
                    ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
                    ArgumentMatchers.anyLong());
        }
    }

    @Test
    public void onCreateDataSingle_pendingReviewEmptyComments_noHunks() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(12L, ReviewState.Pending, null, new Date(3500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of()),
                    commentService(List.of()), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
            assertEquals(0, ((TimelineItem.TimelineReview) items.get(0))
                    .getDiffHunks().size());
        }
    }

    @Test
    public void onCreateDataSingle_reviewCommentUnknownReviewId_skipped() {
        PullRequestConversationFragment f = bare();
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(List.of(), List.of()),
                    commentService(List.of(reviewComment(21L, 999L))), false);
            assertEquals(0, f.onCreateDataSingle(false).blockingGet().size());
        }
    }

    @Test
    public void onCreateDataSingle_twoCommentsSameHunk_shareReviewItem() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(10L, ReviewState.Approved, "LGTM", new Date(1500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of()),
                    commentService(List.of(reviewComment(21L, 10L), reviewComment(24L, 10L))),
                    false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
            assertEquals(1, ((TimelineItem.TimelineReview) items.get(0))
                    .getDiffHunks().size());
        }
    }

    @Test
    public void onCreateDataSingle_commentedReviewWithBody_kept() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(11L, ReviewState.Commented, "text", new Date(2500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of()),
                    commentService(List.of()), false);
            assertEquals(1, f.onCreateDataSingle(false).blockingGet().size());
        }
    }

    @Test
    public void onCreateDataSingle_commentedReviewWithHunks_kept() {
        PullRequestConversationFragment f = bare();
        List<Review> reviews = List.of(
                review(11L, ReviewState.Commented, "", new Date(2500)));
        try (MockedStatic<ServiceFactory> mocked =
                     Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            stubDataServices(mocked, timelineService(List.of()),
                    reviewService(reviews, List.of()),
                    commentService(List.of(reviewComment(21L, 11L))), false);
            List<TimelineItem> items = f.onCreateDataSingle(false).blockingGet();
            assertEquals(1, items.size());
            assertEquals(1, ((TimelineItem.TimelineReview) items.get(0))
                    .getDiffHunks().size());
        }
    }
}
