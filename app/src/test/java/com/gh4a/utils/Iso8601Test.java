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
}
