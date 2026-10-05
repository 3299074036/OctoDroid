package com.gh4a.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.gh4a.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
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
            // 从 release assets 里拿与当前变体匹配的包：
            // debug 版拿 debug 签名包，release 版拿 release 签名包，
            // 签名一致才能覆盖安装，否则会“应用未安装”。
            // assets 为空（发版漏传）时不再走正文扫描兜底——我们的 release
            // 正文里本来也不放 apk 链接，兜底救不到，还多一个被恶意
            // release body 卡死的攻击面（M-NEW-2）；此时 apkUrl 为 null，
            // UI 层会提示“未找到安装包”。
            apkUrl = resolveApkUrlFromAssets(json.optJSONArray("assets"));
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
     * 拉取 latest release 信息：直连、已选镜像、默认镜像三条链路并行竞速，
     * 谁先返回合法 JSON（tag_name 非空）谁赢，其余取消。
     *
     * VPN 下直连最快；只开镜像时镜像快；自选镜像挂了/不支持 API 代理时
     * 默认镜像兜底（gh-proxy 在国内可直达，与下载器的备选默认镜像策略一致）。
     * 任何一条通就能检出更新，不会被一条慢/死的链路拖住
     * （旧的串行逻辑在这种情况下要白等 30s+ 才报错）。
     */
    private static JSONObject fetchLatestRelease(Context context) throws IOException {
        // R-5：三级链路候选收敛到 MirrorHelper.buildFallbackChain；
        // 更新检查走 blindPrefix=true（见该方法注释：api.github.com 必须盲拼镜像前缀）。
        // 并行竞速与顺序无关，所以 [自选, 默认, 直连] 的顺序不影响结果。
        List<String> candidates =
                MirrorHelper.buildFallbackChain(context, LATEST_RELEASE_URL, true);

        List<Call> calls = new ArrayList<>();
        for (String url : candidates) {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "OctoDroid")
                    .build();
            calls.add(getClient().newCall(request));
        }
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CompletionService<JSONObject> ecs = new ExecutorCompletionService<>(pool);
        for (Call call : calls) {
            ecs.submit(() -> fetchReleaseJson(call));
        }
        IOException lastError = new IOException("update check failed");
        long deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(30);
        try {
            for (int i = 0; i < calls.size(); i++) {
                long waitMs = deadline - SystemClock.elapsedRealtime();
                if (waitMs <= 0) {
                    break;
                }
                Future<JSONObject> f = ecs.poll(waitMs, TimeUnit.MILLISECONDS);
                if (f == null) {
                    break;
                }
                try {
                    JSONObject json = f.get();
                    for (Call c : calls) {
                        c.cancel();
                    }
                    return json;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    lastError = cause instanceof IOException
                            ? (IOException) cause
                            : new IOException(String.valueOf(cause));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = new IOException("interrupted", e);
        } finally {
            pool.shutdownNow();
        }
        throw lastError;
    }

    /** 单条链路拉取并校验 release JSON：HTTP 2xx 且 tag_name 非空才算成功。 */
    private static JSONObject fetchReleaseJson(Call call) throws IOException {
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            final JSONObject json;
            try {
                json = new JSONObject(response.body().string());
            } catch (JSONException e) {
                throw new IOException("bad response");
            }
            if (json.optString("tag_name", "").isEmpty()) {
                throw new IOException("empty release tag");
            }
            return json;
        }
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

    // Auto-check policy: check on every startup (user requirement),
    // no once-per-day throttling.

    /** Always true: auto check runs on every app startup when enabled. */
    public static boolean shouldAutoCheck(Context context) {
        return true;
    }
}
