package com.gh4a.fragment;

import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.activities.home.HomeActivityTestHelper;
import com.gh4a.activities.home.TrendingFactory;
import com.gh4a.model.NotificationListLoadResult;
import com.gh4a.model.Trend;
import com.gh4a.model.TrendService;
import com.gh4a.utils.HorizontalSwipeListener;
import com.gh4a.utils.NotificationCountProvider;
import com.gh4a.utils.SingleFactory;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.activity.NotificationService;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** TrendingFragment：趋势榜（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class TrendingFragmentTest {
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
    public void newInstance_nullTypeReturnsNull() {
        assertNull(TrendingFragment.newInstance(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstance_unknownTypeThrows() {
        TrendingFragment.newInstance("bogus");
    }

    @Test
    public void newInstance_dailyStoresTypeAndStarsTemplate() {
        TrendingFragment f = TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY);
        assertEquals("daily", f.getArguments().getString("type"));
        assertEquals(R.string.trend_stars_today,
                f.getArguments().getInt("stars_template", 0));
    }

    @Test
    public void newInstance_weeklyAndMonthlyTemplates() {
        assertEquals(R.string.trend_stars_week,
                TrendingFragment.newInstance(TrendingFragment.TYPE_WEEKLY)
                        .getArguments().getInt("stars_template", 0));
        assertEquals(R.string.trend_stars_month,
                TrendingFragment.newInstance(TrendingFragment.TYPE_MONTHLY)
                        .getArguments().getInt("stars_template", 0));
    }

    @Test
    public void onCreate_readsTypeAndStarsTemplate() throws Exception {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_WEEKLY));
        Field type = TrendingFragment.class.getDeclaredField("mType");
        Field template = TrendingFragment.class.getDeclaredField("mStarsTemplate");
        type.setAccessible(true);
        template.setAccessible(true);
        assertEquals("weekly", type.get(f));
        assertEquals(R.string.trend_stars_week, template.get(f));
    }

    @Test
    public void getEmptyTextResId() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        assertEquals(R.string.no_trends_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_returnsLazySinglesWithoutSubscribing() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        // 只建 Single 不订阅：趋势拉取在订阅前不发真实请求。
        assertNotNull(f.onCreateDataSingle(false));
        assertNotNull(f.onCreateDataSingle(true));
    }

    @Test
    public void makeRangeSwipeListener_returnsListener() throws Exception {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        java.lang.reflect.Method m =
                TrendingFragment.class.getDeclaredMethod("makeRangeSwipeListener");
        m.setAccessible(true);
        assertNotNull(m.invoke(f));
    }

    @Test
    public void onItemClick_withOwnerAndNameStartsRepositoryActivity() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        f.onItemClick(new Trend("octo", "cat", "desc", "Java", 100, 5, 10));
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
    }

    @Test
    public void onItemClick_nullOwnerStartsNothing() {
        TrendingFragment f = attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
        f.onItemClick(new Trend(null, "cat", "desc", "Java", 100, 5, 10));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        f.onItemClick(new Trend("octo", null, "desc", "Java", 100, 5, 10));
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    // ---- Home-hosted: range row, swipes, destroy (needs a HomeActivity) ----

    public static class NoLoad extends TrendingFragment {
        @Override
        protected Single<List<Trend>> onCreateDataSingle(boolean bypassCache) {
            return Single.never();
        }
    }

    // HomeActivity starts by firing two real requests (loadUserInfo via
    // UserService, notification indicator via NotificationService) plus a
    // SingleFactory.getNotifications call; all are stubbed to empty data
    // (copied from HomeActivityTest). The DiscoverHub page also creates a
    // real TrendingFragment on start, so its trend load is stubbed too.
    private MockedStatic<ServiceFactory> mockedServiceFactory;
    private MockedStatic<SingleFactory> mockedSingleFactory;

    @Before
    public void stubNetworkServices() {
        NotificationCountProvider.getInstance().invalidate();

        UserService userService = mock(UserService.class);
        User octocat = User.builder().login("octocat").id(1L).build();
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
        mockedSingleFactory.when(() -> SingleFactory.loadTrends(anyString()))
                .thenReturn(Single.never());
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

    private static Object getField(Object target, String name) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setStaticField(Class<?> cls, String name, Object value) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static NoLoad attachToHome(HomeActivity activity, String type) {
        NoLoad f = new NoLoad();
        f.setArguments(TrendingFragment.newInstance(type).getArguments());
        activity.getSupportFragmentManager().beginTransaction()
                .add(f, "test").commitNow();
        return f;
    }

    private static int trendRange(HomeActivity activity) {
        Object factory = getField(activity, "mFactory");
        TrendingFactory trendingFactory =
                (TrendingFactory) getField(factory, "mTrendingFactory");
        return trendingFactory.getRange();
    }

    private static View rangeRow(NoLoad f) {
        return (View) getField(f, "mPinnedRangeRow");
    }

    private static MotionEvent down(float x, float y) {
        return MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
    }

    private static MotionEvent up(float x, float y) {
        return MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, x, y, 0);
    }

    private static void callSwitchTrendRange(TrendingFragment f, int step) {
        try {
            Method m = TrendingFragment.class.getDeclaredMethod("switchTrendRange", int.class);
            m.setAccessible(true);
            m.invoke(f, step);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static RecyclerView.OnItemTouchListener findForRecyclerViewListener(RecyclerView rv) {
        @SuppressWarnings("unchecked")
        List<RecyclerView.OnItemTouchListener> listeners =
                (List<RecyclerView.OnItemTouchListener>)
                        getField(rv, "mOnItemTouchListeners");
        for (RecyclerView.OnItemTouchListener l : listeners) {
            if (l instanceof HorizontalSwipeListener.ForRecyclerView) {
                return l;
            }
        }
        return null;
    }

    @Test
    public void rangeRow_pinnedOnAttach() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            assertNotNull(rangeRow(f));
            assertNotNull(rangeRow(f).findViewById(R.id.trend_range_today));
            assertNotNull(rangeRow(f).findViewById(R.id.trend_range_week));
            assertNotNull(rangeRow(f).findViewById(R.id.trend_range_month));
        }
    }

    @Test
    public void rangeClick_nonSelected_callsSetTrendRange() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            assertEquals(0, trendRange(activity));
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            TextView week = rangeRow(f).findViewById(R.id.trend_range_week);
            assertNotNull(week);
            week.performClick();
            assertEquals(1, trendRange(activity));
        }
    }

    @Test
    public void rangeClick_selected_doesNotCallSetTrendRange() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            TextView today = rangeRow(f).findViewById(R.id.trend_range_today);
            today.performClick();
            assertEquals(0, trendRange(activity));
        }
    }

    @Test
    public void rangeSwipe_left_switchesToNextRange() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            Method m = TrendingFragment.class.getDeclaredMethod("makeRangeSwipeListener");
            m.setAccessible(true);
            View.OnTouchListener listener = (View.OnTouchListener) m.invoke(f);
            View row = rangeRow(f);
            listener.onTouch(row, down(300, 100));
            listener.onTouch(row, up(100, 100));
            assertEquals(1, trendRange(activity));
        }
    }

    @Test
    public void rangeSwipe_right_switchesToPreviousRange() throws Exception {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_MONTHLY);
            Method m = TrendingFragment.class.getDeclaredMethod("makeRangeSwipeListener");
            m.setAccessible(true);
            View.OnTouchListener listener = (View.OnTouchListener) m.invoke(f);
            View row = rangeRow(f);
            listener.onTouch(row, down(100, 100));
            listener.onTouch(row, up(300, 100));
            assertEquals(1, trendRange(activity));
        }
    }

    @Test
    public void rangeSwipe_left_weekly_switchesToMonthly() throws Exception {
        // The weekly arm of the switchTrendRange ternary (line 148):
        // no other test calls switchTrendRange on a weekly fragment.
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_WEEKLY);
            Method m = TrendingFragment.class.getDeclaredMethod("makeRangeSwipeListener");
            m.setAccessible(true);
            View.OnTouchListener listener = (View.OnTouchListener) m.invoke(f);
            View row = rangeRow(f);
            listener.onTouch(row, down(300, 100));
            listener.onTouch(row, up(100, 100));
            assertEquals(2, trendRange(activity));
        }
    }

    @Test
    public void onDestroyView_noPinnedRow_skipsRemove() {
        // Plain FragmentActivity host: onRecyclerViewInflated returns early,
        // so mPinnedRangeRow stays null and onDestroyView short-circuits.
        NoLoad f = new NoLoad();
        f.setArguments(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY).getArguments());
        FragmentActivity activity = FragmentTestHelper.attach(f);
        assertNull(getField(f, "mPinnedRangeRow"));
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        assertNull(getField(f, "mPinnedRangeRow"));
    }

    @Test
    public void onDestroyView_rowSetNonHomeHost_skipsRemove() {
        // Row set but host is not a HomeActivity: the guard's second half
        // is false (line 178 false arm), so the row is left alone.
        NoLoad f = new NoLoad();
        f.setArguments(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY).getArguments());
        FragmentActivity activity = FragmentTestHelper.attach(f);
        View dummyRow = new View(activity);
        setField(f, "mPinnedRangeRow", dummyRow);
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        assertSame(dummyRow, getField(f, "mPinnedRangeRow"));
    }

    @Test
    public void recyclerViewSwipe_left_switchesToNextRange() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            RecyclerView rv = f.getView().findViewById(R.id.list);
            assertNotNull(rv);
            RecyclerView.OnItemTouchListener forRv = findForRecyclerViewListener(rv);
            assertNotNull(forRv);
            forRv.onInterceptTouchEvent(rv, down(300, 100));
            forRv.onInterceptTouchEvent(rv, up(100, 100));
            assertEquals(1, trendRange(activity));
        }
    }

    @Test
    public void recyclerViewSwipe_right_switchesToPreviousRange() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_MONTHLY);
            RecyclerView rv = f.getView().findViewById(R.id.list);
            assertNotNull(rv);
            RecyclerView.OnItemTouchListener forRv = findForRecyclerViewListener(rv);
            assertNotNull(forRv);
            forRv.onInterceptTouchEvent(rv, down(100, 100));
            forRv.onInterceptTouchEvent(rv, up(300, 100));
            assertEquals(1, trendRange(activity));
        }
    }

    @Test
    public void switchTrendRange_dailyStepDown_stays() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            callSwitchTrendRange(f, -1);
            assertEquals(0, trendRange(activity));
        }
    }

    @Test
    public void switchTrendRange_monthlyStepUp_stays() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_MONTHLY);
            callSwitchTrendRange(f, 1);
            assertEquals(0, trendRange(activity));
        }
    }

    @Test
    public void switchTrendRange_detached_returnsEarly() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
            assertFalse(f.isAdded());
            callSwitchTrendRange(f, 1);
            assertEquals(0, trendRange(activity));
        }
    }

    @Test
    public void onDestroyView_removesPinnedRow() {
        try (ActivityController<HomeActivity> c = HomeActivityTestHelper.launchHome()) {
            HomeActivity activity = c.setup().get();
            NoLoad f = attachToHome(activity, TrendingFragment.TYPE_DAILY);
            assertNotNull(rangeRow(f));
            activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
            assertNull(getField(f, "mPinnedRangeRow"));
        }
    }

    // ---- no-cache interceptor (line 224) ----

    @Test
    public void noCacheInterceptor_addsNoCacheHeader() throws Exception {
        // Reset the cached service so the client is rebuilt in this test
        // (another test may have built it already).
        setStaticField(TrendingFragment.class, "sNoCacheTrendService", null);
        try {
            TrendService trendService = mock(TrendService.class);
            when(trendService.getTrends(anyString())).thenReturn(
                    Single.just(Response.success(Collections.<Trend>emptyList())));
            ArgumentCaptor<OkHttpClient> clientCaptor =
                    ArgumentCaptor.forClass(OkHttpClient.class);
            mockedSingleFactory.when(() -> SingleFactory.trendService(clientCaptor.capture()))
                    .thenReturn(trendService);
            TrendingFragment f =
                    attach(TrendingFragment.newInstance(TrendingFragment.TYPE_DAILY));
            assertNotNull(f.onCreateDataSingle(true));
            OkHttpClient client = clientCaptor.getValue();
            assertNotNull(client);
            assertFalse(client.interceptors().isEmpty());
            // The no-cache interceptor is added last (after the mirror one).
            Interceptor noCache =
                    client.interceptors().get(client.interceptors().size() - 1);
            Request request = new Request.Builder().url("https://example.com/x").build();
            okhttp3.Response response = new okhttp3.Response.Builder().request(request)
                    .protocol(Protocol.HTTP_1_1).code(200).message("OK").build();
            Interceptor.Chain chain = mock(Interceptor.Chain.class);
            when(chain.request()).thenReturn(request);
            ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
            when(chain.proceed(requestCaptor.capture())).thenReturn(response);
            noCache.intercept(chain);
            assertEquals("no-cache", requestCaptor.getValue().header("Cache-Control"));
        } finally {
            setStaticField(TrendingFragment.class, "sNoCacheTrendService", null);
        }
    }
}
