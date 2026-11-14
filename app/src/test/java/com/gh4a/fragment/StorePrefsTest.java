package com.gh4a.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/** StorePrefs：商店本地偏好（关注仓库 + 已下架记录 + 筛选，Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class StorePrefsTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void platform_defaultAllAndRoundtrip() {
        assertEquals("all", StorePrefs.getPlatform(mContext));
        StorePrefs.setPlatform(mContext, "android");
        assertEquals("android", StorePrefs.getPlatform(mContext));
        StorePrefs.setPlatform(mContext, "all");
        assertEquals("all", StorePrefs.getPlatform(mContext));
    }

    @Test
    public void category_defaultAllAndRoundtrip() {
        assertEquals("all", StorePrefs.getCategory(mContext));
        StorePrefs.setCategory(mContext, "ai");
        assertEquals("ai", StorePrefs.getCategory(mContext));
    }

    @Test
    public void exploreSort_defaultZeroAndRoundtrip() {
        assertEquals(0, StorePrefs.getExploreSort(mContext));
        StorePrefs.setExploreSort(mContext, 3);
        assertEquals(3, StorePrefs.getExploreSort(mContext));
        StorePrefs.setExploreSort(mContext, 0);
        assertEquals(0, StorePrefs.getExploreSort(mContext));
    }

    @Test
    public void rankOnlyNonEmpty_defaultFalseAndRoundtrip() {
        assertFalse(StorePrefs.getRankOnlyNonEmpty(mContext));
        StorePrefs.setRankOnlyNonEmpty(mContext, true);
        assertTrue(StorePrefs.getRankOnlyNonEmpty(mContext));
        StorePrefs.setRankOnlyNonEmpty(mContext, false);
        assertFalse(StorePrefs.getRankOnlyNonEmpty(mContext));
    }

    @Test
    public void rankPlatform_defaultAllAndRoundtrip() {
        assertEquals("all", StorePrefs.getRankPlatform(mContext));
        StorePrefs.setRankPlatform(mContext, "ios");
        assertEquals("ios", StorePrefs.getRankPlatform(mContext));
    }

    @Test
    public void newestPlatform_defaultAllAndRoundtrip() {
        assertEquals("all", StorePrefs.getNewestPlatform(mContext));
        StorePrefs.setNewestPlatform(mContext, "windows");
        assertEquals("windows", StorePrefs.getNewestPlatform(mContext));
    }

    @Test
    public void platformAndRankPlatform_areIndependentKeys() {
        StorePrefs.setPlatform(mContext, "android");
        StorePrefs.setRankPlatform(mContext, "ios");
        StorePrefs.setNewestPlatform(mContext, "macos");
        assertEquals("android", StorePrefs.getPlatform(mContext));
        assertEquals("ios", StorePrefs.getRankPlatform(mContext));
        assertEquals("macos", StorePrefs.getNewestPlatform(mContext));
    }

    @Test
    public void followed_defaultEmpty() {
        assertTrue(StorePrefs.getFollowed(mContext).isEmpty());
        assertFalse(StorePrefs.isFollowed(mContext, "a/b"));
    }

    @Test
    public void addFollowed_addsAndDedupesPreservingOrder() {
        StorePrefs.addFollowed(mContext, "a/b");
        StorePrefs.addFollowed(mContext, "c/d");
        StorePrefs.addFollowed(mContext, "a/b");
        assertEquals(Arrays.asList("a/b", "c/d"), StorePrefs.getFollowed(mContext));
        assertTrue(StorePrefs.isFollowed(mContext, "a/b"));
        assertTrue(StorePrefs.isFollowed(mContext, "c/d"));
        assertFalse(StorePrefs.isFollowed(mContext, "e/f"));
    }

    @Test
    public void addFollowed_nullAndEmptyAreIgnored() {
        StorePrefs.addFollowed(mContext, null);
        StorePrefs.addFollowed(mContext, "");
        assertTrue(StorePrefs.getFollowed(mContext).isEmpty());
    }

    @Test
    public void removeFollowed_removesOnlyThatEntry() {
        StorePrefs.addFollowed(mContext, "a/b");
        StorePrefs.addFollowed(mContext, "c/d");
        StorePrefs.removeFollowed(mContext, "a/b");
        assertEquals(Collections.singletonList("c/d"), StorePrefs.getFollowed(mContext));
        assertFalse(StorePrefs.isFollowed(mContext, "a/b"));
    }

    @Test
    public void removeFollowed_missingEntryIsNoop() {
        StorePrefs.addFollowed(mContext, "a/b");
        StorePrefs.removeFollowed(mContext, "x/y");
        assertEquals(Collections.singletonList("a/b"), StorePrefs.getFollowed(mContext));
    }

    @Test
    public void getFollowed_malformedJsonReturnsEmpty() {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("store_followed_repos", "not-json{{").apply();
        assertTrue(StorePrefs.getFollowed(mContext).isEmpty());
        assertFalse(StorePrefs.isFollowed(mContext, "a/b"));
    }

    @Test
    public void getFollowed_skipsEmptyStringsAndDedupes() {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("store_followed_repos",
                        "[\"a/b\", \"\", \"a/b\", \"c/d\"]").apply();
        assertEquals(Arrays.asList("a/b", "c/d"), StorePrefs.getFollowed(mContext));
    }

    @Test
    public void removed_defaultEmpty() {
        assertTrue(StorePrefs.getRemoved(mContext).isEmpty());
    }

    @Test
    public void getRemoved_parsesEntries() {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("store_removed_repos",
                        "[{\"name\":\"a/b\",\"reason\":\"gone\",\"date\":\"2026-01-02\"},"
                        + "{\"name\":\"c/d\",\"reason\":\"\",\"date\":\"\"}]").apply();
        List<StorePrefs.RemovedEntry> removed = StorePrefs.getRemoved(mContext);
        assertEquals(2, removed.size());
        assertEquals("a/b", removed.get(0).name);
        assertEquals("gone", removed.get(0).reason);
        assertEquals("2026-01-02", removed.get(0).date);
        assertEquals("c/d", removed.get(1).name);
        assertEquals("", removed.get(1).reason);
    }

    @Test
    public void getRemoved_skipsNonObjectsAndToleratesMalformedJson() {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("store_removed_repos",
                        "[\"plain-string\", {\"name\":\"a/b\"}, 42]").apply();
        List<StorePrefs.RemovedEntry> removed = StorePrefs.getRemoved(mContext);
        assertEquals(1, removed.size());
        assertEquals("a/b", removed.get(0).name);
        assertEquals("", removed.get(0).reason);
        assertEquals("", removed.get(0).date);

        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("store_removed_repos", "not-json{{").apply();
        assertTrue(StorePrefs.getRemoved(mContext).isEmpty());
    }

    @Test
    public void removedEntry_ctorKeepsFields() {
        StorePrefs.RemovedEntry e = new StorePrefs.RemovedEntry("n", "r", "d");
        assertEquals("n", e.name);
        assertEquals("r", e.reason);
        assertEquals("d", e.date);
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<StorePrefs> c = StorePrefs.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }

}
