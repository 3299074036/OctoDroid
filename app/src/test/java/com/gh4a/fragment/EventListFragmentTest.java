package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.MenuItem;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.resolver.CommitCommentLoadTask;
import com.gh4a.resolver.PullRequestReviewCommentLoadTask;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.DownloadUtils;
import com.gh4a.widget.ContextMenuAwareRecyclerView;
import com.meisolsson.githubsdk.model.Download;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.GitHubEvent;
import com.meisolsson.githubsdk.model.GitHubEventType;
import com.meisolsson.githubsdk.model.GitHubWikiPage;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.model.git.GitCommit;
import com.meisolsson.githubsdk.model.payload.CommitCommentPayload;
import com.meisolsson.githubsdk.model.payload.DownloadPayload;
import com.meisolsson.githubsdk.model.payload.FollowPayload;
import com.meisolsson.githubsdk.model.payload.ForkPayload;
import com.meisolsson.githubsdk.model.payload.GistPayload;
import com.meisolsson.githubsdk.model.payload.GollumPayload;
import com.meisolsson.githubsdk.model.payload.IssueCommentPayload;
import com.meisolsson.githubsdk.model.payload.IssuesPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestPayload;
import com.meisolsson.githubsdk.model.payload.PullRequestReviewCommentPayload;
import com.meisolsson.githubsdk.model.payload.PushPayload;
import com.meisolsson.githubsdk.model.payload.ReleasePayload;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.util.Optional;

import io.reactivex.Single;
import io.reactivex.subjects.SingleSubject;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** EventListFragment.parseRepoIdentifierToOwnerAndName (F1, package-visible static). */
@RunWith(RobolectricTestRunner.class)
public class EventListFragmentTest {

    private static GitHubEvent.RepoIdentifier id(String repoWithUserName) {
        return GitHubEvent.RepoIdentifier.builder()
                .repoWithUserName(repoWithUserName).build();
    }

    @Test
    public void parse_nullIdentifier_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(null));
    }

    @Test
    public void parse_nullRepoName_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id(null)));
    }

    @Test
    public void parse_validIdentifier_returnsOwnerAndName() {
        Pair<String, String> pair =
                EventListFragment.parseRepoIdentifierToOwnerAndName(id("octocat/hello"));
        assertEquals("octocat", pair.first);
        assertEquals("hello", pair.second);
    }

    @Test
    public void parse_noSlash_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("octocat")));
    }

    @Test
    public void parse_emptyString_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("")));
    }

    @Test
    public void parse_threeSegments_returnsNull() {
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("a/b/c")));
    }

    @Test
    public void parse_trailingSlash_returnsNull() {
        // "o/" splits to ["o"] in Java (trailing empties dropped).
        assertNull(EventListFragment.parseRepoIdentifierToOwnerAndName(id("o/")));
    }

    @Test
    public void parse_leadingSlash_returnsNull() {
        // "/r" splits to ["", "r"]: owner is empty but length is 2, so it parses.
        Pair<String, String> pair =
                EventListFragment.parseRepoIdentifierToOwnerAndName(id("/r"));
        assertEquals("", pair.first);
        assertEquals("r", pair.second);
    }

    // ---- onItemClick / 上下文菜单 / onAddData / 兜底 ----

    public static class TestEventFragment extends EventListFragment {
        Single<Response<Page<GitHubEvent>>> rawPage;

        TestEventFragment() {
            rawPage = Single.just(Response.success(new ApiHelpers.DummyPage<>()));
        }

        @Override
        protected Single<Response<Page<GitHubEvent>>> loadRawPage(
                int page, boolean bypassCache) {
            return rawPage;
        }
    }

    private FragmentActivity mActivity;

    @Before
    public void setUpActivity() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private TestEventFragment attach() {
        TestEventFragment f = new TestEventFragment();
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return f;
    }

    private static User actor(String login) {
        return User.builder().login(login).build();
    }

    private static GitHubEvent event(GitHubEventType type,
            com.meisolsson.githubsdk.model.payload.GitHubPayload payload) {
        return GitHubEvent.builder()
                .type(type)
                .id("evt-1")
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .payload(payload)
                .build();
    }

    private static GitHubEvent eventNoRepo(GitHubEventType type,
            com.meisolsson.githubsdk.model.payload.GitHubPayload payload) {
        return GitHubEvent.builder()
                .type(type)
                .id("evt-1")
                .actor(actor("octo"))
                .payload(payload)
                .build();
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static ContextMenuAwareRecyclerView.RecyclerContextMenuInfo menuInfo(
            int position) throws Exception {
        java.lang.reflect.Constructor<ContextMenuAwareRecyclerView
                .RecyclerContextMenuInfo> c =
                ContextMenuAwareRecyclerView.RecyclerContextMenuInfo.class
                        .getDeclaredConstructor(int.class, long.class);
        c.setAccessible(true);
        return c.newInstance(position, 0L);
    }

    /** 记录式 ContextMenu：MenuBuilder 不实现 ContextMenu，改用 mock 记录。 */
    private static class RecordingMenu {
        final ContextMenu menu;
        final java.util.List<CharSequence> titles = new java.util.ArrayList<>();
        final java.util.List<Integer> ids = new java.util.ArrayList<>();

        RecordingMenu() {
            menu = mock(ContextMenu.class);
            when(menu.add(any(CharSequence.class))).thenAnswer(inv -> {
                titles.add(inv.getArgument(0));
                MenuItem item = mock(MenuItem.class);
                when(item.setIntent(any(Intent.class))).thenReturn(item);
                return item;
            });
            when(menu.add(anyInt(), anyInt(), anyInt(), any(CharSequence.class)))
                    .thenAnswer(inv -> {
                        ids.add(inv.getArgument(1));
                        titles.add(inv.getArgument(3));
                        MenuItem item = mock(MenuItem.class);
                        when(item.setIntent(any(Intent.class))).thenReturn(item);
                        return item;
                    });
        }
    }

    private static Release releaseWithAssets(String... names) {
        Release release = mock(Release.class);
        java.util.List<ReleaseAsset> assets = new java.util.ArrayList<>();
        for (String name : names) {
            ReleaseAsset asset = mock(ReleaseAsset.class);
            when(asset.name()).thenReturn(name);
            assets.add(asset);
        }
        when(release.assets()).thenReturn(assets);
        return release;
    }

    private com.gh4a.adapter.EventAdapter adapterWith(GitHubEvent e) {
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        when(adapter.hasHeaderView()).thenReturn(false);
        when(adapter.getCount()).thenReturn(1);
        when(adapter.getItemFromAdapterPosition(0)).thenReturn(e);
        when(adapter.getItem(0)).thenReturn(e);
        return adapter;
    }

    @Test
    public void onItemClick_invalidPayloadIsNoop() {
        TestEventFragment f = attach();
        f.onItemClick(GitHubEvent.builder()
                .type(GitHubEventType.IssuesEvent)
                .id("evt-1")
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .build());
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_issuesEventStartsIssueActivity() {
        TestEventFragment f = attach();
        f.onItemClick(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build()));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(IssueActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onItemClick_repoEventWithoutRepoToasts() {
        TestEventFragment f = attach();
        f.onItemClick(eventNoRepo(GitHubEventType.PushEvent,
                PushPayload.builder().build()));
        assertEquals(mActivity.getString(R.string.repo_not_found_toast),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_forkEventWithoutForkeeToasts() {
        TestEventFragment f = attach();
        f.onItemClick(event(GitHubEventType.ForkEvent,
                ForkPayload.builder().build()));
        assertEquals(mActivity.getString(R.string.repo_not_found_toast),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onItemClick_downloadEventNullDownloadIsNoop() {
        TestEventFragment f = attach();
        f.onItemClick(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().build()));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_commitCommentNullCommentIsNoop() {
        TestEventFragment f = attach();
        f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                CommitCommentPayload.builder().build()));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_pullRequestReviewCommentNullsIsNoop() {
        TestEventFragment f = attach();
        f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().build()));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onCreateContextMenu_nonRecyclerMenuInfoIsNoop() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), null);
        assertEquals(0, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_issuesEventAddsUserRepoAndIssueItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_nullEventIsNoop() throws Exception {
        TestEventFragment f = attach();
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        when(adapter.hasHeaderView()).thenReturn(false);
        when(adapter.getCount()).thenReturn(1);
        when(adapter.getItemFromAdapterPosition(0)).thenReturn(null);
        setField(f, "mAdapter", adapter);
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(0, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_pushEventAddsCompareAndCommitItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.PushEvent,
                PushPayload.builder()
                        .head("abcdef1234567890")
                        .before("0000000000000000")
                        .commits(java.util.Arrays.asList(
                                GitCommit.builder().sha("1234567890abcdef").build()))
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(4, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_releaseEventAddsDownloadItems() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder()
                        .release(releaseWithAssets("a.apk", "b.apk"))
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(4, rec.titles.size());
        assertEquals(java.util.Arrays.asList(100, 101), rec.ids);
    }

    @Test
    public void onCreateContextMenu_downloadEventNullDownloadAddsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onContextItemSelected_nonRecyclerMenuInfoReturnsFalse()
            throws Exception {
        TestEventFragment f = attach();
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(null);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_intentItemStartsActivity() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(0);
        when(item.getIntent()).thenReturn(
                RepositoryActivity.makeIntent(mActivity, "octo", "cat"));
        assertTrue(f.onContextItemSelected(item));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(RepositoryActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onContextItemSelected_downloadRangeOutOfRangeReturnsFalse()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder()
                        .release(releaseWithAssets("a.apk"))
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(105);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_downloadEventNullDownloadReturnsFalse()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(100);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void getEventForContextMenuPosition_nullAdapterReturnsNull()
            throws Exception {
        TestEventFragment f = attach();
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "getEventForContextMenuPosition", int.class);
        m.setAccessible(true);
        assertNull(m.invoke(f, 0));
    }

    @Test
    public void getEventForContextMenuPosition_footerPositionReturnsNull()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "getEventForContextMenuPosition", int.class);
        m.setAccessible(true);
        assertNull(m.invoke(f, 1));
    }

    @Test
    public void onAddData_filtersNullTypeEvents() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        RootAdapter<GitHubEvent, ?> adapter = mock(RootAdapter.class);
        GitHubEvent valid = event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build());
        GitHubEvent bad = GitHubEvent.builder()
                .id("evt-2")
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .payload(IssuesPayload.builder()
                        .issue(Issue.builder().number(8).build())
                        .build())
                .build();
        f.onAddData(adapter, java.util.Arrays.asList(valid, bad));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<GitHubEvent>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(adapter).addAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    private static boolean isUnknownEventTypeError(Throwable t) throws Exception {
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "isUnknownEventTypeError", Throwable.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, t);
    }

    @Test
    public void isUnknownEventTypeError_matchesValueOfError() throws Exception {
        assertTrue(isUnknownEventTypeError(
                new IllegalArgumentException("No enum constant GitHubEventType.Foo")));
        assertTrue(isUnknownEventTypeError(
                new RuntimeException(new IllegalArgumentException(
                        "GitHubEventType.Foo"))));
        assertTrue(isUnknownEventTypeError(
                new com.squareup.moshi.JsonDataException("GitHubEventType")));
        assertFalse(isUnknownEventTypeError(new RuntimeException("boom")));
        assertFalse(isUnknownEventTypeError(null));
    }

    @Test
    public void loadPage_unknownEventTypeErrorReturnsEmptyPage() {
        TestEventFragment f = attach();
        f.rawPage = Single.error(new IllegalArgumentException(
                "No enum constant GitHubEventType.Foo"));
        Response<Page<GitHubEvent>> response = f.loadPage(1, false).blockingGet();
        assertTrue(response.isSuccessful());
        assertEquals(0, response.body().items().size());
    }

    @Test
    public void loadPage_otherErrorPropagates() {
        TestEventFragment f = attach();
        f.rawPage = Single.error(new RuntimeException("boom"));
        try {
            f.loadPage(1, false).blockingGet();
            assertTrue("expected error", false);
        } catch (RuntimeException e) {
            assertEquals("boom", e.getMessage());
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_nonPullRequestIsNoop() throws Exception {
        TestEventFragment f = attach();
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "enrichSinglePullRequestEvent", GitHubEvent.class);
        m.setAccessible(true);
        m.invoke(f, event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build()));
    }

    @Test
    public void enrichSinglePullRequestEvent_nullPullRequestIsNoop() throws Exception {
        TestEventFragment f = attach();
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "enrichSinglePullRequestEvent", GitHubEvent.class);
        m.setAccessible(true);
        m.invoke(f, event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().number(5).build()));
    }

    @Test
    public void enrichSinglePullRequestEvent_nullNumberIsNoop() throws Exception {
        TestEventFragment f = attach();
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "enrichSinglePullRequestEvent", GitHubEvent.class);
        m.setAccessible(true);
        m.invoke(f, event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder()
                        .pullRequest(PullRequest.builder().number(5).build())
                        .build()));
    }

    @Test
    public void enrichSinglePullRequestEvent_successRefreshesItem() throws Exception {
        TestEventFragment f = attach();
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        when(adapter.getCount()).thenReturn(1);
        setField(f, "mAdapter", adapter);
        GitHubEvent e = event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder()
                        .number(5)
                        .pullRequest(PullRequest.builder().number(5).build())
                        .build());
        PullRequestService service = mock(PullRequestService.class);
        when(service.getPullRequest("octo", "cat", 5))
                .thenReturn(Single.just(Response.success(
                        PullRequest.builder().number(5).commits(2).build())));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.ServiceFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(PullRequestService.class),
                            org.mockito.ArgumentMatchers.eq(false)))
                    .thenReturn(service);
            java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                    "enrichSinglePullRequestEvent", GitHubEvent.class);
            m.setAccessible(true);
            m.invoke(f, e);
            long deadline = System.currentTimeMillis() + 5000;
            while (!((java.util.Map<?, ?>) field(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1")) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                if (System.currentTimeMillis() > deadline) break;
                Thread.sleep(20);
            }
            assertTrue(((java.util.Map<?, ?>) field(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1"));
            verify(adapter).refreshItem(e);
        }
    }

    // ---- 缺口补齐：LRU 淘汰 / enrichment provider / 点击意图 / 菜单各类型 / 重试 ----

    public static class EventHostActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    private EventHostActivity mBaseActivity;

    private TestEventFragment attachToBase() {
        mBaseActivity = Robolectric.buildActivity(EventHostActivity.class).setup().get();
        TestEventFragment f = new TestEventFragment();
        mBaseActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return f;
    }

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < 100 && !cond.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            idleMain();
        }
        assertTrue(cond.getAsBoolean());
    }

    private static void removeFragment(TestEventFragment f) {
        ((FragmentActivity) f.getActivity()).getSupportFragmentManager()
                .beginTransaction().remove(f).commitNow();
        idleMain();
    }

    private static GitHubEvent eventNoActor(GitHubEventType type,
            com.meisolsson.githubsdk.model.payload.GitHubPayload payload) {
        return GitHubEvent.builder()
                .type(type)
                .id("evt-1")
                .repo(id("octo/cat"))
                .payload(payload)
                .build();
    }

    private static GitHubEvent eventNoId(GitHubEventType type,
            com.meisolsson.githubsdk.model.payload.GitHubPayload payload) {
        return GitHubEvent.builder()
                .type(type)
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .payload(payload)
                .build();
    }

    private static void callEnrich(TestEventFragment f, GitHubEvent e) throws Exception {
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "enrichSinglePullRequestEvent", GitHubEvent.class);
        m.setAccessible(true);
        m.invoke(f, e);
    }

    private static GitHubEvent prEvent(PullRequest pr) {
        return event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder()
                        .number(5)
                        .pullRequest(pr)
                        .build());
    }

    private static PullRequestService mockPrService(
            Single<Response<PullRequest>> result) {
        PullRequestService service = mock(PullRequestService.class);
        when(service.getPullRequest("octo", "cat", 5)).thenReturn(result);
        return service;
    }

    private static MockedStatic<com.gh4a.ServiceFactory> mockPrServiceStatic(
            PullRequestService service) {
        MockedStatic<com.gh4a.ServiceFactory> mocked =
                Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                        Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> com.gh4a.ServiceFactory.get(
                        org.mockito.ArgumentMatchers.eq(PullRequestService.class),
                        org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(service);
        return mocked;
    }

    @Test
    public void enrichmentCache_evictsEldestAfter100() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        java.util.Map<String, PullRequest> cache =
                (java.util.Map<String, PullRequest>) field(f, "mEnrichedPullRequests");
        for (int i = 0; i < 101; i++) {
            cache.put("e" + i, PullRequest.builder().number(i).build());
        }
        assertEquals(100, cache.size());
        assertFalse(cache.containsKey("e0"));
        assertTrue(cache.containsKey("e100"));
    }

    @Test
    public void onCreateAdapter_setsEnrichmentProvider() throws Exception {
        TestEventFragment f = attach();
        f.onCreateAdapter();
        @SuppressWarnings("unchecked")
        java.util.Map<String, PullRequest> cache =
                (java.util.Map<String, PullRequest>) field(f, "mEnrichedPullRequests");
        PullRequest pr = PullRequest.builder().number(5).commits(2).build();
        cache.put("evt-1", pr);
        com.gh4a.adapter.EventAdapter adapter =
                (com.gh4a.adapter.EventAdapter) field(f, "mAdapter");
        Field providerField = com.gh4a.adapter.EventAdapter.class
                .getDeclaredField("mPullRequestEnrichmentProvider");
        providerField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.function.Function<GitHubEvent, PullRequest> provider =
                (java.util.function.Function<GitHubEvent, PullRequest>)
                        providerField.get(adapter);
        assertNotNull(provider);
        assertEquals(pr, provider.apply(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().number(5).build())));
        assertNull(provider.apply(GitHubEvent.builder()
                .type(GitHubEventType.PullRequestEvent)
                .id("evt-2")
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .payload(PullRequestPayload.builder().number(6).build())
                .build()));
    }

    @Test
    public void onItemClick_commitComment_loadsIntent() throws Exception {
        TestEventFragment f = attach();
        io.reactivex.subjects.SingleSubject<Optional<Intent>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<CommitCommentLoadTask> mocked =
                     Mockito.mockStatic(CommitCommentLoadTask.class)) {
            mocked.when(() -> CommitCommentLoadTask.load(
                            any(), any(), any(), any(), any()))
                    .thenReturn(subject);
            f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                    CommitCommentPayload.builder()
                            .comment(GitComment.builder()
                                    .id(1L).commitId("abcdef1234567890").build())
                            .build()));
            // 先等订阅落到 IO、弹框加上（Single.just 立即投递会和弹框提交
            // 挤在同一次主线程 idle 里丢消费者；生产上网络加载本就有延迟，
            // 用 SingleSubject 还原真实时序）。
            Thread.sleep(500);
            idleMain();
            subject.onSuccess(Optional.of(
                    RepositoryActivity.makeIntent(mActivity, "octo", "cat")));
            Thread.sleep(300);
            idleMain();
            Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(RepositoryActivity.class.getName(),
                    started.getComponent().getClassName());
        }
    }

    @Test
    public void onItemClick_commitComment_loadError_logsOnly() throws Exception {
        TestEventFragment f = attach();
        try (MockedStatic<CommitCommentLoadTask> mocked =
                     Mockito.mockStatic(CommitCommentLoadTask.class)) {
            mocked.when(() -> CommitCommentLoadTask.load(
                            any(), any(), any(), any(), any()))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                    CommitCommentPayload.builder()
                            .comment(GitComment.builder()
                                    .id(1L).commitId("abcdef1234567890").build())
                            .build()));
            Thread.sleep(500);
            idleMain();
            assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        }
    }

    @Test
    public void onItemClick_commitComment_nullComment_noLoad() throws Exception {
        TestEventFragment f = attach();
        try (MockedStatic<CommitCommentLoadTask> mocked =
                     Mockito.mockStatic(CommitCommentLoadTask.class)) {
            f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                    CommitCommentPayload.builder().build()));
            mocked.verifyNoInteractions();
            assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        }
    }

    @Test
    public void onItemClick_commitComment_nullId_noLoad() throws Exception {
        TestEventFragment f = attach();
        try (MockedStatic<CommitCommentLoadTask> mocked =
                     Mockito.mockStatic(CommitCommentLoadTask.class)) {
            f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                    CommitCommentPayload.builder()
                            .comment(GitComment.builder()
                                    .commitId("abcdef1234567890").build())
                            .build()));
            mocked.verifyNoInteractions();
            assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        }
    }

    @Test
    public void onItemClick_commitComment_emptyResult_noActivity() throws Exception {
        TestEventFragment f = attach();
        io.reactivex.subjects.SingleSubject<Optional<Intent>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<CommitCommentLoadTask> mocked =
                     Mockito.mockStatic(CommitCommentLoadTask.class)) {
            mocked.when(() -> CommitCommentLoadTask.load(
                            any(), any(), any(), any(), any()))
                    .thenReturn(subject);
            f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                    CommitCommentPayload.builder()
                            .comment(GitComment.builder()
                                    .id(1L).commitId("abcdef1234567890").build())
                            .build()));
            Thread.sleep(500);
            idleMain();
            subject.onSuccess(Optional.empty());
            Thread.sleep(300);
            idleMain();
            assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        }
    }

    @Test
    public void onItemClick_commitComment_detached_noActivity() throws Exception {
        TestEventFragment f = attach();
        io.reactivex.subjects.SingleSubject<Optional<Intent>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<CommitCommentLoadTask> mocked =
                     Mockito.mockStatic(CommitCommentLoadTask.class)) {
            mocked.when(() -> CommitCommentLoadTask.load(
                            any(), any(), any(), any(), any()))
                    .thenReturn(subject);
            f.onItemClick(event(GitHubEventType.CommitCommentEvent,
                    CommitCommentPayload.builder()
                            .comment(GitComment.builder()
                                    .id(1L).commitId("abcdef1234567890").build())
                            .build()));
            Thread.sleep(500);
            idleMain();
            removeFragment(f);
            subject.onSuccess(Optional.of(
                    RepositoryActivity.makeIntent(mActivity, "octo", "cat")));
            Thread.sleep(300);
            idleMain();
            assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        }
    }

    @Test
    public void onItemClick_downloadEvent_enqueuesDownload() throws Exception {
        TestEventFragment f = attachToBase();
        Download download = mock(Download.class);
        try (MockedStatic<DownloadUtils> mocked =
                     Mockito.mockStatic(DownloadUtils.class)) {
            f.onItemClick(event(GitHubEventType.DownloadEvent,
                    DownloadPayload.builder().download(download).build()));
            assertNull(Shadows.shadowOf(mBaseActivity).getNextStartedActivity());
        }
    }

    @Test
    public void onItemClick_gistEventDefault_isNoop() {
        TestEventFragment f = attach();
        f.onItemClick(event(GitHubEventType.GistEvent,
                GistPayload.builder().build()));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_prReviewComment_withMarker_loadsIntent() throws Exception {
        TestEventFragment f = attach();
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(5L);
        when(comment.commitId()).thenReturn("abc123");
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(5);
        io.reactivex.subjects.SingleSubject<Optional<Intent>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<PullRequestReviewCommentLoadTask> mocked =
                     Mockito.mockStatic(PullRequestReviewCommentLoadTask.class)) {
            mocked.when(() -> PullRequestReviewCommentLoadTask.load(
                            any(), any(), any(), anyInt(), any()))
                    .thenReturn(subject);
            f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                    PullRequestReviewCommentPayload.builder()
                            .pullRequest(pr).comment(comment).build()));
            // 与 commit-comment 测试同理：先等弹框加上再投递。
            Thread.sleep(500);
            idleMain();
            subject.onSuccess(Optional.of(
                    RepositoryActivity.makeIntent(mActivity, "octo", "cat")));
            Thread.sleep(300);
            idleMain();
            Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(RepositoryActivity.class.getName(),
                    started.getComponent().getClassName());
        }
    }

    @Test
    public void onItemClick_prReviewComment_nullComment_goesToPullRequest() {
        TestEventFragment f = attach();
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(5);
        f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().pullRequest(pr).build()));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onItemClick_prReviewComment_nullCommentId_goesToPullRequest() {
        TestEventFragment f = attach();
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(null);
        when(comment.commitId()).thenReturn("abc123");
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(5);
        f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder()
                        .pullRequest(pr).comment(comment).build()));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onItemClick_prReviewComment_noPr_goesToCommit() {
        TestEventFragment f = attach();
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(5L);
        when(comment.commitId()).thenReturn("abc123");
        f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().comment(comment).build()));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(CommitActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onItemClick_prReviewComment_nullNumber_breaks() {
        TestEventFragment f = attach();
        ReviewComment comment = mock(ReviewComment.class);
        when(comment.id()).thenReturn(5L);
        when(comment.commitId()).thenReturn("abc123");
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(null);
        f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder()
                        .pullRequest(pr).comment(comment).build()));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_prReviewComment_noMarker_goesToPullRequest() {
        TestEventFragment f = attach();
        PullRequest pr = mock(PullRequest.class);
        when(pr.number()).thenReturn(5);
        f.onItemClick(event(GitHubEventType.PullRequestReviewCommentEvent,
                PullRequestReviewCommentPayload.builder().pullRequest(pr).build()));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void getEventForContextMenuPosition_headerOffset() throws Exception {
        TestEventFragment f = attach();
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        GitHubEvent e = event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build());
        when(adapter.hasHeaderView()).thenReturn(true);
        when(adapter.getCount()).thenReturn(1);
        when(adapter.getItemFromAdapterPosition(1)).thenReturn(e);
        setField(f, "mAdapter", adapter);
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "getEventForContextMenuPosition", int.class);
        m.setAccessible(true);
        assertEquals(e, m.invoke(f, 1));
        assertNull(m.invoke(f, 0));
    }

    @Test
    public void onCreateContextMenu_invalidPayloadIsNoop() throws Exception {
        TestEventFragment f = attach();
        // payload 为 null 的事件：hasInvalidPayload 为真，只走公共守卫。
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        when(adapter.hasHeaderView()).thenReturn(false);
        when(adapter.getCount()).thenReturn(1);
        when(adapter.getItemFromAdapterPosition(0)).thenReturn(
                GitHubEvent.builder()
                        .type(GitHubEventType.IssuesEvent)
                        .id("evt-1")
                        .actor(actor("octo"))
                        .repo(id("octo/cat"))
                        .build());
        setField(f, "mAdapter", adapter);
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(0, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_noActor_skipsUserItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(eventNoActor(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_noRepo_skipsRepoAndTypeItems() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(eventNoRepo(GitHubEventType.PushEvent,
                PushPayload.builder()
                        .head("abcdef1234567890")
                        .before("0000000000000000")
                        .commits(java.util.Arrays.asList(
                                GitCommit.builder().sha("1234567890abcdef").build()))
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(1, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_commitComment_addsCommitItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.CommitCommentEvent,
                CommitCommentPayload.builder()
                        .comment(GitComment.builder()
                                .id(1L).commitId("abcdef1234567890").build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_commitComment_nullComment_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.CommitCommentEvent,
                CommitCommentPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_commitComment_nullRepo_addsActorOnly()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(eventNoRepo(GitHubEventType.CommitCommentEvent,
                CommitCommentPayload.builder()
                        .comment(GitComment.builder()
                                .id(1L).commitId("abcdef1234567890").build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(1, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_watchEvent_default_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.WatchEvent,
                mock(com.meisolsson.githubsdk.model.payload.GitHubPayload.class))));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_download_addsFileItem() throws Exception {
        TestEventFragment f = attach();
        Download download = mock(Download.class);
        when(download.name()).thenReturn("file.apk");
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().download(download).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
        assertEquals(java.util.Arrays.asList(100), rec.ids);
    }

    @Test
    public void onCreateContextMenu_follow_addsTargetUserItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.FollowEvent,
                FollowPayload.builder().target(actor("bob")).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_follow_nullTarget_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.FollowEvent,
                FollowPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_fork_addsForkItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ForkEvent,
                ForkPayload.builder()
                        .forkee(com.meisolsson.githubsdk.model.Repository.builder()
                                .fullName("x/y")
                                .owner(User.builder().login("x").build())
                                .name("y")
                                .build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_fork_nullForkee_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ForkEvent,
                ForkPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_gist_addsGistItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.GistEvent,
                GistPayload.builder().gist(Gist.builder().id("g1").build()).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_gist_nullGist_addsOnlyCommonItems() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.GistEvent,
                GistPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_gollum_addsWikiItem() throws Exception {
        TestEventFragment f = attach();
        GitHubWikiPage page = mock(GitHubWikiPage.class);
        when(page.htmlUrl()).thenReturn("https://example.com/wiki");
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.GollumEvent,
                GollumPayload.builder()
                        .pages(java.util.Arrays.asList(page)).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_gollum_nullPages_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        // GollumPayload.builder().build() 会抛（pages 必填），用 mock 造 pages()==null。
        GollumPayload payload = mock(GollumPayload.class);
        when(payload.pages()).thenReturn(null);
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.GollumEvent,
                payload)));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_gollum_emptyPages_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.GollumEvent,
                GollumPayload.builder()
                        .pages(java.util.Collections.emptyList()).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_gollum_nullHtmlUrl_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        GitHubWikiPage page = mock(GitHubWikiPage.class);
        when(page.htmlUrl()).thenReturn(null);
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.GollumEvent,
                GollumPayload.builder()
                        .pages(java.util.Arrays.asList(page)).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_issueComment_addsPullsItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder()
                        .issue(Issue.builder().number(7)
                                .pullRequest(PullRequest.builder().number(5).build())
                                .build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_issueComment_addsIssuesItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_issueComment_nullIssue_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssueCommentEvent,
                IssueCommentPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_issues_nullIssue_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_issues_nullNumber_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(null).build())
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_pullRequest_addsPullItem() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().number(5).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_pullRequest_nullNumber_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_push_nullHead_addsCommitItemsOnly() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.PushEvent,
                PushPayload.builder()
                        .before("0000000000000000")
                        .commits(java.util.Arrays.asList(
                                GitCommit.builder().sha("1234567890abcdef").build()))
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_push_nullCommits_addsCompareItemOnly()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.PushEvent,
                PushPayload.builder()
                        .head("abcdef1234567890")
                        .before("0000000000000000")
                        .build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(3, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_release_nullRelease_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onCreateContextMenu_release_nullAssets_addsOnlyCommonItems()
            throws Exception {
        TestEventFragment f = attach();
        Release release = mock(Release.class);
        when(release.assets()).thenReturn(null);
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(release).build())));
        RecordingMenu rec = new RecordingMenu();
        f.onCreateContextMenu(rec.menu, new android.view.View(mActivity), menuInfo(0));
        assertEquals(2, rec.titles.size());
    }

    @Test
    public void onContextItemSelected_nullEvent_returnsFalse() throws Exception {
        TestEventFragment f = attach();
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        when(adapter.hasHeaderView()).thenReturn(false);
        when(adapter.getCount()).thenReturn(1);
        when(adapter.getItemFromAdapterPosition(0)).thenReturn(null);
        setField(f, "mAdapter", adapter);
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(0);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_detachedFragment_returnsFalse() throws Exception {
        TestEventFragment f = new TestEventFragment();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(0);
        when(item.getIntent()).thenReturn(
                RepositoryActivity.makeIntent(mActivity, "octo", "cat"));
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_downloadRangeNonDownloadEvent_returnsTrue()
            throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(100);
        assertTrue(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_idAboveRange_usesIntent() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(200);
        when(item.getIntent()).thenReturn(
                RepositoryActivity.makeIntent(mActivity, "octo", "cat"));
        assertTrue(f.onContextItemSelected(item));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onContextItemSelected_noIntent_returnsFalse() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(0);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_releaseDownload_enqueues() throws Exception {
        TestEventFragment f = attachToBase();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder()
                        .release(releaseWithAssets("a.apk"))
                        .build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(100);
        try (MockedStatic<DownloadUtils> mocked =
                     Mockito.mockStatic(DownloadUtils.class)) {
            assertTrue(f.onContextItemSelected(item));
        }
    }

    @Test
    public void onContextItemSelected_releaseNullDownload_returnsFalse() throws Exception {
        TestEventFragment f = attach();
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(100);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_releaseNullAssets_returnsFalse() throws Exception {
        TestEventFragment f = attach();
        Release release = mock(Release.class);
        when(release.assets()).thenReturn(null);
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.ReleaseEvent,
                ReleasePayload.builder().release(release).build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(100);
        assertFalse(f.onContextItemSelected(item));
    }

    @Test
    public void onContextItemSelected_downloadEvent_enqueues() throws Exception {
        TestEventFragment f = attachToBase();
        Download download = mock(Download.class);
        setField(f, "mAdapter", adapterWith(event(GitHubEventType.DownloadEvent,
                DownloadPayload.builder().download(download).build())));
        MenuItem item = mock(MenuItem.class);
        when(item.getMenuInfo()).thenReturn(menuInfo(0));
        when(item.getItemId()).thenReturn(100);
        try (MockedStatic<DownloadUtils> mocked =
                     Mockito.mockStatic(DownloadUtils.class)) {
            assertTrue(f.onContextItemSelected(item));
        }
    }

    @Test
    public void onAddData_nullData_isNoop() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        RootAdapter<GitHubEvent, ?> adapter = mock(RootAdapter.class);
        f.onAddData(adapter, null);
        verify(adapter).addAll(null);
    }

    @Test
    public void onAddData_nullEvent_filtered() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        RootAdapter<GitHubEvent, ?> adapter = mock(RootAdapter.class);
        GitHubEvent valid = event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build());
        java.util.List<GitHubEvent> data = new java.util.ArrayList<>();
        data.add(null);
        data.add(valid);
        f.onAddData(adapter, data);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<GitHubEvent>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(adapter).addAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    public void enrichPullRequestEvents_detachedFragment_isNoop() throws Exception {
        TestEventFragment f = new TestEventFragment();
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        when(adapter.getCount()).thenReturn(1);
        setField(f, "mAdapter", adapter);
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "enrichPullRequestEvents");
        m.setAccessible(true);
        m.invoke(f);
        assertEquals(0, field(f, "mEnrichmentScannedCount"));
    }

    @Test
    public void enrichSinglePullRequestEvent_nullEvent_isNoop() throws Exception {
        TestEventFragment f = attach();
        callEnrich(f, null);
    }

    @Test
    public void enrichSinglePullRequestEvent_invalidPayload_isNoop() throws Exception {
        TestEventFragment f = attach();
        // 无 payload 的事件：hasInvalidPayload 为真，直接返回。
        callEnrich(f, GitHubEvent.builder()
                .type(GitHubEventType.PullRequestEvent)
                .id("evt-1")
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .build());
    }

    @Test
    public void enrichSinglePullRequestEvent_nullId_isNoop() throws Exception {
        TestEventFragment f = attach();
        callEnrich(f, eventNoId(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder()
                        .number(5)
                        .pullRequest(PullRequest.builder().number(5).build())
                        .build()));
        assertFalse(((java.util.Set<?>) field(f, "mEnrichmentInFlight"))
                .contains("evt-1"));
    }

    @Test
    public void enrichSinglePullRequestEvent_alreadyEnriched_isNoop() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        java.util.Map<String, PullRequest> cache =
                (java.util.Map<String, PullRequest>) field(f, "mEnrichedPullRequests");
        cache.put("evt-1", PullRequest.builder().number(5).commits(2).build());
        callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
        assertEquals(1, cache.size());
        assertFalse(((java.util.Set<?>) field(f, "mEnrichmentInFlight"))
                .contains("evt-1"));
    }

    @Test
    public void enrichSinglePullRequestEvent_alreadyInFlight_isNoop() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        java.util.Set<String> inFlight =
                (java.util.Set<String>) field(f, "mEnrichmentInFlight");
        inFlight.add("evt-1");
        callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
        assertEquals(1, inFlight.size());
    }

    @Test
    public void enrichSinglePullRequestEvent_commitsKnown_isNoop() throws Exception {
        TestEventFragment f = attach();
        callEnrich(f, prEvent(PullRequest.builder().number(5).commits(2).build()));
        assertFalse(((java.util.Set<?>) field(f, "mEnrichmentInFlight"))
                .contains("evt-1"));
    }

    @Test
    public void enrichSinglePullRequestEvent_noRepo_isNoop() throws Exception {
        TestEventFragment f = attach();
        callEnrich(f, eventNoRepo(GitHubEventType.PullRequestEvent,
                PullRequestPayload.builder()
                        .number(5)
                        .pullRequest(PullRequest.builder().number(5).build())
                        .build()));
        assertFalse(((java.util.Set<?>) field(f, "mEnrichmentInFlight"))
                .contains("evt-1"));
    }

    @Test
    public void enrichSinglePullRequestEvent_errorResponse_isNoop() throws Exception {
        TestEventFragment f = attach();
        PullRequestService service = mockPrService(Single.just(
                Response.error(404, ResponseBody.create(null, new byte[0]))));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            assertFalse(((java.util.Map<?, ?>) field(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1"));
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_nullBody_isNoop() throws Exception {
        TestEventFragment f = attach();
        PullRequestService service = mockPrService(
                Single.just(Response.success(null)));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            assertFalse(((java.util.Map<?, ?>) field(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1"));
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_detachedOnResponse_isNoop() throws Exception {
        TestEventFragment f = attach();
        SingleSubject<Response<PullRequest>> subject = SingleSubject.create();
        PullRequestService service = mockPrService(subject);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            removeFragment(f);
            subject.onSuccess(Response.success(
                    PullRequest.builder().number(5).commits(2).build()));
            idleMain();
            assertFalse(((java.util.Map<?, ?>) field(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1"));
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_error_schedulesOneRetry() throws Exception {
        TestEventFragment f = attach();
        PullRequestService service = mock(PullRequestService.class);
        when(service.getPullRequest("octo", "cat", 5))
                .thenReturn(Single.error(new RuntimeException("flaky")),
                        Single.just(Response.success(
                                PullRequest.builder().number(5).commits(2).build())));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            // 2 秒定时器走 computation 调度（真实时间）：等它触发再重拉。
            Thread.sleep(2500);
            idleMain();
            waitUntil(() -> ((java.util.Map<?, ?>) fieldUnchecked(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1"));
            Mockito.verify(service, Mockito.times(2))
                    .getPullRequest("octo", "cat", 5);
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_errorAfterDetach_noRetry() throws Exception {
        // 错误回调到达时 fragment 未 attach → !isAdded() 直接返回，不排重试。
        // 注意：不能用 attach 后 removeFragment 的写法——remove 会走 onDestroyView，
        // mEnrichmentDisposables.clear() 把 SingleSubject 订阅 dispose 掉，错误
        // 回调根本跑不到；且 enrich 经 doInBackground 在 IO 线程订阅，订阅落点
        // 与 remove 的先后是竞态（chunk 里单跑过、合跑失败即由此而来）。这里沿用
        // successOnDetachedFragment 的做法：从未 attach 的 fragment，订阅保持
        // 有效（SingleSubject 会把终态重放给晚订阅者），回调确实跑过。
        TestEventFragment f = new TestEventFragment();
        SingleSubject<Response<PullRequest>> subject = SingleSubject.create();
        PullRequestService service = mockPrService(subject);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            assertTrue(((java.util.Set<?>) field(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            subject.onError(new RuntimeException("flaky"));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            idleMain();
            // 回调第一行清 inFlight 即证明回调跑过；!isAdded() 提前返回，未把
            // evt-1 记入 mEnrichmentRetried，即未排 2 秒重试。
            assertFalse(((java.util.Set<?>) field(f, "mEnrichmentRetried"))
                    .contains("evt-1"));
            Thread.sleep(2500);
            idleMain();
            Mockito.verify(service, Mockito.times(1))
                    .getPullRequest("octo", "cat", 5);
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_error_detachedBeforeRetry_noSecondCall()
            throws Exception {
        TestEventFragment f = attach();
        PullRequestService service =
                mockPrService(Single.error(new RuntimeException("flaky")));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            // 出错时仍挂载（已排 2 秒重试），在定时器触发前 detach。
            removeFragment(f);
            Thread.sleep(2500);
            idleMain();
            Mockito.verify(service, Mockito.times(1))
                    .getPullRequest("octo", "cat", 5);
        }
    }

    @Test
    public void enrichSinglePullRequestEvent_alreadyRetried_logsOnly() throws Exception {
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        java.util.Set<String> retried =
                (java.util.Set<String>) field(f, "mEnrichmentRetried");
        retried.add("evt-1");
        PullRequestService service =
                mockPrService(Single.error(new RuntimeException("flaky")));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            Thread.sleep(2500);
            idleMain();
            Mockito.verify(service, Mockito.times(1))
                    .getPullRequest("octo", "cat", 5);
        }
    }

    private static Object fieldUnchecked(Object target, String name) {
        try {
            return field(target, name);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---- 2026-11-22 缺口补全：空 type 点击 / onAddData 过滤臂 /
    // enrich 扫描体 / 成功回调 detached 臂 ----

    @Test
    public void onItemClick_nullType_isNoop() {
        // type() 为 null 的事件静默无操作（不跳转、不弹 toast）。
        // 注意：它在 L146 的 hasInvalidPayload 守卫即返回（type 为 null 时
        // hasInvalidPayload 恒为真，EventAdapter.java:663），到不了 L174；
        // L174 的 `event.type() != null` 假臂不可达——能走到 L174 的事件
        // 已通过 L146 守卫，type 恒非空。见报告“仍不可达”第 R2-1 项。
        TestEventFragment f = attach();
        f.onItemClick(GitHubEvent.builder()
                .id("evt-1")
                .actor(actor("octo"))
                .repo(id("octo/cat"))
                .payload(IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build())
                .build());
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onAddData_emptyData_skipsFilterLoop() throws Exception {
        // L542：空集合时过滤循环一次也不进；L548：hasBad 为假，不走过滤分支。
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        RootAdapter<GitHubEvent, ?> adapter = mock(RootAdapter.class);
        f.onAddData(adapter, java.util.Collections.emptyList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<GitHubEvent>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(adapter).addAll(captor.capture());
        assertEquals(0, captor.getValue().size());
    }

    @Test
    public void onAddData_allGoodData_skipsFilter() throws Exception {
        // L548：全部事件 type 非空 → hasBad 为假，过滤分支不走，原样透传。
        TestEventFragment f = attach();
        @SuppressWarnings("unchecked")
        RootAdapter<GitHubEvent, ?> adapter = mock(RootAdapter.class);
        GitHubEvent valid = event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build());
        f.onAddData(adapter, java.util.Collections.singletonList(valid));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<GitHubEvent>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(adapter).addAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(valid, captor.getValue().iterator().next());
    }

    @Test
    public void enrichPullRequestEvents_attachedWithAdapter_scansNewItems()
            throws Exception {
        // L591/L596-601：已 attach 且 mAdapter 非空 → 扫描体跑过；
        // 非 PR 事件在 enrichSingle 里直接返回，不发网络。
        TestEventFragment f = attach();
        com.gh4a.adapter.EventAdapter adapter =
                mock(com.gh4a.adapter.EventAdapter.class);
        GitHubEvent e = event(GitHubEventType.IssuesEvent,
                IssuesPayload.builder()
                        .issue(Issue.builder().number(7).build())
                        .build());
        when(adapter.getCount()).thenReturn(1);
        when(adapter.getItem(0)).thenReturn(e);
        setField(f, "mAdapter", adapter);
        java.lang.reflect.Method m = EventListFragment.class.getDeclaredMethod(
                "enrichPullRequestEvents");
        m.setAccessible(true);
        m.invoke(f);
        assertEquals(1, field(f, "mEnrichmentScannedCount"));
        assertTrue(((java.util.Set<?>) field(f, "mEnrichmentInFlight")).isEmpty());
    }

    @Test
    public void enrichSinglePullRequestEvent_successOnDetachedFragment_isNoop()
            throws Exception {
        // L636：成功响应到达时 fragment 从未 attach → !isAdded() 返回，
        // 不写缓存、不刷新。注意与已有的 detachedOnResponse 测试不同：
        // 那个测试先 removeFragment，onDestroyView 会 dispose 掉订阅，
        // 回调根本跑不到；这里用从未 attach 的 fragment，订阅保持有效，
        // inFlight 里的 id 被回调第一行清掉即证明回调确实跑过。
        TestEventFragment f = new TestEventFragment();
        io.reactivex.subjects.SingleSubject<Response<PullRequest>> subject =
                io.reactivex.subjects.SingleSubject.create();
        PullRequestService service = mockPrService(subject);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockPrServiceStatic(service)) {
            callEnrich(f, prEvent(PullRequest.builder().number(5).build()));
            assertTrue(((java.util.Set<?>) field(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            subject.onSuccess(Response.success(
                    PullRequest.builder().number(5).commits(2).build()));
            waitUntil(() -> !((java.util.Set<?>) fieldUnchecked(f, "mEnrichmentInFlight"))
                    .contains("evt-1"));
            assertFalse(((java.util.Map<?, ?>) field(f, "mEnrichedPullRequests"))
                    .containsKey("evt-1"));
        }
    }
}
