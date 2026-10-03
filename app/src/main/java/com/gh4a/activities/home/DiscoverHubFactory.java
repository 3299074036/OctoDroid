package com.gh4a.activities.home;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;

/**
 * 发现聚合页：抽屉「发现」入口，下设「趋势」「话题」双 Tab。
 * 趋势页原内层 3 Tab（今日/本周/本月）已改为右上角「时间范围」筛选对话框。
 */
public class DiscoverHubFactory extends FragmentFactory {
    private static final int TAB_TREND = 0;
    private static final int TAB_TOPICS = 1;

    private static final int[] TAB_TITLES = new int[] {
            R.string.trend, R.string.topic_discovery
    };

    private final TrendingFactory mTrendingFactory;
    private final TopicDiscoveryFactory mTopicsFactory;

    public DiscoverHubFactory(HomeActivity activity) {
        super(activity);
        mTrendingFactory = new TrendingFactory(activity);
        mTopicsFactory = new TopicDiscoveryFactory(activity);
    }

    private FragmentFactory activeFactory() {
        return factoryForPosition(mActivity.getCurrentTabPosition());
    }

    private FragmentFactory factoryForPosition(int position) {
        if (position == TAB_TOPICS) {
            return mTopicsFactory;
        }
        return mTrendingFactory;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.discover_hub;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TAB_TITLES;
    }

    @Override
    protected boolean refreshMenuOnTabSwitch() {
        return true;
    }

    @Override
    protected Fragment makeFragment(int position) {
        return factoryForPosition(position).makeFragment(0);
    }

    @Override
    protected void onFragmentInstantiated(Fragment f, int position) {
        factoryForPosition(position).onFragmentInstantiated(f, position);
    }

    @Override
    protected boolean onCreateOptionsMenu(Menu menu) {
        return activeFactory().onCreateOptionsMenu(menu);
    }

    @Override
    protected boolean onOptionsItemSelected(MenuItem item) {
        return activeFactory().onOptionsItemSelected(item);
    }
}
