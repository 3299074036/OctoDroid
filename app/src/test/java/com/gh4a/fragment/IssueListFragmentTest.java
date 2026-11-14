package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.PullRequestActivity;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;

import io.reactivex.Single;
import retrofit2.Response;

/** IssueListFragment (F1) + its SortDrawerHelper. */
@RunWith(RobolectricTestRunner.class)
public class IssueListFragmentTest {

    public static class NoLoad extends IssueListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }

        /** loadPage that never emits: onRefresh stays offline. */
        public static class NoLoadNeverPage extends NoLoad {
            @Override
            protected Single<Response<Page<Issue>>> loadPage(
                    int page, boolean bypassCache) {
                return Single.never();
            }
        }
    }

    private static NoLoad attached(String query, String sortMode, String order,
            String state, int emptyText, boolean showRepo) {
        NoLoad f = new NoLoad();
        f.setArguments(IssueListFragment.newInstance(
                query, sortMode, order, state, emptyText, showRepo).getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesAllArgs() {
        IssueListFragment f = IssueListFragment.newInstance(
                "is:open", "created", "desc", "open", R.string.no_issues_found, true);
        assertEquals("is:open", f.getArguments().getString("query"));
        assertEquals("created", f.getArguments().getString("sortmode"));
        assertEquals("desc", f.getArguments().getString("order"));
        assertEquals("open", f.getArguments().getString("state"));
        assertEquals(R.string.no_issues_found, f.getArguments().getInt("emptytext"));
        assertTrue(f.getArguments().getBoolean("withrepo"));
    }

    @Test
    public void getEmptyTextResId_returnsConfiguredResId() {
        assertEquals(R.string.no_issues_found,
                attached("q", "created", "desc", "open",
                        R.string.no_issues_found, false).getEmptyTextResId());
    }

    @Test
    public void onViewCreated_nullState_defaultsToOpenWithoutCrash() {
        // 167: missing "state" arg must not NPE the switch.
        NoLoad f = new NoLoad();
        Bundle args = new Bundle();
        args.putString("query", "q");
        args.putString("sortmode", "created");
        args.putString("order", "desc");
        args.putInt("emptytext", R.string.no_issues_found);
        f.setArguments(args);
        FragmentTestHelper.attach(f);
        assertNotNull(f.getView());
    }

    @Test
    public void onViewCreated_closedAndMergedStates_setHighlightColors() {
        assertNotNull(attached("q", "created", "desc", "closed",
                R.string.no_issues_found, false).getView());
        assertNotNull(attached("q", "created", "desc", "merged",
                R.string.no_issues_found, false).getView());
        assertNotNull(attached("q", "created", "desc", "open",
                R.string.no_issues_found, false).getView());
    }

    @Test
    public void loadPage_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("is:open", "created", "desc", "open",
                R.string.no_issues_found, false);
        Single<Response<Page<Issue>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onCreateAdapter_withRepo_returnsRepositoryIssueAdapter() {
        assertNotNull(attached("q", "created", "desc", "open",
                R.string.no_issues_found, true).onCreateAdapter());
    }

    @Test
    public void onCreateAdapter_withoutRepo_returnsIssueAdapter() {
        assertNotNull(attached("q", "created", "desc", "open",
                R.string.no_issues_found, false).onCreateAdapter());
    }

    @Test
    public void onItemClick_nullIssue_doesNothing() {
        NoLoad f = attached("q", "created", "desc", "open",
                R.string.no_issues_found, false);
        FragmentActivity activity = f.getActivity();
        f.onItemClick(null);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_issueWithoutPr_launchesIssueActivity() {
        NoLoad f = attached("q", "created", "desc", "open",
                R.string.no_issues_found, false);
        FragmentActivity activity = f.getActivity();
        Issue issue = Issue.builder().number(42)
                .url("https://api.github.com/repos/o/r/issues/42").build();
        f.onItemClick(issue);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(IssueActivity.class.getName(), started.getComponent().getClassName());
        assertEquals(42, started.getExtras().getInt("number"));
    }

    @Test
    public void onItemClick_issueWithPr_launchesPullRequestActivity() {
        NoLoad f = attached("q", "created", "desc", "open",
                R.string.no_issues_found, false);
        FragmentActivity activity = f.getActivity();
        Issue issue = Issue.builder().number(7)
                .url("https://api.github.com/repos/o/r/issues/7")
                .pullRequest(PullRequest.builder().build()).build();
        f.onItemClick(issue);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(PullRequestActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals(7, started.getExtras().getInt("number"));
    }

    // ---- SortDrawerHelper ----

    @Test
    public void sortHelper_defaultModeIsCreatedDesc() {
        IssueListFragment.SortDrawerHelper helper =
                new IssueListFragment.SortDrawerHelper();
        assertEquals("created", helper.getSortMode());
        assertEquals("desc", helper.getSortOrder());
        assertEquals(R.menu.issue_list_sort,
                IssueListFragment.SortDrawerHelper.getMenuResId());
    }

    @Test
    public void sortHelper_setSortMode_validPairUpdates() {
        IssueListFragment.SortDrawerHelper helper =
                new IssueListFragment.SortDrawerHelper();
        helper.setSortMode("updated", "asc");
        assertEquals("updated", helper.getSortMode());
        assertEquals("asc", helper.getSortOrder());
        helper.setSortMode("comments", "desc");
        assertEquals("comments", helper.getSortMode());
        assertEquals("desc", helper.getSortOrder());
    }

    @Test
    public void sortHelper_setSortMode_invalidPairKeepsCurrent() {
        IssueListFragment.SortDrawerHelper helper =
                new IssueListFragment.SortDrawerHelper();
        helper.setSortMode("bogus", "asc");
        assertEquals("created", helper.getSortMode());
        assertEquals("desc", helper.getSortOrder());
        helper.setSortMode("created", "sideways");
        assertEquals("created", helper.getSortMode());
        assertEquals("desc", helper.getSortOrder());
    }

    public static class OutOfLookupHelper extends IssueListFragment.SortDrawerHelper {
        void setRaw(String mode, boolean ascending) {
            updateSortMode(mode, ascending);
        }
    }

    @Test
    public void sortHelper_updateMenuCheckState_unknownMode_checksNothing() {
        OutOfLookupHelper helper = new OutOfLookupHelper();
        helper.setRaw("bogus", false);
        android.view.Menu menu = mock(android.view.Menu.class);
        helper.updateMenuCheckState(menu);
        verify(menu, never()).findItem(anyInt());
    }

    @Test
    public void sortHelper_handleItemSelection_knownItemUpdatesAndReturnsTrue() {
        IssueListFragment.SortDrawerHelper helper =
                new IssueListFragment.SortDrawerHelper();
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.sort_updated_asc);
        assertTrue(helper.handleItemSelection(item));
        assertEquals("updated", helper.getSortMode());
        assertEquals("asc", helper.getSortOrder());
    }

    @Test
    public void sortHelper_handleItemSelection_unknownItemReturnsFalse() {
        IssueListFragment.SortDrawerHelper helper =
                new IssueListFragment.SortDrawerHelper();
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(999999);
        assertFalse(helper.handleItemSelection(item));
        assertEquals("created", helper.getSortMode());
    }

    @Test
    public void sortHelper_updateMenuCheckState_checksCurrentEntry() {
        IssueListFragment.SortDrawerHelper helper =
                new IssueListFragment.SortDrawerHelper();
        helper.setSortMode("comments", "desc");
        NoLoad f = attached("q", "created", "desc", "open",
                R.string.no_issues_found, false);
        Menu menu = new MenuBuilder(f.getActivity());
        f.getActivity().getMenuInflater().inflate(R.menu.issue_list_sort, menu);
        helper.updateMenuCheckState(menu);
        assertTrue(menu.findItem(R.id.sort_comments_desc).isChecked());
        assertFalse(menu.findItem(R.id.sort_created_desc).isChecked());
    }

    // ---- ActivityResultLauncher: issue editor returns ----

    private static void deliverLauncherResult(NoLoad f, int resultCode) throws Exception {
        FragmentActivity activity = f.getActivity();
        Field launcherField = IssueListFragment.class.getDeclaredField("mIssueLauncher");
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

    private static Object pagedSubscription(NoLoad f) throws Exception {
        Field field = PagedDataBaseFragment.class.getDeclaredField("mSubscription");
        field.setAccessible(true);
        return field.get(f);
    }

    @Test
    public void issueLauncher_resultOk_refreshesList() throws Exception {
        NoLoad f = new NoLoad.NoLoadNeverPage();
        f.setArguments(IssueListFragment.newInstance(
                "is:open", "created", "desc", "open", R.string.no_issues_found, true)
                .getArguments());
        FragmentTestHelper.attach(f);
        assertNull(pagedSubscription(f));
        deliverLauncherResult(f, Activity.RESULT_OK);
        assertNotNull(pagedSubscription(f));
    }

    @Test
    public void issueLauncher_resultCanceled_doesNotRefresh() throws Exception {
        NoLoad f = new NoLoad.NoLoadNeverPage();
        f.setArguments(IssueListFragment.newInstance(
                "is:open", "created", "desc", "open", R.string.no_issues_found, true)
                .getArguments());
        FragmentTestHelper.attach(f);
        deliverLauncherResult(f, Activity.RESULT_CANCELED);
        assertNull(pagedSubscription(f));
    }
}
