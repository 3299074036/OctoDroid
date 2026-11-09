package com.gh4a.utils;

import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 269：StringUtils 是 115 系空守卫与 117/219 共享入口的收敛点，此前零单测覆盖。
 * applyBoldTags 需 android.text 桩，走 Robolectric（与 MirrorHelperTest 同款）。
 */
@RunWith(RobolectricTestRunner.class)
public class StringUtilsTest {

    @Test
    public void isBlank_nullEmptyWhitespace() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank("   "));
        assertFalse(StringUtils.isBlank("a"));
    }

    @Test
    public void getFirstLine_nullSingleMulti() {
        assertNull(StringUtils.getFirstLine(null));
        assertEquals("abc", StringUtils.getFirstLine("abc"));
        assertEquals("first", StringUtils.getFirstLine("first\nsecond"));
    }

    @Test
    public void shortSha_nullShortLong() {
        assertEquals("", StringUtils.shortSha(null));
        assertEquals("abc", StringUtils.shortSha("abc"));
        assertEquals("abcdefg", StringUtils.shortSha("abcdefg"));
        assertEquals("abcdefg", StringUtils.shortSha("abcdefghij"));
    }

    @Test
    public void formatName_blankCombinations() {
        assertEquals("Name", StringUtils.formatName(null, "Name"));
        assertEquals("Name", StringUtils.formatName("", "Name"));
        assertEquals("login", StringUtils.formatName("login", null));
        assertEquals("login", StringUtils.formatName("login", ""));
        assertEquals("login - Name", StringUtils.formatName("login", "Name"));
    }

    @Test
    public void applyBoldTags_balanced() {
        SpannableStringBuilder ssb = StringUtils.applyBoldTags("[b]hi[/b] x");
        assertEquals("hi x", ssb.toString());
        assertEquals(1, ssb.getSpans(0, ssb.length(), StyleSpan.class).length);
    }

    @Test
    public void applyBoldTags_nullAndPlain() {
        assertEquals("", StringUtils.applyBoldTags(null).toString());
        assertEquals("plain", StringUtils.applyBoldTags("plain").toString());
    }

    @Test
    public void applyBoldTags_unbalancedTrailingCloseTagKeptVerbatim() {
        // 265 同款解析器行为：start=-1、end>=0 时走 else 分支，剩余文本
        //（含多余的 [/b]）原样追加，不丢字符。
        assertEquals("a[/b]", StringUtils.applyBoldTags("a[/b]").toString());
    }

    @Test
    public void parseEmojiToUnicode_nullSafe() {
        assertEquals("", StringUtils.parseEmojiToUnicode(null));
        assertEquals("hello", StringUtils.parseEmojiToUnicode("hello"));
        assertFalse(StringUtils.parseEmojiToUnicode(":thumbsup:").contains(":thumbsup:"));
    }

    @Test
    public void findMatchingLines_foundAndMissing() {
        assertArrayEquals(new int[]{2, 2}, StringUtils.findMatchingLines("a\nb\nc", "b"));
        assertNull(StringUtils.findMatchingLines("a\nb\nc", "z"));
        assertNull(StringUtils.findMatchingLines(null, "b"));
        assertNull(StringUtils.findMatchingLines("a\nb", null));
    }

    @Test
    public void extractDiffHunkLineNumbers_validHugeAndInvalid() {
        assertArrayEquals(new int[]{0, 0},
                StringUtils.extractDiffHunkLineNumbers("@@ -1,3 +1,4 @@"));
        assertArrayEquals(new int[]{9, 19},
                StringUtils.extractDiffHunkLineNumbers("@@ -10,2 +20,5 @@ func()"));
        // 112：超大行号 parseInt 失败返回 null，不抛异常。
        assertNull(StringUtils.extractDiffHunkLineNumbers(
                "@@ -99999999999999999999,1 +1,1 @@"));
        assertNull(StringUtils.extractDiffHunkLineNumbers("not a hunk"));
    }

    @Test
    public void unescapeCommonHtmlEntities() {
        assertEquals("a < b & c",
                StringUtils.unescapeCommonHtmlEntities("a &lt; b &amp; c"));
        assertEquals("‘x’",
                StringUtils.unescapeCommonHtmlEntities("&lsquo;x&rsquo;"));
    }
}
