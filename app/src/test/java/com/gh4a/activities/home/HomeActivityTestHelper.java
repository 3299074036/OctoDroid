package com.gh4a.activities.home;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

/**
 * home/ 测试共用的启动辅助：先在应用 prefs 里种一个登录账号
 * （HomeActivity.onCreate 会调 UserService.getUser(login)；login 为 null
 * 时 Retrofit @Path 校验在主线程直接抛 IllegalArgumentException，
 * 未登录启动即崩——见 HomeActivityTest），再启动 HomeActivity。
 * Robolectric 每测试重建 Application/prefs，故每测都要重新种。
 */
public final class HomeActivityTestHelper {

    private HomeActivityTestHelper() {
    }

    public static void seedLogin(String login) {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("active_login", login).apply();
    }

    public static void clearLogin() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove("active_login").apply();
    }

    public static ActivityController<HomeActivity> launchHome() {
        seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        return Robolectric.buildActivity(HomeActivity.class,
                HomeActivity.makeIntent(context, R.id.discover_hub));
    }
}
