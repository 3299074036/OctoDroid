package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.FileViewerActivity;
import com.gh4a.activities.PullRequestActivity;
import com.gh4a.activities.PullRequestDiffViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.GitHubFile;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.PullRequestMarker;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PullRequestDiffLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/pull/7#diff-xyzL1");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private GitHubFile file(String filename) {
        GitHubFile f = mock(GitHubFile.class);
        when(f.filename()).thenReturn(filename);
        when(f.patch()).thenReturn("@@ -1 +1 @@");
        return f;
    }

    private PullRequestDiffLoadTask task(FragmentActivity activity, String fileHash) {
        return new PullRequestDiffLoadTask(activity, URL, "octo", "cat",
                new DiffHighlightId(fileHash, 1, 2, true), 7, 2);
    }

    private PullRequest prWithHead(String sha) {
        PullRequest pr = mock(PullRequest.class);
        PullRequestMarker head = mock(PullRequestMarker.class);
        when(head.sha()).thenReturn(sha);
        when(pr.head()).thenReturn(head);
        return pr;
    }

    private void stubPr(PullRequestService service, PullRequest pr) {
        when(service.getPullRequest(anyString(), anyString(), anyLong()))
                .thenReturn(Single.just(Response.success(pr)));
    }

    private void stubFiles(PullRequestService service, GitHubFile... files) {
        when(service.getPullRequestFiles(anyString(), anyString(), anyLong(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<GitHubFile>builder().items(Arrays.asList(files)).build())));
    }

    @Test
    public void getLaunchIntent_opensPullRequestDiffViewer() {
        FragmentActivity activity = activity();
        Intent intent = task(activity, "hash").getLaunchIntent("sha1",
                file("src/Foo.java"), new DiffHighlightId("hash", 1, 2, true));
        assertEquals(PullRequestDiffViewerActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void getFallbackIntent_opensPullRequestActivity() {
        FragmentActivity activity = activity();
        Intent intent = task(activity, "hash").getFallbackIntent("sha1");
        assertEquals(PullRequestActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void getSha_returnsHeadSha() {
        PullRequestService service = mock(PullRequestService.class);
        stubPr(service, prWithHead("headsha"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestService.class), eq(false)))
                    .thenReturn(service);
            assertEquals("headsha", task(activity(), "hash").getSha().blockingGet());
        }
    }

    @Test
    public void getSha_nullHead_throwsIllegalState() {
        PullRequestService service = mock(PullRequestService.class);
        PullRequest pr = mock(PullRequest.class);
        when(pr.head()).thenReturn(null);
        stubPr(service, pr);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestService.class), eq(false)))
                    .thenReturn(service);
            try {
                task(activity(), "hash").getSha().blockingGet();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("Pull request head sha is missing", e.getMessage());
            }
        }
    }

    @Test
    public void getSha_nullHeadSha_throwsIllegalState() {
        PullRequestService service = mock(PullRequestService.class);
        PullRequest pr = mock(PullRequest.class);
        PullRequestMarker head = mock(PullRequestMarker.class);
        when(head.sha()).thenReturn(null);
        when(pr.head()).thenReturn(head);
        stubPr(service, pr);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(PullRequestService.class), eq(false)))
                    .thenReturn(service);
            try {
                task(activity(), "hash").getSha().blockingGet();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("Pull request head sha is missing", e.getMessage());
            }
        }
    }

    @Test
    public void getFiles_returnsPullRequestFiles() {
        PullRequestService service = mock(PullRequestService.class);
        stubFiles(service, file("a.java"), file("b.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(PullRequestService.class), eq(false))).thenReturn(service);
            assertEquals(2, task(activity(), "hash").getFiles().blockingGet().size());
        }
    }

    private void installBoth(MockedStatic<ServiceFactory> mocked, PullRequestService service) {
        mocked.when(() -> ServiceFactory.get(eq(PullRequestService.class), eq(false)))
                .thenReturn(service);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                eq(PullRequestService.class), eq(false))).thenReturn(service);
    }

    @Test
    public void getSingle_matchingFile_opensDiffViewer() {
        FragmentActivity activity = activity();
        PullRequestService service = mock(PullRequestService.class);
        stubPr(service, prWithHead("headsha"));
        stubFiles(service, file("src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            installBoth(mocked, service);
            Optional<Intent> result = task(activity,
                    ApiHelpers.sha256Of("src/Foo.java")).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(PullRequestDiffViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_matchingImageFile_opensFileViewer() {
        FragmentActivity activity = activity();
        PullRequestService service = mock(PullRequestService.class);
        stubPr(service, prWithHead("headsha"));
        stubFiles(service, file("img.png"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            installBoth(mocked, service);
            Optional<Intent> result = task(activity,
                    ApiHelpers.sha256Of("img.png")).getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_noMatchingFile_opensPullRequestActivity() {
        FragmentActivity activity = activity();
        PullRequestService service = mock(PullRequestService.class);
        stubPr(service, prWithHead("headsha"));
        stubFiles(service, file("src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            installBoth(mocked, service);
            Optional<Intent> result = task(activity, "nomatch").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(PullRequestActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_emptyFiles_opensPullRequestActivity() {
        FragmentActivity activity = activity();
        PullRequestService service = mock(PullRequestService.class);
        stubPr(service, prWithHead("headsha"));
        when(service.getPullRequestFiles(anyString(), anyString(), anyLong(), eq(1L)))
                .thenReturn(Single.just(Response.success(
                        Page.<GitHubFile>builder().items(Collections.emptyList()).build())));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            installBoth(mocked, service);
            Optional<Intent> result = task(activity, "nomatch").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(PullRequestActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_nullHeadSha_propagatesIllegalState() {
        FragmentActivity activity = activity();
        PullRequestService service = mock(PullRequestService.class);
        PullRequest pr = mock(PullRequest.class);
        when(pr.head()).thenReturn(null);
        stubPr(service, pr);
        stubFiles(service, file("src/Foo.java"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            installBoth(mocked, service);
            try {
                task(activity, "hash").getSingle().blockingGet();
                fail("expected IllegalStateException");
            } catch (IllegalStateException e) {
                assertEquals("Pull request head sha is missing", e.getMessage());
            }
        }
    }
}
