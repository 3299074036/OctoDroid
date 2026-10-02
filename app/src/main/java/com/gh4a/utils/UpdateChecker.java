package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.gh4a.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
 * The APK download URL is taken from the release's assets list, picking the
 * asset that matches the current build variant (debug build -> debug APK,
 * release build -> release APK) so the download can install as an update
 * over the installed app (signatures must match). If no matching asset is
 * found, falls back to the historical convention of
 * releases/OctoDroid_&lt;version&gt;.apk on master, verified with a HEAD
 * request before being handed out.
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
        JSONObject json = fetchLatestRelease(context);
        String tag = json.optString("tag_name", "");
        String body = json.optString("body", "");
        if (tag.isEmpty()) {
            throw new IOException("empty release tag");
        }
        String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
        boolean hasUpdate = isNewer(latestVersion, BuildConfig.VERSION_NAME);
        String apkUrl = null;
        if (hasUpdate) {
            // 优先从 release assets 里拿与当前变体匹配的包：
            // debug 版拿 debug 签名包，release 版拿 release 签名包，
            // 签名一致才能覆盖安装，否则会“应用未安装”
            apkUrl = resolveApkUrlFromAssets(json.optJSONArray("assets"));
            if (apkUrl == null) {
                apkUrl = resolveApkUrl(context, latestVersion, body);
            }
        }
        return new UpdateInfo(hasUpdate, latestVersion, body, apkUrl);
    }

    /**
     * 从 releases/latest 的 assets 列表里挑与当前构建变体匹配的 APK，
     * 返回它的 browser_download_url；找不到返回 null。
     */
    private static String resolveApkUrlFromAssets(org.json.JSONArray assets) {
        if (assets == null) {
            return null;
        }
        boolean isDebug = BuildConfig.DEBUG;
        for (int i = 0; i < assets.length(); i++) {
            org.json.JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "").toLowerCase(Locale.ROOT);
            String url = asset.optString("browser_download_url", "");
            if (url.isEmpty() || !name.endsWith(".apk")) {
                continue;
            }
            boolean match = isDebug
                    ? name.contains("debug")
                    : name.contains("release") && !name.contains("debug");
            if (match) {
                return url;
            }
        }
        return null;
    }

    /**
     * 拉取 latest release 信息：先直连 api.github.com，失败且开了镜像加速时
     * 自动走镜像代理（gh-proxy 风格：{@code <mirror>/<原地址>}）。
     * 公开仓库的 releases 接口无需鉴权，走镜像不需要 token。
     * 这样开 VPN（直连被干扰）或不开 VPN（国内直连抽风）都能检测到更新。
     */
    private static JSONObject fetchLatestRelease(Context context) throws IOException {
        List<String> candidates = new ArrayList<>();
        candidates.add(LATEST_RELEASE_URL);
        if (MirrorHelper.isEnabled(context)) {
            String base = MirrorHelper.getMirrorBase(context);
            if (!base.isEmpty()) {
                candidates.add(base + "/" + LATEST_RELEASE_URL);
            }
        }
        IOException lastError = null;
        for (String url : candidates) {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "OctoDroid")
                    .build();
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    lastError = new IOException("HTTP " + response.code());
                    continue;
                }
                try {
                    return new JSONObject(response.body().string());
                } catch (JSONException e) {
                    lastError = new IOException("bad response");
                }
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("update check failed");
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

    private static String resolveApkUrl(Context context, String version, String releaseBody) {
        // Preferred: the published naming convention.
        String conventional =
                String.format(Locale.US,
                        "https://raw.githubusercontent.com/%s/%s/master/releases/OctoDroid_%s.apk",
                        OWNER, REPO, version);
        String reachable = pickReachableUrl(context, conventional);
        if (reachable != null) {
            return reachable;
        }
        // Fallback: first reachable .apk link in the release notes (prefer raw links).
        String fallback = null;
        Matcher matcher = APK_URL_PATTERN.matcher(releaseBody != null ? releaseBody : "");
        while (matcher.find()) {
            String url = matcher.group(1);
            if (url.contains("raw.githubusercontent.com") || url.contains("/raw/")) {
                String rawReachable = pickReachableUrl(context, url);
                if (rawReachable != null) {
                    return rawReachable;
                }
            }
            if (fallback == null) {
                fallback = toRawUrl(url);
            }
        }
        if (fallback != null) {
            String fallbackReachable = pickReachableUrl(context, fallback);
            if (fallbackReachable != null) {
                return fallbackReachable;
            }
        }
        // Last resort: hand out the conventional URL anyway and let the
        // download fail loudly rather than silently doing nothing.
        return conventional;
    }

    /**
     * 在直连地址和镜像地址中挑一个 HEAD 可达的。开了镜像加速时优先探镜像
     * （下载本来就会被改写走镜像，探镜像更快且更准）；都没命中返回 null。
     */
    private static String pickReachableUrl(Context context, String url) {
        String mirrored = MirrorHelper.rewriteUrl(context, url);
        if (!mirrored.equals(url)) {
            if (urlExists(mirrored)) {
                return mirrored;
            }
        }
        return urlExists(url) ? url : null;
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
