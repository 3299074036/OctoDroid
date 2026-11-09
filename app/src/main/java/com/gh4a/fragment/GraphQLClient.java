package com.gh4a.fragment;

import androidx.annotation.Nullable;

import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 0.1.20 D-02: RadarGraphQL 与 UserListsGraphQL 共用的 GraphQL 执行层。
 * 原先两份约 25–30 行神似复制：拼 {"query","variables"} 请求体、POST
 * https://api.github.com/graphql、401 → UnauthorizedException、其他非 2xx →
 * RuntimeException、解析 errors[0].message，各有一份双重检查锁 OkHttp 客户端。
 * 现收敛到这里；查询串与结果解析各留本类。
 *
 * 0.1.20 F-11 的可中断性随之迁入：执行中的 Call 由调用方持有的
 * CallHandle 取消（StarMergedFragment dispose 时调用），而非只 dispose 订阅。
 * 159：不设单槽 sActiveCall——两个调用各持自己的 handle，dispose 只取消
 * 自己的在途调用，不会跨调用取消另一方的请求。
 */
public class GraphQLClient {
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    /**
     * token 失效/被撤销时抛出的异常（HTTP 401），调用方应与普通网络异常
     * 区分处理：401 重试永远不会成功，不应无限 retry。
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    /** Reused client: an OkHttpClient per request leaks threads/connections. */
    private static volatile OkHttpClient sClient;

    /**
     * 159：一次 GraphQL 调用的取消句柄。调用方在订阅时创建、dispose 时调
     * cancel()；同一 fetch 内多次顺序 execute 共用一个 handle，每次执行都
     * 更新在途 Call，任意时刻只取消当前在途的那一次。
     */
    public static final class CallHandle {
        private volatile okhttp3.Call mCall;

        /** 取消本次调用当前在途的请求（无在途请求时不做任何事）。 */
        public void cancel() {
            okhttp3.Call call = mCall;
            if (call != null) {
                call.cancel();
            }
        }
    }

    private static OkHttpClient client() {
        OkHttpClient c = sClient;
        if (c == null) {
            synchronized (GraphQLClient.class) {
                c = sClient;
                if (c == null) {
                    c = ServiceFactory.getHttpClientBuilder()
                            .addInterceptor(chain -> {
                                Request original = chain.request();
                                Request.Builder b = original.newBuilder()
                                        .method(original.method(), original.body());
                                String t = Gh4Application.get().getAuthToken();
                                if (t != null) {
                                    b.header("Authorization", "Bearer " + t);
                                }
                                return chain.proceed(b.build());
                            })
                            .build();
                    sClient = c;
                }
            }
        }
        return c;
    }

    /** 执行一次 GraphQL 请求，返回原始 JSON（有 errors 时抛错，不返回半成品）。 */
    public static String execute(String query, JSONObject variables) throws Exception {
        return execute(query, variables, null);
    }

    /** 同上，带取消句柄：handle 不为 null 时把在途 Call 登记进去，供 dispose 取消。 */
    public static String execute(String query, JSONObject variables,
            @Nullable CallHandle handle) throws Exception {
        String token = Gh4Application.get().getAuthToken();

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("variables", variables);

        Request.Builder reqBuilder = new Request.Builder()
                .url("https://api.github.com/graphql")
                .post(RequestBody.create(body.toString(), JSON));
        if (token != null) {
            reqBuilder.header("Authorization", "Bearer " + token);
        }

        okhttp3.Call call = client().newCall(reqBuilder.build());
        if (handle != null) {
            handle.mCall = call;
        }
        try (Response response = call.execute()) {
            String json = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                if (response.code() == 401) {
                    throw new UnauthorizedException("GraphQL HTTP 401");
                }
                throw new RuntimeException("GraphQL HTTP " + response.code());
            }
            JSONObject root = new JSONObject(json);
            if (root.has("errors")) {
                JSONArray errors = root.optJSONArray("errors");
                if (errors != null && errors.length() > 0) {
                    throw new RuntimeException(
                            "GraphQL: " + errors.getJSONObject(0).optString("message"));
                }
            }
            return json;
        } finally {
            if (handle != null && handle.mCall == call) {
                handle.mCall = null;
            }
        }
    }
}
