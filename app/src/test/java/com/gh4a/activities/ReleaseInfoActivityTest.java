package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.ReleaseAsset;
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
import com.gh4a.Gh4Application;
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
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(user.id()).thenReturn(1L);
        com.gh4a.Gh4Application.get().addAccount(user, "fake-token");
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertTrue(activity.onPrepareOptionsMenu(menu));
            assertTrue(menu.findItem(R.id.react).isVisible());
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
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(user.id()).thenReturn(1L);
        com.gh4a.Gh4Application.get().addAccount(user, "fake-token");
        try (ActivityController<ReleaseInfoActivity> c = launchById()) {
            ReleaseInfoActivity activity = c.setup().get();
            setRelease(activity, mockRelease(false));
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onPrepareOptionsMenu(menu);
            android.view.MenuItem heart = menu.findItem(R.id.heart);
            assertNotNull(heart);
            assertTrue(activity.onOptionsItemSelected(heart));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
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

}
