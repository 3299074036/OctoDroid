package com.gh4a.utils.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Base64;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

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

    // ---------- 翻译凭据的加密存储（S-H2） ----------
    //
    // API key/secret 不再存默认 SharedPreferences 明文，改存加密存储
    //（Gh4Application.getSecurePrefs()，与 GitHub token 同款）；
    // 且存入前先用本类的 AES-GCM 再包一层，存的值本身就是密文
    //（"v1:" + Base64(IV || 密文)）。这样备份导出时直接取存的值即是
    // 密文，不必解密再导出，备份文件里永不出现明文凭据。
    // 密钥存在 AndroidKeyStore（别名 translation-cred-key），卸载应用
    // 后密钥丢失、已导出的密文无法解密，属预期行为（与 token 永不备份一致）。

    private static final String CRED_CIPHER_PREFIX = "v1:";
    private static final String CRED_KEY_ALIAS = "translation-cred-key";

    private static SharedPreferences securePrefs(Context context) {
        return Gh4Application.securePrefs(context);
    }

    private static SecretKey getOrCreateCredKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(CRED_KEY_ALIAS)) {
            java.security.Key key = ks.getKey(CRED_KEY_ALIAS, null);
            if (key instanceof SecretKey) {
                return (SecretKey) key;
            }
        }
        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(CRED_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    /** 明文 → 密文（失败返回 null，调用方不删旧值）。 */
    private static String encryptCredential(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        try {
            SecretKey key = getOrCreateCredKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return CRED_CIPHER_PREFIX + Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isCiphertext(String stored) {
        return stored != null && stored.startsWith(CRED_CIPHER_PREFIX);
    }

    /** 密文 → 明文（非密文或解密失败返回 null）。 */
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
            byte[] iv = Arrays.copyOf(raw, 12);
            byte[] ct = Arrays.copyOfRange(raw, 12, raw.length);
            SecretKey key = getOrCreateCredKey();
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

    /** 读一条凭据（解密后返回明文；无凭据或解密失败返回 ""）。 */
    private static String getCredential(Context context, String key) {
        migrateCredentialsFromDefaultPrefs(context);
        String stored = getStringSafe(securePrefs(context), key, "");
        if (stored.isEmpty()) {
            return "";
        }
        if (isCiphertext(stored)) {
            String plain = decryptCredential(stored);
            return plain != null ? plain : "";
        }
        // 旧版备份恢复的明文：就地加密存回，解密失败也不丢值。
        String cipher = encryptCredential(stored);
        if (cipher != null) {
            securePrefs(context).edit().putString(key, cipher).apply();
        }
        return stored;
    }

    /** 写一条凭据（加密后存；空值则删除）。 */
    private static void setCredential(Context context, String key, String value) {
        migrateCredentialsFromDefaultPrefs(context);
        SharedPreferences.Editor editor = securePrefs(context).edit();
        if (value == null || value.isEmpty()) {
            editor.remove(key);
        } else {
            String cipher = encryptCredential(value);
            if (cipher == null) {
                return;
            }
            editor.putString(key, cipher);
        }
        editor.apply();
        // 旧版明文残留（若有）一并清掉。
        prefs(context).edit().remove(key).apply();
    }

    /** 某 provider 的 API key（解密后）。 */
    public static String getApiKey(Context context, String provider) {
        return getCredential(context, apiKeyPrefKey(provider));
    }

    public static void setApiKey(Context context, String provider, String value) {
        setCredential(context, apiKeyPrefKey(provider), value);
    }

    /** 某 provider 的 API secret（解密后）。 */
    public static String getApiSecret(Context context, String provider) {
        return getCredential(context, apiSecretPrefKey(provider));
    }

    public static void setApiSecret(Context context, String provider, String value) {
        setCredential(context, apiSecretPrefKey(provider), value);
    }

    /**
     * 存着的密文本身（供备份导出：直接取存的值即是密文，不解密）。
     * 空表示该 provider 未配凭据。
     */
    public static String getStoredApiKeyCiphertext(Context context, String provider) {
        migrateCredentialsFromDefaultPrefs(context);
        String stored = getStringSafe(securePrefs(context), apiKeyPrefKey(provider), "");
        return isCiphertext(stored) ? stored : "";
    }

    public static String getStoredApiSecretCiphertext(Context context, String provider) {
        migrateCredentialsFromDefaultPrefs(context);
        String stored = getStringSafe(securePrefs(context), apiSecretPrefKey(provider), "");
        return isCiphertext(stored) ? stored : "";
    }

    /**
     * 一次性迁移：默认 prefs 里的明文凭据 → 加密存储（密文），成功后
     * 删默认 prefs 里的明文。加密失败（KeyStore 不可用）时不动明文，
     * 下次再试，不丢用户已填的凭据。幂等，可反复调。
     */
    public static void migrateCredentialsFromDefaultPrefs(Context context) {
        SharedPreferences def = prefs(context);
        SharedPreferences secure = securePrefs(context);
        // 先迁各 provider 的独立键（优先于共享键，与旧版迁移逻辑一致）。
        for (String provider : new String[]{PROVIDER_YOUDAO, PROVIDER_BAIDU, PROVIDER_DEEPL}) {
            migrateOneCredential(def, secure, apiKeyPrefKey(provider), apiKeyPrefKey(provider));
            migrateOneCredential(def, secure, apiSecretPrefKey(provider), apiSecretPrefKey(provider));
        }
        // 再迁旧版共享键（无 provider 后缀），归入当前 provider。
        String provider = getProvider(context);
        migrateOneCredential(def, secure, KEY_API_KEY, apiKeyPrefKey(provider));
        migrateOneCredential(def, secure, KEY_API_SECRET, apiSecretPrefKey(provider));
    }

    private static void migrateOneCredential(SharedPreferences def, SharedPreferences secure,
            String fromKey, String toKey) {
        String value = getStringSafe(def, fromKey, "");
        if (value.isEmpty()) {
            return;
        }
        if (!getStringSafe(secure, toKey, "").isEmpty()) {
            // 加密存储里已有值，明文副本直接删。
            def.edit().remove(fromKey).apply();
            return;
        }
        String cipher = encryptCredential(value);
        if (cipher == null) {
            return;
        }
        secure.edit().putString(toKey, cipher).apply();
        def.edit().remove(fromKey).apply();
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
