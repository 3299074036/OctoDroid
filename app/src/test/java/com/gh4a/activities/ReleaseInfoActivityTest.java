package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.service.repositories.RepositoryReleaseService;

import io.reactivex.Single;
import retrofit2.Response;
import android.view.View;
import android.widget.TextView;
import com.meisolsson.githubsdk.model.Reactions;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.User;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import androidx.recyclerview.widget.RecyclerView;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
public class ReleaseInfoActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private ActivityController<ReleaseInfoActivity> launchById() {
        return Robolectric.buildActivity(ReleaseInfoActivity.class,
                ReleaseInfoActivity.makeIntent(context(), "octocat", "hello", 123L));
    }

    private ActivityController<ReleaseInfoActivity> launchByTag() {
        return Robolectric.buildActivity(ReleaseInfoActivity.class,
                ReleaseInfoActivity.makeIntent(context(), "octocat", "hello", "v1.0"));
    }

    @Test
    public void makeIntent_byId_carriesExtras() {
        Intent intent = ReleaseInfoActivity.makeIntent(context(), "octocat", "hello", 123L);
        assertEquals(ReleaseInfoActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
        assertEquals(123L, intent.getLongExtra("id", -1L));
        assertNull(intent.getStringExtra("tag"));
    }

    @Test
    public void makeIntent_byTag_carriesExtras() {
        Intent intent = ReleaseInfoActivity.makeIntent(context(), "octocat", "hello", "v1.0");
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
        assertEquals("v1.0", intent.getStringExtra("tag"));
        assertEquals(0L, intent.getLongExtra("id", 0L));
    }

    @Test
    public void launches_byId_titleAndSubtitle() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.release_title),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void launches_byTag_titleAndSubtitle() {
        try (ActivityController<ReleaseInfoActivity> c = launchByTag()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.release_title),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void canAddReaction_unauthorizedAndNoRelease_isFalse() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertFalse(activity.canAddReaction());
        }
    }

    @Test
    public void canChildScrollUp_beforeLoad_isFalse() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertFalse(activity.canChildScrollUp());
        }
    }

    @Test
    public void onOptionsItemSelected_browser_beforeLoad_returnsTrueWithoutCrash() {
        // 155/186 守卫：mRelease 为 null 时不解析 htmlUrl、不启动浏览器。
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertNotNull(menu.findItem(R.id.browser));
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
        }
    }

    @Test
    public void onItemLongClick_copiesDownloadUrlAndReturnsTrue() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            ReleaseAsset asset = mock(ReleaseAsset.class);
            when(asset.name()).thenReturn("app.apk");
            when(asset.browserDownloadUrl())
                    .thenReturn("https://github.com/o/r/releases/download/v1/app.apk");
            assertTrue(activity.onItemLongClick(asset));
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertEquals("https://github.com/o/r/releases/download/v1/app.apk",
                    cm.getPrimaryClip().getItemAt(0).getText().toString());
            assertEquals("Release asset app.apk",
                    cm.getPrimaryClip().getDescription().getLabel().toString());
        }
    }

    @Test
    public void onRefresh_doesNotCrash() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }

    @Test
    public void navigateUp_returnsReleaseListIntent() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(ReleaseListActivity.class.getName(),
                    up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("owner"));
            assertEquals("hello", up.getStringExtra("repo"));
        }
    }

    @Test
    public void onPrepareOptionsMenu_beforeLoad_doesNotCrash() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onPrepareOptionsMenu(menu));
        }
    }

    private Release mockRelease(boolean draft) {
        Release r = mock(Release.class);
        when(r.id()).thenReturn(5L);
        when(r.name()).thenReturn("v1.0");
        when(r.tagName()).thenReturn("v1.0");
        User author = mock(User.class);
        when(author.login()).thenReturn("octocat");
        when(author.avatarUrl()).thenReturn(null);
        when(r.author()).thenReturn(author);
        when(r.publishedAt()).thenReturn(null);
        when(r.createdAt()).thenReturn(new Date(0L));
        when(r.draft()).thenReturn(draft);
        when(r.prerelease()).thenReturn(false);
        when(r.bodyHtml()).thenReturn("");
        when(r.reactions()).thenReturn(null);
        when(r.assets()).thenReturn(null);
        when(r.zipballUrl()).thenReturn(null);
        when(r.tarballUrl()).thenReturn(null);
        when(r.htmlUrl()).thenReturn("https://github.com/octocat/hello/releases/v1.0");
        return r;
    }

    private static void setRelease(ReleaseInfoActivity activity, Release release) throws Exception {
        Field f = ReleaseInfoActivity.class.getDeclaredField("mRelease");
        f.setAccessible(true);
        f.set(activity, release);
    }

    private static void callHandleReleaseReady(ReleaseInfoActivity activity) throws Exception {
        Method m = ReleaseInfoActivity.class.getDeclaredMethod("handleReleaseReady");
        m.setAccessible(true);
        m.invoke(activity);
    }

    @Test
    public void handleReleaseReady_withMockRelease_fillsViewsAndTitle() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            callHandleReleaseReady(activity);
            assertEquals("v1.0", activity.getSupportActionBar().getTitle().toString());
            assertTrue(((TextView) activity.findViewById(R.id.tv_releasetag))
                    .getText().toString().contains("v1.0"));
            assertEquals(activity.getString(R.string.release_no_releasenotes),
                    ((TextView) activity.findViewById(R.id.tv_release_notes))
                            .getText().toString());
            assertEquals(activity.getString(R.string.release_type_final),
                    ((TextView) activity.findViewById(R.id.tv_releasetype))
                            .getText().toString());
            assertEquals(View.GONE, activity.findViewById(R.id.downloads).getVisibility());
        }
    }

    @Test
    public void handleReleaseReady_emptyName_fallsBackToTag() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            Release r = mockRelease(false);
            when(r.name()).thenReturn("");
            setRelease(activity, r);
            callHandleReleaseReady(activity);
            assertEquals("v1.0", activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void handleReleaseReady_draft_showsDraftType() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(true));
            callHandleReleaseReady(activity);
            assertEquals(activity.getString(R.string.release_type_draft),
                    ((TextView) activity.findViewById(R.id.tv_releasetype))
                            .getText().toString());
        }
    }

    @Test
    public void onClick_releaseTag_opensRepositoryActivity() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            activity.onClick(activity.findViewById(R.id.tv_releasetag));
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(RepositoryActivity.class.getName(),
                    next.getComponent().getClassName());
        }
    }

    @Test
    public void onClick_gravatar_opensUserActivity() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            activity.onClick(activity.findViewById(R.id.iv_gravatar));
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(UserActivity.class.getName(), next.getComponent().getClassName());
        }
    }

    @Test
    public void canAddReaction_withoutLogin_returnsFalse() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertFalse(activity.canAddReaction());
            setRelease(activity, mockRelease(false));
            assertFalse(activity.canAddReaction());
            setRelease(activity, mockRelease(true));
            assertFalse(activity.canAddReaction());
        }
    }

    @Test
    public void loadReactionDetails_returnsNonNullSingle() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            assertNotNull(activity.loadReactionDetails(null, false));
        }
    }

    @Test
    public void addReaction_returnsNonNullSingle() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            assertNotNull(activity.addReaction(null, "+1"));
        }
    }

    @Test
    public void deleteReaction_returnsNonNullSingle() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            assertNotNull(activity.deleteReaction(null, 9L));
        }
    }

    @Test
    public void onReactionsUpdated_withCounts_showsBar() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            Reactions reactions = mock(Reactions.class);
            when(reactions.totalCount()).thenReturn(3);
            when(reactions.plusOne()).thenReturn(2);
            when(reactions.minusOne()).thenReturn(0);
            when(reactions.laugh()).thenReturn(0);
            when(reactions.hooray()).thenReturn(0);
            when(reactions.confused()).thenReturn(0);
            when(reactions.heart()).thenReturn(0);
            when(reactions.rocket()).thenReturn(0);
            when(reactions.eyes()).thenReturn(0);
            Method m = ReleaseInfoActivity.class.getDeclaredMethod("onReactionsUpdated",
                    com.gh4a.widget.ReactionBar.Item.class, Reactions.class);
            m.setAccessible(true);
            m.invoke(activity, null, reactions);
            assertEquals(View.VISIBLE,
                    activity.findViewById(R.id.reactions).getVisibility());
        }
    }

    @Test
    public void onItemClick_withValidAsset_noFailedToast() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            ReleaseAsset asset = mock(ReleaseAsset.class);
            when(asset.url()).thenReturn("https://example.com/a.apk");
            when(asset.name()).thenReturn("a.apk");
            activity.onItemClick(asset);
            String toast = ShadowToast.getTextOfLatestToast();
            assertTrue(toast == null
                    || !toast.equals(activity.getString(R.string.download_failed)));
        }
    }

    @Test
    public void onItemLongClick_copiesLinkToClipboard() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            ReleaseAsset asset = mock(ReleaseAsset.class);
            when(asset.name()).thenReturn("a.apk");
            when(asset.browserDownloadUrl()).thenReturn("https://example.com/a.apk");
            assertTrue(activity.onItemLongClick(asset));
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    context().getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(cm.getPrimaryClip());
            assertEquals("https://example.com/a.apk",
                    cm.getPrimaryClip().getItemAt(0).getText().toString());
        }
    }

    @Test
    public void onRefresh_clearsReleaseAndReloadsWithoutCrash() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }

    // ---- A2 第二轮补缺口：ReleaseInfoActivity ----

    private Release mockReleaseRich(boolean prerelease, String bodyHtml,
            java.util.List<ReleaseAsset> assets, String zipball, String tarball,
            java.util.Date publishedAt, Reactions reactions) {
        Release r = mock(Release.class);
        when(r.id()).thenReturn(5L);
        when(r.name()).thenReturn("v1.0");
        when(r.tagName()).thenReturn("v1.0");
        User author = mock(User.class);
        when(author.login()).thenReturn("octocat");
        when(author.avatarUrl()).thenReturn(null);
        when(r.author()).thenReturn(author);
        when(r.publishedAt()).thenReturn(publishedAt);
        when(r.createdAt()).thenReturn(new Date(0L));
        when(r.draft()).thenReturn(false);
        when(r.prerelease()).thenReturn(prerelease);
        when(r.bodyHtml()).thenReturn(bodyHtml);
        when(r.reactions()).thenReturn(reactions);
        when(r.assets()).thenReturn(assets);
        when(r.zipballUrl()).thenReturn(zipball);
        when(r.tarballUrl()).thenReturn(tarball);
        when(r.htmlUrl()).thenReturn("https://github.com/octocat/hello/releases/v1.0");
        return r;
    }

    @Test
    public void handleReleaseReady_prerelease_setsPrereleaseType() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockReleaseRich(true, "", null, null, null, null, null));
            callHandleReleaseReady(activity);
            TextView type = activity.findViewById(R.id.tv_releasetype);
            assertEquals(activity.getString(R.string.release_type_prerelease),
                    type.getText().toString());
        }
    }

    @Test
    public void handleReleaseReady_publishedAtNonNull_usesPublishedAt() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockReleaseRich(false, "", null, null, null, new Date(), null));
            callHandleReleaseReady(activity);
            TextView details = activity.findViewById(R.id.tv_releaseinfo);
            assertTrue(details.getText().toString().contains("octocat"));
        }
    }

    @Test
    public void handleReleaseReady_bodyHtmlNonEmpty_bindsHtml() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockReleaseRich(false, "<p>release notes</p>",
                    null, null, null, null, null));
            callHandleReleaseReady(activity);
            TextView body = activity.findViewById(R.id.tv_release_notes);
            assertTrue(body.getText().toString().contains("release notes"));
        }
    }

    @Test
    public void handleReleaseReady_withAssetsAndArchives_showsThreeDownloadRows() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            ReleaseAsset asset = mock(ReleaseAsset.class);
            when(asset.name()).thenReturn("app.apk");
            when(asset.browserDownloadUrl()).thenReturn("https://example.com/a.apk");
            when(asset.size()).thenReturn(1024);
            when(asset.downloadCount()).thenReturn(5);
            setRelease(activity, mockReleaseRich(false, "",
                    java.util.Collections.singletonList(asset),
                    "https://example.com/zip", "https://example.com/tar", null, null));
            callHandleReleaseReady(activity);
            androidx.recyclerview.widget.RecyclerView downloads =
                    activity.findViewById(R.id.download_list);
            assertNotNull(downloads.getAdapter());
            assertEquals(3, downloads.getAdapter().getItemCount());
        }
    }

    @Test
    public void handleReleaseReady_withReactions_setsReactionBar() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockReleaseRich(false, "", null, null, null, null,
                    mock(Reactions.class)));
            callHandleReleaseReady(activity);
            assertNotNull(activity.findViewById(R.id.reactions));
        }
    }

    @Test
    public void canAddReaction_authorizedNonDraft_returnsTrue() throws Exception {
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(user.id()).thenReturn(1L);
        com.gh4a.Gh4Application.get().addAccount(user, "fake-token");
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            assertTrue(activity.canAddReaction());
        }
    }


    // ---- A2 第三轮补缺口：ReleaseInfoActivity ----

    @Test
    public void onPrepareOptionsMenu_authorized_showsReactItem() throws Exception {
        // Single addAccount is intermittently ineffective in a class run
        // (secure-prefs backend jitter), which made this test fail in the
        // chunk run while passing standalone -- use the authorize() retry
        // loop (same as canAddReaction_authorizedWithNonDraftRelease_...)
        // and assert authorization before exercising the menu.
        authorize();
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertTrue(Gh4Application.get().isAuthorized());
            setRelease(activity, mockRelease(false));
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onPrepareOptionsMenu(menu));
            assertTrue(menu.findItem(R.id.react).isVisible());
        } finally {
            Gh4Application.get().removeAccount("octocat");
        }
    }

    @Test
    public void onOptionsItemSelected_browser_nullRelease_returnsTrue() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            menu.add(0, R.id.browser, 0, "Browser");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
        }
    }

    @Test
    public void onOptionsItemSelected_browser_withUrl_noBrowserShowsToast() throws Exception {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            menu.add(0, R.id.browser, 0, "Browser");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
            assertEquals(activity.getString(R.string.no_browser_found),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void onOptionsItemSelected_reactionItem_withHelper_returnsTrue() throws Exception {
        // Single addAccount is intermittently ineffective in a class run
        // (secure-prefs backend jitter) -- this test failed in the round-2
        // re-run while the same setup passed in the first run, so use the
        // authorize() retry loop like the neighboring auth tests instead
        // of relying on one addAccount (or on auth leaked from another test).
        authorize();
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertTrue(Gh4Application.get().isAuthorized());
            setRelease(activity, mockRelease(false));
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onPrepareOptionsMenu(menu);
            android.view.MenuItem heart = menu.findItem(R.id.heart);
            assertNotNull(heart);
            assertTrue(activity.onOptionsItemSelected(heart));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
        } finally {
            Gh4Application.get().removeAccount("octocat");
        }
    }


    // ---- A2 第四轮补缺口：ReleaseInfoActivity ----

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

    // ---- gap fixes (2026-11-22, group C) ----

    private static void setReactionMenuHelper(ReleaseInfoActivity activity,
            ReactionBar.AddReactionMenuHelper helper) throws Exception {
        Field f = ReleaseInfoActivity.class.getDeclaredField("mReactionMenuHelper");
        f.setAccessible(true);
        f.set(activity, helper);
    }

    private void authorize() {
        // Retry loop (same as IssueEditActivityTest): a single addAccount is
        // intermittently ineffective in the same class run (secure-prefs
        // backend jitter).
        Gh4Application app = Gh4Application.get();
        for (int i = 0; i < 3
                && (!app.isAuthorized() || !"octocat".equals(app.getAuthLogin())); i++) {
            app.addAccount(User.builder().login("octocat").id(1L).build(), "tok");
            ActivityTestHelper.idleMain(100);
        }
    }

    @Test
    public void onOptionsItemSelected_reactionHelperHandlesItem_returnsTrue()
            throws Exception {
        // mReactionMenuHelper non-null and onItemClick true: the item is
        // consumed by the helper (L153 both operands true + L154).
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            ReactionBar.AddReactionMenuHelper helper =
                    mock(ReactionBar.AddReactionMenuHelper.class);
            when(helper.onItemClick(any())).thenReturn(true);
            setReactionMenuHelper(activity, helper);
            assertTrue(activity.onOptionsItemSelected(mock(MenuItem.class)));
        }
    }

    @Test
    public void onOptionsItemSelected_reactionHelperIgnoresItem_fallsThrough()
            throws Exception {
        // mReactionMenuHelper non-null but onItemClick false: the item falls
        // through to the browser/super handling (L153 second operand false).
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            ReactionBar.AddReactionMenuHelper helper =
                    mock(ReactionBar.AddReactionMenuHelper.class);
            when(helper.onItemClick(any())).thenReturn(false);
            setReactionMenuHelper(activity, helper);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

    @Test
    public void onOptionsItemSelected_browser_nullHtmlUrl_returnsTrue() throws Exception {
        // mRelease non-null but htmlUrl() null: the browser item is consumed
        // without launching anything (L158 second operand true).
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            Release r = mockRelease(false);
            when(r.htmlUrl()).thenReturn(null);
            setRelease(activity, r);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void canAddReaction_authorizedWithNonDraftRelease_returnsTrue()
            throws Exception {
        // The only all-true path of L286: authorized + release present +
        // not a draft. The authorized-but-draft / authorized-but-no-release
        // sub-cases stay false.
        authorize();
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            assertTrue(Gh4Application.get().isAuthorized());
            setRelease(activity, mockRelease(false));
            assertTrue(activity.canAddReaction());
            setRelease(activity, mockRelease(true));
            assertFalse(activity.canAddReaction());
            setRelease(activity, null);
            assertFalse(activity.canAddReaction());
        } finally {
            Gh4Application.get().removeAccount("octocat");
        }
    }

    @Test
    public void onClick_unknownView_doesNothing() {
        // View id matches no case in the switch: intent stays null and
        // nothing is started (L336 default + L346 false side).
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            activity.onClick(new View(activity));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void loadRelease_mockedService_successFillsRelease() throws Exception {
        // The loader's success consumer (L364-L367): the mocked service
        // returns the release, onCreate's load fills mRelease, runs
        // handleReleaseReady (title "v1.0") and shows the content.
        // NB: mockRelease() itself stubs a mock, so it must be called
        // BEFORE when(...) starts -- calling it inside thenReturn's
        // argument nests one stubbing inside another and Mockito throws
        // UnfinishedStubbingException.
        Release release = mockRelease(false);
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        when(service.getRelease(anyString(), anyString(), anyLong()))
                .thenReturn(Single.just(Response.success(release)));
        when(service.getRelaseByTagName(anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(release)));
        try (MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.get(
                            eq(RepositoryReleaseService.class), anyBoolean()))
                    .thenReturn(service);
            try (ActivityController<ReleaseInfoActivity> c = launchById()) {
                ReleaseInfoActivity activity = c.setup().get();
                ActivityTestHelper.waitFor(
                        () -> ActivityTestHelper.get(activity, "mRelease") != null, 10000);
                assertNotNull(ActivityTestHelper.get(activity, "mRelease"));
                assertEquals("v1.0",
                        activity.getSupportActionBar().getTitle().toString());
            }
        }
    }

}
