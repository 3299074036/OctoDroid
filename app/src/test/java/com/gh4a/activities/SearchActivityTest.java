package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.SearchFragment;

@RunWith(RobolectricTestRunner.class)
public class SearchActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void searchTypeConstants_mirrorSearchFragment() {
        assertEquals(SearchFragment.SEARCH_TYPE_REPO, SearchActivity.SEARCH_TYPE_REPO);
        assertEquals(SearchFragment.SEARCH_TYPE_USER, SearchActivity.SEARCH_TYPE_USER);
        assertEquals(SearchFragment.SEARCH_TYPE_CODE, SearchActivity.SEARCH_TYPE_CODE);
    }

    @Test
    public void makeIntent_bare_hasNoExtras() {
        Intent intent = SearchActivity.makeIntent(context());
        assertEquals(SearchActivity.class.getName(), intent.getComponent().getClassName());
        assertNull(intent.getExtras());
    }

    @Test
    public void makeIntent_full_carriesAllExtras() {
        Intent intent = SearchActivity.makeIntent(context(), "repo:foo/bar",
                SearchActivity.SEARCH_TYPE_CODE, true);
        assertEquals("repo:foo/bar", intent.getStringExtra("initial_search"));
        assertEquals(SearchActivity.SEARCH_TYPE_CODE, intent.getIntExtra("search_type", -1));
        assertTrue(intent.getBooleanExtra("search_immediately", false));
    }

    @Test
    public void makeIntent_full_startSearchFalsePreserved() {
        Intent intent = SearchActivity.makeIntent(context(), null,
                SearchActivity.SEARCH_TYPE_USER, false);
        assertEquals(SearchActivity.SEARCH_TYPE_USER, intent.getIntExtra("search_type", -1));
        assertEquals(false, intent.getBooleanExtra("search_immediately", true));
    }

    @Test
    public void launches_titleIsSearch() {
        Intent intent = SearchActivity.makeIntent(context(), "octocat",
                SearchActivity.SEARCH_TYPE_REPO, false);
        try (ActivityController<SearchActivity> c =
                Robolectric.buildActivity(SearchActivity.class, intent)) {
            SearchActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.search),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isSearchFragmentWithArgs() {
        Intent intent = SearchActivity.makeIntent(context(), "hello",
                SearchActivity.SEARCH_TYPE_USER, true);
        try (ActivityController<SearchActivity> c =
                Robolectric.buildActivity(SearchActivity.class, intent)) {
            SearchActivity activity = c.setup().get();
            assertTrue(activity.getFragment() instanceof SearchFragment);
            SearchFragment fragment = (SearchFragment) activity.getFragment();
            assertEquals(SearchActivity.SEARCH_TYPE_USER,
                    fragment.getArguments().getInt("search_type", -1));
            assertEquals("hello", fragment.getArguments().getString("initial_search"));
            assertTrue(fragment.getArguments().getBoolean("do_initial_load", false));
        }
    }

    @Test
    public void onCreateFragment_bareIntent_defaultsToRepoType() {
        Intent intent = SearchActivity.makeIntent(context());
        try (ActivityController<SearchActivity> c =
                Robolectric.buildActivity(SearchActivity.class, intent)) {
            SearchActivity activity = c.setup().get();
            SearchFragment fragment = (SearchFragment) activity.getFragment();
            assertNotNull(fragment);
            assertEquals(SearchActivity.SEARCH_TYPE_REPO,
                    fragment.getArguments().getInt("search_type", -1));
        }
    }
}
