package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.NotificationListFragment;

@RunWith(RobolectricTestRunner.class)
public class NotificationListFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NotificationListFactory factory = new NotificationListFactory(activity);
            assertEquals(R.string.notifications, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.notifications, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsNotificationListFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NotificationListFactory factory = new NotificationListFactory(activity);
            assertTrue(factory.makeFragment(0) instanceof NotificationListFragment);
        }
    }
}
