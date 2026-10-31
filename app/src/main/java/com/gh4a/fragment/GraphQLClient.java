package com.gh4a.fragment;

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
 * 0.1.20 F-11 的可中断性随之迁入：执行中的 Call 由 cancelActiveCall()
 * 取消（StarMergedFragment dispose 时调用），而非只 dispose 订阅。
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

    /** 在途的 GraphQL 调用——dispose 时取消它，而不是让它在 io 线程跑到完成。 */
    private static volatile okhttp3.Call sActiveCall;

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

    /** 取消在途的 GraphQL 调用（无在途调用时不做任何事）。 */
    public static void cancelActiveCall() {
        okhttp3.Call call = sActiveCall;
        if (call != null) {
            call.cancel();
        }
    }

    /** 执行一次 GraphQL 请求，返回原始 JSON（有 errors 时抛错，不返回半成品）。 */
    public static String execute(String query, JSONObject variables) throws Exception {
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
        sActiveCall = call;
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
            if (sActiveCall == call) {
                sActiveCall = null;
            }
        }
    }
}
