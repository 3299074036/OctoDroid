package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * EditCommentActivity is abstract; behaviour is exercised through
 * EditCommitCommentActivity (no extra header views).
 */
@RunWith(RobolectricTestRunner.class)
public class EditCommentActivityTest {
    @Test
    public void fillInIntent_setsAllExtras() {
        Intent base = new Intent(appContext(), EditCommitCommentActivity.class);
        Intent intent = EditCommentActivity.fillInIntent(base, "o", "r", 5L, 6L, "body", 123);
        assertEquals("o", intent.getStringExtra("owner"));
        assertEquals("r", intent.getStringExtra("repo"));
        assertEquals(5L, intent.getLongExtra("id", -1));
        assertEquals(6L, intent.getLongExtra("reply_to", -1));
        assertEquals("body", intent.getStringExtra("body"));
        assertEquals(123, intent.getIntExtra("highlight_color_attr", -1));
    }

    @Test
    public void onCreate_consumesBodyExtra() {
        Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha", 0L, "draft");
        assertTrue(intent.hasExtra("body"));
        EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
        assertFalse(activity.getIntent().hasExtra("body"));
    }

    @Test
    public void getCommentEditorHintResId_isZero() {
        Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha", 0L, "");
        assertEquals(0, launch(EditCommitCommentActivity.class, intent).getCommentEditorHintResId());
    }

    @Test
    public void getEditorErrorMessageResId_isIssueCommentError() {
        Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha", 0L, "");
        assertEquals(R.string.issue_error_comment,
                launch(EditCommitCommentActivity.class, intent).getEditorErrorMessageResId());
    }

    @Test
    public void onEditorTextSent_setsOkAndFinishes() {
        Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha", 0L, "");
        EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
        activity.onEditorTextSent();
        assertTrue(activity.isFinishing());
        assertEquals(android.app.Activity.RESULT_OK,
                org.robolectric.Shadows.shadowOf(activity).getResultCode());
    }

    @Test
    public void onEditorDoSend_idZero_routesToCreateComment() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        GitComment created = GitComment.builder().id(11L).body("hi").build();
        when(svc.createCommitComment(eq("o"), eq("r"), eq("sha"), any()))
                .thenReturn(Single.just(Response.success(created)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false)).thenReturn(svc);
            Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha", 0L, "");
            EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
            assertEquals(Long.valueOf(11L),
                    ((GitComment) activity.onEditorDoSend("hi").blockingGet()).id());
            verify(svc).createCommitComment(eq("o"), eq("r"), eq("sha"), any());
        }
    }

    @Test
    public void onEditorDoSend_nonZeroId_routesToEditComment() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        GitComment edited = GitComment.builder().id(5L).body("edited").build();
        when(svc.editCommitComment(eq("o"), eq("r"), eq(5L), any()))
                .thenReturn(Single.just(Response.success(edited)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false)).thenReturn(svc);
            Intent intent = EditCommitCommentActivity.makeIntent(appContext(), "o", "r", "sha", 5L, "old");
            EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class, intent);
            assertEquals("edited",
                    ((GitComment) activity.onEditorDoSend("edited").blockingGet()).body());
            verify(svc).editCommitComment(eq("o"), eq("r"), eq(5L), any());
        }
    }

    @Test
    public void onCreate_withoutBodyExtra_skipsPrefill() {
        EditCommitCommentActivity activity = launch(EditCommitCommentActivity.class,
                new Intent(appContext(), EditCommitCommentActivity.class));
        assertFalse(activity.getIntent().hasExtra("body"));
    }
}
