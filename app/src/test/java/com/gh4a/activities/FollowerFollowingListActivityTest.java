package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.FollowersFollowingListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FollowerFollowingListActivityTest {
    @Test
    public void makeIntent_followers_setsExtras() {
        Intent intent = FollowerFollowingListActivity.makeIntent(appContext(), "alice", true);
        assertEquals(FollowerFollowingListActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("alice", intent.getStringExtra("user"));
        assertTrue(intent.getBooleanExtra("show_followers", false));
    }

    @Test
    public void makeIntent_following_setsExtras() {
        Intent intent = FollowerFollowingListActivity.makeIntent(appContext(), "bob", false);
        assertEquals("bob", intent.getStringExtra("user"));
        assertFalse(intent.getBooleanExtra("show_followers", true));
    }

    @Test
    public void launch_followers_titleAndFragment() {
        Intent intent = FollowerFollowingListActivity.makeIntent(appContext(), "alice", true);
        FollowerFollowingListActivity activity = launch(FollowerFollowingListActivity.class, intent);
        assertEquals(appContext().getString(R.string.user_followers),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("alice", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof FollowersFollowingListFragment);
    }

    @Test
    public void launch_following_title() {
        Intent intent = FollowerFollowingListActivity.makeIntent(appContext(), "bob", false);
        FollowerFollowingListActivity activity = launch(FollowerFollowingListActivity.class, intent);
        assertEquals(appContext().getString(R.string.user_following),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("bob", activity.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void navigateUp_goesToUser() {
        Intent intent = FollowerFollowingListActivity.makeIntent(appContext(), "alice", true);
        FollowerFollowingListActivity activity = launch(FollowerFollowingListActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(UserActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("alice", up.getStringExtra("login"));
    }
}
