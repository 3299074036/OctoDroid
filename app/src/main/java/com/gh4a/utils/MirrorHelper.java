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
    public static final String PREF_MIRROR_CUSTOM_URL = "mirror_custom_url";
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
        SharedPreferences p = prefs(context);
        String preset = p.getString(PREF_MIRROR_PRESET, DEFAULT_PRESET);
        if (DEAD_PRESET.equals(preset)) {
            preset = DEFAULT_PRESET;
            p.edit().putString(PREF_MIRROR_PRESET, preset).apply();
        }
        String base = PRESET_CUSTOM.equals(preset)
                ? p.getString(PREF_MIRROR_CUSTOM_URL, "")
                : preset;
        if (base == null) {
            return "";
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // 只接受 https 镜像：无 scheme 或 http 会在改写时产生畸形 URL (M-9)，
        // 且明文传输可被窃听篡改；不合法视为未配置，原样直连
        if (!base.regionMatches(true, 0, "https://", 0, 8)) {
            return "";
        }
        return base;
    }

    /**
     * OkHttp 拦截器：镜像加速开关打开时，把可走镜像的 URL 改写为镜像地址；
     * 否则原样放行。供图片、趋势等 OkHttp/Retrofit 客户端复用。
     */
    public static okhttp3.Interceptor mirrorInterceptor(Context context) {
        final Context appContext = context.getApplicationContext();
        return chain -> {
            okhttp3.Request request = chain.request();
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
        /** 选中时写入 mirror_preset 的值（预设即 url 本身，自定义地址为 "custom"）。 */
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
        void onResult(List<MirrorSpeedResult> results);
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

    /** 测速专用静态 client (L-6)：复用连接池，反复测速不再堆积线程/连接。 */
    private static volatile OkHttpClient sSpeedTestClient;

    private static OkHttpClient getSpeedTestClient() {
        if (sSpeedTestClient == null) {
            synchronized (MirrorHelper.class) {
                if (sSpeedTestClient == null) {
                    sSpeedTestClient = new OkHttpClient.Builder()
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .readTimeout(8, TimeUnit.SECONDS)
                            .build();
                }
            }
        }
        return sSpeedTestClient;
    }

    /**
     * 镜像测速：并行测试全部预设镜像及已填写的自定义地址的延迟，
     * 按从快到慢排序（不可用的排最后）。结果回调在主线程。
     *
     * @return 任务句柄，调用方销毁时应调 {@link SpeedTestHandle#cancel()}
     */
    public static SpeedTestHandle testAllMirrorsSpeed(Context context, SpeedTestCallback callback) {
        final SpeedTestHandle handle = new SpeedTestHandle(callback);
        final Handler handler = new Handler(Looper.getMainLooper());
        String[] names;
        String[] values;
        try {
            names = context.getResources().getStringArray(R.array.mirror_preset_items);
            values = context.getResources().getStringArray(R.array.mirror_preset_values);
        } catch (android.content.res.Resources.NotFoundException e) {
            Log.d(TAG, "Mirror preset arrays not found", e);
            postResult(handler, handle, new ArrayList<>());
            return handle;
        }
        final List<MirrorSpeedResult> targets = new ArrayList<>();
        int n = Math.min(names.length, values.length);
        for (int i = 0; i < n; i++) {
            if (PRESET_CUSTOM.equals(values[i])) {
                continue;
            }
            targets.add(new MirrorSpeedResult(names[i], values[i], -1));
        }
        // 已填写的自定义地址也一起测，选中时切回“自定义”
        String custom = prefs(context).getString(PREF_MIRROR_CUSTOM_URL, "");
        if (custom != null) {
            custom = custom.trim();
            while (custom.endsWith("/")) {
                custom = custom.substring(0, custom.length() - 1);
            }
            if (!custom.isEmpty()) {
                targets.add(new MirrorSpeedResult(
                        context.getString(R.string.mirror_speed_custom_name),
                        custom, PRESET_CUSTOM, -1));
            }
        }
        if (targets.isEmpty()) {
            postResult(handler, handle, new ArrayList<>());
            return handle;
        }
        final OkHttpClient client = getSpeedTestClient();
        new Thread(() -> {
            ExecutorService pool =
                    Executors.newFixedThreadPool(Math.min(targets.size(), 5));
            List<Future<MirrorSpeedResult>> futures = new ArrayList<>();
            for (MirrorSpeedResult t : targets) {
                futures.add(pool.submit(() -> probeMirror(client, t, handle)));
            }
            List<MirrorSpeedResult> results = new ArrayList<>();
            for (Future<MirrorSpeedResult> f : futures) {
                try {
                    results.add(f.get(30, TimeUnit.SECONDS));
                } catch (Exception e) {
                    Log.d(TAG, "Speed probe future failed", e);
                }
            }
            pool.shutdownNow();
            Collections.sort(results, (a, b) -> {
                if (a.isOk() != b.isOk()) {
                    return a.isOk() ? -1 : 1;
                }
                return Long.compare(a.latencyMs, b.latencyMs);
            });
            postResult(handler, handle, results);
        }).start();
        return handle;
    }

    private static void postResult(Handler handler, SpeedTestHandle handle,
            List<MirrorSpeedResult> results) {
        if (handle.isCancelled()) {
            return;
        }
        handler.post(() -> {
            SpeedTestCallback callback = handle.getCallback();
            // 投递后清空强引用，避免句柄长期持有调用方
            handle.clearCallback();
            if (callback != null && !handle.isCancelled()) {
                callback.onResult(results);
            }
        });
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
}
