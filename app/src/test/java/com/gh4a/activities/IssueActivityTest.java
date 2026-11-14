package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.SingleFactory;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.IssueStateReason;
import com.meisolsson.githubsdk.model.request.issue.IssueRequest;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.issues.IssueService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.dismissCurrentSnackbarAsTimeout;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.logContains;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static com.gh4a.activities.ActivityTestHelper.waitForSnackbarWithText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class IssueActivityTest {
    private Issue issue(int number) {
        return Issue.builder()
                .number(number)
                .title("Bug report")
                .state(IssueState.Open)
                .url("https://api.github.com/repos/o/r/issues/" + number)
                .htmlUrl("https://github.com/o/r/issues/" + number)
                .user(User.builder().login("alice").id(1L).build())
                .build();
    }

    @Test
    public void makeIntent_fromIssue_extractsRepoAndNumber() {
        Intent i = IssueActivity.makeIntent(appContext(), issue(7));
        assertEquals(IssueActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertEquals(7, i.getIntExtra("number", -1));
    }

    @Test
    public void makeIntent_nullIssue_defaultsToZero() {
        Intent i = IssueActivity.makeIntent(appContext(), (Issue) null);
        assertEquals(0, i.getIntExtra("number", -1));
    }

    @Test
    public void makeIntent_nullNumber_defaultsToZero() {
        Issue noNumber = Issue.builder()
                .url("https://api.github.com/repos/o/r/issues/9")
                .build();
        Intent i = IssueActivity.makeIntent(appContext(), noNumber);
        assertEquals(0, i.getIntExtra("number", -1));
    }

    @Test
    public void makeIntent_loginRepoNumber_setsExtras() {
        Intent i = IssueActivity.makeIntent(appContext(), "o", "r", 3);
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals(3, i.getIntExtra("number", -1));
        assertNull(i.getParcelableExtra("initial_comment"));
    }

    @Test
    public void launch_titleAndSubtitle() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        assertEquals(appContext().getString(R.string.issue) + " #7",
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.displayDetachAction());
    }

    @Test
    public void navigateUp_goesToIssueList() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        Intent up = activity.navigateUp();
        assertEquals(IssueListActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("o", up.getStringExtra("owner"));
        assertFalse(up.getBooleanExtra("is_pull_request", true));
    }

    @Test
    public void getActivityUri_buildsIssueUrl() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        assertEquals("https://github.com/o/r/issues/7", activity.getActivityUri().toString());
    }

    @Test
    public void onCreateOptionsMenu_withoutIssue_removesIssueItems() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertNull(menu.findItem(R.id.browser));
        assertNull(menu.findItem(R.id.share));
        assertNull(menu.findItem(R.id.copy_number));
        assertNull(menu.findItem(R.id.copy_link));
    }

    @Test
    public void onOptionsItemSelected_shareWithoutIssue_returnsTrueWithoutCrash() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(
                menu.add(0, R.id.share, 0, "share")));
        assertTrue(activity.onOptionsItemSelected(
                menu.add(0, R.id.browser, 0, "browser")));
    }

    @Test
    public void onOptionsItemSelected_copyNumber_copiesNumber() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(
                menu.add(0, R.id.copy_number, 0, "copy")));
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                appContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        assertEquals("7", cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void updateHeader_openIssue_showsOpenState() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        set(activity, "mIssue", issue(7));
        call(activity, "updateHeader", new Class<?>[]{}, new Object[]{});
        android.widget.TextView state = activity.findViewById(R.id.tv_state);
        assertEquals("OPEN", state.getText().toString());
        assertEquals("Bug report",
                ((android.widget.TextView) activity.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void updateHeader_closedIssue_showsClosedState() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        Issue closed = issue(7).toBuilder().state(IssueState.Closed).build();
        set(activity, "mIssue", closed);
        call(activity, "updateHeader", new Class<?>[]{}, new Object[]{});
        assertEquals("CLOSED",
                ((android.widget.TextView) activity.findViewById(R.id.tv_state)).getText().toString());
    }

    @Test
    public void updateFabVisibility_withoutIssue_noFab() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditFab"));
    }

    @Test
    public void checkForAuthOrExit_unauthorized_startsLoginAndFinishes() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        assertFalse((boolean) call(activity, "checkForAuthOrExit", new Class<?>[]{}, new Object[]{}));
        assertTrue(activity.isFinishing());
        Intent next = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Github4AndroidActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void onConfirmed_reopen_callsService() throws Exception {
        IssueService svc = mock(IssueService.class);
        Issue updated = issue(7).toBuilder().state(IssueState.Open).build();
        when(svc.getIssue(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(issue(7))));
        when(svc.editIssue(eq("o"), eq("r"), eq(7L), any()))
                .thenReturn(Single.just(Response.success(updated)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueService.class, false)).thenReturn(svc);
            IssueActivity activity = launch(IssueActivity.class,
                    IssueActivity.makeIntent(appContext(), "o", "r", 7));
            set(activity, "mIssue", issue(7));
            activity.onConfirmed("reopenconfirm", null);
            // wrapForBackgroundTask subscribes on Schedulers.io(): poll
            // for the background invocation instead of a fixed sleep.
            verify(svc, timeout(5000)).editIssue(eq("o"), eq("r"), eq(7L), any());
        }
    }

    // ---------- gap closers ----------

    private Issue issueWith(String login, IssueState state, String closedByLogin) {
        Issue.Builder b = Issue.builder()
                .number(7)
                .title("Bug report")
                .state(state)
                .url("https://api.github.com/repos/o/r/issues/7")
                .htmlUrl("https://github.com/o/r/issues/7")
                .user(User.builder().login(login).id(1L).build());
        if (closedByLogin != null) {
            b.closedBy(User.builder().login(closedByLogin).id(2L).build());
        }
        return b.build();
    }

    private void authorizeAs(String login) {
        // addAccount is intermittently ineffective across tests in the
        // same class run (secure-prefs backend/migration state leaks
        // between tests): retry until the login actually sticks.
        Gh4Application app = Gh4Application.get();
        for (int i = 0; i < 3
                && (!app.isAuthorized() || !login.equals(app.getAuthLogin())); i++) {
            app.addAccount(User.builder().login(login).id(1L).build(), "tok");
            idleMain(100);
        }
    }

    private void ensureAuthorizedAs(String login) {
        // authorizeAs() before launch does not always survive the launch
        // (the secure-prefs backend only settles during launch); re-check
        // after launch and re-authorize if needed.
        Gh4Application app = Gh4Application.get();
        if (!app.isAuthorized() || !login.equals(app.getAuthLogin())) {
            authorizeAs(login);
        }
    }

    private MockedStatic<ServiceFactory> mockIssueService(IssueService svc) {
        MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        mocked.when(() -> ServiceFactory.get(eq(IssueService.class), anyBoolean())).thenReturn(svc);
        // The IssueFragment added by showUiIfDone loads the timeline through
        // the real IssueTimelineService otherwise: a real request that 401s
        // without a token and raises an INDEFINITE auth snackbar, which
        // queues every later snackbar behind it. Stub it to an empty page.
        com.meisolsson.githubsdk.service.issues.IssueTimelineService timeline =
                Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueTimelineService.class);
        Mockito.when(timeline.getTimeline(anyString(), anyString(), anyInt(), anyLong()))
                .thenReturn(Single.just(Response.success(
                        com.meisolsson.githubsdk.model.Page
                                .<com.meisolsson.githubsdk.model.IssueEvent>builder()
                                .items(java.util.Collections.emptyList()).build())));
        mocked.when(() -> ServiceFactory.getForFullPagedLists(
                        eq(com.meisolsson.githubsdk.service.issues.IssueTimelineService.class),
                        anyBoolean()))
                .thenReturn(timeline);
        return mocked;
    }

    private MockedStatic<SingleFactory> mockCollaborator(boolean value) {
        MockedStatic<SingleFactory> mocked = Mockito.mockStatic(SingleFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        mocked.when(() -> SingleFactory.isAppUserRepoCollaborator(anyString(), anyString(), anyBoolean()))
                .thenReturn(Single.just(value));
        return mocked;
    }

    private IssueActivity launchAndWaitForIssue() throws Exception {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        // Both loaders must have delivered: showUiIfDone (fragment + FAB)
        // only runs once mIssue AND mIsCollaborator are set, and whichever
        // loader finishes first returns early.
        waitFor(() -> get(activity, "mIssue") != null
                && get(activity, "mIsCollaborator") != null, 10000);
        assertNotNull(get(activity, "mIssue"));
        assertNotNull(get(activity, "mIsCollaborator"));
        activity.getSupportFragmentManager().executePendingTransactions();
        return activity;
    }

    private androidx.appcompat.view.menu.MenuBuilder menuFor(IssueActivity activity) {
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        return menu;
    }

    private void setupMenuState(IssueActivity activity, String authLogin,
            Issue issue, boolean collaborator) {
        if (authLogin != null) {
            authorizeAs(authLogin);
        }
        set(activity, "mIssue", issue);
        set(activity, "mIsCollaborator", collaborator);
    }

    // ----- loading -----

    @Test
    public void loadIssue_success_setsIssueAndShowsUi() throws Exception {
        IssueService svc = mock(IssueService.class);
        when(svc.getIssue(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(issue(7))));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            IssueActivity activity = launchAndWaitForIssue();
            assertEquals(issue(7).title(),
                    ((Issue) get(activity, "mIssue")).title());
            assertFalse((boolean) get(activity, "mIsCollaborator"));
            assertNotNull(get(activity, "mFragment"));
            assertEquals(android.view.View.VISIBLE,
                    ((android.view.ViewGroup) get(activity, "mHeader")).getVisibility());
        }
    }

    @Test
    public void loadIssue_error_showsErrorView() throws Exception {
        IssueService svc = mock(IssueService.class);
        when(svc.getIssue(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            IssueActivity activity = launch(IssueActivity.class,
                    IssueActivity.makeIntent(appContext(), "o", "r", 7));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue(Boolean.TRUE.equals(get(activity, "mErrorShown")));
            assertNull(get(activity, "mIssue"));
            assertTrue(logContains(Gh4Application.LOG_TAG, "Loading data failed"));
        }
    }

    @Test
    public void loadCollaboratorStatus_error_showsErrorView() throws Exception {
        IssueService svc = mock(IssueService.class);
        when(svc.getIssue(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(issue(7))));
        MockedStatic<SingleFactory> mocked = Mockito.mockStatic(SingleFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        mocked.when(() -> SingleFactory.isAppUserRepoCollaborator(anyString(), anyString(), anyBoolean()))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mocked) {
            IssueActivity activity = launch(IssueActivity.class,
                    IssueActivity.makeIntent(appContext(), "o", "r", 7));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertTrue(Boolean.TRUE.equals(get(activity, "mErrorShown")));
        }
    }

    @Test
    public void loadCollaboratorStatus_false_noFabWhenNotOwner() throws Exception {
        IssueService svc = mock(IssueService.class);
        when(svc.getIssue(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(issue(7))));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            IssueActivity activity = launchAndWaitForIssue();
            assertNull(get(activity, "mEditFab"));
            assertEquals(android.view.View.VISIBLE,
                    ((android.view.ViewGroup) get(activity, "mHeader")).getVisibility());
        }
    }

    // ----- menu matrix -----

    @Test
    public void menu_openIssue_creatorCanClose() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNotNull(menu.findItem(R.id.issue_close));
        assertNull(menu.findItem(R.id.issue_reopen));
        assertNotNull(menu.findItem(R.id.browser));
        assertNotNull(menu.findItem(R.id.share));
        assertNotNull(menu.findItem(R.id.copy_number));
        assertNotNull(menu.findItem(R.id.copy_link));
    }

    @Test
    public void menu_closedIssue_creatorCloserCanReopen() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Closed, "alice"), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNull(menu.findItem(R.id.issue_close));
        assertNotNull(menu.findItem(R.id.issue_reopen));
    }

    @Test
    public void menu_closedIssue_creatorNonCloserCannotReopen() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Closed, "bob"), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNull(menu.findItem(R.id.issue_close));
        assertNull(menu.findItem(R.id.issue_reopen));
    }

    @Test
    public void menu_closedIssue_collaboratorCanReopen() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Closed, "bob"), true);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNotNull(menu.findItem(R.id.issue_reopen));
    }

    @Test
    public void menu_openIssue_nonCreatorNonCollaboratorCannotClose() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "bob", issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNull(menu.findItem(R.id.issue_close));
        assertNull(menu.findItem(R.id.issue_reopen));
    }

    @Test
    public void menu_openIssue_collaboratorCanClose() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "bob", issueWith("alice", IssueState.Open, null), true);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNotNull(menu.findItem(R.id.issue_close));
    }

    @Test
    public void menu_openIssue_unauthorizedCannotClose() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, null, issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertNull(menu.findItem(R.id.issue_close));
        assertNull(menu.findItem(R.id.issue_reopen));
        assertNotNull(menu.findItem(R.id.browser));
        assertNotNull(menu.findItem(R.id.share));
    }

    // ----- menu actions -----

    @Test
    public void menuItem_close_authorized_showsCloseReasonDialog() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.issue_close)));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("close_reason"));
    }

    @Test
    public void menuItem_close_unauthorized_startsLoginAndFinishes() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, null, issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.issue_close, 0, "close")));
        assertTrue(activity.isFinishing());
        assertEquals(Github4AndroidActivity.class.getName(),
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity()
                        .getComponent().getClassName());
    }

    @Test
    public void menuItem_reopen_authorized_showsConfirmDialog() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Closed, "alice"), false);
        androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.issue_reopen)));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("reopenconfirm"));
    }

    @Test
    public void menuItem_reopen_unauthorized_startsLoginAndFinishes() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, null, issueWith("alice", IssueState.Closed, "alice"), false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.issue_reopen, 0, "reopen")));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void menuItem_share_withHtmlUrl_startsChooser() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, null, issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.share, 0, "share")));
        android.content.Intent next =
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(android.content.Intent.ACTION_CHOOSER, next.getAction());
    }

    @Test
    public void menuItem_share_nullHtmlUrl_returnsTrueWithoutIntent() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        Issue noUrl = Issue.builder().number(7).title("Bug report").state(IssueState.Open)
                .user(User.builder().login("alice").id(1L).build()).build();
        setupMenuState(activity, null, noUrl, false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.share, 0, "share")));
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void menuItem_browser_withHtmlUrl_showsNoBrowserToast() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, null, issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
        assertEquals(appContext().getString(R.string.no_browser_found),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void menuItem_browser_nullHtmlUrl_returnsTrueWithoutToast() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        Issue noUrl = Issue.builder().number(7).title("Bug report").state(IssueState.Open)
                .user(User.builder().login("alice").id(1L).build()).build();
        setupMenuState(activity, null, noUrl, false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void menuItem_copyLink_copiesLinkAndShowsSnackbar() throws Exception {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, null, issueWith("alice", IssueState.Open, null), false);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.copy_link, 0, "copy link")));
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                appContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        assertEquals("https://github.com/o/r/issues/7",
                cm.getPrimaryClip().getItemAt(0).getText().toString());
        idleMain(500);
        assertNotNull(activity.findViewById(com.google.android.material.R.id.snackbar_text));
        // Clean up: the "link copied" snackbar would otherwise stay the
        // SnackbarManager's current snackbar (its timeout never fires under
        // Robolectric) and queue behind it every later snackbar in the run.
        dismissCurrentSnackbarAsTimeout();
    }

    @Test
    public void menuItem_unknown_returnsFalse() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 99999, 0, "unknown")));
    }

    @Test
    public void menuItem_home_startsIssueList() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, android.R.id.home, 0, "home")));
        assertEquals(IssueListActivity.class.getName(),
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity()
                        .getComponent().getClassName());
    }

    // ----- close / reopen flows -----

    private IssueActivity launchWithEditMock(IssueService svc, Issue toLoad, boolean collaborator)
            throws Exception {
        when(svc.getIssue(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(toLoad)));
        return launchAndWaitForIssue();
    }

    @Test
    public void closeReasonDialog_clickNotPlanned_closesIssue() throws Exception {
        IssueService svc = mock(IssueService.class);
        Issue closed = issueWith("alice", IssueState.Closed, null);
        ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
        Mockito.when(svc.editIssue(eq("o"), eq("r"), eq(7L), captor.capture()))
                .thenReturn(Single.just(Response.success(closed)));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.issue_close)));
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ((androidx.fragment.app.DialogFragment)
                            activity.getSupportFragmentManager()
                                    .findFragmentByTag("close_reason")).getDialog();
            assertNotNull(dialog);
            android.widget.ListView listView = dialog.getListView();
            assertNotNull(listView);
            android.view.View row = listView.getAdapter().getView(1, null, listView);
            listView.performItemClick(row, 1, listView.getAdapter().getItemId(1));
            waitFor(() -> ((Issue) get(activity, "mIssue")).state() == IssueState.Closed, 10000);
            assertEquals(IssueState.Closed, ((Issue) get(activity, "mIssue")).state());
            assertEquals(IssueState.Closed, captor.getValue().state());
            assertEquals(IssueStateReason.NotPlanned, captor.getValue().stateReason());
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void closeReasonDialog_clickDuplicate_closesWithDuplicateReason() throws Exception {
        IssueService svc = mock(IssueService.class);
        Issue closed = issueWith("alice", IssueState.Closed, null);
        ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
        when(svc.editIssue(eq("o"), eq("r"), eq(7L), captor.capture()))
                .thenReturn(Single.just(Response.success(closed)));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
            activity.onOptionsItemSelected(menu.findItem(R.id.issue_close));
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ((androidx.fragment.app.DialogFragment)
                            activity.getSupportFragmentManager()
                                    .findFragmentByTag("close_reason")).getDialog();
            android.widget.ListView listView = dialog.getListView();
            android.view.View row = listView.getAdapter().getView(2, null, listView);
            listView.performItemClick(row, 2, listView.getAdapter().getItemId(2));
            waitFor(() -> ((Issue) get(activity, "mIssue")).state() == IssueState.Closed, 10000);
            assertEquals(IssueStateReason.Duplicate, captor.getValue().stateReason());
        }
    }

    @Test
    public void closeReasonDialog_cancel_doesNotCallService() throws Exception {
        IssueService svc = mock(IssueService.class);
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
            activity.onOptionsItemSelected(menu.findItem(R.id.issue_close));
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ((androidx.fragment.app.DialogFragment)
                            activity.getSupportFragmentManager()
                                    .findFragmentByTag("close_reason")).getDialog();
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            idleMain(200);
            verify(svc, never()).editIssue(anyString(), anyString(), eq(7L), any());
            assertEquals(IssueState.Open, ((Issue) get(activity, "mIssue")).state());
        }
    }

    @Test
    public void reopenConfirm_positiveButton_reopensIssue() throws Exception {
        IssueService svc = mock(IssueService.class);
        Issue reopened = issueWith("alice", IssueState.Open, null);
        ArgumentCaptor<IssueRequest> captor = ArgumentCaptor.forClass(IssueRequest.class);
        when(svc.editIssue(eq("o"), eq("r"), eq(7L), captor.capture()))
                .thenReturn(Single.just(Response.success(reopened)));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Closed, "alice"), false);
            ensureAuthorizedAs("alice");
            assertTrue("not authorized as alice after launch",
                    Gh4Application.get().isAuthorized()
                            && "alice".equals(Gh4Application.get().getAuthLogin()));
            androidx.appcompat.view.menu.MenuBuilder menu = menuFor(activity);
            assertNotNull("reopen item missing", menu.findItem(R.id.issue_reopen));
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.issue_reopen)));
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog) ((androidx.fragment.app.DialogFragment)
                            activity.getSupportFragmentManager()
                                    .findFragmentByTag("reopenconfirm")).getDialog();
            assertNotNull(dialog);
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            idleMain(200);
            waitFor(() -> ((Issue) get(activity, "mIssue")).state() == IssueState.Open, 10000);
            assertEquals(IssueState.Open, ((Issue) get(activity, "mIssue")).state());
            assertEquals(IssueState.Open, captor.getValue().state());
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void closeIssue_error_showsRetrySnackbarAndLogs() throws Exception {
        IssueService svc = mock(IssueService.class);
        when(svc.editIssue(eq("o"), eq("r"), eq(7L), any()))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            call(activity, "closeIssue",
                    new Class<?>[]{IssueStateReason.class}, new Object[]{IssueStateReason.Completed});
            waitForSnackbarWithText(activity,
                    activity.getString(R.string.issue_error_close, 7));
            dismissCurrentSnackbarAsTimeout();
            waitFor(() -> logContains(Gh4Application.LOG_TAG, "Closing issue failed"), 5000);
            assertTrue(logContains(Gh4Application.LOG_TAG, "Closing issue failed"));
            assertFalse(activity.isFinishing());
            assertEquals(IssueState.Open, ((Issue) get(activity, "mIssue")).state());
        }
    }

    @Test
    public void reopenIssue_error_showsRetrySnackbarAndLogs() throws Exception {
        IssueService svc = mock(IssueService.class);
        when(svc.editIssue(eq("o"), eq("r"), eq(7L), any()))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Closed, "alice"), false);
            activity.onConfirmed("reopenconfirm", null);
            waitForSnackbarWithText(activity,
                    activity.getString(R.string.issue_error_reopen, 7));
            dismissCurrentSnackbarAsTimeout();
            waitFor(() -> logContains(Gh4Application.LOG_TAG, "Reopening issue failed"), 5000);
            assertTrue(logContains(Gh4Application.LOG_TAG, "Reopening issue failed"));
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void updateUiAfterStateUpdate_withFabAndFragment_updatesBoth() throws Exception {
        IssueService svc = mock(IssueService.class);
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            ensureAuthorizedAs("alice");
            assertTrue("not authorized as alice after launch",
                    Gh4Application.get().isAuthorized()
                            && "alice".equals(Gh4Application.get().getAuthLogin()));
            // showUiIfDone ran during launch while auth was unset, so the
            // FAB was not created then; re-running it now takes the
            // existing-fragment branch and creates the FAB.
            call(activity, "showUiIfDone", new Class<?>[]{}, new Object[]{});
            assertNotNull(get(activity, "mEditFab"));
            com.gh4a.fragment.IssueFragment mockFragment =
                    mock(com.gh4a.fragment.IssueFragment.class);
            set(activity, "mFragment", mockFragment);
            Issue closed = issueWith("alice", IssueState.Closed, null);
            call(activity, "updateUiAfterStateUpdate",
                    new Class<?>[]{Issue.class}, new Object[]{closed});
            assertEquals(IssueState.Closed, ((Issue) get(activity, "mIssue")).state());
            verify(mockFragment).updateState(closed);
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void updateUiAfterStateUpdate_withoutFabOrFragment_stillSetsResult() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        set(activity, "mIssue", issueWith("alice", IssueState.Open, null));
        set(activity, "mEditFab", null);
        set(activity, "mFragment", null);
        Issue closed = issueWith("alice", IssueState.Closed, null);
        call(activity, "updateUiAfterStateUpdate",
                new Class<?>[]{Issue.class}, new Object[]{closed});
        assertEquals(IssueState.Closed, ((Issue) get(activity, "mIssue")).state());
        assertEquals(android.app.Activity.RESULT_OK,
                org.robolectric.Shadows.shadowOf(activity).getResultCode());
    }

    // ----- FAB visibility -----

    @Test
    public void updateFabVisibility_owner_createsFab() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNotNull(get(activity, "mEditFab"));
    }

    @Test
    public void updateFabVisibility_collaborator_createsFab() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "bob", issueWith("alice", IssueState.Open, null), true);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNotNull(get(activity, "mEditFab"));
    }

    @Test
    public void updateFabVisibility_neitherOwnerNorCollaborator_removesFab() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNotNull(get(activity, "mEditFab"));
        set(activity, "mIssue", issueWith("bob", IssueState.Open, null));
        set(activity, "mIsCollaborator", false);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditFab"));
    }

    @Test
    public void updateFabVisibility_nullIssue_removesFab() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNotNull(get(activity, "mEditFab"));
        set(activity, "mIssue", null);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditFab"));
    }

    @Test
    public void updateFabVisibility_existingFab_keepsSameInstance() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        Object first = get(activity, "mEditFab");
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertTrue(first == get(activity, "mEditFab"));
    }

    // ----- onClick -----

    @Test
    public void onClick_editFab_authorized_launchesEdit() throws Exception {
        IssueService svc = mock(IssueService.class);
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            waitFor(() -> get(activity, "mEditFab") != null, 10000);
            assertNotNull(get(activity, "mEditFab"));
            activity.onClick((android.view.View) get(activity, "mEditFab"));
            org.robolectric.shadows.ShadowActivity.IntentForResult started =
                    org.robolectric.Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(started);
            assertEquals(IssueEditActivity.class.getName(),
                    started.intent.getComponent().getClassName());
        }
    }

    @Test
    public void onClick_editFab_unauthorized_startsLoginAndFinishes() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        set(activity, "mIssue", issueWith("alice", IssueState.Open, null));
        android.view.View fab = new android.view.View(appContext());
        fab.setId(R.id.edit_fab);
        activity.onClick(fab);
        assertTrue(activity.isFinishing());
        assertEquals(Github4AndroidActivity.class.getName(),
                org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity()
                        .getComponent().getClassName());
    }

    @Test
    public void onClick_otherView_doesNothing() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        activity.onClick(new android.view.View(appContext()));
        assertFalse(activity.isFinishing());
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
    }

    // ----- onRefresh -----

    @Test
    public void onRefresh_reloadsIssueAndCollaborator() throws Exception {
        IssueService svc = mock(IssueService.class);
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            IssueActivity activity = launchWithEditMock(svc, issue(7), false);
            assertNotNull(get(activity, "mFragment"));
            activity.onRefresh();
            assertNull(get(activity, "mIssue"));
            assertNull(get(activity, "mIsCollaborator"));
            assertNull(get(activity, "mFragment"));
            assertEquals(android.view.View.GONE,
                    ((android.view.ViewGroup) get(activity, "mHeader")).getVisibility());
            waitFor(() -> get(activity, "mIssue") != null, 10000);
            assertNotNull(get(activity, "mIssue"));
            verify(svc, timeout(5000).times(2)).getIssue(eq("o"), eq("r"), eq(7L));
        }
    }

    @Test
    public void onRefresh_withoutFragment_doesNotCrash() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        activity.onRefresh();
        assertFalse(activity.isFinishing());
        assertNull(get(activity, "mFragment"));
    }

    // ----- onBackPressed -----

    @Test
    public void onBackPressed_fragmentConsumes_doesNotFinish() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        com.gh4a.fragment.IssueFragment mockFragment =
                mock(com.gh4a.fragment.IssueFragment.class);
        Mockito.when(mockFragment.onBackPressed()).thenReturn(true);
        set(activity, "mFragment", mockFragment);
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onBackPressed_fragmentDeclines_finishes() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        com.gh4a.fragment.IssueFragment mockFragment =
                mock(com.gh4a.fragment.IssueFragment.class);
        Mockito.when(mockFragment.onBackPressed()).thenReturn(false);
        set(activity, "mFragment", mockFragment);
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onBackPressed_noFragment_finishes() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        assertNull(get(activity, "mFragment"));
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    // ----- auth / launcher callback -----

    @Test
    public void checkForAuthOrExit_authorized_returnsTrue() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        authorizeAs("alice");
        assertTrue((boolean) call(activity, "checkForAuthOrExit", new Class<?>[]{}, new Object[]{}));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void editLauncher_resultOk_refreshesAndSetsResult() throws Exception {
        IssueService svc = mock(IssueService.class);
        try (MockedStatic<ServiceFactory> m1 = mockIssueService(svc);
             MockedStatic<SingleFactory> m2 = mockCollaborator(false)) {
            authorizeAs("alice");
            IssueActivity activity = launchWithEditMock(svc, issueWith("alice", IssueState.Open, null), false);
            waitFor(() -> get(activity, "mEditFab") != null, 10000);
            activity.onClick((android.view.View) get(activity, "mEditFab"));
            androidx.activity.result.ActivityResultRegistry registry =
                    activity.getActivityResultRegistry();
            @SuppressWarnings("unchecked")
            java.util.List<String> launchedKeys =
                    (java.util.List<String>) get(registry, "launchedKeys");
            assertEquals(1, launchedKeys.size());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Integer> keyToRc =
                    (java.util.Map<String, Integer>) get(registry, "keyToRc");
            int rc = keyToRc.get(launchedKeys.get(0));
            assertTrue(registry.dispatchResult(rc, android.app.Activity.RESULT_OK, null));
            waitFor(() -> org.robolectric.Shadows.shadowOf(activity).getResultCode()
                    == android.app.Activity.RESULT_OK, 5000);
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            verify(svc, timeout(5000).times(2)).getIssue(eq("o"), eq("r"), eq(7L));
        }
    }

    @Test
    public void updateFabVisibility_collaboratorButNullIssue_removesFab() {
        IssueActivity activity = launch(IssueActivity.class,
                IssueActivity.makeIntent(appContext(), "o", "r", 7));
        setupMenuState(activity, "alice", issueWith("alice", IssueState.Open, null), false);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNotNull(get(activity, "mEditFab"));
        // Collaborator but no issue: (owner || collaborator) is true, yet
        // the trailing "mIssue != null" is false, so no FAB.
        set(activity, "mIssue", null);
        set(activity, "mIsCollaborator", true);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditFab"));
    }
}
