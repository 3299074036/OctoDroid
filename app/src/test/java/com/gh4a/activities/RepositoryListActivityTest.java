package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;

@RunWith(RobolectricTestRunner.class)
public class RepositoryListActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private ActivityController<RepositoryListActivity> launch(String user, boolean isOrg,
            String filter) {
        return Robolectric.buildActivity(RepositoryListActivity.class,
                RepositoryListActivity.makeIntent(context(), user, isOrg, filter));
    }

    @Test
    public void makeIntent_threeArg_defaultsFilterNull() {
        Intent intent = RepositoryListActivity.makeIntent(context(), "octocat", false);
        assertEquals("octocat", intent.getStringExtra("user"));
        assertFalse(intent.getBooleanExtra("is_org", true));
        assertNull(intent.getStringExtra("filter_type"));
    }

    @Test
    public void makeIntent_fourArg_carriesAll() {
        Intent intent = RepositoryListActivity.makeIntent(context(), "someorg", true, "forks");
        assertEquals("someorg", intent.getStringExtra("user"));
        assertTrue(intent.getBooleanExtra("is_org", false));
        assertEquals("forks", intent.getStringExtra("filter_type"));
    }

    @Test
    public void launches_titleAndSubtitle() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.user_pub_repos),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals("octocat",
                    activity.getSupportActionBar().getSubtitle().toString());
        }
    }

    @Test
    public void onCreateFragment_isRepositoryListContainerFragment() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            assertTrue(activity.getFragment()
                    instanceof com.gh4a.fragment.RepositoryListContainerFragment);
        }
    }

    @Test
    public void getRightNavigationDrawerMenuResources_nonEmpty() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            int[] resIds = activity.getRightNavigationDrawerMenuResources();
            assertNotNull(resIds);
            assertTrue(resIds.length >= 1);
            for (int resId : resIds) {
                assertTrue(resId != 0);
            }
        }
    }

    @Test
    public void onNavigationItemSelected_filterType_returnsTrue() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.filter_type_forks, 0, "forks");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.filter_type_forks)));
        }
    }

    @Test
    public void onNavigationItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "search");
            assertFalse(activity.onNavigationItemSelected(menu.findItem(R.id.search)));
        }
    }

    @Test
    public void supportInvalidateOptionsMenu_isNoOp() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            activity.supportInvalidateOptionsMenu();
            assertNotNull(activity);
        }
    }

    @Test
    public void initiateFilter_doesNotCrash() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            activity.initiateFilter();
            assertNotNull(activity);
        }
    }

    @Test
    public void navigateUp_returnsUserActivityIntent() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertEquals(UserActivity.class.getName(), up.getComponent().getClassName());
            assertEquals("octocat", up.getStringExtra("login"));
        }
    }

    @Test
    public void onPrepareRightNavigationDrawerMenu_withNullFragmentSafe() {
        // mFragment 在 onCreate 即赋值；此处只验证准备菜单不崩。
        try (ActivityController<RepositoryListActivity> c = launch("octocat", true, "sources")) {
            RepositoryListActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            activity.onPrepareRightNavigationDrawerMenu(menu);
            assertNotNull(activity);
        }
    }

    @Test
    public void getRightNavigationDrawerMenuResources_watchedFilter_returnsSingleMenu() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.filter_type_watched, 0, "watched");
            assertTrue(activity.onNavigationItemSelected(
                    menu.findItem(R.id.filter_type_watched)));
            int[] ids = activity.getRightNavigationDrawerMenuResources();
            assertEquals(1, ids.length);
        }
    }

    @Test
    public void onNavigationItemSelected_sortNameDesc_setsSortOrder() {
        try (ActivityController<RepositoryListActivity> c = launch("octocat", false, null)) {
            RepositoryListActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.sort_name_desc, 0, "name desc");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.sort_name_desc)));
            assertEquals("full_name", ((com.gh4a.fragment.RepositoryListContainerFragment)
                    activity.getFragment()).getSortOrder());
        }
    }
}
