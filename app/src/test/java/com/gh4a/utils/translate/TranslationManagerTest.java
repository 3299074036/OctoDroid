package com.gh4a.utils.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.gh4a.Gh4Application;
import com.gh4a.fragment.SettingsFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.security.Security;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TranslationManager: preference keys, provider selection, target language
 * and translator factory. Credentials are stored as plaintext in the
 * default SharedPreferences (user decision 2026-11-15: no encryption);
 * the fake KeyStore below exists only so the one-time migration of legacy
 * ciphertext can be tested.
 */
@RunWith(RobolectricTestRunner.class)
public class TranslationManagerTest {

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    // ---- Fake AndroidKeyStore provider (T-109) ----
    //
    // Robolectric's JVM has no "AndroidKeyStore" provider. This in-memory
    // stand-in exists only so the legacy-ciphertext migration
    // (decryptCredential) can run for real through the production code:
    // aliases survive across KeyStore instances via a static map, and
    // tests put keys in directly. Production code never generates keys
    // anymore, so there is no fake KeyGenerator.

    public static class FakeKeyStoreSpi extends java.security.KeyStoreSpi {
        static final Map<String, java.security.Key> KEYS = new HashMap<>();

        static void clear() {
            KEYS.clear();
        }

        static void put(String alias, java.security.Key key) {
            KEYS.put(alias, key);
        }

        @Override
        public java.security.Key engineGetKey(String alias, char[] password) {
            return KEYS.get(alias);
        }

        @Override
        public void engineSetKeyEntry(String alias, java.security.Key key,
                char[] password, java.security.cert.Certificate[] chain) {
            KEYS.put(alias, key);
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key,
                java.security.cert.Certificate[] chain) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineSetCertificateEntry(String alias,
                java.security.cert.Certificate cert) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void engineDeleteEntry(String alias) {
            KEYS.remove(alias);
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return KEYS.containsKey(alias);
        }

        @Override
        public java.util.Enumeration<String> engineAliases() {
            return java.util.Collections.enumeration(KEYS.keySet());
        }

        @Override
        public int engineSize() {
            return KEYS.size();
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return KEYS.containsKey(alias);
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return false;
        }

        @Override
        public java.security.cert.Certificate engineGetCertificate(String alias) {
            return null;
        }

        @Override
        public String engineGetCertificateAlias(java.security.cert.Certificate cert) {
            return null;
        }

        @Override
        public java.security.cert.Certificate[] engineGetCertificateChain(String alias) {
            return null;
        }

        @Override
        public java.util.Date engineGetCreationDate(String alias) {
            return new java.util.Date();
        }

        @Override
        public void engineLoad(java.io.InputStream stream, char[] password) {
            // in-memory: nothing to load
        }

        @Override
        public void engineStore(java.io.OutputStream stream, char[] password) {
            // in-memory: nothing to store
        }
    }

    public static class FakeAndroidKeyStoreProvider extends java.security.Provider {
        FakeAndroidKeyStoreProvider() {
            super("AndroidKeyStore", 1.0, "Fake AndroidKeyStore for unit tests");
            put("KeyStore.AndroidKeyStore", FakeKeyStoreSpi.class.getName());
        }
    }

    private void installFakeKeyStore() {
        if (Security.getProvider("AndroidKeyStore") == null) {
            Security.addProvider(new FakeAndroidKeyStoreProvider());
        }
    }

    private void removeFakeKeyStore() {
        Security.removeProvider("AndroidKeyStore");
        FakeKeyStoreSpi.clear();
    }

    private SharedPreferences defPrefs() {
        return PreferenceManager.getDefaultSharedPreferences(context());
    }

    private SharedPreferences securePrefs() {
        return Gh4Application.securePrefs(context());
    }

    private SharedPreferences appPrefs() {
        return context().getSharedPreferences(SettingsFragment.PREF_NAME,
                Context.MODE_PRIVATE);
    }

    @Before
    public void setUp() {
        defPrefs().edit().clear().apply();
        securePrefs().edit().clear().apply();
        appPrefs().edit().clear().apply();
    }

    @After
    public void tearDown() {
        removeFakeKeyStore();
        defPrefs().edit().clear().apply();
        securePrefs().edit().clear().apply();
        appPrefs().edit().clear().apply();
    }

    private static String b64(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    public void prefKeys_arePerProvider() {
        assertEquals("translation_api_key_google",
                TranslationManager.apiKeyPrefKey("google"));
        assertEquals("translation_api_secret_baidu",
                TranslationManager.apiSecretPrefKey("baidu"));
        assertEquals("translation_url_deepl",
                TranslationManager.urlPrefKey("deepl"));
    }

    @Test
    public void getProvider_defaultsToGoogle() {
        assertEquals("google", TranslationManager.getProvider(context()));
    }

    @Test
    public void setProvider_roundTrips() {
        TranslationManager.setProvider(context(), "deepl");
        assertEquals("deepl", TranslationManager.getProvider(context()));
        TranslationManager.setProvider(context(), "google");
        assertEquals("google", TranslationManager.getProvider(context()));
    }

    @Test
    public void getConfiguredProviders_defaultIsGoogleOnly() {
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertEquals(1, providers.size());
        assertEquals("google", providers.get(0));
    }

    @Test
    public void getProviderDisplayName_knownProvider() {
        assertEquals("Google",
                TranslationManager.getProviderDisplayName(context(), "google"));
        assertEquals("DeepL",
                TranslationManager.getProviderDisplayName(context(), "deepl"));
    }

    @Test
    public void getProviderDisplayName_unknownReturnsId() {
        assertEquals("nope",
                TranslationManager.getProviderDisplayName(context(), "nope"));
    }

    @Test
    public void createTranslator_googleByDefault() {
        assertTrue(TranslationManager.createTranslator(context())
                instanceof GoogleTranslator);
    }

    @Test
    public void createTranslator_eachProviderReturnsRightType() {
        assertTrue(TranslationManager.createTranslator(context(), "google")
                instanceof GoogleTranslator);
        assertTrue(TranslationManager.createTranslator(context(), "youdao")
                instanceof YoudaoTranslator);
        assertTrue(TranslationManager.createTranslator(context(), "baidu")
                instanceof BaiduTranslator);
        assertTrue(TranslationManager.createTranslator(context(), "deepl")
                instanceof DeepLTranslator);
    }

    @Test
    public void createTranslator_unknownProviderFallsBackToGoogle() {
        assertTrue(TranslationManager.createTranslator(context(), "nope")
                instanceof GoogleTranslator);
    }

    @Test
    public void createTranslator_nullProvider_returnsGoogle() {
        // T-108：null provider 时 switch 直接 NPE；按 default 分支走 Google。
        assertTrue(TranslationManager.createTranslator(context(), null)
                instanceof GoogleTranslator);
    }

    @Test
    public void getTargetLanguage_chineseAppLangMapsToZhCn() {
        context().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(SettingsFragment.KEY_LANGUAGE, "zh").apply();
        assertEquals("zh-CN", TranslationManager.getTargetLanguage(context()));
    }

    @Test
    public void getTargetLanguage_zhTwAlsoMapsToZhCn() {
        context().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(SettingsFragment.KEY_LANGUAGE, "zh-TW").apply();
        assertEquals("zh-CN", TranslationManager.getTargetLanguage(context()));
    }

    @Test
    public void getTargetLanguage_nonChineseMapsToEn() {
        context().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(SettingsFragment.KEY_LANGUAGE, "en").apply();
        assertEquals("en", TranslationManager.getTargetLanguage(context()));
        context().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(SettingsFragment.KEY_LANGUAGE, "fr").apply();
        assertEquals("en", TranslationManager.getTargetLanguage(context()));
    }

    @Test
    public void getApiKey_unconfiguredReturnsEmpty() {
        assertEquals("", TranslationManager.getApiKey(context(), "deepl"));
        assertEquals("", TranslationManager.getApiSecret(context(), "baidu"));
    }

    @Test
    public void setApiKey_emptyValueDeletesAndReturnsTrue() {
        assertTrue(TranslationManager.setApiKey(context(), "deepl", ""));
        assertTrue(TranslationManager.setApiSecret(context(), "deepl", null));
        assertEquals("", TranslationManager.getApiKey(context(), "deepl"));
    }

    @Test
    public void setApiKey_withoutKeyStore_savesPlaintext() {
        // 明文存储不再走 KeyStore：没有 AndroidKeyStore provider 时保存
        // 也必须成功，值以明文落在默认 prefs，不进加密存储。
        assertTrue(TranslationManager.setApiKey(context(), "deepl", "test-key"));
        assertEquals("test-key", TranslationManager.getApiKey(context(), "deepl"));
        assertEquals("test-key", defPrefs().getString(
                TranslationManager.apiKeyPrefKey("deepl"), ""));
        assertFalse(securePrefs().contains(TranslationManager.apiKeyPrefKey("deepl")));
    }

    @Test
    public void configuredProviders_deeplIncludedWithoutKeyStore() {
        assertTrue(TranslationManager.setApiKey(context(), "deepl", "test-key"));
        assertTrue(TranslationManager.setApiSecret(context(), "deepl", "test-secret"));
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertEquals(2, providers.size());
        assertTrue(providers.contains("google"));
        assertTrue(providers.contains("deepl"));
    }

    @Test
    public void implicitConstructor_instantiatesReflectively() throws Exception {
        java.lang.reflect.Constructor<TranslationManager> ctor =
                TranslationManager.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    @Test
    public void isCiphertext_nullReturnsFalse() throws Exception {
        java.lang.reflect.Method m = TranslationManager.class.getDeclaredMethod(
                "isCiphertext", String.class);
        m.setAccessible(true);
        assertFalse((Boolean) m.invoke(null, (String) null));
        assertFalse((Boolean) m.invoke(null, "plain"));
        assertTrue((Boolean) m.invoke(null, "v1:abc"));
    }

    @Test
    public void decryptCredential_nonCiphertextReturnsNull() throws Exception {
        java.lang.reflect.Method m = TranslationManager.class.getDeclaredMethod(
                "decryptCredential", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, (String) null));
        assertNull(m.invoke(null, "plain"));
    }

    @Test
    public void getStringSafe_exceptionReturnsDefault() throws Exception {
        SharedPreferences throwing = mock(SharedPreferences.class);
        when(throwing.getAll()).thenThrow(new RuntimeException("boom"));
        java.lang.reflect.Method m = TranslationManager.class.getDeclaredMethod(
                "getStringSafe", SharedPreferences.class, String.class, String.class);
        m.setAccessible(true);
        assertEquals("def", m.invoke(null, throwing, "k", "def"));
    }

    @Test
    public void getTargetLanguage_emptyPrefFallsBackToLocale() {
        assertEquals("en", TranslationManager.getTargetLanguage(context()));
    }

    @Test
    public void credentialRoundTrip_plaintextInDefaultPrefs() {
        assertTrue(TranslationManager.setApiKey(context(), "deepl", "test-key"));
        assertTrue(TranslationManager.setApiSecret(context(), "deepl", "test-secret"));
        assertEquals("test-key", TranslationManager.getApiKey(context(), "deepl"));
        assertEquals("test-secret", TranslationManager.getApiSecret(context(), "deepl"));
        assertEquals("test-key", defPrefs().getString(
                TranslationManager.apiKeyPrefKey("deepl"), ""));
        assertEquals("test-secret", defPrefs().getString(
                TranslationManager.apiSecretPrefKey("deepl"), ""));
        assertFalse(securePrefs().contains(TranslationManager.apiKeyPrefKey("deepl")));
        assertFalse(securePrefs().contains(TranslationManager.apiSecretPrefKey("deepl")));
    }

    @Test
    public void setApiKey_succeedsWithBrokenKeyUnderAlias() {
        installFakeKeyStore();
        // T-024 的场景：别名下躺着一把算法错误的密钥（真机密钥失效时
        // Cipher.init 抛 InvalidKeyException）；明文存储根本不碰
        // KeyStore，保存必须成功、读回正确。
        FakeKeyStoreSpi.put("translation-cred-key",
                new javax.crypto.spec.SecretKeySpec(new byte[16], "DES"));
        assertTrue(TranslationManager.setApiKey(context(), "youdao", "youdao-key"));
        assertTrue(TranslationManager.setApiSecret(context(), "youdao", "youdao-secret"));
        assertEquals("youdao-key", TranslationManager.getApiKey(context(), "youdao"));
        assertEquals("youdao-secret", TranslationManager.getApiSecret(context(), "youdao"));
    }

    @Test
    public void decryptCredential_shortCiphertextReturnsEmpty() {
        securePrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("deepl"), "v1:" + b64(new byte[5]))
                .apply();
        assertEquals("", TranslationManager.getApiKey(context(), "deepl"));
    }

    @Test
    public void decryptCredential_invalidBase64ReturnsEmpty() {
        securePrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("deepl"), "v1:!!!").apply();
        assertEquals("", TranslationManager.getApiKey(context(), "deepl"));
    }

    @Test
    public void staleCiphertextInSecurePrefs_doesNotShadowNewPlaintext() {
        assertTrue(TranslationManager.setApiKey(context(), "deepl", "test-key"));
        // 加密存储里躺着解不开的旧密文残留（32 随机字节，有效 Base64 但
        // 不是任何密钥的 GCM 密文）：读回的必须是新明文，旧密文被清掉。
        byte[] garbage = new byte[32];
        new java.security.SecureRandom().nextBytes(garbage);
        securePrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("deepl"), "v1:" + b64(garbage)).apply();
        assertEquals("test-key", TranslationManager.getApiKey(context(), "deepl"));
        assertFalse(securePrefs().contains(TranslationManager.apiKeyPrefKey("deepl")));
    }

    @Test
    public void getCredential_plaintextInSecurePrefs_migratesToDefaultPrefs() {
        securePrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("deepl"), "legacy-plain").apply();
        assertEquals("legacy-plain", TranslationManager.getApiKey(context(), "deepl"));
        assertEquals("legacy-plain", defPrefs().getString(
                TranslationManager.apiKeyPrefKey("deepl"), ""));
        assertFalse(securePrefs().contains(TranslationManager.apiKeyPrefKey("deepl")));
    }

    @Test
    public void getCredential_readsPlaintextFromDefaultPrefs() {
        defPrefs().edit()
                .putString(TranslationManager.apiKeyPrefKey("deepl"), "plain-key")
                .putString(TranslationManager.apiSecretPrefKey("baidu"), "plain-secret")
                .apply();
        assertEquals("plain-key", TranslationManager.getApiKey(context(), "deepl"));
        assertEquals("plain-secret", TranslationManager.getApiSecret(context(), "baidu"));
        // 默认 prefs 现在就是主存储：读完值还在原地。
        assertEquals("plain-key", defPrefs().getString(
                TranslationManager.apiKeyPrefKey("deepl"), ""));
    }

    @Test
    public void migrate_withFakeKeyStore_movesDecryptableCiphertextToDefaultPrefs()
            throws Exception {
        installFakeKeyStore();
        // 用 fake keystore 里的密钥手工造一段真密文，放进加密存储；
        // 迁移应把它解开成明文存进默认 prefs，并清掉加密存储里的残留。
        byte[] raw = new byte[16];
        new java.security.SecureRandom().nextBytes(raw);
        javax.crypto.SecretKey key =
                new javax.crypto.spec.SecretKeySpec(raw, "AES");
        FakeKeyStoreSpi.put("translation-cred-key", key);
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.Cipher cipher =
                javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key,
                new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] ct = cipher.doFinal("plain-key".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        securePrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("youdao"), "v1:" + b64(out)).apply();
        // 旧版共享键以明文躺在加密存储里，归入当前 provider（默认 google）。
        securePrefs().edit().putString(
                TranslationManager.KEY_API_KEY, "shared-key").apply();
        TranslationManager.migrateCredentialsFromSecurePrefs(context());
        assertEquals("plain-key", TranslationManager.getApiKey(context(), "youdao"));
        assertEquals("plain-key", defPrefs().getString(
                TranslationManager.apiKeyPrefKey("youdao"), ""));
        assertFalse(securePrefs().contains(TranslationManager.apiKeyPrefKey("youdao")));
        assertEquals("shared-key", TranslationManager.getApiKey(context(), "google"));
        assertFalse(securePrefs().contains(TranslationManager.KEY_API_KEY));
    }

    @Test
    public void migrate_secureCiphertextDoesNotOverwriteDefaultPlaintext() {
        defPrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("baidu"), "real-key").apply();
        // 加密存储里有解不开的旧密文：迁移删它，默认 prefs 的明文不动。
        securePrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("baidu"), "v1:!!!").apply();
        TranslationManager.migrateCredentialsFromSecurePrefs(context());
        assertEquals("real-key", TranslationManager.getApiKey(context(), "baidu"));
        assertFalse(securePrefs().contains(TranslationManager.apiKeyPrefKey("baidu")));
    }

    @Test
    public void migrate_noSecureValueLeavesDefaultPlaintext() {
        defPrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("deepl"), "plain-key").apply();
        TranslationManager.migrateCredentialsFromSecurePrefs(context());
        assertEquals("plain-key", defPrefs().getString(
                TranslationManager.apiKeyPrefKey("deepl"), ""));
        assertEquals("", securePrefs().getString(
                TranslationManager.apiKeyPrefKey("deepl"), ""));
    }

    @Test
    public void getConfiguredProviders_allConfiguredFromDefaultPrefs() {
        defPrefs().edit()
                .putString(TranslationManager.apiKeyPrefKey("youdao"), "k1")
                .putString(TranslationManager.apiSecretPrefKey("youdao"), "s1")
                .putString(TranslationManager.apiKeyPrefKey("baidu"), "k2")
                .putString(TranslationManager.apiSecretPrefKey("baidu"), "s2")
                .putString(TranslationManager.apiKeyPrefKey("deepl"), "k3")
                .apply();
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertEquals(4, providers.size());
        assertEquals("google", providers.get(0));
        assertTrue(providers.contains("youdao"));
        assertTrue(providers.contains("baidu"));
        assertTrue(providers.contains("deepl"));
    }

    @Test
    public void getConfiguredProviders_keyWithoutSecretExcluded() {
        defPrefs().edit().putString(
                TranslationManager.apiKeyPrefKey("youdao"), "k1").apply();
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertEquals(1, providers.size());
        assertFalse(providers.contains("youdao"));
    }

    @Test
    public void configuredProviders_deeplIncludedWhenConfigured() {
        assertTrue(TranslationManager.setApiKey(context(), "deepl", "k3"));
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertEquals(2, providers.size());
        assertTrue(providers.contains("google"));
        assertTrue(providers.contains("deepl"));
    }

    @Test
    public void configuredProviders_youdaoIncludedWhenConfigured() {
        assertTrue(TranslationManager.setApiKey(context(), "youdao", "test-key"));
        assertTrue(TranslationManager.setApiSecret(context(), "youdao", "test-secret"));
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertEquals(2, providers.size());
        assertTrue(providers.contains("youdao"));
    }
}
