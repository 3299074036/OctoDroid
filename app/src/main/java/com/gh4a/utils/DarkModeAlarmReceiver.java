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
        DarkModeScheduler.apply(context.getApplicationContext());
    }
}
