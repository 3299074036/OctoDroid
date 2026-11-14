package com.gh4a.resolver;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.CommitDiffViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommitService;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CommitCommentLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/commit/abc#r7");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private GitComment comment(Long id, String path) {
        GitComment c = mock(GitComment.class);
        when(c.id()).thenReturn(id);
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

    private Commit commitWithFiles(java.util.List<GitHubFile> files) {
        Commit c = mock(Commit.class);
        when(c.files()).thenReturn(files);
        return c;
    }

    // Build the Commit mock OUTSIDE any when(): creating mocks inside a
    // thenReturn() argument nests Mockito stubbing and throws
    // UnfinishedStubbingException.
    private void stubCommitWithFiles(RepositoryCommitService service, GitHubFile... files) {
        Commit commit = commitWithFiles(Arrays.asList(files));
        when(service.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(commit)));
    }

    private void stubServices(MockedStatic<ServiceFactory> mocked,
            RepositoryCommitService commitService, RepositoryCommentService commentService) {
        mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                .thenReturn(commitService);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                eq(RepositoryCommentService.class), eq(false))).thenReturn(commentService);
    }

    private void stubComments(RepositoryCommentService commentService, GitComment... comments) {
        when(commentService.getCommitComments(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<GitComment>builder().items(Arrays.asList(comments)).build())));
    }

    @Test
    public void constructor_storesFields() {
        FragmentActivity activity = activity();
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(7L);
        CommitCommentLoadTask task = new CommitCommentLoadTask(activity, URL,
                "octo", "cat", "abc123", marker);
        assertEquals("octo", task.mRepoOwner);
        assertEquals("cat", task.mRepoName);
        assertEquals("abc123", task.mCommitSha);
        assertEquals(marker, task.mMarker);
    }

    @Test
    public void load_matchingCommentNonImageFile_opensDiffViewer() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file("src/Foo.java"));
        stubComments(commentService, comment(7L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitDiffViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void load_matchingCommentImageFile_returnsEmpty() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file("img/logo.png"));
        stubComments(commentService, comment(7L, "img/logo.png"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertFalse(result.isPresent());
        }
    }

    @Test
    public void load_noMatchingComment_opensCommitActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file("src/Foo.java"));
        stubComments(commentService, comment(8L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void load_commentWithNullId_isSkipped() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file("src/Foo.java"));
        stubComments(commentService, comment(null, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void load_commitFilesNull_opensCommitActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        Commit commit = mock(Commit.class);
        when(commit.files()).thenReturn(null);
        when(commitService.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(commit)));
        stubComments(commentService, comment(7L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void load_commitFilesContainNullEntry_opensCommitActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, null, file("other.java"));
        stubComments(commentService, comment(7L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void load_noComments_opensCommitActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file("src/Foo.java"));
        when(commentService.getCommitComments(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<GitComment>builder().items(Collections.emptyList()).build())));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void load_commitServiceError_propagatesError() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        when(commitService.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.error(new RuntimeException("network down")));
        stubComments(commentService, comment(7L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            try {
                CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                        new IntentUtils.InitialCommentMarker(7L)).blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("network down", e.getMessage());
            }
        }
    }

    @Test
    public void getSingle_delegatesToLoad() {
        FragmentActivity activity = activity();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file("src/Foo.java"));
        stubComments(commentService, comment(7L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            CommitCommentLoadTask task = new CommitCommentLoadTask(activity, URL,
                    "octo", "cat", "abc123", new IntentUtils.InitialCommentMarker(7L));
            Optional<Intent> result = task.getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitDiffViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void sha256Helper_matchesDiffFileHash() {
        // sanity: DiffLoadTask matches files by sha256Of(filename)
        assertEquals(ApiHelpers.sha256Of("src/Foo.java"),
                ApiHelpers.sha256Of("src/Foo.java"));
        assertFalse(ApiHelpers.sha256Of("src/Foo.java").equals(ApiHelpers.sha256Of("other.java")));
    }

    @Test
    public void load_noMatchingCommentAndNullFiles_opensCommitActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        Commit commit = mock(Commit.class);
        when(commit.files()).thenReturn(null);
        when(commitService.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(commit)));
        stubComments(commentService, comment(9L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }


    @Test
    public void load_commentFileWithNullFilename_opensCommitActivity() {
        Context ctx = ApplicationProvider.getApplicationContext();
        RepositoryCommitService commitService = mock(RepositoryCommitService.class);
        RepositoryCommentService commentService = mock(RepositoryCommentService.class);
        stubCommitWithFiles(commitService, file(null));
        stubComments(commentService, comment(7L, "src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            stubServices(mocked, commitService, commentService);
            Optional<Intent> result = CommitCommentLoadTask.load(ctx, "octo", "cat", "abc123",
                    new IntentUtils.InitialCommentMarker(7L)).blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

}
