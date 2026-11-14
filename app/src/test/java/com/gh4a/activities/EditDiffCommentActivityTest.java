package com.gh4a.activities;

import android.content.Intent;
import android.widget.TextView;

import com.gh4a.R;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class EditDiffCommentActivityTest {
    private Intent intent() {
        return EditDiffCommentActivity.makeIntent(appContext(), "o", "r", "c1",
                "a.txt", "@@ -1,1 +1,1 @@", 3, 4, 2, 0L, "draft");
    }

    @Test
    public void makeIntent_setsAllExtras() {
        Intent i = intent();
        assertEquals(EditDiffCommentActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("c1", i.getStringExtra("commit_id"));
        assertEquals("a.txt", i.getStringExtra("path"));
        assertEquals("@@ -1,1 +1,1 @@", i.getStringExtra("line"));
        assertEquals(3, i.getIntExtra("left_line", -1));
        assertEquals(4, i.getIntExtra("right_line", -1));
        assertEquals(2, i.getIntExtra("position", -1));
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals(R.attr.colorIssueOpen, i.getIntExtra("highlight_color_attr", -1));
    }

    @Test
    public void launch_headerShowsLineAndTitle() {
        EditDiffCommentActivity activity = launch(EditDiffCommentActivity.class, intent());
        TextView line = activity.findViewById(R.id.line);
        TextView title = activity.findViewById(R.id.title);
        assertEquals("@@ -1,1 +1,1 @@", line.getText().toString());
        assertEquals(appContext().getString(R.string.commit_comment_dialog_title, 3, 4),
                title.getText().toString());
    }

    @Test
    public void createComment_includesPathAndPosition() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.createCommitComment(eq("o"), eq("r"), eq("c1"), any()))
                .thenReturn(Single.just(Response.success(GitComment.builder().id(1L).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false)).thenReturn(svc);
            EditDiffCommentActivity activity = launch(EditDiffCommentActivity.class, intent());
            activity.createComment("o", "r", "note", 0L).blockingGet();
            ArgumentCaptor<CreateCommitComment> captor = ArgumentCaptor.forClass(CreateCommitComment.class);
            verify(svc).createCommitComment(eq("o"), eq("r"), eq("c1"), captor.capture());
            assertEquals("note", captor.getValue().body());
            assertEquals("a.txt", captor.getValue().path());
            assertEquals(Integer.valueOf(2), captor.getValue().position());
        }
    }

    @Test
    public void editComment_buildsBodyOnlyRequest() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.editCommitComment(eq("o"), eq("r"), eq(9L), any()))
                .thenReturn(Single.just(Response.success(GitComment.builder().id(9L).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false)).thenReturn(svc);
            EditDiffCommentActivity activity = launch(EditDiffCommentActivity.class, intent());
            activity.editComment("o", "r", 9L, "fixed").blockingGet();
            ArgumentCaptor<CommentRequest> captor = ArgumentCaptor.forClass(CommentRequest.class);
            verify(svc).editCommitComment(eq("o"), eq("r"), eq(9L), captor.capture());
            assertEquals("fixed", captor.getValue().body());
        }
    }
}
