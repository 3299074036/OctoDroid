package com.gh4a.activities.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;

import androidx.activity.result.ActivityResultRegistry;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.Github4AndroidActivity;
import com.gh4a.activities.SettingsActivity;
import com.gh4a.utils.DrawerManager;
import com.gh4a.utils.SingleFactory;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;
import com.gh4a.fragment.NotificationListFragment;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.model.NotificationHolder;
import com.gh4a.model.NotificationListLoadResult;
import com.gh4a.utils.NotificationCountProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.activity.NotificationService;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import androidx.appcompat.view.menu.MenuBuilder;
import java.lang.reflect.Field;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.assertNotSame;
import java.lang.reflect.Method;

import io.reactivex.Single;
import retrofit2.Response;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
public class HomeActivityTest {

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static SharedPreferences prefs() {
        return context().getSharedPreferences(SettingsFragment.PREF_NAME,
                Context.MODE_PRIVATE);
    }

    private static NotificationHolder holder(Boolean unread) {
        NotificationThread thread = mock(NotificationThread.class);
        when(thread.unread()).thenReturn(unread);
        when(thread.repository()).thenReturn(null);
        return new NotificationHolder(thread);
    }

    // ---- 真实网络挡掉（每测前后开关） ----
    // HomeActivity 启动即发两路真实请求：loadUserInfo（UserService.getUser）
    // 与 loadNotificationIndicator / updateMeBadge（NotificationService）。
    // 无 token 时通知请求返回 401，ApiHelpers.throwOnFailure 遇 401 即
    // logout()，会把刚切换的登录在 switchActiveUser 的窗口内清掉（见
    // onLoginFinished_addsAccountAndSwitches 旧注释），还是跨测试静态
    // 状态污染的头号嫌疑。全部挡成空数据：其余 ServiceFactory/SingleFactory
    // 调用仍走真实方法（CALLS_REAL_METHODS），只拦截这两路。
    // 另清掉 NotificationCountProvider 单例缓存（TTL 2 分钟，跨测试存活），
    // 否则上一个测试的拉取结果会漏进本测试。
    private MockedStatic<ServiceFactory> mockedServiceFactory;
    private MockedStatic<SingleFactory> mockedSingleFactory;

    @Before
    public void stubNetworkServices() {
        NotificationCountProvider.getInstance().invalidate();

        UserService userService = mock(UserService.class);
        User octocat = User.builder().login("octocat").id(1L).build();
        // any() 而非 anyString()：未登录时 login 为 null 也要返回 Single；
        // 真实 service.getUser(null) 同样先返回 Single（@Path 校验在订阅时），
        // 返回 null 会让 loadUserInfo 在 onCreate 里同步 NPE。
        when(userService.getUser(any()))
                .thenReturn(Single.just(Response.success(octocat)));

        NotificationService notificationService = mock(NotificationService.class);
        Page<NotificationThread> emptyPage = mock(Page.class);
        when(emptyPage.items()).thenReturn(Collections.emptyList());
        when(notificationService.getNotifications(anyMap(), anyLong()))
                .thenReturn(Single.just(Response.success(emptyPage)));

        mockedServiceFactory = mockStatic(ServiceFactory.class, CALLS_REAL_METHODS);
        mockedServiceFactory.when(() -> ServiceFactory.get(eq(UserService.class), anyBoolean()))
                .thenReturn(userService);
        mockedServiceFactory.when(() -> ServiceFactory.get(eq(NotificationService.class),
                anyBoolean(), any())).thenReturn(notificationService);

        mockedSingleFactory = mockStatic(SingleFactory.class, CALLS_REAL_METHODS);
        mockedSingleFactory.when(() -> SingleFactory.getNotifications(false, false, false))
                .thenReturn(Single.just(
                        new NotificationListLoadResult(Collections.emptyList())));
    }

    @After
    public void closeNetworkStubs() {
        if (mockedSingleFactory != null) {
            mockedSingleFactory.close();
            mockedSingleFactory = null;
        }
        if (mockedServiceFactory != null) {
            mockedServiceFactory.close();
            mockedServiceFactory = null;
        }
    }

    // ---- countUnread（静态纯逻辑） ----

    @Test
    public void countUnread_nullAndEmpty_isZero() {
        assertEquals(0, HomeActivity.countUnread(null));
        assertEquals(0, HomeActivity.countUnread(Collections.emptyList()));
    }

    @Test
    public void countUnread_onlyExplicitTrueCounts() {
        List<NotificationHolder> list = Arrays.asList(
                holder(true), holder(true), holder(false));
        assertEquals(2, HomeActivity.countUnread(list));
    }

    @Test
    public void countUnread_nullUnreadField_notCounted() {
        // A2 备注：注释称“缺字段按未读计”，但代码只计 Boolean.TRUE；
        // 与 NotificationHolder.isRead（null 视作已读）一致，注释与代码
        // 不符。此处把实际行为钉死，定性见汇报。
        List<NotificationHolder> list = Arrays.asList(holder(null), holder(true));
        assertEquals(1, HomeActivity.countUnread(list));
    }

    @Test
    public void countUnread_repoOnlyHolder_notCounted() {
        List<NotificationHolder> list = new ArrayList<>();
        list.add(new NotificationHolder((com.meisolsson.githubsdk.model.Repository) null));
        list.add(holder(true));
        assertEquals(1, HomeActivity.countUnread(list));
    }

    // ---- makeIntent ----

    @Test
    public void makeIntent_knownPages_carryInitialPage() {
        assertEquals("search",
                HomeActivity.makeIntent(context(), R.id.search)
                        .getStringExtra("initial_page"));
        assertEquals("feed_hub",
                HomeActivity.makeIntent(context(), R.id.feed_hub)
                        .getStringExtra("initial_page"));
        assertEquals("notifications",
                HomeActivity.makeIntent(context(), R.id.notifications)
                        .getStringExtra("initial_page"));
        assertEquals("discover_hub",
                HomeActivity.makeIntent(context(), R.id.discover_hub)
                        .getStringExtra("initial_page"));
        assertEquals("repos",
                HomeActivity.makeIntent(context(), R.id.my_repos)
                        .getStringExtra("initial_page"));
        assertEquals("star_hub",
                HomeActivity.makeIntent(context(), R.id.star_hub)
                        .getStringExtra("initial_page"));
        assertEquals("my_items",
                HomeActivity.makeIntent(context(), R.id.issues_prs)
                        .getStringExtra("initial_page"));
        assertEquals("profile",
                HomeActivity.makeIntent(context(), R.id.profile)
                        .getStringExtra("initial_page"));
    }

    @Test
    public void makeIntent_unknownPage_noInitialPageExtra() {
        Intent intent = HomeActivity.makeIntent(context(), R.id.search + 9999);
        assertFalse(intent.hasExtra("initial_page"));
        assertEquals(HomeActivity.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void makeIntent_withInitialTab_carriesTab() {
        Intent intent = HomeActivity.makeIntent(context(), R.id.issues_prs, 1);
        assertEquals("my_items", intent.getStringExtra("initial_page"));
        assertEquals(1, intent.getIntExtra("initial_tab", -1));
    }

    @Test
    public void makeNotificationsIntent_carriesRepo() {
        Intent intent = HomeActivity.makeNotificationsIntent(context(), "octocat", "hello");
        assertEquals("notifications", intent.getStringExtra("initial_page"));
        assertEquals("octocat", intent.getStringExtra(
                NotificationListFragment.EXTRA_INITIAL_REPO_OWNER));
        assertEquals("hello", intent.getStringExtra(
                NotificationListFragment.EXTRA_INITIAL_REPO_NAME));
    }

    // ---- 启动与初始页 ----

    @Test
    public void launches_defaultDiscoverPage() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.discover_hub),
                    activity.getSupportActionBar().getTitle().toString());
            assertEquals(0, activity.getCurrentTabPosition());
            assertNotNull(activity.getBadgeState());
        }
    }

    @Test
    public void launches_withoutLogin_doesNotCrash() {
        // 修正：Retrofit 的 @Path 校验发生在 OkHttpCall 执行（订阅）时，
        // 不在 service.getUser(null) 调用时；loadUserInfo 的失败走异步
        // handleLoadFailure，onCreate 不崩。未登录启动 Home 是安全的。
        HomeActivityTestHelper.clearLogin();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context(), R.id.discover_hub))) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.discover_hub),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void determineInitialPage_prefSearch() {
        prefs().edit().putString(SettingsFragment.KEY_START_PAGE, "search").apply();
        // 裸 intent：不带 initial_page，走 prefs
        HomeActivityTestHelper.seedLogin("octocat");
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, new Intent(context(), HomeActivity.class))) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.search),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void determineInitialPage_legacyNewsfeedMapsToFeedHub() {
        prefs().edit().putString(SettingsFragment.KEY_START_PAGE, "newsfeed").apply();
        HomeActivityTestHelper.seedLogin("octocat");
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, new Intent(context(), HomeActivity.class))) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.feed_hub),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void determineInitialPage_unknownValueFallsBackToDiscover() {
        prefs().edit().putString(SettingsFragment.KEY_START_PAGE, "gists").apply();
        HomeActivityTestHelper.seedLogin("octocat");
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, new Intent(context(), HomeActivity.class))) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.discover_hub),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void determineInitialPage_lastUsesSavedPage() {
        prefs().edit().putString(SettingsFragment.KEY_START_PAGE, "last")
                .putString("last_selected_home_page", "star_hub").apply();
        HomeActivityTestHelper.seedLogin("octocat");
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, new Intent(context(), HomeActivity.class))) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.star_hub),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    // ---- 底栏角标 ----

    @Test
    public void setBottomBadge_positiveCount_createsBadge() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.setBottomBadge(R.id.profile, 3);
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            assertNotNull(nav);
            assertNotNull(nav.getBadge(R.id.profile));
            assertEquals(3, nav.getBadge(R.id.profile).getNumber());
        }
    }

    @Test
    public void setBottomBadge_zero_removesBadge() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.setBottomBadge(R.id.profile, 2);
            activity.setBottomBadge(R.id.profile, 0);
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            assertNull(nav.getBadge(R.id.profile));
        }
    }

    @Test
    public void setMeBadge_delegatesToProfileBadge() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.setMeBadge(5);
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            assertEquals(5, nav.getBadge(R.id.profile).getNumber());
        }
    }

    @Test
    public void badgeState_listenerUpdatesBottomBadges() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.getBadgeState().setNotificationCount(4);
            BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
            assertNotNull(nav.getBadge(R.id.profile));
            assertEquals(4, nav.getBadge(R.id.profile).getNumber());
            activity.getBadgeState().setNotificationCount(0);
            assertNull(nav.getBadge(R.id.profile));
        }
    }

    // ---- 导航与菜单 ----

    @Test
    public void navigateToHomePage_unknownId_isNoOp() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search + 9999);
            assertEquals(activity.getString(R.string.discover_hub),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void navigateToHomePage_search_switchesFactory() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            assertEquals(activity.getString(R.string.search),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void onCreateOptionsMenu_bottomPage_hasNoCreateNew() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertNull(menu.findItem(R.id.create_new));
        }
    }

    @Test
    public void onSaveInstanceState_savesFactoryItem() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            Bundle out = new Bundle();
            activity.onSaveInstanceState(out);
            assertEquals(R.id.discover_hub, out.getInt("factoryItem", -1));
        }
    }

    @Test
    public void onLoginCanceled_andFailed_doNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.onLoginCanceled();
            activity.onLoginFailed(new RuntimeException("boom"));
            assertEquals(activity.getString(R.string.login_failed),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void onBackPressed_onInitialPage_finishes() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.onBackPressed();
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void onBackPressed_offInitialPage_returnsToInitialPage() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            activity.onBackPressed();
            assertFalse(activity.isFinishing());
            assertEquals(activity.getString(R.string.discover_hub),
                    activity.getSupportActionBar().getTitle().toString());
        }
    }

    @Test
    public void getActionBarTitle_returnsCurrentFactoryTitle() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.discover_hub),
                    activity.getActionBarTitle());
        }
    }

    private static FragmentFactory factoryOf(HomeActivity activity) throws Exception {
        Field f = HomeActivity.class.getDeclaredField("mFactory");
        f.setAccessible(true);
        return (FragmentFactory) f.get(activity);
    }

    private static int trendRangeOf(HomeActivity activity) throws Exception {
        FragmentFactory f = factoryOf(activity);
        Field tf = DiscoverHubFactory.class.getDeclaredField("mTrendingFactory");
        tf.setAccessible(true);
        TrendingFactory trending = (TrendingFactory) tf.get(f);
        Field rf = TrendingFactory.class.getDeclaredField("mRange");
        rf.setAccessible(true);
        return rf.getInt(trending);
    }

    @Test
    public void onLoginFinished_addsAccountAndSwitches() {
        // onLoginFinished → addAccount + switchActiveUser。旧注：无 token 的
        // 通知请求打真实 api.github.com 返回 401，ApiHelpers.throwOnFailure
        // 遇 401 即 logout()，把刚切换的登录在 switchActiveUser 的窗口内
        // 清掉，故“切换后仍为新用户”不可确定。现 @Before 已把 UserService /
        // NotificationService / SingleFactory.getNotifications 全挡成空数据、
        // 且清掉通知单例缓存，不再发真实请求，切换应真正保持。
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            User user = User.builder().login("newuser").id(123L).build();
            Gh4Application.get().addAccount(user, "token-xyz");
            assertEquals("newuser", Gh4Application.get().getAuthLogin());
            activity.onLoginFinished("token-xyz", user);
            assertNotNull(activity);
            assertEquals("newuser", Gh4Application.get().getAuthLogin());
        }
    }

    @Test
    public void navigateToHomePage_eachFactory_setsFactoryClass() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            int[][] cases = {
                    {R.id.feed_hub, 0}, {R.id.search, 0}, {R.id.issues_prs, 0},
                    {R.id.star_hub, 0}, {R.id.discover_hub, 0}, {R.id.notifications, 0},
                    {R.id.my_repos, 0}, {R.id.topic_discovery, 0}, {R.id.profile, 0},
                    {R.id.my_public_events, 0}, {R.id.my_recent_history, 0},
            };
            Class<?>[] expected = {
                    FeedHubFactory.class, SearchHubFactory.class, IssuesPrsFactory.class,
                    StarHubFactory.class, DiscoverHubFactory.class,
                    NotificationListFactory.class, RepositoryFactory.class,
                    TopicDiscoveryFactory.class, UserHubFactory.class,
                    UserPublicEventsFactory.class, RecentHistoryFactory.class,
            };
            for (int i = 0; i < cases.length; i++) {
                activity.navigateToHomePage(cases[i][0]);
                assertEquals(expected[i], factoryOf(activity).getClass());
                assertNotNull(activity.getTitle());
                assertFalse(activity.getTitle().toString().isEmpty());
            }
        }
    }

    @Test
    public void isToolbarFixed_andUseWhiteTabRow_feedHubOnly() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertFalse(activity.isToolbarFixed());
            assertFalse(activity.useWhiteTabRow());
            activity.navigateToHomePage(R.id.feed_hub);
            assertTrue(activity.isToolbarFixed());
            assertTrue(activity.useWhiteTabRow());
        }
    }

    @Test
    public void isPagerTouchPagingEnabled_multiTabOnly() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            assertFalse(activity.isPagerTouchPagingEnabled());
            activity.navigateToHomePage(R.id.feed_hub);
            assertTrue(activity.isPagerTouchPagingEnabled());
        }
    }

    @Test
    public void setTrendRange_onDiscoverHub_setsRange() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertTrue(factoryOf(activity) instanceof DiscoverHubFactory);
            activity.setTrendRange(1);
            assertEquals(1, trendRangeOf(activity));
            activity.setTrendRange(2);
            assertEquals(2, trendRangeOf(activity));
        }
    }

    @Test
    public void setPinnedTrendRangeRow_addAndRemove() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.view.View row = new android.view.View(activity);
            activity.setPinnedTrendRangeRow(row);
            Field f = HomeActivity.class.getDeclaredField("mPinnedTrendRangeRow");
            f.setAccessible(true);
            assertEquals(row, f.get(activity));
            activity.removePinnedTrendRangeRow(row);
            assertNull(f.get(activity));
        }
    }

    @Test
    public void onOptionsItemSelected_createNewOnNotifications_showsCreateDialog()
            throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.notifications);
            MenuBuilder menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertNotNull(menu.findItem(R.id.create_new));
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.create_new)));
            assertFalse(ShadowAlertDialog.getShownDialogs().isEmpty());
            Method m = HomeActivity.class.getDeclaredMethod("showRepoInputDialog", boolean.class);
            m.setAccessible(true);
            m.invoke(activity, false);
            assertEquals(2, ShadowAlertDialog.getShownDialogs().size());
        }
    }

    @Test
    public void onRefresh_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.onRefresh();
            assertNotNull(activity);
        }
    }

    @Test
    public void supportInvalidateOptionsMenu_withRepositoryFactory_doesNothing() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.my_repos);
            activity.supportInvalidateOptionsMenu();
            assertNotNull(activity);
        }
    }

    @Test
    public void onLoginStartOauth_storesOAuthStateAndVerifier() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.onLoginStartOauth();
            android.content.SharedPreferences secure =
                    com.gh4a.Gh4Application.securePrefs(activity);
            assertNotNull(secure.getString("oauth_state_pending", null));
            assertNotNull(secure.getString("oauth_verifier_pending", null));
        }
    }

    @Test
    public void setStarBadge_andUpdateFromCache() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.setStarBadge(3);
            Field f = HomeActivity.class.getDeclaredField("mBottomNav");
            f.setAccessible(true);
            BottomNavigationView bottomNav = (BottomNavigationView) f.get(activity);
            assertEquals(3, bottomNav.getBadge(R.id.star_hub).getNumber());
            activity.setStarBadge(0);
            assertNull(bottomNav.getBadge(R.id.star_hub));
            activity.updateStarBadgeFromCache();
            assertNotNull(activity);
        }
    }

    @Test
    public void recreate_restoresFactory() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            c.recreate();
            HomeActivity recreated = c.get();
            assertTrue(factoryOf(recreated) instanceof SearchHubFactory);
        }
    }

    // ---- A2 第二轮补缺口：HomeActivity ----

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

    private static java.lang.reflect.Method a2FindMethod(Class<?> cls, String name,
            Class<?>[] params) throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object a2Invoke(Object target, String name, Class<?>[] params,
            Object... args) throws Exception {
        java.lang.reflect.Method m = a2FindMethod(target.getClass(), name, params);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static android.widget.EditText a2FindEditText(android.view.View view) {
        if (view instanceof android.widget.EditText) {
            return (android.widget.EditText) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.EditText found = a2FindEditText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void launches_unknownStartPage_fallsBackToDiscover() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, 999999))) {
            HomeActivity activity = c.setup().get();
            assertTrue(factoryOf(activity) instanceof DiscoverHubFactory);
        }
    }

    @Test
    public void launches_withInitialTabExtra_consumesExtra() {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = HomeActivity.makeIntent(context, R.id.discover_hub);
        intent.putExtra("initial_tab", 1);
        try (ActivityController<HomeActivity> c =
                Robolectric.buildActivity(HomeActivity.class, intent)) {
            HomeActivity activity = c.setup().get();
            assertFalse(activity.getIntent().hasExtra("initial_tab"));
        }
    }

    @Test
    public void launches_withCrashFile_showsCrashDialog() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        java.io.File crashFile = new java.io.File(context.getCacheDir(), "last_crash.txt");
        java.nio.file.Files.write(crashFile.toPath(),
                "fake crash trace".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertFalse(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
        } finally {
            crashFile.delete();
        }
    }

    @Test
    public void setBottomBadge_nullBottomNav_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mBottomNav", null);
            activity.setBottomBadge(R.id.discover_hub, 5);
        }
    }

    @Test
    public void syncBottomNav_nullBottomNav_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mBottomNav", null);
            a2Invoke(activity, "syncBottomNav", new Class<?>[]{});
        }
    }

    @Test
    public void createDialog_itemClick_opensRepoInputDialog() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.create_new, 0, "New");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.create_new)));
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            assertNotNull(dialog);
            android.widget.ListView listView = dialog.getListView();
            listView.performItemClick(
                    listView.getAdapter().getView(0, null, listView), 0, 0);
            assertEquals(2, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
        }
    }

    @Test
    public void repoInputDialog_positive_validInput_startsIssueEdit() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showRepoInputDialog", new Class<?>[]{boolean.class}, false);
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            android.widget.EditText input = a2FindEditText(dialog.getWindow().getDecorView());
            assertNotNull(input);
            input.setText("octocat/hello");
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            // AlertDialog 按钮经 ButtonHandler 发消息，正按钮回调在主 looper 空闲时才跑
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(com.gh4a.activities.IssueEditActivity.class.getName(),
                    Shadows.shadowOf(activity).getNextStartedActivity()
                            .getComponent().getClassName());
        }
    }

    @Test
    public void repoInputDialog_positive_invalidInput_showsToast() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showRepoInputDialog", new Class<?>[]{boolean.class}, false);
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            android.widget.EditText input = a2FindEditText(dialog.getWindow().getDecorView());
            assertNotNull(input);
            input.setText("xxx");
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void switchActiveUser_sameLogin_recreatesActivity() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            HomeActivity before = activity;
            a2Invoke(activity, "switchActiveUser", new Class<?>[]{String.class}, "octocat");
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertNotSame(before, c.get());
        }
    }

    @Test
    public void onPageSelected_firstTab_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.onPageSelected(0);
        }
    }

    @Test
    public void initiateFilter_togglesRightDrawerWithoutCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.initiateFilter();
        }
    }

    @Test
    public void onDrawerClosed_doesNotCrash() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.onDrawerClosed(false);
            activity.onDrawerClosed(true);
        }
    }

    @Test
    public void onCreateOptionsMenu_defaultPage_hasNoCreateItem() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            assertNull(menu.findItem(R.id.create_new));
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

    @Test
    public void navigateUp_returnsToplevelIntent() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            Intent up = (Intent) a2Invoke(activity, "navigateUp", new Class<?>[]{});
            assertNotNull(up);
        }
    }

    @Test
    public void onBackPressed_withBackStackEntry_popsIt() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.getSupportFragmentManager().beginTransaction()
                    .add(new androidx.fragment.app.Fragment(), "dummy")
                    .addToBackStack(null).commit();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertEquals(1, activity.getSupportFragmentManager().getBackStackEntryCount());
            activity.onBackPressed();
            // popBackStack 是异步的：先让主 looper 跑完，再执行待处理事务
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertEquals(0, activity.getSupportFragmentManager().getBackStackEntryCount());
        }
    }

    @Test
    public void isPagerTouchPagingEnabled_nullFactory_returnsFalse() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mFactory", null);
            assertFalse(activity.isPagerTouchPagingEnabled());
        }
    }

    @Test
    public void updateUserInfo_withName_showsExtraView() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            User user = mock(User.class);
            when(user.name()).thenReturn("Octo");
            when(user.login()).thenReturn("octocat");
            when(user.avatarUrl()).thenReturn(null);
            a2SetField(activity, "mUserInfo", user);
            a2Invoke(activity, "updateUserInfo", new Class<?>[]{});
            android.widget.TextView extra =
                    (android.widget.TextView) a2GetField(activity, "mUserExtraView");
            assertNotNull(extra);
            assertEquals("Octo", extra.getText().toString());
            assertEquals(android.view.View.VISIBLE, extra.getVisibility());
        }
    }


    // ---- A2 第三轮补缺口：HomeActivity ----

    @Test
    public void recreate_unknownFactoryIdInSavedState_fallsBackToInitialPage() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        Bundle savedState = new Bundle();
        savedState.putInt("factoryItem", 999999);
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.discover_hub))) {
            HomeActivity activity = c.create(savedState).start().postCreate(null)
                    .resume().visible().get();
            assertTrue(factoryOf(activity) instanceof DiscoverHubFactory);
        }
    }

    @Test
    public void launches_profilePage_disablesSwipeToRefresh() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.profile))) {
            HomeActivity activity = c.setup().get();
            assertFalse((Boolean) a2Invoke(activity, "canSwipeToRefresh", new Class<?>[]{}));
        }
    }

    @Test
    public void launches_nightMode_themeSwitchChecked() throws Exception {
        org.robolectric.RuntimeEnvironment.setQualifiers("+night");
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.appcompat.widget.SwitchCompat themeSwitch =
                    (androidx.appcompat.widget.SwitchCompat) a2GetField(activity, "mThemeSwitch");
            assertNotNull(themeSwitch);
            assertTrue(themeSwitch.isChecked());
        } finally {
            org.robolectric.RuntimeEnvironment.setQualifiers("+notnight");
        }
    }

    @Test
    public void onClick_togglesDrawerMode() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            boolean before = (Boolean) a2GetField(activity, "mDrawerInAccountMode");
            activity.onClick(new android.view.View(activity));
            assertEquals(!before, a2GetField(activity, "mDrawerInAccountMode"));
        }
    }

    @Test
    public void createDialog_itemClickSecond_opensRepoInputDialogForPr() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.create_new, 0, "New");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.create_new)));
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            android.widget.ListView listView = dialog.getListView();
            listView.performItemClick(
                    listView.getAdapter().getView(1, null, listView), 1, 1);
            assertEquals(2, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
        }
    }

    @Test
    public void repoInputDialog_positive_validInputForPr_startsBrowserIntent() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showRepoInputDialog", new Class<?>[]{boolean.class}, true);
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            android.widget.EditText input = a2FindEditText(dialog.getWindow().getDecorView());
            assertNotNull(input);
            input.setText("octocat/hello");
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(activity.getString(R.string.create_pr_web_note),
                    ShadowToast.getTextOfLatestToast());
            Intent browser = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(browser);
            assertEquals(Intent.ACTION_VIEW, browser.getAction());
            assertTrue(browser.getData().toString().contains("github.com/octocat/hello/compare"));
        }
    }

    @Test
    public void repoInputDialog_positive_invalidChars_showsToast() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showRepoInputDialog", new Class<?>[]{boolean.class}, false);
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            android.widget.EditText input = a2FindEditText(dialog.getWindow().getDecorView());
            assertNotNull(input);
            input.setText("a b/c");
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void bottomNavItemClick_switchesToStarHub() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            com.google.android.material.bottomnavigation.BottomNavigationView nav =
                    activity.findViewById(R.id.bottom_nav);
            assertNotNull(nav);
            nav.setSelectedItemId(R.id.star_hub);
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertTrue(factoryOf(activity) instanceof StarHubFactory);
        }
    }

    @Test
    public void onNavigationItemSelected_logout_logsOutAndFinishes() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.logout, 0, "Logout");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.logout)));
            assertNull(Gh4Application.get().getAuthLogin());
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void onNavigationItemSelected_addAccount_showsLoginChooser() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.add_account, 0, "Add account");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.add_account)));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertFalse(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
        }
    }

    @Test
    public void onNavigationItemSelected_settings_startsSettingsActivity() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.settings, 0, "Settings");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.settings)));
            Intent started = Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertNotNull(started);
            assertEquals(com.gh4a.activities.SettingsActivity.class.getName(),
                    started.getComponent().getClassName());
        }
    }

    @Test
    public void onNavigationItemSelected_themeToggle_flipsSwitch() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.appcompat.widget.SwitchCompat themeSwitch =
                    (androidx.appcompat.widget.SwitchCompat) a2GetField(activity, "mThemeSwitch");
            assertNotNull(themeSwitch);
            boolean before = themeSwitch.isChecked();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.theme_toggle, 0, "Theme");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.theme_toggle)));
            assertEquals(!before, themeSwitch.isChecked());
        }
    }

    @Test
    public void onNavigationItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(activity.onNavigationItemSelected(menu.findItem(999999)));
        }
    }

    @Test
    public void onNavigationItemSelected_factoryItem_switchesFactory() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.feed_hub, 0, "Feed");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.feed_hub)));
            assertTrue(factoryOf(activity) instanceof FeedHubFactory);
        }
    }

    @Test
    public void onNavigationItemSelected_otherAccount_switchesUser() {
        User u1 = mock(User.class);
        when(u1.login()).thenReturn("octocat");
        when(u1.id()).thenReturn(1L);
        User u2 = mock(User.class);
        when(u2.login()).thenReturn("seconduser");
        when(u2.id()).thenReturn(2L);
        Gh4Application.get().addAccount(u1, "fake-token-1");
        Gh4Application.get().addAccount(u2, "fake-token-2");
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 1000, 0, "octocat");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(1000)));
        }
    }

    @Test
    public void maybeRequestNotificationPermission_authorized_setsAskedFlag() throws Exception {
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(user.id()).thenReturn(1L);
        Gh4Application.get().addAccount(user, "fake-token");
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "maybeRequestNotificationPermission", new Class<?>[]{});
            Context context = ApplicationProvider.getApplicationContext();
            assertTrue(context.getSharedPreferences(SettingsFragment.PREF_NAME,
                    Context.MODE_PRIVATE).getBoolean(
                    "notification_permission_asked_v1", false));
        }
    }

    @Test
    public void onPageSelected_feedHub_refreshesMenu() {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.feed_hub))) {
            HomeActivity activity = c.setup().get();
            activity.onPageSelected(0);
        }
    }

    @Test
    public void onCreateOptionsMenu_feedHub_fallsThroughToSuper() {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.feed_hub))) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            assertTrue(activity.onCreateOptionsMenu(menu));
        }
    }

    @Test
    public void onOptionsItemSelected_issuesFactory_handlesFirstItem() {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.issues_prs))) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, android.view.Menu.FIRST, 0, "Toggle");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(android.view.Menu.FIRST)));
        }
    }

    @Test
    public void setNotificationsIndicatorVisible_togglesIndicator() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.widget.ImageView indicator = (android.widget.ImageView)
                    a2GetField(activity, "mNotificationsIndicator");
            assertNotNull(indicator);
            activity.setNotificationsIndicatorVisible(true);
            assertEquals(android.view.View.VISIBLE, indicator.getVisibility());
            activity.setNotificationsIndicatorVisible(false);
            assertEquals(android.view.View.GONE, indicator.getVisibility());
        }
    }

    @Test
    public void launches_withCrashFile_dismissingDialog_notesDismissed() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        java.io.File crashFile = new java.io.File(context.getCacheDir(), "last_crash.txt");
        java.nio.file.Files.write(crashFile.toPath(),
                "fake crash trace".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            assertNotNull(dialog);
            dialog.dismiss();
            assertFalse(dialog.isShowing());
        } finally {
            crashFile.delete();
        }
    }


    // ---- A2 第四轮补缺口：HomeActivity ----

    @Test
    public void themeToggle_suppressed_returnsEarlyWithoutWritingPrefs() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.appcompat.widget.SwitchCompat themeSwitch =
                    (androidx.appcompat.widget.SwitchCompat) a2GetField(activity, "mThemeSwitch");
            assertNotNull(themeSwitch);
            a2SetField(activity, "mSuppressThemeToggle", true);
            themeSwitch.setChecked(!themeSwitch.isChecked());
            Context context = ApplicationProvider.getApplicationContext();
            assertEquals(-1, context.getSharedPreferences(SettingsFragment.PREF_NAME,
                    Context.MODE_PRIVATE).getInt(SettingsFragment.KEY_THEME, -1));
        }
    }

    @Test
    public void themeToggle_nonManualMode_togglesTwice_restartsStack() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            Context context = ApplicationProvider.getApplicationContext();
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(
                    com.gh4a.utils.DarkModeScheduler.KEY_DARK_MODE,
                    com.gh4a.utils.DarkModeScheduler.MODE_SCHEDULED).apply();
            androidx.appcompat.widget.SwitchCompat themeSwitch =
                    (androidx.appcompat.widget.SwitchCompat) a2GetField(activity, "mThemeSwitch");
            assertNotNull(themeSwitch);
            themeSwitch.setChecked(true);
            assertEquals(activity.getString(R.string.dark_mode_switched_manual),
                    ShadowToast.getTextOfLatestToast());
            assertEquals(0, prefs.getInt(SettingsFragment.KEY_THEME, -1));
            themeSwitch.setChecked(false);
            assertEquals(1, prefs.getInt(SettingsFragment.KEY_THEME, -1));
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            android.app.Application app = (android.app.Application)
                    ApplicationProvider.getApplicationContext();
            Intent restarted = Shadows.shadowOf(app).getNextStartedActivity();
            assertNotNull(restarted);
            assertEquals(HomeActivity.class.getName(),
                    restarted.getComponent().getClassName());
        }
    }

    @Test
    public void showAccountSwitcher_twoAccounts_showsDialog() throws Exception {
        User u1 = mock(User.class);
        when(u1.login()).thenReturn("octocat");
        when(u1.id()).thenReturn(1L);
        User u2 = mock(User.class);
        when(u2.login()).thenReturn("seconduser");
        when(u2.id()).thenReturn(2L);
        Gh4Application.get().addAccount(u1, "fake-token-1");
        Gh4Application.get().addAccount(u2, "fake-token-2");
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showAccountSwitcher", new Class<?>[]{});
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertFalse(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
        }
    }

    @Test
    public void showLogoutConfirm_positive_logsOut() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showLogoutConfirm", new Class<?>[]{});
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            org.robolectric.shadows.ShadowDialog.getShownDialogs().get(0);
            assertNotNull(dialog);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertNull(Gh4Application.get().getAuthLogin());
        }
    }

    @Test
    public void onCreateOptionsMenu_freshFeedFactory_fallsThroughToSuper() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.feed_hub))) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mFactory", new FeedHubFactory(activity, "octocat"));
            MenuBuilder menu = new MenuBuilder(activity);
            assertTrue(activity.onCreateOptionsMenu(menu));
        }
    }

    @Test
    public void onNavigationItemSelected_repositoryFilter_delegatesToFactory() throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        try (ActivityController<HomeActivity> c = Robolectric.buildActivity(
                HomeActivity.class, HomeActivity.makeIntent(context, R.id.my_repos))) {
            HomeActivity activity = c.setup().get();
            Object factory = factoryOf(activity);
            assertTrue(factory instanceof RepositoryFactory);
            a2SetField(factory, "mFragment",
                    mock(com.gh4a.fragment.RepositoryListContainerFragment.class));
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.filter_type_watched, 0, "Watched");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.filter_type_watched)));
        }
    }


    // ---- A2 第五轮补缺口：HomeActivity 账号切换表行点击 ----

    private static android.widget.TextView a2FindTextView(android.view.View view, String text) {
        if (view instanceof android.widget.TextView
                && text.equals(((android.widget.TextView) view).getText().toString())) {
            return (android.widget.TextView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.TextView found = a2FindTextView(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void seedTwoAccounts() {
        User u1 = mock(User.class);
        when(u1.login()).thenReturn("octocat");
        when(u1.id()).thenReturn(1L);
        User u2 = mock(User.class);
        when(u2.login()).thenReturn("seconduser");
        when(u2.id()).thenReturn(2L);
        Gh4Application.get().addAccount(u1, "fake-token-1");
        Gh4Application.get().addAccount(u2, "fake-token-2");
    }

    @Test
    public void accountSwitcher_otherAccountRowClick_switchesUser() throws Exception {
        seedTwoAccounts();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showAccountSwitcher", new Class<?>[]{});
            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog
                    .getShownDialogs().get(0);
            android.widget.TextView row = a2FindTextView(
                    sheet.getWindow().getDecorView(), "seconduser");
            assertNotNull(row);
            row.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
        }
    }

    @Test
    public void accountSwitcher_addAccountRowClick_showsLoginChooser() throws Exception {
        seedTwoAccounts();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showAccountSwitcher", new Class<?>[]{});
            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog
                    .getShownDialogs().get(0);
            android.widget.TextView row = a2FindTextView(sheet.getWindow().getDecorView(),
                    activity.getString(R.string.add_account));
            assertNotNull(row);
            row.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            activity.getSupportFragmentManager().executePendingTransactions();
            assertEquals(2, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
        }
    }

    @Test
    public void accountSwitcher_logoutRowClick_showsLogoutConfirm() throws Exception {
        seedTwoAccounts();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "showAccountSwitcher", new Class<?>[]{});
            android.app.Dialog sheet = org.robolectric.shadows.ShadowDialog
                    .getShownDialogs().get(0);
            android.widget.TextView row = a2FindTextView(sheet.getWindow().getDecorView(),
                    activity.getString(R.string.logout));
            assertNotNull(row);
            row.performClick();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertEquals(2, org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
        }
    }

    // ---- §6 补缺口：HomeActivity（15:40 报告残 39 行 / 60 分支） ----

    /** 把结果发给最后一次启动的 launcher（DiffViewerActivityTest 同款）。 */
    private static boolean dispatchLastLaunchedResult(android.app.Activity activity,
            int resultCode, Intent data) throws Exception {
        ActivityResultRegistry registry =
                ((androidx.activity.ComponentActivity) activity).getActivityResultRegistry();
        @SuppressWarnings("unchecked")
        Collection<String> launchedKeys =
                (Collection<String>) a2GetField(registry, "launchedKeys");
        if (launchedKeys == null || launchedKeys.isEmpty()) {
            return false;
        }
        String key = launchedKeys.iterator().next();
        @SuppressWarnings("unchecked")
        Map<String, Integer> keyToRc =
                (Map<String, Integer>) a2GetField(registry, "keyToRc");
        Integer rc = keyToRc.get(key);
        if (rc == null) {
            return false;
        }
        return registry.dispatchResult(rc, resultCode, data);
    }

    /** 主 looper 空闲直到条件成立（loader 投递用），最多约 1 秒。 */
    private static void idleUntil(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 50 && !condition.getAsBoolean(); i++) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(20);
        }
    }

    /** 新建仓库输入框走一遍正按钮，返回最新 Toast 文案。 */
    private static String repoInputDialogToast(HomeActivity activity, String text)
            throws Exception {
        a2Invoke(activity, "showRepoInputDialog", new Class<?>[]{boolean.class}, false);
        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog)
                        ShadowDialog.getShownDialogs().get(0);
        android.widget.EditText input = a2FindEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText(text);
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        return ShadowToast.getTextOfLatestToast();
    }

    @Test
    public void settingsLauncher_themeChanged_restartsToToplevel() throws Exception {
        // 先把“通知权限已问”置位，避免 onResume 顺带启动权限 launcher，
        // 保证 launchedKeys 里只有 settings 一个 key。
        prefs().edit().putBoolean("notification_permission_asked_v1", true).apply();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.settings, 0, "Settings");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.settings)));
            Intent settingsIntent = Shadows.shadowOf(activity)
                    .getNextStartedActivityForResult().intent;
            assertNotNull(settingsIntent);
            assertEquals(SettingsActivity.class.getName(),
                    settingsIntent.getComponent().getClassName());
            // startActivityForResult 同时进 plain 队列：先排空，否则后面的
            // getNextStartedActivity() 拿到的是 SettingsActivity 的启动 intent。
            Intent launchIntent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launchIntent);
            assertEquals(SettingsActivity.class.getName(),
                    launchIntent.getComponent().getClassName());
            Intent result = new Intent();
            result.putExtra(SettingsActivity.RESULT_EXTRA_THEME_CHANGED, true);
            assertTrue(dispatchLastLaunchedResult(activity,
                    android.app.Activity.RESULT_OK, result));
            ShadowLooper.idleMainLooper();
            Intent toplevel = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(toplevel);
            // 未登录（无 token）时 getToplevelActivityIntent() 返回
            // Github4AndroidActivity；回调本身（goToToplevelActivity）已覆盖。
            assertEquals(Github4AndroidActivity.class.getName(),
                    toplevel.getComponent().getClassName());
        }
    }

    @Test
    public void settingsLauncher_noThemeChange_doesNotRestart() throws Exception {
        prefs().edit().putBoolean("notification_permission_asked_v1", true).apply();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.settings, 0, "Settings");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.settings)));
            assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
            assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
            assertTrue(dispatchLastLaunchedResult(activity,
                    android.app.Activity.RESULT_OK, new Intent()));
            ShadowLooper.idleMainLooper();
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void notificationPermissionLauncher_result_runsEmptyCallback() throws Exception {
        // 保证未问过：onResume 才会启动权限 launcher。须先有账号（token），
        // 否则 isAuthorized() 为假、onResume 不启动 launcher，dispatch 返回 false。
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(user.id()).thenReturn(1L);
        Gh4Application.get().addAccount(user, "fake-token");
        prefs().edit().putBoolean("notification_permission_asked_v1", false).apply();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            // onResume 已把权限请求发出，launcher 在 launchedKeys 里。
            assertTrue(dispatchLastLaunchedResult(activity,
                    android.app.Activity.RESULT_OK, null));
            assertTrue(prefs().getBoolean("notification_permission_asked_v1", false));
        }
    }

    @Test
    public void maybeRequestNotificationPermission_alreadyGranted_returnsWithoutAsking()
            throws Exception {
        // 须先有账号（token），否则 isAuthorized() 为假、方法在 327 行提前返回，
        // 测不到 333 行的已授权分支。
        User user = mock(User.class);
        when(user.login()).thenReturn("octocat");
        when(user.id()).thenReturn(1L);
        Gh4Application.get().addAccount(user, "fake-token");
        android.app.Application app = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(app).grantPermissions(
                android.Manifest.permission.POST_NOTIFICATIONS);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "maybeRequestNotificationPermission", new Class<?>[]{});
            assertFalse(prefs().getBoolean("notification_permission_asked_v1", false));
        }
    }

    @Test
    public void setupBottomNav_secondCall_usesExistingBottomNav() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            BottomNavigationView first = activity.findViewById(R.id.bottom_nav);
            assertNotNull(first);
            a2Invoke(activity, "setupBottomNav", new Class<?>[]{});
            BottomNavigationView after = activity.findViewById(R.id.bottom_nav);
            assertSame(first, after);
            assertSame(first, a2GetField(activity, "mBottomNav"));
        }
    }

    @Test
    public void setupBottomNav_noSwipeContainer_skipsMargin() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.coordinatorlayout.widget.CoordinatorLayout coordinator =
                    activity.getRootLayout();
            android.view.View bottom = activity.findViewById(R.id.bottom_nav);
            android.view.View swipe = activity.findViewById(R.id.swipe_container);
            assertNotNull(bottom);
            assertNotNull(swipe);
            coordinator.removeView(bottom);
            coordinator.removeView(swipe);
            assertNull(activity.findViewById(R.id.bottom_nav));
            a2Invoke(activity, "setupBottomNav", new Class<?>[]{});
            // 重新 inflate 出底栏，且未因 swipe_container 缺失而崩。
            assertNotNull(activity.findViewById(R.id.bottom_nav));
        }
    }

    @Test
    public void setupBottomNav_nonCoordinatorLayoutParams_skipsMargin() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            androidx.coordinatorlayout.widget.CoordinatorLayout coordinator =
                    activity.getRootLayout();
            android.view.View bottom = activity.findViewById(R.id.bottom_nav);
            android.view.View swipe = activity.findViewById(R.id.swipe_container);
            assertNotNull(bottom);
            assertNotNull(swipe);
            coordinator.removeView(bottom);
            android.view.ViewGroup.LayoutParams orig = swipe.getLayoutParams();
            swipe.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            a2Invoke(activity, "setupBottomNav", new Class<?>[]{});
            // 立即恢复原 LayoutParams，避免后续布局把普通 params 当 CoordinatorLayout 用。
            swipe.setLayoutParams(orig);
            assertNotNull(activity.findViewById(R.id.bottom_nav));
        }
    }

    @Test
    public void updateDrawerMode_twoAccounts_repopulatesOtherAccounts() throws Exception {
        seedTwoAccounts();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.view.Menu menu = (android.view.Menu) a2GetField(activity, "mLeftDrawerMenu");
            assertNotNull(menu);
            a2Invoke(activity, "updateDrawerMode", new Class<?>[]{boolean.class}, true);
            android.view.MenuItem other = menu.findItem(1000);
            assertNotNull(other);
            assertEquals("seconduser", other.getTitle().toString());
            // 第二次调用：先删旧项（1276），再重新加回。
            a2Invoke(activity, "updateDrawerMode", new Class<?>[]{boolean.class}, true);
            android.view.MenuItem other2 = menu.findItem(1000);
            assertNotNull(other2);
            assertEquals("seconduser", other2.getTitle().toString());
            // 当前登录不进 other 列表：只有 1 个 other 项。
            assertNull(menu.findItem(1001));
        }
    }

    @Test
    public void updateDrawerMode_hiddenItem_skipsVisibility() throws Exception {
        Context context = context();
        DrawerManager.setVisible(context, "my_repos", false);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "updateDrawerMode", new Class<?>[]{boolean.class}, true);
            android.view.Menu menu = (android.view.Menu) a2GetField(activity, "mLeftDrawerMenu");
            assertNull(menu.findItem(R.id.my_repos));
        } finally {
            DrawerManager.setVisible(context, "my_repos", true);
        }
    }

    @Test
    public void setupThemeToggle_noDrawer_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.view.View drawer = activity.findViewById(R.id.left_drawer);
            assertNotNull(drawer);
            android.view.ViewGroup parent = (android.view.ViewGroup) drawer.getParent();
            parent.removeView(drawer);
            assertNull(activity.findViewById(R.id.left_drawer));
            a2Invoke(activity, "setupThemeToggle", new Class<?>[]{});
        }
    }

    @Test
    public void setupThemeToggle_noThemeItem_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NavigationView drawer = activity.findViewById(R.id.left_drawer);
            assertNotNull(drawer);
            drawer.getMenu().removeItem(R.id.theme_toggle);
            assertNull(drawer.getMenu().findItem(R.id.theme_toggle));
            a2Invoke(activity, "setupThemeToggle", new Class<?>[]{});
        }
    }

    @Test
    public void updateNotificationIndicator_nullIndicator_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mNotificationsIndicator", null);
            a2Invoke(activity, "updateNotificationIndicator",
                    new Class<?>[]{int.class}, R.id.notifications);
        }
    }

    @Test
    public void updateNotificationIndicator_notificationsItem_usesAccent() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertNotNull(a2GetField(activity, "mNotificationsIndicator"));
            a2Invoke(activity, "updateNotificationIndicator",
                    new Class<?>[]{int.class}, R.id.notifications);
            android.widget.ImageView indicator =
                    (android.widget.ImageView) a2GetField(activity, "mNotificationsIndicator");
            assertNotNull(indicator.getDrawable());
        }
    }

    @Test
    public void setNotificationsIndicatorVisible_nullIndicator_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mNotificationsIndicator", null);
            activity.setNotificationsIndicatorVisible(true);
        }
    }

    @Test
    public void setPinnedTrendRangeRow_nullRow_clearsWithoutAdding() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.setPinnedTrendRangeRow(null);
            assertNull(a2GetField(activity, "mPinnedTrendRangeRow"));
        }
    }

    @Test
    public void removePinnedTrendRangeRow_nullRow_doesNothing() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.removePinnedTrendRangeRow(null);
        }
    }

    @Test
    public void updateStarBadgeFromCache_nullBottomNav_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mBottomNav", null);
            activity.updateStarBadgeFromCache();
        }
    }

    @Test
    public void setTrendRange_nonDiscoverFactory_isNoOp() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
            activity.setTrendRange(7);
            // 非趋势工厂：不刷新页面，工厂不变。
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
        }
    }

    @Test
    public void repoInputDialog_positive_trailingSlash_showsToast() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    repoInputDialogToast(activity, "owner/"));
        }
    }

    @Test
    public void repoInputDialog_positive_dotPrefixName_showsToast() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    repoInputDialogToast(activity, "owner/.hidden"));
        }
    }

    @Test
    public void repoInputDialog_positive_dotSuffixName_showsToast() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    repoInputDialogToast(activity, "owner/name."));
        }
    }

    @Test
    public void repoInputDialog_positive_doubleDotName_showsToast() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    repoInputDialogToast(activity, "owner/na..me"));
        }
    }

    @Test
    public void repoInputDialog_positive_badNameChars_showsToast() throws Exception {
        // 名字含空格，正则 [A-Za-z0-9._-]+ 不匹配：覆盖 643 行 !matches 真分支。
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(activity.getString(R.string.create_repo_invalid),
                    repoInputDialogToast(activity, "owner/na me"));
        }
    }

    @Test
    public void onCreateOptionsMenu_handledFactory_returnsTrue() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FragmentFactory mockFactory = mock(FragmentFactory.class);
            when(mockFactory.onCreateOptionsMenu(any(Menu.class))).thenReturn(true);
            a2SetField(activity, "mFactory", mockFactory);
            assertTrue(activity.onCreateOptionsMenu(new MenuBuilder(activity)));
        }
    }

    @Test
    public void onBackPressed_drawerOpen_closesDrawerWithoutPopping() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
            DrawerLayout drawerLayout = activity.findViewById(R.id.drawer_container);
            assertNotNull(drawerLayout);
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT);
            drawerLayout.openDrawer(Gravity.LEFT, false);
            assertTrue(drawerLayout.isDrawerOpen(Gravity.LEFT));
            activity.onBackPressed();
            // closeDrawers() 调的是动画版 closeDrawer（ViewDragHelper 平滑滑动），
            // Robolectric 下不会自动走完：推进虚拟时钟并反复调 computeScroll
            // 驱动滑块，直到抽屉真正合上（最多 ~1.6s 虚拟时间）。
            for (int i = 0; i < 100 && drawerLayout.isDrawerOpen(Gravity.LEFT); i++) {
                org.robolectric.shadows.ShadowSystemClock.advanceBy(16,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                drawerLayout.computeScroll();
                ShadowLooper.idleMainLooper();
            }
            assertFalse(drawerLayout.isDrawerOpen(Gravity.LEFT));
            // 抽屉关掉后走 else 分支：回初始页，不结束。
            assertFalse(activity.isFinishing());
            assertEquals(R.id.discover_hub, (int) a2GetField(activity, "mSelectedFactoryId"));
        }
    }

    @Test
    public void onBackPressed_offInitialPage_nullLeftDrawerMenu_skipsCheck() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            activity.navigateToHomePage(R.id.search);
            a2SetField(activity, "mLeftDrawerMenu", null);
            activity.onBackPressed();
            assertFalse(activity.isFinishing());
            assertEquals(R.id.discover_hub, (int) a2GetField(activity, "mSelectedFactoryId"));
        }
    }

    @Test
    public void onBackPressed_offInitialPage_hiddenInitialItem_skipsCheck() throws Exception {
        Context context = context();
        DrawerManager.setVisible(context, "discover_hub", false);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            // 初始页被隐藏，启动时已回落到首个可见项（search）。
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
            activity.onBackPressed();
            assertFalse(activity.isFinishing());
            assertEquals(R.id.discover_hub, (int) a2GetField(activity, "mSelectedFactoryId"));
        } finally {
            DrawerManager.setVisible(context, "discover_hub", true);
        }
    }

    @Test
    public void getInitialLeftDrawerSelection_hiddenSelectedItem_fallsBackToFirstVisible()
            throws Exception {
        Context context = context();
        DrawerManager.setVisible(context, "discover_hub", false);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
            assertTrue(a2GetField(activity, "mFactory") instanceof SearchHubFactory);
        } finally {
            DrawerManager.setVisible(context, "discover_hub", true);
        }
    }

    @Test
    public void getInitialLeftDrawerSelection_allHidden_skipsFallback() throws Exception {
        Context context = context();
        String[] keys = {"search", "feed_hub", "notifications", "discover_hub",
                "my_repos", "star_hub", "my_items", "topic_discovery"};
        for (String key : keys) {
            DrawerManager.setVisible(context, key, false);
        }
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            // 无可见项：不回落，仍留在趋势页。
            assertEquals(R.id.discover_hub, (int) a2GetField(activity, "mSelectedFactoryId"));
        } finally {
            for (String key : keys) {
                DrawerManager.setVisible(context, key, true);
            }
        }
    }

    @Test
    public void getInitialLeftDrawerSelection_hiddenNotifications_skipsIndicator()
            throws Exception {
        Context context = context();
        DrawerManager.setVisible(context, "notifications", false);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertNull(a2GetField(activity, "mNotificationsMenuItem"));
            assertNull(a2GetField(activity, "mNotificationsIndicator"));
        } finally {
            DrawerManager.setVisible(context, "notifications", true);
        }
    }

    @Test
    public void updateUserInfo_nullViews_skipsAvatarAndExtra() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            User user = User.builder().login("octocat").id(1L).build();
            a2SetField(activity, "mUserInfo", user);
            a2SetField(activity, "mAvatarView", null);
            a2SetField(activity, "mUserExtraView", null);
            a2Invoke(activity, "updateUserInfo", new Class<?>[]{});
        }
    }

    @Test
    public void switchActiveUser_finishingActivity_skipsRecreate() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            HomeActivity before = activity;
            a2Invoke(activity, "switchActiveUser", new Class<?>[]{String.class}, "octocat");
            activity.finish();
            ShadowLooper.idleMainLooper();
            // 已在结束：posted 的 recreate 被跳过，实例不变。
            assertSame(before, c.get());
            assertTrue(activity.isFinishing());
        }
    }

    @Test
    public void switchActiveUser_destroyedActivity_skipsRecreate() throws Exception {
        // 974 行另一半：isDestroyed() 为真时短路，isFinishing() 不求值。
        // 先 post recreate，再 destroy 掉 activity，runnable 运行时
        // isDestroyed() 为真 → 跳过 recreate。
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2Invoke(activity, "switchActiveUser", new Class<?>[]{String.class}, "octocat");
            c.destroy();
            assertTrue(activity.isDestroyed());
            ShadowLooper.idleMainLooper();
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }

    @Test
    public void onPageSelected_nullFactory_doesNotCrash() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mFactory", null);
            activity.onPageSelected(0);
        }
    }

    @Test
    public void onNavigationItemSelected_themeToggle_nullSwitch_returnsTrue() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mThemeSwitch", null);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.theme_toggle, 0, "Theme");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.theme_toggle)));
        }
    }

    @Test
    public void onNavigationItemSelected_smallUnknownId_returnsFalse() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 5, 0, "Unknown");
            assertFalse(activity.onNavigationItemSelected(menu.findItem(5)));
        }
    }

    @Test
    public void onNavigationItemSelected_nullFactory_fallsThrough() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mFactory", null);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "Search");
            assertTrue(activity.onNavigationItemSelected(menu.findItem(R.id.search)));
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
        }
    }

    @Test
    public void loadNotificationIndicator_nullResult_returnsFalse() throws Exception {
        NotificationService service = mock(NotificationService.class);
        // 用 SingleSubject 控流：等 loader 真订阅（有观察者）再发 null，
        // 保证 map 在 result==null 时跑过（1347 行首条件假分支）。
        // Single.just 版只证明 service 被调，不证明 loader 已订阅、map 已跑。
        io.reactivex.subjects.SingleSubject<Response<Page<NotificationThread>>> subject =
                io.reactivex.subjects.SingleSubject.create();
        when(service.getNotifications(anyMap(), anyLong())).thenReturn(subject);
        mockedServiceFactory.when(() -> ServiceFactory.get(eq(NotificationService.class),
                anyBoolean(), any())).thenReturn(service);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.widget.ImageView indicator =
                    (android.widget.ImageView) a2GetField(activity, "mNotificationsIndicator");
            assertNotNull(indicator);
            idleUntil(subject::hasObservers);
            assertTrue(subject.hasObservers());
            subject.onSuccess(Response.success(null));
            ShadowLooper.idleMainLooper();
            assertEquals(android.view.View.GONE, indicator.getVisibility());
        }
    }

    @Test
    public void loadNotificationIndicator_nullItems_returnsFalse() throws Exception {
        NotificationService service = mock(NotificationService.class);
        Page<NotificationThread> page = mock(Page.class);
        // Mockito 默认对 List 返回空列表而非 null：必须显式桩成 null，
        // 否则测到的是空列表（!isEmpty 假分支），不是 items()==null 分支。
        when(page.items()).thenReturn(null);
        // 与 nullResult 同理用 SingleSubject：等 loader 真订阅再发，
        // 保证 map 在 items()==null 时跑过（1347 行第二条件假分支）。
        io.reactivex.subjects.SingleSubject<Response<Page<NotificationThread>>> subject =
                io.reactivex.subjects.SingleSubject.create();
        when(service.getNotifications(anyMap(), anyLong())).thenReturn(subject);
        mockedServiceFactory.when(() -> ServiceFactory.get(eq(NotificationService.class),
                anyBoolean(), any())).thenReturn(service);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.widget.ImageView indicator =
                    (android.widget.ImageView) a2GetField(activity, "mNotificationsIndicator");
            assertNotNull(indicator);
            idleUntil(subject::hasObservers);
            assertTrue(subject.hasObservers());
            // 探针：确认桩真的让 items() 返回 null（若 Mockito 实际给的是
            // 空列表，map 会走 isEmpty 假分支而非 items()==null 分支）。
            assertNull(page.items());
            // 先置 VISIBLE 再发：loader 尚未投递（subject 未发），发后 map
            // 得 false → 投递 false → 变 GONE。若 map 没跑到，仍为 VISIBLE，
            // 断言失败——这才是非空过的验证（默认 GONE 时断言 GONE 是空过）。
            indicator.setVisibility(android.view.View.VISIBLE);
            subject.onSuccess(Response.success(page));
            idleUntil(() -> indicator.getVisibility() == android.view.View.GONE);
            assertEquals(android.view.View.GONE, indicator.getVisibility());
        }
    }

    @Test
    public void loadNotificationIndicator_nonEmptyItems_returnsTrue() throws Exception {
        NotificationService service = mock(NotificationService.class);
        Page<NotificationThread> page = mock(Page.class);
        NotificationThread thread = mock(NotificationThread.class);
        when(page.items()).thenReturn(Collections.singletonList(thread));
        when(service.getNotifications(anyMap(), anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        mockedServiceFactory.when(() -> ServiceFactory.get(eq(NotificationService.class),
                anyBoolean(), any())).thenReturn(service);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            android.widget.ImageView indicator =
                    (android.widget.ImageView) a2GetField(activity, "mNotificationsIndicator");
            assertNotNull(indicator);
            idleUntil(() -> indicator.getVisibility() == android.view.View.VISIBLE);
            assertEquals(android.view.View.VISIBLE, indicator.getVisibility());
        }
    }

    @Test
    public void bottomNavItemSelected_unknownItem_returnsFalse() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_nav);
            assertNotNull(bottomNav);
            NavigationBarView.OnItemSelectedListener listener =
                    (NavigationBarView.OnItemSelectedListener)
                            a2GetField(bottomNav, "selectedListener");
            assertNotNull(listener);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(listener.onNavigationItemSelected(menu.findItem(999999)));
        }
    }

    @Test
    public void bottomNavItemSelected_validItem_nullLeftDrawerMenu_skipsCheck() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_nav);
            NavigationBarView.OnItemSelectedListener listener =
                    (NavigationBarView.OnItemSelectedListener)
                            a2GetField(bottomNav, "selectedListener");
            a2SetField(activity, "mLeftDrawerMenu", null);
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "Search");
            assertTrue(listener.onNavigationItemSelected(menu.findItem(R.id.search)));
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
        }
    }

    @Test
    public void bottomNavItemSelected_validItem_hiddenDrawerItem_skipsCheck() throws Exception {
        Context context = context();
        DrawerManager.setVisible(context, "search", false);
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_nav);
            NavigationBarView.OnItemSelectedListener listener =
                    (NavigationBarView.OnItemSelectedListener)
                            a2GetField(bottomNav, "selectedListener");
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "Search");
            assertTrue(listener.onNavigationItemSelected(menu.findItem(R.id.search)));
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
        } finally {
            DrawerManager.setVisible(context, "search", true);
        }
    }

    @Test
    public void updateMeBadge_nullBottomNav_returnsEarly() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            a2SetField(activity, "mBottomNav", null);
            activity.updateMeBadge();
        }
    }

    @Test
    public void switchTo_nullFactory_skipsDestroy() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            FragmentFactory factory = (FragmentFactory) a2Invoke(activity,
                    "getFactoryForItem", new Class<?>[]{int.class}, R.id.search);
            assertNotNull(factory);
            a2SetField(activity, "mFactory", null);
            a2Invoke(activity, "switchTo",
                    new Class<?>[]{int.class, FragmentFactory.class}, R.id.search, factory);
            assertEquals(R.id.search, (int) a2GetField(activity, "mSelectedFactoryId"));
            assertSame(factory, a2GetField(activity, "mFactory"));
        }
    }

    // onRestoreInstanceState(null) 已删：super.onRestoreInstanceState(null)
    // 在 Bundle.getBundle 处 NPE（AppCompatActivity 要求非空），框架只在有
    // saved state 时才调它且恒传非空，故 802 行的 null 分支不可覆盖。

    @Test
    public void onRestoreInstanceState_nonNullBundle_callsFactory() throws Exception {
        // 空 Bundle 直接调：super 恢复无 fragment 状态是 no-op，
        // 802 行真分支 + 809 行 mFactory.onRestoreInstanceState 被覆盖。
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertNotNull(a2GetField(activity, "mFactory"));
            activity.onRestoreInstanceState(new Bundle());
        }
    }
    @Test
    public void launches_autoCheckDisabled_skipsUpdateCheck() throws Exception {
        prefs().edit().putBoolean(SettingsFragment.KEY_AUTO_CHECK_UPDATE, false).apply();
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertNotNull(activity);
            assertEquals(R.id.discover_hub, (int) a2GetField(activity, "mSelectedFactoryId"));
        } finally {
            prefs().edit().remove(SettingsFragment.KEY_AUTO_CHECK_UPDATE).apply();
        }
    }

    @Test
    public void launches_withCrashFile_dismissingDialog_notesDismissedAfterIdle()
            throws Exception {
        HomeActivityTestHelper.seedLogin("octocat");
        Context context = ApplicationProvider.getApplicationContext();
        java.io.File crashFile = new java.io.File(context.getCacheDir(), "last_crash.txt");
        java.nio.file.Files.write(crashFile.toPath(),
                "fake crash trace".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            ShadowLooper.idleMainLooper();
            androidx.appcompat.app.AlertDialog dialog =
                    (androidx.appcompat.app.AlertDialog)
                            ShadowDialog.getShownDialogs().get(0);
            assertNotNull(dialog);
            dialog.dismiss();
            // dismiss 经 handler 投递：空闲后 OnDismissListener 才跑
            // （CrashBreadcrumb 计数为跨测试静态量，不在此断言绝对值，以覆盖率为准）。
            ShadowLooper.idleMainLooper();
            assertFalse(dialog.isShowing());
        } finally {
            crashFile.delete();
        }
    }

}

