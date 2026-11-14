package com.gh4a.utils.translate;

import android.content.Context;

import com.gh4a.fragment.SettingsFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TranslationManager: preference keys, provider selection, target language
 * and translator factory. Credential storage goes through the Android
 * KeyStore (S-H2); the round-trip test below documents what Robolectric's
 * shadow KeyStore does with it.
 */
@RunWith(RobolectricTestRunner.class)
public class TranslationManagerTest {

    private Context context() {
        return RuntimeEnvironment.getApplication();
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
    public void getStoredCiphertext_unconfiguredReturnsEmpty() {
        assertEquals("", TranslationManager.getStoredApiKeyCiphertext(context(), "deepl"));
        assertEquals("", TranslationManager.getStoredApiSecretCiphertext(context(), "baidu"));
    }

    @Test
    public void credentialRoundTrip_keyStoreUnavailableFailsGracefully() {
        // Robolectric 4.16 has no AndroidKeyStore provider on the JVM
        // ("AndroidKeyStore not found"), so encryption cannot run here and
        // setApiKey must fail gracefully: false, nothing stored, no crash,
        // and no plaintext left behind. The real encrypt → decrypt
        // round-trip is only verifiable on a device (instrumented test).
        // Fake credential value; never a real key.
        boolean saved = TranslationManager.setApiKey(context(), "deepl", "test-key");
        assertFalse("setApiKey must report failure when the KeyStore is unavailable",
                saved);
        assertEquals("", TranslationManager.getApiKey(context(), "deepl"));
        assertEquals("", TranslationManager.getStoredApiKeyCiphertext(context(), "deepl"));
    }

    @Test
    public void configuredProviders_deeplExcludedWhenKeyStoreUnavailable() {
        assertFalse(TranslationManager.setApiKey(context(), "deepl", "test-key"));
        List<String> providers = TranslationManager.getConfiguredProviders(context());
        assertTrue(providers.contains("google"));
        assertFalse(providers.contains("deepl"));
        assertEquals(1, providers.size());
    }

    @Test
    public void configuredProviders_youdaoExcludedWhenKeyStoreUnavailable() {
        // Key without secret is not enough, and here neither can be stored.
        assertFalse(TranslationManager.setApiKey(context(), "youdao", "test-key"));
        assertEquals(1, TranslationManager.getConfiguredProviders(context()).size());
        assertFalse(TranslationManager.setApiSecret(context(), "youdao", "test-secret"));
        assertFalse(TranslationManager.getConfiguredProviders(context()).contains("youdao"));
        assertEquals(1, TranslationManager.getConfiguredProviders(context()).size());
    }
}
