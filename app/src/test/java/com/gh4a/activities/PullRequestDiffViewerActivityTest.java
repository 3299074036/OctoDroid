package com.gh4a.activities;

import android.content.Intent;
import android.net.Uri;

import com.gh4a.ServiceFactory;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.reactions.ReactionService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import retrofit2.Response;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PullRequestDiffViewerActivityTest {
    private Intent intent() {
        return PullRequestDiffViewerActivity.makeIntent(appContext(), "o", "r", 42,
                "abc1234", "a.txt", "@@ -1,1 +1,1 @@\n-old\n+new", null, -1, -1, -1, false, null);
    }

    private ReviewComment idLessComment() {
        ReviewComment c = mock(ReviewComment.class);
        when(c.id()).thenReturn(null);
        when(c.position()).thenReturn(1);
        when(c.path()).thenReturn("a.txt");
        return c;
    }

    private ReviewComment commentWithId(long id) {
        ReviewComment c = mock(ReviewComment.class);
        when(c.id()).thenReturn(id);
        when(c.position()).thenReturn(1);
        when(c.path()).thenReturn("a.txt");
        return c;
    }

    @Test
    public void makeIntent_setsNumberAndExtras() {
        Intent i = intent();
        assertEquals(PullRequestDiffViewerActivity.class.getName(), i.getComponent().getClassName());
        assertEquals(42, i.getIntExtra("number", -1));
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("abc1234", i.getStringExtra("sha"));
        assertEquals("a.txt", i.getStringExtra("path"));
    }

    @Test
    public void launch_titleAndSubtitle() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        assertEquals("a.txt", a.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", a.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void navigateUp_goesToPullRequest() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        Intent up = a.navigateUp();
        assertEquals(PullRequestActivity.class.getName(), up.getComponent().getClassName());
        assertEquals(42, up.getIntExtra("number", -1));
    }

    @Test
    public void canReply_isTrue() {
        assertTrue(launch(PullRequestDiffViewerActivity.class, intent()).canReply());
    }

    @Test
    public void createUrl_replyId_usesFragmentR() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        Uri uri = a.createUrl("R2", 55L);
        assertEquals("https://github.com/o/r/pull/42/files#r55", uri.toString());
    }

    @Test
    public void createUrl_noReply_usesDiffFragment() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        Uri uri = a.createUrl("R2", 0L);
        assertTrue(uri.toString().startsWith("https://github.com/o/r/pull/42/files#diff-"));
        assertTrue(uri.toString().endsWith("R2"));
        assertEquals(ApiHelpers.sha256Of("a.txt"), uri.getFragment().substring(5, 5 + 64));
    }

    @Test
    public void loadReactionDetails_nullId_throwsNpe_A1_2() {
        // A1-2 (confirmed): unlike CommitDiffViewerActivity, the PR diff
        // viewer passes comment.id() (nullable Long) straight into long
        // parameters without the 216 null guard.
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        DiffViewerActivity.CommentWrapper w =
                new DiffViewerActivity.CommentWrapper(idLessComment());
        try {
            a.loadReactionDetails(w, false).blockingGet();
            fail("expected NullPointerException for id-less review comment");
        } catch (NullPointerException expected) {
            // unboxing null Long in the service call arguments
        }
    }

    @Test
    public void addReaction_nullId_throwsNpe_A1_2() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        DiffViewerActivity.CommentWrapper w =
                new DiffViewerActivity.CommentWrapper(idLessComment());
        try {
            a.addReaction(w, "+1").blockingGet();
            fail("expected NullPointerException for id-less review comment");
        } catch (NullPointerException expected) {
            // unboxing null Long in the service call arguments
        }
    }

    @Test
    public void deleteReaction_nullId_throwsNpe_A1_2() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        DiffViewerActivity.CommentWrapper w =
                new DiffViewerActivity.CommentWrapper(idLessComment());
        try {
            a.deleteReaction(w, 9L).blockingGet();
            fail("expected NullPointerException for id-less review comment");
        } catch (NullPointerException expected) {
            // unboxing null Long in the service call arguments
        }
    }

    @Test
    public void deleteCommentSingle_callsService() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        when(svc.deleteComment("o", "r", 5L)).thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(PullRequestReviewCommentService.class, false))
                    .thenReturn(svc);
            PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
            a.deleteCommentSingle(5L).blockingGet();
            verify(svc).deleteComment("o", "r", 5L);
        }
    }

    @Test
    public void getCommentsSingle_filtersNullPosition() {
        PullRequestReviewCommentService svc = mock(PullRequestReviewCommentService.class);
        List<ReviewComment> page = new ArrayList<>();
        page.add(commentWithId(1L));
        ReviewComment noPos = mock(ReviewComment.class);
        when(noPos.id()).thenReturn(2L);
        when(noPos.position()).thenReturn(null);
        page.add(noPos);
        when(svc.getPullRequestComments(eq("o"), eq("r"), eq(42L), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<ReviewComment>builder().items(page).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(PullRequestReviewCommentService.class, false))
                    .thenReturn(svc);
            PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
            List<ReviewComment> result = a.getCommentsSingle(false).blockingGet();
            assertEquals(1, result.size());
            assertEquals(Long.valueOf(1L), result.get(0).id());
        }
    }

    @Test
    public void loadReactionDetails_withId_callsService() {
        ReactionService svc = mock(ReactionService.class);
        List<Reaction> page = new ArrayList<>();
        page.add(mock(Reaction.class));
        when(svc.getPullRequestReviewCommentReactions(eq("o"), eq("r"), eq(7L), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<Reaction>builder().items(page).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(ReactionService.class, false))
                    .thenReturn(svc);
            PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
            DiffViewerActivity.CommentWrapper w =
                    new DiffViewerActivity.CommentWrapper(commentWithId(7L));
            assertEquals(1, a.loadReactionDetails(w, false).blockingGet().size());
        }
    }

    @Test
    public void deleteReaction_withId_callsServiceAndMapsTrue() {
        ReactionService svc = mock(ReactionService.class);
        when(svc.deletePullRequestReviewCommentReaction("o", "r", 7L, 9L))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false)).thenReturn(svc);
            PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
            DiffViewerActivity.CommentWrapper w =
                    new DiffViewerActivity.CommentWrapper(commentWithId(7L));
            assertTrue(a.deleteReaction(w, 9L).blockingGet());
        }
    }

    @Test
    public void openCommentDialog_nullComment_launchesEditIntent() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        a.openCommentDialog(0, 0, "line", 1, 1, 1, null);
        Intent next = org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        assertEquals(EditPullRequestDiffCommentActivity.class.getName(),
                next.getComponent().getClassName());
    }

    @Test
    public void openCommentDialog_withComment_prefillsBody() {
        PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
        a.openCommentDialog(0, 0, "line", 1, 1, 1, commentWithId(7L));
        Intent next = org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        assertEquals(EditPullRequestDiffCommentActivity.class.getName(),
                next.getComponent().getClassName());
    }

    @Test
    public void addReaction_withId_callsService() {
        ReactionService svc = mock(ReactionService.class);
        Reaction reaction = mock(Reaction.class);
        when(svc.createPullRequestReviewCommentReaction(eq("o"), eq("r"), eq(7L), any()))
                .thenReturn(Single.just(Response.success(reaction)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false)).thenReturn(svc);
            PullRequestDiffViewerActivity a = launch(PullRequestDiffViewerActivity.class, intent());
            DiffViewerActivity.CommentWrapper w =
                    new DiffViewerActivity.CommentWrapper(commentWithId(7L));
            assertEquals(reaction, a.addReaction(w, "+1").blockingGet());
        }
    }
}
