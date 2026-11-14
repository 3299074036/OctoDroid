package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.IssueListFragment;
import android.view.Menu;
import static org.mockito.Mockito.mock;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class IssuesPrsFactoryTest {

    private android.content.SharedPreferences prefs() {
        return ApplicationProvider.getApplicationContext().getSharedPreferences(
                "test-issues-prs-prefs", Context.MODE_PRIVATE);
    }

    @Test
    public void tabPrsConstant_isOne() {
        assertEquals(1, IssuesPrsFactory.TAB_PRS);
    }

    @Test
    public void titleAndTabs() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            assertEquals(R.string.my_items, factory.getTitleResId());
            int[] tabs = factory.getTabTitleResIds();
            assertEquals(2, tabs.length);
            assertEquals(R.string.issues_tab, tabs[0]);
            assertEquals(R.string.prs_tab, tabs[1]);
        }
    }

    @Test
    public void refreshMenuOnTabSwitch_isTrue() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            assertTrue(factory.refreshMenuOnTabSwitch());
        }
    }

    @Test
    public void makeFragment_issuesAndPrsQueries() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            IssueListFragment issues = (IssueListFragment) factory.makeFragment(0);
            assertEquals("is:issue is:open author:octocat",
                    issues.getArguments().getString("query"));
            IssueListFragment prs = (IssueListFragment) factory.makeFragment(1);
            assertEquals("is:pr is:open author:octocat",
                    prs.getArguments().getString("query"));
        }
    }

    @Test
    public void onCreateOptionsMenu_delegatesToActiveFactory() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            MenuBuilder menu = new MenuBuilder(activity);
            // 内层 IssueListFactory 的 onCreateOptionsMenu 恒返回 false，
            // 但三项照常加上
            assertFalse(factory.onCreateOptionsMenu(menu));
            // IssueListFactory 固定加 3 项
            assertEquals(3, menu.size());
        }
    }

    @Test
    public void saveAndRestore_preservesClosedStatePerTab() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            // 把 issues 页切到 closed：菜单第 0 项即切换开/关
            MenuBuilder menu = new MenuBuilder(activity);
            factory.onCreateOptionsMenu(menu);
            factory.onOptionsItemSelected(menu.findItem(android.view.Menu.FIRST));
            Bundle out = new Bundle();
            factory.onSaveInstanceState(out);
            assertTrue(out.getBundle("issues_prs_issues")
                    .getBoolean("issue:showing_closed", false));
            assertTrue(out.containsKey("issues_prs_prs"));
            // 恢复到新工厂：issues 页的 query 应变为 closed
            IssuesPrsFactory restored = new IssuesPrsFactory(activity, "octocat", prefs());
            restored.onRestoreInstanceState(out);
            IssueListFragment restoredIssues =
                    (IssueListFragment) restored.makeFragment(0);
            assertEquals("is:issue is:closed author:octocat",
                    restoredIssues.getArguments().getString("query"));
            // PR 页未动，仍为 open
            IssueListFragment restoredPrs = (IssueListFragment) restored.makeFragment(1);
            assertEquals("is:pr is:open author:octocat",
                    restoredPrs.getArguments().getString("query"));
        }
    }

    @Test
    public void restore_withMissingBundles_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            factory.onRestoreInstanceState(new Bundle());
            assertEquals(R.string.my_items, factory.getTitleResId());
            // 内层 issues 工厂未动：仍为 open
            IssueListFragment issues = (IssueListFragment) factory.makeFragment(0);
            assertEquals("is:issue is:open author:octocat",
                    issues.getArguments().getString("query"));
        }
    }

    @Test
    public void getHeaderColorAttrs_delegatesToActiveFactory() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            // open 态 header 为 null（updateHeaderColor 只在切换时设）
            assertNull(factory.getHeaderColorAttrs());
        }
    }

    @Test
    public void onFragmentInstantiated_delegatesWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            factory.onFragmentInstantiated(mock(IssueListFragment.class), 0);
            assertNotNull(factory);
        }
    }

    @Test
    public void onRefresh_delegatesWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            factory.onRefresh();
            assertNotNull(factory);
        }
    }

    @Test
    public void getToolDrawerMenuResIds_delegatesToActiveFactory() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            int[] ids = factory.getToolDrawerMenuResIds();
            assertNotNull(ids);
            assertEquals(1, ids.length);
        }
    }

    @Test
    public void onDrawerItemSelected_delegatesToActiveFactory() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            MenuBuilder menu = new MenuBuilder(activity);
            for (int resId : factory.getToolDrawerMenuResIds()) {
                activity.getMenuInflater().inflate(resId, menu);
            }
            assertTrue(factory.onDrawerItemSelected(menu.findItem(R.id.sort_created_desc)));
        }
    }

    @Test
    public void onOptionsItemSelected_first_togglesStateFilter() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            IssuesPrsFactory factory = new IssuesPrsFactory(activity, "octocat", prefs());
            MenuBuilder menu = new MenuBuilder(activity);
            factory.onCreateOptionsMenu(menu);
            assertEquals(activity.getString(R.string.issues_menu_show_closed),
                    menu.findItem(Menu.FIRST).getTitle().toString());
            assertTrue(factory.onOptionsItemSelected(menu.findItem(Menu.FIRST)));
            MenuBuilder menu2 = new MenuBuilder(activity);
            factory.onCreateOptionsMenu(menu2);
            assertEquals(activity.getString(R.string.issues_menu_show_open),
                    menu2.findItem(Menu.FIRST).getTitle().toString());
        }
    }
}
