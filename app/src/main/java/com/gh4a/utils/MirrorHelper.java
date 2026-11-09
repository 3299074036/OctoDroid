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
import java.net.InetAddress;
import java.net.UnknownHostException;
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

    /** 镜像测速线程池上限（L-NEW-3）：mirror_list 可无限添加，无上限会 OOM。 */
    private static final int MAX_SPEED_TEST_THREADS = 32;
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
        // 且明文传输可被窃听篡改；不合法视为未配置，原样直连。
        // 0.2.2（S-M3）：同时拒绝回环／内网 host。
        if (!isValidMirrorUrl(base)) {
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

    /**
     * 0.2.2（S-M3）：镜像地址集中校验——必须是 https://，host 不能缺失、
     * 不能是回环／内网 IP 或 localhost。供 add/update/getMirrorUrls/
     * nextMirrorBase/buildFallbackChain/getMirrorBase 共用；UI 保存路径
     * 原有校验不变，这里是纵深防御（防旧存量／外部写 prefs 的非法地址
     * 进入故障转移、测速与拦截器）。
     */
    public static boolean isValidMirrorUrl(String url) {
        if (url == null) {
            return false;
        }
        String norm = normalizeUrl(url);
        if (!norm.regionMatches(true, 0, "https://", 0, 8)) {
            return false;
        }
        String host = extractHost(norm);
        if (host == null || host.isEmpty()) {
            return false;
        }
        // 89：DNS 尾点形（localhost. / 127.0.0.1.）与无尾点等价；不去掉时
        // localhost. 命中不了拦截、127.0.0.1. 按 "." 切出 5 段被当域名放行。
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            return false;
        }
        return !isLoopbackOrPrivateHost(host);
    }

    /**
     * 56：从 URL 里取 host。不能用 Uri.getHost()——旧版本
     * （minSdk 23 的 API 23）不认 IPv6 方括号，"https://[::1]/x" 的
     * host 取出来是 "["，校验路径上后续 getByName 抛异常被吞、回环
     * 地址被放行（真机老设备同样中招，不只是测试问题）。
     * 这里按 authority 手工取：方括号内是 IPv6 字面量（去括号），
     * 否则截到 ':'（端口）为止；含 userinfo 时取最后一个 '@' 之后。
     * 88：authority 到第一个 '/'、'?' 或 '#' 为止——'?'/'#' 之后是
     * query/fragment，不是 host 的一部分；不截断时
     * "https://127.0.0.1#@evil.com/" 会取到 evil.com（放行），而实际
     * 请求按 RFC 3986 发往回环地址 127.0.0.1。
     */
    private static String extractHost(String url) {
        if (url == null) {
            return null;
        }
        String rest = url;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            rest = rest.substring(scheme + 3);
        }
        int end = rest.length();
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            end = slash;
        }
        int query = rest.indexOf('?');
        if (query >= 0 && query < end) {
            end = query;
        }
        int fragment = rest.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        String authority = rest.substring(0, end);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) {
                return null; // 未闭合的 IPv6 字面量，非法
            }
            return authority.substring(1, close);
        }
        int colon = authority.indexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    /** host 是回环／内网地址时返回 true（IPv4、IPv6 与 localhost；域名一律放行）。 */
    private static boolean isLoopbackOrPrivateHost(String host) {
        if (host.equalsIgnoreCase("localhost")) {
            return true;
        }
        if (host.contains(":")) {
            // IPv6 字面量：InetAddress.getByName 对含 ':' 的串只做字面解析
            // 不发 DNS；归一化后判回环/链路本地/站点本地，并处理内嵌 IPv4
            //（::ffff:a.b.c.d 映射形与 ::a.b.c.d 兼容形，含展开写法）。
            try {
                InetAddress addr = InetAddress.getByName(host);
                if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || isUniqueLocalIPv6(addr.getAddress())) {
                    return true;
                }
                Long embedded = embeddedIPv4(addr.getAddress());
                return embedded != null && isPrivateIPv4Value(embedded);
            } catch (UnknownHostException e) {
                return false; // 非法 IPv6 字面量，连不上，交由后续连接失败
            }
        }
        // IPv4：按 inet_aton 规则手写解析（1-4 段，十进制/0x十六进制/0八进制），
        // 不调 InetAddress.getByName——域名一旦解析失败会回落 DNS，
        // 而 isValidMirrorUrl 可能跑在主线程（UI 保存路径），不能阻塞查 DNS。
        // 56：旧代码只认点分十进制标准形，2130706433（=127.0.0.1）、
        // 0177.0.0.1（八进制）、0x7f.0.0.1（十六进制）全部放行。
        Long addr = parseNumericIPv4(host);
        if (addr == null) {
            return false; // 不是数字 IPv4，是域名
        }
        return isPrivateIPv4Value(addr);
    }

    /** 32 位 IPv4 值是否在回环/内网段：0/8、127/8、10/8、172.16/12、192.168/16、169.254/16。 */
    private static boolean isPrivateIPv4Value(long addr) {
        int b0 = (int) ((addr >>> 24) & 0xFF);
        int b1 = (int) ((addr >>> 16) & 0xFF);
        return b0 == 0 || b0 == 127 || b0 == 10
                || (b0 == 172 && b1 >= 16 && b1 <= 31)
                || (b0 == 192 && b1 == 168)
                || (b0 == 169 && b1 == 254);
    }

    /**
     * 按 inet_aton 规则解析 IPv4 字面量（1-4 段，每段十进制/0x十六进制/0八进制），
     * 返回 32 位值；不是数字 IPv4（域名或非法形）返回 null。
     */
    private static Long parseNumericIPv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length < 1 || parts.length > 4) {
            return null;
        }
        long[] vals = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) {
                return null;
            }
            try {
                long v;
                if (p.length() > 2 && (p.startsWith("0x") || p.startsWith("0X"))) {
                    v = Long.parseLong(p.substring(2), 16);
                } else if (p.length() > 1 && p.charAt(0) == '0') {
                    v = Long.parseLong(p.substring(1), 8);
                } else {
                    if (!p.matches("[0-9]+")) {
                        return null;
                    }
                    v = Long.parseLong(p, 10);
                }
                vals[i] = v;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // inet_aton 语义：a / a.b / a.b.c / a.b.c.d（后段占满剩余位）。
        switch (parts.length) {
            case 1:
                if (vals[0] > 0xFFFFFFFFL) {
                    return null;
                }
                return vals[0];
            case 2:
                if (vals[0] > 255 || vals[1] > 0xFFFFFFL) {
                    return null;
                }
                return (vals[0] << 24) | vals[1];
            case 3:
                if (vals[0] > 255 || vals[1] > 255 || vals[2] > 0xFFFFL) {
                    return null;
                }
                return (vals[0] << 24) | (vals[1] << 16) | vals[2];
            default:
                for (long v : vals) {
                    if (v > 255) {
                        return null;
                    }
                }
                return (vals[0] << 24) | (vals[1] << 16) | (vals[2] << 8) | vals[3];
        }
    }

    /**
     * IPv6 地址内嵌的 IPv4（::ffff:a.b.c.d 映射形与 ::a.b.c.d 兼容形，
     * 前 80 位为 0，第 81-96 位为 1 或 0）；其余返回 null。
     */
    /** fc00::/7 唯一本地地址（ULA）：Java 的 isSiteLocalAddress 只认已废弃的
     * fec0::/10，不含 ULA，须显式判（首字节 0xfc/0xfd）。 */
    private static boolean isUniqueLocalIPv6(byte[] b) {
        return b.length == 16 && (b[0] & 0xFE) == 0xFC;
    }

    private static Long embeddedIPv4(byte[] b) {
        if (b.length != 16) {
            return null;
        }
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return null;
            }
        }
        boolean mapped = b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
        boolean compatible = b[10] == 0 && b[11] == 0;
        if (!mapped && !compatible) {
            return null;
        }
        return ((b[12] & 0xFFL) << 24) | ((b[13] & 0xFFL) << 16)
                | ((b[14] & 0xFFL) << 8) | (b[15] & 0xFFL);
    }

    /** 列表里显示的名字：取 URL 的 host，解析失败就显示原串。 */
    public static String displayHost(String url) {
        // 56：与校验路径同款 extractHost——Uri.getHost() 在老版本上
        // 把 IPv6 字面量取成 "["，显示会错。
        String host = extractHost(url);
        if (host != null && !host.isEmpty()) {
            return host;
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
                if (!norm.isEmpty() && isValidMirrorUrl(norm)
                        && !containsIgnoreCase(seed, norm)) {
                    seed.add(norm);
                }
            }
            p.edit().putString(PREF_MIRROR_LIST, toJsonArray(seed)).apply();
            return seed;
        }
        List<String> list = readMirrorList(p);
        // 0.2.2（S-M3）：过滤掉非法存量条目（非 https／回环／内网）；
        // 只过滤不回写，下次保存时自然清理。
        list.removeIf(u -> !isValidMirrorUrl(u));
        return list;
    }

    private static void saveMirrorUrls(Context context, List<String> urls) {
        prefs(context).edit().putString(PREF_MIRROR_LIST, toJsonArray(urls)).apply();
    }

    /** 新增镜像：去重（忽略大小写）。非法地址（非 https／回环／内网）拒绝写入。 */
    public static void addMirrorUrl(Context context, String url) {
        String norm = normalizeUrl(url);
        if (!isValidMirrorUrl(norm)) {
            return;
        }
        List<String> list = getMirrorUrls(context);
        if (!containsIgnoreCase(list, norm)) {
            list.add(norm);
            saveMirrorUrls(context, list);
        }
    }

    /** 修改镜像：原位替换旧地址；新地址若已存在则只删旧的，避免重复。非法地址拒绝写入。 */
    public static void updateMirrorUrl(Context context, String oldUrl, String newUrl) {
        String normNew = normalizeUrl(newUrl);
        if (!isValidMirrorUrl(normNew)) {
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
        // 0.2.2：同时清掉它的测速缓存，否则故障转移还会选它。
        removeSpeedTestResult(context, url);
    }

    /**
     * 0.2.2：清除单个镜像的测速缓存条目（删镜像时调用）。
     * 缓存以 "url=latencyMs" 存 StringSet，按归一化后的地址匹配。
     */
    public static void removeSpeedTestResult(Context context, String url) {
        String norm = normalizeUrl(url);
        if (norm.isEmpty()) {
            return;
        }
        java.util.Set<String> set = prefs(context).getStringSet(KEY_SPEED_CACHE, null);
        if (set == null || set.isEmpty()) {
            return;
        }
        java.util.Set<String> kept = new java.util.HashSet<>();
        boolean removed = false;
        for (String s : set) {
            int eq = s.lastIndexOf('=');
            String key = eq > 0 ? s.substring(0, eq) : s;
            if (normalizeUrl(key).equalsIgnoreCase(norm)) {
                removed = true;
            } else {
                kept.add(s);
            }
        }
        if (removed) {
            prefs(context).edit().putStringSet(KEY_SPEED_CACHE, kept).apply();
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
     *
     * <p>功能1（镜像线路自动故障转移）：只对幂等请求（GET/HEAD）——
     * 镜像连接失败/超时（IOException）或返回 5xx 时，按测速缓存的延迟排序
     * 自动换下一条镜像重试一次；没有可换的镜像时原样返回失败。
     * 携带鉴权头的请求不改写也不转移（见下）。
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
            if (rewritten.equals(original)) {
                return chain.proceed(request);
            }
            request = request.newBuilder()
                    .removeHeader("Authorization")
                    .url(rewritten)
                    .build();
            boolean idempotent = isIdempotent(request.method());
            try {
                okhttp3.Response response = chain.proceed(request);
                if (response.code() < 500 || !idempotent) {
                    return response;
                }
                String failover = nextMirrorBase(appContext, getMirrorBase(appContext));
                if (failover == null) {
                    return response;
                }
                response.close();
                return chain.proceed(request.newBuilder()
                        .url(failover + "/" + original).build());
            } catch (IOException e) {
                if (!idempotent) {
                    throw e;
                }
                String failover = nextMirrorBase(appContext, getMirrorBase(appContext));
                if (failover == null) {
                    throw e;
                }
                return chain.proceed(request.newBuilder()
                        .url(failover + "/" + original).build());
            }
        };
    }

    private static boolean isIdempotent(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    /**
     * 故障转移用的下一条镜像基地址：有测速缓存的按延迟从低到高，
     * 无缓存的按用户列表顺序，最后是默认镜像；跳过当前正在用的基地址。
     * 都没有返回 null，调用方原样返回失败。
     */
    private static String nextMirrorBase(Context appContext, String currentBase) {
        String normCurrent = normalizeUrl(currentBase);
        List<String> candidates = new ArrayList<>();
        // 0.2.2：测速缓存从不清理，删掉的镜像仍会作为故障转移候选；
        // 只保留仍在镜像列表里的缓存条目。
        List<String> currentUrls = getMirrorUrls(appContext);
        List<java.util.Map.Entry<String, Long>> sorted =
                new ArrayList<>(getCachedSpeedResults(appContext).entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue(), b.getValue()));
        for (java.util.Map.Entry<String, Long> e : sorted) {
            if (e.getValue() != null && e.getValue() >= 0) {
                String norm = normalizeUrl(e.getKey());
                if (!norm.isEmpty() && !norm.equalsIgnoreCase(normCurrent)
                        && !containsIgnoreCase(candidates, norm)
                        && containsIgnoreCase(currentUrls, norm)
                        && isValidMirrorUrl(norm)) {
                    candidates.add(norm);
                }
            }
        }
        for (String u : currentUrls) {
            String norm = normalizeUrl(u);
            if (!norm.isEmpty() && !norm.equalsIgnoreCase(normCurrent)
                    && !containsIgnoreCase(candidates, norm)) {
                candidates.add(norm);
            }
        }
        if (!containsIgnoreCase(candidates, DEFAULT_PRESET)
                && !DEFAULT_PRESET.equalsIgnoreCase(normCurrent)) {
            candidates.add(DEFAULT_PRESET);
        }
        return candidates.isEmpty() ? null : candidates.get(0);
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

    /**
     * 镜像三级链路候选（R-5 收敛）：自选镜像 → 默认镜像 → 直连原地址，去重。
     * 更新检查（并行竞速）与 APK 下载（顺序重试）共用同一策略；加第四条链路或
     * 换默认镜像时只改这里。
     *
     * @param blindPrefix false=下载语义：用 {@link #rewriteUrlWithBase} 智能改写
     *                    （只改写可镜像 host，已是镜像地址时原样返回）；
     *                    true=更新检查语义：镜像基直接拼前缀。api.github.com
     *                    不在 MIRRORABLE_HOSTS 里，智能改写不会给它加前缀，
     *                    而 gh-proxy 实测可代理该 API，所以检查链路必须盲拼。
     */
    public static List<String> buildFallbackChain(Context context, String url,
            boolean blindPrefix) {
        // 0.2.2（S-M3）：链路基地址只来自 getMirrorBase()（已集中校验 https＋
        // 非回环／内网）与常量 DEFAULT_PRESET（https://gh-proxy.com），
        // 非法基地址到不了这里。
        List<String> chain = new ArrayList<>();
        if (blindPrefix) {
            if (isEnabled(context)) {
                String base = getMirrorBase(context);
                if (!base.isEmpty()) {
                    addDistinctUrl(chain, base + "/" + url);
                }
            }
            addDistinctUrl(chain, DEFAULT_PRESET + "/" + url);
        } else {
            // 下载语义：rewriteUrl 未开镜像时原样返回 url，保证未开镜像时
            // 链路为 [直连, 默认镜像]（直连优先），与原 buildDownloadChain 一致
            addDistinctUrl(chain, rewriteUrl(context, url));
            addDistinctUrl(chain, rewriteUrlWithBase(DEFAULT_PRESET, url));
        }
        addDistinctUrl(chain, url);
        return chain;
    }

    /** 链路去重（大小写不敏感），null/空直接跳过。 */
    private static void addDistinctUrl(List<String> chain, String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        for (String e : chain) {
            if (e.equalsIgnoreCase(url)) {
                return;
            }
        }
        chain.add(url);
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
        Background.run("mirror-speed-test", () -> {
            // L-NEW-3：线程池上限 32——mirror_list 可无限添加，无上限会 OOM
            ExecutorService pool =
                    Executors.newFixedThreadPool(Math.min(targets.size(), MAX_SPEED_TEST_THREADS));
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
        });
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
                    // 99：Range 只是请求提示，服务端可忽略；bytes() 会把整个响应体
                    // 读进内存。只取前 1KB 就关流，失控镜像也撑不爆测速线程。
                    try (java.io.InputStream in = r.body().byteStream()) {
                        in.read(new byte[1024]);
                    }
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

    /** 上次测速完成时间（毫秒 epoch，随测速缓存一起保存；0 表示从未测过）。 */
    private static final String KEY_SPEED_CACHE_TIME = "mirror_speed_cache_time";

    /**
     * 保存测速结果缓存（url=latencyMs，-1 表示不可用），下次打开镜像源对话框直接显示，
     * 不用干等。空 url 的占位行不存。同时记下本次测速完成时间（统计条“更新于”用）。
     */
    public static void saveSpeedTestResults(Context context, List<MirrorSpeedResult> results) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (MirrorSpeedResult r : results) {
            if (!r.url.isEmpty()) {
                set.add(r.url + "=" + r.latencyMs);
            }
        }
        prefs(context).edit().putStringSet(KEY_SPEED_CACHE, set)
                .putLong(KEY_SPEED_CACHE_TIME, System.currentTimeMillis()).apply();
    }

    /** 上次测速完成时间（毫秒 epoch）；从未测过返回 0。 */
    public static long getSpeedTestTime(Context context) {
        return prefs(context).getLong(KEY_SPEED_CACHE_TIME, 0);
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
