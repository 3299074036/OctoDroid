package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.Status;
import com.meisolsson.githubsdk.model.StatusState;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.PullRequestMarker;
import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.model.TimelineItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
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
        // draft() is a nullable Boolean; assignHighlightColor unboxes it, so
        // fixtures must always set it (see F1-1: same unboxing pattern as
        // round-4 issue 166, fixed in ReleaseAdapter with Boolean.TRUE).
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
}
