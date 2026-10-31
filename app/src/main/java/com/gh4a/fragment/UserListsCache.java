package com.gh4a.fragment;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.1.3：官方列表本地缓存。按登录账号分键存取；成功拉取后覆盖，
 * 新建/归属列表后由调用方重新拉取覆盖，失败时保留旧缓存不清空。
 * load() 返回 null 表示无缓存（与空列表区分：空列表是合法的拉取结果）。
 * 0.1.20 D-01: 骨架收敛到 JsonCache，本类只留字段映射与公开 API。
 */
public class UserListsCache {
    private static final String PREFS = "user_lists_cache";
    private static final String KEY_PREFIX = "lists_";
    private static final String TIME_PREFIX = "time_";

    private static final JsonCache<List<UserListsGraphQL.UserList>> CACHE =
            new JsonCache<>(PREFS,
                    lists -> {
                        JSONArray arr = new JSONArray();
                        for (UserListsGraphQL.UserList list : lists) {
                            JSONObject o = new JSONObject();
                            o.put("id", list.id);
                            o.put("name", list.name);
                            o.put("description", list.description);
                            JSONArray repoArr = new JSONArray();
                            for (UserListsGraphQL.Repo repo : list.repos) {
                                JSONObject r = new JSONObject();
                                r.put("fullName", repo.fullName);
                                r.put("description", repo.description);
                                repoArr.put(r);
                            }
                            o.put("repos", repoArr);
                            arr.put(o);
                        }
                        return arr.toString();
                    },
                    json -> {
                        List<UserListsGraphQL.UserList> lists = new ArrayList<>();
                        JSONArray arr = new JSONArray(json);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            List<UserListsGraphQL.Repo> repos = new ArrayList<>();
                            JSONArray repoArr = o.optJSONArray("repos");
                            if (repoArr != null) {
                                for (int j = 0; j < repoArr.length(); j++) {
                                    JSONObject r = repoArr.optJSONObject(j);
                                    if (r == null) {
                                        continue;
                                    }
                                    String fullName = r.optString("fullName", "");
                                    if (!fullName.isEmpty()) {
                                        repos.add(new UserListsGraphQL.Repo(fullName,
                                                r.optString("description", "")));
                                    }
                                }
                            }
                            lists.add(new UserListsGraphQL.UserList(
                                    o.optString("id", ""),
                                    o.optString("name", ""),
                                    o.optString("description", ""),
                                    repos));
                        }
                        return lists;
                    });

    public static List<UserListsGraphQL.UserList> load(Context context, String login) {
        if (context == null || login == null) {
            return null;
        }
        return CACHE.load(context, KEY_PREFIX + login);
    }

    public static void save(Context context, String login,
            List<UserListsGraphQL.UserList> lists) {
        if (context == null || login == null || lists == null) {
            return;
        }
        CACHE.save(context, KEY_PREFIX + login, TIME_PREFIX + login, lists);
    }

    /** Age of the cached data in millis, or -1 if none. */
    public static long age(Context context, String login) {
        if (context == null || login == null) {
            return -1;
        }
        return CACHE.age(context, TIME_PREFIX + login);
    }
}
