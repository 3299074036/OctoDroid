package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;

@RunWith(RobolectricTestRunner.class)
public class ReleaseListActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void makeIntent_carriesOwnerAndRepo() {
        Intent intent = ReleaseListActivity.makeIntent(context(), "octocat", "hello");
        assertEquals(ReleaseListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
    }

    @Test
    public void launches_titleAndSubtitle() {
        Intent intent = ReleaseListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<ReleaseListActivity> c =
                Robolectric.buildActivity(ReleaseListActivity.class, intent)) {
            ReleaseListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.releases),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isReleaseListFragmentWithArgs() {
        Intent intent = ReleaseListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<ReleaseListActivity> c =
                Robolectric.buildActivity(ReleaseListActivity.class, intent)) {
            ReleaseListActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.ReleaseListFragment);
            android.os.Bundle args = activity.getFragment().getArguments();
            assertNotNull(args);
            assertEquals("octocat", args.getString("owner"));
            assertEquals("hello", args.getString("repo"));
        }
    }

    @Test
    public void navigateUp_returnsRepositoryActivityIntent() {
        Intent intent = ReleaseListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<ReleaseListActivity> c =
                Robolectric.buildActivity(ReleaseListActivity.class, intent)) {
            ReleaseListActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(RepositoryActivity.class.getName(),
                    up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("owner"));
            assertEquals("hello", up.getStringExtra("repo"));
        }
    }
}
