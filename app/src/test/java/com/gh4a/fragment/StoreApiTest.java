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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
