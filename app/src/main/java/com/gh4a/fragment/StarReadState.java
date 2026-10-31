package com.gh4a.fragment;

import android.content.Context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 0.1.20 D-01: Star 更新已读状态——此前是 RadarCache 里约 50 行“根本不是缓存”
 * 的部分（getReadKeys/markRead/markAllRead/unreadCount），现迁出为独立类。
 * 存储不变：偏好文件 star_update_read，按登录分键 read_&lt;login&gt;（与原先
 * 完全相同的键，旧已读状态无缝沿用）；RadarCache 的同名方法改为委托，
 * 旧调用方不受影响。
 */
public class StarReadState {
    private static final String READ_PREFS = "star_update_read";
    private static final String READ_KEY_PREFIX = "read_";

    public static Set<String> getReadKeys(Context context, String login) {
        Set<String> keys = context.getSharedPreferences(READ_PREFS, Context.MODE_PRIVATE)
                .getStringSet(READ_KEY_PREFIX + login, null);
        return keys == null ? new HashSet<>() : new HashSet<>(keys);
    }

    public static void markRead(Context context, String login, String key) {
        Set<String> keys = getReadKeys(context, login);
        keys.add(key);
        context.getSharedPreferences(READ_PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(READ_KEY_PREFIX + login, keys).apply();
    }

    public static void markAllRead(Context context, String login,
            List<ReleaseRadarFragment.RadarItem> items) {
        if (items == null) return;
        Set<String> keys = getReadKeys(context, login);
        for (ReleaseRadarFragment.RadarItem item : items) {
            keys.add(item.owner + "/" + item.repo + "#" + item.releaseId);
        }
        context.getSharedPreferences(READ_PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(READ_KEY_PREFIX + login, keys).apply();
    }

    public static int unreadCount(List<ReleaseRadarFragment.RadarItem> items,
            Set<String> readKeys) {
        if (items == null) return 0;
        int count = 0;
        for (ReleaseRadarFragment.RadarItem item : items) {
            if (readKeys == null
                    || !readKeys.contains(item.owner + "/" + item.repo + "#" + item.releaseId)) {
                count++;
            }
        }
        return count;
    }
}
