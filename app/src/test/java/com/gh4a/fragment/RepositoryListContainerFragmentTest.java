package com.gh4a.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Repository;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    /** AppCompat host: menu inflation needs the AppCompat inflater for the
     *  SearchView action view. Also implements the container's filter Callback. */
    public static class MenuHostActivity extends AppCompatActivity
            implements RepositoryListContainerFragment.Callback {
        boolean filterInitiated;

        @Override
        public void initiateFilter() {
            filterInitiated = true;
        }
    }

    private static MenuHostActivity attachMenuHost(RepositoryListContainerFragment f) {
        MenuHostActivity activity = Robolectric.buildActivity(MenuHostActivity.class).setup().get();
        activity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return activity;
    }

    private static void setPrivate(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** Attach at CREATED with real children already added, then drive onViewCreated
     *  so it takes the existing-children branch (setSearchVisibility path).
     *  Children stay at CREATED, so their loaders never fire (no network). */
    private RepositoryListContainerFragment attachWithRealChildren() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        f.getChildFragmentManager().beginTransaction()
                .add(RepositoryListFragment.newInstance(
                        "octo", false, "all", "full_name", "asc"), "main")
                .add(RepositorySearchFragment.newInstance("octo"), "search")
                .commitNow();
        f.onViewCreated(new View(mContext), null);
        f.getChildFragmentManager().executePendingTransactions();
        return f;
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

    // ---------- onCreate restore ----------

    @Test
    public void onCreate_restoresFromSavedInstanceState() throws Exception {
        // Direct f.onCreate(bundle) on an unattached fragment NPEs in
        // Fragment.onCreate (child FragmentManager not initialized yet), so drive
        // the restore through the real FragmentManager save/restore path.
        ActivityController<FragmentActivity> controller =
                Robolectric.buildActivity(FragmentActivity.class).setup();
        FragmentActivity activity = controller.get();
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        activity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        // The search fields have no public setters; set them reflectively.
        setPrivate(f, "mFilterType", "starred");
        setPrivate(f, "mSortOrder", "updated");
        setPrivate(f, "mSortDirection", "desc");
        setPrivate(f, "mSearchVisible", true);
        setPrivate(f, "mSearchQuery", "foo");
        setPrivate(f, "mSearchIsExpanded", true);
        controller.recreate();
        RepositoryListContainerFragment restored =
                (RepositoryListContainerFragment) controller.get().getSupportFragmentManager()
                        .findFragmentByTag("test");
        assertNotNull(restored);
        assertEquals("starred", restored.getFilterType());
        assertEquals("updated", restored.getSortOrder());
        assertEquals("desc", restored.getSortDirection());
        Bundle out = new Bundle();
        restored.onSaveInstanceState(out);
        assertTrue(out.getBoolean("search_visible"));
        assertEquals("foo", out.getString("search_query"));
        assertTrue(out.getBoolean("search_is_expanded"));
    }

    @Test
    public void onCreate_nonNullBundleWithoutKeys_keepsArgs() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false, "watched");
        // Attach first so the child FragmentManager is initialized, then re-invoke
        // onCreate with a keyless bundle to hit the containsKey false arm.
        attach(f);
        f.onCreate(new Bundle());
        assertEquals("watched", f.getFilterType());
        assertEquals("full_name", f.getSortOrder());
    }

    @Test
    public void setFilterType_beforeAttach_skipsArgsFilterOnCreate() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        f.setFilterType("starred");
        assertEquals("starred", f.getFilterType());
        attach(f);
        assertEquals("starred", f.getFilterType());
    }

    // ---------- sort order validation arms ----------

    @Test
    public void setSortOrder_starredCreated_keepsCreatedAsc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false, "starred"));
        f.setSortOrder("created", "asc");
        assertEquals("created", f.getSortOrder());
        assertEquals("asc", f.getSortDirection());
    }

    @Test
    public void setSortOrder_created_keepsCreatedDesc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setSortOrder("created", "desc");
        assertEquals("created", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
    }

    @Test
    public void setSortOrder_pushed_keepsPushedAsc() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setSortOrder("pushed", "asc");
        assertEquals("pushed", f.getSortOrder());
        assertEquals("asc", f.getSortDirection());
    }

    @Test
    public void setSortOrder_sameValue_isNoop() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.setSortOrder("full_name", "asc");
        assertEquals("full_name", f.getSortOrder());
        assertEquals("asc", f.getSortDirection());
    }

    // ---------- refresh / destroy / scroll ----------

    @Test
    public void onRefresh_delegatesToBothChildren() throws Exception {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        PagedDataBaseFragment<Repository> main = mock(PagedDataBaseFragment.class);
        RepositorySearchFragment search = mock(RepositorySearchFragment.class);
        setPrivate(f, "mMainFragment", main);
        setPrivate(f, "mSearchFragment", search);
        f.onRefresh();
        verify(main).onRefresh();
        verify(search).onRefresh();
    }

    @Test
    public void destroyChildren_beforeViewCreated_bothNull_noop() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        f.destroyChildren();
    }

    @Test
    public void destroyChildren_withChildren_removesBoth() throws Exception {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        RepositoryListFragment main =
                RepositoryListFragment.newInstance("octo", false, "all", "full_name", "asc");
        RepositorySearchFragment search = RepositorySearchFragment.newInstance("octo");
        f.getChildFragmentManager().beginTransaction()
                .add(main, "main").add(search, "search").commitNow();
        setPrivate(f, "mMainFragment", main);
        setPrivate(f, "mSearchFragment", search);
        f.destroyChildren();
        assertNull(f.getChildFragmentManager().findFragmentByTag("main"));
        assertNull(f.getChildFragmentManager().findFragmentByTag("search"));
    }

    @Test
    public void canChildScrollUp_noFilterItem_delegatesToMain() throws Exception {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        PagedDataBaseFragment<Repository> main = mock(PagedDataBaseFragment.class);
        RepositorySearchFragment search = mock(RepositorySearchFragment.class);
        setPrivate(f, "mMainFragment", main);
        setPrivate(f, "mSearchFragment", search);
        assertFalse(f.canChildScrollUp());
        verify(main).canChildScrollUp();
    }

    @Test
    public void canChildScrollUp_searchMode_delegatesToSearch() throws Exception {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        PagedDataBaseFragment<Repository> main = mock(PagedDataBaseFragment.class);
        RepositorySearchFragment search = mock(RepositorySearchFragment.class);
        setPrivate(f, "mMainFragment", main);
        setPrivate(f, "mSearchFragment", search);
        MenuItem filter = mock(MenuItem.class);
        when(filter.isVisible()).thenReturn(false);
        setPrivate(f, "mFilterItem", filter);
        assertFalse(f.canChildScrollUp());
        verify(search).canChildScrollUp();
    }

    @Test
    public void canChildScrollUp_filterVisible_delegatesToMain() throws Exception {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        PagedDataBaseFragment<Repository> main = mock(PagedDataBaseFragment.class);
        RepositorySearchFragment search = mock(RepositorySearchFragment.class);
        setPrivate(f, "mMainFragment", main);
        setPrivate(f, "mSearchFragment", search);
        MenuItem filter = mock(MenuItem.class);
        when(filter.isVisible()).thenReturn(true);
        setPrivate(f, "mFilterItem", filter);
        assertFalse(f.canChildScrollUp());
        verify(main).canChildScrollUp();
    }

    // ---------- view / search visibility ----------

    @Test
    public void onViewCreated_existingChildren_restoresSearchVisibility() {
        RepositoryListContainerFragment f = attachWithRealChildren();
        assertNotNull(f.getChildFragmentManager().findFragmentByTag("main"));
        assertNotNull(f.getChildFragmentManager().findFragmentByTag("search"));
        assertFalse(f.getChildFragmentManager().findFragmentByTag("main").isHidden());
        assertTrue(f.getChildFragmentManager().findFragmentByTag("search").isHidden());
    }

    // ---------- options menu ----------

    @Test
    public void onCreateOptionsMenu_allFilter_setsUpSearch() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        MenuHostActivity activity = attachMenuHost(f);
        MenuBuilder menu = new MenuBuilder(activity);
        f.onCreateOptionsMenu(menu, activity.getMenuInflater());
        assertNotNull(menu.findItem(R.id.search));
        assertNotNull(menu.findItem(R.id.filter));
        assertTrue(menu.findItem(R.id.filter).isVisible());
    }

    @Test
    public void onCreateOptionsMenu_expandedSearch_restoresQuery() throws Exception {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        setPrivate(f, "mSearchIsExpanded", true);
        setPrivate(f, "mSearchQuery", "foo");
        MenuHostActivity activity = attachMenuHost(f);
        MenuBuilder menu = new MenuBuilder(activity);
        f.onCreateOptionsMenu(menu, activity.getMenuInflater());
        SearchView searchView = (SearchView) menu.findItem(R.id.search).getActionView();
        assertNotNull(searchView);
        assertEquals("foo", searchView.getQuery().toString());
    }

    // ---------- options item selection ----------

    @Test
    public void onOptionsItemSelected_search_showsSearch() {
        RepositoryListContainerFragment f = attachWithRealChildren();
        MenuItem search = mock(MenuItem.class);
        when(search.getItemId()).thenReturn(R.id.search);
        assertTrue(f.onOptionsItemSelected(search));
        f.getChildFragmentManager().executePendingTransactions();
        assertFalse(f.getChildFragmentManager().findFragmentByTag("search").isHidden());
        assertTrue(f.getChildFragmentManager().findFragmentByTag("main").isHidden());
    }

    @Test
    public void onOptionsItemSelected_filter_withoutCallback_returnsTrue() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        MenuItem filter = mock(MenuItem.class);
        when(filter.getItemId()).thenReturn(R.id.filter);
        assertTrue(f.onOptionsItemSelected(filter));
    }

    @Test
    public void onOptionsItemSelected_filter_withCallback_initiatesFilter() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        MenuHostActivity activity = attachMenuHost(f);
        MenuItem filter = mock(MenuItem.class);
        when(filter.getItemId()).thenReturn(R.id.filter);
        assertTrue(f.onOptionsItemSelected(filter));
        assertTrue(activity.filterInitiated);
    }

    @Test
    public void onOptionsItemSelected_unknown_returnsFalse() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        MenuItem other = mock(MenuItem.class);
        when(other.getItemId()).thenReturn(R.id.filter_type_all);
        assertFalse(f.onOptionsItemSelected(other));
    }

    // ---------- search query / close / expand ----------

    @Test
    public void onQueryTextSubmit_withSearchFragment_setsQuery() throws Exception {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        RepositorySearchFragment search = mock(RepositorySearchFragment.class);
        setPrivate(f, "mSearchFragment", search);
        assertFalse(f.onQueryTextSubmit("foo"));
        verify(search).setQuery("foo");
    }

    @Test
    public void onQueryTextSubmit_noSearchFragment_returnsFalse() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        assertFalse(f.onQueryTextSubmit("foo"));
    }

    @Test
    public void onQueryTextChange_storesQuery() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        assertFalse(f.onQueryTextChange("bar"));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("bar", out.getString("search_query"));
    }

    @Test
    public void onClose_hidesSearch() {
        RepositoryListContainerFragment f = attachWithRealChildren();
        assertFalse(f.onClose());
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertFalse(out.getBoolean("search_is_expanded"));
        assertNull(out.getString("search_query"));
        f.getChildFragmentManager().executePendingTransactions();
        assertTrue(f.getChildFragmentManager().findFragmentByTag("search").isHidden());
    }

    @Test
    public void onMenuItemActionExpand_marksExpanded() {
        RepositoryListContainerFragment f = attach(
                RepositoryListContainerFragment.newInstance("octo", false));
        assertTrue(f.onMenuItemActionExpand(mock(MenuItem.class)));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.getBoolean("search_is_expanded"));
    }

    @Test
    public void onMenuItemActionCollapse_hidesSearch() {
        RepositoryListContainerFragment f = attachWithRealChildren();
        assertTrue(f.onMenuItemActionCollapse(mock(MenuItem.class)));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertFalse(out.getBoolean("search_is_expanded"));
        assertNull(out.getString("search_query"));
        f.getChildFragmentManager().executePendingTransactions();
        assertTrue(f.getChildFragmentManager().findFragmentByTag("search").isHidden());
    }

    @Test
    public void onCreateOptionsMenu_searchVisible_hidesFilterItem() throws Exception {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        setPrivate(f, "mSearchVisible", true);
        MenuHostActivity activity = attachMenuHost(f);
        MenuBuilder menu = new MenuBuilder(activity);
        f.onCreateOptionsMenu(menu, activity.getMenuInflater());
        assertFalse(menu.findItem(R.id.filter).isVisible());
    }

    @Test
    public void onOptionsItemSelected_search_withFilterItem_hidesFilterItem() {
        RepositoryListContainerFragment f =
                RepositoryListContainerFragment.newInstance("octo", false);
        MenuHostActivity activity = attachMenuHost(f);
        f.getChildFragmentManager().beginTransaction()
                .add(RepositoryListFragment.newInstance(
                        "octo", false, "all", "full_name", "asc"), "main")
                .add(RepositorySearchFragment.newInstance("octo"), "search")
                .commitNow();
        f.onViewCreated(new View(activity), null);
        f.getChildFragmentManager().executePendingTransactions();
        MenuBuilder menu = new MenuBuilder(activity);
        f.onCreateOptionsMenu(menu, activity.getMenuInflater());
        assertTrue(menu.findItem(R.id.filter).isVisible());
        MenuItem search = mock(MenuItem.class);
        when(search.getItemId()).thenReturn(R.id.search);
        assertTrue(f.onOptionsItemSelected(search));
        f.getChildFragmentManager().executePendingTransactions();
        assertFalse(menu.findItem(R.id.filter).isVisible());
        assertFalse(f.getChildFragmentManager().findFragmentByTag("search").isHidden());
        // Closing search flips the same branch the other way (visible=false).
        assertFalse(f.onClose());
        f.getChildFragmentManager().executePendingTransactions();
        assertTrue(menu.findItem(R.id.filter).isVisible());
        assertTrue(f.getChildFragmentManager().findFragmentByTag("search").isHidden());
    }

    private static MenuItem item(int id) {
        MenuItem menuItem = mock(MenuItem.class);
        when(menuItem.getItemId()).thenReturn(id);
        return menuItem;
    }
}
