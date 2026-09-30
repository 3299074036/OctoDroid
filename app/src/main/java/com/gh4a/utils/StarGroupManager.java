package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gh4a.Gh4Application;

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
 */
public class StarGroupManager {
    private static final String PREFS_NAME = "star_groups";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_MEMBERS = "members";

    public static class StarRepo {
        public final String fullName;
        public final String description;

        StarRepo(String fullName, String description) {
            this.fullName = fullName;
            this.description = description;
        }
    }

    private static SharedPreferences prefsFor(Context context) {
        String login = Gh4Application.get().getAuthLogin();
        return context.getSharedPreferences(PREFS_NAME + "_" + login, Context.MODE_PRIVATE);
    }

    @NonNull
    public static List<String> getGroups(Context context) {
        List<String> groups = new ArrayList<>();
        String json = prefsFor(context).getString(KEY_GROUPS, null);
        if (json == null) {
            return groups;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                groups.add(array.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return groups;
    }

    public static boolean createGroup(Context context, String name) {
        name = name.trim();
        if (name.isEmpty()) {
            return false;
        }
        List<String> groups = getGroups(context);
        if (groups.contains(name)) {
            return false;
        }
        groups.add(name);
        saveGroups(context, groups);
        return true;
    }

    public static boolean renameGroup(Context context, String oldName, String newName) {
        newName = newName.trim();
        if (newName.isEmpty()) {
            return false;
        }
        List<String> groups = getGroups(context);
        int index = groups.indexOf(oldName);
        if (index < 0 || groups.contains(newName)) {
            return false;
        }
        groups.set(index, newName);
        saveGroups(context, groups);

        JSONObject members = getMembersJson(context);
        try {
            if (members.has(oldName)) {
                members.put(newName, members.remove(oldName));
                saveMembers(context, members);
            }
        } catch (JSONException ignored) {
        }
        return true;
    }

    public static void deleteGroup(Context context, String name) {
        List<String> groups = getGroups(context);
        groups.remove(name);
        saveGroups(context, groups);

        JSONObject members = getMembersJson(context);
        members.remove(name);
        saveMembers(context, members);
    }

    @NonNull
    public static List<StarRepo> getRepos(Context context, String group) {
        List<StarRepo> repos = new ArrayList<>();
        JSONObject members = getMembersJson(context);
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
    public static void assignRepo(Context context, String group, String fullName, String description) {
        JSONObject members = getMembersJson(context);
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
        saveMembers(context, members);
    }

    public static void removeRepo(Context context, String group, String fullName) {
        JSONObject members = getMembersJson(context);
        JSONArray array = members.optJSONArray(group);
        if (array == null) {
            return;
        }
        for (int i = array.length() - 1; i >= 0; i--) {
            JSONObject obj = array.optJSONObject(i);
            if (obj != null && fullName.equals(obj.optString("fullName"))) {
                array.remove(i);
            }
        }
        saveMembers(context, members);
    }

    @Nullable
    public static String findGroupFor(Context context, String fullName) {
        JSONObject members = getMembersJson(context);
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

    private static void saveGroups(Context context, List<String> groups) {
        JSONArray array = new JSONArray();
        for (String g : groups) {
            array.put(g);
        }
        prefsFor(context).edit().putString(KEY_GROUPS, array.toString()).apply();
    }

    private static JSONObject getMembersJson(Context context) {
        String json = prefsFor(context).getString(KEY_MEMBERS, null);
        if (json == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void saveMembers(Context context, JSONObject members) {
        prefsFor(context).edit().putString(KEY_MEMBERS, members.toString()).apply();
    }
}
