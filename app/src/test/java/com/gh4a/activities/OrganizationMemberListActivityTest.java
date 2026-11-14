package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.OrganizationMemberListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class OrganizationMemberListActivityTest {
    @Test
    public void makeIntent_setsComponentAndExtras() {
        Intent intent = OrganizationMemberListActivity.makeIntent(appContext(), "myorg");
        assertEquals(OrganizationMemberListActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("myorg", intent.getStringExtra("login"));
    }

    @Test
    public void launch_titleSubtitleAndFragment() {
        Intent intent = OrganizationMemberListActivity.makeIntent(appContext(), "myorg");
        OrganizationMemberListActivity activity = launch(OrganizationMemberListActivity.class, intent);
        assertEquals(appContext().getString(R.string.members),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("myorg", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof OrganizationMemberListFragment);
    }

    @Test
    public void navigateUp_goesToUser() {
        Intent intent = OrganizationMemberListActivity.makeIntent(appContext(), "myorg");
        OrganizationMemberListActivity activity = launch(OrganizationMemberListActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(UserActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("myorg", up.getStringExtra("login"));
    }
}
