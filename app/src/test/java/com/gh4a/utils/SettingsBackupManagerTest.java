package com.gh4a.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.fragment.SettingsFragment;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowContentResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * R-6（A 方案）：备份政策的回归网——凭据类键永不进入手动备份。
 * 将来新增凭据类设置键时，先在这里补断言，再改 isExcluded()。
 *
 * B 组试点扩充：backupContainsTranslationCredentials（纯 JSON）、
 * applyBackup 的格式校验路径、readBackupFile、collectBackup 回合。
 * writeBackupFile 依赖 MediaStore 真实落盘，Robolectric 下不覆盖。
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
        // user_id_ 前缀检查是大小写敏感的（原始 key 直接 startsWith），
        // 大写 USER_ID_ALICE 不排除；与 token 检查（先转小写）口径不一致，
        // 但真实 key 均为小写写入，此处锁定现状。
        assertFalse(SettingsBackupManager.isExcluded("USER_ID_ALICE"));
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
}
