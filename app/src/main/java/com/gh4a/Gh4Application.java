/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gh4a;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.LongSparseArray;

import com.gh4a.fragment.SearchFragment;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.Background;
import com.gh4a.utils.StringUtils;
import com.gh4a.worker.NotificationsWorker;
import com.gh4a.worker.ReleaseRadarWorker;
import com.meisolsson.githubsdk.model.User;
import com.tspoon.traceur.Traceur;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The Class Gh4Application.
 */
public class Gh4Application extends Application implements
        androidx.work.Configuration.Provider,
        OnSharedPreferenceChangeListener {
    public static final String LOG_TAG = "Gh4a";

    private static Gh4Application sInstance;
    private PrettyTime mPt;

    // 0.1.23 crash breadcrumb: in-memory only, activity simple class names +
    // booleans, never any credential, repo name, URL, or user content. The
    // destroy-time DecorView crash has zero app frames, so without this the
    // destroyed activity cannot be identified from the trace. onActivityStopped
    // fires before the destroy-time crash (destroy callbacks may not), so the
    // stopped snapshot is recorded alongside the destroyed snapshot.
    private volatile String mLastResumedActivity = "?";
    private volatile String mLastStoppedActivity = "?";
    private volatile boolean mLastStoppedFinishing = false;
    private volatile boolean mLastStoppedDialogShowing = false;
    private volatile String mLastDestroyedActivity = "?";
    private volatile boolean mLastDestroyedFinishing = false;

    private static final int THEME_DARK = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_SYSTEM = 2;

    private static final String KEY_VERSION = "version";
    private static final String KEY_ACTIVE_LOGIN = "active_login";
    private static final String KEY_ALL_LOGINS = "logins";
    private static final String KEY_PREFIX_TOKEN = "token_";
    private static final String KEY_PREFIX_USER_ID = "user_id_";

    /*
     * (non-Javadoc)
     * @see android.app.Application#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;

        installCrashRecorder();
        installCrashBreadcrumbs();
        installRxErrorHandler();

        SharedPreferences prefs = getPrefs();
        // Apply saved language before anything else
        // 228：103 同款防御读取——备份错型（language 存成 int）时 getString
        // 抛 ClassCastException，冷启动循环崩溃；错型回默认 ""。
        SettingsFragment.applyLanguage(getLanguagePref(prefs));

        int prefsVersion = prefs.getInt(KEY_VERSION, 0);
        if (prefsVersion < 4) {
            SharedPreferences.Editor editor = prefs.edit()
                    .putInt(KEY_VERSION, 4);

            if (prefsVersion < 2) {
                // convert old-style login/token pref to new-style login list
                String login = prefs.getString("USER_LOGIN", null);
                String token = prefs.getString("Token", null);
                HashSet<String> loginSet = new HashSet<>();
                if (login != null && token != null) {
                    loginSet.add(login);
                }
                editor.putString(KEY_ACTIVE_LOGIN, login)
                        .putStringSet(KEY_ALL_LOGINS, loginSet)
                        .remove("USER_LOGIN")
                        .remove("Token");
                if (login != null && token != null) {
                    // 老格式 token 直接进加密存储，不落明文
                    getSecurePrefs().edit()
                            .putString(KEY_PREFIX_TOKEN + login, token)
                            .apply();
                }
            }
            if (prefsVersion < 3 && prefs.contains(KEY_ALL_LOGINS)) {
                // Convert user IDs stored with old bindings (int) to format of new
                // bindings (long) ... unfortunately we didn't update the version when
                // doing that change :-/
                for (String login : prefs.getStringSet(KEY_ALL_LOGINS, null)) {
                    try {
                        final String key = KEY_PREFIX_USER_ID + login;
                        int userId = prefs.getInt(key, -1);
                        editor.putLong(key, userId);
                    } catch (ClassCastException e) {
                        // already using the new format, ignore
                    }
                }
            }
            if (prefsVersion < 4) {
                // Convert old 'LightDark' theme to light one
                // 228：同 getThemePref——theme 存成 string 时 getInt 抛错。
                if (getThemePref(prefs, THEME_LIGHT) == 2) {
                    editor.putInt(SettingsFragment.KEY_THEME, THEME_LIGHT);
                }
            }
            editor.apply();
        }

        prefs.registerOnSharedPreferenceChangeListener(this);
        updateTheme(prefs);
        // 0.1.36：诊断追踪（进程启动时刻）。
        com.gh4a.utils.ThemeTrace.logState(this, "APP_CREATED");
        // 0.1.38：启动链埋点（只记事件，不记账号与内容）。
        com.gh4a.utils.DiagTrace.log(this, "APP_CREATED", "");
        // 0.1.12：启动时按已保存主题色启用对应桌面图标别名（已是目标状态时不重复设置）。
        com.gh4a.utils.LauncherIcon.apply(this);
        if (true || BuildConfig.DEBUG) {
            Traceur.enableLogging();
        }

        mPt = new PrettyTime();
        ServiceFactory.initClient(this);

        // P4：启动预取热门建议已删——之前冷启动即发 1 次搜索 API，即使用户
        // 从不打开搜索。热门建议改为懒加载：首次打开搜索页时 loadHotRepos
        // 本来就会拉（有缓存先显示缓存、无缓存骨架屏后填），无需提前热缓存。
        updateNotificationWorker(prefs);
    }

    private void updateNotificationWorker(SharedPreferences prefs) {
        // P12：冷启动主线程首次调 getSecurePrefs 会做 MasterKey +
        // EncryptedSharedPreferences.create（KeyStore + 磁盘 IO），调用链
        // onCreate → updateNotificationWorker → isAuthorized → getAuthToken
        // → getSecurePrefs。先用明文偏好判断（登录名 + 通知开关）：无登录
        // 或未开启时直接取消，不碰 KeyStore；需要 token 时把 isAuthorized
        // 判定与调度移到后台线程（WorkManager 的 schedule/cancel 线程安全，
        // 间隔在主线程先算好传进去）。anr-audit-and-fix skill：先证明执行
        // 线程（冷启动主线程）与阻塞调用（KeyStore/磁盘 IO），再移出主线程。
        if (!prefs.getBoolean(SettingsFragment.KEY_NOTIFICATIONS, false)
                || getAuthLogin() == null) {
            NotificationsWorker.cancel(this);
            return;
        }
        final int intervalMinutes = getNotificationIntervalMinutes(prefs);
        Background.run("update-notification-worker", () -> {
            if (isAuthorized()) {
                NotificationsWorker.schedule(this, intervalMinutes);
            } else {
                NotificationsWorker.cancel(this);
            }
        });
    }

    /**
     * 228：103 同款防御读取——备份错型（language 存成 int / theme 存成
     * string）时 getString/getInt 抛 ClassCastException，冷启动循环崩溃；
     * 经 getAll() 按 instanceof 读，错型回默认。
     */
    private static String getLanguagePref(SharedPreferences prefs) {
        Object raw = prefs.getAll().get(SettingsFragment.KEY_LANGUAGE);
        return raw instanceof String ? (String) raw : "";
    }

    private static int getThemePref(SharedPreferences prefs, int defaultTheme) {
        Object raw = prefs.getAll().get(SettingsFragment.KEY_THEME);
        return raw instanceof Number ? ((Number) raw).intValue() : defaultTheme;
    }

    /**
     * 防御式读取通知间隔：备份混入 String 值时 getInt 会抛 ClassCastException，
     * 这里容忍 String/Number，异常值回退默认 15 分钟。
     */
    private static int getNotificationIntervalMinutes(SharedPreferences prefs) {
        Object raw = prefs.getAll().get(SettingsFragment.KEY_NOTIFICATION_INTERVAL);
        int value = 15;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                value = Integer.parseInt((String) raw);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        // 103：手写/篡改备份可把间隔写成 <15 或负数；WorkManager 要求
        // PeriodicWorkRequest 间隔 >= 15 分钟，否则 onCreate 调度时抛
        // IllegalArgumentException，之后每次冷启动都崩。钳制到 [15, 1440]。
        return Math.max(15, Math.min(1440, value));
    }

    private void updateTheme(SharedPreferences prefs) {
        // 228：103 同款防御读取——theme 存成 string 时 getInt 抛
        // ClassCastException，冷启动循环崩溃；错型回默认主题。
        int theme = getThemePref(prefs,
                getResources().getInteger(R.integer.default_theme));
        final int nightMode;

        switch (theme) {
            case THEME_DARK: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            case THEME_SYSTEM: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
            default: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
        // 0.1.36：诊断追踪。
        com.gh4a.utils.ThemeTrace.logState(this, "UPDATE_THEME theme=" + theme + " night=" + nightMode);
        // Scheduled / follow-system dark mode overrides the manual theme pref
        com.gh4a.utils.DarkModeScheduler.apply(this);
        com.gh4a.utils.ThemeTrace.logState(this, "UPDATE_THEME_AFTER_SCHEDULER");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mPt = new PrettyTime(newConfig.getLocales().get(0));
        } else {
            mPt = new PrettyTime(newConfig.locale);
        }
    }

    public PrettyTime getPrettyTimeInstance() {
        return mPt;
    }

    public void setActiveLogin(String login) {
        // 68：KEY_ALL_LOGINS 缺失/损坏时 getStringSet 返回 null，直接 contains
        // 会 NPE（启动/切账号路径）；正常首启迁移保证非 null，这里是边缘设防。
        java.util.Set<String> allLogins = getPrefs().getStringSet(KEY_ALL_LOGINS, null);
        if (allLogins != null && allLogins.contains(login)) {
            getPrefs().edit()
                    .putString(KEY_ACTIVE_LOGIN, login)
                    .apply();
            // P1：切账号后未读数缓存属于旧账号，单例跨切换存活，必须失效。
            com.gh4a.utils.NotificationCountProvider.getInstance().invalidate();
            rescheduleWorkers();
        }
    }

    public String getAuthLogin() {
        return getPrefs().getString(KEY_ACTIVE_LOGIN, null);
    }

    public LongSparseArray<String> getAccounts() {
        LongSparseArray<String> accounts = new LongSparseArray<>();
        // 68：同 setActiveLogin，键缺失时返回空列表而非 NPE。
        java.util.Set<String> allLogins = getPrefs().getStringSet(KEY_ALL_LOGINS, null);
        if (allLogins == null) {
            return accounts;
        }
        for (String login : allLogins) {
            long id = getPrefs().getLong(KEY_PREFIX_USER_ID + login, -1);
            if (id > 0) {
                accounts.put(id, login);
            }
        }
        return accounts;
    }

    public String getAuthToken() {
        String login = getAuthLogin();
        return login != null ? getSecurePrefs().getString(KEY_PREFIX_TOKEN + login, null) : null;
    }

    public void addAccount(User user, String token) {
        SharedPreferences prefs = getPrefs();
        String login = user.login();
        final Set<String> logins = StringUtils.getEditableStringSetFromPrefs(prefs, KEY_ALL_LOGINS);
        logins.add(login);

        // 226：User.id() 可空，直接拆箱进 putLong 即 NPE（登录成功瞬间）；
        // 缺 id 存 -1，与 getAccounts 的 id > 0 过滤一致。
        prefs.edit()
                .putString(KEY_ACTIVE_LOGIN, login)
                .putStringSet(KEY_ALL_LOGINS, logins)
                .putLong(KEY_PREFIX_USER_ID + login, user.id() != null ? user.id() : -1)
                .apply();
        // token 单独走加密存储，不进明文 prefs
        getSecurePrefs().edit()
                .putString(KEY_PREFIX_TOKEN + login, token)
                .apply();

        rescheduleWorkers();
    }

    public User getCurrentAccountInfoForAvatar() {
        String login = getAuthLogin();
        if (login != null) {
            long userId = getPrefs().getLong(KEY_PREFIX_USER_ID + login, -1);
            if (userId >= 0) {
                return User.builder().login(login).id(userId).build();
            }
        }
        return null;
    }

    public void setCurrentAccountInfo(User user) {
        // 226：同 addAccount，User.id() 可空，缺 id 存 -1。
        getPrefs().edit()
                .putLong(KEY_PREFIX_USER_ID + user.login(), user.id() != null ? user.id() : -1)
                .apply();
    }

    public void logout() {
        String login = getAuthLogin();
        if (login == null) {
            return;
        }
        removeAccount(login);
    }

    /**
     * Remove any account (not just the active one). If the removed account was
     * active, another one becomes active. Returns the new active login, or
     * null when no accounts are left.
     */
    public String removeAccount(String login) {
        SharedPreferences prefs = getPrefs();
        Set<String> logins = StringUtils.getEditableStringSetFromPrefs(prefs, KEY_ALL_LOGINS);
        logins.remove(login);

        String newActiveLogin;
        if (login.equals(getAuthLogin())) {
            newActiveLogin = logins.isEmpty() ? null : logins.iterator().next();
        } else {
            newActiveLogin = getAuthLogin();
        }

        prefs.edit()
                .putString(KEY_ACTIVE_LOGIN, newActiveLogin)
                .putStringSet(KEY_ALL_LOGINS, logins)
                .remove(KEY_PREFIX_USER_ID + login)
                .apply();
        // 先取出 token 再删：删后去服务端撤销（M-7）
        String token = getSecurePrefs().getString(KEY_PREFIX_TOKEN + login, null);
        getSecurePrefs().edit()
                .remove(KEY_PREFIX_TOKEN + login)
                .apply();
        if (token != null) {
            revokeTokenAsync(token);
        }
        clearWebViewData();
        // SEC-6 + BUG-27: logout() delegates here, so there is no separate
        // mere-logout path in this codebase — account removal clears the
        // shared HTTP caches and this login's per-login caches, so a later
        // account cannot be served the removed account's private data.
        ServiceFactory.evictCaches();
        clearPerLoginCaches(login);

        rescheduleWorkers();
        return newActiveLogin;
    }

    /**
     * M-7: 删号/登出时撤销服务端的 OAuth token，best-effort 后台执行，
     * 失败（无网络等）不影响本地登出流程。
     */
    private void revokeTokenAsync(String token) {
        if (BuildConfig.CLIENT_ID.isEmpty() || BuildConfig.CLIENT_SECRET.isEmpty()) {
            return;
        }
        Background.run("token-revoke", () -> {
            try {
                String url = "https://api.github.com/applications/"
                        + BuildConfig.CLIENT_ID + "/token";
                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        "{\"access_token\":\"" + token + "\"}");
                Request request = new Request.Builder()
                        .url(url)
                        .delete(body)
                        .header("Authorization",
                                Credentials.basic(BuildConfig.CLIENT_ID, BuildConfig.CLIENT_SECRET))
                        .header("Accept", "application/vnd.github+json")
                        .build();
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.w(LOG_TAG, "Token revocation returned " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "Token revocation failed", e);
            }
        });
    }

    /**
     * M-7: 删号/登出时清掉 WebView 的 cookie 与本地存储，避免残留登录态。
     * 从未创建过 WebView 时 getInstance 可能抛异常，直接忽略。
     */
    private void clearWebViewData() {
        try {
            android.webkit.CookieManager cm = android.webkit.CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (Exception e) {
            Log.w(LOG_TAG, "Clear cookies failed", e);
        }
        try {
            android.webkit.WebStorage.getInstance().deleteAllData();
        } catch (Exception e) {
            Log.w(LOG_TAG, "Clear web storage failed", e);
        }
    }

    /**
     * Re-schedule background workers after an account switch, removal or
     * addition, so they run with the current account's token and settings.
     */
    public void rescheduleWorkers() {
        SharedPreferences prefs = getPrefs();
        updateNotificationWorker(prefs);
        if (prefs.getBoolean(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS, false)) {
            ReleaseRadarWorker.schedule(this, getReleaseRadarIntervalMinutes(prefs));
        } else {
            ReleaseRadarWorker.cancel(this);
        }
    }

    /**
     * Reads the release radar interval defensively. The setting is an
     * IntegerListPreference which persists an int, but a String value is
     * tolerated as well so a type mismatch can never crash the app again.
     */
    private static int getReleaseRadarIntervalMinutes(SharedPreferences prefs) {
        Object raw = prefs.getAll().get(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL);
        int value = 360;
        if (raw instanceof Number) {
            value = ((Number) raw).intValue();
        } else if (raw instanceof String) {
            try {
                value = Integer.parseInt((String) raw);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        // 103：同通知间隔——篡改备份写 <15 分钟会在 rescheduleWorkers
        // 调度 ReleaseRadarWorker 时抛 IllegalArgumentException；钳制到
        // [15, 1440]（合法设置值 360/720/1440 本就在此区间内）。
        return Math.max(15, Math.min(1440, value));
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(SettingsFragment.PREF_NAME, MODE_PRIVATE);
    }

    private SharedPreferences mSecurePrefs;
    /** True only while the current secure store is the plaintext fallback. */
    private boolean mSecurePrefsPlainFallback;
    /** SEC-5: plaintext fallback file — deliberately NOT "Gh4a-secure", so a
     * later successful encrypted open can never read the fallback file as
     * if it were the encrypted store (format clash in the old code). */
    private static final String PLAIN_FALLBACK_PREFS = "Gh4a-secure-plain";
    /** Device-state flag in Gh4a-pref: set when the fallback is in use,
     * cleared once encrypted storage opens again. Never restored from a
     * backup (not in the restore whitelist), so it always reflects this
     * device. Login UI reads it via isSecurePrefsPlainFallback(). */
    private static final String KEY_SECURE_PLAINTEXT_FALLBACK = "secure_prefs_plaintext_fallback";

    /** True if tokens are currently held in the plaintext fallback file. */
    public boolean isSecurePrefsPlainFallback() {
        return mSecurePrefsPlainFallback;
    }

    /**
     * Encrypted storage for GitHub OAuth tokens (H-1). Keys are AES-256-GCM
     * encrypted with a key kept in the AndroidKeyStore; the file on disk
     * never contains a plaintext token.
     * Falls back to plain prefs only if the keystore is unusable, so login
     * never hard-fails on exotic devices — but the fallback now uses a
     * DIFFERENT file (Gh4a-secure-plain), is flagged visibly at login
     * (Github4AndroidActivity.onLoginFinished), and is migrated away and
     * deleted as soon as encrypted storage opens again (SEC-5).
     */
    private synchronized SharedPreferences getSecurePrefs() {
        if (mSecurePrefs == null) {
            SharedPreferences legacyPlain = getSharedPreferences("Gh4a-secure", MODE_PRIVATE);
            try {
                MasterKey masterKey = new MasterKey.Builder(this)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                mSecurePrefs = EncryptedSharedPreferences.create(
                        this, "Gh4a-secure", masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                mSecurePrefsPlainFallback = false;
                getPrefs().edit()
                        .remove(KEY_SECURE_PLAINTEXT_FALLBACK)
                        .apply();
            } catch (Exception e) {
                android.util.Log.w(LOG_TAG, "Encrypted prefs unavailable, using plain fallback", e);
                mSecurePrefs = getSharedPreferences(PLAIN_FALLBACK_PREFS, MODE_PRIVATE);
                mSecurePrefsPlainFallback = true;
                getPrefs().edit()
                        .putBoolean(KEY_SECURE_PLAINTEXT_FALLBACK, true)
                        .apply();
            }
            migratePlaintextTokens(legacyPlain);
            // Last, so its (newest) tokens win over any older legacy copy:
            // migrate the new plaintext fallback file into the encrypted
            // store and delete it. Skipped on the fallback path itself.
            if (!mSecurePrefsPlainFallback) {
                migratePlaintextTokensFromFallbackFile();
            }
        }
        return mSecurePrefs;
    }

    /**
     * Encrypted prefs shared by the GitHub token and the translation
     * credentials (S-H2): the file on disk never contains a plaintext
     * credential (same store as the OAuth token).
     */
    public static SharedPreferences securePrefs(android.content.Context context) {
        Gh4Application app = (Gh4Application) context.getApplicationContext();
        return app.getSecurePrefs();
    }

    /**
     * SEC-5: when encrypted storage opens successfully, move any token_*
     * keys out of the plaintext fallback file into the encrypted store,
     * then delete the fallback file outright. Runs only on the encrypted
     * path; the fallback path never calls it.
     * 101: the fallback file can also hold translation credentials
     * (translation_api_key_<provider> / translation_api_secret_<provider>,
     * written there while the KeyStore is down - see TranslationManager)
     * and a pending OAuth login's state/verifier; move those too before
     * clearing, or they would be deleted along with the tokens.
     */
    private void migratePlaintextTokensFromFallbackFile() {
        SharedPreferences plain = getSharedPreferences(PLAIN_FALLBACK_PREFS, MODE_PRIVATE);
        SharedPreferences.Editor secureEditor = null;
        Set<String> logins = getPrefs().getStringSet(KEY_ALL_LOGINS, null);
        if (logins != null && !logins.isEmpty()) {
            for (String login : logins) {
                String key = KEY_PREFIX_TOKEN + login;
                if (plain.contains(key)) {
                    if (secureEditor == null) {
                        secureEditor = mSecurePrefs.edit();
                    }
                    secureEditor.putString(key, plain.getString(key, null));
                }
            }
        }
        // 101: translation credentials and pending OAuth keys live in the
        // same fallback file while the KeyStore is down; migrate them as
        // well instead of dropping them with clear() below. Translation
        // values restored from an old backup are plaintext here and are
        // encrypted in place on next read (TranslationManager.getCredential).
        // Key prefixes match SettingsBackupManager.isTranslationCredentialKey;
        // the OAuth key names match Github4AndroidActivity (private there).
        for (Map.Entry<String, ?> entry : plain.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            if (key.startsWith("translation_api_key")
                    || key.startsWith("translation_api_secret")
                    || "oauth_state_pending".equals(key)
                    || "oauth_verifier_pending".equals(key)) {
                if (secureEditor == null) {
                    secureEditor = mSecurePrefs.edit();
                }
                secureEditor.putString(key, (String) value);
            }
        }
        if (secureEditor != null) {
            secureEditor.apply();
        }
        plain.edit().clear().apply();
        deletePrefsFile(PLAIN_FALLBACK_PREFS);
    }

    /** Deletes a SharedPreferences file (API 24+ API, file fallback below). */
    private void deletePrefsFile(String name) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                deleteSharedPreferences(name);
            } else {
                java.io.File f = new java.io.File(
                        getApplicationInfo().dataDir + "/shared_prefs/" + name + ".xml");
                if (f.exists()) {
                    f.delete();
                }
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Delete prefs file failed: " + name, e);
        }
    }

    /**
     * SEC-6/BUG-27: clears the removed login's per-login caches. Called
     * from removeAccount() only (logout() delegates to it). The star-group
     * file is per-login by name (star_groups_&lt;login&gt;); the other three
     * files are shared but keyed per login, so only this login's keys go.
     */
    private void clearPerLoginCaches(String login) {
        try {
            getSharedPreferences("user_lists_cache", MODE_PRIVATE).edit()
                    .remove("lists_" + login)
                    .remove("time_" + login)
                    .apply();
            getSharedPreferences("radar_cache", MODE_PRIVATE).edit()
                    .remove("items_" + login)
                    .remove("time_" + login)
                    .apply();
            SharedPreferences readPrefs =
                    getSharedPreferences("star_update_read", MODE_PRIVATE);
            if (readPrefs.contains("read_" + login)) {
                readPrefs.edit().remove("read_" + login).apply();
            }
            deletePrefsFile("star_groups_" + login);
            // 0.1.20 F-32: 登出/删号同步清除该登录的我页头缓存。
            com.gh4a.fragment.MeHeaderCache.clear(this, login);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Clear per-login caches failed", e);
        }
    }

    /**
     * One-time migration: move any plaintext token_<login> left in the old
     * prefs file into the encrypted store, then delete the plaintext copy.
     */
    private void migratePlaintextTokens(SharedPreferences oldPlainPrefs) {
        SharedPreferences plain = getPrefs();
        Set<String> logins = plain.getStringSet(KEY_ALL_LOGINS, null);
        if (logins == null || logins.isEmpty()) {
            return;
        }
        SharedPreferences.Editor plainEditor = null;
        SharedPreferences.Editor secureEditor = null;
        for (String login : logins) {
            String key = KEY_PREFIX_TOKEN + login;
            String token = plain.getString(key, null);
            if (token != null) {
                if (secureEditor == null) {
                    secureEditor = mSecurePrefs.edit();
                    plainEditor = plain.edit();
                }
                secureEditor.putString(key, token);
                plainEditor.remove(key);
            }
            // Also sweep the pre-migration plain fallback file, if we fell back
            if (oldPlainPrefs != mSecurePrefs && oldPlainPrefs.contains(key)) {
                if (secureEditor == null) {
                    secureEditor = mSecurePrefs.edit();
                    plainEditor = plain.edit();
                }
                secureEditor.putString(key, oldPlainPrefs.getString(key, null));
                oldPlainPrefs.edit().remove(key).apply();
            }
        }
        if (secureEditor != null) {
            secureEditor.apply();
            plainEditor.apply();
        }
    }

    public static Gh4Application get() {
        return sInstance;
    }

    /**
     * Records uncaught crashes to a file so the user can report the stack trace.
     * The file is shown on next launch (see HomeActivity).
     */
    /** Max bytes kept for a recorded crash trace (was 8000, which cut off
     * CompositeException causal chains mid-chain). If the full trace is
     * longer, the middle is dropped, never the causal-chain tail. */
    private static final int CRASH_TRACE_CAP = 65536;

    /**
     * 0.1.23: tracks the lifecycle snapshots the crash header reports.
     * DialogFragment visibility is read from the stopped activity's
     * FragmentManager; plain AlertDialogs are covered only when they go
     * through the central helpers that report to CrashBreadcrumb, so
     * dialogShowing is a lower bound (see CrashBreadcrumb).
     */
    private void installCrashBreadcrumbs() {
        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(android.app.Activity activity) {
            }

            @Override
            public void onActivityResumed(android.app.Activity activity) {
                mLastResumedActivity = activity.getClass().getSimpleName();
            }

            @Override
            public void onActivityPaused(android.app.Activity activity) {
            }

            @Override
            public void onActivityStopped(android.app.Activity activity) {
                mLastStoppedActivity = activity.getClass().getSimpleName();
                mLastStoppedFinishing = activity.isFinishing();
                mLastStoppedDialogShowing = com.gh4a.utils.CrashBreadcrumb.isAnyDialogShowing()
                        || isAnyDialogFragmentShowing(activity);
            }

            @Override
            public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(android.app.Activity activity) {
                mLastDestroyedActivity = activity.getClass().getSimpleName();
                mLastDestroyedFinishing = activity.isFinishing();
            }
        });
    }

    private static boolean isAnyDialogFragmentShowing(android.app.Activity activity) {
        if (!(activity instanceof androidx.fragment.app.FragmentActivity)) {
            return false;
        }
        for (androidx.fragment.app.Fragment fragment
                : ((androidx.fragment.app.FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
            if (fragment instanceof androidx.fragment.app.DialogFragment) {
                android.app.Dialog dialog = ((androidx.fragment.app.DialogFragment) fragment).getDialog();
                if (dialog != null && dialog.isShowing()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Header prepended to every recorded crash trace (never truncated away). */
    private String buildCrashBreadcrumbHeader() {
        return "[crash breadcrumb] lastStopped=" + mLastStoppedActivity
                + " stoppingFinishing=" + mLastStoppedFinishing
                + " lastDestroyed=" + mLastDestroyedActivity
                + " destroyedFinishing=" + mLastDestroyedFinishing
                + " lastResumed=" + mLastResumedActivity
                + " dialogShowing=" + (com.gh4a.utils.CrashBreadcrumb.isAnyDialogShowing()
                        || mLastStoppedDialogShowing)
                // 0.1.24: 最近一次 App 内 finish() 调用点；"none" 且
                // destroyingFinishing=true 时，finish 来自系统清栈侧。
                + " lastFinish=" + com.gh4a.utils.CrashBreadcrumb.getLastFinish();
    }

    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                // SEC-4: redact BEFORE writing — last_crash.txt is shown in a
                // selectable dialog the user is invited to screenshot/share.
                String full = redactCrashTrace(buildFullCrashTrace(throwable));
                String text = full;
                if (full.length() > CRASH_TRACE_CAP) {
                    int half = CRASH_TRACE_CAP / 2;
                    text = full.substring(0, half)
                            + "\n... [trace truncated in the middle; head and causal-chain tail kept] ...\n"
                            + full.substring(full.length() - half);
                }
                java.io.FileWriter w = new java.io.FileWriter(
                        new java.io.File(getCacheDir(), "last_crash.txt"));
                // 0.1.23: breadcrumb header first (after truncation, so it is
                // never cut away); it holds only class names + booleans.
                w.write(buildCrashBreadcrumbHeader() + "\n" + text);
                w.close();
                // 0.1.38：崩溃历史持久化——除了一次性 last_crash.txt（弹框用），
                // 另存一份到私有文件目录 diagnostic/，最多留 3 条，读后不清。
                com.gh4a.utils.DiagTrace.recordCrash(this,
                        buildCrashBreadcrumbHeader(), text);
                com.gh4a.utils.DiagTrace.log(this, "CRASH_RECORDED",
                        throwable != null ? throwable.getClass().getSimpleName() : "?");
            } catch (Exception ignored) {
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    /**
     * S-L1：日志里的 URL 去 query 再记——GitHub 签名下载 URL 的 query 含时效
     * token，ChainDownloadHelper 写下载记录时已有同款 clearQuery 处理，
     * 这里给日志路径复用。解析失败时不输出原串。
     */
    public static String redactUrlForLog(String url) {
        if (url == null) {
            return null;
        }
        try {
            Uri uri = Uri.parse(url);
            Uri.Builder builder = uri.buildUpon().clearQuery();
            // 214：只去 query 不够——镜像 URL 可带 userinfo
            //（https://user:password@host/…），密码会原样进 logcat；
            // 剥掉 userinfo，只留 host 与端口（IPv6 host 补括号）。
            if (uri.getUserInfo() != null) {
                String host = uri.getHost();
                if (host == null) {
                    return "[unparseable-url]";
                }
                if (host.contains(":")) {
                    host = "[" + host + "]";
                }
                String authority = host;
                if (uri.getPort() != -1) {
                    authority += ":" + uri.getPort();
                }
                builder.encodedAuthority(authority);
            }
            return builder.build().toString();
        } catch (Exception ignored) {
            return "[unparseable-url]";
        }
    }

    /**
     * SEC-4: strips credential-shaped material from a crash trace before it
     * is persisted or shown: token/Authorization/access_token values,
     * GitHub-style tokens, URL query strings (signed URLs carry tokens),
     * and over-long URLs (truncated, query already removed). Stack frames
     * and exception class/message structure are preserved; only values
     * are replaced. Private repo names in paths/messages are NOT removed
     * by construction — the dialog warns the user to check before sharing.
     */
    private static String redactCrashTrace(String trace) {
        if (trace == null) {
            return null;
        }
        String out = trace;
        // GitHub-style tokens (values only, pattern-matched, never logged).
        out = out.replaceAll("(?i)\\b(ghp_[A-Za-z0-9]{20,}|gho_[A-Za-z0-9]{20,}"
                + "|ghu_[A-Za-z0-9]{20,}|ghs_[A-Za-z0-9]{20,}|ghr_[A-Za-z0-9]{20,}"
                + "|github_pat_[A-Za-z0-9_]{20,})\\b", "[redacted-token]");
        // Authorization / token / secret / password assignments.
        out = out.replaceAll("(?i)(authorization[\"'\\s:=]+)(Bearer\\s+|Token\\s+)?[^\\s\"'\\]]+",
                "$1[redacted]");
        out = out.replaceAll("(?i)(access_token|api[_-]?key|client_secret|secret|password|passwd|pwd)"
                + "([\"'\\s:=]+)[^\\s\"'\\],]+", "$1$2[redacted]");
        // Bare "token: <value>" / "token=<value>" leftovers.
        out = out.replaceAll("(?i)(\\btoken[\"'\\s:=]+)[^\\s\"'\\],]{8,}", "$1[redacted]");
        // URLs: drop the query string, then truncate what is left.
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+").matcher(out);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String url = m.group();
            // 229：URL 可能含 userinfo（https://user:password@host/…），
            // 只去 query 会让镜像密码原样进 last_crash.txt（弹窗邀请用户
            // 截图/分享的文件）；先剥 authority 段的 userinfo。
            int schemeEnd = url.indexOf("://");
            if (schemeEnd >= 0) {
                int authEnd = url.length();
                for (int i = schemeEnd + 3; i < url.length(); i++) {
                    char c = url.charAt(i);
                    if (c == '/' || c == '?' || c == '#') {
                        authEnd = i;
                        break;
                    }
                }
                int at = url.indexOf('@', schemeEnd + 3);
                if (at >= 0 && at < authEnd) {
                    url = url.substring(0, schemeEnd + 3) + "[userinfo-redacted]@"
                            + url.substring(at + 1);
                }
            }
            int q = url.indexOf('?');
            if (q >= 0) {
                url = url.substring(0, q) + "?[query-redacted]";
            }
            if (url.length() > 128) {
                url = url.substring(0, 128) + "...[url-truncated]";
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(url));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Walks the FULL throwable chain recursively (with a cycle guard) and,
     * for RxJava CompositeExceptions, every exception inside getExceptions()
     * with its own stack — the old code wrote only one cause line and no
     * nested stacks, so a CompositeException's underlying errors were lost. */
    private static String buildFullCrashTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<Throwable> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        appendThrowable(sb, throwable, "", seen);
        return sb.toString();
    }

    private static void appendThrowable(StringBuilder sb, Throwable t,
            String header, java.util.Set<Throwable> seen) {
        if (t == null || !seen.add(t)) {
            if (t != null) {
                sb.append(header).append("[circular reference: ")
                        .append(t.toString()).append("]\n");
            }
            return;
        }
        sb.append(header).append(t.toString()).append("\n");
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append("    at ").append(e.toString()).append("\n");
        }
        if (t instanceof io.reactivex.exceptions.CompositeException) {
            java.util.List<Throwable> inner =
                    ((io.reactivex.exceptions.CompositeException) t).getExceptions();
            for (int i = 0; i < inner.size(); i++) {
                sb.append("CompositeException exception [")
                        .append(i).append("]:\n");
                appendThrowable(sb, inner.get(i), "  ", seen);
            }
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            appendThrowable(sb, cause, "Caused by: ", seen);
        }
    }

    /**
     * Handles RxJava UndeliverableExceptions. When a fragment is switched away,
     * its disposed SingleFromCallable gets its IO thread interrupted, producing
     * an InterruptedIOException/InterruptedException that has nowhere to go.
     * Without this handler RxJava rethrows it as an uncaught exception -> crash.
     */
    private void installRxErrorHandler() {
        io.reactivex.plugins.RxJavaPlugins.setErrorHandler(e -> {
            Throwable cause = e instanceof io.reactivex.exceptions.UndeliverableException
                    ? e.getCause() : e;
            if (cause instanceof InterruptedException
                    || cause instanceof java.io.InterruptedIOException) {
                // Expected: flow was disposed (e.g., user switched pages). Ignore.
                return;
            }
            if (cause instanceof java.io.IOException && "Canceled".equals(cause.getMessage())) {
                // 0.1.24: OkHttp 取消信号——在途 GraphQL 调用被 dispose 取消
                // （如 Star 页加载中途切走）后，无主错误经此到达。只吞字面
                // "Canceled"：它只会在下游已 dispose 时经插件处理器到达此处，
                // 下游存活时的正常 onError 根本不走这里；其他 IOException
                // （超时、HTTP 错等）仍按原样转发，行为不变。之所以不在
                // GraphQLClient 里转义：该异常到达崩溃记录的唯一路径就是
                // 这里的转发，客户端改写异常类型会误导所有同步调用方。
                android.util.Log.w("Gh4Application",
                        "Undeliverable RxJava Canceled error, ignored", cause);
                return;
            }
            if (cause instanceof io.reactivex.exceptions.UndeliverableException) {
                // Other undeliverables (e.g., network error after dispose): log, don't crash.
                android.util.Log.w("Gh4Application",
                        "Undeliverable RxJava error", cause);
                return;
            }
            if (cause instanceof io.reactivex.exceptions.CompositeException
                    || e instanceof io.reactivex.exceptions.CompositeException) {
                // Unwrap CompositeExceptions (bare or inside Undeliverable):
                // the old code forwarded the whole composite to the uncaught
                // handler, so a disposed request erroring again after a page
                // switch showed up as a crash dialog. Only forward when at
                // least one leaf is NOT a benign dispose-related error;
                // real bugs (NPE, Resources$NotFound, IllegalState, ...) are
                // never swallowed.
                Throwable composite = cause instanceof io.reactivex.exceptions.CompositeException
                        ? cause : e;
                if (isAllBenignDisposeErrors(composite)) {
                    android.util.Log.w("Gh4Application",
                            "Benign disposed CompositeException, not crashing", composite);
                    return;
                }
            }
            Thread t = Thread.currentThread();
            Thread.UncaughtExceptionHandler h = t.getUncaughtExceptionHandler();
            if (h != null) {
                h.uncaughtException(t, cause != null ? cause : e);
            }
        });
    }

    /** True only if EVERY leaf of t (recursively flattening nested
     * CompositeExceptions, and unwrapping UndeliverableException) is a
     * benign dispose-related error: an interruption, or an IOException
     * from a call that was cancelled/disposed. Any other leaf — including
     * an empty composite, which proves nothing — makes this false. */
    private static boolean isAllBenignDisposeErrors(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof io.reactivex.exceptions.UndeliverableException) {
            return isAllBenignDisposeErrors(t.getCause());
        }
        if (t instanceof io.reactivex.exceptions.CompositeException) {
            java.util.List<Throwable> inner =
                    ((io.reactivex.exceptions.CompositeException) t).getExceptions();
            if (inner.isEmpty()) {
                return false;
            }
            for (Throwable leaf : inner) {
                if (!isAllBenignDisposeErrors(leaf)) {
                    return false;
                }
            }
            return true;
        }
        return t instanceof InterruptedException
                || t instanceof java.io.InterruptedIOException
                || t instanceof java.io.IOException;
    }

    /** Returns the recorded crash trace, or null if none. Clears it after reading. */
    public String takeLastCrashTrace() {
        try {
            java.io.File f = new java.io.File(getCacheDir(), "last_crash.txt");
            if (!f.exists()) {
                return null;
            }
            byte[] bytes = new byte[(int) f.length()];
            // 109：try-with-resources + 循环读满（70 同款）；单次 read() 大文件/
            // 慢存储下可能读不全，read() 抛异常时 close() 也走不到。
            int read = 0;
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                while (read < bytes.length) {
                    int n = in.read(bytes, read, bytes.length - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
            }
            f.delete();
            return read > 0 ? new String(bytes, 0, read, "UTF-8") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isAuthorized() {
        return getAuthLogin() != null && getAuthToken() != null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // 227：key 可为 null（targetSdk 35 + Android R+ 时 clear() 回调的
        // key 为 null，如备份恢复失败回滚的 editor.clear()）；三处
        // key.equals(...) 无守卫即 NPE，恢复流程本身崩溃。
        if (key == null) {
            return;
        }
        // 0.1.36：诊断追踪。
        com.gh4a.utils.ThemeTrace.logState(this, "LISTENER key=" + key);
        if (key.equals(SettingsFragment.KEY_THEME)) {
            updateTheme(sharedPreferences);
        }
        // 0.1.35：模式变了也要重算开关——此前只看主题键，跟随系统→浅色
        // （主题 1→1 不变）时监听全程静默，开关滞留跟随系统。updateTheme
        // 先按主题定开关、再按模式覆盖（系统档覆盖为跟随系统，手动档不覆盖），
        // 所以模式监听走同一入口是安全的。
        if (key.equals(com.gh4a.utils.DarkModeScheduler.KEY_DARK_MODE)) {
            updateTheme(sharedPreferences);
        }
        // 0.1.12：主题色一改（我页/设置页均写同一偏好），桌面图标别名跟着切。
        if (key.equals(SettingsFragment.KEY_ACCENT_COLOR)) {
            com.gh4a.utils.LauncherIcon.apply(this,
                    sharedPreferences.getString(SettingsFragment.KEY_ACCENT_COLOR, "green"));
        }
    }

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder().build();
    }
}
