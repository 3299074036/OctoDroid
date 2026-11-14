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
public class TrendingFactoryTest {

    @Test
    public void rangeConstants_areOrdered() {
        assertEquals(0, TrendingFactory.RANGE_DAILY);
        assertEquals(1, TrendingFactory.RANGE_WEEKLY);
        assertEquals(2, TrendingFactory.RANGE_MONTHLY);
    }

    @Test
    public void defaultRange_isDaily() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            assertEquals(TrendingFactory.RANGE_DAILY, factory.getRange());
        }
    }

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            assertEquals(R.string.trend, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.trend, tabs[0]);
        }
    }

    @Test
    public void makeFragment_perRange() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            assertEquals(TrendingFragment.TYPE_DAILY,
                    ((TrendingFragment) factory.makeFragment(0)).getArguments()
                            .getString("type"));
            factory.setRange(TrendingFactory.RANGE_WEEKLY);
            assertEquals(TrendingFragment.TYPE_WEEKLY,
                    ((TrendingFragment) factory.makeFragment(0)).getArguments()
                            .getString("type"));
            factory.setRange(TrendingFactory.RANGE_MONTHLY);
            assertEquals(TrendingFragment.TYPE_MONTHLY,
                    ((TrendingFragment) factory.makeFragment(0)).getArguments()
                            .getString("type"));
            // 未知值回落日榜
            factory.setRange(99);
            assertEquals(TrendingFragment.TYPE_DAILY,
                    ((TrendingFragment) factory.makeFragment(0)).getArguments()
                            .getString("type"));
        }
    }

    @Test
    public void saveAndRestore_roundTripsRange() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            factory.setRange(TrendingFactory.RANGE_MONTHLY);
            Bundle out = new Bundle();
            factory.onSaveInstanceState(out);
            TrendingFactory restored = new TrendingFactory(activity);
            restored.onRestoreInstanceState(out);
            assertEquals(TrendingFactory.RANGE_MONTHLY, restored.getRange());
        }
    }

    @Test
    public void restore_outOfRangeValue_clampedToDaily() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            factory.setRange(TrendingFactory.RANGE_WEEKLY);
            Bundle out = new Bundle();
            out.putInt("trending:range", 99);
            factory.onRestoreInstanceState(out);
            assertEquals(TrendingFactory.RANGE_DAILY, factory.getRange());
            Bundle out2 = new Bundle();
            out2.putInt("trending:range", -1);
            factory.onRestoreInstanceState(out2);
            assertEquals(TrendingFactory.RANGE_DAILY, factory.getRange());
        }
    }

    @Test
    public void restore_missingKey_defaultsToDaily() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            factory.setRange(TrendingFactory.RANGE_MONTHLY);
            factory.onRestoreInstanceState(new Bundle());
            assertEquals(TrendingFactory.RANGE_DAILY, factory.getRange());
        }
    }

    @Test
    public void restore_sameRange_doesNotInvalidate() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TrendingFactory factory = new TrendingFactory(activity);
            Bundle out = new Bundle();
            out.putInt("trending:range", TrendingFactory.RANGE_DAILY);
            // 相同值不触发 invalidateFragments，不抛异常即通过
            factory.onRestoreInstanceState(out);
            assertEquals(TrendingFactory.RANGE_DAILY, factory.getRange());
        }
    }
}
