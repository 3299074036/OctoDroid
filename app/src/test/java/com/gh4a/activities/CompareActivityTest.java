package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.fragment.CommitCompareFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CompareActivityTest {
    @Test
    public void makeIntent_setsComponentAndExtras() {
        Intent intent = CompareActivity.makeIntent(appContext(), "octo", "repo", "v1", "v2");
        assertEquals(CompareActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octo", intent.getStringExtra("owner"));
        assertEquals("repo", intent.getStringExtra("repo"));
        assertEquals("v1", intent.getStringExtra("base"));
        assertEquals("v2", intent.getStringExtra("head"));
    }

    @Test
    public void launch_titleSubtitleAndFragment() {
        Intent intent = CompareActivity.makeIntent(appContext(), "octo", "repo", "v1", "v2");
        CompareActivity activity = launch(CompareActivity.class, intent);
        assertEquals(appContext().getString(R.string.commit_compare),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("octo/repo", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.getFragment() instanceof CommitCompareFragment);
    }

    @Test
    public void navigateUp_goesToRepository() {
        Intent intent = CompareActivity.makeIntent(appContext(), "octo", "repo", "v1", "v2");
        CompareActivity activity = launch(CompareActivity.class, intent);
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("octo", up.getStringExtra("owner"));
        assertEquals("repo", up.getStringExtra("repo"));
    }
}
