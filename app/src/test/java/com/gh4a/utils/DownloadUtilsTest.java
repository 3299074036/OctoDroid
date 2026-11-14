package com.gh4a.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Looper;

import com.gh4a.BaseActivity;
import com.meisolsson.githubsdk.model.Download;
import com.meisolsson.githubsdk.model.ReleaseAsset;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNetworkInfo;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A 组：DownloadUtils 的公开入口是 enqueueDownloadWithPermissionCheck
 *（需 BaseActivity，用空子类 + Robolectric 建）与 verifyDownloadedFile
 *（后台算 SHA-256 + 主线程回调，轮询等完成）。
 * ReleaseAsset 的有效路径会走 OkHttp 真实解析重定向（发真实请求），
 * 只测它的 null 守卫，不走有效路径。
 */
@RunWith(RobolectricTestRunner.class)
public class DownloadUtilsTest {

    /** BaseActivity 无抽象方法，空子类即可建实例。 */
    public static class TestActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // BaseActivity.onStart 要求 content container 至少一个子 view，
            // 否则抛 "Content view must be initialized before"。
            setContentView(new android.view.View(this));
        }
    }

    private Context context;
    private TestActivity activity;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DownloadRecordManager.clear(context);
        // 隔离各测试的活动网络：Robolectric 默认活动网络是移动网络
        //（默认即 metered），不重置会被带 metered 的测试污染。
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Shadows.shadowOf(cm).setActiveNetworkInfo(null);
    }

    private TestActivity activity() {
        if (activity == null) {
            activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        }
        return activity;
    }

    private void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** 等 latch，同时轮询 idle 主线程（verify 的回调经主线程 Handler.post）。 */
    private boolean awaitCallback(CountDownLatch latch, long timeoutMs)
            throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            idleMainLooper();
            latch.await(100, TimeUnit.MILLISECONDS);
        }
        idleMainLooper();
        return latch.getCount() == 0;
    }

    private void waitForRecord(long downloadId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            idleMainLooper();
            for (DownloadRecordManager.Record r
                    : DownloadRecordManager.getRecords(context)) {
                if (r.downloadId == downloadId && r.size > 0) {
                    return;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("record " + downloadId
                + " was not updated within " + timeoutMs + "ms");
    }

    private void waitForNotVerifying(long downloadId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            idleMainLooper();
            if (!DownloadRecordManager.isVerifying(downloadId)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("record " + downloadId
                + " still verifying after " + timeoutMs + "ms");
    }

    private void waitForRecordGone(long downloadId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            idleMainLooper();
            boolean found = false;
            for (DownloadRecordManager.Record r
                    : DownloadRecordManager.getRecords(context)) {
                if (r.downloadId == downloadId) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("record " + downloadId
                + " was not removed within " + timeoutMs + "ms");
    }

    @Test
    public void downloadNeedsWarning_noActiveNetworkNeedsNoWarning() {
        assertFalse(DownloadUtils.downloadNeedsWarning(context));
    }

    @Test
    public void downloadNeedsWarning_wifiNeedsNoWarning() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo wifi = ShadowNetworkInfo.newInstance(
                android.net.NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI, 0, true, true);
        Shadows.shadowOf(cm).setActiveNetworkInfo(wifi);
        assertFalse(DownloadUtils.downloadNeedsWarning(context));
    }

    @Test
    public void downloadNeedsWarning_meteredNetworkNeedsWarning() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        // ShadowConnectivityManager.isActiveNetworkMetered() 为真当且仅当
        // 活动网络类型为 TYPE_MOBILE（无 setActiveNetworkMetered，用
        // ShadowNetworkInfo 注入一条移动网络）。
        android.net.NetworkInfo mobile = ShadowNetworkInfo.newInstance(
                android.net.NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE, 0, true, true);
        Shadows.shadowOf(cm).setActiveNetworkInfo(mobile);
        assertTrue(DownloadUtils.downloadNeedsWarning(context));
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_downloadNullUrlReturnsEarly() {
        Download download = mock(Download.class);
        when(download.htmlUrl()).thenReturn(null);
        when(download.name()).thenReturn("f.apk");
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(), download);
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_downloadNullNameReturnsEarly() {
        Download download = mock(Download.class);
        when(download.htmlUrl()).thenReturn("https://example.com/f.apk");
        when(download.name()).thenReturn(null);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(), download);
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_downloadValidEnqueuesChain() {
        Download download = mock(Download.class);
        when(download.htmlUrl()).thenReturn("https://example.com/f.apk");
        when(download.name()).thenReturn("f.apk");
        when(download.contentType()).thenReturn("application/octet-stream");
        when(download.description()).thenReturn("desc");
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(), download);
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals("f.apk",
                DownloadRecordManager.getRecords(context).get(0).fileName);
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_assetNullUrlReturnsEarly() {
        ReleaseAsset asset = mock(ReleaseAsset.class);
        when(asset.url()).thenReturn(null);
        when(asset.name()).thenReturn("a.apk");
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(), asset);
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_assetNullNameReturnsEarly() {
        ReleaseAsset asset = mock(ReleaseAsset.class);
        when(asset.url()).thenReturn("https://example.com/a.apk");
        when(asset.name()).thenReturn(null);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(), asset);
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_stringNullUrlIsNoop() {
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                null, "application/octet-stream", "f.apk", "desc");
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_stringValidUrlEnqueuesChain() {
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/f.apk", "application/octet-stream",
                "f.apk", "desc");
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void verifyDownloadedFile_plainFileRecordsSha256AndSize() throws Exception {
        File file = new File(context.getFilesDir(), "hello.txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("abc");
        }
        DownloadRecordManager.record(context, 42L, "hello.txt", "u", "d");
        DownloadUtils.verifyDownloadedFile(context, file, 42L, false);
        waitForRecord(42L, 5000);
        // 生产代码先在后台线程写 size/sha256，再 post 到主线程清 isVerifying；
        // 只等到 size>0 就断言会竞态失败，须等到 flag 清掉。
        waitForNotVerifying(42L, 5000);
        DownloadRecordManager.Record r = null;
        for (DownloadRecordManager.Record cand
                : DownloadRecordManager.getRecords(context)) {
            if (cand.downloadId == 42L) {
                r = cand;
                break;
            }
        }
        assertNotNull(r);
        assertEquals(3L, r.size);
        // SHA-256("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                r.sha256);
        assertFalse(DownloadRecordManager.isVerifying(42L));
    }

    @Test
    public void verifyDownloadedFile_callbackReceivesNullCheckForNonApk()
            throws Exception {
        File file = new File(context.getFilesDir(), "notes.txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("hello");
        }
        DownloadRecordManager.record(context, 43L, "notes.txt", "u", "d");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApkUpdateDownloader.ApkSignatureCheck> check =
                new AtomicReference<>();
        DownloadUtils.verifyDownloadedFile(context, file, 43L, false,
                c -> {
                    check.set(c);
                    latch.countDown();
                });
        assertTrue(awaitCallback(latch, 5000));
        assertNull(check.get());
        assertTrue(file.exists());
    }

    @Test
    public void verifyDownloadedFile_fakeApkWithoutCallbackDeletesFileAndRecord()
            throws Exception {
        File file = new File(context.getFilesDir(), "fake.apk");
        try (FileWriter w = new FileWriter(file)) {
            w.write("not a real apk");
        }
        DownloadRecordManager.record(context, 44L, "fake.apk", "u", "d");
        DownloadUtils.verifyDownloadedFile(context, file, 44L, false);
        waitForRecordGone(44L, 5000);
        assertFalse(file.exists());
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void verifyDownloadedFile_fakeApkWithCallbackReportsMismatch()
            throws Exception {
        File file = new File(context.getFilesDir(), "fake2.apk");
        try (FileWriter w = new FileWriter(file)) {
            w.write("not a real apk");
        }
        DownloadRecordManager.record(context, 45L, "fake2.apk", "u", "d");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApkUpdateDownloader.ApkSignatureCheck> check =
                new AtomicReference<>();
        DownloadUtils.verifyDownloadedFile(context, file, 45L, false,
                c -> {
                    check.set(c);
                    latch.countDown();
                });
        assertTrue(awaitCallback(latch, 5000));
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH, check.get());
        // 回调分支不做自己的处置：文件与记录都保留
        assertTrue(file.exists());
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void verifyDownloadedFile_fakeApkWithExpectedPackageReportsMismatch()
            throws Exception {
        File file = new File(context.getFilesDir(), "fake3.apk");
        try (FileWriter w = new FileWriter(file)) {
            w.write("not a real apk");
        }
        DownloadRecordManager.record(context, 46L, "fake3.apk", "u", "d");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApkUpdateDownloader.ApkSignatureCheck> check =
                new AtomicReference<>();
        DownloadUtils.verifyDownloadedFile(context, file, 46L, false,
                c -> {
                    check.set(c);
                    latch.countDown();
                }, "com.gh4a");
        assertTrue(awaitCallback(latch, 5000));
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MISMATCH, check.get());
    }
}
