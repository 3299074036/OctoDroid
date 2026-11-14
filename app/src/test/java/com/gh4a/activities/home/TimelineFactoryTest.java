package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.PublicTimelineFragment;

@RunWith(RobolectricTestRunner.class)
public class TimelineFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TimelineFactory factory = new TimelineFactory(activity);
            assertEquals(R.string.pub_timeline, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.pub_timeline, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsPublicTimelineFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TimelineFactory factory = new TimelineFactory(activity);
            assertTrue(factory.makeFragment(0) instanceof PublicTimelineFragment);
            assertTrue(factory.makeFragment(5) instanceof PublicTimelineFragment);
        }
    }
}
