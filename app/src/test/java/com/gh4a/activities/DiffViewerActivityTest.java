package com.gh4a.activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.view.menu.MenuBuilder;

import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.IntentUtils;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Reaction;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.ReviewComment;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.git.GitComment;
import com.meisolsson.githubsdk.service.reactions.ReactionService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCommentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.reactivex.Single;
import retrofit2.Response;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.callStatic;
import static com.gh4a.activities.ActivityTestHelper.controller;
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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DiffViewerActivity is abstract; static/inner-class behaviour is tested
 * directly, instance behaviour through CommitDiffViewerActivity.
 */
@RunWith(RobolectricTestRunner.class)
public class DiffViewerActivityTest {
    @Test
    public void fillInIntent_setsAllExtras() {
        Intent intent = DiffViewerActivity.fillInIntent(
                new Intent(appContext(), CommitDiffViewerActivity.class),
                "o", "r", "abc1234", "a.txt", "@@ diff", null, 3, 5, 9, true, null);
        assertEquals("o", intent.getStringExtra("owner"));
        assertEquals("r", intent.getStringExtra("repo"));
        assertEquals("abc1234", intent.getStringExtra("sha"));
        assertEquals("a.txt", intent.getStringExtra("path"));
        assertEquals("@@ diff", intent.getStringExtra("diff"));
        assertEquals(3, intent.getIntExtra("initial_line", -1));
        assertEquals(5, intent.getIntExtra("highlight_start", -1));
        assertEquals(9, intent.getIntExtra("highlight_end", -1));
        assertTrue(intent.getBooleanExtra("highlight_right", false));
        assertFalse(intent.hasExtra("comments"));
    }

    @Test
    public void fillInIntent_withComments_storesCompressedExtra() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(GitComment.builder().id(1L).body("hi").path("a.txt").position(1).build());
        Intent intent = DiffViewerActivity.fillInIntent(
                new Intent(appContext(), CommitDiffViewerActivity.class),
                "o", "r", "abc1234", "a.txt", "@@ diff", comments, -1, -1, -1, false, null);
        assertTrue(intent.hasExtra("comments"));
        List<GitComment> back = IntentUtils.getCompressedExtra(intent, "comments");
        assertEquals(1, back.size());
        assertEquals(Long.valueOf(1L), back.get(0).id());
        assertEquals("hi", back.get(0).body());
    }

    @Test
    public void fillInIntent_withInitialComment_roundTrips() {
        IntentUtils.InitialCommentMarker marker = new IntentUtils.InitialCommentMarker(77L);
        Intent intent = DiffViewerActivity.fillInIntent(
                new Intent(appContext(), CommitDiffViewerActivity.class),
                "o", "r", "abc1234", "a.txt", "@@ diff", null, -1, -1, -1, false, marker);
        assertEquals(77L, ((IntentUtils.InitialCommentMarker)
                intent.getParcelableExtra("initial_comment")).commentId);
    }

    @Test
    public void shortSha_nullShortLong() {
        assertEquals("", callStatic(DiffViewerActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals("abc", callStatic(DiffViewerActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{"abc"}));
        assertEquals("abcdefg", callStatic(DiffViewerActivity.class, "shortSha",
                new Class<?>[]{String.class}, new Object[]{"abcdefghi"}));
    }

    @Test
    public void commentWrapper_getCacheKey_returnsCommentId() {
        GitComment c = GitComment.builder().id(55L).body("b").build();
        DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(c);
        assertEquals(55L, w.getCacheKey());
        assertEquals(c, w.comment);
    }

    @Test
    public void commentWrapper_getCacheKey_nullIdReturnsNull() {
        GitComment c = GitComment.builder().body("b").build();
        DiffViewerActivity.CommentWrapper w = new DiffViewerActivity.CommentWrapper(c);
        assertNull(w.getCacheKey());
    }

    @Test
    public void createLineLinkId_rightAndLeft() {
        Intent intent = CommitDiffViewerActivity.makeIntent(appContext(), "o", "r",
                "abc1234", "a.txt", "@@ -1,1 +1,1 @@\n-x\n+y", null, -1, -1, false, null);
        CommitDiffViewerActivity activity =
                com.gh4a.activities.ActivityTestHelper.launch(CommitDiffViewerActivity.class, intent);
        assertEquals("R7", com.gh4a.activities.ActivityTestHelper.call(activity, "createLineLinkId",
                new Class<?>[]{int.class, boolean.class}, new Object[]{7, true}));
        assertEquals("L3", com.gh4a.activities.ActivityTestHelper.call(activity, "createLineLinkId",
                new Class<?>[]{int.class, boolean.class}, new Object[]{3, false}));
        assertEquals("a.txt", get(activity, "mPath"));
    }

    // ---------- helpers ----------

    private static final String DIFF =
            "@@ -1,3 +1,4 @@\n ctx1\n-old\n+new\n ctx2";

    private GitComment comment(long id, String userLogin, Integer position) {
        return comment(id, userLogin, position, "a.txt");
    }

    private GitComment comment(long id, String userLogin, Integer position, String path) {
        return GitComment.builder().id(id).body("body" + id).bodyHtml("<p>body" + id + "</p>")
                .path(path).position(position)
                .user(userLogin == null ? null
                        : User.builder().login(userLogin).id(1L).build())
                .createdAt(new Date(1700000000000L)).build();
    }

    private GitComment commentWithReactions(long id, Reactions reactions) {
        return GitComment.builder().id(id).body("b").bodyHtml("b").path("a.txt").position(2)
                .user(User.builder().login("alice").id(1L).build())
                .createdAt(new Date(1700000000000L)).reactions(reactions).build();
    }

    private static Reactions reactions(int plusOne) {
        return Reactions.builder().plusOne(plusOne).minusOne(0).laugh(0).hooray(0)
                .confused(0).heart(0).rocket(0).eyes(0).build();
    }

    private Intent commitIntent(List<GitComment> comments) {
        return CommitDiffViewerActivity.makeIntent(appContext(), "o", "r", "abc1234", "a.txt",
                DIFF, comments, -1, -1, false, null);
    }

    private Intent commitIntent(List<GitComment> comments, int highlightStart, int highlightEnd,
            boolean highlightIsRight, IntentUtils.InitialCommentMarker marker) {
        return CommitDiffViewerActivity.makeIntent(appContext(), "o", "r", "abc1234", "a.txt",
                DIFF, comments, highlightStart, highlightEnd, highlightIsRight, marker);
    }

    /** Launch with intent comments (so no network load runs) and force the
     * diff html build, which populates mWrappedComments. */
    private CommitDiffViewerActivity launchCommit(List<GitComment> comments) {
        CommitDiffViewerActivity activity = launch(CommitDiffViewerActivity.class,
                commitIntent(comments));
        activity.generateHtml("light", false);
        return activity;
    }

    private void authorizeAs(String login) {
        Gh4Application app = Gh4Application.get();
        for (int i = 0; i < 3 && (!app.isAuthorized() || !login.equals(app.getAuthLogin())); i++) {
            app.addAccount(User.builder().login(login).id(1L).build(), "tok");
            idleMain(100);
        }
    }

    private ReviewComment reviewComment(long id, String userLogin, int position) {
        // The SDK's own Moshi instance (it registers the @FormattedHtml adapter
        // a plain Builder lacks).
        com.squareup.moshi.Moshi moshi =
                com.meisolsson.githubsdk.core.ServiceGenerator.moshi;
        String json = "{\"id\":" + id + ",\"body\":\"b\",\"body_html\":\"b\","
                + "\"path\":\"a.txt\",\"position\":" + position
                + ",\"user\":{\"login\":\"" + userLogin + "\",\"id\":1},"
                + "\"created_at\":\"2023-11-14T22:13:20Z\"}";
        try {
            return ReviewComment.jsonAdapter(moshi).fromJson(json);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private MockedStatic<ServiceFactory> mockServices(
            RepositoryCommentService commentSvc, ReactionService reactionSvc) {
        MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        if (commentSvc != null) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(RepositoryCommentService.class), anyBoolean())).thenReturn(commentSvc);
            mocked.when(() -> ServiceFactory.get(
                    eq(RepositoryCommentService.class), anyBoolean())).thenReturn(commentSvc);
        }
        if (reactionSvc != null) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                    eq(ReactionService.class), anyBoolean())).thenReturn(reactionSvc);
            mocked.when(() -> ServiceFactory.get(
                    eq(ReactionService.class), anyBoolean())).thenReturn(reactionSvc);
        }
        return mocked;
    }

    private MockedStatic<ServiceFactory> mockCommentService(RepositoryCommentService svc) {
        return mockServices(svc, null);
    }

    private RepositoryCommentService stubEmptyCommentService() {
        RepositoryCommentService svc = mock(RepositoryCommentService.class);
        when(svc.getCommitComments(eq("o"), eq("r"), eq("abc1234"), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<GitComment>builder().items(new ArrayList<>()).build())));
        return svc;
    }

    private MockedStatic<ServiceFactory> mockReactionService(ReactionService svc) {
        return mockServices(null, svc);
    }

    private ReactionService stubEmptyReactionService() {
        ReactionService svc = mock(ReactionService.class);
        when(svc.getCommitCommentReactions(eq("o"), eq("r"), anyLong(), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Reaction>builder().items(new ArrayList<>()).build())));
        return svc;
    }

    /** Reflectively construct the private CommentActionPopup inner class. */
    private Object newPopup(DiffViewerActivity activity, long id, int position,
            String lineText, int left, int right, int x, int y, boolean isRight) throws Exception {
        Class<?> popupClass =
                Class.forName("com.gh4a.activities.DiffViewerActivity$CommentActionPopup");
        java.lang.reflect.Constructor<?> ctor = popupClass.getDeclaredConstructor(
                DiffViewerActivity.class, long.class, int.class, String.class,
                int.class, int.class, int.class, int.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(activity, id, position, lineText, left, right, x, y, isRight);
    }

    private Menu popupMenu(Object popup) {
        return ((androidx.appcompat.widget.PopupMenu) popup).getMenu();
    }

    private boolean popupClick(Object popup, MenuItem item) {
        return ((androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener) popup)
                .onMenuItemClick(item);
    }

    /** Dispatch a result to whichever launcher was launched last
     * (ActivityResultRegistry only dispatches to keys in launchedKeys). */
    private void dispatchLastLaunchedResult(Activity activity, int resultCode, Intent data)
            throws Exception {
        androidx.activity.result.ActivityResultRegistry registry =
                ((androidx.activity.ComponentActivity) activity).getActivityResultRegistry();
        java.util.Collection<String> launchedKeys =
                (java.util.Collection<String>) get(registry, "launchedKeys");
        assertNotNull("no launcher was launched", launchedKeys);
        assertFalse("no launcher was launched", launchedKeys.isEmpty());
        String key = launchedKeys.iterator().next();
        java.util.Map<String, Integer> keyToRc =
                (java.util.Map<String, Integer>) get(registry, "keyToRc");
        assertTrue("launched key not in keyToRc: " + key, registry.dispatchResult(
                keyToRc.get(key), resultCode, data));
    }

    // ---------- lifecycle / refresh / menu ----------

    @Test
    public void onDestroy_destroysActivity() {
        org.robolectric.android.controller.ActivityController<CommitDiffViewerActivity> c =
                controller(CommitDiffViewerActivity.class,
                        commitIntent(new ArrayList<>()));
        CommitDiffViewerActivity activity = c.create().start().resume().get();
        c.destroy();
        assertTrue(activity.isDestroyed());
    }

    @Test
    public void canSwipeToRefresh_withIntentComments_false() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        assertFalse((Boolean) call(activity, "canSwipeToRefresh",
                new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void canSwipeToRefresh_withoutIntentComments_true() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        try (MockedStatic<ServiceFactory> mocked = mockCommentService(svc)) {
            CommitDiffViewerActivity activity =
                    launch(CommitDiffViewerActivity.class, commitIntent(null));
            assertTrue((Boolean) call(activity, "canSwipeToRefresh",
                    new Class<?>[]{}, new Object[]{}));
        }
    }

    @Test
    public void onRefresh_reloadsComments() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        try (MockedStatic<ServiceFactory> mocked = mockCommentService(svc)) {
            CommitDiffViewerActivity activity =
                    launch(CommitDiffViewerActivity.class, commitIntent(null));
            call(activity, "onRefresh", new Class<?>[]{}, new Object[]{});
            verify(svc, timeout(5000).times(2)).getCommitComments(
                    eq("o"), eq("r"), eq("abc1234"), anyLong());
        }
    }

    @Test
    public void onCreateOptionsMenu_addsViewItemAndRemovesDownload() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        MenuBuilder menu = new MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertNull("download must be removed", menu.findItem(com.gh4a.R.id.download));
        MenuItem view = null;
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).getTitle() != null
                    && menu.getItem(i).getTitle().toString().contains("abc1234")) {
                view = menu.getItem(i);
            }
        }
        assertNotNull("view-this-version item missing", view);
        assertEquals(10, view.getItemId());
    }

    // ---------- onReactionsUpdated / replaceOutdatedComment ----------

    @Test
    public void onReactionsUpdated_updatesCommentAndRegenerates() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        DiffViewerActivity.CommentWrapper wrapper =
                ((androidx.collection.LongSparseArray<DiffViewerActivity.CommentWrapper>)
                        get(activity, "mWrappedComments")).get(7L);
        activity.onReactionsUpdated(wrapper, reactions(3));
        assertEquals(3, (int) wrapper.comment.reactions().plusOne());
        @SuppressWarnings("unchecked")
        SparseArray<List<GitComment>> byPosition =
                (SparseArray<List<GitComment>>) get(activity, "mCommentsByPosition");
        assertEquals(3, (int) byPosition.get(2).get(0).reactions().plusOne());
        assertTrue(activity.generateHtml("light", false).contains(">3</span>"));
    }

    @Test
    public void replaceOutdatedComment_notFound_doesNothing() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        call(activity, "replaceOutdatedComment",
                new Class<?>[]{com.meisolsson.githubsdk.model.PositionalCommentBase.class},
                new Object[]{comment(8L, "alice", 2)});
        @SuppressWarnings("unchecked")
        SparseArray<List<GitComment>> byPosition =
                (SparseArray<List<GitComment>>) get(activity, "mCommentsByPosition");
        assertEquals(1, byPosition.get(2).size());
        assertNull(byPosition.get(3));
    }

    @Test
    public void replaceOutdatedComment_unknownPosition_throwsNpe_A1_7() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        try {
            call(activity, "replaceOutdatedComment",
                    new Class<?>[]{com.meisolsson.githubsdk.model.PositionalCommentBase.class},
                    new Object[]{comment(99L, "alice", 42)});
            fail("expected NullPointerException for unknown position");
        } catch (AssertionError expected) {
            // ActivityTestHelper.call wraps the real exception as the cause;
            // rethrow anything else (e.g. the fail() above) unchanged.
            if (!(expected.getCause() instanceof NullPointerException)) {
                throw expected;
            }
        }
    }

    // ---------- generateHtml ----------

    @Test
    public void generateHtml_withTitleHeader_rendersTitle() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        String html = activity.generateHtml("light", true);
        assertTrue(html.contains("<h2>"));
        assertTrue(html.contains("a.txt"));
        assertTrue(html.contains("abc1234"));
    }

    @Test
    public void generateHtml_nullDiff_rendersEmptyPre() {
        Intent intent = CommitDiffViewerActivity.makeIntent(appContext(), "o", "r", "abc1234",
                "a.txt", null, new ArrayList<>(), -1, -1, false, null);
        CommitDiffViewerActivity activity =
                launch(CommitDiffViewerActivity.class, intent);
        assertTrue(activity.generateHtml("light", false).contains("<pre></pre>"));
    }

    @Test
    public void generateHtml_malformedHunkHeader_ignoresLineNumbers() {
        Intent intent = CommitDiffViewerActivity.makeIntent(appContext(), "o", "r", "abc1234",
                "a.txt", "@@ garbage\n-x\n+y", null, -1, -1, false, null);
        CommitDiffViewerActivity activity =
                launch(CommitDiffViewerActivity.class, intent);
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("change"));
        assertTrue(html.contains("-x"));
        assertTrue(html.contains("+y"));
    }

    @Test
    public void generateHtml_secondHighlightMatch_ignored() {
        Intent intent = CommitDiffViewerActivity.makeIntent(appContext(), "o", "r", "abc1234",
                "a.txt", "@@ -1,1 +5,1 @@\n ctx\n-del\n ctx2",
                null, 5, 5, true, null);
        CommitDiffViewerActivity activity =
                launch(CommitDiffViewerActivity.class, intent);
        assertTrue(activity.generateHtml("light", false).contains("highlightDiffLines(1,1)"));
    }

    @Test
    public void generateHtml_authorized_addsCommentUris() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        authorizeAs("alice");
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("comment://add?position="));
        assertTrue(html.contains("comment://edit?position=2"));
    }

    @Test
    public void generateHtml_initialComment_matches_highlightsComment() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        Intent intent = commitIntent(comments, -1, -1, false,
                new IntentUtils.InitialCommentMarker(7L));
        CommitDiffViewerActivity activity = launch(CommitDiffViewerActivity.class, intent);
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("comment highlighted"));
        assertTrue(html.contains("scrollToElement(\"comment7\")"));
    }

    @Test
    public void generateHtml_initialComment_noMatch_noHighlight() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        Intent intent = commitIntent(comments, -1, -1, false,
                new IntentUtils.InitialCommentMarker(99L));
        CommitDiffViewerActivity activity = launch(CommitDiffViewerActivity.class, intent);
        String html = activity.generateHtml("light", false);
        assertFalse(html.contains("comment highlighted"));
        assertTrue(html.contains("scrollToElement(\"comment99\")"));
    }

    @Test
    public void generateHtml_zeroReactions_noReactionRow() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(commentWithReactions(7L, reactions(0)));
        CommitDiffViewerActivity activity = launchCommit(comments);
        assertFalse(activity.generateHtml("light", false).contains("class='reaction'"));
    }

    @Test
    public void generateHtml_highlightStartOnly_noOnload() {
        Intent intent = CommitDiffViewerActivity.makeIntent(appContext(), "o", "r", "abc1234",
                "a.txt", "@@ -1,2 +1,2 @@\n-old\n+new\n ctx", null, 1, 99, false, null);
        CommitDiffViewerActivity activity =
                launch(CommitDiffViewerActivity.class, intent);
        assertFalse(activity.generateHtml("light", false).contains("highlightDiffLines"));
    }

    // ---------- onOptionsItemSelected ----------

    @Test
    public void onOptionsItemSelected_browser_showsNoBrowserToast() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        MenuBuilder menu = new MenuBuilder(appContext());
        MenuItem item = menu.add(0, com.gh4a.R.id.browser, 0, "browser");
        assertTrue(activity.onOptionsItemSelected(item));
        assertEquals(activity.getString(com.gh4a.R.string.no_browser_found),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onOptionsItemSelected_share_startsChooser() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        MenuBuilder menu = new MenuBuilder(appContext());
        MenuItem item = menu.add(0, com.gh4a.R.id.share, 0, "share");
        assertTrue(activity.onOptionsItemSelected(item));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
    }

    @Test
    public void onOptionsItemSelected_view_startsFileViewer() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        java.lang.reflect.Field f = DiffViewerActivity.class.getDeclaredField("MENU_ITEM_VIEW");
        f.setAccessible(true);
        MenuBuilder menu = new MenuBuilder(appContext());
        MenuItem item = menu.add(0, f.getInt(null), 0, "view");
        assertTrue(activity.onOptionsItemSelected(item));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(FileViewerActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void onOptionsItemSelected_unknown_returnsFalse() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        MenuBuilder menu = new MenuBuilder(appContext());
        MenuItem item = menu.add(0, 99999, 0, "unknown");
        assertFalse(activity.onOptionsItemSelected(item));
    }

    // ---------- onConfirmed / deleteComment ----------

    @Test
    public void onConfirmed_deleteConfirm_deletesCommentAndRefreshes() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        when(svc.deleteCommitComment(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(null)));
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        try (MockedStatic<ServiceFactory> mocked = mockCommentService(svc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            Bundle data = new Bundle();
            data.putLong("id", 7L);
            activity.onConfirmed("deleteconfirm", data);
            waitFor(() -> Shadows.shadowOf(activity).getResultCode() == Activity.RESULT_OK,
                    5000);
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            verify(svc).deleteCommitComment(eq("o"), eq("r"), eq(7L));
            verify(svc, timeout(5000).atLeastOnce()).getCommitComments(
                    eq("o"), eq("r"), eq("abc1234"), anyLong());
        }
    }

    @Test
    public void onConfirmed_deleteConfirm_error_showsRetrySnackbarAndLogs() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        when(svc.deleteCommitComment(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.error(new IOException("boom")));
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        try (MockedStatic<ServiceFactory> mocked = mockCommentService(svc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            Bundle data = new Bundle();
            data.putLong("id", 7L);
            activity.onConfirmed("deleteconfirm", data);
            waitForSnackbarWithText(activity,
                    activity.getString(com.gh4a.R.string.error_delete_commit_comment));
            dismissCurrentSnackbarAsTimeout();
            assertTrue(logContains(Gh4Application.LOG_TAG, "Comment deletion failed"));
            assertFalse(activity.isFinishing());
        }
    }

    // ---------- addCommentsToMap ----------

    @Test
    public void addCommentsToMap_skipsWrongPathAndNullPosition() {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        comments.add(comment(8L, "alice", 2));
        comments.add(comment(9L, "alice", null));
        comments.add(comment(10L, "alice", 2, "b.txt"));
        CommitDiffViewerActivity activity = launchCommit(comments);
        @SuppressWarnings("unchecked")
        SparseArray<List<GitComment>> byPosition =
                (SparseArray<List<GitComment>>) get(activity, "mCommentsByPosition");
        assertEquals(1, byPosition.size());
        assertEquals(2, byPosition.get(2).size());
        assertEquals(7L, (long) byPosition.get(2).get(0).id());
        assertEquals(8L, (long) byPosition.get(2).get(1).id());
    }

    // ---------- handleUrlLoad ----------

    @Test
    public void handleUrlLoad_nonCommentScheme_opensInternally() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("https://github.com/o/r/issues/1")});
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull("github link should open internally", started);
        assertEquals(IssueActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void handleUrlLoad_nullScheme_ignored() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("relative/path")});
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void handleUrlLoad_commentUri_showsPopup() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("comment://add?position=2&l=2&r=1&isRightLine=false")});
        // The androidx PopupMenu has no Robolectric shadow to observe, so the
        // assertion is that the handler runs without crashing and starts
        // nothing; the popup's own content is covered by the direct
        // construction tests below.
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void handleUrlLoad_malformedUri_ignored() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("comment://add?position=abc&l=1&r=1")});
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void handleUrlLoad_missingId_showsEmptyPopup() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("comment://add?position=2&l=2&r=1&isRightLine=true")});
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void handleUrlLoad_negativeLine_ignored() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("comment://add?position=-1&l=0&r=0")});
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void handleUrlLoad_lineOutOfBounds_ignored() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("comment://add?position=999&l=0&r=0")});
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void handleUrlLoad_nullDiffLines_ignored() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        set(activity, "mDiffLines", null);
        call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                new Object[]{Uri.parse("comment://add?position=2&l=2&r=1&isRightLine=false")});
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    // ---------- canAddReaction / refresh / edit launcher ----------

    @Test
    public void canAddReaction_returnsTrue() {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        assertTrue(activity.canAddReaction());
    }

    @Test
    public void refresh_setsResultOkAndReloads() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        try (MockedStatic<ServiceFactory> mocked = mockCommentService(svc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            call(activity, "refresh", new Class<?>[]{}, new Object[]{});
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            assertFalse(activity.getIntent().hasExtra("comments"));
            verify(svc, timeout(5000)).getCommitComments(
                    eq("o"), eq("r"), eq("abc1234"), anyLong());
        }
    }

    @Test
    public void editLauncher_resultOk_refreshes() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        try (MockedStatic<ServiceFactory> mocked = mockCommentService(svc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            call(activity, "openCommentDialog",
                    new Class<?>[]{long.class, long.class, String.class, int.class, int.class,
                            int.class, com.meisolsson.githubsdk.model.PositionalCommentBase.class},
                    new Object[]{0L, 0L, "line", 1, 1, 1, null});
            dispatchLastLaunchedResult(activity, Activity.RESULT_OK, null);
            waitFor(() -> Shadows.shadowOf(activity).getResultCode() == Activity.RESULT_OK,
                    5000);
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            verify(svc, timeout(5000)).getCommitComments(
                    eq("o"), eq("r"), eq("abc1234"), anyLong());
        }
    }

    // ---------- CommentActionPopup (direct construction) ----------

    @Test
    public void popup_unknownId_returnsEarlyWithEmptyMenu() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        Object popup = newPopup(activity, 999L, 2, "-old", 2, 1, 0, 0, false);
        assertEquals(0, popupMenu(popup).size());
    }

    @Test
    public void popup_nullComment_returnsEarly() throws Exception {
        CommitDiffViewerActivity activity = launchCommit(new ArrayList<>());
        @SuppressWarnings("unchecked")
        androidx.collection.LongSparseArray<DiffViewerActivity.CommentWrapper> wrapped =
                (androidx.collection.LongSparseArray<DiffViewerActivity.CommentWrapper>)
                        get(activity, "mWrappedComments");
        wrapped.put(123L, new DiffViewerActivity.CommentWrapper(null));
        Object popup = newPopup(activity, 123L, 2, "-old", 2, 1, 0, 0, false);
        assertEquals(0, popupMenu(popup).size());
    }

    @Test
    public void popup_addComment_hasAddCommentShareOnly() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(0L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        Object popup = newPopup(activity, 0L, 2, "-old", 2, 1, 0, 0, false);
        Menu menu = popupMenu(popup);
        assertNotNull(menu.findItem(com.gh4a.R.id.add_comment));
        assertNotNull(menu.findItem(com.gh4a.R.id.share));
        assertNull(menu.findItem(com.gh4a.R.id.reply));
        assertNull(menu.findItem(com.gh4a.R.id.edit));
        assertNull(menu.findItem(com.gh4a.R.id.delete));
        assertNull(menu.findItem(com.gh4a.R.id.react));
    }

    @Test
    public void popup_ownComment_keepsEditDelete() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            Menu menu = popupMenu(popup);
            assertNotNull(menu.findItem(com.gh4a.R.id.edit));
            assertNotNull(menu.findItem(com.gh4a.R.id.delete));
            assertNotNull(menu.findItem(com.gh4a.R.id.react));
            // CommitDiffViewerActivity.canReply() is always false.
            assertNull(menu.findItem(com.gh4a.R.id.reply));
        }
    }

    @Test
    public void popup_foreignComment_removesEditDelete() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "bob", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            Menu menu = popupMenu(popup);
            assertNull(menu.findItem(com.gh4a.R.id.edit));
            assertNull(menu.findItem(com.gh4a.R.id.delete));
            assertNotNull(menu.findItem(com.gh4a.R.id.share));
            assertNotNull(menu.findItem(com.gh4a.R.id.react));
        }
    }

    @Test
    public void popup_canReplyTrue_keepsReply() throws Exception {
        List<ReviewComment> comments = new ArrayList<>();
        comments.add(reviewComment(7L, "alice", 2));
        ReactionService reactionSvc = mock(ReactionService.class);
        when(reactionSvc.getPullRequestReviewCommentReactions(
                eq("o"), eq("r"), eq(7L), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Reaction>builder().items(new ArrayList<>()).build())));
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            Intent intent = PullRequestDiffViewerActivity.makeIntent(appContext(), "o", "r", 1,
                    "abc1234", "a.txt", DIFF, comments, -1, -1, -1, false, null);
            PullRequestDiffViewerActivity activity =
                    launch(PullRequestDiffViewerActivity.class, intent);
            activity.generateHtml("light", false);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            Menu menu = popupMenu(popup);
            // PullRequestDiffViewerActivity.canReply() returns true.
            assertNotNull(menu.findItem(com.gh4a.R.id.reply));
            assertNotNull(menu.findItem(com.gh4a.R.id.edit));
            assertNotNull(menu.findItem(com.gh4a.R.id.delete));
        }
    }

    @Test
    public void popupMenu_delete_showsConfirmDialogAndDeletes() throws Exception {
        RepositoryCommentService svc = stubEmptyCommentService();
        when(svc.deleteCommitComment(eq("o"), eq("r"), eq(7L)))
                .thenReturn(Single.just(Response.success(null)));
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockServices(svc, reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.delete)));
            activity.getSupportFragmentManager().executePendingTransactions();
            androidx.fragment.app.DialogFragment dialog =
                    (androidx.fragment.app.DialogFragment) activity.getSupportFragmentManager()
                            .findFragmentByTag("deleteconfirm");
            assertNotNull("confirm dialog missing", dialog);
            ((androidx.appcompat.app.AlertDialog) dialog.getDialog())
                    .getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            idleMain(200);
            waitFor(() -> Shadows.shadowOf(activity).getResultCode() == Activity.RESULT_OK,
                    5000);
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
            verify(svc).deleteCommitComment(eq("o"), eq("r"), eq(7L));
        }
    }

    @Test
    public void popupMenu_reply_opensCommentDialog() throws Exception {
        // Reply only exists when canReply() is true, i.e. the pull request
        // viewer; the commit viewer removes the item (see
        // popup_ownComment_keepsEditDelete).
        List<ReviewComment> comments = new ArrayList<>();
        comments.add(reviewComment(7L, "alice", 2));
        ReactionService reactionSvc = mock(ReactionService.class);
        when(reactionSvc.getPullRequestReviewCommentReactions(
                eq("o"), eq("r"), eq(7L), anyLong()))
                .thenReturn(Single.just(Response.success(
                        Page.<Reaction>builder().items(new ArrayList<>()).build())));
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            Intent intent = PullRequestDiffViewerActivity.makeIntent(appContext(), "o", "r", 1,
                    "abc1234", "a.txt", DIFF, comments, -1, -1, -1, false, null);
            PullRequestDiffViewerActivity activity =
                    launch(PullRequestDiffViewerActivity.class, intent);
            activity.generateHtml("light", false);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.reply)));
            ShadowActivity.IntentForResult started =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(started);
            assertEquals(EditPullRequestDiffCommentActivity.class.getName(),
                    started.intent.getComponent().getClassName());
        }
    }

    @Test
    public void popupMenu_edit_opensCommentDialogWithComment() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.edit)));
            ShadowActivity.IntentForResult started =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(started);
            assertEquals(EditDiffCommentActivity.class.getName(),
                    started.intent.getComponent().getClassName());
            assertEquals(7L, started.intent.getLongExtra("id", -1L));
        }
    }

    @Test
    public void popupMenu_addComment_opensCommentDialog() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(0L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        Object popup = newPopup(activity, 0L, 2, "-old", 2, 1, 0, 0, false);
        assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.add_comment)));
        ShadowActivity.IntentForResult started =
                Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(started);
        assertEquals(EditDiffCommentActivity.class.getName(),
                started.intent.getComponent().getClassName());
    }

    @Test
    public void popupMenu_share_startsChooser() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.share)));
            Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(Intent.ACTION_CHOOSER, started.getAction());
        }
    }

    @Test
    public void popupMenu_reactionItem_addsReaction() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        Reaction added = mock(Reaction.class);
        when(added.id()).thenReturn(42L);
        when(reactionSvc.createCommitCommentReaction(eq("o"), eq("r"), eq(7L), any()))
                .thenReturn(Single.just(Response.success(added)));
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            MenuItem plusOne = popupMenu(popup).findItem(com.gh4a.R.id.plus_one);
            assertNotNull("plus_one item missing", plusOne);
            assertTrue(popupClick(popup, plusOne));
            verify(reactionSvc, timeout(5000)).createCommitCommentReaction(
                    eq("o"), eq("r"), eq(7L), any());
        }
    }

    @Test
    public void popupMenu_unknownItem_returnsTrue() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            MenuBuilder menu = new MenuBuilder(appContext());
            MenuItem unknown = menu.add(0, 99999, 0, "unknown");
            assertTrue(popupClick(popup, unknown));
        }
    }

    @Test
    public void handleUrlLoad_commentUriWithId_showsPopupForComment() throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            call(activity, "handleUrlLoad", new Class<?>[]{Uri.class},
                    new Object[]{Uri.parse(
                            "comment://edit?position=2&l=2&r=1&isRightLine=false&id=7")});
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void popupMenu_edit_wrapperRemoved_opensCommentDialogWithNullComment()
            throws Exception {
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(7L, "alice", 2));
        ReactionService reactionSvc = stubEmptyReactionService();
        try (MockedStatic<ServiceFactory> mocked = mockReactionService(reactionSvc)) {
            CommitDiffViewerActivity activity = launchCommit(comments);
            authorizeAs("alice");
            Object popup = newPopup(activity, 7L, 2, "-old", 2, 1, 0, 0, false);
            // The wrapper existed at construction time; removing it now makes
            // the edit handler take the wrapper == null branch.
            @SuppressWarnings("unchecked")
            androidx.collection.LongSparseArray<DiffViewerActivity.CommentWrapper> wrapped =
                    (androidx.collection.LongSparseArray<DiffViewerActivity.CommentWrapper>)
                            get(activity, "mWrappedComments");
            wrapped.remove(7L);
            assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.edit)));
            ShadowActivity.IntentForResult started =
                    Shadows.shadowOf(activity).getNextStartedActivityForResult();
            assertNotNull(started);
            assertEquals(EditDiffCommentActivity.class.getName(),
                    started.intent.getComponent().getClassName());
        }
    }

    @Test
    public void popupMenu_share_rightLine_usesRightLine() throws Exception {
        // id 0: createUrl embeds the line link id in the fragment
        // ("diff-<sha>R1"); with a reply id it would use "r<id>" instead.
        List<GitComment> comments = new ArrayList<>();
        comments.add(comment(0L, "alice", 2));
        CommitDiffViewerActivity activity = launchCommit(comments);
        Object popup = newPopup(activity, 0L, 2, "-old", 2, 1, 0, 0, true);
        assertTrue(popupClick(popup, popupMenu(popup).findItem(com.gh4a.R.id.share)));
        Intent chooser = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(chooser);
        assertEquals(Intent.ACTION_CHOOSER, chooser.getAction());
        Intent send = chooser.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(send);
        assertTrue(send.getStringExtra(Intent.EXTRA_TEXT).contains("R1"));
    }
}
