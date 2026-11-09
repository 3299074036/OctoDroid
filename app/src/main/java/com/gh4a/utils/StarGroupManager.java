package com.gh4a.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gh4a.fragment.JsonCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Local (per-account) grouping of starred repositories. Groups and memberships
 * are stored in SharedPreferences; only the repo full name and description are
 * kept, so no extra API calls are needed to display a group.
 * 去重-1（0.2.4）：手写 JSON 编解码收敛到 JsonCache。文件名按登录动态拼
 * （star_groups_<login>），故编解码 lambda 静态共用、实例按调用构造；
 * groups 与 members 共用同一文件、两把键。文件名、键名、字段名与原先
 * 一字不差；损坏文件时 groups 仍返回已解析的部分条目、members 仍返回
 * 空对象（与原先一致）。
 */
public class StarGroupManager {
    private static final String PREFS_NAME = "star_groups";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_MEMBERS = "members";

    private static final JsonCache.ToJson<List<String>> GROUPS_TO_JSON = groups -> {
        JSONArray array = new JSONArray();
        for (String g : groups) {
            array.put(g);
        }
        return array.toString();
    };

    private static final JsonCache.FromJson<List<String>> GROUPS_FROM_JSON = json -> {
        List<String> groups = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            try {
                groups.add(array.getString(i));
            } catch (Exception ignored) {
                // 单条损坏跳过，其余照收（与原先一致）。
            }
        }
        return groups;
    };

    private static final JsonCache.ToJson<JSONObject> MEMBERS_TO_JSON =
            members -> members.toString();

    private static final JsonCache.FromJson<JSONObject> MEMBERS_FROM_JSON =
            json -> new JSONObject(json);

    /**
     * BUG-27：本地分组按显式传入的登录取键，不再读全局登录态
     * （Gh4Application.getAuthLogin()）。调用方必须传被查看账号的登录；
     * context 或 login 为空时返回 null（调用方按空结果/不写处理），
     * 避免落入共享的 null 桶。
     */
    private static JsonCache<List<String>> groupsCacheFor(Context context, String login) {
        if (context == null || login == null) {
            return null;
        }
        return new JsonCache<>(PREFS_NAME + "_" + login, GROUPS_TO_JSON, GROUPS_FROM_JSON);
    }

    private static JsonCache<JSONObject> membersCacheFor(Context context, String login) {
        if (context == null || login == null) {
            return null;
        }
        return new JsonCache<>(PREFS_NAME + "_" + login, MEMBERS_TO_JSON, MEMBERS_FROM_JSON);
    }

    public static class StarRepo {
        public final String fullName;
        public final String description;

        StarRepo(String fullName, String description) {
            this.fullName = fullName;
            this.description = description;
        }
    }

    @NonNull
    public static List<String> getGroups(Context context, String login) {
        List<String> groups = new ArrayList<>();
        JsonCache<List<String>> cache = groupsCacheFor(context, login);
        if (cache == null) {
            return groups;
        }
        List<String> loaded = cache.load(context, KEY_GROUPS);
        return loaded != null ? loaded : groups;
    }

    public static boolean createGroup(Context context, String login, String name) {
        name = name.trim();
        if (name.isEmpty()) {
            return false;
        }
        List<String> groups = getGroups(context, login);
        if (groups.contains(name)) {
            return false;
        }
        groups.add(name);
        saveGroups(context, login, groups);
        return true;
    }

    // 71（0.2.4）：renameGroup/deleteGroup/removeRepo 已删除——全树零调用方，
    // 本地分组 UI 只接了建组与入组；官方列表（UserListsGraphQL）有改名/删除/
    // 批量移，本地分组属遗留路径。删方法不删数据（groups/members 照旧），
    // 今后若给本地分组接 UI 再按需加回。

    @NonNull
    public static List<StarRepo> getRepos(Context context, String login, String group) {
        List<StarRepo> repos = new ArrayList<>();
        JSONObject members = getMembersJson(context, login);
        JSONArray array = members.optJSONArray(group);
        if (array == null) {
            return repos;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null) {
                repos.add(new StarRepo(
                        obj.optString("fullName"),
                        obj.optString("description", "")));
            }
        }
        return repos;
    }

    /** Assigns a repo to a group, removing it from any other group first. */
    public static void assignRepo(Context context, String login, String group, String fullName, String description) {
        JSONObject members = getMembersJson(context, login);
        // A repo belongs to at most one group
        for (Iterator<String> it = members.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONArray array = members.optJSONArray(key);
            if (array == null) {
                continue;
            }
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null && fullName.equals(obj.optString("fullName"))) {
                    array.remove(i);
                }
            }
        }
        JSONArray array = members.optJSONArray(group);
        if (array == null) {
            array = new JSONArray();
            try {
                members.put(group, array);
            } catch (JSONException ignored) {
            }
        }
        try {
            JSONObject obj = new JSONObject();
            obj.put("fullName", fullName);
            obj.put("description", description != null ? description : "");
            array.put(obj);
        } catch (JSONException ignored) {
        }
        saveMembers(context, login, members);
    }

    @Nullable
    public static String findGroupFor(Context context, String login, String fullName) {
        JSONObject members = getMembersJson(context, login);
        for (Iterator<String> it = members.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONArray array = members.optJSONArray(key);
            if (array == null) {
                continue;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null && fullName.equals(obj.optString("fullName"))) {
                    return key;
                }
            }
        }
        return null;
    }

    private static void saveGroups(Context context, String login, List<String> groups) {
        JsonCache<List<String>> cache = groupsCacheFor(context, login);
        if (cache != null) {
            cache.save(context, KEY_GROUPS, null, groups);
        }
    }

    private static JSONObject getMembersJson(Context context, String login) {
        JsonCache<JSONObject> cache = membersCacheFor(context, login);
        if (cache == null) {
            return new JSONObject();
        }
        JSONObject members = cache.load(context, KEY_MEMBERS);
        return members != null ? members : new JSONObject();
    }

    private static void saveMembers(Context context, String login, JSONObject members) {
        JsonCache<JSONObject> cache = membersCacheFor(context, login);
        if (cache != null) {
            cache.save(context, KEY_MEMBERS, null, members);
        }
    }
}
