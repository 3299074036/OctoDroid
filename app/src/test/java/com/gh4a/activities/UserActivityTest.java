package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import androidx.appcompat.view.menu.MenuBuilder;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;

@RunWith(RobolectricTestRunner.class)
public class UserActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    // ---- makeIntent ----

    @Test
    public void makeIntent_nullLogin_returnsNull() {
        assertNull(UserActivity.makeIntent(context(), (String) null));
    }

    @Test
    public void makeIntent_login_carriesExtra() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        assertEquals(UserActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("octocat", intent.getStringExtra("login"));
    }

    @Test
    public void makeIntent_nullUser_returnsNull() {
        assertNull(UserActivity.makeIntent(context(), (User) null));
    }

    @Test
    public void makeIntent_userUsesLoginOnly() {
        User user = User.builder().login("octocat").name("The Octocat").build();
        Intent intent = UserActivity.makeIntent(context(), user);
        assertNotNull(intent);
        assertEquals("octocat", intent.getStringExtra("login"));
    }

    @Test
    public void makeIntent_mannequinUser_returnsNull() {
        User mannequin = User.builder().login("ghost").type(UserType.Mannequin).build();
        assertNull(UserActivity.makeIntent(context(), mannequin));
    }

    @Test
    public void makeIntent_userWithNullLogin_returnsNull() {
        User user = User.builder().build();
        assertNull(UserActivity.makeIntent(context(), user));
    }

    @Test
    public void makeIntent_botUser_notMannequin_returnsIntent() {
        User bot = User.builder().login("bot[bot]").type(UserType.Bot).build();
        assertNotNull(UserActivity.makeIntent(context(), bot));
    }

    // ---- Activity 行为（离线：loadUser 失败走错误页，不崩） ----

    @Test
    public void launches_titleIsLogin() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            assertEquals("octocat",
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void getActionBarTitle_stripsBotSuffix() {
        Intent intent = UserActivity.makeIntent(context(), "dependabot[bot]");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            assertEquals("dependabot", activity.getActionBarTitle());
            assertEquals("dependabot",
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void getActionBarTitle_nonBotLoginUnchanged() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            assertEquals("octocat", activity.getActionBarTitle());
        }
    }

    @Test
    public void getTabTitleResIds_hasTwoTabs() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            int[] titles = activity.getTabTitleResIds();
            assertEquals(2, titles.length);
            assertEquals(R.string.about, titles[0]);
            assertEquals(R.string.user_public_activity, titles[1]);
        }
    }

    @Test
    public void makeFragment_positions() throws Exception {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            // loadUser 未完成时 mUser 为 null：第 0 页以 null User 建实例无妨；
            // 第 1 页 PublicEventListFragment.newInstance(User) 直接取
            // user.login()，null 时 NPE（页签只在加载后才建，UI 上不可达，
            // 此处只把前置条件钉死，见 A2 备注）。
            assertTrue(activity.makeFragment(0)
                    instanceof com.gh4a.fragment.UserFragment);
            // loadUser 是异步网络调用；测试 JVM 经 MITM 代理可达 api.github.com，
            // loadUser 可能已完成（mUser 非 null）也可能未完成。两种状态都断言：
            java.lang.reflect.Field userField =
                    UserActivity.class.getDeclaredField("mUser");
            userField.setAccessible(true);
            if (userField.get(activity) == null) {
                // 未加载：第 1 页 PublicEventListFragment.newInstance(User)
                // 直接取 user.login()，null 时 NPE（页签只在加载后才建，
                // UI 上不可达，此处只把前置条件钉死，见 A2 备注）。
                try {
                    activity.makeFragment(1);
                    throw new AssertionError("expected NullPointerException");
                } catch (NullPointerException expected) {
                    // expected
                }
            } else {
                assertTrue(activity.makeFragment(1)
                        instanceof com.gh4a.fragment.PublicEventListFragment);
            }
            assertNull(activity.makeFragment(2));
        }
    }

    @Test
    public void displayDetachAction_isTrue() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            assertTrue(activity.displayDetachAction());
        }
    }

    @Test
    public void onOptionsItemSelected_browser_noBrowserInstalledShowsToast() {
        // Robolectric 下无浏览器可解析：launchBrowser 回落到 no_browser_found Toast。
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem browserItem = menu.findItem(R.id.browser);
            assertNotNull(browserItem);
            assertTrue(activity.onOptionsItemSelected(browserItem));
            assertEquals(activity.getString(R.string.no_browser_found),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void onOptionsItemSelected_share_startsShareIntent() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem shareItem = menu.findItem(R.id.share);
            assertNotNull(shareItem);
            assertTrue(activity.onOptionsItemSelected(shareItem));
            ShadowActivity shadow = Shadows.shadowOf(activity);
            Intent started = shadow.getNextStartedActivity();
            assertNotNull(started);
            assertEquals(Intent.ACTION_CHOOSER, started.getAction());
        }
    }

    @Test
    public void navigateUp_returnsToplevelIntent() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            Intent up = activity.navigateUp();
            assertNotNull(up);
            assertEquals(Github4AndroidActivity.class.getName(),
                    up.getComponent().getClassName());
        }
    }

    @Test
    public void onRefresh_doesNotCrash() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }

    // ---- A2 第二轮补缺口：UserActivity ----

    private static java.lang.reflect.Field a2FindField(Class<?> cls, String name) throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object a2GetField(Object target, String name) throws Exception {
        java.lang.reflect.Field f = a2FindField(target.getClass(), name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void a2SetField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = a2FindField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void onFragmentDestroyed_userFragment_clearsField() throws Exception {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            com.gh4a.fragment.UserFragment frag =
                    mock(com.gh4a.fragment.UserFragment.class);
            a2SetField(activity, "mUserFragment", frag);
            activity.onFragmentDestroyed(frag);
            assertNull(a2GetField(activity, "mUserFragment"));
            a2SetField(activity, "mUserFragment", frag);
            activity.onFragmentDestroyed(mock(com.gh4a.fragment.UserFragment.class));
            assertNotNull(a2GetField(activity, "mUserFragment"));
        }
    }

    @Test
    public void onOptionsItemSelected_share_withLoadedUser_usesNameSubject() throws Exception {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            User user = mock(User.class);
            when(user.name()).thenReturn("Octo Cat");
            a2SetField(activity, "mUser", user);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            android.view.MenuItem shareItem = menu.findItem(R.id.share);
            assertNotNull(shareItem);
            assertTrue(activity.onOptionsItemSelected(shareItem));
            Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(Intent.ACTION_CHOOSER, started.getAction());
            Intent inner = started.getParcelableExtra(Intent.EXTRA_INTENT);
            assertNotNull(inner);
            assertTrue(inner.getStringExtra(Intent.EXTRA_SUBJECT).contains("Octo Cat"));
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onOptionsItemSelected(menu.findItem(999999)));
        }
    }


    // ---- A2 第四轮补缺口：UserActivity ----

    @Test
    public void makeFragment_withLoadedUser_returnsPublicEvents() throws Exception {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            User user = mock(User.class);
            when(user.login()).thenReturn("octocat");
            a2SetField(activity, "mUser", user);
            assertTrue(activity.makeFragment(1)
                    instanceof com.gh4a.fragment.PublicEventListFragment);
        }
    }

    @Test
    public void onFragmentInstantiated_positionZero_setsUserFragment() throws Exception {
        Intent intent = UserActivity.makeIntent(context(), "octocat");
        try (ActivityController<UserActivity> c =
                Robolectric.buildActivity(UserActivity.class, intent)) {
            UserActivity activity = c.setup().get();
            com.gh4a.fragment.UserFragment frag =
                    mock(com.gh4a.fragment.UserFragment.class);
            activity.onFragmentInstantiated(frag, 0);
            assertEquals(frag, a2GetField(activity, "mUserFragment"));
        }
    }

}
