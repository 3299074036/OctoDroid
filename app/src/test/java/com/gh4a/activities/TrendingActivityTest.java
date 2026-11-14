package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.TrendingFragment;

@RunWith(RobolectricTestRunner.class)
public class TrendingActivityTest {

    @Test
    public void launches_titleIsTrend() {
        try (ActivityController<TrendingActivity> c =
                Robolectric.buildActivity(TrendingActivity.class)) {
            TrendingActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.trend),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void getTabTitleResIds_hasThreeTabs() {
        try (ActivityController<TrendingActivity> c =
                Robolectric.buildActivity(TrendingActivity.class)) {
            TrendingActivity activity = c.setup().get();
            int[] titles = activity.getTabTitleResIds();
            assertEquals(3, titles.length);
            assertEquals(R.string.trend_today, titles[0]);
            assertEquals(R.string.trend_week, titles[1]);
            assertEquals(R.string.trend_month, titles[2]);
        }
    }

    @Test
    public void makeFragment_dailyWeeklyMonthly() {
        try (ActivityController<TrendingActivity> c =
                Robolectric.buildActivity(TrendingActivity.class)) {
            TrendingActivity activity = c.setup().get();
            assertTrue(activity.makeFragment(0) instanceof TrendingFragment);
            assertEquals(TrendingFragment.TYPE_DAILY,
                    activity.makeFragment(0).getArguments().getString("type"));
            assertEquals(TrendingFragment.TYPE_WEEKLY,
                    activity.makeFragment(1).getArguments().getString("type"));
            assertEquals(TrendingFragment.TYPE_MONTHLY,
                    activity.makeFragment(2).getArguments().getString("type"));
        }
    }

    @Test
    public void makeFragment_invalidPosition_returnsNull() {
        try (ActivityController<TrendingActivity> c =
                Robolectric.buildActivity(TrendingActivity.class)) {
            TrendingActivity activity = c.setup().get();
            assertNull(activity.makeFragment(3));
            assertNull(activity.makeFragment(-1));
        }
    }

    @Test
    public void navigateUp_returnsToplevelIntent() {
        try (ActivityController<TrendingActivity> c =
                Robolectric.buildActivity(TrendingActivity.class)) {
            TrendingActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertNotNull(up);
            assertEquals(Github4AndroidActivity.class.getName(),
                    up.getComponent().getClassName());
        }
    }
}
