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
    public void selectTab_rankAndTagsRenderFromCache() throws Exception {
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
        setField(view, "mTagDetail", "windows");
        setField(view, "mTagQuery", "win");
        setField(view, "mTagShown", 60);
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
        assertNull(field(view, "mTagDetail"));
        assertEquals("", field(view, "mTagQuery"));
        assertEquals(20, field(view, "mTagShown"));
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
    public void renderRankFullList_sameGroupDifferentListResetsShown() throws Exception {
        // L1474：组相同、榜不同时也复位 mRankShown（|| 的第二操作数真臂；
        // 组不同臂已有 validList 测试、组榜都相同臂已有追加测试覆盖）。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID)),
                        repo(EnumSet.of(StoreClassifier.Platform.IOS))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 0);
        setField(view, "mRankShownGroup", 0);
        setField(view, "mRankShownList", 1);
        setField(view, "mRankShown", 40);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(20, field(view, "mRankShown"));
        assertEquals(0, field(view, "mRankShownGroup"));
        assertEquals(0, field(view, "mRankShownList"));
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
    public void applyCategory_fromRankTabResetsShown() throws Exception {
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

    private static StoreApi.StoreData feedOfWithBoards(java.util.List<StoreRepo> items,
            java.util.List<StoreRankGroups.Group> boards) {
        return new StoreApi.StoreData(items, false, "2026-11-14", boards);
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
        return containers[1];
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
    public void homeScrollListener_scrollUpOrNoFeed_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        android.view.View.OnScrollChangeListener l =
                scrollListener(tabScroll(view, 0));
        l.onScrollChange(null, 0, 0, 0, 10); // 未下滑
        l.onScrollChange(null, 0, 100, 0, 0); // 无 feed
        assertEquals(20, field(view, "mExploreShown"));
    }

    @Test
    public void homeScrollListener_shownReachedVisible_returns() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            setField(view, "mExploreShown", 60);
            setField(view, "mExploreVisibleSize", 3);
            scrollListener(tabScroll(view, 0))
                    .onScrollChange(null, 0, 100, 0, 0);
            assertEquals(60, field(view, "mExploreShown"));
        }
    }

    @Test
    public void homeScrollListener_atBottom_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            call(view, "selectTab", new Class<?>[]{int.class}, 0);
            assertEquals(25, field(view, "mExploreVisibleSize"));
            scrollListener(tabScroll(view, 0))
                    .onScrollChange(null, 0, 100, 0, 0);
            assertEquals(40, field(view, "mExploreShown"));
        }
    }

    @Test
    public void rankScrollListener_guardsReturn() throws Exception {
        StoreView view = new StoreView(mContext);
        android.view.View.OnScrollChangeListener l =
                scrollListener(tabScroll(view, 1));
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
            scrollListener(tabScroll(view, 1))
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
    public void resetToHome_fromRank_selectsHome() throws Exception {
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
        assertEquals(containers[1],
                call(view, "listTarget", new Class<?>[]{int.class}, 1));
    }

    @Test
    public void listTarget_tags_returnsListContainer() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout[] lists =
                (android.widget.LinearLayout[]) field(view, "mTabListContainers");
        assertEquals(lists[2],
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
    public void statCell_reposClick_staysOnHome() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            statCell(view, 0, 0).performClick();
            assertEquals(0, (int) field(view, "mCurrentTab"));
        }
    }

    @Test
    public void statCell_assetsClick_staysOnHome() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            statCell(view, 0, 1).performClick();
            assertEquals(0, (int) field(view, "mCurrentTab"));
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
            assertEquals(1, (int) field(view, "mCurrentTab"));
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

    // ---- 首页头/填充 ----

    @Test
    public void homeSortChipClick_appliesSort() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            view.onShown();
            // 首页头：0 统计块、1 排序 chips 行（4 个 chips，ⓘ 已删）。
            android.widget.LinearLayout sortChips =
                    (android.widget.LinearLayout) tabHeader(view, 0).getChildAt(1);
            assertEquals(4, sortChips.getChildCount());
            sortChips.getChildAt(1).performClick(); // 最近更新
            assertEquals(1, field(view, "mSort"));
        }
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
    public void ensureHomeFillViewport_nullScroll_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.ScrollView[] scrolls =
                (android.widget.ScrollView[]) field(view, "mTabScrolls");
        java.lang.reflect.Array.set(scrolls, 0, null);
        call(view, "ensureHomeFillViewport", new Class<?>[]{});
    }

    @Test
    public void ensureHomeFillViewport_noFeed_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        attachView(view);
        call(view, "ensureHomeFillViewport", new Class<?>[]{});
        idleMain();
        assertEquals(20, field(view, "mExploreShown"));
    }

    @Test
    public void ensureHomeFillViewport_wrongTab_returns() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            view.onShown();
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            call(view, "ensureHomeFillViewport", new Class<?>[]{});
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
    public void ensureHomeFillViewport_notScrollable_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            call(view, "selectTab", new Class<?>[]{int.class}, 0);
            assertEquals(20, field(view, "mExploreShown"));
            makeUnscrollable(tabScroll(view, 0));
            idleMain();
            assertEquals(40, field(view, "mExploreShown"));
        }
    }

    @Test
    public void ensureRankFillViewport_nullScroll_returns() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.ScrollView[] scrolls =
                (android.widget.ScrollView[]) field(view, "mTabScrolls");
        java.lang.reflect.Array.set(scrolls, 1, null);
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
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
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
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            assertEquals(20, field(view, "mRankShown"));
            makeUnscrollable(tabScroll(view, 1));
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
        // 动态化后：仅 topic 不再归属，须由后端 ranks + feed 的 boards 结构定位。
        StoreRepo docker = repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                5000, "Go", 0L, "v1", 100L, null, null,
                java.util.Arrays.asList("容器/镜像"),
                java.util.Arrays.asList("docker"));
        StoreRankGroups.Group tools = new StoreRankGroups.Group("工具");
        tools.lists.add(new StoreRankGroups.RankList("容器/镜像"));
        setField(view, "mFeedData", feedOfWithBoards(
                java.util.Arrays.asList(docker),
                java.util.Arrays.asList(tools)));
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
    public void canScrollUp_scrolledHome_returnsTrue() throws Exception {
        StoreView view = new StoreView(mContext);
        attachView(view);
        setField(view, "mFeedData", feedOf(manyRepos(25)));
        call(view, "selectTab", new Class<?>[]{int.class}, 0);
        idleMain();
        tabScroll(view, 0).scrollTo(0, 100);
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
    public void loadNewest_successWhileOnHome_updatesCountView() throws Exception {
        // L1628：不在最新页、当前为首页且 feed 已加载时，只刷新统计格数字，
        // 不重建首页头（与 showing 时整页重绘互补）。
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.just(cannedFeed()));
        try {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mCurrentTab", 0);
            setField(view, "mShowingNewest", false);
            android.widget.TextView countView =
                    new android.widget.TextView(mContext);
            setField(view, "mNewestCountView", countView);
            call(view, "loadNewest", new Class<?>[]{});
            assertFalse((Boolean) field(view, "mNewestLoading"));
            assertFalse((Boolean) field(view, "mNewestFailed"));
            assertEquals(StoreViews.formatStars(3), countView.getText().toString());
        } finally {
            mocked.close();
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

    // ---- A 组补缺（2026-11-22）：StoreView 剩余分支 ----

    private static MockedStatic<StoreApi> mockNewest(int n) {
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.just(feedOf(manyRepos(n))));
        return mocked;
    }

    private static StoreRepo repoNoDescNoPlatforms() {
        return new StoreRepo("o/r", "o", "r", "", "Java", "", 10, 2, 100L,
                new java.util.ArrayList<>(), new java.util.HashSet<>(),
                "v1", 0L, EnumSet.noneOf(StoreClassifier.Platform.class), 1,
                null, null, null, null, "tools");
    }

    private static StoreRepo repoWithCategory(String fullName, String category) {
        return new StoreRepo(fullName, "o", "r", "d", "Java", "", 10, 2, 100L,
                new java.util.ArrayList<>(), new java.util.HashSet<>(),
                "v1", 0L, EnumSet.of(StoreClassifier.Platform.ANDROID), 1,
                null, null, null, null, category);
    }

    @Test
    public void rankScrollListener_groupSetListNegative_returns() throws Exception {
        // L256：mRankGroup >= 0 但 mRankList < 0 时直接返回（|| 的第二臂）。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", -1);
        scrollListener(tabScroll(view, 1)).onScrollChange(null, 0, 100, 0, 0);
        assertEquals(20, field(view, "mRankShown"));
    }

    @Test
    public void rankScrollListener_scrollable_returnsWithoutAppend() throws Exception {
        // L262：榜单可继续下滑时不追加。
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            setField(view, "mRankGroups", java.util.Arrays.asList(
                    rankGroupWithList("Hot", manyRepos(25))));
            setField(view, "mRankGroup", 0);
            setField(view, "mRankList", 0);
            setField(view, "mRankShownGroup", 0);
            setField(view, "mRankShownList", 0);
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            // selectTab 把 mRankShown 复位成 20；改回 25 再重绘，凑满 25 行
            // 才可滚动（只展 20 行时滚动性不确定）。
            setField(view, "mRankShown", 25);
            call(view, "renderRank", new Class<?>[]{});
            setField(view, "mRankFullTotal", 40);
            idleMain();
            assertTrue(tabScroll(view, 1).canScrollVertically(1));
            scrollListener(tabScroll(view, 1)).onScrollChange(null, 0, 100, 0, 0);
            assertEquals(25, field(view, "mRankShown"));
        }
    }

    @Test
    public void canScrollUp_showingNewestScrolled_returnsTrue() throws Exception {
        // L333：最新入库子屏已下滑时 canScrollUp() 为真。
        try (MockedStatic<StoreApi> mocked = mockNewest(60)) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            view.onShown();
            call(view, "showNewest", new Class<?>[]{});
            idleMain();
            assertEquals(60, ((StoreApi.StoreData) field(view, "mNewestData"))
                    .items.size());
            android.widget.ScrollView newestScroll =
                    (android.widget.ScrollView) field(view, "mNewestScroll");
            newestScroll.scrollTo(0, 100);
            assertTrue(view.canScrollUp());
        }
    }

    @Test
    public void applyExploreSort_sameSortWithoutFeed_loadsFeed() throws Exception {
        // L510：排序未变但 feed 为空时不走刷新分支，改为加载。
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            call(view, "applyExploreSort", new Class<?>[]{int.class}, 0);
            assertEquals(0, field(view, "mSort"));
            assertNotNull(field(view, "mFeedData"));
        }
    }

    @Test
    public void ensureCounts_withoutFeed_skipsCompute() throws Exception {
        // L613：feed 为空时不算计数。
        StoreView view = new StoreView(mContext);
        call(view, "ensureCounts", new Class<?>[]{});
        assertFalse((Boolean) field(view, "mCountsReady"));
        assertEquals(null, field(view, "mPlatformCounts"));
    }

    @Test
    public void ensureCounts_secondCallSkipsCompute() throws Exception {
        // L613：计数已算好时第二次调用不再重算（同一数组引用）。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        call(view, "ensureCounts", new Class<?>[]{});
        assertTrue((Boolean) field(view, "mCountsReady"));
        Object counts = field(view, "mPlatformCounts");
        assertNotNull(counts);
        call(view, "ensureCounts", new Class<?>[]{});
        assertTrue(counts == field(view, "mPlatformCounts"));
    }

    @Test
    public void statCell_nullListener_buildsWithoutClick() throws Exception {
        // L956：listener 为 null 时不设点击监听（生产里 4 格都传非空，
        // 直接调私有方法覆盖该臂）。
        StoreView view = new StoreView(mContext);
        android.view.View cell = (android.view.View) call(view, "statCell",
                new Class<?>[]{int.class, String.class, String.class,
                        boolean.class, boolean.class,
                        android.view.View.OnClickListener.class},
                com.gh4a.R.drawable.ic_stat_package, "5", "label",
                false, false, null);
        assertNotNull(cell);
        assertFalse(cell.hasOnClickListeners());
        assertEquals("5", ((android.widget.TextView)
                ((android.widget.LinearLayout) cell).getChildAt(2))
                .getText().toString());
    }

    @Test
    public void ensureRankFillViewport_wrongTab_returns() throws Exception {
        // L1241：feed 已有但当前页不是排行榜时直接返回。
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            view.onShown();
            assertEquals(0, (int) field(view, "mCurrentTab"));
            call(view, "ensureRankFillViewport", new Class<?>[]{});
            idleMain();
            assertEquals(20, field(view, "mRankShown"));
        }
    }

    @Test
    public void ensureRankFillViewport_listNegative_returns() throws Exception {
        // L1244：mRankGroup >= 0 但 mRankList < 0 时直接返回。
        StoreView view = new StoreView(mContext);
        attachView(view);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", -1);
        call(view, "selectTab", new Class<?>[]{int.class}, 1);
        call(view, "ensureRankFillViewport", new Class<?>[]{});
        idleMain();
        assertEquals(20, field(view, "mRankShown"));
    }

    @Test
    public void ensureRankFillViewport_scrollable_returnsWithoutAppend()
            throws Exception {
        // L1247：榜单可继续下滑时不追加。
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            setField(view, "mRankGroups", java.util.Arrays.asList(
                    rankGroupWithList("Hot", manyRepos(25))));
            setField(view, "mRankGroup", 0);
            setField(view, "mRankList", 0);
            setField(view, "mRankShownGroup", 0);
            setField(view, "mRankShownList", 0);
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            setField(view, "mRankShown", 25);
            call(view, "renderRank", new Class<?>[]{});
            setField(view, "mRankFullTotal", 40);
            idleMain();
            assertTrue(tabScroll(view, 1).canScrollVertically(1));
            call(view, "ensureRankFillViewport", new Class<?>[]{});
            idleMain();
            assertEquals(25, field(view, "mRankShown"));
        }
    }

    @Test
    public void ensureRankFillViewport_shownComplete_returnsWithoutAppend()
            throws Exception {
        // L1247：已展完（mRankShown >= 总数）时不追加。
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            attachView(view);
            setField(view, "mFeedData", feedOf(manyRepos(25)));
            setField(view, "mRankGroups", java.util.Arrays.asList(
                    rankGroupWithList("Hot", manyRepos(25))));
            setField(view, "mRankGroup", 0);
            setField(view, "mRankList", 0);
            call(view, "selectTab", new Class<?>[]{int.class}, 1);
            setField(view, "mRankShown", 25);
            setField(view, "mRankFullTotal", 25);
            makeUnscrollable(tabScroll(view, 1));
            call(view, "ensureRankFillViewport", new Class<?>[]{});
            idleMain();
            assertEquals(25, field(view, "mRankShown"));
        }
    }

    @Test
    public void exploreFiltered_matchingCategoryKeepsItem() throws Exception {
        // L1270：用途已选且条目用途相同（第二臂为假）时保留该条。
        StoreView view = new StoreView(mContext);
        setField(view, "mCategory", "ai");
        java.util.List<StoreRepo> pool = java.util.Arrays.asList(
                repoWithCategory("o/ai", "ai"),
                repoWithCategory("o/tools", "tools"));
        @SuppressWarnings("unchecked")
        java.util.List<StoreRepo> out = (java.util.List<StoreRepo>) call(view,
                "exploreFiltered", new Class<?>[]{java.util.List.class}, pool);
        assertEquals(1, out.size());
        assertEquals("o/ai", out.get(0).fullName());
    }

    @Test
    public void renderRank_groupSetListNegative_rendersOverview() throws Exception {
        // L1292：mRankGroup >= 0 但 mRankList < 0 时渲染概览。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", -1);
        call(view, "renderRank", new Class<?>[]{});
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank_title),
                ((android.widget.TextView) rankContainer(view).getChildAt(0))
                        .getText().toString());
    }

    @Test
    public void renderRankFullList_listNegative_rendersOverview() throws Exception {
        // L1446：榜单下标为负时复位并渲染概览。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", -1);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(-1, field(view, "mRankGroup"));
        assertEquals(-1, field(view, "mRankList"));
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank_title),
                ((android.widget.TextView) container.getChildAt(0)).getText().toString());
    }

    @Test
    public void renderRankFullList_listIndexOutOfRange_resetsBoth() throws Exception {
        // L1446：榜单下标越界时复位并渲染概览。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        setField(view, "mRankGroups", java.util.Arrays.asList(
                rankGroupWithList("Hot", java.util.Arrays.asList(
                        repo(EnumSet.of(StoreClassifier.Platform.ANDROID))))));
        setField(view, "mRankGroup", 0);
        setField(view, "mRankList", 5);
        android.widget.LinearLayout container = new android.widget.LinearLayout(mContext);
        call(view, "renderRankFullList",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(-1, field(view, "mRankGroup"));
        assertEquals(-1, field(view, "mRankList"));
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank_title),
                ((android.widget.TextView) container.getChildAt(0)).getText().toString());
    }

    @Test
    public void renderRankFullList_sameListKeepsShown() throws Exception {
        // L1474：同榜内追加不重置 mRankShown。
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", feedOf(manyRepos(25)));
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
        assertEquals(25, field(view, "mRankFullTotal"));
    }

    @Test
    public void rankRowView_emptyDescAndPlatforms_omitsDescAndIcons()
            throws Exception {
        // L1538 + L1553：简介为空且无平台时行里只剩名与副标题。
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "rankRowView", new Class<?>[]{int.class, StoreRepo.class},
                1, repoNoDescNoPlatforms());
        android.widget.LinearLayout texts =
                (android.widget.LinearLayout) row.getChildAt(1);
        assertEquals(2, texts.getChildCount());
        assertEquals("o/r", ((android.widget.TextView) texts.getChildAt(0))
                .getText().toString());
    }

    @Test
    public void loadNewest_homeTabUpdatesCountView() throws Exception {
        // L1628（首页＋feed）+ L1633（countView 非空）：最新入库数到了只改数字。
        try (MockedStatic<StoreApi> mocked = mockNewest(5)) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mCurrentTab", 0);
            setField(view, "mShowingNewest", false);
            android.widget.TextView countView =
                    new android.widget.TextView(mContext);
            countView.setText("old");
            setField(view, "mNewestCountView", countView);
            call(view, "loadNewest", new Class<?>[]{});
            assertEquals(StoreViews.formatStars(5),
                    countView.getText().toString());
        }
    }

    @Test
    public void loadNewest_rankTabSkipsCountView() throws Exception {
        // L1628：不在首页时不改统计数字。
        try (MockedStatic<StoreApi> mocked = mockNewest(5)) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mCurrentTab", 1);
            setField(view, "mShowingNewest", false);
            android.widget.TextView countView =
                    new android.widget.TextView(mContext);
            countView.setText("old");
            setField(view, "mNewestCountView", countView);
            call(view, "loadNewest", new Class<?>[]{});
            assertEquals("old", countView.getText().toString());
            assertEquals(5, ((StoreApi.StoreData) field(view, "mNewestData"))
                    .items.size());
        }
    }

    @Test
    public void loadNewest_nullCountViewSkipsSetText() throws Exception {
        // L1633：countView 为空时不改数字、不崩溃。
        try (MockedStatic<StoreApi> mocked = mockNewest(5)) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mCurrentTab", 0);
            setField(view, "mShowingNewest", false);
            setField(view, "mNewestCountView", null);
            call(view, "loadNewest", new Class<?>[]{});
            assertEquals(5, ((StoreApi.StoreData) field(view, "mNewestData"))
                    .items.size());
        }
    }

    @Test
    public void renderNewestList_nullDataWithoutFailure_showsError() throws Exception {
        // L1668：未失败但数据为空时同样显示失败重试（第二臂）。
        StoreView view = new StoreView(mContext);
        setField(view, "mNewestData", null);
        setField(view, "mNewestFailed", false);
        call(view, "renderNewestList", new Class<?>[]{});
        android.widget.LinearLayout container =
                (android.widget.LinearLayout) field(view, "mNewestContainer");
        assertNotNull(findText(container,
                mContext.getString(com.gh4a.R.string.store_fetch_failed)));
    }

    @Test
    public void newestRowView_emptyDescNoPlatformsNoUpdated_omitsBottom()
            throws Exception {
        // L1776 + L1798：简介为空、无平台、无更新时间时行里只剩名与 meta。
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoNoDescNoPlatforms());
        assertEquals(2, row.getChildCount());
        assertEquals("o/r", ((android.widget.TextView) row.getChildAt(0))
                .getText().toString());
    }

    @Test
    public void newestRowView_nullLanguageShowsDash() throws Exception {
        // L1789：语言为 null 时 meta 显示“—”。
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoVariant(EnumSet.noneOf(StoreClassifier.Platform.class),
                        0, null, 0L));
        assertEquals(3, row.getChildCount());
        assertEquals("— · v1", ((android.widget.TextView) row.getChildAt(2))
                .getText().toString());
    }

    @Test
    public void newestRowView_emptyVersionOmitsVersion() throws Exception {
        // L1790：版本为空时 meta 不带版本后缀。
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoFull(EnumSet.of(StoreClassifier.Platform.ANDROID),
                        10, "Java", 0L, "", 100L, null, null, null, null));
        assertEquals(4, row.getChildCount());
        assertEquals("★ " + StoreViews.formatStars(10) + " · Java",
                ((android.widget.TextView) row.getChildAt(2)).getText().toString());
    }

    @Test
    public void newestRowView_updatedWithoutPlatforms_showsClockOnly()
            throws Exception {
        // L1798：无平台但有更新时间时底栏只有时钟＋时间，无图标。
        StoreView view = new StoreView(mContext);
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(view,
                "newestRowView", new Class<?>[]{StoreRepo.class},
                repoFull(EnumSet.noneOf(StoreClassifier.Platform.class),
                        10, "Java", System.currentTimeMillis(), "v1", 100L,
                        null, null, null, null));
        assertEquals(4, row.getChildCount());
        android.widget.LinearLayout bottom =
                (android.widget.LinearLayout) row.getChildAt(3);
        assertEquals(5, bottom.getChildCount());
        android.widget.LinearLayout icons =
                (android.widget.LinearLayout) bottom.getChildAt(0);
        assertEquals(0, icons.getChildCount());
    }

    @Test
    public void loadNewest_successOnHome_noFeedData_skipsCountView() throws Exception {
        // L1628（第 3 轮）：不在最新页、当前为首页但 feed 尚未加载时，
        // else-if 第二操作数 mFeedData != null 为假，不碰统计格。
        // 与 R2 的 feed 已加载版（真臂）互补。
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.just(cannedFeed()));
        try {
            StoreView view = new StoreView(mContext);
            // mFeedData 故意不置（null）；mCurrentTab 默认即 TAB_HOME(0)。
            setField(view, "mShowingNewest", false);
            android.widget.TextView countView =
                    new android.widget.TextView(mContext);
            setField(view, "mNewestCountView", countView);
            call(view, "loadNewest", new Class<?>[]{});
            assertFalse((Boolean) field(view, "mNewestLoading"));
            assertFalse((Boolean) field(view, "mNewestFailed"));
            assertEquals("", countView.getText().toString());
        } finally {
            mocked.close();
        }
    }

    @Test
    public void loadNewest_successOffHome_skipsCountView() throws Exception {
        // L1628（第 3 轮）：不在最新页、当前不在首页时，else-if 第一操作数
        // mCurrentTab == TAB_HOME 为假，不碰统计格。
        MockedStatic<StoreApi> mocked =
                mockStatic(StoreApi.class, CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(any(), anyString(), anyString()))
                .thenReturn(Single.just(cannedFeed()));
        mocked.when(() -> StoreApi.loadNewest(any()))
                .thenReturn(Single.just(cannedFeed()));
        try {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", cannedFeed());
            setField(view, "mCurrentTab", 1);
            setField(view, "mShowingNewest", false);
            android.widget.TextView countView =
                    new android.widget.TextView(mContext);
            setField(view, "mNewestCountView", countView);
            call(view, "loadNewest", new Class<?>[]{});
            assertFalse((Boolean) field(view, "mNewestLoading"));
            assertFalse((Boolean) field(view, "mNewestFailed"));
            assertEquals("", countView.getText().toString());
        } finally {
            mocked.close();
        }
    }

    // ---- 2026-11-22：首页／排行榜／标签云三页签 ----

    private static StoreRepo repoWithTopic(String fullName, int stars, String topic) {
        return repoWithTopicPlatform(fullName, stars, topic,
                com.gh4a.fragment.StoreClassifier.Platform.ANDROID);
    }

    private static StoreRepo repoWithTopicPlatform(String fullName, int stars,
            String topic, com.gh4a.fragment.StoreClassifier.Platform platform) {
        return new StoreRepo(fullName, "o", "r", "d", "Java", "", stars, 2, 100L,
                java.util.Arrays.asList(topic), new java.util.HashSet<>(),
                "v1", 0L, EnumSet.of(platform), 1,
                null, null, null, null, "tools");
    }

    /** 12 条 Android 的 windows 标签＋12 条 Windows 的 rust 标签（平台筛专用）。 */
    private static java.util.List<StoreRepo> platformFeed() {
        java.util.List<StoreRepo> out = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            out.add(repoWithTopicPlatform("o/win" + i, 10 + i, "windows",
                    com.gh4a.fragment.StoreClassifier.Platform.ANDROID));
        }
        for (int i = 0; i < 12; i++) {
            out.add(repoWithTopicPlatform("o/rust" + i, 10 + i, "rust",
                    com.gh4a.fragment.StoreClassifier.Platform.WINDOWS));
        }
        return out;
    }

    private static java.util.List<StoreRepo> taggedFeed() {
        java.util.List<StoreRepo> out = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            out.add(repoWithTopic("o/win" + i, 10 + i, "windows"));
        }
        for (int i = 0; i < 5; i++) {
            out.add(repoWithTopic("o/rust" + i, 10 + i, "rust"));
        }
        out.add(repoWithTopic("o/rare", 10, "rare-tag"));
        return out;
    }

    /** 云里的标签是“名字＋空格＋收录数”的 TextView，按名字前缀找。 */
    private static android.widget.TextView findTag(android.view.View root, String name) {
        if (root instanceof android.widget.TextView) {
            CharSequence t = ((android.widget.TextView) root).getText();
            if (t != null && t.toString().startsWith(name + " ")) {
                return (android.widget.TextView) root;
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.TextView found = findTag(g.getChildAt(i), name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static android.widget.EditText findEditText(android.view.View root) {
        if (root instanceof android.widget.EditText) {
            return (android.widget.EditText) root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.EditText found = findEditText(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void tabBarLabels_homeRankTags() throws Exception {
        StoreView view = new StoreView(mContext);
        android.widget.TextView[] labels =
                (android.widget.TextView[]) field(view, "mTabLabels");
        assertEquals(mContext.getString(com.gh4a.R.string.store_home),
                labels[0].getText().toString());
        assertEquals(mContext.getString(com.gh4a.R.string.store_rank),
                labels[1].getText().toString());
        assertEquals(mContext.getString(com.gh4a.R.string.store_tags),
                labels[2].getText().toString());
    }

    @Test
    public void renderHomeHeader_noExploreTitle_hasStatsAndSortChips() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", cannedFeed());
        call(view, "renderHomeHeader", new Class<?>[]{});
        android.widget.LinearLayout header = tabHeader(view, 0);
        // 标题行已删：旧标题文案不在头里。
        assertNull(findText(header,
                mContext.getString(com.gh4a.R.string.store_explore_title)));
        // 统计块在头顶（第 0 个孩子），排序 chips 在第 1 个孩子里。
        android.widget.LinearLayout stats =
                (android.widget.LinearLayout) header.getChildAt(0);
        assertEquals(2, stats.getChildCount());
        assertNotNull(findText(stats,
                mContext.getString(com.gh4a.R.string.store_stat_repos)));
        assertNotNull(findText(header,
                mContext.getString(com.gh4a.R.string.store_sort_stars)));
    }

    @Test
    public void renderTags_cloudShowsOnlyHotTags() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(taggedFeed()));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            android.widget.LinearLayout header = tabHeader(view, 2);
            assertNotNull(findTag(header, "windows"));
            assertNull(findTag(header, "rust"));
            assertNull(findTag(header, "rare-tag"));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_tags_stats, 1, 3)));
        }
    }

    @Test
    public void renderTags_searchFiltersTags() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(taggedFeed()));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            android.widget.EditText search = findEditText(tabHeader(view, 2));
            assertNotNull(search);
            search.setText("rust");
            android.widget.LinearLayout header = tabHeader(view, 2);
            assertNull(findTag(header, "windows"));
            assertNotNull(findTag(header, "rust"));
            search.setText("zzz-no-match");
            assertNotNull(findText(tabHeader(view, 2),
                    mContext.getString(com.gh4a.R.string.store_tags_empty)));
            search.setText("");
            assertNotNull(findTag(tabHeader(view, 2), "windows"));
        }
    }

    @Test
    public void ensureTagCounts_countsTopicsSorted() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", feedOf(taggedFeed()));
        call(view, "ensureTagCounts", new Class<?>[]{});
        @SuppressWarnings("unchecked")
        java.util.List<Object> tags =
                (java.util.List<Object>) field(view, "mTagCounts");
        assertEquals(3, tags.size());
        // TagCount 是私有静态类，用反射读名字与收录数。
        java.lang.reflect.Field nameField =
                tags.get(0).getClass().getDeclaredField("name");
        nameField.setAccessible(true);
        java.lang.reflect.Field countField =
                tags.get(0).getClass().getDeclaredField("count");
        countField.setAccessible(true);
        assertEquals("windows", nameField.get(tags.get(0)));
        assertEquals(12, countField.get(tags.get(0)));
        assertEquals("rust", nameField.get(tags.get(1)));
        assertEquals(5, countField.get(tags.get(1)));
        assertEquals("rare-tag", nameField.get(tags.get(2)));
        assertEquals(1, countField.get(tags.get(2)));
    }

    @Test
    public void showTagDetail_listsReposByStarsAndBack() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(java.util.Arrays.asList(
                    repoWithTopic("o/low", 10, "windows"),
                    repoWithTopic("o/high", 50, "windows"),
                    repoWithTopic("o/mid", 30, "windows"),
                    repoWithTopic("o/other", 99, "rust"))));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            call(view, "showTagDetail", new Class<?>[]{String.class}, "windows");
            assertEquals("windows", field(view, "mTagDetail"));
            assertEquals(3, field(view, "mTagVisibleSize"));
            // 列表里以 o/ 开头的名按出现顺序收：应为星标降序。
            java.util.List<String> names = new java.util.ArrayList<>();
            collectRepoNames(tabList(view, 2), names);
            assertEquals(java.util.Arrays.asList("o/high", "o/mid", "o/low"), names);
            // 点“‹ 标签云”回云（本组 windows 只有 3 条、不是热门，框内搜才见）。
            tabList(view, 2).getChildAt(0).performClick();
            assertNull(field(view, "mTagDetail"));
            android.widget.EditText search = findEditText(tabHeader(view, 2));
            assertNotNull(search);
            search.setText("windows");
            assertNotNull(findTag(tabHeader(view, 2), "windows"));
        }
    }

    private static void collectRepoNames(android.view.View root,
            java.util.List<String> names) {
        if (root instanceof android.widget.TextView) {
            CharSequence t = ((android.widget.TextView) root).getText();
            if (t != null && t.toString().startsWith("o/")) {
                names.add(t.toString());
            }
            return;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectRepoNames(g.getChildAt(i), names);
            }
        }
    }

    @Test
    public void tagDetail_scrollListener_appendsPage() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            java.util.List<StoreRepo> items = new java.util.ArrayList<>();
            for (int i = 0; i < 25; i++) {
                items.add(repoWithTopic("o/w" + i, 10 + i, "windows"));
            }
            setField(view, "mFeedData", feedOf(items));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            call(view, "showTagDetail", new Class<?>[]{String.class}, "windows");
            assertEquals(25, field(view, "mTagVisibleSize"));
            assertEquals(20, field(view, "mTagShown"));
            scrollListener(tabScroll(view, 2))
                    .onScrollChange(null, 0, 100, 0, 0);
            assertEquals(40, field(view, "mTagShown"));
        }
    }

    @Test
    public void selectTab_leavingTagsExitsDetail() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(taggedFeed()));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            call(view, "showTagDetail", new Class<?>[]{String.class}, "windows");
            assertEquals("windows", field(view, "mTagDetail"));
            call(view, "selectTab", new Class<?>[]{int.class}, 0);
            assertNull(field(view, "mTagDetail"));
        }
    }

    // ---- 2026-11-22 晚：首页 ⓘ 去掉、标签云加系统筛选 ----

    @Test
    public void tagPlatformGrid_showsNineCells() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(platformFeed()));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            android.widget.LinearLayout header = tabHeader(view, 2);
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_filter_platform)));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_all)));
            assertNotNull(findText(header, "Android"));
            assertNotNull(findText(header, "iOS"));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_windows)));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_macos)));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_linux)));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_harmony)));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_extension)));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_platform_other)));
        }
    }

    @Test
    public void applyTagPlatform_filtersCloudCounts() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(platformFeed()));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            android.widget.LinearLayout header = tabHeader(view, 2);
            assertNotNull(findTag(header, "windows"));
            assertNotNull(findTag(header, "rust"));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_tags_stats, 2, 2)));
            call(view, "applyTagPlatform",
                    new Class<?>[]{String.class}, "android");
            assertEquals("android", field(view, "mTagPlatform"));
            assertEquals("android",
                    com.gh4a.fragment.StorePrefs.getTagPlatform(mContext));
            header = tabHeader(view, 2);
            assertNotNull(findTag(header, "windows"));
            assertNull(findTag(header, "rust"));
            assertNotNull(findText(header, mContext.getString(
                    com.gh4a.R.string.store_tags_stats, 1, 1)));
        }
    }

    @Test
    public void showTagDetail_platformFiltered() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            StoreView view = new StoreView(mContext);
            setField(view, "mFeedData", feedOf(platformFeed()));
            call(view, "selectTab", new Class<?>[]{int.class}, 2);
            call(view, "applyTagPlatform",
                    new Class<?>[]{String.class}, "windows");
            call(view, "showTagDetail", new Class<?>[]{String.class}, "windows");
            assertEquals(0, field(view, "mTagVisibleSize"));
            assertNotNull(findText(tabList(view, 2), mContext.getString(
                    com.gh4a.R.string.store_empty_result)));
            call(view, "showTagDetail", new Class<?>[]{String.class}, "rust");
            assertEquals(12, field(view, "mTagVisibleSize"));
        }
    }

    @Test
    public void ensureTagCounts_platformFiltered() throws Exception {
        StoreView view = new StoreView(mContext);
        setField(view, "mFeedData", feedOf(platformFeed()));
        setField(view, "mTagPlatform", "android");
        setField(view, "mTagCountsReady", false);
        call(view, "ensureTagCounts", new Class<?>[]{});
        @SuppressWarnings("unchecked")
        java.util.List<Object> tags =
                (java.util.List<Object>) field(view, "mTagCounts");
        assertEquals(1, tags.size());
        java.lang.reflect.Field nameField =
                tags.get(0).getClass().getDeclaredField("name");
        nameField.setAccessible(true);
        java.lang.reflect.Field countField =
                tags.get(0).getClass().getDeclaredField("count");
        countField.setAccessible(true);
        assertEquals("windows", nameField.get(tags.get(0)));
        assertEquals(12, countField.get(tags.get(0)));
    }
}
