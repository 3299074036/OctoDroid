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

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.mockito.MockedStatic;
import org.robolectric.Shadows;

import java.util.Arrays;

import com.meisolsson.githubsdk.service.users.UserFollowerService;

import io.reactivex.Single;
import retrofit2.Response;

/**
 * FollowersFollowingListFragment (F1).
 * Note: onCreate() with showFollowers=true calls loadFollowingState(), which
 * returns immediately while unauthorized, so attach stays offline.
 * onRefresh()/toggleFollowingState() are NOT tested: they subscribe network
 * Singles (see PagedDataBaseFragment.load) and would attempt real requests.
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
}
