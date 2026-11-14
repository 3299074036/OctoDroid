package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.GistListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class GistListActivityTest {
    @Test
    public void makeIntent_setsComponentAndExtras() {
        Intent intent = GistListActivity.makeIntent(appContext(), "alice");
        assertEquals(GistListActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("alice", intent.getStringExtra("user"));
    }

    @Test
    public void launch_titleSubtitleAndFragment() {
        Intent intent = GistListActivity.makeIntent(appContext(), "alice");
        GistListActivity activity = launch(GistListActivity.class, intent);
        assertEquals(appContext().getString(R.string.gists),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("alice", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof GistListFragment);
    }

    @Test
    public void navigateUp_goesToUser() {
        Intent intent = GistListActivity.makeIntent(appContext(), "alice");
        GistListActivity activity = launch(GistListActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(UserActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("alice", up.getStringExtra("login"));
    }
}
