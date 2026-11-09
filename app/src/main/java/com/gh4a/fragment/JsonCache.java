package com.gh4a.fragment;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 0.1.20 D-01: 四份 SharedPreferences JSON 缓存（HotReposCache、MeHeaderCache、
 * UserListsCache、RadarCache）的共用骨架。原先每份约 40–50 行同款代码：
 * 取 SharedPreferences、org.json 编解码、catch (Exception) → null、apply() 写
 * 时间戳。现收敛到这里，DTO 字段映射仍各留本类（toJson/fromJson lambda）。
 *
 * 键策略不变（全局热门键是故意的，趋势数据不分用户；其余三者按登录分键），
 * 不存在键碰撞：每个缓存实例绑定自己的 prefs 文件，键名由调用方传入。
 */
public class JsonCache<T> {
    /** DTO → JSON 字符串（调用方写字段映射，异常由 save() 吞掉，与原先一致）。 */
    public interface ToJson<T> {
        String apply(T value) throws Exception;
    }

    /** JSON 字符串 → DTO（返回 null 表示无可用缓存，与原先 load() 的语义一致）。 */
    public interface FromJson<T> {
        T apply(String json) throws Exception;
    }

    private final String mPrefsName;
    private final ToJson<T> mToJson;
    private final FromJson<T> mFromJson;

    public JsonCache(String prefsName, ToJson<T> toJson, FromJson<T> fromJson) {
        mPrefsName = prefsName;
        mToJson = toJson;
        mFromJson = fromJson;
    }

    /** 读缓存：无记录或解析失败返回 null（调用方按 null = 无缓存处理）。 */
    public T load(Context context, String key) {
        if (context == null || key == null) {
            return null;
        }
        String json = context.getSharedPreferences(mPrefsName, Context.MODE_PRIVATE)
                .getString(key, null);
        if (json == null) {
            return null;
        }
        try {
            return mFromJson.apply(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** 写缓存并打时间戳（timeKey 为 null 时不写时间戳）。 */
    public void save(Context context, String key, String timeKey, T value) {
        if (context == null || key == null || value == null) {
            return;
        }
        try {
            SharedPreferences.Editor editor = context
                    .getSharedPreferences(mPrefsName, Context.MODE_PRIVATE).edit()
                    .putString(key, mToJson.apply(value));
            if (timeKey != null) {
                editor.putLong(timeKey, System.currentTimeMillis());
            }
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    /** 缓存年龄（毫秒），无时间戳返回 -1。 */
    public long age(Context context, String timeKey) {
        if (context == null || timeKey == null) {
            return -1;
        }
        long t = context.getSharedPreferences(mPrefsName, Context.MODE_PRIVATE)
                .getLong(timeKey, -1);
        return t < 0 ? -1 : System.currentTimeMillis() - t;
    }

    /** 删除指定键（登出清理用）。 */
    public void clear(Context context, String... keys) {
        if (context == null || keys == null) {
            return;
        }
        SharedPreferences.Editor editor = context
                .getSharedPreferences(mPrefsName, Context.MODE_PRIVATE).edit();
        for (String key : keys) {
            editor.remove(key);
        }
        editor.apply();
    }
}
