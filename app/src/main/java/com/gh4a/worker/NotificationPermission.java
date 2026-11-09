package com.gh4a.worker;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 0.2.2：发通知前的权限守卫（B-M12）。Android 13+ 没有 POST_NOTIFICATIONS
 * 权限时调 notify() 会抛 SecurityException（lint MissingPermission x4）；
 * 无权限时跳过并由调用方记一条日志，不崩溃、不打扰。
 * 与 ReleaseRadarWorker.notifyAuthFailed 原有的守卫同款写法。
 * 0.2.4（57）：DownloadUtils 的下载完成通知也要走同一守卫，类与方法
 * 改为 public（原包内可见，跨包的 utils 调不到）。
 */
public final class NotificationPermission {
    private NotificationPermission() {
    }

    /** 有发通知权限时返回 true（Android 13 以下永远有）。 */
    public static boolean canPostNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 去重-3：统一的通知权限请求入口（封装版本分支 + 已授权短路）。
     * Android 13 以下或已授权时直接返回 true，不弹框；否则发起一次系统
     * 权限请求并返回 false。调用方无需处理请求结果：Worker 调度本身不
     * 依赖授权（doWork 开头无权限即跳过，见 53/54），授权状态下次进入
     * 相关流程时由 canPostNotifications 重新判定。用 ActivityResultLauncher
     * 的调用方（HomeActivity）不受影响，不必经此入口。
     */
    public static boolean requestIfNeeded(Activity activity) {
        // 显式版本分支放这里（不只依赖 canPostNotifications 内部的检查），
        // 否则 lint 认不出守卫、报 InlinedApi（POST_NOTIFICATIONS 是 API 33 常量）。
        if (activity == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || canPostNotifications(activity)) {
            return true;
        }
        ActivityCompat.requestPermissions(activity,
                new String[] { Manifest.permission.POST_NOTIFICATIONS }, 0);
        return false;
    }
}
