package com.gh4a.activities.home;

import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;

import com.meisolsson.githubsdk.model.User;

public abstract class FragmentFactory {
    protected final HomeActivity mActivity;

    protected FragmentFactory(HomeActivity activity) {
        mActivity = activity;
    }

    protected abstract @StringRes int getTitleResId();
    protected abstract int[] getTabTitleResIds();
    protected abstract Fragment makeFragment(int position);

    /**
     * 切 Tab 时是否刷新 options menu。聚合 Factory（如动态页的组织切换器、
     * 事项页的开/关切换）需要按激活 Tab 重建菜单时返回 true。
     */
    protected boolean refreshMenuOnTabSwitch() {
        return false;
    }

    protected void onFragmentInstantiated(Fragment f, int position) {
    }

    protected void onFragmentDestroyed(Fragment f) {
    }

    protected int[] getHeaderColorAttrs() {
        return null;
    }

    protected int[] getToolDrawerMenuResIds() {
        return null;
    }

    protected void prepareToolDrawerMenu(Menu menu) {

    }

    protected boolean onDrawerItemSelected(MenuItem item) {
        return false;
    }

    protected boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    protected boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    protected void onSaveInstanceState(Bundle outState) {}

    protected void onRestoreInstanceState(Bundle state) {}

    protected void onRefresh() {}

    protected void onDestroy() {}

    protected @IdRes int getInitialToolDrawerSelection() {
        return 0;
    }

    protected void setUserInfo(User user) { }

    protected void onStartLoadingData() {}
}
