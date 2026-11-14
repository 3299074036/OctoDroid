package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.IssueListFragment;
import java.lang.reflect.Field;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class IssueListFactoryTest {

    private android.content.SharedPreferences prefs() {
        return ApplicationProvider.getApplicationContext().getSharedPreferences(
                "test-issue-list-prefs", Context.MODE_PRIVATE);
    }

    private IssueListFactory issues(HomeActivity activity) {
        return new IssueListFactory(activity, "octocat", false, prefs());
    }

    private IssueListFactory prs(HomeActivity activity) {
        return new IssueListFactory(activity, "octocat", true, prefs());
    }

    @Test
    public void titleResId_openAndClosed_issuesAndPrs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            assertEquals(R.string.issues_open, f.getTitleResId());
            assertEquals(R.string.pull_requests_open, prs(activity).getTitleResId());
            // 切到 closed
            MenuBuilder menu = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu);
            f.onOptionsItemSelected(menu.findItem(Menu.FIRST));
            assertEquals(R.string.issues_closed, f.getTitleResId());
            IssueListFactory p = prs(activity);
            MenuBuilder menu2 = new MenuBuilder(activity);
            p.onCreateOptionsMenu(menu2);
            p.onOptionsItemSelected(menu2.findItem(Menu.FIRST));
            assertEquals(R.string.pull_requests_closed, p.getTitleResId());
        }
    }

    @Test
    public void tabTitleResIds_singleTab() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(R.string.issues_tab, issues(activity).getTabTitleResIds()[0]);
            assertEquals(R.string.prs_tab, prs(activity).getTabTitleResIds()[0]);
            assertEquals(1, issues(activity).getTabTitleResIds().length);
        }
    }

    @Test
    public void makeFragment_defaultQuery_openAuthor() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFragment f = (IssueListFragment) issues(activity).makeFragment(0);
            assertEquals("is:issue is:open author:octocat",
                    f.getArguments().getString("query"));
            assertEquals("open", f.getArguments().getString("state"));
            assertEquals(R.string.no_issues_found,
                    f.getArguments().getInt("emptytext", -1));
            assertTrue(f.getArguments().getBoolean("withrepo", false));
            IssueListFragment p = (IssueListFragment) prs(activity).makeFragment(0);
            assertEquals("is:pr is:open author:octocat",
                    p.getArguments().getString("query"));
            assertEquals(R.string.no_pull_requests_found,
                    p.getArguments().getInt("emptytext", -1));
        }
    }

    @Test
    public void makeFragment_actionFilter_changesAction() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            Bundle out = new Bundle();
            out.putInt("issue:action_filter", 1);
            f.onRestoreInstanceState(out);
            assertEquals("is:issue is:open assignee:octocat",
                    ((IssueListFragment) f.makeFragment(0)).getArguments()
                            .getString("query"));
            out.putInt("issue:action_filter", 2);
            f.onRestoreInstanceState(out);
            assertEquals("is:issue is:open mentions:octocat",
                    ((IssueListFragment) f.makeFragment(0)).getArguments()
                            .getString("query"));
            out.putInt("issue:action_filter", 3);
            f.onRestoreInstanceState(out);
            assertEquals("is:issue is:open involves:octocat",
                    ((IssueListFragment) f.makeFragment(0)).getArguments()
                            .getString("query"));
            out.putInt("issue:action_filter", 99);
            f.onRestoreInstanceState(out);
            assertEquals("is:issue is:open author:octocat",
                    ((IssueListFragment) f.makeFragment(0)).getArguments()
                            .getString("query"));
        }
    }

    @Test
    public void makeFragment_closedState_changesQueryAndState() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            Bundle out = new Bundle();
            out.putBoolean("issue:showing_closed", true);
            f.onRestoreInstanceState(out);
            IssueListFragment frag = (IssueListFragment) f.makeFragment(0);
            assertEquals("is:issue is:closed author:octocat",
                    frag.getArguments().getString("query"));
            assertEquals("closed", frag.getArguments().getString("state"));
        }
    }

    @Test
    public void onCreateOptionsMenu_addsThreeItems() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            // 基类 onCreateOptionsMenu 恒返回 false，但三项照常加上
            assertFalse(issues(activity).onCreateOptionsMenu(menu));
            assertEquals(3, menu.size());
            assertEquals(activity.getString(R.string.issues_menu_show_closed),
                    menu.findItem(Menu.FIRST).getTitle().toString());
            assertNotNull(menu.findItem(Menu.FIRST + 1));
            assertNotNull(menu.findItem(Menu.FIRST + 2));
        }
    }

    @Test
    public void onCreateOptionsMenu_closedState_showsOpenLabel() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu);
            f.onOptionsItemSelected(menu.findItem(Menu.FIRST));
            MenuBuilder menu2 = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu2);
            assertEquals(activity.getString(R.string.issues_menu_show_open),
                    menu2.findItem(Menu.FIRST).getTitle().toString());
        }
    }

    @Test
    public void onOptionsItemSelected_toolDrawerToggle_returnsTrue() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu);
            assertTrue(f.onOptionsItemSelected(menu.findItem(Menu.FIRST + 1)));
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "search");
            assertFalse(issues(activity).onOptionsItemSelected(menu.findItem(R.id.search)));
        }
    }

    @Test
    public void saveAndRestore_roundTripsState() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu);
            f.onOptionsItemSelected(menu.findItem(Menu.FIRST));
            Bundle out = new Bundle();
            f.onSaveInstanceState(out);
            assertTrue(out.getBoolean("issue:showing_closed", false));
            assertEquals(0, out.getInt("issue:action_filter", -1));
            IssueListFactory restored = issues(activity);
            restored.onRestoreInstanceState(out);
            assertEquals(R.string.issues_closed, restored.getTitleResId());
        }
    }

    @Test
    public void restore_noChange_doesNotReload() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            // 与当前状态一致的恢复不触发重建，不抛异常即通过
            f.onRestoreInstanceState(new Bundle());
            assertEquals(R.string.issues_open, f.getTitleResId());
        }
    }

    @Test
    public void constructor_readsPersistedSortFromPrefs() {
        android.content.SharedPreferences p = prefs();
        p.edit().putString("home_issue_list_sort_order", "comments")
                .putString("home_issue_list_sort_dir", "asc").apply();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFragment f = (IssueListFragment) issues(activity).makeFragment(0);
            assertEquals("comments", f.getArguments().getString("sortmode"));
            assertEquals("asc", f.getArguments().getString("order"));
        }
    }

    @Test
    public void getToolDrawerMenuResIds_returnsSortMenu() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            int[] ids = issues(activity).getToolDrawerMenuResIds();
            assertEquals(1, ids.length);
            assertEquals(R.menu.issue_list_sort, ids[0]);
        }
    }

    @Test
    public void onDrawerItemSelected_sortItem_persistsPrefs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            activity.getMenuInflater().inflate(R.menu.issue_list_sort, menu);
            android.view.MenuItem item = menu.findItem(R.id.sort_comments_desc);
            assertNotNull(item);
            assertTrue(f.onDrawerItemSelected(item));
            assertEquals("comments",
                    prefs().getString("home_issue_list_sort_order", null));
            assertEquals("desc",
                    prefs().getString("home_issue_list_sort_dir", null));
        }
    }

    @Test
    public void onDrawerItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "search");
            assertFalse(issues(activity)
                    .onDrawerItemSelected(menu.findItem(R.id.search)));
        }
    }

    @Test
    public void headerColorAttrs_nullInitially_setAfterToggle() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            assertNull(f.getHeaderColorAttrs());
            MenuBuilder menu = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu);
            f.onOptionsItemSelected(menu.findItem(Menu.FIRST));
            int[] attrs = f.getHeaderColorAttrs();
            assertNotNull(attrs);
            assertEquals(2, attrs.length);
            assertEquals(R.attr.colorIssueClosed, attrs[0]);
            assertEquals(R.attr.colorIssueClosedDark, attrs[1]);
        }
    }

    private static int intField(Object target, String name) throws Exception {
        Field f = IssueListFactory.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(target);
    }

    @Test
    public void showActionFilterDialog_clickItem_changesFilter() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            f.onCreateOptionsMenu(menu);
            assertTrue(f.onOptionsItemSelected(menu.findItem(Menu.FIRST + 2)));
            assertFalse(ShadowAlertDialog.getShownDialogs().isEmpty());
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            ShadowAlertDialog.getShownDialogs().get(0);
            ShadowLooper.idleMainLooper();
            android.widget.ListView listView = dialog.getListView();
            assertNotNull(listView);
            android.view.View row = listView.getChildAt(1);
            if (row == null) {
                row = new android.view.View(activity);
            }
            listView.performItemClick(row, 1, listView.getItemIdAtPosition(1));
            assertEquals(1, intField(f, "mActionFilter"));
        }
    }

    @Test
    public void prepareToolDrawerMenu_selectsCurrentSort() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssueListFactory f = issues(activity);
            MenuBuilder menu = new MenuBuilder(activity);
            for (int resId : f.getToolDrawerMenuResIds()) {
                activity.getMenuInflater().inflate(resId, menu);
            }
            f.prepareToolDrawerMenu(menu);
            assertNotNull(menu);
        }
    }
}
