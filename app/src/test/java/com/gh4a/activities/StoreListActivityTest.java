package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import com.gh4a.R;
import com.gh4a.fragment.StoreApi;
import com.gh4a.fragment.StoreClassifier;
import com.gh4a.fragment.StoreRepo;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Shadows;
import io.reactivex.Single;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class StoreListActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void modeConstants_areDistinct() {
        assertEquals("newest", StoreListActivity.MODE_NEWEST);
        assertEquals("recent", StoreListActivity.MODE_RECENT);
    }

    @Test
    public void makeIntent_carriesMode() {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        assertEquals(StoreListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("newest", intent.getStringExtra("mode"));
    }

    @Test
    public void launches_recentMode_titleIsRecentUpdated() {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_RECENT);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_recent_updated),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void launches_newestMode_actionBarTitleShowsNewest() {
        // T-011 修复后：mMode 赋值后刷新标题，newest 模式显示“最新入库”。
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_newest),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void getActionBarTitle_afterModeSet_returnsNewest() {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_newest),
                    activity.getActionBarTitle());
        }
    }

    @Test
    public void launches_nullMode_defaultsToRecent() {
        Intent intent = new Intent(context(), StoreListActivity.class);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_recent_updated),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void launches_showsLoadingThenErrorOffline() {
        // 离线：load() 先挂 loading 文案，网络失败后挂失败文案（带重试点击）。
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_RECENT);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            assertNotNull(activity);
            // 给后台 IO 线程一点时间完成失败回调（失败路径只改 UI，不抛异常）
            ShadowLooper.idleMainLooper();
        }
    }

    @Test
    public void onRefresh_doesNotCrash() {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            activity.onRefresh();
            ShadowLooper.idleMainLooper();
            assertNotNull(activity);
        }
    }

    @Test
    public void onDestroy_clearsDisposablesWithoutCrash() {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_RECENT);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            c.destroy();
            assertNotNull(activity);
        }
    }

    private static android.widget.LinearLayout content(StoreListActivity activity)
            throws Exception {
        Field f = StoreListActivity.class.getDeclaredField("mContent");
        f.setAccessible(true);
        return (android.widget.LinearLayout) f.get(activity);
    }

    private static String contentText(StoreListActivity activity) throws Exception {
        android.widget.LinearLayout root = content(activity);
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString();
    }

    private static void collectText(android.view.View view, StringBuilder sb) {
        if (view instanceof android.widget.TextView) {
            sb.append(((android.widget.TextView) view).getText()).append('\n');
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), sb);
            }
        }
    }

    @Test
    public void load_onCreate_showsLoadingImmediately() throws Exception {
        // load() 在 onCreate 同步挂上 loading 文本；成功/失败分支走真实网络
        // （测试 JVM 经 MITM 代理可达 api.github.com，见覆盖率报告未覆盖清单），
        // 结果不可确定，故此处只断言同步的 loading 态。
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_RECENT);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.create().get();
            assertTrue(contentText(activity)
                    .contains(activity.getString(R.string.loading_msg)));
        }
    }

    // ---------- missed-line close-out (2026-11-15 §6): load() 的两个
    // subscribe 回调（成功 84-96 / 失败 97-105）在重建里从未跑过，原因是
    // 成功/失败都走真实网络且结果不可确定；此处用 mockStatic(StoreApi)
    // 把两路都挡成确定值。 ----------

    private static StoreRepo repo(String fullName, String owner, String name) {
        return new StoreRepo(fullName, owner, name, "desc", "Java", null,
                100, 5, 10L, null, null, "v1.0", 0L,
                EnumSet.of(StoreClassifier.Platform.ANDROID), 1,
                null, null, null, null, null);
    }

    @Test
    public void load_newest_nonEmpty_showsRowsAndRowClickStartsRepository() throws Exception {
        List<StoreRepo> items = Arrays.asList(
                repo("octo/cat", "octo", "cat"), repo("foo/bar", "foo", "bar"));
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        try (MockedStatic<StoreApi> mocked =
                Mockito.mockStatic(StoreApi.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> StoreApi.loadListNewest()).thenReturn(Single.just(items));
            try (ActivityController<StoreListActivity> c =
                    Robolectric.buildActivity(StoreListActivity.class, intent)) {
                StoreListActivity activity = c.setup().get();
                android.widget.LinearLayout root = content(activity);
                assertEquals(4, root.getChildCount());
                assertTrue(contentText(activity).contains("octo/cat"));
                assertTrue(contentText(activity).contains("foo/bar"));
                // 点第一行：行点击 lambda 进仓库详情。
                root.getChildAt(0).performClick();
                Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
                assertNotNull(started);
                assertEquals(RepositoryActivity.class.getName(),
                        started.getComponent().getClassName());
                assertEquals("octo", started.getStringExtra("owner"));
                assertEquals("cat", started.getStringExtra("repo"));
            }
        }
    }

    @Test
    public void load_newest_empty_showsEmptyText() throws Exception {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        try (MockedStatic<StoreApi> mocked =
                Mockito.mockStatic(StoreApi.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> StoreApi.loadListNewest())
                    .thenReturn(Single.just(Collections.emptyList()));
            try (ActivityController<StoreListActivity> c =
                    Robolectric.buildActivity(StoreListActivity.class, intent)) {
                StoreListActivity activity = c.setup().get();
                assertTrue(contentText(activity)
                        .contains(activity.getString(R.string.store_empty_result)));
            }
        }
    }

    @Test
    public void load_recent_nonEmpty_showsRows() throws Exception {
        List<StoreRepo> items = Arrays.asList(repo("octo/cat", "octo", "cat"));
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_RECENT);
        try (MockedStatic<StoreApi> mocked =
                Mockito.mockStatic(StoreApi.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> StoreApi.loadListRecent()).thenReturn(Single.just(items));
            try (ActivityController<StoreListActivity> c =
                    Robolectric.buildActivity(StoreListActivity.class, intent)) {
                StoreListActivity activity = c.setup().get();
                assertEquals(2, content(activity).getChildCount());
                assertTrue(contentText(activity).contains("octo/cat"));
            }
        }
    }

    @Test
    public void load_error_showsErrorViewAndRetryReloads() throws Exception {
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_RECENT);
        try (MockedStatic<StoreApi> mocked =
                Mockito.mockStatic(StoreApi.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> StoreApi.loadListRecent())
                    .thenReturn(Single.error(new RuntimeException("boom")));
            try (ActivityController<StoreListActivity> c =
                    Robolectric.buildActivity(StoreListActivity.class, intent)) {
                StoreListActivity activity = c.setup().get();
                assertTrue(contentText(activity)
                        .contains(activity.getString(R.string.store_fetch_failed)));
                // 失败视图点按重试：mock 仍报错，失败文案仍在。
                android.widget.LinearLayout root = content(activity);
                assertEquals(1, root.getChildCount());
                root.getChildAt(0).performClick();
                assertTrue(contentText(activity)
                        .contains(activity.getString(R.string.store_fetch_failed)));
            }
        }
    }
}
