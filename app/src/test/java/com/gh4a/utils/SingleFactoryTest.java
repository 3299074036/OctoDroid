package com.gh4a.utils;

import android.os.Parcel;

import com.gh4a.ApiRequestException;
import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.gh4a.model.Feed;
import com.gh4a.model.GitHubFeed;
import com.gh4a.model.GitHubFeedService;
import com.gh4a.model.NotificationListLoadResult;
import com.gh4a.model.Trend;
import com.gh4a.model.TrendService;
import com.meisolsson.githubsdk.model.NotificationReason;
import com.meisolsson.githubsdk.model.NotificationSubject;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.activity.NotificationService;
import com.meisolsson.githubsdk.service.repositories.RepositoryCollaboratorService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.simpleframework.xml.transform.Transform;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B 组试点：SingleFactory。notificationsToResult 是纯分组/排序逻辑，
 * 用 Builder（Repository）与匿名子类（NotificationThread）构造模型；
 * trendService 只断言拼装出非空服务。isAppUserRepoCollaborator /
 * getNotifications / getNotificationsPaged / loadFeed / loadTrends /
 * RetrofitHelper 用 mockStatic（ServiceFactory / RetrofitHelper）
 * 覆盖，网络调用永不真实发出；因用到 Gh4Application，整类跑
 * RobolectricTestRunner（既有纯 JVM 测试在其下仍通过）。
 */
@RunWith(RobolectricTestRunner.class)
public class SingleFactoryTest {

    private static Repository repo(String fullName) {
        return Repository.builder().fullName(fullName).build();
    }

    private static NotificationThread thread(Repository repository, Boolean unread,
            Date updatedAt) {
        return new NotificationThread() {
            @Override
            public String id() {
                return "1";
            }

            @Override
            public Repository repository() {
                return repository;
            }

            @Override
            public NotificationSubject subject() {
                return null;
            }

            @Override
            public NotificationReason reason() {
                return null;
            }

            @Override
            public String url() {
                return null;
            }

            @Override
            public Boolean unread() {
                return unread;
            }

            @Override
            public Date updatedAt() {
                return updatedAt;
            }

            @Override
            public Date lastReadAt() {
                return null;
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
            }
        };
    }

    @Test
    public void notificationsToResult_emptyList() {
        NotificationListLoadResult result =
                SingleFactory.notificationsToResult(new ArrayList<>());
        assertTrue(result.notifications.isEmpty());
        assertNotNull(result.loadTime);
    }

    @Test
    public void notificationsToResult_singleRepoGroupsAndMarksRead() {
        Repository r = repo("octocat/Hello-World");
        List<NotificationThread> input = Arrays.asList(
                thread(r, true, new Date(1000)),
                thread(r, false, new Date(3000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        // [repo 头, 较新通知, 较旧通知]：组内按 updatedAt 降序
        assertEquals(3, result.notifications.size());
        assertNull(result.notifications.get(0).notification);
        assertEquals(r, result.notifications.get(0).repository);
        assertFalse(result.notifications.get(0).isRead());
        assertEquals(new Date(3000),
                result.notifications.get(1).notification.updatedAt());
        assertEquals(new Date(1000),
                result.notifications.get(2).notification.updatedAt());
        assertFalse(result.notifications.get(1).isLastRepositoryNotification());
        assertTrue(result.notifications.get(2).isLastRepositoryNotification());
    }

    @Test
    public void notificationsToResult_allReadMarksRepoRead() {
        Repository r = repo("o/r");
        NotificationListLoadResult result = SingleFactory.notificationsToResult(
                Arrays.asList(thread(r, false, new Date(1000)),
                        thread(r, false, new Date(2000))));
        assertTrue(result.notifications.get(0).isRead());
    }

    @Test
    public void notificationsToResult_nullUnreadCountsAsRead() {
        // 注意：SingleFactory:122 的注释写"缺字段按未读计"，但实现
        //（此处、NotificationHolder、HomeActivity.countUnread 三处一致）
        // 均为 Boolean.TRUE.equals → null 按已读计，与 orFalse(null)=false
        // 同款；注释文字疑似笔误，行为本身全树一致，此处锁定实现行为。
        Repository r = repo("o/r");
        NotificationListLoadResult result = SingleFactory.notificationsToResult(
                Collections.singletonList(thread(r, null, new Date(1000))));
        assertTrue(result.notifications.get(0).isRead());
        assertTrue(result.notifications.get(1).isRead());
    }

    @Test
    public void notificationsToResult_groupsSortedByNewestFirst() {
        Repository older = repo("o/older");
        Repository newer = repo("o/newer");
        List<NotificationThread> input = Arrays.asList(
                thread(older, false, new Date(5000)),
                thread(newer, false, new Date(1000)),
                thread(newer, false, new Date(9000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        // 组按组内最新通知降序：newer(9000) 在前，older(5000) 在后
        assertEquals(5, result.notifications.size());
        assertEquals(newer, result.notifications.get(0).repository);
        assertEquals(new Date(9000),
                result.notifications.get(1).notification.updatedAt());
        assertEquals(older, result.notifications.get(3).repository);
    }

    @Test
    public void notificationsToResult_sameRepoDifferentInstancesGrouped() {
        // Repository 是 AutoValue：同 fullName 的两个实例相等，应归入同一组
        List<NotificationThread> input = Arrays.asList(
                thread(repo("o/r"), false, new Date(1000)),
                thread(repo("o/r"), false, new Date(2000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(3, result.notifications.size());
    }

    @Test
    public void notificationsToResult_nullUpdatedAtSortsLast() {
        Repository r = repo("o/r");
        List<NotificationThread> input = Arrays.asList(
                thread(r, false, null),
                thread(r, false, new Date(1000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(new Date(1000),
                result.notifications.get(1).notification.updatedAt());
        assertNull(result.notifications.get(2).notification.updatedAt());
    }

    @Test
    public void notificationsToResult_bothNullUpdatedAtKeepsBoth() {
        Repository r = repo("o/r");
        List<NotificationThread> input = Arrays.asList(
                thread(r, false, null),
                thread(r, false, null));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(3, result.notifications.size());
    }

    @Test
    public void notificationsToResult_nullRepositoryGroupedSeparately() {
        List<NotificationThread> input = Arrays.asList(
                thread(null, false, new Date(1000)),
                thread(repo("o/r"), false, new Date(2000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(4, result.notifications.size());
        // o/r 组（2000）排在 null 仓库组（1000）前面
        assertEquals(repo("o/r"), result.notifications.get(0).repository);
        assertNull(result.notifications.get(2).repository);
    }

    @Test
    public void trendService_buildsNonNullService() {
        assertNotNull(SingleFactory.trendService(new OkHttpClient()));
    }
    // ---- RetrofitHelper 静态量每测清零：initialize / 并发首调的
    // 初始化结果不跨方法泄漏；登录态同样每测清理。
    @Before
    public void setUp() throws Exception {
        clearRetrofitHelperStatics();
    }

    @After
    public void tearDown() throws Exception {
        clearRetrofitHelperStatics();
        if (Gh4Application.get().isAuthorized()) {
            Gh4Application.get().logout();
        }
    }

    private static Class<?> retrofitHelperClass() throws Exception {
        return Class.forName("com.gh4a.utils.SingleFactory$RetrofitHelper");
    }

    private static void clearRetrofitHelperStatics() throws Exception {
        Class<?> clazz = retrofitHelperClass();
        Field feedServiceField = clazz.getDeclaredField("sFeedService");
        feedServiceField.setAccessible(true);
        feedServiceField.set(null, null);
        Field trendServiceField = clazz.getDeclaredField("sTrendService");
        trendServiceField.setAccessible(true);
        trendServiceField.set(null, null);
    }

    private static Object callRetrofitHelper(String methodName) throws Exception {
        Class<?> clazz = retrofitHelperClass();
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static void loginAsTester() {
        Gh4Application.get().addAccount(
                User.builder().login("tester").id(999L).build(), "fake-token");
    }

    @Test
    public void defaultConstructor_instantiates() {
        assertNotNull(new SingleFactory());
    }

    @Test
    public void notificationsToResult_datedBeforeNull_lhsNullSortsLast() {
        // 135/138/139：比较器以 (null, dated) 调用时才走 return 1；
        // 输入 [dated, null] 时 TimSort 正是以该顺序比较。
        Repository r = repo("o/r");
        List<NotificationThread> input = Arrays.asList(
                thread(r, false, new Date(1000)),
                thread(r, false, null));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(new Date(1000),
                result.notifications.get(1).notification.updatedAt());
        assertNull(result.notifications.get(2).notification.updatedAt());
    }

    @Test
    public void isAppUserRepoCollaborator_unauthorized_returnsFalse() {
        assertFalse(Gh4Application.get().isAuthorized());
        SingleFactory.isAppUserRepoCollaborator("o", "r", false)
                .test()
                .assertValue(false)
                .assertComplete();
    }

    private static RepositoryCollaboratorService mockCollaboratorService(
            MockedStatic<ServiceFactory> mocked, Single<Response<Void>> result) {
        RepositoryCollaboratorService service =
                mock(RepositoryCollaboratorService.class);
        mocked.when(() -> ServiceFactory.get(
                        eq(RepositoryCollaboratorService.class), anyBoolean()))
                .thenReturn(service);
        when(service.isUserCollaborator(eq("o"), eq("r"), eq("tester")))
                .thenReturn(result);
        return service;
    }

    @Test
    public void isAppUserRepoCollaborator_authorized_success_returnsTrue() {
        loginAsTester();
        try (MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mockCollaboratorService(mocked,
                    Single.just(Response.success((Void) null)));
            SingleFactory.isAppUserRepoCollaborator("o", "r", false)
                    .test()
                    .assertValue(true)
                    .assertComplete();
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void isAppUserRepoCollaborator_authorized_403_returnsFalse() {
        loginAsTester();
        try (MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mockCollaboratorService(mocked, Single.just(Response.error(403,
                    ResponseBody.create(MediaType.parse("text/plain"), "forbidden"))));
            SingleFactory.isAppUserRepoCollaborator("o", "r", false)
                    .test()
                    .assertValue(false)
                    .assertComplete();
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void isAppUserRepoCollaborator_authorized_404_propagatesError() {
        loginAsTester();
        try (MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mockCollaboratorService(mocked, Single.just(Response.error(404,
                    ResponseBody.create(MediaType.parse("text/plain"), "not found"))));
            SingleFactory.isAppUserRepoCollaborator("o", "r", false)
                    .test()
                    .assertError(ApiRequestException.class);
        } finally {
            Gh4Application.get().logout();
        }
    }

    private static Page<NotificationThread> notificationPage(
            List<NotificationThread> items, Integer next) {
        Page<NotificationThread> page = mock(Page.class);
        when(page.items()).thenReturn(items);
        when(page.next()).thenReturn(next);
        return page;
    }

    private static NotificationService mockNotificationService(
            Page<NotificationThread> page1, Page<NotificationThread> page2) {
        NotificationService service = mock(NotificationService.class);
        when(service.getNotifications(anyMap(), eq(1L)))
                .thenReturn(Single.just(Response.success(page1)));
        when(service.getNotifications(anyMap(), eq(2L)))
                .thenReturn(Single.just(Response.success(page2)));
        return service;
    }

    @Test
    public void getNotifications_pagesGroupedIntoResult() {
        NotificationThread t = thread(repo("o/r"), false, new Date(1000));
        Page<NotificationThread> page1 =
                notificationPage(Collections.singletonList(t), 2);
        Page<NotificationThread> page2 = notificationPage(Collections.emptyList(), null);

        NotificationService service = mockNotificationService(page1, page2);
        try (MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(NotificationService.class), anyBoolean()))
                    .thenReturn(service);
            SingleFactory.getNotifications(true, false, false)
                    .test()
                    .assertComplete()
                    .assertValue(result -> result.notifications.size() == 2
                            && result.notifications.get(1).notification == t);
        }
    }

    @Test
    public void getNotificationsPaged_emitsEachPage() {
        NotificationThread t = thread(repo("o/r"), false, new Date(1000));
        Page<NotificationThread> page1 =
                notificationPage(Collections.singletonList(t), 2);
        Page<NotificationThread> page2 = notificationPage(Collections.emptyList(), null);

        NotificationService service = mockNotificationService(page1, page2);
        try (MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(
                            eq(NotificationService.class), anyBoolean()))
                    .thenReturn(service);
            SingleFactory.getNotificationsPaged(true, false, false)
                    .test()
                    .assertComplete()
                    .assertValues(
                            Collections.singletonList(t),
                            Collections.<NotificationThread>emptyList());
        }
    }

    @Test
    public void loadFeed_success_returnsEntries() throws Exception {
        GitHubFeed gitHubFeed = new GitHubFeed();
        gitHubFeed.feed = Arrays.asList(mock(Feed.class), mock(Feed.class));

        Class<?> helperClass = retrofitHelperClass();
        try (MockedStatic<?> mocked = Mockito.mockStatic(helperClass)) {
            GitHubFeedService service = mock(GitHubFeedService.class);
            Method feedServiceMethod = helperClass.getDeclaredMethod("feedService");
            feedServiceMethod.setAccessible(true);
            mocked.when(() -> feedServiceMethod.invoke(null)).thenReturn(service);
            when(service.getFeed(anyString()))
                    .thenReturn(Single.just(Response.success(gitHubFeed)));
            SingleFactory.loadFeed("https://example.com/feed.atom")
                    .test()
                    .assertComplete()
                    .assertValue(feeds -> feeds.size() == 2);
        }
    }

    @Test
    public void loadFeed_nullFeed_returnsEmptyList() throws Exception {
        // T-121：零条目 feed（feed.feed 为 null）不再 NPE，返回空列表。
        GitHubFeed gitHubFeed = new GitHubFeed();
        gitHubFeed.feed = null;

        Class<?> helperClass = retrofitHelperClass();
        try (MockedStatic<?> mocked = Mockito.mockStatic(helperClass)) {
            GitHubFeedService service = mock(GitHubFeedService.class);
            Method feedServiceMethod = helperClass.getDeclaredMethod("feedService");
            feedServiceMethod.setAccessible(true);
            mocked.when(() -> feedServiceMethod.invoke(null)).thenReturn(service);
            when(service.getFeed(anyString()))
                    .thenReturn(Single.just(Response.success(gitHubFeed)));
            SingleFactory.loadFeed("https://example.com/feed.atom")
                    .test()
                    .assertComplete()
                    .assertValue(feeds -> feeds.isEmpty());
        }
    }

    @Test
    public void loadFeed_failure_propagatesError() throws Exception {
        Class<?> helperClass = retrofitHelperClass();
        try (MockedStatic<?> mocked = Mockito.mockStatic(helperClass)) {
            GitHubFeedService service = mock(GitHubFeedService.class);
            Method feedServiceMethod = helperClass.getDeclaredMethod("feedService");
            feedServiceMethod.setAccessible(true);
            mocked.when(() -> feedServiceMethod.invoke(null)).thenReturn(service);
            when(service.getFeed(anyString()))
                    .thenReturn(Single.just(Response.error(404,
                            ResponseBody.create(MediaType.parse("text/plain"), "not found"))));
            SingleFactory.loadFeed("https://example.com/feed.atom")
                    .test()
                    .assertError(ApiRequestException.class);
        }
    }

    @Test
    public void loadTrends_success_returnsTrends() throws Exception {
        List<Trend> trends = Arrays.asList(
                new Trend("o", "r", "d", "Java", 10, 5, 2),
                new Trend("o", "r2", "d2", "Kotlin", 20, 3, 1));

        Class<?> helperClass = retrofitHelperClass();
        try (MockedStatic<?> mocked = Mockito.mockStatic(helperClass)) {
            TrendService service = mock(TrendService.class);
            Method trendServiceMethod = helperClass.getDeclaredMethod("trendService");
            trendServiceMethod.setAccessible(true);
            mocked.when(() -> trendServiceMethod.invoke(null)).thenReturn(service);
            when(service.getTrends(eq("daily")))
                    .thenReturn(Single.just(Response.success(trends)));
            SingleFactory.loadTrends("daily")
                    .test()
                    .assertComplete()
                    .assertValue(result -> result.size() == 2
                            && "r2".equals(result.get(1).getRepoName()));
        }
    }

    @Test
    public void loadTrends_failure_propagatesError() throws Exception {
        Class<?> helperClass = retrofitHelperClass();
        try (MockedStatic<?> mocked = Mockito.mockStatic(helperClass)) {
            TrendService service = mock(TrendService.class);
            Method trendServiceMethod = helperClass.getDeclaredMethod("trendService");
            trendServiceMethod.setAccessible(true);
            mocked.when(() -> trendServiceMethod.invoke(null)).thenReturn(service);
            when(service.getTrends(anyString()))
                    .thenReturn(Single.just(Response.error(404,
                            ResponseBody.create(MediaType.parse("text/plain"), "not found"))));
            SingleFactory.loadTrends("daily")
                    .test()
                    .assertError(ApiRequestException.class);
        }
    }

    @Test
    public void retrofitHelper_initialize_buildsBothServices() throws Exception {
        callRetrofitHelper("initialize");
        assertNotNull(callRetrofitHelper("feedService"));
        assertNotNull(callRetrofitHelper("trendService"));
        // 再次调用走 DCL 外层非空分支，返回同一实例
        // 注意：Retrofit 服务代理的 equals 恒为 false（转给 handler
        // 比较），同一性只能用 assertSame 断言。
        assertSame(callRetrofitHelper("feedService"), callRetrofitHelper("feedService"));
        assertSame(callRetrofitHelper("trendService"), callRetrofitHelper("trendService"));
    }

    private static void hammerRetrofitHelper(String methodName, List<Object> results)
            throws Exception {
        clearRetrofitHelperStatics();
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    results.add(callRetrofitHelper(methodName));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(threads, results.size());
        // 全部线程拿到同一实例：首个线程跑 initialize（DCL 内层
        // true 分支），其余线程在 synchronized 内复读到非空
        //（内层 false 分支）
        for (Object service : results) {
            assertSame(results.get(0), service);
        }
    }

    @Test
    public void retrofitHelper_concurrentFirstCalls_shareOneInstance() throws Exception {
        // 两服务各打一轮：initialize 一次置起两个静态量，若先打
        // feedService，trendService 的内层 true 分支永远走不到。
        List<Object> feedServices = Collections.synchronizedList(new ArrayList<>());
        hammerRetrofitHelper("feedService", feedServices);
        List<Object> trendServices = Collections.synchronizedList(new ArrayList<>());
        hammerRetrofitHelper("trendService", trendServices);
    }

    @Test
    public void retrofitHelper_privateConstructor_instantiates() throws Exception {
        Constructor<?> ctor = retrofitHelperClass().getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    public void retrofitHelperTransform_readWrite_delegateToIso8601() throws Exception {
        Class<?> transformClass =
                Class.forName("com.gh4a.utils.SingleFactory$RetrofitHelper$1");
        Constructor<?> ctor = transformClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        @SuppressWarnings("unchecked")
        Transform<Date> transform = (Transform<Date>) ctor.newInstance();
        Date date = transform.read("2024-06-01T12:00:00Z");
        assertNotNull(date);
        assertNotNull(transform.write(date));
    }
    @Test
    public void retrofitHelper_trendService_doubleCheckedLock_innerFalseReturnsPreset()
            throws Exception {
        // L185：内层 if (service == null) 的 false 分支——第二个线程已越过外层
        // 检查后在 synchronized 内复读到非空。32 线程 hammer 在 11-21 实测中
        // 没撞上（1.5 核上线程常被串行调度，外层检查已看到非空）；这里确定性
        // 复现：测试线程先占住 RetrofitHelper.LOCK，工作线程在 trendService()
        // 的 synchronized 处 BLOCKED（已越过外层检查），再把 sTrendService
        // 置为预设值并放锁。
        clearRetrofitHelperStatics();
        Object preset = SingleFactory.trendService(new OkHttpClient());
        Class<?> clazz = retrofitHelperClass();
        Field lockField = clazz.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        Field serviceField = clazz.getDeclaredField("sTrendService");
        serviceField.setAccessible(true);
        java.util.concurrent.atomic.AtomicReference<Object> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(callRetrofitHelper("trendService"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        synchronized (lock) {
            worker.start();
            long deadline = System.currentTimeMillis() + 10_000;
            while (worker.getState() != Thread.State.BLOCKED
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, worker.getState());
            serviceField.set(null, preset);
        }
        worker.join(10_000);
        assertSame(preset, result.get());
    }

    @Test
    public void retrofitHelper_feedService_doubleCheckedLock_innerFalseReturnsPreset()
            throws Exception {
        // L171：内层 if (service == null) 的 false 分支——与 trendService 同款
        // 确定性复现（见上一个测试）。预设用 mock 的 GitHubFeedService（公开
        // 拼装入口只有 trendService；feedService 的 Retrofit 由 initialize()
        // 内部创建，这里只要一个身份可断言的实例）。
        clearRetrofitHelperStatics();
        Object preset = mock(GitHubFeedService.class);
        Class<?> clazz = retrofitHelperClass();
        Field lockField = clazz.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        Field serviceField = clazz.getDeclaredField("sFeedService");
        serviceField.setAccessible(true);
        java.util.concurrent.atomic.AtomicReference<Object> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(callRetrofitHelper("feedService"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        synchronized (lock) {
            worker.start();
            long deadline = System.currentTimeMillis() + 10_000;
            while (worker.getState() != Thread.State.BLOCKED
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, worker.getState());
            serviceField.set(null, preset);
        }
        worker.join(10_000);
        assertSame(preset, result.get());
    }

}
