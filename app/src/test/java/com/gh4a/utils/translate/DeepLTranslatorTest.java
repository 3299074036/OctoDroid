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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DeepLTranslator with a stubbed OkHttp interceptor: no real network,
 * fake key "test-key" / "test-key:fx" only. Robolectric is needed for org.json.
 */
@RunWith(RobolectricTestRunner.class)
public class DeepLTranslatorTest {

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
            "{\"translations\":[{\"detected_source_language\":\"EN\",\"text\":\"你好\"}]}";

    @Test
    public void translate_successReturnsText() throws Exception {
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-key");
        assertEquals("你好", t.translate("Hello", "en", "zh-CN"));
    }

    @Test
    public void translate_freeKeyUsesFreeEndpoint() throws Exception {
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, cap), "test-key:fx");
        t.translate("Hello", "en", "zh-CN");
        assertEquals("https://api-free.deepl.com/v2/translate",
                cap.request.url().toString());
    }

    @Test
    public void translate_proKeyUsesProEndpoint() throws Exception {
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, cap), "test-key");
        t.translate("Hello", "en", "zh-CN");
        assertEquals("https://api.deepl.com/v2/translate", cap.request.url().toString());
    }

    @Test
    public void translate_sendsAuthHeader() throws Exception {
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, cap), "test-key");
        t.translate("Hello", "en", "zh-CN");
        assertEquals("DeepL-Auth-Key test-key", cap.request.header("Authorization"));
    }

    @Test
    public void translate_autoSourceOmitsSourceLang() throws Exception {
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, cap), "test-key");
        t.translate("Hello", "auto", "zh-CN");
        assertNull(formValue(cap.request, "source_lang"));
        assertEquals("ZH", formValue(cap.request, "target_lang"));
        assertEquals("Hello", formValue(cap.request, "text"));
    }

    @Test
    public void translate_mapsLanguages() throws Exception {
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, cap), "test-key");
        t.translate("Hello", "en", "fr");
        assertEquals("EN", formValue(cap.request, "source_lang"));
        assertEquals("FR", formValue(cap.request, "target_lang"));
    }

    @Test
    public void translate_zhTargetMapsToZh() throws Exception {
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient(OK_BODY, 200, cap), "test-key");
        t.translate("Hello", "en", "zh-TW");
        assertEquals("ZH", formValue(cap.request, "target_lang"));
    }

    @Test
    public void translate_missingKeyThrows() {
        for (String key : new String[]{null, ""}) {
            DeepLTranslator t = new DeepLTranslator(
                    fakeClient(OK_BODY, 200, new Captured()), key);
            try {
                t.translate("Hello", "en", "zh-CN");
                fail("expected IOException for key=" + key);
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("not configured"));
            } catch (Exception e) {
                fail("expected IOException, got " + e);
            }
        }
    }

    @Test
    public void translate_httpErrorThrows() {
        DeepLTranslator t = new DeepLTranslator(
                fakeClient("unauthorized", 403, new Captured()), "test-key");
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("403"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_emptyTranslationsThrows() {
        DeepLTranslator t = new DeepLTranslator(
                fakeClient("{\"translations\":[]}", 200, new Captured()), "test-key");
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
    public void translate_emptyTextFieldThrows() {
        DeepLTranslator t = new DeepLTranslator(
                fakeClient("{\"translations\":[{\"text\":\"\"}]}", 200, new Captured()), "test-key");
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
    public void getName_isDeepL() {
        assertEquals("DeepL", new DeepLTranslator(
                fakeClient(OK_BODY, 200, new Captured()), "test-key").getName());
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
        // L52：200 但 body 为 null 时抛错（含状态码）。
        DeepLTranslator t = new DeepLTranslator(fakeClientNullBody(), "test-key");
        try {
            t.translate("Hello", "auto", "zh");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("200"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_missingTranslationsThrows() {
        // L58：body 里没有 translations 数组时抛“空结果”错。
        DeepLTranslator t = new DeepLTranslator(
                fakeClient("{}", 200, new Captured()), "test-key");
        try {
            t.translate("Hello", "auto", "zh");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("empty result"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

    @Test
    public void translate_nullOrEmptyTargetLangMapsToZh() throws Exception {
        // L70-71：目标语言为 null 或空串时 mapLang 返回 "ZH"。
        Captured cap = new Captured();
        DeepLTranslator t = new DeepLTranslator(
                fakeClient("{\"translations\":[{\"text\":\"你好\"}]}", 200, cap),
                "test-key");
        assertEquals("你好", t.translate("Hello", "auto", null));
        assertEquals("ZH", formValue(cap.request, "target_lang"));
        assertEquals("你好", t.translate("Hello", "auto", ""));
        assertEquals("ZH", formValue(cap.request, "target_lang"));
    }

}
