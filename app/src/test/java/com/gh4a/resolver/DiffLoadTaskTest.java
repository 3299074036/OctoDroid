package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.activities.CommitActivity;
import com.gh4a.activities.FileViewerActivity;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.GitHubFile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DiffLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octo/cat/commit/abc");

    static class StubDiffTask extends DiffLoadTask {
        private final List<GitHubFile> mFiles;
        private final String mSha;
        boolean launchCalled;
        boolean fallbackCalled;

        StubDiffTask(FragmentActivity activity, DiffHighlightId diffId,
                List<GitHubFile> files, String sha) {
            super(activity, URL, "octo", "cat", diffId);
            mFiles = files;
            mSha = sha;
        }

        @Override
        protected Single<List<GitHubFile>> getFiles() {
            return Single.just(mFiles);
        }

        @Override
        protected Single<String> getSha() {
            return Single.just(mSha);
        }

        @Override
        protected @NonNull Intent getLaunchIntent(String sha, @NonNull GitHubFile file,
                DiffHighlightId diffId) {
            launchCalled = true;
            return CommitActivity.makeIntent(mActivity, "octo", "cat", sha);
        }

        @Override
        protected @NonNull Intent getFallbackIntent(String sha) {
            fallbackCalled = true;
            return CommitActivity.makeIntent(mActivity, "octo", "cat", sha);
        }
    }

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private GitHubFile file(String filename) {
        GitHubFile f = mock(GitHubFile.class);
        when(f.filename()).thenReturn(filename);
        return f;
    }

    @Test
    public void constructor_storesFields() {
        FragmentActivity activity = activity();
        DiffHighlightId diffId = new DiffHighlightId("h", 1, 2, false);
        StubDiffTask task = new StubDiffTask(activity, diffId,
                Collections.emptyList(), "sha1");
        assertEquals("octo", task.mRepoOwner);
        assertEquals("cat", task.mRepoName);
        assertEquals(diffId, task.mDiffId);
    }

    @Test
    public void getSingle_matchingFile_callsLaunchIntent() {
        FragmentActivity activity = activity();
        StubDiffTask task = new StubDiffTask(activity,
                new DiffHighlightId(ApiHelpers.sha256Of("a.java"), 1, 1, false),
                Arrays.asList(file("a.java")), "sha1");
        Optional<Intent> result = task.getSingle().blockingGet();
        assertTrue(result.isPresent());
        assertTrue(task.launchCalled);
        assertTrue(!task.fallbackCalled);
    }

    @Test
    public void getSingle_hashMatchIsCaseInsensitive() {
        FragmentActivity activity = activity();
        String hash = ApiHelpers.sha256Of("a.java").toUpperCase();
        StubDiffTask task = new StubDiffTask(activity,
                new DiffHighlightId(hash, 1, 1, false),
                Arrays.asList(file("a.java")), "sha1");
        task.getSingle().blockingGet();
        assertTrue(task.launchCalled);
    }

    @Test
    public void getSingle_matchingImageFile_opensFileViewer() {
        // Image branch is hard-coded in DiffLoadTask, not delegated.
        FragmentActivity activity = activity();
        StubDiffTask task = new StubDiffTask(activity,
                new DiffHighlightId(ApiHelpers.sha256Of("pic.png"), 1, 1, false),
                Arrays.asList(file("pic.png")), "sha1");
        Optional<Intent> result = task.getSingle().blockingGet();
        assertTrue(result.isPresent());
        assertEquals(FileViewerActivity.class.getName(),
                result.get().getComponent().getClassName());
        assertTrue(!task.launchCalled);
    }

    @Test
    public void getSingle_noMatch_callsFallbackIntent() {
        FragmentActivity activity = activity();
        StubDiffTask task = new StubDiffTask(activity,
                new DiffHighlightId("nomatch", 1, 1, false),
                Arrays.asList(file("a.java")), "sha1");
        Optional<Intent> result = task.getSingle().blockingGet();
        assertTrue(result.isPresent());
        assertTrue(task.fallbackCalled);
        assertTrue(!task.launchCalled);
    }

    @Test
    public void getSingle_nullFilename_neverMatchesHash() {
        FragmentActivity activity = activity();
        StubDiffTask task = new StubDiffTask(activity,
                new DiffHighlightId("somehash", 1, 1, false),
                Arrays.asList(file(null)), "sha1");
        task.getSingle().blockingGet();
        assertTrue(task.fallbackCalled);
    }

    @Test
    public void getSingle_emptyHash_matchesNullFilename() {
        // sha256Of(null) == "" so a DiffHighlightId with "" matches a null filename.
        FragmentActivity activity = activity();
        StubDiffTask task = new StubDiffTask(activity,
                new DiffHighlightId("", 1, 1, false),
                Arrays.asList(file(null)), "sha1");
        task.getSingle().blockingGet();
        assertTrue(task.launchCalled);
    }
}
