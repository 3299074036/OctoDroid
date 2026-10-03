package com.gh4a.activities.home;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;

/**
 * 动态聚合页：抽屉「动态」入口，下设「关注」「全站」双 Tab。
 * 分别复用 NewsFeedFactory（关注的人/组织的动态，带组织切换器）
 * 与 TimelineFactory（全站公共时间线），Fragment 零改动。
 *
 * 菜单、生命周期等调用按当前 Tab 委托给对应的子 Factory；
 * 组织切换器只在「关注」Tab 显示，切 Tab 时 HomeActivity 会刷新菜单。
 */
public class FeedHubFactory extends FragmentFactory {
    private static final int TAB_FOLLOWING = 0;
    private static final int TAB_PUBLIC = 1;

    private static final int[] TAB_TITLES = new int[] {
            R.string.feed_tab_following, R.string.feed_tab_public
    };

    private final NewsFeedFactory mFollowingFactory;
    private final TimelineFactory mPublicFactory;

    public FeedHubFactory(HomeActivity activity, String userLogin) {
        super(activity);
        mFollowingFactory = new NewsFeedFactory(activity, userLogin);
        mPublicFactory = new TimelineFactory(activity);
    }

    private FragmentFactory activeFactory() {
        return factoryForPosition(mActivity.getCurrentTabPosition());
    }

    private FragmentFactory factoryForPosition(int position) {
        return position == TAB_PUBLIC ? mPublicFactory : mFollowingFactory;
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
    protected void setUserInfo(User user) {
        // 组织切换器需要用户信息，只有关注页用得到
        mFollowingFactory.setUserInfo(user);
    }

    @Override
    protected boolean onCreateOptionsMenu(Menu menu) {
        return activeFactory().onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStartLoadingData() {
        mFollowingFactory.onStartLoadingData();
    }

    @Override
    protected boolean onOptionsItemSelected(MenuItem item) {
        return activeFactory().onOptionsItemSelected(item);
    }

    @Override
    protected void onRefresh() {
        activeFactory().onRefresh();
    }

    @Override
    protected void onDestroy() {
        mFollowingFactory.onDestroy();
    }
}
