package com.gh4a.activities.home;

import android.content.SharedPreferences;
import android.view.MenuItem;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.ReleaseRadarFragment;
import com.gh4a.fragment.StarGroupFragment;
import com.gh4a.fragment.StarredRepositoryListFragment;

/**
 * Star 聚合页：抽屉「Star」入口，下设「星标」「分组」「Release 动态」三 Tab。
 * 星标页的排序菜单逻辑沿用原 BookmarkFactory（排序偏好持久化）。
 */
public class StarHubFactory extends FragmentFactory {
    private static final int TAB_STARRED = 0;
    private static final int TAB_GROUPS = 1;
    public static final int TAB_RADAR = 2;

    private static final int[] TAB_TITLES = new int[] {
            R.string.starred, R.string.star_groups, R.string.release_radar
    };

    private static final String PREF_KEY_SORT_ORDER = "home_starred_list_sort_order";
    private static final String PREF_KEY_SORT_DIR = "home_starred_list_sort_dir";

    private final String mUserLogin;
    private final SharedPreferences mPrefs;
    private StarredRepositoryListFragment mStarredRepoFragment;

    public StarHubFactory(HomeActivity activity, String userLogin, SharedPreferences prefs) {
        super(activity);
        mUserLogin = userLogin;
        mPrefs = prefs;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.star_hub;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TAB_TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        switch (position) {
            case TAB_GROUPS:
                return StarGroupFragment.newInstance();
            case TAB_RADAR:
                return ReleaseRadarFragment.newInstance(mUserLogin);
            default:
                return StarredRepositoryListFragment.newInstance(mUserLogin);
        }
    }

    @Override
    protected void onFragmentInstantiated(Fragment f, int position) {
        if (position == TAB_STARRED) {
            mStarredRepoFragment = (StarredRepositoryListFragment) f;
            loadLastSortOrder();
        }
        super.onFragmentInstantiated(f, position);
    }

    @Override
    protected void onFragmentDestroyed(Fragment f) {
        if (f == mStarredRepoFragment) {
            mStarredRepoFragment = null;
        }
        super.onFragmentDestroyed(f);
    }

    @Override
    protected boolean onOptionsItemSelected(MenuItem item) {
        // 星标页的排序菜单，只在星标 Tab 处理
        if (mActivity.getCurrentTabPosition() == TAB_STARRED
                && mStarredRepoFragment != null
                && mStarredRepoFragment.onOptionsItemSelected(item)) {
            saveLastSortOrder();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadLastSortOrder() {
        String order = mPrefs.getString(PREF_KEY_SORT_ORDER, null);
        String dir = mPrefs.getString(PREF_KEY_SORT_DIR, null);
        if (order != null && dir != null && mStarredRepoFragment != null) {
            mStarredRepoFragment.setSortOrderAndDirection(order, dir);
        }
    }

    private void saveLastSortOrder() {
        if (mStarredRepoFragment == null) {
            return;
        }
        mPrefs.edit()
                .putString(PREF_KEY_SORT_ORDER, mStarredRepoFragment.getSortOrder())
                .putString(PREF_KEY_SORT_DIR, mStarredRepoFragment.getSortDirection())
                .apply();
    }
}
