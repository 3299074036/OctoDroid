package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.IssueMilestoneListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class IssueMilestoneListActivityTest {
    private Intent intent(boolean fromPr) {
        return IssueMilestoneListActivity.makeIntent(appContext(), "o", "r", fromPr);
    }

    @Test
    public void makeIntent_setsExtras() {
        Intent i = intent(true);
        assertEquals(IssueMilestoneListActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertTrue(i.getBooleanExtra("from_pr", false));
    }

    @Test
    public void launch_titleAndSubtitle() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        assertEquals(appContext().getString(R.string.issue_milestones),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void getTabTitleResIds_openAndClosed() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        int[] titles = activity.getTabTitleResIds();
        assertEquals(2, titles.length);
        assertEquals(R.string.open, titles[0]);
        assertEquals(R.string.closed, titles[1]);
    }

    @Test
    public void makeFragment_openAndClosed() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        assertTrue(activity.makeFragment(0) instanceof IssueMilestoneListFragment);
        assertTrue(activity.makeFragment(1) instanceof IssueMilestoneListFragment);
    }

    @Test
    public void onFragmentInstantiated_tracksOpenFragment() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        IssueMilestoneListFragment open = (IssueMilestoneListFragment) activity.makeFragment(0);
        IssueMilestoneListFragment closed = (IssueMilestoneListFragment) activity.makeFragment(1);
        activity.onFragmentInstantiated(open, 0);
        assertEquals(open, get(activity, "mOpenFragment"));
        activity.onFragmentInstantiated(closed, 1);
        assertEquals(open, get(activity, "mOpenFragment"));
    }

    @Test
    public void onFragmentDestroyed_clearsOpenFragment() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        IssueMilestoneListFragment open = (IssueMilestoneListFragment) activity.makeFragment(0);
        activity.onFragmentInstantiated(open, 0);
        activity.onFragmentDestroyed(open);
        assertNull(get(activity, "mOpenFragment"));
    }

    @Test
    public void onFragmentDestroyed_otherFragment_keepsOpen() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        IssueMilestoneListFragment open = (IssueMilestoneListFragment) activity.makeFragment(0);
        IssueMilestoneListFragment closed = (IssueMilestoneListFragment) activity.makeFragment(1);
        activity.onFragmentInstantiated(open, 0);
        activity.onFragmentDestroyed(closed);
        assertEquals(open, get(activity, "mOpenFragment"));
    }

    @Test
    public void invalidateFragments_clearsOpenFragment() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        IssueMilestoneListFragment open = (IssueMilestoneListFragment) activity.makeFragment(0);
        activity.onFragmentInstantiated(open, 0);
        activity.invalidateFragments();
        assertNull(get(activity, "mOpenFragment"));
    }

    @Test
    public void navigateUp_goesToIssueListWithPrFlag() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(true));
        Intent up = activity.navigateUp();
        assertEquals(IssueListActivity.class.getName(), up.getComponent().getClassName());
        assertTrue(up.getBooleanExtra("is_pull_request", false));
    }

    @Test
    public void onPageMoved_withoutFab_doesNotCrash() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        assertNull(get(activity, "mCreateFab"));
        activity.onPageMoved(1, 0.5f);
    }

    @Test
    public void getTabHeaderColorAttrs_twoTabs() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        assertEquals(2, activity.getTabHeaderColorAttrs().length);
    }

    private void authorize() {
        com.gh4a.Gh4Application.get().addAccount(
                com.meisolsson.githubsdk.model.User.builder().login("tester").id(1L).build(), "tok");
    }

    @Test
    public void onCreate_authorized_createsFab() {
        authorize();
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        assertNotNull(get(activity, "mCreateFab"));
    }

    @Test
    public void onRecyclerViewCreated_openFragment_setsTag() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        IssueMilestoneListFragment open = mock(IssueMilestoneListFragment.class);
        set(activity, "mOpenFragment", open);
        androidx.recyclerview.widget.RecyclerView rv =
                new androidx.recyclerview.widget.RecyclerView(appContext());
        activity.onRecyclerViewCreated(open, rv);
        assertNotNull(rv.getTag(R.id.FloatingActionButtonScrollEnabled));
    }

    @Test
    public void onRecyclerViewCreated_otherFragment_noTag() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        set(activity, "mOpenFragment", mock(IssueMilestoneListFragment.class));
        androidx.recyclerview.widget.RecyclerView rv =
                new androidx.recyclerview.widget.RecyclerView(appContext());
        activity.onRecyclerViewCreated(mock(IssueMilestoneListFragment.class), rv);
        assertNull(rv.getTag(R.id.FloatingActionButtonScrollEnabled));
    }

    @Test
    public void onPageMoved_withFab_scalesAndHidesAtEnd() {
        authorize();
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        android.view.View fab = (android.view.View) get(activity, "mCreateFab");
        activity.onPageMoved(0, 0f);
        assertEquals(android.view.View.VISIBLE, fab.getVisibility());
        assertEquals(1f, fab.getScaleX(), 0.001f);
        activity.onPageMoved(0, 1f);
        assertEquals(android.view.View.INVISIBLE, fab.getVisibility());
    }

    @Test
    public void onPageMoved_withoutFab_isNoOp() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        assertNull(get(activity, "mCreateFab"));
        activity.onPageMoved(0, 0.5f);
    }

    @Test
    public void onClick_launchesCreateMilestone() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        activity.onClick(new android.view.View(appContext()));
        android.content.Intent next =
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(IssueMilestoneEditActivity.class.getName(),
                next.getComponent().getClassName());
    }

    @Test
    public void createMilestoneLauncher_okResult_refreshesAndSetsResult() {
        IssueMilestoneListActivity activity = launch(IssueMilestoneListActivity.class, intent(false));
        activity.onClick(new android.view.View(appContext()));
        android.content.Intent launched =
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        org.robolectric.Shadows.shadowOf(activity).receiveResult(
                launched, android.app.Activity.RESULT_OK, null);
        assertEquals(android.app.Activity.RESULT_OK,
                org.robolectric.Shadows.shadowOf(activity).getResultCode());
    }
}
