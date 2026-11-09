package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.gh4a.fragment.SettingsFragment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 0.1.36：深色模式诊断追踪（临时，定论后即删）。
 * 只记不改行为：每步带时间追加写进缓存文件 theme-trace.txt，
 * 整栈重启、进程重建都不丢；我页“导出深色模式日志”行经
 * FileProvider 分享该文件。绝不记账号、凭据、内容，只记
 * 模式/主题/开关/页面昼夜与 Activity 实例标识。
 */
public class ThemeTrace {
    private static final String FILE_NAME = "theme-trace.txt";
    private static final long MAX_BYTES = 256 * 1024;

    public static File file(Context context) {
        return new File(context.getCacheDir(), FILE_NAME);
    }

    /**
     * P14：与 DiagTrace.log 同款——写盘移入单线程写盘器，进程启动与每次
     * 主题切换的调用方（主线程）不再等磁盘 IO；时间戳在调用线程取。
     */
    private static final ExecutorService sWriter =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ThemeTrace-writer");
                t.setDaemon(true);
                return t;
            });

    public static void log(Context context, String tag, String detail) {
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        sWriter.execute(() -> writeLog(appContext, tag, detail, time));
    }

    private static void writeLog(Context context, String tag, String detail, String time) {
        try {
            File f = file(context);
            if (f.exists() && f.length() > MAX_BYTES) {
                f.delete();
            }
            // 70：同 DiagTrace.log()，try-with-resources。
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, true))) {
                w.write(time + " " + tag + " " + detail);
                w.newLine();
            }
        } catch (Exception ignored) {
        }
    }

    public static String delegateLabel(int delegate) {
        switch (delegate) {
            case AppCompatDelegate.MODE_NIGHT_YES: return "YES";
            case AppCompatDelegate.MODE_NIGHT_NO: return "NO";
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM: return "FOLLOW_SYSTEM";
            case AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY: return "AUTO_BATTERY";
            default: return String.valueOf(delegate);
        }
    }

    public static String uiModeLabel(Context context) {
        try {
            int mask = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            if (mask == Configuration.UI_MODE_NIGHT_YES) return "YES";
            if (mask == Configuration.UI_MODE_NIGHT_NO) return "NO";
            return "UNKNOWN";
        } catch (Exception e) {
            return "?";
        }
    }

    public static String state(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
            String mode = prefs.getString(DarkModeScheduler.KEY_DARK_MODE, "?");
            int theme = prefs.getInt(SettingsFragment.KEY_THEME, -1);
            return "mode=" + mode + " theme=" + theme
                    + " delegate=" + delegateLabel(AppCompatDelegate.getDefaultNightMode())
                    + " page=" + uiModeLabel(context);
        } catch (Exception e) {
            return "state=?";
        }
    }

    public static void logState(Context context, String tag) {
        log(context, tag, state(context));
    }

    public static String instance(Object activity) {
        if (activity == null) {
            return "?";
        }
        return activity.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(activity));
    }
}
