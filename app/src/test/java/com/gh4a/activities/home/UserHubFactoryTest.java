package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.MeFragment;

@RunWith(RobolectricTestRunner.class)
public class UserHubFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            UserHubFactory factory = new UserHubFactory(activity, "octocat");
            assertEquals(R.string.my_profile, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.my_profile, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsMeFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            UserHubFactory factory = new UserHubFactory(activity, "octocat");
            assertTrue(factory.makeFragment(0) instanceof MeFragment);
        }
    }
}
