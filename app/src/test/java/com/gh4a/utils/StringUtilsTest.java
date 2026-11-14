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

    // ---- B 组试点扩充 ----

    private android.content.Context context() {
        return androidx.test.core.app.ApplicationProvider.getApplicationContext();
    }

    @Test
    public void getFirstLine_edgeCases() {
        assertEquals("", StringUtils.getFirstLine(""));
        assertEquals("", StringUtils.getFirstLine("\nabc"));
        assertEquals("a", StringUtils.getFirstLine("a\n"));
        assertEquals("a", StringUtils.getFirstLine("a\nb\nc"));
    }

    @Test
    public void shortSha_exactlySeven() {
        assertEquals("abcdefg", StringUtils.shortSha("abcdefg"));
        assertEquals("abcdef", StringUtils.shortSha("abcdef"));
        assertEquals("", StringUtils.shortSha(""));
    }

    @Test
    public void formatName_whitespaceLogin() {
        assertEquals("Name", StringUtils.formatName("   ", "Name"));
        assertEquals("login", StringUtils.formatName("login", "   "));
    }

    @Test
    public void formatRelativeTime_nullDateReturnsNull() {
        assertNull(StringUtils.formatRelativeTime(context(), (java.util.Date) null, false));
        assertNull(StringUtils.formatRelativeTime(context(), (java.util.Date) null, true));
    }

    @Test
    public void formatRelativeTime_recentDateIsNonEmpty() {
        CharSequence text = StringUtils.formatRelativeTime(context(),
                new java.util.Date(System.currentTimeMillis() - 60_000), false);
        assertTrue(text != null && text.length() > 0);
    }

    @Test
    public void formatRelativeTime_epochOverloadMatchesDateOverload() {
        // 两个重载完全等价。注意不能用 now 本身作输入：PrettyTime 在
        // 毫秒边界上 0 差值判"未来"、1ms 后判"过去"，两次调用会各取一端
        // 而 flaky；取 now ± 30s 的确定性时刻。
        long now = System.currentTimeMillis();
        assertEquals("moments ago",
                StringUtils.formatRelativeTime(context(), now - 30_000, false).toString());
        assertEquals(
                StringUtils.formatRelativeTime(context(), new java.util.Date(now - 30_000), false)
                        .toString(),
                StringUtils.formatRelativeTime(context(), now - 30_000, false).toString());
        assertEquals("moments from now",
                StringUtils.formatRelativeTime(context(), now + 30_000, false).toString());
        assertEquals(
                StringUtils.formatRelativeTime(context(), new java.util.Date(now + 30_000), false)
                        .toString(),
                StringUtils.formatRelativeTime(context(), now + 30_000, false).toString());
    }

    @Test
    public void formatRelativeTime_longAgoWithFlagReturnsText() {
        java.util.Date old = new java.util.Date(
                System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        CharSequence text = StringUtils.formatRelativeTime(context(), old, true);
        assertTrue(text != null && text.length() > 0);
    }

    @Test
    public void formatExactTime_returnsNonEmpty() {
        CharSequence text = StringUtils.formatExactTime(context(),
                new java.util.Date(1793872117000L));
        assertTrue(text != null && text.length() > 0);
        assertTrue(text.toString().contains("2026"));
    }

    @Test
    public void applyBoldTags_multipleSegments() {
        SpannableStringBuilder ssb =
                StringUtils.applyBoldTags("[b]a[/b] and [b]b[/b]");
        assertEquals("a and b", ssb.toString());
        assertEquals(2, ssb.getSpans(0, ssb.length(), StyleSpan.class).length);
    }

    @Test
    public void applyBoldTags_unclosedOpenTagKeptVerbatim() {
        assertEquals("[b]abc", StringUtils.applyBoldTags("[b]abc").toString());
        assertEquals("abc[b]", StringUtils.applyBoldTags("abc[b]").toString());
    }

    @Test
    public void applyBoldTags_closeBeforeOpenKeptVerbatim() {
        assertEquals("[/b]a", StringUtils.applyBoldTags("[/b]a").toString());
    }

    @Test
    public void applyBoldTags_emptyInput() {
        assertEquals("", StringUtils.applyBoldTags("").toString());
    }

    @Test
    public void parseEmojiToUnicode_aliasConverted() {
        assertEquals("hello", StringUtils.parseEmojiToUnicode("hello"));
        String converted = StringUtils.parseEmojiToUnicode("I :heart: it");
        assertFalse(converted.contains(":heart:"));
        assertTrue(converted.contains("I "));
    }

    @Test
    public void findMatchingLines_multiLineMatch() {
        assertArrayEquals(new int[]{2, 3},
                StringUtils.findMatchingLines("a\nb\nc\nd", "b\nc"));
        assertArrayEquals(new int[]{1, 1},
                StringUtils.findMatchingLines("a\nb", "a"));
        assertArrayEquals(new int[]{1, 1},
                StringUtils.findMatchingLines("a\n", "a"));
        assertArrayEquals(new int[]{1, 1},
                StringUtils.findMatchingLines("abc", ""));
    }

    @Test
    public void extractDiffHunkLineNumbers_moreCases() {
        assertArrayEquals(new int[]{-1, -1},
                StringUtils.extractDiffHunkLineNumbers("@@ -0,0 +0,0 @@"));
        assertArrayEquals(new int[]{4, 6},
                StringUtils.extractDiffHunkLineNumbers("@@ -5,3 +7,4 @@ extra"));
        assertNull(StringUtils.extractDiffHunkLineNumbers("@@ -x,y +1,1 @@"));
        assertNull(StringUtils.extractDiffHunkLineNumbers("@@ -1,1"));
        assertNull(StringUtils.extractDiffHunkLineNumbers(""));
    }

    @Test
    public void formatMention_withLoginAndWithout() {
        com.meisolsson.githubsdk.model.User user =
                com.meisolsson.githubsdk.model.User.builder().login("octocat").build();
        assertEquals("@octocat ", StringUtils.formatMention(context(), user).toString());
        // login 为空 → 回落 "(deleted)"
        com.meisolsson.githubsdk.model.User noLogin =
                com.meisolsson.githubsdk.model.User.builder().build();
        assertEquals("@(deleted) ",
                StringUtils.formatMention(context(), noLogin).toString());
        assertEquals("@(deleted) ",
                StringUtils.formatMention(context(), null).toString());
    }

    @Test
    public void toBase64AndFromBase64_roundTrip() {
        assertEquals("aGVsbG8=", StringUtils.toBase64("hello"));
        assertEquals("hello", StringUtils.fromBase64("aGVsbG8="));
        assertEquals("", StringUtils.toBase64(""));
        assertEquals("", StringUtils.fromBase64(""));
        assertEquals("aGVsbG8gd29ybGQ=",
                StringUtils.toBase64(StringUtils.fromBase64("aGVsbG8gd29ybGQ=")));
    }

    @Test(expected = NullPointerException.class)
    public void toBase64_nullThrows() {
        StringUtils.toBase64(null);
    }

    @Test
    public void getEditableStringSetFromPrefs_copySemantics() {
        java.util.Set<String> stored = new java.util.HashSet<>();
        stored.add("a");
        stored.add("b");
        android.content.SharedPreferences prefs = context().getSharedPreferences(
                "test-string-set", android.content.Context.MODE_PRIVATE);
        prefs.edit().putStringSet("key", stored).apply();
        java.util.Set<String> copy =
                StringUtils.getEditableStringSetFromPrefs(prefs, "key");
        assertEquals(stored, copy);
        // 改返回的集合不影响 prefs 里的原值
        copy.add("c");
        assertEquals(stored,
                StringUtils.getEditableStringSetFromPrefs(prefs, "key"));
        // 缺失的键返回空集合而非 null
        assertTrue(StringUtils.getEditableStringSetFromPrefs(prefs, "absent").isEmpty());
    }

    @Test
    public void replaceLabelPlaceholder_cases() {
        com.meisolsson.githubsdk.model.Label label =
                com.meisolsson.githubsdk.model.Label.builder()
                        .name("bug").color("d73a4a").build();
        SpannableStringBuilder text = new SpannableStringBuilder("Fix [label] issue");
        StringUtils.replaceLabelPlaceholder(context(), text, label);
        assertEquals("Fix bug issue", text.toString());
        assertEquals(1, text.getSpans(0, text.length(),
                com.gh4a.widget.IssueLabelSpan.class).length);
        // 无占位符 → 不变
        SpannableStringBuilder plain = new SpannableStringBuilder("no placeholder");
        StringUtils.replaceLabelPlaceholder(context(), plain, label);
        assertEquals("no placeholder", plain.toString());
        // label 为空 → 不变
        SpannableStringBuilder withTag = new SpannableStringBuilder("x [label] y");
        StringUtils.replaceLabelPlaceholder(context(), withTag, null);
        assertEquals("x [label] y", withTag.toString());
    }

    @Test
    public void addUserTypeSpan_insertsSuffixAndSpans() {
        SpannableStringBuilder builder = new SpannableStringBuilder("octocat");
        StringUtils.addUserTypeSpan(context(), builder, 7, "Bot");
        assertEquals("octocat (Bot)", builder.toString());
        assertEquals(1, builder.getSpans(0, builder.length(),
                android.text.style.RelativeSizeSpan.class).length);
        assertEquals(1, builder.getSpans(0, builder.length(),
                android.text.style.ForegroundColorSpan.class).length);
    }

    @Test
    public void unescapeCommonHtmlEntities_more() {
        assertEquals("“x”",
                StringUtils.unescapeCommonHtmlEntities("&ldquo;x&rdquo;"));
        assertEquals("a–b—c",
                StringUtils.unescapeCommonHtmlEntities("a&ndash;b&mdash;c"));
        assertEquals("‘’", StringUtils.unescapeCommonHtmlEntities("&#8216;&#8217;"));
        // 不在处理列表里的实体原样保留
        assertEquals("&quot;x&quot;",
                StringUtils.unescapeCommonHtmlEntities("&quot;x&quot;"));
        assertEquals("plain", StringUtils.unescapeCommonHtmlEntities("plain"));
    }

    @Test(expected = NullPointerException.class)
    public void unescapeCommonHtmlEntities_nullThrows() {
        StringUtils.unescapeCommonHtmlEntities(null);
    }
}
