package com.gh4a.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.1.20 Observer 角标 holder（小规模，非事件总线）：只持两个角标数——
 * 未读通知数与未读 Star 更新数。HomeActivity 订阅它并统一落到底栏；
 * 各页（我页、Star 页）只向它发布/失效计数，不再直接碰 BottomNavigationView。
 * Home 自身的 loader 刷新（updateMeBadge、updateStarBadgeFromCache）同样
 * 走发布路径，角标只有一个写入点（Home 的订阅回调）。
 */
public class BadgeState {
    /** 角标数变化时回调（HomeActivity 的订阅者在主线程落到底栏）。 */
    public interface Listener {
        void onBadgeCountsChanged(int notificationCount, int starUpdateCount);
    }

    private int mNotificationCount;
    private int mStarUpdateCount;
    private final List<Listener> mListeners = new ArrayList<>();

    public void addListener(Listener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            // 新订阅者立即拿到当前计数（底栏重建后订阅时角标不丢）。
            listener.onBadgeCountsChanged(mNotificationCount, mStarUpdateCount);
        }
    }

    /** 发布新的未读通知数（不变时不通知，避免无谓刷新）。 */
    public void setNotificationCount(int count) {
        if (mNotificationCount != count) {
            mNotificationCount = count;
            notifyListeners();
        }
    }

    /** 发布新的未读 Star 更新数（不变时不通知）。 */
    public void setStarUpdateCount(int count) {
        if (mStarUpdateCount != count) {
            mStarUpdateCount = count;
            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(mListeners)) {
            listener.onBadgeCountsChanged(mNotificationCount, mStarUpdateCount);
        }
    }
}
