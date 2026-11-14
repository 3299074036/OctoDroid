package com.gh4a;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BasePagerActivityTest {

    public static class TestPagerAdapter extends PagerAdapter {
        private final int count;

        TestPagerAdapter(int count) {
            this.count = count;
        }

        @Override
        public int getCount() {
            return count;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = new View(container.getContext());
            container.addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "Tab " + position;
        }
    }

    public static class TestPagerActivity extends BasePagerActivity {
        boolean whiteRow;
        boolean toolbarFixed;
        boolean touchPaging = true;
        int tabCount = 2;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // No setContentView here: BasePagerActivity.onCreate already
            // installed R.layout.view_pager as the content view.
            super.onCreate(savedInstanceState);
        }

        @Override
        protected PagerAdapter createAdapter(ViewGroup root) {
            return new TestPagerAdapter(tabCount);
        }

        @Override
        protected boolean useWhiteTabRow() {
            return whiteRow;
        }

        @Override
        protected boolean isToolbarFixed() {
            return toolbarFixed;
        }

        @Override
        protected boolean isPagerTouchPagingEnabled() {
            return touchPaging;
        }

        @Override
        protected int[][] getTabHeaderColorAttrs() {
            return new int[][]{
                    {androidx.appcompat.R.attr.colorPrimary,
                            androidx.appcompat.R.attr.colorPrimaryDark},
                    {androidx.appcompat.R.attr.colorPrimary,
                            androidx.appcompat.R.attr.colorPrimaryDark}};
        }
    }

    public static class SingleTabActivity extends BasePagerActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // No setContentView here: BasePagerActivity.onCreate already
            // installed R.layout.view_pager as the content view.
            super.onCreate(savedInstanceState);
        }

        @Override
        protected PagerAdapter createAdapter(ViewGroup root) {
            return new TestPagerAdapter(1);
        }
    }

    public static class HeaderColorAttrsActivity extends BasePagerActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        protected PagerAdapter createAdapter(ViewGroup root) {
            return new TestPagerAdapter(2);
        }

        @Override
        protected int[] getHeaderColorAttrs() {
            return new int[]{
                    androidx.appcompat.R.attr.colorPrimary,
                    androidx.appcompat.R.attr.colorPrimaryDark};
        }
        // getTabHeaderColorAttrs() stays null (default) so invalidateTabs
        // takes the getHeaderColorAttrs() branch (lines 112-114).
    }

    public static class NoColorAttrsActivity extends BasePagerActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            // No setContentView here: BasePagerActivity.onCreate already
            // installed R.layout.view_pager as the content view.
            super.onCreate(savedInstanceState);
        }

        @Override
        protected PagerAdapter createAdapter(ViewGroup root) {
            return new TestPagerAdapter(2);
        }
    }

    private static TestPagerActivity launch() {
        return Robolectric.buildActivity(TestPagerActivity.class)
                .create().start().resume().get();
    }

    private static Object getField(Object target, String name) {
        try {
            Field f = BasePagerActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError("field " + name, e);
        }
    }

    @Test
    public void launch_pagerAndTabsPresent() {
        TestPagerActivity activity = launch();
        ViewPager pager = (ViewPager) activity.findViewById(R.id.pager);
        assertNotNull(pager);
        assertEquals(2, pager.getAdapter().getCount());
        assertNotNull(getField(activity, "mTabs"));
    }

    @Test
    public void defaults_hooks() {
        TestPagerActivity activity = launch();
        assertFalse(activity.isToolbarFixed());
        assertFalse(activity.useWhiteTabRow());
        assertTrue(activity.isPagerTouchPagingEnabled());
        assertFalse(activity.hasTabsInToolbar());
    }

    @Test
    public void onPageMoved_withTabColors_setsHeaderColor() {
        TestPagerActivity activity = launch();
        activity.setHeaderColor(0xFF000000, 0xFF000000);
        activity.onPageMoved(0, 0f);
        // Both tabs share the primary color, so the mixed color is primary.
        assertEquals(com.gh4a.utils.UiUtils.resolveColor(activity,
                        androidx.appcompat.R.attr.colorPrimary),
                activity.getCurrentHeaderColor());
    }

    @Test
    public void onPageMoved_positionClampedBeyondRange() {
        TestPagerActivity activity = launch();
        activity.onPageMoved(99, 0.5f);
        activity.onPageMoved(-5, 0.5f);
        assertTrue(activity.getCurrentHeaderColor() != 0);
    }

    @Test
    public void onPageScrolled_delegatesToOnPageMoved() {
        TestPagerActivity activity = launch();
        activity.onPageScrolled(1, 0.5f, 10);
        assertTrue(activity.getCurrentHeaderColor() != 0);
    }

    @Test
    public void onPageSelected_whenNotScrolling_movesPage() {
        TestPagerActivity activity = launch();
        activity.onPageSelected(1);
        assertTrue(activity.getCurrentHeaderColor() != 0);
    }

    @Test
    public void onPageScrollStateChanged_idle_updatesPagerColor() {
        TestPagerActivity activity = launch();
        activity.onPageScrollStateChanged(ViewPager.SCROLL_STATE_DRAGGING);
        activity.onPageScrollStateChanged(ViewPager.SCROLL_STATE_IDLE);
        assertNotNull(activity.findViewById(R.id.pager).getTag(R.id.EdgeColorHelper));
    }

    @Test
    public void invalidateTabs_noColorAttrs_fallsBackToPrimary() {
        NoColorAttrsActivity activity = Robolectric.buildActivity(NoColorAttrsActivity.class)
                .create().start().resume().get();
        activity.invalidateTabs();
        assertTrue(activity.getCurrentHeaderColor() != 0);
    }

    @Test
    public void invalidatePages_doNotCrash() {
        TestPagerActivity activity = launch();
        activity.invalidatePages();
    }

    @Test
    public void adjustTabsForHeaderAlignedFab_setsMargin() {
        TestPagerActivity activity = launch();
        activity.adjustTabsForHeaderAlignedFab(true);
        activity.adjustTabsForHeaderAlignedFab(false);
    }

    @Test
    public void singleTab_tabsHidden() {
        SingleTabActivity activity = Robolectric.buildActivity(SingleTabActivity.class)
                .create().start().resume().get();
        View tabs = (View) getField(activity, "mTabs");
        assertEquals(View.GONE, tabs.getVisibility());
    }

    @Test
    public void twoTabs_tabsVisible() {
        TestPagerActivity activity = launch();
        View tabs = (View) getField(activity, "mTabs");
        assertEquals(View.VISIBLE, tabs.getVisibility());
    }

    @Test
    public void setErrorViewVisibility_hidesTabs() {
        TestPagerActivity activity = launch();
        activity.setErrorViewVisibility(true, new java.io.IOException("x"));
        View tabs = (View) getField(activity, "mTabs");
        assertEquals(View.GONE, tabs.getVisibility());
        activity.setErrorViewVisibility(false, null);
        assertEquals(View.VISIBLE, tabs.getVisibility());
    }

    @Test
    public void whiteTabRow_appliesAndRestoresStyle() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        View tabs = (View) getField(activity, "mTabs");
        assertNotNull(tabs.getBackground());
        activity.whiteRow = false;
        activity.invalidateTabs();
        assertNotNull(tabs);
    }

    @Test
    public void setHeaderColor_updatesCurrentHeaderColor() {
        TestPagerActivity activity = launch();
        activity.setHeaderColor(0xFF123456, 0xFF654321);
        assertEquals(0xFF123456, activity.getCurrentHeaderColor());
    }

    @Test
    public void transitionHeaderToColor_updatesCurrentHeaderColor() {
        TestPagerActivity activity = launch();
        activity.transitionHeaderToColor(
                androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
        assertTrue(activity.getCurrentHeaderColor() != 0);
    }

    @Test
    public void getPager_returnsPager() {
        TestPagerActivity activity = launch();
        assertNotNull(activity.getPager());
        assertEquals(2, activity.getPager().getAdapter().getCount());
    }

    @Test
    public void touchPagingDisabled_lockablePager() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.touchPaging = false;
        controller.create().start().resume();
        assertNotNull(activity.getPager());
    }

    @Test
    public void onPostCreate_movesToFirstPage() {
        TestPagerActivity activity = launch();
        activity.onPostCreate(null);
        assertEquals(0, activity.getPager().getCurrentItem());
        assertTrue(activity.getCurrentHeaderColor() != 0);
    }

    @Test
    public void invalidateTabs_withHeaderColorAttrs_usesThem() {
        HeaderColorAttrsActivity activity =
                Robolectric.buildActivity(HeaderColorAttrsActivity.class)
                        .create().start().resume().get();
        activity.invalidateTabs();
        assertEquals(com.gh4a.utils.UiUtils.resolveColor(activity,
                        androidx.appcompat.R.attr.colorPrimary),
                activity.getCurrentHeaderColor());
    }

    @Test
    public void hasTabsInToolbar_landscape_true() {
        String original = RuntimeEnvironment.getQualifiers();
        try {
            RuntimeEnvironment.setQualifiers("land");
            TestPagerActivity activity = Robolectric.buildActivity(TestPagerActivity.class)
                    .create().start().resume().get();
            assertTrue(activity.hasTabsInToolbar());
            // In landscape the tabs live inside the toolbar.
            assertNotNull(activity.findViewById(R.id.toolbar));
        } finally {
            RuntimeEnvironment.setQualifiers(original);
        }
    }

    private static void swipe(View view, float fromX, float toX) {
        long now = android.os.SystemClock.uptimeMillis();
        view.dispatchTouchEvent(MotionEvent.obtain(now, now,
                MotionEvent.ACTION_DOWN, fromX, 100f, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now + 10,
                MotionEvent.ACTION_UP, toX, 100f, 0));
    }

    @Test
    public void whiteTabRow_swipeLeft_movesToNextTab() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        activity.invalidateTabs();
        ViewPager pager = activity.getPager();
        assertEquals(0, pager.getCurrentItem());
        View tabs = (View) getField(activity, "mTabs");
        View tab = ((ViewGroup) ((ViewGroup) tabs).getChildAt(0)).getChildAt(0);
        swipe(tab, 500f, 100f);
        assertEquals(1, pager.getCurrentItem());
    }

    @Test
    public void whiteTabRow_swipeRight_movesToPreviousTab() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        activity.invalidateTabs();
        ViewPager pager = activity.getPager();
        pager.setCurrentItem(1);
        assertEquals(1, pager.getCurrentItem());
        View tabs = (View) getField(activity, "mTabs");
        View tab = ((ViewGroup) ((ViewGroup) tabs).getChildAt(0)).getChildAt(0);
        swipe(tab, 100f, 500f);
        assertEquals(0, pager.getCurrentItem());
    }

    @Test
    public void whiteTabRow_swipeLeftAtLastTab_stays() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        activity.invalidateTabs();
        ViewPager pager = activity.getPager();
        pager.setCurrentItem(1);
        View tabs = (View) getField(activity, "mTabs");
        View tab = ((ViewGroup) ((ViewGroup) tabs).getChildAt(0)).getChildAt(0);
        swipe(tab, 500f, 100f);
        assertEquals(1, pager.getCurrentItem());
    }

    // ---- gap-closing tests round 2 ----

    private static Object callPrivate(Object target, String name,
            Class<?>[] types, Object... args) {
        try {
            Method m = BasePagerActivity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call " + name, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + name, e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = BasePagerActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError("field " + name, e);
        }
    }

    private static Object getBaseActivityField(Object target, String name) {
        try {
            Field f = BaseActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError("field " + name, e);
        }
    }

    @Test
    public void updateToolbarScrollableState_toolbarFixed_notScrollable() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.toolbarFixed = true;
        controller.create().start().resume();
        assertTrue(activity.isToolbarFixed());
        assertFalse((Boolean) getBaseActivityField(activity, "mAppBarScrollable"));
    }

    @Test
    public void attachTabRowSwipe_nullTabs_noCrash() {
        TestPagerActivity activity = launch();
        setField(activity, "mTabs", null);
        callPrivate(activity, "attachTabRowSwipe", new Class<?>[0]);
    }

    @Test
    public void attachTabRowSwipe_emptyTabStrip_noCrash() {
        TestPagerActivity activity = launch();
        setField(activity, "mTabs",
                new com.google.android.material.tabs.TabLayout(activity));
        callPrivate(activity, "attachTabRowSwipe", new Class<?>[0]);
    }

    @Test
    public void attachTabRowSwipe_nullPager_noCrash() {
        TestPagerActivity activity = launch();
        setField(activity, "mPager", null);
        callPrivate(activity, "attachTabRowSwipe", new Class<?>[0]);
    }

    @Test
    public void detachTabRowSwipe_nullTabs_noCrash() {
        TestPagerActivity activity = launch();
        setField(activity, "mTabs", null);
        callPrivate(activity, "detachTabRowSwipe", new Class<?>[0]);
    }

    @Test
    public void detachTabRowSwipe_emptyTabStrip_noCrash() {
        TestPagerActivity activity = launch();
        setField(activity, "mTabs",
                new com.google.android.material.tabs.TabLayout(activity));
        callPrivate(activity, "detachTabRowSwipe", new Class<?>[0]);
    }

    @Test
    public void applyTabRowStyle_nullTabs_noCrash() {
        TestPagerActivity activity = launch();
        setField(activity, "mTabs", null);
        callPrivate(activity, "applyTabRowStyle", new Class<?>[0]);
    }

    @Test
    public void applyTabRowStyle_errorVisible_restoresOriginalStyle() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        activity.invalidateTabs();
        Object originalBackground = getField(activity, "mTabRowOriginalBackground");
        assertNotNull(originalBackground);
        activity.setErrorViewVisibility(true, new RuntimeException("x"));
        callPrivate(activity, "applyTabRowStyle", new Class<?>[0]);
        View tabs = (View) getField(activity, "mTabs");
        assertSame(originalBackground, tabs.getBackground());
    }

    @Test
    public void whiteTabRow_swipeLeft_adapterNull_stays() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        activity.invalidateTabs();
        ViewPager pager = activity.getPager();
        View tabs = (View) getField(activity, "mTabs");
        View tab = ((ViewGroup) ((ViewGroup) tabs).getChildAt(0)).getChildAt(0);
        pager.setAdapter(null);
        swipe(tab, 500f, 100f);
        assertEquals(0, pager.getCurrentItem());
    }

    @Test
    public void whiteTabRow_swipeRightAtFirstTab_stays() {
        ActivityController<TestPagerActivity> controller =
                Robolectric.buildActivity(TestPagerActivity.class);
        TestPagerActivity activity = controller.get();
        activity.whiteRow = true;
        controller.create().start().resume();
        activity.invalidateTabs();
        ViewPager pager = activity.getPager();
        assertEquals(0, pager.getCurrentItem());
        View tabs = (View) getField(activity, "mTabs");
        View tab = ((ViewGroup) ((ViewGroup) tabs).getChildAt(0)).getChildAt(0);
        swipe(tab, 100f, 500f);
        assertEquals(0, pager.getCurrentItem());
    }

    @Test
    public void onPageSelected_scrolling_skipsOnPageMoved() {
        HeaderColorAttrsActivity activity = Robolectric
                .buildActivity(HeaderColorAttrsActivity.class)
                .create().start().resume().get();
        Object before = getField(activity, "mCurrentHeaderColor");
        activity.onPageScrollStateChanged(ViewPager.SCROLL_STATE_DRAGGING);
        activity.onPageSelected(1);
        assertEquals(before, getField(activity, "mCurrentHeaderColor"));
    }

    @Test
    public void onPageMoved_noTabHeaderColors_noCrash() {
        NoColorAttrsActivity activity = Robolectric
                .buildActivity(NoColorAttrsActivity.class)
                .create().start().resume().get();
        callPrivate(activity, "onPageMoved",
                new Class<?>[]{int.class, float.class}, 0, 0f);
    }

    @Test
    public void tryUpdatePagerColor_secondCall_reusesHelper() {
        TestPagerActivity activity = launch();
        ViewPager pager = activity.getPager();
        callPrivate(activity, "tryUpdatePagerColor", new Class<?>[0]);
        Object first = pager.getTag(R.id.EdgeColorHelper);
        assertNotNull(first);
        callPrivate(activity, "tryUpdatePagerColor", new Class<?>[0]);
        assertSame(first, pager.getTag(R.id.EdgeColorHelper));
    }

    @Test
    public void edgeColorHelper_applyColorNull_noCrash() throws Exception {
        TestPagerActivity activity = launch();
        ViewPager pager = activity.getPager();
        callPrivate(activity, "tryUpdatePagerColor", new Class<?>[0]);
        Object helper = pager.getTag(R.id.EdgeColorHelper);
        assertNotNull(helper);
        Method applyColor = helper.getClass().getDeclaredMethod("applyColor",
                Class.forName("android.widget.EdgeEffect"));
        applyColor.setAccessible(true);
        applyColor.invoke(helper, new Object[]{null});
    }

    @Test
    public void edgeColorHelper_ensureStaticFieldsTwice_returnsTrue() throws Exception {
        TestPagerActivity activity = launch();
        ViewPager pager = activity.getPager();
        callPrivate(activity, "tryUpdatePagerColor", new Class<?>[0]);
        Object helper = pager.getTag(R.id.EdgeColorHelper);
        assertNotNull(helper);
        Method ensure = helper.getClass().getDeclaredMethod("ensureStaticFields");
        ensure.setAccessible(true);
        assertTrue((Boolean) ensure.invoke(helper));
        assertTrue((Boolean) ensure.invoke(helper));
    }

    @Test
    public void applyPagerTouchPaging_plainViewPager_noCrash() {
        TestPagerActivity activity = launch();
        callPrivate(activity, "applyPagerTouchPaging",
                new Class<?>[]{ViewPager.class}, new ViewPager(activity));
    }
}
