package com.gh4a.fragment;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Caches radar items per account so the page shows instantly from the
 * last load while a fresh fetch runs in the background.
 * 0.1.20 D-01: 骨架收敛到 JsonCache；已读状态已迁出为 StarReadState，
 * 本类同名方法仅作委托（旧调用方不受影响，新代码直接用 StarReadState）。
 */
public class RadarCache {
    private static final String PREFS = "radar_cache";
    private static final String KEY_PREFIX = "items_";
    private static final String TIME_PREFIX = "time_";

    private static final JsonCache<List<ReleaseRadarFragment.RadarItem>> CACHE =
            new JsonCache<>(PREFS,
                    items -> {
                        JSONArray arr = new JSONArray();
                        for (ReleaseRadarFragment.RadarItem item : items) {
                            JSONObject o = new JSONObject();
                            o.put("owner", item.owner);
                            o.put("repo", item.repo);
                            o.put("releaseId", item.releaseId);
                            o.put("tagName", item.tagName);
                            o.put("releaseName", item.releaseName);
                            o.put("publishedAt", item.publishedAt);
                            o.put("createdAt", item.createdAt);
                            arr.put(o);
                        }
                        return arr.toString();
                    },
                    json -> {
                        List<ReleaseRadarFragment.RadarItem> items = new ArrayList<>();
                        JSONArray arr = new JSONArray(json);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            items.add(new ReleaseRadarFragment.RadarItem(
                                    o.optString("owner", ""),
                                    o.optString("repo", ""),
                                    o.optLong("releaseId", 0),
                                    o.optString("tagName", ""),
                                    o.optString("releaseName", ""),
                                    o.optLong("publishedAt", 0),
                                    o.optLong("createdAt", 0)));
                        }
                        return items;
                    });

    public static List<ReleaseRadarFragment.RadarItem> load(Context context, String login) {
        if (context == null || login == null) {
            return null;
        }
        return CACHE.load(context, KEY_PREFIX + login);
    }

    public static void save(Context context, String login,
            List<ReleaseRadarFragment.RadarItem> items) {
        if (context == null || login == null || items == null) {
            return;
        }
        CACHE.save(context, KEY_PREFIX + login, TIME_PREFIX + login, items);
    }

    // 0.1.20 D-01: 已读状态迁出——以下方法委托给 StarReadState（存储与键不变）。
    public static Set<String> getReadKeys(Context context, String login) {
        return StarReadState.getReadKeys(context, login);
    }

    public static void markRead(Context context, String login, String key) {
        StarReadState.markRead(context, login, key);
    }

    public static void markAllRead(Context context, String login,
            List<ReleaseRadarFragment.RadarItem> items) {
        StarReadState.markAllRead(context, login, items);
    }

    public static int unreadCount(List<ReleaseRadarFragment.RadarItem> items,
            Set<String> readKeys) {
        return StarReadState.unreadCount(items, readKeys);
    }

    /** Age of the cached data in millis, or -1 if none. */
    public static long age(Context context, String login) {
        if (context == null || login == null) {
            return -1;
        }
        return CACHE.age(context, TIME_PREFIX + login);
    }
}
