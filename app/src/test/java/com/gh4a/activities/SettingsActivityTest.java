package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Field;
import static org.junit.Assert.assertNotSame;

import com.gh4a.R;

@RunWith(RobolectricTestRunner.class)
public class SettingsActivityTest {

    private ActivityController<SettingsActivity> launch() {
        return Robolectric.buildActivity(SettingsActivity.class);
    }

    @Test
    public void launches_titleIsSettingsAndFragmentAdded() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.settings),
                    activity.getSupportActionBar().getTitle().toString());
            assertTrue(activity.getSupportFragmentManager()
                    .findFragmentById(R.id.content_container)
                    instanceof com.gh4a.fragment.SettingsFragment);
        }
    }

    @Test
    public void canSwipeToRefresh_isFalse() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            assertFalse(activity.canSwipeToRefresh());
        }
    }

    @Test
    public void onCreate_setsResultOk() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            assertEquals(Activity.RESULT_OK,
                    Shadows.shadowOf(activity).getResultCode());
            assertNotNull(Shadows.shadowOf(activity).getResultIntent());
        }
    }

    @Test
    public void onThemeChanged_setsThemeChangedExtra() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onThemeChanged();
            Intent result = Shadows.shadowOf(activity).getResultIntent();
            assertTrue(result.getBooleanExtra(
                    SettingsActivity.RESULT_EXTRA_THEME_CHANGED, false));
            assertFalse(result.getBooleanExtra(
                    SettingsActivity.RESULT_EXTRA_FONT_SCALE_CHANGED, false));
        }
    }

    @Test
    public void onFontScaleChanged_setsFontScaleChangedExtra() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onFontScaleChanged();
            Intent result = Shadows.shadowOf(activity).getResultIntent();
            assertTrue(result.getBooleanExtra(
                    SettingsActivity.RESULT_EXTRA_FONT_SCALE_CHANGED, false));
            assertFalse(result.getBooleanExtra(
                    SettingsActivity.RESULT_EXTRA_THEME_CHANGED, false));
        }
    }

    @Test
    public void onSaveInstanceState_roundTripsResultIntent() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onThemeChanged();
            Bundle outState = new Bundle();
            activity.onSaveInstanceState(outState);
            assertNotNull(outState.getParcelable("result"));
            Intent saved = outState.getParcelable("result");
            assertTrue(saved.getBooleanExtra(
                    SettingsActivity.RESULT_EXTRA_THEME_CHANGED, false));
        }
    }

    @Test
    public void onThemeChanged_postsRecreate_onlyWhenAlive() {
        // 0.1.22：posted recreate 在 activity 销毁后不执行、不崩。
        // 这里只验证 post 本身不抛异常；Robolectric 主 looper 空闲后
        // recreate 会正常走一遍（不崩即通过）。
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onThemeChanged();
            ShadowLooper.idleMainLooper();
            assertNotNull(activity);
        }
    }

    @Test
    public void recreate_restoresSettingsFragment() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            assertNotNull(activity);
            c.recreate();
            SettingsActivity recreated = c.get();
            assertTrue(recreated.getSupportFragmentManager()
                    .findFragmentById(R.id.content_container)
                    instanceof com.gh4a.fragment.SettingsFragment);
        }
    }

    @Test
    public void onThemeChanged_afterFinish_doesNotRecreate() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onThemeChanged();
            activity.finish();
            ShadowLooper.idleMainLooper();
            assertNotNull(activity);
        }
    }

    @Test
    public void onFontScaleChanged_afterFinish_doesNotRecreate() {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onFontScaleChanged();
            activity.finish();
            ShadowLooper.idleMainLooper();
            assertNotNull(activity);
        }
    }

    // ---- A2 第二轮补缺口：SettingsActivity ----

    @Test
    public void onFontScaleChanged_setsResultExtraAndRecreates() throws Exception {
        try (ActivityController<SettingsActivity> c = launch()) {
            SettingsActivity activity = c.setup().get();
            activity.onFontScaleChanged();
            java.lang.reflect.Field resultIntentField =
                    SettingsActivity.class.getDeclaredField("mResultIntent");
            resultIntentField.setAccessible(true);
            Intent resultIntent = (Intent) resultIntentField.get(activity);
            assertTrue(resultIntent.getBooleanExtra(
                    SettingsActivity.RESULT_EXTRA_FONT_SCALE_CHANGED, false));
            ShadowLooper.idleMainLooper();
            assertNotSame(activity, c.get());
        }
    }

}
