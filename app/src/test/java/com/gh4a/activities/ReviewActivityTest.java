package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import com.gh4a.R;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.Review;
import androidx.appcompat.view.menu.MenuBuilder;
import org.robolectric.Shadows;

@RunWith(RobolectricTestRunner.class)
public class ReviewActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static Review review(Long id, String htmlUrl) {
        Review r = mock(Review.class);
        when(r.id()).thenReturn(id);
        when(r.htmlUrl()).thenReturn(htmlUrl);
        return r;
    }

    private ActivityController<ReviewActivity> launch(Review r) {
        Intent intent = ReviewActivity.makeIntent(context(), "octocat", "hello",
                42, r, null);
        return Robolectric.buildActivity(ReviewActivity.class, intent);
    }

    @Test
    public void makeIntent_carriesAllExtras() {
        Review r = review(7L, "https://github.com/o/r/pull/42#review-7");
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(9L);
        Intent intent = ReviewActivity.makeIntent(context(), "octocat", "hello",
                42, r, marker);
        assertEquals(ReviewActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("repo_owner"));
        assertEquals("hello", intent.getStringExtra("repo_name"));
        assertEquals(42, intent.getIntExtra("issue_number", -1));
        assertNotNull(intent.getParcelableExtra("review"));
        assertNotNull(intent.getParcelableExtra("initial_comment"));
    }

    @Test
    public void makeIntent_nullReviewAndMarker_allowed() {
        Intent intent = ReviewActivity.makeIntent(context(), "octocat", "hello",
                1, null, null);
        assertNull(intent.getParcelableExtra("review"));
        assertNull(intent.getParcelableExtra("initial_comment"));
    }

    @Test
    public void launches_titleIsReviewNumber() {
        try (ActivityController<ReviewActivity> c = launch(review(7L, null))) {
            ReviewActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.review_title_format, 42),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isReviewFragment() {
        try (ActivityController<ReviewActivity> c = launch(review(7L, null))) {
            ReviewActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.ReviewFragment);
        }
    }

    @Test
    public void onInitExtras_consumesInitialCommentIntoField() throws Exception {
        // onInitExtras 把 initial_comment 取进 mInitialComment 字段。
        // 注：extras.remove() 的效果在 Robolectric 下经 getIntent() 不可观测
        //（getExtras() 每次返回副本），故只断言字段本身被正确消费。
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(9L);
        Intent intent = ReviewActivity.makeIntent(context(), "octocat", "hello",
                42, review(7L, null), marker);
        try (ActivityController<ReviewActivity> c =
                Robolectric.buildActivity(ReviewActivity.class, intent)) {
            ReviewActivity activity = c.setup().get();
            java.lang.reflect.Field f =
                    ReviewActivity.class.getDeclaredField("mInitialComment");
            f.setAccessible(true);
            IntentUtils.InitialCommentMarker stored =
                    (IntentUtils.InitialCommentMarker) f.get(activity);
            assertNotNull(stored);
            assertEquals(9L, stored.commentId);
        }
    }

    @Test
    public void intentExtras_areCopiesUnderRobolectric() {
        // 探针：解释上条测试为何不断言 remove 效果——Robolectric 下
        // getExtras() 两次调用返回不同 Bundle 对象。
        Intent intent = ReviewActivity.makeIntent(context(), "octocat", "hello",
                42, review(7L, null), null);
        assertTrue(intent.getExtras() != intent.getExtras());
    }

    @Test
    public void onOptionsItemSelected_shareAndBrowser_withNullHtmlUrl_returnTrueWithoutCrash() {
        try (ActivityController<ReviewActivity> c = launch(review(7L, null))) {
            ReviewActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
        }
    }

    @Test
    public void onOptionsItemSelected_browser_withHtmlUrl_noBrowserShowsToast() {
        try (ActivityController<ReviewActivity> c =
                launch(review(7L, "https://github.com/o/r/pull/42"))) {
            ReviewActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
            assertEquals(activity.getString(R.string.no_browser_found),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void navigateUp_withReviewId_carriesMarker() {
        try (ActivityController<ReviewActivity> c = launch(review(7L, null))) {
            ReviewActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(PullRequestActivity.class.getName(),
                    up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("owner"));
            assertEquals("hello", up.getStringExtra("repo"));
            assertEquals(42, up.getIntExtra("number", -1));
            IntentUtils.InitialCommentMarker marker =
                    up.getParcelableExtra("initial_comment");
            assertNotNull(marker);
            assertEquals(7L, marker.commentId);
        }
    }

    @Test
    public void launches_withNullReview_crashesInReviewFragment() {
        // A2-1：makeIntent 允许 review 为 null（onOptionsItemSelected 也
        // 判空），但 ReviewFragment.onCreateDataSingle 在
        // ReviewFragment.java:189 直接取 mReview.id()，建页即 NPE 崩溃。
        ActivityController<ReviewActivity> c = launch(null);
        try {
            c.setup();
            throw new AssertionError("expected NullPointerException");
        } catch (NullPointerException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("mReview"));
        } finally {
            try {
                c.destroy();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Test
    public void launches_withNullReviewId_crashesInReviewFragment() {
        // A2-1 同款：Review.id() 为可空 Long，null 时在 getReview(..., long)
        // 拆箱处 NPE（同一行 ReviewFragment.java:189）。
        ActivityController<ReviewActivity> c = launch(review(null, null));
        try {
            c.setup();
            throw new AssertionError("expected NullPointerException");
        } catch (NullPointerException expected) {
            assertTrue(expected.getMessage() != null
                    && expected.getMessage().contains("Review.id()"));
        } finally {
            try {
                c.destroy();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Test
    public void onOptionsItemSelected_shareWithUrl_showsChooser() {
        try (ActivityController<ReviewActivity> c =
                launch(review(7L, "https://github.com/o/r/pull/42#review-7"))) {
            ReviewActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.share, 0, "Share");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
            assertEquals(Intent.ACTION_CHOOSER,
                    Shadows.shadowOf(activity).getNextStartedActivity().getAction());
        }
    }

    // ---- A2 第二轮补缺口：ReviewActivity ----

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<ReviewActivity> c = launch(review(7L, null))) {
            ReviewActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

}
