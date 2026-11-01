package com.gh4a.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 软件商店的本地数据：关注仓库 + 已下架记录 + 已看／隐藏，存在 Gh4a-pref（与镜像源同库）。
 * 只存仓库名、原因、日期，不存任何账号、凭据与搜索内容。
 */
public class StorePrefs {
    private static final String KEY_FOLLOWED = "store_followed_repos";
    private static final String KEY_REMOVED = "store_removed_repos";
    private static final String KEY_SEEN = "store_seen_repos";
    private static final String KEY_HIDDEN = "store_hidden_repos";

    public static class RemovedEntry {
        public final String name;
        public final String reason;
        public final String date;

        public RemovedEntry(String name, String reason, String date) {
            this.name = name;
            this.reason = reason;
            this.date = date;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    public static List<String> getFollowed(Context context) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY_FOLLOWED, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.optString(i, "");
                if (!name.isEmpty() && !out.contains(name)) {
                    out.add(name);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isFollowed(Context context, String fullName) {
        return getFollowed(context).contains(fullName);
    }

    public static void addFollowed(Context context, String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return;
        }
        List<String> followed = getFollowed(context);
        if (!followed.contains(fullName)) {
            followed.add(fullName);
            saveFollowed(context, followed);
        }
    }

    public static void removeFollowed(Context context, String fullName) {
        List<String> followed = getFollowed(context);
        if (followed.remove(fullName)) {
            saveFollowed(context, followed);
        }
    }

    private static void saveFollowed(Context context, List<String> followed) {
        JSONArray arr = new JSONArray();
        for (String name : followed) {
            arr.put(name);
        }
        prefs(context).edit().putString(KEY_FOLLOWED, arr.toString()).apply();
    }

    /** 已看：行置灰，不从列表消失。 */
    public static boolean isSeen(Context context, String fullName) {
        return getStringSet(context, KEY_SEEN).contains(fullName);
    }

    public static void setSeen(Context context, String fullName, boolean seen) {
        if (fullName == null || fullName.isEmpty()) {
            return;
        }
        List<String> seenList = getStringSet(context, KEY_SEEN);
        boolean changed = seen ? !seenList.contains(fullName) && seenList.add(fullName)
                : seenList.remove(fullName);
        if (changed) {
            saveStringSet(context, KEY_SEEN, seenList);
        }
    }

    /** 隐藏：从所有商店列表消失（无单独管理页，清除应用数据可恢复）。 */
    public static boolean isHidden(Context context, String fullName) {
        return getStringSet(context, KEY_HIDDEN).contains(fullName);
    }

    public static void hide(Context context, String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return;
        }
        List<String> hidden = getStringSet(context, KEY_HIDDEN);
        if (!hidden.contains(fullName)) {
            hidden.add(fullName);
            saveStringSet(context, KEY_HIDDEN, hidden);
        }
    }

    private static List<String> getStringSet(Context context, String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.optString(i, "");
                if (!name.isEmpty() && !out.contains(name)) {
                    out.add(name);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void saveStringSet(Context context, String key, List<String> values) {
        JSONArray arr = new JSONArray();
        for (String name : values) {
            arr.put(name);
        }
        prefs(context).edit().putString(key, arr.toString()).apply();
    }

    public static List<RemovedEntry> getRemoved(Context context) {
        List<RemovedEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY_REMOVED, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                out.add(new RemovedEntry(
                        obj.optString("name", ""),
                        obj.optString("reason", ""),
                        obj.optString("date", "")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** 记一条下架：同名已存在则刷新原因与日期，不重复。 */
    public static void recordRemoved(Context context, String fullName, String reason) {
        if (fullName == null || fullName.isEmpty()) {
            return;
        }
        List<RemovedEntry> entries = getRemoved(context);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        boolean found = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name.equals(fullName)) {
                entries.set(i, new RemovedEntry(fullName, reason, today));
                found = true;
                break;
            }
        }
        if (!found) {
            entries.add(0, new RemovedEntry(fullName, reason, today));
        }
        try {
            JSONArray arr = new JSONArray();
            for (RemovedEntry entry : entries) {
                JSONObject obj = new JSONObject();
                obj.put("name", entry.name);
                obj.put("reason", entry.reason);
                obj.put("date", entry.date);
                arr.put(obj);
            }
            prefs(context).edit().putString(KEY_REMOVED, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}
