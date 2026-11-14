package com.gh4a.activities;

import android.app.Dialog;
import android.content.Intent;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.SingleFactory;
import com.meisolsson.githubsdk.model.Issue;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.PullRequest;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.request.pull_request.MergeRequest;
import com.meisolsson.githubsdk.service.issues.IssueService;
import com.meisolsson.githubsdk.service.pull_request.PullRequestService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;

import com.gh4a.Gh4Application;
import com.gh4a.fragment.CommitCompareFragment;
import com.gh4a.fragment.ConfirmationDialogFragment;
import com.gh4a.fragment.PullRequestConversationFragment;
import com.gh4a.fragment.PullRequestFilesFragment;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.IntentUtils;
import com.google.android.material.textfield.TextInputLayout;
import com.meisolsson.githubsdk.model.MergeResponse;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.PullRequestMarker;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.Review;
import com.meisolsson.githubsdk.model.ReviewState;
import com.meisolsson.githubsdk.model.request.pull_request.EditPullRequest;
import com.meisolsson.githubsdk.service.pull_request.PullRequestReviewService;

import org.mockito.ArgumentCaptor;
import org.robolectric.Shadows;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import com.google.android.material.appbar.AppBarLayout;
import com.meisolsson.githubsdk.model.UserType;

import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.gh4a.activities.ActivityTestHelper.dismissCurrentSnackbarAsTimeout;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static com.gh4a.activities.ActivityTestHelper.waitForSnackbarWithText;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class PullRequestActivityTest {
    private PullRequest pr(boolean merged, IssueState state, boolean draft) {
        return PullRequest.builder()
                .number(42)
                .title("Add feature")
                .merged(merged)
                .mergeableState(PullRequest.MergeableState.Clean)
                .state(state)
                .draft(draft)
                .htmlUrl("https://github.com/o/r/pull/42")
                .user(User.builder().login("alice").id(1L).build())
                .build();
    }

    private Issue issue() {
        return Issue.builder().number(42).title("Add feature").state(IssueState.Open)
                .user(User.builder().login("alice").id(1L).build()).build();
    }

    @Test
    public void makeIntent_fromIssue_extractsRepoAndNumber() {
        Issue i = Issue.builder().number(3)
                .url("https://api.github.com/repos/o/r/issues/3").build();
        Intent intent = PullRequestActivity.makeIntent(appContext(), i);
        assertEquals(PullRequestActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("o", intent.getStringExtra("owner"));
        assertEquals("r", intent.getStringExtra("repo"));
        assertEquals(3, intent.getIntExtra("number", -1));
    }

    @Test(expected = NullPointerException.class)
    public void makeIntent_nullIssue_throwsNpe_A1_4() {
        // Unlike IssueActivity.makeIntent, the (Issue) overload dereferences
        // issue.number() with no null guard on the Issue itself.
        PullRequestActivity.makeIntent(appContext(), (Issue) null);
    }

    @Test
    public void makeIntent_loginRepoNumber_defaults() {
        Intent intent = PullRequestActivity.makeIntent(appContext(), "o", "r", 42);
        assertEquals(42, intent.getIntExtra("number", -1));
        assertEquals(-1, intent.getIntExtra("initial_page", -99));
        assertNull(intent.getParcelableExtra("initial_comment"));
    }

    @Test
    public void launch_titleAndSubtitle() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        assertEquals(appContext().getString(R.string.pull_request_title) + " #42",
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.displayDetachAction());
    }

    @Test
    public void navigateUp_goesToPrList() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        Intent up = activity.navigateUp();
        assertEquals(IssueListActivity.class.getName(), up.getComponent().getClassName());
        assertTrue(up.getBooleanExtra("is_pull_request", false));
    }

    @Test
    public void getActivityUri_buildsPullUrl() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        assertEquals("https://github.com/o/r/pull/42", activity.getActivityUri().toString());
    }

    @Test
    public void getTabTitleResIds_nullBeforeLoad() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        assertNull(activity.getTabTitleResIds());
    }

    @Test
    public void onCreateOptionsMenu_withoutPr_removesPrItems() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertNull(menu.findItem(R.id.share));
        assertNull(menu.findItem(R.id.browser));
        assertNull(menu.findItem(R.id.copy_number));
        assertNull(menu.findItem(R.id.copy_link));
        assertNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void onOptionsItemSelected_shareWithoutPr_doesNotCrash() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.share, 0, "share")));
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
    }

    @Test
    public void fillHeader_openPr_showsOpen() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        call(activity, "fillHeader", new Class<?>[]{}, new Object[]{});
        assertEquals("OPEN",
                ((android.widget.TextView) activity.findViewById(R.id.tv_state)).getText().toString());
        assertEquals("Add feature",
                ((android.widget.TextView) activity.findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void fillHeader_mergedPr_showsMerged() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        set(activity, "mPullRequest", pr(true, IssueState.Closed, false));
        call(activity, "fillHeader", new Class<?>[]{}, new Object[]{});
        assertEquals("MERGED",
                ((android.widget.TextView) activity.findViewById(R.id.tv_state)).getText().toString());
    }

    @Test
    public void fillHeader_draftPr_showsDraft() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        set(activity, "mPullRequest", pr(false, IssueState.Open, true));
        call(activity, "fillHeader", new Class<?>[]{}, new Object[]{});
        assertEquals("DRAFT",
                ((android.widget.TextView) activity.findViewById(R.id.tv_state)).getText().toString());
    }

    @Test
    public void fillHeader_closedPr_showsClosed() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        set(activity, "mPullRequest", pr(false, IssueState.Closed, false));
        call(activity, "fillHeader", new Class<?>[]{}, new Object[]{});
        assertEquals("CLOSED",
                ((android.widget.TextView) activity.findViewById(R.id.tv_state)).getText().toString());
    }

    @Test
    public void load_mockedServices_fillsPrIssueAndCollaborator() throws Exception {
        PullRequestService prService = mock(PullRequestService.class);
        IssueService issueService = mock(IssueService.class);
        when(prService.getPullRequest("o", "r", 42))
                .thenReturn(Single.just(Response.success(pr(false, IssueState.Open, false))));
        when(issueService.getIssue("o", "r", 42))
                .thenReturn(Single.just(Response.success(issue())));
        try (MockedStatic<ServiceFactory> mockedSvc = Mockito.mockStatic(ServiceFactory.class);
             MockedStatic<SingleFactory> mockedSingle = Mockito.mockStatic(SingleFactory.class)) {
            mockedSvc.when(() -> ServiceFactory.get(PullRequestService.class, false)).thenReturn(prService);
            mockedSvc.when(() -> ServiceFactory.get(IssueService.class, false)).thenReturn(issueService);
            mockedSingle.when(() -> SingleFactory.isAppUserRepoCollaborator("o", "r", false))
                    .thenReturn(Single.just(true));
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> get(activity, "mPullRequest") != null, 10000);
            assertEquals(42, ((PullRequest) get(activity, "mPullRequest")).number().intValue());
            assertEquals(42, ((Issue) get(activity, "mIssue")).number().intValue());
            assertEquals(Boolean.TRUE, get(activity, "mIsCollaborator"));
            assertTrue(activity.getTabTitleResIds() != null);
        }
    }

    @Test
    public void mergeMethodDesc_toStringReturnsText() throws Exception {
        Class<?> descClass = null;
        for (Class<?> c : PullRequestActivity.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("MergeMethodDesc")) {
                descClass = c;
            }
        }
        java.lang.reflect.Constructor<?> ctor = descClass.getDeclaredConstructor(
                String.class, MergeRequest.Method.class);
        ctor.setAccessible(true);
        Object desc = ctor.newInstance("Squash and merge", MergeRequest.Method.Squash);
        assertEquals("Squash and merge", desc.toString());
        assertEquals(MergeRequest.Method.Squash, get(desc, "action"));
    }

    @Test
    public void mergeDialogFragment_newInstance_storesPr() {
        PullRequestActivity.MergeDialogFragment f =
                PullRequestActivity.MergeDialogFragment.newInstance(pr(false, IssueState.Open, false));
        assertEquals(42, ((PullRequest) f.getArguments().getParcelable("pr")).number().intValue());
    }

    @Test
    public void mergeDialogFragment_onCreateDialog_buildsDialogWithThreeMethods() {
        PullRequestActivity activity = launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
        PullRequestActivity.MergeDialogFragment f =
                PullRequestActivity.MergeDialogFragment.newInstance(pr(false, IssueState.Open, false));
        activity.getSupportFragmentManager().beginTransaction().add(f, "merge").commitNow();
        Dialog dialog = f.onCreateDialog(null);
        assertTrue(dialog != null);
        dialog.show();
        android.widget.TextView titleView =
                dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        assertEquals(appContext().getString(R.string.pull_message_dialog_title, 42),
                titleView.getText().toString());
        android.widget.Spinner spinner =
                (android.widget.Spinner) get(f, "mMergeMethodSelector");
        assertEquals(3, spinner.getAdapter().getCount());
        assertEquals(appContext().getString(R.string.pull_merge_method_merge),
                spinner.getAdapter().getItem(0).toString());
    }

    // ---------- additional helpers ----------

    private void authorizeAs(String login) {
        // Same retry loop as IssueActivityTest/IssueListActivityTest: addAccount is
        // intermittently ineffective across tests in the same class run.
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

    /** Stubs the three services the activity loads from, for both force values. */
    private static class Mocks implements AutoCloseable {
        final MockedStatic<ServiceFactory> svc;
        final MockedStatic<SingleFactory> single;
        final PullRequestService prService = mock(PullRequestService.class);
        final IssueService issueService = mock(IssueService.class);
        final PullRequestReviewService reviewService = mock(PullRequestReviewService.class);

        Mocks(PullRequest pr, Issue issue, boolean collaborator, List<Review> reviews) {
            // CALLS_REAL_METHODS so any unstubbed lookup degrades to the real
            // service (failing network call) instead of a null-service NPE.
            svc = Mockito.mockStatic(ServiceFactory.class,
                    Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
            single = Mockito.mockStatic(SingleFactory.class,
                    Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
            try {
                // All arguments raw values: mixing a raw Class literal with a
                // matcher (anyBoolean()) throws InvalidUseOfMatchersException,
                // and a constructor throw would leak the static mock.
                svc.when(() -> ServiceFactory.get(PullRequestService.class, false))
                        .thenReturn(prService);
                svc.when(() -> ServiceFactory.get(PullRequestService.class, true))
                        .thenReturn(prService);
                svc.when(() -> ServiceFactory.get(IssueService.class, false))
                        .thenReturn(issueService);
                svc.when(() -> ServiceFactory.get(IssueService.class, true))
                        .thenReturn(issueService);
                svc.when(() -> ServiceFactory.getForFullPagedLists(
                        PullRequestReviewService.class, false)).thenReturn(reviewService);
                svc.when(() -> ServiceFactory.getForFullPagedLists(
                        PullRequestReviewService.class, true)).thenReturn(reviewService);
                when(prService.getPullRequest("o", "r", 42))
                        .thenReturn(Single.just(Response.success(pr)));
                when(issueService.getIssue("o", "r", 42))
                        .thenReturn(Single.just(Response.success(issue)));
                when(reviewService.getReviews(eq("o"), eq("r"), eq(42L), anyLong()))
                        .thenReturn(Single.just(Response.success(Page.<Review>builder()
                                .items(reviews).build())));
                single.when(() -> SingleFactory.isAppUserRepoCollaborator("o", "r", false))
                        .thenReturn(Single.just(collaborator));
                single.when(() -> SingleFactory.isAppUserRepoCollaborator("o", "r", true))
                        .thenReturn(Single.just(collaborator));
            } catch (RuntimeException e) {
                svc.close();
                single.close();
                throw e;
            }
        }

        @Override
        public void close() {
            svc.close();
            single.close();
        }
    }

    /** Launch with the load fully mocked (empty review list), wait for mPullRequest. */
    private PullRequestActivity launchLoaded(PullRequest pr, Issue issue, boolean collaborator)
            throws Exception {
        try (Mocks mocks = new Mocks(pr, issue, collaborator, new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            return activity;
        }
    }

    private PullRequestActivity launchPlain() {
        return launch(PullRequestActivity.class,
                PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
    }

    private PullRequest prWithUser(String login, boolean merged, IssueState state, boolean draft) {
        return PullRequest.builder()
                .number(42)
                .title("Add feature")
                .merged(merged)
                .mergeableState(PullRequest.MergeableState.Clean)
                .state(state)
                .draft(draft)
                .htmlUrl("https://github.com/o/r/pull/42")
                .user(User.builder().login(login).id(2L).build())
                .build();
    }

    private PullRequest prWithMergeable(PullRequest.MergeableState mergeableState) {
        return PullRequest.builder()
                .number(42)
                .title("Add feature")
                .merged(false)
                .mergeableState(mergeableState)
                .state(IssueState.Open)
                .draft(false)
                .htmlUrl("https://github.com/o/r/pull/42")
                .user(User.builder().login("bob").id(2L).build())
                .build();
    }

    private PullRequest prWithBaseHead() {
        Repository repo = mock(Repository.class);
        when(repo.owner()).thenReturn(User.builder().login("o").id(3L).build());
        when(repo.name()).thenReturn("r");
        PullRequestMarker base = mock(PullRequestMarker.class);
        when(base.label()).thenReturn("o:main");
        when(base.sha()).thenReturn("base-sha");
        when(base.repo()).thenReturn(repo);
        PullRequestMarker head = mock(PullRequestMarker.class);
        when(head.label()).thenReturn("o:feature");
        when(head.sha()).thenReturn("head-sha");
        when(head.repo()).thenReturn(repo);
        return PullRequest.builder()
                .number(42)
                .title("Add feature")
                .merged(false)
                .mergeableState(PullRequest.MergeableState.Clean)
                .state(IssueState.Open)
                .draft(false)
                .htmlUrl("https://github.com/o/r/pull/42")
                .user(User.builder().login("alice").id(1L).build())
                .base(base)
                .head(head)
                .build();
    }

    private Issue issueWithUser(String login) {
        return Issue.builder().number(42).title("Add feature").state(IssueState.Open)
                .user(User.builder().login(login).id(2L).build()).build();
    }

    private Issue issueClosedBy(String login, String closerLogin) {
        return Issue.builder().number(42).title("Add feature").state(IssueState.Closed)
                .user(User.builder().login(login).id(1L).build())
                .closedBy(User.builder().login(closerLogin).id(2L).build()).build();
    }

    private Review review(ReviewState state, String login) {
        Review r = mock(Review.class);
        when(r.state()).thenReturn(state);
        when(r.user()).thenReturn(User.builder().login(login).id(1L).build());
        return r;
    }

    private MenuBuilder menuFor(PullRequestActivity activity, PullRequest pr, Issue issue,
            Boolean collaborator, boolean pendingReviewLoaded) throws Exception {
        set(activity, "mPullRequest", pr);
        set(activity, "mIssue", issue);
        set(activity, "mIsCollaborator", collaborator);
        set(activity, "mPendingReviewLoaded", pendingReviewLoaded);
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        return menu;
    }

    // ---------- onCreateOptionsMenu matrix ----------

    @Test
    public void menu_openPrCreatorCollaborator_showsCloseAndMergeNotReopen() throws Exception {
        PullRequestActivity activity = launchLoaded(
                pr(false, IssueState.Open, false), issue(), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity, pr(false, IssueState.Open, false), issue(), true, true);
        assertNotNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_reopen));
        assertNotNull(menu.findItem(R.id.pull_merge));
        assertTrue(menu.findItem(R.id.pull_merge).isEnabled());
        assertNotNull(menu.findItem(R.id.share));
        assertNotNull(menu.findItem(R.id.browser));
        assertNotNull(menu.findItem(R.id.copy_number));
        assertNotNull(menu.findItem(R.id.copy_link));
        assertNotNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void menu_openPrNonCreatorNonCollaborator_removesActionItems() throws Exception {
        PullRequestActivity activity = launchLoaded(
                prWithUser("bob", false, IssueState.Open, false), issueWithUser("bob"), false);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                prWithUser("bob", false, IssueState.Open, false), issueWithUser("bob"), false, true);
        assertNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_reopen));
        assertNull(menu.findItem(R.id.pull_merge));
        assertNotNull(menu.findItem(R.id.share));
        assertNotNull(menu.findItem(R.id.browser));
        assertNotNull(menu.findItem(R.id.copy_number));
        assertNotNull(menu.findItem(R.id.copy_link));
        assertNotNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void menu_openPrCollaboratorNotCreator_showsCloseAndMerge() throws Exception {
        PullRequestActivity activity = launchLoaded(
                prWithUser("bob", false, IssueState.Open, false), issueWithUser("bob"), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                prWithUser("bob", false, IssueState.Open, false), issueWithUser("bob"), true, true);
        assertNotNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_reopen));
        assertNotNull(menu.findItem(R.id.pull_merge));
        assertTrue(menu.findItem(R.id.pull_merge).isEnabled());
    }

    @Test
    public void menu_closedPrCreatorCollaborator_showsReopenOnly() throws Exception {
        // Closed but NOT merged: merged() true would remove pull_reopen on its own.
        PullRequestActivity activity = launchLoaded(
                pr(false, IssueState.Closed, false), issueClosedBy("alice", "alice"), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                pr(false, IssueState.Closed, false), issueClosedBy("alice", "alice"), true, true);
        assertNull(menu.findItem(R.id.pull_close));
        assertNotNull(menu.findItem(R.id.pull_reopen));
        assertNull(menu.findItem(R.id.pull_merge));
        assertNull(menu.findItem(R.id.pull_review));
        assertNotNull(menu.findItem(R.id.share));
    }

    @Test
    public void menu_closedPrCreatorCloserIsCreator_showsReopen() throws Exception {
        PullRequestActivity activity = launchLoaded(
                pr(false, IssueState.Closed, false), issueClosedBy("alice", "alice"), false);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                pr(false, IssueState.Closed, false), issueClosedBy("alice", "alice"), false, true);
        assertNull(menu.findItem(R.id.pull_close));
        assertNotNull(menu.findItem(R.id.pull_reopen));
        assertNull(menu.findItem(R.id.pull_merge));
        assertNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void menu_closedPrCreatorCloserNotCreator_hidesReopen() throws Exception {
        PullRequestActivity activity = launchLoaded(
                pr(false, IssueState.Closed, false), issueClosedBy("alice", "bob"), false);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                pr(false, IssueState.Closed, false), issueClosedBy("alice", "bob"), false, true);
        assertNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_reopen));
        assertNull(menu.findItem(R.id.pull_merge));
        assertNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void menu_mergedPr_hidesReopen() throws Exception {
        PullRequestActivity activity = launchLoaded(
                pr(true, IssueState.Closed, false), issueClosedBy("alice", "alice"), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                pr(true, IssueState.Closed, false), issueClosedBy("alice", "alice"), true, true);
        assertNull(menu.findItem(R.id.pull_reopen));
    }

    @Test
    public void menu_draftPrCollaborator_hidesMerge() throws Exception {
        PullRequestActivity activity = launchLoaded(
                prWithUser("bob", false, IssueState.Open, true), issueWithUser("bob"), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                prWithUser("bob", false, IssueState.Open, true), issueWithUser("bob"), true, true);
        assertNotNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_merge));
    }

    @Test
    public void menu_blockedMergeable_disablesMergeItem() throws Exception {
        PullRequestActivity activity = launchLoaded(
                prWithMergeable(PullRequest.MergeableState.Blocked), issueWithUser("bob"), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                prWithMergeable(PullRequest.MergeableState.Blocked), issueWithUser("bob"), true, true);
        assertNotNull(menu.findItem(R.id.pull_merge));
        assertFalse(menu.findItem(R.id.pull_merge).isEnabled());
    }

    @Test
    public void menu_unstableMergeable_keepsMergeEnabled() throws Exception {
        PullRequestActivity activity = launchLoaded(
                prWithMergeable(PullRequest.MergeableState.Unstable), issueWithUser("bob"), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity,
                prWithMergeable(PullRequest.MergeableState.Unstable), issueWithUser("bob"), true, true);
        assertNotNull(menu.findItem(R.id.pull_merge));
        assertTrue(menu.findItem(R.id.pull_merge).isEnabled());
    }

    @Test
    public void menu_pendingReviewNotLoaded_hidesReview() throws Exception {
        PullRequestActivity activity = launchLoaded(
                pr(false, IssueState.Open, false), issue(), true);
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        MenuBuilder menu = menuFor(activity, pr(false, IssueState.Open, false), issue(), true, false);
        assertNull(menu.findItem(R.id.pull_review));
        assertNotNull(menu.findItem(R.id.pull_close));
        assertNotNull(menu.findItem(R.id.pull_merge));
    }

    @Test
    public void menu_unauthorizedCreator_hidesActionItems() throws Exception {
        PullRequestActivity activity = launchLoaded(
                pr(false, IssueState.Open, false), issue(), true);
        assertFalse(Gh4Application.get().isAuthorized());
        MenuBuilder menu = menuFor(activity, pr(false, IssueState.Open, false), issue(), true, true);
        assertNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_reopen));
        assertNull(menu.findItem(R.id.pull_merge));
        assertNotNull(menu.findItem(R.id.share));
        assertNotNull(menu.findItem(R.id.browser));
        assertNotNull(menu.findItem(R.id.copy_number));
        assertNotNull(menu.findItem(R.id.copy_link));
        assertNotNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void menu_noPr_removesAllPrItems() throws Exception {
        PullRequestActivity activity = launchPlain();
        MenuBuilder menu = menuFor(activity, null, null, null, false);
        assertNull(menu.findItem(R.id.pull_close));
        assertNull(menu.findItem(R.id.pull_reopen));
        assertNull(menu.findItem(R.id.pull_merge));
        assertNull(menu.findItem(R.id.share));
        assertNull(menu.findItem(R.id.browser));
        assertNull(menu.findItem(R.id.copy_number));
        assertNull(menu.findItem(R.id.copy_link));
        assertNull(menu.findItem(R.id.pull_review));
    }


    /** Warm-up launch + authorize + mocked launch, so the load runs authorized. */
    private PullRequestActivity launchLoadedAuthorized(PullRequest pr, Issue issue,
            boolean collaborator) throws Exception {
        launchPlain();
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        try (Mocks mocks = new Mocks(pr, issue, collaborator, new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            // Authorization can be lost across the second launch (secure-prefs
            // backend jitter, same as the GistActivity flake): re-authorize and
            // re-evaluate the FAB, which the load's updateFabVisibility may have
            // computed while unauthorized.
            ensureAuthorizedAs("alice");
            call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
            assertTrue(Gh4Application.get().isAuthorized());
            return activity;
        }
    }

    // ---------- onOptionsItemSelected ----------

    @Test
    public void options_merge_showsMergeDialog() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.pull_merge, 0, "merge")));
        activity.getSupportFragmentManager().executePendingTransactions();
        PullRequestActivity.MergeDialogFragment f =
                (PullRequestActivity.MergeDialogFragment) activity.getSupportFragmentManager()
                        .findFragmentByTag("mergemethod");
        assertNotNull(f);
        assertEquals(42, ((PullRequest) f.getArguments().getParcelable("pr")).number().intValue());
    }

    @Test
    public void options_review_launchesCreateReview() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        set(activity, "mPendingReview", null);
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onOptionsItemSelected(menu.add(0, R.id.pull_review, 0, "review"));
        ShadowActivity.IntentForResult result =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(result);
        assertEquals(CreateReviewActivity.class.getName(),
                result.intent.getComponent().getClassName());
        assertEquals("o", result.intent.getStringExtra("owner"));
        assertEquals("r", result.intent.getStringExtra("repo"));
        assertEquals(42, result.intent.getIntExtra("pr_number", -1));
        assertFalse(result.intent.getBooleanExtra("draft_pr", true));
        assertNull(result.intent.getParcelableExtra("pending_review"));
    }

    @Test
    public void options_review_draftPr_passesDraftFlag() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, true));
        set(activity, "mPendingReview", null);
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onOptionsItemSelected(menu.add(0, R.id.pull_review, 0, "review"));
        ShadowActivity.IntentForResult result =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(result);
        assertTrue(result.intent.getBooleanExtra("draft_pr", false));
    }

    @Test
    public void options_close_showsConfirmDialogWithCloseText() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onOptionsItemSelected(menu.add(0, R.id.pull_close, 0, "close"));
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("opencloseconfirm");
        assertNotNull(f);
        Bundle args = f.getArguments();
        assertEquals(appContext().getString(R.string.close_pull_request_confirm),
                args.getString("text"));
        assertEquals(R.string.pull_request_close, args.getInt("confirmButtonTextResId"));
        assertTrue(args.getBoolean("showWarning"));
        assertFalse(((Bundle) args.getParcelable("data")).getBoolean("reopen"));
    }

    @Test
    public void options_reopen_showsConfirmDialogWithReopenText() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Closed, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onOptionsItemSelected(menu.add(0, R.id.pull_reopen, 0, "reopen"));
        activity.getSupportFragmentManager().executePendingTransactions();
        ConfirmationDialogFragment f = (ConfirmationDialogFragment) activity
                .getSupportFragmentManager().findFragmentByTag("opencloseconfirm");
        assertNotNull(f);
        Bundle args = f.getArguments();
        assertEquals(appContext().getString(R.string.reopen_pull_request_confirm),
                args.getString("text"));
        assertEquals(R.string.pull_request_reopen, args.getInt("confirmButtonTextResId"));
        assertTrue(args.getBoolean("showWarning"));
        assertTrue(((Bundle) args.getParcelable("data")).getBoolean("reopen"));
    }

    @Test
    public void options_share_startsChooserWithUrl() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onOptionsItemSelected(menu.add(0, R.id.share, 0, "share"));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
        Intent send = started.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, send.getAction());
        assertEquals("https://github.com/o/r/pull/42", send.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals(appContext().getString(R.string.share_pull_subject, 42, "Add feature", "o/r"),
                send.getStringExtra(Intent.EXTRA_SUBJECT));
    }

    @Test
    public void options_shareWithoutHtmlUrl_doesNothing() throws Exception {
        PullRequestActivity activity = launchPlain();
        PullRequest prNoUrl = mock(PullRequest.class);
        when(prNoUrl.htmlUrl()).thenReturn(null);
        set(activity, "mPullRequest", prNoUrl);
        MenuBuilder menu = new MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.share, 0, "share")));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void options_browserWithoutBrowser_showsNoBrowserToast() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
        assertEquals(appContext().getString(R.string.no_browser_found),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void options_browserWithoutHtmlUrl_doesNothing() throws Exception {
        PullRequestActivity activity = launchPlain();
        PullRequest prNoUrl = mock(PullRequest.class);
        when(prNoUrl.htmlUrl()).thenReturn(null);
        set(activity, "mPullRequest", prNoUrl);
        MenuBuilder menu = new MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void options_copyNumber_copiesNumberToClipboard() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.copy_number, 0, "copy")));
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("42", cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void options_copyLink_copiesLinkAndShowsSnackbar() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        MenuBuilder menu = new MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.copy_link, 0, "copy")));
        ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("https://github.com/o/r/pull/42",
                cm.getPrimaryClip().getItemAt(0).getText().toString());
        waitForSnackbarWithText(activity, appContext().getString(R.string.link_copied));
        dismissCurrentSnackbarAsTimeout();
    }

    @Test
    public void options_unknownItem_returnsFalse() throws Exception {
        PullRequestActivity activity = launchPlain();
        MenuBuilder menu = new MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 99999, 0, "unknown")));
    }

    // ---------- onClick ----------

    @Test
    public void onClick_editFab_launchesIssueEdit() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                pr(false, IssueState.Open, false), issue(), false);
        Object fab = get(activity, "mEditFab");
        assertNotNull(fab);
        activity.onClick((View) fab);
        ShadowActivity.IntentForResult result =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(result);
        assertEquals(IssueEditActivity.class.getName(),
                result.intent.getComponent().getClassName());
    }

    @Test
    public void onClick_gravatar_launchesUserActivity() throws Exception {
        PullRequestActivity activity = launchPlain();
        View gravatar = new View(appContext());
        gravatar.setId(R.id.iv_gravatar);
        gravatar.setTag(User.builder().login("alice").id(1L).build());
        activity.onClick(gravatar);
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("alice", started.getStringExtra("login"));
    }

    @Test
    public void onClick_otherView_doesNothing() throws Exception {
        PullRequestActivity activity = launchPlain();
        View other = new View(appContext());
        other.setId(R.id.tv_title);
        activity.onClick(other);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    // ---------- getTabTitleResIds / makeFragment / fragment callbacks ----------

    @Test
    public void getTabTitleResIds_prWithoutIssue_returnsNull() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        set(activity, "mIssue", null);
        set(activity, "mIsCollaborator", true);
        assertNull(activity.getTabTitleResIds());
    }

    @Test
    public void getTabTitleResIds_prAndIssueWithoutCollaborator_returnsNull() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        set(activity, "mIssue", issue());
        set(activity, "mIsCollaborator", null);
        assertNull(activity.getTabTitleResIds());
    }

    @Test
    public void makeFragment_commits_usesBaseAndHead() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", prWithBaseHead());
        Fragment f = (Fragment) call(activity, "makeFragment",
                new Class<?>[]{int.class}, new Object[]{PullRequestActivity.PAGE_COMMITS});
        assertTrue(f instanceof CommitCompareFragment);
        Bundle args = f.getArguments();
        assertEquals("base-sha", args.getString("base"));
        assertEquals("o:main", args.getString("base_label"));
        assertEquals("head-sha", args.getString("head"));
        assertEquals("o:feature", args.getString("head_label"));
        assertEquals(42, args.getInt("pr"));
    }

    @Test
    public void makeFragment_commitsWithoutBaseHead_passesNulls() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        Fragment f = (Fragment) call(activity, "makeFragment",
                new Class<?>[]{int.class}, new Object[]{PullRequestActivity.PAGE_COMMITS});
        assertTrue(f instanceof CommitCompareFragment);
        assertNull(f.getArguments().getString("base"));
        assertNull(f.getArguments().getString("base_label"));
        assertNull(f.getArguments().getString("head"));
        assertNull(f.getArguments().getString("head_label"));
    }

    @Test
    public void makeFragment_files_usesHeadSha() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", prWithBaseHead());
        Fragment f = (Fragment) call(activity, "makeFragment",
                new Class<?>[]{int.class}, new Object[]{PullRequestActivity.PAGE_FILES});
        assertTrue(f instanceof PullRequestFilesFragment);
        assertEquals("head-sha", f.getArguments().getString("head"));
        assertEquals(42, f.getArguments().getInt("number"));
    }

    @Test
    public void makeFragment_filesWithoutHead_passesNullHead() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        Fragment f = (Fragment) call(activity, "makeFragment",
                new Class<?>[]{int.class}, new Object[]{PullRequestActivity.PAGE_FILES});
        assertTrue(f instanceof PullRequestFilesFragment);
        assertNull(f.getArguments().getString("head"));
    }

    @Test
    public void makeFragment_conversation_passesPrIssueAndClearsInitialComment() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", prWithBaseHead());
        set(activity, "mIssue", issue());
        set(activity, "mIsCollaborator", true);
        set(activity, "mInitialComment", new IntentUtils.InitialCommentMarker(123L));
        Fragment f = (Fragment) call(activity, "makeFragment",
                new Class<?>[]{int.class}, new Object[]{PullRequestActivity.PAGE_CONVERSATION});
        assertTrue(f instanceof PullRequestConversationFragment);
        Bundle args = f.getArguments();
        assertEquals(42, ((PullRequest) args.getParcelable("pr")).number().intValue());
        assertEquals("o", args.getString("owner"));
        assertEquals("r", args.getString("repo"));
        assertTrue(args.getBoolean("collaborator"));
        assertNotNull(args.getParcelable("initial_comment"));
        assertNull(get(activity, "mInitialComment"));
    }

    @Test
    public void onFragmentInstantiated_conversation_setsConversationFragment() throws Exception {
        PullRequestActivity activity = launchPlain();
        PullRequestConversationFragment conv = mock(PullRequestConversationFragment.class);
        call(activity, "onFragmentInstantiated",
                new Class<?>[]{Fragment.class, int.class},
                new Object[]{conv, PullRequestActivity.PAGE_CONVERSATION});
        assertEquals(conv, get(activity, "mConversationFragment"));
        PullRequestConversationFragment other = mock(PullRequestConversationFragment.class);
        call(activity, "onFragmentInstantiated",
                new Class<?>[]{Fragment.class, int.class},
                new Object[]{other, PullRequestActivity.PAGE_COMMITS});
        assertEquals(conv, get(activity, "mConversationFragment"));
    }

    @Test
    public void onFragmentDestroyed_matchingFragment_clearsIt() throws Exception {
        PullRequestActivity activity = launchPlain();
        PullRequestConversationFragment conv = mock(PullRequestConversationFragment.class);
        PullRequestConversationFragment other = mock(PullRequestConversationFragment.class);
        set(activity, "mConversationFragment", conv);
        call(activity, "onFragmentDestroyed",
                new Class<?>[]{Fragment.class}, new Object[]{other});
        assertEquals(conv, get(activity, "mConversationFragment"));
        call(activity, "onFragmentDestroyed",
                new Class<?>[]{Fragment.class}, new Object[]{conv});
        assertNull(get(activity, "mConversationFragment"));
    }

    @Test
    public void fragmentNeedsRefresh_returnsTrue() throws Exception {
        PullRequestActivity activity = launchPlain();
        assertTrue((Boolean) call(activity, "fragmentNeedsRefresh",
                new Class<?>[]{Fragment.class}, new Object[]{mock(Fragment.class)}));
    }

    @Test
    public void onCommentsUpdated_withConversationFragment_reloadsEvents() throws Exception {
        PullRequestActivity activity = launchPlain();
        PullRequestConversationFragment conv = mock(PullRequestConversationFragment.class);
        set(activity, "mConversationFragment", conv);
        activity.onCommentsUpdated();
        verify(conv).reloadEvents(true);
    }

    @Test
    public void onCommentsUpdated_withoutConversationFragment_doesNothing() throws Exception {
        PullRequestActivity activity = launchPlain();
        activity.onCommentsUpdated();
    }


    // ---------- updateFabVisibility ----------

    @Test
    public void fab_owner_createsFabWithOpenState() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                pr(false, IssueState.Open, false), issue(), false);
        Object fab = get(activity, "mEditFab");
        assertNotNull(fab);
        assertEquals(IssueState.Open, get(fab, "mState"));
        assertEquals(Boolean.FALSE, get(fab, "mMerged"));
        assertEquals(Boolean.FALSE, get(fab, "mDraft"));
    }

    @Test
    public void fab_collaborator_createsFab() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                prWithUser("bob", false, IssueState.Open, false), issueWithUser("bob"), true);
        assertNotNull(get(activity, "mEditFab"));
    }

    @Test
    public void fab_neitherOwnerNorCollaborator_noFab() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                prWithUser("bob", false, IssueState.Open, false), issueWithUser("bob"), false);
        assertNull(get(activity, "mEditFab"));
    }

    @Test
    public void fab_prBecomesNull_removesFab() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                pr(false, IssueState.Open, false), issue(), false);
        assertNotNull(get(activity, "mEditFab"));
        set(activity, "mPullRequest", null);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditFab"));
    }

    @Test
    public void fab_stateUpdatedOnReusedFab() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                pr(false, IssueState.Open, false), issue(), false);
        Object fab = get(activity, "mEditFab");
        assertNotNull(fab);
        set(activity, "mPullRequest", pr(false, IssueState.Closed, false));
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertEquals(fab, get(activity, "mEditFab"));
        assertEquals(IssueState.Closed, get(fab, "mState"));
    }

    @Test
    public void fab_mergedPr_showsMergedState() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                pr(true, IssueState.Closed, false), issueClosedBy("alice", "alice"), true);
        Object fab = get(activity, "mEditFab");
        assertNotNull(fab);
        assertEquals(IssueState.Closed, get(fab, "mState"));
        assertEquals(Boolean.TRUE, get(fab, "mMerged"));
    }

    @Test
    public void fab_draftPr_showsDraftState() throws Exception {
        PullRequestActivity activity = launchLoadedAuthorized(
                pr(false, IssueState.Open, true), issue(), false);
        Object fab = get(activity, "mEditFab");
        assertNotNull(fab);
        assertEquals(Boolean.TRUE, get(fab, "mDraft"));
    }

    // ---------- onRefresh ----------

    @Test
    public void onRefresh_resetsAndReloads() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            activity.onRefresh();
            assertNull(get(activity, "mIssue"));
            assertNull(get(activity, "mPullRequest"));
            assertNull(get(activity, "mIsCollaborator"));
            assertNull(get(activity, "mPendingReview"));
            assertEquals(View.GONE, ((View) get(activity, "mHeader")).getVisibility());
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            assertEquals(42, ((PullRequest) get(activity, "mPullRequest")).number().intValue());
            assertNotNull(get(activity, "mIssue"));
        }
    }

    @Test
    public void onRefresh_withFab_postsFabUpdateAndReloads() throws Exception {
        launchPlain();
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            ensureAuthorizedAs("alice");
            call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
            assertNotNull(get(activity, "mEditFab"));
            activity.onRefresh();
            assertNull(get(activity, "mPullRequest"));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            assertNotNull(get(activity, "mEditFab"));
        }
    }

    // ---------- launcher callbacks ----------

    @Test
    public void editLauncher_resultOk_refreshesAndSetsResult() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            @SuppressWarnings("unchecked")
            ActivityResultLauncher<Intent> launcher =
                    (ActivityResultLauncher<Intent>) get(activity, "mEditIssueLauncher");
            launcher.launch(new Intent(appContext(), IssueEditActivity.class));
            ActivityResultRegistry registry = activity.getActivityResultRegistry();
            @SuppressWarnings("unchecked")
            Map<String, Integer> keyToRc = (Map<String, Integer>) get(registry, "keyToRc");
            registry.dispatchResult(keyToRc.get("activity_rq#1"), Activity.RESULT_OK, null);
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            assertEquals(42, ((PullRequest) get(activity, "mPullRequest")).number().intValue());
        }
    }

    @Test
    public void reviewLauncher_resultOk_reloadsEventsAndPendingReview() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            PullRequestConversationFragment conv = mock(PullRequestConversationFragment.class);
            set(activity, "mConversationFragment", conv);
            set(activity, "mPendingReviewLoaded", false);
            @SuppressWarnings("unchecked")
            ActivityResultLauncher<Intent> launcher =
                    (ActivityResultLauncher<Intent>) get(activity, "mCreateReviewLauncher");
            launcher.launch(new Intent(appContext(), CreateReviewActivity.class));
            ActivityResultRegistry registry = activity.getActivityResultRegistry();
            @SuppressWarnings("unchecked")
            Map<String, Integer> keyToRc = (Map<String, Integer>) get(registry, "keyToRc");
            registry.dispatchResult(keyToRc.get("activity_rq#2"), Activity.RESULT_OK, null);
            verify(conv).reloadEvents(false);
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mPendingReviewLoaded")), 10000);
            assertNull(get(activity, "mPendingReview"));
        }
    }

    @Test
    public void reviewLauncher_resultOk_withoutConversationFragment_loadsPendingReview()
            throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            set(activity, "mPendingReviewLoaded", false);
            @SuppressWarnings("unchecked")
            ActivityResultLauncher<Intent> launcher =
                    (ActivityResultLauncher<Intent>) get(activity, "mCreateReviewLauncher");
            launcher.launch(new Intent(appContext(), CreateReviewActivity.class));
            ActivityResultRegistry registry = activity.getActivityResultRegistry();
            @SuppressWarnings("unchecked")
            Map<String, Integer> keyToRc = (Map<String, Integer>) get(registry, "keyToRc");
            registry.dispatchResult(keyToRc.get("activity_rq#2"), Activity.RESULT_OK, null);
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mPendingReviewLoaded")), 10000);
        }
    }

    // ---------- load / loadPendingReview ----------

    @Test
    public void load_initialPageInRange_selectsPage() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42, 1, null));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            assertEquals(-1, ((Integer) get(activity, "mInitialPage")).intValue());
            androidx.viewpager.widget.ViewPager pager =
                    (androidx.viewpager.widget.ViewPager) call(activity, "getPager",
                            new Class<?>[]{}, new Object[]{});
            assertEquals(1, pager.getCurrentItem());
        }
    }

    @Test
    public void load_initialPageOutOfRange_ignored() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42, 5, null));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            assertEquals(5, ((Integer) get(activity, "mInitialPage")).intValue());
        }
    }

    @Test
    public void loadPendingReview_pendingReviewBySelf_found() throws Exception {
        launchPlain();
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        List<Review> reviews = new ArrayList<>();
        reviews.add(review(ReviewState.Approved, "alice"));
        reviews.add(review(ReviewState.Pending, "bob"));
        reviews.add(review(ReviewState.Pending, "alice"));
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false, reviews)) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mPendingReviewLoaded")), 10000);
            // The predicate reads the auth login at fetch time; if authorization
            // was lost across the launch, re-authorize and re-run the fetch.
            ensureAuthorizedAs("alice");
            set(activity, "mPendingReviewLoaded", false);
            set(activity, "mPendingReview", null);
            call(activity, "loadPendingReview", new Class<?>[]{boolean.class},
                    new Object[]{true});
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mPendingReviewLoaded")), 10000);
            Review found = (Review) get(activity, "mPendingReview");
            assertNotNull(found);
            assertEquals(ReviewState.Pending, found.state());
            assertEquals("alice", found.user().login());
        }
    }

    @Test
    public void loadPendingReview_noMatchingReview_leavesPendingReviewNull() throws Exception {
        List<Review> reviews = new ArrayList<>();
        reviews.add(review(ReviewState.Pending, "bob"));
        reviews.add(review(ReviewState.Approved, "alice"));
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false, reviews)) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mPendingReviewLoaded")), 10000);
            assertNull(get(activity, "mPendingReview"));
        }
    }

    @Test
    public void loadPendingReview_serviceError_showsErrorView() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            when(mocks.reviewService.getReviews(eq("o"), eq("r"), eq(42L), anyLong()))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 10000);
            assertFalse(Boolean.TRUE.equals(get(activity, "mPendingReviewLoaded")));
        }
    }

    // ---------- onConfirmed / updatePullRequestState / handlePullRequestUpdate ----------

    @Test
    public void onConfirmed_reopen_updatesPrToOpen() throws Exception {
        PullRequest openPr = pr(false, IssueState.Open, false);
        try (Mocks mocks = new Mocks(pr(false, IssueState.Closed, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            when(mocks.prService.editPullRequest(eq("o"), eq("r"), eq(42L),
                    any(EditPullRequest.class)))
                    .thenReturn(Single.just(Response.success(openPr)));
            Bundle data = new Bundle();
            data.putBoolean("reopen", true);
            activity.onConfirmed("opencloseconfirm", data);
            waitFor(() -> ((PullRequest) get(activity, "mPullRequest")).state()
                    == IssueState.Open, 10000);
            ArgumentCaptor<EditPullRequest> captor = ArgumentCaptor.forClass(EditPullRequest.class);
            verify(mocks.prService).editPullRequest(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals(ApiHelpers.IssueState.OPEN, captor.getValue().state());
        }
    }

    @Test
    public void onConfirmed_close_updatesPrToClosed() throws Exception {
        PullRequest closedPr = pr(false, IssueState.Closed, false);
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            when(mocks.prService.editPullRequest(eq("o"), eq("r"), eq(42L),
                    any(EditPullRequest.class)))
                    .thenReturn(Single.just(Response.success(closedPr)));
            Bundle data = new Bundle();
            data.putBoolean("reopen", false);
            activity.onConfirmed("opencloseconfirm", data);
            waitFor(() -> ((PullRequest) get(activity, "mPullRequest")).state()
                    == IssueState.Closed, 10000);
            ArgumentCaptor<EditPullRequest> captor = ArgumentCaptor.forClass(EditPullRequest.class);
            verify(mocks.prService).editPullRequest(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals(ApiHelpers.IssueState.CLOSED, captor.getValue().state());
        }
    }

    @Test
    public void updatePullRequestState_failure_keepsPrUnchanged() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            when(mocks.prService.editPullRequest(eq("o"), eq("r"), eq(42L),
                    any(EditPullRequest.class)))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            Bundle data = new Bundle();
            data.putBoolean("reopen", false);
            activity.onConfirmed("opencloseconfirm", data);
            waitForSnackbarWithText(activity,
                    appContext().getString(R.string.issue_error_close, 42));
            dismissCurrentSnackbarAsTimeout();
            assertEquals(IssueState.Open,
                    ((PullRequest) get(activity, "mPullRequest")).state());
        }
    }

    @Test
    public void handlePullRequestUpdate_closedPr_setsClosedByToCurrentAccount() throws Exception {
        launchPlain();
        authorizeAs("alice");
        ensureAuthorizedAs("alice");
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            // closedBy is read from the current account at update time.
            ensureAuthorizedAs("alice");
            PullRequestConversationFragment conv = mock(PullRequestConversationFragment.class);
            set(activity, "mConversationFragment", conv);
            PullRequest closedPr = pr(false, IssueState.Closed, false);
            set(activity, "mPullRequest", closedPr);
            call(activity, "handlePullRequestUpdate", new Class<?>[]{}, new Object[]{});
            verify(conv).updateState(closedPr);
            Issue updated = (Issue) get(activity, "mIssue");
            assertEquals(IssueState.Closed, updated.state());
            assertEquals("alice", updated.closedBy().login());
            assertEquals("CLOSED",
                    ((TextView) activity.findViewById(R.id.tv_state)).getText().toString());
        }
    }

    // ---------- mergePullRequest / MergeDialogFragment ----------

    @Test
    public void mergePullRequest_successMerged_updatesPrToMergedClosed() throws Exception {
        MergeResponse mergedResponse = mock(MergeResponse.class);
        when(mergedResponse.merged()).thenReturn(true);
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            when(mocks.prService.mergePullRequest(eq("o"), eq("r"), eq(42L),
                    any(MergeRequest.class)))
                    .thenReturn(Single.just(Response.success(mergedResponse)));
            call(activity, "mergePullRequest",
                    new Class<?>[]{String.class, String.class, MergeRequest.Method.class},
                    new Object[]{"Merge it", "Details", MergeRequest.Method.Merge});
            waitFor(() -> Boolean.TRUE.equals(
                    ((PullRequest) get(activity, "mPullRequest")).merged()), 10000);
            assertEquals(IssueState.Closed,
                    ((PullRequest) get(activity, "mPullRequest")).state());
            ArgumentCaptor<MergeRequest> captor = ArgumentCaptor.forClass(MergeRequest.class);
            verify(mocks.prService).mergePullRequest(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals("Merge it", captor.getValue().commitTitle());
            assertEquals("Details", captor.getValue().commitMessage());
            assertEquals(MergeRequest.Method.Merge, captor.getValue().method());
        }
    }

    @Test
    public void mergePullRequest_successNotMerged_keepsPrOpen() throws Exception {
        MergeResponse response = mock(MergeResponse.class);
        when(response.merged()).thenReturn(false);
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            PullRequestConversationFragment conv = mock(PullRequestConversationFragment.class);
            set(activity, "mConversationFragment", conv);
            when(mocks.prService.mergePullRequest(eq("o"), eq("r"), eq(42L),
                    any(MergeRequest.class)))
                    .thenReturn(Single.just(Response.success(response)));
            call(activity, "mergePullRequest",
                    new Class<?>[]{String.class, String.class, MergeRequest.Method.class},
                    new Object[]{"Merge it", "Details", MergeRequest.Method.Squash});
            // handlePullRequestUpdate ran (conversation fragment updated) but the
            // PR was not rebuilt: still open and not merged. Note: verify(timeout)
            // does not pump the main looper, so the main-thread success callback
            // would never run under it; poll with waitFor instead.
            waitFor(() -> {
                try {
                    verify(conv).updateState(any(PullRequest.class));
                    return true;
                } catch (AssertionError e) {
                    return false;
                }
            }, 10000);
            assertEquals(IssueState.Open,
                    ((PullRequest) get(activity, "mPullRequest")).state());
            assertEquals(Boolean.FALSE, ((PullRequest) get(activity, "mPullRequest")).merged());
        }
    }

    @Test
    public void mergePullRequest_failure_keepsPrUnchanged() throws Exception {
        try (Mocks mocks = new Mocks(pr(false, IssueState.Open, false), issue(), false,
                new ArrayList<>())) {
            PullRequestActivity activity = launch(PullRequestActivity.class,
                    PullRequestActivity.makeIntent(appContext(), "o", "r", 42));
            waitFor(() -> get(activity, "mPullRequest") != null, 10000);
            when(mocks.prService.mergePullRequest(eq("o"), eq("r"), eq(42L),
                    any(MergeRequest.class)))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            call(activity, "mergePullRequest",
                    new Class<?>[]{String.class, String.class, MergeRequest.Method.class},
                    new Object[]{"Merge it", "Details", MergeRequest.Method.Merge});
            waitForSnackbarWithText(activity,
                    appContext().getString(R.string.pull_error_merge, 42));
            dismissCurrentSnackbarAsTimeout();
            assertEquals(IssueState.Open,
                    ((PullRequest) get(activity, "mPullRequest")).state());
        }
    }

    @Test
    public void mergeDialog_positiveButton_callsMergeWithDialogValues() throws Exception {
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        PullRequestActivity.MergeDialogFragment f =
                PullRequestActivity.MergeDialogFragment.newInstance(pr(false, IssueState.Open, false));
        activity.getSupportFragmentManager().beginTransaction().add(f, "merge").commitNow();
        Dialog dialog = f.onCreateDialog(null);
        dialog.show();
        TextInputLayout titleField = (TextInputLayout) get(f, "mTitleField");
        titleField.getEditText().setText("Merge it");
        TextInputLayout detailsField = (TextInputLayout) get(f, "mDetailsField");
        detailsField.getEditText().setText("Details");
        Spinner spinner = (Spinner) get(f, "mMergeMethodSelector");
        spinner.setSelection(1); // Squash
        MergeResponse mergedResponse = mock(MergeResponse.class);
        when(mergedResponse.merged()).thenReturn(true);
        try (MockedStatic<ServiceFactory> mockedSvc = Mockito.mockStatic(ServiceFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS))) {
            PullRequestService svc = mock(PullRequestService.class);
            mockedSvc.when(() -> ServiceFactory.get(PullRequestService.class, false))
                    .thenReturn(svc);
            when(svc.mergePullRequest(eq("o"), eq("r"), eq(42L), any(MergeRequest.class)))
                    .thenReturn(Single.just(Response.success(mergedResponse)));
            ((androidx.appcompat.app.AlertDialog) dialog)
                    .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            idleMain(200);
            waitFor(() -> Boolean.TRUE.equals(
                    ((PullRequest) get(activity, "mPullRequest")).merged()), 10000);
            ArgumentCaptor<MergeRequest> captor = ArgumentCaptor.forClass(MergeRequest.class);
            verify(svc, timeout(5000)).mergePullRequest(eq("o"), eq("r"), eq(42L), captor.capture());
            assertEquals("Merge it", captor.getValue().commitTitle());
            assertEquals("Details", captor.getValue().commitMessage());
            assertEquals(MergeRequest.Method.Squash, captor.getValue().method());
        }
    }

    @Test
    public void mergeDialog_spinnerRebase_hidesTitleAndDetails() throws Exception {
        PullRequestActivity activity = launchPlain();
        PullRequestActivity.MergeDialogFragment f =
                PullRequestActivity.MergeDialogFragment.newInstance(pr(false, IssueState.Open, false));
        activity.getSupportFragmentManager().beginTransaction().add(f, "merge").commitNow();
        Dialog dialog = f.onCreateDialog(null);
        dialog.show();
        TextInputLayout titleField = (TextInputLayout) get(f, "mTitleField");
        TextInputLayout detailsField = (TextInputLayout) get(f, "mDetailsField");
        Spinner spinner = (Spinner) get(f, "mMergeMethodSelector");
        assertEquals(View.VISIBLE, titleField.getVisibility());
        spinner.setSelection(2); // Rebase
        idleMain(100);
        assertEquals(View.GONE, titleField.getVisibility());
        assertEquals(View.GONE, detailsField.getVisibility());
        spinner.setSelection(0); // Merge
        idleMain(100);
        assertEquals(View.VISIBLE, titleField.getVisibility());
        assertEquals(View.VISIBLE, detailsField.getVisibility());
        spinner.setSelection(1); // Squash
        idleMain(100);
        assertEquals(View.VISIBLE, titleField.getVisibility());
        // onNothingSelected is an intentional no-op.
        spinner.getOnItemSelectedListener().onNothingSelected(spinner);
    }


    // ---------- remaining branch arms ----------

    @Test
    public void launch_landscape_headerNotScrollable() throws Exception {
        // hasTabsInToolbar() is true only in landscape, so onCreate calls
        // addHeaderView(mHeader, false) instead of addHeaderView(mHeader, true).
        String originalQualifiers = RuntimeEnvironment.getQualifiers();
        RuntimeEnvironment.setQualifiers("+land");
        try {
            PullRequestActivity activity = launchPlain();
            ViewGroup header = (ViewGroup) get(activity, "mHeader");
            AppBarLayout.LayoutParams lp =
                    (AppBarLayout.LayoutParams) header.getLayoutParams();
            assertEquals(0, lp.getScrollFlags());
        } finally {
            RuntimeEnvironment.setQualifiers(originalQualifiers);
        }
    }

    @Test
    public void menu_noPrButPendingReviewLoaded_removesReviewItem() throws Exception {
        // The pull_review removal chain needs the mPullRequest == null arm with
        // !mPendingReviewLoaded false; menu_noPr short-circuits on the first arm.
        PullRequestActivity activity = launchPlain();
        MenuBuilder menu = menuFor(activity, null, null, null, true);
        assertNull(menu.findItem(R.id.pull_review));
    }

    @Test
    public void onClick_gravatarMannequin_doesNotStartActivity() throws Exception {
        // UserActivity.makeIntent returns null for Mannequin users, so the
        // intent != null guard in onClick takes its false arm.
        PullRequestActivity activity = launchPlain();
        View gravatar = new View(appContext());
        gravatar.setId(R.id.iv_gravatar);
        gravatar.setTag(User.builder().login("ghost").type(UserType.Mannequin).build());
        activity.onClick(gravatar);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void fab_issueNullButCollaborator_noFab() throws Exception {
        // shouldHaveFab's && chain needs the mIssue != null arm with
        // (isIssueOwner || isCollaborator) true and mPullRequest non-null.
        PullRequestActivity activity = launchPlain();
        set(activity, "mPullRequest", pr(false, IssueState.Open, false));
        set(activity, "mIssue", null);
        set(activity, "mIsCollaborator", true);
        call(activity, "updateFabVisibility", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mEditFab"));
    }

}
