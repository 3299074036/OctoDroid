package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.RecentHistoryFragment;
import com.gh4a.fragment.SearchFragment;

/**
 * 搜索聚合页：抽屉「搜索」入口（R.id.search 不变），下设「搜索」「历史」双 Tab。
 */
public class SearchHubFactory extends FragmentFactory {
    private static final int TAB_SEARCH = 0;
    private static final int TAB_HISTORY = 1;

    private static final int[] TAB_TITLES = new int[] {
            R.string.search, R.string.recent_history
    };

    public SearchHubFactory(HomeActivity activity) {
        super(activity);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.search;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TAB_TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        if (position == TAB_HISTORY) {
            return RecentHistoryFragment.newInstance();
        }
        return SearchFragment.newInstance(SearchFragment.SEARCH_TYPE_REPO, null, false);
    }
}
