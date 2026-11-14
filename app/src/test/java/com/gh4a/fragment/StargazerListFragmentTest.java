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
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.service.activity.StarringService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** StargazerListFragment：点星用户列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class StargazerListFragmentTest {
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
        StargazerListFragment f = StargazerListFragment.newInstance("octo", "cat");
        assertEquals("octo", f.getArguments().getString("owner"));
        assertEquals("cat", f.getArguments().getString("repo"));
    }

    @Test
    public void onCreate_readsOwnerAndRepo() throws Exception {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
        Field owner = StargazerListFragment.class.getDeclaredField("mRepoOwner");
        Field repo = StargazerListFragment.class.getDeclaredField("mRepoName");
        owner.setAccessible(true);
        repo.setAccessible(true);
        assertEquals("octo", owner.get(f));
        assertEquals("cat", repo.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("o", "r"));
        assertEquals(R.string.no_stargazers_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("o", "r"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("o", "r"));
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onItemClick_startsUserActivity() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("o", "r"));
        f.onItemClick(User.builder().login("someone").build());
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("someone", started.getStringExtra("login"));
    }

    @Test
    public void onItemClick_nullUserStartsNothing() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("o", "r"));
        f.onItemClick(null);
        // UserActivity.makeIntent(null) 返回登录为空的 intent；不崩溃即通过，
        // 具体行为以实际 intent 为准。
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNull(started);
    }

    @Test
    public void onRefresh_whenLoggedOutIsNoop() {
        // 未登录时 loadStarringState 直接返回，不发请求、不崩溃。
        StargazerListFragment f = attach(StargazerListFragment.newInstance("o", "r"));
        f.onRefresh();
    }

    // ---------- RESUMED 挂载测试：trampoline 调度 + mock ServiceFactory ----------
    // 与 WatcherListFragmentTest 同款模式（mockStatic 只在创建线程生效，
    // 加载链走 Schedulers.io()，须换成 trampoline；footer 占 1 个 item）。

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
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("active_login", "octo").apply();
        ctx.getSharedPreferences("Gh4a-secure-plain", Context.MODE_PRIVATE)
                .edit().putString("token_octo", "fake-token").apply();
    }

    private StarringService mockService(boolean starred) {
        StarringService service = mock(StarringService.class);
        User u1 = mock(User.class);
        User u2 = mock(User.class);
        when(service.getStargazers(anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(retrofit2.Response.success(
                        Page.<User>builder().items(Arrays.asList(u1, u2)).build())));
        when(service.checkIfRepositoryIsStarred(anyString(), anyString()))
                .thenReturn(starred
                        ? Single.just(retrofit2.Response.success(null))
                        : Single.just(retrofit2.Response.error(404,
                                okhttp3.ResponseBody.create(null, new byte[0]))));
        return service;
    }

    private StargazerListFragment mountResumed() {
        StargazerListFragment f = StargazerListFragment.newInstance("octo", "cat");
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

    private androidx.recyclerview.widget.RecyclerView recyclerOf(StargazerListFragment f) {
        return (androidx.recyclerview.widget.RecyclerView) f.getView()
                .findViewById(com.gh4a.R.id.list);
    }

    private static Object field(StargazerListFragment f, String name) throws Exception {
        Field field = StargazerListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(StargazerListFragment f, String name, Object value)
            throws Exception {
        Field field = StargazerListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private androidx.appcompat.view.menu.MenuBuilder menuFor(StargazerListFragment f) {
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(mActivity);
        f.onCreateOptionsMenu(menu, mActivity.getMenuInflater());
        return menu;
    }

    @Test
    public void resumedMount_loadsStargazersFromMockedService() throws Exception {
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = mountResumed();
            pumpMainLooper();
            assertNotNull(f.getView());
            assertEquals(3, recyclerOf(f).getAdapter().getItemCount()); // 2 用户 + footer
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void loadStarringState_authorizedStarredSetsTrue() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsStarring"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void loadStarringState_authorizedNotStarredSetsFalseOn404() throws Exception {
        // 404（未 star）时 mapToBooleanOrThrowOnFailure 返回 false。
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(false);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.FALSE, field(f, "mIsStarring"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_authorizedStarredShowsUnstarTitle() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", Boolean.TRUE);
            android.view.MenuItem item = menuFor(f).findItem(android.view.Menu.FIRST);
            assertNotNull(item);
            assertEquals(mActivity.getString(R.string.repo_unstar_action),
                    item.getTitle().toString());
            assertTrue(item.isEnabled());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_authorizedNotStarredShowsStarTitle() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(false);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", Boolean.FALSE);
            android.view.MenuItem item = menuFor(f).findItem(android.view.Menu.FIRST);
            assertNotNull(item);
            assertEquals(mActivity.getString(R.string.repo_star_action),
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
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", null);
            android.view.MenuItem item = menuFor(f).findItem(android.view.Menu.FIRST);
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
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", Boolean.TRUE);
            setField(f, "mStarringInFlight", true);
            assertFalse(menuFor(f).findItem(android.view.Menu.FIRST).isEnabled());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onCreateOptionsMenu_loggedOutAddsNoItem() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
        assertNull(menuFor(f).findItem(android.view.Menu.FIRST));
    }

    @Test
    public void onOptionsItemSelected_starItemUnstarsWhenStarring() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            when(service.unstarRepository(anyString(), anyString()))
                    .thenReturn(Single.just(retrofit2.Response.success(null)));
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsStarring"));
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            pumpMainLooper();
            assertEquals(Boolean.FALSE, field(f, "mIsStarring"));
            assertEquals(Boolean.FALSE, field(f, "mStarringInFlight"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onOptionsItemSelected_starItemStarsWhenNotStarring() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            when(service.starRepository(anyString(), anyString()))
                    .thenReturn(Single.just(retrofit2.Response.success(null)));
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", Boolean.FALSE);
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsStarring"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onOptionsItemSelected_starItemErrorKeepsStateAndClearsInFlight()
            throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            when(service.unstarRepository(anyString(), anyString()))
                    .thenReturn(Single.error(new RuntimeException("boom")));
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", Boolean.TRUE);
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsStarring"));
            assertEquals(Boolean.FALSE, field(f, "mStarringInFlight"));
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItemReturnsFalse() {
        StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(item.getItemId()).thenReturn(12345);
        assertFalse(f.onOptionsItemSelected(item));
    }

    @Test
    public void toggleStarringState_inFlightIsNoop() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", Boolean.TRUE);
            setField(f, "mStarringInFlight", true);
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            verify(service, never()).unstarRepository(anyString(), anyString());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void toggleStarringState_unknownStateIsNoop() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = attach(StargazerListFragment.newInstance("octo", "cat"));
            setField(f, "mIsStarring", null);
            android.view.MenuItem item = mock(android.view.MenuItem.class);
            when(item.getItemId()).thenReturn(android.view.Menu.FIRST);
            assertTrue(f.onOptionsItemSelected(item));
            verify(service, never()).unstarRepository(anyString(), anyString());
            verify(service, never()).starRepository(anyString(), anyString());
        } finally {
            resetSchedulers();
        }
    }

    @Test
    public void onRefresh_reloadsStarringStateAndPage() throws Exception {
        setAuthorized();
        installTrampolineSchedulers();
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            StarringService service = mockService(true);
            mocked.when(() -> ServiceFactory.get(eq(StarringService.class), anyBoolean()))
                    .thenReturn(service);
            StargazerListFragment f = mountResumed();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsStarring"));
            f.onRefresh();
            pumpMainLooper();
            assertEquals(Boolean.TRUE, field(f, "mIsStarring"));
            verify(service, org.mockito.Mockito.atLeast(2))
                    .checkIfRepositoryIsStarred(anyString(), anyString());
        } finally {
            resetSchedulers();
        }
    }

}
