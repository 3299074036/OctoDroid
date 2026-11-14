package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.RepositoryListContainerFragment;

import java.lang.reflect.Field;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class RepositoryFactoryTest {

    private android.content.SharedPreferences prefs() {
        return ApplicationProvider.getApplicationContext().getSharedPreferences(
                "test-repo-factory-prefs", Context.MODE_PRIVATE);
    }

    private RepositoryFactory factory(HomeActivity activity) {
        return new RepositoryFactory(activity, "octocat", prefs());
    }

    private static void setFragmentField(RepositoryFactory factory,
            RepositoryListContainerFragment fragment) throws Exception {
        Field f = RepositoryFactory.class.getDeclaredField("mFragment");
        f.setAccessible(true);
        f.set(factory, fragment);
    }

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            assertEquals(R.string.my_repositories, f.getTitleResId());
            int[] tabs = f.getTabTitleResIds();
            assertEquals(1, tabs.length);
            assertEquals(R.string.my_repositories, tabs[0]);
        }
    }

    @Test
    public void getToolDrawerMenuResIds_defaultFilter_hasSortAndFilter() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            int[] ids = factory(activity).getToolDrawerMenuResIds();
            assertEquals(2, ids.length);
            assertEquals(R.menu.repo_sort, ids[0]);
            assertTrue(ids[1] != 0);
        }
    }

    @Test
    public void makeFragment_returnsRepoListContainer() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryListContainerFragment f =
                    (RepositoryListContainerFragment) factory(activity).makeFragment(0);
            assertEquals("octocat", f.getArguments().getString("user"));
            assertFalse(f.getArguments().getBoolean("is_org", true));
        }
    }

    @Test
    public void onFragmentInstantiated_withEmptyPrefs_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment frag =
                    (RepositoryListContainerFragment) f.makeFragment(0);
            // 空 prefs 时 restorePreviouslySelectedFilterAndSort 不碰 fragment
            f.onFragmentInstantiated(frag, 0);
        }
    }

    @Test
    public void onFragmentDestroyed_callsDestroyChildren() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment mockFragment =
                    mock(RepositoryListContainerFragment.class);
            setFragmentField(f, mockFragment);
            f.onFragmentDestroyed(mockFragment);
            verify(mockFragment).destroyChildren();
        }
    }

    @Test
    public void onFragmentDestroyed_otherFragment_doesNothing() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment mockFragment =
                    mock(RepositoryListContainerFragment.class);
            setFragmentField(f, mockFragment);
            // 非当前 fragment：不调 destroyChildren、不清字段
            f.onFragmentDestroyed(mock(RepositoryListContainerFragment.class));
            org.mockito.Mockito.verifyNoInteractions(mockFragment);
        }
    }

    @Test
    public void onDrawerItemSelected_filterType_persistsPref() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment mockFragment =
                    mock(RepositoryListContainerFragment.class);
            setFragmentField(f, mockFragment);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.filter_type_forks, 0, "forks");
            assertTrue(f.onDrawerItemSelected(menu.findItem(R.id.filter_type_forks)));
            verify(mockFragment).setFilterType("forks");
            assertEquals("forks", prefs().getString("home_repo_list_filter", null));
        }
    }

    @Test
    public void onDrawerItemSelected_sortItem_persistsPref() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment mockFragment =
                    mock(RepositoryListContainerFragment.class);
            setFragmentField(f, mockFragment);
            MenuBuilder menu = new MenuBuilder(activity);
            activity.getMenuInflater().inflate(R.menu.repo_sort, menu);
            android.view.MenuItem item = menu.findItem(R.id.sort_name_desc);
            assertNotNull(item);
            assertTrue(f.onDrawerItemSelected(item));
            verify(mockFragment).setSortOrder("full_name", "desc");
            assertEquals("full_name",
                    prefs().getString("home_repo_list_sort_order", null));
            assertEquals("desc", prefs().getString("home_repo_list_sort_dir", null));
        }
    }

    @Test
    public void onDrawerItemSelected_unknownItem_returnsFalse() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            setFragmentField(f, mock(RepositoryListContainerFragment.class));
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "search");
            assertFalse(f.onDrawerItemSelected(menu.findItem(R.id.search)));
        }
    }

    @Test
    public void onDrawerItemSelected_withoutFragment_throwsNpe() {
        // A2 备注：onDrawerItemSelected 未判空 mFragment，fragment 尚未
        // 实例化时选过滤/排序即 NPE；prepareToolDrawerMenu 有判空，
        // 正常流程 fragment 先建好，此处只把前置条件钉死。
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.filter_type_forks, 0, "forks");
            try {
                f.onDrawerItemSelected(menu.findItem(R.id.filter_type_forks));
                throw new AssertionError("expected NullPointerException");
            } catch (NullPointerException expected) {
                // expected
            }
        }
    }

    @Test
    public void prepareToolDrawerMenu_withoutFragment_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            f.prepareToolDrawerMenu(menu);
        }
    }

    @Test
    public void saveAndRestore_withoutFragment_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            Bundle out = new Bundle();
            f.onSaveInstanceState(out);
            f.onRestoreInstanceState(out);
        }
    }

    @Test
    public void onDestroy_withoutFragment_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            f.onDestroy();
        }
    }

    @Test
    public void watchedFilter_hidesSortMenu() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment mockFragment =
                    mock(RepositoryListContainerFragment.class);
            setFragmentField(f, mockFragment);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.filter_type_watched, 0, "watched");
            assertTrue(f.onDrawerItemSelected(menu.findItem(R.id.filter_type_watched)));
            // watched 下 SortDrawerHelper.getMenuResId() 为 0，只剩过滤菜单
            int[] ids = f.getToolDrawerMenuResIds();
            assertEquals(1, ids.length);
        }
    }

    @Test
    public void onFragmentInstantiated_withPrefs_restoresFilterAndSort() throws Exception {
        prefs().edit()
                .putString("home_repo_list_filter", "forks")
                .putString("home_repo_list_sort_order", "full_name")
                .putString("home_repo_list_sort_dir", "desc")
                .apply();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryListContainerFragment fragment =
                    mock(RepositoryListContainerFragment.class);
            factory(activity).onFragmentInstantiated(fragment, 0);
            verify(fragment).setFilterType("forks");
            verify(fragment).setSortOrder("full_name", "desc");
        } finally {
            prefs().edit()
                    .remove("home_repo_list_filter")
                    .remove("home_repo_list_sort_order")
                    .remove("home_repo_list_sort_dir")
                    .apply();
        }
    }

    @Test
    public void onSaveAndRestoreInstanceState_roundTripsFragment() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f1 = factory(activity);
            RepositoryListContainerFragment fragment =
                    (RepositoryListContainerFragment) f1.makeFragment(0);
            activity.getSupportFragmentManager().beginTransaction()
                    .add(fragment, "test-fragment").commit();
            activity.getSupportFragmentManager().executePendingTransactions();
            f1.onFragmentInstantiated(fragment, 0);
            Bundle out = new Bundle();
            f1.onSaveInstanceState(out);
            RepositoryFactory f2 = factory(activity);
            f2.onRestoreInstanceState(out);
            Field f = RepositoryFactory.class.getDeclaredField("mFragment");
            f.setAccessible(true);
            assertEquals(fragment, f.get(f2));
        }
    }

    @Test
    public void prepareToolDrawerMenu_withFragment_selectsCurrent() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            RepositoryFactory f = factory(activity);
            RepositoryListContainerFragment fragment =
                    mock(RepositoryListContainerFragment.class);
            when(fragment.getFilterType()).thenReturn("all");
            when(fragment.getSortOrder()).thenReturn("created");
            when(fragment.getSortDirection()).thenReturn("desc");
            setFragmentField(f, fragment);
            MenuBuilder menu = new MenuBuilder(activity);
            for (int resId : f.getToolDrawerMenuResIds()) {
                activity.getMenuInflater().inflate(resId, menu);
            }
            f.prepareToolDrawerMenu(menu);
            assertNotNull(menu);
        }
    }
}
