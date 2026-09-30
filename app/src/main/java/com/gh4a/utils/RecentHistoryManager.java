package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.gh4a.Gh4Application;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a local (per-account) history of recently viewed repositories,
 * issues and pull requests. Newest first, capped at MAX_ITEMS entries.
 */
public class RecentHistoryManager {
    public static final int TYPE_REPO = 0;
    public static final int TYPE_ISSUE = 1;
    public static final int TYPE_PR = 2;

    private static final String PREFS_NAME = "recent_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 50;

    private static SharedPreferences prefsFor(Context context) {
        String login = Gh4Application.get().getAuthLogin();
        return context.getSharedPreferences(PREFS_NAME + "_" + login, Context.MODE_PRIVATE);
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
        List<Entry> entries = new ArrayList<>();
        SharedPreferences prefs = prefsFor(context);
        String json = prefs.getString(KEY_ITEMS, null);
        if (json == null) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                entries.add(new Entry(
                        obj.getInt("type"),
                        obj.getString("owner"),
                        obj.getString("repo"),
                        obj.optInt("number", 0),
                        obj.getLong("time")));
            }
        } catch (JSONException e) {
            // Corrupted history; start fresh
        }
        return entries;
    }

    public static void clear(Context context) {
        prefsFor(context).edit().remove(KEY_ITEMS).apply();
    }

    private static void saveEntries(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", e.type);
                obj.put("owner", e.owner);
                obj.put("repo", e.repo);
                obj.put("number", e.number);
                obj.put("time", e.time);
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefsFor(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }
}
