package com.gh4a.model;

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

@RunWith(RobolectricTestRunner.class)
public class GitHubFeedTest {

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

    @Test
    public void parse_twoEntries() throws Exception {
        String xml = "<feed>"
                + "<entry><id>1</id><content>first</content></entry>"
                + "<entry><id>2</id><content>second</content></entry>"
                + "</feed>";
        GitHubFeed feed = persister().read(GitHubFeed.class, xml);
        assertEquals(2, feed.feed.size());
        assertEquals("1", feed.feed.get(0).getId());
        assertEquals("first", feed.feed.get(0).getPreview());
        assertEquals("2", feed.feed.get(1).getId());
        assertEquals("second", feed.feed.get(1).getPreview());
    }

    @Test
    public void parse_emptyFeed_yieldsNoEntries() throws Exception {
        // T-121：@ElementList required=false，零条目 feed 解析成功、不再 abort 整篇；
        // 无 entry 时字段保持 null（由 SingleFactory.loadFeed 兜底为空列表）。
        GitHubFeed feed = persister().read(GitHubFeed.class, "<feed></feed>");
        assertTrue(feed.feed == null || feed.feed.isEmpty());
    }

    @Test
    public void parse_unknownElementsIgnored() throws Exception {
        String xml = "<feed><weird>ignored</weird>"
                + "<entry><id>1</id><unknown>u</unknown><content>c</content></entry>"
                + "</feed>";
        GitHubFeed feed = persister().read(GitHubFeed.class, xml);
        assertEquals(1, feed.feed.size());
        assertEquals("1", feed.feed.get(0).getId());
    }

    @Test
    public void parse_entryWithoutContent_survives() throws Exception {
        // Regression cover for #233: a missing content element must not fail
        // the whole feed parse.
        String xml = "<feed><entry><id>1</id></entry></feed>";
        GitHubFeed feed = persister().read(GitHubFeed.class, xml);
        assertEquals(1, feed.feed.size());
        assertNull(feed.feed.get(0).getContent());
        assertNull(feed.feed.get(0).getPreview());
    }
}
