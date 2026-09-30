package com.gh4a.utils.translate;

import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Baidu Translate (百度翻译开放平台) machine translation.
 * Requires APP ID + secret key. Standard edition is free but limited
 * to ~1 request per second, so requests are throttled.
 * See https://fanyi-api.baidu.com/product/113
 */
public class BaiduTranslator implements Translator {
    private static final String ENDPOINT = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    // Baidu standard edition: max ~1 QPS
    private static final Object RATE_LOCK = new Object();
    private static final long MIN_INTERVAL_MS = 1100;
    private static long sLastRequestMs = 0;

    private final OkHttpClient mClient;
    private final String mAppId;
    private final String mSecretKey;

    public BaiduTranslator(OkHttpClient client, String appId, String secretKey) {
        mClient = client;
        mAppId = appId;
        mSecretKey = secretKey;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws Exception {
        if (isEmpty(mAppId) || isEmpty(mSecretKey)) {
            throw new IOException("Baidu APP ID/secret key not configured");
        }
        throttle();
        String salt = UUID.randomUUID().toString().replace("-", "");
        String sign = md5(mAppId + text + salt + mSecretKey);

        RequestBody body = new FormBody.Builder()
                .add("q", text)
                .add("from", "auto")
                .add("to", mapLang(targetLang))
                .add("appid", mAppId)
                .add("salt", salt)
                .add("sign", sign)
                .build();
        Request request = new Request.Builder()
                .url(ENDPOINT)
                .post(body)
                .build();
        try (Response response = mClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Baidu translate failed: " + response.code());
            }
            JSONObject root = new JSONObject(response.body().string());
            if (root.has("error_code")) {
                throw new IOException("Baidu error " + root.optString("error_code")
                        + ": " + root.optString("error_msg"));
            }
            JSONArray results = root.optJSONArray("trans_result");
            if (results == null || results.length() == 0) {
                throw new IOException("Baidu translate returned empty result");
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.length(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(results.getJSONObject(i).optString("dst", ""));
            }
            String result = sb.toString();
            if (result.isEmpty()) {
                throw new IOException("Baidu translate returned empty result");
            }
            return result;
        }
    }

    /** Keep at most ~1 request per second (Baidu standard edition limit). */
    private static void throttle() {
        synchronized (RATE_LOCK) {
            long elapsed = SystemClock.elapsedRealtime() - sLastRequestMs;
            if (elapsed < MIN_INTERVAL_MS) {
                try {
                    Thread.sleep(MIN_INTERVAL_MS - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            sLastRequestMs = SystemClock.elapsedRealtime();
        }
    }

    private static String mapLang(String lang) {
        if (lang == null || lang.isEmpty()) {
            return "zh";
        }
        if (lang.startsWith("zh")) {
            return "zh";
        }
        // Baidu uses non-standard codes for some languages
        if (lang.startsWith("ja")) {
            return "jp";
        }
        if (lang.startsWith("ko")) {
            return "kor";
        }
        return lang.length() >= 2 ? lang.substring(0, 2) : lang;
    }

    private static String md5(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public String getName() {
        return "Baidu";
    }

    @Override
    public int getMaxParallelRequests() {
        // Baidu free tier is ~1 QPS; parallel requests get throttled (54003)
        return 1;
    }
}
