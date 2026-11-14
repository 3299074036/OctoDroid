package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.TopicDiscoveryFragment;

@RunWith(RobolectricTestRunner.class)
public class TopicDiscoveryFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TopicDiscoveryFactory factory = new TopicDiscoveryFactory(activity);
            assertEquals(R.string.topic_discovery, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.topic_discovery, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsTopicDiscoveryFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TopicDiscoveryFactory factory = new TopicDiscoveryFactory(activity);
            assertTrue(factory.makeFragment(0) instanceof TopicDiscoveryFragment);
        }
    }
}
