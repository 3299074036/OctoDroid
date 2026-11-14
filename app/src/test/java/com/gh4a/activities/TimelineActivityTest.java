package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;

@RunWith(RobolectricTestRunner.class)
public class TimelineActivityTest {

    @Test
    public void launches_titleIsPublicTimeline() {
        try (ActivityController<TimelineActivity> c =
                Robolectric.buildActivity(TimelineActivity.class)) {
            TimelineActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.pub_timeline),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isPublicTimelineFragment() {
        try (ActivityController<TimelineActivity> c =
                Robolectric.buildActivity(TimelineActivity.class)) {
            TimelineActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.PublicTimelineFragment);
        }
    }

    @Test
    public void navigateUp_returnsToplevelIntent() {
        try (ActivityController<TimelineActivity> c =
                Robolectric.buildActivity(TimelineActivity.class)) {
            TimelineActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertNotNull(up);
            // 未登录时回落到登录页（Github4AndroidActivity）
            assertEquals(com.gh4a.activities.Github4AndroidActivity.class.getName(),
                    up.getComponent().getClassName());
        }
    }

    @Test
    public void getActionBarTitle_returnsPublicTimelineString() {
        try (ActivityController<TimelineActivity> c =
                Robolectric.buildActivity(TimelineActivity.class)) {
            TimelineActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.pub_timeline),
                    activity.getActionBarTitle());
        }
    }

    @Test
    public void recreate_restoresFragment() {
        try (ActivityController<TimelineActivity> c =
                Robolectric.buildActivity(TimelineActivity.class)) {
            TimelineActivity activity = c.setup().get();
            assertNotNull(activity.getFragment());
            c.recreate();
            TimelineActivity recreated = c.get();
            assertTrue(recreated.getFragment()
                    instanceof com.gh4a.fragment.PublicTimelineFragment);
        }
    }

    @Test
    public void onRefresh_delegatesWithoutCrash() {
        try (ActivityController<TimelineActivity> c =
                Robolectric.buildActivity(TimelineActivity.class)) {
            TimelineActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }
}
