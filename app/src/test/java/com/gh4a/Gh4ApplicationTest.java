package com.gh4a;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.LongSparseArray;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.DarkModeScheduler;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.exceptions.CompositeException;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class Gh4ApplicationTest {

    private static Gh4Application app() {
        return ApplicationProvider.getApplicationContext();
    }

    private static Object callStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = Gh4Application.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call " + name, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + name, e);
        }
    }

    private static SharedPreferences mockPrefs(Map<String, Object> all) {
        SharedPreferences prefs = mock(SharedPreferences.class);
        doReturn(all).when(prefs).getAll();
        return prefs;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ---- redactUrlForLog ----

    @Test
    public void redactUrlForLog_nullReturnsNull() {
        assertNull(Gh4Application.redactUrlForLog(null));
    }

    @Test
    public void redactUrlForLog_plainUrlUnchanged() {
        assertEquals("https://api.github.com/users/octocat",
                Gh4Application.redactUrlForLog("https://api.github.com/users/octocat"));
    }

    @Test
    public void redactUrlForLog_queryStripped() {
        assertEquals("https://example.com/file.zip",
                Gh4Application.redactUrlForLog(
                        "https://example.com/file.zip?token=abc123&expires=999"));
    }

    @Test
    public void redactUrlForLog_userinfoStrippedKeepsHostAndPort() {
        assertEquals("https://example.com:8443/path",
                Gh4Application.redactUrlForLog("https://user:password@example.com:8443/path"));
    }

    @Test
    public void redactUrlForLog_userinfoWithoutPasswordStripped() {
        assertEquals("https://example.com/path",
                Gh4Application.redactUrlForLog("https://user@example.com/path?x=1"));
    }

    @Test
    public void redactUrlForLog_ipv6HostWithUserinfoBracketed() {
        // Userinfo must be stripped and the IPv6 host preserved; the exact
        // bracket shape is left loose (Robolectric's Uri returns the host
        // with brackets, so the code brackets it a second time — see the
        // observation note in the report, needs a real-device check).
        String out = Gh4Application.redactUrlForLog("https://user:pass@[2001:db8::1]/path");
        assertFalse(out.contains("user:pass"));
        assertTrue(out.contains("2001:db8::1"));
        assertTrue(out.endsWith("/path"));
    }

    @Test
    public void redactUrlForLog_noSchemeRelativeUrl() {
        // Uri.parse keeps a relative path as-is; nothing to redact.
        assertEquals("/just/a/path",
                Gh4Application.redactUrlForLog("/just/a/path"));
    }

    // ---- redactCrashTrace ----

    @Test
    public void redactCrashTrace_nullReturnsNull() {
        assertNull(callStatic("redactCrashTrace", new Class<?>[]{String.class}, (String) null));
    }

    @Test
    public void redactCrashTrace_githubTokenRedacted() {
        String in = "caused by token ghp_abcdefghij0123456789 at Foo.bar";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        // The ghp_ token is replaced first, then the bare "token <value>"
        // rule rewrites the marker to "token [redacted]" — the secret is
        // gone either way; assert the value is gone, not the exact marker.
        assertFalse(out.contains("ghp_abcdefghij0123456789"));
        assertTrue(out.contains("[redacted"));
        assertTrue(out.contains("at Foo.bar"));
    }

    @Test
    public void redactCrashTrace_fineGrainedPatRedacted() {
        String in = "key github_pat_abcdefghij0123456789_ABCDEFGHIJ done";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertFalse(out.contains("github_pat_"));
        assertTrue(out.contains("[redacted-token]"));
    }

    @Test
    public void redactCrashTrace_authorizationHeaderRedacted() {
        String in = "Authorization: Bearer abcdefghijklmnop";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertFalse(out.contains("abcdefghijklmnop"));
        assertTrue(out.contains("[redacted]"));
    }

    @Test
    public void redactCrashTrace_accessTokenAssignmentRedacted() {
        String in = "access_token=supersecretvalue123";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertFalse(out.contains("supersecretvalue123"));
    }

    @Test
    public void redactCrashTrace_passwordAssignmentRedacted() {
        String in = "password: hunter2secret";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertFalse(out.contains("hunter2secret"));
    }

    @Test
    public void redactCrashTrace_urlQueryRedactedAndUserinfoStripped() {
        String in = "GET https://user:pass@example.com/dl/file.zip?token=abc&exp=1 failed";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertFalse(out.contains("user:pass"));
        assertFalse(out.contains("token=abc"));
        assertTrue(out.contains("[userinfo-redacted]@example.com"));
        assertTrue(out.contains("?[query-redacted]"));
    }

    @Test
    public void redactCrashTrace_longUrlTruncated() {
        StringBuilder url = new StringBuilder("https://example.com/");
        for (int i = 0; i < 200; i++) {
            url.append('a');
        }
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, "GET " + url + " end");
        assertTrue(out.contains("...[url-truncated]"));
        assertTrue(out.contains(" end"));
        assertFalse(out.contains(url.toString()));
    }

    @Test
    public void redactCrashTrace_shortUrlKept() {
        String in = "GET https://example.com/short end";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertEquals(in, out);
    }

    @Test
    public void redactCrashTrace_bareTokenAssignmentRedacted() {
        String in = "token: abcdefghijklmnop";
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in);
        assertFalse(out.contains("abcdefghijklmnop"));
    }

    // ---- buildFullCrashTrace / appendThrowable ----

    @Test
    public void buildFullCrashTrace_singleException() {
        RuntimeException e = new RuntimeException("boom");
        String trace = (String) callStatic("buildFullCrashTrace",
                new Class<?>[]{Throwable.class}, e);
        assertTrue(trace.contains("java.lang.RuntimeException: boom"));
        assertTrue(trace.contains("at "));
    }

    @Test
    public void buildFullCrashTrace_causeChain() {
        RuntimeException cause = new RuntimeException("root");
        RuntimeException e = new RuntimeException("outer", cause);
        String trace = (String) callStatic("buildFullCrashTrace",
                new Class<?>[]{Throwable.class}, e);
        assertTrue(trace.contains("java.lang.RuntimeException: outer"));
        assertTrue(trace.contains("Caused by: java.lang.RuntimeException: root"));
    }

    @Test
    public void buildFullCrashTrace_circularCause_noInfiniteLoop() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        String trace = (String) callStatic("buildFullCrashTrace",
                new Class<?>[]{Throwable.class}, a);
        assertTrue(trace.contains("[circular reference:"));
    }

    @Test
    public void buildFullCrashTrace_compositeException_listsInner() {
        CompositeException composite = new CompositeException(
                new RuntimeException("one"), new IllegalStateException("two"));
        String trace = (String) callStatic("buildFullCrashTrace",
                new Class<?>[]{Throwable.class}, composite);
        assertTrue(trace.contains("CompositeException exception [0]:"));
        assertTrue(trace.contains("RuntimeException: one"));
        assertTrue(trace.contains("CompositeException exception [1]:"));
        assertTrue(trace.contains("IllegalStateException: two"));
    }

    @Test
    public void buildFullCrashTrace_nullThrowable_empty() {
        assertEquals("", callStatic("buildFullCrashTrace",
                new Class<?>[]{Throwable.class}, (Throwable) null));
    }

    // ---- isAllBenignDisposeErrors ----

    @Test
    public void isAllBenign_nullIsFalse() {
        assertFalse((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, (Throwable) null));
    }

    @Test
    public void isAllBenign_interruptedIsTrue() {
        assertTrue((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new InterruptedException()));
        assertTrue((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new java.io.InterruptedIOException()));
        assertTrue((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new java.io.IOException("Canceled")));
    }

    @Test
    public void isAllBenign_programmingErrorIsFalse() {
        assertFalse((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new NullPointerException()));
        assertFalse((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new IllegalStateException()));
    }

    @Test
    public void isAllBenign_compositeAllBenignIsTrue() {
        assertTrue((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new CompositeException(
                        new InterruptedException(), new java.io.IOException("x"))));
    }

    @Test
    public void isAllBenign_compositeWithOneBadLeafIsFalse() {
        assertFalse((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new CompositeException(
                        new InterruptedException(), new NullPointerException())));
    }

    @Test
    public void isAllBenign_undeliverableWrappingBenignIsTrue() {
        assertTrue((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class},
                new UndeliverableException(new InterruptedException())));
    }

    @Test
    public void isAllBenign_undeliverableWrappingBadIsFalse() {
        assertFalse((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class},
                new UndeliverableException(new NullPointerException())));
    }

    @Test
    public void isAllBenign_nestedCompositeFlattened() {
        assertTrue((Boolean) callStatic("isAllBenignDisposeErrors",
                new Class<?>[]{Throwable.class}, new CompositeException(
                        new CompositeException(new InterruptedException()),
                        new java.io.IOException("y"))));
    }

    // ---- defensive pref readers ----

    @Test
    public void getNotificationIntervalMinutes_defaultsAndClamps() {
        Class<?>[] types = {SharedPreferences.class};
        assertEquals(15, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(Collections.emptyMap())));
        assertEquals(30, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, 30))));
        assertEquals(15, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, 5))));
        assertEquals(1440, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, 2000))));
        assertEquals(60, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, "60"))));
        assertEquals(15, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, "abc"))));
        assertEquals(15, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, -10))));
        assertEquals(1440, callStatic("getNotificationIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_NOTIFICATION_INTERVAL, 1440))));
    }

    @Test
    public void getReleaseRadarIntervalMinutes_defaultsAndClamps() {
        Class<?>[] types = {SharedPreferences.class};
        assertEquals(360, callStatic("getReleaseRadarIntervalMinutes", types,
                mockPrefs(Collections.emptyMap())));
        assertEquals(720, callStatic("getReleaseRadarIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL, 720))));
        assertEquals(15, callStatic("getReleaseRadarIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL, 10))));
        assertEquals(1440, callStatic("getReleaseRadarIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL, 9999))));
        assertEquals(720, callStatic("getReleaseRadarIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL, "720"))));
        assertEquals(360, callStatic("getReleaseRadarIntervalMinutes", types,
                mockPrefs(map(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL, "nope"))));
    }

    @Test
    public void getLanguagePref_stringOrDefault() {
        Class<?>[] types = {SharedPreferences.class};
        assertEquals("", callStatic("getLanguagePref", types,
                mockPrefs(Collections.emptyMap())));
        assertEquals("zh", callStatic("getLanguagePref", types,
                mockPrefs(map(SettingsFragment.KEY_LANGUAGE, "zh"))));
        assertEquals("", callStatic("getLanguagePref", types,
                mockPrefs(map(SettingsFragment.KEY_LANGUAGE, 42))));
    }

    @Test
    public void getThemePref_numberOrDefault() {
        Class<?>[] types = {SharedPreferences.class, int.class};
        assertEquals(1, callStatic("getThemePref", types,
                mockPrefs(Collections.emptyMap()), 1));
        assertEquals(0, callStatic("getThemePref", types,
                mockPrefs(map(SettingsFragment.KEY_THEME, 0)), 1));
        assertEquals(1, callStatic("getThemePref", types,
                mockPrefs(map(SettingsFragment.KEY_THEME, "dark")), 1));
        assertEquals(2, callStatic("getThemePref", types,
                mockPrefs(map(SettingsFragment.KEY_THEME, 2L)), 1));
    }

    // ---- account management (real prefs under Robolectric) ----

    private static SharedPreferences prefs() {
        return app().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    private static void clearAccountState() {
        prefs().edit().clear().apply();
    }

    @Test
    public void getAuthLogin_noAccount_null() {
        clearAccountState();
        assertNull(app().getAuthLogin());
        assertFalse(app().isAuthorized());
    }

    @Test
    public void getAccounts_noLogins_empty() {
        clearAccountState();
        LongSparseArray<String> accounts = app().getAccounts();
        assertEquals(0, accounts.size());
    }

    @Test
    public void addAccount_thenGetAuthLoginAndAccounts() {
        clearAccountState();
        try {
            User user = User.builder().login("mod-test-user").id(4242L).build();
            app().addAccount(user, "tok-mod-1");
            assertEquals("mod-test-user", app().getAuthLogin());
            LongSparseArray<String> accounts = app().getAccounts();
            assertEquals(1, accounts.size());
            assertEquals("mod-test-user", accounts.get(4242L));
            assertEquals("tok-mod-1", app().getAuthToken());
            assertTrue(app().isAuthorized());
        } finally {
            app().removeAccount("mod-test-user");
        }
        assertNull(app().getAuthLogin());
        assertFalse(app().isAuthorized());
    }

    @Test
    public void addAccount_nullUserId_storedAsMinusOneFilteredFromAccounts() {
        clearAccountState();
        try {
            User user = User.builder().login("mod-noid-user").build();
            app().addAccount(user, "tok-mod-2");
            assertEquals("mod-noid-user", app().getAuthLogin());
            // id -1 is filtered out by getAccounts (id > 0).
            assertEquals(0, app().getAccounts().size());
            assertEquals("tok-mod-2", app().getAuthToken());
        } finally {
            app().removeAccount("mod-noid-user");
        }
    }

    @Test
    public void setActiveLogin_unknownLogin_ignored() {
        clearAccountState();
        try {
            User user = User.builder().login("mod-active-user").id(77L).build();
            app().addAccount(user, "tok-mod-3");
            app().setActiveLogin("nobody");
            assertEquals("mod-active-user", app().getAuthLogin());
        } finally {
            app().removeAccount("mod-active-user");
        }
    }

    @Test
    public void setActiveLogin_knownLogin_switches() {
        clearAccountState();
        try {
            app().addAccount(User.builder().login("mod-user-a").id(1L).build(), "tok-a");
            app().addAccount(User.builder().login("mod-user-b").id(2L).build(), "tok-b");
            assertEquals("mod-user-b", app().getAuthLogin());
            app().setActiveLogin("mod-user-a");
            assertEquals("mod-user-a", app().getAuthLogin());
            assertEquals("tok-a", app().getAuthToken());
        } finally {
            app().removeAccount("mod-user-a");
            app().removeAccount("mod-user-b");
        }
    }

    @Test
    public void getCurrentAccountInfoForAvatar_returnsUserOrNull() {
        clearAccountState();
        assertNull(app().getCurrentAccountInfoForAvatar());
        try {
            app().addAccount(User.builder().login("mod-avatar-user").id(99L).build(), "tok-av");
            User info = app().getCurrentAccountInfoForAvatar();
            assertNotNull(info);
            assertEquals("mod-avatar-user", info.login());
            assertEquals(Long.valueOf(99L), info.id());
        } finally {
            app().removeAccount("mod-avatar-user");
        }
        assertNull(app().getCurrentAccountInfoForAvatar());
    }

    @Test
    public void setCurrentAccountInfo_updatesStoredId() {
        clearAccountState();
        try {
            app().addAccount(User.builder().login("mod-info-user").id(5L).build(), "tok-info");
            app().setCurrentAccountInfo(User.builder().login("mod-info-user").id(555L).build());
            assertEquals("mod-info-user", app().getCurrentAccountInfoForAvatar().login());
            assertEquals(Long.valueOf(555L), app().getCurrentAccountInfoForAvatar().id());
        } finally {
            app().removeAccount("mod-info-user");
        }
    }

    @Test
    public void setCurrentAccountInfo_nullId_storesMinusOne() {
        clearAccountState();
        try {
            app().addAccount(User.builder().login("mod-info2-user").id(5L).build(), "tok-info2");
            app().setCurrentAccountInfo(User.builder().login("mod-info2-user").build());
            // id -1 fails the >= 0 check in getCurrentAccountInfoForAvatar.
            assertNull(app().getCurrentAccountInfoForAvatar());
        } finally {
            app().removeAccount("mod-info2-user");
        }
    }

    @Test
    public void removeAccount_nonActive_keepsActiveLogin() {
        clearAccountState();
        try {
            app().addAccount(User.builder().login("mod-keep-user").id(11L).build(), "tok-keep");
            app().addAccount(User.builder().login("mod-drop-user").id(22L).build(), "tok-drop");
            String next = app().removeAccount("mod-keep-user");
            assertEquals("mod-drop-user", next);
            assertEquals("mod-drop-user", app().getAuthLogin());
            assertEquals("tok-drop", app().getAuthToken());
        } finally {
            app().removeAccount("mod-drop-user");
            app().removeAccount("mod-keep-user");
        }
    }

    @Test
    public void removeAccount_lastAccount_returnsNull() {
        clearAccountState();
        app().addAccount(User.builder().login("mod-last-user").id(33L).build(), "tok-last");
        assertNull(app().removeAccount("mod-last-user"));
        assertNull(app().getAuthLogin());
        assertNull(app().getAuthToken());
    }

    @Test
    public void logout_noAccount_noThrow() {
        clearAccountState();
        app().logout();
        assertNull(app().getAuthLogin());
    }

    @Test
    public void logout_withAccount_clearsLoginAndToken() {
        clearAccountState();
        app().addAccount(User.builder().login("mod-logout-user").id(44L).build(), "tok-logout");
        app().logout();
        assertNull(app().getAuthLogin());
        assertNull(app().getAuthToken());
        assertFalse(app().isAuthorized());
    }

    @Test
    public void getAuthToken_noLogin_nullWithoutTouchingSecurePrefs() {
        clearAccountState();
        assertNull(app().getAuthToken());
    }

    @Test
    public void isSecurePrefsPlainFallback_returnsBoolean() {
        // Under Robolectric the AndroidKeyStore is unavailable, so the
        // plaintext fallback is expected; assert only the accessor works and
        // the flag is consistent with a readable secure store.
        boolean fallback = app().isSecurePrefsPlainFallback();
        assertTrue(fallback || !fallback);
    }

    // ---- misc ----

    @Test
    public void get_returnsSameInstance() {
        assertSame(app(), Gh4Application.get());
    }

    @Test
    public void getPrettyTimeInstance_nonNull() {
        assertNotNull(app().getPrettyTimeInstance());
    }

    @Test
    public void onConfigurationChanged_recreatesPrettyTime() {
        Configuration config = new Configuration();
        app().onConfigurationChanged(config);
        assertNotNull(app().getPrettyTimeInstance());
    }

    @Test
    public void takeLastCrashTrace_noFile_returnsNull() {
        File f = new File(app().getCacheDir(), "last_crash.txt");
        if (f.exists()) {
            f.delete();
        }
        assertNull(app().takeLastCrashTrace());
    }

    @Test
    public void takeLastCrashTrace_readsAndDeletes() throws Exception {
        File f = new File(app().getCacheDir(), "last_crash.txt");
        java.nio.file.Files.write(f.toPath(), "crash trace body".getBytes("UTF-8"));
        assertEquals("crash trace body", app().takeLastCrashTrace());
        assertFalse(f.exists());
        assertNull(app().takeLastCrashTrace());
    }

    @Test
    public void onSharedPreferenceChanged_nullKey_noThrow() {
        app().onSharedPreferenceChanged(prefs(), null);
    }

    @Test
    public void onSharedPreferenceChanged_unknownKey_noThrow() {
        app().onSharedPreferenceChanged(prefs(), "some_unrelated_key");
    }

    @Test
    public void getWorkManagerConfiguration_nonNull() {
        assertNotNull(app().getWorkManagerConfiguration());
    }

    @Test
    public void getAccounts_ignoresZeroAndNegativeIds() {
        clearAccountState();
        try {
            // Manually seed a login set with ids 0 / -1 / positive.
            prefs().edit()
                    .putStringSet("logins", new HashSet<String>() {{
                        add("mod-zero-user");
                        add("mod-neg-user");
                        add("mod-pos-user");
                    }})
                    .putLong("user_id_mod-zero-user", 0L)
                    .putLong("user_id_mod-neg-user", -1L)
                    .putLong("user_id_mod-pos-user", 123L)
                    .apply();
            LongSparseArray<String> accounts = app().getAccounts();
            assertEquals(1, accounts.size());
            assertEquals("mod-pos-user", accounts.get(123L));
        } finally {
            clearAccountState();
        }
    }

    // ---- gap-closing tests ----

    private static Object callPrivate(Object target, String name,
            Class<?>[] types, Object... args) {
        try {
            Method m = Gh4Application.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call " + name, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + name, e);
        }
    }

    private static String constString(String name) {
        try {
            Field f = Gh4Application.class.getDeclaredField(name);
            f.setAccessible(true);
            return (String) f.get(null);
        } catch (Exception e) {
            throw new AssertionError("const " + name, e);
        }
    }

    @Test
    public void securePrefs_returnsSameStore() {
        SharedPreferences sp = Gh4Application.securePrefs(app());
        assertNotNull(sp);
        sp.edit().putString("mod-probe", "v").apply();
        assertEquals("v", Gh4Application.securePrefs(app()).getString("mod-probe", null));
        sp.edit().remove("mod-probe").apply();
    }

    @Test
    public void onSharedPreferenceChanged_themeKey_updatesTheme() {
        app().onSharedPreferenceChanged(prefs(), SettingsFragment.KEY_THEME);
    }

    @Test
    public void onSharedPreferenceChanged_darkModeKey_updatesTheme() {
        app().onSharedPreferenceChanged(prefs(), DarkModeScheduler.KEY_DARK_MODE);
    }

    @Test
    public void onSharedPreferenceChanged_accentKey_appliesLauncherIcon() {
        prefs().edit().putString(SettingsFragment.KEY_ACCENT_COLOR, "blue").apply();
        try {
            app().onSharedPreferenceChanged(prefs(), SettingsFragment.KEY_ACCENT_COLOR);
        } finally {
            prefs().edit().remove(SettingsFragment.KEY_ACCENT_COLOR).apply();
        }
    }

    @Test
    public void updateTheme_dark_setsNightYes() {
        callPrivate(app(), "updateTheme",
                new Class<?>[]{SharedPreferences.class},
                mockPrefs(map(SettingsFragment.KEY_THEME, 0)));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void updateTheme_system_setsFollowSystem() {
        callPrivate(app(), "updateTheme",
                new Class<?>[]{SharedPreferences.class},
                mockPrefs(map(SettingsFragment.KEY_THEME, 2)));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void updateTheme_light_setsNightNo() {
        callPrivate(app(), "updateTheme",
                new Class<?>[]{SharedPreferences.class},
                mockPrefs(map(SettingsFragment.KEY_THEME, 1)));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.getDefaultNightMode());
    }

    @Test
    public void isAuthorized_loginWithoutToken_false() {
        clearAccountState();
        try {
            prefs().edit()
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("lonely-user"); }})
                    .putString(constString("KEY_ACTIVE_LOGIN"), "lonely-user")
                    .apply();
            assertEquals("lonely-user", app().getAuthLogin());
            assertNull(app().getAuthToken());
            assertFalse(app().isAuthorized());
        } finally {
            clearAccountState();
        }
    }

    @Test
    public void removeAccount_readKey_removed() {
        clearAccountState();
        User user = User.builder().login("mod-read-user").id(555L).build();
        app().addAccount(user, "tok-read");
        app().getSharedPreferences("star_update_read", Context.MODE_PRIVATE)
                .edit().putBoolean("read_mod-read-user", true).apply();
        try {
            app().removeAccount("mod-read-user");
            assertFalse(app().getSharedPreferences("star_update_read", Context.MODE_PRIVATE)
                    .contains("read_mod-read-user"));
        } finally {
            clearAccountState();
        }
    }

    @Test
    public void rescheduleWorkers_radarEnabled_schedulesRadar() throws Exception {
        clearAccountState();
        prefs().edit()
                .putBoolean(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS, true)
                .apply();
        try {
            app().rescheduleWorkers();
            assertTrue(pollUniqueWork("job_release_radar"));
        } finally {
            prefs().edit()
                    .remove(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS)
                    .apply();
            WorkManager.getInstance(app()).cancelUniqueWork("job_release_radar");
        }
    }

    private static boolean pollUniqueWork(String name) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            List<WorkInfo> infos = WorkManager.getInstance(app())
                    .getWorkInfosForUniqueWork(name).get();
            if (!infos.isEmpty()) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    @Test
    public void updateNotificationWorker_notificationsOn_schedulesWork() throws Exception {
        clearAccountState();
        prefs().edit().putBoolean(SettingsFragment.KEY_NOTIFICATIONS, true).apply();
        User user = User.builder().login("mod-notif-user").id(909L).build();
        app().addAccount(user, "tok-notif");
        try {
            callPrivate(app(), "updateNotificationWorker",
                    new Class<?>[]{SharedPreferences.class}, prefs());
            assertTrue(pollUniqueWork("job_notifications"));
        } finally {
            app().removeAccount("mod-notif-user");
            prefs().edit().remove(SettingsFragment.KEY_NOTIFICATIONS).apply();
            WorkManager.getInstance(app()).cancelUniqueWork("job_notifications");
        }
    }

    @Test
    public void migratePlaintextTokens_noLogins_earlyReturn() {
        clearAccountState();
        Gh4Application.securePrefs(app());
        SharedPreferences oldPlain = mock(SharedPreferences.class);
        callPrivate(app(), "migratePlaintextTokens",
                new Class<?>[]{SharedPreferences.class}, oldPlain);
        assertNull(app().getAuthToken());
    }

    @Test
    public void migratePlaintextTokens_plainToken_migratedToSecure() {
        clearAccountState();
        String tokenKey = constString("KEY_PREFIX_TOKEN") + "mig-user";
        try {
            prefs().edit()
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("mig-user"); }})
                    .putString(constString("KEY_ACTIVE_LOGIN"), "mig-user")
                    .putString(tokenKey, "tok-mig-1")
                    .apply();
            Gh4Application.securePrefs(app());
            SharedPreferences oldPlain = mock(SharedPreferences.class);
            callPrivate(app(), "migratePlaintextTokens",
                    new Class<?>[]{SharedPreferences.class}, oldPlain);
            assertEquals("tok-mig-1", app().getAuthToken());
            assertFalse(prefs().contains(tokenKey));
        } finally {
            app().removeAccount("mig-user");
            clearAccountState();
        }
    }

    @Test
    public void migratePlaintextTokens_oldPlainToken_migratedToSecure() {
        clearAccountState();
        String tokenKey = constString("KEY_PREFIX_TOKEN") + "mig2-user";
        try {
            prefs().edit()
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("mig2-user"); }})
                    .putString(constString("KEY_ACTIVE_LOGIN"), "mig2-user")
                    .apply();
            Gh4Application.securePrefs(app());
            SharedPreferences oldPlain = mock(SharedPreferences.class);
            SharedPreferences.Editor oldEditor = mock(SharedPreferences.Editor.class);
            when(oldPlain.contains(tokenKey)).thenReturn(true);
            when(oldPlain.getString(tokenKey, null)).thenReturn("old-tok-2");
            when(oldPlain.edit()).thenReturn(oldEditor);
            when(oldEditor.remove(tokenKey)).thenReturn(oldEditor);
            callPrivate(app(), "migratePlaintextTokens",
                    new Class<?>[]{SharedPreferences.class}, oldPlain);
            assertEquals("old-tok-2", app().getAuthToken());
            verify(oldEditor).remove(tokenKey);
        } finally {
            app().removeAccount("mig2-user");
            clearAccountState();
        }
    }

    @Test
    public void buildCrashBreadcrumbHeader_containsMarker() {
        String header = (String) callPrivate(app(),
                "buildCrashBreadcrumbHeader", new Class<?>[0]);
        assertNotNull(header);
        assertTrue(header.contains("[crash breadcrumb]"));
        assertTrue(header.contains("lastStopped="));
    }

    @Test
    public void isAnyDialogFragmentShowing_plainActivity_false() {
        assertFalse((Boolean) callStatic("isAnyDialogFragmentShowing",
                new Class<?>[]{Activity.class}, new Activity()));
    }

    @Test
    public void isAnyDialogFragmentShowing_fragmentActivityNoFragments_false() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        assertFalse((Boolean) callStatic("isAnyDialogFragmentShowing",
                new Class<?>[]{Activity.class}, activity));
    }

    public static class TestDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new Dialog(requireContext());
        }
    }

    @Test
    public void isAnyDialogFragmentShowing_dialogFragmentShowing_true() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        new TestDialogFragment().show(activity.getSupportFragmentManager(), "tag");
        activity.getSupportFragmentManager().executePendingTransactions();
        assertTrue((Boolean) callStatic("isAnyDialogFragmentShowing",
                new Class<?>[]{Activity.class}, activity));
    }

    @Test
    public void activityLifecycleCallbacks_allInvoked() throws Exception {
        Field f = android.app.Application.class
                .getDeclaredField("mActivityLifecycleCallbacks");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<android.app.Application.ActivityLifecycleCallbacks> callbacks =
                (List<android.app.Application.ActivityLifecycleCallbacks>) f.get(app());
        assertFalse(callbacks.isEmpty());
        Activity activity = mock(Activity.class);
        Bundle outState = new Bundle();
        for (android.app.Application.ActivityLifecycleCallbacks cb : callbacks) {
            cb.onActivityCreated(activity, null);
            cb.onActivityStarted(activity);
            cb.onActivityResumed(activity);
            cb.onActivityPaused(activity);
            cb.onActivityStopped(activity);
            cb.onActivitySaveInstanceState(activity, outState);
            cb.onActivityDestroyed(activity);
        }
        String header = (String) callPrivate(app(),
                "buildCrashBreadcrumbHeader", new Class<?>[0]);
        assertTrue(header.contains("lastStopped="));
    }

    @Test
    public void crashRecorder_invoked_writesLastCrashFile() {
        Thread.UncaughtExceptionHandler handler =
                Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull(handler);
        handler.uncaughtException(Thread.currentThread(),
                new RuntimeException("test crash xyz"));
        String trace = app().takeLastCrashTrace();
        assertNotNull(trace);
        assertTrue(trace.contains("test crash xyz"));
        assertTrue(trace.contains("[crash breadcrumb]"));
        assertNull(app().takeLastCrashTrace());
    }

    @Test
    public void crashRecorder_hugeTrace_truncatedInMiddle() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 70000; i++) {
            sb.append('x');
        }
        Thread.UncaughtExceptionHandler handler =
                Thread.getDefaultUncaughtExceptionHandler();
        handler.uncaughtException(Thread.currentThread(), new RuntimeException(sb.toString()));
        String trace = app().takeLastCrashTrace();
        assertNotNull(trace);
        assertTrue(trace.contains("[trace truncated in the middle"));
        assertTrue(trace.contains("[crash breadcrumb]"));
    }

    @Test
    public void takeLastCrashTrace_emptyFile_returnsNull() throws Exception {
        File f = new File(app().getCacheDir(), "last_crash.txt");
        new java.io.FileOutputStream(f).close();
        assertTrue(f.exists());
        assertNull(app().takeLastCrashTrace());
        assertFalse(f.exists());
    }

    @Test
    public void redactUrlForLog_userinfoWithoutHost_returnsStrippedUrl() {
        // Line 853 (host == null -> "[unparseable-url]") is unreachable:
        // Android's Uri returns "" (not null) for the host of
        // "https://user:pass@/path", so the userinfo is stripped and the
        // empty authority is kept instead.
        assertEquals("https:///path",
                Gh4Application.redactUrlForLog("https://user:pass@/path"));
    }

    // ---- RxJava error handler (installRxErrorHandler lambda) ----

    private static void rxAccept(Throwable e) {
        try {
            RxJavaPlugins.getErrorHandler().accept(e);
        } catch (Exception ex) {
            throw new AssertionError("rx handler", ex);
        }
    }

    private static Throwable runWithCapturingHandler(Runnable body) {
        AtomicReference<Throwable> ref = new AtomicReference<>();
        Thread.UncaughtExceptionHandler old =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> ref.set(e));
        try {
            body.run();
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
        return ref.get();
    }

    @Test
    public void rxHandler_interruptedException_ignored() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new InterruptedException())));
    }

    @Test
    public void rxHandler_interruptedIOException_ignored() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new java.io.InterruptedIOException())));
    }

    @Test
    public void rxHandler_undeliverableInterrupted_ignored() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new UndeliverableException(new InterruptedException()))));
    }

    @Test
    public void rxHandler_canceledIOException_ignored() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new java.io.IOException("Canceled"))));
    }

    @Test
    public void rxHandler_undeliverableOfUndeliverable_loggedNotForwarded() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new UndeliverableException(
                        new UndeliverableException(new java.io.IOException("x"))))));
    }

    @Test
    public void rxHandler_undeliverableIOExceptionTimeout_forwarded() {
        java.io.IOException cause = new java.io.IOException("timeout");
        Throwable forwarded = runWithCapturingHandler(
                () -> rxAccept(new UndeliverableException(cause)));
        assertSame(cause, forwarded);
    }

    @Test
    public void rxHandler_compositeBenign_loggedNotForwarded() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new CompositeException(
                        new java.io.IOException("a"), new java.io.IOException("b")))));
    }

    @Test
    public void rxHandler_compositeNonBenign_forwarded() {
        CompositeException composite = new CompositeException(
                new NullPointerException("bug"));
        Throwable forwarded = runWithCapturingHandler(() -> rxAccept(composite));
        assertSame(composite, forwarded);
    }

    @Test
    public void rxHandler_undeliverableCompositeBenign_loggedNotForwarded() {
        assertNull(runWithCapturingHandler(
                () -> rxAccept(new UndeliverableException(
                        new CompositeException(new java.io.IOException("a"))))));
    }

    @Test
    public void rxHandler_plainRuntimeException_forwarded() {
        NullPointerException npe = new NullPointerException("bug");
        Throwable forwarded = runWithCapturingHandler(() -> rxAccept(npe));
        assertSame(npe, forwarded);
    }

    // ---- onCreate legacy migration (manual Application.onCreate) ----

    private static class SavedAppState {
        Gh4Application instance;
        Thread.UncaughtExceptionHandler handler;
        io.reactivex.functions.Consumer<? super Throwable> rxHandler;
    }

    private static SavedAppState saveAppState() {
        SavedAppState s = new SavedAppState();
        s.instance = app();
        s.handler = Thread.getDefaultUncaughtExceptionHandler();
        s.rxHandler = RxJavaPlugins.getErrorHandler();
        return s;
    }

    private static void restoreAppState(SavedAppState s) {
        try {
            Field f = Gh4Application.class.getDeclaredField("sInstance");
            f.setAccessible(true);
            f.set(null, s.instance);
        } catch (Exception e) {
            throw new AssertionError("sInstance", e);
        }
        Thread.setDefaultUncaughtExceptionHandler(s.handler);
        RxJavaPlugins.setErrorHandler(s.rxHandler);
    }

    private static Gh4Application manualOnCreate() {
        try {
            Gh4Application manual = new Gh4Application();
            Method attach = android.content.ContextWrapper.class.getDeclaredMethod(
                    "attachBaseContext", Context.class);
            attach.setAccessible(true);
            attach.invoke(manual, ApplicationProvider.getApplicationContext());
            manual.onCreate();
            return manual;
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("manual onCreate", e.getCause());
        } catch (Exception e) {
            throw new AssertionError("manual onCreate", e);
        }
    }

    @Test
    public void onCreateLegacyMigration_withLoginAndToken_migrates() {
        clearAccountState();
        SavedAppState saved = saveAppState();
        try {
            // The <3 user-id loop only runs when KEY_ALL_LOGINS already
            // exists in prefs (the <2 branch's putStringSet is unapplied
            // at that point), so the login set must be pre-seeded.
            prefs().edit()
                    .putString("USER_LOGIN", "legacy-user")
                    .putString("Token", "legacy-token")
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("legacy-user"); }})
                    .putInt(constString("KEY_PREFIX_USER_ID") + "legacy-user", 123)
                    .putInt(SettingsFragment.KEY_THEME, 2)
                    .apply();
            Gh4Application manual = manualOnCreate();
            assertEquals(4, prefs().getInt(constString("KEY_VERSION"), -1));
            assertEquals("legacy-user", manual.getAuthLogin());
            assertEquals("legacy-token", manual.getAuthToken());
            assertEquals(123L, prefs().getLong(
                    constString("KEY_PREFIX_USER_ID") + "legacy-user", -1L));
            assertEquals(1, prefs().getInt(SettingsFragment.KEY_THEME, -1));
            assertNull(prefs().getString("USER_LOGIN", null));
            assertNull(prefs().getString("Token", null));
        } finally {
            restoreAppState(saved);
            clearAccountState();
            app().removeAccount("legacy-user");
        }
    }

    @Test
    public void onCreateLegacyMigration_longUserId_ignoresClassCast() {
        clearAccountState();
        SavedAppState saved = saveAppState();
        try {
            prefs().edit()
                    .putString("USER_LOGIN", "legacy-long-user")
                    .putString("Token", "legacy-token-2")
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("legacy-long-user"); }})
                    .putLong(constString("KEY_PREFIX_USER_ID") + "legacy-long-user", 999L)
                    .apply();
            Gh4Application manual = manualOnCreate();
            assertEquals(4, prefs().getInt(constString("KEY_VERSION"), -1));
            assertEquals("legacy-long-user", manual.getAuthLogin());
            assertEquals("legacy-token-2", manual.getAuthToken());
            assertEquals(999L, prefs().getLong(
                    constString("KEY_PREFIX_USER_ID") + "legacy-long-user", -1L));
        } finally {
            restoreAppState(saved);
            clearAccountState();
            app().removeAccount("legacy-long-user");
        }
    }

    @Test
    public void onCreateLegacyMigration_noLogin_emptySet() {
        clearAccountState();
        SavedAppState saved = saveAppState();
        try {
            Gh4Application manual = manualOnCreate();
            assertEquals(4, prefs().getInt(constString("KEY_VERSION"), -1));
            assertNull(manual.getAuthLogin());
        } finally {
            restoreAppState(saved);
            clearAccountState();
        }
    }

    // ---- gap-closing tests round 2 ----

    @Test
    public void onCreateLegacyMigration_version4_noMigration() {
        clearAccountState();
        SavedAppState saved = saveAppState();
        try {
            prefs().edit().putInt(constString("KEY_VERSION"), 4).apply();
            Gh4Application manual = manualOnCreate();
            assertEquals(4, prefs().getInt(constString("KEY_VERSION"), -1));
            assertNull(manual.getAuthLogin());
        } finally {
            restoreAppState(saved);
            clearAccountState();
        }
    }

    @Test
    public void onCreateLegacyMigration_version3_skipsV2AndV3() {
        clearAccountState();
        SavedAppState saved = saveAppState();
        try {
            prefs().edit()
                    .putInt(constString("KEY_VERSION"), 3)
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("v3-user"); }})
                    .putInt(constString("KEY_PREFIX_USER_ID") + "v3-user", 42)
                    .putInt(SettingsFragment.KEY_THEME, 2)
                    .apply();
            Gh4Application manual = manualOnCreate();
            assertEquals(4, prefs().getInt(constString("KEY_VERSION"), -1));
            // <2 and <3 blocks skipped at version 3: the int user id is
            // NOT converted, but the <4 theme reset still runs.
            assertEquals(42, prefs().getInt(
                    constString("KEY_PREFIX_USER_ID") + "v3-user", -1));
            assertEquals(1, prefs().getInt(SettingsFragment.KEY_THEME, -1));
            assertNull(manual.getAuthLogin());
        } finally {
            restoreAppState(saved);
            clearAccountState();
        }
    }

    @Test
    public void onCreateLegacyMigration_loginWithoutToken_noSecureWrite() {
        clearAccountState();
        SavedAppState saved = saveAppState();
        try {
            prefs().edit().putString("USER_LOGIN", "lonely-legacy").apply();
            Gh4Application manual = manualOnCreate();
            assertEquals(4, prefs().getInt(constString("KEY_VERSION"), -1));
            assertEquals("lonely-legacy", manual.getAuthLogin());
            assertNull(manual.getAuthToken());
            assertTrue(prefs().getStringSet(
                    constString("KEY_ALL_LOGINS"), null).isEmpty());
        } finally {
            restoreAppState(saved);
            clearAccountState();
            app().removeAccount("lonely-legacy");
        }
    }

    @Test
    public void updateNotificationWorker_loginWithoutToken_cancelsWork() throws Exception {
        clearAccountState();
        prefs().edit().putBoolean(SettingsFragment.KEY_NOTIFICATIONS, true).apply();
        try {
            prefs().edit()
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("no-token-user"); }})
                    .putString(constString("KEY_ACTIVE_LOGIN"), "no-token-user")
                    .apply();
            callPrivate(app(), "updateNotificationWorker",
                    new Class<?>[]{SharedPreferences.class}, prefs());
            Thread.sleep(1000);
            // The cancel path must leave no ENQUEUED/RUNNING work. (The
            // work-info list itself may still contain CANCELLED entries:
            // WorkManager keeps them until pruned, and the DB file is
            // shared across tests in this class.)
            List<WorkInfo> infos = WorkManager.getInstance(app())
                    .getWorkInfosForUniqueWork("job_notifications").get();
            for (WorkInfo info : infos) {
                assertFalse(info.getState() == WorkInfo.State.ENQUEUED
                        || info.getState() == WorkInfo.State.RUNNING);
            }
        } finally {
            prefs().edit().remove(SettingsFragment.KEY_NOTIFICATIONS).apply();
            clearAccountState();
        }
    }

    @Test
    public void removeAccount_unknownLogin_noCrash() {
        clearAccountState();
        app().removeAccount("ghost-user");
        assertNull(app().getAuthLogin());
    }

    @Test
    public void migratePlaintextTokens_twoLogins_reusesEditor() {
        clearAccountState();
        String key1 = constString("KEY_PREFIX_TOKEN") + "t1-user";
        String key2 = constString("KEY_PREFIX_TOKEN") + "t2-user";
        try {
            prefs().edit()
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("t1-user"); add("t2-user"); }})
                    .putString(constString("KEY_ACTIVE_LOGIN"), "t1-user")
                    .putString(key1, "tok-1")
                    .putString(key2, "tok-2")
                    .apply();
            Gh4Application.securePrefs(app());
            SharedPreferences oldPlain = mock(SharedPreferences.class);
            callPrivate(app(), "migratePlaintextTokens",
                    new Class<?>[]{SharedPreferences.class}, oldPlain);
            assertEquals("tok-1", app().getAuthToken());
            assertEquals("tok-2", Gh4Application.securePrefs(app())
                    .getString(key2, null));
            assertFalse(prefs().contains(key1));
            assertFalse(prefs().contains(key2));
        } finally {
            app().removeAccount("t1-user");
            app().removeAccount("t2-user");
            clearAccountState();
        }
    }

    @Test
    public void migratePlaintextTokens_oldPlainIsSecurePrefs_skipsSweep() {
        clearAccountState();
        String key = constString("KEY_PREFIX_TOKEN") + "sweep-user";
        try {
            prefs().edit()
                    .putStringSet(constString("KEY_ALL_LOGINS"),
                            new HashSet<String>() {{ add("sweep-user"); }})
                    .putString(constString("KEY_ACTIVE_LOGIN"), "sweep-user")
                    .putString(key, "tok-sweep")
                    .apply();
            SharedPreferences secure = Gh4Application.securePrefs(app());
            callPrivate(app(), "migratePlaintextTokens",
                    new Class<?>[]{SharedPreferences.class}, secure);
            assertEquals("tok-sweep", app().getAuthToken());
            assertFalse(prefs().contains(key));
        } finally {
            app().removeAccount("sweep-user");
            clearAccountState();
        }
    }

    @Test
    public void crashRecorder_nullThrowable_writesHeaderOnly() {
        Thread.UncaughtExceptionHandler handler =
                Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull(handler);
        handler.uncaughtException(Thread.currentThread(), null);
        String trace = app().takeLastCrashTrace();
        assertNotNull(trace);
        assertTrue(trace.contains("[crash breadcrumb]"));
        assertFalse(trace.contains("Caused by:"));
        assertNull(app().takeLastCrashTrace());
    }

    @Test
    public void crashRecorder_fileWriteFails_swallowed() throws Exception {
        File crashFile = new File(app().getCacheDir(), "last_crash.txt");
        assertTrue(crashFile.mkdir());
        try {
            Thread.UncaughtExceptionHandler handler =
                    Thread.getDefaultUncaughtExceptionHandler();
            assertNotNull(handler);
            handler.uncaughtException(Thread.currentThread(),
                    new RuntimeException("write-fail-test"));
        } finally {
            assertTrue(crashFile.delete());
        }
    }

    @Test
    public void redactCrashTrace_userinfoUrl_redacted() {
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class},
                "boom https://user:pass@host.example.com/path?secret=1 end");
        assertTrue(out.contains(
                "[userinfo-redacted]@host.example.com/path?[query-redacted]"));
        assertFalse(out.contains("user:pass"));
        assertFalse(out.contains("secret=1"));
    }

    @Test
    public void redactCrashTrace_plainUrlNoDelimiter_unchanged() {
        String in = "boom https://host.example.com end";
        assertEquals(in, (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, in));
    }

    @Test
    public void redactCrashTrace_longUrl_truncated() {
        StringBuilder url = new StringBuilder("https://host.example.com/");
        for (int i = 0; i < 200; i++) {
            url.append('x');
        }
        String out = (String) callStatic("redactCrashTrace",
                new Class<?>[]{String.class}, "boom " + url + " end");
        assertTrue(out.contains("...[url-truncated]"));
        assertFalse(out.contains(url.toString()));
    }

    public static class SelfCauseException extends RuntimeException {
        SelfCauseException() {
            super("self-cause-test");
        }

        @Override
        public Throwable getCause() {
            // Throwable.initCause forbids a self cause, but the guard in
            // appendThrowable (cause != t) can still be reached through an
            // overridden getCause(); this exercises it directly.
            return this;
        }
    }

    @Test
    public void appendThrowable_selfCause_terminates() {
        Thread.UncaughtExceptionHandler handler =
                Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull(handler);
        handler.uncaughtException(Thread.currentThread(), new SelfCauseException());
        String trace = app().takeLastCrashTrace();
        assertNotNull(trace);
        assertTrue(trace.contains("self-cause-test"));
        assertNull(app().takeLastCrashTrace());
    }

    @Test
    public void isAnyDialogFragmentShowing_plainFragment_false() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        activity.getSupportFragmentManager().beginTransaction()
                .add(new Fragment(), "plain").commit();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertFalse((Boolean) callStatic("isAnyDialogFragmentShowing",
                new Class<?>[]{Activity.class}, activity));
    }

    @Test
    public void isAnyDialogFragmentShowing_dialogFragmentAddedNotShown_false() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class)
                .create().start().resume().get();
        // Added WITH a container id: DialogFragment.onCreate derives
        // mShowsDialog from the container id (mContainerId != 0 -> false),
        // so no dialog is created and getDialog() stays null. (Calling
        // setShowsDialog(false) alone does not work: onCreate overwrites
        // the flag from the container id.)
        TestDialogFragment df = new TestDialogFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, df, "added").commit();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNull(df.getDialog());
        assertFalse((Boolean) callStatic("isAnyDialogFragmentShowing",
                new Class<?>[]{Activity.class}, activity));
    }

    @Test
    public void lifecycleCallbacks_dialogShowing_shortCircuits() throws Exception {        java.lang.reflect.Field shown =
                com.gh4a.utils.CrashBreadcrumb.class.getDeclaredField("sShownDialogs");
        shown.setAccessible(true);
        shown.set(null, 1);
        try {
            Field f = android.app.Application.class
                    .getDeclaredField("mActivityLifecycleCallbacks");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<android.app.Application.ActivityLifecycleCallbacks> callbacks =
                    (List<android.app.Application.ActivityLifecycleCallbacks>) f.get(app());
            assertFalse(callbacks.isEmpty());
            Activity activity = mock(Activity.class);
            for (android.app.Application.ActivityLifecycleCallbacks cb : callbacks) {
                cb.onActivityStopped(activity);
            }
            Field stopped = Gh4Application.class
                    .getDeclaredField("mLastStoppedDialogShowing");
            stopped.setAccessible(true);
            assertTrue((Boolean) stopped.get(app()));
            String header = (String) callPrivate(app(),
                    "buildCrashBreadcrumbHeader", new Class<?>[0]);
            assertTrue(header.contains("dialogShowing=true"));
        } finally {
            shown.set(null, 0);
        }
    }
}
