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
public class StargazerListActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void makeIntent_carriesOwnerAndRepo() {
        Intent intent = StargazerListActivity.makeIntent(context(), "octocat", "hello");
        assertEquals(StargazerListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
    }

    @Test
    public void launches_titleAndSubtitle() {
        Intent intent = StargazerListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<StargazerListActivity> c =
                Robolectric.buildActivity(StargazerListActivity.class, intent)) {
            StargazerListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.repo_stargazers),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isStargazerListFragment() {
        Intent intent = StargazerListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<StargazerListActivity> c =
                Robolectric.buildActivity(StargazerListActivity.class, intent)) {
            StargazerListActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.StargazerListFragment);
        }
    }

    @Test
    public void onCreateFragment_fragmentArgsCarryOwnerAndRepo() {
        Intent intent = StargazerListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<StargazerListActivity> c =
                Robolectric.buildActivity(StargazerListActivity.class, intent)) {
            StargazerListActivity activity = c.setup().get();
            android.os.Bundle args = activity.getFragment().getArguments();
            assertNotNull(args);
            assertEquals("octocat", args.getString("owner"));
            assertEquals("hello", args.getString("repo"));
        }
    }
}
