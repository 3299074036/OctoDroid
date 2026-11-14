package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.GitHubComment;
import com.meisolsson.githubsdk.model.request.CommentRequest;
import com.meisolsson.githubsdk.service.issues.IssueCommentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;
import static com.gh4a.activities.ActivityTestHelper.get;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class EditIssueCommentActivityTest {
    @Test
    public void makeIntent_setsIssueAndFillInExtras() {
        Intent i = EditIssueCommentActivity.makeIntent(appContext(), "o", "r", 12, 5L, "body", 0);
        assertEquals(EditIssueCommentActivity.class.getName(), i.getComponent().getClassName());
        assertEquals(12, i.getIntExtra("issue", -1));
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals(5L, i.getLongExtra("id", -1));
        assertEquals("body", i.getStringExtra("body"));
    }

    @Test
    public void createComment_usesIssueNumber() {
        IssueCommentService svc = mock(IssueCommentService.class);
        when(svc.createIssueComment(eq("o"), eq("r"), eq(12L), any()))
                .thenReturn(Single.just(Response.success(mock(GitHubComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueCommentService.class, false)).thenReturn(svc);
            Intent i = EditIssueCommentActivity.makeIntent(appContext(), "o", "r", 12, 0L, "", 0);
            EditIssueCommentActivity activity = launch(EditIssueCommentActivity.class, i);
            activity.createComment("o", "r", "hello", 0L).blockingGet();
            ArgumentCaptor<CommentRequest> captor = ArgumentCaptor.forClass(CommentRequest.class);
            verify(svc).createIssueComment(eq("o"), eq("r"), eq(12L), captor.capture());
            assertEquals("hello", captor.getValue().body());
        }
    }

    @Test
    public void editComment_callsEditIssueComment() {
        IssueCommentService svc = mock(IssueCommentService.class);
        when(svc.editIssueComment(eq("o"), eq("r"), eq(5L), any()))
                .thenReturn(Single.just(Response.success(mock(GitHubComment.class))));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueCommentService.class, false)).thenReturn(svc);
            Intent i = EditIssueCommentActivity.makeIntent(appContext(), "o", "r", 12, 5L, "old", 0);
            EditIssueCommentActivity activity = launch(EditIssueCommentActivity.class, i);
            activity.editComment("o", "r", 5L, "new").blockingGet();
            verify(svc).editIssueComment(eq("o"), eq("r"), eq(5L), any());
        }
    }

    @Test
    public void getActivity_returnsSelf() {
        Intent i = EditIssueCommentActivity.makeIntent(appContext(), "o", "r", 12, 0L, "", 0);
        EditIssueCommentActivity activity = launch(EditIssueCommentActivity.class, i);
        assertEquals(activity, activity.getActivity());
    }

    @Test
    public void getRootLayout_returnsCoordinatorLayout() {
        Intent i = EditIssueCommentActivity.makeIntent(appContext(), "o", "r", 12, 0L, "", 0);
        EditIssueCommentActivity activity = launch(EditIssueCommentActivity.class, i);
        assertNotNull(activity.getRootLayout());
    }
}
