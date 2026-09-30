package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.ReleaseRadarFragment;

public class ReleaseRadarFactory extends FragmentFactory {
    private static final int[] TITLES = new int[] {
        R.string.release_radar
    };

    private final String mUserLogin;

    public ReleaseRadarFactory(HomeActivity activity, String userLogin) {
        super(activity);
        mUserLogin = userLogin;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.release_radar;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return ReleaseRadarFragment.newInstance(mUserLogin);
    }
}
