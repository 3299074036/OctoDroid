package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.ReviewActivity;
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
public class PullRequestReviewDiffLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/pull/1#discussion-diff-12345");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ReviewComment reviewComment(Long id, Long reviewId) {
        ReviewComment c = mock(ReviewComment.class);
        when(c.id()).thenReturn(id);
        when(c.pullRequestReviewId()).thenReturn(reviewId);
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
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewCommentService.class), eq(false)))
                    .thenReturn(commentService);
            mocked.when(() -> ServiceFactory.get(eq(PullRequestReviewService.class), eq(false)))
                    .thenReturn(reviewService);
        }
    }

    private PullRequestReviewDiffLoadTask task(FragmentActivity activity, String fileHash) {
        return new PullRequestReviewDiffLoadTask(activity, URL, "octo", "cat",
                new DiffHighlightId(fileHash, -1, -1, false), 1);
    }

    @Test
    public void constructor_storesFields() {
        FragmentActivity activity = activity();
        DiffHighlightId diffId = new DiffHighlightId("12345", -1, -1, false);
        PullRequestReviewDiffLoadTask t = new PullRequestReviewDiffLoadTask(
                activity, URL, "octo", "cat", diffId, 4);
        assertEquals("octo", t.mRepoOwner);
        assertEquals("cat", t.mRepoName);
        assertEquals(diffId, t.mDiffId);
        assertEquals(4, t.mPullRequestNumber);
    }

    @Test
    public void getSingle_matchingComment_opensReviewActivity() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(12345L, 99L), reviewComment(22222L, 100L));
        Review review = mock(Review.class);
        f.stubReview(99L, review);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = task(activity, "12345").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(ReviewActivity.class.getName(),
                    result.get().getComponent().getClassName());
            verify(f.reviewService).getReview("octo", "cat", 1L, 99L);
        }
    }

    @Test
    public void getSingle_noMatchingComment_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(22222L, 100L));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity, "12345").getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_noComments_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        when(f.commentService.getPullRequestComments(anyString(), anyString(), anyLong(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<ReviewComment>builder().items(Collections.emptyList()).build())));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity, "12345").getSingle().blockingGet().isPresent());
        }
    }

    @Test(expected = NumberFormatException.class)
    public void getSingle_nonNumericFileHash_throwsNumberFormat() {
        // Long.parseLong runs before the Single is built, so the failure
        // is thrown synchronously from getSingle() itself.
        task(activity(), "not-a-number").getSingle();
    }

    @Test
    public void getSingle_commentWithNullId_throwsNpe_TRES() {
        // T-RES-01 (suspected production defect): the first() predicate
        // does `c.id() == diffCommentId`, unboxing a nullable Long.
        // A comment without an id crashes the whole resolution instead of
        // being skipped.
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(null, 99L));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            try {
                task(activity, "12345").getSingle().blockingGet();
                fail("expected NullPointerException for null comment id");
            } catch (NullPointerException e) {
                // current behaviour, see T-RES-01
            }
        }
    }

    @Test
    public void getSingle_commentWithNullReviewId_throwsNpe_TRES() {
        // T-RES-02 (suspected production defect): `long reviewId =
        // comment.pullRequestReviewId()` unboxes a nullable Long.
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(12345L, null));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            try {
                task(activity, "12345").getSingle().blockingGet();
                fail("expected NullPointerException for null review id");
            } catch (NullPointerException e) {
                // current behaviour, see T-RES-02
            }
        }
    }

    @Test
    public void getSingle_reviewServiceError_propagatesError() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(12345L, 99L));
        when(f.reviewService.getReview(anyString(), anyString(), anyLong(), eq(99L)))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            try {
                task(activity, "12345").getSingle().blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }
}
