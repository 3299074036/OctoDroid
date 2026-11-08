package com.gh4a.worker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/**
 * 0.2.2：发通知前的权限守卫（B-M12）。Android 13+ 没有 POST_NOTIFICATIONS
 * 权限时调 notify() 会抛 SecurityException（lint MissingPermission x4）；
 * 无权限时跳过并由调用方记一条日志，不崩溃、不打扰。
 * 与 ReleaseRadarWorker.notifyAuthFailed 原有的守卫同款写法。
 */
final class NotificationPermission {
    private NotificationPermission() {
    }

    /** 有发通知权限时返回 true（Android 13 以下永远有）。 */
    static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
