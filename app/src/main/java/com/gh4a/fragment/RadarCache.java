package com.gh4a.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Caches radar items per account so the page shows instantly from the
 * last load while a fresh fetch runs in the background.
 */
public class RadarCache {
    private static final String PREFS = "radar_cache";
    private static final String KEY_PREFIX = "items_";
    private static final String TIME_PREFIX = "time_";

    public static List<ReleaseRadarFragment.RadarItem> load(Context context, String login) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_PREFIX + login, null);
        if (json == null) {
            return null;
        }
        try {
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
        } catch (Exception e) {
            return null;
        }
    }

    public static void save(Context context, String login,
            List<ReleaseRadarFragment.RadarItem> items) {
        try {
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
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PREFIX + login, arr.toString())
                    .putLong(TIME_PREFIX + login, System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    /** Age of the cached data in millis, or -1 if none. */
    public static long age(Context context, String login) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long t = prefs.getLong(TIME_PREFIX + login, -1);
        return t < 0 ? -1 : System.currentTimeMillis() - t;
    }
}
