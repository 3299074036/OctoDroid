package com.gh4a.fragment;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.activities.UserActivity;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Subscription;
import com.meisolsson.githubsdk.service.activity.WatchingService;
import io.reactivex.Single;
import org.mockito.MockedStatic;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** WatcherListFragment：关注者列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class WatcherListFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    @Test
    public void newInstance_storesOwnerAndRepo() {
        WatcherListFragment f = WatcherListFragment.newInstance("octo", "cat");
        assertEquals("octo", f.getArguments().getString("owner"));
        assertEquals("cat", f.getArguments().getString("repo"));
    }

    @Test
    public void onCreate_readsOwnerAndRepo() throws Exception {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
        Field owner = WatcherListFragment.class.getDeclaredField("mRepoOwner");
        Field repo = WatcherListFragment.class.getDeclaredField("mRepoName");
        owner.setAccessible(true);
        repo.setAccessible(true);
        assertEquals("octo", owner.get(f));
        assertEquals("cat", repo.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("o", "r"));
        assertEquals(R.string.no_watchers_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("o", "r"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("o", "r"));
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onItemClick_startsUserActivity() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("o", "r"));
        f.onItemClick(User.builder().login("someone").build());
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("someone", started.getStringExtra("login"));
    }

    @Test
    public void onItemClick_nullUserStartsNothing() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("o", "r"));
        f.onItemClick(null);
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onRefresh_whenLoggedOutIsNoop() {
        // 未登录时 loadWatchingState 直接返回，不发请求、不崩溃。
        WatcherListFragment f = attach(WatcherListFragment.newInstance("o", "r"));
        f.onRefresh();
    }

    // ---------- RESUMED 挂载测试：trampoline 调度 + mock ServiceFactory ----------
    // 关键发现（探针已证实）：Mockito mockStatic 只在创建线程生效；
    // 片段加载链走 Schedulers.io()，须把 io/main 线程调度器换成 trampoline，
    // 订阅才发生在测试线程、mock 才生效。Footer 占 1 个 item。

    private static void installTrampolineSchedulers() {
        io.reactivex.plugins.RxJavaPlugins.setIoSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        io.reactivex.plugins.RxJavaPlugins.setComputationSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        io.reactivex.plugins.RxJavaPlugins.setNewThreadSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
        io.reactivex.android.plugins.RxAndroidPlugins.setMainThreadSchedulerHandler(
                s -> io.reactivex.schedulers.Schedulers.trampoline());
    }

    private static void resetSchedulers() {
        io.reactivex.plugins.RxJavaPlugins.reset();
        io.reactivex.android.plugins.RxAndroidPlugins.reset();
    }

    private void setAuthorized() {
        // getAuthToken() 在 Robolectric 下走明文回落文件 Gh4a-secure-plain。
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("active_login", "octo").apply();
        ctx.getSharedPreferences("Gh4a-secure-plain", Context.MODE_PRIVATE)
                .edit().putString("token_octo", "fake-token").apply();
    }

    private WatchingService mockService(boolean subscribed) {
        WatchingService service = mock(WatchingService.class);
        User u1 = mock(User.class);
        User u2 = mock(User.class);
        when(service.getRepositoryWatchers(anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(retrofit2.Response.success(
                        Page.<User>builder().items(Arrays.asList(u1, u2)).build())));
        Subscription sub = mock(Subscription.class);
        when(sub.subscribed()).thenReturn(subscribed);
        when(service.getRepositorySubscription(anyString(), anyString()))
                .thenReturn(Single.just(retrofit2.Response.success(sub)));
        return service;
    }

    private WatcherListFragment mountResumed() {
        WatcherListFragment f = WatcherListFragment.newInstance("octo", "cat");
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private void pumpMainLooper() throws Exception {
        for (int i = 0; i < 20; i++) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(20);
        }
    }

    private androidx.recyclerview.widget.RecyclerView recyclerOf(WatcherListFragment f) {
        return (androidx.recyclerview.widget.RecyclerView) f.getView()
                .findViewById(com.gh4a.R.id.list);
    }

    private static Object field(WatcherListFragment f, String name) throws Exception {
        Field field = WatcherListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(WatcherListFragment f, String name, Object value)
            throws Exception {
        Field field = WatcherListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    @Test
    public void resumedMount_loadsWatchersFromMockedService() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertNotNull(f.getView());
            assertEquals(3, recyclerOf(f).getAdapter().getItemCount()); // 2 用户 + footer
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void loadWatchingState_authorizedSetsWatchingTrue() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsWatching"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void loadWatchingState_authorizedUnsubscribedSetsWatchingFalse() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(false);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.FALSE, field(f, "mIsWatching"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void loadWatchingState_authorized404SetsWatchingFalse() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mock(WatchingService.class);
            when(service.getRepositoryWatchers(anyString(), anyString(), eq(1L)))
                    .thenReturn(Single.just(retrofit2.Response.success(
                            Page.<User>builder().items(Collections.<User>emptyList()).build())));
            when(service.getRepositorySubscription(anyString(), anyString()))
                    .thenReturn(Single.just(retrofit2.Response.error(404,
                            okhttp3.ResponseBody.create(null, new byte[0]))));
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.FALSE, field(f, "mIsWatching"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_authorizedWatchingShowsUnwatchTitle() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
            setField(f, "mIsWatching", Boolean.TRUE);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(mActivity);
            f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
            android.view.MenuItem item = menu.findItem(android.view.Menu.FIRST);
            assertNotNull(item);
            assertEquals(mActivity.getString(R.string.repo_unwatch_action),
                    item.getTitle().toString());
            assertTrue(item.isEnabled());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_authorizedNotWatchingShowsWatchTitle() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(false);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
            setField(f, "mIsWatching", Boolean.FALSE);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(mActivity);
            f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
            android.view.MenuItem item = menu.findItem(android.view.Menu.FIRST);
            assertNotNull(item);
            assertEquals(mActivity.getString(R.string.repo_watch_action),
                    item.getTitle().toString());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_authorizedUnknownShowsLoadingView() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
            setField(f, "mIsWatching", null);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(mActivity);
            f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
            android.view.MenuItem item = menu.findItem(android.view.Menu.FIRST);
            assertNotNull(item);
            assertNotNull(item.getActionView());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_inFlightDisablesItem() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
            setField(f, "mIsWatching", Boolean.TRUE);
            setField(f, "mWatchingInFlight", true);
            androidx.appcompat.view.menu.MenuBuilder menu =
                    new androidx.appcompat.view.menu.MenuBuilder(mActivity);
            f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
            assertFalse(menu.findItem(android.view.Menu.FIRST).isEnabled());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_loggedOutAddsNoItem() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(mActivity);
        f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
        assertNull(menu.findItem(android.view.Menu.FIRST));
    }

    @Test
    public void onOptionsItemSelected_watchItemUnwatchesWhenWatching() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            when(service.deleteRepositorySubscription(anyString(), anyString()))
                    .thenReturn(Single.just(retrofit2.Response.success(null)));
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsWatching"));
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            pumpMainLooper();
            assertEquals(Boolean.FALSE, field(f, "mIsWatching"));
            assertEquals(Boolean.FALSE, field(f, "mWatchingInFlight"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onOptionsItemSelected_watchItemWatchesWhenNotWatching() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(false);
            Subscription sub = mock(Subscription.class);
            when(sub.subscribed()).thenReturn(true);
            when(service.setRepositorySubscription(anyString(), anyString(),
                    org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Single.just(retrofit2.Response.success(sub)));
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.FALSE, field(f, "mIsWatching"));
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsWatching"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItemReturnsFalse() {
        WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(item.getItemId()).thenReturn(12345);
        assertFalse(f.onOptionsItemSelected(item));
    }

    @Test
    public void toggleWatchingState_inFlightIsNoop() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
            setField(f, "mIsWatching", Boolean.TRUE);
            setField(f, "mWatchingInFlight", true);
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            org.mockito.Mockito.verify(service,
                    org.mockito.Mockito.never()).deleteRepositorySubscription(
                            anyString(), anyString());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void toggleWatchingState_unknownStateIsNoop() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = attach(WatcherListFragment.newInstance("octo", "cat"));
            setField(f, "mIsWatching", null);
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            org.mockito.Mockito.verify(service,
                    org.mockito.Mockito.never()).deleteRepositorySubscription(
                            anyString(), anyString());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onRefresh_reloadsWatchingStateAndPage() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            WatchingService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(WatchingService.class), anyBoolean()))
                    .thenReturn(service);
            WatcherListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsWatching"));
            f.onRefresh();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsWatching"));
            org.mockito.Mockito.verify(service, org.mockito.Mockito.atLeast(2))
                    .getRepositorySubscription(anyString(), anyString());
        } finally {
            resetSchedulers();
        }
    }
}
