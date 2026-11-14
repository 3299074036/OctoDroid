package com.gh4a.activities;

import android.content.Intent;
import android.widget.TextView;

import com.gh4a.R;
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
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class EditPullRequestDiffCommentActivityTest {
    private Intent intent() {
        return EditPullRequestDiffCommentActivity.makeIntent(appContext(), "o", "r", "c1",
                "a.txt", "@@ -1,1 +1,1 @@", 3, 4, 2, 0L, "draft", 42, 0L);
    }

    @Test
    public void makeIntent_setsAllExtras() {
        Intent i = intent();
        assertEquals(EditPullRequestDiffCommentActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("c1", i.getStringExtra("commit_id"));
        assertEquals("a.txt", i.getStringExtra("path"));
        assertEquals(42, i.getIntExtra("pull_request_number", -1));
        assertEquals(0L, i.getLongExtra("reply_to", -1));
        assertEquals(R.attr.colorIssueOpen, i.getIntExtra("highlight_color_attr", -1));
    }

    @Test
    public void launch_headerShowsLineAndTitle() {
        EditPullRequestDiffCommentActivity activity = launch(EditPullRequestDiffCommentActivity.class, intent());
        assertEquals("@@ -1,1 +1,1 @@",
                ((TextView) activity.findViewById(R.id.line)).getText().toString());
        assertEquals(appContext().getString(R.string.commit_comment_dialog_title, 3, 4),
                ((TextView) activity.findViewById(R.id.title)).getText().toString());
    }

    @Test
    public void createComment_reply_setsInReplyToOnly() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.createReviewComment(eq("o"), eq("r"), eq(42L), any()))
                .thenReturn(Single.just(Response.success(mock(ReviewComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewCommentService.class, false)).thenReturn(svc);
            EditPullRequestDiffCommentActivity activity = launch(EditPullRequestDiffCommentActivity.class, intent());
            activity.createComment("o", "r", "reply", 7L).blockingGet();
            ArgumentCaptor<CreateReviewComment> captor = ArgumentCaptor.forClass(CreateReviewComment.class);
            verify(svc).createReviewComment(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals(Long.valueOf(7L), captor.getValue().inReplyTo());
            assertNull(captor.getValue().commitId());
        }
    }

    @Test
    public void createComment_new_setsCommitPathPosition() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.createReviewComment(eq("o"), eq("r"), eq(42L), any()))
                .thenReturn(Single.just(Response.success(mock(ReviewComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewCommentService.class, false)).thenReturn(svc);
            EditPullRequestDiffCommentActivity activity = launch(EditPullRequestDiffCommentActivity.class, intent());
            activity.createComment("o", "r", "new", 0L).blockingGet();
            ArgumentCaptor<CreateReviewComment> captor = ArgumentCaptor.forClass(CreateReviewComment.class);
            verify(svc).createReviewComment(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals("c1", captor.getValue().commitId());
            assertEquals("a.txt", captor.getValue().path());
            assertEquals(Integer.valueOf(2), captor.getValue().position());
            assertNull(captor.getValue().inReplyTo());
        }
    }

    @Test
    public void editComment_callsEditReviewComment() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.editReviewComment(eq("o"), eq("r"), eq(5L), any()))
                .thenReturn(Single.just(Response.success(mock(ReviewComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewCommentService.class, false)).thenReturn(svc);
            EditPullRequestDiffCommentActivity activity = launch(EditPullRequestDiffCommentActivity.class, intent());
            activity.editComment("o", "r", 5L, "new").blockingGet();
            ArgumentCaptor<CommentRequest> captor = ArgumentCaptor.forClass(CommentRequest.class);
            verify(svc).editReviewComment(eq("o"), eq("r"), eq(5L), captor.capture());
            assertEquals("new", captor.getValue().body());
        }
    }
}
