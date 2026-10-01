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
        DEFAULT_ITEMS.add(new DrawerItemDef("news_feed", R.id.news_feed,
                R.string.user_news_feed, R.drawable.icon_news_feed));
        DEFAULT_ITEMS.add(new DrawerItemDef("notifications", R.id.notifications,
                R.string.notifications, R.drawable.icon_notifications));
        DEFAULT_ITEMS.add(new DrawerItemDef("trend", R.id.trend,
                R.string.trend, R.drawable.icon_trending));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_repos", R.id.my_repos,
                R.string.my_repositories, R.drawable.icon_repositories));
        DEFAULT_ITEMS.add(new DrawerItemDef("bookmarks", R.id.bookmarks,
                R.string.bookmarks_and_stars, R.drawable.icon_bookmark));
        DEFAULT_ITEMS.add(new DrawerItemDef("release_radar", R.id.release_radar,
                R.string.release_radar, R.drawable.icon_star));
        DEFAULT_ITEMS.add(new DrawerItemDef("pub_timeline", R.id.pub_timeline,
                R.string.pub_timeline, R.drawable.icon_timeline));
        DEFAULT_ITEMS.add(new DrawerItemDef("topic_discovery", R.id.topic_discovery,
                R.string.topic_discovery, R.drawable.tag));
        DEFAULT_ITEMS.add(new DrawerItemDef("star_groups", R.id.star_groups,
                R.string.star_groups, R.drawable.folder));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_issues", R.id.my_issues,
                R.string.my_issues, R.drawable.icon_issues));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_prs", R.id.my_prs,
                R.string.my_pull_requests, R.drawable.icon_pull_request));
        DEFAULT_ITEMS.add(new DrawerItemDef("my_gists", R.id.my_gists,
                R.string.my_gists, R.drawable.icon_gists));
        DEFAULT_ITEMS.add(new DrawerItemDef("blog", R.id.blog,
                R.string.blog, R.drawable.icon_github));
        DEFAULT_ITEMS.add(new DrawerItemDef("recent_history", R.id.recent_history,
                R.string.recent_history, R.drawable.icon_history));
        DEFAULT_ITEMS.add(new DrawerItemDef("download_manager", R.id.download_manager,
                R.string.download_manager, R.drawable.download_small));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
                    if (map.containsKey(key)) {
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
