package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

@RunWith(RobolectricTestRunner.class)
public class StoreRemovedActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private void seedRemoved(String json) {
        SharedPreferences prefs = context().getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("store_removed_repos", json).apply();
    }

    private void clearRemoved() {
        seedRemoved("[]");
    }

    /** 取 activity 根 ScrollView 下 LinearLayout 的全部文本。 */
    private String allText(StoreRemovedActivity activity) {
        ViewGroup root = (ViewGroup) activity.findViewById(android.R.id.content);
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        return sb.toString();
    }

    private void collectText(View view, StringBuilder sb) {
        if (view instanceof TextView) {
            sb.append(((TextView) view).getText()).append('\n');
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), sb);
            }
        }
    }

    @Test
    public void makeIntent_targetsActivity() {
        Intent intent = StoreRemovedActivity.makeIntent(context());
        assertEquals(StoreRemovedActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void launches_titleIsStoreRemoved() {
        clearRemoved();
        try (ActivityController<StoreRemovedActivity> c =
                Robolectric.buildActivity(StoreRemovedActivity.class,
                        StoreRemovedActivity.makeIntent(context()))) {
            StoreRemovedActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.store_removed),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void render_emptyShowsEmptyText() {
        clearRemoved();
        try (ActivityController<StoreRemovedActivity> c =
                Robolectric.buildActivity(StoreRemovedActivity.class,
                        StoreRemovedActivity.makeIntent(context()))) {
            StoreRemovedActivity activity = c.setup().get();
            String text = allText(activity);
            assertTrue(text.contains(activity.getString(R.string.store_removed_empty)));
            assertTrue(text.contains(activity.getString(R.string.store_removed_note)));
        }
    }

    @Test
    public void render_withEntriesShowsNameReasonDate() {
        seedRemoved("[{\"name\":\"octocat/gone\",\"reason\":\"deleted\",\"date\":\"2026-11-01\"},"
                + "{\"name\":\"foo/bar\",\"reason\":\"private\",\"date\":\"2026-11-02\"}]");
        try (ActivityController<StoreRemovedActivity> c =
                Robolectric.buildActivity(StoreRemovedActivity.class,
                        StoreRemovedActivity.makeIntent(context()))) {
            StoreRemovedActivity activity = c.setup().get();
            String text = allText(activity);
            assertTrue(text.contains("octocat/gone"));
            assertTrue(text.contains("deleted"));
            assertTrue(text.contains("2026-11-01"));
            assertTrue(text.contains("foo/bar"));
            assertTrue(text.contains("private"));
            assertTrue(text.contains("2026-11-02"));
        }
    }

    @Test
    public void render_malformedEntrySkippedButOthersShown() {
        // 非对象元素被 getRemoved 跳过，不影响其他条目。
        seedRemoved("[\"not-an-object\","
                + "{\"name\":\"octocat/kept\",\"reason\":\"r\",\"date\":\"2026-11-03\"}]");
        try (ActivityController<StoreRemovedActivity> c =
                Robolectric.buildActivity(StoreRemovedActivity.class,
                        StoreRemovedActivity.makeIntent(context()))) {
            StoreRemovedActivity activity = c.setup().get();
            assertTrue(allText(activity).contains("octocat/kept"));
        }
    }

    @Test
    public void onRefresh_rerendersWithoutCrash() {
        seedRemoved("[{\"name\":\"octocat/gone\",\"reason\":\"deleted\",\"date\":\"2026-11-01\"}]");
        try (ActivityController<StoreRemovedActivity> c =
                Robolectric.buildActivity(StoreRemovedActivity.class,
                        StoreRemovedActivity.makeIntent(context()))) {
            StoreRemovedActivity activity = c.setup().get();
            clearRemoved();
            activity.onRefresh();
            assertTrue(allText(activity)
                    .contains(activity.getString(R.string.store_removed_empty)));
            assertNotNull(activity);
        }
    }
}
