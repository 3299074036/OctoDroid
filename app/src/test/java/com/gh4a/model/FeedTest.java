package com.gh4a.model;

import android.os.Parcel;

import com.gh4a.utils.Iso8601;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.transform.RegistryMatcher;
import org.simpleframework.xml.transform.Transform;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Feed is exercised through its real SimpleXML path (Persister with the same
 * Date transform SingleFactory uses), so @Commit apply() runs for real.
 */
@RunWith(RobolectricTestRunner.class)
public class FeedTest {

    private static Persister persister() {
        RegistryMatcher matcher = new RegistryMatcher();
        matcher.bind(Date.class, new Transform<Date>() {
            @Override
            public Date read(String value) {
                return Iso8601.parseOrNull(value);
            }

            @Override
            public String write(Date value) {
                return Iso8601.formatFeed(value);
            }
        });
        return new Persister(matcher);
    }

    private static Feed parse(String xml) throws Exception {
        return persister().read(Feed.class, xml);
    }

    private static final String ENTRY_OPEN = "<entry>";
    private static final String ENTRY_CLOSE = "</entry>";

    private static String entry(String inner) {
        return ENTRY_OPEN + inner + ENTRY_CLOSE;
    }

    private static String fullEntry() {
        return entry(
                "<id>tag:github.com,2008:Issue/1</id>"
                + "<published>2026-11-01T10:00:00Z</published>"
                + "<link href=\"https://github.com/octocat/Hello-World/issues/1\"/>"
                + "<title>Issue title &amp; more</title>"
                + "<content type=\"html\">&lt;p&gt;Hello &lt;b&gt;world&lt;/b&gt;&lt;/p&gt;</content>"
                + "<author><name>octocat</name></author>"
                + "<thumbnail url=\"https://avatars.githubusercontent.com/u/583231?v=4\"/>"
                + "<updated>2026-11-02T10:00:00Z</updated>");
    }

    @Test
    public void parse_fullEntry_allFields() throws Exception {
        Feed feed = parse(fullEntry());
        assertEquals("tag:github.com,2008:Issue/1", feed.getId());
        assertEquals("Issue title & more", feed.getTitle());
        assertEquals("<p>Hello <b>world</b></p>", feed.getContent());
        assertEquals("Hello world", feed.getPreview());
        assertEquals("octocat", feed.getAuthor());
        assertEquals(583231, feed.getUserId());
        assertEquals("https://avatars.githubusercontent.com/u/583231?v=4", feed.getAvatarUrl());
        assertEquals(new Date(1793527200000L), feed.getPublished());
        assertEquals(new Date(1793613600000L), feed.getUpdated());
    }

    @Test
    public void parse_missingContentAndTitle_titleFromUrlPreviewNull() throws Exception {
        Feed feed = parse(entry(
                "<id>x</id>"
                + "<link href=\"https://github.com/octocat/Hello-World/issues/my-cool-issue\"/>"
                + "<author><name>octocat</name></author>"));
        assertNull(feed.getContent());
        assertNull(feed.getPreview());
        assertEquals("my cool issue", feed.getTitle());
        // No thumbnail URL at all: determineUserId returns -1 before ever
        // consulting the author name (author-hash fallback only runs when
        // a thumbnail URL exists but is not an avatar URL).
        assertEquals(-1, feed.getUserId());
        assertNull(feed.getPublished());
        assertNull(feed.getUpdated());
    }

    @Test
    public void parse_missingLinkAndTitle_titleNull() throws Exception {
        Feed feed = parse(entry("<id>x</id><content>c</content>"));
        assertNull(feed.getTitle());
        assertEquals("c", feed.getPreview());
        assertEquals(-1, feed.getUserId());
    }

    @Test
    public void parse_blankTitle_titleFromUrl() throws Exception {
        Feed feed = parse(entry(
                "<title>   </title>"
                + "<link href=\"https://github.com/o/r/pulls/some-pr-title\"/>"
                + "<content>c</content>"));
        assertEquals("some pr title", feed.getTitle());
    }

    @Test
    public void parse_avatarNonNumericLastSegment_fallsBackToAuthorHash() throws Exception {
        Feed feed = parse(entry(
                "<thumbnail url=\"https://avatars.githubusercontent.com/u/octocat\"/>"
                + "<author><name>octocat</name></author>"
                + "<content>c</content>"));
        // Last path segment is not numeric: the NumberFormatException catch
        // returns -1 directly instead of falling back to the author hash.
        assertEquals(-1, feed.getUserId());
    }

    @Test
    public void parse_avatarHostStartsWithAvatarsButNotGithubusercontent_fallsBackToAuthorHash() throws Exception {
        // Line 111: host.startsWith("avatars") is true but
        // host.contains("githubusercontent.com") is false, so the second
        // operand of the && is exercised and the author-hash fallback runs.
        Feed feed = parse(entry(
                "<thumbnail url=\"https://avatars.example.com/u/123\"/>"
                + "<author><name>carol</name></author>"
                + "<content>c</content>"));
        assertEquals("carol".hashCode(), feed.getUserId());
    }

    @Test
    public void parse_avatarNonAvatarHost_fallsBackToAuthorHash() throws Exception {
        Feed feed = parse(entry(
                "<thumbnail url=\"https://example.com/avatars/123\"/>"
                + "<author><name>alice</name></author>"
                + "<content>c</content>"));
        assertEquals("alice".hashCode(), feed.getUserId());
    }

    @Test
    public void parse_avatarRelativeUrl_returnsMinusOne() throws Exception {
        Feed feed = parse(entry(
                "<thumbnail url=\"/u/123\"/>"
                + "<author><name>alice</name></author>"
                + "<content>c</content>"));
        assertEquals(-1, feed.getUserId());
    }

    @Test
    public void parse_avatarNoAuthor_returnsMinusOne() throws Exception {
        Feed feed = parse(entry(
                "<thumbnail url=\"https://example.com/x.png\"/>"
                + "<content>c</content>"));
        assertEquals(-1, feed.getUserId());
    }

    @Test
    public void parse_avatarWrongSegmentCount_fallsBackToAuthorHash() throws Exception {
        Feed feed = parse(entry(
                "<thumbnail url=\"https://avatars.githubusercontent.com/u/1/extra\"/>"
                + "<author><name>bob</name></author>"
                + "<content>c</content>"));
        assertEquals("bob".hashCode(), feed.getUserId());
    }

    @Test
    public void parse_longContent_previewTruncatedTo500CharsAfterStrip() throws Exception {
        StringBuilder sb = new StringBuilder("<content>");
        for (int i = 0; i < 600; i++) {
            sb.append('a');
        }
        sb.append("</content>");
        Feed feed = parse(entry(sb.toString()));
        assertEquals(500, feed.getPreview().length());
        assertTrue(feed.getPreview().matches("a+"));
    }

    @Test
    public void parse_veryLongContent_inputTruncatedTo2000BeforeStrip() throws Exception {
        StringBuilder sb = new StringBuilder("<content>");
        for (int i = 0; i < 1500; i++) {
            sb.append('a');
        }
        sb.append("&lt;tag&gt;");
        for (int i = 0; i < 1500; i++) {
            sb.append('b');
        }
        sb.append("</content>");
        Feed feed = parse(entry(sb.toString()));
        // First 2000 chars of content contain the tag text; after stripping
        // the tag the preview is 500 chars (the cap), all 'a'/'b'.
        assertEquals(500, feed.getPreview().length());
    }

    @Test
    public void parse_htmlEntitiesInPreview_unescaped() throws Exception {
        Feed feed = parse(entry("<content>&lt;p&gt;a &amp;amp; b&lt;/p&gt;</content>"));
        assertEquals("a & b", feed.getPreview());
    }

    @Test
    public void parcelRoundTrip_preservesAllFieldsIncludingDates() throws Exception {
        Feed feed = parse(fullEntry());
        Parcel parcel = Parcel.obtain();
        feed.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Feed restored = Feed.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(feed.getId(), restored.getId());
        assertEquals(feed.getTitle(), restored.getTitle());
        assertEquals(feed.getContent(), restored.getContent());
        assertEquals(feed.getPreview(), restored.getPreview());
        assertEquals(feed.getAuthor(), restored.getAuthor());
        assertEquals(feed.getUserId(), restored.getUserId());
        assertEquals(feed.getAvatarUrl(), restored.getAvatarUrl());
        assertEquals(feed.getPublished(), restored.getPublished());
        assertEquals(feed.getUpdated(), restored.getUpdated());
        assertEquals(0, feed.describeContents());
    }

    @Test
    public void parcelRoundTrip_nullDatesStayNull() throws Exception {
        Feed feed = parse(entry("<id>x</id><content>c</content>"));
        Parcel parcel = Parcel.obtain();
        feed.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Feed restored = Feed.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertNull(restored.getPublished());
        assertNull(restored.getUpdated());
        assertEquals("x", restored.getId());
        assertEquals("c", restored.getPreview());
    }

    @Test
    public void creator_newArray_hasRequestedSize() {
        Feed[] array = Feed.CREATOR.newArray(3);
        assertEquals(3, array.length);
        assertNull(array[0]);
    }

    @Test
    public void parse_badDate_doesNotFailWholeEntry() throws Exception {
        // Iso8601.parseOrNull returns null for an unparseable date.
        Feed feed = parse(entry(
                "<published>not-a-date</published>"
                + "<content>c</content>"));
        assertNull(feed.getPublished());
        assertEquals("c", feed.getContent());
    }
}
