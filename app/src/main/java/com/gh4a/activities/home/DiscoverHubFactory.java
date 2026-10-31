package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;

/**
 * 趋势页（0.5.0 起原“发现”聚合页改名）：顶部只剩“趋势仓库”一页，
 * “TOPIC 发现”已摘出，改为抽屉里的独立菜单项（见 HomeActivity 的
 * topic_discovery 工厂与 DrawerManager），本页不再含话题 Tab。
 * 标题与抽屉/底部文案统一用 @string/discover_hub（现为“趋势”）。
 */
public class DiscoverHubFactory extends FragmentFactory {
    private final TrendingFactory mTrendingFactory;

    public DiscoverHubFactory(HomeActivity activity) {
        super(activity);
        mTrendingFactory = new TrendingFactory(activity);
    }

    public int getTrendRange() {
        return mTrendingFactory.getRange();
    }

    public void setTrendRange(int range) {
        mTrendingFactory.setRange(range);
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.discover_hub;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return new int[] { R.string.trend };
    }

    @Override
    protected Fragment makeFragment(int position) {
        return mTrendingFactory.makeFragment(0);
    }

    @Override
    protected void onFragmentInstantiated(Fragment f, int position) {
        mTrendingFactory.onFragmentInstantiated(f, position);
    }
}
