package com.gh4a.activities.home;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;

/**
 * 事项聚合页：抽屉「事项」入口，下设「议题」「拉取请求」双 Tab，
 * 分别复用 IssueListFactory（pr=false/true）。
 * 原内层 4 Tab（创建/指派/提到/参与）已改为各子页右上角「筛选」对话框。
 */
public class IssuesPrsFactory extends FragmentFactory {
    public static final int TAB_ISSUES = 0;
    public static final int TAB_PRS = 1;

    private static final int[] TAB_TITLES = new int[] {
            R.string.issues_tab, R.string.prs_tab
    };

    private final IssueListFactory mIssuesFactory;
    private final IssueListFactory mPrsFactory;

    public IssuesPrsFactory(HomeActivity activity, String userLogin, SharedPreferences prefs) {
        super(activity);
        mIssuesFactory = new IssueListFactory(activity, userLogin, false, prefs);
        mPrsFactory = new IssueListFactory(activity, userLogin, true, prefs);
    }

    private FragmentFactory activeFactory() {
        return factoryForPosition(mActivity.getCurrentTabPosition());
    }

    private FragmentFactory factoryForPosition(int position) {
        return position == TAB_PRS ? mPrsFactory : mIssuesFactory;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.my_items;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TAB_TITLES;
    }

    @Override
    protected boolean refreshMenuOnTabSwitch() {
        return true;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return factoryForPosition(position).makeFragment(0);
    }

    @Override
    protected void onFragmentInstantiated(Fragment f, int position) {
        factoryForPosition(position).onFragmentInstantiated(f, position);
    }

    @Override
    protected boolean onCreateOptionsMenu(Menu menu) {
        return activeFactory().onCreateOptionsMenu(menu);
    }

    @Override
    protected boolean onOptionsItemSelected(MenuItem item) {
        return activeFactory().onOptionsItemSelected(item);
    }

    @Override
    protected int[] getToolDrawerMenuResIds() {
        return activeFactory().getToolDrawerMenuResIds();
    }

    @Override
    protected void prepareToolDrawerMenu(Menu menu) {
        activeFactory().prepareToolDrawerMenu(menu);
    }

    @Override
    protected boolean onDrawerItemSelected(MenuItem item) {
        return activeFactory().onDrawerItemSelected(item);
    }

    @Override
    protected int[] getHeaderColorAttrs() {
        return activeFactory().getHeaderColorAttrs();
    }

    @Override
    protected void onRefresh() {
        activeFactory().onRefresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Bundle issues = new Bundle();
        mIssuesFactory.onSaveInstanceState(issues);
        outState.putBundle("issues_prs_issues", issues);
        Bundle prs = new Bundle();
        mPrsFactory.onSaveInstanceState(prs);
        outState.putBundle("issues_prs_prs", prs);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        Bundle issues = state.getBundle("issues_prs_issues");
        if (issues != null) {
            mIssuesFactory.onRestoreInstanceState(issues);
        }
        Bundle prs = state.getBundle("issues_prs_prs");
        if (prs != null) {
            mPrsFactory.onRestoreInstanceState(prs);
        }
    }
}
