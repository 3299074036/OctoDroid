package com.gh4a.utils;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 269：Iso8601 是日期解析的唯一入口（61+72 去重-2），此前零单测覆盖。
 * 锁定四种输入形的解析、非法输入的 0/null 回退，以及 feed 写回格式。
 * 期望值均为 UTC epoch 毫秒（解析固定 UTC，与设备时区无关）。
 */
public class Iso8601Test {

    @Test
    public void parseOrZero_allFormats() {
        assertEquals(1793872117000L, Iso8601.parseOrZero("2026-11-05T09:48:37Z"));
        assertEquals(1793872117123L, Iso8601.parseOrZero("2026-11-05T09:48:37.123Z"));
        assertEquals(1793872117000L, Iso8601.parseOrZero("2026-11-05T09:48:37"));
        assertEquals(1793836800000L, Iso8601.parseOrZero("2026-11-05"));
    }

    @Test
    public void parseOrZero_invalidReturnsZero() {
        assertEquals(0L, Iso8601.parseOrZero(null));
        assertEquals(0L, Iso8601.parseOrZero(""));
        assertEquals(0L, Iso8601.parseOrZero("not-a-date"));
        assertEquals(0L, Iso8601.parseOrZero("2026-13-40T99:99:99Z"));
    }

    @Test
    public void parseOrNull_validAndInvalid() {
        assertEquals(new Date(1793872117000L),
                Iso8601.parseOrNull("2026-11-05T09:48:37Z"));
        assertNull(Iso8601.parseOrNull(null));
        assertNull(Iso8601.parseOrNull("bogus"));
    }

    @Test
    public void formatFeed_epochZero() {
        assertEquals("1970-01-01T00:00:00", Iso8601.formatFeed(new Date(0)));
        assertEquals("2026-11-05T09:48:37",
                Iso8601.formatFeed(new Date(1793872117000L)));
    }

    @Test
    public void parseOrZero_leapAndNonLeapFeb29() {
        assertEquals(1709164800000L, Iso8601.parseOrZero("2024-02-29"));
        assertEquals(0L, Iso8601.parseOrZero("2026-02-29"));
    }

    @Test
    public void parseOrZero_unsupportedShapesReturnZero() {
        assertEquals(0L, Iso8601.parseOrZero("2026/11/05"));
        assertEquals(0L, Iso8601.parseOrZero("2026-00-10"));
        assertEquals(0L, Iso8601.parseOrZero("2026-11-32"));
    }

    @Test
    public void parseOrZero_invalidHourReturnsZero() {
        // 疑似生产 bug：时 25 非法，本应返回 0；实际回落到纯日期格式
        // "yyyy-MM-dd" 做前缀匹配，返回 2026-11-05 零点（1793836800000L）。
        // 本测试保持失败态，不迁就；见 B 组汇报。
        assertEquals(0L, Iso8601.parseOrZero("2026-11-05T25:00:00Z"));
    }

    @Test
    public void parseOrZero_offsetSuffixIsSilentlyIgnored() {
        // 注意：SimpleDateFormat.parse 只要求前缀匹配，不要求消费整个
        // 字符串——带时区偏移的形不会返回 0，而是被无时区 pattern 按
        // 前缀解析、偏移部分直接丢弃（按 UTC 计）。+00:00 时结果碰巧
        // 正确，+08:00 则会静默偏差 8 小时（GitHub 只返回 Z 形，实
        // 际无影响；此处锁定现状，是否收紧为返回 0 待定，见 B 组汇报）。
        assertEquals(1793872117000L,
                Iso8601.parseOrZero("2026-11-05T09:48:37+08:00"));
        assertEquals(1793872117000L,
                Iso8601.parseOrZero("2026-11-05T09:48:37.123+00:00"));
        assertEquals(1793872117000L, Iso8601.parseOrZero("2026-11-05T09:48:37Z "));
        // 前导空白同样被 SimpleDateFormat 容忍
        assertEquals(1793872117000L, Iso8601.parseOrZero(" 2026-11-05T09:48:37Z"));
    }

    @Test
    public void parseOrZero_millisAndMidnightEdges() {
        assertEquals(1793872117001L, Iso8601.parseOrZero("2026-11-05T09:48:37.001Z"));
        assertEquals(1793872117999L, Iso8601.parseOrZero("2026-11-05T09:48:37.999Z"));
        assertEquals(1793836800000L, Iso8601.parseOrZero("2026-11-05T00:00:00Z"));
    }

    @Test
    public void parseOrNull_epochZeroSentinelCollision() {
        // 注意：epoch 0（1970-01-01T00:00:00Z）与解析失败共用 0 哨兵，
        // parseOrNull 无法区分，返回 null——这是哨兵设计的已知代价
        //（GitHub 日期不可能为 epoch 0，实际无影响），此处锁定现状。
        assertNull(Iso8601.parseOrNull("1970-01-01T00:00:00Z"));
        assertNull(Iso8601.parseOrNull("1970-01-01"));
    }

    @Test
    public void formatFeed_roundTrip() {
        assertEquals("2026-11-05T09:48:37",
                Iso8601.formatFeed(Iso8601.parseOrNull("2026-11-05T09:48:37.123Z")));
    }

    @Test(expected = NullPointerException.class)
    public void formatFeed_nullThrows() {
        Iso8601.formatFeed(null);
    }
}
