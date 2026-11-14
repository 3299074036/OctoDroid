package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;

/** FragmentFactory 基类的默认行为：全部是空实现/false/null/0。 */
@RunWith(RobolectricTestRunner.class)
public class FragmentFactoryTest {

    private static class TestFactory extends FragmentFactory {
        TestFactory(HomeActivity activity) {
            super(activity);
        }

        @Override
        protected @StringRes int getTitleResId() {
            return R.string.app_name;
        }

        @Override
        protected int[] getTabTitleResIds() {
            return new int[] { R.string.app_name };
        }

        @Override
        protected Fragment makeFragment(int position) {
            return null;
        }
    }

    @Test
    public void defaults_areNoOps() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            TestFactory factory = new TestFactory(activity);
            assertFalse(factory.refreshMenuOnTabSwitch());
            assertNull(factory.getHeaderColorAttrs());
            assertNull(factory.getToolDrawerMenuResIds());
            assertFalse(factory.onDrawerItemSelected(null));
            assertFalse(factory.onCreateOptionsMenu(null));
            assertFalse(factory.onOptionsItemSelected(null));
            assertEquals(0, factory.getInitialToolDrawerSelection());
            // 以下空实现调用不抛异常
            Fragment f = new Fragment();
            factory.onFragmentInstantiated(f, 0);
            factory.onFragmentDestroyed(f);
            factory.prepareToolDrawerMenu(null);
            factory.onSaveInstanceState(new Bundle());
            factory.onRestoreInstanceState(new Bundle());
            factory.onRefresh();
            factory.onDestroy();
            factory.setUserInfo(null);
            factory.onStartLoadingData();
            assertEquals(R.string.app_name, factory.getTitleResId());
            assertEquals(1, factory.getTabTitleResIds().length);
            assertNull(factory.makeFragment(0));
        }
    }
}
