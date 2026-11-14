package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.CommitDiffViewerActivity;
import com.gh4a.activities.FileViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.GitHubFile;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CommitDiffLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/commit/abc#diff-xyzL1");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private GitHubFile file(String filename) {
        GitHubFile f = mock(GitHubFile.class);
        when(f.filename()).thenReturn(filename);
        when(f.patch()).thenReturn("@@ -1 +1 @@");
        return f;
    }

    private CommitDiffLoadTask task(FragmentActivity activity, String fileHash) {
        return new CommitDiffLoadTask(activity, URL, "octo", "cat",
                new DiffHighlightId(fileHash, 1, 2, true), "abc123");
    }

    private void stubCommit(RepositoryCommitService service, GitHubFile... files) {
        Commit commit = mock(Commit.class);
        when(commit.files()).thenReturn(Arrays.asList(files));
        when(service.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(commit)));
    }

    @Test
    public void constructor_storesSha() {
        FragmentActivity activity = activity();
        CommitDiffLoadTask task = task(activity, "hash");
        assertEquals("abc123", task.mSha);
        assertEquals("octo", task.mRepoOwner);
        assertEquals("cat", task.mRepoName);
    }

    @Test
    public void getSha_returnsConstructorSha() {
        assertEquals("abc123", task(activity(), "hash").getSha().blockingGet());
    }

    @Test
    public void getLaunchIntent_opensCommitDiffViewer() {
        FragmentActivity activity = activity();
        Intent intent = task(activity, "hash").getLaunchIntent("abc123",
                file("src/Foo.java"), new DiffHighlightId("hash", 1, 2, true));
        assertEquals(CommitDiffViewerActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void getFallbackIntent_opensCommitActivity() {
        FragmentActivity activity = activity();
        Intent intent = task(activity, "hash").getFallbackIntent("abc123");
        assertEquals(CommitActivity.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void getFiles_returnsCommitFiles() {
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        stubCommit(service, file("a.java"), file("b.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            assertEquals(2, task(activity(), "hash").getFiles().blockingGet().size());
        }
    }

    @Test
    public void getSingle_matchingFile_opensDiffViewer() {
        FragmentActivity activity = activity();
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        stubCommit(service, file("src/Foo.java"), file("other.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            Optional<Intent> result = task(activity,
                    ApiHelpers.sha256Of("src/Foo.java")).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitDiffViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_matchingImageFile_opensFileViewer() {
        FragmentActivity activity = activity();
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        stubCommit(service, file("img/logo.png"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            Optional<Intent> result = task(activity,
                    ApiHelpers.sha256Of("img/logo.png")).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_noMatchingFile_opensCommitActivity() {
        FragmentActivity activity = activity();
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        stubCommit(service, file("src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            Optional<Intent> result = task(activity,
                    ApiHelpers.sha256Of("missing.java")).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_emptyFileList_opensCommitActivity() {
        FragmentActivity activity = activity();
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        Commit commit = mock(Commit.class);
        when(commit.files()).thenReturn(Collections.emptyList());
        when(service.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(commit)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            Optional<Intent> result = task(activity, "whatever").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(CommitActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_nullFilesList_propagatesError() {
        // Commit.files() == null: DiffLoadTask composes over a null list.
        FragmentActivity activity = activity();
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        Commit commit = mock(Commit.class);
        when(commit.files()).thenReturn(null);
        when(service.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(commit)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            try {
                task(activity, "whatever").getSingle().blockingGet();
                fail("expected error for null files list");
            } catch (RuntimeException e) {
                // Commit::files returns null, so Single.map() rejects the
                // null value with a NullPointerException.
                assertTrue(e instanceof NullPointerException
                        || e.getCause() instanceof NullPointerException);
            }
        }
    }

    @Test
    public void getSingle_commitServiceError_propagatesError() {
        RepositoryCommitService service = mock(RepositoryCommitService.class);
        when(service.getCommit(anyString(), anyString(), anyString()))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryCommitService.class), eq(false)))
                    .thenReturn(service);
            try {
                task(activity(), "hash").getSingle().blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }
}
