package com.gh4a.utils;

import com.gh4a.model.NotificationListLoadResult;

import io.reactivex.Single;

/**
 * P1：未读通知计数的统一入口（Caching Proxy + 单飞）。
 *
 * 问题：冷启动进“我”页时，HomeActivity（setupBottomNav + onResume）与
 * MeFragment（onCreateView + onResume）各调一次
 * {@code SingleFactory.getNotifications(false, false, false)}，即 4 次
 * 未读通知全量分页拉取；通知 API 的 ETag 失效 + max-age 2 秒，几乎每次
 * 都是真网络请求。4 个调用点各自持有自己的拉取逻辑与缓存键。
 *
 * 本类持有：TTL 内的缓存结果 + 在途请求的单飞复用。同一 TTL 窗口内
 * 的多次调用只发一次网络请求；在途请求未完成时后来者共享同一个
 * Single（{@link Single#cache()}），不重复拉取。
 *
 * 失效点（缺一不可，否则角标会 stale）：
 * <ul>
 *   <li>标记已读后：{@link com.gh4a.fragment.NotificationListFragment}
 *       的 markAsRead 成功回调调 {@link #invalidate()}——否则回 Home
 *       时 onResume 拿到的是读之前的缓存，角标清不掉。</li>
 *   <li>切换账号后：{@link com.gh4a.Gh4Application#setActiveLogin(String)}
 *       调 {@link #invalidate()}——缓存属于旧账号，单例跨账号切换存活。</li>
 * </ul>
 *
 * 不接 Worker：后台 Worker（15 分钟一轮）要的是新鲜数据来推进
 * hasUnseen/hasNew 状态，复用 ≤2 分钟的旧结果可能漏掉刚到的通知；
 * 且 Worker 节奏下缓存几乎总是冷的，省不下请求。Worker 继续直调
 * SingleFactory（权限短路见 53/54）。
 */
public final class NotificationCountProvider {
    /** 缓存有效期 2 分钟（报告 P1：1–2 分钟 TTL）。 */
    private static final long TTL_MS = 2 * 60 * 1000L;

    private static volatile NotificationCountProvider sInstance;

    private NotificationListLoadResult mCachedResult;
    private long mCachedAt;
    private Single<NotificationListLoadResult> mInFlight;
    /** 代数：invalidate 后在途的旧请求回来时不得写缓存（读之前的数据）。 */
    private int mGeneration;

    private NotificationCountProvider() {
    }

    public static NotificationCountProvider getInstance() {
        NotificationCountProvider instance = sInstance;
        if (instance == null) {
            synchronized (NotificationCountProvider.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new NotificationCountProvider();
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    /**
     * 未读通知全量结果。TTL 内直接给缓存；在途请求未完成时共享同一
     * 次拉取；否则发起一次新拉取（成功后写缓存，失败不写）。
     * 调用方仍按原样订阅（Home 走 loader，Me 走 doInBackground）。
     */
    public synchronized Single<NotificationListLoadResult> getNotifications() {
        long now = System.currentTimeMillis();
        if (mCachedResult != null && now - mCachedAt < TTL_MS) {
            return Single.just(mCachedResult);
        }
        if (mInFlight != null) {
            return mInFlight;
        }
        final int generation = mGeneration;
        mInFlight = SingleFactory.getNotifications(false, false, false)
                .doOnSuccess(result -> {
                    synchronized (NotificationCountProvider.this) {
                        // 只有代数未变才写缓存：在途期间 invalidate 过，
                        // 说明这份结果是标记已读/切账号之前拉的，已 stale。
                        if (generation == mGeneration) {
                            mCachedResult = result;
                            mCachedAt = System.currentTimeMillis();
                        }
                        mInFlight = null;
                    }
                })
                .doOnError(error -> {
                    synchronized (NotificationCountProvider.this) {
                        mInFlight = null;
                    }
                })
                .cache();
        return mInFlight;
    }

    /** 标记已读 / 切换账号后调用：丢弃缓存，下次取数重新拉取。 */
    public synchronized void invalidate() {
        mGeneration++;
        mCachedResult = null;
        mCachedAt = 0;
        // 在途请求不取消：它回来后因代数已变不会写缓存。
    }
}
