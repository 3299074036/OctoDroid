package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.PullRequestDiffViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.PullRequestMarker;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewCommentService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;

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
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PullRequestDiffCommentLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/pull/1#r5");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ReviewComment reviewComment(Long id, Integer position, String path) {
        ReviewComment c = mock(ReviewComment.class);
        when(c.id()).thenReturn(id);
        when(c.position()).thenReturn(position);
        when(c.path()).thenReturn(path);
        when(c.createdAt()).thenReturn(null);
        return c;
    }

    private GitHubFile file(String filename) {
        GitHubFile f = mock(GitHubFile.class);
        when(f.filename()).thenReturn(filename);
        when(f.patch()).thenReturn("@@ -1 +1 @@");
        return f;
    }

    private PullRequest prWithHead(String sha) {
        PullRequest pr = mock(PullRequest.class);
        PullRequestMarker head = mock(PullRequestMarker.class);
        when(head.sha()).thenReturn(sha);
        when(pr.head()).thenReturn(head);
        return pr;
    }

    private PullRequest prWithNullHead() {
        PullRequest pr = mock(PullRequest.class);
        when(pr.head()).thenReturn(null);
        return pr;
    }

    private static class Fixture {
        final PullRequestService prService = mock(PullRequestService.class);
        final PullRequestReviewCommentService commentService =
                mock(PullRequestReviewCommentService.class);

        void stubComments(ReviewComment... comments) {
            when(commentService.getPullRequestComments(anyString(), anyString(), anyLong(), eq(1L)))
                    .thenReturn(Single.just(Response.success(
                            Page.<ReviewComment>builder().items(Arrays.asList(comments)).build())));
        }

        void stubFiles(GitHubFile... files) {
            when(prService.getPullRequestFiles(anyString(), anyString(), anyLong(), eq(1L)))
                    .thenReturn(Single.just(Response.success(
                            Page.<GitHubFile>builder().items(Arrays.asList(files)).build())));
        }

        void stubPr(PullRequest pr) {
            when(prService.getPullRequest(anyString(), anyString(), anyLong()))
                    .thenReturn(Single.just(Response.success(pr)));
        }

        void install(MockedStatic<ServiceFactory> mocked) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestService.class), eq(false))).thenReturn(prService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestReviewCommentService.class), eq(false)))
                    .thenReturn(commentService);
        }
    }

    private PullRequestDiffCommentLoadTask task(FragmentActivity activity) {
        return new PullRequestDiffCommentLoadTask(activity, URL, "octo", "cat", 1,
                new IntentUtils.InitialCommentMarker(5L), 0);
    }

    @Test
    public void constructor_storesFields() {
        FragmentActivity activity = activity();
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(5L);
        PullRequestDiffCommentLoadTask t = new PullRequestDiffCommentLoadTask(
                activity, URL, "octo", "cat", 42, marker, 3);
        assertEquals("octo", t.mRepoOwner);
        assertEquals("cat", t.mRepoName);
        assertEquals(42, t.mPullRequestNumber);
        assertEquals(marker, t.mMarker);
        assertEquals(3, t.mPage);
    }

    @Test
    public void getSingle_commentAndFileFound_opensDiffViewer() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = task(activity).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(PullRequestDiffViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_prHeadNull_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithNullHead());
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_prHeadShaNull_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        PullRequest pr = mock(PullRequest.class);
        PullRequestMarker head = mock(PullRequestMarker.class);
        when(head.sha()).thenReturn(null);
        when(pr.head()).thenReturn(head);
        f.stubPr(pr);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_commentFoundButFileMissing_opensPullRequestActivity() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "gone.java"));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = task(activity).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(PullRequestActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_commentFoundOnImageFile_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "img.png"));
        f.stubFiles(file("img.png"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_noMatchingComment_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(9L, 3, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_commentWithNullId_isSkipped() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(null, 3, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_commentWithNullPosition_isFilteredOut() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, null, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_noComments_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        when(f.commentService.getPullRequestComments(anyString(), anyString(), anyLong(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<ReviewComment>builder().items(Collections.emptyList()).build())));
        f.stubFiles(file("src/Foo.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(task(activity).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_prServiceError_propagatesError() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "src/Foo.java"));
        f.stubFiles(file("src/Foo.java"));
        when(f.prService.getPullRequest(anyString(), anyString(), anyLong()))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            try {
                task(activity).getSingle().blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }

    @Test
    public void getSingle_commentFoundButNoMatchingFile_opensPullRequestActivity() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubComments(reviewComment(5L, 3, "src/Nope.java"));
        f.stubFiles(file("src/Other.java"));
        f.stubPr(prWithHead("deadbeef"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = task(activity).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(PullRequestActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

}
