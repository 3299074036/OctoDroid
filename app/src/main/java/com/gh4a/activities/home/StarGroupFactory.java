package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.StarGroupFragment;

public class StarGroupFactory extends FragmentFactory {
    private static final int[] TITLES = new int[] {
        R.string.star_groups
    };

    public StarGroupFactory(HomeActivity activity) {
        super(activity);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.star_groups;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return StarGroupFragment.newInstance();
    }
}
