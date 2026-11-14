package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.request.CommentRequest;
import com.meisolsson.githubsdk.model.request.pull_request.CreateReviewComment;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class EditPullRequestCommentActivityTest {
    @Test
    public void makeIntent_bothIdsZero_throwsIllegalState() {
        try {
            EditPullRequestCommentActivity.makeIntent(appContext(), "o", "r", 3, 0L, 0L, "b", 0);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("Only editing and replying allowed", expected.getMessage());
        }
    }

    @Test
    public void makeIntent_editMode_setsExtras() {
        Intent i = EditPullRequestCommentActivity.makeIntent(appContext(), "o", "r", 3, 5L, 0L, "b", 0);
        assertEquals(3, i.getIntExtra("pr", -1));
        assertEquals(5L, i.getLongExtra("id", -1));
    }

    @Test
    public void makeIntent_replyMode_setsExtras() {
        Intent i = EditPullRequestCommentActivity.makeIntent(appContext(), "o", "r", 3, 0L, 7L, "b", 0);
        assertEquals(0L, i.getLongExtra("id", -1));
        assertEquals(7L, i.getLongExtra("reply_to", -1));
    }

    @Test
    public void createComment_replySetsInReplyTo() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.createReviewComment(eq("o"), eq("r"), eq(3L), any()))
                .thenReturn(Single.just(Response.success(mock(ReviewComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewCommentService.class, false)).thenReturn(svc);
            Intent i = EditPullRequestCommentActivity.makeIntent(appContext(), "o", "r", 3, 0L, 7L, "", 0);
            EditPullRequestCommentActivity activity = launch(EditPullRequestCommentActivity.class, i);
            activity.createComment("o", "r", "reply", 7L).blockingGet();
            ArgumentCaptor<CreateReviewComment> captor = ArgumentCaptor.forClass(CreateReviewComment.class);
            verify(svc).createReviewComment(eq("o"), eq("r"), eq(3L), captor.capture());
            assertEquals("reply", captor.getValue().body());
            assertEquals(Long.valueOf(7L), captor.getValue().inReplyTo());
        }
    }

    @Test
    public void editComment_callsEditReviewComment() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.editReviewComment(eq("o"), eq("r"), eq(5L), any()))
                .thenReturn(Single.just(Response.success(mock(ReviewComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewCommentService.class, false)).thenReturn(svc);
            Intent i = EditPullRequestCommentActivity.makeIntent(appContext(), "o", "r", 3, 5L, 0L, "old", 0);
            EditPullRequestCommentActivity activity = launch(EditPullRequestCommentActivity.class, i);
            activity.editComment("o", "r", 5L, "new").blockingGet();
            ArgumentCaptor<CommentRequest> captor = ArgumentCaptor.forClass(CommentRequest.class);
            verify(svc).editReviewComment(eq("o"), eq("r"), eq(5L), captor.capture());
            assertEquals("new", captor.getValue().body());
        }
    }
}
