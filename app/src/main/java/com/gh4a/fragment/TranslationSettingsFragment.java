package com.gh4a.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
        // S-H2：凭据不走 Preference 框架的默认 prefs 明文持久化。
        // 输入框只负责显示（解密后的值）与收集输入，保存时由下面的
        // listener 加密后写进加密存储；setPersistent(false) 保证框架
        // 本身绝不把明文写进默认 prefs。
        if (mKeyPref != null) {
            mKeyPref.setPersistent(false);
            mKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (getContext() == null) {
                    return false;
                }
                // 52：加密失败时 setApiKey 返回 false，toast 提示并拒绝新值，
                // 输入框保持旧值，不再假装已保存。
                boolean saved = TranslationManager.setApiKey(getContext(), currentProvider(),
                        newValue == null ? "" : String.valueOf(newValue));
                if (!saved) {
                    Toast.makeText(getContext(),
                            R.string.translation_credential_save_failed, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            });
        }
        if (mSecretPref != null) {
            mSecretPref.setPersistent(false);
            mSecretPref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (getContext() == null) {
                    return false;
                }
                // 52：同 key 逻辑，加密失败时 toast 提示并拒绝新值。
                boolean saved = TranslationManager.setApiSecret(getContext(), currentProvider(),
                        newValue == null ? "" : String.valueOf(newValue));
                if (!saved) {
                    Toast.makeText(getContext(),
                            R.string.translation_credential_save_failed, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            });
        }
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
     * One-time migration: old versions stored credentials in the default
     * prefs as plaintext. Move them into the encrypted store (S-H2);
     * TranslationManager's migration is idempotent, so this is safe to
     * run on every open (a user upgrading from a version that already
     * set the old flag still gets migrated).
     */
    private void migrateCredentials() {
        if (getContext() == null) {
            return;
        }
        TranslationManager.migrateCredentialsFromDefaultPrefs(getContext());
    }

    private String currentProvider() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        return getStringSafe(prefs, TranslationManager.KEY_PROVIDER,
                TranslationManager.PROVIDER_GOOGLE);
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
            // S-H2：显示解密后的值；框架不持久化（setPersistent(false)）。
            mKeyPref.setText(getContext() == null ? ""
                    : TranslationManager.getApiKey(getContext(), provider));
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
            // S-H2：显示解密后的值；框架不持久化（setPersistent(false)）。
            mSecretPref.setText(getContext() == null ? ""
                    : TranslationManager.getApiSecret(getContext(), provider));
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
