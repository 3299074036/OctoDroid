package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.TopicDiscoveryFragment;

public class TopicDiscoveryFactory extends FragmentFactory {
    private static final int[] TITLES = new int[] {
        R.string.topic_discovery
    };

    public TopicDiscoveryFactory(HomeActivity activity) {
        super(activity);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.topic_discovery;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TITLES;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return TopicDiscoveryFragment.newInstance();
    }
}
