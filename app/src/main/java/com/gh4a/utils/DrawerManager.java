package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;

import com.gh4a.R;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages user customization of the navigation drawer: which items are visible
 * and in what order. Stored globally (not per-account) in SharedPreferences.
 */
public class DrawerManager {
    private static final String PREFS_NAME = "drawer_config";
    private static final String KEY_ORDER = "order";
    private static final String KEY_HIDDEN = "hidden";

    public static class DrawerItemDef {
        public final String key;
        @IdRes public final int menuId;
        @StringRes public final int titleRes;
        @DrawableRes public final int iconRes;

        DrawerItemDef(String key, @IdRes int menuId,
                      @StringRes int titleRes, @DrawableRes int iconRes) {
            this.key = key;
            this.menuId = menuId;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
        }
    }

    private static final List<DrawerItemDef> DEFAULT_ITEMS = new ArrayList<>();

    static {
        DEFAULT_ITEMS.add(new DrawerItemDef("search", R.id.search,
                R.string.search, R.drawable.icon_search));
        DEFAULT_ITEMS.add(new DrawerItemDef("feed_hub", R.id.feed_hub,
                R.string.feed_hub, R.drawable.icon_news_feed));
        DEFAULT_ITEMS.add(new DrawerItemDef("notifications", R.id.notifications,
                R.string.notifications, R.drawable.icon_notifications));
        DEFAULT_ITEMS.add(new DrawerItemDef("discover_hub", R.id.discover_hub,
                R.string.discover_hub, R.drawable.icon_trending));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_repos", R.id.my_repos,
                R.string.my_repositories, R.drawable.icon_repositories));
        DEFAULT_ITEMS.add(new DrawerItemDef("star_hub", R.id.star_hub,
                R.string.star_hub, R.drawable.icon_star));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_items", R.id.issues_prs,
                R.string.my_items, R.drawable.icon_issues));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_gists", R.id.my_gists,
                R.string.my_gists, R.drawable.icon_gists));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 抽屉合并迁移：旧 key → 新 key。后续合并（事项页、Star 页、发现页…）往这里加。
     * 排序时旧 key 映射到新 key 并保留首次出现的位置；隐藏状态按组折算
     * （组内旧 key 全被隐藏 → 新 key 隐藏）。
     */
    private static final Map<String, String> MERGED_KEY_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("news_feed", "feed_hub");
        m.put("pub_timeline", "feed_hub");
        m.put("recent_history", "search");
        m.put("my_issues", "my_items");
        m.put("my_prs", "my_items");
        m.put("bookmarks", "star_hub");
        m.put("star_groups", "star_hub");
        m.put("release_radar", "star_hub");
        m.put("trend", "discover_hub");
        m.put("topic_discovery", "discover_hub");
        m.put("blog", "discover_hub");
        MERGED_KEY_MAP = Collections.unmodifiableMap(m);
    }

    private static Map<String, DrawerItemDef> defMap() {
        Map<String, DrawerItemDef> map = new LinkedHashMap<>();
        for (DrawerItemDef def : DEFAULT_ITEMS) {
            map.put(def.key, def);
        }
        return map;
    }

    /** All items in default order (for the edit UI). */
    public static List<DrawerItemDef> getDefaultItems() {
        return new ArrayList<>(DEFAULT_ITEMS);
    }

    /** Current user order of keys; falls back to default on first run. */
    public static List<String> getOrderedKeys(Context context) {
        List<String> keys = new ArrayList<>();
        String json = prefs(context).getString(KEY_ORDER, null);
        Map<String, DrawerItemDef> map = defMap();
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    String key = array.getString(i);
                    // 合并迁移：旧 key 映射到新 key，保留首次出现的位置并去重
                    String mapped = MERGED_KEY_MAP.get(key);
                    if (mapped != null) {
                        key = mapped;
                    }
                    if (map.containsKey(key) && !keys.contains(key)) {
                        keys.add(key);
                    }
                }
            } catch (JSONException ignored) {
            }
        }
        // Append any new items not yet in the saved order (e.g. after an update)
        for (String key : map.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static void saveOrder(Context context, List<String> keys) {
        JSONArray array = new JSONArray();
        for (String key : keys) {
            array.put(key);
        }
        prefs(context).edit().putString(KEY_ORDER, array.toString()).apply();
    }

    public static Set<String> getHiddenKeys(Context context) {
        Set<String> hidden = new HashSet<>();
        String json = prefs(context).getString(KEY_HIDDEN, null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    hidden.add(array.getString(i));
                }
            } catch (JSONException ignored) {
            }
        }
        // 合并迁移：按新 key 分组旧 key，组内旧 key 全被隐藏 → 新 key 隐藏；
        // 旧 key 从隐藏集合里清理掉
        Map<String, List<String>> groups = new HashMap<>();
        for (Map.Entry<String, String> e : MERGED_KEY_MAP.entrySet()) {
            List<String> g = groups.get(e.getValue());
            if (g == null) {
                g = new ArrayList<>();
                groups.put(e.getValue(), g);
            }
            g.add(e.getKey());
        }
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            boolean allHidden = true;
            for (String oldKey : e.getValue()) {
                if (!hidden.contains(oldKey)) {
                    allHidden = false;
                    break;
                }
            }
            if (allHidden) {
                hidden.add(e.getKey());
            }
            hidden.removeAll(e.getValue());
        }
        return hidden;
    }

    public static boolean isVisible(Context context, String key) {
        return !getHiddenKeys(context).contains(key);
    }

    public static void setVisible(Context context, String key, boolean visible) {
        Set<String> hidden = getHiddenKeys(context);
        if (visible) {
            hidden.remove(key);
        } else {
            hidden.add(key);
        }
        JSONArray array = new JSONArray();
        for (String key2 : hidden) {
            array.put(key2);
        }
        prefs(context).edit().putString(KEY_HIDDEN, array.toString()).apply();
    }

    /** Visible items in user order, for building the drawer menu. */
    public static List<DrawerItemDef> getVisibleOrderedItems(Context context) {
        Map<String, DrawerItemDef> map = defMap();
        Set<String> hidden = getHiddenKeys(context);
        List<DrawerItemDef> result = new ArrayList<>();
        for (String key : getOrderedKeys(context)) {
            if (!hidden.contains(key)) {
                result.add(map.get(key));
            }
        }
        return result;
    }

    public static void resetToDefault(Context context) {
        prefs(context).edit().remove(KEY_ORDER).remove(KEY_HIDDEN).apply();
    }
}
