package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.User;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;

/** GraphQLClient (F1): cancel handle + UnauthorizedException only.
 * execute() is tested via an injected mock OkHttpClient (no real network). */
@RunWith(RobolectricTestRunner.class)
public class GraphQLClientTest {

    @Test
    public void unauthorizedException_carriesMessage() {
        GraphQLClient.UnauthorizedException e =
                new GraphQLClient.UnauthorizedException("GraphQL HTTP 401");
        assertEquals("GraphQL HTTP 401", e.getMessage());
        assertTrue(e instanceof RuntimeException);
    }

    @Test
    public void callHandle_cancelWithoutCall_doesNothing() {
        new GraphQLClient.CallHandle().cancel();
    }

    @Test
    public void callHandle_cancelWithCall_cancelsIt() {
        GraphQLClient.CallHandle handle = new GraphQLClient.CallHandle();
        okhttp3.Call call = mock(okhttp3.Call.class);
        try {
            java.lang.reflect.Field field =
                    GraphQLClient.CallHandle.class.getDeclaredField("mCall");
            field.setAccessible(true);
            field.set(handle, call);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        handle.cancel();
        verify(call).cancel();
    }

    // ---------- mocked-network helpers ----------

    private static Field sClientField() throws Exception {
        Field f = GraphQLClient.class.getDeclaredField("sClient");
        f.setAccessible(true);
        return f;
    }

    private static OkHttpClient invokeClient() throws Exception {
        Method m = GraphQLClient.class.getDeclaredMethod("client");
        m.setAccessible(true);
        return (OkHttpClient) m.invoke(null);
    }

    /** Builds the real client (with the real auth interceptor) via a mocked builder. */
    private static OkHttpClient buildRealClient() throws Exception {
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(ServiceFactory::getHttpClientBuilder)
                    .thenReturn(new OkHttpClient.Builder());
            return invokeClient();
        }
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

    private static okhttp3.Call callReturning(okhttp3.Response response) throws Exception {
        okhttp3.Call call = mock(okhttp3.Call.class);
        when(call.execute()).thenReturn(response);
        return call;
    }

    private static void injectMockClient(OkHttpClient client) throws Exception {
        sClientField().set(null, client);
    }

    private static OkHttpClient mockClientReturning(okhttp3.Response response) throws Exception {
        OkHttpClient client = mock(OkHttpClient.class);
        okhttp3.Call call = callReturning(response);
        when(client.newCall(any())).thenReturn(call);
        return client;
    }

    private static Object handleCallField(GraphQLClient.CallHandle handle) throws Exception {
        Field f = GraphQLClient.CallHandle.class.getDeclaredField("mCall");
        f.setAccessible(true);
        return f.get(handle);
    }

    private static void loginFakeToken() {
        User user = mock(User.class);
        when(user.login()).thenReturn("octo");
        Gh4Application.get().addAccount(user, "fake-token");
    }

    @Test
    public void implicitCtor_instantiatesUtilityClass() {
        assertNotNull(new GraphQLClient());
    }

    @Test
    public void client_doubleCheckedLocking_secondCallerSeesCachedClient() throws Exception {
        // L68/L69：第二个调用方在外层读到 null、进 synchronized 后重读到已缓存的 client。
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        OkHttpClient cached = mock(OkHttpClient.class);
        try {
            f.set(null, null);
            CountDownLatch holderReady = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Object> result = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread holder = new Thread(() -> {
                synchronized (GraphQLClient.class) {
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
                    result.set(invokeClient());
                } catch (Throwable t) {
                    error.set(t);
                }
            });
            holder.start();
            assertTrue(holderReady.await(10, TimeUnit.SECONDS));
            worker.start();
            // 等 worker 在 GraphQLClient.class 监视器上阻塞：此时它已读到外层 null，
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
    public void client_buildsOnceAndCaches() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, null);
            OkHttpClient c1 = buildRealClient();
            OkHttpClient c2 = invokeClient();
            assertNotNull(c1);
            assertTrue(c1 == c2);
            assertEquals(1, c1.interceptors().size());
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void interceptor_noTokenOmitsAuthHeader() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, null);
            assertNull(Gh4Application.get().getAuthToken());
            OkHttpClient client = buildRealClient();
            Interceptor auth = client.interceptors().get(0);
            Request req = new Request.Builder().url("https://example.com").build();
            Interceptor.Chain chain = mock(Interceptor.Chain.class);
            when(chain.request()).thenReturn(req);
            okhttp3.Response resp = new okhttp3.Response.Builder().request(req)
                    .protocol(Protocol.HTTP_1_1).code(200).message("OK").build();
            when(chain.proceed(any())).thenReturn(resp);
            assertTrue(auth.intercept(chain) == resp);
            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(chain).proceed(captor.capture());
            assertNull(captor.getValue().header("Authorization"));
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void interceptor_withTokenAddsBearerHeader() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            f.set(null, null);
            loginFakeToken();
            assertEquals("fake-token", Gh4Application.get().getAuthToken());
            OkHttpClient client = buildRealClient();
            Interceptor auth = client.interceptors().get(0);
            Request req = new Request.Builder().url("https://example.com").build();
            Interceptor.Chain chain = mock(Interceptor.Chain.class);
            when(chain.request()).thenReturn(req);
            okhttp3.Response resp = new okhttp3.Response.Builder().request(req)
                    .protocol(Protocol.HTTP_1_1).code(200).message("OK").build();
            when(chain.proceed(any())).thenReturn(resp);
            assertTrue(auth.intercept(chain) == resp);
            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(chain).proceed(captor.capture());
            assertEquals("Bearer fake-token",
                    captor.getValue().header("Authorization"));
        } finally {
            Gh4Application.get().logout();
            f.set(null, saved);
        }
    }

    @Test
    public void execute_twoArgSuccessReturnsJson() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(
                    okHttpResponse("{\"data\":{\"x\":1}}")));
            JSONObject vars = new JSONObject();
            vars.put("a", 1);
            String out = GraphQLClient.execute("{ viewer { login } }", vars);
            assertEquals("{\"data\":{\"x\":1}}", out);
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_twoArgWithTokenSetsBearerHeader() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        OkHttpClient client = mock(OkHttpClient.class);
        okhttp3.Response response = okHttpResponse("{\"data\":{}}");
        okhttp3.Call call = callReturning(response);
        when(client.newCall(any())).thenReturn(call);
        try {
            loginFakeToken();
            injectMockClient(client);
            String out = GraphQLClient.execute("{ viewer { login } }", new JSONObject());
            assertEquals("{\"data\":{}}", out);
            ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
            verify(client).newCall(captor.capture());
            assertEquals("Bearer fake-token",
                    captor.getValue().header("Authorization"));
            assertEquals("https://api.github.com/graphql",
                    captor.getValue().url().toString());
        } finally {
            Gh4Application.get().logout();
            f.set(null, saved);
        }
    }

    @Test
    public void execute_withHandleRegistersAndClearsCall() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(okHttpResponse("{\"data\":{}}")));
            GraphQLClient.CallHandle handle = new GraphQLClient.CallHandle();
            assertNull(handleCallField(handle));
            String out = GraphQLClient.execute("{ q }", new JSONObject(), handle);
            assertEquals("{\"data\":{}}", out);
            // finally 里清掉在途 Call。
            assertNull(handleCallField(handle));
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_handleCallReplacedMidFlightIsNotCleared() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            // 新请求在旧请求读响应期间抢占了同一个 handle：
            // finally 的 `handle.mCall == call` 守卫不得清掉新 Call。
            GraphQLClient.CallHandle handle = new GraphQLClient.CallHandle();
            okhttp3.Call newerCall = mock(okhttp3.Call.class);
            okhttp3.Response response = mock(okhttp3.Response.class);
            when(response.isSuccessful()).thenReturn(true);
            when(response.code()).thenReturn(200);
            ResponseBody rb = mock(ResponseBody.class);
            when(rb.string()).thenAnswer(inv -> {
                Field hf = GraphQLClient.CallHandle.class.getDeclaredField("mCall");
                hf.setAccessible(true);
                hf.set(handle, newerCall);
                return "{\"data\":{}}";
            });
            when(response.body()).thenReturn(rb);
            injectMockClient(mockClientReturning(response));
            String out = GraphQLClient.execute("{ q }", new JSONObject(), handle);
            assertEquals("{\"data\":{}}", out);
            assertSame(newerCall, handleCallField(handle));
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_unauthorizedThrowsUnauthorizedException() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(okHttpErrorResponse(401)));
            try {
                GraphQLClient.execute("{ q }", new JSONObject());
                fail("expected UnauthorizedException");
            } catch (GraphQLClient.UnauthorizedException e) {
                assertEquals("GraphQL HTTP 401", e.getMessage());
            }
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_serverErrorThrowsRuntimeException() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(okHttpErrorResponse(500)));
            try {
                GraphQLClient.execute("{ q }", new JSONObject());
                fail("expected RuntimeException");
            } catch (RuntimeException e) {
                assertEquals("GraphQL HTTP 500", e.getMessage());
            }
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_graphqlErrorsThrowsWithMessage() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(
                    okHttpResponse("{\"errors\":[{\"message\":\"boom\"}]}")));
            try {
                GraphQLClient.execute("{ q }", new JSONObject());
                fail("expected RuntimeException");
            } catch (RuntimeException e) {
                assertEquals("GraphQL: boom", e.getMessage());
            }
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_emptyErrorsArrayReturnsJson() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(okHttpResponse("{\"errors\":[]}")));
            assertEquals("{\"errors\":[]}",
                    GraphQLClient.execute("{ q }", new JSONObject()));
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_errorsNotArrayReturnsJson() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            injectMockClient(mockClientReturning(
                    okHttpResponse("{\"errors\":\"oops\"}")));
            assertEquals("{\"errors\":\"oops\"}",
                    GraphQLClient.execute("{ q }", new JSONObject()));
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_nullBodyThrowsJsonException() throws Exception {
        // 行为钉住：200 空 body 时 json=""，new JSONObject("") 即抛错。
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            okhttp3.Response response = mock(okhttp3.Response.class);
            when(response.isSuccessful()).thenReturn(true);
            when(response.code()).thenReturn(200);
            when(response.body()).thenReturn(null);
            injectMockClient(mockClientReturning(response));
            try {
                GraphQLClient.execute("{ q }", new JSONObject());
                fail("expected JSONException");
            } catch (org.json.JSONException e) {
                assertTrue(e.getMessage().contains("End of input"));
            }
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void execute_callThrowsPropagatesAndClearsHandle() throws Exception {
        Field f = sClientField();
        OkHttpClient saved = (OkHttpClient) f.get(null);
        try {
            okhttp3.Call call = mock(okhttp3.Call.class);
            when(call.execute()).thenThrow(new java.io.IOException("down"));
            OkHttpClient client = mock(OkHttpClient.class);
            when(client.newCall(any())).thenReturn(call);
            injectMockClient(client);
            GraphQLClient.CallHandle handle = new GraphQLClient.CallHandle();
            try {
                GraphQLClient.execute("{ q }", new JSONObject(), handle);
                fail("expected IOException");
            } catch (java.io.IOException e) {
                assertEquals("down", e.getMessage());
            }
            assertNull(handleCallField(handle));
        } finally {
            f.set(null, saved);
        }
    }

    @Test
    public void callHandle_isPublicFinalClass() {
        assertNotNull(new GraphQLClient.CallHandle());
    }
}
