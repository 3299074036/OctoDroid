package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 国内网络加速：把直连慢的 GitHub 资源 host（raw 文件、头像、release 附件等）
 * 改写为 gh-proxy 风格镜像地址：{@code <mirror>/<original-url>}。
 *
 * api.github.com 不走镜像（需要鉴权），只改写无鉴权的静态资源。
 */
public class MirrorHelper {
    private static final String TAG = "MirrorHelper";

    public static final String PREF_MIRROR_ENABLED = "mirror_enabled";
    public static final String PREF_MIRROR_PRESET = "mirror_preset";
    /** 旧版单条自定义地址（已迁移到镜像列表，仅迁移代码使用）。 */
    static final String PREF_MIRROR_CUSTOM_URL = "mirror_custom_url";
    /** 全部镜像地址：JSON 数组，首次从内置预设播种，用户可增删改。 */
    public static final String PREF_MIRROR_LIST = "mirror_list";
    public static final String PRESET_CUSTOM = "custom";
    public static final String DEFAULT_PRESET = "https://gh-proxy.com";
    /** 已下线镜像（2026-10-01 实测不可用），老用户存量选择自动迁移到默认。 */
    private static final String DEAD_PRESET = "https://mirror.ghproxy.com";

    /** 国内直连慢、且无鉴权可安全走代理的 host。 */
    private static final Set<String> MIRRORABLE_HOSTS = new HashSet<>(Arrays.asList(
            "raw.githubusercontent.com",
            "avatars.githubusercontent.com",
            "user-images.githubusercontent.com",
            "objects.githubusercontent.com",
            "codeload.github.com"
    ));

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(PREF_MIRROR_ENABLED, false);
    }

    /** 镜像基地址（去掉末尾斜杠），如 https://gh-proxy.com；未配置返回空串。 */
    public static String getMirrorBase(Context context) {
        migrateLegacyCustomUrl(context);
        SharedPreferences p = prefs(context);
        String preset = p.getString(PREF_MIRROR_PRESET, DEFAULT_PRESET);
        if (DEAD_PRESET.equals(preset) || PRESET_CUSTOM.equals(preset)) {
            preset = DEFAULT_PRESET;
            p.edit().putString(PREF_MIRROR_PRESET, preset).apply();
        }
        String base = normalizeUrl(preset);
        // 只接受 https 镜像：无 scheme 或 http 会在改写时产生畸形 URL (M-9)，
        // 且明文传输可被窃听篡改；不合法视为未配置，原样直连
        if (!base.regionMatches(true, 0, "https://", 0, 8)) {
            return "";
        }
        return base;
    }

    /** 去掉首尾空白和末尾斜杠；null 返回空串。 */
    public static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** 列表里显示的名字：取 URL 的 host，解析失败就显示原串。 */
    public static String displayHost(String url) {
        try {
            String host = Uri.parse(url).getHost();
            if (host != null && !host.isEmpty()) {
                return host;
            }
        } catch (Exception ignored) {
            // 解析失败就原样显示
        }
        return url == null ? "" : url;
    }

    /**
     * 旧版单条自定义地址迁移到镜像列表（一次性），之后删除旧键。
     * 注意：不能调 getMirrorUrls()（它会反过来调迁移），直接读原始 JSON。
     */
    private static void migrateLegacyCustomUrl(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.contains(PREF_MIRROR_CUSTOM_URL)) {
            return;
        }
        String norm = normalizeUrl(p.getString(PREF_MIRROR_CUSTOM_URL, ""));
        SharedPreferences.Editor e = p.edit().remove(PREF_MIRROR_CUSTOM_URL);
        if (!norm.isEmpty()) {
            List<String> list = readMirrorList(p);
            if (!containsIgnoreCase(list, norm)) {
                list.add(norm);
                e.putString(PREF_MIRROR_LIST, toJsonArray(list));
            }
            if (PRESET_CUSTOM.equals(p.getString(PREF_MIRROR_PRESET, ""))) {
                e.putString(PREF_MIRROR_PRESET, norm);
            }
        } else if (PRESET_CUSTOM.equals(p.getString(PREF_MIRROR_PRESET, ""))) {
            e.putString(PREF_MIRROR_PRESET, DEFAULT_PRESET);
        }
        e.apply();
    }

    private static boolean containsIgnoreCase(List<String> list, String url) {
        for (String s : list) {
            if (s.equalsIgnoreCase(url)) {
                return true;
            }
        }
        return false;
    }

    private static boolean removeIgnoreCase(List<String> list, String url) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(url)) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    private static String toJsonArray(List<String> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String s : list) {
            arr.put(s);
        }
        return arr.toString();
    }

    /** 从 prefs 读原始镜像列表 JSON，不做迁移不播种（供迁移逻辑内部使用）。 */
    private static List<String> readMirrorList(SharedPreferences p) {
        List<String> out = new ArrayList<>();
        String json = p.getString(PREF_MIRROR_LIST, null);
        if (json == null) {
            return out;
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String u = normalizeUrl(arr.optString(i, ""));
                if (!u.isEmpty() && !containsIgnoreCase(out, u)) {
                    out.add(u);
                }
            }
        } catch (org.json.JSONException ignored) {
            // 坏数据就当空列表
        }
        return out;
    }

    /**
     * 全部镜像地址（已去重、格式归一）。从未保存过则用内置预设播种一次；
     * 之后就是用户自己的列表，可增删改。
     */
    public static List<String> getMirrorUrls(Context context) {
        migrateLegacyCustomUrl(context);
        SharedPreferences p = prefs(context);
        if (p.getString(PREF_MIRROR_LIST, null) == null) {
            List<String> seed = new ArrayList<>();
            for (String v : presetValues(context)) {
                if (PRESET_CUSTOM.equals(v)) {
                    continue;
                }
                String norm = normalizeUrl(v);
                if (!norm.isEmpty() && !containsIgnoreCase(seed, norm)) {
                    seed.add(norm);
                }
            }
            p.edit().putString(PREF_MIRROR_LIST, toJsonArray(seed)).apply();
            return seed;
        }
        return readMirrorList(p);
    }

    private static void saveMirrorUrls(Context context, List<String> urls) {
        prefs(context).edit().putString(PREF_MIRROR_LIST, toJsonArray(urls)).apply();
    }

    /** 新增镜像：去重（忽略大小写）。 */
    public static void addMirrorUrl(Context context, String url) {
        String norm = normalizeUrl(url);
        if (norm.isEmpty()) {
            return;
        }
        List<String> list = getMirrorUrls(context);
        if (!containsIgnoreCase(list, norm)) {
            list.add(norm);
            saveMirrorUrls(context, list);
        }
    }

    /** 修改镜像：原位替换旧地址；新地址若已存在则只删旧的，避免重复。 */
    public static void updateMirrorUrl(Context context, String oldUrl, String newUrl) {
        String normNew = normalizeUrl(newUrl);
        if (normNew.isEmpty()) {
            return;
        }
        List<String> list = getMirrorUrls(context);
        removeIgnoreCase(list, normNew);
        boolean replaced = false;
        String normOld = normalizeUrl(oldUrl);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(normOld)) {
                list.set(i, normNew);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.add(normNew);
        }
        saveMirrorUrls(context, list);
    }

    /** 删除镜像。 */
    public static void removeMirrorUrl(Context context, String url) {
        List<String> list = getMirrorUrls(context);
        if (removeIgnoreCase(list, normalizeUrl(url))) {
            saveMirrorUrls(context, list);
        }
    }

    private static String[] presetValues(Context context) {
        try {
            return context.getResources().getStringArray(R.array.mirror_preset_values);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.d(TAG, "Mirror preset arrays not found", e);
            return new String[0];
        }
    }

    /**
     * OkHttp 拦截器：镜像加速开关打开时，把可走镜像的 URL 改写为镜像地址；
     * 否则原样放行。供图片、趋势等 OkHttp/Retrofit 客户端复用。
     */
    public static okhttp3.Interceptor mirrorInterceptor(Context context) {
        final Context appContext = context.getApplicationContext();
        return chain -> {
            okhttp3.Request request = chain.request();
            // 携带鉴权头的请求（私有仓库图片等）直接跳过镜像改写：
            // 镜像站拿不到有效 token，改写后必 404；且 token 绝不外发 (N-1)
            if (request.header("Authorization") != null) {
                return chain.proceed(request);
            }
            String original = request.url().toString();
            String rewritten = rewriteUrl(appContext, original);
            if (!rewritten.equals(original)) {
                // 改写目标是第三方镜像站：剥离 Authorization 等鉴权头，
                // 镜像站本就无法使用该 token，纵深防御 (H-5)
                request = request.newBuilder()
                        .removeHeader("Authorization")
                        .url(rewritten)
                        .build();
            }
            return chain.proceed(request);
        };
    }

    /**
     * 开关打开且 host 在名单内时，把 url 改写为走镜像的地址；
     * 否则原样返回。
     * <p>
     * 另外处理 GitHub release 附件下载地址（{@code github.com/.../releases/download/...}）：
     * 应用内更新从 release assets 拿到的 browser_download_url 就是这种形式，
     * 只改写该路径，其他 github.com 地址（API、网页）一律不动。
     */
    public static String rewriteUrl(Context context, String url) {
        if (url == null || !isEnabled(context)) {
            return url;
        }
        String base = getMirrorBase(context);
        if (base.isEmpty()) {
            return url;
        }
        return rewriteUrlWithBase(base, url);
    }

    /**
     * 用指定镜像基地址改写 url（不看开关）。供下载失败换线路重试时使用：
     * 未开镜像加速时也可用默认镜像做一次兜底。
     */
    public static String rewriteUrlWithBase(String base, String url) {
        if (url == null || base == null || base.isEmpty()) {
            return url;
        }
        if (url.regionMatches(true, 0, base, 0, base.length())) {
            return url; // 已经是镜像地址（大小写不敏感比较）(L-7)
        }
        String host = Uri.parse(url).getHost();
        if (host == null) {
            return url;
        }
        host = host.toLowerCase(Locale.ROOT);
        if ("github.com".equals(host)) {
            String path = Uri.parse(url).getPath();
            if (path != null && path.contains("/releases/download/")) {
                return base + "/" + url;
            }
            return url;
        }
        if (!MIRRORABLE_HOSTS.contains(host)) {
            return url;
        }
        return base + "/" + url;
    }

    /** 单个镜像的测速结果；latencyMs &lt; 0 表示不可用。 */
    public static class MirrorSpeedResult {
        public final String name;
        /** 探测用的镜像基地址。 */
        public final String url;
        /** 选中时写入 mirror_preset 的值（即镜像基地址本身）。 */
        public final String presetValue;
        public final long latencyMs;

        public MirrorSpeedResult(String name, String url, long latencyMs) {
            this(name, url, url, latencyMs);
        }

        public MirrorSpeedResult(String name, String url, String presetValue, long latencyMs) {
            this.name = name;
            this.url = url;
            this.presetValue = presetValue;
            this.latencyMs = latencyMs;
        }

        public boolean isOk() {
            return latencyMs >= 0;
        }
    }

    public interface SpeedTestCallback {
        /** 单个镜像探测完成时回调（主线程）。 */
        void onProbeComplete(MirrorSpeedResult result);
        /** 全部探测完成时回调（主线程）。 */
        void onAllComplete();
    }

    /**
     * 测速任务句柄 (M-10)：调用方（如 Fragment）在销毁时调 {@link #cancel()}，
     * 中止未完成的探测并阻止回调，避免回调强引用已销毁的调用方。
     * <p>
     * 任务执行期间句柄强持有 callback（匿名 lambda 若只被弱引用，
     * 方法返回后可能被 GC 导致正常测速也收不到结果）；cancel() 或结果投递后清空。
     */
    public static class SpeedTestHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());
        private volatile SpeedTestCallback callback;

        SpeedTestHandle(SpeedTestCallback callback) {
            this.callback = callback;
        }

        /** 取消测速：中止进行中的请求，之后不再投递回调。 */
        public void cancel() {
            cancelled.set(true);
            callback = null;
            synchronized (calls) {
                for (Call call : calls) {
                    call.cancel();
                }
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        SpeedTestCallback getCallback() {
            return callback;
        }

        void clearCallback() {
            callback = null;
        }

        void track(Call call) {
            calls.add(call);
        }
    }

    /** 测速专用静态 client：复用连接池，反复测速不再堆积线程/连接；超时收紧到 5s，测速要快。 */
    private static volatile OkHttpClient sSpeedTestClient;

    private static OkHttpClient getSpeedTestClient() {
        if (sSpeedTestClient == null) {
            synchronized (MirrorHelper.class) {
                if (sSpeedTestClient == null) {
                    sSpeedTestClient = new OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sSpeedTestClient;
    }

    /**
     * 待测目标列表（latencyMs &lt; 0 表示未测），供 UI 先占位显示、逐个更新。
     * 就是用户当前的全部镜像，没有预设和自建之分。
     */
    public static List<MirrorSpeedResult> getSpeedTestTargets(Context context) {
        final List<MirrorSpeedResult> targets = new ArrayList<>();
        for (String u : getMirrorUrls(context)) {
            targets.add(new MirrorSpeedResult(displayHost(u), u, -1));
        }
        return targets;
    }

    /**
     * 镜像测速：全部并行探测（不再分 5 线程两批），每完成一个就通过
     * {@link SpeedTestCallback#onProbeComplete} 实时回调，主线程可逐行更新；
     * 全部完成后调 {@link SpeedTestCallback#onAllComplete}。
     *
     * @return 任务句柄，调用方销毁时应调 {@link SpeedTestHandle#cancel()}
     */
    public static SpeedTestHandle testAllMirrorsSpeed(Context context, SpeedTestCallback callback) {
        return testAllMirrorsSpeed(context, getSpeedTestTargets(context), callback);
    }

    public static SpeedTestHandle testAllMirrorsSpeed(Context context,
            List<MirrorSpeedResult> targets, SpeedTestCallback callback) {
        final SpeedTestHandle handle = new SpeedTestHandle(callback);
        final Handler handler = new Handler(Looper.getMainLooper());
        if (targets.isEmpty()) {
            handler.post(() -> {
                SpeedTestCallback cb = handle.getCallback();
                handle.clearCallback();
                if (cb != null && !handle.isCancelled()) {
                    cb.onAllComplete();
                }
            });
            return handle;
        }
        final OkHttpClient client = getSpeedTestClient();
        new Thread(() -> {
            ExecutorService pool = Executors.newFixedThreadPool(targets.size());
            CompletionService<MirrorSpeedResult> cs = new ExecutorCompletionService<>(pool);
            for (MirrorSpeedResult t : targets) {
                cs.submit(() -> probeMirror(client, t, handle));
            }
            int remaining = targets.size();
            while (remaining > 0 && !handle.isCancelled()) {
                try {
                    // 12s 兜底：远大于单次探测的 5s+5s 超时，正常不会触发
                    Future<MirrorSpeedResult> f = cs.poll(12, TimeUnit.SECONDS);
                    if (f == null) {
                        break;
                    }
                    final MirrorSpeedResult r = f.get();
                    remaining--;
                    handler.post(() -> {
                        SpeedTestCallback cb = handle.getCallback();
                        if (cb != null && !handle.isCancelled()) {
                            cb.onProbeComplete(r);
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG, "Speed probe future failed", e);
                    remaining--;
                }
            }
            pool.shutdownNow();
            handler.post(() -> {
                SpeedTestCallback cb = handle.getCallback();
                // 投递后清空强引用，避免句柄长期持有调用方
                handle.clearCallback();
                if (cb != null && !handle.isCancelled()) {
                    cb.onAllComplete();
                }
            });
        }).start();
        return handle;
    }

    /** 测单个镜像：Range 取小文件前 1KB，返回耗时；失败返回 latencyMs = -1。 */
    private static MirrorSpeedResult probeMirror(OkHttpClient client, MirrorSpeedResult target,
            SpeedTestHandle handle) {
        String probe = target.url
                + "/https://raw.githubusercontent.com/github/gitignore/main/README.md";
        long start = SystemClock.elapsedRealtime();
        Call call = null;
        try {
            call = client.newCall(new Request.Builder()
                    .url(probe)
                    .header("Range", "bytes=0-1023")
                    .build());
            handle.track(call);
            try (Response r = call.execute()) {
                if (r.isSuccessful() && r.body() != null) {
                    r.body().bytes(); // 读完 1KB，耗时才真实
                    return new MirrorSpeedResult(target.name, target.url, target.presetValue,
                            SystemClock.elapsedRealtime() - start);
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Speed probe failed: " + target.url, e);
        } catch (RuntimeException e) {
            // 畸形地址（如无 scheme 的自定义地址）抛 IllegalArgumentException：
            // 返回不可用条目而不是静默丢弃，让用户在列表里看得到 (L-8)
            Log.d(TAG, "Speed probe bad URL: " + target.url, e);
        }
        return new MirrorSpeedResult(target.name, target.url, target.presetValue, -1);
    }

    private static final String KEY_SPEED_CACHE = "mirror_speed_cache";

    /**
     * 保存测速结果缓存（url=latencyMs，-1 表示不可用），下次打开镜像源对话框直接显示，
     * 不用干等。空 url 的占位行不存。
     */
    public static void saveSpeedTestResults(Context context, List<MirrorSpeedResult> results) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (MirrorSpeedResult r : results) {
            if (!r.url.isEmpty()) {
                set.add(r.url + "=" + r.latencyMs);
            }
        }
        prefs(context).edit().putStringSet(KEY_SPEED_CACHE, set).apply();
    }

    /**
     * 读取测速结果缓存：url -&gt; latencyMs（-1=不可用）。没有缓存返回空 map。
     */
    public static java.util.Map<String, Long> getCachedSpeedResults(Context context) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        java.util.Set<String> set = prefs(context).getStringSet(KEY_SPEED_CACHE, null);
        if (set == null) {
            return map;
        }
        for (String s : set) {
            int eq = s.lastIndexOf('=');
            if (eq > 0) {
                try {
                    map.put(s.substring(0, eq), Long.parseLong(s.substring(eq + 1)));
                } catch (NumberFormatException ignored) {
                    // 坏条目跳过
                }
            }
        }
        return map;
    }
}
