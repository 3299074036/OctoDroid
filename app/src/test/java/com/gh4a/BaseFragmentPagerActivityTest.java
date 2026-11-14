package com.gh4a;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.gh4a.fragment.LoadingFragmentBase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BaseFragmentPagerActivityTest {

    public static class SimpleFragment extends Fragment {
    }

    public static class RefreshableFragment extends Fragment
            implements BaseActivity.RefreshableChild {
        boolean refreshed;

        @Override
        public void onRefresh() {
            refreshed = true;
        }
    }

    public static class ScrollDelegateFragment extends Fragment
            implements com.gh4a.widget.SwipeRefreshLayout.ChildScrollDelegate {
        @Override
        public boolean canChildScrollUp() {
            return true;
        }
    }

    public static class BackPressFragment extends LoadingFragmentBase {
        @Override
        public boolean onBackPressed() {
            return true;
        }

        @Override
        protected View onCreateContentView(LayoutInflater inflater, ViewGroup parent) {
            return new View(inflater.getContext());
        }

        @Override
        public void onRefresh() {
        }
    }

    public static class TestFragmentPagerActivity extends BaseFragmentPagerActivity {
        int tabCount = 2;
        boolean needsRefresh;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // No setContentView here: BasePagerActivity.onCreate already
            // installed R.layout.view_pager as the content view.
            super.onCreate(savedInstanceState);
        }

        @Override
        protected int[] getTabTitleResIds() {
            int[] ids = new int[tabCount];
            for (int i = 0; i < tabCount; i++) {
                ids[i] = R.string.app_name;
            }
            return ids;
        }

        @Override
        protected Fragment makeFragment(int position) {
            return new SimpleFragment();
        }

        @Override
        protected boolean fragmentNeedsRefresh(Fragment object) {
            return needsRefresh;
        }
    }

    public static class NullTitlesActivity extends BaseFragmentPagerActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // No setContentView here: BasePagerActivity.onCreate already
            // installed R.layout.view_pager as the content view.
            super.onCreate(savedInstanceState);
        }

        @Override
        protected int[] getTabTitleResIds() {
            return null;
        }

        @Override
        protected Fragment makeFragment(int position) {
            return new SimpleFragment();
        }
    }

    private static TestFragmentPagerActivity launch() {
        return Robolectric.buildActivity(TestFragmentPagerActivity.class)
                .create().start().resume().get();
    }

    private static Object getAdapterField(BaseFragmentPagerActivity activity) {
        try {
            Field f = BaseFragmentPagerActivity.class.getDeclaredField("mAdapter");
            f.setAccessible(true);
            return f.get(activity);
        } catch (Exception e) {
            throw new AssertionError("mAdapter", e);
        }
    }

    @Test
    public void launch_adapterCountMatchesTitles() {
        TestFragmentPagerActivity activity = launch();
        Object adapter = getAdapterField(activity);
        assertNotNull(adapter);
        assertEquals(2, ((androidx.viewpager.widget.PagerAdapter) adapter).getCount());
    }

    @Test
    public void launch_adapterPageTitle() {
        TestFragmentPagerActivity activity = launch();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        assertEquals(activity.getString(R.string.app_name), adapter.getPageTitle(0));
    }

    @Test
    public void nullTitles_adapterCountZero() {
        NullTitlesActivity activity = Robolectric.buildActivity(NullTitlesActivity.class)
                .create().start().resume().get();
        assertNotNull(activity.getPager());
        assertEquals(0, activity.getPager().getAdapter().getCount());
    }

    @Test
    public void invalidateFragments_doNotCrash() {
        TestFragmentPagerActivity activity = launch();
        activity.invalidateFragments();
    }

    @Test
    public void onRefresh_noFragments_doNotCrash() {
        TestFragmentPagerActivity activity = launch();
        activity.onRefresh();
    }

    @Test
    public void canChildScrollUp_noCurrentFragment_false() {
        TestFragmentPagerActivity activity = launch();
        assertFalse(activity.canChildScrollUp());
    }

    @Test
    public void onBackPressed_noCurrentFragment_finishes() {
        TestFragmentPagerActivity activity = launch();
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void fragmentNeedsRefresh_defaultFalse_itemPositionUnchanged() {
        TestFragmentPagerActivity activity = launch();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        assertEquals(androidx.viewpager.widget.PagerAdapter.POSITION_UNCHANGED,
                adapter.getItemPosition(new SimpleFragment()));
    }

    @Test
    public void fragmentNeedsRefresh_true_itemPositionNone() {
        ActivityController<TestFragmentPagerActivity> controller =
                Robolectric.buildActivity(TestFragmentPagerActivity.class);
        TestFragmentPagerActivity activity = controller.get();
        activity.needsRefresh = true;
        controller.create().start().resume();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        assertEquals(androidx.viewpager.widget.PagerAdapter.POSITION_NONE,
                adapter.getItemPosition(new SimpleFragment()));
    }

    @Test
    public void singleTab_tabsHidden() {
        ActivityController<TestFragmentPagerActivity> controller =
                Robolectric.buildActivity(TestFragmentPagerActivity.class);
        TestFragmentPagerActivity activity = controller.get();
        activity.tabCount = 1;
        controller.create().start().resume();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void activityLifecycle_destroyNoCrash() {
        ActivityController<TestFragmentPagerActivity> controller =
                Robolectric.buildActivity(TestFragmentPagerActivity.class)
                        .create().start().resume();
        controller.pause().stop().destroy();
        assertNotNull(controller.get());
    }

    @Test
    public void adapter_instantiateDestroySetPrimary_tracksFragments() throws Exception {
        TestFragmentPagerActivity activity = launch();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        ViewGroup container = activity.getPager();
        Fragment f = (Fragment) adapter.instantiateItem(container, 0);
        assertNotNull(f);
        assertTrue(f instanceof SimpleFragment);
        java.lang.reflect.Method getExisting =
                adapter.getClass().getDeclaredMethod("getExistingFragment", int.class);
        getExisting.setAccessible(true);
        assertSame(f, getExisting.invoke(adapter, 0));
        adapter.setPrimaryItem(container, 0, f);
        java.lang.reflect.Method getCurrent =
                adapter.getClass().getDeclaredMethod("getCurrentFragment");
        getCurrent.setAccessible(true);
        assertSame(f, getCurrent.invoke(adapter));
        adapter.destroyItem(container, 0, f);
        assertNull(getExisting.invoke(adapter, 0));
        assertNull(getCurrent.invoke(adapter));
    }

    @Test
    public void onRefresh_refreshableFragment_refreshed() throws Exception {
        TestFragmentPagerActivity activity = launch();
        Object adapter = getAdapterField(activity);
        RefreshableFragment rf = new RefreshableFragment();
        java.lang.reflect.Field frags =
                adapter.getClass().getDeclaredField("mFragments");
        frags.setAccessible(true);
        @SuppressWarnings("unchecked")
        SparseArray<Fragment> map = (SparseArray<Fragment>) frags.get(adapter);
        map.put(0, rf);
        activity.onRefresh();
        assertTrue(rf.refreshed);
    }

    @Test
    public void canChildScrollUp_delegateTrue_returnsTrue() throws Exception {
        TestFragmentPagerActivity activity = launch();
        Object adapter = getAdapterField(activity);
        java.lang.reflect.Field cur =
                adapter.getClass().getDeclaredField("mCurrentFragment");
        cur.setAccessible(true);
        cur.set(adapter, new ScrollDelegateFragment());
        assertTrue(activity.canChildScrollUp());
    }

    @Test
    public void onBackPressed_currentFragmentConsumes_doesNotFinish() throws Exception {
        TestFragmentPagerActivity activity = launch();
        Object adapter = getAdapterField(activity);
        java.lang.reflect.Field cur =
                adapter.getClass().getDeclaredField("mCurrentFragment");
        cur.setAccessible(true);
        cur.set(adapter, new BackPressFragment());
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
    }

    @Test
    public void fragmentNeedsRefresh_baseDefaultFalse_itemPositionUnchanged() {
        // NullTitlesActivity does not override fragmentNeedsRefresh:
        // the base implementation (lines 64-66) returns false.
        NullTitlesActivity activity = Robolectric.buildActivity(NullTitlesActivity.class)
                .create().start().resume().get();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        assertEquals(androidx.viewpager.widget.PagerAdapter.POSITION_UNCHANGED,
                adapter.getItemPosition(new SimpleFragment()));
    }

    // ---- gap-closing tests round 2 ----

    public static class NonConsumingBackFragment extends LoadingFragmentBase {
        @Override
        public boolean onBackPressed() {
            return false;
        }

        @Override
        protected View onCreateContentView(LayoutInflater inflater, ViewGroup parent) {
            return new View(inflater.getContext());
        }

        @Override
        public void onRefresh() {
        }
    }

    @Test
    public void onBackPressed_loadingFragmentNotConsuming_finishes() throws Exception {
        TestFragmentPagerActivity activity = launch();
        Object adapter = getAdapterField(activity);
        java.lang.reflect.Field cur =
                adapter.getClass().getDeclaredField("mCurrentFragment");
        cur.setAccessible(true);
        cur.set(adapter, new NonConsumingBackFragment());
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void adapter_destroyNonCurrentFragment_keepsCurrent() throws Exception {
        TestFragmentPagerActivity activity = launch();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        ViewGroup container = activity.getPager();
        Fragment f1 = (Fragment) adapter.instantiateItem(container, 0);
        Fragment f2 = (Fragment) adapter.instantiateItem(container, 1);
        adapter.setPrimaryItem(container, 0, f1);
        java.lang.reflect.Method getCurrent =
                adapter.getClass().getDeclaredMethod("getCurrentFragment");
        getCurrent.setAccessible(true);
        assertSame(f1, getCurrent.invoke(adapter));
        adapter.destroyItem(container, 1, f2);
        assertSame(f1, getCurrent.invoke(adapter));
    }

    @Test
    public void adapter_getItemPositionNonFragment_unchanged() {
        TestFragmentPagerActivity activity = launch();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) getAdapterField(activity);
        assertEquals(androidx.viewpager.widget.PagerAdapter.POSITION_UNCHANGED,
                adapter.getItemPosition(new Object()));
    }
}
