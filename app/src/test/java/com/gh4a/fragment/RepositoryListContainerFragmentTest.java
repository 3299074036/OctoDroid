package com.gh4a.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** RepositoryListContainerFragment：仓库列表容器 + 抽屉助手（Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class RepositoryListContainerFragmentTest {
    private FragmentActivity mActivity;
    private Context mContext;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        mContext = ApplicationProvider.getApplicationContext();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    @Test
    public void newInstance_twoArgStoresUserOrgAndDefaultFilter() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", true);
        assertEquals("octo", f.getArguments().getString("user"));
        assertTrue(f.getArguments().getBoolean("is_org"));
        assertNull(f.getArguments().getString("filter_type"));
    }

    @Test
    public void newInstance_threeArgStoresDefaultFilter() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false, "starred");
        assertEquals("starred", f.getArguments().getString("filter_type"));
    }

    @Test
    public void onCreate_defaultFilterTypeIsAll() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        assertEquals("all", f.getFilterType());
    }

    @Test
    public void onCreate_usesDefaultFilterFromArguments() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false, "watched"));
        assertEquals("watched", f.getFilterType());
    }

    @Test
    public void getSortOrder_defaultIsFullNameAsc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        assertEquals("full_name", f.getSortOrder());
        assertEquals("asc", f.getSortDirection());
    }

    @Test
    public void setFilterType_starredResetsSortToCreatedDesc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setFilterType("starred");
        assertEquals("starred", f.getFilterType());
        assertEquals("created", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
    }

    @Test
    public void setSortOrder_starredKeepsUpdatedDesc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false, "starred"));
        f.setSortOrder("updated", "desc");
        assertEquals("updated", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
    }

    @Test
    public void setSortOrder_invalidSortResetsToFullNameAsc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setSortOrder("bogus", "sideways");
        assertEquals("full_name", f.getSortOrder());
        assertEquals("asc", f.getSortDirection());
    }

    @Test
    public void setFilterType_sameValueIsNoop() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setFilterType("all");
        assertEquals("all", f.getFilterType());
        assertEquals("full_name", f.getSortOrder());
    }

    @Test
    public void onSaveInstanceState_persistsFilterAndSort() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setFilterType("starred");
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("starred", out.getString("filter_type"));
        // setFilterType 只改 mFilterType、不调 validateSortOrder()，
        // 故存的是未校验的原始值 full_name/asc；getSortOrder() 读时才校验为 created。
        assertEquals("full_name", out.getString("sort_order"));
        assertEquals("asc", out.getString("sort_direction"));
        assertEquals("created", f.getSortOrder());
        assertTrue(out.containsKey("search_visible"));
        assertTrue(out.containsKey("search_query"));
        assertTrue(out.containsKey("search_is_expanded"));
    }

    // ---------- FilterDrawerHelper ----------

    @Test
    public void filterHelper_createPicksMenuBySelfOrgOrUser() {
        // 未登录：auth login 为 null，loginEquals(null) 为 false，走 org/user 分支。
        assertEquals(R.menu.repo_filter_user,
                RepositoryListContainerFragment.FilterDrawerHelper
                        .create("someone", false).getMenuResId());
        assertEquals(R.menu.repo_filter_org,
                RepositoryListContainerFragment.FilterDrawerHelper
                        .create("someorg", true).getMenuResId());
        assertEquals(R.menu.repo_filter_user,
                RepositoryListContainerFragment.FilterDrawerHelper
                        .create(null, false).getMenuResId());
    }

    @Test
    public void filterHelper_handleSelectionMapsAllIds() {
        RepositoryListContainerFragment.FilterDrawerHelper helper =
                RepositoryListContainerFragment.FilterDrawerHelper.create("x", false);
        assertEquals("all", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_all)));
        assertEquals("owner", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_owner)));
        assertEquals("member", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_member)));
        assertEquals("public", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_public)));
        assertEquals("private", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_private)));
        assertEquals("sources", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_sources)));
        assertEquals("forks", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_forks)));
        assertEquals("watched", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_watched)));
        assertEquals("starred", helper.handleSelectionAndGetFilterType(item(R.id.filter_type_starred)));
        assertNull(helper.handleSelectionAndGetFilterType(item(R.id.search)));
    }

    @Test
    public void filterHelper_selectFilterTypeChecksOnlySelected() {
        RepositoryListContainerFragment.FilterDrawerHelper helper =
                RepositoryListContainerFragment.FilterDrawerHelper.create("x", false);
        MenuBuilder menu = new MenuBuilder(mContext);
        menu.add(0, R.id.filter_type_all, 0, "All");
        menu.add(0, R.id.filter_type_owner, 0, "Owner");
        menu.add(0, R.id.filter_type_member, 0, "Member");
        helper.selectFilterType(menu, "owner");
        assertFalse(menu.findItem(R.id.filter_type_all).isChecked());
        assertTrue(menu.findItem(R.id.filter_type_owner).isChecked());
        assertFalse(menu.findItem(R.id.filter_type_member).isChecked());
        // 未知类型：无一选中。
        helper.selectFilterType(menu, "bogus");
        assertFalse(menu.findItem(R.id.filter_type_owner).isChecked());
    }

    // ---------- SortDrawerHelper ----------

    @Test
    public void sortHelper_defaultMenuIsRepoSort() {
        RepositoryListContainerFragment.SortDrawerHelper helper =
                new RepositoryListContainerFragment.SortDrawerHelper();
        assertEquals(R.menu.repo_sort, helper.getMenuResId());
    }

    @Test
    public void sortHelper_watchedAndStarredHideSortMenu() {
        RepositoryListContainerFragment.SortDrawerHelper helper =
                new RepositoryListContainerFragment.SortDrawerHelper();
        helper.setFilterType("watched");
        assertEquals(0, helper.getMenuResId());
        helper.setFilterType("starred");
        assertEquals(0, helper.getMenuResId());
        helper.setFilterType("all");
        assertEquals(R.menu.repo_sort, helper.getMenuResId());
    }

    @Test
    public void sortHelper_handleSelectionMapsAllIds() {
        RepositoryListContainerFragment.SortDrawerHelper helper =
                new RepositoryListContainerFragment.SortDrawerHelper();
        assertArrayEquals(new String[]{"full_name", "asc"},
                helper.handleSelectionAndGetSortOrder(item(R.id.sort_name_asc)));
        assertArrayEquals(new String[]{"full_name", "desc"},
                helper.handleSelectionAndGetSortOrder(item(R.id.sort_name_desc)));
        assertArrayEquals(new String[]{"created", "asc"},
                helper.handleSelectionAndGetSortOrder(item(R.id.sort_created_asc)));
        assertArrayEquals(new String[]{"pushed", "desc"},
                helper.handleSelectionAndGetSortOrder(item(R.id.sort_pushed_desc)));
        assertArrayEquals(new String[]{"updated", "asc"},
                helper.handleSelectionAndGetSortOrder(item(R.id.sort_updated_asc)));
        assertNull(helper.handleSelectionAndGetSortOrder(item(R.id.search)));
    }

    @Test
    public void sortHelper_selectSortTypeChecksOnlySelected() {
        RepositoryListContainerFragment.SortDrawerHelper helper =
                new RepositoryListContainerFragment.SortDrawerHelper();
        MenuBuilder menu = new MenuBuilder(mContext);
        menu.add(0, R.id.sort_name_asc, 0, "Name A-Z");
        menu.add(0, R.id.sort_created_desc, 0, "Newest");
        menu.add(0, R.id.sort_updated_desc, 0, "Updated");
        helper.selectSortType(menu, "created", "desc", false);
        assertFalse(menu.findItem(R.id.sort_name_asc).isChecked());
        assertTrue(menu.findItem(R.id.sort_created_desc).isChecked());
        assertFalse(menu.findItem(R.id.sort_updated_desc).isChecked());
    }

    @Test
    public void sortHelper_selectSortTypeSingleItemMode() {
        RepositoryListContainerFragment.SortDrawerHelper helper =
                new RepositoryListContainerFragment.SortDrawerHelper();
        MenuBuilder menu = new MenuBuilder(mContext);
        menu.add(0, R.id.sort_created_desc, 0, "Newest");
        helper.selectSortType(menu, "created", "desc", true);
        assertTrue(menu.findItem(R.id.sort_created_desc).isChecked());
        // 菜单里没有该项时不崩溃。
        MenuBuilder emptyMenu = new MenuBuilder(mContext);
        helper.selectSortType(emptyMenu, "created", "desc", true);
    }

    @Test
    public void sortHelper_selectSortTypeUnknownSortChecksNothing() {
        RepositoryListContainerFragment.SortDrawerHelper helper =
                new RepositoryListContainerFragment.SortDrawerHelper();
        MenuBuilder menu = new MenuBuilder(mContext);
        menu.add(0, R.id.sort_name_asc, 0, "Name A-Z");
        helper.selectSortType(menu, "bogus", "desc", false);
        assertFalse(menu.findItem(R.id.sort_name_asc).isChecked());
    }

    private static MenuItem item(int id) {
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getItemId()).thenReturn(id);
        return menuItem;
    }
}
