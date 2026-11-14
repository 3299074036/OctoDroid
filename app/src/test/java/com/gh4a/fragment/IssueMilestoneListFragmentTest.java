package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
import com.meisolsson.githubsdk.model.Milestone;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.service.issues.IssueMilestoneService;

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

/** IssueMilestoneListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class IssueMilestoneListFragmentTest {

    public static class NoLoad extends IssueMilestoneListFragment {
        boolean useRealSingle;

        @Override
        protected List<Milestone> onGetInitialData() {
            return null;
        }

        @Override
        protected io.reactivex.Observable<List<Milestone>> onCreateDataObservable(
                boolean bypassCache) {
            return null;
        }

        @Override
        protected Single<List<Milestone>> onCreateDataSingle(boolean bypassCache) {
            if (useRealSingle) {
                return super.onCreateDataSingle(bypassCache);
            }
            return Single.never();
        }

        /** Calls the real onCreateDataSingle (bypassing the attach stub). */
        Single<List<Milestone>> realSingle(boolean bypassCache) {
            useRealSingle = true;
            try {
                return onCreateDataSingle(bypassCache);
            } finally {
                useRealSingle = false;
            }
        }
    }

    private static NoLoad attached(String owner, String repo,
            boolean closed, boolean fromPr) {
        NoLoad f = new NoLoad();
        f.setArguments(IssueMilestoneListFragment.newInstance(owner, repo, closed, fromPr)
                .getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesAllArgs() {
        IssueMilestoneListFragment f = IssueMilestoneListFragment.newInstance(
                "o", "r", true, true);
        assertEquals("o", f.getArguments().getString("owner"));
        assertEquals("r", f.getArguments().getString("repo"));
        assertTrue(f.getArguments().getBoolean("closed"));
        assertTrue(f.getArguments().getBoolean("from_pr"));
        IssueMilestoneListFragment g = IssueMilestoneListFragment.newInstance(
                "o", "r", false, false);
        assertFalse(g.getArguments().getBoolean("closed"));
        assertFalse(g.getArguments().getBoolean("from_pr"));
    }

    @Test
    public void getEmptyTextResId_closedVsOpen() {
        assertEquals(R.string.no_closed_milestones_found,
                attached("o", "r", true, false).getEmptyTextResId());
        assertEquals(R.string.no_open_milestones_found,
                attached("o", "r", false, false).getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("o", "r", false, false).onCreateAdapter());
        assertNotNull(attached("o", "r", true, true).onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_open_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("o", "r", false, false);
        assertNotNull(f.realSingle(false));
    }

    @Test
    public void onCreateDataSingle_closed_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("o", "r", true, false);
        Single<List<Milestone>> single = f.realSingle(false);
        assertNotNull(single);
        assertNotNull(f.realSingle(true));
    }

    @Test
    public void onItemClick_launchesEditActivity() {
        NoLoad f = attached("o", "r", false, true);
        androidx.fragment.app.FragmentActivity activity = f.getActivity();
        Milestone milestone = Milestone.builder().number(3).title("v1.0").build();
        f.onItemClick(milestone);
        android.content.Intent started =
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.IssueMilestoneEditActivity.class.getName(),
                started.getComponent().getClassName());
    }

    // ---- ActivityResultLauncher: milestone editor returns ----

    private static void deliverLauncherResult(NoLoad f, int resultCode) throws Exception {
        FragmentActivity activity = f.getActivity();
        Field launcherField =
                IssueMilestoneListFragment.class.getDeclaredField("mEditMilestoneLauncher");
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

    @Test
    public void editMilestoneLauncher_resultOk_refreshesAndSetsActivityResult()
            throws Exception {
        NoLoad f = attached("o", "r", false, false);
        deliverLauncherResult(f, Activity.RESULT_OK);
        assertEquals(Activity.RESULT_OK, Shadows.shadowOf(f.getActivity()).getResultCode());
    }

    @Test
    public void editMilestoneLauncher_resultCanceled_doesNotSetResult() throws Exception {
        NoLoad f = attached("o", "r", false, false);
        deliverLauncherResult(f, Activity.RESULT_CANCELED);
        assertEquals(Activity.RESULT_CANCELED,
                Shadows.shadowOf(f.getActivity()).getResultCode());
    }

    // ---- onCreateDataSingle with a mocked IssueMilestoneService ----

    @SuppressWarnings("unchecked")
    private static Page<Milestone> page(List<Milestone> items, Integer next) {
        Page<Milestone> p = mock(Page.class);
        when(p.items()).thenReturn(items);
        when(p.next()).thenReturn(next);
        return p;
    }

    private static Milestone milestone(int number) {
        return Milestone.builder().number(number).title("m" + number).build();
    }

    @Test
    public void realSingle_singlePage_returnsAllMilestones() {
        NoLoad f = attached("o", "r", false, false);
        Milestone m1 = milestone(1);
        Milestone m2 = milestone(2);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueMilestoneService service = mock(IssueMilestoneService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueMilestoneService.class, false)).thenReturn(service);
            Page<Milestone> page1 = page(Arrays.asList(m1, m2), null);
            when(service.getRepositoryMilestones("o", "r", "open", 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList(m1, m2))
                    .assertComplete();
            verify(service).getRepositoryMilestones("o", "r", "open", 1L);
        }
    }

    @Test
    public void realSingle_twoPages_concatenatesItems() {
        NoLoad f = attached("o", "r", false, false);
        Milestone m1 = milestone(1);
        Milestone m2 = milestone(2);
        Milestone m3 = milestone(3);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueMilestoneService service = mock(IssueMilestoneService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueMilestoneService.class, false)).thenReturn(service);
            Page<Milestone> page1 = page(Arrays.asList(m1, m2), 2);
            Page<Milestone> page2 = page(Arrays.asList(m3), null);
            when(service.getRepositoryMilestones("o", "r", "open", 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            when(service.getRepositoryMilestones("o", "r", "open", 2L))
                    .thenReturn(Single.just(Response.success(page2)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList(m1, m2, m3))
                    .assertComplete();
            verify(service).getRepositoryMilestones("o", "r", "open", 2L);
        }
    }

    @Test
    public void realSingle_closedState_requestsClosedMilestones() {
        NoLoad f = attached("o", "r", true, false);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueMilestoneService service = mock(IssueMilestoneService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueMilestoneService.class, false)).thenReturn(service);
            Page<Milestone> page1 = page(Arrays.asList(), null);
            when(service.getRepositoryMilestones("o", "r", "closed", 1L))
                    .thenReturn(Single.just(Response.success(page1)));
            f.realSingle(false).test()
                    .assertValue(Arrays.asList())
                    .assertComplete();
            verify(service).getRepositoryMilestones("o", "r", "closed", 1L);
        }
    }

    @Test
    public void realSingle_pageError_propagates() {
        NoLoad f = attached("o", "r", false, false);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            IssueMilestoneService service = mock(IssueMilestoneService.class);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    IssueMilestoneService.class, false)).thenReturn(service);
            when(service.getRepositoryMilestones("o", "r", "open", 1L))
                    .thenReturn(Single.error(new ApiRequestException(
                            Response.error(500, ResponseBody.create(null, "")))));
            f.realSingle(false).test()
                    .assertError(ApiRequestException.class);
        }
    }
}
