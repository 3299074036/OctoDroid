package com.gh4a.utils;

import android.content.Context;

import com.gh4a.fragment.JsonCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps a local history of files downloaded through DownloadUtils
 * (release assets, source zips, …), newest first, capped at 100 entries.
 * 去重-1（0.2.4）：手写 JSON 编解码收敛到 JsonCache<List<Record>>——
 * 文件名、键名、字段名与原先一字不差；损坏文件仍返回已解析的部分条目
 * （fromJson 按条容错，与原先 try 包整个循环的语义一致）。
 */
public class DownloadRecordManager {
    private static final String PREFS_NAME = "download_records";
    private static final String KEY_RECORDS = "records";
    private static final int MAX_RECORDS = 100;

    private static final JsonCache<List<Record>> CACHE =
            new JsonCache<>(PREFS_NAME,
                    records -> {
                        JSONArray array = new JSONArray();
                        for (Record r : records) {
                            JSONObject o = new JSONObject();
                            o.put("id", r.downloadId);
                            o.put("name", r.fileName);
                            o.put("url", r.url);
                            o.put("desc", r.description);
                            o.put("time", r.time);
                            o.put("size", r.size);
                            if (r.sha256 != null) {
                                o.put("sha256", r.sha256);
                            }
                            array.put(o);
                        }
                        return array.toString();
                    },
                    json -> {
                        List<Record> records = new ArrayList<>();
                        JSONArray array = new JSONArray(json);
                        for (int i = 0; i < array.length(); i++) {
                            try {
                                JSONObject o = array.getJSONObject(i);
                                records.add(new Record(
                                        o.optLong("id"),
                                        o.optString("name"),
                                        o.optString("url"),
                                        o.optString("desc"),
                                        o.optLong("time"),
                                        o.optLong("size"),
                                        o.has("sha256") ? o.optString("sha256", null) : null));
                            } catch (Exception ignored) {
                                // 单条损坏跳过，其余照收（与原先一致）。
                            }
                        }
                        return records;
                    });

    /**
     * 57：正在校验中的下载 id（仅内存，不持久化——校验线程与本进程同生死，
     * 进程死即无校验在跑，不会留下永远打不开的脏记录）。
     */
    private static final Set<Long> sVerifyingIds =
            Collections.synchronizedSet(new HashSet<>());

    /** 57：标记某条记录正在校验（下载列表点开前据此拦截）。 */
    public static void setVerifying(long downloadId, boolean verifying) {
        if (verifying) {
            sVerifyingIds.add(downloadId);
        } else {
            sVerifyingIds.remove(downloadId);
        }
    }

    /** 57：某条记录是否正在校验（签名还没比对完）。 */
    public static boolean isVerifying(long downloadId) {
        return sVerifyingIds.contains(downloadId);
    }

    public static class Record {
        public final long downloadId;
        public final String fileName;
        public final String url;
        public final String description;
        public final long time;
        /** 下载完成后的文件大小（字节）；未知为 0。 */
        public final long size;
        /** 下载完成后的 SHA-256（小写 hex）；未知为 null。 */
        public final String sha256;

        public Record(long downloadId, String fileName, String url,
                String description, long time) {
            this(downloadId, fileName, url, description, time, 0, null);
        }

        public Record(long downloadId, String fileName, String url,
                String description, long time, long size, String sha256) {
            this.downloadId = downloadId;
            this.fileName = fileName;
            this.url = url;
            this.description = description;
            this.time = time;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /**
     * 250：record/updateFileInfo/remove 均为 getRecords() 全量读 → 内存改 →
     * save() 全量写，JsonCache 不做同步；两个下载的校验可并发执行（见
     * DownloadUtils.verifyDownloadedFile 的 Background 线程），后 save
     * 者覆盖前者，补记的大小/SHA-256 静默丢失。读改写三步走同一把锁。
     */
    private static final Object sRecordsLock = new Object();

    public static void record(Context context, long downloadId, String fileName,
            String url, String description) {
        synchronized (sRecordsLock) {
            List<Record> records = getRecords(context);
            records.add(0, new Record(downloadId, fileName, url, description,
                    System.currentTimeMillis()));
            while (records.size() > MAX_RECORDS) {
                records.remove(records.size() - 1);
            }
            save(context, records);
        }
    }

    /** 下载完成后补记文件大小与 SHA-256（供下载列表展示、人工核对）。 */
    public static void updateFileInfo(Context context, long downloadId,
            long size, String sha256) {
        synchronized (sRecordsLock) {
            List<Record> records = getRecords(context);
            boolean changed = false;
            for (int i = 0; i < records.size(); i++) {
                Record r = records.get(i);
                if (r.downloadId == downloadId) {
                    records.set(i, new Record(r.downloadId, r.fileName, r.url,
                            r.description, r.time, size, sha256));
                    changed = true;
                    break;
                }
            }
            if (changed) {
                save(context, records);
            }
        }
    }

    public static List<Record> getRecords(Context context) {
        List<Record> records = CACHE.load(context, KEY_RECORDS);
        return records != null ? records : new ArrayList<>();
    }

    public static void remove(Context context, long downloadId) {
        synchronized (sRecordsLock) {
            List<Record> records = getRecords(context);
            for (int i = records.size() - 1; i >= 0; i--) {
                if (records.get(i).downloadId == downloadId) {
                    records.remove(i);
                }
            }
            save(context, records);
        }
    }

    public static void clear(Context context) {
        CACHE.clear(context, KEY_RECORDS);
    }

    private static void save(Context context, List<Record> records) {
        CACHE.save(context, KEY_RECORDS, null, records);
    }
}
