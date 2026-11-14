package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.TrendingFragment;

@RunWith(RobolectricTestRunner.class)
public class DiscoverHubFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            DiscoverHubFactory factory = new DiscoverHubFactory(activity);
            assertEquals(R.string.discover_hub, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.trend, tabs[0]);
        }
    }

    @Test
    public void makeFragment_delegatesToTrendingFactory() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            DiscoverHubFactory factory = new DiscoverHubFactory(activity);
            assertTrue(factory.makeFragment(0) instanceof TrendingFragment);
            assertEquals(TrendingFragment.TYPE_DAILY,
                    factory.makeFragment(0).getArguments().getString("type"));
        }
    }

    @Test
    public void setTrendRange_changesDelegatedFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            DiscoverHubFactory factory = new DiscoverHubFactory(activity);
            factory.setTrendRange(TrendingFactory.RANGE_WEEKLY);
            assertEquals(TrendingFragment.TYPE_WEEKLY,
                    factory.makeFragment(0).getArguments().getString("type"));
        }
    }

    @Test
    public void saveAndRestore_passthroughToTrendingFactory() {
        // 190：DiscoverHubFactory 透传保存/恢复，mRange 随旋转不丢失。
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            DiscoverHubFactory factory = new DiscoverHubFactory(activity);
            factory.setTrendRange(TrendingFactory.RANGE_MONTHLY);
            Bundle out = new Bundle();
            factory.onSaveInstanceState(out);
            assertEquals(TrendingFactory.RANGE_MONTHLY,
                    out.getInt("trending:range", -1));
            DiscoverHubFactory restored = new DiscoverHubFactory(activity);
            restored.onRestoreInstanceState(out);
            assertEquals(TrendingFragment.TYPE_MONTHLY,
                    restored.makeFragment(0).getArguments().getString("type"));
        }
    }
}
