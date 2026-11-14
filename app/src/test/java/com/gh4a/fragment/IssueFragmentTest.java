package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gh4a.ApiRequestException;
import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.model.TimelineItem;
import com.gh4a.model.TimelineItem.TimelineEvent;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueEvent;
import com.meisolsson.githubsdk.model.IssueEventType;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.service.issues.IssueTimelineService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.ResponseBody;

import io.reactivex.Observable;
import io.reactivex.Single;
import retrofit2.Response;

/** IssueFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class IssueFragmentTest {

    /** Minimal concrete BaseActivity: IssueFragmentBase.onViewCreated needs one. */
    public static class TestBaseActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // BaseActivity.onStart throws unless a content view was set.
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    public static class NoLoad extends IssueFragment {
        boolean useRealObservable;

        @Override
        protected List<TimelineItem> onGetInitialData() {
            return null;
        }

        @Override
        protected Observable<List<TimelineItem>> onCreateDataObservable(
                boolean bypassCache) {
            if (useRealObservable) {
                return super.onCreateDataObservable(bypassCache);
            }
            return Observable.never();
        }

        /** Calls the real onCreateDataObservable (bypassing the attach stub). */
        Observable<List<TimelineItem>> realObservable(boolean bypassCache) {
            useRealObservable = true;
            try {
                return onCreateDataObservable(bypassCache);
            } finally {
                useRealObservable = false;
            }
        }

        @Override
        protected Single<List<TimelineItem>> onCreateDataSingle(boolean bypassCache) {
            return Single.never();
        }
    }

    private static Issue issue(Integer number, IssueState state, PullRequest pr) {
        Issue.Builder b = Issue.builder().number(number).state(state)
                .url("https://api.github.com/repos/o/r/issues/1");
        if (pr != null) {
            b.pullRequest(pr);
        }
        return b.build();
    }

    private static NoLoad attached(Issue issueArg) {
        NoLoad f = new NoLoad();
        f.setArguments(IssueFragment.newInstance("octocat", "hello", issueArg,
                false, null).getArguments());
        FragmentTestHelper.attachTo(TestBaseActivity.class, f);
        return f;
    }

    private static void setIssueField(Object target, String name, Object value) {
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

    private static Object getIssueField(Object target, String name) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void newInstance_storesArgs() {
        Issue i = issue(5, IssueState.Open, null);
        IssueFragment f = IssueFragment.newInstance("o", "r", i, true, null);
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
        assertEquals(true, f.getArguments().getBoolean("collaborator"));
        assertNotNull(f.getArguments().getParcelable("issue"));
    }

    @Test
    public void getCommentEditorHintResId_isIssueCommentHint() {
        assertEquals(R.string.issue_comment_hint,
                new IssueFragment().getCommentEditorHintResId());
    }

    @Test
    public void getEmptyTextResId_isZero() {
        assertEquals(0, attached(issue(1, IssueState.Open, null)).getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached(issue(1, IssueState.Open, null)).onCreateAdapter());
    }

    @Test
    public void onCreateDataObservable_returnsNonNullWithoutNetwork() {
        NoLoad f = attached(issue(3, IssueState.Open, null));
        // Call the real method via a fresh bare instance with fields set.
        IssueFragment bare = new IssueFragment();
        setIssueField(bare, "mIssue", issue(3, IssueState.Open, null));
        setIssueField(bare, "mRepoOwner", "octocat");
        setIssueField(bare, "mRepoName", "hello");
        assertNotNull(bare.onCreateDataObservable(false));
        assertNotNull(bare.onCreateDataObservable(true));
    }

    @Test
    public void onCreateDataObservable_nullNumber_usesZero() {
        IssueFragment bare = new IssueFragment();
        setIssueField(bare, "mIssue", issue(null, IssueState.Open, null));
        setIssueField(bare, "mRepoOwner", "octocat");
        setIssueField(bare, "mRepoName", "hello");
        assertNotNull(bare.onCreateDataObservable(false));
    }

    @Test
    public void doDeleteComment_returnsNonNullSingleWithoutNetwork() {
        IssueFragment bare = new IssueFragment();
        setIssueField(bare, "mRepoOwner", "octocat");
        setIssueField(bare, "mRepoName", "hello");
        Single<Response<Void>> single = bare.doDeleteComment(
                GitHubComment.builder().id(9L).build());
        assertNotNull(single);
    }

    @Test
    public void doDeleteComment_nullId_usesZero() {
        IssueFragment bare = new IssueFragment();
        setIssueField(bare, "mRepoOwner", "octocat");
        setIssueField(bare, "mRepoName", "hello");
        assertNotNull(bare.doDeleteComment(GitHubComment.builder().build()));
    }

    @Test
    public void bindSpecialViews_withoutPrDiff_hidesPullLink() {
        NoLoad f = attached(issue(1, IssueState.Open, null));
        LinearLayout header = new LinearLayout(f.getActivity());
        TextView tvPull = new TextView(f.getActivity());
        tvPull.setId(R.id.tv_pull);
        header.addView(tvPull);
        f.bindSpecialViews(header);
        assertEquals(View.GONE, tvPull.getVisibility());
    }

    @Test
    public void bindSpecialViews_prWithoutDiffUrl_hidesPullLink() {
        // pullRequest present but diffUrl null: second operand of the && fails.
        PullRequest pr = PullRequest.builder().build();
        NoLoad f = attached(issue(1, IssueState.Open, pr));
        LinearLayout header = new LinearLayout(f.getActivity());
        TextView tvPull = new TextView(f.getActivity());
        tvPull.setId(R.id.tv_pull);
        header.addView(tvPull);
        f.bindSpecialViews(header);
        assertEquals(View.GONE, tvPull.getVisibility());
    }

    @Test
    public void bindSpecialViews_withPrDiff_showsPullLink() {
        PullRequest pr = PullRequest.builder()
                .diffUrl("https://github.com/o/r/pull/1.diff").build();
        NoLoad f = attached(issue(1, IssueState.Open, pr));
        LinearLayout header = new LinearLayout(f.getActivity());
        TextView tvPull = new TextView(f.getActivity());
        tvPull.setId(R.id.tv_pull);
        header.addView(tvPull);
        f.bindSpecialViews(header);
        assertEquals(View.VISIBLE, tvPull.getVisibility());
        // The click listener is wired: clicking opens the pull request.
        tvPull.performClick();
        android.content.Intent started = Shadows.shadowOf(f.getActivity())
                .getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void assignHighlightColor_closedAndOpen_doNotCrash() {
        NoLoad f = attached(issue(1, IssueState.Closed, null));
        f.assignHighlightColor();
        setIssueField(f, "mIssue", issue(1, IssueState.Open, null));
        f.assignHighlightColor();
    }

    @Test
    public void updateState_updatesIssueState() {
        NoLoad f = attached(issue(1, IssueState.Open, null));
        f.updateState(issue(1, IssueState.Closed, null));
        Issue updated = (Issue) getIssueField(f, "mIssue");
        assertEquals(IssueState.Closed, updated.state());
    }

    @Test
    public void onClick_pullLink_startsPullRequestActivity() {
        PullRequest pr = PullRequest.builder()
                .diffUrl("https://github.com/o/r/pull/1.diff").build();
        NoLoad f = attached(issue(4, IssueState.Open, pr));
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        TextView tvPull = new TextView(activity);
        tvPull.setId(R.id.tv_pull);
        f.onClick(tvPull);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals(4, started.getExtras().getInt("number"));
    }

    @Test
    public void onClick_nonPullView_delegatesToSuperWithoutCrash() {
        NoLoad f = attached(issue(1, IssueState.Open, null));
        // Not tv_pull: super.onClick runs (tv_extra check fails, null tag ->
        // makeIntent returns null, nothing starts).
        android.view.View v = new android.view.View(f.getActivity());
        f.onClick(v);
    }

    @Test
    public void editComment_closedIssue_usesClosedHighlight() {
        NoLoad f = attached(issue(2, IssueState.Closed, null));
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        f.editComment(GitHubComment.builder().id(8L).body("nice").build());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.EditIssueCommentActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals(R.attr.colorIssueClosed,
                started.getExtras().getInt("highlight_color_attr"));
    }

    @Test
    public void editComment_launchesEditActivity() {
        NoLoad f = attached(issue(2, IssueState.Open, null));
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        f.editComment(GitHubComment.builder().id(8L).body("nice").build());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.EditIssueCommentActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onBackPressed_withoutBottomSheet_returnsFalse() {
        assertEquals(false, attached(issue(1, IssueState.Open, null)).onBackPressed());
    }

    // ---- onCreateDataObservable with a mocked IssueTimelineService ----

    @SuppressWarnings("unchecked")
    private static Page<IssueEvent> eventPage(List<IssueEvent> events, Integer next) {
        Page<IssueEvent> p = mock(Page.class);
        when(p.items()).thenReturn(events);
        when(p.next()).thenReturn(next);
        return p;
    }

    private static IssueEvent event(IssueEventType type) {
        IssueEvent e = mock(IssueEvent.class);
        when(e.event()).thenReturn(type);
        return e;
    }

    @Test
    public void realObservable_singlePage_filtersUninterestingEvents() throws Exception {
        NoLoad f = attached(issue(12, IssueState.Open, null));
        IssueEvent closed = event(IssueEventType.Closed);
        IssueEvent subscribed = event(IssueEventType.Subscribed);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueTimelineService service = mock(IssueTimelineService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueTimelineService.class, false)).thenReturn(service);
            Page<IssueEvent> page1 = eventPage(
                    Arrays.asList(closed, subscribed), null);
            when(service.getTimeline("octocat", "hello", 12, 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            io.reactivex.observers.TestObserver<List<TimelineItem>> to =
                    f.realObservable(false).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            // scan() emits the seed first, then one value per page; the
            // accumulator mutates the seed in place, so every emission is
            // the same list instance holding the final cumulative content.
            to.assertComplete().assertValueCount(2);
            assertTrue(to.values().get(0) == to.values().get(1));
            to.assertValueAt(1, items -> items.size() == 1
                    && items.get(0) instanceof TimelineEvent
                    && ((TimelineEvent) items.get(0)).event == closed);
            verify(service).getTimeline("octocat", "hello", 12, 1L);
        }
    }

    @Test
    public void realObservable_twoPages_scanAccumulates() throws Exception {
        NoLoad f = attached(issue(12, IssueState.Open, null));
        IssueEvent closed = event(IssueEventType.Closed);
        IssueEvent reopened = event(IssueEventType.Reopened);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueTimelineService service = mock(IssueTimelineService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueTimelineService.class, false)).thenReturn(service);
            Page<IssueEvent> page1 = eventPage(Arrays.asList(closed), 2);
            Page<IssueEvent> page2 = eventPage(Arrays.asList(reopened), null);
            when(service.getTimeline("octocat", "hello", 12, 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            when(service.getTimeline("octocat", "hello", 12, 2L))
                    .thenReturn(Single.just(Response.success(page2)));
            io.reactivex.observers.TestObserver<List<TimelineItem>> to =
                    f.realObservable(false).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            // scan() emits the seed first, then one value per page; the
            // accumulator mutates the seed in place, so all three emissions
            // alias the same list holding the final cumulative content.
            to.assertComplete().assertValueCount(3);
            assertTrue(to.values().get(0) == to.values().get(1));
            assertTrue(to.values().get(1) == to.values().get(2));
            to.assertValueAt(2, items -> items.size() == 2
                    && ((TimelineEvent) items.get(0)).event == closed
                    && ((TimelineEvent) items.get(1)).event == reopened);
            verify(service).getTimeline("octocat", "hello", 12, 2L);
        }
    }

    @Test
    public void realObservable_pageError_propagates() throws Exception {
        NoLoad f = attached(issue(12, IssueState.Open, null));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueTimelineService service = mock(IssueTimelineService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueTimelineService.class, false)).thenReturn(service);
            when(service.getTimeline("octocat", "hello", 12, 1L))
                    .thenReturn(Single.error(new ApiRequestException(
                            Response.error(500, ResponseBody.create(null, "")))));
            io.reactivex.observers.TestObserver<List<TimelineItem>> to =
                    f.realObservable(false).test();
            assertTrue(to.awaitTerminalEvent(10, TimeUnit.SECONDS));
            to.assertError(ApiRequestException.class);
        }
    }
}
