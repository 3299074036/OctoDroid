package com.gh4a.resolver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.ReviewActivity;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import io.reactivex.Single;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PullRequestReviewCommentLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/pull/1#discussion_r11");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    /** Review comment in hunk (commit abc, path f.java, position 3). */
    private ReviewComment reviewComment(Long id, Long reviewId) {
        ReviewComment c = mock(ReviewComment.class);
        when(c.id()).thenReturn(id);
        when(c.originalCommitId()).thenReturn("abc");
        when(c.path()).thenReturn("f.java");
        when(c.originalPosition()).thenReturn(3);
        when(c.pullRequestReviewId()).thenReturn(reviewId);
        when(c.createdAt()).thenReturn(null);
        return c;
    }

    private static class Fixture {
        final PullRequestReviewService reviewService = mock(PullRequestReviewService.class);
        final PullRequestReviewCommentService commentService =
                mock(PullRequestReviewCommentService.class);

        void stubComments(ReviewComment... comments) {
            when(commentService.getPullRequestComments(anyString(), anyString(), anyLong(), eq(1L)))
                    .thenReturn(Single.just(Response.success(
                            Page.<ReviewComment>builder().items(Arrays.asList(comments)).build())));
        }

        void stubReview(long reviewId, Review review) {
            when(reviewService.getReview(anyString(), anyString(), anyLong(), eq(reviewId)))
                    .thenReturn(Single.just(Response.success(review)));
        }

        void install(MockedStatic<ServiceFactory> mocked) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestReviewService.class), eq(false)))
                    .thenReturn(reviewService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewCommentService.class), eq(false)))
                    .thenReturn(commentService);
        }
    }

    @Test
    public void constructor_storesFields() {
        FragmentActivity activity = activity();
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(11L);
        PullRequestReviewCommentLoadTask t = new PullRequestReviewCommentLoadTask(
                activity, URL, "octo", "cat", 9, marker);
        assertEquals("octo", t.mRepoOwner);
        assertEquals("cat", t.mRepoName);
        assertEquals(9, t.mPullRequestNumber);
        assertEquals(marker, t.mMarker);
    }

    @Test
    public void load_matchingComment_opensReviewActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        // first comment is the hunk's initial comment (review 77); the
        // marker matches the second comment in the same hunk.
        f.stubComments(reviewComment(10L, 77L), reviewComment(11L, 77L));
        Review review = mock(Review.class);
        f.stubReview(77L, review);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = PullRequestReviewCommentLoadTask.load(ctx, "octo", "cat", 1,
                    new IntentUtils.InitialCommentMarker(11L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(ReviewActivity.class.getName(),
                    result.get().getComponent().getClassName());
            verify(f.reviewService).getReview("octo", "cat", 1L, 77L);
        }
    }

    @Test
    public void load_noMatchingComment_returnsEmpty() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(10L, 77L));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = PullRequestReviewCommentLoadTask.load(ctx, "octo", "cat", 1,
                    new IntentUtils.InitialCommentMarker(99L)).blockingGet();
            assertFalse(result.isPresent());
        }
    }

    @Test
    public void load_commentWithNullId_isSkipped() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(null, 77L));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(PullRequestReviewCommentLoadTask.load(ctx, "octo", "cat", 1,
                    new IntentUtils.InitialCommentMarker(11L)).blockingGet().isPresent());
        }
    }

    @Test
    public void load_initialCommentReviewIdNull_usesZero() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(10L, null), reviewComment(11L, null));
        Review review = mock(Review.class);
        f.stubReview(0L, review);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = PullRequestReviewCommentLoadTask.load(ctx, "octo", "cat", 1,
                    new IntentUtils.InitialCommentMarker(11L)).blockingGet();
            assertTrue(result.isPresent());
            verify(f.reviewService).getReview("octo", "cat", 1L, 0L);
        }
    }

    @Test
    public void load_noComments_returnsEmpty() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        when(f.commentService.getPullRequestComments(anyString(), anyString(), anyLong(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<ReviewComment>builder().items(Collections.emptyList()).build())));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(PullRequestReviewCommentLoadTask.load(ctx, "octo", "cat", 1,
                    new IntentUtils.InitialCommentMarker(11L)).blockingGet().isPresent());
        }
    }

    @Test
    public void load_reviewServiceError_propagatesError() {
        Context ctx = ApplicationProvider.getApplicationContext();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(11L, 77L));
        when(f.reviewService.getReview(anyString(), anyString(), anyLong(), eq(77L)))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            try {
                PullRequestReviewCommentLoadTask.load(ctx, "octo", "cat", 1,
                        new IntentUtils.InitialCommentMarker(11L)).blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }

    @Test
    public void getSingle_delegatesToLoad() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(11L, 77L));
        Review review = mock(Review.class);
        f.stubReview(77L, review);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            PullRequestReviewCommentLoadTask task = new PullRequestReviewCommentLoadTask(
                    activity, URL, "octo", "cat", 1, new IntentUtils.InitialCommentMarker(11L));
            Optional<Intent> result = task.getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(ReviewActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }
}
