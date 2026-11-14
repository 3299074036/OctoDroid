package com.gh4a.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A 组：Background 跑共享线程池；失败路径调 android.util.Log，走 Robolectric
 *（纯 JVM 下 Log 是 stub，会抛异常，无法验证“异常被吞掉、线程池继续工作”）。
 */
@RunWith(RobolectricTestRunner.class)
public class BackgroundTest {

    @Test
    public void run_executesTask() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Background.run("test-exec", latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void run_threadNameContainsTaskName() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Background.run("my-task", () -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("Background-my-task", threadName.get());
    }

    @Test
    public void run_taskNotOnCallingThread() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Background.run("other-thread", () -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(!Thread.currentThread().getName().equals(threadName.get()));
    }

    @Test
    public void run_throwingTaskDoesNotKillPool() throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        Background.run("thrower", () -> {
            try {
                throw new RuntimeException("boom");
            } finally {
                firstDone.countDown();
            }
        });
        assertTrue(firstDone.await(5, TimeUnit.SECONDS));
        // 异常被顶层 catch 记日志后，线程池仍能跑下一个任务
        CountDownLatch secondDone = new CountDownLatch(1);
        Background.run("after-thrower", secondDone::countDown);
        assertTrue(secondDone.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void run_throwingErrorIsCaughtToo() throws Exception {
        CountDownLatch firstDone = new CountDownLatch(1);
        Background.run("error-thrower", () -> {
            try {
                throw new AssertionError("fatal?");
            } finally {
                firstDone.countDown();
            }
        });
        assertTrue(firstDone.await(5, TimeUnit.SECONDS));
        CountDownLatch secondDone = new CountDownLatch(1);
        Background.run("after-error", secondDone::countDown);
        assertTrue(secondDone.await(5, TimeUnit.SECONDS));
    }
}
