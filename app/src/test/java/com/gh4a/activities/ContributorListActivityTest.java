package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.ContributorListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ContributorListActivityTest {
    @Test
    public void makeIntent_setsComponentAndExtras() {
        Intent intent = ContributorListActivity.makeIntent(appContext(), "octo", "repo");
        assertEquals(ContributorListActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("owner"));
        assertEquals("repo", intent.getStringExtra("repo"));
    }

    @Test
    public void launch_titleSubtitleAndFragment() {
        Intent intent = ContributorListActivity.makeIntent(appContext(), "octo", "repo");
        ContributorListActivity activity = launch(ContributorListActivity.class, intent);
        assertEquals(appContext().getString(R.string.repo_contributors),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("octo/repo", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof ContributorListFragment);
    }

    @Test
    public void navigateUp_goesToRepository() {
        Intent intent = ContributorListActivity.makeIntent(appContext(), "octo", "repo");
        ContributorListActivity activity = launch(ContributorListActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("octo", up.getStringExtra("owner"));
        assertEquals("repo", up.getStringExtra("repo"));
    }
}
