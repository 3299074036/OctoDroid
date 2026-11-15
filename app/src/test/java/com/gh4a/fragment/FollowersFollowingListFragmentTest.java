package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.mockito.MockedStatic;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

import com.meisolsson.githubsdk.service.users.UserFollowerService;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

/**
 * FollowersFollowingListFragment (F1).
 * Note: onCreate() with showFollowers=true calls loadFollowingState(), which
 * returns immediately while unauthorized, so attach stays offline.
 * The authorized follow-state flow (onRefresh/toggleFollowingState) is
 * tested with a mocked UserFollowerService; no real requests are made.
 */
@RunWith(RobolectricTestRunner.class)
public class FollowersFollowingListFragmentTest {

    public static class NoLoad extends FollowersFollowingListFragment {
        @Override
        protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
            return false;
        }
    }

    private static NoLoad attached(String login, boolean showFollowers) {
        NoLoad f = new NoLoad();
        f.setArguments(FollowersFollowingListFragment.newInstance(login, showFollowers)
                .getArguments());
        FragmentTestHelper.attach(f);
        return f;
    }

    @Test
    public void newInstance_storesLoginAndFlag() {
        FollowersFollowingListFragment f =
                FollowersFollowingListFragment.newInstance("octocat", true);
        assertEquals("octocat", f.getArguments().getString("user"));
        assertTrue(f.getArguments().getBoolean("show_followers"));
        FollowersFollowingListFragment g =
                FollowersFollowingListFragment.newInstance("octocat", false);
        assertFalse(g.getArguments().getBoolean("show_followers"));
    }

    @Test
    public void getEmptyTextResId_followersVsFollowing() {
        assertEquals(R.string.no_followers_found,
                attached("octocat", true).getEmptyTextResId());
        assertEquals(R.string.no_following_found,
                attached("octocat", false).getEmptyTextResId());
    }

    @Test
    public void loadPage_followers_returnsNonNullSingleWithoutNetwork() {
        assertNotNull(attached("octocat", true).loadPage(1, false));
    }

    @Test
    public void loadPage_following_returnsNonNullSingleWithoutNetwork() {
        NoLoad f = attached("octocat", false);
        Single<Response<Page<User>>> single = f.loadPage(1, false);
        assertNotNull(single);
        assertNotNull(f.loadPage(2, true));
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached("octocat", true).onCreateAdapter());
        assertNotNull(attached("octocat", false).onCreateAdapter());
    }

    @Test
    public void onItemClick_startsUserActivity() {
        NoLoad f = attached("octocat", false);
        FragmentActivity activity = f.getActivity();
        f.onItemClick(User.builder().login("octocat").build());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octocat", started.getExtras().getString("login"));
    }

    @Test
    public void onItemClick_mannequinStartsNothing() {
        NoLoad f = attached("octocat", false);
        FragmentActivity activity = f.getActivity();
        User mannequin = User.builder().login("ghost")
                .type(com.meisolsson.githubsdk.model.UserType.Mannequin).build();
        f.onItemClick(mannequin);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onCreateOptionsMenu_notFollowing_addsNothing() {
        NoLoad f = attached("octocat", false);
        Menu menu = new MenuBuilder(f.getActivity());
        f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
        assertEquals(0, menu.size());
    }

    @Test
    public void onOptionsItemSelected_otherItem_returnsFalse() {
        NoLoad f = attached("octocat", false);
        android.view.MenuItem item =
                org.mockito.Mockito.mock(android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId()).thenReturn(12345);
        assertFalse(f.onOptionsItemSelected(item));
    }

    @Test
    public void onOptionsItemSelected_followItemWhileLoading_returnsTrue() {
        // mIsFollowing == null while the follow state loads: click is ignored
        // but the item is consumed.
        NoLoad f = attached("octocat", true);
        android.view.MenuItem item =
                org.mockito.Mockito.mock(android.view.MenuItem.class);
        org.mockito.Mockito.when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
        assertTrue(f.onOptionsItemSelected(item));
    }

    // ---- loadPage with a mocked UserFollowerService ----

    @SuppressWarnings("unchecked")
    private static Page<User> userPage(User... users) {
        Page<User> p = mock(Page.class);
        when(p.items()).thenReturn(Arrays.asList(users));
        when(p.next()).thenReturn(null);
        return p;
    }

    @Test
    public void loadPage_followers_returnsServicePage() {
        NoLoad f = attached("octocat", true);
        User u1 = User.builder().login("follower1").build();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            UserFollowerService service = mock(UserFollowerService.class);
            mocked.when(() -> ServiceFactory.get(UserFollowerService.class, false))
                    .thenReturn(service);
            Page<User> page = userPage(u1);
            when(service.getFollowers("octocat", 1L))
                    .thenReturn(Single.just(Response.success(page)));
            f.loadPage(1, false).test()
                    .assertValue(response -> response.body() == page)
                    .assertComplete();
            verify(service).getFollowers("octocat", 1L);
        }
    }

    @Test
    public void loadPage_following_returnsServicePage() {
        NoLoad f = attached("octocat", false);
        User u1 = User.builder().login("following1").build();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            UserFollowerService service = mock(UserFollowerService.class);
            mocked.when(() -> ServiceFactory.get(UserFollowerService.class, false))
                    .thenReturn(service);
            Page<User> page = userPage(u1);
            when(service.getFollowing("octocat", 1L))
                    .thenReturn(Single.just(Response.success(page)));
            f.loadPage(1, false).test()
                    .assertValue(response -> response.body() == page)
                    .assertComplete();
            verify(service).getFollowing("octocat", 1L);
        }
    }

    // ---- follow-state flow (authorized; mocked UserFollowerService) ----

    @After
    public void logoutIfAuthorized() {
        if (Gh4Application.get().isAuthorized()) {
            Gh4Application.get().logout();
        }
    }

    private static void authorize() {
        Gh4Application.get().addAccount(
                User.builder().login("octocat").id(1L).build(), "fake-token");
    }

    private static MockedStatic<ServiceFactory> mockService(UserFollowerService service) {
        MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> ServiceFactory.get(eq(UserFollowerService.class), anyBoolean()))
                .thenReturn(service);
        return mocked;
    }

    private static void stubFollowing(UserFollowerService service, boolean following) {
        when(service.isFollowing("octocat")).thenReturn(following
                ? Single.just(Response.success((Void) null))
                : Single.just(Response.error(404, ResponseBody.create(null, ""))));
    }

    private static Field declaredField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new RuntimeException(new NoSuchFieldException(name));
    }

    private static Object get(Object target, String name) {
        try {
            return declaredField(target.getClass(), name).get(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            declaredField(target.getClass(), name).set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void callToggle(FollowersFollowingListFragment f) {
        try {
            Method m = FollowersFollowingListFragment.class
                    .getDeclaredMethod("toggleFollowingState");
            m.setAccessible(true);
            m.invoke(f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static MenuItem followMenuItem() {
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(Menu.FIRST);
        return item;
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
            ShadowLooper.idleMainLooper();
        }
        assertTrue("condition not met within " + timeoutMs + "ms", condition.getAsBoolean());
    }

    @Test
    public void loadFollowingState_authorized_deliversTrue() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            verify(service).isFollowing("octocat");
        }
    }

    @Test
    public void loadFollowingState_authorized_notFollowing_deliversFalse() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, false);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.FALSE.equals(get(f, "mIsFollowing")), 5000);
            verify(service).isFollowing("octocat");
        }
    }

    @Test
    public void loadFollowingState_authorized_error_keepsNull() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        when(service.isFollowing("octocat"))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            ShadowLooper.idleMainLooper();
            // The loader delivered the error to handleLoadFailure (a no-op
            // with a plain FragmentActivity host); the state stays null.
            assertNull(get(f, "mIsFollowing"));
            verify(service).isFollowing("octocat");
        }
    }

    @Test
    public void onRefresh_authorizedFollowers_reloadsState() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        Page<User> page = userPage();
        when(service.getFollowers(eq("octocat"), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            f.onRefresh();
            // onRefresh clears the state first, then reloads it (force=true).
            assertNull(get(f, "mIsFollowing"));
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            verify(service, Mockito.times(2)).isFollowing("octocat");
        }
    }

    @Test
    public void onRefresh_notFollowers_skipsStateReload() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        Page<User> page = userPage();
        when(service.getFollowing(eq("octocat"), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", false);
            set(f, "mIsFollowing", Boolean.TRUE);
            f.onRefresh();
            // showFollowers is false: loadFollowingState is skipped, so the
            // state is only cleared, never reloaded.
            assertNull(get(f, "mIsFollowing"));
            verify(service, Mockito.never()).isFollowing("octocat");
        }
    }

    @Test
    public void onCreateOptionsMenu_unauthorizedFollowers_addsNothing() {
        NoLoad f = attached("octocat", true);
        Menu menu = new MenuBuilder(f.getActivity());
        f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
        assertEquals(0, menu.size());
    }

    @Test
    public void onCreateOptionsMenu_authorized_loading_showsLoadingView() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            set(f, "mIsFollowing", null);
            Menu menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
            assertEquals(1, menu.size());
            assertNotNull(menu.getItem(0).getActionView());
        }
    }

    @Test
    public void onCreateOptionsMenu_authorized_following_showsUnfollowTitle() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            set(f, "mIsFollowing", Boolean.TRUE);
            Menu menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
            assertEquals(1, menu.size());
            assertEquals(f.getActivity().getString(R.string.user_unfollow_action),
                    menu.getItem(0).getTitle().toString());
        }
    }

    @Test
    public void onCreateOptionsMenu_authorized_notFollowing_showsFollowTitle() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            set(f, "mIsFollowing", Boolean.FALSE);
            Menu menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
            assertEquals(1, menu.size());
            assertEquals(f.getActivity().getString(R.string.user_follow_action),
                    menu.getItem(0).getTitle().toString());
        }
    }

    @Test
    public void onCreateOptionsMenu_inFlight_disablesItem() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            set(f, "mIsFollowing", Boolean.FALSE);
            set(f, "mFollowingInFlight", true);
            Menu menu = new MenuBuilder(f.getActivity());
            f.onCreateOptionsMenu(menu, f.getActivity().getMenuInflater());
            assertEquals(1, menu.size());
            assertFalse(menu.getItem(0).isEnabled());
        }
    }

    @Test
    public void toggle_unfollow_success_flipsToNotFollowing() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        when(service.unfollowUser("octocat"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            waitUntil(() -> !((Boolean) get(f, "mFollowingInFlight")), 5000);
            assertEquals(Boolean.FALSE, get(f, "mIsFollowing"));
            verify(service).unfollowUser("octocat");
        }
    }

    @Test
    public void toggle_follow_success_flipsToFollowing() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, false);
        when(service.followUser("octocat"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.FALSE.equals(get(f, "mIsFollowing")), 5000);
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            waitUntil(() -> !((Boolean) get(f, "mFollowingInFlight")), 5000);
            assertEquals(Boolean.TRUE, get(f, "mIsFollowing"));
            verify(service).followUser("octocat");
        }
    }

    @Test
    public void toggle_unfollow_error_resetsInFlightKeepsState() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        when(service.unfollowUser("octocat"))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            waitUntil(() -> !((Boolean) get(f, "mFollowingInFlight")), 5000);
            assertEquals(Boolean.TRUE, get(f, "mIsFollowing"));
            verify(service).unfollowUser("octocat");
        }
    }

    @Test
    public void toggle_inFlight_ignored() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            set(f, "mFollowingInFlight", true);
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            verify(service, Mockito.never()).unfollowUser("octocat");
            verify(service, Mockito.never()).followUser("octocat");
            assertTrue((Boolean) get(f, "mFollowingInFlight"));
        }
    }

    @Test
    public void toggle_nullIsFollowing_returnsEarly() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            set(f, "mIsFollowing", null);
            callToggle(f);
            verify(service, Mockito.never()).unfollowUser("octocat");
            verify(service, Mockito.never()).followUser("octocat");
            assertFalse((Boolean) get(f, "mFollowingInFlight"));
        }
    }

    @Test
    public void toggle_success_detached_returnsEarly() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        when(service.unfollowUser("octocat"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            FragmentActivity activity = f.getActivity();
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            assertTrue((Boolean) get(f, "mFollowingInFlight"));
            activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
            assertFalse(f.isAdded());
            waitUntil(() -> !((Boolean) get(f, "mFollowingInFlight")), 5000);
            assertEquals(Boolean.TRUE, get(f, "mIsFollowing"));
            verify(service).unfollowUser("octocat");
        }
    }

    @Test
    public void toggle_success_isFollowingNulledMidFlight_skipsFlip() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        when(service.unfollowUser("octocat"))
                .thenReturn(Single.just(Response.success((Void) null)));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            // Null the state before the background delivery runs: the
            // success callback skips the flip (line 170 false arm).
            set(f, "mIsFollowing", null);
            waitUntil(() -> !((Boolean) get(f, "mFollowingInFlight")), 5000);
            assertNull(get(f, "mIsFollowing"));
            verify(service).unfollowUser("octocat");
        }
    }

    @Test
    public void toggle_error_detached_skipsInvalidate() {
        authorize();
        UserFollowerService service = mock(UserFollowerService.class);
        stubFollowing(service, true);
        when(service.unfollowUser("octocat"))
                .thenReturn(Single.error(new RuntimeException("boom")));
        try (MockedStatic<ServiceFactory> mocked = mockService(service)) {
            NoLoad f = attached("octocat", true);
            waitUntil(() -> Boolean.TRUE.equals(get(f, "mIsFollowing")), 5000);
            FragmentActivity activity = f.getActivity();
            assertTrue(f.onOptionsItemSelected(followMenuItem()));
            activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
            assertFalse(f.isAdded());
            waitUntil(() -> !((Boolean) get(f, "mFollowingInFlight")), 5000);
            assertEquals(Boolean.TRUE, get(f, "mIsFollowing"));
        }
    }
}
