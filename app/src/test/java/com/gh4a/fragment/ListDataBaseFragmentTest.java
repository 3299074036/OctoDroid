package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.adapter.RootAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * ListDataBaseFragment: the abstract base for list fragments that load
 * their data as a Single or an Observable of pages. Exercised through a
 * minimal concrete subclass; loading paths are reached without
 * subscribing a network load (Single.never() / initial data only).
 */
@RunWith(RobolectricTestRunner.class)
public class ListDataBaseFragmentTest {

    /** Minimal concrete subclass: never emits, safe to attach offline. */
    public static class TestableList extends ListDataBaseFragment<String> {
        boolean useRealInitialData;
        List<String> initialData;

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

    /** Bare subclass that overrides nothing: exposes the base defaults. */
    public static class DefaultsList extends ListDataBaseFragment<String> {
        @Override
        protected int getEmptyTextResId() {
            return 0;
        }

        @Override
        protected RootAdapter<String, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
            return null;
        }
    }

    private static TestableList attached() {
        TestableList f = new TestableList();
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
    public void defaults_dataSourcesAreNull() {
        DefaultsList f = new DefaultsList();
        assertNull(f.onCreateDataSingle(false));
        assertNull(f.onCreateDataObservable(false));
        assertNull(f.onGetInitialData());
    }

    @Test
    public void onAddData_addsAllAndNotifies() {
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        new TestableList().onAddData(adapter, Arrays.asList("a", "b"));
        verify(adapter).addAll(Arrays.asList("a", "b"));
        verify(adapter).notifyDataSetChanged();
    }

    @Test
    public void onRecyclerViewInflated_assignsAdapterFromFactory() {
        TestableList f = attached();
        assertEquals(f.adapter, field(f, ListDataBaseFragment.class, "mAdapter"));
    }

    @Test
    public void onDestroyView_withSubscription_disposesAndNulls() {
        TestableList f = attached();
        assertNotNull(field(f, ListDataBaseFragment.class, "mSubscription"));
        f.getActivity().getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        assertNull(field(f, ListDataBaseFragment.class, "mSubscription"));
        assertNull(field(f, ListDataBaseFragment.class, "mAdapter"));
    }

    @Test
    public void onDestroyView_withoutSubscription_doesNothing() {
        new TestableList().onDestroyView();
    }

    @Test
    public void onRefresh_disposesAndResubscribes() {
        TestableList f = attached();
        Object before = field(f, ListDataBaseFragment.class, "mSubscription");
        assertNotNull(before);
        f.onRefresh();
        Object after = field(f, ListDataBaseFragment.class, "mSubscription");
        assertNotNull(after);
        assertTrue(before != after);
    }

    @Test
    public void onRefresh_withAdapter_clearsAdapter() {
        TestableList f = attached();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        f.onRefresh();
        verify(adapter).clear();
    }

    @Test
    public void onRefresh_noSubscriptionNoAdapter_nullBranches() {
        TestableList f = attached();
        setField(f, ListDataBaseFragment.class, "mSubscription", null);
        setField(f, ListDataBaseFragment.class, "mAdapter", null);
        f.onRefresh();
        assertNotNull(field(f, ListDataBaseFragment.class, "mSubscription"));
    }

    @Test
    public void hasDividers_combos() {
        TestableList f = new TestableList();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        when(adapter.hasDividers()).thenReturn(false);
        assertFalse(f.hasDividers());
        when(adapter.hasDividers()).thenReturn(true);
        when(adapter.isCardStyle()).thenReturn(true);
        assertFalse(f.hasDividers());
        when(adapter.isCardStyle()).thenReturn(false);
        assertTrue(f.hasDividers());
    }

    @Test
    public void hasCards_followsAdapterCardStyle() {
        TestableList f = new TestableList();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        when(adapter.isCardStyle()).thenReturn(false);
        assertFalse(f.hasCards());
        when(adapter.isCardStyle()).thenReturn(true);
        assertTrue(f.hasCards());
    }

    @Test
    public void loadData_withInitialData_rendersAndShowsContent() {
        TestableList f = attached();
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
    public void handleNewPage_rendersCumulativeItems() {
        TestableList f = attached();
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
    public void handleAllPagesLoaded_showsContent() {
        TestableList f = attached();
        invoke(f, ListDataBaseFragment.class, "handleAllPagesLoaded",
                new Class<?>[0], new Object[0]);
        assertTrue(f.isContentShown());
    }

    @Test
    public void handlePagedLoadFailure_withData_logsOnly() {
        TestableList f = attached();
        @SuppressWarnings("unchecked")
        RootAdapter<String, RecyclerView.ViewHolder> adapter = mock(RootAdapter.class);
        when(adapter.getCount()).thenReturn(2);
        setField(f, ListDataBaseFragment.class, "mAdapter", adapter);
        invoke(f, ListDataBaseFragment.class, "handlePagedLoadFailure",
                new Class<?>[] { Throwable.class },
                new Object[] { new RuntimeException("page 2 failed") });
    }
}
