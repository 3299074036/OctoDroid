package com.gh4a.utils.translate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BaiduTranslator with a stubbed OkHttp interceptor: no real network,
 * fake credentials "test-appid" / "test-key" only.
 * Robolectric is needed for android.os.SystemClock (throttle) and org.json.
 * Note: the static 1.1s throttle is shared across tests in this class;
 * Robolectric's SystemClock makes the sleeps short in practice.
 */
@RunWith(RobolectricTestRunner.class)
public class BaiduTranslatorTest {

    private static final class Captured {
        Request request;
    }

    private static OkHttpClient fakeClient(String body, int code, Captured cap) {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    cap.request = chain.request();
                    ResponseBody rb = ResponseBody.create(body,
                            MediaType.get("application/json; charset=utf-8"));
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(code)
                            .message(code == 200 ? "OK" : "Error")
                            .body(rb)
                            .build();
                })
                .build();
    }

    private static String formValue(Request request, String name) {
        RequestBody body = request.body();
        if (body instanceof FormBody) {
            FormBody form = (FormBody) body;
            for (int i = 0; i < form.size(); i++) {
                if (form.name(i).equals(name)) {
                    return form.value(i);
                }
            }
        }
        return null;
    }

    private static final String OK_BODY =
            "{\"from\":\"en\",\"to\":\"zh\",\"trans_result\":[{\"src\":\"Hello\",\"dst\":\"你好\"}]}";

    @Test
    public void translate_successReturnsDst() throws Exception {
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-appid", "test-key");
        assertEquals("你好", t.translate("Hello", "en", "zh-CN"));
    }

    @Test
    public void translate_multipleResultsJoinedWithNewline() throws Exception {
        String body = "{\"trans_result\":[{\"src\":\"a\",\"dst\":\"一\"},{\"src\":\"b\",\"dst\":\"二\"}]}";
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(body, 200, new Captured()), "test-appid", "test-key");
        assertEquals("一\n二", t.translate("a\nb", "en", "zh-CN"));
    }

    @Test
    public void translate_sendsSignedForm() throws Exception {
        Captured cap = new Captured();
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, cap), "test-appid", "test-key");
        t.translate("Hello", "en", "zh-CN");
        assertEquals("https://fanyi-api.baidu.com/api/trans/vip/translate",
                cap.request.url().toString());
        assertEquals("Hello", formValue(cap.request, "q"));
        assertEquals("auto", formValue(cap.request, "from"));
        assertEquals("zh", formValue(cap.request, "to"));
        assertEquals("test-appid", formValue(cap.request, "appid"));
        String salt = formValue(cap.request, "salt");
        assertTrue(salt != null && !salt.isEmpty());
        String sign = formValue(cap.request, "sign");
        assertTrue(sign != null && sign.matches("[0-9a-f]{32}"));
    }

    @Test
    public void translate_mapsLanguages() throws Exception {
        Captured cap = new Captured();
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, cap), "test-appid", "test-key");
        t.translate("x", "en", "ja");
        assertEquals("jp", formValue(cap.request, "to"));
        t.translate("x", "en", "ko");
        assertEquals("kor", formValue(cap.request, "to"));
        t.translate("x", "en", "en");
        assertEquals("en", formValue(cap.request, "to"));
    }

    @Test
    public void translate_missingCredentialsThrow() {
        String[][] creds = {{null, "test-key"}, {"", "test-key"},
                {"test-appid", null}, {"test-appid", ""}};
        for (String[] c : creds) {
            BaiduTranslator t = new BaiduTranslator(
                    fakeClient(OK_BODY, 200, new Captured()), c[0], c[1]);
            try {
                t.translate("Hello", "en", "zh-CN");
                fail("expected IOException for appId=" + c[0] + " secret=" + c[1]);
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("not configured"));
            } catch (Exception e) {
                fail("expected IOException, got " + e);
            }
        }
    }

    @Test
    public void translate_apiErrorCodeThrows() {
        String body = "{\"error_code\":\"54003\",\"error_msg\":\"Invalid Sign\"}";
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(body, 200, new Captured()), "test-appid", "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("54003"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_httpErrorThrows() {
        BaiduTranslator t = new BaiduTranslator(
                fakeClient("error", 500, new Captured()), "test-appid", "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("500"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_emptyTransResultThrows() {
        BaiduTranslator t = new BaiduTranslator(
                fakeClient("{\"trans_result\":[]}", 200, new Captured()),
                "test-appid", "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("empty result"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_emptyDstThrows() {
        BaiduTranslator t = new BaiduTranslator(
                fakeClient("{\"trans_result\":[{\"src\":\"Hello\",\"dst\":\"\"}]}",
                        200, new Captured()), "test-appid", "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("empty result"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void getName_isBaidu() {
        assertEquals("Baidu", new BaiduTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-appid", "test-key").getName());
    }

    @Test
    public void getMaxParallelRequests_isOne() {
        assertEquals(1, new BaiduTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-appid", "test-key")
                .getMaxParallelRequests());
    }
    private static OkHttpClient fakeClientNullBody() throws Exception {
        // OkHttp 的拦截器链不允许拦截器返回无 body 的 Response（会先抛
        // IllegalStateException，异常在翻译器代码之前就抛了），所以不走拦截器：
        // 直接 mock OkHttpClient，让 newCall 返回的 Call.execute() 给出无 body 的 Response。
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Response nullBody = new Response.Builder()
                .request(new Request.Builder().url("https://example.com/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build();
        when(client.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(nullBody);
        return client;
    }

    @Test
    public void translate_nullBodyThrows() throws Exception {
        // L65：200 但 body 为 null 时抛错（含状态码）。
        BaiduTranslator t = new BaiduTranslator(
                fakeClientNullBody(), "test-appid", "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("200"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_emptyResultsThrows() {
        // L74：body 里没有 trans_result 数组时抛“空结果”错。
        BaiduTranslator t = new BaiduTranslator(
                fakeClient("{}", 200, new Captured()), "test-appid", "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("empty result"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_backToBackSecondCallThrottles() throws Exception {
        // L96-98：两次连续调用之间不足 1.1 秒时第二次走 Thread.sleep 节流，
        // 两次都返回正确结果。
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-appid", "test-key");
        assertEquals("你好", t.translate("Hello", "en", "zh-CN"));
        assertEquals("你好", t.translate("Hello", "en", "zh-CN"));
    }

    @Test
    public void translate_elapsedPastIntervalSkipsThrottle() throws Exception {
        // L96 的 false 边：上次请求已是 5 秒前，elapsed >= MIN_INTERVAL_MS
        //（1100），不 sleep 直接发请求。直接反射置 sLastRequestMs——Robolectric
        // 下 SystemClock.elapsedRealtime() 被冻结，不置它时 elapsed 恒为 0，
        // false 边永远走不到（这就是第 1 轮背靠背测试只覆盖了 true 边的原因）。
        // 墙钟断言证明没走 sleep 分支（一次节流 sleep 约 1100ms）。
        java.lang.reflect.Field f =
                BaiduTranslator.class.getDeclaredField("sLastRequestMs");
        f.setAccessible(true);
        f.setLong(null, android.os.SystemClock.elapsedRealtime() - 5000);
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-appid", "test-key");
        long start = System.nanoTime();
        assertEquals("你好", t.translate("Hello", "en", "zh-CN"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("throttle sleep taken unexpectedly, took " + elapsedMs + "ms",
                elapsedMs < 1000);
    }

    @Test
    public void translate_throttleInterruptedRestoresFlag() throws Exception {
        // L99-100：节流 sleep 被打断时 catch 里恢复中断标志，翻译照常完成。
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-appid", "test-key");
        t.translate("Hello", "en", "zh-CN");
        java.util.concurrent.atomic.AtomicReference<Throwable> thrown =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean interruptedFlag =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            try {
                t.translate("Hello", "en", "zh-CN");
            } catch (Throwable e) {
                thrown.set(e);
            } finally {
                interruptedFlag.set(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        Thread.sleep(200);
        worker.interrupt();
        worker.join(5000);
        assertEquals(null, thrown.get());
        assertTrue(interruptedFlag.get());
    }

    @Test
    public void translate_nullOrEmptyTargetLangMapsToZh() throws Exception {
        // L108-109：目标语言为 null 或空串时 mapLang 返回 "zh"。
        Captured cap = new Captured();
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, cap), "test-appid", "test-key");
        assertEquals("你好", t.translate("Hello", "en", null));
        assertEquals("zh", formValue(cap.request, "to"));
        assertEquals("你好", t.translate("Hello", "en", ""));
        assertEquals("zh", formValue(cap.request, "to"));
    }

    @Test
    public void translate_singleCharLangIsKeptAsIs() throws Exception {
        // L121：单字符语言码不做截取，原样送出。
        Captured cap = new Captured();
        BaiduTranslator t = new BaiduTranslator(
                fakeClient(OK_BODY, 200, cap), "test-appid", "test-key");
        assertEquals("你好", t.translate("Hello", "en", "x"));
        assertEquals("x", formValue(cap.request, "to"));
    }

}
