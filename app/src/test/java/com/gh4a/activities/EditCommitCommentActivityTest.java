package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.model.request.CommentRequest;
import com.meisolsson.githubsdk.model.request.repository.CreateCommitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class EditCommitCommentActivityTest {
    @Test
    public void makeIntent_setsCommitAndFillInExtras() {
        Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha1", 5L, "body");
        assertEquals(EditCommitCommentActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("sha1", intent.getStringExtra("commit"));
        assertEquals("o", intent.getStringExtra("owner"));
        assertEquals("r", intent.getStringExtra("repo"));
        assertEquals(5L, intent.getLongExtra("id", -1));
        assertEquals("body", intent.getStringExtra("body"));
        assertEquals(0, intent.getIntExtra("highlight_color_attr", -1));
    }

    @Test
    public void createComment_buildsBareRequest() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.createCommitComment(eq("o"), eq("r"), eq("sha1"), any()))
                .thenReturn(Single.just(Response.success(GitComment.builder().id(1L).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false)).thenReturn(svc);
            Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha1", 0L, "");
            EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
            activity.createComment("o", "r", "hello", 0L).blockingGet();
            ArgumentCaptor<CreateCommitComment> captor = ArgumentCaptor.forClass(CreateCommitComment.class);
            verify(svc).createCommitComment(eq("o"), eq("r"), eq("sha1"), captor.capture());
            assertEquals("hello", captor.getValue().body());
        }
    }

    @Test
    public void editComment_buildsBodyOnlyRequest() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.editCommitComment(eq("o"), eq("r"), eq(5L), any()))
                .thenReturn(Single.just(Response.success(GitComment.builder().id(5L).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false)).thenReturn(svc);
            Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha1", 5L, "old");
            EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
            activity.editComment("o", "r", 5L, "new body").blockingGet();
            ArgumentCaptor<CommentRequest> captor = ArgumentCaptor.forClass(CommentRequest.class);
            verify(svc).editCommitComment(eq("o"), eq("r"), eq(5L), captor.capture());
            assertEquals("new body", captor.getValue().body());
        }
    }

    @Test
    public void launch_editMode_showsSaveIcon() {
        Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha1", 5L, "old");
        EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
        assertTrue(activity.getIntent().getLongExtra("id", 0L) != 0L);
    }
}
