package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.gh4a.Gh4Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * A 组：DarkModeScheduler 读 SharedPreferences、调 AppCompatDelegate，
 * 走 Robolectric。T-127：定时模式已删除，只剩 manual/system 与
 * scheduled→system 迁移（BUG-20）。
 */
@RunWith(RobolectricTestRunner.class)
public class DarkModeSchedulerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE)
                .edit().clear().apply();
        // 真实 Application（Gh4Application）监听 dark_mode 键：写
        // "scheduled" 会立刻经 updateTheme → DarkModeScheduler.apply
        // 迁移成 "system"（BUG-20）。单测 DarkModeScheduler 本身时先
        // 摘掉该监听，否则 "scheduled" 永远存不住；每个测试方法都是
        // 新 sandbox，下一个测试的 Application.onCreate 会重新注册。
        Gh4Application app = Gh4Application.get();
        if (app != null) {
            context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(app);
        }
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE);
    }

    @Test
    public void getMode_defaultIsManual() {
        assertEquals(DarkModeScheduler.MODE_MANUAL, DarkModeScheduler.getMode(context));
    }

    @Test
    public void migrateScheduledToSystem_nullContextIsNoop() {
        DarkModeScheduler.migrateScheduledToSystem(null);
    }

    @Test
    public void migrateScheduledToSystem_manualModeIsNoop() {
        DarkModeScheduler.migrateScheduledToSystem(context);
        assertEquals(DarkModeScheduler.MODE_MANUAL, DarkModeScheduler.getMode(context));
    }

    @Test
    public void migrateScheduledToSystem_scheduledBecomesSystem() {
        prefs().edit().putString(DarkModeScheduler.KEY_DARK_MODE,
                DarkModeScheduler.MODE_SCHEDULED).apply();
        DarkModeScheduler.migrateScheduledToSystem(context);
        assertEquals(DarkModeScheduler.MODE_SYSTEM, DarkModeScheduler.getMode(context));
    }

    @Test
    public void migrateScheduledToSystem_isIdempotent() {
        prefs().edit().putString(DarkModeScheduler.KEY_DARK_MODE,
                DarkModeScheduler.MODE_SCHEDULED).apply();
        DarkModeScheduler.migrateScheduledToSystem(context);
        DarkModeScheduler.migrateScheduledToSystem(context);
        assertEquals(DarkModeScheduler.MODE_SYSTEM, DarkModeScheduler.getMode(context));
    }

    @Test
    public void apply_systemModeSetsFollowSystem() {
        DarkModeScheduler.apply(context, DarkModeScheduler.MODE_SYSTEM);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void apply_scheduledModeMigratesToSystem() {
        // BUG-20：显式 scheduled 一律迁移成 system，不再进入定时分支
        DarkModeScheduler.apply(context, DarkModeScheduler.MODE_SCHEDULED);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void apply_unknownModeDoesNotCrashAndKeepsManual() {
        DarkModeScheduler.apply(context, "bogus-mode");
        assertEquals(DarkModeScheduler.MODE_MANUAL, DarkModeScheduler.getMode(context));
    }

    @Test
    public void apply_readsStoredMode() {
        prefs().edit().putString(DarkModeScheduler.KEY_DARK_MODE,
                DarkModeScheduler.MODE_SYSTEM).apply();
        DarkModeScheduler.apply(context);
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void apply_storedScheduledModeMigratesOnRead() {
        prefs().edit().putString(DarkModeScheduler.KEY_DARK_MODE,
                DarkModeScheduler.MODE_SCHEDULED).apply();
        DarkModeScheduler.apply(context);
        assertEquals(DarkModeScheduler.MODE_SYSTEM, DarkModeScheduler.getMode(context));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void defaultConstructor_instantiates() {
        assertNotNull(new DarkModeScheduler());
    }
}
