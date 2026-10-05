package com.gh4a.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * R-6（A 方案）：备份政策的回归网——凭据类键永不进入手动备份。
 * 将来新增凭据类设置键时，先在这里补断言，再改 isExcluded()。
 */
public class SettingsBackupManagerTest {

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
}
