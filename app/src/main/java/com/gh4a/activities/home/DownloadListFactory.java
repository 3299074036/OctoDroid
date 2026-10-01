package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.DownloadListFragment;

public class DownloadListFactory extends FragmentFactory {
    private static final int[] TITLES = new int[] {
        R.string.download_manager
    };

    public DownloadListFactory(HomeActivity activity) {
        super(activity);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.download_manager;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return DownloadListFragment.newInstance();
    }
}
