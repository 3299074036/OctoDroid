package com.gh4a.fragment;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.ApiRequestException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Release;
import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchPage;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.repositories.RepositoryReleaseService;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;
import com.meisolsson.githubsdk.service.search.SearchService;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** StoreApi：商店数据层（反射测私有解析 + 新鲜缓存链路；不发真实请求）。 */
@RunWith(RobolectricTestRunner.class)
public class StoreApiTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    private static Object invokePrivate(String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = StoreApi.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static StoreApi.StoreData parseBackend(String json, boolean offline) throws Exception {
        return (StoreApi.StoreData) invokePrivate("parseBackend",
                new Class<?>[]{String.class, boolean.class}, json, offline);
    }

    /**
     * 等待经 RxUtils.doInBackground（observeOn 主线程）的 Single。
     * 注意：Robolectric 主 looper 是暂停的，直接 blockingGet() 会死锁——
     * 结果经主线程派发，而主线程正阻塞在 blockingGet 里等结果。
     * 这里用 TestObserver 订阅 + 反复 idle() 泵主 looper 直到终止。
     */
    private static <T> T awaitSingle(Single<T> single) {
        io.reactivex.observers.TestObserver<T> observer = single.test();
        for (int i = 0; i < 500 && !observer.isTerminated(); i++) {
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        observer.assertComplete();
        observer.assertNoErrors();
        return observer.values().get(0);
    }

    private static StoreApi.StoreData parseNewest(String json, boolean offline) throws Exception {
        return (StoreApi.StoreData) invokePrivate("parseNewest",
                new Class<?>[]{String.class, boolean.class}, json, offline);
    }

    private static final String ONE_ITEM_BACKEND =
            "{\"generatedAt\":\"2026-01-01T00:00:00Z\",\"items\":["
            + "{\"fullName\":\"octo/cat\",\"owner\":\"octo\",\"name\":\"cat\","
            + "\"description\":\"d\",\"language\":\"Java\",\"stars\":42,\"forks\":7,"
            + "\"latestTag\":\"v1.0\",\"latestDate\":\"2026-01-02T03:04:05Z\","
            + "\"platforms\":[\"android\"],\"assetCount\":1}]}";

    @Test
    public void storeData_threeArgCtorSetsMirrorFalse() {
        StoreApi.StoreData d = new StoreApi.StoreData(
                Collections.emptyList(), true, "2026-01-01");
        assertTrue(d.offline);
        assertFalse(d.mirror);
        assertEquals("2026-01-01", d.generatedAt);
        assertTrue(d.items.isEmpty());
    }

    @Test
    public void storeData_fourArgCtorKeepsAllFields() {
        List<StoreRepo> items = Collections.singletonList(
                new StoreRepo("o/n", "o", "n", "", null, "", 0, 0, 0L,
                        null, null, "", 0L, null, 0, null, null, null, null, null));
        StoreApi.StoreData d = new StoreApi.StoreData(items, true, true, "gen");
        assertEquals(items, d.items);
        assertTrue(d.offline);
        assertTrue(d.mirror);
        assertEquals("gen", d.generatedAt);
    }

    @Test
    public void describeError_nullReturnsNullString() {
        assertEquals("null", StoreApi.describeError(null));
    }

    @Test
    public void describeError_plainExceptionReturnsSimpleName() {
        assertEquals("RuntimeException",
                StoreApi.describeError(new RuntimeException("x")));
        assertEquals("IllegalStateException",
                StoreApi.describeError(new IllegalStateException("x")));
        assertEquals("IOException",
                StoreApi.describeError(new java.io.IOException("x")));
    }

    @Test
    public void describeError_apiExceptionIncludesStatus() {
        Response<Object> response = Response.error(404,
                ResponseBody.create("", MediaType.parse("application/json")));
        ApiRequestException e = new ApiRequestException(response);
        assertEquals("ApiRequestException status=404", StoreApi.describeError(e));
        Response<Object> r500 = Response.error(500,
                ResponseBody.create("", MediaType.parse("application/json")));
        assertEquals("ApiRequestException status=500",
                StoreApi.describeError(new ApiRequestException(r500)));
    }

    @Test
    public void newestQuery_formatAndDateWindow() {
        String q = StoreApi.newestQuery();
        assertTrue("query=" + q, q.matches("created:>\\d{4}-\\d{2}-\\d{2} stars:>50"));
        // 日期应在约 30 天前（±2 天容差，防跨日边界）。
        String date = q.substring("created:>".length(), "created:>".length() + 10);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -30);
        String expected = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(cal.getTime());
        assertTrue("query date " + date + " vs expected " + expected,
                date.equals(expected) || Math.abs(
                        java.time.LocalDate.parse(date).toEpochDay()
                        - java.time.LocalDate.parse(expected).toEpochDay()) <= 1);
    }

    @Test
    public void parseBackend_validPayload() throws Exception {
        StoreApi.StoreData d = parseBackend(ONE_ITEM_BACKEND, false);
        assertNotNull(d);
        assertFalse(d.offline);
        assertEquals("2026-01-01T00:00:00Z", d.generatedAt);
        assertEquals(1, d.items.size());
        assertEquals("octo/cat", d.items.get(0).fullName);
        assertEquals(42, d.items.get(0).stars);
    }

    @Test
    public void parseBackend_offlineFlagPassesThrough() throws Exception {
        StoreApi.StoreData d = parseBackend(ONE_ITEM_BACKEND, true);
        assertNotNull(d);
        assertTrue(d.offline);
    }

    @Test
    public void parseBackend_emptyItemsReturnsNull() throws Exception {
        assertNull(parseBackend("{\"generatedAt\":\"x\",\"items\":[]}", false));
        assertNull(parseBackend("{\"generatedAt\":\"x\"}", false));
    }

    @Test
    public void parseBackend_badItemIsSkippedNotFatal() throws Exception {
        String json = "{\"generatedAt\":\"x\",\"items\":["
                + "{\"fullName\":\"good/one\",\"stars\":5},"
                + "{\"fullName\":12345,\"stars\":\"not-a-number\"},"
                + "{\"fullName\":\"good/two\",\"stars\":9}]}";
        // 第二条：JSONObject optString/optInt 对错型字段有容错，仍可解析；
        // 关键是整组不垮、至少两条好数据都在。
        StoreApi.StoreData d = parseBackend(json, false);
        assertNotNull(d);
        assertTrue(d.items.size() >= 2);
        assertEquals("good/one", d.items.get(0).fullName);
    }

    @Test
    public void parseBackend_invalidJsonReturnsNull() throws Exception {
        assertNull(parseBackend("not json at all", false));
        assertNull(parseBackend("", false));
        assertNull(parseBackend(null, false));
    }

    @Test
    public void parseNewest_bareArrayForm() throws Exception {
        String json = "[{\"fullName\":\"a/b\",\"stars\":3}]";
        StoreApi.StoreData d = parseNewest(json, false);
        assertNotNull(d);
        assertEquals(1, d.items.size());
        assertEquals("a/b", d.items.get(0).fullName);
        assertEquals("", d.generatedAt);
    }

    @Test
    public void parseNewest_wrappedObjectForm() throws Exception {
        String json = "{\"generatedAt\":\"2026-01-01\",\"items\":"
                + "[{\"fullName\":\"a/b\",\"stars\":3}]}";
        StoreApi.StoreData d = parseNewest(json, true);
        assertNotNull(d);
        assertTrue(d.offline);
        assertEquals("2026-01-01", d.generatedAt);
        assertEquals(1, d.items.size());
    }

    @Test
    public void parseNewest_emptyOrInvalidReturnsNull() throws Exception {
        assertNull(parseNewest("[]", false));
        assertNull(parseNewest("{\"items\":[]}", false));
        assertNull(parseNewest("{\"items\":null}", false));
        assertNull(parseNewest("garbage", false));
        assertNull(parseNewest("", false));
        assertNull(parseNewest("   ", false));
    }

    @Test
    public void parseFile_delegatesByNewestFlag() throws Exception {
        StoreApi.StoreData backend = (StoreApi.StoreData) invokePrivate("parseFile",
                new Class<?>[]{String.class, boolean.class, boolean.class},
                ONE_ITEM_BACKEND, false, false);
        assertNotNull(backend);
        assertEquals(1, backend.items.size());
        StoreApi.StoreData newest = (StoreApi.StoreData) invokePrivate("parseFile",
                new Class<?>[]{String.class, boolean.class, boolean.class},
                "[{\"fullName\":\"a/b\"}]", false, true);
        assertNotNull(newest);
        assertEquals(1, newest.items.size());
        // 同一份 backend JSON 按 newest 解析：无顶层数组 → object 分支取 items，同样能解析。
        StoreApi.StoreData cross = (StoreApi.StoreData) invokePrivate("parseFile",
                new Class<?>[]{String.class, boolean.class, boolean.class},
                ONE_ITEM_BACKEND, false, true);
        assertNotNull(cross);
    }

    @Test
    public void readSavedAt_missingGarbageAndValid() throws Exception {
        File dir = new File(mContext.getFilesDir(), "storeapi-test");
        dir.mkdirs();
        File missing = new File(dir, "missing.meta");
        assertEquals(0L, invokePrivate("readSavedAt",
                new Class<?>[]{File.class}, missing));
        File garbage = new File(dir, "garbage.meta");
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                garbage, "not-a-number");
        assertEquals(0L, invokePrivate("readSavedAt",
                new Class<?>[]{File.class}, garbage));
        File valid = new File(dir, "valid.meta");
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                valid, "  1700000000000\n");
        assertEquals(1700000000000L, invokePrivate("readSavedAt",
                new Class<?>[]{File.class}, valid));
    }

    @Test
    public void readWriteFile_roundtrip() throws Exception {
        File dir = new File(mContext.getFilesDir(), "storeapi-test");
        dir.mkdirs();
        File f = new File(dir, "roundtrip.txt");
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                f, "héllo store ✓");
        String back = (String) invokePrivate("readFile", new Class<?>[]{File.class}, f);
        assertEquals("héllo store ✓", back);
    }

    @Test
    public void enrich_nullAndEmptyReturnEmptyWithoutNetwork() {
        List<StoreRepo> fromNull = awaitSingle(StoreApi.enrich(null, true));
        assertTrue(fromNull.isEmpty());
        List<StoreRepo> fromEmpty = awaitSingle(StoreApi.enrich(Collections.emptyList(), false));
        assertTrue(fromEmpty.isEmpty());
    }

    @Test
    public void loadBackendMethods_returnLazySinglesWithoutNetwork() {
        assertNotNull(StoreApi.loadBackend(mContext, "feed", "all"));
        assertNotNull(StoreApi.loadNewest(mContext));
        assertNotNull(StoreApi.loadListNewest());
        assertNotNull(StoreApi.loadListRecent());
    }

    @Test
    public void loadBackend_freshCacheReturnsCachedDataWithoutNetwork() throws Exception {
        File dir = new File(mContext.getFilesDir(), "store-cache");
        dir.mkdirs();
        File cacheFile = new File(dir, "feed_all.json");
        File metaFile = new File(dir, "feed_all.meta");
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                cacheFile, ONE_ITEM_BACKEND);
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                metaFile, String.valueOf(System.currentTimeMillis()));
        StoreApi.StoreData data = awaitSingle(StoreApi.loadBackend(mContext, "feed", "all"));
        assertNotNull(data);
        assertFalse(data.offline);
        assertEquals(1, data.items.size());
        assertEquals("octo/cat", data.items.get(0).fullName);
    }

    @Test
    public void loadNewest_freshCacheReturnsCachedDataWithoutNetwork() throws Exception {
        File dir = new File(mContext.getFilesDir(), "store-cache");
        dir.mkdirs();
        File cacheFile = new File(dir, "newest.json");
        File metaFile = new File(dir, "newest.meta");
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                cacheFile, "[{\"fullName\":\"n/w\",\"stars\":8}]");
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                metaFile, String.valueOf(System.currentTimeMillis()));
        StoreApi.StoreData data = awaitSingle(StoreApi.loadNewest(mContext));
        assertNotNull(data);
        assertFalse(data.offline);
        assertEquals(1, data.items.size());
        assertEquals("n/w", data.items.get(0).fullName);
    }

    // ---------- mocked-network helpers ----------

    private static Field fetchClientField() throws Exception {
        Field f = StoreApi.class.getDeclaredField("sFetchClient");
        f.setAccessible(true);
        return f;
    }

    private static okhttp3.Response okHttpResponse(String body) throws Exception {
        okhttp3.Response response = mock(okhttp3.Response.class);
        when(response.isSuccessful()).thenReturn(true);
        when(response.code()).thenReturn(200);
        ResponseBody rb = mock(ResponseBody.class);
        when(rb.string()).thenReturn(body);
        when(response.body()).thenReturn(rb);
        return response;
    }

    private static okhttp3.Response okHttpErrorResponse(int code) {
        okhttp3.Response response = mock(okhttp3.Response.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.code()).thenReturn(code);
        when(response.body()).thenReturn(null);
        return response;
    }

    private static okhttp3.Response okHttpSuccessNullBody() {
        okhttp3.Response response = mock(okhttp3.Response.class);
        when(response.isSuccessful()).thenReturn(true);
        when(response.code()).thenReturn(200);
        when(response.body()).thenReturn(null);
        return response;
    }

    private static Call callReturning(okhttp3.Response response) throws Exception {
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        return call;
    }

    private static Call callThrowing(java.io.IOException e) throws Exception {
        Call call = mock(Call.class);
        when(call.execute()).thenThrow(e);
        return call;
    }

    /** OkHttpClient answering newCall from a per-call queue of Calls. */
    private static OkHttpClient queueClient(Call... calls) {
        OkHttpClient client = mock(OkHttpClient.class);
        java.util.concurrent.atomic.AtomicInteger idx =
                new java.util.concurrent.atomic.AtomicInteger();
        when(client.newCall(any())).thenAnswer(
                inv -> calls[Math.min(idx.getAndIncrement(), calls.length - 1)]);
        return client;
    }

    private static Throwable awaitSingleError(Single<?> single) {
        io.reactivex.observers.TestObserver<?> observer = single.test();
        for (int i = 0; i < 500 && !observer.isTerminated(); i++) {
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
            org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        observer.assertError(Throwable.class);
        return observer.errors().get(0);
    }

    private static Throwable invokePrivateExpectingThrow(String name, Class<?>[] types,
            Object... args) throws Exception {
        try {
            invokePrivate(name, types, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause();
        }
        fail("expected " + name + " to throw");
        return null;
    }

    private void writeCacheFile(String key, String json, long savedAtMs) throws Exception {
        File dir = new File(mContext.getFilesDir(), "store-cache");
        dir.mkdirs();
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                new File(dir, key + ".json"), json);
        invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                new File(dir, key + ".meta"), String.valueOf(savedAtMs));
    }

    private void deleteCacheFiles(String key) {
        File dir = new File(mContext.getFilesDir(), "store-cache");
        new File(dir, key + ".json").delete();
        new File(dir, key + ".meta").delete();
    }

    @Test
    public void implicitCtor_instantiatesUtilityClass() {
        assertNotNull(new StoreApi());
    }

    @Test
    public void fetchClient_doubleCheckedLocking_secondCallerSeesCachedClient()
            throws Exception {
        // L81/L82：第二个调用方在外层读到 null、进 synchronized 后重读到已缓存的 client。
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        OkHttpClient cached = mock(OkHttpClient.class);
        try {
            f.set(null, null);
            CountDownLatch holderReady = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Object> result = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread holder = new Thread(() -> {
                synchronized (StoreApi.class) {
                    holderReady.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            return;
                        }
                        f.set(null, cached);
                    } catch (Exception e) {
                        error.set(e);
                    }
                }
            });
            Thread worker = new Thread(() -> {
                try {
                    result.set(invokePrivate("fetchClient", new Class<?>[0]));
                } catch (Throwable t) {
                    error.set(t);
                }
            });
            holder.start();
            assertTrue(holderReady.await(10, TimeUnit.SECONDS));
            worker.start();
            // 等 worker 在 StoreApi.class 监视器上阻塞：此时它已读到外层 null，
            // 接下来进 synchronized 后重读即看到 holder 刚写入的 client。
            long deadline = System.currentTimeMillis() + 10000;
            while (worker.getState() != Thread.State.BLOCKED
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(Thread.State.BLOCKED, worker.getState());
            release.countDown();
            worker.join(10000);
            holder.join(10000);
            assertNull(error.get());
            assertFalse(worker.isAlive());
            assertTrue(result.get() == cached);
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void readFile_shortRead_returnsPartialContent() throws Exception {
        // L308：文件长度谎报（length() > 实际字节数）时 read() 返回 -1 → break。
        File dir = new File(mContext.getFilesDir(), "storeapi-test");
        dir.mkdirs();
        File empty = new File(dir, "empty.bin");
        if (!empty.exists()) {
            assertTrue(empty.createNewFile());
        }
        File lying = new File(empty.getPath()) {
            @Override
            public long length() {
                return 8;
            }
        };
        String out = (String) invokePrivate("readFile", new Class<?>[]{File.class}, lying);
        assertEquals("", out);
    }

    @Test
    public void fetchClient_buildFailureFallsBackToFreshBuild() throws Exception {
        Field f = fetchClientField();
        f.set(null, null);
        OkHttpClient.Builder builder = mock(OkHttpClient.Builder.class);
        when(builder.connectTimeout(eq(15L), eq(TimeUnit.SECONDS))).thenReturn(builder);
        when(builder.readTimeout(eq(20L), eq(TimeUnit.SECONDS))).thenReturn(builder);
        OkHttpClient fresh = mock(OkHttpClient.class);
        when(builder.build()).thenThrow(new RuntimeException("no init"))
                .thenReturn(fresh);
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(ServiceFactory::getHttpClientBuilder).thenReturn(builder);
            OkHttpClient out = (OkHttpClient) invokePrivate("fetchClient", new Class<?>[0]);
            assertTrue(out == fresh);
            // catch 回落不写 sFetchClient：下次调用重新初始化。
            assertNull(f.get(null));
        } finally {
            f.set(null, null);
        }
    }

    @Test
    public void fetchUrl_successReturnsBodyString() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            String out = (String) invokePrivate("fetchUrl", new Class<?>[]{String.class},
                    "https://3299074036.github.io/OctoDroid/store/feed/all.json");
            assertEquals(ONE_ITEM_BACKEND, out);
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void fetchUrl_nonSuccessfulThrowsHttpCode() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(callReturning(okHttpErrorResponse(404))));
            Throwable err = invokePrivateExpectingThrow("fetchUrl",
                    new Class<?>[]{String.class},
                    "https://3299074036.github.io/OctoDroid/store/feed/all.json");
            assertTrue(err instanceof java.io.IOException);
            assertEquals("http 404", err.getMessage());
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void fetchUrl_nullBodyThrowsHttpCode() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(callReturning(okHttpSuccessNullBody())));
            Throwable err = invokePrivateExpectingThrow("fetchUrl",
                    new Class<?>[]{String.class},
                    "https://3299074036.github.io/OctoDroid/store/feed/all.json");
            assertTrue(err instanceof java.io.IOException);
            assertEquals("http 200", err.getMessage());
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void loadBackend_chartKindUsesCategoriesPathAndCaches() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        OkHttpClient client = queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND)));
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        try {
            f.set(null, client);
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "trending", "all"));
            assertNotNull(data);
            assertEquals(1, data.items.size());
            verify(client).newCall(captor.capture());
            assertTrue(captor.getValue().url().toString()
                    .endsWith("categories/trending/all.json"));
            assertTrue(new File(new File(mContext.getFilesDir(), "store-cache"),
                    "trending_all.json").exists());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("trending_all");
        }
    }

    @Test
    public void loadBackend_staleMetaFetchesFromNetwork() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            writeCacheFile("feed_t-stale", ONE_ITEM_BACKEND,
                    System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000);
            f.set(null, queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-stale"));
            assertNotNull(data);
            assertFalse(data.offline);
            assertEquals(1, data.items.size());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-stale");
        }
    }

    @Test
    public void loadBackend_noMetaFetchesFromNetwork() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-nometa"));
            assertNotNull(data);
            assertFalse(data.offline);
            assertEquals(1, data.items.size());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-nometa");
        }
    }

    @Test
    public void loadBackend_freshMetaButCacheMissingFetchesFromNetwork() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            File dir = new File(mContext.getFilesDir(), "store-cache");
            dir.mkdirs();
            invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                    new File(dir, "feed_t-nocache.meta"),
                    String.valueOf(System.currentTimeMillis()));
            f.set(null, queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-nocache"));
            assertNotNull(data);
            assertFalse(data.offline);
            assertEquals(1, data.items.size());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-nocache");
        }
    }

    @Test
    public void loadBackend_freshCacheBadJsonFallsThroughToNetwork() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            writeCacheFile("feed_t-badcache", "garbage{{{", System.currentTimeMillis());
            f.set(null, queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-badcache"));
            assertNotNull(data);
            assertFalse(data.offline);
            assertEquals(1, data.items.size());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-badcache");
        }
    }

    @Test
    public void loadBackend_cachePathIsDirectoryErrors() throws Exception {
        // 缓存路径被目录占用：新鲜缓存读失败（走 95 的 catch）→ 拉取成功
        // 但写缓存失败 → 过期缓存读也失败 → 拉取到的数据被丢弃、抛错。
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        File dir = new File(mContext.getFilesDir(), "store-cache");
        dir.mkdirs();
        File cacheAsDir = new File(dir, "feed_t-dircache.json");
        cacheAsDir.mkdirs();
        try {
            invokePrivate("writeFile", new Class<?>[]{File.class, String.class},
                    new File(dir, "feed_t-dircache.meta"),
                    String.valueOf(System.currentTimeMillis()));
            f.set(null, queueClient(callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            Throwable err = awaitSingleError(StoreApi.loadBackend(mContext, "feed", "t-dircache"));
            assertTrue(err instanceof java.io.FileNotFoundException);
        } finally {
            f.set(null, saved);
            cacheAsDir.delete();
            deleteCacheFiles("feed_t-dircache");
        }
    }

    @Test
    public void loadBackend_badPayloadFallsBackToStaleCache() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            writeCacheFile("feed_t-badpay", ONE_ITEM_BACKEND,
                    System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000);
            f.set(null, queueClient(callReturning(okHttpResponse("not json"))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-badpay"));
            assertNotNull(data);
            assertTrue(data.offline);
            assertFalse(data.mirror);
            assertEquals(1, data.items.size());
            assertEquals("octo/cat", data.items.get(0).fullName);
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-badpay");
        }
    }

    @Test
    public void loadBackend_fetchThrowsFallsBackToStaleCache() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            writeCacheFile("feed_t-throw", ONE_ITEM_BACKEND,
                    System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000);
            f.set(null, queueClient(callThrowing(new java.io.IOException("boom"))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-throw"));
            assertNotNull(data);
            assertTrue(data.offline);
            assertFalse(data.mirror);
            assertEquals(1, data.items.size());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-throw");
        }
    }

    @Test
    public void loadBackend_noStaleCacheFallsBackToMirror() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(
                    callThrowing(new java.io.IOException("backend down")),
                    callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-mirror"));
            assertNotNull(data);
            assertTrue(data.offline);
            assertTrue(data.mirror);
            assertEquals(1, data.items.size());
            assertFalse(new File(new File(mContext.getFilesDir(), "store-cache"),
                    "feed_t-mirror.json").exists());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-mirror");
        }
    }

    @Test
    public void loadBackend_badPayloadAndBadStaleFallsBackToMirror() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            writeCacheFile("feed_t-badstale", "garbage",
                    System.currentTimeMillis() - 4L * 24 * 60 * 60 * 1000);
            f.set(null, queueClient(
                    callReturning(okHttpResponse("also bad")),
                    callReturning(okHttpResponse(ONE_ITEM_BACKEND))));
            StoreApi.StoreData data =
                    awaitSingle(StoreApi.loadBackend(mContext, "feed", "t-badstale"));
            assertNotNull(data);
            assertTrue(data.offline);
            assertTrue(data.mirror);
            assertEquals(1, data.items.size());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-badstale");
        }
    }

    @Test
    public void loadBackend_allFailedThrowsOriginalError() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(
                    callThrowing(new java.io.IOException("backend down")),
                    callThrowing(new java.io.IOException("mirror down"))));
            Throwable err = awaitSingleError(StoreApi.loadBackend(mContext, "feed", "t-allfail"));
            assertTrue(err instanceof java.io.IOException);
            assertEquals("backend down", err.getMessage());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-allfail");
        }
    }

    @Test
    public void loadBackend_mirrorBadPayloadRethrowsOriginalError() throws Exception {
        Field f = fetchClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, queueClient(
                    callThrowing(new java.io.IOException("backend down")),
                    callReturning(okHttpResponse("not json"))));
            Throwable err = awaitSingleError(StoreApi.loadBackend(mContext, "feed", "t-mirrorbad"));
            assertTrue(err instanceof java.io.IOException);
            assertEquals("backend down", err.getMessage());
        } finally {
            f.set(null, saved);
            deleteCacheFiles("feed_t-mirrorbad");
        }
    }

    // ---------- GitHub realtime API (mocked services) ----------

    @SuppressWarnings("unchecked")
    private static Repository mockRepo(String ownerLogin, String name) {
        Repository repo = mock(Repository.class);
        User user = mock(User.class);
        when(user.login()).thenReturn(ownerLogin);
        when(repo.owner()).thenReturn(user);
        when(repo.name()).thenReturn(name);
        when(repo.fullName()).thenReturn(ownerLogin + "/" + name);
        return repo;
    }

    private static Release mockRelease(String tag, Date publishedAt,
            java.util.List<ReleaseAsset> assets, Boolean draft) {
        Release r = mock(Release.class);
        when(r.draft()).thenReturn(draft);
        when(r.tagName()).thenReturn(tag);
        when(r.publishedAt()).thenReturn(publishedAt);
        when(r.assets()).thenReturn(assets);
        return r;
    }

    private static ReleaseAsset mockAsset(String name, Integer downloads) {
        ReleaseAsset a = mock(ReleaseAsset.class);
        when(a.name()).thenReturn(name);
        when(a.downloadCount()).thenReturn(downloads);
        return a;
    }

    private static <T> Response<T> retrofitErrorResponse(int code) {
        return Response.error(code,
                ResponseBody.create("", MediaType.parse("application/json")));
    }

    @Test
    public void parseBackend_nonObjectItemSkipped() throws Exception {
        String json = "{\"generatedAt\":\"x\",\"items\":[42,{\"fullName\":\"a/b\",\"stars\":3}]}";
        StoreApi.StoreData d = parseBackend(json, false);
        assertNotNull(d);
        assertEquals(1, d.items.size());
        assertEquals("a/b", d.items.get(0).fullName);
    }

    @Test
    public void parseNewest_nonObjectItemSkipped() throws Exception {
        StoreApi.StoreData d = parseNewest("[42,{\"fullName\":\"a/b\",\"stars\":3}]", false);
        assertNotNull(d);
        assertEquals(1, d.items.size());
        assertEquals("a/b", d.items.get(0).fullName);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchRepos_successReturnsRepositories() throws Exception {
        SearchService service = mock(SearchService.class);
        SearchPage<Repository> page = mock(SearchPage.class);
        Repository repo = mockRepo("o", "r");
        when(page.items()).thenReturn(Collections.singletonList(repo));
        when(service.searchRepositories(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        List<Repository> out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.searchRepos("q", "stars", "desc"));
        }
        assertEquals(1, out.size());
        assertEquals("o/r", out.get(0).fullName());
    }

    // searchRepos 的 `item instanceof Repository` 假臂不可达：SearchPageAdapter
    // 的 items() 在映射时先对元素做 Repository 类型转换，非 Repository 元素
    // 在到达该判断前即抛 ClassCastException（实测：曾断言跳过该元素的测试
    // 以 ClassCastException 失败）。故不设测试。

    @Test
    @SuppressWarnings("unchecked")
    public void searchRepos_nullItemsReturnsEmpty() throws Exception {
        SearchService service = mock(SearchService.class);
        SearchPage<Repository> page = mock(SearchPage.class);
        when(page.items()).thenReturn(null);
        when(service.searchRepositories(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        List<Repository> out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.searchRepos("q", "stars", "desc"));
        }
        assertTrue(out.isEmpty());
    }

    @Test
    public void searchRepos_errorResponseThrowsApiException() throws Exception {
        SearchService service = mock(SearchService.class);
        when(service.searchRepositories(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(retrofitErrorResponse(404)));
        Throwable err;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), eq(false)))
                    .thenReturn(service);
            err = awaitSingleError(StoreApi.searchRepos("q", "stars", "desc"));
        }
        assertTrue(err instanceof ApiRequestException);
        assertEquals(404, ((ApiRequestException) err).getStatus());
    }

    @Test
    public void searchRepos_nullBodyReturnsEmptyList() throws Exception {
        // T-130：200 空 body 时 SearchPageAdapter 对 null 页返回空列表；
        // 真实 Retrofit 空体会在转换层先失败，生产上基本不可达。
        SearchService service = mock(SearchService.class);
        when(service.searchRepositories(anyString(), anyString(), anyString(), eq(1L)))
                .thenReturn(Single.just(Response.success(null)));
        List<Repository> out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(SearchService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.searchRepos("q", "stars", "desc"));
        }
        assertTrue(out.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_noReleasesReturnsEmptyRow() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        when(page.items()).thenReturn(Collections.emptyList());
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("o/r", out.fullName());
        assertEquals("", out.version());
        assertEquals(0L, out.publishedAt);
        assertFalse(out.hasAssets());
        assertEquals(0, out.assetCount);
        assertEquals(0L, out.downloadCount);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_firstNonDraftReleaseWins() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        Release draft = mockRelease("v9-draft", new Date(1000L), null, true);
        Release live = mockRelease("v2", new Date(2000L), null, false);
        Release older = mockRelease("v1", new Date(500L), null, false);
        when(page.items()).thenReturn(Arrays.asList(draft, live, older));
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("v2", out.version());
        assertEquals(2000L, out.publishedAt);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_nullReleaseSkipped() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        Release live = mockRelease("v3", null, null, false);
        when(page.items()).thenReturn(Arrays.asList(null, live));
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("v3", out.version());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_allDraftsReturnsEmptyRow() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        Release draft1 = mockRelease("v9", null, null, true);
        Release draft2 = mockRelease("v8", null, null, true);
        when(page.items()).thenReturn(Arrays.asList(draft1, draft2));
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("", out.version());
        assertFalse(out.hasAssets());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_nullBodyReturnsEmptyRow() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(null)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("", out.version());
        assertFalse(out.hasAssets());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_nullItemsReturnsEmptyRow() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        when(page.items()).thenReturn(null);
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("", out.version());
        assertFalse(out.hasAssets());
    }

    @Test
    public void latestStoreRepo_errorResponseReturnsEmptyRow() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(retrofitErrorResponse(404)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("", out.version());
        assertFalse(out.hasAssets());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_releaseWithAssetsCountsInstallableOnly() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        ReleaseAsset apk = mockAsset("app.apk", 5);
        ReleaseAsset txt = mockAsset("readme.txt", 99);
        ReleaseAsset apkNoCount = mockAsset("app2.apk", null);
        ReleaseAsset nullName = mockAsset(null, 7);
        Release release = mockRelease("v2", new Date(1700000000000L),
                Arrays.asList(apk, txt, apkNoCount, nullName), false);
        when(page.items()).thenReturn(Collections.singletonList(release));
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("v2", out.version());
        assertEquals(1700000000000L, out.publishedAt);
        assertTrue(out.hasAssets());
        assertEquals(2, out.assetCount);
        assertEquals(5L, out.downloadCount);
        assertTrue(out.platforms.contains(StoreClassifier.Platform.ANDROID));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void latestStoreRepo_nullTagDateAndAssetsDefaults() throws Exception {
        RepositoryReleaseService service = mock(RepositoryReleaseService.class);
        Page<Release> page = mock(Page.class);
        Release release = mockRelease(null, null, null, false);
        when(page.items()).thenReturn(Collections.singletonList(release));
        when(service.getReleases(eq("o"), eq("r"), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        StoreRepo out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.latestStoreRepo(mockRepo("o", "r")));
        }
        assertEquals("", out.version());
        assertEquals(0L, out.publishedAt);
        assertFalse(out.hasAssets());
        assertEquals(0, out.assetCount);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void buildStoreRepo_nullOwnerAndNullCountsDefaults() throws Exception {
        Repository repo = mock(Repository.class);
        when(repo.owner()).thenReturn(null);
        when(repo.name()).thenReturn("r");
        when(repo.fullName()).thenReturn("?/r");
        // Mockito 默认把 Integer 方法返回 0 而非 null，须显式 stub null；
        // topics 默认 null（未 stub）。
        when(repo.stargazersCount()).thenReturn(null);
        when(repo.forksCount()).thenReturn(null);
        StoreRepo out = (StoreRepo) invokePrivate("buildStoreRepo",
                new Class<?>[]{Repository.class, Release.class}, repo, null);
        assertEquals("?/r", out.fullName());
        assertEquals("", out.owner());
        assertEquals("r", out.name());
        assertEquals(0, out.stars());
        assertEquals(0, out.forks);
        assertEquals("", out.version());
        assertFalse(out.hasAssets());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void buildStoreRepo_fieldsPassThrough() throws Exception {
        Repository repo = mockRepo("o", "r");
        when(repo.description()).thenReturn("d");
        when(repo.language()).thenReturn("Java");
        when(repo.stargazersCount()).thenReturn(42);
        when(repo.forksCount()).thenReturn(7);
        when(repo.topics()).thenReturn(Arrays.asList("android", "game"));
        StoreRepo out = (StoreRepo) invokePrivate("buildStoreRepo",
                new Class<?>[]{Repository.class, Release.class}, repo, null);
        assertEquals("o/r", out.fullName());
        assertEquals("o", out.owner());
        assertEquals("r", out.name());
        assertEquals("d", out.description);
        assertEquals("Java", out.language);
        assertEquals(42, out.stars());
        assertEquals(7, out.forks);
        assertTrue(out.topics.contains("android"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void enrich_dropEmptyKeepsOnlyReposWithAssets() throws Exception {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        try {
            RepositoryReleaseService service = mock(RepositoryReleaseService.class);
            when(service.getReleases(anyString(), anyString(), eq(1L)))
                    .thenAnswer(inv -> {
                        String name = inv.getArgument(1);
                        if ("good".equals(name)) {
                            Page<Release> page = mock(Page.class);
                            Release release = mockRelease("v1", null,
                                    Collections.singletonList(mockAsset("a.apk", 3)), false);
                            when(page.items()).thenReturn(Collections.singletonList(release));
                            return Single.just(Response.success(page));
                        }
                        if ("empty".equals(name)) {
                            Page<Release> page = mock(Page.class);
                            when(page.items()).thenReturn(Collections.emptyList());
                            return Single.just(Response.success(page));
                        }
                        return Single.error(new java.io.IOException("404"));
                    });
            List<StoreRepo> out;
            try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
                mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                        .thenReturn(service);
                out = awaitSingle(StoreApi.enrich(Arrays.asList(
                        mockRepo("o", "good"), mockRepo("o", "empty"), mockRepo("o", "boom")), true));
            }
            assertEquals(1, out.size());
            assertEquals("o/good", out.get(0).fullName());
            assertTrue(out.get(0).hasAssets());
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void enrich_keepEmptyPreservesOrderAndSentinels() throws Exception {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        try {
            RepositoryReleaseService service = mock(RepositoryReleaseService.class);
            when(service.getReleases(anyString(), anyString(), eq(1L)))
                    .thenAnswer(inv -> {
                        String name = inv.getArgument(1);
                        if ("good".equals(name)) {
                            Page<Release> page = mock(Page.class);
                            Release release = mockRelease("v1", null,
                                    Collections.singletonList(mockAsset("a.apk", 3)), false);
                            when(page.items()).thenReturn(Collections.singletonList(release));
                            return Single.just(Response.success(page));
                        }
                        if ("empty".equals(name)) {
                            Page<Release> page = mock(Page.class);
                            when(page.items()).thenReturn(Collections.emptyList());
                            return Single.just(Response.success(page));
                        }
                        return Single.error(new java.io.IOException("404"));
                    });
            List<StoreRepo> out;
            try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
                mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                        .thenReturn(service);
                out = awaitSingle(StoreApi.enrich(Arrays.asList(
                        mockRepo("o", "good"), mockRepo("o", "empty"), mockRepo("o", "boom")), false));
            }
            assertEquals(3, out.size());
            assertEquals("o/good", out.get(0).fullName());
            assertEquals("o/empty", out.get(1).fullName());
            assertEquals("o/boom", out.get(2).fullName());
            assertTrue(out.get(0).hasAssets());
            assertFalse(out.get(1).hasAssets());
            assertFalse(out.get(2).hasAssets());
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void enrich_nullOwnerRepoDropsRepoInsteadOfFailingChain() throws Exception {
        // T-111/T-131：null owner 的 NPE 在订阅时变成 onError，由 enrich 的
        // onErrorReturnItem 哨兵接住、只丢该仓；哨兵行无安装包，dropEmpty 时被过滤。
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        try {
            Repository repo = mock(Repository.class);
            when(repo.owner()).thenReturn(null);
            when(repo.name()).thenReturn("r");
            List<StoreRepo> out = awaitSingle(StoreApi.enrich(
                    Collections.singletonList(repo), true));
            assertTrue(out.isEmpty());
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void fetchRepo_successAndError() throws Exception {
        RepositoryService service = mock(RepositoryService.class);
        Repository repo = mockRepo("o", "r");
        when(service.getRepository(eq("o"), eq("r")))
                .thenReturn(Single.just(Response.success(repo)));
        Repository out;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryService.class), eq(false)))
                    .thenReturn(service);
            out = awaitSingle(StoreApi.fetchRepo("o", "r"));
        }
        assertEquals("o/r", out.fullName());

        when(service.getRepository(eq("o"), eq("r")))
                .thenReturn(Single.just(retrofitErrorResponse(404)));
        Throwable err;
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(RepositoryService.class), eq(false)))
                    .thenReturn(service);
            err = awaitSingleError(StoreApi.fetchRepo("o", "r"));
        }
        assertTrue(err instanceof ApiRequestException);
        assertEquals(404, ((ApiRequestException) err).getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void loadListNewest_searchesCreatedQueryAndEnriches() throws Exception {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        try {
            SearchService searchService = mock(SearchService.class);
            SearchPage<Repository> searchPage = mock(SearchPage.class);
            Repository repo = mockRepo("o", "r");
            when(searchPage.items())
                    .thenReturn(Collections.singletonList(repo));
            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            when(searchService.searchRepositories(queryCaptor.capture(),
                    eq("stars"), eq("desc"), eq(1L)))
                    .thenReturn(Single.just(Response.success(searchPage)));
            RepositoryReleaseService releaseService = mock(RepositoryReleaseService.class);
            Page<Release> releasePage = mock(Page.class);
            Release release = mockRelease("v5", null,
                    Collections.singletonList(mockAsset("a.apk", 1)), false);
            when(releasePage.items()).thenReturn(Collections.singletonList(release));
            when(releaseService.getReleases(eq("o"), eq("r"), eq(1L)))
                    .thenReturn(Single.just(Response.success(releasePage)));
            List<StoreRepo> out;
            try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
                mocked.when(() -> ServiceFactory.get(eq(SearchService.class), eq(false)))
                        .thenReturn(searchService);
                mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                        .thenReturn(releaseService);
                out = awaitSingle(StoreApi.loadListNewest());
            }
            assertTrue(queryCaptor.getValue().startsWith("created:>"));
            assertTrue(queryCaptor.getValue().contains("stars:>50"));
            assertEquals(1, out.size());
            assertEquals("v5", out.get(0).version());
            assertTrue(out.get(0).hasAssets());
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void loadListRecent_searchesStarsQueryAndEnriches() throws Exception {
        RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
        try {
            SearchService searchService = mock(SearchService.class);
            SearchPage<Repository> searchPage = mock(SearchPage.class);
            Repository repo = mockRepo("o", "r");
            when(searchPage.items())
                    .thenReturn(Collections.singletonList(repo));
            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            when(searchService.searchRepositories(queryCaptor.capture(),
                    eq("updated"), eq("desc"), eq(1L)))
                    .thenReturn(Single.just(Response.success(searchPage)));
            RepositoryReleaseService releaseService = mock(RepositoryReleaseService.class);
            Page<Release> releasePage = mock(Page.class);
            Release release = mockRelease("v6", null,
                    Collections.singletonList(mockAsset("a.apk", 2)), false);
            when(releasePage.items()).thenReturn(Collections.singletonList(release));
            when(releaseService.getReleases(eq("o"), eq("r"), eq(1L)))
                    .thenReturn(Single.just(Response.success(releasePage)));
            List<StoreRepo> out;
            try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
                mocked.when(() -> ServiceFactory.get(eq(SearchService.class), eq(false)))
                        .thenReturn(searchService);
                mocked.when(() -> ServiceFactory.get(eq(RepositoryReleaseService.class), eq(false)))
                        .thenReturn(releaseService);
                out = awaitSingle(StoreApi.loadListRecent());
            }
            assertEquals("stars:>100", queryCaptor.getValue());
            assertEquals(1, out.size());
            assertEquals("v6", out.get(0).version());
            assertTrue(out.get(0).hasAssets());
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void fetchRepo_searchRepos_latestStoreRepo_returnLazySingles() {
        // 只建不订阅：ServiceFactory.get 在订阅前不触发网络；订阅会走真实网络，故不订阅。
        assertNotNull(StoreApi.fetchRepo("o", "r"));
        assertNotNull(StoreApi.searchRepos("q", "stars", "desc"));
        com.meisolsson.githubsdk.model.Repository repo =
                com.meisolsson.githubsdk.model.Repository.builder()
                        .fullName("o/r")
                        .owner(com.meisolsson.githubsdk.model.User.builder()
                                .login("o").build())
                        .build();
        assertNotNull(StoreApi.latestStoreRepo(repo));
        assertNotNull(StoreApi.enrich(Arrays.asList(repo), true));
    }
}
