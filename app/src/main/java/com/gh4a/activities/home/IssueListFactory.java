package com.gh4a.activities.home;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;

import com.gh4a.R;
import com.gh4a.fragment.IssueListFragment;
import com.gh4a.utils.ApiHelpers;

public class IssueListFactory extends FragmentFactory {
    private static final String QUERY = "is:%s is:%s %s:%s";

    private static final String STATE_KEY_SHOWING_CLOSED = "issue:showing_closed";
    private static final String STATE_KEY_ACTION_FILTER = "issue:action_filter";

    /**
     * 内层 4 Tab（创建/指派/提到/参与）在聚合为事项页后改为筛选器：
     * 0=author, 1=assignee, 2=mentions, 3=involves，对应原 TAB_TITLES 顺序。
     */
    private static final int[] ACTION_TITLES = new int[] {
            R.string.created, R.string.assigned, R.string.mentioned, R.string.participating
    };

    private boolean mShowingClosed;
    private int mActionFilter;
    private final String mLogin;
    private final boolean mIsPullRequest;
    private final IssueListFragment.SortDrawerHelper mDrawerHelper =
            new IssueListFragment.SortDrawerHelper();
    private int[] mHeaderColorAttrs;
    private SharedPreferences mPrefs;

    public IssueListFactory(HomeActivity activity, String userLogin, boolean pr,
            SharedPreferences prefs) {
        super(activity);
        mLogin = userLogin;
        mShowingClosed = false;
        mActionFilter = 0;
        mIsPullRequest = pr;
        mPrefs = prefs;

        String lastOrder = mPrefs.getString(getSortOrderPrefKey(), null);
        String lastDir = mPrefs.getString(getSortDirPrefKey(), null);
        if (lastOrder != null && lastDir != null) {
            mDrawerHelper.setSortMode(lastOrder, lastDir);
        }
    }

    @Override
    protected @StringRes int getTitleResId() {
        if (mShowingClosed) {
            return mIsPullRequest ? R.string.pull_requests_closed : R.string.issues_closed;
        } else {
            return mIsPullRequest ? R.string.pull_requests_open : R.string.issues_open;
        }
    }

    @Override
    protected int[] getTabTitleResIds() {
        // 聚合为事项页后只剩单页（外层 Tab 由 IssuesPrsFactory 提供），
        // 单 Tab 时 tab 条自动隐藏
        return new int[] { mIsPullRequest ? R.string.prs_tab : R.string.issues_tab };
    }

    @Override
    protected int[] getHeaderColorAttrs() {
        return mHeaderColorAttrs;
    }

    @Override
    protected Fragment makeFragment(int position) {
        final String action;
        switch (mActionFilter) {
            case 1:
                action = "assignee";
                break;
            case 2:
                action = "mentions";
                break;
            case 3:
                action = "involves";
                break;
            default:
                action = "author";
                break;
        }

        final String query = String.format(QUERY, mIsPullRequest ? "pr" : "issue",
                mShowingClosed ? ApiHelpers.IssueState.CLOSED : ApiHelpers.IssueState.OPEN,
                action, mLogin);

        return IssueListFragment.newInstance(query,
                mDrawerHelper.getSortMode(), mDrawerHelper.getSortOrder(),
                mShowingClosed ? ApiHelpers.IssueState.CLOSED : ApiHelpers.IssueState.OPEN,
                mIsPullRequest ? R.string.no_pull_requests_found : R.string.no_issues_found,
                true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        int resIdState = mShowingClosed ?
                R.string.issues_menu_show_open : R.string.issues_menu_show_closed;
        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, resIdState)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE, R.string.actions)
                .setIcon(R.drawable.menu_overflow_horizontal)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, Menu.FIRST + 2, Menu.NONE, R.string.issues_filter)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case Menu.FIRST:
                toggleStateFilter();
                return true;
            case Menu.FIRST + 1:
                mActivity.toggleToolDrawer();
                return true;
            case Menu.FIRST + 2:
                showActionFilterDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 原内层 4 Tab（创建/指派/提到/参与）改为筛选对话框。 */
    private void showActionFilterDialog() {
        final String[] entries = new String[ACTION_TITLES.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = mActivity.getString(ACTION_TITLES[i]);
        }
        new androidx.appcompat.app.AlertDialog.Builder(mActivity)
                .setTitle(R.string.issues_filter)
                .setSingleChoiceItems(entries, mActionFilter, (dialog, which) -> {
                    dialog.dismiss();
                    if (which != mActionFilter) {
                        mActionFilter = which;
                        reloadIssueList();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected int[] getToolDrawerMenuResIds() {
        return new int[] { IssueListFragment.SortDrawerHelper.getMenuResId() };
    }

    @Override
    protected void prepareToolDrawerMenu(Menu menu) {
        super.prepareToolDrawerMenu(menu);
        mDrawerHelper.updateMenuCheckState(menu);
    }

    @Override
    protected boolean onDrawerItemSelected(MenuItem item) {
        if (mDrawerHelper.handleItemSelection(item)) {
            mPrefs.edit()
                    .putString(getSortOrderPrefKey(), mDrawerHelper.getSortMode())
                    .putString(getSortDirPrefKey(), mDrawerHelper.getSortOrder())
                    .apply();
            reloadIssueList();
            return true;
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_KEY_SHOWING_CLOSED, mShowingClosed);
        outState.putInt(STATE_KEY_ACTION_FILTER, mActionFilter);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        boolean showedClosed = state.getBoolean(STATE_KEY_SHOWING_CLOSED, false);
        int filter = state.getInt(STATE_KEY_ACTION_FILTER, 0);
        if (mShowingClosed != showedClosed || mActionFilter != filter) {
            mShowingClosed = showedClosed;
            mActionFilter = filter;
            reloadIssueList();
            updateHeaderColor();
            mActivity.invalidateTitle();
        }
    }

    private void reloadIssueList() {
        mActivity.invalidateFragments();
    }

    private void toggleStateFilter() {
        mShowingClosed = !mShowingClosed;
        reloadIssueList();
        updateHeaderColor();
        mActivity.invalidateTitle();
        mActivity.supportInvalidateOptionsMenu();
    }

    private void updateHeaderColor() {
        mHeaderColorAttrs = new int[] {
            mShowingClosed ? R.attr.colorIssueClosed : R.attr.colorIssueOpen,
            mShowingClosed ? R.attr.colorIssueClosedDark : R.attr.colorIssueOpenDark
        };
        mActivity.invalidateTabs();
    }

    private String getSortOrderPrefKey() {
        return mIsPullRequest ? "home_pr_list_sort_order" : "home_issue_list_sort_order";
    }

    private String getSortDirPrefKey() {
        return mIsPullRequest ? "home_pr_list_sort_dir" : "home_issue_list_sort_dir";
    }
}
