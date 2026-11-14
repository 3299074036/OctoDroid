package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 56：镜像 URL 校验的非规范回环形必须被拦截（isValidMirrorUrl 经
 * isLoopbackOrPrivateHost 归一化判定）。域名一律放行、不断言 DNS 结果。
 */
@RunWith(RobolectricTestRunner.class)
public class MirrorHelperTest {

    @Test
    public void standardLoopbackAndPrivateRejected() {
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.1.2.3/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://10.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://172.16.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://192.168.1.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://169.254.1.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://0.0.0.0/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://localhost/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[fe80::1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[fc00::1]/x"));
    }

    @Test
    public void nonCanonicalLoopbackRejected() {
        // 单段十进制 = 127.0.0.1；八进制 0177 = 127；十六进制 0x7f = 127。
        assertFalse(MirrorHelper.isValidMirrorUrl("https://2130706433/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://0177.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://0x7f.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.1/x"));
        // 展开形 IPv6：::1 与 ::ffff:127.0.0.1 的展开写法。
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[0:0:0:0:0:0:0:1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[0:0:0:0:0:ffff:127.0.0.1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::ffff:127.0.0.1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::ffff:10.0.0.1]/x"));
    }

    @Test
    public void publicHostsAndDomainsAllowed() {
        assertTrue(MirrorHelper.isValidMirrorUrl("https://mirror.example.com/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://8.8.8.8/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://1.2.3.4/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://[2001:db8::1]/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://[::ffff:8.8.8.8]/x"));
    }

    @Test
    public void nonHttpsRejected() {
        assertFalse(MirrorHelper.isValidMirrorUrl("http://8.8.8.8/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl(null));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://"));
    }

    @Test
    public void userinfoUrlValidationIsAboutHostNotCredentials() {
        // 269（关联第四轮 214）：https://user:password@host/ 能通过校验——
        // 校验只判定 host 是否回环/内网，不管 userinfo；凭据进不进日志/
        // 崩溃文件是 redactUrlForLog（214）的职责，不归校验管。
        // 公共 host + userinfo 仍放行；回环 host + userinfo 仍拦截。
        assertTrue(MirrorHelper.isValidMirrorUrl(
                "https://user:password@mirror.example.com/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl(
                "https://user:password@127.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl(
                "https://user:password@192.168.1.1/x"));
    }

    // ---- B 组试点扩充 ----

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context().getSharedPreferences(SettingsFragment.PREF_NAME,
                Context.MODE_PRIVATE);
    }

    @Test
    public void normalizeUrl_cases() {
        assertEquals("", MirrorHelper.normalizeUrl(null));
        assertEquals("", MirrorHelper.normalizeUrl(""));
        assertEquals("", MirrorHelper.normalizeUrl("   "));
        assertEquals("https://x.com", MirrorHelper.normalizeUrl("  https://x.com/  "));
        assertEquals("https://x.com", MirrorHelper.normalizeUrl("https://x.com///"));
        assertEquals("", MirrorHelper.normalizeUrl("/"));
        assertEquals("https://x.com/a", MirrorHelper.normalizeUrl("https://x.com/a"));
    }

    @Test
    public void displayHost_cases() {
        assertEquals("mirror.example.com",
                MirrorHelper.displayHost("https://mirror.example.com/x"));
        assertEquals("::1", MirrorHelper.displayHost("https://[::1]/x"));
        assertEquals("host.com",
                MirrorHelper.displayHost("https://user:pw@host.com:8443/x"));
        assertEquals("host.com", MirrorHelper.displayHost("https://host.com?x=1"));
        assertEquals("", MirrorHelper.displayHost(null));
        assertEquals("not a url", MirrorHelper.displayHost("not a url"));
    }

    @Test
    public void isValidMirrorUrl_moreEdges() {
        assertFalse(MirrorHelper.isValidMirrorUrl(""));
        assertFalse(MirrorHelper.isValidMirrorUrl("   "));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.0.0.1./x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://localhost./x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.0.0.1\\evil.com/"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://１２７．０．０．１/"));
        assertFalse(MirrorHelper.isValidMirrorUrl("HTTPS://127.0.0.1/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://exa mple.com/"));
        assertTrue(MirrorHelper.isValidMirrorUrl("HTTPS://mirror.example.com/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://mirror.example.com:8443/x"));
    }

    @Test
    public void isEnabled_defaultFalseAndToggle() {
        assertFalse(MirrorHelper.isEnabled(context()));
        prefs().edit().putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true).apply();
        assertTrue(MirrorHelper.isEnabled(context()));
    }

    @Test
    public void getMirrorBase_defaultPreset() {
        assertEquals("https://gh-proxy.com", MirrorHelper.getMirrorBase(context()));
    }

    @Test
    public void getMirrorBase_deadPresetMigratesToDefault() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET,
                "https://mirror.ghproxy.com").apply();
        assertEquals("https://gh-proxy.com", MirrorHelper.getMirrorBase(context()));
        assertEquals("https://gh-proxy.com",
                prefs().getString(MirrorHelper.PREF_MIRROR_PRESET, null));
    }

    @Test
    public void getMirrorBase_customPresetMigratesToDefault() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET, "custom").apply();
        assertEquals("https://gh-proxy.com", MirrorHelper.getMirrorBase(context()));
    }

    @Test
    public void getMirrorBase_invalidPresetReturnsEmpty() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET, "http://x.com").apply();
        assertEquals("", MirrorHelper.getMirrorBase(context()));
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET,
                "https://127.0.0.1").apply();
        assertEquals("", MirrorHelper.getMirrorBase(context()));
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET, "not a url").apply();
        assertEquals("", MirrorHelper.getMirrorBase(context()));
    }

    @Test
    public void getMirrorBase_stripsTrailingSlash() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET,
                "https://mirror.example.com/").apply();
        assertEquals("https://mirror.example.com", MirrorHelper.getMirrorBase(context()));
    }

    @Test
    public void getMirrorUrls_seedsFromPresetsOnce() {
        List<String> urls = MirrorHelper.getMirrorUrls(context());
        // 播种 = mirror_preset_values 全量减 custom 项（数组当前 72 项，
        // 含 1 项 custom；数量随数组走，不硬编码，避免数组扩容后误报）。
        int expected = context().getResources()
                .getStringArray(R.array.mirror_preset_values).length - 1;
        assertEquals(expected, urls.size());
        assertTrue(urls.contains("https://gh-proxy.com"));
        assertFalse(urls.contains("custom"));
        for (String u : urls) {
            assertTrue(u, MirrorHelper.isValidMirrorUrl(u));
        }
        // 第二次返回同一份（已持久化，不再播种）
        assertEquals(urls, MirrorHelper.getMirrorUrls(context()));
    }

    @Test
    public void getMirrorUrls_badJsonReturnsEmpty() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST, "not-json{{").apply();
        assertTrue(MirrorHelper.getMirrorUrls(context()).isEmpty());
    }

    @Test
    public void getMirrorUrls_filtersInvalidStoredEntries() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://good.example.com\", \"http://bad.example.com\","
                        + " \"https://127.0.0.1\", \"https://good.example.com\"]").apply();
        List<String> urls = MirrorHelper.getMirrorUrls(context());
        assertEquals(1, urls.size());
        assertEquals("https://good.example.com", urls.get(0));
    }

    @Test
    public void addMirrorUrl_addDedupRejectInvalid() {
        MirrorHelper.addMirrorUrl(context(), "https://new.example.com/");
        assertTrue(MirrorHelper.getMirrorUrls(context())
                .contains("https://new.example.com"));
        int size = MirrorHelper.getMirrorUrls(context()).size();
        MirrorHelper.addMirrorUrl(context(), "HTTPS://NEW.EXAMPLE.COM");
        assertEquals(size, MirrorHelper.getMirrorUrls(context()).size());
        MirrorHelper.addMirrorUrl(context(), "http://bad.example.com");
        MirrorHelper.addMirrorUrl(context(), "https://10.0.0.1");
        MirrorHelper.addMirrorUrl(context(), null);
        assertEquals(size, MirrorHelper.getMirrorUrls(context()).size());
    }

    @Test
    public void updateMirrorUrl_replaceAppendAndRejectInvalid() {
        MirrorHelper.addMirrorUrl(context(), "https://old.example.com");
        MirrorHelper.updateMirrorUrl(context(), "https://OLD.example.com",
                "https://new.example.com/");
        List<String> urls = MirrorHelper.getMirrorUrls(context());
        assertFalse(urls.contains("https://old.example.com"));
        assertTrue(urls.contains("https://new.example.com"));
        int size = urls.size();
        // 更新不存在的旧地址 → 追加
        MirrorHelper.updateMirrorUrl(context(), "https://absent.example.com",
                "https://added.example.com");
        assertEquals(size + 1, MirrorHelper.getMirrorUrls(context()).size());
        // 新地址非法 → 原列表不动
        MirrorHelper.updateMirrorUrl(context(), "https://added.example.com",
                "https://192.168.0.1");
        assertTrue(MirrorHelper.getMirrorUrls(context())
                .contains("https://added.example.com"));
    }

    @Test
    public void updateMirrorUrl_newAlreadyExistsRemovesOldOnly() {
        MirrorHelper.addMirrorUrl(context(), "https://a.example.com");
        MirrorHelper.addMirrorUrl(context(), "https://b.example.com");
        MirrorHelper.updateMirrorUrl(context(), "https://a.example.com",
                "https://b.example.com");
        List<String> urls = MirrorHelper.getMirrorUrls(context());
        assertFalse(urls.contains("https://a.example.com"));
        assertEquals(1, Collections.frequency(urls, "https://b.example.com"));
    }

    @Test
    public void removeMirrorUrl_caseInsensitiveAndAbsent() {
        MirrorHelper.addMirrorUrl(context(), "https://gone.example.com");
        MirrorHelper.removeMirrorUrl(context(), "HTTPS://GONE.EXAMPLE.COM");
        assertFalse(MirrorHelper.getMirrorUrls(context())
                .contains("https://gone.example.com"));
        int size = MirrorHelper.getMirrorUrls(context()).size();
        MirrorHelper.removeMirrorUrl(context(), "https://absent.example.com");
        assertEquals(size, MirrorHelper.getMirrorUrls(context()).size());
    }

    @Test
    public void rewriteUrlWithBase_mirrorableHosts() {
        String base = "https://gh-proxy.com";
        assertEquals(base + "/https://raw.githubusercontent.com/o/r/main/f.txt",
                MirrorHelper.rewriteUrlWithBase(base,
                        "https://raw.githubusercontent.com/o/r/main/f.txt"));
        assertEquals(base + "/https://avatars.githubusercontent.com/u/1?v=4",
                MirrorHelper.rewriteUrlWithBase(base,
                        "https://avatars.githubusercontent.com/u/1?v=4"));
        assertEquals(base + "/https://codeload.github.com/o/r/tar.gz/refs/tags/v1",
                MirrorHelper.rewriteUrlWithBase(base,
                        "https://codeload.github.com/o/r/tar.gz/refs/tags/v1"));
    }

    @Test
    public void rewriteUrlWithBase_githubReleasePathOnly() {
        String base = "https://gh-proxy.com";
        assertEquals(base + "/https://github.com/o/r/releases/download/v1/app.apk",
                MirrorHelper.rewriteUrlWithBase(base,
                        "https://github.com/o/r/releases/download/v1/app.apk"));
        assertEquals("https://github.com/o/r/issues/1",
                MirrorHelper.rewriteUrlWithBase(base, "https://github.com/o/r/issues/1"));
        assertEquals("https://github.com/o/r",
                MirrorHelper.rewriteUrlWithBase(base, "https://github.com/o/r"));
    }

    @Test
    public void rewriteUrlWithBase_nonMirrorableAndAlreadyMirrored() {
        String base = "https://gh-proxy.com";
        assertEquals("https://api.github.com/repos/o/r",
                MirrorHelper.rewriteUrlWithBase(base, "https://api.github.com/repos/o/r"));
        assertEquals("https://example.com/x.png",
                MirrorHelper.rewriteUrlWithBase(base, "https://example.com/x.png"));
        assertEquals(base + "/https://raw.githubusercontent.com/o/r/main/f",
                MirrorHelper.rewriteUrlWithBase(base,
                        base + "/https://raw.githubusercontent.com/o/r/main/f"));
        // 已是镜像地址（大小写不敏感比较）时原样返回，不再加前缀 (L-7)
        assertEquals("HTTPS://GH-PROXY.COM/https://raw.githubusercontent.com/o/r/main/f",
                MirrorHelper.rewriteUrlWithBase(base,
                        "HTTPS://GH-PROXY.COM/https://raw.githubusercontent.com/o/r/main/f"));
    }

    @Test
    public void rewriteUrlWithBase_nullAndEmptyBase() {
        assertNull(MirrorHelper.rewriteUrlWithBase("https://gh-proxy.com", null));
        assertEquals("https://raw.githubusercontent.com/o/r/main/f",
                MirrorHelper.rewriteUrlWithBase(null,
                        "https://raw.githubusercontent.com/o/r/main/f"));
        assertEquals("https://raw.githubusercontent.com/o/r/main/f",
                MirrorHelper.rewriteUrlWithBase("",
                        "https://raw.githubusercontent.com/o/r/main/f"));
    }

    @Test
    public void rewriteUrl_disabledReturnsOriginal() {
        String url = "https://raw.githubusercontent.com/o/r/main/f.txt";
        assertEquals(url, MirrorHelper.rewriteUrl(context(), url));
        assertNull(MirrorHelper.rewriteUrl(context(), null));
    }

    @Test
    public void rewriteUrl_enabledRewritesAndInvalidBasePassesThrough() {
        prefs().edit().putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true).apply();
        String url = "https://raw.githubusercontent.com/o/r/main/f.txt";
        assertEquals("https://gh-proxy.com/" + url, MirrorHelper.rewriteUrl(context(), url));
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET, "http://bad").apply();
        assertEquals(url, MirrorHelper.rewriteUrl(context(), url));
    }

    @Test
    public void buildFallbackChain_downloadSemantics() {
        String url = "https://raw.githubusercontent.com/o/r/main/f.txt";
        // 未开镜像：[直连, 默认镜像]
        List<String> chain = MirrorHelper.buildFallbackChain(context(), url, false);
        assertEquals(2, chain.size());
        assertEquals(url, chain.get(0));
        assertEquals("https://gh-proxy.com/" + url, chain.get(1));
        // 开镜像：[自选镜像, 默认镜像, 直连]（自选=默认时去重）
        prefs().edit().putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true).apply();
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET,
                "https://mirror.example.com").apply();
        chain = MirrorHelper.buildFallbackChain(context(), url, false);
        assertEquals("https://mirror.example.com/" + url, chain.get(0));
        assertEquals("https://gh-proxy.com/" + url, chain.get(1));
        assertEquals(url, chain.get(2));
    }

    @Test
    public void buildFallbackChain_blindPrefixSemantics() {
        String url = "https://api.github.com/repos/o/r";
        // 未开镜像：[默认镜像盲拼, 直连]
        List<String> chain = MirrorHelper.buildFallbackChain(context(), url, true);
        assertEquals(2, chain.size());
        assertEquals("https://gh-proxy.com/" + url, chain.get(0));
        assertEquals(url, chain.get(1));
        // 开镜像：[自选盲拼, 默认盲拼, 直连]
        prefs().edit().putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true).apply();
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_PRESET,
                "https://mirror.example.com").apply();
        chain = MirrorHelper.buildFallbackChain(context(), url, true);
        assertEquals("https://mirror.example.com/" + url, chain.get(0));
        assertEquals("https://gh-proxy.com/" + url, chain.get(1));
        assertEquals(url, chain.get(2));
    }

    @Test
    public void mirrorSpeedResult_isOk() {
        assertTrue(new MirrorHelper.MirrorSpeedResult("n", "https://a.example.com", 0).isOk());
        assertTrue(new MirrorHelper.MirrorSpeedResult("n", "https://a.example.com", 123).isOk());
        assertFalse(new MirrorHelper.MirrorSpeedResult("n", "https://a.example.com", -1).isOk());
        MirrorHelper.MirrorSpeedResult r =
                new MirrorHelper.MirrorSpeedResult("n", "https://a.example.com", 5);
        assertEquals("https://a.example.com", r.presetValue);
        MirrorHelper.MirrorSpeedResult r2 = new MirrorHelper.MirrorSpeedResult(
                "n", "https://a.example.com", "https://preset.example.com", 5);
        assertEquals("https://preset.example.com", r2.presetValue);
    }

    @Test
    public void speedTestHandle_cancelBeforeEmptyTargets() {
        // 空目标列表：onAllComplete 经主线程 Handler 投递；先 cancel 则不回调。
        final boolean[] allComplete = {false};
        MirrorHelper.SpeedTestCallback cb = new MirrorHelper.SpeedTestCallback() {
            @Override
            public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
            }

            @Override
            public void onAllComplete() {
                allComplete[0] = true;
            }
        };
        MirrorHelper.SpeedTestHandle handle = MirrorHelper.testAllMirrorsSpeed(
                context(), new ArrayList<>(), cb);
        assertFalse(handle.isCancelled());
        handle.cancel();
        assertTrue(handle.isCancelled());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(allComplete[0]);
    }

    @Test
    public void speedTestHandle_emptyTargetsCallsOnAllComplete() {
        final boolean[] allComplete = {false};
        MirrorHelper.SpeedTestCallback cb = new MirrorHelper.SpeedTestCallback() {
            @Override
            public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
            }

            @Override
            public void onAllComplete() {
                allComplete[0] = true;
            }
        };
        MirrorHelper.testAllMirrorsSpeed(context(), new ArrayList<>(), cb);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(allComplete[0]);
    }

    @Test
    public void getSpeedTestTargets_matchesMirrorList() {
        List<MirrorHelper.MirrorSpeedResult> targets =
                MirrorHelper.getSpeedTestTargets(context());
        List<String> urls = MirrorHelper.getMirrorUrls(context());
        assertEquals(urls.size(), targets.size());
        for (int i = 0; i < urls.size(); i++) {
            assertEquals(urls.get(i), targets.get(i).url);
            assertEquals(-1L, targets.get(i).latencyMs);
            assertEquals(MirrorHelper.displayHost(urls.get(i)), targets.get(i).name);
        }
    }

    @Test
    public void saveAndGetCachedSpeedResults() {
        assertTrue(MirrorHelper.getCachedSpeedResults(context()).isEmpty());
        assertEquals(0L, MirrorHelper.getSpeedTestTime(context()));
        List<MirrorHelper.MirrorSpeedResult> results = new ArrayList<>();
        results.add(new MirrorHelper.MirrorSpeedResult("a", "https://a.example.com", 120));
        results.add(new MirrorHelper.MirrorSpeedResult("b", "https://b.example.com", -1));
        results.add(new MirrorHelper.MirrorSpeedResult("empty", "", 5));
        MirrorHelper.saveSpeedTestResults(context(), results);
        Map<String, Long> cached = MirrorHelper.getCachedSpeedResults(context());
        assertEquals(2, cached.size());
        assertEquals(Long.valueOf(120), cached.get("https://a.example.com"));
        assertEquals(Long.valueOf(-1), cached.get("https://b.example.com"));
        assertTrue(MirrorHelper.getSpeedTestTime(context()) > 0);
    }

    @Test
    public void removeSpeedTestResult_removesOnlyTarget() {
        List<MirrorHelper.MirrorSpeedResult> results = new ArrayList<>();
        results.add(new MirrorHelper.MirrorSpeedResult("a", "https://a.example.com", 120));
        results.add(new MirrorHelper.MirrorSpeedResult("b", "https://b.example.com", 50));
        MirrorHelper.saveSpeedTestResults(context(), results);
        MirrorHelper.removeSpeedTestResult(context(), "https://A.example.com/");
        Map<String, Long> cached = MirrorHelper.getCachedSpeedResults(context());
        assertEquals(1, cached.size());
        assertEquals(Long.valueOf(50), cached.get("https://b.example.com"));
        // 删不存在的、空串均不动
        MirrorHelper.removeSpeedTestResult(context(), "https://absent.example.com");
        MirrorHelper.removeSpeedTestResult(context(), "");
        assertEquals(1, MirrorHelper.getCachedSpeedResults(context()).size());
    }

    @Test
    public void removeMirrorUrl_alsoClearsSpeedCache() {
        MirrorHelper.addMirrorUrl(context(), "https://gone.example.com");
        List<MirrorHelper.MirrorSpeedResult> results = new ArrayList<>();
        results.add(new MirrorHelper.MirrorSpeedResult("g", "https://gone.example.com", 10));
        MirrorHelper.saveSpeedTestResults(context(), results);
        MirrorHelper.removeMirrorUrl(context(), "https://gone.example.com");
        assertTrue(MirrorHelper.getCachedSpeedResults(context()).isEmpty());
    }

    @Test
    public void migrateLegacyCustomUrl_movesToList() {
        prefs().edit()
                .putString("mirror_custom_url", "https://legacy.example.com/")
                .putString(MirrorHelper.PREF_MIRROR_PRESET, "custom")
                .apply();
        // 任意一次读操作都会触发迁移
        assertEquals("https://legacy.example.com", MirrorHelper.getMirrorBase(context()));
        assertFalse(prefs().contains("mirror_custom_url"));
        assertTrue(MirrorHelper.getMirrorUrls(context())
                .contains("https://legacy.example.com"));
    }

    @Test
    public void migrateLegacyCustomUrl_emptyCustomResetsPreset() {
        prefs().edit()
                .putString("mirror_custom_url", "")
                .putString(MirrorHelper.PREF_MIRROR_PRESET, "custom")
                .apply();
        assertEquals("https://gh-proxy.com", MirrorHelper.getMirrorBase(context()));
        assertFalse(prefs().contains("mirror_custom_url"));
    }
}
