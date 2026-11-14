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

@RunWith(RobolectricTestRunner.class)
public class WatcherListActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void makeIntent_carriesOwnerAndRepo() {
        Intent intent = WatcherListActivity.makeIntent(context(), "octocat", "hello");
        assertEquals(WatcherListActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("owner"));
        assertEquals("hello", intent.getStringExtra("repo"));
    }

    @Test
    public void launches_titleIsOwnerSlashRepo() {
        Intent intent = WatcherListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<WatcherListActivity> c =
                Robolectric.buildActivity(WatcherListActivity.class, intent)) {
            WatcherListActivity activity = c.setup().get();
            assertEquals("octocat/hello",
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isWatcherListFragmentWithArgs() {
        Intent intent = WatcherListActivity.makeIntent(context(), "octocat", "hello");
        try (ActivityController<WatcherListActivity> c =
                Robolectric.buildActivity(WatcherListActivity.class, intent)) {
            WatcherListActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.WatcherListFragment);
            android.os.Bundle args = activity.getFragment().getArguments();
            assertNotNull(args);
            assertEquals("octocat", args.getString("owner"));
            assertEquals("hello", args.getString("repo"));
        }
    }
}
