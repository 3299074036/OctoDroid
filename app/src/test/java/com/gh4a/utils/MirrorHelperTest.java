package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    // ---------- 补缺批（2026-11-15）：逐行缺口对照 JaCoCo ----------

    @Before
    public void clearMirrorPrefs() {
        prefs().edit()
                .remove(MirrorHelper.PREF_MIRROR_ENABLED)
                .remove(MirrorHelper.PREF_MIRROR_PRESET)
                .remove(MirrorHelper.PREF_MIRROR_LIST)
                .remove(MirrorHelper.PREF_MIRROR_CUSTOM_URL)
                .remove("mirror_speed_cache")
                .remove("mirror_speed_cache_time")
                .apply();
    }

    private void seedMirrorList(String... urls) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < urls.length; i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(urls[i]).append("\"");
        }
        json.append("]");
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST, json.toString()).apply();
    }

    private void seedSpeedCache(String... entries) {
        prefs().edit().putStringSet("mirror_speed_cache",
                new HashSet<>(Arrays.asList(entries))).apply();
    }

    private static Object invokePrivate(String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = MirrorHelper.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static Long parseNumericIPv4(String host) throws Exception {
        return (Long) invokePrivate("parseNumericIPv4",
                new Class<?>[]{String.class}, host);
    }

    private static Long embeddedIPv4(byte[] b) throws Exception {
        return (Long) invokePrivate("embeddedIPv4",
                new Class<?>[]{byte[].class}, (Object) b);
    }

    private static boolean isUniqueLocalIPv6(byte[] b) throws Exception {
        return (Boolean) invokePrivate("isUniqueLocalIPv6",
                new Class<?>[]{byte[].class}, (Object) b);
    }

    private static boolean isLoopbackOrPrivateHost(String host) throws Exception {
        return (Boolean) invokePrivate("isLoopbackOrPrivateHost",
                new Class<?>[]{String.class}, host);
    }

    private static byte[] ipv6Bytes(String literal) throws Exception {
        return InetAddress.getByName(literal).getAddress();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            return s.getLocalPort();
        }
    }

    private static boolean waitForLatch(CountDownLatch latch, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (latch.getCount() == 0) {
                return true;
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return latch.getCount() == 0;
    }

    /** 本地 HTTP 服务器：测速探针的确定性目标，不走外网。 */
    private static class LocalHttpServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;
        private final boolean hang;
        private final int status;
        private final byte[] body;

        LocalHttpServer(boolean hang, int status, byte[] body) throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            this.hang = hang;
            this.status = status;
            this.body = body;
            acceptThread = new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        Thread handler = new Thread(() -> handle(socket));
                        handler.setDaemon(true);
                        handler.start();
                    } catch (IOException ignored) {
                        // closed
                    }
                }
            });
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void handle(Socket socket) {
            try (Socket s = socket) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    // consume request headers
                }
                if (hang) {
                    Thread.sleep(10_000);
                    return;
                }
                OutputStream out = s.getOutputStream();
                String reason = status == 200 ? "OK" : "Error";
                String headers = "HTTP/1.1 " + status + " " + reason
                        + "\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write(body);
                out.flush();
            } catch (Exception ignored) {
            }
        }

        String url() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }

    @Test
    public void defaultConstructor_instantiates() {
        assertNotNull(new MirrorHelper());
    }

    @Test
    public void isValidMirrorUrl_emptyHostForms() {
        // OkHttp 把 https:///x 解析成 host "x"（合法域名），故放行
        assertTrue(MirrorHelper.isValidMirrorUrl("https:///x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://./x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://.../x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://?x=1"));
    }

    @Test
    public void isValidMirrorUrl_privateRangeEdges() {
        // 172.16/12、192.168/16、169.254/16 的边界外 → 放行
        assertTrue(MirrorHelper.isValidMirrorUrl("https://172.15.0.1/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://172.32.0.1/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://192.169.1.1/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://169.253.1.1/x"));
        // 边界内 → 拦截
        assertFalse(MirrorHelper.isValidMirrorUrl("https://172.31.255.255/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://192.168.0.1/x"));
    }

    @Test
    public void isValidMirrorUrl_ipv6Forms() {
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[fec0::1]/x"));
        assertFalse(MirrorHelper.isValidMirrorUrl("https://[::127.0.0.1]/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://[::8.8.8.8]/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://[::1:8.8.8.8]/x"));
    }

    @Test
    public void isValidMirrorUrl_threePartIpv4() {
        assertFalse(MirrorHelper.isValidMirrorUrl("https://127.0.1/x"));
        assertTrue(MirrorHelper.isValidMirrorUrl("https://8.8.1/x"));
    }

    @Test
    public void parseNumericIPv4_edges() throws Exception {
        assertNull(parseNumericIPv4("1.2.3.4.5"));
        assertNull(parseNumericIPv4("1..2.3"));
        assertNull(parseNumericIPv4("99999999999999999999"));
        assertNull(parseNumericIPv4("4294967296"));
        assertNull(parseNumericIPv4("256.1"));
        assertNull(parseNumericIPv4("1.16777216"));
        assertNull(parseNumericIPv4("1.2.65536"));
        assertNull(parseNumericIPv4("1.2.3.256"));
        assertNull(parseNumericIPv4("256.1.1"));
        assertNull(parseNumericIPv4("1.256.1"));
        assertNull(parseNumericIPv4("0x"));
        assertNull(parseNumericIPv4("abc"));
        assertEquals(Long.valueOf(0x7F000001L), parseNumericIPv4("0X7F.0.0.1"));
        assertEquals(Long.valueOf(0x7F000001L), parseNumericIPv4("127.0.1"));
        assertEquals(Long.valueOf(0x08080808L), parseNumericIPv4("8.8.8.8"));
    }

    @Test
    public void embeddedIPv4_andUniqueLocal_edges() throws Exception {
        assertFalse(isUniqueLocalIPv6(new byte[4]));
        assertTrue(isUniqueLocalIPv6(ipv6Bytes("fc00::1")));
        assertFalse(isUniqueLocalIPv6(ipv6Bytes("2001:db8::1")));
        assertNull(embeddedIPv4(new byte[4]));
        assertNull(embeddedIPv4(ipv6Bytes("2001:db8::1")));
        // getByName 会把 ::ffff:a.b.c.d / ::a.b.c.d 映射成 Inet4Address（4 字节），
        // 故 16 字节的映射形/兼容形直接手写字节数组
        assertNull(embeddedIPv4(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 8, 8, 8, 8}));
        // b[10]==0xFF 但 b[11]!=0xFF：mapped 与 compatible 皆否 → null
        assertNull(embeddedIPv4(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, 0, 8, 8, 8, 8}));
        assertEquals(Long.valueOf(0x08080808L), embeddedIPv4(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 8, 8, 8, 8}));
        assertEquals(Long.valueOf(0x08080808L), embeddedIPv4(new byte[]{
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 8, 8, 8}));
    }

    @Test
    public void isLoopbackOrPrivateHost_edges() throws Exception {
        // 兼容形内嵌私网 IPv4：OkHttp 会把映射形归一成 IPv4，故直测本方法
        assertTrue(isLoopbackOrPrivateHost("0:0:0:0:0:0:127.0.0.1"));
        assertFalse(isLoopbackOrPrivateHost("0:0:0:0:0:0:8.8.8.8"));
        assertFalse(isLoopbackOrPrivateHost("::ffff:8.8.8.8"));
        assertFalse(isLoopbackOrPrivateHost("::1:8.8.8.8"));
        assertTrue(isLoopbackOrPrivateHost("fec0::1"));
        assertFalse(isLoopbackOrPrivateHost("2001:db8::1"));
        // 非法 IPv6 字面量 → UnknownHostException → false
        assertFalse(isLoopbackOrPrivateHost("::ffff:999.1.1.1"));
        assertFalse(isLoopbackOrPrivateHost("::zzzz"));
    }

    @Test
    public void displayHost_queryAndFragmentEdges() {
        assertEquals("host.com", MirrorHelper.displayHost("https://host.com/path?x=1"));
        assertEquals("host.com", MirrorHelper.displayHost("https://host.com#frag"));
        assertEquals("host.com", MirrorHelper.displayHost("https://host.com/path#frag"));
        assertEquals("https://[::1/x", MirrorHelper.displayHost("https://[::1/x"));
        assertEquals("https:///x", MirrorHelper.displayHost("https:///x"));
    }

    @Test
    public void migrateLegacyCustomUrl_alreadyInListSkipsAdd() {
        prefs().edit()
                .putString(MirrorHelper.PREF_MIRROR_LIST, "[\"https://legacy.example.com\"]")
                .putString(MirrorHelper.PREF_MIRROR_CUSTOM_URL, "https://legacy.example.com/")
                .putString(MirrorHelper.PREF_MIRROR_PRESET, "https://gh-proxy.com")
                .apply();
        assertEquals("https://gh-proxy.com", MirrorHelper.getMirrorBase(context()));
        assertFalse(prefs().contains(MirrorHelper.PREF_MIRROR_CUSTOM_URL));
        assertEquals(1, MirrorHelper.getMirrorUrls(context()).size());
    }

    @Test
    public void migrateLegacyCustomUrl_emptyCustomKeepsPreset() {
        prefs().edit()
                .putString(MirrorHelper.PREF_MIRROR_CUSTOM_URL, "")
                .putString(MirrorHelper.PREF_MIRROR_PRESET, "https://mirror.example.com")
                .apply();
        assertEquals("https://mirror.example.com", MirrorHelper.getMirrorBase(context()));
        assertFalse(prefs().contains(MirrorHelper.PREF_MIRROR_CUSTOM_URL));
    }

    @Test
    public void getMirrorUrls_skipsEmptyEntries() {
        prefs().edit().putString(MirrorHelper.PREF_MIRROR_LIST,
                "[\"https://good.example.com\", \"\", \"   \"]").apply();
        List<String> urls = MirrorHelper.getMirrorUrls(context());
        assertEquals(1, urls.size());
        assertEquals("https://good.example.com", urls.get(0));
    }

    @Test
    public void getMirrorUrls_mockPresetArrayDedupsAndSkipsInvalid() {
        Context mockCtx = mock(Context.class);
        Resources mockRes = mock(Resources.class);
        when(mockCtx.getResources()).thenReturn(mockRes);
        when(mockRes.getStringArray(R.array.mirror_preset_values)).thenReturn(new String[]{
                "", "http://bad.example.com", "https://a.example.com",
                "https://a.example.com", "custom"});
        when(mockCtx.getSharedPreferences(any(String.class), any(Integer.class)))
                .thenReturn(prefs());
        List<String> urls = MirrorHelper.getMirrorUrls(mockCtx);
        assertEquals(1, urls.size());
        assertEquals("https://a.example.com", urls.get(0));
    }

    @Test
    public void getMirrorUrls_presetArrayNotFoundReturnsEmpty() {
        Context mockCtx = mock(Context.class);
        Resources mockRes = mock(Resources.class);
        when(mockCtx.getResources()).thenReturn(mockRes);
        when(mockRes.getStringArray(any(Integer.class)))
                .thenThrow(new Resources.NotFoundException("nf"));
        when(mockCtx.getSharedPreferences(any(String.class), any(Integer.class)))
                .thenReturn(prefs());
        assertTrue(MirrorHelper.getMirrorUrls(mockCtx).isEmpty());
    }

    @Test
    public void removeSpeedTestResult_noCacheIsNoop() {
        MirrorHelper.removeSpeedTestResult(context(), "https://a.example.com");
        assertTrue(MirrorHelper.getCachedSpeedResults(context()).isEmpty());
    }

    @Test
    public void removeSpeedTestResult_emptyCacheIsNoop() {
        MirrorHelper.saveSpeedTestResults(context(), new ArrayList<>());
        MirrorHelper.removeSpeedTestResult(context(), "https://a.example.com");
        assertTrue(MirrorHelper.getCachedSpeedResults(context()).isEmpty());
    }

    @Test
    public void removeSpeedTestResult_keepsEntryWithoutEquals() {
        seedSpeedCache("noequals", "https://a.example.com=5");
        MirrorHelper.removeSpeedTestResult(context(), "https://a.example.com");
        Set<String> raw = prefs().getStringSet("mirror_speed_cache", null);
        assertNotNull(raw);
        assertEquals(1, raw.size());
        assertTrue(raw.contains("noequals"));
    }

    @Test
    public void getCachedSpeedResults_skipsBadEntries() {
        seedSpeedCache("https://a.example.com=120", "noequals", "https://b.example.com=abc");
        Map<String, Long> cached = MirrorHelper.getCachedSpeedResults(context());
        assertEquals(1, cached.size());
        assertEquals(Long.valueOf(120), cached.get("https://a.example.com"));
    }

    @Test
    public void buildFallbackChain_blindEnabledEmptyBase() {
        prefs().edit()
                .putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true)
                .putString(MirrorHelper.PREF_MIRROR_PRESET, "http://bad")
                .apply();
        String url = "https://api.github.com/repos/o/r";
        List<String> chain = MirrorHelper.buildFallbackChain(context(), url, true);
        assertEquals(2, chain.size());
        assertEquals("https://gh-proxy.com/" + url, chain.get(0));
        assertEquals(url, chain.get(1));
    }

    @Test
    public void rewriteUrlWithBase_githubNoPath() {
        String base = "https://gh-proxy.com";
        assertEquals("https://github.com",
                MirrorHelper.rewriteUrlWithBase(base, "https://github.com"));
        assertEquals("https://github.com/",
                MirrorHelper.rewriteUrlWithBase(base, "https://github.com/"));
    }

    // ---------- mirrorInterceptor ----------

    private static final String RAW_URL =
            "https://raw.githubusercontent.com/o/r/main/f.txt";

    private Interceptor.Chain mockChain(Request request) {
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        when(chain.request()).thenReturn(request);
        return chain;
    }

    private Response mockResponse(int code) {
        Response r = mock(Response.class);
        when(r.code()).thenReturn(code);
        return r;
    }

    private void enableMirrorWithList(String preset, String... list) {
        prefs().edit()
                .putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true)
                .putString(MirrorHelper.PREF_MIRROR_PRESET, preset)
                .apply();
        seedMirrorList(list);
    }

    @Test
    public void mirrorInterceptor_authorizationSkipsRewrite() throws Exception {
        enableMirrorWithList("https://a.example.com", "https://a.example.com");
        Request request = new Request.Builder().url(RAW_URL)
                .header("Authorization", "token x").build();
        Interceptor.Chain chain = mockChain(request);
        Response ok = mockResponse(200);
        when(chain.proceed(any(Request.class))).thenReturn(ok);
        assertEquals(ok, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(chain, times(1)).proceed(captor.capture());
        assertEquals(RAW_URL, captor.getValue().url().toString());
        assertEquals("token x", captor.getValue().header("Authorization"));
    }

    @Test
    public void mirrorInterceptor_noRewritePassesThrough() throws Exception {
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        Response ok = mockResponse(200);
        when(chain.proceed(any(Request.class))).thenReturn(ok);
        assertEquals(ok, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(1)).proceed(request);
    }

    @Test
    public void mirrorInterceptor_successReturnsResponse() throws Exception {
        enableMirrorWithList("https://a.example.com", "https://a.example.com");
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        Response ok = mockResponse(200);
        when(chain.proceed(any(Request.class))).thenReturn(ok);
        assertEquals(ok, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(1)).proceed(any(Request.class));
    }

    @Test
    public void mirrorInterceptor_5xxFailover() throws Exception {
        enableMirrorWithList("https://a.example.com",
                "https://a.example.com", "https://b.example.com");
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        Response r500 = mockResponse(500);
        Response r200 = mockResponse(200);
        when(chain.proceed(any(Request.class))).thenReturn(r500, r200);
        assertEquals(r200, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(chain, times(2)).proceed(captor.capture());
        assertEquals("https://a.example.com/" + RAW_URL,
                captor.getAllValues().get(0).url().toString());
        assertEquals("https://b.example.com/" + RAW_URL,
                captor.getAllValues().get(1).url().toString());
    }

    @Test
    public void mirrorInterceptor_5xxNoFailoverReturnsResponse() throws Exception {
        enableMirrorWithList("https://gh-proxy.com", "https://gh-proxy.com");
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        Response r500 = mockResponse(500);
        when(chain.proceed(any(Request.class))).thenReturn(r500);
        assertEquals(r500, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(1)).proceed(any(Request.class));
    }

    @Test
    public void mirrorInterceptor_5xxNonIdempotentReturnsResponse() throws Exception {
        enableMirrorWithList("https://a.example.com",
                "https://a.example.com", "https://b.example.com");
        Request request = new Request.Builder().url(RAW_URL).delete().build();
        Interceptor.Chain chain = mockChain(request);
        Response r500 = mockResponse(500);
        when(chain.proceed(any(Request.class))).thenReturn(r500);
        assertEquals(r500, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(1)).proceed(any(Request.class));
    }

    @Test
    public void mirrorInterceptor_headIsIdempotent() throws Exception {
        enableMirrorWithList("https://a.example.com",
                "https://a.example.com", "https://b.example.com");
        Request request = new Request.Builder().url(RAW_URL).head().build();
        Interceptor.Chain chain = mockChain(request);
        Response r500 = mockResponse(500);
        Response r200 = mockResponse(200);
        when(chain.proceed(any(Request.class))).thenReturn(r500, r200);
        assertEquals(r200, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(2)).proceed(any(Request.class));
    }

    @Test
    public void mirrorInterceptor_ioFailover() throws Exception {
        enableMirrorWithList("https://a.example.com",
                "https://a.example.com", "https://b.example.com");
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        Response r200 = mockResponse(200);
        when(chain.proceed(any(Request.class)))
                .thenThrow(new IOException("boom")).thenReturn(r200);
        assertEquals(r200, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(chain, times(2)).proceed(captor.capture());
        assertEquals("https://b.example.com/" + RAW_URL,
                captor.getAllValues().get(1).url().toString());
    }

    @Test
    public void mirrorInterceptor_ioNoFailoverRethrows() throws Exception {
        enableMirrorWithList("https://gh-proxy.com", "https://gh-proxy.com");
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        when(chain.proceed(any(Request.class))).thenThrow(new IOException("boom"));
        assertThrows(IOException.class,
                () -> MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(1)).proceed(any(Request.class));
    }

    @Test
    public void mirrorInterceptor_ioNonIdempotentRethrows() throws Exception {
        enableMirrorWithList("https://a.example.com",
                "https://a.example.com", "https://b.example.com");
        Request request = new Request.Builder().url(RAW_URL).delete().build();
        Interceptor.Chain chain = mockChain(request);
        when(chain.proceed(any(Request.class))).thenThrow(new IOException("boom"));
        assertThrows(IOException.class,
                () -> MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        verify(chain, times(1)).proceed(any(Request.class));
    }

    @Test
    public void nextMirrorBase_cacheAndListOrdering() throws Exception {
        enableMirrorWithList("https://a.example.com",
                "https://a.example.com", "https://b.example.com",
                "https://c.example.com", "https://gh-proxy.com");
        seedSpeedCache("https://b.example.com=-1", "https://a.example.com=5",
                "https://c.example.com=50",
                "https://gone.example.com=5", "https://C.example.com=60");
        Request request = new Request.Builder().url(RAW_URL).build();
        Interceptor.Chain chain = mockChain(request);
        Response r500 = mockResponse(500);
        Response r200 = mockResponse(200);
        when(chain.proceed(any(Request.class))).thenReturn(r500, r200);
        assertEquals(r200, MirrorHelper.mirrorInterceptor(context()).intercept(chain));
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(chain, times(2)).proceed(captor.capture());
        // 缓存：b 不可用跳过、gone 不在列表跳过、c 先入选、大小写重复跳过；
        // 列表再补 b 与默认镜像；延迟最低的 c 排第一
        assertEquals("https://c.example.com/" + RAW_URL,
                captor.getAllValues().get(1).url().toString());
    }

    // ---------- testAllMirrorsSpeed ----------

    @Test
    public void testAllMirrorsSpeed_twoArgOverload() throws Exception {
        seedMirrorList("https://nonexistent.invalid");
        List<MirrorHelper.MirrorSpeedResult> probes =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(1);
        MirrorHelper.testAllMirrorsSpeed(context(), new MirrorHelper.SpeedTestCallback() {
            @Override
            public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
                probes.add(result);
            }

            @Override
            public void onAllComplete() {
                done.countDown();
            }
        });
        assertTrue(waitForLatch(done, 30_000));
        assertEquals(1, probes.size());
        assertEquals(-1L, probes.get(0).latencyMs);
    }

    @Test
    public void testAllMirrorsSpeed_nonEmptyTargets() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer(false, 200, new byte[2048]);
                LocalHttpServer errorServer = new LocalHttpServer(false, 500, new byte[0])) {
            int closedPort = freePort();
            List<MirrorHelper.MirrorSpeedResult> targets = new ArrayList<>();
            targets.add(new MirrorHelper.MirrorSpeedResult("ok", server.url(), -1));
            targets.add(new MirrorHelper.MirrorSpeedResult("servererror",
                    errorServer.url(), -1));
            targets.add(new MirrorHelper.MirrorSpeedResult("refused",
                    "http://127.0.0.1:" + closedPort, -1));
            targets.add(new MirrorHelper.MirrorSpeedResult("badurl", "", -1));
            List<MirrorHelper.MirrorSpeedResult> probes =
                    Collections.synchronizedList(new ArrayList<>());
            CountDownLatch done = new CountDownLatch(1);
            MirrorHelper.testAllMirrorsSpeed(context(), targets,
                    new MirrorHelper.SpeedTestCallback() {
                        @Override
                        public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
                            probes.add(result);
                        }

                        @Override
                        public void onAllComplete() {
                            done.countDown();
                        }
                    });
            assertTrue(waitForLatch(done, 30_000));
            assertEquals(4, probes.size());
            Map<String, Long> byName = new java.util.HashMap<>();
            for (MirrorHelper.MirrorSpeedResult r : probes) {
                byName.put(r.name, r.latencyMs);
            }
            assertTrue(byName.get("ok") >= 0);
            assertEquals(Long.valueOf(-1), byName.get("servererror"));
            assertEquals(Long.valueOf(-1), byName.get("refused"));
            assertEquals(Long.valueOf(-1), byName.get("badurl"));
        }
    }

    @Test
    public void testAllMirrorsSpeed_cancelSuppressesCallbacks() throws Exception {
        try (LocalHttpServer fast = new LocalHttpServer(false, 200, new byte[2048]);
                LocalHttpServer hang = new LocalHttpServer(true, 200, new byte[0])) {
            List<MirrorHelper.MirrorSpeedResult> targets = new ArrayList<>();
            targets.add(new MirrorHelper.MirrorSpeedResult("fast", fast.url(), -1));
            targets.add(new MirrorHelper.MirrorSpeedResult("hang1", hang.url(), -1));
            targets.add(new MirrorHelper.MirrorSpeedResult("hang2", hang.url(), -1));
            List<MirrorHelper.MirrorSpeedResult> probes =
                    Collections.synchronizedList(new ArrayList<>());
            boolean[] allComplete = {false};
            MirrorHelper.SpeedTestHandle handle = MirrorHelper.testAllMirrorsSpeed(
                    context(), targets, new MirrorHelper.SpeedTestCallback() {
                        @Override
                        public void onProbeComplete(MirrorHelper.MirrorSpeedResult result) {
                            probes.add(result);
                        }

                        @Override
                        public void onAllComplete() {
                            allComplete[0] = true;
                        }
                    });
            Thread.sleep(500);
            handle.cancel();
            assertTrue(handle.isCancelled());
            // 等后台排空循环退出并投递 final 回调，再 idle 主线程，
            // 使 final 回调以 cb==null 跑到（否则该半边量不到）
            Thread.sleep(1000);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(probes.isEmpty());
            assertFalse(allComplete[0]);
        }
    }
}
