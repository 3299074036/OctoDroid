package com.gh4a.utils;

import com.gh4a.model.NotificationListLoadResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.Collections;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * B 组试点：NotificationCountProvider。
 *
 * SingleFactory.getNotifications() 用 Mockito mockStatic 挡掉（不发真实请求）；
 * 单例是静态的，各测试前后经反射复位缓存/在途/代数，避免互相污染。
 * TTL 本身是 2 分钟常量，不靠 Thread.sleep 测过期（太慢）；缓存命中与
 * 失效后重拉通过"订阅成功写缓存 → invalidate → 再取"覆盖。
 */
@RunWith(RobolectricTestRunner.class)
public class NotificationCountProviderTest {

    private static NotificationListLoadResult result() {
        return new NotificationListLoadResult(Collections.emptyList());
    }

    /** 经反射复位单例状态（缓存/在途/代数），保证各测试互不污染。 */
    private static void resetProvider() throws Exception {
        NotificationCountProvider provider = NotificationCountProvider.getInstance();
        setField(provider, "mCachedResult", null);
        setField(provider, "mCachedAt", 0L);
        setField(provider, "mInFlight", null);
        setField(provider, "mGeneration", 0);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Before
    public void setUp() throws Exception {
        resetProvider();
    }

    @After
    public void tearDown() throws Exception {
        resetProvider();
    }

    @Test
    public void getInstance_returnsSameSingleton() {
        assertSame(NotificationCountProvider.getInstance(),
                NotificationCountProvider.getInstance());
    }

    @Test
    public void invalidate_isIdempotentAndHarmless() {
        NotificationCountProvider provider = NotificationCountProvider.getInstance();
        provider.invalidate();
        provider.invalidate();
    }

    @Test
    public void getNotifications_returnsNonNullSingle() {
        // 未订阅时不发网络请求：只断言拿到 Single（ServiceFactory 仅在
        // 订阅时才真正建服务/发请求）。
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result()));
            Single<?> single = NotificationCountProvider.getInstance().getNotifications();
            assertNotNull(single);
        }
    }

    @Test
    public void getNotifications_inFlightSingleIsShared() {
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result()));
            NotificationCountProvider provider = NotificationCountProvider.getInstance();
            Single<?> first = provider.getNotifications();
            Single<?> second = provider.getNotifications();
            // 在途请求未完成时后来者共享同一个 Single
            assertSame(first, second);
        }
    }

    @Test
    public void getNotifications_afterInvalidateWhileInFlightReturnsSameSingle() {
        // invalidate 不取消在途请求（类注释写明的设计）：在途期间再取，
        // 仍共享同一个 Single；它回来后因代数已变不会写缓存。
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result()));
            NotificationCountProvider provider = NotificationCountProvider.getInstance();
            Single<?> first = provider.getNotifications();
            provider.invalidate();
            Single<?> second = provider.getNotifications();
            assertSame(first, second);
        }
    }

    @Test
    public void getNotifications_cacheHitWithinTtlReturnsCachedResult() {
        NotificationListLoadResult cached = result();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(cached));
            NotificationCountProvider provider = NotificationCountProvider.getInstance();
            Single<NotificationListLoadResult> first = provider.getNotifications();
            // 订阅成功后写缓存、在途清掉
            TestObserver<NotificationListLoadResult> observer = first.test();
            observer.assertValue(cached);
            observer.assertComplete();

            Single<NotificationListLoadResult> second = provider.getNotifications();
            assertNotSame(first, second);
            // TTL 内不再调工厂：只发过一次请求
            mocked.verify(() -> SingleFactory.getNotifications(false, false, false), times(1));
            TestObserver<NotificationListLoadResult> secondObserver = second.test();
            secondObserver.assertValue(cached);
            secondObserver.assertComplete();
        }
    }

    @Test
    public void getNotifications_afterInvalidateRefetches() {
        NotificationListLoadResult firstResult = result();
        NotificationListLoadResult secondResult = result();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(firstResult));
            NotificationCountProvider provider = NotificationCountProvider.getInstance();
            Single<NotificationListLoadResult> first = provider.getNotifications();
            first.test().assertValue(firstResult);

            provider.invalidate();
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(secondResult));
            Single<NotificationListLoadResult> second = provider.getNotifications();
            assertNotSame(first, second);
            second.test().assertValue(secondResult);
            mocked.verify(() -> SingleFactory.getNotifications(false, false, false), times(2));
        }
    }

    @Test
    public void getNotifications_errorDoesNotWriteCache() {
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.error(new IOException("boom")));
            NotificationCountProvider provider = NotificationCountProvider.getInstance();
            Single<NotificationListLoadResult> first = provider.getNotifications();
            TestObserver<NotificationListLoadResult> observer = first.test();
            observer.assertError(IOException.class);
            assertTrue(observer.errors().get(0).getMessage().contains("boom"));

            // 失败不写缓存、在途已清：再取会重新调工厂（新 Single）
            Single<NotificationListLoadResult> second = provider.getNotifications();
            assertNotSame(first, second);
            mocked.verify(() -> SingleFactory.getNotifications(false, false, false), times(2));
        }
    }
}
