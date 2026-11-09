package com.gh4a.utils;

import android.content.Context;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 0.1.38：通用诊断追踪（正式诊断日志的写盘层）。
 * 只记链路事件，不记账号、凭据、内容、搜索词、仓库名、通知正文：
 * 调用方只传事件名与数量/布尔/类名，绝不传值本身。
 * 落盘在私有文件目录 diagnostic/diag-trace.txt（不清缓存就一直留着），
 * 上限 512KB，超了删了重记；崩溃历史另存 crash-*.txt，最多留 3 条。
 * 导出时由 exportFile() 合成一份 diag-export.txt（含主题追踪与崩溃历史），
 * 放缓存目录经 FileProvider 分享，导出文件本身不常驻。
 */
public class DiagTrace {
    private static final String DIR_NAME = "diagnostic";
    private static final String TRACE_NAME = "diag-trace.txt";
    private static final String EXPORT_NAME = "diag-export.txt";
    private static final long MAX_BYTES = 512 * 1024;
    private static final int MAX_CRASH_FILES = 3;
    private static final int CRASH_COPY_CAP = 64 * 1024;

    public static File dir(Context context) {
        File d = new File(context.getFilesDir(), DIR_NAME);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static File traceFile(Context context) {
        return new File(dir(context), TRACE_NAME);
    }

    public static synchronized void log(Context context, String tag, String detail) {
        if (context == null) {
            return;
        }
        try {
            File f = traceFile(context);
            if (f.exists() && f.length() > MAX_BYTES) {
                f.delete();
            }
            String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            // 70：try-with-resources——旧写法 w.write() 抛异常时 w.close()
            // 走不到（fd 泄漏，文件可能留半行）。
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, true))) {
                w.write(time + " " + tag + " " + detail);
                w.newLine();
            }
        } catch (Exception ignored) {
        }
    }

    /** 崩溃历史：写一份带时间戳的副本，最多留 3 条，旧的删掉。 */
    public static synchronized void recordCrash(Context context, String header, String text) {
        if (context == null) {
            return;
        }
        try {
            File d = dir(context);
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
                    .format(new Date());
            File f = new File(d, "crash-" + stamp + ".txt");
            // 70：同 log()，try-with-resources。
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
                w.write(header);
                w.newLine();
                String body = text != null && text.length() > CRASH_COPY_CAP
                        ? text.substring(0, CRASH_COPY_CAP) : text;
                w.write(body != null ? body : "");
            }
            File[] crashes = d.listFiles((dir1, name) ->
                    name.startsWith("crash-") && name.endsWith(".txt"));
            if (crashes != null && crashes.length > MAX_CRASH_FILES) {
                Arrays.sort(crashes, (a, b) ->
                        Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < crashes.length - MAX_CRASH_FILES; i++) {
                    crashes[i].delete();
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 崩溃历史文件（按时间倒序，最新的在前）。 */
    public static List<File> crashFiles(Context context) {
        try {
            File d = dir(context);
            File[] crashes = d.listFiles((dir1, name) ->
                    name.startsWith("crash-") && name.endsWith(".txt"));
            List<File> list = crashes != null
                    ? new ArrayList<>(Arrays.asList(crashes)) : new ArrayList<>();
            Collections.sort(list, (a, b) ->
                    Long.compare(b.lastModified(), a.lastModified()));
            return list;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    /** 读文件文本（上限 cap 字节），诊断页展示用。 */
    public static String readText(File f, int cap) {
        return readCapped(f, cap);
    }

    private static String readCapped(File f, int cap) {
        // 70：循环读满——旧写法只调一次 read()，大文件/慢存储下返回可能
        // 小于 bytes.length，诊断页展示被截断；close 也进 finally。
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] bytes = new byte[(int) Math.min(f.length(), cap)];
            int read = 0;
            int n;
            while (read < bytes.length
                    && (n = in.read(bytes, read, bytes.length - read)) != -1) {
                read += n;
            }
            return read > 0 ? new String(bytes, 0, read, "UTF-8") : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 合成导出文件：诊断追踪 + 主题追踪 + 崩溃历史（最新 3 条）。 */
    public static synchronized File exportFile(Context context) {
        // 70：同 log()，try-with-resources（旧写法中途抛异常时不 close）。
        File out = new File(context.getCacheDir(), EXPORT_NAME);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(out))) {
            w.write("=== 诊断追踪 diag-trace.txt ===");
            w.newLine();
            File trace = traceFile(context);
            w.write(trace.exists() ? readCapped(trace, (int) MAX_BYTES) : "(无)");
            w.newLine();
            w.write("=== 主题追踪 theme-trace.txt ===");
            w.newLine();
            File theme = ThemeTrace.file(context);
            w.write(theme.exists() ? readCapped(theme, 256 * 1024) : "(无)");
            w.newLine();
            File d = dir(context);
            File[] crashes = d.listFiles((dir1, name) ->
                    name.startsWith("crash-") && name.endsWith(".txt"));
            List<File> list = crashes != null
                    ? new ArrayList<>(Arrays.asList(crashes)) : new ArrayList<>();
            Collections.sort(list, (a, b) ->
                    Long.compare(b.lastModified(), a.lastModified()));
            w.write("=== 崩溃历史（最新 " + list.size() + " 条） ===");
            w.newLine();
            for (File c : list) {
                w.write("--- " + c.getName() + " ---");
                w.newLine();
                w.write(readCapped(c, CRASH_COPY_CAP));
                w.newLine();
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }
}
