package com.gh4a.activities.home;

import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.TrendingFragment;

/**
 * 趋势子页：时间范围不再是右上角工具栏菜单（0.5.0 摘除）。
 * 范围选择改为趋势列表上方的固定选择行（见 TrendingFragment 的选择行，
 * 仅在 Home 内显示；独立 TrendingActivity 仍用原 3 Tab，不受影响）。
 * 本 Factory 只持有范围状态，点击选择行经 HomeActivity.setTrendRange()
 * 回到这里并刷新页面，筛选逻辑与原来对话框的三选项完全一致。
 */
public class TrendingFactory extends FragmentFactory {
    public static final int RANGE_DAILY = 0;
    public static final int RANGE_WEEKLY = 1;
    public static final int RANGE_MONTHLY = 2;

    private static final String STATE_KEY_RANGE = "trending:range";

    private int mRange = RANGE_DAILY;

    public TrendingFactory(HomeActivity activity) {
        super(activity);
    }

    // 190：mRange 原先不保存，旋转后回落日榜；按 IssueListFactory 同款
    // 存取，恢复值钳制到 0–2，变化时重建页面（与 setTrendRange 同款）。
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_KEY_RANGE, mRange);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        int range = state.getInt(STATE_KEY_RANGE, RANGE_DAILY);
        if (range < RANGE_DAILY || range > RANGE_MONTHLY) {
            range = RANGE_DAILY;
        }
        if (mRange != range) {
            mRange = range;
            mActivity.invalidateFragments();
        }
    }

    public int getRange() {
        return mRange;
    }

    public void setRange(int range) {
        mRange = range;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.trend;
    }

    @Override
    protected int[] getTabTitleResIds() {
        // 聚合后只剩单页（外层 Tab 由 DiscoverHubFactory 提供），单 Tab 时 tab 条自动隐藏
        return new int[] { R.string.trend };
    }

    @Override
    protected Fragment makeFragment(int position) {
        switch (mRange) {
            case RANGE_WEEKLY:
                return TrendingFragment.newInstance(TrendingFragment.TYPE_WEEKLY);
            case RANGE_MONTHLY:
                return TrendingFragment.newInstance(TrendingFragment.TYPE_MONTHLY);
            default:
                return TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY);
        }
    }
}
