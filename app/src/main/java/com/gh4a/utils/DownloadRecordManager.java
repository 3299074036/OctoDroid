package com.gh4a.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a local history of files downloaded through DownloadUtils
 * (release assets, source zips, …), newest first, capped at 100 entries.
 */
public class DownloadRecordManager {
    private static final String PREFS_NAME = "download_records";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_RECORDS = 100;

    public static class Record {
        public final long downloadId;
        public final String fileName;
        public final String url;
        public final String description;
        public final long time;

        public Record(long downloadId, String fileName, String url,
                String description, long time) {
            this.downloadId = downloadId;
            this.fileName = fileName;
            this.url = url;
            this.description = description;
            this.time = time;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void record(Context context, long downloadId, String fileName,
            String url, String description) {
        List<Record> records = getRecords(context);
        records.add(0, new Record(downloadId, fileName, url, description,
                System.currentTimeMillis()));
        while (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
        save(context, records);
    }

    public static List<Record> getRecords(Context context) {
        List<Record> records = new ArrayList<>();
        String json = prefs(context).getString(KEY_RECORDS, null);
        if (json == null) {
            return records;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                records.add(new Record(
                        o.optLong("id"),
                        o.optString("name"),
                        o.optString("url"),
                        o.optString("desc"),
                        o.optLong("time")));
            }
        } catch (JSONException ignored) {
        }
        return records;
    }

    public static void remove(Context context, long downloadId) {
        List<Record> records = getRecords(context);
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).downloadId == downloadId) {
                records.remove(i);
            }
        }
        save(context, records);
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_RECORDS).apply();
    }

    private static void save(Context context, List<Record> records) {
        JSONArray array = new JSONArray();
        try {
            for (Record r : records) {
                JSONObject o = new JSONObject();
                o.put("id", r.downloadId);
                o.put("name", r.fileName);
                o.put("url", r.url);
                o.put("desc", r.description);
                o.put("time", r.time);
                array.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs(context).edit().putString(KEY_RECORDS, array.toString()).apply();
    }
}
