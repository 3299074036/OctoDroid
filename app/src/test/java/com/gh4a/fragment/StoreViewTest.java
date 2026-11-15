package com.gh4a.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

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

    private static Object call(StoreView v, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method method = StoreView.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(v, args);
    }

    private static StoreRepo repo(EnumSet<StoreClassifier.Platform> platforms) {
        return new StoreRepo("o/r", "o", "r", "d", "Java", "", 10, 2, 100L,
                new java.util.ArrayList<>(), new java.util.HashSet<>(),
                "v1", 0L, platforms, 1, null, null, null, null, "tools");
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

    // ---- 平台文案与归属（纯逻辑，无网络） ----

    @Test
    public void platformLabel_allKeys() throws Exception {
        StoreView view = new StoreView(mContext);
        assertEquals("Android", call(view, "platformLabel",
                new Class<?>[]{String.class}, "android"));
        assertEquals("iOS", call(view, "platformLabel",
                new Class<?>[]{String.class}, "ios"));
        assertNotNull(call(view, "platformLabel",
                new Class<?>[]{String.class}, "windows"));
        assertNotNull(call(view, "platformLabel",
                new Class<?>[]{String.class}, "macos"));
        assertNotNull(call(view, "platformLabel",
                new Class<?>[]{String.class}, "linux"));
        assertNotNull(call(view, "platformLabel",
                new Class<?>[]{String.class}, "harmony"));
        assertNotNull(call(view, "platformLabel",
                new Class<?>[]{String.class}, "extension"));
        assertNotNull(call(view, "platformLabel",
                new Class<?>[]{String.class}, "other"));
        assertEquals(call(view, "platformLabel",
                new Class<?>[]{String.class}, "all"),
                call(view, "platformLabel",
                        new Class<?>[]{String.class}, "unknown-key"));
    }

    @Test
    public void platformForKey_allKeys() throws Exception {
        StoreView view = new StoreView(mContext);
        assertEquals(StoreClassifier.Platform.ANDROID, call(view, "platformForKey",
                new Class<?>[]{String.class}, "android"));
        assertEquals(StoreClassifier.Platform.IOS, call(view, "platformForKey",
                new Class<?>[]{String.class}, "ios"));
        assertEquals(StoreClassifier.Platform.WINDOWS, call(view, "platformForKey",
                new Class<?>[]{String.class}, "windows"));
        assertEquals(StoreClassifier.Platform.MACOS, call(view, "platformForKey",
                new Class<?>[]{String.class}, "macos"));
        assertEquals(StoreClassifier.Platform.LINUX, call(view, "platformForKey",
                new Class<?>[]{String.class}, "linux"));
        assertEquals(StoreClassifier.Platform.HARMONY, call(view, "platformForKey",
                new Class<?>[]{String.class}, "harmony"));
        assertEquals(StoreClassifier.Platform.EXTENSION, call(view, "platformForKey",
                new Class<?>[]{String.class}, "extension"));
        assertNull(call(view, "platformForKey",
                new Class<?>[]{String.class}, "other"));
        assertNull(call(view, "platformForKey",
                new Class<?>[]{String.class}, "unknown-key"));
    }

    @Test
    public void matchesPlatform_allMatchesEverything() throws Exception {
        StoreView view = new StoreView(mContext);
        StoreRepo empty = repo(EnumSet.noneOf(StoreClassifier.Platform.class));
        assertTrue((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, empty, "all"));
    }

    @Test
    public void matchesPlatform_otherMeansNoPlatformOrArchiveOnly() throws Exception {
        StoreView view = new StoreView(mContext);
        StoreRepo empty = repo(EnumSet.noneOf(StoreClassifier.Platform.class));
        StoreRepo archiveOnly = repo(EnumSet.of(StoreClassifier.Platform.ARCHIVE));
        StoreRepo android = repo(EnumSet.of(StoreClassifier.Platform.ANDROID));
        StoreRepo archivePlusAndroid = repo(EnumSet.of(
                StoreClassifier.Platform.ARCHIVE, StoreClassifier.Platform.ANDROID));
        assertTrue((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, empty, "other"));
        assertTrue((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, archiveOnly, "other"));
        assertFalse((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, android, "other"));
        assertFalse((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, archivePlusAndroid, "other"));
    }

    @Test
    public void matchesPlatform_specificKeyChecksMembership() throws Exception {
        StoreView view = new StoreView(mContext);
        StoreRepo android = repo(EnumSet.of(StoreClassifier.Platform.ANDROID));
        assertTrue((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, android, "android"));
        assertFalse((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, android, "ios"));
        assertFalse((Boolean) call(view, "matchesPlatform",
                new Class<?>[]{StoreRepo.class, String.class}, android, "unknown-key"));
    }

    @Test
    public void updateTabStyles_selectedTabUsesPrimaryColor() throws Exception {
        StoreView view = new StoreView(mContext);
        call(view, "updateTabStyles", new Class<?>[]{});
        android.widget.TextView[] labels =
                (android.widget.TextView[]) field(view, "mTabLabels");
        // 注：setTypeface(null, BOLD) 后 Robolectric 下 getTypeface() 为 null，
        // 故不断言字重，改断言选中态颜色（主色）与未选中态（次要文字色）。
        assertEquals(StoreViews.primaryColor(mContext),
                labels[0].getCurrentTextColor());
        assertEquals(StoreViews.secondaryText(mContext),
                labels[1].getCurrentTextColor());
        assertEquals(StoreViews.secondaryText(mContext),
                labels[2].getCurrentTextColor());
    }

    // ---- 取数与渲染（StoreApi 挡成空数据，不发真实请求） ----

    private static StoreApi.StoreData cannedFeed() {
        return new StoreApi.StoreData(Arrays.asList(
                repo(EnumSet.of(StoreClassifier.Platform.ANDROID)),
                repo(EnumSet.of(StoreClassifier.Platform.IOS)),
                repo(EnumSet.of(StoreClassifier.Platform.ARCHIVE))),
                false, "2026-11-14");
    }

    private static StoreApi.StoreData emptyFeed() {
        return new StoreApi.StoreData(
                new java.util.ArrayList<>(), false, "2026-11-14");
    }

    private static MockedStatic<StoreApi> mockStoreApi() {
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.just(emptyFeed()));
        return mocked;
    }

    @Test
    public void onShown_loadsFeedAndRendersHome() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            assertNotNull(field(view, "mFeedData"));
            assertFalse((Boolean) field(view, "mFeedLoading"));
            boolean[] loaded = (boolean[]) field(view, "mTabLoaded");
            assertTrue(loaded[0]);
            assertTrue(loaded[1]);
            assertTrue(loaded[2]);
        }
    }

    @Test
    public void selectTab_exploreAndRankRenderFromCache() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            assertEquals(1, (int) field(view, "mCurrentTab"));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            assertEquals(2, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void applyPlatform_switchesFilterWithoutReload() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "applyPlatform", new Class<?>[]{String.class}, "android");
            assertEquals("android", field(view, "mPlatform"));
            call(view, "applyPlatform", new Class<?>[]{String.class}, "android");
            assertEquals("android", field(view, "mPlatform"));
        }
    }

    @Test
    public void showTabError_addsRetryView() throws Exception {
        StoreView view = new StoreView(mContext);
        call(view, "showTabError",
                new Class<?>[]{int.class, Throwable.class},
                0, new RuntimeException("boom"));
        android.widget.LinearLayout target =
                (android.widget.LinearLayout) call(view, "listTarget",
                        new Class<?>[]{int.class}, 0);
        assertEquals(1, target.getChildCount());
        assertTrue(target.getChildAt(0) instanceof android.widget.TextView);
    }

    // ---- 最新入库 / 排行榜渲染（纯字段＋视图构建） ----

    private static void setField(StoreView v, String name, Object value) throws Exception {
        Field field = StoreView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(v, value);
    }

    private static StoreRepo repoVariant(EnumSet<StoreClassifier.Platform> platforms,
            int stars, String language, long publishedAt) {
        return new StoreRepo("o/r", "o", "r", "d", language, "", stars, 2, 100L,
                new java.util.ArrayList<>(), new java.util.HashSet<>(),
                "v1", publishedAt, platforms, 1, null, null, null, null, "tools");
    }

    @Test
    public void resetFeed_clearsState() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedLoading", true);
        setField(view, "mFeedOffline", true);
        setField(view, "mFeedMirror", true);
        setField(view, "mRankGroup", 2);
        setField(view, "mRankList", 1);
        setField(view, "mRankShown", 60);
        setField(view, "mExploreShown", 60);
        boolean[] loaded = (boolean[]) field(view, "mTabLoaded");
        loaded[0] = true;
        call(view, "resetFeed", new Class<?>[]{});
        assertNull(field(view, "mFeedData"));
        assertFalse((Boolean) field(view, "mFeedLoading"));
        assertFalse((Boolean) field(view, "mFeedOffline"));
        assertFalse((Boolean) field(view, "mFeedMirror"));
        assertEquals(-1, field(view, "mRankGroup"));
        assertEquals(-1, field(view, "mRankList"));
        assertEquals(20, field(view, "mRankShown"));
        assertEquals(20, field(view, "mExploreShown"));
        assertFalse(((boolean[]) field(view, "mTabLoaded"))[0]);
    }

    @Test
    public void newestPool_nullDataReturnsEmpty() throws Exception {
        StoreView view = new StoreView(mContext);
        assertTrue(((java.util.List<?>) call(view, "newestPool",
                new Class<?>[]{})).isEmpty());
    }

    @Test
    public void newestPool_filtersByPlatform() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        setField(view, "mNewestPlatform", "android");
        assertEquals(1, ((java.util.List<?>) call(view, "newestPool",
                new Class<?>[]{})).size());
    }

    @Test
    public void newestPlatformCounts_countsPerPlatform() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        int[] counts = (int[]) call(view, "newestPlatformCounts", new Class<?>[]{});
        assertEquals(9, counts.length);
        // PLATFORM_KEYS：all/android/ios/windows/macos/linux/harmony/extension/other
        assertEquals(3, counts[0]);
        assertEquals(1, counts[1]);
        assertEquals(1, counts[2]);
        assertEquals(0, counts[3]);
        assertEquals(1, counts[8]); // 仅压缩包的归 other
    }

    @Test
    public void newestPlatformGrid_buildsGrid() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        android.widget.LinearLayout grid =
                (android.widget.LinearLayout) call(view, "newestPlatformGrid",
                        new Class<?>[]{});
        assertNotNull(grid);
        assertTrue(grid.getChildCount() > 0);
    }

    @Test
    public void newestRowView_richRepoShowsMeta() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        assertEquals("o/r",
                ((android.widget.TextView) row.getChildAt(0)).getText().toString());
        String meta = ((android.widget.TextView) row.getChildAt(2)).getText().toString();
        assertTrue(meta.contains("★"));
        assertTrue(meta.contains("Java"));
        assertTrue(meta.contains("v1"));
    }

    @Test
    public void newestRowView_zeroStarsOmitsStarAndShowsDashForEmptyLanguage()
            throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoVariant(EnumSet.noneOf(StoreClassifier.Platform.class), 0, "", 0L));
        // 有简介：子视图为 name(0) + desc(1) + meta(2)，无 bottom 行
        String meta = ((android.widget.TextView) row.getChildAt(2)).getText().toString();
        assertFalse(meta.contains("★"));
        assertTrue(meta.contains("—"));
    }

    @Test
    public void newestUpdatedText_noTimeReturnsNull() throws Exception {
        StoreView view = new StoreView(mContext);
        assertNull(call(view, "newestUpdatedText",
                new Class<?>[]{StoreRepo.class}, repo(EnumSet.noneOf(
                        StoreClassifier.Platform.class))));
    }

    @Test
    public void newestUpdatedText_todayReturnsTodayString() throws Exception {
        StoreView view = new StoreView(mContext);
        String text = (String) call(view, "newestUpdatedText",
                new Class<?>[]{StoreRepo.class},
                repoVariant(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java", System.currentTimeMillis()));
        assertEquals(mContext.getString(com.gh4a.R.string.store_newest_updated_today),
                text);
    }

    @Test
    public void newestUpdatedText_daysAgoReturnsPlural() throws Exception {
        StoreView view = new StoreView(mContext);
        String text = (String) call(view, "newestUpdatedText",
                new Class<?>[]{StoreRepo.class},
                repoVariant(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java",
                        System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000));
        assertTrue(text.contains("3"));
    }

    @Test
    public void offlineStrip_textIsOfflineData() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.TextView strip =
                (android.widget.TextView) call(view, "offlineStrip", new Class<?>[]{});
        assertEquals(mContext.getString(com.gh4a.R.string.store_offline_data),
                strip.getText().toString());
    }

    @Test
    public void mirrorStrip_textIsMirrorData() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.TextView strip =
                (android.widget.TextView) call(view, "mirrorStrip", new Class<?>[]{});
        assertEquals(mContext.getString(com.gh4a.R.string.store_mirror_data),
                strip.getText().toString());
    }

    @Test
    public void rankRowView_buildsRowWithRankAndName() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        assertEquals("1",
                ((android.widget.TextView) row.getChildAt(0)).getText().toString());
        assertEquals(StoreViews.primaryColor(mContext),
                ((android.widget.TextView) row.getChildAt(0)).getCurrentTextColor());
        android.widget.LinearLayout texts =
                (android.widget.LinearLayout) row.getChildAt(1);
        assertEquals("o/r",
                ((android.widget.TextView) texts.getChildAt(0)).getText().toString());
    }

    @Test
    public void rankRowView_rankBeyondThreeUsesSecondaryColor() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                4, repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        assertEquals(StoreViews.secondaryText(mContext),
                ((android.widget.TextView) row.getChildAt(0)).getCurrentTextColor());
    }

    @Test
    public void renderRankFullList_invalidGroupRendersOverview() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroup", -1);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertTrue(container.getChildCount() > 0);
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank_title),
                ((android.widget.TextView) container.getChildAt(0)).getText().toString());
    }

    private static StoreRankGroups.Group rankGroupWithList(String name,
            java.util.List<StoreRepo> items) {
        StoreRankGroups.Group group = new StoreRankGroups.Group("g");
        StoreRankGroups.RankList list = new StoreRankGroups.RankList(name);
        list.items.addAll(items);
        group.lists.add(list);
        return group;
    }

    @Test
    public void renderRankFullList_validListRendersRowsAndEndBar() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID)),
                        repo(EnumSet.of(StoreClassifier.Platform.IOS))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        // back + title + sub + (row + divider) × 2 + end
        assertEquals(8, container.getChildCount());
        assertEquals("Hot",
                ((android.widget.TextView) container.getChildAt(1)).getText().toString());
        assertEquals(mContext.getString(com.gh4a.R.string.store_all_loaded),
                ((android.widget.TextView) container.getChildAt(7)).getText().toString());
    }

    @Test
    public void renderRankFullList_emptyListShowsEmpty() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", new java.util.ArrayList<>())));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(4, container.getChildCount());
        assertEquals(mContext.getString(com.gh4a.R.string.store_empty_result),
                ((android.widget.TextView) container.getChildAt(3)).getText().toString());
    }

    @Test
    public void showNewest_withDataRendersNewestList() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        call(view, "showNewest", new Class<?>[]{});
        assertTrue((Boolean) field(view, "mShowingNewest"));
        android.widget.LinearLayout container =
                (android.widget.LinearLayout) field(view, "mNewestContainer");
        // back + title + sub + count + filterLabel + grid + (row + divider) × 3
        assertEquals(12, container.getChildCount());
    }

    @Test
    public void showNewest_withoutDataLoadsNewest() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            call(view, "showNewest", new Class<?>[]{});
            assertTrue((Boolean) field(view, "mShowingNewest"));
            assertNotNull(field(view, "mNewestData"));
        }
    }

    @Test
    public void applyRankPlatform_switchesAndResetsGroups() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "applyRankPlatform", new Class<?>[]{String.class}, "ios");
            assertEquals("ios", field(view, "mRankPlatform"));
            assertEquals(20, field(view, "mRankShown"));
            // 同值再调是 no-op
            call(view, "applyRankPlatform", new Class<?>[]{String.class}, "ios");
            assertEquals("ios", field(view, "mRankPlatform"));
        }
    }

    @Test
    public void applyCategory_exploreTabResetsShown() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            call(view, "applyCategory", new Class<?>[]{String.class}, "ai");
            assertEquals("ai", field(view, "mCategory"));
            assertEquals(20, field(view, "mExploreShown"));
        }
    }

    @Test
    public void applyExploreSort_sameSortReloads() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "applyExploreSort", new Class<?>[]{int.class}, 0);
            assertNotNull(field(view, "mFeedData"));
            assertEquals(0, field(view, "mSort"));
        }
    }

    @Test
    public void applyExploreSort_newSortRenders() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "applyExploreSort", new Class<?>[]{int.class}, 1);
            assertEquals(1, field(view, "mSort"));
            assertEquals(20, field(view, "mExploreShown"));
            assertNotNull(field(view, "mFeedData"));
        }
    }

    @Test
    public void reloadCurrentTab_withNewestShowingReloadsNewest() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mShowingNewest", true);
            view.reloadCurrentTab();
            assertNotNull(field(view, "mNewestData"));
        }
    }

    @Test
    public void reloadCurrentTab_resetsFeedAndReloads() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            view.reloadCurrentTab();
            assertNotNull(field(view, "mFeedData"));
            assertFalse((Boolean) field(view, "mFeedLoading"));
        }
    }

    // ---- batch 2 helpers ----

    private static StoreRepo repoFull(
            java.util.EnumSet<StoreClassifier.Platform> platforms,
            int stars, String language, long publishedAt, String tag, long downloadCount,
            Double trendingScore, Double starsPerDay,
            java.util.List<String> ranks, java.util.List<String> topics) {
        return new StoreRepo("o/r", "o", "r", "d", language, "", stars, 2, downloadCount,
                topics != null ? topics : new java.util.ArrayList<>(),
                new java.util.HashSet<>(),
                tag, publishedAt, platforms, 1, trendingScore, null, starsPerDay,
                ranks, "tools");
    }

    private static java.util.List<StoreRepo> manyRepos(int n) {
        java.util.List<StoreRepo> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new StoreRepo("o/r" + i, "o", "r" + i, "d", "Java", "", 10 + i, 2,
                    100L, new java.util.ArrayList<>(), new java.util.HashSet<>(),
                    "v1", i * 1000L,
                    EnumSet.of(StoreClassifier.Platform.ANDROID), 1,
                    null, null, null, null, "tools"));
        }
        return out;
    }

    private static StoreApi.StoreData feedOf(java.util.List<StoreRepo> items) {
        return new StoreApi.StoreData(items, false, "2026-11-14");
    }

    private static android.widget.LinearLayout tabList(StoreView v, int tab)
            throws Exception {
        android.widget.LinearLayout[] lists =
                (android.widget.LinearLayout[]) field(v, "mTabListContainers");
        return lists[tab];
    }

    private static android.widget.LinearLayout tabHeader(StoreView v, int tab)
            throws Exception {
        android.widget.LinearLayout[] headers =
                (android.widget.LinearLayout[]) field(v, "mTabHeaderContainers");
        return headers[tab];
    }

    private static android.widget.LinearLayout rankContainer(StoreView v)
            throws Exception {
        android.widget.LinearLayout[] containers =
                (android.widget.LinearLayout[]) field(v, "mTabContainers");
        return containers[2];
    }

    private static android.view.View.OnScrollChangeListener scrollListener(
            android.widget.ScrollView scroll) {
        try {
            Field liField = android.view.View.class.getDeclaredField("mListenerInfo");
            liField.setAccessible(true);
            Object info = liField.get(scroll);
            assertNotNull(info);
            Field f = info.getClass().getDeclaredField("mOnScrollChangeListener");
            f.setAccessible(true);
            return (android.view.View.OnScrollChangeListener) f.get(info);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static android.widget.ScrollView tabScroll(StoreView v, int tab)
            throws Exception {
        android.widget.ScrollView[] scrolls =
                (android.widget.ScrollView[]) field(v, "mTabScrolls");
        return scrolls[tab];
    }

    private static void clickTab(StoreView v, int tab) {
        android.widget.LinearLayout tabBar =
                (android.widget.LinearLayout) v.getChildAt(0);
        tabBar.getChildAt(tab).performClick();
    }

    private static android.app.Activity newActivity() {
        return org.robolectric.Robolectric
                .buildActivity(android.app.Activity.class).setup().get();
    }

    private static android.content.Intent nextStarted(
            android.app.Activity activity) {
        return org.robolectric.Shadows.shadowOf(activity)
                .getNextStartedActivity();
    }

    private static android.content.Intent nextStarted() {
        android.app.Application app =
                org.robolectric.RuntimeEnvironment.getApplication();
        return org.robolectric.Shadows.shadowOf(app).getNextStartedActivity();
    }

    private static void clickDialogItem(int which) {
        android.app.Dialog dialog =
                org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
        android.widget.ListView list =
                dialog.findViewById(androidx.appcompat.R.id.select_dialog_listview);
        assertNotNull(list);
        list.performItemClick(list.getAdapter().getView(which, null, list),
                which, which);
    }

    private static MockedStatic<StoreApi> mockStoreApiFeedErrorThenOk() {
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.error(new RuntimeException("net")),
                        Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.just(emptyFeed()));
        return mocked;
    }

    private static MockedStatic<StoreApi> mockStoreApiNewestError() {
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.error(new RuntimeException("net")));
        return mocked;
    }

    private static void sortForExplore(java.util.List<StoreRepo> items, int sort)
            throws Exception {
        call(new StoreView(ApplicationProvider.getApplicationContext()),
                "sortForExplore",
                new Class<?>[]{java.util.List.class, int.class}, items, sort);
    }

    // ---- 滚动监听（235-266） ----

    @Test
    public void exploreScrollListener_scrollUpOrNoFeed_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        android.view.View.OnScrollChangeListener l =
                scrollListener(tabScroll(view, 1));
        l.onScrollChange(null, 0, 0, 0, 10); // 未下滑
        l.onScrollChange(null, 0, 100, 0, 0); // 无 feed
        assertEquals(20, field(view, "mExploreShown"));
    }

    @Test
    public void exploreScrollListener_shownReachedVisible_returns() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            setField(view, "mExploreShown", 60);
            setField(view, "mExploreVisibleSize", 3);
            scrollListener(tabScroll(view, 1))
                    .onScrollChange(null, 0, 100, 0, 0);
            assertEquals(60, field(view, "mExploreShown"));
        }
    }

    @Test
    public void exploreScrollListener_atBottom_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            assertEquals(25, field(view, "mExploreVisibleSize"));
            scrollListener(tabScroll(view, 1))
                    .onScrollChange(null, 0, 100, 0, 0);
            assertEquals(40, field(view, "mExploreShown"));
        }
    }

    @Test
    public void rankScrollListener_guardsReturn() throws Exception {
        StoreView view = new StoreView(mContext);
        android.view.View.OnScrollChangeListener l =
                scrollListener(tabScroll(view, 2));
        l.onScrollChange(null, 0, 0, 0, 10); // 未下滑
        l.onScrollChange(null, 0, 100, 0, 0); // 无 feed
        assertEquals(20, field(view, "mRankShown"));
        setField(view, "mFeedData", cannedFeed());
        l.onScrollChange(null, 0, 100, 0, 0); // 概览页（无榜单）
        assertEquals(20, field(view, "mRankShown"));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        setField(view, "mRankFullTotal", 20);
        l.onScrollChange(null, 0, 100, 0, 0); // 已展完
        assertEquals(20, field(view, "mRankShown"));
    }

    @Test
    public void rankScrollListener_atBottom_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mRankGroups", java.util.Arrays.asList(
                    rankGroupWithList("Hot", manyRepos(25))));
            setField(view, "mRankGroup", 0);
            setField(view, "mRankList", 0);
            setField(view, "mRankFullTotal", 25);
            // renderRank() 只在榜单切换时复位 mRankShown；先把已展榜单记成
            // 当前榜，否则追加后 renderRank 会把 40 重置回 20。
            setField(view, "mRankShownGroup", 0);
            setField(view, "mRankShownList", 0);
            scrollListener(tabScroll(view, 2))
                    .onScrollChange(null, 0, 100, 0, 0);
            assertEquals(40, field(view, "mRankShown"));
        }
    }

    // ---- 页签点击 / canScrollUp / resetToHome / reselect ----

    @Test
    public void tabCellClick_selectsTab() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            clickTab(view, 1);
            assertEquals(1, (int) field(view, "mCurrentTab"));
            clickTab(view, 2);
            assertEquals(2, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void selectTab_reselectReloadsFeed() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 0);
            assertNotNull(field(view, "mFeedData"));
            assertFalse((Boolean) field(view, "mFeedLoading"));
        }
    }

    @Test
    public void selectTab_sameTabWhileShowingNewest_rendersFromCache() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "showNewest", new Class<?>[]{});
            assertTrue((Boolean) field(view, "mShowingNewest"));
            call(view, "selectTab", new Class<?>[]{int.class}, 0);
            assertFalse((Boolean) field(view, "mShowingNewest"));
            assertEquals(0, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void canScrollUp_showingNewest_usesNewestScroll() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mShowingNewest", true);
        assertFalse(view.canScrollUp());
    }

    @Test
    public void canScrollUp_nullScroll_returnsFalse() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.ScrollView[] scrolls =
                (android.widget.ScrollView[]) field(view, "mTabScrolls");
        java.lang.reflect.Array.set(scrolls, 0, null);
        assertFalse(view.canScrollUp());
    }

    @Test
    public void resetToHome_fromExplore_selectsHome() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            view.resetToHome();
            assertEquals(0, (int) field(view, "mCurrentTab"));
            assertFalse((Boolean) field(view, "mShowingNewest"));
        }
    }

    @Test
    public void resetToHome_onHomeWithFeed_rendersHome() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            view.resetToHome();
            assertTrue(tabList(view, 0).getChildCount() > 0);
        }
    }

    // ---- applyCategory / applyExploreSort / jumpToExploreWithSort ----

    @Test
    public void applyCategory_onHomeTab_rendersHome() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "applyCategory", new Class<?>[]{String.class}, "tools");
            assertEquals("tools", field(view, "mCategory"));
            assertTrue(tabList(view, 0).getChildCount() > 0);
        }
    }

    @Test
    public void applyExploreSort_withoutFeed_loadsFeed() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            call(view, "applyExploreSort", new Class<?>[]{int.class}, 1);
            assertEquals(1, field(view, "mSort"));
            assertNotNull(field(view, "mFeedData"));
        }
    }

    @Test
    public void jumpToExploreWithSort_jumpsToExplore() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "jumpToExploreWithSort", new Class<?>[]{int.class}, 2);
            assertEquals(2, field(view, "mSort"));
            assertEquals(1, (int) field(view, "mCurrentTab"));
        }
    }

    // ---- loadFeed / renderTabFromCache / listTarget ----

    @Test
    public void loadFeed_withFeedData_rendersFromCache() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "loadFeed", new Class<?>[]{});
            assertFalse((Boolean) field(view, "mFeedLoading"));
            assertNotNull(field(view, "mFeedData"));
        }
    }

    @Test
    public void loadFeed_whileLoading_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedLoading", true);
        call(view, "loadFeed", new Class<?>[]{});
        assertTrue((Boolean) field(view, "mFeedLoading"));
    }

    @Test
    public void loadFeed_error_showsErrorAndRetryLoads() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApiFeedErrorThenOk()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            assertFalse((Boolean) field(view, "mFeedLoading"));
            assertNull(field(view, "mFeedData"));
            android.widget.LinearLayout target = tabList(view, 0);
            assertEquals(1, target.getChildCount());
            // 点错误提示重试，第二次取数成功。
            target.getChildAt(0).performClick();
            assertNotNull(field(view, "mFeedData"));
        }
    }

    @Test
    public void renderTabFromCache_withoutFeed_loadsFeed() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            call(view, "renderTabFromCache", new Class<?>[]{int.class}, 0);
            assertNotNull(field(view, "mFeedData"));
        }
    }

    @Test
    public void listTarget_rank_returnsFullContainer() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout[] containers =
                (android.widget.LinearLayout[]) field(view, "mTabContainers");
        assertEquals(containers[2],
                call(view, "listTarget", new Class<?>[]{int.class}, 2));
    }

    // ---- pool / rankGroupsFromBackend / sortForExplore ----

    @Test
    public void pool_withoutFeed_returnsEmpty() throws Exception {
        StoreView view = new StoreView(mContext);
        assertTrue(((java.util.List<?>) call(view, "pool",
                new Class<?>[]{})).isEmpty());
    }

    @Test
    public void rankGroupsFromBackend_withRanks_returnsTrue() throws Exception {
        StoreView view = new StoreView(mContext);
        StoreRepo ranked = repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                10, "Java", 0L, "v1", 100L, null, null,
                java.util.Arrays.asList("some-rank"), null);
        setField(view, "mFeedData", feedOf(java.util.Arrays.asList(ranked)));
        assertTrue((Boolean) call(view, "rankGroupsFromBackend", new Class<?>[]{}));
    }

    @Test
    public void rankGroupsFromBackend_withoutRanks_returnsFalse() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        assertFalse((Boolean) call(view, "rankGroupsFromBackend", new Class<?>[]{}));
    }

    @Test
    public void sortForExplore_updatedTieBreaksByStars() throws Exception {
        java.util.List<StoreRepo> items = new java.util.ArrayList<>(java.util.Arrays.asList(
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        5, "Java", 1000L, "v1", 100L, null, null, null, null),
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java", 1000L, "v1", 100L, null, null, null, null),
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        99, "Java", 2000L, "v1", 100L, null, null, null, null)));
        sortForExplore(items, 1);
        assertEquals(99, items.get(0).stars());
        assertEquals(10, items.get(1).stars());
        assertEquals(5, items.get(2).stars());
    }

    @Test
    public void sortForExplore_downloadsTieBreaksByStars() throws Exception {
        java.util.List<StoreRepo> items = new java.util.ArrayList<>(java.util.Arrays.asList(
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        5, "Java", 0L, "v1", 100L, null, null, null, null),
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java", 0L, "v1", 100L, null, null, null, null),
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        99, "Java", 0L, "v1", 500L, null, null, null, null)));
        sortForExplore(items, 2);
        assertEquals(99, items.get(0).stars());
        assertEquals(10, items.get(1).stars());
        assertEquals(5, items.get(2).stars());
    }

    @Test
    public void sortForExplore_hotFallsBackToStars() throws Exception {
        java.util.List<StoreRepo> items = new java.util.ArrayList<>(java.util.Arrays.asList(
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        5, "Java", 0L, "v1", 100L, null, null, null, null),
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java", 0L, "v1", 100L, null, null, null, null)));
        sortForExplore(items, 3);
        assertEquals(10, items.get(0).stars());
        assertEquals(5, items.get(1).stars());
    }

    @Test
    public void sortForExplore_unknownSortDefaultsToStars() throws Exception {
        java.util.List<StoreRepo> items = new java.util.ArrayList<>(java.util.Arrays.asList(
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        5, "Java", 0L, "v1", 100L, null, null, null, null),
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java", 0L, "v1", 100L, null, null, null, null)));
        sortForExplore(items, 99);
        assertEquals(10, items.get(0).stars());
        assertEquals(5, items.get(1).stars());
    }

    // ---- batch 2b helpers ----

    private static android.widget.TextView findText(android.view.View root, String text) {
        if (root instanceof android.widget.TextView) {
            CharSequence t = ((android.widget.TextView) root).getText();
            if (t != null && t.toString().equals(text)) {
                return (android.widget.TextView) root;
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.TextView found = findText(g.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void idleMain() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static android.app.Activity attachView(StoreView view) {
        android.app.Activity activity = org.robolectric.Robolectric
                .buildActivity(android.app.Activity.class).setup().get();
        activity.setContentView(view);
        return activity;
    }

    private static android.widget.LinearLayout statsRow(StoreView v) throws Exception {
        return (android.widget.LinearLayout) tabHeader(v, 0).getChildAt(0);
    }

    private static android.view.View statCell(StoreView v, int row, int cell)
            throws Exception {
        android.widget.LinearLayout rows = statsRow(v);
        android.widget.LinearLayout r = (android.widget.LinearLayout) rows.getChildAt(row);
        return r.getChildAt(cell * 2);
    }

    // ---- 首页头/列表/统计块点击 ----

    @Test
    public void renderHomeHeader_mirrorStripShown() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedMirror", true);
        call(view, "renderHomeHeader", new Class<?>[]{});
        assertNotNull(findText(tabHeader(view, 0),
                mContext.getString(com.gh4a.R.string.store_mirror_data)));
    }

    @Test
    public void renderHomeHeader_offlineStripShown() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedOffline", true);
        call(view, "renderHomeHeader", new Class<?>[]{});
        assertNotNull(findText(tabHeader(view, 0),
                mContext.getString(com.gh4a.R.string.store_offline_data)));
    }

    @Test
    public void renderHomeList_allFilteredOut_showsEmpty() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mPlatform", "harmony");
        call(view, "renderHomeList", new Class<?>[]{});
        assertNotNull(findText(tabList(view, 0),
                mContext.getString(com.gh4a.R.string.store_empty_result)));
    }

    @Test
    public void viewAllRow_hotClick_jumpsWithStarsSort() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            tabList(view, 0).getChildAt(7).performClick();
            assertEquals(1, (int) field(view, "mCurrentTab"));
            assertEquals(0, field(view, "mSort"));
        }
    }

    @Test
    public void viewAllRow_recentClick_jumpsWithUpdatedSort() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            tabList(view, 0).getChildAt(15).performClick();
            assertEquals(1, (int) field(view, "mCurrentTab"));
            assertEquals(1, field(view, "mSort"));
        }
    }

    @Test
    public void statCell_reposClick_selectsExplore() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            statCell(view, 0, 0).performClick();
            assertEquals(1, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void statCell_assetsClick_selectsExplore() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            statCell(view, 0, 1).performClick();
            assertEquals(1, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void statCell_newestClick_showsNewest() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            statCell(view, 1, 0).performClick();
            assertTrue((Boolean) field(view, "mShowingNewest"));
        }
    }

    @Test
    public void statCell_ranksClick_selectsRank() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            statCell(view, 1, 1).performClick();
            assertEquals(2, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void platformGridCellClick_appliesPlatform() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            android.widget.LinearLayout grid =
                    (android.widget.LinearLayout) call(view, "platformGrid",
                            new Class<?>[]{});
            android.widget.LinearLayout row0 =
                    (android.widget.LinearLayout) grid.getChildAt(0);
            row0.getChildAt(1).performClick(); // android
            assertEquals("android", field(view, "mPlatform"));
        }
    }

    @Test
    public void categoryGridCellClick_appliesCategory() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            android.widget.LinearLayout grid =
                    (android.widget.LinearLayout) call(view, "categoryGrid",
                            new Class<?>[]{});
            android.widget.LinearLayout row0 =
                    (android.widget.LinearLayout) grid.getChildAt(0);
            row0.getChildAt(1).performClick(); // ai
            assertEquals("ai", field(view, "mCategory"));
        }
    }

    @Test
    public void rankPlatformGridCellClick_appliesRankPlatform() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            android.widget.LinearLayout grid =
                    (android.widget.LinearLayout) call(view, "rankPlatformGrid",
                            new Class<?>[]{});
            android.widget.LinearLayout row0 =
                    (android.widget.LinearLayout) grid.getChildAt(0);
            row0.getChildAt(1).performClick(); // android
            assertEquals("android", field(view, "mRankPlatform"));
        }
    }

    // ---- 探索头/填充 ----

    @Test
    public void exploreInfoClick_showsSortNoteToast() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            android.widget.LinearLayout titleRow =
                    (android.widget.LinearLayout) tabHeader(view, 1).getChildAt(0);
            titleRow.getChildAt(1).performClick();
            assertNotNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void exploreSortChipClick_appliesSort() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            android.widget.LinearLayout sortChips =
                    (android.widget.LinearLayout) tabHeader(view, 1).getChildAt(1);
            sortChips.getChildAt(1).performClick(); // 最近更新
            assertEquals(1, field(view, "mSort"));
        }
    }

    @Test
    public void renderExploreHeader_mirrorStripShown() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedMirror", true);
        call(view, "renderExploreHeader", new Class<?>[]{});
        assertNotNull(findText(tabHeader(view, 1),
                mContext.getString(com.gh4a.R.string.store_mirror_data)));
    }

    @Test
    public void renderExploreHeader_offlineStripShown() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedOffline", true);
        call(view, "renderExploreHeader", new Class<?>[]{});
        assertNotNull(findText(tabHeader(view, 1),
                mContext.getString(com.gh4a.R.string.store_offline_data)));
    }

    @Test
    public void exploreFiltered_platformMismatchSkipped() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mPlatform", "ios");
        assertEquals(1, ((java.util.List<?>) call(view, "exploreFiltered",
                new Class<?>[]{java.util.List.class},
                ((java.util.List<?>) call(view, "pool", new Class<?>[]{})))).size());
    }

    @Test
    public void ensureExploreFillViewport_nullScroll_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.ScrollView[] scrolls =
                (android.widget.ScrollView[]) field(view, "mTabScrolls");
        java.lang.reflect.Array.set(scrolls, 1, null);
        call(view, "ensureExploreFillViewport", new Class<?>[]{});
    }

    @Test
    public void ensureExploreFillViewport_noFeed_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        attachView(view);
        call(view, "ensureExploreFillViewport", new Class<?>[]{});
        idleMain();
        assertEquals(20, field(view, "mExploreShown"));
    }

    @Test
    public void ensureExploreFillViewport_wrongTab_returns() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            view.onShown();
            call(view, "ensureExploreFillViewport", new Class<?>[]{});
            idleMain();
            assertEquals(20, field(view, "mExploreShown"));
        }
    }

    private static void makeUnscrollable(android.widget.ScrollView scroll) {
        // Robolectric 真机布局下 25 行内容本身可滚动，canScrollVertically(1)
        // 为 true、fillViewport 不会追加；把 ScrollView 量成远超内容高度，
        // 让它恒为不可滚动。layout 高度也要改：selectTab 渲染时排队的
        // traversal 会在 idleMain() 时先于 posted runnable 执行、把高度
        // 量回窗口尺寸，光手动 measure 会被它覆盖。
        scroll.getLayoutParams().height = 100000;
        scroll.requestLayout();
        scroll.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080,
                        android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(100000,
                        android.view.View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 1080, 100000);
    }

    @Test
    public void ensureExploreFillViewport_notScrollable_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            assertEquals(20, field(view, "mExploreShown"));
            makeUnscrollable(tabScroll(view, 1));
            idleMain();
            assertEquals(40, field(view, "mExploreShown"));
        }
    }

    @Test
    public void ensureRankFillViewport_nullScroll_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.ScrollView[] scrolls =
                (android.widget.ScrollView[]) field(view, "mTabScrolls");
        java.lang.reflect.Array.set(scrolls, 2, null);
        call(view, "ensureRankFillViewport", new Class<?>[]{});
    }

    @Test
    public void ensureRankFillViewport_noFeed_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        attachView(view);
        call(view, "ensureRankFillViewport", new Class<?>[]{});
        idleMain();
        assertEquals(20, field(view, "mRankShown"));
    }

    @Test
    public void ensureRankFillViewport_overview_returns() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            call(view, "ensureRankFillViewport", new Class<?>[]{});
            idleMain();
            assertEquals(20, field(view, "mRankShown"));
        }
    }

    @Test
    public void ensureRankFillViewport_notScrollable_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mRankGroups", java.util.Arrays.asList(
                    rankGroupWithList("Hot", manyRepos(25))));
            setField(view, "mRankGroup", 0);
            setField(view, "mRankList", 0);
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            assertEquals(20, field(view, "mRankShown"));
            makeUnscrollable(tabScroll(view, 2));
            idleMain();
            assertEquals(40, field(view, "mRankShown"));
        }
    }

    // ---- 排行榜 ----

    @Test
    public void renderRank_mirrorStripShown() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedMirror", true);
        call(view, "renderRank", new Class<?>[]{});
        assertNotNull(findText(rankContainer(view),
                mContext.getString(com.gh4a.R.string.store_mirror_data)));
    }

    @Test
    public void renderRank_offlineStripShown() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mFeedOffline", true);
        call(view, "renderRank", new Class<?>[]{});
        assertNotNull(findText(rankContainer(view),
                mContext.getString(com.gh4a.R.string.store_offline_data)));
    }

    @Test
    public void renderRank_fullListBranch_rendersFullList() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        call(view, "renderRank", new Class<?>[]{});
        assertEquals(mContext.getString(com.gh4a.R.string.store_back),
                ((android.widget.TextView) rankContainer(view).getChildAt(0))
                        .getText().toString());
    }

    @Test
    public void renderRankOverview_onlyNonEmptyToggle_persists() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID))))));
        android.widget.LinearLayout container = rankContainer(view);
        call(view, "renderRankOverview",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        android.widget.CheckBox box =
                (android.widget.CheckBox) container.getChildAt(4);
        box.setChecked(true);
        assertTrue((Boolean) field(view, "mRankOnlyNonEmpty"));
        box.setChecked(false);
        assertFalse((Boolean) field(view, "mRankOnlyNonEmpty"));
    }

    @Test
    public void renderRankOverview_onlyNonEmptyWithRankedRepo_showsCards() throws Exception {
        StoreView view = new StoreView(mContext);
        StoreRepo docker = repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                5000, "Go", 0L, "v1", 100L, null, null, null,
                java.util.Arrays.asList("docker"));
        setField(view, "mFeedData", feedOf(java.util.Arrays.asList(docker)));
        setField(view, "mRankOnlyNonEmpty", true);
        android.widget.LinearLayout container = rankContainer(view);
        call(view, "renderRankOverview",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        // 标题/副标题/筛选/网格/勾选 + 有收录的分组头/注/卡片网格
        assertEquals(8, container.getChildCount());
    }

    @Test
    public void renderRankOverview_onlyNonEmptyAllEmpty_showsEmpty() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankOnlyNonEmpty", true);
        android.widget.LinearLayout container = rankContainer(view);
        call(view, "renderRankOverview",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertNotNull(findText(container,
                mContext.getString(com.gh4a.R.string.store_empty_result)));
    }

    @Test
    public void rankCardClick_opensFullList() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID)),
                        repo(EnumSet.of(StoreClassifier.Platform.IOS))))));
        android.widget.LinearLayout container = rankContainer(view);
        call(view, "renderRankOverview",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        android.widget.LinearLayout grid =
                (android.widget.LinearLayout) container.getChildAt(container.getChildCount() - 1);
        android.widget.LinearLayout row0 =
                (android.widget.LinearLayout) grid.getChildAt(0);
        row0.getChildAt(0).performClick();
        assertEquals(0, field(view, "mRankGroup"));
        assertEquals(0, field(view, "mRankList"));
    }

    @Test
    public void renderRankFullList_groupOutOfRange_rendersOverview() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", new java.util.ArrayList<>())));
        setField(view, "mRankGroup", 99);
        setField(view, "mRankList", 0);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank_title),
                ((android.widget.TextView) container.getChildAt(0)).getText().toString());
    }

    @Test
    public void renderRankFullList_listOutOfRange_rendersOverview() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 99);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(-1, field(view, "mRankGroup"));
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank_title),
                ((android.widget.TextView) container.getChildAt(0)).getText().toString());
    }

    @Test
    public void loadNewest_loadingGuard_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestLoading", true);
        call(view, "loadNewest", new Class<?>[]{});
        assertTrue((Boolean) field(view, "mNewestLoading"));
    }

    @Test
    public void canScrollUp_newestScrollNull_returnsFalse() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mShowingNewest", true);
        setField(view, "mNewestScroll", null);
        assertFalse(view.canScrollUp());
    }

    @Test
    public void canScrollUp_scrolledExplore_returnsTrue() throws Exception {
        StoreView view = new StoreView(mContext);
        attachView(view);
        setField(view, "mFeedData", feedOf(manyRepos(25)));
        call(view, "selectTab", new Class<?>[]{int.class}, 1);
        idleMain();
        tabScroll(view, 1).scrollTo(0, 100);
        assertTrue(view.canScrollUp());
    }

    @Test
    public void renderRankFullList_backClick_returnsToOverview() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        android.widget.LinearLayout container = rankContainer(view);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        container.getChildAt(0).performClick();
        assertEquals(-1, field(view, "mRankGroup"));
        assertEquals(-1, field(view, "mRankList"));
    }

    @Test
    public void renderRankFullList_sameListTwice_keepsShownCount() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", manyRepos(25))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        setField(view, "mRankShownGroup", 0);
        setField(view, "mRankShownList", 0);
        setField(view, "mRankShown", 40);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(40, field(view, "mRankShown"));
    }

    @Test
    public void renderRankFullList_backendRanks_subShowsBackend() throws Exception {
        StoreView view = new StoreView(mContext);
        StoreRepo ranked = repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                10, "Java", 0L, "v1", 100L, null, null,
                java.util.Arrays.asList("some-rank"), null);
        setField(view, "mFeedData", feedOf(java.util.Arrays.asList(ranked)));
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(ranked))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        String sub = ((android.widget.TextView) container.getChildAt(2))
                .getText().toString();
        assertTrue(sub.contains(mContext.getString(com.gh4a.R.string.store_rank_backend)));
    }

    @Test
    public void rankRowView_nullLanguageShowsUnknown() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                        10, null, 0L, "v1", 100L, null, null, null, null));
        android.widget.LinearLayout texts =
                (android.widget.LinearLayout) row.getChildAt(1);
        String sub = ((android.widget.TextView) texts.getChildAt(2))
                .getText().toString();
        assertTrue(sub.contains(mContext.getString(com.gh4a.R.string.unknown)));
    }

    @Test
    public void rankRowView_emptyVersionOmitsVersion() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                        10, "Java", 0L, "", 100L, null, null, null, null));
        android.widget.LinearLayout texts =
                (android.widget.LinearLayout) row.getChildAt(1);
        String sub = ((android.widget.TextView) texts.getChildAt(2))
                .getText().toString();
        assertEquals("★ 10 · Java", sub);
    }

    @Test
    public void rankRowView_twoPlatformsShowsGap() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID,
                        StoreClassifier.Platform.IOS),
                        10, "Java", 0L, "v1", 100L, null, null, null, null));
        android.widget.LinearLayout texts =
                (android.widget.LinearLayout) row.getChildAt(1);
        android.widget.LinearLayout icons =
                (android.widget.LinearLayout) texts.getChildAt(3);
        assertEquals(3, icons.getChildCount());
    }

    @Test
    public void rankRowView_clickOpensRepository() throws Exception {
        android.app.Activity activity = newActivity();
        StoreView view = new StoreView(activity);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        row.performClick();
        android.content.Intent started = nextStarted(activity);
        assertNotNull(started);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void rankRowView_longClickShowsMenu() throws Exception {
        android.app.Activity activity = newActivity();
        StoreView view = new StoreView(activity);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        assertTrue(row.performLongClick());
        assertEquals(1, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    // ---- 最新入库加载/错误/返回/重试 ----

    @Test
    public void loadNewestIfNeeded_withDataOrLoading_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        call(view, "loadNewestIfNeeded", new Class<?>[]{});
        assertFalse((Boolean) field(view, "mNewestLoading"));
        setField(view, "mNewestData", null);
        setField(view, "mNewestLoading", true);
        call(view, "loadNewestIfNeeded", new Class<?>[]{});
        assertTrue((Boolean) field(view, "mNewestLoading"));
    }

    @Test
    public void loadNewest_errorWhileShowing_rendersError() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApiNewestError()) {
            StoreView view = new StoreView(mContext);
            call(view, "showNewest", new Class<?>[]{});
            assertTrue((Boolean) field(view, "mNewestFailed"));
            android.widget.LinearLayout container =
                    (android.widget.LinearLayout) field(view, "mNewestContainer");
            assertNotNull(findText(container,
                    mContext.getString(com.gh4a.R.string.store_fetch_failed)));
        }
    }

    @Test
    public void loadNewest_errorWhileNotShowing_onlySetsFlags() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApiNewestError()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            assertTrue((Boolean) field(view, "mNewestFailed"));
            assertFalse((Boolean) field(view, "mShowingNewest"));
        }
    }

    @Test
    public void renderNewestList_backClick_returnsHome() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        call(view, "showNewest", new Class<?>[]{});
        android.widget.LinearLayout container =
                (android.widget.LinearLayout) field(view, "mNewestContainer");
        container.getChildAt(0).performClick();
        assertFalse((Boolean) field(view, "mShowingNewest"));
        assertEquals(0, (int) field(view, "mCurrentTab"));
    }

    @Test
    public void renderNewestList_errorRetry_reloads() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mNewestFailed", true);
            setField(view, "mNewestData", null);
            setField(view, "mShowingNewest", true);
            call(view, "renderNewestList", new Class<?>[]{});
            android.widget.LinearLayout container =
                    (android.widget.LinearLayout) field(view, "mNewestContainer");
            container.getChildAt(2).performClick();
            assertFalse((Boolean) field(view, "mNewestFailed"));
            assertNotNull(field(view, "mNewestData"));
        }
    }

    @Test
    public void newestPlatformCounts_withoutData_returnsZeros() throws Exception {
        StoreView view = new StoreView(mContext);
        int[] counts = (int[]) call(view, "newestPlatformCounts", new Class<?>[]{});
        assertEquals(9, counts.length);
        assertEquals(0, counts[0]);
    }

    @Test
    public void newestPlatformGridCellClick_appliesNewestPlatform() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        android.widget.LinearLayout grid =
                (android.widget.LinearLayout) call(view, "newestPlatformGrid",
                        new Class<?>[]{});
        android.widget.LinearLayout row0 =
                (android.widget.LinearLayout) grid.getChildAt(0);
        row0.getChildAt(1).performClick(); // android
        assertEquals("android", field(view, "mNewestPlatform"));
    }

    @Test
    public void applyNewestPlatform_sameKey_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        call(view, "applyNewestPlatform", new Class<?>[]{String.class}, "all");
        assertEquals("all", field(view, "mNewestPlatform"));
    }

    @Test
    public void applyNewestPlatform_newKey_rendersList() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", cannedFeed());
        call(view, "applyNewestPlatform", new Class<?>[]{String.class}, "android");
        assertEquals("android", field(view, "mNewestPlatform"));
        android.widget.LinearLayout container =
                (android.widget.LinearLayout) field(view, "mNewestContainer");
        assertTrue(container.getChildCount() > 0);
    }

    // ---- 最新入库行 / 通用行 / 长按菜单 ----

    @Test
    public void newestRowView_twoPlatformsShowsGap() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID,
                        StoreClassifier.Platform.IOS),
                        10, "Java", 0L, "v1", 100L, null, null, null, null));
        android.widget.LinearLayout bottom =
                (android.widget.LinearLayout) row.getChildAt(3);
        android.widget.LinearLayout icons =
                (android.widget.LinearLayout) bottom.getChildAt(0);
        assertEquals(3, icons.getChildCount());
    }

    @Test
    public void newestRowView_withUpdated_showsClockRow() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                        10, "Java", System.currentTimeMillis(), "v1", 100L,
                        null, null, null, null));
        assertEquals(4, row.getChildCount());
        android.widget.LinearLayout bottom =
                (android.widget.LinearLayout) row.getChildAt(3);
        assertEquals(5, bottom.getChildCount());
    }

    @Test
    public void newestRowView_clickOpensRepository() throws Exception {
        android.app.Activity activity = newActivity();
        StoreView view = new StoreView(activity);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        row.performClick();
        android.content.Intent started = nextStarted(activity);
        assertNotNull(started);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void newestRowView_longClickShowsMenu() throws Exception {
        android.app.Activity activity = newActivity();
        StoreView view = new StoreView(activity);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repo(EnumSet.of(StoreClassifier.Platform.ANDROID)));
        assertTrue(row.performLongClick());
        assertEquals(1, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void rowView_clickOpensRepository() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            android.app.Activity activity = newActivity();
            StoreView view = new StoreView(activity);
            view.onShown();
            android.view.View row = tabList(view, 0).getChildAt(1);
            row.performClick();
            android.content.Intent started = nextStarted(activity);
            assertNotNull(started);
            assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                    started.getComponent().getClassName());
        }
    }

    @Test
    public void rowView_longClickShowsMenu() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            android.app.Activity activity = newActivity();
            StoreView view = new StoreView(activity);
            view.onShown();
            android.view.View row = tabList(view, 0).getChildAt(1);
            assertTrue(row.performLongClick());
            assertEquals(1, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
        }
    }

    @Test
    public void showRowMenu_shareStartsChooser() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            android.app.Activity activity = newActivity();
            StoreView view = new StoreView(activity);
            view.onShown();
            tabList(view, 0).getChildAt(1).performLongClick();
            clickDialogItem(0);
            android.content.Intent started = nextStarted(activity);
            assertNotNull(started);
            assertEquals(android.content.Intent.ACTION_CHOOSER, started.getAction());
        }
    }

    @Test
    public void showRowMenu_openGithubStartsView() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            android.app.Activity activity = newActivity();
            StoreView view = new StoreView(activity);
            view.onShown();
            tabList(view, 0).getChildAt(1).performLongClick();
            clickDialogItem(1);
            android.content.Intent started = nextStarted(activity);
            assertNotNull(started);
            assertEquals(android.content.Intent.ACTION_VIEW, started.getAction());
            assertEquals("https://github.com/o/r", started.getData().toString());
        }
    }
}
