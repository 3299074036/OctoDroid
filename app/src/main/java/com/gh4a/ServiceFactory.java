package com.gh4a;

import android.content.Context;
import android.util.Log;

import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.MirrorHelper;
import com.meisolsson.githubsdk.core.ByteArrayResponseConverterFactory;
import com.meisolsson.githubsdk.core.GitHubPaginationInterceptor;
import com.meisolsson.githubsdk.core.ServiceGenerator;
import com.meisolsson.githubsdk.core.StringResponseConverterFactory;
import com.meisolsson.githubsdk.model.GitHubEventType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import org.json.JSONArray;
import org.json.JSONObject;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

public class ServiceFactory {
    private static final String DEFAULT_HEADER_ACCEPT = "application/vnd.github.v3+json," +
            "application/vnd.github.v3.raw+json," +
            "application/vnd.github.v3.html+json";

    private final static HttpLoggingInterceptor LOGGING_INTERCEPTOR = new HttpLoggingInterceptor()
            .setLevel(HttpLoggingInterceptor.Level.BASIC);

    private final static Interceptor PAGINATION_INTERCEPTOR = new GitHubPaginationInterceptor();

    /**
     * 213：GitHub 一加新事件类型，SDK 的 GitHubEventType.valueOf 即抛
     * IllegalArgumentException，整页解析失败（111 号只把整页降级为空页）。
     * 这里在 Moshi 解析前把未知类型条目从 events 响应里剥掉：已知类型
     * 正常显示，只有新类型条目缺失，下一页照常加载。Moshi 层覆盖
     * GitHubEventType 适配器对 SDK 的自定义 GitHubEventAdapter 无效
     * （实测见 /tmp/sdkx/MoshiSdkTest.java：自定义 @FromJson 直接调
     * valueOf），故只能在 OkHttp 层过滤。任何解析问题都 fail-open
     * （原样返回），绝不因过滤本身丢整页；111 兜底保留。
     */
    private final static Interceptor UNKNOWN_EVENT_TYPE_INTERCEPTOR = chain -> {
        Response response = chain.proceed(chain.request());
        // fail-open 用的原字节：body 一旦读出即被消费，任何提前返回都
        // 必须用它重建 body，否则下游 converter 读到空体。
        byte[] rawBytes = null;
        try {
            if (!isEventsRequest(chain.request())
                    || !response.isSuccessful()
                    || response.body() == null) {
                return response;
            }
            MediaType contentType = response.body().contentType();
            if (contentType == null || !"json".equalsIgnoreCase(contentType.subtype())) {
                return response;
            }
            rawBytes = response.body().bytes();
            String bodyString = new String(rawBytes,
                    contentType.charset(StandardCharsets.UTF_8));
            JSONArray events = new JSONArray(bodyString);
            JSONArray kept = new JSONArray();
            boolean dropped = false;
            for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event != null && isUnknownEventType(event.optString("type", null))) {
                    dropped = true;
                    continue;
                }
                kept.put(events.get(i));
            }
            if (!dropped) {
                // 无改动：原字节重建，与原响应逐字节一致。
                return response.newBuilder()
                        .body(ResponseBody.create(contentType, rawBytes))
                        .build();
            }
            Log.d(Gh4Application.LOG_TAG,
                    "Stripped unknown GitHub event type(s) from events response");
            byte[] bytes = kept.toString().getBytes(StandardCharsets.UTF_8);
            return response.newBuilder()
                    .body(ResponseBody.create(contentType, bytes))
                    .build();
        } catch (Exception e) {
            // fail-open：过滤失败时返回原响应（body 已消费则用原字节
            // 重建），解析层/111 兜底接手。
            if (rawBytes != null && response.body() != null) {
                return response.newBuilder()
                        .body(ResponseBody.create(response.body().contentType(), rawBytes))
                        .build();
            }
            return response;
        }
    };

    /** 仅 events 系端点（9 个 EventService 方法的路径都含 events /
     * received_events 段）需要剥未知类型；其余端点原样放行。 */
    private static boolean isEventsRequest(Request request) {
        for (String segment : request.url().pathSegments()) {
            if ("events".equals(segment) || "received_events".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /** type 缺失不归本拦截器管（留给解析层/111 兜底）；type 存在但
     * GitHubEventType 里没有即未知。 */
    private static boolean isUnknownEventType(String type) {
        if (type == null) {
            return false;
        }
        try {
            GitHubEventType.valueOf(type);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private final static Interceptor CACHE_STATUS_INTERCEPTOR = chain -> {
        Response response = chain.proceed(chain.request());
        Log.d("OkHttp", String.format(Locale.US, "For %s: network return code %d, cache %d",
                Gh4Application.redactUrlForLog(response.request().url().toString()),
                response.networkResponse() != null ? response.networkResponse().code() : -1,
                response.cacheResponse() != null ? response.cacheResponse().code() : -1));
        return response;
    };

    private final static Interceptor CACHE_BYPASS_INTERCEPTOR = chain -> {
        Request request = chain.request()
                .newBuilder()
                .addHeader("Cache-Control", "no-cache")
                .build();
        return chain.proceed(request);
    };

    // FIXME: The notifications endpoint currently returns invalid/empty ETags. GH support
    //        says they're looking into it and to use If-Modified-Since in the meantime.
    //        Unfortunately, the Last-Modified header can't be relied on either, as it's not
    //        updated when marking notifications as read :-(
    //        We thus check for the invalid ETag and prevent caching if we found it.
    //        Once this is fixed on server side, this interceptor should be removed.
    private final static CacheControl NO_STORE_CACHE_CONTROL =
            new CacheControl.Builder().noStore().build();
    private final static Interceptor ETAG_WORKAROUND_INTERCEPTOR = chain -> {
        Response response = chain.proceed(chain.request());
        String etag = response.header("ETag");
        if (etag != null && etag.contains("\"\"")) {
            return response.newBuilder()
                    .header("Cache-Control", NO_STORE_CACHE_CONTROL.toString())
                    .build();
        }
        return response;
    };

    private final static Interceptor CACHE_MAX_AGE_INTERCEPTOR = chain -> {
        Response response = chain.proceed(chain.request());
        CacheControl origCacheControl = CacheControl.parse(response.headers());
        // Github sends max-age=60, which leads to problems when we modify stuff and
        // reload data afterwards. Make sure to constrain max age to 2 seconds to only avoid
        // network calls in cases where the exact same data is loaded from multiple places
        // at the same time, and use ETags to avoid useless data transfers otherwise.
        if (origCacheControl.maxAgeSeconds() <= 2) {
            return response;
        }
        CacheControl.Builder newBuilder = new CacheControl.Builder()
                .maxAge(2, TimeUnit.SECONDS);
        if (origCacheControl.maxStaleSeconds() >= 0) {
            newBuilder.maxStale(origCacheControl.maxStaleSeconds(), TimeUnit.SECONDS);
        }
        if (origCacheControl.minFreshSeconds() >= 0) {
            newBuilder.minFresh(origCacheControl.minFreshSeconds(), TimeUnit.SECONDS);
        }
        if (origCacheControl.noCache()) {
            newBuilder.noCache();
        }
        if (origCacheControl.noStore()) {
            newBuilder.noStore();
        }
        if (origCacheControl.noTransform()) {
            newBuilder.noTransform();
        }
        return response.newBuilder()
                .header("Cache-Control", newBuilder.build().toString())
                .build();
    };

    private final static Retrofit.Builder RETROFIT_BUILDER = new Retrofit.Builder()
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .addConverterFactory(new StringResponseConverterFactory())
            .addConverterFactory(new ByteArrayResponseConverterFactory())
            .addConverterFactory(MoshiConverterFactory.create(ServiceGenerator.moshi));

    private static OkHttpClient sApiHttpClient;
    private static OkHttpClient sImageHttpClient;

    // 107：多线程并发读写（Rx IO 线程 + 主线程 + WorkManager 线程均可进入），
    // 未同步 HashMap 最坏丢条目/重复建 service，换 ConcurrentHashMap。
    private final static ConcurrentHashMap<String, Object> sCache = new ConcurrentHashMap<>();

    public static <S> S get(Class<S> serviceClass, boolean bypassCache) {
        return get(serviceClass, bypassCache, null, null, null);
    }

    public static <S> S get(Class<S> serviceClass, boolean bypassCache, Integer pageSize) {
        return get(serviceClass, bypassCache, null, null, pageSize);
    }

    public static <S> S getForFullPagedLists(Class<S> serviceClass, boolean bypassCache) {
        return get(serviceClass, bypassCache, ApiHelpers.MAX_PAGE_SIZE);
    }

    public static <S> S get(Class<S> serviceClass, boolean bypassCache, String acceptHeader,
            String token, Integer pageSize) {
        String key = makeKey(serviceClass, bypassCache, acceptHeader, token, pageSize);
        // 230：check-then-put 非原子——ConcurrentHashMap 只保证单次操作
        // 原子，并发两线程可同时 get 到 null、各自建一份 Retrofit/
        // OkHttpClient（各带连接池 + Dispatcher 线程池），后 put 者覆盖，
        // 丢弃的一份白建；computeIfAbsent 让 get-or-create 一次原子完成。
        return (S) sCache.computeIfAbsent(key, k ->
                createService(serviceClass, bypassCache, acceptHeader, token, pageSize));
    }

    private static String makeKey(Class<?> serviceClass, boolean bypassCache,
            String acceptHeader, String token, Integer pageSize) {
        // S-L6：缓存 key 里不再拼原始 token，只拼它的 SHA-256（仅内存，
        // 无持久化、无日志；区分度不变，token 本身不再出现在 key 里）。
        return String.format(Locale.US, "%s-%d-%s-%s-%d",
                serviceClass.getSimpleName(), bypassCache ? 1 : 0,
                acceptHeader != null ? acceptHeader : "",
                token != null ? sha256Hex(token) : "", pageSize != null ? pageSize : 0);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private static <S> S createService(Class<S> serviceClass, final boolean bypassCache,
            final String acceptHeader, final String token, final Integer pageSize) {
        OkHttpClient.Builder clientBuilder = sApiHttpClient.newBuilder()
                .addInterceptor(PAGINATION_INTERCEPTOR)
                .addInterceptor(UNKNOWN_EVENT_TYPE_INTERCEPTOR)
                .addNetworkInterceptor(ETAG_WORKAROUND_INTERCEPTOR)
                .addNetworkInterceptor(CACHE_MAX_AGE_INTERCEPTOR)
                .addInterceptor(chain -> {
                    Request original = chain.request();

                    Request.Builder requestBuilder = original.newBuilder()
                            .method(original.method(), original.body());

                    String tokenToUse = token != null
                            ? token : Gh4Application.get().getAuthToken();
                    if (tokenToUse != null) {
                        requestBuilder.header("Authorization", "Token " + tokenToUse);
                        // SEC-6: mirror the ImageViewerActivity no-store
                        // pattern — authenticated responses (which may be
                        // private-repo data) must never land in the shared
                        // api-http disk cache. Unauthenticated requests keep
                        // the existing caching behaviour (public data only).
                        requestBuilder.header("Cache-Control", "no-store");
                    }
                    // 0.2.2（S-M4）：未登录时不发任何 Authorization 头——之前
                    // 用 CLIENT_ID:CLIENT_SECRET 发 Basic Auth，会把客户端
                    // 密钥随每个未登录请求发往 api.github.com。代价是未登录
                    // 请求走更低的匿名限额；已登录请求（Token）不受影响。
                    if (pageSize != null && original.url().queryParameterNames().contains("page")) {
                        requestBuilder.url(original.url().newBuilder()
                                .addQueryParameter("per_page", String.valueOf(pageSize))
                                .build());
                    }
                    if (original.header("Accept") == null) {
                        final String header;
                        if (acceptHeader != null) {
                            header = acceptHeader;
                        } else {
                            header = DEFAULT_HEADER_ACCEPT;
                        }
                        requestBuilder.addHeader("Accept", header);
                    }

                    return chain.proceed(requestBuilder.build());
                });

        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(LOGGING_INTERCEPTOR);
            clientBuilder.addInterceptor(CACHE_STATUS_INTERCEPTOR);
        }
        if (bypassCache) {
            clientBuilder.addInterceptor(CACHE_BYPASS_INTERCEPTOR);
        }

        Retrofit retrofit = RETROFIT_BUILDER
                .baseUrl("https://api.github.com")
                .client(clientBuilder.build())
                .build();
        return retrofit.create(serviceClass);
    }

    public static OkHttpClient.Builder getHttpClientBuilder() {
        return sApiHttpClient.newBuilder();
    }

    public static OkHttpClient getImageHttpClient() {
        return sImageHttpClient;
    }

    static void initClient(Context context) {
        int twentyMB = 20 * 1024 * 1024;
        // 231：存储不可用时 getCacheDir() 返回 null，new File((File) null, …)
        // 即 NPE，发生在 Gh4Application.onCreate，应用无法启动；
        // 无缓存目录时建无缓存 client（功能不受影响，只是不走磁盘缓存）。
        File cacheDir = context.getCacheDir();
        OkHttpClient.Builder apiBuilder = new OkHttpClient.Builder();
        if (cacheDir != null) {
            apiBuilder.cache(new Cache(new File(cacheDir, "api-http"), twentyMB));
        }
        sApiHttpClient = apiBuilder.build();
        OkHttpClient.Builder imageBuilder = sApiHttpClient.newBuilder();
        if (cacheDir != null) {
            imageBuilder.cache(new Cache(new File(cacheDir, "image-http"), twentyMB));
        }
        sImageHttpClient = imageBuilder
                .addInterceptor(MirrorHelper.mirrorInterceptor(context))
                .build();
    }

    /**
     * SEC-6: evicts both OkHttp disk caches. Called from
     * Gh4Application.removeAccount() (which logout() delegates to), so no
     * private-repo response survives an account removal. Best-effort:
     * failures are swallowed, never breaking logout.
     */
    public static void evictCaches() {
        evictOne(sApiHttpClient);
        evictOne(sImageHttpClient);
    }

    private static void evictOne(OkHttpClient client) {
        try {
            if (client != null && client.cache() != null) {
                client.cache().evictAll();
            }
        } catch (Exception ignored) {
        }
    }
}