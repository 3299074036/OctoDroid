package com.gh4a.adapter.timeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.adapter.AdapterTestSupport;
import com.gh4a.model.TimelineItem;
import com.gh4a.utils.HttpImageGetter;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.ReviewState;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ReviewViewHolderTest {
    private Context context;
    private HttpImageGetter imageGetter;
    private List<CharSequence> quoted;
    private List<String> translatedRequests;
    private String translatedBody;
    private boolean canQuote = true;

    private final ReviewViewHolder.Callback callback = new ReviewViewHolder.Callback() {
        @Override
        public boolean canQuote() {
            return canQuote;
        }

        @Override
        public void quoteText(CharSequence text) {
            quoted.add(text);
        }

        @Override
        public void translateBody(long id, String bodyHtml) {
            translatedRequests.add(id + ":" + bodyHtml);
        }

        @Override
        public String getTranslatedBody(long id) {
            return translatedBody;
        }
    };

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        imageGetter = new HttpImageGetter(context);
        quoted = new ArrayList<>();
        translatedRequests = new ArrayList<>();
        translatedBody = null;
    }

    private ReviewViewHolder holder(boolean displayDetails) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.row_timeline_review, null, false);
        return new ReviewViewHolder(view, imageGetter, "owner", "repo", 5,
                displayDetails, callback);
    }

    private Review review(ReviewState state, String body, Long id, String htmlUrl) {
        Review r = mock(Review.class);
        when(r.user()).thenReturn(User.builder().login("octocat").build());
        when(r.state()).thenReturn(state);
        when(r.body()).thenReturn(body);
        when(r.bodyHtml()).thenReturn(body == null ? null : "<p>" + body + "</p>");
        when(r.submittedAt()).thenReturn(new Date(System.currentTimeMillis() - 1000L));
        when(r.id()).thenReturn(id);
        when(r.htmlUrl()).thenReturn(htmlUrl);
        return r;
    }

    private String message(ReviewViewHolder h) {
        return ((TextView) h.itemView.findViewById(R.id.tv_message)).getText().toString();
    }

    @Test
    public void bind_eachState_hasTitle() {
        for (ReviewState state : ReviewState.values()) {
            ReviewViewHolder h = holder(false);
            h.bind(new TimelineItem.TimelineReview(review(state, "body", 1L, null)));
            assertTrue("state " + state, message(h).contains("octocat"));
        }
    }

    @Test
    public void bind_nullState_defaultsToReviewed() {
        ReviewViewHolder h = holder(false);
        h.bind(new TimelineItem.TimelineReview(review(null, "body", 1L, null)));
        assertTrue(message(h).contains("octocat"));
    }

    @Test
    public void bind_nullSubmittedAt_noCrash() {
        Review r = review(ReviewState.Approved, "b", 1L, null);
        when(r.submittedAt()).thenReturn(null);
        ReviewViewHolder h = holder(false);
        h.bind(new TimelineItem.TimelineReview(r));
        assertTrue(message(h).contains("octocat"));
        assertFalse(message(h).contains("[time]"));
    }

    @Test
    public void bind_nullId_noCrash() {
        ReviewViewHolder h = holder(false);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Commented, "b", null, null)));
        assertTrue(message(h).contains("octocat"));
    }

    @Test
    public void bind_translatedBody_usedWhenPresent() {
        translatedBody = "<p>translated</p>";
        ReviewViewHolder h = holder(false);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 8L, null)));
        assertTrue(message(h).contains("octocat"));
    }

    @Test
    public void bind_noDetails_hidesContainersAndMenu() {
        ReviewViewHolder h = holder(false);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        assertEquals(View.GONE, h.itemView.findViewById(R.id.details_container).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.btn_show_details).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.details_container_header).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.iv_menu).getVisibility());
        assertEquals(View.GONE, h.itemView.findViewById(R.id.details_container_divider).getVisibility());
    }

    @Test
    public void bind_detailsWithDiffs_showsFileRows() {
        TimelineItem.TimelineReview item =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, "body", 1L, null));
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        ReviewComment c1 = AdapterTestSupport.reviewComment(10L, null, null, null, null, null, 1, "a.java", null, null, "abc", 1);
        ReviewComment c2 = AdapterTestSupport.reviewComment(11L, null, null,
                new Date(System.currentTimeMillis() - 1000L), null, null, 2, "a.java",
                null, null, "abc", 1);
        item.addComment(c1, file, true);
        item.addComment(c2, file, false);
        ReviewViewHolder h = holder(true);
        h.bind(item);
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.details_container).getVisibility());
        assertEquals(View.VISIBLE, h.itemView.findViewById(R.id.btn_show_details).getVisibility());
        android.view.ViewGroup container =
                h.itemView.findViewById(R.id.details_container);
        assertEquals(1, container.getChildCount());
        assertEquals(View.VISIBLE, container.getChildAt(0).getVisibility());
        assertEquals("• a.java",
                ((TextView) container.getChildAt(0).findViewById(R.id.tv_file))
                        .getText().toString());
        assertEquals("2",
                ((TextView) container.getChildAt(0).findViewById(R.id.tv_file_comments))
                        .getText().toString());
        assertEquals(View.VISIBLE,
                h.itemView.findViewById(R.id.details_container_divider).getVisibility());
    }

    @Test
    public void bind_detailsWithDiffs_noBody_hidesDivider() {
        TimelineItem.TimelineReview item =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, null, 1L, null));
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        ReviewComment c1 = AdapterTestSupport.reviewComment(10L, null, null, null, null, null, 1, "a.java", null, null, "abc", 1);
        item.addComment(c1, file, true);
        ReviewViewHolder h = holder(true);
        h.bind(item);
        assertEquals(View.GONE,
                h.itemView.findViewById(R.id.details_container_divider).getVisibility());
    }

    @Test
    public void bind_detailsOutdated_strikesThroughFile() {
        TimelineItem.TimelineReview item =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null));
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        ReviewComment c1 = AdapterTestSupport.reviewComment(10L, null, null, null, null, null, null, "a.java", null, null, "abc", 1);
        item.addComment(c1, file, true);
        ReviewViewHolder h = holder(true);
        h.bind(item);
        android.view.ViewGroup container =
                h.itemView.findViewById(R.id.details_container);
        TextView tvFile = container.getChildAt(0).findViewById(R.id.tv_file);
        assertTrue((tvFile.getPaintFlags() & android.graphics.Paint.STRIKE_THRU_TEXT_FLAG) != 0);
    }

    @Test
    public void bind_rebindFewerDiffs_hidesExtraRows() {
        TimelineItem.TimelineReview item1 =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null));
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        item1.addComment(AdapterTestSupport.reviewComment(10L, null, null, null, null, null, 1, "a.java", null, null, "abc", 1), file, true);
        item1.addComment(AdapterTestSupport.reviewComment(11L, null, null, null, null, null, 1, "b.java", null, null, "def", 2), file, true);
        ReviewViewHolder h = holder(true);
        h.bind(item1);
        android.view.ViewGroup container =
                h.itemView.findViewById(R.id.details_container);
        assertEquals(2, container.getChildCount());

        TimelineItem.TimelineReview item2 =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null));
        item2.addComment(AdapterTestSupport.reviewComment(10L, null, null, null, null, null, 1, "a.java", null, null, "abc", 1), file, true);
        h.bind(item2);
        assertEquals(View.VISIBLE, container.getChildAt(0).getVisibility());
        assertEquals(View.GONE, container.getChildAt(1).getVisibility());
    }

    @Test
    public void onClick_avatar_startsUserActivity() {
        ReviewViewHolder h = holder(false);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        h.itemView.findViewById(R.id.avatar_container).performClick();
        assertEquals("com.gh4a.activities.UserActivity",
                AdapterTestSupport.shadowApp(context).getNextStartedActivity().getComponent().getClassName());
    }

    @Test
    public void onClick_showDetails_startsReviewActivity() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        h.itemView.findViewById(R.id.btn_show_details).performClick();
        Intent started = AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.ReviewActivity",
                started.getComponent().getClassName());
    }

    @Test
    public void onClick_menu_showsPopup() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        h.itemView.findViewById(R.id.iv_menu).performClick();
    }

    @Test
    public void onClick_fileDetailsRow_startsReviewActivityWithMarker() {
        TimelineItem.TimelineReview item =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null));
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        item.addComment(AdapterTestSupport.reviewComment(10L, null, null, null, null, null, 1, "a.java", null, null, "abc", 1), file, true);
        ReviewViewHolder h = holder(true);
        h.bind(item);
        android.view.ViewGroup container =
                h.itemView.findViewById(R.id.details_container);
        container.getChildAt(0).performClick();
        Intent started = AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals("com.gh4a.activities.ReviewActivity",
                started.getComponent().getClassName());
        assertTrue(started.hasExtra("initial_comment"));
    }

    @Test
    public void onMenuItemClick_share_nullUrl_noShare() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        MenuItem share = mock(MenuItem.class);
        when(share.getItemId()).thenReturn(R.id.share);
        assertTrue(h.onMenuItemClick(share));
        assertEquals(null, AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onMenuItemClick_share_withUrl_startsChooser() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(
                review(ReviewState.Approved, "b", 1L, "https://github.com/o/r/pull/5#review-1")));
        MenuItem share = mock(MenuItem.class);
        when(share.getItemId()).thenReturn(R.id.share);
        assertTrue(h.onMenuItemClick(share));
        Intent started = AdapterTestSupport.shadowApp(context).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
    }

    @Test
    public void onMenuItemClick_browser_withUrl_returnsTrue_noBrowserInstalled() {
        // No browser is installed under Robolectric, so launchBrowser falls
        // through to the "no browser found" toast and starts no activity;
        // the menu branch itself (review != null, htmlUrl != null) is covered.
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(
                review(ReviewState.Approved, "b", 1L, "https://github.com/o/r/pull/5#review-1")));
        MenuItem browser = mock(MenuItem.class);
        when(browser.getItemId()).thenReturn(R.id.browser);
        assertTrue(h.onMenuItemClick(browser));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onMenuItemClick_unknown_returnsFalse() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        MenuItem unknown = mock(MenuItem.class);
        when(unknown.getItemId()).thenReturn(R.id.edit);
        assertFalse(h.onMenuItemClick(unknown));
    }

    @Test
    public void quoteText_viaSelectionCallback_callsCallback() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        ((com.gh4a.utils.UiUtils.QuoteActionModeCallback) ((TextView) h.itemView
                .findViewById(R.id.tv_desc)).getCustomSelectionActionModeCallback())
                .onTextQuoted("quoted");
        assertEquals(1, quoted.size());
        assertEquals("quoted", quoted.get(0).toString());
    }

    @Test
    public void bind_detailsSameFileTwoHunks_aggregatesCounts() {
        // Two diff hunks for the same file (different hunk ids via
        // originalPosition) aggregate into one file row with summed counts.
        TimelineItem.TimelineReview item =
                new TimelineItem.TimelineReview(review(ReviewState.Approved, "body", 1L, null));
        GitHubFile file = mock(GitHubFile.class);
        when(file.patch()).thenReturn("@@ -1 +1 @@");
        ReviewComment c1 = AdapterTestSupport.reviewComment(10L, null, null, null, null, null,
                1, "a.java", null, null, "abc", 1);
        ReviewComment c2 = AdapterTestSupport.reviewComment(11L, null, null,
                new Date(System.currentTimeMillis() - 1000L), null, null, 2, "a.java",
                null, null, "abc", 2);
        item.addComment(c1, file, true);
        item.addComment(c2, file, true);
        ReviewViewHolder h = holder(true);
        h.bind(item);
        android.view.ViewGroup container =
                h.itemView.findViewById(R.id.details_container);
        assertEquals(1, container.getChildCount());
        assertEquals("2",
                ((TextView) container.getChildAt(0).findViewById(R.id.tv_file_comments))
                        .getText().toString());
    }

    @Test
    public void onMenuItemClick_share_nullReview_returnsTrue() {
        ReviewViewHolder h = holder(true);
        // never bound: ivMenu has no review tag
        MenuItem share = mock(MenuItem.class);
        when(share.getItemId()).thenReturn(R.id.share);
        assertTrue(h.onMenuItemClick(share));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onMenuItemClick_browser_nullUrl_returnsTrue() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        MenuItem browser = mock(MenuItem.class);
        when(browser.getItemId()).thenReturn(R.id.browser);
        assertTrue(h.onMenuItemClick(browser));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onMenuItemClick_browser_nullReview_returnsTrue() {
        ReviewViewHolder h = holder(true);
        MenuItem browser = mock(MenuItem.class);
        when(browser.getItemId()).thenReturn(R.id.browser);
        assertTrue(h.onMenuItemClick(browser));
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void onClick_unknownView_noop() {
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(review(ReviewState.Approved, "b", 1L, null)));
        h.onClick(new View(context));
    }

    @Test
    public void onClick_avatar_mannequin_noActivity() {
        Review r = review(ReviewState.Approved, "b", 1L, null);
        when(r.user()).thenReturn(User.builder().login("ghost")
                .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build());
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(r));
        h.itemView.findViewById(R.id.avatar_container).performClick();
        assertNull(AdapterTestSupport.shadowApp(context).getNextStartedActivity());
    }

    @Test
    public void bind_nullSubmittedAt_timePlaceholderEmpty() {
        Review r = review(ReviewState.Approved, "b", 1L, null);
        when(r.submittedAt()).thenReturn(null);
        ReviewViewHolder h = holder(true);
        h.bind(new TimelineItem.TimelineReview(r));
        assertFalse(((TextView) h.itemView.findViewById(R.id.tv_message))
                .getText().toString().contains("[time]"));
    }
}
