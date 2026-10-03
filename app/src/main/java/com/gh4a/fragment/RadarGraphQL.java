package com.gh4a.fragment;

import android.util.Log;

import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Fetches the latest releases of the user's starred repositories via a
 * single GitHub GraphQL request, instead of N+1 REST calls. One round-trip
 * instead of ~30, which matters a lot on high-latency networks.
 */
public class RadarGraphQL {
    private static final String TAG = "RadarGraphQL";
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");
    private static final int MAX_REPOS = 30;

    /**
     * token 失效/被撤销时抛出的异常（HTTP 401），调用方应与普通网络异常
     * 区分处理：401 重试永远不会成功，不应无限 retry (N-2)。
     */
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    /** Reused client: an OkHttpClient per request leaks threads/connections. */
    private static volatile OkHttpClient sClient;

    private static OkHttpClient client() {
        OkHttpClient c = sClient;
        if (c == null) {
            synchronized (RadarGraphQL.class) {
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

    private static final String QUERY =
            "query($login: String!) {"
            + "  user(login: $login) {"
            + "    starredRepositories(first: 30, orderBy: {field: STARRED_AT, direction: DESC}) {"
            + "      nodes {"
            + "        nameWithOwner"
            + "        releases(first: 5, orderBy: {field: CREATED_AT, direction: DESC}) {"
            + "          nodes {"
            + "            databaseId"
            + "            tagName"
            + "            name"
            + "            publishedAt"
            + "            createdAt"
            + "            isDraft"
            + "          }"
            + "        }"
            + "      }"
            + "    }"
            + "  }"
            + "}";

    /** Single GraphQL request returning radar items, newest first. */
    public static Single<List<ReleaseRadarFragment.RadarItem>> fetch(String login) {
        return Single.fromCallable(() -> doFetch(login));
    }

    private static List<ReleaseRadarFragment.RadarItem> doFetch(String login) throws Exception {
        String token = Gh4Application.get().getAuthToken();

        JSONObject body = new JSONObject();
        body.put("query", QUERY);
        JSONObject vars = new JSONObject();
        vars.put("login", login);
        body.put("variables", vars);

        Request.Builder reqBuilder = new Request.Builder()
                .url("https://api.github.com/graphql")
                .post(RequestBody.create(body.toString(), JSON));
        if (token != null) {
            reqBuilder.header("Authorization", "Bearer " + token);
        }

        OkHttpClient client = client();

        try (Response response = client.newCall(reqBuilder.build()).execute()) {
            String json = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                if (response.code() == 401) {
                    throw new UnauthorizedException("GraphQL HTTP 401");
                }
                throw new RuntimeException("GraphQL HTTP " + response.code());
            }
            return parse(json);
        }
    }

    private static List<ReleaseRadarFragment.RadarItem> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.has("errors")) {
            JSONArray errors = root.getJSONArray("errors");
            if (errors.length() > 0) {
                throw new RuntimeException(
                        "GraphQL: " + errors.getJSONObject(0).optString("message"));
            }
        }
        List<ReleaseRadarFragment.RadarItem> items = new ArrayList<>();
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return items;
        }
        JSONObject user = data.optJSONObject("user");
        if (user == null) {
            return items;
        }
        JSONArray repos = user.optJSONObject("starredRepositories")
                .optJSONArray("nodes");
        if (repos == null) {
            return items;
        }
        for (int i = 0; i < repos.length() && i < MAX_REPOS; i++) {
            JSONObject repo = repos.getJSONObject(i);
            String nameWithOwner = repo.optString("nameWithOwner", "");
            int slash = nameWithOwner.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String owner = nameWithOwner.substring(0, slash);
            String name = nameWithOwner.substring(slash + 1);

            JSONArray releases = repo.optJSONObject("releases")
                    .optJSONArray("nodes");
            if (releases == null) {
                continue;
            }
            // First non-draft release (already newest-first)
            for (int j = 0; j < releases.length(); j++) {
                JSONObject r = releases.getJSONObject(j);
                if (r.optBoolean("isDraft", false)) {
                    continue;
                }
                items.add(new ReleaseRadarFragment.RadarItem(
                        owner,
                        name,
                        r.optLong("databaseId", 0),
                        r.optString("tagName", ""),
                        r.optString("name", ""),
                        parseTime(r.optString("publishedAt", null)),
                        parseTime(r.optString("createdAt", null))));
                break;
            }
        }
        // Newest first
        Collections.sort(items, (a, b) -> Long.compare(b.sortTime(), a.sortTime()));
        return items;
    }

    private static long parseTime(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime();
        } catch (ParseException e) {
            Log.w(TAG, "Bad date: " + iso);
            return 0;
        }
    }
}
