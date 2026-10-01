package com.gh4a.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.gh4a.fragment.SettingsFragment;

import java.util.Calendar;

/**
 * Drives the "dark_mode" setting (manual / follow system / scheduled).
 *
 * In scheduled mode the app switches to dark during the user-configured
 * period (default 22:00–07:00). Exact alarms fire at the period boundaries;
 * applying is also re-evaluated on every process start, so a missed alarm
 * self-corrects the next time the app opens.
 */
public class DarkModeScheduler {
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_DARK_START = "dark_mode_start";
    public static final String KEY_DARK_END = "dark_mode_end";

    public static final String MODE_MANUAL = "manual";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_SCHEDULED = "scheduled";

    private static final int DEFAULT_START_MINUTES = 22 * 60;
    private static final int DEFAULT_END_MINUTES = 7 * 60;

    private static final int REQ_START = 1001;
    private static final int REQ_END = 1002;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getMode(Context context) {
        return prefs(context).getString(KEY_DARK_MODE, MODE_MANUAL);
    }

    public static boolean isScheduled(Context context) {
        return MODE_SCHEDULED.equals(getMode(context));
    }

    public static int getStartMinutes(Context context) {
        return prefs(context).getInt(KEY_DARK_START, DEFAULT_START_MINUTES);
    }

    public static int getEndMinutes(Context context) {
        return prefs(context).getInt(KEY_DARK_END, DEFAULT_END_MINUTES);
    }

    public static void setSchedule(Context context, int startMinutes, int endMinutes) {
        prefs(context).edit()
                .putInt(KEY_DARK_START, startMinutes)
                .putInt(KEY_DARK_END, endMinutes)
                .apply();
        apply(context);
    }

    /** Applies the effective night mode; call on process start and on setting changes. */
    public static void apply(Context context) {
        String mode = getMode(context);
        switch (mode) {
            case MODE_SYSTEM:
                cancelAlarms(context);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case MODE_SCHEDULED: {
                int now = minutesSinceMidnight();
                boolean dark = isInDarkPeriod(now, getStartMinutes(context), getEndMinutes(context));
                AppCompatDelegate.setDefaultNightMode(dark
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
                scheduleAlarms(context);
                break;
            }
            default:
                // manual: Gh4Application.updateTheme() handles KEY_THEME
                cancelAlarms(context);
                break;
        }
    }

    static boolean isInDarkPeriod(int nowMinutes, int startMinutes, int endMinutes) {
        if (startMinutes == endMinutes) {
            return false;
        }
        if (startMinutes < endMinutes) {
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        }
        return nowMinutes >= startMinutes || nowMinutes < endMinutes;
    }

    private static int minutesSinceMidnight() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private static long nextTriggerMillis(int targetMinutes) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, targetMinutes / 60);
        cal.set(Calendar.MINUTE, targetMinutes % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private static PendingIntent alarmIntent(Context context, int requestCode) {
        Intent intent = new Intent(context, DarkModeAlarmReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    public static void scheduleAlarms(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        int start = getStartMinutes(context);
        int end = getEndMinutes(context);
        setAlarm(am, nextTriggerMillis(start), alarmIntent(context, REQ_START));
        setAlarm(am, nextTriggerMillis(end), alarmIntent(context, REQ_END));
    }

    private static void setAlarm(AlarmManager am, long triggerAtMillis, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    public static void cancelAlarms(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        am.cancel(alarmIntent(context, REQ_START));
        am.cancel(alarmIntent(context, REQ_END));
    }

    /** HH:mm formatting for the settings summary. */
    public static String formatMinutes(int minutes) {
        return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
