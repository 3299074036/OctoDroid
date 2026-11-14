package com.gh4a.resolver;

import android.content.Intent;
import android.net.Uri;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.activities.FollowerFollowingListActivity;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class UserFollowersLoadTaskTest {
    private static final Uri URL = Uri.parse("https://github.com/octocat?tab=followers");

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private User user(UserType type, String login) {
        User u = mock(User.class);
        when(u.type()).thenReturn(type);
        when(u.login()).thenReturn(login);
        return u;
    }

    @Test
    public void constructor_storesShowFollowers() {
        FragmentActivity activity = activity();
        UserFollowersLoadTask followers = new UserFollowersLoadTask(activity, URL, "octo", true);
        assertTrue(followers.mShowFollowers);
        assertEquals("octo", followers.mUserLogin);
        UserFollowersLoadTask following = new UserFollowersLoadTask(activity, URL, "octo", false);
        assertFalse(following.mShowFollowers);
    }

    @Test
    public void getIntent_organizationUser_opensUserActivity() {
        FragmentActivity activity = activity();
        UserFollowersLoadTask task = new UserFollowersLoadTask(activity, URL, "someorg", true);
        Intent intent = task.getIntent(user(UserType.Organization, "someorg"));
        assertEquals(UserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("someorg", intent.getStringExtra("login"));
    }

    @Test
    public void getIntent_regularUserFollowers_opensFollowerList() {
        FragmentActivity activity = activity();
        UserFollowersLoadTask task = new UserFollowersLoadTask(activity, URL, "octo", true);
        Intent intent = task.getIntent(user(UserType.User, "octo"));
        assertEquals(FollowerFollowingListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("user"));
        assertTrue(intent.getBooleanExtra("show_followers", false));
    }

    @Test
    public void getIntent_regularUserFollowing_opensFollowingList() {
        FragmentActivity activity = activity();
        UserFollowersLoadTask task = new UserFollowersLoadTask(activity, URL, "octo", false);
        Intent intent = task.getIntent(user(UserType.User, "octo"));
        assertEquals(FollowerFollowingListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("user"));
        assertFalse(intent.getBooleanExtra("show_followers", true));
    }

    @Test
    public void getIntent_nullType_treatedAsRegularUser() {
        FragmentActivity activity = activity();
        UserFollowersLoadTask task = new UserFollowersLoadTask(activity, URL, "octo", true);
        Intent intent = task.getIntent(user(null, "octo"));
        assertEquals(FollowerFollowingListActivity.class.getName(),
                intent.getComponent().getClassName());
    }
}
