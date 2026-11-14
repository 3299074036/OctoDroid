package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.fragment.IssueListFragment;
import com.gh4a.utils.SingleFactory;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Milestone;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.issues.IssueAssigneeService;
import com.meisolsson.githubsdk.service.issues.IssueLabelService;
import com.meisolsson.githubsdk.service.issues.IssueMilestoneService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import retrofit2.Response;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class IssueListActivityTest {
    private Intent intent(boolean isPr) {
        return IssueListActivity.makeIntent(appContext(), "o", "r", isPr);
    }

    @Test
    public void makeIntent_defaultsToIssues() {
        Intent i = IssueListActivity.makeIntent(appContext(), "o", "r");
        assertEquals(IssueListActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertFalse(i.getBooleanExtra("is_pull_request", true));
    }

    @Test
    public void makeIntent_prMode_setsFlag() {
        assertTrue(intent(true).getBooleanExtra("is_pull_request", false));
    }

    @Test
    public void launch_issueMode_titleAndSubtitle() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertEquals(appContext().getString(R.string.issues),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void launch_prMode_titleIsPullRequests() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(true));
        assertEquals(appContext().getString(R.string.pull_requests),
                activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void navigateUp_goesToRepository() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("o", up.getStringExtra("owner"));
    }

    @Test
    public void getActivityUri_buildsIssuesUrl() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertEquals("https://github.com/o/r/issues", activity.getActivityUri().toString());
    }

    @Test
    public void getIssueState_positions() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertEquals("open", call(activity, "getIssueState",
                new Class<?>[]{int.class}, new Object[]{0}));
        assertEquals("closed", call(activity, "getIssueState",
                new Class<?>[]{int.class}, new Object[]{1}));
        assertEquals("merged", call(activity, "getIssueState",
                new Class<?>[]{int.class}, new Object[]{2}));
        assertEquals("open", call(activity, "getIssueState",
                new Class<?>[]{int.class}, new Object[]{99}));
    }

    @Test
    public void getIssueType_prClosedAddsUnmerged() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(true));
        assertEquals("is:closed is:unmerged", call(activity, "getIssueType",
                new Class<?>[]{int.class}, new Object[]{1}));
        assertEquals("is:open", call(activity, "getIssueType",
                new Class<?>[]{int.class}, new Object[]{0}));
    }

    @Test
    public void getIssueType_issueClosedHasNoUnmerged() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertEquals("is:closed", call(activity, "getIssueType",
                new Class<?>[]{int.class}, new Object[]{1}));
    }

    @Test
    public void buildFilterItem_valueQuotedAndEscaped() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertEquals("label:\"bug\"", call(activity, "buildFilterItem",
                new Class<?>[]{String.class, String.class}, new Object[]{"label", "bug"}));
        assertEquals("label:\"a\\\"b\"", call(activity, "buildFilterItem",
                new Class<?>[]{String.class, String.class}, new Object[]{"label", "a\"b"}));
        assertEquals("", call(activity, "buildFilterItem",
                new Class<?>[]{String.class, String.class}, new Object[]{"label", null}));
        assertEquals("no:label", call(activity, "buildFilterItem",
                new Class<?>[]{String.class, String.class}, new Object[]{"label", ""}));
    }

    @Test
    public void buildParticipatingFilterItem_statuses() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mUserLogin", "alice");
        set(activity, "mSelectedParticipatingStatus", 0);
        assertEquals("", call(activity, "buildParticipatingFilterItem", new Class<?>[]{}, new Object[]{}));
        set(activity, "mSelectedParticipatingStatus", 1);
        assertEquals("involves:alice", call(activity, "buildParticipatingFilterItem", new Class<?>[]{}, new Object[]{}));
        set(activity, "mSelectedParticipatingStatus", 2);
        assertEquals("-involves:alice", call(activity, "buildParticipatingFilterItem", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void isFiltering_reflectsSelections() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertFalse((boolean) call(activity, "isFiltering", new Class<?>[]{}, new Object[]{}));
        set(activity, "mSelectedLabel", "bug");
        assertTrue((boolean) call(activity, "isFiltering", new Class<?>[]{}, new Object[]{}));
        set(activity, "mSelectedLabel", null);
        set(activity, "mSelectedParticipatingStatus", 1);
        assertTrue((boolean) call(activity, "isFiltering", new Class<?>[]{}, new Object[]{}));
        set(activity, "mSelectedParticipatingStatus", 0);
        set(activity, "mSelectedMilestone", "v1");
        assertTrue((boolean) call(activity, "isFiltering", new Class<?>[]{}, new Object[]{}));
        set(activity, "mSelectedMilestone", null);
        set(activity, "mSelectedAssignee", "alice");
        assertTrue((boolean) call(activity, "isFiltering", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void onSaveInstanceState_roundTripsFilterState() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSearchQuery", "crash");
        set(activity, "mSearchMode", true);
        set(activity, "mSearchIsExpanded", true);
        set(activity, "mSelectedMilestone", "v1");
        set(activity, "mSelectedLabel", "bug");
        set(activity, "mSelectedAssignee", "alice");
        set(activity, "mSelectedParticipatingStatus", 2);
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertEquals("crash", out.getString("search_query"));
        assertTrue(out.getBoolean("search_mode"));
        assertTrue(out.getBoolean("search_is_expanded"));
        assertEquals("v1", out.getString("selected_milestone"));
        assertEquals("bug", out.getString("selected_label"));
        assertEquals("alice", out.getString("selected_assignee"));
        assertEquals(2, out.getInt("participating_status"));
    }

    @Test
    public void onQueryTextSubmit_enablesSearchMode() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.onQueryTextSubmit("crash"));
        assertEquals("crash", get(activity, "mSearchQuery"));
        assertTrue((boolean) get(activity, "mSearchMode"));
    }

    @Test
    public void onClose_clearsSearch() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onQueryTextSubmit("crash");
        set(activity, "mSearchIsExpanded", true);
        assertTrue(activity.onClose());
        assertNull(get(activity, "mSearchQuery"));
        assertFalse((boolean) get(activity, "mSearchMode"));
        assertFalse((boolean) get(activity, "mSearchIsExpanded"));
    }

    @Test
    public void onMenuItemActionExpandCollapse_trackState() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        android.view.MenuItem item = new androidx.appcompat.view.menu.MenuBuilder(appContext())
                .add(0, R.id.search, 0, "search");
        assertTrue(activity.onMenuItemActionExpand(item));
        assertTrue((boolean) get(activity, "mSearchIsExpanded"));
        assertTrue(activity.onMenuItemActionCollapse(item));
        assertFalse((boolean) get(activity, "mSearchIsExpanded"));
    }

    @Test
    public void getTabTitleResIds_issueModeHasTwoTabsPrModeThree() {
        IssueListActivity issues = launch(IssueListActivity.class, intent(false));
        assertEquals(2, issues.getTabTitleResIds().length);
        IssueListActivity prs = launch(IssueListActivity.class, intent(true));
        assertEquals(3, prs.getTabTitleResIds().length);
    }

    @Test
    public void makeFragment_positionsReturnIssueListFragments() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.makeFragment(0) instanceof IssueListFragment);
        assertTrue(activity.makeFragment(1) instanceof IssueListFragment);
        assertTrue(activity.makeFragment(2) instanceof IssueListFragment);
    }

    @Test
    public void onActivityResult_createdIssue_startsIssueActivity() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        Intent data = new Intent();
        com.meisolsson.githubsdk.model.Issue issue =
                com.meisolsson.githubsdk.model.Issue.builder().number(5).build();
        data.putExtra("issue", issue);
        activity.onActivityResult(1001, android.app.Activity.RESULT_OK, data);
        Intent next = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(IssueActivity.class.getName(), next.getComponent().getClassName());
        assertEquals(5, next.getIntExtra("number", -1));
    }

    @Test
    public void onActivityResult_canceledCreate_doesNothing() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onActivityResult(1001, android.app.Activity.RESULT_CANCELED, null);
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onActivityResult_nullIssue_doesNothing() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        Intent data = new Intent();
        data.putExtra("issue", (android.os.Parcelable) null);
        activity.onActivityResult(1001, android.app.Activity.RESULT_OK, data);
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
    }

    // ---------- helpers ----------

    private void authorizeAs(String login) {
        // Same retry loop as IssueActivityTest: addAccount is intermittently
        // ineffective across tests in the same class run.
        Gh4Application app = Gh4Application.get();
        for (int i = 0; i < 3
                && (!app.isAuthorized() || !login.equals(app.getAuthLogin())); i++) {
            app.addAccount(User.builder().login(login).id(1L).build(), "tok");
            idleMain(100);
        }
    }

    private void ensureAuthorizedAs(String login) {
        Gh4Application app = Gh4Application.get();
        if (!app.isAuthorized() || !login.equals(app.getAuthLogin())) {
            authorizeAs(login);
        }
    }

    /** Launch after the secure-prefs backend has settled (a first launch),
     * so the authorization is still in effect when onCreate runs. */
    private IssueListActivity launchAuthorized(boolean isPr) throws Exception {
        launch(IssueListActivity.class, intent(isPr));
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        IssueListActivity activity = launch(IssueListActivity.class, intent(isPr));
        assertTrue(Gh4Application.get().isAuthorized());
        return activity;
    }

    private MockedStatic<SingleFactory> mockCollaborator(boolean value) {
        MockedStatic<SingleFactory> mocked = Mockito.mockStatic(SingleFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        mocked.when(() -> SingleFactory.isAppUserRepoCollaborator(anyString(), anyString(), anyBoolean()))
                .thenReturn(Single.just(value));
        return mocked;
    }

    private MockedStatic<ServiceFactory> mockPagedService() {
        return Mockito.mockStatic(ServiceFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
    }

    private MenuItem menuItem(int id) {
        return new MenuBuilder(appContext()).add(0, id, 0, "item");
    }

    private Menu rightDrawerMenu(IssueListActivity activity) {
        Object drawer = get(activity, "mRightDrawer");
        assertNotNull(drawer);
        return ((com.google.android.material.navigation.NavigationView) drawer).getMenu();
    }

    private String drawerItemTitle(IssueListActivity activity, int id) {
        MenuItem item = rightDrawerMenu(activity).findItem(id);
        assertNotNull(item);
        return item.getTitle().toString();
    }

    private Fragment dialogFragment(IssueListActivity activity, String tag) {
        activity.getSupportFragmentManager().executePendingTransactions();
        return activity.getSupportFragmentManager().findFragmentByTag(tag);
    }

    private List<String> dialogEntries(IssueListActivity activity, String tag) {
        Fragment f = dialogFragment(activity, tag);
        assertNotNull(f);
        return f.getArguments().getStringArrayList("entries");
    }

    private int dialogSelected(IssueListActivity activity, String tag) {
        Fragment f = dialogFragment(activity, tag);
        assertNotNull(f);
        return f.getArguments().getInt("selectedPosition");
    }

    private Bundle savedBundle() {
        Bundle b = new Bundle();
        b.putString("search_query", "crash");
        b.putBoolean("search_mode", true);
        b.putBoolean("search_is_expanded", true);
        b.putString("selected_milestone", "v1");
        b.putString("selected_label", "bug");
        b.putString("selected_assignee", "alice");
        b.putInt("participating_status", 2);
        return b;
    }

    // ---------- onCreate / lifecycle ----------

    @Test
    public void onCreate_restoresStateFromBundle() {
        ActivityController<IssueListActivity> c =
                controller(IssueListActivity.class, intent(false));
        IssueListActivity activity = c.create(savedBundle()).start().resume().get();
        assertEquals("crash", get(activity, "mSearchQuery"));
        assertTrue((boolean) get(activity, "mSearchMode"));
        assertTrue((boolean) get(activity, "mSearchIsExpanded"));
        assertEquals("v1", get(activity, "mSelectedMilestone"));
        assertEquals("bug", get(activity, "mSelectedLabel"));
        assertEquals("alice", get(activity, "mSelectedAssignee"));
        assertEquals(2, get(activity, "mSelectedParticipatingStatus"));
    }

    @Test
    public void launch_authorizedIssueMode_showsFab() throws Exception {
        IssueListActivity activity = launchAuthorized(false);
        assertNotNull(get(activity, "mCreateFab"));
        assertEquals(View.VISIBLE,
                ((View) get(activity, "mCreateFab")).getVisibility());
    }

    @Test
    public void launch_authorizedPrMode_noFab() throws Exception {
        IssueListActivity activity = launchAuthorized(true);
        assertNull(get(activity, "mCreateFab"));
    }

    @Test
    public void onRefresh_resetsFilterCaches() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mLabels", new ArrayList<>());
        set(activity, "mMilestones", new ArrayList<>());
        set(activity, "mAssignees", new ArrayList<>());
        set(activity, "mIsCollaborator", true);
        activity.onRefresh();
        assertNull(get(activity, "mLabels"));
        assertNull(get(activity, "mMilestones"));
        assertNull(get(activity, "mAssignees"));
        assertNull(get(activity, "mIsCollaborator"));
    }

    @Test
    public void onRefresh_reloadsCollaboratorStatus() throws Exception {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onRefresh();
        waitFor(() -> get(activity, "mIsCollaborator") != null, 10000);
        assertEquals(false, get(activity, "mIsCollaborator"));
    }

    // ---------- onItemSelected ----------

    @Test
    public void onItemSelected_labelPositions() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onItemSelected("labelselect", 0, "bug");
        assertNull(get(activity, "mSelectedLabel"));
        activity.onItemSelected("labelselect", 1, "bug");
        assertEquals("", get(activity, "mSelectedLabel"));
        activity.onItemSelected("labelselect", 2, "bug");
        assertEquals("bug", get(activity, "mSelectedLabel"));
    }

    @Test
    public void onItemSelected_milestonePositions() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onItemSelected("milestoneselect", 0, "v1");
        assertNull(get(activity, "mSelectedMilestone"));
        activity.onItemSelected("milestoneselect", 1, "v1");
        assertEquals("", get(activity, "mSelectedMilestone"));
        activity.onItemSelected("milestoneselect", 2, "v1");
        assertEquals("v1", get(activity, "mSelectedMilestone"));
    }

    @Test
    public void onItemSelected_assigneePositions() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onItemSelected("assigneeselect", 0, "alice");
        assertNull(get(activity, "mSelectedAssignee"));
        activity.onItemSelected("assigneeselect", 1, "alice");
        assertEquals("", get(activity, "mSelectedAssignee"));
        activity.onItemSelected("assigneeselect", 2, "alice");
        assertEquals("alice", get(activity, "mSelectedAssignee"));
    }

    @Test
    public void onItemSelected_participatingPositions() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onItemSelected("participatingselect", 1, null);
        assertEquals(1, get(activity, "mSelectedParticipatingStatus"));
        activity.onItemSelected("participatingselect", 2, null);
        assertEquals(2, get(activity, "mSelectedParticipatingStatus"));
        activity.onItemSelected("participatingselect", 0, null);
        assertEquals(0, get(activity, "mSelectedParticipatingStatus"));
    }

    @Test
    public void onItemSelected_unknownTag_changesNothing() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSelectedLabel", "bug");
        activity.onItemSelected("nope", 2, "x");
        assertEquals("bug", get(activity, "mSelectedLabel"));
    }

    // ---------- fragments ----------

    @Test
    public void onFragmentInstantiated_positionZero_setsOpenFragment() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        Fragment f = activity.makeFragment(0);
        activity.onFragmentInstantiated(f, 0);
        assertEquals(f, get(activity, "mOpenFragment"));
        Fragment g = activity.makeFragment(1);
        activity.onFragmentInstantiated(g, 1);
        assertEquals(f, get(activity, "mOpenFragment"));
    }

    @Test
    public void onFragmentDestroyed_openFragment_clears() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        Fragment f = activity.makeFragment(0);
        activity.onFragmentInstantiated(f, 0);
        Fragment g = activity.makeFragment(1);
        activity.onFragmentDestroyed(g);
        assertEquals(f, get(activity, "mOpenFragment"));
        activity.onFragmentDestroyed(f);
        assertNull(get(activity, "mOpenFragment"));
    }

    @Test
    public void fragmentNeedsRefresh_returnsTrue() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.fragmentNeedsRefresh(activity.makeFragment(0)));
    }

    @Test
    public void onRecyclerViewCreated_openFragment_setsTag() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        Fragment f = activity.makeFragment(0);
        activity.onFragmentInstantiated(f, 0);
        RecyclerView rv = new RecyclerView(appContext());
        activity.onRecyclerViewCreated(f, rv);
        assertNotNull(rv.getTag(R.id.FloatingActionButtonScrollEnabled));
        RecyclerView rv2 = new RecyclerView(appContext());
        activity.onRecyclerViewCreated(activity.makeFragment(1), rv2);
        assertNull(rv2.getTag(R.id.FloatingActionButtonScrollEnabled));
    }

    @Test
    public void onPageMoved_scalesFabWithPosition() throws Exception {
        IssueListActivity activity = launchAuthorized(false);
        View fab = (View) get(activity, "mCreateFab");
        activity.onPageMoved(0, 0.0f);
        assertEquals(1.0f, fab.getScaleX(), 0.001f);
        assertEquals(View.VISIBLE, fab.getVisibility());
        activity.onPageMoved(0, 1.0f);
        assertEquals(0.0f, fab.getScaleX(), 0.001f);
        assertEquals(View.INVISIBLE, fab.getVisibility());
        activity.onPageMoved(1, 0.0f);
        assertEquals(View.INVISIBLE, fab.getVisibility());
    }

    @Test
    public void onPageMoved_searchModeOrNoFab_doesNothing() throws Exception {
        IssueListActivity activity = launchAuthorized(false);
        View fab = (View) get(activity, "mCreateFab");
        set(activity, "mSearchMode", true);
        activity.onPageMoved(0, 1.0f);
        assertEquals(1.0f, fab.getScaleX(), 0.001f);
        assertEquals(View.VISIBLE, fab.getVisibility());
        IssueListActivity noFab = launch(IssueListActivity.class, intent(false));
        // Force the null-FAB state explicitly: whether a plain launch is
        // unauthorized depends on authorization leaking from other tests.
        set(noFab, "mCreateFab", null);
        noFab.onPageMoved(0, 1.0f);
    }

    // ---------- onActivityResult ----------

    @Test
    public void onActivityResult_afterCreate_refreshes() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onActivityResult(1002, android.app.Activity.RESULT_OK, null);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onActivityResult_unknownRequest_delegatesToSuper() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onActivityResult(9999, android.app.Activity.RESULT_OK, null);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onActivityResult_createOkNullData_doesNothing() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onActivityResult(1001, android.app.Activity.RESULT_OK, null);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onActivityResult_createOkNoIssueExtra_doesNothing() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onActivityResult(1001, android.app.Activity.RESULT_OK, new Intent());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    // ---------- makeFragment variants ----------

    @Test
    public void makeFragment_prMode_queryUsesPrAndEmptyText() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(true));
        Fragment f = activity.makeFragment(0);
        assertTrue(f instanceof IssueListFragment);
        assertTrue(f.getArguments().getString("query").contains("is:pr"));
        assertEquals(R.string.no_pull_requests_found,
                f.getArguments().getInt("emptytext"));
    }

    @Test
    public void makeFragment_searchMode_usesNoSearchEmptyText() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSearchMode", true);
        Fragment f = activity.makeFragment(0);
        assertEquals(R.string.no_search_issues_found,
                f.getArguments().getInt("emptytext"));
        IssueListActivity prs = launch(IssueListActivity.class, intent(true));
        set(prs, "mSearchMode", true);
        assertEquals(R.string.no_search_pull_requests_found,
                prs.makeFragment(0).getArguments().getInt("emptytext"));
    }

    @Test
    public void makeFragment_withFiltersAndQuery_buildsQuery() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSearchQuery", "crash");
        set(activity, "mSelectedLabel", "bug");
        set(activity, "mSelectedMilestone", "v1");
        set(activity, "mSelectedAssignee", "alice");
        set(activity, "mSelectedParticipatingStatus", 1);
        set(activity, "mUserLogin", "alice");
        String query = activity.makeFragment(0).getArguments().getString("query");
        assertTrue(query.contains("is:issue"));
        assertTrue(query.contains("repo:o/r"));
        assertTrue(query.contains("crash"));
        assertTrue(query.contains("label:\"bug\""));
        assertTrue(query.contains("milestone:\"v1\""));
        assertTrue(query.contains("assignee:\"alice\""));
        assertTrue(query.contains("involves:alice"));
        assertEquals(R.string.no_search_issues_found,
                activity.makeFragment(0).getArguments().getInt("emptytext"));
    }

    @Test
    public void makeFragment_searchQueryNull_omitsQueryPart() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        String query = activity.makeFragment(0).getArguments().getString("query");
        assertTrue(query.startsWith("is:issue"));
        assertFalse(query.contains("null"));
    }

    // ---------- right navigation drawer ----------

    @Test
    public void getRightNavigationDrawerMenuResources_collaboratorMenu() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mIsCollaborator", true);
        int[] res = activity.getRightNavigationDrawerMenuResources();
        assertEquals(R.menu.issue_list_filter_collab, res[1]);
        set(activity, "mIsCollaborator", false);
        assertEquals(R.menu.issue_list_filter,
                activity.getRightNavigationDrawerMenuResources()[1]);
        set(activity, "mIsCollaborator", null);
        assertEquals(R.menu.issue_list_filter,
                activity.getRightNavigationDrawerMenuResources()[1]);
    }

    @Test
    public void getInitialRightDrawerSelection_returnsSortCreatedDesc() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertEquals(R.id.sort_created_desc, activity.getInitialRightDrawerSelection());
    }

    @Test
    public void onPrepareRightNavigationDrawerMenu_selectionSubtitles() throws Exception {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        // null selections -> "any" subtitles (launch already shows them)
        assertTrue(drawerItemTitle(activity, R.id.filter_by_milestone).contains("Any milestone"));
        assertTrue(drawerItemTitle(activity, R.id.filter_by_label).contains("Any label"));
        assertTrue(drawerItemTitle(activity, R.id.filter_by_assignee).contains("Any assignee"));
        // empty selections -> "no" subtitles
        set(activity, "mSelectedMilestone", "");
        set(activity, "mSelectedLabel", "");
        set(activity, "mSelectedAssignee", "");
        call(activity, "updateRightNavigationDrawer", new Class<?>[]{}, new Object[]{});
        // The "no" subtitles are the resource strings, not "No <thing>":
        // "No milestone set" / "Unlabeled" / "Unassigned".
        assertTrue(drawerItemTitle(activity, R.id.filter_by_milestone)
                .contains(appContext().getString(R.string.issue_filter_by_no_milestone)));
        assertTrue(drawerItemTitle(activity, R.id.filter_by_label)
                .contains(appContext().getString(R.string.issue_filter_by_no_label)));
        assertTrue(drawerItemTitle(activity, R.id.filter_by_assignee)
                .contains(appContext().getString(R.string.issue_filter_by_no_assignee)));
        // concrete values -> the value itself
        set(activity, "mSelectedMilestone", "v1");
        set(activity, "mSelectedLabel", "bug");
        set(activity, "mSelectedAssignee", "alice");
        call(activity, "updateRightNavigationDrawer", new Class<?>[]{}, new Object[]{});
        assertTrue(drawerItemTitle(activity, R.id.filter_by_milestone).contains("v1"));
        assertTrue(drawerItemTitle(activity, R.id.filter_by_label).contains("bug"));
        assertTrue(drawerItemTitle(activity, R.id.filter_by_assignee).contains("alice"));
        // participating status -> the matching array entry
        String[] options = appContext().getResources().getStringArray(R.array.filter_participating);
        set(activity, "mSelectedParticipatingStatus", 1);
        call(activity, "updateRightNavigationDrawer", new Class<?>[]{}, new Object[]{});
        assertTrue(drawerItemTitle(activity, R.id.filter_by_participating).contains(options[1]));
        set(activity, "mSelectedParticipatingStatus", 2);
        call(activity, "updateRightNavigationDrawer", new Class<?>[]{}, new Object[]{});
        assertTrue(drawerItemTitle(activity, R.id.filter_by_participating).contains(options[2]));
    }

    @Test
    public void onPrepareRightNavigationDrawerMenu_missingItems_skips() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        // A menu without the filter items: every findItem returns null and
        // the method must simply skip the subtitle updates.
        activity.onPrepareRightNavigationDrawerMenu(new MenuBuilder(appContext()));
    }

    // ---------- onNavigationItemSelected ----------

    @Test
    public void onNavigationItemSelected_sortItem_updatesSortAndRefreshes() throws Exception {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.sort_updated_asc)));
        Object helper = get(activity, "mSortHelper");
        assertEquals("updated", call(helper, "getSortMode", new Class<?>[]{}, new Object[]{}));
        assertEquals("asc", call(helper, "getSortOrder", new Class<?>[]{}, new Object[]{}));
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.sort_comments_desc)));
        assertEquals("comments", call(helper, "getSortMode", new Class<?>[]{}, new Object[]{}));
        assertEquals("desc", call(helper, "getSortOrder", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void onNavigationItemSelected_unknownId_returnsFalse() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertFalse(activity.onNavigationItemSelected(menuItem(0x9999)));
    }

    @Test
    public void onNavigationItemSelected_filterByAssigneeCached_showsDialog() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        List<User> users = Arrays.asList(
                User.builder().login("alice").id(1L).build(),
                User.builder().login("bob").id(2L).build());
        set(activity, "mAssignees", users);
        set(activity, "mSelectedAssignee", "bob");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_assignee)));
        assertEquals(Arrays.asList(
                        appContext().getString(R.string.issue_filter_by_any_assignee),
                        appContext().getString(R.string.issue_filter_by_no_assignee),
                        "alice", "bob"),
                dialogEntries(activity, "assigneeselect"));
        assertEquals(3, dialogSelected(activity, "assigneeselect"));
    }

    @Test
    public void onNavigationItemSelected_filterByLabelCached_showsDialog() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        List<Label> labels = Arrays.asList(
                Label.builder().name("bug").color("ff0000").build(),
                Label.builder().name("feature").color("00ff00").build());
        set(activity, "mLabels", labels);
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
        assertEquals(Arrays.asList(
                        appContext().getString(R.string.issue_filter_by_any_label),
                        appContext().getString(R.string.issue_filter_by_no_label),
                        "bug", "feature"),
                dialogEntries(activity, "labelselect"));
        assertEquals(0, dialogSelected(activity, "labelselect"));
    }

    @Test
    public void onNavigationItemSelected_filterByMilestoneCached_showsDialog() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        List<Milestone> milestones = Collections.singletonList(
                Milestone.builder().title("v1").number(1).build());
        set(activity, "mMilestones", milestones);
        set(activity, "mSelectedMilestone", "v1");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_milestone)));
        assertEquals(Arrays.asList(
                        appContext().getString(R.string.issue_filter_by_any_milestone),
                        appContext().getString(R.string.issue_filter_by_no_milestone),
                        "v1"),
                dialogEntries(activity, "milestoneselect"));
        assertEquals(2, dialogSelected(activity, "milestoneselect"));
    }

    @Test
    public void onNavigationItemSelected_filterByParticipating_showsDialog() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSelectedParticipatingStatus", 1);
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_participating)));
        String[] options = appContext().getResources().getStringArray(R.array.filter_participating);
        assertEquals(Arrays.asList(options), dialogEntries(activity, "participatingselect"));
        assertEquals(1, dialogSelected(activity, "participatingselect"));
    }

    @Test
    public void onNavigationItemSelected_manageLabels_startsLabelList() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.manage_labels)));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(IssueLabelListActivity.class.getName(),
                next.getComponent().getClassName());
        assertEquals("o", next.getStringExtra("owner"));
        assertEquals("r", next.getStringExtra("repo"));
    }

    @Test
    public void onNavigationItemSelected_manageMilestones_startsMilestoneList() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.manage_milestones)));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(IssueMilestoneListActivity.class.getName(),
                next.getComponent().getClassName());
    }

    // ---------- options menu / search / click ----------

    @Test
    public void onCreateOptionsMenu_inflatesAndWiresSearch() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        MenuBuilder menu = new MenuBuilder(appContext());
        assertTrue(activity.onCreateOptionsMenu(menu));
        assertNotNull(get(activity, "mRemoveFilterButton"));
        assertNotNull(get(activity, "mSearch"));
        assertFalse(menu.findItem(R.id.remove_filter).isVisible());
        set(activity, "mSelectedLabel", "bug");
        activity.onCreateOptionsMenu(new MenuBuilder(appContext()));
        // remove-filter visibility follows isFiltering()
        assertTrue(((MenuItem) get(activity, "mRemoveFilterButton")).isVisible());
    }

    @Test
    public void onCreateOptionsMenu_expandedState_restoresQuery() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSearchIsExpanded", true);
        set(activity, "mSearchQuery", "crash");
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        // expandActionView() on a bare MenuBuilder item does not take effect
        // under Robolectric (no menu presenter), so the observable effect of
        // the expanded branch is the restored query on the SearchView.
        com.gh4a.widget.SearchView search =
                (com.gh4a.widget.SearchView) get(activity, "mSearch");
        assertEquals("crash", search.getQuery().toString());
    }

    @Test
    public void onOptionsItemSelected_overflow_returnsTrue() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertTrue(activity.onOptionsItemSelected(menuItem(R.id.overflow)));
    }

    @Test
    public void onOptionsItemSelected_removeFilter_clearsFilters() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mSelectedMilestone", "v1");
        set(activity, "mSelectedLabel", "bug");
        set(activity, "mSelectedAssignee", "alice");
        set(activity, "mSelectedParticipatingStatus", 2);
        assertTrue(activity.onOptionsItemSelected(menuItem(R.id.remove_filter)));
        assertNull(get(activity, "mSelectedMilestone"));
        assertNull(get(activity, "mSelectedLabel"));
        assertNull(get(activity, "mSelectedAssignee"));
        assertEquals(0, get(activity, "mSelectedParticipatingStatus"));
    }

    @Test
    public void onOptionsItemSelected_unknown_delegatesToSuper() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        assertFalse(activity.onOptionsItemSelected(menuItem(0x9999)));
    }

    @Test
    public void onQueryTextChange_expanded_updatesQuery() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        // Expanding the SearchView directly: MenuItem.expandActionView()
        // does not invoke onActionViewExpanded on a bare MenuBuilder item.
        ((com.gh4a.widget.SearchView) get(activity, "mSearch")).onActionViewExpanded();
        assertFalse(activity.onQueryTextChange("foo"));
        assertEquals("foo", get(activity, "mSearchQuery"));
    }

    @Test
    public void onQueryTextChange_collapsed_ignoresQuery() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertFalse(activity.onQueryTextChange("foo"));
        assertNull(get(activity, "mSearchQuery"));
    }

    @Test
    public void onClick_fab_startsIssueEditForResult() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        activity.onClick(new View(appContext()));
        org.robolectric.shadows.ShadowActivity.IntentForResult result =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(result);
        assertEquals(IssueEditActivity.class.getName(),
                result.intent.getComponent().getClassName());
        assertEquals(1001, result.requestCode);
    }

    @Test
    public void setSearchMode_togglesFabVisibility() throws Exception {
        IssueListActivity activity = launchAuthorized(false);
        View fab = (View) get(activity, "mCreateFab");
        call(activity, "setSearchMode", new Class<?>[]{boolean.class}, new Object[]{true});
        assertEquals(View.GONE, fab.getVisibility());
        call(activity, "setSearchMode", new Class<?>[]{boolean.class}, new Object[]{false});
        assertEquals(View.VISIBLE, fab.getVisibility());
    }

    // ---------- filter dialogs: selection index ----------

    private List<Label> twoLabels() {
        return Arrays.asList(
                Label.builder().name("bug").color("ff0000").build(),
                Label.builder().name("feature").color("00ff00").build());
    }

    @Test
    public void filterLabel_emptySelection_selectsNoLabel() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mLabels", twoLabels());
        set(activity, "mSelectedLabel", "");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
        assertEquals(1, dialogSelected(activity, "labelselect"));
    }

    @Test
    public void filterLabel_matchingSelection_selectsLabel() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mLabels", twoLabels());
        set(activity, "mSelectedLabel", "feature");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
        assertEquals(3, dialogSelected(activity, "labelselect"));
    }

    @Test
    public void filterLabel_unmatchedSelection_selectsAny() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mLabels", twoLabels());
        set(activity, "mSelectedLabel", "zzz");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
        assertEquals(0, dialogSelected(activity, "labelselect"));
    }

    @Test
    public void filterLabel_emptyList_onlyAnyAndNo() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mLabels", new ArrayList<>());
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
        assertEquals(Arrays.asList(
                        appContext().getString(R.string.issue_filter_by_any_label),
                        appContext().getString(R.string.issue_filter_by_no_label)),
                dialogEntries(activity, "labelselect"));
        assertEquals(0, dialogSelected(activity, "labelselect"));
    }

    @Test
    public void filterMilestone_emptySelection_selectsNoMilestone() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mMilestones", Collections.singletonList(
                Milestone.builder().title("v1").number(1).build()));
        set(activity, "mSelectedMilestone", "");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_milestone)));
        assertEquals(1, dialogSelected(activity, "milestoneselect"));
    }

    @Test
    public void filterMilestone_unmatchedSelection_selectsAny() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mMilestones", Collections.singletonList(
                Milestone.builder().title("v1").number(1).build()));
        set(activity, "mSelectedMilestone", "zzz");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_milestone)));
        assertEquals(0, dialogSelected(activity, "milestoneselect"));
    }

    @Test
    public void filterAssignee_emptySelection_selectsNoAssignee() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        set(activity, "mAssignees", Collections.singletonList(
                User.builder().login("alice").id(1L).build()));
        set(activity, "mSelectedAssignee", "");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_assignee)));
        assertEquals(1, dialogSelected(activity, "assigneeselect"));
    }

    @Test
    public void filterAssignee_nullUsersAndCaseInsensitiveMatch() {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        User nullLogin = mock(User.class);
        when(nullLogin.login()).thenReturn(null);
        List<User> users = new ArrayList<>();
        users.add(null);
        users.add(nullLogin);
        users.add(User.builder().login("Alice").id(3L).build());
        set(activity, "mAssignees", users);
        // "alice" matches "Alice" case-insensitively -> index 4
        // (Any, No, "", "", "Alice").
        set(activity, "mSelectedAssignee", "alice");
        assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_assignee)));
        assertEquals(Arrays.asList(
                        appContext().getString(R.string.issue_filter_by_any_assignee),
                        appContext().getString(R.string.issue_filter_by_no_assignee),
                        "", "", "Alice"),
                dialogEntries(activity, "assigneeselect"));
        assertEquals(4, dialogSelected(activity, "assigneeselect"));
    }

    // ---------- filter network paths ----------

    @Test
    public void filterAssignee_loadsFromServiceAndShowsDialog() throws Exception {
        IssueAssigneeService svc = mock(IssueAssigneeService.class);
        List<User> users = Arrays.asList(
                User.builder().login("alice").id(1L).build(),
                User.builder().login("bob").id(2L).build());
        when(svc.getAssignees(eq("o"), eq("r"), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<User>builder().items(users).build())));
        try (MockedStatic<ServiceFactory> mocked = mockPagedService()) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(IssueAssigneeService.class), anyBoolean()))
                    .thenReturn(svc);
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_assignee)));
            waitFor(() -> get(activity, "mAssignees") != null, 10000);
            assertEquals(users, get(activity, "mAssignees"));
            assertEquals(Arrays.asList(
                            appContext().getString(R.string.issue_filter_by_any_assignee),
                            appContext().getString(R.string.issue_filter_by_no_assignee),
                            "alice", "bob"),
                    dialogEntries(activity, "assigneeselect"));
        }
    }

    @Test
    public void filterAssignee_loadFailure_showsError() throws Exception {
        IssueAssigneeService svc = mock(IssueAssigneeService.class);
        when(svc.getAssignees(eq("o"), eq("r"), anyLong()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockPagedService()) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(IssueAssigneeService.class), anyBoolean()))
                    .thenReturn(svc);
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_assignee)));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue((boolean) get(activity, "mErrorShown"));
            assertNull(get(activity, "mAssignees"));
            assertNull(dialogFragment(activity, "assigneeselect"));
        }
    }

    @Test
    public void filterMilestone_loadsFromServiceAndShowsDialog() throws Exception {
        IssueMilestoneService svc = mock(IssueMilestoneService.class);
        List<Milestone> milestones = Collections.singletonList(
                Milestone.builder().title("v1").number(1).build());
        when(svc.getRepositoryMilestones(eq("o"), eq("r"), eq("open"), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Milestone>builder().items(milestones).build())));
        try (MockedStatic<ServiceFactory> mocked = mockPagedService()) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(IssueMilestoneService.class), anyBoolean()))
                    .thenReturn(svc);
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_milestone)));
            waitFor(() -> get(activity, "mMilestones") != null, 10000);
            assertEquals(milestones, get(activity, "mMilestones"));
            assertEquals(Arrays.asList(
                            appContext().getString(R.string.issue_filter_by_any_milestone),
                            appContext().getString(R.string.issue_filter_by_no_milestone),
                            "v1"),
                    dialogEntries(activity, "milestoneselect"));
        }
    }

    @Test
    public void filterMilestone_loadFailure_showsError() throws Exception {
        IssueMilestoneService svc = mock(IssueMilestoneService.class);
        when(svc.getRepositoryMilestones(eq("o"), eq("r"), eq("open"), anyLong()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockPagedService()) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(IssueMilestoneService.class), anyBoolean()))
                    .thenReturn(svc);
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_milestone)));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue((boolean) get(activity, "mErrorShown"));
            assertNull(get(activity, "mMilestones"));
        }
    }

    @Test
    public void filterLabel_loadsFromServiceAndShowsDialog() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        List<Label> labels = twoLabels();
        when(svc.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Label>builder().items(labels).build())));
        try (MockedStatic<ServiceFactory> mocked = mockPagedService()) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(IssueLabelService.class), anyBoolean()))
                    .thenReturn(svc);
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
            waitFor(() -> get(activity, "mLabels") != null, 10000);
            assertEquals(labels, get(activity, "mLabels"));
            assertEquals(Arrays.asList(
                            appContext().getString(R.string.issue_filter_by_any_label),
                            appContext().getString(R.string.issue_filter_by_no_label),
                            "bug", "feature"),
                    dialogEntries(activity, "labelselect"));
        }
    }

    @Test
    public void filterLabel_loadFailure_showsError() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        when(svc.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockPagedService()) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(IssueLabelService.class), anyBoolean()))
                    .thenReturn(svc);
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            assertTrue(activity.onNavigationItemSelected(menuItem(R.id.filter_by_label)));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue((boolean) get(activity, "mErrorShown"));
            assertNull(get(activity, "mLabels"));
        }
    }

    // ---------- collaborator loader ----------

    @Test
    public void loadCollaboratorStatus_unauthorized_setsFalse() throws Exception {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        waitFor(() -> get(activity, "mIsCollaborator") != null, 10000);
        assertEquals(false, get(activity, "mIsCollaborator"));
    }

    @Test
    public void loadCollaboratorStatus_collaboratorTrue_updatesDrawer() throws Exception {
        try (MockedStatic<SingleFactory> mocked = mockCollaborator(true)) {
            IssueListActivity activity = launch(IssueListActivity.class, intent(false));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mIsCollaborator")), 10000);
            assertEquals(R.menu.issue_list_filter_collab,
                    activity.getRightNavigationDrawerMenuResources()[1]);
        }
    }

    @Test
    public void loadCollaboratorStatus_secondDelivery_ignored() throws Exception {
        IssueListActivity activity = launch(IssueListActivity.class, intent(false));
        waitFor(() -> get(activity, "mIsCollaborator") != null, 10000);
        assertEquals(false, get(activity, "mIsCollaborator"));
        // Loading again re-delivers the cached result while mIsCollaborator
        // is already set: the callback must ignore the second delivery.
        call(activity, "loadCollaboratorStatus", new Class<?>[]{boolean.class},
                new Object[]{false});
        waitFor(() -> get(activity, "mIsCollaborator") != null, 5000);
        assertEquals(false, get(activity, "mIsCollaborator"));
    }
}
