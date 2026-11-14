package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.ApiRequestException;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.CommitCompare;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommitService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Response;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

/** CommitCompareFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class CommitCompareFragmentTest {

    public static class NoLoad extends CommitCompareFragment {
        boolean useRealSingle;

        @Override
        protected List<Commit> onGetInitialData() {
            return null;
        }

        @Override
        protected io.reactivex.Observable<List<Commit>> onCreateDataObservable(
                boolean bypassCache) {
            return null;
        }

        @Override
        protected Single<List<Commit>> onCreateDataSingle(boolean bypassCache) {
            if (useRealSingle) {
                return super.onCreateDataSingle(bypassCache);
            }
            return Single.never();
        }

        /** Calls the real onCreateDataSingle (bypassing the attach stub). */
        Single<List<Commit>> realSingle(boolean bypassCache) {
            useRealSingle = true;
            try {
                return onCreateDataSingle(bypassCache);
            } finally {
                useRealSingle = false;
            }
        }
    }

    private static NoLoad attached(String owner, String repo, String base, String head) {
        NoLoad f = new NoLoad();
        f.setArguments(CommitCompareFragment.newInstance(owner, repo, base, head)
                .getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    private static NoLoad attachedFull(String owner, String repo, int pr,
            String baseLabel, String base, String headLabel, String head) {
        NoLoad f = new NoLoad();
        f.setArguments(CommitCompareFragment.newInstance(
                owner, repo, pr, baseLabel, base, headLabel, head).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_shortForm_storesRefsAndDefaultPr() {
        CommitCompareFragment f = CommitCompareFragment.newInstance(
                "o", "r", "main", "feature");
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
        assertEquals("main", f.getArguments().getString("base"));
        assertEquals("feature", f.getArguments().getString("head"));
        assertEquals(-1, f.getArguments().getInt("pr"));
        assertEquals(null, f.getArguments().getString("base_label"));
        assertEquals(null, f.getArguments().getString("head_label"));
    }

    @Test
    public void newInstance_fullForm_storesAllArgs() {
        CommitCompareFragment f = CommitCompareFragment.newInstance(
                "o", "r", 12, "base-lbl", "main", "head-lbl", "feature");
        assertEquals(12, f.getArguments().getInt("pr"));
        assertEquals("base-lbl", f.getArguments().getString("base_label"));
        assertEquals("main", f.getArguments().getString("base"));
        assertEquals("head-lbl", f.getArguments().getString("head_label"));
        assertEquals("feature", f.getArguments().getString("head"));
    }

    @Test
    public void getEmptyTextResId_isNoCommits() {
        assertEquals(R.string.no_commits_found,
                attached("o", "r", "main", "feature").getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("o", "r", "main", "feature").onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_nullBase_returnsEmptyList() {
        NoLoad f = attached("o", "r", null, "feature");
        TestObserver<List<Commit>> observer = f.realSingle(false).test();
        observer.assertValue(list -> list.isEmpty());
        observer.assertComplete();
    }

    @Test
    public void onCreateDataSingle_nullHead_returnsEmptyList() {
        NoLoad f = attached("o", "r", "main", null);
        TestObserver<List<Commit>> observer = f.realSingle(false).test();
        observer.assertValue(list -> list.isEmpty());
        observer.assertComplete();
    }

    @Test
    public void onCreateDataSingle_bothRefs_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attachedFull("o", "r", -1, null, "main", null, "feature");
        assertNotNull(f.realSingle(false));
        assertNotNull(f.realSingle(true));
    }

    @Test
    public void onItemClick_launchesCommitActivity() {
        NoLoad f = attachedFull("o", "r", 12, "b", "main", "h", "feature");
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        f.onItemClick(Commit.builder().sha("abc123").build());
        android.content.Intent started =
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.CommitActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals("abc123", started.getExtras().getString("sha"));
    }

    // ---- ActivityResultLauncher: commit viewer returns ----

    private static void deliverLauncherResult(NoLoad f, int resultCode) throws Exception {
        FragmentActivity activity = f.getActivity();
        Field launcherField = CommitCompareFragment.class.getDeclaredField("mCommitLauncher");
        launcherField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ActivityResultLauncher<Intent> launcher =
                (ActivityResultLauncher<Intent>) launcherField.get(f);
        Intent intent = new Intent();
        launcher.launch(intent);
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(started);
        Shadows.shadowOf(activity).callOnActivityResult(
                started.requestCode, resultCode, new Intent());
    }

    private static Object listSubscription(NoLoad f) throws Exception {
        Field field = ListDataBaseFragment.class.getDeclaredField("mSubscription");
        field.setAccessible(true);
        return field.get(f);
    }

    @Test
    public void commitLauncher_resultOk_refreshesList() throws Exception {
        NoLoad f = attached("o", "r", "base", "head");
        Object before = listSubscription(f);
        assertNotNull(before);
        deliverLauncherResult(f, Activity.RESULT_OK);
        Object after = listSubscription(f);
        assertNotNull(after);
        assertTrue(before != after);
    }

    @Test
    public void commitLauncher_resultCanceled_doesNotRefresh() throws Exception {
        NoLoad f = attached("o", "r", "base", "head");
        Object before = listSubscription(f);
        deliverLauncherResult(f, Activity.RESULT_CANCELED);
        assertEquals(before, listSubscription(f));
    }

    // ---- onCreateDataSingle with a mocked RepositoryCommitService ----

    private static ApiRequestException apiError(int status) {
        return new ApiRequestException(
                Response.error(status, ResponseBody.create(null, "")));
    }

    private static CommitCompare compareWith(List<Commit> commits) {
        CommitCompare compare = mock(CommitCompare.class);
        when(compare.commits()).thenReturn(commits);
        return compare;
    }

    @Test
    public void realSingle_success_returnsCommits() {
        NoLoad f = attached("o", "r", "main", "feature");
        Commit c1 = Commit.builder().sha("a").build();
        Commit c2 = Commit.builder().sha("b").build();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            CommitCompare compare = compareWith(Arrays.asList(c1, c2));
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.just(Response.success(compare)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList(c1, c2))
                    .assertComplete();
        }
    }

    @Test
    public void realSingle_404WithLabels_retriesWithLabels() {
        NoLoad f = attachedFull("o", "r", -1, "base-lbl", "main", "head-lbl", "feature");
        Commit c1 = Commit.builder().sha("x").build();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.error(apiError(404)));
            CommitCompare compare = compareWith(Arrays.asList(c1));
            when(service.compareCommits("o", "r", "base-lbl", "head-lbl"))
                    .thenReturn(Single.just(Response.success(compare)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList(c1))
                    .assertComplete();
            verify(service).compareCommits("o", "r", "main", "feature");
            verify(service).compareCommits("o", "r", "base-lbl", "head-lbl");
        }
    }

    @Test
    public void realSingle_404WithoutLabels_mapsToEmptyList() {
        NoLoad f = attached("o", "r", "main", "feature");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.error(apiError(404)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList())
                    .assertComplete();
            verify(service, times(1)).compareCommits("o", "r", "main", "feature");
        }
    }

    @Test
    public void realSingle_non404Error_propagates() {
        NoLoad f = attached("o", "r", "main", "feature");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.error(apiError(500)));
            f.realSingle(false).test()
                    .assertError(ApiRequestException.class);
            verify(service, times(1)).compareCommits("o", "r", "main", "feature");
        }
    }

    @Test
    public void realSingle_404RetryAlso404_mapsToEmptyList() {
        NoLoad f = attachedFull("o", "r", -1, "base-lbl", "main", "head-lbl", "feature");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.error(apiError(404)));
            when(service.compareCommits("o", "r", "base-lbl", "head-lbl"))
                    .thenReturn(Single.error(apiError(404)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList())
                    .assertComplete();
            verify(service).compareCommits("o", "r", "base-lbl", "head-lbl");
        }
    }

    @Test
    public void realSingle_baseNull_returnsEmptyList() {
        NoLoad f = attached("o", "r", null, "feature");
        f.realSingle(false).test()
                .assertValue(Arrays.asList())
                .assertComplete();
    }

    @Test
    public void realSingle_headNull_returnsEmptyList() {
        NoLoad f = attached("o", "r", "main", null);
        f.realSingle(false).test()
                .assertValue(Arrays.asList())
                .assertComplete();
    }

    @Test
    public void realSingle_nonApiError_propagatesWithoutRetry() {
        NoLoad f = attached("o", "r", "main", "feature");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.error(new java.io.IOException("boom")));
            f.realSingle(false).test()
                    .assertError(java.io.IOException.class);
            verify(service, times(1)).compareCommits("o", "r", "main", "feature");
        }
    }

    @Test
    public void realSingle_404WithBaseLabelOnly_mapsToEmptyList() {
        // base label set but head label null: the retry guard's third
        // operand is false, so no label retry happens.
        NoLoad f = attachedFull("o", "r", -1, "base-lbl", "main", null, "feature");
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            RepositoryCommitService service = mock(RepositoryCommitService.class);
            mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                    .thenReturn(service);
            when(service.compareCommits("o", "r", "main", "feature"))
                    .thenReturn(Single.error(apiError(404)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList())
                    .assertComplete();
            verify(service, times(1)).compareCommits("o", "r", "main", "feature");
        }
    }
}
