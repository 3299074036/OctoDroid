package com.gh4a.fragment;

import android.content.Context;

import org.json.JSONObject;

/**
 * 0.1.10：我页头部缓存。按登录账号分键存取上次拉到的用户信息头
 * （头像、名字、登录名、加入时间、3 格计数），进我页先秒出缓存，后台
 * 拉到新数据后再替换；失败时保留缓存，不清空头部。
 * 0.1.20 D-01: 骨架收敛到 JsonCache，本类只留字段映射与公开 API。
 */
public class MeHeaderCache {
    private static final String PREFS = "me_header_cache";
    private static final String KEY_PREFIX = "header_";
    private static final String TIME_PREFIX = "time_";
    /** 0.1.20 F-32: 头缓存有效期 7 天——此前永不过期，只有成功拉取才替换。 */
    public static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;

    public static class Header {
        public final String login;
        public final String name;
        public final long userId;
        public final String avatarUrl;
        public final long joinedMillis;
        public final int repos;
        public final int followers;
        public final int following;

        public Header(String login, String name, long userId, String avatarUrl,
                long joinedMillis, int repos, int followers, int following) {
            this.login = login;
            this.name = name;
            this.userId = userId;
            this.avatarUrl = avatarUrl;
            this.joinedMillis = joinedMillis;
            this.repos = repos;
            this.followers = followers;
            this.following = following;
        }
    }

    private static final JsonCache<Header> CACHE = new JsonCache<>(PREFS,
            header -> {
                JSONObject o = new JSONObject();
                o.put("login", header.login);
                o.put("name", header.name);
                o.put("userId", header.userId);
                o.put("avatarUrl", header.avatarUrl);
                o.put("joinedMillis", header.joinedMillis);
                o.put("repos", header.repos);
                o.put("followers", header.followers);
                o.put("following", header.following);
                return o.toString();
            },
            json -> {
                JSONObject o = new JSONObject(json);
                return new Header(
                        o.optString("login", ""),
                        o.optString("name", ""),
                        o.optLong("userId", 0),
                        o.optString("avatarUrl", ""),
                        o.optLong("joinedMillis", -1),
                        o.optInt("repos", 0),
                        o.optInt("followers", 0),
                        o.optInt("following", 0));
            });

    public static Header load(Context context, String login) {
        if (context == null || login == null) {
            return null;
        }
        // 0.1.20 F-32: 过期缓存视为无缓存（调用方走网络重拉），不展示陈旧头部。
        // 无时间戳的旧缓存（F-32 前写入）不过期判定，仍可读。
        long age = CACHE.age(context, TIME_PREFIX + login);
        if (age >= 0 && age >= TTL_MS) {
            return null;
        }
        Header header = CACHE.load(context, KEY_PREFIX + login);
        if (header == null) {
            return null;
        }
        // 原先 optString("login", login) 的回落语义：JSON 缺 login 字段时以键为准。
        if (header.login == null || header.login.isEmpty()) {
            return new Header(login, header.name, header.userId, header.avatarUrl,
                    header.joinedMillis, header.repos, header.followers, header.following);
        }
        return header;
    }

    /** 0.1.20 F-32: 登出/删号时清除该登录的头缓存（含时间戳），不留磁盘残留。 */
    public static void clear(Context context, String login) {
        if (context == null || login == null) {
            return;
        }
        CACHE.clear(context, KEY_PREFIX + login, TIME_PREFIX + login);
    }

    public static void save(Context context, Header header) {
        if (context == null || header == null || header.login == null) {
            return;
        }
        CACHE.save(context, KEY_PREFIX + header.login, TIME_PREFIX + header.login, header);
    }
}
