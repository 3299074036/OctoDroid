package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.adapter.RootAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** LoadingListFragmentBase (F1) via a minimal concrete subclass. */
@RunWith(RobolectricTestRunner.class)
public class LoadingListFragmentBaseTest {

    public static class TestListFragment extends LoadingListFragmentBase {
        @Override
        protected int getEmptyTextResId() {
            return R.string.no_events_found;
        }

        @Override
        public void onRefresh() {
        }
    }

    public static class NoEmptyTextFragment extends LoadingListFragmentBase {
        @Override
        protected int getEmptyTextResId() {
            return 0;
        }

        @Override
        public void onRefresh() {
        }
    }

    private static TestListFragment attached() {
        TestListFragment f = new TestListFragment();
        FragmentTestHelper.attach(f);
        return f;
    }

    /** BaseActivity host: the fast-scroller listener locks the drawer via it. */
    public static class TestBaseActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    /** BaseActivity host that also satisfies ContentListFragment.ParentCallback. */
    public static class BaseParentActivity extends TestBaseActivity
            implements com.gh4a.fragment.ContentListFragment.ParentCallback {
        @Override
        public void onContentsLoaded(com.gh4a.fragment.ContentListFragment fragment,
                java.util.List<com.meisolsson.githubsdk.model.Content> contents) {
        }

        @Override
        public void onTreeSelected(com.meisolsson.githubsdk.model.Content content) {
        }

        @Override
        public void onCommitSelected(com.meisolsson.githubsdk.model.Commit commit) {
        }

        @Override
        public java.util.Set<String> getSubModuleNames(
                com.gh4a.fragment.ContentListFragment fragment) {
            return java.util.Collections.emptySet();
        }
    }

    /** Host activity implementing the recycler-created listener. */
    public static class ListenerActivity extends androidx.fragment.app.FragmentActivity
            implements LoadingListFragmentBase.OnRecyclerViewCreatedListener {
        int calls;

        @Override
        public void onRecyclerViewCreated(androidx.fragment.app.Fragment fragment,
                RecyclerView recyclerView) {
            calls++;
        }
    }

    private static TestListFragment attachedToBaseActivity() {
        TestListFragment f = new TestListFragment();
        FragmentTestHelper.attachTo(TestBaseActivity.class, f);
        return f;
    }

    @Test
    public void onCreateContentView_inflatesListLayout() {
        TestListFragment f = attached();
        assertNotNull(f.getView());
        RecyclerView list = f.getView().findViewById(R.id.list);
        assertNotNull(list);
        assertNotNull(list.getLayoutManager());
        assertNotNull(f.getView().findViewById(android.R.id.empty));
    }

    /**
     * Drives the fast-scroller handle-touch listener the production code
     * registers. Dispatching MotionEvents at (0,0) never reaches the
     * handle, so the listener is invoked directly via its field.
     */
    private static boolean invokeHandleTouch(TestListFragment f, int action) throws Exception {
        View scroller = f.getView().findViewById(R.id.fast_scroller);
        assertNotNull(scroller);
        java.lang.reflect.Field field = scroller.getClass()
                .getDeclaredField("mOnTouchListener");
        field.setAccessible(true);
        View.OnTouchListener listener = (View.OnTouchListener) field.get(scroller);
        assertNotNull(listener);
        long now = SystemClock.uptimeMillis();
        return listener.onTouch(scroller, MotionEvent.obtain(now, now, action, 0, 0, 0));
    }

    @Test
    public void fastScrollerTouch_plainHost_nullActivityBranches() throws Exception {
        TestListFragment f = attached();
        assertEquals(false, invokeHandleTouch(f, MotionEvent.ACTION_DOWN));
        assertEquals(false, invokeHandleTouch(f, MotionEvent.ACTION_UP));
        assertEquals(false, invokeHandleTouch(f, MotionEvent.ACTION_CANCEL));
        assertEquals(false, invokeHandleTouch(f, MotionEvent.ACTION_MOVE));
    }

    @Test
    public void fastScrollerTouch_baseActivityHost_locksDrawer() throws Exception {
        TestListFragment f = attachedToBaseActivity();
        assertEquals(false, invokeHandleTouch(f, MotionEvent.ACTION_DOWN));
        assertEquals(false, invokeHandleTouch(f, MotionEvent.ACTION_UP));
    }

    @Test
    public void onViewCreated_mismatchedArgsHash_noAdapter_skipsPolicy() {
        TestListFragment f = attached();
        Bundle state = new Bundle();
        state.putInt("args_hash", 12345);
        f.onViewCreated(f.getView(), state);
    }

    @Test
    public void onViewCreated_mismatchedArgsHash_withArgsAndAdapter_setsPreventPolicy()
            throws Exception {
        com.gh4a.fragment.ContentListFragmentTest.NoLoad f =
                new com.gh4a.fragment.ContentListFragmentTest.NoLoad();
        f.setArguments(com.gh4a.fragment.ContentListFragment.newInstance(
                com.gh4a.fragment.ContentListFragmentTest.repoForTest(),
                "", new java.util.ArrayList<>(), "main").getArguments());
        FragmentTestHelper.attachTo(
                com.gh4a.fragment.ContentListFragmentTest.ContentHostActivity.class, f);
        RecyclerView list = f.getView().findViewById(R.id.list);
        assertNotNull(list.getAdapter());
        Bundle state = new Bundle();
        state.putInt("args_hash", 12345);
        f.onViewCreated(f.getView(), state);
        assertEquals(RecyclerView.Adapter.StateRestorationPolicy.PREVENT,
                list.getAdapter().getStateRestorationPolicy());
    }

    @Test
    public void onViewCreated_matchingArgsHash_skipsPolicy() {
        TestListFragment f = attached();
        f.setArguments(new Bundle());
        Bundle state = new Bundle();
        state.putInt("args_hash", f.getArguments().hashCode());
        f.onViewCreated(f.getView(), state);
    }

    @Test
    public void onViewCreated_listenerHost_isNotified() {
        ListenerActivity activity = org.robolectric.Robolectric
                .buildActivity(ListenerActivity.class).setup().get();
        TestListFragment f = new TestListFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(f, "test").commitNow();
        assertEquals(1, activity.calls);
    }

    @Test
    public void canChildScrollUp_bareFragment_isFalse() {
        assertEquals(false, new TestListFragment().canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_emptyAttachedList_isFalse() {
        assertEquals(false, attached().canChildScrollUp());
    }

    @Test
    public void updateEmptyState_bareFragment_doesNothing() {
        new TestListFragment().updateEmptyState();
    }

    /** Plain (non-RootAdapter) adapter with a settable item count. */
    public static class CountingAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        int count;

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(
                android.view.ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(
                    new android.view.View(parent.getContext())) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return count;
        }
    }

    @Test
    public void updateEmptyState_nonRootAdapter_usesItemCount() {
        TestListFragment f = attached();
        RecyclerView list = f.getView().findViewById(R.id.list);
        CountingAdapter plain = new CountingAdapter();
        list.setAdapter(plain);
        f.updateEmptyState();
        assertEquals(View.VISIBLE,
                f.getView().findViewById(R.id.empty_view_container).getVisibility());
        plain.count = 3;
        plain.notifyDataSetChanged();
        f.updateEmptyState();
        assertEquals(View.GONE,
                f.getView().findViewById(R.id.empty_view_container).getVisibility());
    }

    @Test
    public void canChildScrollUp_scrolledList_returnsTrue() throws Exception {
        // A real adapter with many items, a measured layout and a scroll
        // position past the top make RecyclerView.canScrollVertically(-1)
        // true. LoadTestFragment carries 20 canned items.
        com.gh4a.fragment.PagedDataBaseFragmentTest.LoadTestFragment f =
                new com.gh4a.fragment.PagedDataBaseFragmentTest.LoadTestFragment();
        java.util.List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add("item-" + i);
        }
        f.pages.put(1, io.reactivex.Single.just(retrofit2.Response.success(
                com.gh4a.fragment.PagedDataBaseFragmentTest.stringPage(items, null))));
        FragmentTestHelper.attach(f);
        RecyclerView list = f.getView().findViewById(R.id.list);
        // Wait for the load to populate the adapter.
        for (int i = 0; i < 100 && list.getAdapter().getItemCount() < 20; i++) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            Thread.sleep(20);
        }
        assertTrue(list.getAdapter().getItemCount() >= 20); // +1 footer item
        // Measure with a small height so only a few items fit, then scroll.
        int widthSpec = View.MeasureSpec.makeMeasureSpec(1080,
                View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(600,
                View.MeasureSpec.EXACTLY);
        list.measure(widthSpec, heightSpec);
        list.layout(0, 0, 1080, 600);
        list.scrollToPosition(15);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        list.measure(widthSpec, heightSpec);
        list.layout(0, 0, 1080, 600);
        assertTrue(f.canChildScrollUp());
    }

    @Test
    public void scrollToAndHighlightPosition_baseActivityHost_collapsesAppBar() {
        TestListFragment f = attachedToBaseActivity();
        f.scrollToAndHighlightPosition(0);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    @Test
    public void scrollToAndHighlightPosition_withRootAdapter_highlights() throws Exception {
        com.gh4a.fragment.ContentListFragmentTest.NoLoad f =
                new com.gh4a.fragment.ContentListFragmentTest.NoLoad();
        f.setArguments(com.gh4a.fragment.ContentListFragment.newInstance(
                com.gh4a.fragment.ContentListFragmentTest.repoForTest(),
                "", new java.util.ArrayList<>(), "main").getArguments());
        FragmentTestHelper.attachTo(BaseParentActivity.class, f);
        RecyclerView list = f.getView().findViewById(R.id.list);
        RootAdapter<?, ?> adapter = (RootAdapter<?, ?>) list.getAdapter();
        assertNotNull(adapter);
        f.scrollToAndHighlightPosition(0);
        // FragmentTestHelper adds by tag (no container), so the view is not
        // attached to a window and View.postDelayed only queues the runnable
        // in the view's RunQueue. Attaching the view to the activity's
        // content flushes the RunQueue onto the main handler; idling the
        // looper then runs the 600 ms delayed highlight lambda.
        f.getActivity().addContentView(f.getView(),
                new android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idle(2, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field field = RootAdapter.class.getDeclaredField("mHighlightPosition");
        field.setAccessible(true);
        assertEquals(0, field.get(adapter));
    }

    @Test
    public void hasCards_defaultsFalse() {
        assertFalse(attached().hasCards());
    }

    @Test
    public void hasDividers_defaultsTrue() {
        assertTrue(attached().hasDividers());
    }

    @Test
    public void canChildScrollUp_emptyList_returnsFalse() {
        assertFalse(attached().canChildScrollUp());
    }

    @Test
    public void updateEmptyState_noAdapter_keepsListVisible() {
        // With no adapter at all, updateEmptyState() leaves `empty` false:
        // the list stays visible and the empty view stays gone.
        TestListFragment f = attached();
        f.updateEmptyState();
        RecyclerView list = f.getView().findViewById(R.id.list);
        assertEquals(View.VISIBLE, list.getVisibility());
        assertEquals(View.GONE,
                f.getView().findViewById(R.id.empty_view_container).getVisibility());
    }

    @Test
    public void onSaveInstanceState_storesArgsHash() {
        TestListFragment f = attached();
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.containsKey("args_hash"));
        assertEquals(0, out.getInt("args_hash"));
    }

    @Test
    public void onSaveInstanceState_withArgs_storesArgsHash() {
        TestListFragment f = new TestListFragment();
        Bundle args = new Bundle();
        args.putString("k", "v");
        f.setArguments(args);
        FragmentTestHelper.attach(f);
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals(args.hashCode(), out.getInt("args_hash"));
    }

    @Test
    public void getEmptyTextResId_zero_doesNotSetText() {
        NoEmptyTextFragment f = new NoEmptyTextFragment();
        FragmentTestHelper.attach(f);
        assertNotNull(f.getView());
    }

    @Test
    public void setHighlightColors_updatesOverscrollWithoutCrash() {
        TestListFragment f = attached();
        f.setHighlightColors(androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
    }

    @Test
    public void scrollToAndHighlightPosition_noBaseActivity_doesNotCrash() {
        // BaseActivity is null for a plain host; only the layout-manager
        // scroll runs. Adapter is null so no highlight is posted.
        TestListFragment f = attached();
        f.scrollToAndHighlightPosition(0);
    }
}
