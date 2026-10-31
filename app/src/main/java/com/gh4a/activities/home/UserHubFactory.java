package com.gh4a.activities.home;

import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.fragment.MeFragment;

/**
 * 0.1.0 “我”内嵌页：单页、无顶部 Tab（TabLayout 在只有 1 页时自动隐藏）。
 * 不再是 0.5.0 的关于 / 公开动态两 Tab，不再有底部仓库区。
 * 内容为 MeFragment：头像 + 计数格（仓库 / 关注者 / 正在关注 / 动态，均可点）
 * + 功能区（通知 / 事项 / Topic 发现）+ 下方内联设置（按 settings.xml 原分组）。
 * 动态流不再是 Tab：点计数格的“动态”在本页内嵌打开公开动态流。
 * UserActivity（他人资料页）不受影响，仍用原来的 UserFragment 两 Tab。
 */
public class UserHubFactory extends FragmentFactory {
    private final String mUserLogin;

    public UserHubFactory(HomeActivity activity, String userLogin) {
        super(activity);
        mUserLogin = userLogin;
    }

    @Override
    protected @StringRes int getTitleResId() {
        return R.string.my_profile;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return new int[] { R.string.my_profile };
    }

    @Override
    protected Fragment makeFragment(int position) {
        return MeFragment.newInstance(mUserLogin);
    }
}
