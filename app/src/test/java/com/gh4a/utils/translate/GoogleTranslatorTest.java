package com.gh4a.utils.translate;

import com.gh4a.utils.translate.GoogleTranslator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GoogleTranslator with a stubbed OkHttp interceptor: no real network.
 * Robolectric is needed for android.net.Uri and org.json.
 */
@RunWith(RobolectricTestRunner.class)
public class GoogleTranslatorTest {

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

    @Test
    public void translate_joinsSentences() throws Exception {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient(
                "[[[\"你好\",\"Hello\",null,null,1],[\"世界\",\"world\",null,null,1]],null,\"en\"]",
                200, cap));
        assertEquals("你好世界", t.translate("Hello world", "en", "zh-CN"));
    }

    @Test
    public void translate_singleSentence() throws Exception {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient(
                "[[[\"Bonjour\",\"Hello\",null,null,1]],null,\"en\"]", 200, cap));
        assertEquals("Bonjour", t.translate("Hello", "en", "fr"));
    }

    @Test
    public void translate_nullSentenceElementSkipped() throws Exception {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient(
                "[[[null,\"x\",null,null,1],[\"ok\",\"y\",null,null,1]],null,\"en\"]", 200, cap));
        assertEquals("ok", t.translate("x y", "en", "zh-CN"));
    }

    @Test
    public void translate_requestUrlCarriesLangsAndQuery() throws Exception {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient(
                "[[[\"x\",\"y\",null,null,1]],null,\"en\"]", 200, cap));
        t.translate("Hello world", "auto", "zh-CN");
        String url = cap.request.url().toString();
        assertTrue(url.contains("sl=auto"));
        assertTrue(url.contains("tl=zh-CN"));
        assertTrue(url.contains("q=Hello%20world"));
        assertEquals("Mozilla/5.0", cap.request.header("User-Agent"));
    }

    @Test
    public void translate_httpErrorThrows() {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient("forbidden", 403, cap));
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
    public void translate_emptyResultThrows() {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient(
                "[[[\"\",\"Hello\",null,null,1]],null,\"en\"]", 200, cap));
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
    public void translate_malformedJsonThrows() {
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClient("not json at all", 200, cap));
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected Exception");
        } catch (Exception e) {
            // JSONException (a checked Exception subclass is not declared;
            // any failure to parse must surface, not return a bogus value)
            assertTrue(e.getMessage() != null || e.getCause() != null);
        }
    }

    @Test
    public void getName_isGoogle() {
        assertEquals("Google", new GoogleTranslator(fakeClient("", 200, new Captured())).getName());
    }

    @Test
    public void getMaxParallelRequests_defaultsToFive() {
        assertEquals(5, new GoogleTranslator(fakeClient("", 200, new Captured()))
                .getMaxParallelRequests());
    }
    private static OkHttpClient fakeClientNullBody(int code, Captured cap) throws Exception {
        // 与 Baidu/DeepL/Youdao 同款：不走拦截器（无 body 的 Response 会被
        // 拦截器链先拦下抛 IllegalStateException），直接 mock OkHttpClient；
        // 请求仍经 newCall 的 thenAnswer 捕获，供断言用。
        OkHttpClient client = mock(OkHttpClient.class);
        Call call = mock(Call.class);
        Response nullBody = new Response.Builder()
                .request(new Request.Builder().url("https://example.com/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .build();
        when(client.newCall(any(Request.class))).thenAnswer(inv -> {
            cap.request = inv.getArgument(0);
            return call;
        });
        when(call.execute()).thenReturn(nullBody);
        return client;
    }

    @Test
    public void translate_nullBodyThrows() throws Exception {
        // L35：200 但 body() == null 时同样抛 IOException。
        Captured cap = new Captured();
        GoogleTranslator t = new GoogleTranslator(fakeClientNullBody(200, cap));
        try {
            t.translate("Hello", "en", "zh-CN");
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("200"));
        } catch (Exception e) {
            fail("expected IOException, got " + e);
        }
    }

}
