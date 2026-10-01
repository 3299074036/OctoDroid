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
import android.os.Build;
import android.util.LongSparseArray;

import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.StringUtils;
import com.gh4a.worker.NotificationsWorker;
import com.gh4a.worker.ReleaseRadarWorker;
import com.meisolsson.githubsdk.model.User;
import com.tspoon.traceur.Traceur;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * The Class Gh4Application.
 */
public class Gh4Application extends Application implements
        androidx.work.Configuration.Provider,
        OnSharedPreferenceChangeListener {
    public static final String LOG_TAG = "Gh4a";

    private static Gh4Application sInstance;
    private PrettyTime mPt;

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
        installRxErrorHandler();

        SharedPreferences prefs = getPrefs();
        // Apply saved language before anything else
        SettingsFragment.applyLanguage(prefs.getString(SettingsFragment.KEY_LANGUAGE, ""));

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
                    editor.putString(KEY_PREFIX_TOKEN + login, token);
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
                if (prefs.getInt(SettingsFragment.KEY_THEME, THEME_LIGHT) == 2) {
                    editor.putInt(SettingsFragment.KEY_THEME, THEME_LIGHT);
                }
            }
            editor.apply();
        }

        prefs.registerOnSharedPreferenceChangeListener(this);
        updateTheme(prefs);
        if (true || BuildConfig.DEBUG) {
            Traceur.enableLogging();
        }

        mPt = new PrettyTime();
        ServiceFactory.initClient(this);

        updateNotificationWorker(prefs);
    }

    private void updateNotificationWorker(SharedPreferences prefs) {
        if (isAuthorized() && prefs.getBoolean(SettingsFragment.KEY_NOTIFICATIONS, false)) {
            int intervalMinutes = prefs.getInt(SettingsFragment.KEY_NOTIFICATION_INTERVAL, 15);
            NotificationsWorker.schedule(this, intervalMinutes);
        } else {
            NotificationsWorker.cancel(this);
        }
    }

    private void updateTheme(SharedPreferences prefs) {
        int theme = prefs.getInt(SettingsFragment.KEY_THEME,
                getResources().getInteger(R.integer.default_theme));
        final int nightMode;

        switch (theme) {
            case THEME_DARK: nightMode = AppCompatDelegate.MODE_NIGHT_YES; break;
            case THEME_SYSTEM: nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
            default: nightMode = AppCompatDelegate.MODE_NIGHT_NO; break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
        // Scheduled / follow-system dark mode overrides the manual theme pref
        com.gh4a.utils.DarkModeScheduler.apply(this);
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
        if (getPrefs().getStringSet(KEY_ALL_LOGINS, null).contains(login)) {
            getPrefs().edit()
                    .putString(KEY_ACTIVE_LOGIN, login)
                    .apply();
            rescheduleWorkers();
        }
    }

    public String getAuthLogin() {
        return getPrefs().getString(KEY_ACTIVE_LOGIN, null);
    }

    public LongSparseArray<String> getAccounts() {
        LongSparseArray<String> accounts = new LongSparseArray<>();
        for (String login : getPrefs().getStringSet(KEY_ALL_LOGINS, null)) {
            long id = getPrefs().getLong(KEY_PREFIX_USER_ID + login, -1);
            if (id > 0) {
                accounts.put(id, login);
            }
        }
        return accounts;
    }

    public String getAuthToken() {
        String login = getAuthLogin();
        return login != null ? getPrefs().getString(KEY_PREFIX_TOKEN + login, null) : null;
    }

    public void addAccount(User user, String token) {
        SharedPreferences prefs = getPrefs();
        String login = user.login();
        final Set<String> logins = StringUtils.getEditableStringSetFromPrefs(prefs, KEY_ALL_LOGINS);
        logins.add(login);

        prefs.edit()
                .putString(KEY_ACTIVE_LOGIN, login)
                .putStringSet(KEY_ALL_LOGINS, logins)
                .putString(KEY_PREFIX_TOKEN + login, token)
                .putLong(KEY_PREFIX_USER_ID + login, user.id())
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
        getPrefs().edit()
                .putLong(KEY_PREFIX_USER_ID + user.login(), user.id())
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
                .remove(KEY_PREFIX_TOKEN + login)
                .remove(KEY_PREFIX_USER_ID + login)
                .apply();

        rescheduleWorkers();
        return newActiveLogin;
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
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof String) {
            try {
                return Integer.parseInt((String) raw);
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return 360;
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(SettingsFragment.PREF_NAME, MODE_PRIVATE);
    }

    public static Gh4Application get() {
        return sInstance;
    }

    /**
     * Records uncaught crashes to a file so the user can report the stack trace.
     * The file is shown on next launch (see HomeActivity).
     */
    private void installCrashRecorder() {
        final Thread.UncaughtExceptionHandler defaultHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(throwable.toString()).append("\n");
                for (StackTraceElement e : throwable.getStackTrace()) {
                    sb.append("    at ").append(e.toString()).append("\n");
                    if (sb.length() > 8000) {
                        break;
                    }
                }
                Throwable cause = throwable.getCause();
                if (cause != null && cause != throwable) {
                    sb.append("Caused by: ").append(cause.toString()).append("\n");
                }
                java.io.FileWriter w = new java.io.FileWriter(
                        new java.io.File(getCacheDir(), "last_crash.txt"));
                w.write(sb.toString());
                w.close();
            } catch (Exception ignored) {
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
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
            if (cause instanceof io.reactivex.exceptions.UndeliverableException) {
                // Other undeliverables (e.g., network error after dispose): log, don't crash.
                android.util.Log.w("Gh4Application",
                        "Undeliverable RxJava error", cause);
                return;
            }
            Thread t = Thread.currentThread();
            Thread.UncaughtExceptionHandler h = t.getUncaughtExceptionHandler();
            if (h != null) {
                h.uncaughtException(t, cause != null ? cause : e);
            }
        });
    }

    /** Returns the recorded crash trace, or null if none. Clears it after reading. */
    public String takeLastCrashTrace() {
        try {
            java.io.File f = new java.io.File(getCacheDir(), "last_crash.txt");
            if (!f.exists()) {
                return null;
            }
            byte[] bytes = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            int read = in.read(bytes);
            in.close();
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
        if (key.equals(SettingsFragment.KEY_THEME)) {
            updateTheme(sharedPreferences);
        }
    }

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder().build();
    }
}
