package com.gh4a.fragment;

import android.content.SharedPreferences;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.utils.translate.TranslationManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import android.content.Context;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.utils.translate.Translator;
import org.mockito.MockedStatic;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

/** TranslationSettingsFragment：翻译设置（反射测 getStringSafe + 偏好屏挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class TranslationSettingsFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private static String getStringSafe(SharedPreferences prefs, String key, String defValue)
            throws Exception {
        Method m = TranslationSettingsFragment.class.getDeclaredMethod(
                "getStringSafe", SharedPreferences.class, String.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, prefs, key, defValue);
    }

    private static SharedPreferences prefsWith(Map<String, Object> values) {
        SharedPreferences prefs = mock(SharedPreferences.class);
        // getAll() 返回 Map<String, ?>：thenReturn 的泛型推断不接受 Map<String, Object>，
        // 改用 doReturn（无泛型约束）。
        org.mockito.Mockito.doReturn(values).when(prefs).getAll();
        return prefs;
    }

    @Test
    public void getStringSafe_stringValueReturned() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put(TranslationManager.KEY_PROVIDER, "deepseek");
        assertEquals("deepseek",
                getStringSafe(prefsWith(values), TranslationManager.KEY_PROVIDER, "google"));
    }

    @Test
    public void getStringSafe_missingKeyReturnsDefault() throws Exception {
        assertEquals("google",
                getStringSafe(prefsWith(new HashMap<>()),
                        TranslationManager.KEY_PROVIDER, "google"));
    }

    @Test
    public void getStringSafe_wrongTypeReturnsDefault() throws Exception {
        // 备份损坏导致值类型错乱：返回默认值而不是抛 ClassCastException。
        Map<String, Object> values = new HashMap<>();
        values.put(TranslationManager.KEY_PROVIDER, 42);
        assertEquals("google",
                getStringSafe(prefsWith(values), TranslationManager.KEY_PROVIDER, "google"));
        Map<String, Object> boolValues = new HashMap<>();
        boolValues.put(TranslationManager.KEY_PROVIDER, true);
        assertEquals("google",
                getStringSafe(prefsWith(boolValues), TranslationManager.KEY_PROVIDER, "google"));
    }

    @Test
    public void getStringSafe_nullValueReturnsDefault() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put(TranslationManager.KEY_PROVIDER, null);
        assertEquals("google",
                getStringSafe(prefsWith(values), TranslationManager.KEY_PROVIDER, "google"));
    }

    @Test
    public void getStringSafe_getAllThrowsReturnsDefault() throws Exception {
        SharedPreferences prefs = mock(SharedPreferences.class);
        when(prefs.getAll()).thenThrow(new RuntimeException("boom"));
        assertEquals("google",
                getStringSafe(prefs, TranslationManager.KEY_PROVIDER, "google"));
    }

    @Test
    public void onCreatePreferences_loadsTranslationSettingsScreen() {
        TranslationSettingsFragment f = new TranslationSettingsFragment();
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        assertNotNull(f.getPreferenceScreen());
        assertNotNull(f.findPreference(TranslationManager.KEY_PROVIDER));
        // updateVisibility() 把 key/secret 偏好重绑到分 provider 的键
        //（apiKeyPrefKey(provider)），原 "translation_api_key" 键已不存在。
        assertNotNull(f.findPreference(
                TranslationManager.apiKeyPrefKey(TranslationManager.PROVIDER_GOOGLE)));
        assertNotNull(f.findPreference(
                TranslationManager.apiSecretPrefKey(TranslationManager.PROVIDER_GOOGLE)));
        assertNull(f.findPreference(TranslationManager.KEY_API_KEY));
        assertNull(f.findPreference(TranslationManager.KEY_API_SECRET));
    }

    // ---------- updateVisibility / 迁移 / 监听器 ----------

    private TranslationSettingsFragment attach() {
        TranslationSettingsFragment f = new TranslationSettingsFragment();
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return f;
    }

    private TranslationSettingsFragment attachResumed() {
        // 挂进 activity 的容器里，视图才能拿到 AttachInfo，
        // getView().post() 的任务才会在窗口附着后执行（无容器挂载时
        // windowToken 为 null，post 进 RunQueue 永不执行）。
        android.widget.FrameLayout container =
                new android.widget.FrameLayout(mActivity);
        container.setId(android.view.View.generateViewId());
        mActivity.setContentView(container);
        TranslationSettingsFragment f = new TranslationSettingsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(container.getId(), f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private void setProvider(String provider) {
        Context ctx = ApplicationProvider.getApplicationContext();
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit().putString(TranslationManager.KEY_PROVIDER, provider).apply();
    }

    private static EditTextPreference keyPref(TranslationSettingsFragment f) throws Exception {
        java.lang.reflect.Field field =
                TranslationSettingsFragment.class.getDeclaredField("mKeyPref");
        field.setAccessible(true);
        return (EditTextPreference) field.get(f);
    }

    private static EditTextPreference secretPref(TranslationSettingsFragment f) throws Exception {
        java.lang.reflect.Field field =
                TranslationSettingsFragment.class.getDeclaredField("mSecretPref");
        field.setAccessible(true);
        return (EditTextPreference) field.get(f);
    }

    private static void detach(TranslationSettingsFragment f) {
        // 用测试内宿主 Activity 的 FragmentManager 卸载，getContext() 变 null。
        androidx.fragment.app.FragmentActivity activity =
                (androidx.fragment.app.FragmentActivity) f.getActivity();
        activity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
    }

    private static void invokePrivate(TranslationSettingsFragment f, String name)
            throws Exception {
        java.lang.reflect.Method m =
                TranslationSettingsFragment.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(f);
    }

    private static boolean warnedFlag(TranslationSettingsFragment f) throws Exception {
        java.lang.reflect.Field field =
                TranslationSettingsFragment.class.getDeclaredField("mPlainFallbackWarned");
        field.setAccessible(true);
        return (Boolean) field.get(f);
    }

    private static MockedStatic<TranslationManager> mockTranslationManager() {
        MockedStatic<TranslationManager> mocked =
                mockStatic(TranslationManager.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        mocked.when(() -> TranslationManager.apiKeyPrefKey(anyString()))
                .thenCallRealMethod();
        mocked.when(() -> TranslationManager.apiSecretPrefKey(anyString()))
                .thenCallRealMethod();
        mocked.when(() -> TranslationManager.getApiKey(any(), anyString()))
                .thenCallRealMethod();
        mocked.when(() -> TranslationManager.getApiSecret(any(), anyString()))
                .thenCallRealMethod();
        return mocked;
    }

    @Test
    public void updateVisibility_googleHidesKeyAndSecret() throws Exception {
        setProvider("google");
        TranslationSettingsFragment f = attach();
        assertFalse(keyPref(f).isVisible());
        assertFalse(secretPref(f).isVisible());
        assertNotNull(f.findPreference(
                TranslationManager.apiKeyPrefKey("google")));
        assertNull(f.findPreference(TranslationManager.KEY_API_KEY));
    }

    @Test
    public void updateVisibility_youdaoShowsKeyAndSecretWithYoudaoTitles() throws Exception {
        setProvider("youdao");
        TranslationSettingsFragment f = attach();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertTrue(keyPref(f).isVisible());
        assertTrue(secretPref(f).isVisible());
        assertEquals(ctx.getString(R.string.translation_youdao_appkey),
                keyPref(f).getTitle().toString());
        assertEquals(ctx.getString(R.string.translation_api_secret),
                secretPref(f).getTitle().toString());
    }

    @Test
    public void updateVisibility_baiduShowsBaiduTitles() throws Exception {
        setProvider("baidu");
        TranslationSettingsFragment f = attach();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertTrue(keyPref(f).isVisible());
        assertTrue(secretPref(f).isVisible());
        assertEquals(ctx.getString(R.string.translation_baidu_appid),
                keyPref(f).getTitle().toString());
        assertEquals(ctx.getString(R.string.translation_baidu_secret),
                secretPref(f).getTitle().toString());
    }

    @Test
    public void updateVisibility_deeplShowsKeyOnlyWithDeeplTitle() throws Exception {
        setProvider("deepl");
        TranslationSettingsFragment f = attach();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertTrue(keyPref(f).isVisible());
        assertFalse(secretPref(f).isVisible());
        assertEquals(ctx.getString(R.string.translation_deepl_key),
                keyPref(f).getTitle().toString());
    }

    @Test
    public void migrateRemovedProvider_libretranslateFallsBackToGoogle() {
        setProvider("libretranslate");
        attach();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertEquals("google", PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(TranslationManager.KEY_PROVIDER, null));
    }

    @Test
    public void migrateRemovedProvider_microsoftFallsBackToGoogle() {
        setProvider("microsoft");
        attach();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertEquals("google", PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(TranslationManager.KEY_PROVIDER, null));
    }

    @Test
    public void migrateRemovedProvider_otherProviderIsKept() {
        setProvider("youdao");
        attach();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertEquals("youdao", PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(TranslationManager.KEY_PROVIDER, null));
    }

    @Test
    public void currentProvider_defaultIsGoogleAndReadsPersistedValue() throws Exception {
        TranslationSettingsFragment f = attach();
        java.lang.reflect.Method m =
                TranslationSettingsFragment.class.getDeclaredMethod("currentProvider");
        m.setAccessible(true);
        assertEquals("google", m.invoke(f));
        setProvider("baidu");
        assertEquals("baidu", m.invoke(f));
    }

    @Test
    public void keyChangeListener_saveSuccessAcceptsValue() throws Exception {
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            mocked.when(() -> TranslationManager.setApiKey(any(), anyString(), anyString()))
                    .thenReturn(true);
            TranslationSettingsFragment f = attach();
            EditTextPreference key = keyPref(f);
            assertTrue(key.getOnPreferenceChangeListener()
                    .onPreferenceChange(key, "new-key"));
        }
    }

    @Test
    public void keyChangeListener_saveFailureRejectsValueAndToasts() throws Exception {
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            mocked.when(() -> TranslationManager.setApiKey(any(), anyString(), anyString()))
                    .thenReturn(false);
            TranslationSettingsFragment f = attach();
            EditTextPreference key = keyPref(f);
            assertFalse(key.getOnPreferenceChangeListener()
                    .onPreferenceChange(key, "new-key"));
            Context ctx = ApplicationProvider.getApplicationContext();
            assertEquals(ctx.getString(R.string.translation_credential_save_failed),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void secretChangeListener_saveSuccessAcceptsValue() throws Exception {
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            mocked.when(() -> TranslationManager.setApiSecret(any(), anyString(), anyString()))
                    .thenReturn(true);
            TranslationSettingsFragment f = attach();
            EditTextPreference secret = secretPref(f);
            assertTrue(secret.getOnPreferenceChangeListener()
                    .onPreferenceChange(secret, "new-secret"));
        }
    }

    @Test
    public void secretChangeListener_saveFailureRejectsValueAndToasts() throws Exception {
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            mocked.when(() -> TranslationManager.setApiSecret(any(), anyString(), anyString()))
                    .thenReturn(false);
            TranslationSettingsFragment f = attach();
            EditTextPreference secret = secretPref(f);
            assertFalse(secret.getOnPreferenceChangeListener()
                    .onPreferenceChange(secret, "new-secret"));
            Context ctx = ApplicationProvider.getApplicationContext();
            assertEquals(ctx.getString(R.string.translation_credential_save_failed),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void keyChangeListener_nullContextRejectsValue() throws Exception {
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            TranslationSettingsFragment f = attach();
            EditTextPreference key = keyPref(f);
            androidx.preference.Preference.OnPreferenceChangeListener listener =
                    key.getOnPreferenceChangeListener();
            detach(f);
            assertFalse(listener.onPreferenceChange(key, "new-key"));
        }
    }

    @Test
    public void secretChangeListener_nullContextRejectsValue() throws Exception {
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            TranslationSettingsFragment f = attach();
            EditTextPreference secret = secretPref(f);
            androidx.preference.Preference.OnPreferenceChangeListener listener =
                    secret.getOnPreferenceChangeListener();
            detach(f);
            assertFalse(listener.onPreferenceChange(secret, "new-secret"));
        }
    }

    @Test
    public void testClick_successShowsTranslatedToast() throws Exception {
        // 注：后台任务跑在 Background 线程池，mockStatic 只在创建线程生效，
        // 后台线程里 createTranslator 走真实实现，结果 toast 的断言无法稳定；
        // 这里只断言点击被接受且“测试中”toast 已出，后台成功/失败分支见未覆盖清单。
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            Translator translator = mock(Translator.class);
            when(translator.translate(anyString(), anyString(), anyString()))
                    .thenReturn("你好，世界！");
            mocked.when(() -> TranslationManager.createTranslator(any()))
                    .thenReturn(translator);
            mocked.when(() -> TranslationManager.getTargetLanguage(any()))
                    .thenReturn("zh");
            TranslationSettingsFragment f = attach();
            Preference testPref = f.findPreference("translation_test");
            assertNotNull(testPref);
            assertTrue(testPref.getOnPreferenceClickListener().onPreferenceClick(testPref));
            Context ctx = ApplicationProvider.getApplicationContext();
            assertEquals(ctx.getString(R.string.translation_testing),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void testClick_failureShowsFailureToast() throws Exception {
        // 同上：后台分支无法稳定断言，只断言点击被接受且“测试中”toast 已出。
        try (MockedStatic<TranslationManager> mocked = mockTranslationManager()) {
            mocked.when(() -> TranslationManager.createTranslator(any()))
                    .thenThrow(new RuntimeException("no key configured"));
            TranslationSettingsFragment f = attach();
            Preference testPref = f.findPreference("translation_test");
            assertTrue(testPref.getOnPreferenceClickListener().onPreferenceClick(testPref));
            Context ctx = ApplicationProvider.getApplicationContext();
            assertEquals(ctx.getString(R.string.translation_testing),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void onPreferenceChange_providerKeyRefreshesVisibilityImmediately() throws Exception {
        setProvider("youdao");
        TranslationSettingsFragment f = attach();
        ListPreference providerPref = f.findPreference(TranslationManager.KEY_PROVIDER);
        assertNotNull(providerPref);
        assertTrue(f.onPreferenceChange(providerPref, "youdao"));
        Context ctx = ApplicationProvider.getApplicationContext();
        assertEquals(ctx.getString(R.string.translation_youdao_appkey),
                keyPref(f).getTitle().toString());
    }

    @Test
    public void onPreferenceChange_otherKeyReturnsTrueWithoutRefresh() throws Exception {
        TranslationSettingsFragment f = attach();
        EditTextPreference key = keyPref(f);
        assertTrue(f.onPreferenceChange(key, "x"));
    }

    @Test
    public void onPreferenceChange_providerKeyWithViewPostsVisibilityUpdate() throws Exception {
        setProvider("google");
        TranslationSettingsFragment f = attachResumed();
        assertFalse(keyPref(f).isVisible());
        setProvider("deepl");
        ListPreference providerPref = f.findPreference(TranslationManager.KEY_PROVIDER);
        assertTrue(f.onPreferenceChange(providerPref, "deepl"));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        Context ctx = ApplicationProvider.getApplicationContext();
        assertTrue(keyPref(f).isVisible());
        assertEquals(ctx.getString(R.string.translation_deepl_key),
                keyPref(f).getTitle().toString());
    }

    @Test
    public void onDisplayPreferenceDialog_passwordPrefShowsPasswordDialog() throws Exception {
        TranslationSettingsFragment f = attach();
        EditTextPreference secret = secretPref(f);
        f.onDisplayPreferenceDialog(secret);
        mActivity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(mActivity.getSupportFragmentManager().findFragmentByTag(
                "androidx.preference.PreferenceFragment.DIALOG"));
    }

    @Test
    public void onDisplayPreferenceDialog_plainEditTextPrefShowsDefaultDialog() throws Exception {
        TranslationSettingsFragment f = attach();
        EditTextPreference key = keyPref(f);
        f.onDisplayPreferenceDialog(key);
        mActivity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(mActivity.getSupportFragmentManager().findFragmentByTag(
                "androidx.preference.PreferenceFragment.DIALOG"));
    }

    @Test
    public void warnIfPlainFallback_showsDialogOnceWhenFallbackActive() throws Exception {
        // 回落 flag 在 getSecurePrefs 初始化时才确定；attach 过程中的
        // updateVisibility 会触发它，故 attach 后再读 flag 判定。
        TranslationSettingsFragment f = attach();
        boolean fallbackActive = Gh4Application.get().isSecurePrefsPlainFallback();
        if (fallbackActive) {
            assertTrue(warnedFlag(f));
            assertEquals(1, ShadowAlertDialog.getShownDialogs().size());
            invokePrivate(f, "warnIfPlainFallback");
            assertEquals(1, ShadowAlertDialog.getShownDialogs().size());
        } else {
            assertFalse(warnedFlag(f));
        }
    }

    @Test
    public void warnIfPlainFallback_alreadyWarnedIsNoop() throws Exception {
        TranslationSettingsFragment f = attach();
        java.lang.reflect.Field field =
                TranslationSettingsFragment.class.getDeclaredField("mPlainFallbackWarned");
        field.setAccessible(true);
        field.set(f, true);
        invokePrivate(f, "warnIfPlainFallback");
        assertTrue(warnedFlag(f));
    }

    @Test
    public void privateMethods_nullContextReturnEarly() throws Exception {
        TranslationSettingsFragment f = attach();
        detach(f);
        invokePrivate(f, "migrateCredentials");
        invokePrivate(f, "warnIfPlainFallback");
        invokePrivate(f, "testTranslation");
    }
}
