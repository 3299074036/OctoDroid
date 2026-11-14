package com.gh4a.utils.translate;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import okhttp3.FormBody;
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
}
