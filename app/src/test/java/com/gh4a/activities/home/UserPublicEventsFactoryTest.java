package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.PublicEventListFragment;

@RunWith(RobolectricTestRunner.class)
public class UserPublicEventsFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            UserPublicEventsFactory factory = new UserPublicEventsFactory(activity, "octocat");
            assertEquals(R.string.feed_hub, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.feed_hub, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsPublicEventListFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            UserPublicEventsFactory factory = new UserPublicEventsFactory(activity, "octocat");
            assertTrue(factory.makeFragment(0) instanceof PublicEventListFragment);
        }
    }
}
