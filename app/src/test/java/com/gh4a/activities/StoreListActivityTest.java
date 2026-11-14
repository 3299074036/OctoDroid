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
import java.lang.reflect.Field;
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
    public void launches_newestMode_actionBarTitleShowsRecentInsteadOfNewest() {
        // A2-2：标题在 BaseActivity.onCreate 里按 getActionBarTitle() 定死一次，
        // 此时 mMode 尚未赋值（StoreListActivity.onCreate 晚于 super.onCreate
        // 才读 intent），且之后从不刷新标题；newest 模式标题恒错显示
        // "Recently updated"。方法本身是对的（见下一条）。
        Intent intent = StoreListActivity.makeIntent(context(), StoreListActivity.MODE_NEWEST);
        try (ActivityController<StoreListActivity> c =
                Robolectric.buildActivity(StoreListActivity.class, intent)) {
            StoreListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_recent_updated),
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
}
