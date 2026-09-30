package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gh4a.fragment.SettingsFragment;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        return base;
    }

    /**
     * 开关打开且 host 在名单内时，把 url 改写为走镜像的地址；
     * 否则原样返回。
     */
    public static String rewriteUrl(Context context, String url) {
        if (url == null || !isEnabled(context)) {
            return url;
        }
        String base = getMirrorBase(context);
        if (base.isEmpty()) {
            return url;
        }
        String host = Uri.parse(url).getHost();
        if (host == null || !MIRRORABLE_HOSTS.contains(host.toLowerCase())) {
            return url;
        }
        if (url.startsWith(base)) {
            return url; // 已经是镜像地址
        }
        return base + "/" + url;
    }

    public interface TestCallback {
        void onResult(boolean ok, String message);
    }

    /** 连通性测试：经镜像拉一个小文件，10 秒超时。 */
    public static void testMirror(Context context, TestCallback callback) {
        String base = getMirrorBase(context);
        Handler handler = new Handler(Looper.getMainLooper());
        if (base.isEmpty()) {
            handler.post(() -> callback.onResult(false, null));
            return;
        }
        String probe = base
                + "/https://raw.githubusercontent.com/github/gitignore/main/README.md";
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        new Thread(() -> {
            boolean ok;
            String msg;
            try (Response r = client.newCall(
                    new Request.Builder().url(probe).build()).execute()) {
                ok = r.isSuccessful();
                msg = ok ? null : "HTTP " + r.code();
            } catch (IOException e) {
                Log.d(TAG, "Mirror test failed", e);
                ok = false;
                msg = e.getClass().getSimpleName();
            }
            final boolean fOk = ok;
            final String fMsg = msg;
            handler.post(() -> callback.onResult(fOk, fMsg));
        }).start();
    }
}
