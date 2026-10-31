package com.gh4a.fragment;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.1.10：热门建议磁盘缓存。热门榜变化慢，有效期 24 小时；进搜索页先显示
 * 缓存（秒出），后台再刷新替换；App 启动时预取，多数情况下进搜索页已有缓存。
 * 只存展示所需字段（仓库全名 + 星数），点击时用 owner/name 打开仓库页，
 * 不依赖内存里的 Repository 对象。
 * 0.1.20 D-01: 骨架（取 prefs、编解码容错、时间戳）收敛到 JsonCache，
 * 本类只留字段映射与公开 API（键名、TTL、空→null 语义均不变）。
 */
public class HotReposCache {
    private static final String PREFS = "hot_repos_cache";
    private static final String KEY_REPOS = "hot_repos";
    private static final String KEY_TIME = "hot_time";

    /** 热门缓存有效期：24 小时。 */
    public static final long TTL_MS = 24L * 60 * 60 * 1000;

    public static class HotRepo {
        public final String fullName;
        public final int stars;

        public HotRepo(String fullName, int stars) {
            this.fullName = fullName;
            this.stars = stars;
        }
    }

    private static final JsonCache<List<HotRepo>> CACHE = new JsonCache<>(PREFS,
            repos -> {
                JSONArray arr = new JSONArray();
                for (HotRepo r : repos) {
                    JSONObject o = new JSONObject();
                    o.put("fullName", r.fullName);
                    o.put("stars", r.stars);
                    arr.put(o);
                }
                return arr.toString();
            },
            json -> {
                List<HotRepo> out = new ArrayList<>();
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String fullName = o.optString("fullName", "");
                    if (!fullName.isEmpty()) {
                        out.add(new HotRepo(fullName, o.optInt("stars", 0)));
                    }
                }
                return out.isEmpty() ? null : out;
            });

    public static List<HotRepo> load(Context context) {
        return CACHE.load(context, KEY_REPOS);
    }

    public static void save(Context context, List<HotRepo> repos) {
        // 0.1.20 F-25: 空结果不存为“新鲜”缓存——load() 读到空即返回 null，
        // 存空会造成 isFresh()==true 但无可用缓存，启动预取因此跳过。
        if (context == null || repos == null || repos.isEmpty()) {
            return;
        }
        CACHE.save(context, KEY_REPOS, KEY_TIME, repos);
    }

    /** 缓存年龄（毫秒），无缓存返回 -1。 */
    public static long age(Context context) {
        return CACHE.age(context, KEY_TIME);
    }

    /** 缓存是否存在且在有效期内。 */
    public static boolean isFresh(Context context) {
        return CACHE.isFresh(context, KEY_TIME, TTL_MS);
    }
}
