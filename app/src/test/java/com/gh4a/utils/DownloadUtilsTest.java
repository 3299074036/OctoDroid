package com.gh4a.utils;

import android.Manifest;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageStats;
import android.content.pm.Signature;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.worker.NotificationPermission;
import com.meisolsson.githubsdk.model.Download;
import com.meisolsson.githubsdk.model.ReleaseAsset;
import com.meisolsson.githubsdk.model.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowToast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    // ---------- B 组：重定向解析（mock sRedirectClient）+ 对话框 + 链路完成 + 通知 ----------

    private OkHttpClient mockClient;

    private static void setRedirectClient(OkHttpClient client) throws Exception {
        Field f = DownloadUtils.class.getDeclaredField("sRedirectClient");
        f.setAccessible(true);
        f.set(null, client);
    }

    private static OkHttpClient getRedirectClientReflectively() throws Exception {
        Method m = DownloadUtils.class.getDeclaredMethod("getRedirectClient");
        m.setAccessible(true);
        return (OkHttpClient) m.invoke(null);
    }

    private static String getMimeTypeReflectively(String fileName) throws Exception {
        Method m = DownloadUtils.class.getDeclaredMethod("getMimeTypeForFile", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, fileName);
    }

    private static Signature sig(byte... bytes) {
        return new Signature(bytes);
    }

    private static PackageInfo packageInfo(String packageName, Signature[] sigs) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.signatures = sigs;
        return info;
    }

    private void grantPostNotifications() {
        android.app.Application app = ApplicationProvider.getApplicationContext();
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void setMobileNetwork() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        android.net.NetworkInfo mobile = ShadowNetworkInfo.newInstance(
                android.net.NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_MOBILE, 0, true, true);
        Shadows.shadowOf(cm).setActiveNetworkInfo(mobile);
    }

    private ReleaseAsset mockAsset(String url, String name) {
        ReleaseAsset asset = mock(ReleaseAsset.class);
        when(asset.url()).thenReturn(url);
        when(asset.name()).thenReturn(name);
        when(asset.label()).thenReturn("label");
        when(asset.contentType()).thenReturn("application/octet-stream");
        return asset;
    }

    private Call mockCallReturning(Response response) throws IOException {
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        return call;
    }

    private Response mockRedirectResponse(String location) {
        Response r = mock(Response.class);
        when(r.isSuccessful()).thenReturn(false);
        when(r.isRedirect()).thenReturn(true);
        when(r.header("Location")).thenReturn(location);
        return r;
    }

    private Response mockOkResponse() {
        Response r = mock(Response.class);
        when(r.isSuccessful()).thenReturn(true);
        when(r.isRedirect()).thenReturn(false);
        return r;
    }

    private Response mockErrorResponse(int code) {
        Response r = mock(Response.class);
        when(r.isSuccessful()).thenReturn(false);
        when(r.isRedirect()).thenReturn(false);
        when(r.code()).thenReturn(code);
        return r;
    }

    private Callback captureCallback(Call mockCall) {
        ArgumentCaptor<Callback> captor = ArgumentCaptor.forClass(Callback.class);
        verify(mockCall).enqueue(captor.capture());
        return captor.getValue();
    }

    private Request captureRequest() {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(captor.capture());
        return captor.getValue();
    }

    private void broadcastComplete(long downloadId) {
        Intent done = new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        done.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        context.sendBroadcast(done);
        idleMainLooper();
    }

    private ShadowDownloadManager.ShadowRequest shadowRequest(long downloadId) {
        DownloadManager dm = (DownloadManager) context.getSystemService(
                Context.DOWNLOAD_SERVICE);
        return Shadows.shadowOf(Shadows.shadowOf(dm).getRequest(downloadId));
    }

    private void seedSignature(File file, String pkg, Signature signature) {
        ShadowPackageManager spm = Shadows.shadowOf(context.getPackageManager());
        spm.setPackageArchiveInfo(file.getAbsolutePath(),
                packageInfo(pkg, new Signature[]{signature}));
        PackageInfo installed = packageInfo(pkg, new Signature[]{signature});
        spm.addPackage(installed);
        spm.addPackage(installed, new PackageStats(pkg));
    }

    private int notificationCount() {
        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        return Shadows.shadowOf(nm).getAllNotifications().size();
    }

    @Before
    public void setUpRedirect() throws Exception {
        mockClient = mock(OkHttpClient.class);
        setRedirectClient(mockClient);
    }

    @After
    public void tearDownRedirect() throws Exception {
        setRedirectClient(null);
    }

    @Test
    public void getRedirectClient_createsAndCaches() throws Exception {
        setRedirectClient(null);
        OkHttpClient first = getRedirectClientReflectively();
        assertNotNull(first);
        assertTrue(first == getRedirectClientReflectively());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_assetValidEnqueuesViaRedirect()
            throws Exception {
        Call call = mockCallReturning(mockRedirectResponse("https://cdn.example.com/a.apk"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Request request = captureRequest();
        assertEquals("application/octet-stream", request.header("Accept"));
        assertNull(request.header("Authorization"));
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockRedirectResponse("https://cdn.example.com/a.apk"));
        idleMainLooper();
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals("a.apk",
                DownloadRecordManager.getRecords(context).get(0).fileName);
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_assetValidEnqueuesToPublicDownloads()
            throws Exception {
        // 发布页附件落公共 Downloads/：用户在系统下载里找得到、点开即装。
        Call call = mockCallReturning(mockRedirectResponse("https://cdn.example.com/a.apk"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockRedirectResponse("https://cdn.example.com/a.apk"));
        idleMainLooper();
        long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
        ShadowDownloadManager.ShadowRequest req = shadowRequest(id);
        String publicDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        String destPath = req.getDestination().getPath();
        assertTrue(destPath, destPath.startsWith(publicDir));
        assertFalse(destPath, destPath.contains("Android/data"));
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_stringUrlEnqueuesToPrivateDir()
            throws Exception {
        // 非附件下载保持现状：落应用私有目录。
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/notes.txt", "text/plain", "notes.txt", "desc");
        long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
        ShadowDownloadManager.ShadowRequest req = shadowRequest(id);
        assertTrue(req.getDestination().toString(),
                req.getDestination().toString().contains("Android/data"));
    }

    @Test
    public void enqueueDownloadResolvingRedirect_withTokenSendsAuthorization()
            throws Exception {
        Gh4Application.get().addAccount(
                User.builder().login("tester").id(999L).build(), "fake-token");
        try {
            Call call = mockCallReturning(mockOkResponse());
            when(mockClient.newCall(any(Request.class))).thenReturn(call);
            DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                    "https://api.github.com/repos/o/r/tarball/1",
                    "application/octet-stream", "src.tar.gz", "desc", true);
            Request request = captureRequest();
            assertEquals("Token fake-token", request.header("Authorization"));
            assertNull(request.header("Accept"));
        } finally {
            Gh4Application.get().logout();
        }
    }

    @Test
    public void enqueueDownloadResolvingRedirect_nonRedirect200UsesRequestUrl()
            throws Exception {
        Request built = new Request.Builder()
                .url("https://api.github.com/repos/o/r/tarball/1").build();
        Call call = mockCallReturning(mockOkResponse());
        when(call.request()).thenReturn(built);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://api.github.com/repos/o/r/tarball/1",
                "application/octet-stream", "src.tar.gz", "desc", true);
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockOkResponse());
        idleMainLooper();
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals("src.tar.gz",
                DownloadRecordManager.getRecords(context).get(0).fileName);
    }

    @Test
    public void enqueueDownloadResolvingRedirect_errorResponseShowsToast()
            throws Exception {
        Call call = mockCallReturning(mockErrorResponse(404));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockErrorResponse(404));
        idleMainLooper();
        assertEquals(context.getString(com.gh4a.R.string.download_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_failureShowsToast() throws Exception {
        Call call = mock(Call.class);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onFailure(call, new IOException("boom"));
        idleMainLooper();
        assertEquals(context.getString(com.gh4a.R.string.download_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_finishingActivitySkipsComplete()
            throws Exception {
        TestActivity finishing = Robolectric.buildActivity(TestActivity.class).setup().get();
        Call call = mockCallReturning(mockRedirectResponse("https://cdn.example.com/a.apk"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(finishing,
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockRedirectResponse("https://cdn.example.com/a.apk"));
        finishing.finish();
        idleMainLooper();
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_destroyedActivitySkipsFailureToast()
            throws Exception {
        TestActivity destroyed = Robolectric.buildActivity(TestActivity.class)
                .setup().destroy().get();
        Call call = mock(Call.class);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(destroyed,
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onFailure(call, new IOException("boom"));
        idleMainLooper();
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_destroyedActivitySkipsComplete()
            throws Exception {
        TestActivity destroyed = Robolectric.buildActivity(TestActivity.class)
                .setup().destroy().get();
        Call call = mockCallReturning(mockRedirectResponse("https://cdn.example.com/a.apk"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(destroyed,
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockRedirectResponse("https://cdn.example.com/a.apk"));
        idleMainLooper();
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_appContextSkipsActivityGuardComplete()
            throws Exception {
        Method m = DownloadUtils.class.getDeclaredMethod(
                "enqueueDownloadResolvingRedirect", Context.class, String.class,
                String.class, String.class, String.class, String.class, boolean.class);
        m.setAccessible(true);
        Call call = mockCallReturning(mockRedirectResponse("https://cdn.example.com/a.apk"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        m.invoke(null, context, "https://api.github.com/repos/o/r/releases/assets/1",
                "a.apk", "desc", "application/octet-stream", null, false);
        Callback cb = captureCallback(call);
        cb.onResponse(call, mockRedirectResponse("https://cdn.example.com/a.apk"));
        idleMainLooper();
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_appContextSkipsActivityGuardFailure()
            throws Exception {
        Method m = DownloadUtils.class.getDeclaredMethod(
                "enqueueDownloadResolvingRedirect", Context.class, String.class,
                String.class, String.class, String.class, String.class, boolean.class);
        m.setAccessible(true);
        Call call = mock(Call.class);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        m.invoke(null, context, "https://api.github.com/repos/o/r/releases/assets/1",
                "a.apk", "desc", "application/octet-stream", null, false);
        Callback cb = captureCallback(call);
        cb.onFailure(call, new IOException("boom"));
        idleMainLooper();
        assertEquals(context.getString(com.gh4a.R.string.download_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_finishingActivitySkipsFailureToast()
            throws Exception {
        TestActivity finishing = Robolectric.buildActivity(TestActivity.class).setup().get();
        Call call = mock(Call.class);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(finishing,
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        cb.onFailure(call, new IOException("boom"));
        finishing.finish();
        idleMainLooper();
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_responseCloseThrowingStillCompletes()
            throws Exception {
        Response redirect = mockRedirectResponse("https://cdn.example.com/a.apk");
        org.mockito.Mockito.doThrow(new IOException("close boom"))
                .when(redirect).close();
        Call call = mockCallReturning(redirect);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        try {
            cb.onResponse(call, redirect);
            org.junit.Assert.fail("expected close() to throw");
        } catch (IOException e) {
            assertEquals("close boom", e.getMessage());
        }
        idleMainLooper();
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_bodyThrowingStillClosesResponse()
            throws Exception {
        Response bad = mock(Response.class);
        when(bad.isSuccessful()).thenThrow(new RuntimeException("body boom"));
        Call call = mockCallReturning(bad);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        try {
            cb.onResponse(call, bad);
            org.junit.Assert.fail("expected body to throw");
        } catch (RuntimeException e) {
            assertEquals("body boom", e.getMessage());
        }
        idleMainLooper();
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadResolvingRedirect_nullResponsePropagatesNpe() throws Exception {
        Call call = mockCallReturning(mockRedirectResponse("https://cdn.example.com/a.apk"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        try {
            cb.onResponse(call, null);
            org.junit.Assert.fail("expected NPE on null response");
        } catch (NullPointerException expected) {
            // OkHttp never passes a null Response; the try-with-resources
            // null-check on the resource is only reachable this way.
        }
    }

    @Test
    public void enqueueDownloadResolvingRedirect_bodyAndCloseBothThrowingSuppresses()
            throws Exception {
        Response bad = mock(Response.class);
        when(bad.isSuccessful()).thenThrow(new RuntimeException("body boom"));
        org.mockito.Mockito.doThrow(new IOException("close boom"))
                .when(bad).close();
        Call call = mockCallReturning(bad);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                mockAsset("https://api.github.com/repos/o/r/releases/assets/1", "a.apk"));
        Callback cb = captureCallback(call);
        try {
            cb.onResponse(call, bad);
            org.junit.Assert.fail("expected body to throw");
        } catch (RuntimeException e) {
            assertEquals("body boom", e.getMessage());
            assertEquals(1, e.getSuppressed().length);
            assertEquals("close boom", e.getSuppressed()[0].getMessage());
        }
    }

    @Test
    public void enqueueDownload_mobileWarningPositiveDownloadsNow() {
        setMobileNetwork();
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/f.apk", "application/octet-stream", "f.apk", "desc");
        List<android.app.Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertEquals(1, dialogs.size());
        ((AlertDialog) dialogs.get(0)).getButton(DialogInterface.BUTTON_POSITIVE)
                .performClick();
        idleMainLooper();
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void enqueueDownload_mobileWarningNeutralDownloadsWifiOnly() {
        setMobileNetwork();
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/f.apk", "application/octet-stream", "f.apk", "desc");
        List<android.app.Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertEquals(1, dialogs.size());
        ((AlertDialog) dialogs.get(0)).getButton(DialogInterface.BUTTON_NEUTRAL)
                .performClick();
        idleMainLooper();
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void enqueueDownload_mobileWarningNegativeCancels() {
        setMobileNetwork();
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/f.apk", "application/octet-stream", "f.apk", "desc");
        List<android.app.Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertEquals(1, dialogs.size());
        ((AlertDialog) dialogs.get(0)).getButton(DialogInterface.BUTTON_NEGATIVE)
                .performClick();
        idleMainLooper();
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void enqueueDownloadWithPermissionCheck_nullMimeTypeSkipsSetMimeType() {
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/f.apk", null, "f.apk", "desc");
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void enqueueChainDownload_failureShowsToast() throws Exception {
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/f.apk", "application/octet-stream", "f.apk", "desc");
        String failed = context.getString(com.gh4a.R.string.download_failed);
        for (int i = 0; i < 5; i++) {
            List<DownloadRecordManager.Record> records =
                    DownloadRecordManager.getRecords(context);
            if (records.isEmpty()) {
                break;
            }
            broadcastComplete(records.get(0).downloadId);
            if (failed.equals(ShadowToast.getTextOfLatestToast())) {
                break;
            }
            Thread.sleep(100);
        }
        assertEquals(failed, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void enqueueChainDownload_successWithNonApkNotifies() throws Exception {
        grantPostNotifications();
        DownloadUtils.enqueueDownloadWithPermissionCheck(activity(),
                "https://example.com/notes.txt", "text/plain", "notes.txt", "desc");
        long id = DownloadRecordManager.getRecords(context).get(0).downloadId;
        ShadowDownloadManager.ShadowRequest req = shadowRequest(id);
        req.setStatus(DownloadManager.STATUS_SUCCESSFUL);
        File dest = new File(req.getDestination().getPath());
        dest.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(dest)) {
            w.write("hello");
        }
        Uri fakeUri = Uri.parse("content://" + context.getPackageName()
                + ".fileprovider/private_download/notes.txt");
        try (MockedStatic<FileProvider> mocked = Mockito.mockStatic(FileProvider.class)) {
            mocked.when(() -> FileProvider.getUriForFile(any(Context.class),
                    anyString(), any(File.class))).thenReturn(fakeUri);
            broadcastComplete(id);
            waitForNotVerifying(id, 5000);
            idleMainLooper();
        }
        assertEquals(1, notificationCount());
        DownloadRecordManager.Record r = null;
        for (DownloadRecordManager.Record cand
                : DownloadRecordManager.getRecords(context)) {
            if (cand.downloadId == id) {
                r = cand;
                break;
            }
        }
        assertNotNull(r);
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                r.sha256);
    }

    @Test
    public void verifyDownloadedFile_matchWithCallbackNotifies() throws Exception {
        grantPostNotifications();
        File file = new File(context.getFilesDir(), "real.apk");
        try (FileWriter w = new FileWriter(file)) {
            w.write("x");
        }
        seedSignature(file, context.getPackageName(), sig((byte) 1, (byte) 2, (byte) 3));
        DownloadRecordManager.record(context, 51L, "real.apk", "u", "d");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApkUpdateDownloader.ApkSignatureCheck> check =
                new AtomicReference<>();
        Uri fakeUri = Uri.parse("content://" + context.getPackageName()
                + ".fileprovider/private_download/real.apk");
        try (MockedStatic<FileProvider> mocked = Mockito.mockStatic(FileProvider.class)) {
            mocked.when(() -> FileProvider.getUriForFile(any(Context.class),
                    anyString(), any(File.class))).thenReturn(fakeUri);
            DownloadUtils.verifyDownloadedFile(context, file, 51L, true,
                    c -> {
                        check.set(c);
                        latch.countDown();
                    });
            assertTrue(awaitCallback(latch, 5000));
            idleMainLooper();
        }
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MATCH, check.get());
        assertEquals(1, notificationCount());
        assertTrue(file.exists());
    }

    @Test
    public void verifyDownloadedFile_matchWithCallbackNoPermissionSkipsNotify()
            throws Exception {
        File file = new File(context.getFilesDir(), "real2.apk");
        try (FileWriter w = new FileWriter(file)) {
            w.write("x");
        }
        seedSignature(file, context.getPackageName(), sig((byte) 4, (byte) 5));
        DownloadRecordManager.record(context, 52L, "real2.apk", "u", "d");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApkUpdateDownloader.ApkSignatureCheck> check =
                new AtomicReference<>();
        try (MockedStatic<NotificationPermission> np =
                     Mockito.mockStatic(NotificationPermission.class)) {
            np.when(() -> NotificationPermission.canPostNotifications(any(Context.class)))
                    .thenReturn(false);
            DownloadUtils.verifyDownloadedFile(context, file, 52L, true,
                    c -> {
                        check.set(c);
                        latch.countDown();
                    });
            assertTrue(awaitCallback(latch, 5000));
            idleMainLooper();
        }
        assertEquals(ApkUpdateDownloader.ApkSignatureCheck.MATCH, check.get());
        assertEquals(0, notificationCount());
    }

    @Test
    public void verifyDownloadedFile_notInstalledShowsToast() throws Exception {
        File file = new File(context.getFilesDir(), "other.apk");
        try (FileWriter w = new FileWriter(file)) {
            w.write("x");
        }
        ShadowPackageManager spm = Shadows.shadowOf(context.getPackageManager());
        spm.setPackageArchiveInfo(file.getAbsolutePath(),
                packageInfo("com.other.app", new Signature[]{sig((byte) 1)}));
        DownloadRecordManager.record(context, 53L, "other.apk", "u", "d");
        DownloadUtils.verifyDownloadedFile(context, file, 53L, false);
        waitForNotVerifying(53L, 5000);
        assertEquals(context.getString(com.gh4a.R.string.apk_signature_unverified),
                ShadowToast.getTextOfLatestToast());
        assertTrue(file.exists());
    }

    @Test
    public void verifyDownloadedFile_missingFileReturnsNullSha256() throws Exception {
        File ghost = new File(context.getFilesDir(), "ghost.txt");
        DownloadRecordManager.record(context, 54L, "ghost.txt", "u", "d");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApkUpdateDownloader.ApkSignatureCheck> check =
                new AtomicReference<>(ApkUpdateDownloader.ApkSignatureCheck.MATCH);
        DownloadUtils.verifyDownloadedFile(context, ghost, 54L, false,
                c -> {
                    check.set(c);
                    latch.countDown();
                });
        assertTrue(awaitCallback(latch, 5000));
        assertNull(check.get());
        DownloadRecordManager.Record r = null;
        for (DownloadRecordManager.Record cand
                : DownloadRecordManager.getRecords(context)) {
            if (cand.downloadId == 54L) {
                r = cand;
                break;
            }
        }
        assertNotNull(r);
        assertNull(r.sha256);
        assertEquals(0L, r.size);
    }

    @Test
    public void getMimeTypeForFile_cases() throws Exception {
        assertEquals("application/vnd.android.package-archive",
                getMimeTypeReflectively("a.apk"));
        assertEquals("text/plain", getMimeTypeReflectively("a.txt"));
        assertEquals("*/*", getMimeTypeReflectively("a"));
        assertEquals("*/*", getMimeTypeReflectively("a.unknownext"));
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
    @Test
    public void classLine_instantiationCoversDeclaration() {
        // L44：类声明行；隐式默认构造器。
        new DownloadUtils();
    }

    @Test
    public void getRedirectClient_doubleCheckedLock_innerFalseReturnsPreset() throws Exception {
        // L100：内层 if (sRedirectClient == null) 的 false 分支，确定性复现
        //（同 SingleFactory 的持锁手法，锁是 DownloadUtils.class）。
        setRedirectClient(null);
        OkHttpClient preset = new OkHttpClient();
        java.util.concurrent.atomic.AtomicReference<OkHttpClient> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(getRedirectClientReflectively());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        synchronized (DownloadUtils.class) {
            worker.start();
            long deadline = System.currentTimeMillis() + 10_000;
            while (worker.getState() != Thread.State.BLOCKED
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
            assertEquals(Thread.State.BLOCKED, worker.getState());
            setRedirectClient(preset);
        }
        worker.join(10_000);
        assertTrue(preset == result.get());
    }

    @Test
    public void chainCallback_successWithNullFileSkipsVerify() {
        // L255：链路回调 success=true 但 file 为 null 时跳过校验、不发通知。
        Download download = mock(Download.class);
        when(download.htmlUrl()).thenReturn("https://example.com/f.zip");
        when(download.name()).thenReturn("f.zip");
        when(download.contentType()).thenReturn("application/zip");
        when(download.description()).thenReturn("desc");
        try (MockedStatic<ChainDownloadHelper> mocked =
                Mockito.mockStatic(ChainDownloadHelper.class)) {
            ArgumentCaptor<ChainDownloadHelper.Callback> captor =
                    ArgumentCaptor.forClass(ChainDownloadHelper.Callback.class);
            mocked.when(() -> ChainDownloadHelper.enqueueChain(any(), any(), any(),
                    any(), any(), captor.capture(), anyBoolean()))
                    .thenAnswer(inv -> null);
            DownloadUtils.enqueueDownloadWithPermissionCheck(activity(), download);
            assertNotNull(captor.getValue());
            int before = notificationCount();
            captor.getValue().onDownloadComplete(context, true, null, 1L);
            assertEquals(before, notificationCount());
        }
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 25)
    public void createDownloadChannel_preOReturnsEarly() throws Exception {
        // L353：SDK < O 时不建通知渠道，直接返回。
        java.lang.reflect.Method m = DownloadUtils.class.getDeclaredMethod(
                "createDownloadChannel", Context.class);
        m.setAccessible(true);
        m.invoke(null, context);
        NotificationManager nm = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        assertTrue(Shadows.shadowOf(nm).getNotificationChannels().isEmpty());
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 32)
    public void verifyDownloadedFile_nonApkNotifiesOnPreTiramisu() throws Exception {
        // L374：SDK < TIRAMISU 时跳过通知权限检查，非 APK 文件直接发完成通知。
        File file = new File(context.getFilesDir(), "report.pdf");
        try (FileWriter w = new FileWriter(file)) {
            w.write("x");
        }
        DownloadRecordManager.record(context, 55L, "report.pdf", "u", "d");
        Uri fakeUri = Uri.parse("content://" + context.getPackageName()
                + ".fileprovider/private_download/report.pdf");
        try (MockedStatic<FileProvider> mocked = Mockito.mockStatic(FileProvider.class)) {
            mocked.when(() -> FileProvider.getUriForFile(any(Context.class),
                    anyString(), any(File.class))).thenReturn(fakeUri);
            DownloadUtils.verifyDownloadedFile(context, file, 55L, true);
            waitForNotVerifying(55L, 5000);
            idleMainLooper();
        }
        assertEquals(1, notificationCount());
        assertTrue(file.exists());
    }

}
