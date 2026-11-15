package com.gh4a.utils.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import okhttp3.OkHttpClient;

/**
 * Creates the configured {@link Translator} from app settings and
 * resolves the target language from the app language setting.
 */
public class TranslationManager {
    private static final String TAG = "TranslationManager";

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

    // ---------- 翻译凭据的明文存储（用户决定 2026-11-15） ----------
    //
    // API key/secret 直接存默认 SharedPreferences 明文，不再走
    // AndroidKeyStore 加密：真机上 KeyStore 密钥失效/生成失败导致保存
    // 永远失败（T-024），用户决定不用加密、就是明文。
    // 加密存储里若有旧密文残留，读时能解开的迁成明文照常用，解不开的丢弃
    //（迁移见 migrateCredentialsFromSecurePrefs；decryptCredential 仅供
    // 一次性迁移旧残留之用，不再加密任何新值）。

    private static final String CRED_CIPHER_PREFIX = "v1:";
    private static final String CRED_KEY_ALIAS = "translation-cred-key";

    private static SharedPreferences securePrefs(Context context) {
        return Gh4Application.securePrefs(context);
    }

    /** 仅读：取已有的凭据密钥（不存在或不可用返回 null；不生成新密钥）。 */
    private static SecretKey getExistingCredKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(CRED_KEY_ALIAS)) {
                return null;
            }
            java.security.Key key = ks.getKey(CRED_KEY_ALIAS, null);
            return key instanceof SecretKey ? (SecretKey) key : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCiphertext(String stored) {
        return stored != null && stored.startsWith(CRED_CIPHER_PREFIX);
    }

    /** 密文 → 明文（仅供一次性迁移旧残留；非密文、密钥缺失或解密失败返回 null）。 */
    private static String decryptCredential(String stored) {
        if (!isCiphertext(stored)) {
            return null;
        }
        try {
            byte[] raw = Base64.decode(
                    stored.substring(CRED_CIPHER_PREFIX.length()), Base64.DEFAULT);
            if (raw.length <= 12) {
                return null;
            }
            SecretKey key = getExistingCredKey();
            if (key == null) {
                return null;
            }
            byte[] iv = Arrays.copyOf(raw, 12);
            byte[] ct = Arrays.copyOfRange(raw, 12, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getStringSafe(SharedPreferences prefs, String key, String defValue) {
        try {
            Object v = prefs.getAll().get(key);
            return v instanceof String ? (String) v : defValue;
        } catch (Exception e) {
            return defValue;
        }
    }

    /** 读一条凭据（明文；无凭据返回 ""）。 */
    private static String getCredential(Context context, String key) {
        migrateCredentialsFromSecurePrefs(context);
        String plain = getStringSafe(prefs(context), key, "");
        if (isCiphertext(plain)) {
            // 旧版备份恢复的密文躺在默认 prefs 里：能解开就地转明文，
            // 解不开丢弃（不把 "v1:..." 当 API key 用）。
            String decrypted = decryptCredential(plain);
            if (decrypted != null && !decrypted.isEmpty()) {
                prefs(context).edit().putString(key, decrypted).apply();
                return decrypted;
            }
            prefs(context).edit().remove(key).apply();
            return "";
        }
        return plain;
    }

    /**
     * 写一条凭据（明文存默认 prefs；空值则删除）。恒返回 true——默认
     * prefs 写入不走 KeyStore，不会失败。
     */
    private static boolean setCredential(Context context, String key, String value) {
        migrateCredentialsFromSecurePrefs(context);
        SharedPreferences.Editor editor = prefs(context).edit();
        if (value == null || value.isEmpty()) {
            editor.remove(key);
        } else {
            editor.putString(key, value);
        }
        editor.apply();
        // 删掉加密存储里同 key 的旧密文残留（不删它，旧密文躺在那里无意义；
        // 尽力而为，删不掉也不影响明文读写）。
        try {
            securePrefs(context).edit().remove(key).apply();
        } catch (Exception ignored) {
            // secure prefs 不可用时不影响明文读写。
        }
        return true;
    }

    /** 某 provider 的 API key（解密后）。 */
    public static String getApiKey(Context context, String provider) {
        return getCredential(context, apiKeyPrefKey(provider));
    }

    public static boolean setApiKey(Context context, String provider, String value) {
        return setCredential(context, apiKeyPrefKey(provider), value);
    }

    /** 某 provider 的 API secret（解密后）。 */
    public static String getApiSecret(Context context, String provider) {
        return getCredential(context, apiSecretPrefKey(provider));
    }

    public static boolean setApiSecret(Context context, String provider, String value) {
        return setCredential(context, apiSecretPrefKey(provider), value);
    }

    /**
     * 一次性迁移：加密存储里的旧凭据 → 默认 prefs 明文（用户决定
     * 2026-11-15：翻译凭据不再加密）。能解开的密文迁成明文；解不开的
     * 丢弃；默认 prefs 里已有明文时旧密文直接删。幂等，可反复调。
     */
    public static void migrateCredentialsFromSecurePrefs(Context context) {
        SharedPreferences secure = securePrefs(context);
        SharedPreferences def = prefs(context);
        // 先迁各 provider 的独立键（优先于共享键，与旧版迁移逻辑一致）。
        for (String provider : new String[]{PROVIDER_YOUDAO, PROVIDER_BAIDU, PROVIDER_DEEPL}) {
            migrateOneCredentialFromSecure(secure, def,
                    apiKeyPrefKey(provider), apiKeyPrefKey(provider));
            migrateOneCredentialFromSecure(secure, def,
                    apiSecretPrefKey(provider), apiSecretPrefKey(provider));
        }
        // 再迁旧版共享键（无 provider 后缀），归入当前 provider。
        String provider = getProvider(context);
        migrateOneCredentialFromSecure(secure, def, KEY_API_KEY, apiKeyPrefKey(provider));
        migrateOneCredentialFromSecure(secure, def, KEY_API_SECRET, apiSecretPrefKey(provider));
    }

    private static void migrateOneCredentialFromSecure(SharedPreferences secure,
            SharedPreferences def, String fromKey, String toKey) {
        String stored = getStringSafe(secure, fromKey, "");
        if (stored.isEmpty()) {
            return;
        }
        if (!getStringSafe(def, toKey, "").isEmpty()) {
            // 默认 prefs 里已有明文，旧密文直接删。
            secure.edit().remove(fromKey).apply();
            return;
        }
        String plain = stored;
        if (isCiphertext(stored)) {
            plain = decryptCredential(stored);
            if (plain == null || plain.isEmpty()) {
                // 解不开，丢弃（记一条日志，否则无从排查）。
                Log.w(TAG, "Discarding undecryptable legacy credential for " + fromKey);
                secure.edit().remove(fromKey).apply();
                return;
            }
        }
        def.edit().putString(toKey, plain).apply();
        secure.edit().remove(fromKey).apply();
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
        List<String> result = new ArrayList<>();
        result.add(PROVIDER_GOOGLE);
        if (hasKeyAndSecret(context, PROVIDER_YOUDAO)) {
            result.add(PROVIDER_YOUDAO);
        }
        if (hasKeyAndSecret(context, PROVIDER_BAIDU)) {
            result.add(PROVIDER_BAIDU);
        }
        if (!TextUtils.isEmpty(getApiKey(context, PROVIDER_DEEPL))) {
            result.add(PROVIDER_DEEPL);
        }
        return result;
    }

    private static boolean hasKeyAndSecret(Context context, String provider) {
        return !TextUtils.isEmpty(getApiKey(context, provider))
                && !TextUtils.isEmpty(getApiSecret(context, provider));
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
        if (provider == null) {
            // T-108：null provider 时 switch 直接 NPE；按 default 分支走 Google（无需凭据）。
            provider = PROVIDER_GOOGLE;
        }
        SharedPreferences prefs = prefs(context);
        String url = prefs.getString(urlPrefKey(provider), "");
        String apiKey = getApiKey(context, provider);
        String apiSecret = getApiSecret(context, provider);
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
     * NOTE: the in-app language lives in SettingsFragment.PREF_NAME ("Gh4a-pref"),
     * not in the default shared prefs used for translation provider settings.
     */
    public static String getTargetLanguage(Context context) {
        SharedPreferences appPrefs =
                context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        String appLang = appPrefs.getString(SettingsFragment.KEY_LANGUAGE, "");
        if (TextUtils.isEmpty(appLang)) {
            appLang = Locale.getDefault().getLanguage();
        }
        return appLang.startsWith("zh") ? "zh-CN" : "en";
    }
}
