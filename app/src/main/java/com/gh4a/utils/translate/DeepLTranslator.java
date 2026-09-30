package com.gh4a.utils.translate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * DeepL API translation. Requires an Auth Key from deepl.com.
 * Free-tier keys end with ":fx" and are automatically routed to the
 * free endpoint (api-free.deepl.com, 500k chars/month); all other keys
 * use the Pro endpoint.
 */
public class DeepLTranslator implements Translator {
    private static final String ENDPOINT_FREE = "https://api-free.deepl.com/v2/translate";
    private static final String ENDPOINT_PRO = "https://api.deepl.com/v2/translate";

    private final OkHttpClient mClient;
    private final String mAuthKey;

    public DeepLTranslator(OkHttpClient client, String authKey) {
        mClient = client;
        mAuthKey = authKey;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws Exception {
        if (mAuthKey == null || mAuthKey.isEmpty()) {
            throw new IOException("DeepL API key not configured");
        }
        String endpoint = mAuthKey.endsWith(":fx") ? ENDPOINT_FREE : ENDPOINT_PRO;

        FormBody.Builder form = new FormBody.Builder()
                .add("text", text)
                .add("target_lang", mapLang(targetLang));
        if (!"auto".equals(sourceLang)) {
            form.add("source_lang", mapLang(sourceLang));
        }
        RequestBody body = form.build();
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Authorization", "DeepL-Auth-Key " + mAuthKey)
                .post(body)
                .build();
        try (Response response = mClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("DeepL translate failed: " + response.code()
                        + " " + response.message());
            }
            JSONObject root = new JSONObject(response.body().string());
            JSONArray translations = root.optJSONArray("translations");
            if (translations == null || translations.length() == 0) {
                throw new IOException("DeepL translate returned empty result");
            }
            String result = translations.getJSONObject(0).optString("text", "");
            if (result.isEmpty()) {
                throw new IOException("DeepL translate returned empty result");
            }
            return result;
        }
    }

    private static String mapLang(String lang) {
        if (lang == null || lang.isEmpty()) {
            return "ZH";
        }
        if (lang.startsWith("zh")) {
            return "ZH";
        }
        return lang.substring(0, Math.min(2, lang.length())).toUpperCase();
    }

    @Override
    public String getName() {
        return "DeepL";
    }
}
