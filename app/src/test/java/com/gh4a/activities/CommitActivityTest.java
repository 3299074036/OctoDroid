package com.gh4a.activities;

import android.content.Intent;
import android.net.Uri;

import com.gh4a.R;
import com.gh4a.utils.IntentUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.callStatic;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Commit;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommitService;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.reactivex.Single;
import retrofit2.Response;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class CommitActivityTest {
    @Test
    public void makeIntent_shaOnly_defaultsPrMinusOne() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234");
        assertEquals(CommitActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("o", intent.getStringExtra("owner"));
        assertEquals("r", intent.getStringExtra("repo"));
        assertEquals("abcdef1234", intent.getStringExtra("sha"));
        assertEquals(-1, intent.getIntExtra("pr", -99));
        assertNull(intent.getParcelableExtra("initial_comment"));
    }

    @Test
    public void makeIntent_withPrNumber() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", 42, "abcdef1234");
        assertEquals(42, intent.getIntExtra("pr", -1));
    }

    @Test
    public void makeIntent_withInitialComment_roundTrips() {
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(123L);
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234", marker);
        IntentUtils.InitialCommentMarker back =
                intent.getParcelableExtra("initial_comment");
        assertEquals(123L, back.commentId);
    }

    @Test
    public void shortSha_nullShortLong() {
        assertEquals("", callStatic(CommitActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals("abc", callStatic(CommitActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{"abc"}));
        assertEquals("abcdefg", callStatic(CommitActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{"abcdefg"}));
        assertEquals("abcdefg", callStatic(CommitActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{"abcdefghij"}));
        assertEquals("", callStatic(CommitActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{""}));
    }

    @Test
    public void launch_titleUsesShortSha() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertEquals(appContext().getString(R.string.commit_title, "abcdef1"),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void launch_missingSha_finishes() {
        // No browser is installed in the Robolectric environment, so
        // launchBrowser falls through to the "no browser" toast.
        Intent intent = new Intent(appContext(), CommitActivity.class)
                .putExtra("owner", "o").putExtra("repo", "r");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertTrue(activity.isFinishing());
        assertEquals(appContext().getString(R.string.no_browser_found),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void launch_missingEverything_finishesWithoutBrowser() {
        CommitActivity activity = launch(CommitActivity.class);
        assertTrue(activity.isFinishing());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void getTabTitleResIds_nullBeforeLoad() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertNull(activity.getTabTitleResIds());
    }

    @Test
    public void navigateUp_withPr_goesToPullRequest() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", 42, "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(PullRequestActivity.class.getName(), up.getComponent().getClassName());
        assertEquals(42, up.getIntExtra("number", -1));
    }

    @Test
    public void navigateUp_withoutPr_goesToRepository() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("o", up.getStringExtra("owner"));
    }

    @Test
    public void getActivityUri_withPr() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", 42, "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        Uri uri = activity.getActivityUri();
        assertEquals("https://github.com/o/r/pull/42/commit/abcdef1234", uri.toString());
    }

    @Test
    public void getActivityUri_withoutSha_fallsBackToCommits() {
        Intent intent = new Intent(appContext(), CommitActivity.class)
                .putExtra("owner", "o").putExtra("repo", "r").putExtra("pr", -1);
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertTrue(activity.isFinishing());
        assertEquals("https://github.com/o/r/commits", activity.getActivityUri().toString());
    }

    @Test
    public void getActivityUri_withoutOwner_returnsNull() {
        CommitActivity activity = launch(CommitActivity.class);
        assertNull(activity.getActivityUri());
    }

    @Test
    public void onOptionsItemSelected_withoutSha_delegatesToSuper() {
        CommitActivity activity = launch(CommitActivity.class);
        android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(appContext());
        android.view.MenuItem item = menu.add(0, R.id.browser, 0, "browser");
        assertFalse(activity.onOptionsItemSelected(item));
    }

    @Test
    public void fragmentNeedsRefresh_alwaysTrue() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertTrue(activity.fragmentNeedsRefresh(null));
    }

    @Test
    public void displayDetachAction_true() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertTrue(activity.displayDetachAction());
    }

    private RepositoryCommentService commentServiceWith(List<GitComment> page) {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.getCommitComments(eq("o"), eq("r"), eq("abcdef1234"), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<GitComment>builder().items(page).build())));
        return svc;
    }

    private void mockLoadServices(MockedStatic<ServiceFactory> mocked,
            RepositoryCommitService commitSvc, RepositoryCommentService commentSvc) {
        mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, false))
                .thenReturn(commitSvc);
        mocked.when(() -> ServiceFactory.get(RepositoryCommitService.class, true))
                .thenReturn(commitSvc);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(RepositoryCommentService.class, false))
                .thenReturn(commentSvc);
        mocked.when(() -> ServiceFactory.getForFullPagedLists(RepositoryCommentService.class, true))
                .thenReturn(commentSvc);
    }

    private RepositoryCommitService commitServiceWith(Commit commit) {
        RepositoryCommitService svc = mock(RepositoryCommitService.class);
        when(svc.getCommit("o", "r", "abcdef1234"))
                .thenReturn(Single.just(Response.success(commit)));
        return svc;
    }

    @Test
    public void getTabTitleResIds_withCommitAndComments_returnsTitles() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mCommit", mock(Commit.class));
        set(activity, "mComments", new java.util.ArrayList<>());
        assertNotNull(activity.getTabTitleResIds());
    }

    @Test
    public void onRefresh_mockedServices_reloadsCommitAndComments() throws Exception {
        RepositoryCommitService commitSvc = commitServiceWith(mock(Commit.class));
        List<GitComment> page = new java.util.ArrayList<>();
        page.add(GitComment.builder().id(1L).body("a").position(1).build());
        RepositoryCommentService commentSvc = commentServiceWith(page);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mockLoadServices(mocked, commitSvc, commentSvc);
            CommitActivity activity = launch(CommitActivity.class,
                    CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> get(activity, "mCommit") != null
                            && get(activity, "mComments") != null, 10000);
            set(activity, "mCommit", null);
            set(activity, "mComments", null);
            activity.onRefresh();
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> get(activity, "mCommit") != null
                            && get(activity, "mComments") != null, 10000);
            assertNotNull(activity.getTabTitleResIds());
        }
    }

    @Test
    public void loadComments_emptyPage_clearsInitialComment() throws Exception {
        RepositoryCommitService commitSvc = commitServiceWith(mock(Commit.class));
        RepositoryCommentService commentSvc = commentServiceWith(new java.util.ArrayList<>());
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mockLoadServices(mocked, commitSvc, commentSvc);
            IntentUtils.InitialCommentMarker marker =
                    new IntentUtils.InitialCommentMarker(5L);
            CommitActivity activity = launch(CommitActivity.class,
                    CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234", marker));
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> get(activity, "mComments") != null, 10000);
            assertNull(get(activity, "mInitialComment"));
        }
    }

    @Test
    public void makeFragment_positionZero_returnsCommitFragment() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        assertTrue(activity.makeFragment(0)
                instanceof com.gh4a.fragment.CommitFragment);
    }

    @Test
    public void makeFragment_positionOne_returnsCommentsFragment() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        // newInstance streams over the comments and reads the commit
        // author; the pager only builds this tab after a successful load.
        set(activity, "mCommit", mock(Commit.class));
        set(activity, "mComments", new java.util.ArrayList<>());
        assertTrue(activity.makeFragment(1)
                instanceof com.gh4a.fragment.CommitCommentsFragment);
    }

    private android.view.MenuItem commitMenuItem(CommitActivity activity, int id) {
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        return menu.findItem(id);
    }

    @Test
    public void onOptionsItemSelected_browser_returnsTrue() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        assertTrue(activity.onOptionsItemSelected(commitMenuItem(activity, R.id.browser)));
    }

    @Test
    public void onOptionsItemSelected_share_returnsTrue() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        assertTrue(activity.onOptionsItemSelected(commitMenuItem(activity, R.id.share)));
    }

    @Test
    public void onOptionsItemSelected_copySha_copiesSha() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        assertTrue(activity.onOptionsItemSelected(commitMenuItem(activity, R.id.copy_sha)));
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                appContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        assertEquals("abcdef1234", cm.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test
    public void onOptionsItemSelected_nullSha_delegatesToSuper() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mObjectSha", null);
        assertFalse(activity.onOptionsItemSelected(commitMenuItem(activity, R.id.browser)));
    }

    @Test
    public void onCommentsUpdated_mockedService_reloadsComments() throws Exception {
        RepositoryCommitService commitSvc = commitServiceWith(mock(Commit.class));
        List<GitComment> page = new java.util.ArrayList<>();
        page.add(GitComment.builder().id(1L).body("a").position(1).build());
        RepositoryCommentService commentSvc = commentServiceWith(page);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mockLoadServices(mocked, commitSvc, commentSvc);
            CommitActivity activity = launch(CommitActivity.class,
                    CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> get(activity, "mComments") != null, 10000);
            set(activity, "mComments", null);
            activity.onCommentsUpdated();
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> get(activity, "mComments") != null, 10000);
            assertEquals(1, ((List<?>) get(activity, "mComments")).size());
        }
    }

    @Test
    public void showContentIfReady_withInitialComment_selectsCommentsTab() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mCommit", mock(Commit.class));
        set(activity, "mComments", new java.util.ArrayList<>());
        set(activity, "mInitialComment", new IntentUtils.InitialCommentMarker(5L));
        call(activity, "showContentIfReady", new Class<?>[]{}, new Object[]{});
        androidx.viewpager.widget.ViewPager pager =
                (androidx.viewpager.widget.ViewPager) call(activity, "getPager",
                        new Class<?>[]{}, new Object[]{});
        assertEquals(1, pager.getCurrentItem());
    }

    @Test
    public void launch_missingOwner_finishes() {
        Intent intent = CommitActivity.makeIntent(appContext(), null, "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void launch_missingRepo_finishes() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", null, "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onOptionsItemSelected_missingOwner_delegatesToSuper() {
        Intent intent = CommitActivity.makeIntent(appContext(), null, "r", "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
    }

    @Test
    public void onOptionsItemSelected_missingRepo_delegatesToSuper() {
        Intent intent = CommitActivity.makeIntent(appContext(), "o", null, "abcdef1234");
        CommitActivity activity = launch(CommitActivity.class, intent);
        android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
    }

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesToSuper() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        android.view.Menu menu = new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 123456, 0, "unknown")));
    }

    @Test
    public void getTabTitleResIds_commitOnly_returnsNull() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mCommit", mock(Commit.class));
        assertNull(activity.getTabTitleResIds());
    }

    @Test
    public void getTabTitleResIds_commentsOnly_returnsNull() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mComments", new java.util.ArrayList<>());
        assertNull(activity.getTabTitleResIds());
    }

    @Test
    public void showContentIfReady_commitOnly_doesNotShowContent() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mCommit", mock(Commit.class));
        call(activity, "showContentIfReady", new Class<?>[]{}, new Object[]{});
        assertEquals(Boolean.FALSE, get(activity, "mContentShown"));
    }

    @Test
    public void getActivityUri_ownerOnly_returnsNull() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mRepoOwner", "o");
        set(activity, "mRepoName", null);
        assertNull(activity.getActivityUri());
    }

    @Test
    public void showContentIfReady_noCommit_doesNotShowContent() {
        CommitActivity activity = launch(CommitActivity.class,
                CommitActivity.makeIntent(appContext(), "o", "r", "abcdef1234"));
        set(activity, "mComments", new java.util.ArrayList<>());
        call(activity, "showContentIfReady", new Class<?>[]{}, new Object[]{});
        assertEquals(Boolean.FALSE, get(activity, "mContentShown"));
    }
}
