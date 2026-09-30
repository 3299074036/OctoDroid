package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.RecentHistoryFragment;

public class RecentHistoryFactory extends FragmentFactory {
    private static final int[] TITLES = new int[] {
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
        return TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return RecentHistoryFragment.newInstance();
    }
}
