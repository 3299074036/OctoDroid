package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.SearchFragment;

@RunWith(RobolectricTestRunner.class)
public class SearchHubFactoryTest {

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            SearchHubFactory factory = new SearchHubFactory(activity);
            assertEquals(R.string.search, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.search, tabs[0]);
        }
    }

    @Test
    public void makeFragment_returnsRepoSearchFragment() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            SearchHubFactory factory = new SearchHubFactory(activity);
            SearchFragment fragment = (SearchFragment) factory.makeFragment(0);
            assertEquals(SearchFragment.SEARCH_TYPE_REPO,
                    fragment.getArguments().getInt("search_type", -1));
            assertEquals(null, fragment.getArguments().getString("initial_search"));
            assertEquals(false,
                    fragment.getArguments().getBoolean("do_initial_load", true));
        }
    }
}
