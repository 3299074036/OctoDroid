package com.gh4a.activities;

import android.content.Intent;
import android.net.Uri;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.reactions.ReactionService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;

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
public class CommitDiffViewerActivityTest {
    private Intent intent(List<GitComment> comments) {
        return CommitDiffViewerActivity.makeIntent(appContext(), "o", "r",
                "abc1234", "a.txt", "@@ -1,1 +1,1 @@\n-old\n+new", comments, -1, -1, false, null);
    }

    @Test
    public void makeIntent_setsExtras() {
        Intent i = intent(null);
        assertEquals(CommitDiffViewerActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("abc1234", i.getStringExtra("sha"));
        assertEquals("a.txt", i.getStringExtra("path"));
        assertEquals("@@ -1,1 +1,1 @@\n-old\n+new", i.getStringExtra("diff"));
    }

    @Test
    public void launch_titleAndSubtitle() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        assertEquals("a.txt", a.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", a.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void navigateUp_goesToCommit() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        Intent up = a.navigateUp();
        assertEquals(CommitActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("abc1234", up.getStringExtra("sha"));
    }

    @Test
    public void canReply_isFalse() {
        assertFalse(launch(CommitDiffViewerActivity.class, intent(null)).canReply());
    }

    @Test
    public void createUrl_replyId_usesFragmentR() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        Uri uri = a.createUrl("L5", 123L);
        assertEquals("https://github.com/o/r/commit/abc1234#r123", uri.toString());
    }

    @Test
    public void createUrl_noReply_usesDiffFragment() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        Uri uri = a.createUrl("L5", 0L);
        assertTrue(uri.toString().startsWith("https://github.com/o/r/commit/abc1234#diff-"));
        assertTrue(uri.toString().endsWith("L5"));
        assertEquals(ApiHelpers.sha256Of("a.txt"), uri.getFragment().substring(5, 5 + 64));
    }

    @Test
    public void generateHtml_withComment_rendersBodyAndHeader() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(GitComment.builder().id(7L).body("hello").bodyHtml("<p>hello</p>")
                .path("a.txt").position(1).user(null).build());
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(comments));
        String html = a.generateHtml("light", false);
        assertTrue(html.contains("<p>hello</p>"));
        assertTrue(html.contains("comment7"));
        assertTrue(html.contains("class=\"add\""));
    }

    @Test
    public void generateHtml_withReactions_rendersReactionSpans() {
        Reactions reactions = Reactions.builder().plusOne(2).minusOne(0).laugh(0).hooray(0).confused(0).heart(0).rocket(0).eyes(0).build();
        List<GitComment> comments = new ArrayList<>();
        comments.add(GitComment.builder().id(7L).body("hi").bodyHtml("hi")
                .path("a.txt").position(1).reactions(reactions).build());
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(comments));
        String html = a.generateHtml("light", false);
        assertTrue(html.contains("class='reaction'"));
        assertTrue(html.contains(">2</span>"));
    }

    @Test
    public void generateHtml_commentWithoutId_throwsNpe_A1_1() {
        // A1-1 (confirmed): DiffViewerActivity.generateHtml unboxes
        // comment.id() into a long; id-less comments crash the diff render
        // (the Rx subscribe swallows it into a load-failure screen).
        List<GitComment> comments = new ArrayList<>();
        comments.add(GitComment.builder().body("x").bodyHtml("x").path("a.txt").position(1).build());
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(comments));
        try {
            a.generateHtml("light", false);
            fail("expected NullPointerException for id-less comment");
        } catch (NullPointerException expected) {
            assertTrue(expected.getMessage().contains("id()"));
        }
    }

    @Test
    public void generateHtml_highlightRange_addsOnload() {
        Intent i = CommitDiffViewerActivity.makeIntent(appContext(), "o", "r",
                "abc1234", "a.txt", "@@ -1,2 +1,2 @@\n-old\n+new\n ctx", null, 1, 2, true, null);
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, i);
        String html = a.generateHtml("light", false);
        assertTrue(html.contains("highlightDiffLines(2,3)"));
    }

    @Test
    public void generateHtml_initialLine_addsScrollOnload() {
        Intent i = new Intent(appContext(), CommitDiffViewerActivity.class);
        DiffViewerActivity.fillInIntent(i, "o", "r", "abc1234", "a.txt",
                "@@ -1,1 +1,1 @@\n-x\n+y", null, 1, -1, -1, false, null);
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, i);
        assertTrue(a.generateHtml("light", false).contains("scrollToElement(\"line1\")"));
    }

    @Test
    public void loadReactionDetails_nullId_returnsEmptyWithoutNetwork() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(
                GitComment.builder().body("x").build());
        List<Reaction> result = a.loadReactionDetails(w, false).blockingGet();
        assertTrue(result.isEmpty());
    }

    @Test
    public void addReaction_nullId_returnsErrorWithoutNetwork() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(
                GitComment.builder().body("x").build());
        try {
            a.addReaction(w, "+1").blockingGet();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("Commit comment has no id", expected.getMessage());
        }
    }

    @Test
    public void deleteReaction_nullId_returnsFalseWithoutNetwork() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(
                GitComment.builder().body("x").build());
        assertFalse(a.deleteReaction(w, 9L).blockingGet());
    }

    @Test
    public void deleteCommentSingle_callsService() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.deleteCommitComment("o", "r", 5L))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(RepositoryCommentService.class, false))
                    .thenReturn(svc);
            CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
            a.deleteCommentSingle(5L).blockingGet();
            verify(svc).deleteCommitComment("o", "r", 5L);
        }
    }

    @Test
    public void getCommentsSingle_filtersNullPosition() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        List<GitComment> page = new ArrayList<>();
        page.add(GitComment.builder().id(1L).body("a").position(1).build());
        page.add(GitComment.builder().id(2L).body("b").position(null).build());
        when(svc.getCommitComments(eq("o"), eq("r"), eq("abc1234"), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<GitComment>builder().items(page).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(RepositoryCommentService.class, false))
                    .thenReturn(svc);
            CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
            List<GitComment> result = a.getCommentsSingle(false).blockingGet();
            assertEquals(1, result.size());
            assertEquals(Long.valueOf(1L), result.get(0).id());
        }
    }

    @Test
    public void loadReactionDetails_withId_callsService() {
        ReactionService svc = mock(ReactionService.class);
        List<Reaction> page = new ArrayList<>();
        page.add(mock(Reaction.class));
        when(svc.getCommitCommentReactions(eq("o"), eq("r"), eq(7L), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<Reaction>builder().items(page).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(ReactionService.class, false))
                    .thenReturn(svc);
            CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
            DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(
                    GitComment.builder().id(7L).body("x").build());
            assertEquals(1, a.loadReactionDetails(w, false).blockingGet().size());
        }
    }

    @Test
    public void openCommentDialog_nullComment_launchesEditIntent() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        a.openCommentDialog(0, 0, "line", 1, 1, 1, null);
        Intent next = org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        assertEquals(EditDiffCommentActivity.class.getName(),
                next.getComponent().getClassName());
    }

    @Test
    public void openCommentDialog_withComment_prefillsBody() {
        CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
        GitComment c = GitComment.builder().id(7L).body("hi").build();
        a.openCommentDialog(0, 0, "line", 1, 1, 1, c);
        Intent next = org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
        assertEquals(EditDiffCommentActivity.class.getName(),
                next.getComponent().getClassName());
    }

    @Test
    public void addReaction_withId_callsService() {
        ReactionService svc = mock(ReactionService.class);
        Reaction reaction = mock(Reaction.class);
        when(svc.createCommitCommentReaction(eq("o"), eq("r"), eq(7L), any()))
                .thenReturn(Single.just(Response.success(reaction)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false)).thenReturn(svc);
            CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
            DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(
                    GitComment.builder().id(7L).body("x").build());
            assertEquals(reaction, a.addReaction(w, "+1").blockingGet());
        }
    }

    @Test
    public void deleteReaction_withId_callsService() {
        ReactionService svc = mock(ReactionService.class);
        when(svc.deleteCommitCommentReaction(eq("o"), eq("r"), eq(7L), eq(3L)))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(ReactionService.class, false)).thenReturn(svc);
            CommitDiffViewerActivity a = launch(CommitDiffViewerActivity.class, intent(null));
            DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(
                    GitComment.builder().id(7L).body("x").build());
            assertTrue(a.deleteReaction(w, 3L).blockingGet());
        }
    }
}
