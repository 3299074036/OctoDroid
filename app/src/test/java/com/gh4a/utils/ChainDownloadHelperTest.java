package com.gh4a.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A 组：ChainDownloadHelper 走系统 DownloadManager，走 Robolectric
 *（ShadowDownloadManager，不发真实请求、不写真实用户文件）。
 * 失败回调都是同步的；成功链路用 shadow DM 入队后发完成广播驱动。
 */
@RunWith(RobolectricTestRunner.class)
public class ChainDownloadHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DownloadRecordManager.clear(context);
    }

    private void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static class CapturingCallback implements ChainDownloadHelper.Callback {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile boolean success = true;
        volatile File file;
        volatile long downloadId = -2;

        @Override
        public void onDownloadComplete(Context ctx, boolean success, File file,
                long downloadId) {
            this.success = success;
            this.file = file;
            this.downloadId = downloadId;
            latch.countDown();
        }
    }

    @Test
    public void enqueueChain_nullUrlEmptyChainFailsImmediately() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, null, "f.apk", "d", null, cb);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
        assertEquals(null, cb.file);
    }

    @Test
    public void enqueueChain_emptyUrlFailsImmediately() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "", "f.apk", "d", null, cb);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
    }

    @Test
    public void enqueueChain_nullFileNameFailsImmediately() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                null, "d", null, cb);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
    }

    @Test
    public void enqueueChain_pathTraversalFileNameIsSanitizedToBaseName() {
        // "../evil.apk" 不被拒：sanitizeFileName 先剥目录段再校验，
        // 得 "evil.apk" 后正常入队，文件落应用私有下载目录，不越界。
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "../evil.apk", "d", null, cb);
        assertEquals(1L, cb.latch.getCount());
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals("evil.apk",
                DownloadRecordManager.getRecords(context).get(0).fileName);
    }

    @Test
    public void enqueueChain_dotDotFileNameFailsImmediately() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "a..b.apk", "d", null, cb);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
    }

    @Test
    public void enqueueChain_validUrlEnqueuesAndWritesRecord() {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "f.apk", "desc", null, cb);
        // 入队成功：回调尚未触发（等下载完成广播，latch 仍为 1），但记录已写
        assertEquals(1L, cb.latch.getCount());
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals("f.apk",
                DownloadRecordManager.getRecords(context).get(0).fileName);
        // ShadowDownloadManager 的 id 计数器从 -1 起，首次入队得 0；
        // 有效 id 的约定是非负（-1 是失败回调的哨兵）。
        assertTrue(DownloadRecordManager.getRecords(context).get(0).downloadId >= 0);
    }

    @Test
    public void enqueueChain_completionBroadcastWithWrongIdIsIgnored() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "f.apk", "desc", null, cb);
        long realId = DownloadRecordManager.getRecords(context).get(0).downloadId;
        Intent wrong = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        wrong.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, realId + 9999);
        context.sendBroadcast(wrong);
        idleMainLooper();
        assertEquals(1L, cb.latch.getCount());
    }

    @Test
    public void enqueueChain_completionBroadcastResolvesToFailureWithoutFile()
            throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "f.apk", "desc", null, cb);
        long realId = DownloadRecordManager.getRecords(context).get(0).downloadId;
        Intent done = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        done.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, realId);
        context.sendBroadcast(done);
        idleMainLooper();
        // shadow DownloadManager 没有真实文件：resolveResult 判失败，
        // 单链路无 rest，直接失败回调（不抛异常、不重试到死循环）
        assertTrue(cb.latch.await(5, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(realId, cb.downloadId);
        assertEquals(null, cb.file);
    }

    @Test
    public void enqueueChain_customizerIsInvoked() {
        AtomicReference<Boolean> customized = new AtomicReference<>(false);
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "f.apk", "desc",
                request -> {
                    request.setDescription("custom-desc");
                    customized.set(true);
                },
                new CapturingCallback());
        assertTrue(customized.get());
    }
}
