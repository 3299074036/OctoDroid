package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.model.Feed;

@RunWith(RobolectricTestRunner.class)
public class WikiActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private Feed feed(String title, String content) {
        // Feed 无 setter（字段由解析器填）：反射设私有字段。
        Feed f = new Feed();
        try {
            java.lang.reflect.Field titleField = Feed.class.getDeclaredField("title");
            titleField.setAccessible(true);
            titleField.set(f, title);
            java.lang.reflect.Field contentField = Feed.class.getDeclaredField("content");
            contentField.setAccessible(true);
            contentField.set(f, content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return f;
    }

    @Test
    public void makeIntent_carriesOwnerRepoAndCompressedFeed() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello",
                feed("Home", "<p>hi</p>"));
        assertEquals(WikiActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
        assertTrue(intent.hasExtra("page_feed"));
    }

    @Test
    public void makeIntent_nullFeed_stillCarriesOwnerRepo() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
    }

    @Test
    public void launches_withFeed_titleIsFeedTitle() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello",
                feed("Home Page", "<p>hi</p>"));
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("Home Page",
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void launches_withoutFeed_titleFallsBackToRepoName() {
        // BUG-13：feed 字节缺失时 readCompressedValueFromBundle 返回 null，
        // 标题回落仓库名，不 NPE。
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("hello",
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void launches_withFeedEmptyTitle_titleFallsBackToRepoName() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello",
                feed("", "<p>hi</p>"));
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("hello",
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void generateHtml_withoutFeed_returnsSanitizedEmptyPage() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            String html = activity.generateHtml("light", false);
            assertNotNull(html);
            assertTrue(html.contains("sanitizeHtml"));
            assertFalse(html.contains("<h2>"));
        }
    }

    @Test
    public void generateHtml_withFeedAndTitleHeader_containsContentAndTitle() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello",
                feed("Home", "<p>hi</p>"));
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            String html = activity.generateHtml("light", true);
            // getDocumentTitle() 是格式化串 "Wiki page Home in octocat/hello"
            assertTrue(html.contains("<h2>Wiki page Home in octocat/hello</h2>"));
            // 内容进 JS 单引号字符串：</ 被转义为 <\/，原始 HTML 不直接拼进页面
            assertTrue(html.contains("<p>hi<\\/p>"));
            assertFalse(html.contains("<p>hi</p>"));
        }
    }

    @Test
    public void getDocumentTitle_withoutFeed_returnsRepoName() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("hello", activity.getDocumentTitle());
        }
    }

    @Test
    public void getDocumentTitle_withFeed_returnsFormattedTitle() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello",
                feed("Home", "<p>hi</p>"));
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.wiki_print_document_title,
                    "Home", "octocat", "hello"), activity.getDocumentTitle());
        }
    }

    @Test
    public void canSwipeToRefresh_isFalse() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertFalse(activity.canSwipeToRefresh());
        }
    }

    @Test
    public void navigateUp_returnsWikiListIntent() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(WikiListActivity.class.getName(),
                    up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("owner"));
            assertEquals("hello", up.getStringExtra("repo"));
        }
    }

    // ---- A2 第二轮补缺口：WikiActivity ----

    @Test
    public void getActionBarTitle_nullFeed_fallsBackToRepoName() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("hello", activity.getActionBarTitle());
        }
    }

    @Test
    public void getDocumentTitle_nullFeed_fallsBackToRepoName() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", "hello", null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertTrue(activity.getDocumentTitle().contains("hello"));
        }
    }


    // ---- A2 第三轮补缺口：WikiActivity ----

    @Test
    public void getActionBarTitle_nullRepo_returnsEmpty() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", null, null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("", activity.getActionBarTitle());
        }
    }

    @Test
    public void getDocumentTitle_nullRepo_returnsEmpty() {
        Intent intent = WikiActivity.makeIntent(context(), "octocat", null, null);
        try (ActivityController<WikiActivity> c =
                Robolectric.buildActivity(WikiActivity.class, intent)) {
            WikiActivity activity = c.setup().get();
            assertEquals("", activity.getDocumentTitle());
        }
    }

}
