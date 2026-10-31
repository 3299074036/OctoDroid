package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.PublicEventListFragment;

/**
 * 0.1.2: Me's 动态 as a Home page, like the notifications page: title in the
 * top green bar (动态, reusing the bottom-tab string — not 公开动态), no back
 * button, bottom nav visible, return via the bottom 我 tab.
 */
public class UserPublicEventsFactory extends FragmentFactory {
    private static final int[] TAB_TITLES = new int[] {
        R.string.feed_hub
    };

    private final String mLogin;

    public UserPublicEventsFactory(HomeActivity activity, String login) {
        super(activity);
        mLogin = login;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.feed_hub;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TAB_TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return PublicEventListFragment.newInstance(mLogin);
    }
}
