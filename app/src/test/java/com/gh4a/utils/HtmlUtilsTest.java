package com.gh4a.utils;

import android.content.Context;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.widget.LinkSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * B 组试点：HtmlUtils。write*Include 与 rewriteRelativeUrls 是纯逻辑；
 * encode 走 Robolectric（需 Context/Resources 解析 HTML）。
 */
@RunWith(RobolectricTestRunner.class)
public class HtmlUtilsTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void writeScriptInclude_exact() {
        StringBuilder sb = new StringBuilder();
        HtmlUtils.writeScriptInclude(sb, "highlight");
        assertEquals("<script src='file:///android_asset/highlight.js' "
                + "type='text/javascript'></script>", sb.toString());
    }

    @Test
    public void writeScriptInclude_appendsToExisting() {
        StringBuilder sb = new StringBuilder("<!--x-->");
        HtmlUtils.writeScriptInclude(sb, "a");
        assertTrue(sb.toString().startsWith("<!--x-->"));
        assertTrue(sb.toString().contains("a.js"));
    }

    @Test
    public void writeCssInclude_exact() {
        StringBuilder sb = new StringBuilder();
        HtmlUtils.writeCssInclude(sb, "github", "dark");
        assertEquals("<link href='file:///android_asset/github-dark.css' "
                + "rel='stylesheet' type='text/css'/>", sb.toString());
    }

    @Test
    public void rewriteRelativeUrls_hrefRelative() {
        String html = "<a href=\"doc/guide.md\">g</a>";
        String out = HtmlUtils.rewriteRelativeUrls(html, "octocat", "repo", "main", "docs");
        assertEquals("<a href=\"https://github.com/octocat/repo/blob/main/docs/doc/guide.md\">g</a>",
                out);
    }

    @Test
    public void rewriteRelativeUrls_hrefAbsolutePath() {
        String html = "<a href=\"/octocat/repo/blob/main/x.md\">g</a>";
        String out = HtmlUtils.rewriteRelativeUrls(html, "octocat", "repo", "main", "docs");
        assertEquals("<a href=\"https://github.com/octocat/repo/blob/main/docs"
                + "/octocat/repo/blob/main/x.md\">g</a>", out);
    }

    @Test
    public void rewriteRelativeUrls_hrefAbsoluteAndAnchorUnchanged() {
        String html = "<a href=\"https://example.com/x\">a</a> <a href=\"#frag\">b</a>"
                + " <a href=\"//cdn.example.com/x\">c</a> <a href=\"mailto:a@b.c\">d</a>";
        assertEquals(html, HtmlUtils.rewriteRelativeUrls(html, "o", "r", "main", ""));
    }

    @Test
    public void rewriteRelativeUrls_srcUsesRawBase() {
        String html = "<img src=\"img/logo.png\"/>";
        String out = HtmlUtils.rewriteRelativeUrls(html, "octocat", "repo", "main", "docs");
        assertEquals("<img src=\"https://raw.github.com/octocat/repo/main/docs/img/logo.png\"/>",
                out);
    }

    @Test
    public void rewriteRelativeUrls_nullOrEmptyRefFallsBackToHead() {
        // folderPath 为空时 baseUrl 以 "/" 结尾，改写再拼 "/" 形成双斜杠
        //（"…/blob/HEAD//x.md"）；GitHub 可正常解析，此处锁定现状。
        String html = "<a href=\"x.md\">g</a>";
        assertEquals("<a href=\"https://github.com/o/r/blob/HEAD//x.md\">g</a>",
                HtmlUtils.rewriteRelativeUrls(html, "o", "r", null, ""));
        assertEquals("<a href=\"https://github.com/o/r/blob/HEAD//x.md\">g</a>",
                HtmlUtils.rewriteRelativeUrls(html, "o", "r", "", ""));
    }

    @Test
    public void rewriteRelativeUrls_colonRelativeIsRewritten() {
        // 131：含冒号的相对路径（如 12:30/x.png）不是绝对 URL，必须改写；
        // 单字母 scheme（a:b）同样按相对路径改写。
        // folderPath 为空时 baseUrl 末尾 "/" 与拼入的 "/" 形成双斜杠（见上）。
        String html = "<img src=\"12:30/x.png\"/> <img src=\"a:b\"/>";
        String out = HtmlUtils.rewriteRelativeUrls(html, "o", "r", "main", "");
        assertEquals("<img src=\"https://raw.github.com/o/r/main//12:30/x.png\"/>"
                + " <img src=\"https://raw.github.com/o/r/main//a:b\"/>", out);
    }

    @Test
    public void rewriteRelativeUrls_noMatchReturnsSameString() {
        String html = "<p>no links here</p>";
        assertEquals(html, HtmlUtils.rewriteRelativeUrls(html, "o", "r", "main", ""));
    }

    @Test
    public void rewriteRelativeUrls_hrefAndSrcBothRewritten() {
        String html = "<a href=\"a.md\">x</a><img src=\"b.png\"/>";
        String out = HtmlUtils.rewriteRelativeUrls(html, "o", "r", "v1", "d");
        assertEquals("<a href=\"https://github.com/o/r/blob/v1/d/a.md\">x</a>"
                + "<img src=\"https://raw.github.com/o/r/v1/d/b.png\"/>", out);
    }

    @Test
    public void encode_nullAndEmptyReturnEmpty() {
        assertEquals("", HtmlUtils.encode(mContext, null, null).toString());
        assertEquals("", HtmlUtils.encode(mContext, "", null).toString());
    }

    @Test
    public void encode_plainText() {
        assertEquals("hello", HtmlUtils.encode(mContext, "hello", null).toString());
    }

    @Test
    public void encode_boldProducesStyleSpan() {
        Spanned spanned = (Spanned) HtmlUtils.encode(mContext, "<b>hi</b> there", null);
        assertEquals("hi there", spanned.toString());
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(Typeface.BOLD, spans[0].getStyle());
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(2, spanned.getSpanEnd(spans[0]));
    }

    @Test
    public void encode_linkProducesLinkSpan() {
        Spanned spanned = (Spanned) HtmlUtils.encode(
                mContext, "<a href=\"https://example.com\">text</a>", null);
        assertEquals("text", spanned.toString());
        LinkSpan[] spans = spanned.getSpans(0, spanned.length(), LinkSpan.class);
        assertEquals(1, spans.length);
    }

    @Test
    public void encode_headingProducesRelativeSizeSpan() {
        Spanned spanned = (Spanned) HtmlUtils.encode(mContext, "<h1>Title</h1>", null);
        assertEquals("Title", spanned.toString().trim());
        assertEquals(1, spanned.getSpans(0, spanned.length(), RelativeSizeSpan.class).length);
    }

    @Test
    public void encode_unorderedListProducesBulletSpan() {
        Spanned spanned = (Spanned) HtmlUtils.encode(
                mContext, "<ul><li>one</li><li>two</li></ul>", null);
        assertTrue(spanned.toString().contains("one"));
        assertTrue(spanned.toString().contains("two"));
        assertEquals(2, spanned.getSpans(0, spanned.length(), BulletSpan.class).length);
    }

    @Test
    public void encode_italicAndCode() {
        Spanned spanned = (Spanned) HtmlUtils.encode(
                mContext, "<i>it</i> <code>cd</code>", null);
        assertTrue(spanned.toString().contains("it"));
        assertTrue(spanned.toString().contains("cd"));
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(Typeface.ITALIC, spans[0].getStyle());
    }
}
