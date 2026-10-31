package com.gh4a.fragment;

import android.util.Log;


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

/**
 * Fetches the latest releases of the user's starred repositories via a
 * single GitHub GraphQL request, instead of N+1 REST calls. One round-trip
 * instead of ~30, which matters a lot on high-latency networks.
 */
public class RadarGraphQL {
    private static final String TAG = "RadarGraphQL";
    private static final int MAX_REPOS = 30;

    /**
     * 0.1.20 D-02: 401 异常现由 GraphQLClient 抛出；本类保留同名异常作
     * 子类兼容（旧调用方 catch RadarGraphQL.UnauthorizedException 仍有效）。
     */
    public static class UnauthorizedException extends GraphQLClient.UnauthorizedException {
        public UnauthorizedException(String message) {
            super(message);
        }
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
        // 0.1.20 F-11/D-02: dispose 时取消在途 GraphQL 调用（与官方列表同款）。
        return Single.fromCallable(() -> doFetch(login))
                .doOnDispose(GraphQLClient::cancelActiveCall);
    }

    private static List<ReleaseRadarFragment.RadarItem> doFetch(String login) throws Exception {
        // 0.1.20 D-02: 请求体拼装、HTTP、401/errors 处理收敛到 GraphQLClient；
        // 本类只留查询串与结果解析。parse() 仍自带 errors 检查（无害的二次检查）。
        JSONObject vars = new JSONObject();
        vars.put("login", login);
        return parse(GraphQLClient.execute(QUERY, vars));
    }

    private static List<ReleaseRadarFragment.RadarItem> parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (root.has("errors")) {
            JSONArray errors = root.optJSONArray("errors");
            if (errors != null && errors.length() > 0) {
                JSONObject first = errors.optJSONObject(0);
                throw new RuntimeException(
                        "GraphQL: " + (first != null ? first.optString("message") : ""));
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
        // BUG-26：与 UserListsGraphQL 一致的空安全链——字段缺失时
        // optJSONObject 返回 null，不再直接解引用抛 NPE 拖垮整页。
        JSONObject starred = user.optJSONObject("starredRepositories");
        JSONArray repos = starred != null ? starred.optJSONArray("nodes") : null;
        if (repos == null) {
            return items;
        }
        for (int i = 0; i < repos.length() && i < MAX_REPOS; i++) {
            JSONObject repo = repos.optJSONObject(i);
            if (repo == null) {
                continue;
            }
            String nameWithOwner = repo.optString("nameWithOwner", "");
            int slash = nameWithOwner.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String owner = nameWithOwner.substring(0, slash);
            String name = nameWithOwner.substring(slash + 1);

            JSONObject releasesObj = repo.optJSONObject("releases");
            JSONArray releases = releasesObj != null ? releasesObj.optJSONArray("nodes") : null;
            if (releases == null) {
                continue;
            }
            // First non-draft release (already newest-first)
            for (int j = 0; j < releases.length(); j++) {
                JSONObject r = releases.optJSONObject(j);
                if (r == null) {
                    continue;
                }
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
        // BUG-26：先试带毫秒格式（GitHub 常返回 …ss.SSS'Z'），再试无毫秒
        // 格式；都失败才记日志回 0，避免带毫秒时间戳被排到底部。
        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String format : formats) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(format, Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("UTC"));
                return f.parse(iso).getTime();
            } catch (ParseException ignored) {
            }
        }
        Log.w(TAG, "Bad date: " + iso);
        return 0;
    }
}
