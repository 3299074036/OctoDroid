package com.gh4a.utils;

import android.content.Context;

import com.gh4a.Gh4Application;
import com.gh4a.fragment.JsonCache;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a local (per-account) history of recently viewed repositories,
 * issues and pull requests. Newest first, capped at MAX_ITEMS entries.
 * 去重-1（0.2.4）：手写 JSON 编解码收敛到 JsonCache<List<Entry>>。
 * 文件名按登录动态拼（recent_history_<login>），JsonCache 实例绑死
 * 一个文件名，故不能用静态实例——编解码 lambda 静态共用，实例按调用
 * 构造；文件名、键名、字段名与原先一字不差，损坏文件仍返回已解析的
 * 部分条目（fromJson 按条容错）。
 */
public class RecentHistoryManager {
    public static final int TYPE_REPO = 0;
    public static final int TYPE_ISSUE = 1;
    public static final int TYPE_PR = 2;

    private static final String PREFS_NAME = "recent_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 50;

    private static final JsonCache.ToJson<List<Entry>> TO_JSON = entries -> {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            JSONObject obj = new JSONObject();
            obj.put("type", e.type);
            obj.put("owner", e.owner);
            obj.put("repo", e.repo);
            obj.put("number", e.number);
            obj.put("time", e.time);
            array.put(obj);
        }
        return array.toString();
    };

    private static final JsonCache.FromJson<List<Entry>> FROM_JSON = json -> {
        List<Entry> entries = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                entries.add(new Entry(
                        obj.getInt("type"),
                        obj.getString("owner"),
                        obj.getString("repo"),
                        obj.optInt("number", 0),
                        obj.getLong("time")));
            } catch (Exception ignored) {
                // 单条损坏跳过，其余照收（与原先一致）。
            }
        }
        return entries;
    };

    // 252：无有效登录时 getAuthLogin() 返回 null，文件名会成字面量
    // recent_history_null——无登录态的浏览记录落入与任何账号无关的共享
    // 文件，登录后成孤儿、既不可见也不清理。login 为 null 时返回 null，
    // 调用方直接不记录（record 提前 return，getEntries 返回空列表）。
    @Nullable
    private static JsonCache<List<Entry>> cacheFor(Context context) {
        String login = Gh4Application.get().getAuthLogin();
        if (login == null) {
            return null;
        }
        return new JsonCache<>(PREFS_NAME + "_" + login, TO_JSON, FROM_JSON);
    }

    public static class Entry {
        public final int type;
        public final String owner;
        public final String repo;
        public final int number; // issue/PR number, 0 for repos
        public final long time;

        Entry(int type, String owner, String repo, int number, long time) {
            this.type = type;
            this.owner = owner;
            this.repo = repo;
            this.number = number;
            this.time = time;
        }

        public String key() {
            return type + "/" + owner + "/" + repo + "/" + number;
        }
    }

    public static void recordRepo(Context context, String owner, String repo) {
        record(context, new Entry(TYPE_REPO, owner, repo, 0, System.currentTimeMillis()));
    }

    public static void recordIssue(Context context, String owner, String repo, int number) {
        record(context, new Entry(TYPE_ISSUE, owner, repo, number, System.currentTimeMillis()));
    }

    public static void recordPr(Context context, String owner, String repo, int number) {
        record(context, new Entry(TYPE_PR, owner, repo, number, System.currentTimeMillis()));
    }

    private static void record(Context context, Entry entry) {
        JsonCache<List<Entry>> cache = cacheFor(context);
        if (cache == null) {
            return;
        }
        List<Entry> entries = getEntries(context);
        // Dedupe: move existing entry to front instead of duplicating
        // (also covers activity recreation on rotation)
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(entry.key())) {
                entries.remove(i);
                break;
            }
        }
        entries.add(0, entry);
        while (entries.size() > MAX_ITEMS) {
            entries.remove(entries.size() - 1);
        }
        saveEntries(context, entries);
    }

    @NonNull
    public static List<Entry> getEntries(Context context) {
        JsonCache<List<Entry>> cache = cacheFor(context);
        if (cache == null) {
            return new ArrayList<>();
        }
        List<Entry> entries = cache.load(context, KEY_ITEMS);
        return entries != null ? entries : new ArrayList<>();
    }

    public static void clear(Context context) {
        JsonCache<List<Entry>> cache = cacheFor(context);
        if (cache == null) {
            return;
        }
        cache.clear(context, KEY_ITEMS);
    }

    private static void saveEntries(Context context, List<Entry> entries) {
        JsonCache<List<Entry>> cache = cacheFor(context);
        if (cache == null) {
            return;
        }
        cache.save(context, KEY_ITEMS, null, entries);
    }
}
