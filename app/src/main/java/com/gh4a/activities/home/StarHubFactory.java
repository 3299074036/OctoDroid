package com.gh4a.activities.home;

import android.content.SharedPreferences;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.StarMergedFragment;

/**
 * 0.1.1 Star 分段 3 页：分段控件切换我的列表 / 已加星标 / STAR 更新独立页。
 * 单 Tab（TabLayout 在只有 1 页时自动隐藏，见 BasePagerActivity），
 * 内容为 StarMergedFragment：我的列表 / 已加星标（全部）/ STAR 更新
 * 三区单页滚动，页内 chips 跳转。排序菜单与长按归属逻辑由合并页
 * 内部处理（与 0.5.0 的星标页一致），数据接口未改。
 * 旧的 StarredRepositoryListFragment / ReleaseRadarFragment 保留在树里，
 * 不再由 Home 引用（其他入口若有引用不受影响）。
 */
public class StarHubFactory extends FragmentFactory {
    /**
     * 0.1.0：Star 已是单页，不再有 Tab；此常量仅保留给 ReleaseRadarWorker
     * 编译引用（通知深链仍带 initial_tab=1，HomeActivity 会将其钳制到
     * 单页范围内，不再切 Tab）。
     */
    public static final int TAB_RADAR = 1;

    private final String mUserLogin;

    public StarHubFactory(HomeActivity activity, String userLogin, SharedPreferences prefs) {
        super(activity);
        mUserLogin = userLogin;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.star_hub;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return new int[] { R.string.star_hub };
    }

    @Override
    protected Fragment makeFragment(int position) {
        return StarMergedFragment.newInstance(mUserLogin);
    }
}
