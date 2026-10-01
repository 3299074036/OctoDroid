package com.gh4a.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.gh4a.R;
import com.gh4a.utils.translate.TranslationManager;
import com.gh4a.utils.translate.Translator;

/**
 * Settings for README translation: provider selection plus per-provider
 * API key / secret configuration.
 */
public class TranslationSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private EditTextPreference mKeyPref;
    private EditTextPreference mSecretPref;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.translation_settings, rootKey);

        ListPreference providerPref = findPreference(TranslationManager.KEY_PROVIDER);
        if (providerPref != null) {
            providerPref.setOnPreferenceChangeListener(this);
        }
        mKeyPref = findPreference(TranslationManager.KEY_API_KEY);
        // password field with eye toggle (PasswordEditTextPreference)
        mSecretPref = findPreference(TranslationManager.KEY_API_SECRET);
        Preference testPref = findPreference("translation_test");
        if (testPref != null) {
            testPref.setOnPreferenceClickListener(p -> {
                testTranslation();
                return true;
            });
        }
        migrateCredentials();
        migrateRemovedProvider();
        updateVisibility();
    }

    /**
     * LibreTranslate and Microsoft were removed: anyone who had one of
     * them selected falls back to Google.
     */
    private void migrateRemovedProvider() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String provider = getStringSafe(prefs, TranslationManager.KEY_PROVIDER, "");
        if ("libretranslate".equals(provider) || "microsoft".equals(provider)) {
            prefs.edit().putString(TranslationManager.KEY_PROVIDER,
                    TranslationManager.PROVIDER_GOOGLE).apply();
        }
    }

    /**
     * One-time migration: old versions stored all providers' credentials
     * under the same shared keys. Move them to the current provider's
     * isolated keys so nothing the user typed is lost.
     */
    private void migrateCredentials() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (isMigrationDone(prefs)) {
            return;
        }
        String provider = getStringSafe(prefs, TranslationManager.KEY_PROVIDER,
                TranslationManager.PROVIDER_GOOGLE);
        copyIfPresent(prefs, TranslationManager.KEY_API_KEY,
                TranslationManager.apiKeyPrefKey(provider));
        copyIfPresent(prefs, TranslationManager.KEY_API_SECRET,
                TranslationManager.apiSecretPrefKey(provider));
        prefs.edit().putBoolean("translation_cred_migrated_v1", true).apply();
    }

    /**
     * 类型安全地读 String：备份损坏/恶意备份导致值类型错乱时返回默认值，
     * 而不是抛 ClassCastException 让设置页必现闪退 (M-2)。
     */
    private static String getStringSafe(SharedPreferences prefs, String key, String defValue) {
        try {
            Object v = prefs.getAll().get(key);
            return v instanceof String ? (String) v : defValue;
        } catch (Exception e) {
            return defValue;
        }
    }

    /**
     * 备份恢复可能把 boolean 存成 String（0.0.32 的 bug），这里兼容读取并顺手修复类型；
     * 其他错乱类型一律视为未迁移并修复为 boolean，直接 getBoolean 会闪退。
     */
    private static boolean isMigrationDone(SharedPreferences prefs) {
        Object v;
        try {
            v = prefs.getAll().get("translation_cred_migrated_v1");
        } catch (Exception e) {
            return false;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        boolean done = v instanceof String && Boolean.parseBoolean((String) v);
        prefs.edit().putBoolean("translation_cred_migrated_v1", done).apply();
        return done;
    }

    private static void copyIfPresent(SharedPreferences prefs, String from, String to) {
        String value = getStringSafe(prefs, from, "");
        if (!TextUtils.isEmpty(value) && TextUtils.isEmpty(getStringSafe(prefs, to, ""))) {
            prefs.edit().putString(to, value).apply();
        }
    }

    /**
     * Translate a short sample with the current provider config and show
     * the result (or the concrete failure reason) in a toast.
     */
    private void testTranslation() {
        if (getContext() == null) {
            return;
        }
        // Context 和文案在主线程先快照：测试中途退出设置页，后台线程再碰
        // getContext()/getString() 会因 detach 崩溃 (M-3)
        final android.content.Context appContext = getContext().getApplicationContext();
        final String okFormat = getString(R.string.translation_test_ok);
        final String failFormat = getString(R.string.translation_test_fail);
        Toast.makeText(getContext(), R.string.translation_testing, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String message;
            try {
                Translator translator = TranslationManager.createTranslator(appContext);
                String target = TranslationManager.getTargetLanguage(appContext);
                String result = translator.translate("Hello, world!", "en", target);
                message = String.format(okFormat, result);
            } catch (Exception e) {
                String detail = e.getMessage() != null ? e.getMessage() : e.toString();
                message = String.format(failFormat, detail);
            }
            final String toastMsg = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), toastMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (TranslationManager.KEY_PROVIDER.equals(preference.getKey())) {
            // delay until the new value is persisted
            updateVisibilityDelayed();
        }
        return true;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof PasswordEditTextPreference) {
            PasswordDialogFragment f =
                    PasswordDialogFragment.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getParentFragmentManager(),
                    "androidx.preference.PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    private void updateVisibilityDelayed() {
        if (getView() != null) {
            getView().post(this::updateVisibility);
        } else {
            updateVisibility();
        }
    }

    /**
     * Show only the fields relevant to the selected provider, with
     * provider-specific titles and hints:
     * - Google: nothing extra
     * - Youdao: AppKey + appSecret
     * - Baidu: APP ID + secret key
     * - DeepL: API Key
     */
    private void updateVisibility() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String provider = getStringSafe(prefs, TranslationManager.KEY_PROVIDER,
                TranslationManager.PROVIDER_GOOGLE);

        boolean isGoogle = TranslationManager.PROVIDER_GOOGLE.equals(provider);
        boolean isYoudao = TranslationManager.PROVIDER_YOUDAO.equals(provider);
        boolean isBaidu = TranslationManager.PROVIDER_BAIDU.equals(provider);
        boolean isDeepL = TranslationManager.PROVIDER_DEEPL.equals(provider);

        if (mKeyPref != null) {
            // rebind to this provider's isolated storage
            String key = TranslationManager.apiKeyPrefKey(provider);
            mKeyPref.setKey(key);
            mKeyPref.setText(getStringSafe(prefs, key, ""));
            mKeyPref.setVisible(!isGoogle);
            int titleRes;
            int summaryRes;
            if (isYoudao) {
                titleRes = R.string.translation_youdao_appkey;
                summaryRes = R.string.translation_key_summary_youdao;
            } else if (isBaidu) {
                titleRes = R.string.translation_baidu_appid;
                summaryRes = R.string.translation_key_summary_baidu;
            } else if (isDeepL) {
                titleRes = R.string.translation_deepl_key;
                summaryRes = R.string.translation_key_summary_deepl;
            } else {
                titleRes = R.string.translation_api_key;
                summaryRes = R.string.translation_api_key_summary;
            }
            mKeyPref.setTitle(titleRes);
            mKeyPref.setSummary(summaryRes);
            mKeyPref.setDialogTitle(titleRes);
        }
        if (mSecretPref != null) {
            String key = TranslationManager.apiSecretPrefKey(provider);
            mSecretPref.setKey(key);
            mSecretPref.setText(getStringSafe(prefs, key, ""));
            mSecretPref.setVisible(isYoudao || isBaidu);
            int titleRes;
            int summaryRes;
            if (isBaidu) {
                titleRes = R.string.translation_baidu_secret;
                summaryRes = R.string.translation_secret_summary_baidu;
            } else {
                titleRes = R.string.translation_api_secret;
                summaryRes = R.string.translation_api_secret_summary;
            }
            mSecretPref.setTitle(titleRes);
            mSecretPref.setSummary(summaryRes);
            mSecretPref.setDialogTitle(titleRes);
        }
    }
}
