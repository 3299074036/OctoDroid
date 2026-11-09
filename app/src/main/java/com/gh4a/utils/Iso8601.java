package com.gh4a.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 61+72（去重-2）：ISO-8601 日期解析的唯一入口。
 * <p>
 * SimpleDateFormat 不是线程安全的——旧代码三处各写一遍，其中 SingleFactory
 * 的还是跨线程共享的可变实例，且未设 UTC（feed 日期按设备时区解析，
 * CST 下偏差 8 小时）。这里用 ThreadLocal 持有各格式实例并固定 UTC。
 * <p>
 * 解析失败返回 0（或 null），不抛异常——一条坏日期不应拖垮整个列表。
 */
public class Iso8601 {
    /** GitHub 常用的 ISO-8601 形：带/不带毫秒、带/不带 Z、纯日期（Z 形优先）。 */
    private static final String[] PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
    };

    /** feed 写回格式（无 Z、无毫秒，与旧 Transform 的 write 一致）。 */
    private static final String FEED_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    private static final ThreadLocal<SimpleDateFormat[]> FORMATTERS =
            ThreadLocal.withInitial(() -> {
                SimpleDateFormat[] formats = new SimpleDateFormat[PATTERNS.length];
                for (int i = 0; i < PATTERNS.length; i++) {
                    SimpleDateFormat f = new SimpleDateFormat(PATTERNS[i], Locale.US);
                    f.setTimeZone(TimeZone.getTimeZone("UTC"));
                    formats[i] = f;
                }
                return formats;
            });

    private static final ThreadLocal<SimpleDateFormat> FEED_FORMATTER =
            ThreadLocal.withInitial(() -> {
                SimpleDateFormat f = new SimpleDateFormat(FEED_PATTERN, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f;
            });

    /** 解析 ISO-8601 字符串为 epoch 毫秒；null/空/非法返回 0。 */
    public static long parseOrZero(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        for (SimpleDateFormat f : FORMATTERS.get()) {
            try {
                Date d = f.parse(value);
                if (d != null) {
                    return d.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    /** 解析为 Date；失败返回 null（供 SimpleXML Transform 用）。 */
    public static Date parseOrNull(String value) {
        long t = parseOrZero(value);
        return t == 0 ? null : new Date(t);
    }

    /** 按 feed 格式（无 Z、无毫秒）格式化，固定 UTC。 */
    public static String formatFeed(Date value) {
        return FEED_FORMATTER.get().format(value);
    }
}
