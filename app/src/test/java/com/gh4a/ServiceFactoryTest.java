package com.gh4a;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.model.TrendService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ServiceFactoryTest {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // ---- reflection helpers ----

    private static Object callStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ServiceFactory.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call " + name, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + name, e);
        }
    }

    private static Interceptor interceptor(String fieldName) {
        try {
            Field f = ServiceFactory.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return (Interceptor) f.get(null);
        } catch (Exception e) {
            throw new AssertionError("field " + fieldName, e);
        }
    }

    /** Minimal fake Interceptor.Chain: captures the request, returns a canned response. */
    private static class FakeChain implements Interceptor.Chain {
        final Request request;
        final Response response;
        Request proceededWith;

        FakeChain(Request request, Response response) {
            this.request = request;
            this.response = response;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response proceed(Request request) {
            proceededWith = request;
            return response;
        }

        @Override
        public okhttp3.Connection connection() {
            return null;
        }

        @Override
        public okhttp3.Call call() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static Response cannedResponse(Request request, int code, String body,
            String contentType) {
        Response.Builder builder = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK");
        if (body != null) {
            builder.body(contentType == null
                    ? ResponseBody.create(null, body)
                    : ResponseBody.create(MediaType.parse(contentType), body));
        }
        return builder.build();
    }

    private static Request request(String url) {
        return new Request.Builder().url(url).build();
    }

    // ---- isEventsRequest / isUnknownEventType ----

    @Test
    public void isEventsRequest_matchesEventsSegments() {
        assertTrue((Boolean) callStatic("isEventsRequest",
                new Class<?>[]{Request.class}, request("https://api.github.com/users/o/events")));
        assertTrue((Boolean) callStatic("isEventsRequest",
                new Class<?>[]{Request.class},
                request("https://api.github.com/users/o/received_events")));
        assertTrue((Boolean) callStatic("isEventsRequest",
                new Class<?>[]{Request.class},
                request("https://api.github.com/repos/o/r/events?page=2")));
        assertFalse((Boolean) callStatic("isEventsRequest",
                new Class<?>[]{Request.class}, request("https://api.github.com/users/o")));
        assertFalse((Boolean) callStatic("isEventsRequest",
                new Class<?>[]{Request.class},
                request("https://api.github.com/repos/o/r")));
    }

    @Test
    public void isUnknownEventType_knownAndUnknown() {
        assertFalse((Boolean) callStatic("isUnknownEventType",
                new Class<?>[]{String.class}, "PushEvent"));
        assertFalse((Boolean) callStatic("isUnknownEventType",
                new Class<?>[]{String.class}, "ForkEvent"));
        assertTrue((Boolean) callStatic("isUnknownEventType",
                new Class<?>[]{String.class}, "BrandNewEventXYZ"));
        assertFalse((Boolean) callStatic("isUnknownEventType",
                new Class<?>[]{String.class}, (String) null));
        assertTrue((Boolean) callStatic("isUnknownEventType",
                new Class<?>[]{String.class}, ""));
    }

    // ---- makeKey / sha256Hex ----

    @Test
    public void makeKey_deterministicAndTokenFree() {
        String key1 = (String) callStatic("makeKey",
                new Class<?>[]{Class.class, boolean.class, String.class, String.class, Integer.class},
                TrendService.class, false, null, "secret-token-123", null);
        String key2 = (String) callStatic("makeKey",
                new Class<?>[]{Class.class, boolean.class, String.class, String.class, Integer.class},
                TrendService.class, false, null, "secret-token-123", null);
        assertEquals(key1, key2);
        assertFalse("cache key must not contain the raw token", key1.contains("secret-token-123"));
    }

    @Test
    public void makeKey_differsOnTokenBypassAcceptAndPageSize() {
        Class<?>[] types = {Class.class, boolean.class, String.class, String.class, Integer.class};
        String base = (String) callStatic("makeKey", types,
                TrendService.class, false, null, "tok", null);
        assertNotEquals(base, callStatic("makeKey", types,
                TrendService.class, false, null, "other", null));
        assertNotEquals(base, callStatic("makeKey", types,
                TrendService.class, true, null, "tok", null));
        assertNotEquals(base, callStatic("makeKey", types,
                TrendService.class, false, "application/json", "tok", null));
        assertNotEquals(base, callStatic("makeKey", types,
                TrendService.class, false, null, "tok", 100));
        assertNotEquals(base, callStatic("makeKey", types,
                TrendService.class, false, null, null, null));
    }

    @Test
    public void sha256Hex_knownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                callStatic("sha256Hex", new Class<?>[]{String.class}, "abc"));
        assertEquals(64, ((String) callStatic("sha256Hex",
                new Class<?>[]{String.class}, "")).length());
    }

    // ---- UNKNOWN_EVENT_TYPE_INTERCEPTOR ----

    @Test
    public void unknownEventInterceptor_nonEventsRequest_passesThrough() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = cannedResponse(req, 200, "[{\"type\":\"X\"}]", "application/json");
        FakeChain chain = new FakeChain(req, resp);
        assertSame(resp, interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain));
    }

    @Test
    public void unknownEventInterceptor_unsuccessfulResponse_passesThrough() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        Response resp = cannedResponse(req, 500, "err", "application/json");
        FakeChain chain = new FakeChain(req, resp);
        assertSame(resp, interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain));
    }

    @Test
    public void unknownEventInterceptor_nonJsonBody_passesThrough() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        Response resp = cannedResponse(req, 200, "<html/>", "text/html");
        FakeChain chain = new FakeChain(req, resp);
        assertSame(resp, interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain));
    }

    @Test
    public void unknownEventInterceptor_nullContentType_passesThrough() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        Response resp = cannedResponse(req, 200, "[]", null);
        FakeChain chain = new FakeChain(req, resp);
        assertSame(resp, interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain));
    }

    @Test
    public void unknownEventInterceptor_unknownTypeStripped() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        String body = "[{\"type\":\"PushEvent\",\"id\":1},"
                + "{\"type\":\"BrandNewEventXYZ\",\"id\":2},"
                + "{\"type\":\"ForkEvent\",\"id\":3}]";
        Response resp = cannedResponse(req, 200, body, "application/json");
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain);
        org.json.JSONArray kept = new org.json.JSONArray(out.body().string());
        assertEquals(2, kept.length());
        assertEquals("PushEvent", kept.getJSONObject(0).getString("type"));
        assertEquals("ForkEvent", kept.getJSONObject(1).getString("type"));
    }

    @Test
    public void unknownEventInterceptor_allKnownTypes_bodyBytesUnchanged() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        String body = "[{\"type\":\"PushEvent\",\"id\":1}]";
        Response resp = cannedResponse(req, 200, body, "application/json");
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain);
        assertEquals(body, out.body().string());
    }

    @Test
    public void unknownEventInterceptor_missingTypeField_kept() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        String body = "[{\"id\":1},{\"type\":\"PushEvent\",\"id\":2}]";
        Response resp = cannedResponse(req, 200, body, "application/json");
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain);
        org.json.JSONArray kept = new org.json.JSONArray(out.body().string());
        assertEquals(2, kept.length());
    }

    @Test
    public void unknownEventInterceptor_nonObjectEntries_kept() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        String body = "[42,\"str\",{\"type\":\"PushEvent\"}]";
        Response resp = cannedResponse(req, 200, body, "application/json");
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain);
        org.json.JSONArray kept = new org.json.JSONArray(out.body().string());
        assertEquals(3, kept.length());
    }

    @Test
    public void unknownEventInterceptor_malformedJson_failOpenPreservesBody() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        String body = "[{bad json";
        Response resp = cannedResponse(req, 200, body, "application/json");
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain);
        assertEquals(body, out.body().string());
    }

    @Test
    public void unknownEventInterceptor_nullBody_passesThrough() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        Response resp = cannedResponse(req, 200, null, "application/json");
        FakeChain chain = new FakeChain(req, resp);
        assertSame(resp, interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain));
    }

    // ---- ETAG / CACHE_MAX_AGE / CACHE_BYPASS / CACHE_STATUS ----

    @Test
    public void etagInterceptor_emptyEtag_addsNoStore() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = new Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("ETag", "W/\"\"")
                .body(ResponseBody.create(JSON, "{}"))
                .build();
        Response out = interceptor("ETAG_WORKAROUND_INTERCEPTOR")
                .intercept(new FakeChain(req, resp));
        assertEquals("no-store", out.header("Cache-Control"));
    }

    @Test
    public void etagInterceptor_normalEtag_unchanged() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = cannedResponse(req, 200, "{}", "application/json");
        Response withEtag = resp.newBuilder().header("ETag", "\"abc123\"").build();
        FakeChain chain = new FakeChain(req, withEtag);
        assertSame(withEtag,
                interceptor("ETAG_WORKAROUND_INTERCEPTOR").intercept(chain));
    }

    @Test
    public void etagInterceptor_noEtag_unchanged() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = cannedResponse(req, 200, "{}", "application/json");
        assertSame(resp, interceptor("ETAG_WORKAROUND_INTERCEPTOR")
                .intercept(new FakeChain(req, resp)));
    }

    @Test
    public void cacheMaxAgeInterceptor_longMaxAge_clampedToTwoSeconds() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = new Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Cache-Control", "max-age=60")
                .body(ResponseBody.create(JSON, "{}"))
                .build();
        Response out = interceptor("CACHE_MAX_AGE_INTERCEPTOR")
                .intercept(new FakeChain(req, resp));
        assertEquals(2, CacheControl.parse(out.headers()).maxAgeSeconds());
    }

    @Test
    public void cacheMaxAgeInterceptor_shortMaxAge_unchanged() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = new Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Cache-Control", "max-age=1")
                .body(ResponseBody.create(JSON, "{}"))
                .build();
        assertSame(resp, interceptor("CACHE_MAX_AGE_INTERCEPTOR")
                .intercept(new FakeChain(req, resp)));
    }

    @Test
    public void cacheMaxAgeInterceptor_preservesStaleFreshAndFlags() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = new Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .header("Cache-Control", "max-age=60, max-stale=30, min-fresh=5, "
                        + "no-cache, no-store, no-transform")
                .body(ResponseBody.create(JSON, "{}"))
                .build();
        Response out = interceptor("CACHE_MAX_AGE_INTERCEPTOR")
                .intercept(new FakeChain(req, resp));
        CacheControl cc = CacheControl.parse(out.headers());
        assertEquals(2, cc.maxAgeSeconds());
        assertEquals(30, cc.maxStaleSeconds());
        assertEquals(5, cc.minFreshSeconds());
        assertTrue(cc.noCache());
        assertTrue(cc.noStore());
        assertTrue(cc.noTransform());
    }

    @Test
    public void cacheBypassInterceptor_addsNoCacheHeader() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = cannedResponse(req, 200, "{}", "application/json");
        FakeChain chain = new FakeChain(req, resp);
        assertSame(resp, interceptor("CACHE_BYPASS_INTERCEPTOR").intercept(chain));
        assertEquals("no-cache", chain.proceededWith.header("Cache-Control"));
    }

    @Test
    public void cacheStatusInterceptor_passesThrough() throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response resp = cannedResponse(req, 200, "{}", "application/json");
        assertSame(resp, interceptor("CACHE_STATUS_INTERCEPTOR")
                .intercept(new FakeChain(req, resp)));
    }

    // ---- initClient / get / evictCaches ----

    @Test
    public void initClient_nullCacheDir_buildsCachelessClients() {
        Context mockContext = mock(Context.class);
        // getCacheDir() returns null by default on the mock.
        assertNull(mockContext.getCacheDir());
        ServiceFactory.initClient(mockContext);
        assertNotNull(ServiceFactory.getHttpClientBuilder());
        OkHttpClient imageClient = ServiceFactory.getImageHttpClient();
        assertNotNull(imageClient);
        assertNull(imageClient.cache());
        // Restore clients backed by the real app context for other tests.
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void initClient_realContext_buildsCachedClients() {
        Context app = ApplicationProvider.getApplicationContext();
        ServiceFactory.initClient(app);
        assertNotNull(ServiceFactory.getHttpClientBuilder());
        OkHttpClient imageClient = ServiceFactory.getImageHttpClient();
        assertNotNull(imageClient);
        assertNotNull(imageClient.cache());
        assertTrue(imageClient.cache().directory().getPath()
                .contains("image-http"));
    }

    @Test
    public void get_returnsCachedSameInstance() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        TrendService a = ServiceFactory.get(TrendService.class, false);
        TrendService b = ServiceFactory.get(TrendService.class, false);
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    public void get_bypassCacheReturnsDifferentInstance() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        TrendService cached = ServiceFactory.get(TrendService.class, false);
        TrendService bypassed = ServiceFactory.get(TrendService.class, true);
        assertNotNull(bypassed);
        assertNotEquals(cached, bypassed);
    }

    @Test
    public void get_pageSizeOverloadReturnsDistinctInstance() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        TrendService noPage = ServiceFactory.get(TrendService.class, false);
        TrendService paged = ServiceFactory.get(TrendService.class, false, 100);
        assertNotNull(paged);
        assertNotEquals(noPage, paged);
        assertSame(paged, ServiceFactory.get(TrendService.class, false, 100));
    }

    @Test
    public void getForFullPagedLists_returnsService() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        assertNotNull(ServiceFactory.getForFullPagedLists(TrendService.class, false));
    }

    @Test
    public void get_withExplicitToken_returnsService() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        assertNotNull(ServiceFactory.get(TrendService.class, false, null, "tok-xyz", null));
    }

    @Test
    public void evictCaches_noThrow() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        ServiceFactory.evictCaches();
    }

    @Test
    public void evictOne_nullClient_noThrow() {
        try {
            Method m = ServiceFactory.class.getDeclaredMethod("evictOne", OkHttpClient.class);
            m.setAccessible(true);
            m.invoke(null, (OkHttpClient) null);
        } catch (Exception e) {
            throw new AssertionError("evictOne(null)", e);
        }
    }

    // ---- default constructor (line 35) ----

    @Test
    public void defaultConstructor_instantiates() throws Exception {
        java.lang.reflect.Constructor<ServiceFactory> c =
                ServiceFactory.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }

    // ---- auth interceptor lambda inside createService (lines 269-304) ----

    private static Method findAuthLambda() {
        for (Method m : ServiceFactory.class.getDeclaredMethods()) {
            if (m.getName().startsWith("lambda$createService")
                    && m.getParameterCount() == 4
                    && Interceptor.Chain.class.isAssignableFrom(m.getParameterTypes()[3])) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new AssertionError("auth lambda not found");
    }

    private static Request runAuthLambda(String token, Integer pageSize,
            String acceptHeader, Request request) throws Exception {
        FakeChain chain = new FakeChain(request,
                cannedResponse(request, 200, "{}", "application/json"));
        findAuthLambda().invoke(null, token, pageSize, acceptHeader, chain);
        return chain.proceededWith;
    }

    private static String defaultAcceptHeader() {
        try {
            Field f = ServiceFactory.class.getDeclaredField("DEFAULT_HEADER_ACCEPT");
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (Exception e) {
            throw new AssertionError("DEFAULT_HEADER_ACCEPT", e);
        }
    }

    @Test
    public void authInterceptor_explicitToken_setsAuthAndNoStore() throws Exception {
        Request out = runAuthLambda("tok-1", null, null,
                request("https://api.github.com/user"));
        assertEquals("Token tok-1", out.header("Authorization"));
        assertEquals("no-store", out.header("Cache-Control"));
        assertEquals(defaultAcceptHeader(), out.header("Accept"));
    }

    @Test
    public void authInterceptor_noTokenNoLogin_noAuthHeader() throws Exception {
        Request out = runAuthLambda(null, null, null,
                request("https://api.github.com/user"));
        assertNull(out.header("Authorization"));
        assertNull(out.header("Cache-Control"));
        assertEquals(defaultAcceptHeader(), out.header("Accept"));
    }

    @Test
    public void authInterceptor_noTokenUsesAppToken() throws Exception {
        Gh4Application app = ApplicationProvider.getApplicationContext();
        app.getSharedPreferences(com.gh4a.fragment.SettingsFragment.PREF_NAME,
                android.content.Context.MODE_PRIVATE).edit().clear().apply();
        try {
            com.meisolsson.githubsdk.model.User user =
                    com.meisolsson.githubsdk.model.User.builder()
                            .login("svc-user").id(777L).build();
            app.addAccount(user, "svc-token-xyz");
            Request out = runAuthLambda(null, null, null,
                    request("https://api.github.com/user"));
            assertEquals("Token svc-token-xyz", out.header("Authorization"));
            assertEquals("no-store", out.header("Cache-Control"));
        } finally {
            app.removeAccount("svc-user");
        }
    }

    @Test
    public void authInterceptor_pageSize_addsPerPageOnlyWhenPagePresent() throws Exception {
        Request withPage = runAuthLambda(null, 25, null,
                request("https://api.github.com/repos/o/r/issues?page=2"));
        assertEquals("25", withPage.url().queryParameter("per_page"));
        assertEquals("2", withPage.url().queryParameter("page"));
        Request noPage = runAuthLambda(null, 25, null,
                request("https://api.github.com/repos/o/r/issues"));
        assertNull(noPage.url().queryParameter("per_page"));
    }

    @Test
    public void authInterceptor_customAcceptHeader_used() throws Exception {
        Request out = runAuthLambda(null, null, "application/vnd.github.foo",
                request("https://api.github.com/user"));
        assertEquals("application/vnd.github.foo", out.header("Accept"));
    }

    @Test
    public void authInterceptor_existingAcceptHeader_kept() throws Exception {
        Request original = new Request.Builder()
                .url("https://api.github.com/user")
                .header("Accept", "custom/accept")
                .build();
        Request out = runAuthLambda(null, null, "application/vnd.github.other", original);
        assertEquals("custom/accept", out.header("Accept"));
    }

    // ---- UNKNOWN_EVENT_TYPE_INTERCEPTOR catch (line 104) ----

    @Test
    public void unknownEventTypeInterceptor_bodyBytesThrow_returnsOriginalResponse()
            throws Exception {
        Request req = request("https://api.github.com/users/x/received_events");
        ResponseBody throwingBody = new ResponseBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/json");
            }

            @Override
            public long contentLength() {
                return 0;
            }

            @Override
            public okio.BufferedSource source() {
                throw new RuntimeException(new java.io.IOException("boom"));
            }
        };
        Response original = new Response.Builder()
                .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(throwingBody).build();
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR")
                .intercept(new FakeChain(req, original));
        assertSame(original, out);
    }

    // ---- evictOne catch (line 366) ----

    @Test
    public void evictOne_cacheThrows_swallowed() throws Exception {
        OkHttpClient client = mock(OkHttpClient.class);
        okhttp3.Cache cache = mock(okhttp3.Cache.class);
        when(client.cache()).thenReturn(cache);
        doThrow(new java.io.IOException("boom")).when(cache).evictAll();
        Method m = ServiceFactory.class.getDeclaredMethod("evictOne", OkHttpClient.class);
        m.setAccessible(true);
        m.invoke(null, client);
        verify(cache).evictAll();
    }

    @Test
    public void getHttpClientBuilder_returnsNewBuilderEachTime() {
        ServiceFactory.initClient(ApplicationProvider.getApplicationContext());
        OkHttpClient.Builder a = ServiceFactory.getHttpClientBuilder();
        OkHttpClient.Builder b = ServiceFactory.getHttpClientBuilder();
        assertNotNull(a);
        assertNotNull(b);
        assertTrue(a != b);
    }

    // ---- gap-closing tests round 2 ----

    @Test
    public void unknownEventTypeInterceptor_malformedJson_returnsOriginalBytes() throws Exception {
        Request req = request("https://api.github.com/users/o/events");
        Response resp = cannedResponse(req, 200, "{bad", "application/json");
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("UNKNOWN_EVENT_TYPE_INTERCEPTOR").intercept(chain);
        assertNotNull(out.body());
        assertEquals("{bad", out.body().string());
    }

    @Test
    public void cacheStatusInterceptor_withNetworkAndCacheResponses_returnsResponse()
            throws Exception {
        Request req = request("https://api.github.com/users/o");
        Response inner = cannedResponse(req, 200, null, null);
        Response resp = cannedResponse(req, 200, "ok", "application/json")
                .newBuilder()
                .networkResponse(inner)
                .cacheResponse(inner)
                .build();
        FakeChain chain = new FakeChain(req, resp);
        Response out = interceptor("CACHE_STATUS_INTERCEPTOR").intercept(chain);
        assertSame(resp, out);
    }

    @Test
    public void evictOne_nullCache_noThrow() throws Exception {
        OkHttpClient client = mock(OkHttpClient.class);
        when(client.cache()).thenReturn(null);
        Method m = ServiceFactory.class.getDeclaredMethod("evictOne", OkHttpClient.class);
        m.setAccessible(true);
        m.invoke(null, client);
        verify(client).cache();
    }
}
