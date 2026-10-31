package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.RecentHistoryFragment;

/**
 * 0.1.2: Me's 最近浏览 as a Home page, like the notifications page: title in
 * the top green bar, no back button, bottom nav visible, return via the
 * bottom 我 tab.
 */
public class RecentHistoryFactory extends FragmentFactory {
    private static final int[] TAB_TITLES = new int[] {
        R.string.recent_history
    };

    public RecentHistoryFactory(HomeActivity activity) {
        super(activity);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.recent_history;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TAB_TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return RecentHistoryFragment.newInstance();
    }
}
