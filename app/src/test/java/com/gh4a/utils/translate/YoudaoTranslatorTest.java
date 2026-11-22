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
 * YoudaoTranslator with a stubbed OkHttp interceptor: no real network,
 * fake credentials "test-key" / "test-secret" only.
 * Robolectric is needed for org.json.
 */
@RunWith(RobolectricTestRunner.class)
public class YoudaoTranslatorTest {

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
            "{\"errorCode\":\"0\",\"translation\":[\"你好\"],\"query\":\"Hello\"}";

    @Test
    public void translate_successReturnsFirstTranslation() throws Exception {
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient(OK_BODY, 200, new Captured()), null, "test-key", "test-secret");
        assertEquals("你好", t.translate("Hello", "en", "zh-CN"));
    }

    @Test
    public void translate_defaultEndpointWhenUrlNullOrEmpty() throws Exception {
        for (String url : new String[]{null, ""}) {
            Captured cap = new Captured();
            YoudaoTranslator t = new YoudaoTranslator(
                    fakeClient(OK_BODY, 200, cap), url, "test-key", "test-secret");
            t.translate("Hello", "en", "zh-CN");
            assertEquals("https://openapi.youdao.com/api", cap.request.url().toString());
        }
    }

    @Test
    public void translate_customEndpointUsed() throws Exception {
        Captured cap = new Captured();
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient(OK_BODY, 200, cap), "https://example.test/api",
                "test-key", "test-secret");
        t.translate("Hello", "en", "zh-CN");
        assertEquals("https://example.test/api", cap.request.url().toString());
    }

    @Test
    public void translate_sendsSignedForm() throws Exception {
        Captured cap = new Captured();
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient(OK_BODY, 200, cap), null, "test-key", "test-secret");
        t.translate("Hello", "auto", "zh-CN");
        assertEquals("Hello", formValue(cap.request, "q"));
        assertEquals("auto", formValue(cap.request, "from"));
        assertEquals("zh-CHS", formValue(cap.request, "to"));
        assertEquals("test-key", formValue(cap.request, "appKey"));
        assertEquals("v3", formValue(cap.request, "signType"));
        String sign = formValue(cap.request, "sign");
        assertTrue(sign != null && sign.matches("[0-9a-f]{64}"));
        assertTrue(formValue(cap.request, "salt") != null);
        assertTrue(formValue(cap.request, "curtime") != null);
    }

    @Test
    public void translate_nonAutoSourceMapped() throws Exception {
        Captured cap = new Captured();
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient(OK_BODY, 200, cap), null, "test-key", "test-secret");
        t.translate("Hello", "en", "fr");
        assertEquals("en", formValue(cap.request, "from"));
        assertEquals("fr", formValue(cap.request, "to"));
    }

    @Test
    public void translate_missingCredentialsThrow() {
        String[][] creds = {{null, "test-secret"}, {"", "test-secret"},
                {"test-key", null}, {"test-key", ""}};
        for (String[] c : creds) {
            YoudaoTranslator t = new YoudaoTranslator(
                    fakeClient(OK_BODY, 200, new Captured()), null, c[0], c[1]);
            try {
                t.translate("Hello", "en", "zh-CN");
                fail("expected IOException for key=" + c[0] + " secret=" + c[1]);
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("not configured"));
            } catch (Exception e) {
                fail("expected IOException, got " + e);
            }
        }
    }

    @Test
    public void translate_nonZeroErrorCodeThrows() {
        String body = "{\"errorCode\":\"108\",\"translation\":[\"x\"]}";
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient(body, 200, new Captured()), null, "test-key", "test-secret");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("108"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_httpErrorThrows() {
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient("error", 500, new Captured()), null, "test-key", "test-secret");
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
    public void translate_emptyTranslationArrayThrows() {
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient("{\"errorCode\":\"0\",\"translation\":[]}", 200, new Captured()),
                null, "test-key", "test-secret");
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
    public void getName_isYoudao() {
        assertEquals("Youdao", new YoudaoTranslator(
                fakeClient(OK_BODY, 200, new Captured()), null, "test-key", "test-secret")
                .getName());
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
        // L62：200 但 body 为 null 时抛错（含状态码）。
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClientNullBody(), null, "test-key", "test-secret");
        try {
            t.translate("Hello", "en", "zh-CHS");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("200"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_emptyTranslationThrows() {
        // L71：errorCode 为 0 但没有 translation 数组时抛“空结果”错。
        YoudaoTranslator t = new YoudaoTranslator(
                fakeClient("{\"errorCode\":\"0\"}", 200, new Captured()),
                null, "test-key", "test-secret");
        try {
            t.translate("Hello", "en", "zh-CHS");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("empty result"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void truncate_nullReturnsEmpty() throws Exception {
        // L86-87：q 为 null 时截断结果为空串（签名用空串计算，不抛错）。
        java.lang.reflect.Method m = YoudaoTranslator.class.getDeclaredMethod(
                "truncate", String.class);
        m.setAccessible(true);
        assertEquals("", m.invoke(null, (Object) null));
    }

    @Test
    public void truncate_longStringKeepsHeadAndTailWithLength() throws Exception {
        // L90-93：长度超 20 时保留前 10 + 长度 + 后 10。
        java.lang.reflect.Method m = YoudaoTranslator.class.getDeclaredMethod(
                "truncate", String.class);
        m.setAccessible(true);
        assertEquals("abcdefghij360123456789",
                m.invoke(null, "abcdefghijklmnopqrstuvwxyz0123456789"));
    }

}
