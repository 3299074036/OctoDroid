package com.gh4a.activities;

import android.content.Intent;
import android.view.MenuItem;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.GistFile;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.gists.GistService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class GistActivityTest {
    @Test
    public void makeIntent_setsIdExtra() {
        Intent i = GistActivity.makeIntent(appContext(), "abc123");
        assertEquals(GistActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("abc123", i.getStringExtra("id"));
    }

    @Test
    public void launch_titleUsesGistId() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        assertEquals(appContext().getString(R.string.gist_title, "abc123"),
                activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void navigateUp_withoutGist_returnsNull() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        assertNull(activity.navigateUp());
    }

    @Test
    public void navigateUp_withGistOwner_goesToGistList() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        Gist gist = mock(Gist.class);
        when(gist.owner()).thenReturn(User.builder().login("alice").build());
        set(activity, "mGist", gist);
        Intent up = activity.navigateUp();
        assertEquals(GistListActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("alice", up.getStringExtra("user"));
    }

    @Test
    public void navigateUp_withGistButNoOwner_returnsNull() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        Gist gist = mock(Gist.class);
        when(gist.owner()).thenReturn(null);
        set(activity, "mGist", gist);
        assertNull(activity.navigateUp());
    }

    @Test
    public void onPrepareOptionsMenu_unauthorized_hidesStar() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertFalse(menu.findItem(R.id.star).isVisible());
    }

    @Test
    public void onOptionsItemSelected_shareWithoutGist_returnsTrueWithoutCrash() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
    }

    @Test
    public void onOptionsItemSelected_starWithoutState_doesNotCallNetwork() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        // mIsStarred is null: updateStarringState short-circuits, no request
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.star)));
        assertNull(get(activity, "mIsStarred"));
    }

    @Test
    public void fillData_populatesDescriptionAndFiles() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        Gist gist = mock(Gist.class);
        when(gist.owner()).thenReturn(User.builder().login("alice").build());
        when(gist.description()).thenReturn("my gist");
        when(gist.createdAt()).thenReturn(null);
        when(gist.files()).thenReturn(new HashMap<>());
        when(gist.isPublic()).thenReturn(true);
        callFillData(activity, gist);
        assertEquals("alice", activity.getSupportActionBar().getSubtitle().toString());
        assertEquals("my gist",
                ((android.widget.TextView) activity.findViewById(R.id.tv_desc)).getText().toString());
        assertEquals(android.view.View.GONE, activity.findViewById(R.id.file_card).getVisibility());
        assertEquals(android.view.View.GONE, activity.findViewById(R.id.tv_private).getVisibility());
    }

    @Test
    public void fillData_emptyDescription_showsPlaceholderAndPrivateBadge() {
        GistActivity activity = launch(GistActivity.class, GistActivity.makeIntent(appContext(), "abc123"));
        Gist gist = mock(Gist.class);
        when(gist.owner()).thenReturn(null);
        when(gist.description()).thenReturn("");
        when(gist.createdAt()).thenReturn(null);
        when(gist.files()).thenReturn(null);
        when(gist.isPublic()).thenReturn(false);
        callFillData(activity, gist);
        assertEquals(appContext().getString(R.string.gist_no_description),
                ((android.widget.TextView) activity.findViewById(R.id.tv_desc)).getText().toString());
        assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.tv_private).getVisibility());
    }

    @Test
    public void onRefresh_reloadsGistAndStarredState() throws Exception {
        Gist gist = gistWithFile();
        GistService svc = mock(GistService.class);
        when(svc.getGist("abc123")).thenReturn(Single.just(Response.success(gist)));
        when(svc.checkIfGistIsStarred("abc123"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.get(GistService.class, true)).thenReturn(svc);
            GistActivity activity = launch(GistActivity.class,
                    GistActivity.makeIntent(appContext(), "abc123"));
            waitFor(() -> get(activity, "mGist") != null
                    && get(activity, "mIsStarred") != null, 10000);
            assertEquals(gist, get(activity, "mGist"));
            assertEquals(Boolean.TRUE, get(activity, "mIsStarred"));
            // force a refresh: both loads must run again with force=true
            set(activity, "mGist", null);
            set(activity, "mIsStarred", null);
            activity.onRefresh();
            waitFor(() -> get(activity, "mGist") != null
                    && get(activity, "mIsStarred") != null, 10000);
            assertEquals(gist, get(activity, "mGist"));
            assertEquals(Boolean.TRUE, get(activity, "mIsStarred"));
            assertEquals("hello.txt", ((android.widget.TextView)
                    ((android.view.ViewGroup) activity.findViewById(R.id.file_container))
                            .getChildAt(0)).getText().toString());
        }
    }

    @Test
    public void fillData_withFiles_buildsRowsAndRowClickOpensViewer() {
        GistActivity activity = launch(GistActivity.class,
                GistActivity.makeIntent(appContext(), "abc123"));
        callFillData(activity, gistWithFile());
        android.view.ViewGroup container =
                activity.findViewById(R.id.file_container);
        assertEquals(1, container.getChildCount());
        android.view.View row = container.getChildAt(0);
        assertEquals("hello.txt", ((android.widget.TextView) row).getText().toString());
        assertTrue(row.getTag() instanceof GistFile);
        row.performClick();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(GistViewerActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("abc123", started.getStringExtra("id"));
        assertEquals("hello.txt", started.getStringExtra("file"));
    }

    @Test
    public void onPrepareOptionsMenu_authorizedStarredNull_showsLoadingView() {
        authorize();
        GistActivity activity = launchWithMockedService();
        ensureAuthorized();
        assertTrue(Gh4Application.get().isAuthorized());
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.star).isVisible());
        assertNotNull(menu.findItem(R.id.star).getActionView());
    }

    @Test
    public void onPrepareOptionsMenu_authorizedStarredTrue_showsUnstar() {
        authorize();
        GistActivity activity = launchWithMockedService();
        set(activity, "mIsStarred", Boolean.TRUE);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertEquals(appContext().getString(R.string.repo_unstar_action),
                menu.findItem(R.id.star).getTitle().toString());
        assertNotNull(menu.findItem(R.id.star).getIcon());
    }

    @Test
    public void onPrepareOptionsMenu_authorizedStarredFalse_showsStar() {
        authorize();
        GistActivity activity = launchWithMockedService();
        set(activity, "mIsStarred", Boolean.FALSE);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertEquals(appContext().getString(R.string.repo_star_action),
                menu.findItem(R.id.star).getTitle().toString());
        assertNotNull(menu.findItem(R.id.star).getIcon());
    }

    @Test
    public void onPrepareOptionsMenu_noGist_removesShareItem() {
        authorize();
        GistActivity activity = launchWithMockedService();
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertNull(menu.findItem(R.id.share));
    }

    @Test
    public void onPrepareOptionsMenu_withGist_keepsShareItem() {
        authorize();
        GistActivity activity = launchWithMockedService();
        set(activity, "mGist", mock(Gist.class));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        activity.onPrepareOptionsMenu(menu);
        assertNotNull(menu.findItem(R.id.share));
    }

    @Test
    public void onOptionsItemSelected_shareWithGist_startsChooser() {
        GistActivity activity = launch(GistActivity.class,
                GistActivity.makeIntent(appContext(), "abc123"));
        Gist gist = mock(Gist.class);
        when(gist.htmlUrl()).thenReturn("https://gist.github.com/alice/abc123");
        when(gist.owner()).thenReturn(User.builder().login("alice").build());
        set(activity, "mGist", gist);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
    }

    @Test
    public void onOptionsItemSelected_shareWithNullHtmlUrl_returnsTrueWithoutStarting() {
        GistActivity activity = launch(GistActivity.class,
                GistActivity.makeIntent(appContext(), "abc123"));
        Gist gist = mock(Gist.class);
        when(gist.htmlUrl()).thenReturn(null);
        set(activity, "mGist", gist);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void updateStarringState_starred_unstarsAndFlipsState() throws Exception {
        GistService svc = mock(GistService.class);
        when(svc.getGist("abc123")).thenReturn(Single.just(Response.success(mock(Gist.class))));
        when(svc.checkIfGistIsStarred("abc123"))
                .thenReturn(Single.just(Response.success((Void) null)));
        when(svc.unstarGist("abc123"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistActivity activity = launch(GistActivity.class,
                    GistActivity.makeIntent(appContext(), "abc123"));
            waitFor(() -> get(activity, "mIsStarred") != null, 10000);
            set(activity, "mIsStarred", Boolean.TRUE);
            callUpdateStarringState(activity);
            waitFor(() -> Boolean.FALSE.equals(get(activity, "mIsStarred")), 10000);
            verify(svc).unstarGist("abc123");
        }
    }

    @Test
    public void updateStarringState_unstarred_starsAndFlipsState() throws Exception {
        GistService svc = mock(GistService.class);
        when(svc.getGist("abc123")).thenReturn(Single.just(Response.success(mock(Gist.class))));
        when(svc.checkIfGistIsStarred("abc123"))
                .thenReturn(Single.just(Response.success((Void) null)));
        when(svc.starGist("abc123"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistActivity activity = launch(GistActivity.class,
                    GistActivity.makeIntent(appContext(), "abc123"));
            waitFor(() -> get(activity, "mIsStarred") != null, 10000);
            set(activity, "mIsStarred", Boolean.FALSE);
            callUpdateStarringState(activity);
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mIsStarred")), 10000);
            verify(svc).starGist("abc123");
        }
    }

    @Test
    public void updateStarringState_error_leavesStateUnchanged() throws Exception {
        GistService svc = mock(GistService.class);
        when(svc.getGist("abc123")).thenReturn(Single.just(Response.success(mock(Gist.class))));
        when(svc.checkIfGistIsStarred("abc123"))
                .thenReturn(Single.just(Response.success((Void) null)));
        when(svc.starGist("abc123"))
                .thenReturn(Single.error(new IOException("boom")));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistActivity activity = launch(GistActivity.class,
                    GistActivity.makeIntent(appContext(), "abc123"));
            waitFor(() -> get(activity, "mIsStarred") != null, 10000);
            set(activity, "mIsStarred", Boolean.FALSE);
            callUpdateStarringState(activity);
            // let the background error callback land; state must stay false
            Thread.sleep(1500);
            com.gh4a.activities.ActivityTestHelper.idleMain(500);
            assertEquals(Boolean.FALSE, get(activity, "mIsStarred"));
            verify(svc).starGist("abc123");
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesToSuper() {
        GistActivity activity = launch(GistActivity.class,
                GistActivity.makeIntent(appContext(), "abc123"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 123456, 0, "unknown")));
    }

    // Launch with a stubbed GistService whose Singles never emit, so the
    // onCreate loaders start but no callback ever lands: mIsStarred stays
    // null and no real-network loader thread runs. The static mock only
    // needs to be open during launch(): ServiceFactory.get is called
    // synchronously from onCreate, the service object is captured there.
    private GistActivity launchWithMockedService() {
        GistService svc = mock(GistService.class);
        when(svc.getGist("abc123")).thenReturn(Single.never());
        when(svc.checkIfGistIsStarred("abc123")).thenReturn(Single.never());
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            return launch(GistActivity.class,
                    GistActivity.makeIntent(appContext(), "abc123"));
        }
    }

    private void ensureAuthorized() {
        if (!Gh4Application.get().isAuthorized()) {
            authorize();
        }
    }

    private void authorize() {
        Gh4Application.get().addAccount(User.builder().login("tester").id(1L).build(), "tok");
    }

    private Gist gistWithFile() {
        Gist gist = mock(Gist.class);
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("hello.txt");
        Map<String, GistFile> files = new HashMap<>();
        files.put("hello.txt", file);
        when(gist.owner()).thenReturn(User.builder().login("alice").build());
        when(gist.description()).thenReturn("my gist");
        when(gist.createdAt()).thenReturn(null);
        when(gist.files()).thenReturn(files);
        when(gist.isPublic()).thenReturn(true);
        return gist;
    }

    private void callUpdateStarringState(GistActivity activity) {
        try {
            java.lang.reflect.Method m =
                    GistActivity.class.getDeclaredMethod("updateStarringState");
            m.setAccessible(true);
            m.invoke(activity);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void callFillData(GistActivity activity, Gist gist) {
        try {
            java.lang.reflect.Method m = GistActivity.class.getDeclaredMethod("fillData", Gist.class);
            m.setAccessible(true);
            m.invoke(activity, gist);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
