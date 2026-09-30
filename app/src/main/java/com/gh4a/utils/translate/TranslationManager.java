package com.gh4a.utils.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.gh4a.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Creates the configured {@link Translator} from app settings and
 * resolves the target language from the app language setting.
 */
public class TranslationManager {
    public static final String KEY_PROVIDER = "translation_provider";
    public static final String KEY_URL = "translation_url";
    public static final String KEY_API_KEY = "translation_api_key";
    public static final String KEY_API_SECRET = "translation_api_secret";
    public static final String KEY_REGION = "translation_region";

    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_YOUDAO = "youdao";
    public static final String PROVIDER_BAIDU = "baidu";
    public static final String PROVIDER_DEEPL = "deepl";

    private static OkHttpClient sClient;

    private static synchronized OkHttpClient getClient() {
        if (sClient == null) {
            sClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return sClient;
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Storage key for the API key / AppKey of a provider.
     * Credentials are isolated per provider so switching providers
     * no longer shows another provider's values.
     */
    public static String apiKeyPrefKey(String provider) {
        return KEY_API_KEY + "_" + provider;
    }

    /** Storage key for the API secret of a provider (isolated per provider). */
    public static String apiSecretPrefKey(String provider) {
        return KEY_API_SECRET + "_" + provider;
    }

    /**
     * Storage key for the service endpoint of a provider (isolated per
     * provider).
     */
    public static String urlPrefKey(String provider) {
        return KEY_URL + "_" + provider;
    }

    /** Build the translator selected in settings. Never returns null. */
    public static Translator createTranslator(Context context) {
        return createTranslator(context, getProvider(context));
    }

    /** Currently selected provider id. */
    public static String getProvider(Context context) {
        return prefs(context).getString(KEY_PROVIDER, PROVIDER_GOOGLE);
    }

    /** Persist the selected provider. */
    public static void setProvider(Context context, String provider) {
        prefs(context).edit().putString(KEY_PROVIDER, provider).apply();
    }

    /**
     * Providers that are ready to translate right now: Google needs no
     * credentials, the others only when their required key/secret fields
     * are filled in.
     */
    public static List<String> getConfiguredProviders(Context context) {
        SharedPreferences prefs = prefs(context);
        List<String> result = new ArrayList<>();
        result.add(PROVIDER_GOOGLE);
        if (hasKeyAndSecret(prefs, PROVIDER_YOUDAO)) {
            result.add(PROVIDER_YOUDAO);
        }
        if (hasKeyAndSecret(prefs, PROVIDER_BAIDU)) {
            result.add(PROVIDER_BAIDU);
        }
        if (!TextUtils.isEmpty(prefs.getString(apiKeyPrefKey(PROVIDER_DEEPL), ""))) {
            result.add(PROVIDER_DEEPL);
        }
        return result;
    }

    private static boolean hasKeyAndSecret(SharedPreferences prefs, String provider) {
        return !TextUtils.isEmpty(prefs.getString(apiKeyPrefKey(provider), ""))
                && !TextUtils.isEmpty(prefs.getString(apiSecretPrefKey(provider), ""));
    }

    /** Display name of a provider, matching the settings list. */
    public static String getProviderDisplayName(Context context, String provider) {
        String[] values = context.getResources()
                .getStringArray(R.array.translation_provider_values);
        String[] items = context.getResources()
                .getStringArray(R.array.translation_provider_items);
        for (int i = 0; i < values.length && i < items.length; i++) {
            if (values[i].equals(provider)) {
                return items[i];
            }
        }
        return provider;
    }

    /** Build the translator for the given provider. Never returns null. */
    public static Translator createTranslator(Context context, String provider) {
        SharedPreferences prefs = prefs(context);
        String url = prefs.getString(urlPrefKey(provider), "");
        String apiKey = prefs.getString(apiKeyPrefKey(provider), "");
        String apiSecret = prefs.getString(apiSecretPrefKey(provider), "");
        String region = prefs.getString(KEY_REGION, "");
        OkHttpClient client = getClient();

        switch (provider) {
            case PROVIDER_YOUDAO:
                return new YoudaoTranslator(client, url, apiKey, apiSecret);
            case PROVIDER_BAIDU:
                return new BaiduTranslator(client, apiKey, apiSecret);
            case PROVIDER_DEEPL:
                return new DeepLTranslator(client, apiKey);
            case PROVIDER_GOOGLE:
            default:
                return new GoogleTranslator(client);
        }
    }

    /**
     * Target language for translation, derived from the app language setting:
     * Chinese -> zh-CN, otherwise en.
     */
    public static String getTargetLanguage(Context context) {
        String appLang = prefs(context).getString("language", "");
        if (TextUtils.isEmpty(appLang)) {
            appLang = Locale.getDefault().getLanguage();
        }
        return appLang.startsWith("zh") ? "zh-CN" : "en";
    }

    /** Human-readable summary of the current provider config, for settings. */
    public static String getProviderSummary(Context context) {
        SharedPreferences prefs = prefs(context);
        String provider = prefs.getString(KEY_PROVIDER, PROVIDER_GOOGLE);
        switch (provider) {
            case PROVIDER_YOUDAO:
                return "有道";
            case PROVIDER_BAIDU:
                return "百度翻译";
            case PROVIDER_DEEPL:
                return "DeepL";
            case PROVIDER_GOOGLE:
            default:
                return "Google";
        }
    }
}
