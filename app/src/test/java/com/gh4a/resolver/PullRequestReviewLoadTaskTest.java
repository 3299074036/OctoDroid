package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.ReviewActivity;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Optional;

import io.reactivex.Single;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
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
public class PullRequestReviewLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/pull/1#pullrequestreview-55");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void constructor_storesFields() {
        FragmentActivity activity = activity();
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(55L);
        PullRequestReviewLoadTask t = new PullRequestReviewLoadTask(
                activity, URL, "octo", "cat", 3, marker);
        assertEquals("octo", t.mRepoOwner);
        assertEquals("cat", t.mRepoName);
        assertEquals(3, t.mPullRequestNumber);
        assertEquals(marker, t.mMarker);
    }

    @Test
    public void getSingle_reviewFound_opensReviewActivity() {
        FragmentActivity activity = activity();
        PullRequestReviewService service = mock(PullRequestReviewService.class);
        Review review = mock(Review.class);
        when(service.getReview(anyString(), anyString(), anyLong(), eq(55L)))
                .thenReturn(Single.just(Response.success(review)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestReviewService.class), eq(false)))
                    .thenReturn(service);
            PullRequestReviewLoadTask task = new PullRequestReviewLoadTask(activity, URL,
                    "octo", "cat", 1, new IntentUtils.InitialCommentMarker(55L));
            Optional<Intent> result = task.getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(ReviewActivity.class.getName(),
                    result.get().getComponent().getClassName());
            verify(service).getReview("octo", "cat", 1L, 55L);
        }
    }

    @Test
    public void getSingle_markerWithDateOnly_usesCommentIdMinusOne() {
        // InitialCommentMarker(Date) has commentId == -1; getReview is
        // called with -1, i.e. the marker's commentId is used verbatim.
        FragmentActivity activity = activity();
        PullRequestReviewService service = mock(PullRequestReviewService.class);
        Review review = mock(Review.class);
        when(service.getReview(anyString(), anyString(), anyLong(), eq(-1L)))
                .thenReturn(Single.just(Response.success(review)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestReviewService.class), eq(false)))
                    .thenReturn(service);
            PullRequestReviewLoadTask task = new PullRequestReviewLoadTask(activity, URL,
                    "octo", "cat", 1,
                    new IntentUtils.InitialCommentMarker(new java.util.Date()));
            Optional<Intent> result = task.getSingle().blockingGet();
            assertTrue(result.isPresent());
            verify(service).getReview("octo", "cat", 1L, -1L);
        }
    }

    @Test
    public void getSingle_reviewServiceError_propagatesError() {
        FragmentActivity activity = activity();
        PullRequestReviewService service = mock(PullRequestReviewService.class);
        when(service.getReview(anyString(), anyString(), anyLong(), eq(55L)))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestReviewService.class), eq(false)))
                    .thenReturn(service);
            PullRequestReviewLoadTask task = new PullRequestReviewLoadTask(activity, URL,
                    "octo", "cat", 1, new IntentUtils.InitialCommentMarker(55L));
            try {
                task.getSingle().blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }
}
