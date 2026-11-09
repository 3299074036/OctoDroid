package com.gh4a.utils;

import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;

/**
 * D-E5：裸 {@code new Thread(...)} 的收敛点。
 *
 * <p>此前约 12 处各写一遍 {@code new Thread(() -> {...}).start()}：无命名、
 * 异常静默死亡（未捕获的异常直接杀进程，且 log 里无上下文）、无统一日志。
 * 这里用一个共享缓存线程池跑全部一次性后台任务：线程按任务命名
 * （{@code Background-<name>}，logcat 可直接定位任务），顶层统一
 * try/catch 记日志。调用方原有的内部 try/catch 全部保留，行为不变。
 *
 * <p>注意：线程池是无界缓存池，与原先裸线程的并发语义一致（任务之间不
 * 互相排队）；空闲线程 60 秒后回收，不长期占资源。
 */
public class Background {
    private static final String TAG = "Background";

    private static final ExecutorService sExecutor =
            Executors.newCachedThreadPool(new ThreadFactory() {
                private final AtomicInteger mCount = new AtomicInteger(1);

                @Override
                public Thread newThread(@NonNull Runnable r) {
                    return new Thread(r, "Background-" + mCount.getAndIncrement());
                }
            });

    private Background() {
    }

    /**
     * 在共享后台线程池里跑一个一次性任务。
     *
     * @param name 任务名，进线程名与失败日志，用于定位
     * @param task 任务本体；抛出的任何异常（含 Error）都会记一条
     *             {@link Log#e}，不再像裸线程那样直接杀进程
     */
    public static void run(@NonNull String name, @NonNull Runnable task) {
        sExecutor.execute(() -> {
            Thread.currentThread().setName("Background-" + name);
            try {
                task.run();
            } catch (Throwable t) {
                Log.e(TAG, "Background task '" + name + "' failed", t);
            }
        });
    }
}
