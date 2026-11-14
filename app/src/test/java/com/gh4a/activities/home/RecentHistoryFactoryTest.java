package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.RecentHistoryFragment;

@RunWith(RobolectricTestRunner.class)
public class RecentHistoryFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RecentHistoryFactory factory = new RecentHistoryFactory(activity);
            assertEquals(R.string.recent_history, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.recent_history, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsRecentHistoryFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RecentHistoryFactory factory = new RecentHistoryFactory(activity);
            assertTrue(factory.makeFragment(0) instanceof RecentHistoryFragment);
        }
    }
}
