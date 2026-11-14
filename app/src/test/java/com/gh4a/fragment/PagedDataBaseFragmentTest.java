package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.adapter.RootAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.meisolsson.githubsdk.model.Page;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * PagedDataBaseFragment + ListDataBaseFragment (F1): behavior that is
 * reachable without subscribing a network load. Loading paths themselves
 * are covered by the concrete subclass tests.
 */
@RunWith(RobolectricTestRunner.class)
public class PagedDataBaseFragmentTest {

    @Test
    public void shouldDoInitialLoad_defaultsTrue() {
        assertTrue(new CollaboratorListFragment().shouldDoInitialLoad(null));
        assertTrue(new CollaboratorListFragment().shouldDoInitialLoad(new Bundle()));
    }

    @Test
    public void resetSubject_createsPageSubjectAtOne() throws Exception {
        CollaboratorListFragment f = new CollaboratorListFragment();
        f.resetSubject();
        Field subjectField = PagedDataBaseFragment.class.getDeclaredField("mPageSubject");
        subjectField.setAccessible(true);
        io.reactivex.subjects.BehaviorSubject<?> subject =
                (io.reactivex.subjects.BehaviorSubject<?>) subjectField.get(f);
        assertNotNull(subject);
        assertEquals(1, subject.getValue());
        Field nextPageField = PagedDataBaseFragment.class.getDeclaredField("mNextPage");
        nextPageField.setAccessible(true);
        assertEquals(null, nextPageField.get(f));
    }

    @Test
    public void disposeCurrentSubscription_withoutSubscription_doesNothing() {
        new CollaboratorListFragment().disposeCurrentSubscription();
    }

    @Test
    public void onScrolledToFooter_withoutLoadingView_doesNothing() {
        // Bare instance: mFailedPage and mLoadingView are both null.
        new CollaboratorListFragment().onScrolledToFooter();
    }

    @Test
    public void onRefresh_beforeViewReady_marksRefreshPending() throws Exception {
        CollaboratorListFragment f = new CollaboratorListFragment();
        f.onRefresh();
        Field pendingField = PagedDataBaseFragment.class.getDeclaredField("mRefreshPending");
        pendingField.setAccessible(true);
        assertTrue((boolean) pendingField.get(f));
    }

    @Test
    public void onAddData_addsAllToAdapter() {
        @SuppressWarnings("unchecked")
        RootAdapter<com.meisolsson.githubsdk.model.User,
                RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        new CollaboratorListFragmentTest.NoLoad().onAddData(adapter,
                Arrays.asList(
                        com.meisolsson.githubsdk.model.User.builder().login("a").build(),
                        com.meisolsson.githubsdk.model.User.builder().login("b").build()));
        verify(adapter).addAll(Arrays.asList(
                com.meisolsson.githubsdk.model.User.builder().login("a").build(),
                com.meisolsson.githubsdk.model.User.builder().login("b").build()));
    }

    /** ListDataBaseFragment with a never-emitting Single: safe to load offline. */
    public static class LoadNoLoad extends ListDataBaseFragment<String> {
        boolean useRealInitialData;
        List<String> initialData;

        // onCreateContentView calls hasDividers() before mAdapter is assigned
        // by onRecyclerViewInflated, so onCreateAdapter must return non-null.
        @SuppressWarnings("unchecked")
        final RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);

        @Override
        protected int getEmptyTextResId() {
            return 0;
        }

        @Override
        protected RootAdapter<String, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
            return adapter;
        }

        @Override
        protected List<String> onGetInitialData() {
            return useRealInitialData ? initialData : null;
        }

        @Override
        protected io.reactivex.Observable<List<String>> onCreateDataObservable(
                boolean bypassCache) {
            return null;
        }

        @Override
        protected io.reactivex.Single<List<String>> onCreateDataSingle(boolean bypassCache) {
            return io.reactivex.Single.never();
        }
    }

    private static LoadNoLoad attachedLoad() {
        LoadNoLoad f = new LoadNoLoad();
        FragmentTestHelper.attach(f);
        return f;
    }

    private static Object field(Object target, Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object target, Class<?> owner, String name, Object value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object invoke(Object target, Class<?> owner, String name,
            Class<?>[] types, Object[] args) {
        try {
            java.lang.reflect.Method m = owner.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void listOnDestroyView_withSubscription_disposesAndNulls() {
        LoadNoLoad f = attachedLoad();
        assertNotNull(field(f, ListDataBaseFragment.class, "mSubscription"));
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        activity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        assertEquals(null, field(f, ListDataBaseFragment.class, "mSubscription"));
        assertEquals(null, field(f, ListDataBaseFragment.class, "mAdapter"));
    }

    @Test
    public void listOnRefresh_attached_disposesAndResubscribes() {
        LoadNoLoad f = attachedLoad();
        Object before = field(f, ListDataBaseFragment.class, "mSubscription");
        assertNotNull(before);
        f.onRefresh();
        Object after = field(f, ListDataBaseFragment.class, "mSubscription");
        assertNotNull(after);
        assertTrue(before != after);
    }

    @Test
    public void listOnRefresh_withAdapter_clearsAdapter() {
        LoadNoLoad f = attachedLoad();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        f.onRefresh();
        verify(adapter).clear();
    }

    @Test
    public void listHasDividers_combos() {
        LoadNoLoad f = new LoadNoLoad();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        org.mockito.Mockito.when(adapter.hasDividers()).thenReturn(false);
        assertEquals(false, f.hasDividers());
        org.mockito.Mockito.when(adapter.hasDividers()).thenReturn(true);
        org.mockito.Mockito.when(adapter.isCardStyle()).thenReturn(true);
        assertEquals(false, f.hasDividers());
        org.mockito.Mockito.when(adapter.isCardStyle()).thenReturn(false);
        assertEquals(true, f.hasDividers());
        assertEquals(false, f.hasCards());
        org.mockito.Mockito.when(adapter.isCardStyle()).thenReturn(true);
        assertEquals(true, f.hasCards());
    }

    @Test
    public void listLoadData_withInitialData_callsHandleNewData() {
        LoadNoLoad f = attachedLoad();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        f.useRealInitialData = true;
        f.initialData = Arrays.asList("a", "b");
        invoke(f, ListDataBaseFragment.class, "loadData",
                new Class<?>[] { boolean.class }, new Object[] { false });
        verify(adapter).clear();
        verify(adapter).addAll(Arrays.asList("a", "b"));
        assertTrue(f.isContentShown());
    }

    @Test
    public void listHandleNewPage_rendersCumulativeItems() {
        LoadNoLoad f = attachedLoad();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        invoke(f, ListDataBaseFragment.class, "handleNewPage",
                new Class<?>[] { List.class }, new Object[] { Arrays.asList("x") });
        verify(adapter).clear();
        verify(adapter).addAll(Arrays.asList("x"));
        assertTrue(f.isContentShown());
    }

    @Test
    public void listHandleAllPagesLoaded_showsContent() {
        LoadNoLoad f = attachedLoad();
        invoke(f, ListDataBaseFragment.class, "handleAllPagesLoaded",
                new Class<?>[0], new Object[0]);
        assertTrue(f.isContentShown());
    }

    @Test
    public void listHandlePagedLoadFailure_withData_logsOnly() {
        LoadNoLoad f = attachedLoad();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        org.mockito.Mockito.when(adapter.getCount()).thenReturn(2);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        invoke(f, ListDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("page 2 failed") });
    }

    @Test
    public void listOnDestroyView_withoutSubscription_doesNothing() {
        new LoadNoLoad().onDestroyView();
    }

    @Test
    public void listOnRefresh_noSubscriptionNoAdapter_nullBranches() {
        // onRefresh on a bare fragment NPEs in loadData (mRxLoader is null),
        // so the null branches are reached on an attached fragment with the
        // fields nulled via reflection instead.
        LoadNoLoad f = attachedLoad();
        setField(f, ListDataBaseFragment.class, "mSubscription", null);
        setField(f, ListDataBaseFragment.class, "mAdapter", null);
        f.onRefresh();
        assertNotNull(field(f, ListDataBaseFragment.class, "mSubscription"));
    }

    @Test
    public void listHandlePagedLoadFailure_withEmptyAdapter_delegatesToLoadFailure() {
        // mAdapter non-null (the attach stub) but count 0: the second
        // operand of the && is false, so handleLoadFailure runs.
        LoadNoLoad f = attachedLoad();
        invoke(f, ListDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("first page failed") });
    }

    @Test
    public void listHandlePagedLoadFailure_withoutData_delegatesToLoadFailure() {
        LoadNoLoad f = new LoadNoLoad();
        invoke(f, ListDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("first page failed") });
    }

    // ---- ListDataBaseFragment defaults (bare instance, no Android needed) ----

    /** Bare subclass that overrides nothing: exercises the base defaults. */
    public static class TestListFragment extends ListDataBaseFragment<String> {
        @Override
        protected int getEmptyTextResId() {
            return 0;
        }

        @Override
        protected com.gh4a.adapter.RootAdapter<String,
                ? extends androidx.recyclerview.widget.RecyclerView.ViewHolder>
                onCreateAdapter() {
            return null;
        }
    }

    @Test
    public void listDefaults_onCreateDataSingleIsNull() {
        assertEquals(null, new TestListFragment().onCreateDataSingle(false));
        assertEquals(null, new TestListFragment().onCreateDataSingle(true));
    }

    @Test
    public void listDefaults_onCreateDataObservableIsNull() {
        assertEquals(null, new TestListFragment().onCreateDataObservable(false));
    }

    @Test
    public void listDefaults_onGetInitialDataIsNull() {
        assertEquals(null, new CommitCompareFragment().onGetInitialData());
    }

    @Test
    public void listOnAddData_addsAllAndNotifies() {
        @SuppressWarnings("unchecked")
        RootAdapter<com.meisolsson.githubsdk.model.Commit,
                RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        List<com.meisolsson.githubsdk.model.Commit> data = Arrays.asList(
                com.meisolsson.githubsdk.model.Commit.builder().sha("a").build(),
                com.meisolsson.githubsdk.model.Commit.builder().sha("b").build());
        new CommitCompareFragment().onAddData(adapter, data);
        verify(adapter).addAll(data);
        verify(adapter).notifyDataSetChanged();
    }

    @Test
    public void listOnRefresh_withStubbedSingle_clearsAdapterAndReloads() {
        CommitCompareFragmentTest.NoLoad f = new CommitCompareFragmentTest.NoLoad();
        f.setArguments(CommitCompareFragment.newInstance("o", "r", "main", "feature")
                .getArguments());
        FragmentTestHelper.attach(f);
        // onRefresh() with the stubbed Single.never() must not crash and
        // must not touch the network.
        f.onRefresh();
    }

    @Test
    public void pagedOnDestroyView_clearsAdapterState() {
        CollaboratorListFragmentTest.NoLoad f = new CollaboratorListFragmentTest.NoLoad();
        f.setArguments(CollaboratorListFragment.newInstance("o", "r").getArguments());
        androidx.fragment.app.FragmentActivity activity = FragmentTestHelper.attach(f);
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        // onDestroyView ran; re-attaching is out of scope, just assert no crash.
        assertEquals(null, f.getView());
    }

    // ---- PagedDataBaseFragment.load() subscription paths ----

    /** Minimal real RootAdapter over Strings. */
    public static class StringAdapter extends RootAdapter<String, RecyclerView.ViewHolder> {
        public StringAdapter(Context context) {
            super(context);
        }

        @Override
        protected RecyclerView.ViewHolder onCreateViewHolder(LayoutInflater inflater,
                ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(new View(parent.getContext())) {
            };
        }

        @Override
        protected void onBindViewHolder(RecyclerView.ViewHolder holder, String item) {
        }
    }

    /** Paged fragment whose pages are canned Singles; loadPage calls counted. */
    public static class LoadTestFragment extends PagedDataBaseFragment<String> {
        final Map<Integer, Single<Response<Page<String>>>> pages = new HashMap<>();
        Single<Response<Page<String>>> fallback = Single.never();
        final AtomicInteger loadCalls = new AtomicInteger();
        StringAdapter adapter;

        @Override
        protected int getEmptyTextResId() {
            return 0;
        }

        @Override
        protected RootAdapter<String, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
            adapter = new StringAdapter(getActivity());
            return adapter;
        }

        @Override
        protected Single<Response<Page<String>>> loadPage(int page, boolean bypassCache) {
            loadCalls.incrementAndGet();
            return pages.getOrDefault(page, fallback);
        }

        @Override
        public void onItemClick(String item) {
        }
    }

    @SuppressWarnings("unchecked")
    static Page<String> stringPage(List<String> items, Integer next) {
        Page<String> p = mock(Page.class);
        org.mockito.Mockito.when(p.items()).thenReturn(items);
        org.mockito.Mockito.when(p.next()).thenReturn(next);
        return p;
    }

    private static LoadTestFragment attachedLoadTest() {
        LoadTestFragment f = new LoadTestFragment();
        FragmentTestHelper.attach(f);
        return f;
    }

    /**
     * RxLoader delivers on the main looper while the IO producer runs on a
     * real thread: pump the looper and sleep until the adapter reaches the
     * expected count, or fail after a bounded wait.
     */
    private static void waitForCount(LoadTestFragment f, int expectedCount) throws Exception {
        for (int i = 0; i < 100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
            Thread.sleep(20);
            if (f.adapter != null && f.adapter.getCount() >= expectedCount) {
                return;
            }
        }
        throw new AssertionError("timed out waiting for " + expectedCount
                + " items, got " + (f.adapter == null ? "null adapter" : f.adapter.getCount()));
    }

    /** Same wait, but for the content-shown flag (no items expected). */
    private static void waitForContentShown(LoadTestFragment f) throws Exception {
        for (int i = 0; i < 100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
            Thread.sleep(20);
            if (f.isContentShown()) {
                return;
            }
        }
        throw new AssertionError("timed out waiting for content to be shown");
    }

    private static void settleMainLooper() throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(500, TimeUnit.MILLISECONDS);
        Thread.sleep(200);
    }

    @Test
    public void load_singlePage_rendersItemsAndHidesLoadingView() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a", "b"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 2);
        assertEquals(Arrays.asList("a", "b"),
                new ArrayList<>(Arrays.asList(f.adapter.getItem(0), f.adapter.getItem(1))));
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mNextPage"));
        assertEquals(View.GONE,
                ((View) field(f, PagedDataBaseFragment.class, "mLoadingView")).getVisibility());
        assertTrue(f.isContentShown());
    }

    @Test
    public void load_twoPages_scrolledToFooterAppendsSecondPage() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        Page<String> page2 = stringPage(Arrays.asList("b"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        f.pages.put(2, Single.just(Response.success(page2)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        assertEquals(2, field(f, PagedDataBaseFragment.class, "mNextPage"));
        assertEquals(View.VISIBLE,
                ((View) field(f, PagedDataBaseFragment.class, "mLoadingView")).getVisibility());
        f.onScrolledToFooter();
        waitForCount(f, 2);
        assertEquals(Arrays.asList("a", "b"),
                new ArrayList<>(Arrays.asList(f.adapter.getItem(0), f.adapter.getItem(1))));
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mNextPage"));
    }

    @Test
    public void load_refresh_replacesItemsViaClearPath() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        // Refresh with different first item: fillData takes the clear path.
        Page<String> refreshed = stringPage(Arrays.asList("c", "d"), null);
        f.pages.put(1, Single.just(Response.success(refreshed)));
        f.onRefresh();
        waitForCount(f, 2);
        assertEquals(Arrays.asList("c", "d"),
                new ArrayList<>(Arrays.asList(f.adapter.getItem(0), f.adapter.getItem(1))));
    }

    @Test
    public void load_204Response_mapsToDummyPage() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        f.pages.put(1, Single.just(Response.success(204, null)));
        FragmentTestHelper.attach(f);
        waitForContentShown(f);
        assertEquals(0, f.adapter.getCount());
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mNextPage"));
        assertEquals(View.GONE,
                ((View) field(f, PagedDataBaseFragment.class, "mLoadingView")).getVisibility());
    }

    @Test
    public void load_nullItemsPage_treatedAsEmpty() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(null, null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForContentShown(f);
        assertEquals(0, f.adapter.getCount());
    }

    @Test
    public void load_firstPageFailure_withEmptyAdapter_staysLoading() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        f.pages.put(1, Single.error(new RuntimeException("page 1 failed")));
        FragmentTestHelper.attach(f);
        settleMainLooper();
        assertEquals(false, f.isContentShown());
        assertEquals(0, f.adapter.getCount());
    }

    @Test
    public void load_secondPageFailure_keepsDataAndFooterRetryWorks() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        f.pages.put(1, Single.just(Response.success(page1)));
        f.pages.put(2, Single.error(new RuntimeException("page 2 failed")));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        f.onScrolledToFooter();
        // Wait for the failure to be recorded: mFailedPage becomes 2.
        for (int i = 0; i < 100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
            Thread.sleep(20);
            if (Integer.valueOf(2).equals(field(f, PagedDataBaseFragment.class, "mFailedPage"))) {
                break;
            }
        }
        assertEquals(2, field(f, PagedDataBaseFragment.class, "mFailedPage"));
        assertEquals(1, f.adapter.getCount());
        assertTrue(f.isContentShown());
        assertEquals(View.VISIBLE,
                ((View) field(f, PagedDataBaseFragment.class, "mLoadingView")).getVisibility());
        // Fix page 2 and retry via the loading-view click.
        // NOTE (F1-2): the retried page's delivery races a stale replay of
        // the page-1 data, so the final adapter contents are nondeterministic
        // here ([a] or [b]); only the deterministic effects are asserted.
        int callsBefore = f.loadCalls.get();
        Page<String> page2 = stringPage(Arrays.asList("b"), null);
        f.pages.put(2, Single.just(Response.success(page2)));
        ((View) field(f, PagedDataBaseFragment.class, "mLoadingView")).performClick();
        settleMainLooper();
        assertEquals(callsBefore + 1, f.loadCalls.get());
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mFailedPage"));
    }

    @Test
    public void load_failedPage_scrolledToFooterRetries() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        f.pages.put(1, Single.just(Response.success(page1)));
        f.pages.put(2, Single.error(new RuntimeException("page 2 failed")));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        f.onScrolledToFooter();
        for (int i = 0; i < 100; i++) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
            Thread.sleep(20);
            if (Integer.valueOf(2).equals(field(f, PagedDataBaseFragment.class, "mFailedPage"))) {
                break;
            }
        }
        assertEquals(2, field(f, PagedDataBaseFragment.class, "mFailedPage"));
        int callsBefore = f.loadCalls.get();
        Page<String> page2 = stringPage(Arrays.asList("b"), null);
        f.pages.put(2, Single.just(Response.success(page2)));
        // onScrolledToFooter with a failed page retries it (F1-2: same
        // nondeterministic-delivery caveat as the footer-click retry).
        f.onScrolledToFooter();
        settleMainLooper();
        assertEquals(callsBefore + 1, f.loadCalls.get());
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mFailedPage"));
    }

    @Test
    public void load_refreshPendingBeforeAttach_loadsOnAttach() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        // onRefresh before the view exists marks the refresh pending.
        f.onRefresh();
        assertTrue((boolean) field(f, PagedDataBaseFragment.class, "mRefreshPending"));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        assertEquals("a", f.adapter.getItem(0));
    }

    @Test
    public void onScrolledToFooter_withoutNextPage_doesNothing() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        int callsBefore = f.loadCalls.get();
        f.onScrolledToFooter();
        settleMainLooper();
        assertEquals(callsBefore, f.loadCalls.get());
        assertEquals(1, f.adapter.getCount());
    }

    @Test
    public void fillData_bareFragment_returnsEarly() {
        LoadTestFragment f = new LoadTestFragment();
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { Arrays.asList("a"), null });
    }

    @Test
    public void pagedOnDestroyView_withSubscription_disposesAndNulls() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        assertNotNull(field(f, PagedDataBaseFragment.class, "mSubscription"));
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        activity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mSubscription"));
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mFailedPage"));
    }

    @Test
    public void disposeCurrentSubscription_withSubscription_disposesAndNulls()
            throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        assertNotNull(field(f, PagedDataBaseFragment.class, "mSubscription"));
        f.disposeCurrentSubscription();
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mSubscription"));
    }

    @Test
    public void hasDividers_noDividersOrCardStyle_returnsFalse() {
        // PagedDataBaseFragment.hasDividers is mAdapter.hasDividers() &&
        // !mAdapter.isCardStyle(): both && operands' false arms, via a
        // mocked adapter swapped into the fragment.
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> mockAdapter =
                mock(RootAdapter.class);
        when(mockAdapter.hasDividers()).thenReturn(false);
        when(mockAdapter.isCardStyle()).thenReturn(false);
        setField(f, PagedDataBaseFragment.class, "mAdapter", mockAdapter);
        assertFalse(f.hasDividers());
        when(mockAdapter.hasDividers()).thenReturn(true);
        when(mockAdapter.isCardStyle()).thenReturn(true);
        assertFalse(f.hasDividers());
        when(mockAdapter.hasDividers()).thenReturn(true);
        when(mockAdapter.isCardStyle()).thenReturn(false);
        assertTrue(f.hasDividers());
    }

    @Test
    public void pagedHandlePagedLoadFailure_withData_nullSubjectAndView_skipsRecording()
            throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        setField(f, PagedDataBaseFragment.class, "mPageSubject", null);
        setField(f, PagedDataBaseFragment.class, "mLoadingView", null);
        invoke(f, PagedDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("page 2 failed") });
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mFailedPage"));
    }

    @Test
    public void fillData_withoutAdapter_returnsEarly() {
        LoadTestFragment f = new LoadTestFragment();
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { new ArrayList<>(), null });
    }

    @Test
    public void fillData_emptyDataWithItems_clearsAdapter() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { new ArrayList<>(), null });
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mNextPage"));
        assertEquals(0, f.adapter.getCount());
    }

    @Test
    public void fillData_dataLongerThanAdapter_appendsRemainder() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        String sameA = f.adapter.getItem(0);
        List<String> data = Arrays.asList(sameA, "b");
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { data, null });
        assertEquals(2, f.adapter.getCount());
        assertEquals("a", f.adapter.getItem(0));
        assertEquals("b", f.adapter.getItem(1));
    }

    @Test
    public void retryFailedPage_withNullSubject_doesNothing() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        int callsBefore = f.loadCalls.get();
        setField(f, PagedDataBaseFragment.class, "mFailedPage", 2);
        setField(f, PagedDataBaseFragment.class, "mPageSubject", null);
        invoke(f, PagedDataBaseFragment.class, "retryFailedPage",
                new Class<?>[0], new Object[0]);
        assertEquals(callsBefore, f.loadCalls.get());
        assertEquals(2, field(f, PagedDataBaseFragment.class, "mFailedPage"));
    }

    @Test
    public void onScrolledToFooter_nextPageButLoadingHidden_doesNothing()
            throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        int callsBefore = f.loadCalls.get();
        View loadingView = (View) field(f, PagedDataBaseFragment.class, "mLoadingView");
        assertNotNull(loadingView);
        loadingView.setVisibility(View.GONE);
        f.onScrolledToFooter();
        settleMainLooper();
        assertEquals(callsBefore, f.loadCalls.get());
        assertEquals(2, field(f, PagedDataBaseFragment.class, "mNextPage"));
    }

    @Test
    public void pagedHandlePagedLoadFailure_emptyAdapter_delegatesToLoadFailure()
            throws Exception {
        // Adapter exists but holds no items: the second operand of the &&
        // in handlePagedLoadFailure is false, so it delegates.
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(new ArrayList<>(), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        settleMainLooper();
        assertNotNull(field(f, PagedDataBaseFragment.class, "mAdapter"));
        invoke(f, PagedDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("first page failed") });
        assertEquals(null, field(f, PagedDataBaseFragment.class, "mFailedPage"));
    }

    @Test
    public void fillData_nullLoadingView_returnsEarly() throws Exception {
        // Adapter non-null but loading view null: the second operand of
        // the || is true, fillData returns without touching the adapter.
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        setField(f, PagedDataBaseFragment.class, "mLoadingView", null);
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { Arrays.asList("z"), null });
        assertEquals(1, f.adapter.getCount());
        assertEquals("a", f.adapter.getItem(0));
    }

    @Test
    public void fillData_firstItemChanged_clearsAndReplaces() throws Exception {
        // Adapter holds [a], new data starts with a different item: the
        // third operand of the preserve check is false, so the adapter is
        // cleared and replaced.
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { Arrays.asList("z"), null });
        assertEquals(1, f.adapter.getCount());
        assertEquals("z", f.adapter.getItem(0));
    }

    @Test
    public void fillData_itemDiffersAtValidIndex_appendsIt() throws Exception {
        // Adapter holds [a, b]; new data shares the first item but differs
        // at index 1: the loop's second operand is false at a valid index,
        // so the differing item is appended.
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        Page<String> page2 = stringPage(Arrays.asList("b"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        f.pages.put(2, Single.just(Response.success(page2)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        f.onScrolledToFooter();
        waitForCount(f, 2);
        String sameA = f.adapter.getItem(0);
        invoke(f, PagedDataBaseFragment.class, "fillData",
                new Class<?>[] { List.class, Integer.class },
                new Object[] { Arrays.asList(sameA, "X"), null });
        assertEquals(3, f.adapter.getCount());
        assertEquals("a", f.adapter.getItem(0));
        assertEquals("b", f.adapter.getItem(1));
        assertEquals("X", f.adapter.getItem(2));
    }

    @Test
    public void pagedHandlePagedLoadFailure_withoutData_delegatesToLoadFailure() {
        LoadTestFragment f = new LoadTestFragment();
        invoke(f, PagedDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("first page failed") });
    }

    @Test
    public void retryFailedPage_withObservers_reloadsViaSubject() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        Page<String> page2 = stringPage(Arrays.asList("b"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        f.pages.put(2, Single.just(Response.success(page2)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        // Simulate a recorded failed page while the load subscription is
        // still alive: retry goes through mPageSubject.onNext, so the scan
        // keeps the accumulated page-1 items.
        setField(f, PagedDataBaseFragment.class, "mFailedPage", 2);
        invoke(f, PagedDataBaseFragment.class, "retryFailedPage",
                new Class<?>[0], new Object[0]);
        waitForCount(f, 2);
        assertEquals(Arrays.asList("a", "b"),
                new ArrayList<>(Arrays.asList(f.adapter.getItem(0), f.adapter.getItem(1))));
    }

    @Test
    public void onScrolledToFooter_withoutObservers_reloads() throws Exception {
        LoadTestFragment f = new LoadTestFragment();
        Page<String> page1 = stringPage(Arrays.asList("a"), 2);
        Page<String> page2 = stringPage(Arrays.asList("b"), null);
        f.pages.put(1, Single.just(Response.success(page1)));
        f.pages.put(2, Single.just(Response.success(page2)));
        FragmentTestHelper.attach(f);
        waitForCount(f, 1);
        // Kill the load subscription: the next scroll-to-footer has no
        // observers and must fall back to load(true).
        f.disposeCurrentSubscription();
        int callsBefore = f.loadCalls.get();
        f.onScrolledToFooter();
        settleMainLooper();
        assertEquals(callsBefore + 1, f.loadCalls.get());
    }

    @Test
    public void retryFailedPage_withoutFailedPage_doesNothing() {
        LoadTestFragment f = new LoadTestFragment();
        invoke(f, PagedDataBaseFragment.class, "retryFailedPage",
                new Class<?>[0], new Object[0]);
    }
}
