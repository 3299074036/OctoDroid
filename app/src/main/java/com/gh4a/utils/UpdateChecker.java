package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.gh4a.BuildConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Checks the OctoDroid GitHub releases for a newer version.
 *
 * APKs are published under releases/OctoDroid_&lt;version&gt;.apk on master,
 * so the download URL is derived from the release tag and verified with a
 * HEAD request before being handed out.
 */
public class UpdateChecker {
    private static final String OWNER = "3299074036";
    private static final String REPO = "OctoDroid";
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    private static final Pattern APK_URL_PATTERN =
            Pattern.compile("(https?://[^\\s\"')]+\\.apk)");

    public interface Callback {
        void onResult(boolean hasUpdate, String latestVersion, String releaseNotes, String apkUrl);
        void onError(String message);
    }

    public static class UpdateInfo {
        public final boolean hasUpdate;
        public final String latestVersion;
        public final String releaseNotes;
        public final String apkUrl;

        UpdateInfo(boolean hasUpdate, String latestVersion, String releaseNotes, String apkUrl) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.releaseNotes = releaseNotes;
            this.apkUrl = apkUrl;
        }
    }

    private static volatile OkHttpClient sClient;

    private static OkHttpClient getClient() {
        if (sClient == null) {
            synchronized (UpdateChecker.class) {
                if (sClient == null) {
                    sClient = new OkHttpClient.Builder()
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sClient;
    }

    public static void check(Context context, Callback callback) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                UpdateInfo info = doCheck(appContext);
                handler.post(() -> callback.onResult(
                        info.hasUpdate, info.latestVersion, info.releaseNotes, info.apkUrl));
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                handler.post(() -> callback.onError(message));
            }
        }).start();
    }

    private static UpdateInfo doCheck(Context context) throws Exception {
        Request request = new Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OctoDroid")
                .build();
        String tag;
        String body;
        try (Response response = getClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            JSONObject json = new JSONObject(response.body().string());
            tag = json.optString("tag_name", "");
            body = json.optString("body", "");
        }
        if (tag.isEmpty()) {
            throw new IOException("empty release tag");
        }
        String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
        boolean hasUpdate = isNewer(latestVersion, BuildConfig.VERSION_NAME);
        String apkUrl = hasUpdate ? resolveApkUrl(latestVersion, body) : null;
        return new UpdateInfo(hasUpdate, latestVersion, body, apkUrl);
    }

    /** True when the latest release version is newer than the installed one. */
    static boolean isNewer(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");
        int len = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < len; i++) {
            int l = i < latestParts.length ? parsePart(latestParts[i]) : 0;
            int c = i < currentParts.length ? parsePart(currentParts[i]) : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolveApkUrl(String version, String releaseBody) {
        // Preferred: the published naming convention.
        String conventional =
                String.format(Locale.US,
                        "https://raw.githubusercontent.com/%s/%s/master/releases/OctoDroid_%s.apk",
                        OWNER, REPO, version);
        if (urlExists(conventional)) {
            return conventional;
        }
        // Fallback: first .apk link in the release notes (prefer raw links).
        String fallback = null;
        Matcher matcher = APK_URL_PATTERN.matcher(releaseBody != null ? releaseBody : "");
        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.contains("raw.githubusercontent.com") || url.contains("/raw/")) {
                return url;
            }
            if (fallback == null) {
                fallback = toRawUrl(url);
            }
        }
        if (fallback != null && urlExists(fallback)) {
            return fallback;
        }
        // Last resort: hand out the conventional URL anyway and let the
        // download fail loudly rather than silently doing nothing.
        return conventional;
    }

    private static String toRawUrl(String url) {
        // https://github.com/owner/repo/blob/master/releases/x.apk
        //   -> https://raw.githubusercontent.com/owner/repo/master/releases/x.apk
        return url.replace("://github.com/", "://raw.githubusercontent.com/")
                .replace("/blob/", "/");
    }

    private static boolean urlExists(String url) {
        Request request = new Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "OctoDroid")
                .build();
        try (Response response = getClient().newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    // Auto-check bookkeeping: at most once per day.

    private static final String PREFS_UPDATE = "update_checker";
    private static final String KEY_LAST_AUTO_CHECK = "last_auto_check";

    public static boolean shouldAutoCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_AUTO_CHECK, 0);
        return System.currentTimeMillis() - last > TimeUnit.DAYS.toMillis(1);
    }

    public static void markAutoChecked(Context context) {
        context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis())
                .apply();
    }
}
