package com.gh4a.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.widget.LinkSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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

    // ---- 其余标签分支（encode 直通 HtmlToSpannedConverter） ----

    private Spanned encode(String html) {
        return (Spanned) HtmlUtils.encode(mContext, html, null);
    }

    @Test
    public void encode_paragraphAndDivAndBr() {
        Spanned spanned = encode("<p>one</p><div>two</div>three<br>four");
        String text = spanned.toString();
        assertTrue(text.contains("one"));
        assertTrue(text.contains("two"));
        assertTrue(text.contains("three"));
        assertTrue(text.contains("four"));
        assertTrue(text.contains("\n"));
    }

    @Test
    public void encode_blockquoteProducesReplySpan() {
        Spanned spanned = encode("<blockquote>quoted</blockquote>");
        assertTrue(spanned.toString().contains("quoted"));
        // ReplySpan 是 private 类，按其实现的 LeadingMarginSpan 断言
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class).length);
    }

    @Test
    public void encode_preProducesCodeBlockSpan() {
        Spanned spanned = encode("<pre>line1\nline2</pre>");
        assertTrue(spanned.toString().contains("line1"));
        // CodeBlockSpan 是 private 类，按其实现的 LineBackgroundSpan 断言
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                TypefaceSpan.class).length);
    }

    @Test
    public void encode_codeInsidePreIsIgnored() {
        // <pre> 内的 <code> 不再另挂 Code span（pre 已包办代码块格式）
        Spanned spanned = encode("<pre><code>cd</code></pre>");
        assertTrue(spanned.toString().contains("cd"));
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class).length);
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                android.text.style.BackgroundColorSpan.class).length);
    }

    @Test
    public void encode_hrProducesHorizontalLineSpan() {
        Spanned spanned = encode("a<hr>b");
        assertTrue(spanned.toString().contains("a"));
        assertTrue(spanned.toString().contains("b"));
        // HorizontalLineSpan 是 private 类，按 LineBackgroundSpan 断言
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class).length);
    }

    @Test
    public void encode_strongEmCiteDfn() {
        Spanned spanned = encode(
                "<strong>s</strong> <em>e</em> <cite>c</cite> <dfn>d</dfn>");
        StyleSpan[] spans = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(4, spans.length);
    }

    @Test
    public void encode_bigAndSmall() {
        Spanned spanned = encode("<big>B</big> <small>S</small>");
        RelativeSizeSpan[] spans =
                spanned.getSpans(0, spanned.length(), RelativeSizeSpan.class);
        assertEquals(2, spans.length);
    }

    @Test
    public void encode_underlineStrikeSupSub() {
        Spanned spanned = encode(
                "<u>u</u> <del>d</del> <s>s</s> <strike>k</strike>"
                        + " <sup>up</sup> <sub>dn</sub>");
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                UnderlineSpan.class).length);
        assertEquals(3, spanned.getSpans(0, spanned.length(),
                StrikethroughSpan.class).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                SuperscriptSpan.class).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                SubscriptSpan.class).length);
    }

    @Test
    public void encode_sampAndTtAreMonospace() {
        Spanned spanned = encode("<samp>sm</samp> <tt>tt</tt>");
        assertEquals(2, spanned.getSpans(0, spanned.length(),
                TypefaceSpan.class).length);
    }

    @Test
    public void encode_fontColorAndFace() {
        Spanned spanned = encode(
                "<font color=\"#ff0000\" face=\"monospace\">f</font>");
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                ForegroundColorSpan.class).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                TypefaceSpan.class).length);
    }

    @Test
    public void encode_fontInvalidColorIsIgnored() {
        Spanned spanned = encode("<font color=\"notacolor\">f</font>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                ForegroundColorSpan.class).length);
    }

    @Test
    public void encode_spanCssColorsAndLineThrough() {
        // style 各声明间须带空格：color 正则的 (\S*) 会一路吞到空白处，
        // 无空格时整串被当成一个颜色值、parseColor 失败（见下个测试）。
        Spanned spanned = encode("<span style=\"color: #00ff00;"
                + " background-color: #0000ff;"
                + " text-decoration: line-through\">x</span>");
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                ForegroundColorSpan.class).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                BackgroundColorSpan.class).length);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                StrikethroughSpan.class).length);
    }

    @Test
    public void encode_spanCssColorsNoSpaces_colorIsDropped() {
        // 锁定现状：声明间无空格时 color 正则把后续全部吞进颜色值，
        // parseColor 失败，Foreground span 不挂；background 与
        // text-decoration 的正则要求前面是空白或串首，分号前也不匹配，
        // 三者全丢。
        Spanned spanned = encode("<span style=\"color:#00ff00;"
                + "background-color:#0000ff;"
                + "text-decoration:line-through\">x</span>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                ForegroundColorSpan.class).length);
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                BackgroundColorSpan.class).length);
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                StrikethroughSpan.class).length);
    }

    @Test
    public void encode_headingsH2ToH6() {
        for (int level = 2; level <= 6; level++) {
            Spanned spanned = encode("<h" + level + ">T</h" + level + ">");
            assertTrue(spanned.toString().contains("T"));
            assertEquals(1, spanned.getSpans(0, spanned.length(),
                    RelativeSizeSpan.class).length);
            assertEquals(1, spanned.getSpans(0, spanned.length(),
                    StyleSpan.class).length);
        }
    }

    @Test
    public void encode_orderedListWithStart() {
        Spanned spanned = encode("<ol start=\"3\"><li>a</li><li>b</li></ol>");
        assertTrue(spanned.toString().contains("a"));
        assertTrue(spanned.toString().contains("b"));
        // NumberedItemSpan 是 private 类，按 LeadingMarginSpan 断言
        assertEquals(2, spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class).length);
    }

    @Test
    public void encode_checkboxInputCheckedAndUnchecked() {
        Spanned checked = encode("<input type=\"checkbox\" checked>");
        Spanned unchecked = encode("<input type=\"checkbox\">");
        assertEquals(1, checked.getSpans(0, checked.length(),
                ImageSpan.class).length);
        assertEquals(1, unchecked.getSpans(0, unchecked.length(),
                ImageSpan.class).length);
        Spanned other = encode("<input type=\"text\">");
        assertEquals(0, other.getSpans(0, other.length(),
                ImageSpan.class).length);
    }

    @Test
    public void encode_imgUsesImageGetter() {
        Html.ImageGetter getter = source -> {
            Drawable d = new ColorDrawable(0xff000000);
            d.setBounds(0, 0, 10, 10);
            return d;
        };
        Spanned spanned = (Spanned) HtmlUtils.encode(
                mContext, "<img src=\"https://example.com/x.png\"/>", getter);
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                ImageSpan.class).length);
    }

    @Test
    public void encode_videoWithSrcProducesLink() {
        Spanned spanned = encode(
                "<video src=\"https://example.com/v.mp4\"></video>");
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LinkSpan.class).length);
    }

    @Test
    public void encode_videoWithoutSrcProducesNothing() {
        Spanned spanned = encode("<video></video>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                LinkSpan.class).length);
        assertFalse(spanned.toString().contains("🎥"));
    }

    @Test
    public void encode_tableRowsAndCells() {
        Spanned spanned = encode("<table><tr><th>H</th><td>C</td></tr>"
                + "<tr><td>D</td></tr></table>");
        String text = spanned.toString();
        assertTrue(text.contains("H"));
        assertTrue(text.contains("C"));
        assertTrue(text.contains("D"));
        assertTrue(text.contains("\n"));
        StyleSpan[] bold = spanned.getSpans(0, spanned.length(), StyleSpan.class);
        assertEquals(1, bold.length);
    }

    @Test
    public void encode_detailsAndSummary() {
        Spanned spanned = encode(
                "<details><summary>sum</summary>body</details>");
        assertTrue(spanned.toString().contains("sum"));
        assertTrue(spanned.toString().contains("body"));
    }

    @Test
    public void encode_anchorWithoutHrefProducesNoLink() {
        Spanned spanned = encode("<a>plain</a>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                LinkSpan.class).length);
    }

    @Test
    public void implicitConstructor_instantiates() {
        assertNotNull(new HtmlUtils());
    }

    // ---- span draw methods: invoked directly (a TextView draw would
    // exercise the same code, but these are deterministic unit calls) ----

    private Canvas canvas() {
        return new Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
    }

    @Test
    public void drawReplySpan_getLeadingMarginAndDraw() {
        Spanned spanned = encode("<blockquote>quoted</blockquote>");
        LeadingMarginSpan[] spans = spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class);
        assertEquals(1, spans.length);
        assertTrue(spans[0].getLeadingMargin(true) >= 0);
        spans[0].drawLeadingMargin(canvas(), new Paint(), 0, 1, 0, 10, 20,
                spanned, 0, 6, true, null);
    }

    @Test
    public void drawCodeBlockSpan_drawBackground() {
        Spanned spanned = encode("<pre>line1\nline2</pre>");
        LineBackgroundSpan[] spans = spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class);
        assertEquals(1, spans.length);
        spans[0].drawBackground(canvas(), new Paint(), 0, 100, 0, 10, 20,
                spanned, 0, 11, 0);
    }

    @Test
    public void drawHorizontalLineSpan_drawBackground() {
        Spanned spanned = encode("a<hr>b");
        LineBackgroundSpan[] spans = spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class);
        assertEquals(1, spans.length);
        spans[0].drawBackground(canvas(), new Paint(), 0, 100, 0, 10, 20,
                spanned, 0, 3, 0);
    }

    @Test
    public void drawNumberedItemSpan_measureAndDrawBothStartHalves() {
        Spanned spanned = encode("<ol start=\"3\"><li>a</li><li>b</li></ol>");
        LeadingMarginSpan[] spans = spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class);
        assertEquals(2, spans.length);
        assertTrue(spans[0].getLeadingMargin(true) > 0);
        int spanStart = spanned.getSpanStart(spans[0]);
        // start == span start: draws the "3. " marker
        spans[0].drawLeadingMargin(canvas(), new Paint(), 0, 1, 0, 10, 20,
                spanned, spanStart, spanStart + 1, true, null);
        // start != span start: draws nothing
        spans[0].drawLeadingMargin(canvas(), new Paint(), 0, 1, 0, 10, 20,
                spanned, spanStart + 1, spanStart + 2, true, null);
    }

    // ---- text-align on block elements ----

    private Layout.Alignment alignmentOf(String html) {
        Spanned spanned = encode(html);
        AlignmentSpan.Standard[] spans = spanned.getSpans(0, spanned.length(),
                AlignmentSpan.Standard.class);
        assertEquals(1, spans.length);
        return spans[0].getAlignment();
    }

    @Test
    public void encode_textAlignCenter() {
        assertEquals(Layout.Alignment.ALIGN_CENTER,
                alignmentOf("<p style=\"text-align: center\">x</p>"));
    }

    @Test
    public void encode_textAlignStart() {
        assertEquals(Layout.Alignment.ALIGN_NORMAL,
                alignmentOf("<p style=\"text-align: start\">x</p>"));
    }

    @Test
    public void encode_textAlignEnd() {
        assertEquals(Layout.Alignment.ALIGN_OPPOSITE,
                alignmentOf("<p style=\"text-align: end\">x</p>"));
    }

    @Test
    public void encode_textAlignUnknownValueProducesNoAlignment() {
        Spanned spanned = encode("<p style=\"text-align: justify\">x</p>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                AlignmentSpan.Standard.class).length);
    }

    @Test
    public void encode_textAlignAbsentProducesNoAlignment() {
        Spanned spanned = encode("<p style=\"color: red\">x</p>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                AlignmentSpan.Standard.class).length);
    }

    @Test
    public void encode_textAlignWithBrBacksOffTrailingNewlines() {
        Spanned spanned = encode("<p style=\"text-align: center\">x<br></p>");
        assertEquals("x", spanned.toString());
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                AlignmentSpan.Standard.class).length);
    }

    @Test
    public void encode_textAlignSingleNewlineSpanSkipsBackoff() {
        Spanned spanned = encode(
                "<p>a</p><p style=\"text-align: center\"><br></p>");
        assertEquals("a", spanned.toString());
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                AlignmentSpan.Standard.class).length);
    }

    @Test
    public void encode_bulletSpanEndingAtOneSkipsBackoffGuard() {
        Spanned spanned = encode("<ul><li><br></li></ul>");
        assertEquals("", spanned.toString());
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                BulletSpan.class).length);
    }

    // ---- newlines / whitespace ----

    @Test
    public void encode_leadingBrIsStripped() {
        assertEquals("hello", encode("<br>hello").toString());
    }

    @Test
    public void encode_allNewlinesPrefixSkipsAppend() {
        assertEquals("x", encode("<br><br><p>x</p>").toString());
    }

    @Test
    public void encode_newlineInTextCollapsesToSpace() {
        assertEquals("a b", encode("a\nb").toString());
    }

    @Test
    public void encode_leadingSpaceIsDropped() {
        assertEquals("hello", encode(" hello").toString());
    }

    @Test
    public void encode_spaceAfterBrIsDropped() {
        assertEquals("a\nb", encode("a<br> b").toString());
    }

    // ---- headings h0 / h7 are not headings ----

    @Test
    public void encode_headingH0AndH7AreNotHeadings() {
        Spanned spanned = encode("<h0>x</h0><h7>y</h7>");
        assertTrue(spanned.toString().contains("x"));
        assertTrue(spanned.toString().contains("y"));
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                RelativeSizeSpan.class).length);
    }

    // ---- table code snippets (suggested-change diffs) ----

    @Test
    public void encode_tableCodeSnippetAddition() {
        Spanned spanned = encode("<table><tr>"
                + "<td class=\"blob-num blob-num-addition\"></td>"
                + "<td class=\"blob-code-addition\">line1</td></tr></table>");
        // '+' 与行内容之间隔着 </td> 追加的 \u2003，分开断言
        assertTrue(spanned.toString().contains("+"));
        assertTrue(spanned.toString().contains("line1"));
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class).length);
    }

    @Test
    public void encode_tableCodeSnippetDeletion() {
        Spanned spanned = encode("<table><tr>"
                + "<td class=\"blob-num blob-num-deletion\"></td>"
                + "<td class=\"blob-code-deletion\">line2</td></tr></table>");
        assertTrue(spanned.toString().contains("-"));
        assertTrue(spanned.toString().contains("line2"));
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class).length);
    }

    @Test
    public void encode_tableCodeSnippetPlainBlobNum() {
        Spanned spanned = encode("<table><tr>"
                + "<td class=\"blob-num\"></td>"
                + "<td class=\"blob-code\">line3</td></tr></table>");
        assertTrue(spanned.toString().contains("line3"));
        assertFalse(spanned.toString().contains("+line3"));
        assertFalse(spanned.toString().contains("-line3"));
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LineBackgroundSpan.class).length);
    }

    // ---- markdown alerts ----

    @Test
    public void encode_markdownAlertDivProducesReplySpanAndBoldTitle() {
        Spanned spanned = encode("<div class=\"markdown-alert markdown-alert-note\">"
                + "<p class=\"markdown-alert-title\">Note</p>"
                + "<p>body text</p></div>");
        assertTrue(spanned.toString().contains("Note"));
        assertTrue(spanned.toString().contains("body text"));
        assertTrue(spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class).length >= 1);
        boolean hasBoldItalic = false;
        for (StyleSpan s : spanned.getSpans(0, spanned.length(), StyleSpan.class)) {
            if (s.getStyle() == Typeface.BOLD_ITALIC) {
                hasBoldItalic = true;
            }
        }
        assertTrue(hasBoldItalic);
    }

    // ---- stray end tags: null guards must not crash ----

    @Test
    public void encode_strayEndTagsDoNotCrash() {
        assertTrue(encode("</p>x").toString().contains("x"));
        assertTrue(encode("</h1>x").toString().contains("x"));
        assertTrue(encode("</li>x").toString().contains("x"));
        assertTrue(encode("<a>x</a></a>").toString().contains("x"));
        assertTrue(encode("<p>x</p></p>").toString().contains("x"));
    }

    // ---- empty / missing attributes ----

    @Test
    public void encode_emptyBoldProducesNoSpan() {
        Spanned spanned = encode("<b></b>");
        assertEquals("", spanned.toString());
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                StyleSpan.class).length);
    }

    @Test
    public void encode_styleWithoutCssDeclarationsProducesNoSpans() {
        Spanned spanned = encode("<span style=\"font-weight: bold\">x</span>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                ForegroundColorSpan.class).length);
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                BackgroundColorSpan.class).length);
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                StrikethroughSpan.class).length);
    }

    @Test
    public void encode_backgroundInvalidColorIsIgnored() {
        Spanned spanned = encode("<span style=\"background-color: notacolor\">x</span>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                BackgroundColorSpan.class).length);
    }

    @Test
    public void encode_textDecorationUnderlineIsNotStruckThrough() {
        Spanned spanned = encode("<span style=\"text-decoration: underline\">x</span>");
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                StrikethroughSpan.class).length);
    }

    @Test
    public void listItem_nullListUsesDefaults() throws Exception {
        // 孤儿 <li> 到不了这里：TagSoup 会给它补一个隐式 <ul>，
        // 所以 list==null 的半边只能反射直构造。
        Class<?> converter = Class.forName(
                "com.gh4a.utils.HtmlUtils$HtmlToSpannedConverter");
        Class<?> listClass = Class.forName(
                "com.gh4a.utils.HtmlUtils$HtmlToSpannedConverter$List");
        Class<?> itemClass = Class.forName(
                "com.gh4a.utils.HtmlUtils$HtmlToSpannedConverter$ListItem");
        java.lang.reflect.Constructor<?> ctor =
                itemClass.getDeclaredConstructor(listClass, org.xml.sax.Attributes.class);
        ctor.setAccessible(true);
        Object item = ctor.newInstance(null, org.mockito.Mockito.mock(
                org.xml.sax.Attributes.class));
        java.lang.reflect.Field ordered = itemClass.getDeclaredField("mOrdered");
        ordered.setAccessible(true);
        assertFalse((Boolean) ordered.get(item));
        java.lang.reflect.Field position = itemClass.getDeclaredField("mPosition");
        position.setAccessible(true);
        assertEquals(-1, position.getInt(item));
    }

    @Test
    public void encode_fontFaceWithoutColor() {
        Spanned spanned = encode("<font face=\"monospace\">f</font>");
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                TypefaceSpan.class).length);
        assertEquals(0, spanned.getSpans(0, spanned.length(),
                ForegroundColorSpan.class).length);
    }

    // ---- lists: value attribute, orphan li, invalid start ----

    @Test
    public void encode_orderedListInvalidStartFallsBackToOne() {
        Spanned spanned = encode("<ol start=\"abc\"><li>a</li></ol>");
        assertTrue(spanned.toString().contains("a"));
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class).length);
    }

    @Test
    public void encode_orderedListItemValueOverridesPosition() {
        Spanned spanned = encode(
                "<ol><li value=\"5\">a</li><li>b</li></ol>");
        assertTrue(spanned.toString().contains("a"));
        assertTrue(spanned.toString().contains("b"));
        assertEquals(2, spanned.getSpans(0, spanned.length(),
                LeadingMarginSpan.class).length);
    }

    @Test
    public void encode_orphanLiProducesBullet() {
        Spanned spanned = encode("<li>orphan</li>");
        assertTrue(spanned.toString().contains("orphan"));
        assertEquals(1, spanned.getSpans(0, spanned.length(),
                BulletSpan.class).length);
    }

    // ---- parseColor null guard (reflective: no caller passes null) ----

    @Test
    public void parseColor_nullReturnsNull() throws Exception {
        Class<?> converter = Class.forName(
                "com.gh4a.utils.HtmlUtils$HtmlToSpannedConverter");
        java.lang.reflect.Method m = converter.getDeclaredMethod(
                "parseColor", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, (String) null));
        assertNull(m.invoke(null, "notacolor"));
        assertNotNull(m.invoke(null, "#ff0000"));
    }

    // ---- processing instructions / unknown entities (TagSoup may or
    // may not deliver these; the test pins the text either way) ----

    @Test
    public void encode_processingInstructionAndUnknownEntity() {
        assertTrue(encode("<?pi data?>x").toString().contains("x"));
        assertTrue(encode("a &nosuchentity; b").toString().contains("a"));
    }
}
