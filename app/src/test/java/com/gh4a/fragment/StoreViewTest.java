package com.gh4a.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** StoreView：软件商店视图（Robolectric；构造 + 无网络操作）。 */
@RunWith(RobolectricTestRunner.class)
public class StoreViewTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    private static Object field(StoreView v, String name) throws Exception {
        Field field = StoreView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(v);
    }

    @Test
    public void ctor_buildsTabBarDividerAndContent() throws Exception {
        StoreView view = new StoreView(mContext);
        assertNotNull(view);
        // tabBar + divider + content(FrameLayout)
        assertEquals(3, view.getChildCount());
        assertEquals(0, (int) field(view, "mCurrentTab"));
        assertFalse((Boolean) field(view, "mShowingNewest"));
        assertFalse((Boolean) field(view, "mShown"));
    }

    @Test
    public void ctor_readsPrefsForFilters() throws Exception {
        StorePrefs.setPlatform(mContext, "android");
        StorePrefs.setCategory(mContext, "ai");
        StorePrefs.setExploreSort(mContext, 2);
        StorePrefs.setRankOnlyNonEmpty(mContext, true);
        StorePrefs.setRankPlatform(mContext, "ios");
        StorePrefs.setNewestPlatform(mContext, "macos");
        StoreView view = new StoreView(mContext);
        assertEquals("android", field(view, "mPlatform"));
        assertEquals("ai", field(view, "mCategory"));
        assertEquals(2, field(view, "mSort"));
        assertTrue((Boolean) field(view, "mRankOnlyNonEmpty"));
        assertEquals("ios", field(view, "mRankPlatform"));
        assertEquals("macos", field(view, "mNewestPlatform"));
    }

    @Test
    public void ctor_defaultPrefsAreAllAndZero() throws Exception {
        StoreView view = new StoreView(mContext);
        assertEquals("all", field(view, "mPlatform"));
        assertEquals("all", field(view, "mCategory"));
        assertEquals(0, field(view, "mSort"));
        assertFalse((Boolean) field(view, "mRankOnlyNonEmpty"));
    }

    @Test
    public void canScrollUp_initiallyFalse() {
        StoreView view = new StoreView(mContext);
        assertFalse(view.canScrollUp());
    }

    @Test
    public void resetToHome_onHomeTabWithoutFeedIsNoop() throws Exception {
        StoreView view = new StoreView(mContext);
        view.resetToHome();
        assertEquals(0, (int) field(view, "mCurrentTab"));
        assertFalse((Boolean) field(view, "mShowingNewest"));
    }

    @Test
    public void onDestroy_clearsDisposablesWithoutThrowing() {
        StoreView view = new StoreView(mContext);
        view.onDestroy();
        view.onDestroy();
    }

    @Test
    public void tabScrolls_haveThreeTabs() throws Exception {
        StoreView view = new StoreView(mContext);
        Object scrolls = field(view, "mTabScrolls");
        assertEquals(3, java.lang.reflect.Array.getLength(scrolls));
        Object containers = field(view, "mTabContainers");
        assertEquals(3, java.lang.reflect.Array.getLength(containers));
    }
}
