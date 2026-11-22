package com.gh4a.fragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.adapter.ReleaseRadarAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/** ReleaseRadarFragment：Release 雷达（RadarItem + newInstance/onCreate/empty 文本）。 */
@RunWith(RobolectricTestRunner.class)
public class ReleaseRadarFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    @Test
    public void radarItem_ctorKeepsFields() {
        ReleaseRadarFragment.RadarItem item = new ReleaseRadarFragment.RadarItem(
                "octo", "cat", 11L, "v1.0", "Release 1.0", 1700000000000L, 1699900000000L);
        assertEquals("octo", item.owner);
        assertEquals("cat", item.repo);
        assertEquals(11L, item.releaseId);
        assertEquals("v1.0", item.tagName);
        assertEquals("Release 1.0", item.releaseName);
        assertEquals(1700000000000L, item.publishedAt);
        assertEquals(1699900000000L, item.createdAt);
    }

    @Test
    public void radarItem_sortTimePrefersPublishedAt() {
        ReleaseRadarFragment.RadarItem withPublished = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 5000L, 3000L);
        assertEquals(5000L, withPublished.sortTime());
        ReleaseRadarFragment.RadarItem withoutPublished = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 0L, 3000L);
        assertEquals(3000L, withoutPublished.sortTime());
        ReleaseRadarFragment.RadarItem neither = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 0L, 0L);
        assertEquals(0L, neither.sortTime());
    }

    @Test
    public void newInstance_storesLogin() {
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        assertEquals("alice", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLogin() throws Exception {
        ReleaseRadarFragment f = attach(ReleaseRadarFragment.newInstance("alice"));
        Field login = ReleaseRadarFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals("alice", login.get(f));
    }

    @Test
    public void onCreate_nullArgumentsMeansNullLogin() throws Exception {
        ReleaseRadarFragment f = attach(new ReleaseRadarFragment());
        Field login = ReleaseRadarFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals(null, login.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        ReleaseRadarFragment f = attach(ReleaseRadarFragment.newInstance("alice"));
        assertEquals(R.string.no_release_radar_items, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        ReleaseRadarFragment f = attach(ReleaseRadarFragment.newInstance("alice"));
        assertNotNull(f.onCreateAdapter());
    }

    // ---- 完整挂载：BaseActivity host，挂进 android.R.id.content ----

    public static class RadarHostActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }
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

    private static Object field(Object target, String name) throws Exception {
        Field f = null;
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                f = c.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = null;
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                f = c.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        f.setAccessible(true);
        f.set(target, value);
    }

    private static RadarHostActivity attachResumed(ReleaseRadarFragment f) {
        RadarHostActivity activity =
                Robolectric.buildActivity(RadarHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        idleMain();
        return activity;
    }

    private static RadarHostActivity attachNoIdle(ReleaseRadarFragment f) {
        RadarHostActivity activity =
                Robolectric.buildActivity(RadarHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        return activity;
    }

    private static void remove(ReleaseRadarFragment f, RadarHostActivity activity) {
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        idleMain();
    }

    private static ReleaseRadarFragment.RadarItem item(String repo, long releaseId) {
        return new ReleaseRadarFragment.RadarItem("octo", repo, releaseId,
                "v1.0", "R " + repo, 1700000000000L, 1699900000000L);
    }

    private static int adapterCount(ReleaseRadarFragment f) throws Exception {
        ReleaseRadarAdapter adapter = (ReleaseRadarAdapter) field(f, "mAdapter");
        return adapter == null ? -1 : adapter.getCount();
    }

    private static String emptyViewText(ReleaseRadarFragment f) {
        android.widget.TextView emptyView =
                f.getView().findViewById(android.R.id.empty);
        return emptyView.getText().toString();
    }

    private static MockedStatic<RadarGraphQL> mockFetch(
            Single<List<ReleaseRadarFragment.RadarItem>> single) {
        MockedStatic<RadarGraphQL> mocked = Mockito.mockStatic(RadarGraphQL.class);
        mocked.when(() -> RadarGraphQL.fetch("alice")).thenReturn(single);
        return mocked;
    }

    @Test
    public void onViewCreated_noCache_loadsDirect() throws Exception {
        List<ReleaseRadarFragment.RadarItem> fresh =
                Arrays.asList(item("a", 1L), item("b", 2L));
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.just(fresh))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 2;
                } catch (Exception e) {
                    return false;
                }
            });
            assertTrue((Boolean) field(f, "mLoadComplete"));
            assertFalse((Boolean) field(f, "mShowingCache"));
            // 新数据落盘进真实缓存。
            assertEquals(2, RadarCache.load(
                    RuntimeEnvironment.getApplication(), "alice").size());
        }
    }

    @Test
    public void onViewCreated_withCache_showsCacheThenRefreshes() throws Exception {
        RadarCache.save(RuntimeEnvironment.getApplication(), "alice",
                Arrays.asList(item("cached", 9L)));
        List<ReleaseRadarFragment.RadarItem> fresh =
                Arrays.asList(item("a", 1L), item("b", 2L));
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.just(fresh))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachNoIdle(f);
            // 同步的缓存渲染：还没等后台刷新，缓存已上屏。
            assertEquals(1, adapterCount(f));
            assertTrue((Boolean) field(f, "mShowingCache"));
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 2;
                } catch (Exception e) {
                    return false;
                }
            });
            assertFalse((Boolean) field(f, "mShowingCache"));
            assertTrue((Boolean) field(f, "mLoadComplete"));
        }
    }

    @Test
    public void onViewCreated_emptyCache_showsLoadingStage() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never());
             MockedStatic<RadarCache> cacheMock =
                     Mockito.mockStatic(RadarCache.class)) {
            cacheMock.when(() -> RadarCache.load(any(), any()))
                    .thenReturn(new ArrayList<>());
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            // 缓存非空判断的假分支：空缓存走加载中。
            assertEquals(f.getString(R.string.loading_msg), emptyViewText(f));
        }
    }

    @Test
    public void onViewCreated_rotationRestoresLatestItems() throws Exception {
        List<ReleaseRadarFragment.RadarItem> fresh =
                Arrays.asList(item("a", 1L), item("b", 2L));
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.just(fresh))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachResumed(f);
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 2;
                } catch (Exception e) {
                    return false;
                }
            });
            remove(f, activity);
            // 同一实例重挂：走 mLoadComplete 分支直接渲染旧数据，不再拉取。
            activity.getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, f, "test").commitNow();
            idleMain();
            assertEquals(2, adapterCount(f));
            mocked.verify(() -> RadarGraphQL.fetch("alice"), Mockito.times(1));
        }
    }

    @Test
    public void onViewCreated_rotationAfterFailure_retries() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.error(new RuntimeException("boom")))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachResumed(f);
            waitUntil(() -> {
                try {
                    return (Boolean) field(f, "mLoadFailed");
                } catch (Exception e) {
                    return false;
                }
            });
            remove(f, activity);
            // 失败后旋转：mLoadFailed 为真，不走缓存渲染分支，重新拉取。
            activity.getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, f, "test").commitNow();
            idleMain();
            mocked.verify(() -> RadarGraphQL.fetch("alice"), Mockito.times(2));
        }
    }

    @Test
    public void onRefresh_reloadsDirect() throws Exception {
        List<ReleaseRadarFragment.RadarItem> fresh =
                Arrays.asList(item("a", 1L), item("b", 2L));
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.just(fresh))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 2;
                } catch (Exception e) {
                    return false;
                }
            });
            f.onRefresh();
            waitUntil(() -> {
                try {
                    return (Boolean) field(f, "mLoadComplete");
                } catch (Exception e) {
                    return false;
                }
            });
            mocked.verify(() -> RadarGraphQL.fetch("alice"), Mockito.times(2));
            assertEquals(2, adapterCount(f));
        }
    }

    @Test
    public void onRefresh_whileShowingCache_keepsCacheVisible() throws Exception {
        RadarCache.save(RuntimeEnvironment.getApplication(), "alice",
                Arrays.asList(item("cached", 9L)));
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachNoIdle(f);
            assertEquals(1, adapterCount(f));
            assertTrue((Boolean) field(f, "mShowingCache"));
            f.onRefresh();
            // 正在展示缓存：不清空 adapter，缓存仍可见。
            assertEquals(1, adapterCount(f));
            assertTrue((Boolean) field(f, "mShowingCache"));
            mocked.verify(() -> RadarGraphQL.fetch("alice"), Mockito.times(2));
        }
    }

    @Test
    public void onDestroyView_disposesDirectLoad() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachNoIdle(f);
            assertNotNull(field(f, "mDirectLoad"));
            remove(f, activity);
            assertNull(field(f, "mDirectLoad"));
        }
    }

    @Test
    public void onDestroyView_nullDirectLoad_skipsDispose() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachNoIdle(f);
            setField(f, "mDirectLoad", null);
            remove(f, activity);
            assertNull(field(f, "mDirectLoad"));
        }
    }

    @Test
    public void onFreshData_empty_showsDiagnostic() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.just(new ArrayList<>()))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return (Boolean) field(f, "mLoadComplete");
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(0, adapterCount(f));
            assertEquals(f.getString(R.string.no_release_radar_items),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertEquals(f.getString(R.string.no_release_radar_items), emptyViewText(f));
        }
    }

    @Test
    public void renderError_noCache_showsError() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.error(new RuntimeException("boom")))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return (Boolean) field(f, "mLoadFailed");
                } catch (Exception e) {
                    return false;
                }
            });
            String expected = f.getString(R.string.radar_stage_load_failed,
                    "RuntimeException: boom");
            assertEquals(expected, emptyViewText(f));
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("boom"));
        }
    }

    @Test
    public void renderError_noMessage_usesClassNameOnly() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.error(new RuntimeException()))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return (Boolean) field(f, "mLoadFailed");
                } catch (Exception e) {
                    return false;
                }
            });
            String expected = f.getString(R.string.radar_stage_load_failed,
                    "RuntimeException");
            assertEquals(expected, emptyViewText(f));
        }
    }

    @Test
    public void renderError_withCache_showsQuietToast() throws Exception {
        RadarCache.save(RuntimeEnvironment.getApplication(), "alice",
                Arrays.asList(item("cached", 9L)));
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.error(new RuntimeException("boom")))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachNoIdle(f);
            assertEquals(1, adapterCount(f));
            waitUntil(() -> {
                try {
                    return (Boolean) field(f, "mLoadFailed");
                } catch (Exception e) {
                    return false;
                }
            });
            // 缓存已在屏：刷新失败只静默提示，不清列表。
            assertEquals(1, adapterCount(f));
            assertFalse((Boolean) field(f, "mShowingCache"));
            assertEquals(f.getString(R.string.radar_stage_refresh_failed_cache),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void setStage_afterDetach_skips() {
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachNoIdle(f);
            // stage runnable 还在主线程队列里：先分离，再放行，
            // runnable 里 isAdded() 为假，直接返回。
            activity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            idleMain();
            assertNull(f.getActivity());
        }
    }

    @Test
    public void onItemClick_startsReleaseInfo() throws Exception {
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.just(Arrays.asList(item("a", 42L))))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            RadarHostActivity activity = attachResumed(f);
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 1;
                } catch (Exception e) {
                    return false;
                }
            });
            androidx.recyclerview.widget.RecyclerView list =
                    (androidx.recyclerview.widget.RecyclerView) field(f, "mRecyclerView");
            idleMain();
            list.getChildAt(0).performClick();
            idleMain();
            android.content.Intent started =
                    Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(com.gh4a.activities.ReleaseInfoActivity.class.getName(),
                    started.getComponent().getClassName());
            assertEquals("octo", started.getStringExtra("owner"));
            assertEquals("a", started.getStringExtra("repo"));
            assertEquals(42L, started.getLongExtra("id", -1));
        }
    }

    @Test
    public void onCreateDataSingle_returnsNever() {
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        f.onCreateDataSingle(false).test()
                .assertNotTerminated()
                .assertNoValues();
    }

    // ---- A 组补缺（2026-11-22）：ReleaseRadarFragment 剩余分支 ----

    private static Object call(ReleaseRadarFragment f, String name,
            Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m =
                ReleaseRadarFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(f, args);
    }

    private static RadarHostActivity attachCreated(ReleaseRadarFragment f) {
        RadarHostActivity activity =
                Robolectric.buildActivity(RadarHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return activity;
    }

    private static void resume(ReleaseRadarFragment f, RadarHostActivity activity) {
        activity.getSupportFragmentManager().beginTransaction()
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        idleMain();
    }

    private static MockedStatic<RadarGraphQL> mockFetchFor(String login,
            Single<List<ReleaseRadarFragment.RadarItem>> single) {
        MockedStatic<RadarGraphQL> mocked = Mockito.mockStatic(RadarGraphQL.class);
        mocked.when(() -> RadarGraphQL.fetch(login)).thenReturn(single);
        return mocked;
    }

    @Test
    public void onViewCreated_completeButFailed_reloadsDirect() throws Exception {
        // L100：mLoadComplete 为真但 mLoadFailed 为真时不走缓存渲染，重拉。
        try (MockedStatic<RadarGraphQL> mocked = mockFetchFor("bob", Single.never());
             MockedStatic<RadarCache> cacheMock =
                     Mockito.mockStatic(RadarCache.class)) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("bob");
            RadarHostActivity activity = attachCreated(f);
            setField(f, "mLoadComplete", true);
            setField(f, "mLoadFailed", true);
            setField(f, "mLatestItems", Arrays.asList(item("x", 1L)));
            resume(f, activity);
            mocked.verify(() -> RadarGraphQL.fetch("bob"), Mockito.times(1));
            assertFalse((Boolean) field(f, "mLoadComplete"));
            assertTrue((Boolean) field(f, "mLoadFailed"));
        }
    }

    @Test
    public void onViewCreated_completeNoItems_reloadsDirect() throws Exception {
        // L100：mLoadComplete 为真但 mLatestItems 为空时不走缓存渲染，重拉。
        try (MockedStatic<RadarGraphQL> mocked = mockFetchFor("bob", Single.never());
             MockedStatic<RadarCache> cacheMock =
                     Mockito.mockStatic(RadarCache.class)) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("bob");
            RadarHostActivity activity = attachCreated(f);
            setField(f, "mLoadComplete", true);
            setField(f, "mLoadFailed", false);
            setField(f, "mLatestItems", null);
            resume(f, activity);
            mocked.verify(() -> RadarGraphQL.fetch("bob"), Mockito.times(1));
            assertFalse((Boolean) field(f, "mLoadComplete"));
            assertEquals(null, field(f, "mLatestItems"));
        }
    }

    @Test
    public void startDirectLoad_nullAdapter_skipsClear() throws Exception {
        // L161：mAdapter 为空时不调 clear（直接调私有方法；CREATED 态尚无 view，
        // setContentShown/updateEmptyState 均有判空，不崩溃）。
        // 附带：setStage 排队的 runnable 在 getView() 为空时返回（L181）。
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attach(f);
            setField(f, "mShowingCache", false);
            call(f, "startDirectLoad", new Class<?>[]{boolean.class}, true);
            idleMain();
            assertNotNull(field(f, "mDirectLoad"));
            assertFalse((Boolean) field(f, "mLoadComplete"));
            assertEquals(null, field(f, "mLatestItems"));
        }
    }

    @Test
    public void setStage_rootNull_returns() throws Exception {
        // L181：stage runnable 运行时 getView() 为空则直接返回。
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attach(f);
            call(f, "setStage", new Class<?>[]{String.class}, "stage");
            idleMain();
            assertTrue(f.isAdded());
            assertEquals(null, f.getView());
        }
    }

    @Test
    public void renderItems_detached_returns() throws Exception {
        // L191/L192：未挂载时 renderItems 直接返回，不写 mLatestItems。
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        call(f, "renderItems",
                new Class<?>[]{java.util.List.class},
                Arrays.asList(item("a", 1L)));
        assertEquals(null, field(f, "mLatestItems"));
    }

    @Test
    public void renderItems_nullAdapter_returns() throws Exception {
        // L191/L192：已挂载但 mAdapter 为空（CREATED 态）时直接返回。
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attach(f);
            assertEquals(null, field(f, "mAdapter"));
            call(f, "renderItems",
                    new Class<?>[]{java.util.List.class},
                    Arrays.asList(item("a", 1L)));
            assertEquals(null, field(f, "mLatestItems"));
        }
    }

    @Test
    public void onFreshData_detached_returns() throws Exception {
        // L203/L204：未挂载时 onFreshData 直接返回，不置 mLoadComplete。
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        call(f, "onFreshData",
                new Class<?>[]{java.util.List.class},
                Arrays.asList(item("a", 1L)));
        assertFalse((Boolean) field(f, "mLoadComplete"));
    }

    @Test
    public void onLoadComplete_directCall_isNoop() throws Exception {
        // L220：onLoadComplete 是保留的空方法（Single 路径已不用），直接调用无操作。
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        call(f, "onLoadComplete", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mLoadComplete"));
    }

    @Test
    public void showDiagnostic_withItems_returns() throws Exception {
        // L223/L224：adapter 已有数据时不弹诊断 toast。
        try (MockedStatic<RadarGraphQL> mocked =
                     mockFetch(Single.just(Arrays.asList(item("a", 1L))))) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            waitUntil(() -> {
                try {
                    return adapterCount(f) == 1;
                } catch (Exception e) {
                    return false;
                }
            });
            assertEquals(null,
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            call(f, "showDiagnostic", new Class<?>[]{});
            assertEquals(null,
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void showDiagnostic_nullContext_returns() throws Exception {
        // L223/L224：adapter 为空且 getContext() 为空时直接返回（第二臂）。
        ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
        setField(f, "mAdapter", new ReleaseRadarAdapter(mActivity));
        call(f, "showDiagnostic", new Class<?>[]{});
        assertEquals(null,
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void renderError_nullAdapter_showsError() throws Exception {
        // L237：mAdapter 为空时走错误分支（不走静默缓存分支），照常弹错。
        try (MockedStatic<RadarGraphQL> mocked = mockFetch(Single.never())) {
            ReleaseRadarFragment f = ReleaseRadarFragment.newInstance("alice");
            attachResumed(f);
            setField(f, "mAdapter", null);
            call(f, "renderError",
                    new Class<?>[]{Throwable.class},
                    new RuntimeException("boom"));
            idleMain();
            assertTrue((Boolean) field(f, "mLoadFailed"));
            String expected = f.getString(R.string.radar_stage_load_failed,
                    "RuntimeException: boom");
            assertEquals(expected, emptyViewText(f));
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("boom"));
        }
    }
}
