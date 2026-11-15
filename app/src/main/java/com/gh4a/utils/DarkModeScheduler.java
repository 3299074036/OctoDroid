package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.gh4a.fragment.SettingsFragment;

/**
 * Drives the "dark_mode" setting (manual / follow system).
 *
 * Scheduled mode was removed (T-127): the scheduler case, the schedule
 * dialog, the alarm receiver and the "dark period" preference are gone.
 * A stored "scheduled" value is migrated once to "system" (BUG-20);
 * see migrateScheduledToSystem().
 */
public class DarkModeScheduler {
    public static final String KEY_DARK_MODE = "dark_mode";

    public static final String MODE_MANUAL = "manual";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_SCHEDULED = "scheduled";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getMode(Context context) {
        return prefs(context).getString(KEY_DARK_MODE, MODE_MANUAL);
    }

    /**
     * BUG-20 (migration side, chosen over keeping scheduled behaviour):
     * the UI has no schedule entry any more, so a stored "scheduled"
     * mode is rewritten once to "system". Idempotent; a no-op unless the
     * stored mode is scheduled. Called from apply() (so process startup
     * via Gh4Application migrates) and from MeFragment's first read.
     * T-127: no alarms exist any more (receiver deleted), so there is
     * nothing to cancel here; a stale one-shot alarm from an old install
     * fires at most once and fails to resolve, which is harmless.
     */
    public static void migrateScheduledToSystem(Context context) {
        if (context == null) {
            return;
        }
        if (MODE_SCHEDULED.equals(prefs(context).getString(KEY_DARK_MODE, MODE_MANUAL))) {
            prefs(context).edit().putString(KEY_DARK_MODE, MODE_SYSTEM).apply();
        }
    }

    /** Applies the effective night mode; call on process start and on setting changes. */
    public static void apply(Context context) {
        apply(context, getMode(context));
    }

    /**
     * Applies the given mode directly. Use from preference change listeners,
     * where the new value is not yet persisted when the callback runs.
     */
    public static void apply(Context context, String mode) {
        // 0.1.36：诊断追踪。
        ThemeTrace.logState(context, "SCHEDULER_APPLY in mode=" + mode);
        // BUG-20: never (re-)enter scheduled mode — an explicit scheduled
        // value (e.g. restored from a backup, or the old schedule dialog)
        // is migrated to system before the switch below runs.
        if (MODE_SCHEDULED.equals(mode)) {
            migrateScheduledToSystem(context);
            mode = MODE_SYSTEM;
        }
        switch (mode) {
            case MODE_SYSTEM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                ThemeTrace.logState(context, "SCHEDULER_APPLY out system");
                break;
            default:
                // manual: Gh4Application.updateTheme() handles KEY_THEME
                ThemeTrace.logState(context, "SCHEDULER_APPLY out manual");
                break;
        }
    }
}
