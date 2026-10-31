package com.gh4a.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires at dark-period boundaries (and on boot) to re-apply the scheduled
 * night mode and re-arm the next alarms.
 */
public class DarkModeAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (DarkModeScheduler.isScheduled(context)) {
                DarkModeScheduler.scheduleAlarms(context);
            }
            return;
        }
        // SEC-10 action whitelist: the only other legitimate delivery is
        // the app's own alarm PendingIntent, which carries NO action
        // (explicit intent, DarkModeScheduler.alarmIntent). Any non-null,
        // non-BOOT action is ignored instead of re-applying the theme.
        if (intent.getAction() != null) {
            return;
        }
        DarkModeScheduler.apply(context.getApplicationContext());
    }
}
