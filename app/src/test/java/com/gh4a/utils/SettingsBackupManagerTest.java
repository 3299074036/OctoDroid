package com.gh4a.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.Gh4Application;
import com.gh4a.fragment.SettingsFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * R-6（A 方案）：备份政策的回归网——凭据类键永不进入手动备份。
 * 将来新增凭据类设置键时，先在这里补断言，再改 isExcluded()。
 *
 * B 组试点扩充：backupContainsTranslationCredentials（纯 JSON）、
 * applyBackup 的格式校验路径、readBackupFile、collectBackup 回合。
 * writeBackupFile 走 mock ContentResolver 覆盖 MediaStore Q 分支；
 * pre-Q 文件分支 SDK 恒 35 不可达。
 */
@RunWith(RobolectricTestRunner.class)
public class SettingsBackupManagerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void isExcluded_blocksTokens() {
        assertTrue(SettingsBackupManager.isExcluded("token_foo"));
        assertTrue(SettingsBackupManager.isExcluded("my_token_key"));
        // 大小写不敏感
        assertTrue(SettingsBackupManager.isExcluded("TOKEN_UPPER"));
        assertTrue(SettingsBackupManager.isExcluded("user_id_123"));
        assertTrue(SettingsBackupManager.isExcluded("active_login"));
        assertTrue(SettingsBackupManager.isExcluded("logins"));
    }

    @Test
    public void isExcluded_allowsNormalKeys() {
        assertFalse(SettingsBackupManager.isExcluded("language"));
        assertFalse(SettingsBackupManager.isExcluded("mirror_list"));
        assertFalse(SettingsBackupManager.isExcluded("start_page"));
    }

    @Test
    public void isExcluded_translationSecretsGoThroughConsentFlow() {
        // 翻译 API 凭据不走黑名单：它们进手动备份前有专门的弹窗确认流程，
        // 这里不断言排除，只锁定"不被静默排除"的现状，防止误改
        assertFalse(SettingsBackupManager.isExcluded("translation_api_secret_youdao"));
        assertFalse(SettingsBackupManager.isExcluded("translation_api_key_baidu"));
    }

    @Test
    public void isExcluded_moreCases() {
        assertTrue(SettingsBackupManager.isExcluded("token"));
        assertTrue(SettingsBackupManager.isExcluded("my_token"));
        assertTrue(SettingsBackupManager.isExcluded("TOKEN"));
        assertTrue(SettingsBackupManager.isExcluded("user_id_alice"));
        // T-106：user_id_ 前缀检查与 token 检查口径一致（先转小写再
        // startsWith），大写 USER_ID_ALICE 同样排除。
        assertTrue(SettingsBackupManager.isExcluded("USER_ID_ALICE"));
        // user_id_ 前缀才排除；不带下划线的 user_id 不排除
        assertFalse(SettingsBackupManager.isExcluded("user_id"));
        // 精确键才排除；带后缀的不排除
        assertFalse(SettingsBackupManager.isExcluded("active_login2"));
        assertFalse(SettingsBackupManager.isExcluded("logins_backup"));
        assertFalse(SettingsBackupManager.isExcluded("my_logins"));
        assertFalse(SettingsBackupManager.isExcluded(""));
    }

    private static JSONObject backupWithTranslation(String key, String value)
            throws JSONException {
        JSONObject holder = new JSONObject();
        holder.put("t", "s");
        holder.put("v", value);
        JSONObject def = new JSONObject();
        def.put(key, holder);
        JSONObject prefs = new JSONObject();
        prefs.put("default", def);
        JSONObject backup = new JSONObject();
        backup.put("prefs", prefs);
        return backup;
    }

    @Test
    public void backupContainsTranslationCredentials_cases() throws JSONException {
        assertTrue(SettingsBackupManager.backupContainsTranslationCredentials(
                backupWithTranslation("translation_api_key_youdao", "ciphertext")));
        assertTrue(SettingsBackupManager.backupContainsTranslationCredentials(
                backupWithTranslation("translation_api_secret_baidu", "ciphertext")));
        assertFalse(SettingsBackupManager.backupContainsTranslationCredentials(
                backupWithTranslation("translation_api_key_youdao", "")));
        assertFalse(SettingsBackupManager.backupContainsTranslationCredentials(
                backupWithTranslation("translation_provider", "youdao")));
        assertFalse(SettingsBackupManager.backupContainsTranslationCredentials(
                new JSONObject()));
        assertFalse(SettingsBackupManager.backupContainsTranslationCredentials(
                new JSONObject("{\"prefs\":{}}")));
        assertFalse(SettingsBackupManager.backupContainsTranslationCredentials(
                new JSONObject("{\"prefs\":{\"default\":{}}}")));
    }

    @Test
    public void applyBackup_rejectsBadFormat() throws JSONException {
        assertFalse(SettingsBackupManager.applyBackup(mContext, new JSONObject()));
        assertFalse(SettingsBackupManager.applyBackup(mContext,
                new JSONObject("{\"app\":\"Other\",\"version\":1,\"prefs\":{}}")));
        assertFalse(SettingsBackupManager.applyBackup(mContext,
                new JSONObject("{\"app\":\"OctoDroid\",\"version\":2,\"prefs\":{}}")));
        assertFalse(SettingsBackupManager.applyBackup(mContext,
                new JSONObject("{\"app\":\"OctoDroid\",\"version\":0,\"prefs\":{}}")));
        assertFalse(SettingsBackupManager.applyBackup(mContext,
                new JSONObject("{\"app\":\"OctoDroid\",\"prefs\":{}}")));
        assertFalse(SettingsBackupManager.applyBackup(mContext,
                new JSONObject("{\"app\":\"OctoDroid\",\"version\":1}")));
    }

    @Test
    public void applyBackup_emptyPrefsSucceeds() throws JSONException {
        JSONObject backup = new JSONObject(
                "{\"app\":\"OctoDroid\",\"version\":1,\"prefs\":{}}");
        assertTrue(SettingsBackupManager.applyBackup(mContext, backup));
    }

    @Test
    public void applyBackup_restoresWhitelistedKeyAndBumpsVersion() throws JSONException {
        JSONObject holder = new JSONObject();
        holder.put("t", "s");
        holder.put("v", "dark");
        JSONObject gh4a = new JSONObject();
        gh4a.put("start_page", holder);
        JSONObject versionHolder = new JSONObject();
        versionHolder.put("t", "i");
        versionHolder.put("v", 1);
        gh4a.put("version", versionHolder);
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        JSONObject backup = new JSONObject();
        backup.put("app", "OctoDroid");
        backup.put("version", 1);
        backup.put("prefs", prefs);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backup));
        assertEquals("dark", mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .getString("start_page", null));
        // 102：恢复后 version 写回当前值 4
        assertEquals(4, mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .getInt("version", -1));
    }

    @Test
    public void applyBackup_dropsUnknownKeyAndWrongTypedKey() throws JSONException {
        JSONObject evil = new JSONObject();
        evil.put("t", "s");
        evil.put("v", "https://evil.example.com");
        JSONObject themeWrongType = new JSONObject();
        themeWrongType.put("t", "s");
        themeWrongType.put("v", "dark");
        JSONObject gh4a = new JSONObject();
        gh4a.put("mirror_list", evil);
        gh4a.put("theme", themeWrongType);
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        JSONObject backup = new JSONObject();
        backup.put("app", "OctoDroid");
        backup.put("version", 1);
        backup.put("prefs", prefs);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backup));
        // mirror_list 不在白名单（SEC-9）、theme 错型（228）均被丢弃
        assertFalse(mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE).contains("mirror_list"));
        assertFalse(mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE).contains("theme"));
    }

    @Test
    public void applyBackup_excludedKeysNeverRestored() throws JSONException {
        JSONObject token = new JSONObject();
        token.put("t", "s");
        token.put("v", "ghp_secret");
        JSONObject gh4a = new JSONObject();
        gh4a.put("token_alice", token);
        gh4a.put("active_login", token);
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        JSONObject backup = new JSONObject();
        backup.put("app", "OctoDroid");
        backup.put("version", 1);
        backup.put("prefs", prefs);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backup));
        assertFalse(mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE).contains("token_alice"));
        assertFalse(mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE).contains("active_login"));
    }

    @Test
    public void applyBackup_badSectionRollsBackAndReturnsFalse() throws JSONException {
        // Gh4a-pref 先写一个合法键，再在 drawer_config 放非法结构
        //（jsonToPrefs 要求 holder 为 JSONObject，字符串会抛 JSONException）
        JSONObject ok = new JSONObject();
        ok.put("t", "s");
        ok.put("v", "dark");
        JSONObject gh4a = new JSONObject();
        gh4a.put("start_page", ok);
        JSONObject drawer = new JSONObject();
        drawer.put("order", "not-an-object");
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        prefs.put("drawer_config", drawer);
        JSONObject backup = new JSONObject();
        backup.put("app", "OctoDroid");
        backup.put("version", 1);
        backup.put("prefs", prefs);
        assertFalse(SettingsBackupManager.applyBackup(mContext, backup));
        // 103 回滚：已提交的 Gh4a-pref 恢复到调用前（无 start_page）
        assertFalse(mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE).contains("start_page"));
    }

    @Test
    public void readBackupFile_roundTrip() throws Exception {
        String json = "{\"app\":\"OctoDroid\",\"version\":1,\"prefs\":{}}";
        android.net.Uri uri = android.net.Uri.parse("content://test/backup.json");
        InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        ShadowContentResolver shadowResolver =
                Shadows.shadowOf(mContext.getContentResolver());
        shadowResolver.registerInputStream(uri, in);
        JSONObject read = SettingsBackupManager.readBackupFile(mContext, uri);
        assertEquals("OctoDroid", read.getString("app"));
        assertEquals(1, read.getInt("version"));
    }

    @Test
    public void readBackupFile_streamReadErrorThrowsIOException() {
        // Robolectric 下未注册的 content Uri 调 openInputStream 会抛
        // UnsupportedOperationException（须经 ShadowContentResolver 注册流），
        // 故注册一个读即抛 IOException 的流，验证生产代码透传 IOException。
        android.net.Uri uri = android.net.Uri.parse("content://test/absent.json");
        java.io.InputStream failing = new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        Shadows.shadowOf(mContext.getContentResolver())
                .registerInputStream(uri, failing);
        try {
            SettingsBackupManager.readBackupFile(mContext, uri);
            fail("expected IOException");
        } catch (IOException expected) {
        } catch (JSONException e) {
            fail("expected IOException, got JSONException");
        }
    }

    @Test
    public void collectBackup_excludesTokensKeepsNormalKeys() throws JSONException {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("language", "en")
                .putString("token_alice", "ghp_secret")
                .putString("active_login", "alice")
                .apply();
        JSONObject backup = SettingsBackupManager.collectBackup(mContext);
        assertEquals("OctoDroid", backup.getString("app"));
        assertEquals(1, backup.getInt("version"));
        JSONObject gh4a = backup.getJSONObject("prefs").getJSONObject("Gh4a-pref");
        assertTrue(gh4a.has("language"));
        assertEquals("en", gh4a.getJSONObject("language").getString("v"));
        assertFalse(gh4a.has("token_alice"));
        assertFalse(gh4a.has("active_login"));
    }

    // ---------- 补缺批（2026-11-15）：逐行缺口对照 JaCoCo ----------
    // 每个测试前清三处 prefs + 加密存储，保证方法间不互相污染
    //（本类曾因跨方法残留 version=4 误盖 restorePrefsSnapshot 的 Integer 分支）。

    @Before
    public void clearPrefs() {
        gh4aPrefs().edit().clear().apply();
        mContext.getSharedPreferences("drawer_config", Context.MODE_PRIVATE)
                .edit().clear().apply();
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().clear().apply();
        Gh4Application.securePrefs(mContext).edit().clear().apply();
    }

    private SharedPreferences gh4aPrefs() {
        return mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    private static Object invokePrivate(String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = SettingsBackupManager.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static boolean putTypedValue(JSONObject holder, Object value) throws Exception {
        return (Boolean) invokePrivate("putTypedValue",
                new Class<?>[]{JSONObject.class, Object.class}, holder, value);
    }

    private static boolean isGh4aPrefKeyAllowed(String key) throws Exception {
        return (Boolean) invokePrivate("isGh4aPrefKeyAllowed",
                new Class<?>[]{String.class}, key);
    }

    private static boolean isTranslationKeyAllowed(String key) throws Exception {
        return (Boolean) invokePrivate("isTranslationKeyAllowed",
                new Class<?>[]{String.class}, key);
    }

    private static boolean isTranslationCredentialKey(String key) throws Exception {
        return (Boolean) invokePrivate("isTranslationCredentialKey",
                new Class<?>[]{String.class}, key);
    }

    private static JSONObject holder(String type, Object value) throws JSONException {
        JSONObject h = new JSONObject();
        h.put("t", type);
        h.put("v", value);
        return h;
    }

    private static JSONObject backupWithPrefs(JSONObject prefs) throws JSONException {
        JSONObject backup = new JSONObject();
        backup.put("app", "OctoDroid");
        backup.put("version", 1);
        backup.put("prefs", prefs);
        return backup;
    }

    private static Context mockResolverContext(ContentResolver resolver) {
        Context ctx = mock(Context.class);
        when(ctx.getContentResolver()).thenReturn(resolver);
        return ctx;
    }

    @Test
    public void defaultConstructor_instantiates() {
        assertNotNull(new SettingsBackupManager());
    }

    @Test
    public void putTypedValue_allTypes() throws Exception {
        JSONObject h = new JSONObject();
        assertTrue(putTypedValue(h, "x"));
        assertEquals("s", h.getString("t"));
        assertEquals("x", h.getString("v"));

        h = new JSONObject();
        assertTrue(putTypedValue(h, 7));
        assertEquals("i", h.getString("t"));
        assertEquals(7, h.getInt("v"));

        h = new JSONObject();
        assertTrue(putTypedValue(h, 123456789L));
        assertEquals("l", h.getString("t"));
        assertEquals(123456789L, h.getLong("v"));

        h = new JSONObject();
        assertTrue(putTypedValue(h, 1.5f));
        assertEquals("f", h.getString("t"));
        assertEquals(1.5, h.getDouble("v"), 0.0);

        h = new JSONObject();
        assertTrue(putTypedValue(h, Boolean.TRUE));
        assertEquals("b", h.getString("t"));
        assertTrue(h.getBoolean("v"));

        h = new JSONObject();
        assertTrue(putTypedValue(h, new HashSet<>(Arrays.asList("a", "b"))));
        assertEquals("set", h.getString("t"));
        assertEquals(2, h.getJSONArray("v").length());

        // SharedPreferences 存不下的类型（Double）返回 false
        h = new JSONObject();
        assertFalse(putTypedValue(h, 2.5));
        assertFalse(h.has("t"));
    }

    @Test
    public void prefsToJson_skipsUnsupportedType() throws Exception {
        SharedPreferences prefs = mock(SharedPreferences.class);
        Map<String, Object> all = new HashMap<>();
        all.put("good", "v");
        all.put("bad", 2.5);
        when(prefs.getAll()).thenAnswer(invocation -> all);
        JSONObject out = (JSONObject) invokePrivate("prefsToJson",
                new Class<?>[]{SharedPreferences.class, boolean.class}, prefs, true);
        assertTrue(out.has("good"));
        assertFalse(out.has("bad"));
    }

    @Test
    public void isGh4aPrefKeyAllowed_prefixes() throws Exception {
        assertTrue(isGh4aPrefKeyAllowed("token_alice"));
        assertTrue(isGh4aPrefKeyAllowed("user_id_123"));
        assertTrue(isGh4aPrefKeyAllowed("search_sort_repos_sort"));
        assertTrue(isGh4aPrefKeyAllowed("start_page"));
        assertFalse(isGh4aPrefKeyAllowed("mirror_list"));
        assertFalse(isGh4aPrefKeyAllowed("unknown_key"));
    }

    @Test
    public void isTranslationKeyAllowed_cases() throws Exception {
        assertTrue(isTranslationKeyAllowed("translation_provider"));
        assertTrue(isTranslationKeyAllowed("translation_cred_migrated_v1"));
        assertTrue(isTranslationKeyAllowed("translation_api_key_youdao"));
        assertTrue(isTranslationKeyAllowed("translation_region_youdao"));
        assertFalse(isTranslationKeyAllowed("translation_url_youdao"));
        assertFalse(isTranslationKeyAllowed("other_key"));
    }

    @Test
    public void isTranslationCredentialKey_cases() throws Exception {
        assertTrue(isTranslationCredentialKey("translation_api_key_youdao"));
        assertTrue(isTranslationCredentialKey("translation_api_secret_baidu"));
        assertFalse(isTranslationCredentialKey("translation_provider"));
        assertFalse(isTranslationCredentialKey("translation_url_youdao"));
        assertFalse(isTranslationCredentialKey("translation_region_x"));
    }

    @Test
    public void collectBackup_drawerKeepsExcludedKeys() throws JSONException {
        mContext.getSharedPreferences("drawer_config", Context.MODE_PRIVATE)
                .edit()
                .putString("order", "abc")
                .putString("token_alice", "ghp_secret")
                .apply();
        JSONObject backup = SettingsBackupManager.collectBackup(mContext);
        JSONObject drawer = backup.getJSONObject("prefs").getJSONObject("drawer_config");
        // drawer 走 excludeAccounts=false：token 键也保留（112 的 false 半边）
        assertEquals("abc", drawer.getJSONObject("order").getString("v"));
        assertTrue(drawer.has("token_alice"));
    }

    @Test
    public void collectBackup_translationSection() throws JSONException {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString("translation_provider", "youdao")
                .putString("translation_region_youdao", "cn-north-1")
                .putString("translation_api_key_youdao", "plain-key")
                .putString("other_key", "x")
                .apply();
        JSONObject backup = SettingsBackupManager.collectBackup(mContext);
        JSONObject def = backup.getJSONObject("prefs").getJSONObject("default");
        assertEquals("youdao", def.getJSONObject("translation_provider").getString("v"));
        assertEquals("cn-north-1", def.getJSONObject("translation_region_youdao").getString("v"));
        assertFalse(def.has("other_key"));
        // 凭据现为明文存储（用户决定 2026-11-15）：以明文导出。
        assertEquals("plain-key", def.getJSONObject("translation_api_key_youdao")
                .getString("v"));
    }

    @Test
    public void collectBackup_exportsStoredPlaintexts() throws JSONException {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString("translation_api_key_baidu", "plain-key")
                .putString("translation_api_secret_baidu", "plain-secret")
                .apply();
        JSONObject backup = SettingsBackupManager.collectBackup(mContext);
        JSONObject def = backup.getJSONObject("prefs").getJSONObject("default");
        assertEquals("plain-key",
                def.getJSONObject("translation_api_key_baidu").getString("v"));
        assertEquals("plain-secret",
                def.getJSONObject("translation_api_secret_baidu").getString("v"));
        assertFalse(def.has("translation_api_key_youdao"));
    }

    @Test
    public void writeBackupFile_success() throws Exception {
        JSONObject backup = new JSONObject("{\"app\":\"OctoDroid\",\"version\":1}");
        ContentResolver resolver = mock(ContentResolver.class);
        Uri uri = Uri.parse("content://com.android.providers.downloads.documents/document/1");
        when(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                any(ContentValues.class))).thenReturn(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(resolver.openOutputStream(uri)).thenReturn(out);
        String name = SettingsBackupManager.writeBackupFile(
                mockResolverContext(resolver), backup);
        assertTrue(name.matches("OctoDroid_backup_\\d{8}_\\d{6}_[0-9a-f]{6}\\.json"));
        assertEquals(backup.toString(), out.toString(StandardCharsets.UTF_8.name()));
    }

    @Test
    public void writeBackupFile_insertNullThrows() throws JSONException {
        JSONObject backup = new JSONObject("{\"app\":\"OctoDroid\",\"version\":1}");
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                any(ContentValues.class))).thenReturn(null);
        try {
            SettingsBackupManager.writeBackupFile(mockResolverContext(resolver), backup);
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("MediaStore insert failed", e.getMessage());
        }
    }

    @Test
    public void writeBackupFile_outputStreamNullThrows() throws Exception {
        JSONObject backup = new JSONObject("{\"app\":\"OctoDroid\",\"version\":1}");
        ContentResolver resolver = mock(ContentResolver.class);
        Uri uri = Uri.parse("content://test/doc/1");
        when(resolver.insert(eq(MediaStore.Downloads.EXTERNAL_CONTENT_URI),
                any(ContentValues.class))).thenReturn(uri);
        when(resolver.openOutputStream(uri)).thenReturn(null);
        try {
            SettingsBackupManager.writeBackupFile(mockResolverContext(resolver), backup);
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("openOutputStream failed", e.getMessage());
        }
    }

    @Test
    public void readBackupFile_nullStreamThrows() throws Exception {
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.openInputStream(any(Uri.class))).thenReturn(null);
        try {
            SettingsBackupManager.readBackupFile(mockResolverContext(resolver),
                    Uri.parse("content://test/backup.json"));
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("openInputStream failed", e.getMessage());
        } catch (JSONException e) {
            fail("expected IOException, got JSONException");
        }
    }

    @Test
    public void readBackupFile_tooLargeThrows() {
        Uri uri = Uri.parse("content://test/huge.json");
        Shadows.shadowOf(mContext.getContentResolver())
                .registerInputStream(uri,
                        new ByteArrayInputStream(new byte[16 * 1024 * 1024 + 1]));
        try {
            SettingsBackupManager.readBackupFile(mContext, uri);
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("Backup file too large"));
        } catch (JSONException e) {
            fail("expected IOException, got JSONException");
        }
    }

    @Test
    public void backupContainsTranslationCredentials_nonObjectHolder() throws JSONException {
        JSONObject def = new JSONObject();
        def.put("translation_api_key_youdao", "plaintext");
        JSONObject prefs = new JSONObject();
        prefs.put("default", def);
        JSONObject backup = new JSONObject();
        backup.put("prefs", prefs);
        assertFalse(SettingsBackupManager.backupContainsTranslationCredentials(backup));
    }

    @Test
    public void applyBackup_emptyGh4aPrefSectionSucceeds() throws JSONException {
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", new JSONObject());
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        // names()==null 提前返回；version 缺失 → 写回 4
        assertEquals(4, gh4aPrefs().getInt("version", -1));
    }

    @Test
    public void applyBackup_correctTypeThemeRestores() throws JSONException {
        JSONObject gh4a = new JSONObject();
        gh4a.put("theme", holder("i", 2));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertEquals(2, gh4aPrefs().getInt("theme", -1));
    }

    @Test
    public void applyBackup_longFloatBoolTypesRestore() throws JSONException {
        JSONObject gh4a = new JSONObject();
        gh4a.put("last_notification_check", holder("l", 123456789L));
        gh4a.put("webview_initial_zoom", holder("f", 1.5));
        gh4a.put("notifications", holder("b", true));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertEquals(123456789L, gh4aPrefs().getLong("last_notification_check", -1));
        assertEquals(1.5f, gh4aPrefs().getFloat("webview_initial_zoom", -1f), 0f);
        assertTrue(gh4aPrefs().getBoolean("notifications", false));
    }

    @Test
    public void applyBackup_drawerSetRestores() throws JSONException {
        JSONObject drawer = new JSONObject();
        drawer.put("hidden", holder("set", new JSONArray(Arrays.asList("a", "b"))));
        JSONObject prefs = new JSONObject();
        prefs.put("drawer_config", drawer);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        Set<String> hidden = mContext.getSharedPreferences("drawer_config", Context.MODE_PRIVATE)
                .getStringSet("hidden", null);
        assertNotNull(hidden);
        assertTrue(hidden.contains("a"));
        assertTrue(hidden.contains("b"));
    }

    @Test
    public void applyBackup_unknownTypeDropped() throws JSONException {
        JSONObject gh4a = new JSONObject();
        gh4a.put("start_page", holder("x", "?"));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertFalse(gh4aPrefs().contains("start_page"));
    }

    @Test
    public void applyBackup_versionStringBumpsToFour() throws JSONException {
        JSONObject gh4a = new JSONObject();
        gh4a.put("version", holder("s", "3"));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertEquals(4, gh4aPrefs().getInt("version", -1));
    }

    @Test
    public void applyBackup_versionFourStays() throws JSONException {
        JSONObject gh4a = new JSONObject();
        gh4a.put("version", holder("i", 4));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertEquals(4, gh4aPrefs().getInt("version", -1));
    }

    @Test
    public void applyBackup_intUserIdMigratedToLong() throws JSONException {
        gh4aPrefs().edit().putInt("user_id_123", 456).apply();
        JSONObject gh4a = new JSONObject();
        gh4a.put("start_page", holder("s", "dark"));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertEquals(456L, gh4aPrefs().getLong("user_id_123", -1));
        assertTrue(gh4aPrefs().getAll().get("user_id_123") instanceof Long);
    }

    @Test
    public void applyBackup_alreadyLongUserIdNotMigrated() throws JSONException {
        gh4aPrefs().edit().putLong("user_id_456", 789L).apply();
        JSONObject gh4a = new JSONObject();
        gh4a.put("start_page", holder("s", "dark"));
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertEquals(789L, gh4aPrefs().getLong("user_id_456", -1));
    }

    @Test
    public void applyBackup_defaultSectionWritesCredAndPlainToDefaultPrefs() throws JSONException {
        JSONObject def = new JSONObject();
        def.put("translation_provider", holder("s", "youdao"));
        def.put("translation_region_youdao", holder("s", "cn-north-1"));
        def.put("translation_url_youdao", holder("s", "https://evil.example.com"));
        def.put("translation_api_key_youdao", holder("s", "v1:YWJj"));
        def.put("translation_api_secret_deepl", holder("s", "v1:ZGVm"));
        JSONObject prefs = new JSONObject();
        prefs.put("default", def);
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        SharedPreferences defPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        assertEquals("youdao", defPrefs.getString("translation_provider", null));
        assertEquals("cn-north-1", defPrefs.getString("translation_region_youdao", null));
        assertFalse(defPrefs.contains("translation_url_youdao"));
        // 凭据与其它翻译键一样写默认 prefs（明文存储）；旧备份的密文值原样写入。
        assertEquals("v1:YWJj", defPrefs.getString("translation_api_key_youdao", null));
        assertEquals("v1:ZGVm", defPrefs.getString("translation_api_secret_deepl", null));
        SharedPreferences secure = Gh4Application.securePrefs(mContext);
        assertFalse(secure.contains("translation_api_key_youdao"));
        assertFalse(secure.contains("translation_api_secret_deepl"));
    }

    @Test
    public void applyBackup_emptyDefaultSectionSucceeds() throws JSONException {
        JSONObject prefs = new JSONObject();
        prefs.put("default", new JSONObject());
        assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
    }

    @Test
    public void applyBackup_prefsNotObjectReturnsFalse() throws JSONException {
        JSONObject backup = new JSONObject();
        backup.put("app", "OctoDroid");
        backup.put("version", 1);
        backup.put("prefs", "not-an-object");
        assertFalse(SettingsBackupManager.applyBackup(mContext, backup));
    }

    @Test
    public void applyBackup_badDefaultHolderRollsBack() throws JSONException {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString("translation_provider", "deepl").apply();
        Gh4Application.securePrefs(mContext).edit()
                .putString("translation_api_key_youdao", "v1:c2VlZA==").apply();
        JSONObject def = new JSONObject();
        def.put("translation_provider", "not-an-object");
        JSONObject prefs = new JSONObject();
        prefs.put("default", def);
        assertFalse(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        // 快照回滚：default 与 secure 的旧值都在
        assertEquals("deepl", PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("translation_provider", null));
        assertEquals("v1:c2VlZA==", Gh4Application.securePrefs(mContext)
                .getString("translation_api_key_youdao", null));
    }

    @Test
    public void applyBackup_rollbackRestoresAllTypes() throws JSONException {
        gh4aPrefs().edit()
                .putString("language", "en")
                .putInt("theme", 2)
                .putLong("last_notification_check", 999L)
                .putFloat("webview_initial_zoom", 1.5f)
                .putBoolean("notifications", true)
                .putStringSet("last_notification_repo_ids",
                        new HashSet<>(Arrays.asList("a", "b")))
                .apply();
        mContext.getSharedPreferences("drawer_config", Context.MODE_PRIVATE).edit()
                .putString("order", "abc").apply();
        JSONObject gh4a = new JSONObject();
        gh4a.put("start_page", holder("s", "dark"));
        JSONObject drawer = new JSONObject();
        drawer.put("order", "not-an-object");
        JSONObject prefs = new JSONObject();
        prefs.put("Gh4a-pref", gh4a);
        prefs.put("drawer_config", drawer);
        assertFalse(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
        assertFalse(gh4aPrefs().contains("start_page"));
        assertEquals("en", gh4aPrefs().getString("language", null));
        assertEquals(2, gh4aPrefs().getInt("theme", -1));
        assertEquals(999L, gh4aPrefs().getLong("last_notification_check", -1));
        assertEquals(1.5f, gh4aPrefs().getFloat("webview_initial_zoom", -1f), 0f);
        assertTrue(gh4aPrefs().getBoolean("notifications", false));
        Set<String> ids = gh4aPrefs().getStringSet("last_notification_repo_ids", null);
        assertNotNull(ids);
        assertTrue(ids.contains("a"));
        assertTrue(ids.contains("b"));
        assertEquals("abc", mContext.getSharedPreferences("drawer_config", Context.MODE_PRIVATE)
                .getString("order", null));
    }
    @Test
    public void collectBackup_unsupportedValueTypeIsSkipped() throws JSONException {
        // L328：默认 prefs 里 translation_ 键的值是 Double（putTypedValue
        // 不支持的类型）时该键不进入备份；String 键正常进入。
        SharedPreferences mockPrefs = mock(SharedPreferences.class);
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("translation_foo", 3.14);
        map.put("translation_provider", "google");
        doReturn(map).when(mockPrefs).getAll();
        when(mockPrefs.getString(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        SharedPreferences.Editor mockEditor = mock(SharedPreferences.Editor.class);
        when(mockEditor.putString(any(), any())).thenReturn(mockEditor);
        when(mockEditor.remove(any())).thenReturn(mockEditor);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        Context wrapper = new ContextWrapper(mContext) {
            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                if ((mContext.getPackageName() + "_preferences").equals(name)) {
                    return mockPrefs;
                }
                return super.getSharedPreferences(name, mode);
            }
        };
        JSONObject backup = SettingsBackupManager.collectBackup(wrapper);
        JSONObject def = backup.getJSONObject("prefs").getJSONObject("default");
        assertFalse(def.has("translation_foo"));
        assertTrue(def.has("translation_provider"));
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 28)
    public void writeBackupFile_preQ_writesToDownloadsDir() throws Exception {
        // L357/L374-380：SDK 28 下走 pre-Q 文件分支，写进外部存储 Downloads 目录。
        JSONObject backup = new JSONObject().put("app", "OctoDroid");
        String fileName = SettingsBackupManager.writeBackupFile(mContext, backup);
        assertTrue(fileName.startsWith("OctoDroid_backup_"));
        assertTrue(fileName.endsWith(".json"));
        java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
        assertTrue(new java.io.File(dir, fileName).exists());
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 28)
    public void writeBackupFile_preQ_mkdirsFailureThrows() throws Exception {
        // L375-376：Downloads 目录不存在且 mkdirs() 失败时抛 IOException。
        // 用 mockStatic(Environment) 把 Downloads 目录指到一个真实文件之下
        // （<临时文件>/Download）：父路径是文件时 mkdirs() 恒返回 false。
        // 不用 chmod 拦：测试以 root 运行，权限位拦不住 mkdir。
        JSONObject backup = new JSONObject().put("app", "OctoDroid");
        java.io.File blocker = java.io.File.createTempFile("octo-block", ".tmp");
        java.io.File badDir = new java.io.File(blocker, "Download");
        try (MockedStatic<android.os.Environment> mocked =
                mockStatic(android.os.Environment.class)) {
            mocked.when(() -> android.os.Environment.getExternalStoragePublicDirectory(
                    anyString())).thenReturn(badDir);
            try {
                SettingsBackupManager.writeBackupFile(mContext, backup);
                fail("expected IOException");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("mkdirs failed"));
            }
        } finally {
            blocker.delete();
        }
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 28)
    public void writeBackupFile_preQ_mkdirsCreatesMissingDir() throws Exception {
        // L375：Downloads 目录不存在但 mkdirs() 成功时不抛错、正常写文件。
        // 与 mkdirsFailureThrows 对偶：覆盖 !dir.exists() 为真且
        // !dir.mkdirs() 为假的那条边（此前只有 exists 为真、mkdirs 为假
        // 两条边被覆盖）。
        JSONObject backup = new JSONObject().put("app", "OctoDroid");
        java.io.File parent =
                java.nio.file.Files.createTempDirectory("octo-dl-").toFile();
        java.io.File missingDir = new java.io.File(parent, "Download");
        assertFalse(missingDir.exists());
        java.io.File written = null;
        try (MockedStatic<android.os.Environment> mocked =
                mockStatic(android.os.Environment.class)) {
            mocked.when(() -> android.os.Environment.getExternalStoragePublicDirectory(
                    anyString())).thenReturn(missingDir);
            String name = SettingsBackupManager.writeBackupFile(mContext, backup);
            assertTrue(name.startsWith("OctoDroid_backup_"));
            written = new java.io.File(missingDir, name);
            assertTrue(written.exists());
            assertEquals(backup.toString(),
                    new String(java.nio.file.Files.readAllBytes(written.toPath()),
                            StandardCharsets.UTF_8));
        } finally {
            if (written != null) {
                written.delete();
            }
            missingDir.delete();
            parent.delete();
        }
    }

    @Test
    public void putCredentialPlaintext_nullPlaintextIsSkipped() throws Exception {
        // L237：plaintext 为 null 时直接返回，不写键。生产路径上
        // TranslationManager.getApiKey/getApiSecret 最低返回 ""，null 只在
        // 防御性判空里出现；此处直接调私有方法覆盖 null 边。
        JSONObject translation = new JSONObject();
        invokePrivate("putCredentialPlaintext",
                new Class<?>[]{JSONObject.class, String.class, String.class},
                translation, "translation_api_key_youdao", null);
        assertFalse(translation.has("translation_api_key_youdao"));
    }

    @Test
    public void applyBackup_noApplicationInstance_stillApplies() throws Exception {
        // L539：Gh4Application.get() 为 null 时跳过 rescheduleWorkers，
        // 其余恢复照常完成并返回 true（通知渠道创建不依赖 Application 实例）。
        java.lang.reflect.Field f =
                Gh4Application.class.getDeclaredField("sInstance");
        f.setAccessible(true);
        Object old = f.get(null);
        try {
            f.set(null, null);
            // 注意：类型码是 "i" 不是 "int"——jsonToPrefs 的 switch 只认
            // "s"/"i"/"l"/"f"/"b"/"set"，第 1 轮误写 "int" 会被静默丢弃，
            // applyBackup 返回 true 但 theme 写不进去（单跑 expected 2 / was -1）。
            JSONObject prefs = new JSONObject();
            prefs.put("Gh4a-pref", new JSONObject()
                    .put("theme", holder("i", 2)));
            assertTrue(SettingsBackupManager.applyBackup(mContext, backupWithPrefs(prefs)));
            assertEquals(2, gh4aPrefs().getInt("theme", -1));
        } finally {
            f.set(null, old);
        }
    }

}
