package com.gh4a.utils;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Environment;
import android.os.Looper;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // ---- reflective helpers: private methods / static state ----

    private static Method chainMethod(String name, Class<?>... params) throws Exception {
        Method m = ChainDownloadHelper.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static void invokeChainMethod(Method m, Object... args) throws Exception {
        try {
            m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static void callEnqueueNext(Context ctx, List<String> urls, String fileName,
            String description, ChainDownloadHelper.RequestCustomizer customizer,
            ChainDownloadHelper.Callback callback, boolean usePublicDir) throws Exception {
        invokeChainMethod(chainMethod("enqueueNext", Context.class, List.class,
                String.class, String.class, ChainDownloadHelper.RequestCustomizer.class,
                ChainDownloadHelper.Callback.class, boolean.class),
                ctx, urls, fileName, description, customizer, callback, usePublicDir);
    }

    private static void callPruneStaleReceivers(Context ctx, DownloadManager dm)
            throws Exception {
        invokeChainMethod(chainMethod("pruneStaleReceivers", Context.class,
                DownloadManager.class), ctx, dm);
    }

    private static void callResolveResult(Context ctx, DownloadManager dm, long downloadId,
            List<String> rest, String fileName, String description,
            ChainDownloadHelper.RequestCustomizer customizer,
            ChainDownloadHelper.Callback callback, boolean usePublicDir)
            throws Exception {
        invokeChainMethod(chainMethod("resolveResult", Context.class,
                DownloadManager.class, long.class, List.class, String.class, String.class,
                ChainDownloadHelper.RequestCustomizer.class,
                ChainDownloadHelper.Callback.class, boolean.class),
                ctx, dm, downloadId, rest, fileName, description, customizer, callback,
                usePublicDir);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> pendingReceivers() throws Exception {
        Field f = ChainDownloadHelper.class.getDeclaredField("sPendingReceivers");
        f.setAccessible(true);
        return (List<Object>) f.get(null);
    }

    private static long pendingDownloadId(Object pending) throws Exception {
        Field f = pending.getClass().getDeclaredField("downloadId");
        f.setAccessible(true);
        return f.getLong(pending);
    }

    private static boolean pendingListHasId(long id) throws Exception {
        for (Object p : pendingReceivers()) {
            if (pendingDownloadId(p) == id) {
                return true;
            }
        }
        return false;
    }

    private static Object seedPendingReceiver(BroadcastReceiver receiver, long downloadId)
            throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.utils.ChainDownloadHelper$PendingReceiver");
        Constructor<?> ctor = clazz.getDeclaredConstructor(BroadcastReceiver.class, long.class);
        ctor.setAccessible(true);
        Object pending = ctor.newInstance(receiver, downloadId);
        pendingReceivers().add(pending);
        return pending;
    }

    private static void removePendingEntry(Object pending) throws Exception {
        pendingReceivers().remove(pending);
    }

    private static void clearPendingReceivers() throws Exception {
        pendingReceivers().clear();
    }

    private static Context noDownloadManagerContext(Context base) {
        return new ContextWrapper(base) {
            @Override
            public Object getSystemService(String name) {
                if (Context.DOWNLOAD_SERVICE.equals(name)) {
                    return null;
                }
                return super.getSystemService(name);
            }
        };
    }

    private static Context noExternalFilesDirContext(Context base) {
        return new ContextWrapper(base) {
            @Override
            public File getExternalFilesDir(String type) {
                return null;
            }
        };
    }

    private static Context downloadManagerContext(Context base, DownloadManager dm) {
        return new ContextWrapper(base) {
            @Override
            public Object getSystemService(String name) {
                if (Context.DOWNLOAD_SERVICE.equals(name)) {
                    return dm;
                }
                return super.getSystemService(name);
            }
        };
    }

    private static DownloadManager mockDmWithCursor(Cursor cursor) {
        DownloadManager mockDm = mock(DownloadManager.class);
        when(mockDm.query(any(DownloadManager.Query.class))).thenReturn(cursor);
        return mockDm;
    }

    @Test
    public void defaultConstructor_instantiates() {
        assertNotNull(new ChainDownloadHelper());
    }

    @Test
    public void enqueueNext_nullUrls_failsImmediately() throws Exception {
        // 77 的 urls == null 半边：公开入口永远传非 null（空链），
        // 只能反射直调 enqueueNext 覆盖这道防御性守卫。
        CapturingCallback cb = new CapturingCallback();
        callEnqueueNext(context, null, "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
        assertNull(cb.file);
    }

    @Test
    public void enqueueNext_nullDownloadManager_failsImmediately() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        callEnqueueNext(noDownloadManagerContext(context),
                Collections.singletonList("https://example.com/f.apk"),
                "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
        assertEquals(context.getString(R.string.download_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueNext_nullExternalFilesDir_failsImmediately() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        callEnqueueNext(noExternalFilesDirContext(context),
                Collections.singletonList("https://example.com/f.apk"),
                "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
    }

    @Test
    public void enqueueNext_enqueueThrows_failsImmediately() throws Exception {
        clearPendingReceivers();
        DownloadManager mockDm = mock(DownloadManager.class);
        when(mockDm.enqueue(any(DownloadManager.Request.class)))
                .thenThrow(new IllegalArgumentException("no dm"));
        CapturingCallback cb = new CapturingCallback();
        callEnqueueNext(downloadManagerContext(context, mockDm),
                Collections.singletonList("https://example.com/f.apk"),
                "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(-1L, cb.downloadId);
    }

    @Test
    public void pruneStaleReceivers_goneReceiverUnregisteredAndRemoved() throws Exception {
        clearPendingReceivers();
        // Receiver that was registered and then unregistered: the second
        // unregisterReceiver throws IllegalArgumentException (the catch).
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
            }
        };
        context.registerReceiver(receiver, new IntentFilter("test.prune.action"));
        context.unregisterReceiver(receiver);
        Object seeded = seedPendingReceiver(receiver, 99999L);
        try {
            Cursor cursor = mock(Cursor.class);
            when(cursor.moveToFirst()).thenReturn(false);
            callPruneStaleReceivers(context, mockDmWithCursor(cursor));
            assertFalse(pendingListHasId(99999L));
        } finally {
            removePendingEntry(seeded);
        }
    }

    @Test
    public void pruneStaleReceivers_goneReceiverCallsUnregister() throws Exception {
        // Behavioral proof that the unregisterReceiver call (line 177)
        // executes: a mock Context records the call directly.
        clearPendingReceivers();
        BroadcastReceiver receiver = mock(BroadcastReceiver.class);
        Object seeded = seedPendingReceiver(receiver, 99999L);
        try {
            Cursor cursor = mock(Cursor.class);
            when(cursor.moveToFirst()).thenReturn(false);
            Context mockContext = mock(Context.class);
            callPruneStaleReceivers(mockContext, mockDmWithCursor(cursor));
            Mockito.verify(mockContext).unregisterReceiver(receiver);
            assertFalse(pendingListHasId(99999L));
        } finally {
            removePendingEntry(seeded);
        }
    }

    @Test
    public void pruneStaleReceivers_liveReceiverKeptOnNextEnqueue() throws Exception {
        // The second enqueue prunes while the first download is still live
        // in the DownloadManager: moveToFirst() true -> gone false -> kept.
        clearPendingReceivers();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/a.apk",
                "a.apk", "d", null, new CapturingCallback());
        long firstId = DownloadRecordManager.getRecords(context).get(0).downloadId;
        assertTrue(pendingListHasId(firstId));
        ChainDownloadHelper.enqueueChain(context, "https://example.com/b.apk",
                "b.apk", "d", null, new CapturingCallback());
        assertTrue(pendingListHasId(firstId));
        assertEquals(2, pendingReceivers().size());
    }

    @Test
    public void pruneStaleReceivers_queryThrows_leavesReceiversAlone() throws Exception {
        clearPendingReceivers();
        Object seeded = seedPendingReceiver(mock(BroadcastReceiver.class), 99999L);
        try {
            DownloadManager mockDm = mock(DownloadManager.class);
            when(mockDm.query(any(DownloadManager.Query.class)))
                    .thenThrow(new RuntimeException("dm down"));
            callPruneStaleReceivers(context, mockDm);
            assertTrue(pendingListHasId(99999L));
        } finally {
            removePendingEntry(seeded);
        }
    }

    @Test
    public void removePendingReceiver_nonMatchingEntryKept() throws Exception {
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context, "https://example.com/f.apk",
                "f.apk", "desc", null, cb);
        long realId = DownloadRecordManager.getRecords(context).get(0).downloadId;
        // Seed after the enqueue so prune (which runs inside enqueueNext)
        // cannot touch it before the broadcast.
        Object seeded = seedPendingReceiver(mock(BroadcastReceiver.class), 99999L);
        try {
            Intent done = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            done.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, realId);
            context.sendBroadcast(done);
            idleMainLooper();
            assertTrue(cb.latch.await(5, TimeUnit.SECONDS));
            assertFalse(cb.success);
            // The matching entry was removed, the seeded non-matching one kept.
            assertTrue(pendingListHasId(99999L));
            assertFalse(pendingListHasId(realId));
        } finally {
            removePendingEntry(seeded);
        }
    }

    @Test
    public void resolveResult_emptyCursor_reportsFailure() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(cursor.moveToFirst()).thenReturn(false);
        CapturingCallback cb = new CapturingCallback();
        callResolveResult(context, mockDmWithCursor(cursor), 12345L,
                Collections.emptyList(), "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(12345L, cb.downloadId);
        assertNull(cb.file);
    }

    @Test
    public void resolveResult_successfulStatusNullLocalUri_reportsFailure() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getInt(anyInt())).thenReturn(DownloadManager.STATUS_SUCCESSFUL);
        // getString defaults to null: successful status but no local uri.
        CapturingCallback cb = new CapturingCallback();
        callResolveResult(context, mockDmWithCursor(cursor), 12345L,
                Collections.emptyList(), "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(12345L, cb.downloadId);
    }

    @Test
    public void resolveResult_successfulStatusMissingFile_reportsFailure() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getInt(anyInt())).thenReturn(DownloadManager.STATUS_SUCCESSFUL);
        when(cursor.getString(anyInt())).thenReturn("file:///nonexistent-dir-xyz/f.apk");
        CapturingCallback cb = new CapturingCallback();
        callResolveResult(context, mockDmWithCursor(cursor), 12345L,
                Collections.emptyList(), "f.apk", "d", null, cb, false);
        assertTrue(cb.latch.await(2, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertEquals(12345L, cb.downloadId);
    }

    @Test
    public void enqueueChain_firstLinkFailsRetriesNextLink() throws Exception {
        // GitHub URL: buildFallbackChain gives [direct, preset mirror]
        // (at least 2 links), so the first failure must retry.
        CapturingCallback cb = new CapturingCallback();
        ChainDownloadHelper.enqueueChain(context,
                "https://github.com/o/r/releases/download/v1/f.apk",
                "f.apk", "desc", null, cb);
        Set<Long> seenIds = new HashSet<>();
        for (int i = 0; i < 10 && cb.latch.getCount() == 1; i++) {
            assertFalse(DownloadRecordManager.getRecords(context).isEmpty());
            long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
            seenIds.add(id);
            Intent done = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            done.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id);
            context.sendBroadcast(done);
            idleMainLooper();
        }
        assertTrue(cb.latch.await(5, TimeUnit.SECONDS));
        assertFalse(cb.success);
        assertTrue("expected at least one retry, saw ids " + seenIds, seenIds.size() >= 2);
        assertEquals(context.getString(R.string.download_retry_alt),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueChain_publicDir_usesPublicDownloadsDestination() throws Exception {
        clearPendingReceivers();
        CapturingCallback cb = new CapturingCallback();
        try {
            ChainDownloadHelper.enqueueChain(context, "https://example.com/a.apk",
                    "a.apk", "d", null, cb, true);
            assertEquals(1L, cb.latch.getCount());
            long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
            DownloadManager dm = (DownloadManager) context.getSystemService(
                    Context.DOWNLOAD_SERVICE);
            ShadowDownloadManager.ShadowRequest req =
                    Shadows.shadowOf(Shadows.shadowOf(dm).getRequest(id));
            String publicDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String destPath = req.getDestination().getPath();
            assertTrue(destPath, destPath.startsWith(publicDir));
            // Robolectric 下公共目录与私有目录共享 external-files 根，
            // 但私有目录必含 Android/data/<包名> 一段：不在其中即为公共落盘。
            assertFalse(destPath, destPath.contains("Android/data"));
        } finally {
            clearPendingReceivers();
        }
    }

    @Test
    public void enqueueNext_publicDir_skipsExternalFilesDirCheck() throws Exception {
        clearPendingReceivers();
        DownloadManager mockDm = mock(DownloadManager.class);
        when(mockDm.enqueue(any(DownloadManager.Request.class))).thenReturn(42L);
        CapturingCallback cb = new CapturingCallback();
        try {
            callEnqueueNext(downloadManagerContext(
                    noExternalFilesDirContext(context), mockDm),
                    Collections.singletonList("https://example.com/f.apk"),
                    "f.apk", "d", null, cb, true);
            // 公共目录不依赖 getExternalFilesDir：入队照常发生，不立即失败。
            Mockito.verify(mockDm).enqueue(any(DownloadManager.Request.class));
            assertEquals(1L, cb.latch.getCount());
        } finally {
            clearPendingReceivers();
        }
    }
}
