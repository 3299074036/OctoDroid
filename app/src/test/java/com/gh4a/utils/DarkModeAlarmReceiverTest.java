package com.gh4a.utils;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

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
import static org.junit.Assert.assertTrue;

/**
 * A 组：DarkModeAlarmReceiver 是 BroadcastReceiver，走 Robolectric。
 * 校验三条分支：BOOT_COMPLETED、非法 action 忽略、无 action 触发 apply。
 */
@RunWith(RobolectricTestRunner.class)
public class DarkModeAlarmReceiverTest {

    private Context context;
    private ShadowAlarmManager shadowAlarmManager;
    private final DarkModeAlarmReceiver receiver = new DarkModeAlarmReceiver();

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE)
                .edit().clear().apply();
        // 同 DarkModeSchedulerTest：真实 Application 监听 dark_mode 键，
        // 写 "scheduled" 会立刻被迁移成 "system"（BUG-20）；测 receiver
        // 自己的分支时先摘掉该监听。
        Gh4Application app = Gh4Application.get();
        if (app != null) {
            context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(app);
        }
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        shadowAlarmManager = Shadows.shadowOf(am);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void setMode(String mode) {
        context.getSharedPreferences("Gh4a-pref", Context.MODE_PRIVATE)
                .edit().putString(DarkModeScheduler.KEY_DARK_MODE, mode).apply();
    }

    @Test
    public void onReceive_bootCompletedWithScheduledMode_schedulesAlarms() {
        setMode(DarkModeScheduler.MODE_SCHEDULED);
        Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED);
        receiver.onReceive(context, intent);
        assertEquals(2, shadowAlarmManager.getScheduledAlarms().size());
    }

    @Test
    public void onReceive_bootCompletedWithManualMode_schedulesNothing() {
        setMode(DarkModeScheduler.MODE_MANUAL);
        receiver.onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }

    @Test
    public void onReceive_bootCompletedWithSystemMode_schedulesNothing() {
        setMode(DarkModeScheduler.MODE_SYSTEM);
        receiver.onReceive(context, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(0, shadowAlarmManager.getScheduledAlarms().size());
    }

    @Test
    public void onReceive_nullAction_appliesStoredMode() {
        setMode(DarkModeScheduler.MODE_SYSTEM);
        receiver.onReceive(context, new Intent());
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void onReceive_nullActionWithScheduledMode_migratesToSystem() {
        setMode(DarkModeScheduler.MODE_SCHEDULED);
        receiver.onReceive(context, new Intent());
        assertEquals(DarkModeScheduler.MODE_SYSTEM, DarkModeScheduler.getMode(context));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void onReceive_unknownNonNullAction_isIgnored() {
        // SEC-10：非 BOOT、非空 action 一律忽略，不重新应用主题
        setMode(DarkModeScheduler.MODE_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        Intent intent = new Intent("com.example.MALICIOUS_REAPPLY");
        receiver.onReceive(context, intent);
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.getDefaultNightMode());
        assertTrue(shadowAlarmManager.getScheduledAlarms().isEmpty());
    }

    @Test
    public void onReceive_unknownAction_doesNotMigrateScheduledMode() {
        setMode(DarkModeScheduler.MODE_SCHEDULED);
        receiver.onReceive(context, new Intent("com.example.SOMETHING"));
        assertEquals(DarkModeScheduler.MODE_SCHEDULED, DarkModeScheduler.getMode(context));
    }
}
