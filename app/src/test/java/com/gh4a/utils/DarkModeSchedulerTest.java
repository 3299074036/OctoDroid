package com.gh4a.utils;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.gh4a.Gh4Application;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A 组：DarkModeScheduler 读 SharedPreferences、调 AlarmManager/AppCompatDelegate，
 * 走 Robolectric。isInDarkPeriod 与 formatMinutes 是包内/纯函数，同包可直接测。
 */
@RunWith(RobolectricTestRunner.class)
public class DarkModeSchedulerTest {

    private Context context;
    private ShadowAlarmManager shadowAlarmManager;

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
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        shadowAlarmManager = Shadows.shadowOf(am);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE);
    }

    @Test
    public void isInDarkPeriod_sameStartEndIsNeverDark() {
        assertFalse(DarkModeScheduler.isInDarkPeriod(600, 600, 600));
        assertFalse(DarkModeScheduler.isInDarkPeriod(0, 600, 600));
    }

    @Test
    public void isInDarkPeriod_daytimeRangeBoundaries() {
        // 09:00–17:00：左闭右开
        assertFalse(DarkModeScheduler.isInDarkPeriod(8 * 60 + 59, 540, 1020));
        assertTrue(DarkModeScheduler.isInDarkPeriod(9 * 60, 540, 1020));
        assertTrue(DarkModeScheduler.isInDarkPeriod(16 * 60 + 59, 540, 1020));
        assertFalse(DarkModeScheduler.isInDarkPeriod(17 * 60, 540, 1020));
        assertFalse(DarkModeScheduler.isInDarkPeriod(0, 540, 1020));
    }

    @Test
    public void isInDarkPeriod_overnightRangeWrapsMidnight() {
        // 22:00–07:00：跨午夜
        assertFalse(DarkModeScheduler.isInDarkPeriod(21 * 60 + 59, 1320, 420));
        assertTrue(DarkModeScheduler.isInDarkPeriod(22 * 60, 1320, 420));
        assertTrue(DarkModeScheduler.isInDarkPeriod(23 * 60 + 30, 1320, 420));
        assertTrue(DarkModeScheduler.isInDarkPeriod(0, 1320, 420));
        assertTrue(DarkModeScheduler.isInDarkPeriod(6 * 60 + 59, 1320, 420));
        assertFalse(DarkModeScheduler.isInDarkPeriod(7 * 60, 1320, 420));
        assertFalse(DarkModeScheduler.isInDarkPeriod(12 * 60, 1320, 420));
    }

    @Test
    public void formatMinutes_zeroOneAndBoundaries() {
        assertEquals("00:00", DarkModeScheduler.formatMinutes(0));
        assertEquals("01:00", DarkModeScheduler.formatMinutes(60));
        assertEquals("07:05", DarkModeScheduler.formatMinutes(7 * 60 + 5));
        assertEquals("22:00", DarkModeScheduler.formatMinutes(1320));
        assertEquals("23:59", DarkModeScheduler.formatMinutes(1439));
    }

    @Test
    public void getMode_defaultIsManual() {
        assertEquals(DarkModeScheduler.MODE_MANUAL, DarkModeScheduler.getMode(context));
    }

    @Test
    public void getStartEndMinutes_defaultsAre22And7() {
        assertEquals(22 * 60, DarkModeScheduler.getStartMinutes(context));
        assertEquals(7 * 60, DarkModeScheduler.getEndMinutes(context));
    }

    @Test
    public void setSchedule_persistsStartAndEnd() {
        DarkModeScheduler.setSchedule(context, 21 * 60, 6 * 60);
        assertEquals(21 * 60, DarkModeScheduler.getStartMinutes(context));
        assertEquals(6 * 60, DarkModeScheduler.getEndMinutes(context));
    }

    @Test
    public void isScheduled_falseByDefaultTrueWhenStored() {
        assertFalse(DarkModeScheduler.isScheduled(context));
        prefs().edit().putString(DarkModeScheduler.KEY_DARK_MODE,
                DarkModeScheduler.MODE_SCHEDULED).apply();
        assertTrue(DarkModeScheduler.isScheduled(context));
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
        assertFalse(DarkModeScheduler.isScheduled(context));
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
    public void apply_unknownModeCancelsAlarmsWithoutCrashing() {
        DarkModeScheduler.scheduleAlarms(context);
        assertEquals(2, shadowAlarmManager.getScheduledAlarms().size());
        DarkModeScheduler.apply(context, "bogus-mode");
        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
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
    public void scheduleAndCancelAlarms_twoAlarmsThenZero() {
        DarkModeScheduler.scheduleAlarms(context);
        assertEquals(2, shadowAlarmManager.getScheduledAlarms().size());
        DarkModeScheduler.cancelAlarms(context);
        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }

    @Test
    public void cancelAlarms_withNoAlarmsIsNoop() {
        DarkModeScheduler.cancelAlarms(context);
        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }
}
