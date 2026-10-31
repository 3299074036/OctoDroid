package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.SearchFragment;

/**
 * 0.1.1 搜索聚合页：只剩「搜索」单页；「最近浏览」已拆出到我页功能区，
 * 搜索页不再有历史 Tab（原 RecentHistoryFragment 入口已从搜索摘除）。
 */
public class SearchHubFactory extends FragmentFactory {
    public SearchHubFactory(HomeActivity activity) {
        super(activity);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.search;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return new int[] { R.string.search };
    }

    @Override
    protected Fragment makeFragment(int position) {
        return SearchFragment.newInstance(SearchFragment.SEARCH_TYPE_REPO, null, false);
    }
}
