package com.gh4a.utils.translate;

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
 * Youdao (有道智云) machine translation. Requires appKey and appSecret.
 * See https://ai.youdao.com/DOCSIR/ZH/guide.html
 */
public class YoudaoTranslator implements Translator {
    private static final String DEFAULT_ENDPOINT = "https://openapi.youdao.com/api";

    private final OkHttpClient mClient;
    private final String mEndpoint;
    private final String mAppKey;
    private final String mAppSecret;

    public YoudaoTranslator(OkHttpClient client, String url, String appKey, String appSecret) {
        mClient = client;
        mEndpoint = (url == null || url.isEmpty()) ? DEFAULT_ENDPOINT : url;
        mAppKey = appKey;
        mAppSecret = appSecret;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws Exception {
        if (mAppKey == null || mAppKey.isEmpty() || mAppSecret == null || mAppSecret.isEmpty()) {
            throw new IOException("Youdao appKey/appSecret not configured");
        }
        String from = "auto".equals(sourceLang) ? "auto" : mapLang(sourceLang);
        String to = mapLang(targetLang);
        String salt = UUID.randomUUID().toString();
        String curtime = String.valueOf(System.currentTimeMillis() / 1000);
        String sign = sha256(mAppKey + truncate(text) + salt + curtime + mAppSecret);

        RequestBody body = new FormBody.Builder()
                .add("q", text)
                .add("from", from)
                .add("to", to)
                .add("appKey", mAppKey)
                .add("salt", salt)
                .add("signType", "v3")
                .add("curtime", curtime)
                .add("sign", sign)
                .build();
        Request request = new Request.Builder()
                .url(mEndpoint)
                .post(body)
                .build();
        try (Response response = mClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Youdao translate failed: " + response.code());
            }
            JSONObject root = new JSONObject(response.body().string());
            String errorCode = root.optString("errorCode", "");
            if (!"0".equals(errorCode)) {
                throw new IOException("Youdao error: " + errorCode);
            }
            JSONArray translation = root.optJSONArray("translation");
            if (translation == null || translation.length() == 0) {
                throw new IOException("Youdao translate returned empty result");
            }
            return translation.getString(0);
        }
    }

    private static String mapLang(String lang) {
        if (lang.startsWith("zh")) {
            return "zh-CHS";
        }
        return lang;
    }

    private static String truncate(String q) {
        if (q == null) {
            return "";
        }
        int len = q.length();
        if (len <= 20) {
            return q;
        }
        return q.substring(0, 10) + len + q.substring(len - 10);
    }

    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public String getName() {
        return "Youdao";
    }
}
