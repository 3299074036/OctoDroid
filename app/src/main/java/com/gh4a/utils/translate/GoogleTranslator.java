package com.gh4a.utils.translate;

import android.net.Uri;

import org.json.JSONArray;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Google Translate via the free (unofficial) endpoint. No API key required.
 * Note: may be blocked in some regions.
 */
public class GoogleTranslator implements Translator {
    private static final String ENDPOINT =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s";

    private final OkHttpClient mClient;

    public GoogleTranslator(OkHttpClient client) {
        mClient = client;
    }

    @Override
    public String translate(String text, String sourceLang, String targetLang) throws Exception {
        String url = String.format(ENDPOINT, sourceLang, targetLang, Uri.encode(text));
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build();
        try (Response response = mClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Google translate failed: " + response.code());
            }
            String body = response.body().string();
            // Response: [[["translated","original",...],...],null,"en",...]
            JSONArray root = new JSONArray(body);
            JSONArray sentences = root.getJSONArray(0);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sentences.length(); i++) {
                JSONArray sentence = sentences.getJSONArray(i);
                if (!sentence.isNull(0)) {
                    sb.append(sentence.getString(0));
                }
            }
            String result = sb.toString();
            if (result.isEmpty()) {
                throw new IOException("Google translate returned empty result");
            }
            return result;
        }
    }

    @Override
    public String getName() {
        return "Google";
    }
}
