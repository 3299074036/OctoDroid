package com.gh4a.activities.home;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.TrendingFragment;

/**
 * 趋势子页：原内层 3 Tab（今日/本周/本月）改为右上角「时间范围」筛选对话框，
 * 供发现聚合页（DiscoverHubFactory）复用。
 */
public class TrendingFactory extends FragmentFactory {
    public static final int RANGE_DAILY = 0;
    public static final int RANGE_WEEKLY = 1;
    public static final int RANGE_MONTHLY = 2;

    private static final int[] RANGE_TITLES = new int[] {
            R.string.trend_today, R.string.trend_week, R.string.trend_month
    };

    private int mRange = RANGE_DAILY;

    public TrendingFactory(HomeActivity activity) {
        super(activity);
    }

    public int getRange() {
        return mRange;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, R.string.trend_range)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == Menu.FIRST) {
            showRangeDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRangeDialog() {
        final String[] entries = new String[RANGE_TITLES.length];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = mActivity.getString(RANGE_TITLES[i]);
        }
        new androidx.appcompat.app.AlertDialog.Builder(mActivity)
                .setTitle(R.string.trend_range)
                .setSingleChoiceItems(entries, mRange, (dialog, which) -> {
                    dialog.dismiss();
                    if (which != mRange) {
                        mRange = which;
                        mActivity.invalidateFragments();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
