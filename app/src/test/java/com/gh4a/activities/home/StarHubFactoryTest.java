package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.StarMergedFragment;

@RunWith(RobolectricTestRunner.class)
public class StarHubFactoryTest {

    @Test
    public void tabRadarConstant_isOne() {
        assertEquals(1, StarHubFactory.TAB_RADAR);
    }

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            StarHubFactory factory = new StarHubFactory(activity, "octocat",
                    ApplicationProvider.getApplicationContext().getSharedPreferences(
                            "test-star-prefs", Context.MODE_PRIVATE));
            assertEquals(R.string.star_hub, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.star_hub, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsStarMergedFragmentWithLogin() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            StarHubFactory factory = new StarHubFactory(activity, "octocat",
                    ApplicationProvider.getApplicationContext().getSharedPreferences(
                            "test-star-prefs", Context.MODE_PRIVATE));
            assertTrue(factory.makeFragment(0) instanceof StarMergedFragment);
            assertTrue(factory.makeFragment(StarHubFactory.TAB_RADAR)
                    instanceof StarMergedFragment);
        }
    }
}
