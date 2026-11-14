package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.ServiceFactory;
import com.gh4a.activities.FileViewerActivity;
import com.gh4a.activities.RepositoryActivity;
import com.meisolsson.githubsdk.model.Branch;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.service.repositories.RepositoryBranchService;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;

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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class RefPathDisambiguationTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/tree/master/README.md");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private Branch branch(String name) {
        Branch b = mock(Branch.class);
        when(b.name()).thenReturn(name);
        return b;
    }

    private static class Fixture {
        final RepositoryBranchService branchService = mock(RepositoryBranchService.class);
        final RepositoryService repoService = mock(RepositoryService.class);

        void stubBranches(Branch... branches) {
            when(branchService.getBranches(anyString(), anyString(), eq(1L)))
                    .thenReturn(Single.just(Response.success(
                            Page.<Branch>builder().items(Arrays.asList(branches)).build())));
        }

        void stubBranchesEmpty() {
            when(branchService.getBranches(anyString(), anyString(), eq(1L)))
                    .thenReturn(Single.just(Response.success(
                            Page.<Branch>builder().items(Collections.emptyList()).build())));
        }

        void stubTags(Branch... tags) {
            when(repoService.getTags(anyString(), anyString(), eq(1L)))
                    .thenReturn(Single.just(Response.success(
                            Page.<Branch>builder().items(Arrays.asList(tags)).build())));
        }

        void install(MockedStatic<ServiceFactory> mocked) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(RepositoryBranchService.class), eq(false))).thenReturn(branchService);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(RepositoryService.class), eq(false))).thenReturn(repoService);
        }
    }

    private RefPathDisambiguationTask repoTask(FragmentActivity activity, String refAndPath) {
        return new RefPathDisambiguationTask(activity, URL, "octo", "cat", refAndPath, 0);
    }

    private RefPathDisambiguationTask fileTask(FragmentActivity activity, String refAndPath,
            String fragment) {
        return new RefPathDisambiguationTask(activity, URL, "octo", "cat", refAndPath, fragment);
    }

    @Test
    public void constructor_pageVersion_storesFields() {
        FragmentActivity activity = activity();
        RefPathDisambiguationTask t = repoTask(activity, "master/README.md");
        assertEquals("octo", t.mRepoOwner);
        assertEquals("cat", t.mRepoName);
        assertEquals("master/README.md", t.mRefAndPath);
        assertEquals(0, t.mInitialPage);
        assertNull(t.mFragment);
        assertFalse(t.mGoToFileViewer);
    }

    @Test
    public void constructor_fragmentVersion_storesFields() {
        FragmentActivity activity = activity();
        RefPathDisambiguationTask t = fileTask(activity, "master/README.md", "L12");
        assertEquals("L12", t.mFragment);
        assertEquals(-1, t.mInitialPage);
        assertTrue(t.mGoToFileViewer);
    }

    @Test
    public void getSingle_headRef_opensRepositoryWithoutServiceCall() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity, "HEAD").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
            verify(f.branchService, never()).getBranches(anyString(), anyString(), anyLong());
        }
    }

    @Test
    public void getSingle_headWithPath_opensRepository() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity, "HEAD/docs/guide.md")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_sha1Ref_opensRepositoryWithoutServiceCall() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        String sha = "a".repeat(40);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity, sha + "/src/Foo.java")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
            verify(f.branchService, never()).getBranches(anyString(), anyString(), anyLong());
        }
    }

    @Test
    public void getSingle_sha1WithoutPath_opensRepository() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        String sha = "0123456789abcdef0123456789abcdef01234567";
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertTrue(repoTask(activity, sha).getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_uppercaseSha1_isNotRecognizedAsSha() {
        // SHA1_PATTERN is [a-z0-9]{40}: an uppercase SHA falls through to
        // the branch/tag lookup instead of the fast path.
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("ABCDEF0123456789ABCDEF0123456789ABCDEF01"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity,
                    "ABCDEF0123456789ABCDEF0123456789ABCDEF01").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_branchWithSlash_matchesLongestRef() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("feature/x"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity, "feature/x/README.md")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_branchExactMatch_noPath() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("main"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertTrue(repoTask(activity, "main").getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_tagFallbackWhenNoBranchMatches() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranchesEmpty();
        f.stubTags(branch("v1.0"));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity, "v1.0").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_noBranchOrTagMatch_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranchesEmpty();
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(repoTask(activity, "nope/nothing").getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_branchServiceError_propagatesError() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        when(f.branchService.getBranches(anyString(), anyString(), eq(1L)))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            try {
                repoTask(activity, "master/x").getSingle().blockingGet();
                fail("expected error");
            } catch (RuntimeException e) {
                assertEquals("down", e.getMessage());
            }
        }
    }

    @Test
    public void getSingle_fileViewer_singleLineFragment_opensFileViewer() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("master"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = fileTask(activity, "master/README.md", "L12")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_fileViewer_lineRangeFragment_opensFileViewer() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("master"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = fileTask(activity, "master/README.md", "L12-L14")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_fileViewer_badFragment_stillOpensFileViewer() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("master"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = fileTask(activity, "master/README.md", "Lxx")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_fileViewer_nullFragment_opensFileViewer() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("master"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = fileTask(activity, "master/README.md", null)
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_fileViewer_headWithoutPath_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(fileTask(activity, "HEAD", "L12").getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_fileViewer_noMatch_returnsEmpty() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranchesEmpty();
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            assertFalse(fileTask(activity, "nope/file.txt", "L1")
                    .getSingle().blockingGet().isPresent());
        }
    }

    @Test
    public void getSingle_branchListWithDecoyFirst_matchesSecondBranch() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("other"), branch("master"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = repoTask(activity, "master").getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(RepositoryActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

    @Test
    public void getSingle_fileViewer_nonLineFragment_opensFileViewerWithoutHighlight() {
        FragmentActivity activity = activity();
        Fixture f = new Fixture();
        f.stubBranches(branch("master"));
        f.stubTags();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            f.install(mocked);
            Optional<Intent> result = fileTask(activity, "master/README.md", "README")
                    .getSingle().blockingGet();
            assertTrue(result.isPresent());
            assertEquals(FileViewerActivity.class.getName(),
                    result.get().getComponent().getClassName());
        }
    }

}
