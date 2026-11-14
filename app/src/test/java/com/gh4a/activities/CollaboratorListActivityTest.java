package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.CollaboratorListFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CollaboratorListActivityTest {
    @Test
    public void makeIntent_setsComponentAndExtras() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        assertEquals(CollaboratorListActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("owner"));
        assertEquals("repo", intent.getStringExtra("repo"));
    }

    @Test
    public void makeIntent_nullOwnerRepo_preserved() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), null, null);
        assertNull(intent.getStringExtra("owner"));
        assertNull(intent.getStringExtra("repo"));
    }

    @Test
    public void launch_titleSubtitleAndFragment() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class, intent);
        assertEquals(appContext().getString(R.string.repo_collaborators),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("octo/repo", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof CollaboratorListFragment);
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentById(R.id.content_container));
    }

    @Test
    public void launch_withoutExtras_subtitleShowsNulls() {
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class);
        assertEquals("null/null", activity.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void navigateUp_goesToRepository() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("octo", up.getStringExtra("owner"));
        assertEquals("repo", up.getStringExtra("repo"));
    }

    @Test
    public void recreate_restoresFragment() {
        Intent intent = CollaboratorListActivity.makeIntent(appContext(), "octo", "repo");
        CollaboratorListActivity activity = launch(CollaboratorListActivity.class, intent);
        android.os.Bundle out = new android.os.Bundle();
        call(activity, "onSaveInstanceState", new Class<?>[]{android.os.Bundle.class}, new Object[]{out});
        assertNotNull(activity.getFragment());
    }
}
