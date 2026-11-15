package com.gh4a.utils;

import android.content.Context;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.BuildConfig;
import com.gh4a.fragment.SettingsFragment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-JVM tests for UpdateChecker.isNewer / shouldAutoCheck,
 * plus the network paths via a mock OkHttpClient injected into the
 * private static sClient field (no real network; restored after each test).
 */
@RunWith(RobolectricTestRunner.class)
public class UpdateCheckerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private OkHttpClient mockClient;

    @Before
    public void setUp() throws Exception {
        mockClient = mock(OkHttpClient.class);
        setClient(mockClient);
        // 三条竞速链路：自选镜像（与默认不同）+ 默认镜像 + 直连
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MirrorHelper.PREF_MIRROR_ENABLED, true)
                .putString(MirrorHelper.PREF_MIRROR_PRESET, "https://mirror.example.com")
                .apply();
    }

    @After
    public void tearDown() throws Exception {
        setClient(null);
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(MirrorHelper.PREF_MIRROR_ENABLED)
                .remove(MirrorHelper.PREF_MIRROR_PRESET)
                .apply();
    }

    private static void setClient(OkHttpClient client) throws Exception {
        Field f = UpdateChecker.class.getDeclaredField("sClient");
        f.setAccessible(true);
        f.set(null, client);
    }

    private static OkHttpClient getClientReflectively() throws Exception {
        Method m = UpdateChecker.class.getDeclaredMethod("getClient");
        m.setAccessible(true);
        return (OkHttpClient) m.invoke(null);
    }

    private static String resolveApkUrlFromAssets(JSONArray assets) throws Exception {
        Method m = UpdateChecker.class.getDeclaredMethod(
                "resolveApkUrlFromAssets", JSONArray.class);
        m.setAccessible(true);
        return (String) m.invoke(null, assets);
    }

    private static Call okCall(Response response) throws IOException {
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        return call;
    }

    private static Call failingCall(IOException e) throws IOException {
        Call call = mock(Call.class);
        when(call.execute()).thenThrow(e);
        return call;
    }

    private static Response mockResponse(int code, boolean successful, ResponseBody body) {
        Response r = mock(Response.class);
        when(r.code()).thenReturn(code);
        when(r.isSuccessful()).thenReturn(successful);
        when(r.body()).thenReturn(body);
        return r;
    }

    private static ResponseBody mockBody(String content) {
        ResponseBody b = mock(ResponseBody.class);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        when(b.contentLength()).thenReturn((long) bytes.length);
        when(b.byteStream()).thenReturn(new ByteArrayInputStream(bytes));
        return b;
    }

    private static Response streamedResponse(byte[] bytes) {
        ResponseBody b = mock(ResponseBody.class);
        when(b.contentLength()).thenReturn(-1L);
        when(b.byteStream()).thenReturn(new ByteArrayInputStream(bytes));
        return mockResponse(200, true, b);
    }

    private static ResponseBody mockBodyWithLength(String content, long contentLength) {
        ResponseBody b = mock(ResponseBody.class);
        when(b.contentLength()).thenReturn(contentLength);
        when(b.byteStream()).thenReturn(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        return b;
    }

    private static Response freshOkResponse(String content) {
        return mockResponse(200, true, mockBody(content));
    }

    private void stubCalls(Call... calls) {
        when(mockClient.newCall(any(Request.class)))
                .thenReturn(calls[0], java.util.Arrays.copyOfRange(calls, 1, calls.length));
    }

    private static String releaseJson(String tag, String body, String assetsJson) {
        return "{\"tag_name\":\"" + tag + "\",\"body\":\""
                + body + "\",\"assets\":" + assetsJson + "}";
    }

    private static String debugAssetJson(String version) {
        return "[{\"name\":\"OctoDroid-debug-" + version
                + ".apk\",\"browser_download_url\":\"https://github.com/3299074036/OctoDroid/releases/download/v"
                + version + "/OctoDroid-debug-" + version + ".apk\"}]";
    }

    private static String releaseOnlyAssetJson(String version) {
        return "[{\"name\":\"OctoDroid-release-" + version
                + ".apk\",\"browser_download_url\":\"https://github.com/3299074036/OctoDroid/releases/download/v"
                + version + "/OctoDroid-release-" + version + ".apk\"}]";
    }

    private static String bumpPatch(String version) {
        String[] parts = version.split("\\.");
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) + 1 : 1;
        return parts[0] + "." + (parts.length > 1 ? parts[1] : "0") + "." + patch;
    }

    private static class RecordingCallback implements UpdateChecker.Callback {
        boolean hasUpdate;
        String latestVersion;
        String releaseNotes;
        String apkUrl;
        String error;
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onResult(boolean hasUpdate, String latestVersion,
                String releaseNotes, String apkUrl) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.releaseNotes = releaseNotes;
            this.apkUrl = apkUrl;
            latch.countDown();
        }

        @Override
        public void onError(String message) {
            this.error = message;
            latch.countDown();
        }
    }

    private RecordingCallback runCheck() throws InterruptedException {
        RecordingCallback cb = new RecordingCallback();
        UpdateChecker.check(mContext, cb);
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            if (cb.latch.getCount() == 0) {
                return cb;
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(50);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return cb;
    }

    @Test
    public void getClient_createsAndCaches() throws Exception {
        setClient(null);
        OkHttpClient first = getClientReflectively();
        assertNotNull(first);
        assertSame(first, getClientReflectively());
    }

    @Test
    public void resolveApkUrlFromAssets_cases() throws Exception {
        assertNull(resolveApkUrlFromAssets(null));
        assertNull(resolveApkUrlFromAssets(new JSONArray("[]")));
        assertNull(resolveApkUrlFromAssets(new JSONArray("[\"not-an-object\"]")));
        assertNull(resolveApkUrlFromAssets(new JSONArray(
                "[{\"name\":\"notes.txt\",\"browser_download_url\":\"https://x/y.txt\"}]")));
        assertNull(resolveApkUrlFromAssets(new JSONArray(
                "[{\"name\":\"OctoDroid-debug-0.2.6.apk\",\"browser_download_url\":\"\"}]")));
        // 测试包是 debug 构建：只要 debug 包；release 包不匹配
        assertNull(resolveApkUrlFromAssets(new JSONArray(releaseOnlyAssetJson("0.2.6"))));
        assertEquals("https://github.com/3299074036/OctoDroid/releases/download/v0.2.6/OctoDroid-debug-0.2.6.apk",
                resolveApkUrlFromAssets(new JSONArray(debugAssetJson("0.2.6"))));
        assertEquals("https://github.com/3299074036/OctoDroid/releases/download/v0.2.6/OctoDroid-debug-0.2.6.apk",
                resolveApkUrlFromAssets(new JSONArray(
                        "[{\"name\":\"OCTODROID-DEBUG-0.2.6.APK\",\"browser_download_url\":\"https://github.com/3299074036/OctoDroid/releases/download/v0.2.6/OctoDroid-debug-0.2.6.apk\"}]")));
    }

    @Test
    public void check_successWithUpdate() throws Exception {
        String newer = bumpPatch(BuildConfig.VERSION_NAME);
        Response ok = mockResponse(200, true,
                mockBody(releaseJson("v" + newer, "notes", debugAssetJson(newer))));
        Call c1 = okCall(ok);
        Call c2 = okCall(ok);
        Call c3 = okCall(ok);
        stubCalls(c1, c2, c3);
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertNull(cb.error);
        assertTrue(cb.hasUpdate);
        assertEquals(newer, cb.latestVersion);
        assertEquals("notes", cb.releaseNotes);
        assertTrue(cb.apkUrl.contains("OctoDroid-debug-" + newer + ".apk"));
        // 任一链路成功后其余全部取消
        verify(c1, times(1)).cancel();
        verify(c2, times(1)).cancel();
        verify(c3, times(1)).cancel();
    }

    @Test
    public void check_successNoUpdate() throws Exception {
        Response ok = mockResponse(200, true, mockBody(releaseJson(
                "v" + BuildConfig.VERSION_NAME, "notes", debugAssetJson("0.2.6"))));
        stubCalls(okCall(ok), okCall(ok), okCall(ok));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertNull(cb.error);
        assertFalse(cb.hasUpdate);
        assertEquals(BuildConfig.VERSION_NAME, cb.latestVersion);
        assertNull(cb.apkUrl);
    }

    @Test
    public void check_successTagWithoutV() throws Exception {
        String newer = bumpPatch(BuildConfig.VERSION_NAME);
        Response ok = mockResponse(200, true,
                mockBody(releaseJson(newer, "notes", "[]")));
        stubCalls(okCall(ok), okCall(ok), okCall(ok));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertTrue(cb.hasUpdate);
        assertEquals(newer, cb.latestVersion);
        assertNull(cb.apkUrl);
    }

    @Test
    public void check_successNoMatchingAsset() throws Exception {
        String newer = bumpPatch(BuildConfig.VERSION_NAME);
        Response ok = mockResponse(200, true, mockBody(releaseJson(
                "v" + newer, "notes", releaseOnlyAssetJson(newer))));
        stubCalls(okCall(ok), okCall(ok), okCall(ok));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertTrue(cb.hasUpdate);
        assertNull(cb.apkUrl);
    }

    @Test
    public void check_firstChainFailsSecondWins() throws Exception {
        String newer = bumpPatch(BuildConfig.VERSION_NAME);
        Response ok = mockResponse(200, true,
                mockBody(releaseJson("v" + newer, "notes", debugAssetJson(newer))));
        Call slowOk = mock(Call.class);
        when(slowOk.execute()).thenAnswer(invocation -> {
            Thread.sleep(300);
            return ok;
        });
        stubCalls(failingCall(new IOException("boom1")), slowOk,
                failingCall(new IOException("boom3")));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertNull(cb.error);
        assertTrue(cb.hasUpdate);
        assertEquals(newer, cb.latestVersion);
    }

    @Test
    public void check_allChainsFail() throws Exception {
        stubCalls(failingCall(new IOException("boom")),
                failingCall(new IOException("boom")),
                failingCall(new IOException("boom")));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("boom", cb.error);
    }

    @Test
    public void check_nonIoExceptionCauseWrapped() throws Exception {
        // fetchReleaseJson 透传 RuntimeException：cause 不是 IOException，
        // fetchLatestRelease 用 String.valueOf(cause) 包一层
        Call rt1 = mock(Call.class);
        when(rt1.execute()).thenThrow(new IllegalStateException("boom-rt"));
        Call rt2 = mock(Call.class);
        when(rt2.execute()).thenThrow(new IllegalStateException("boom-rt"));
        Call rt3 = mock(Call.class);
        when(rt3.execute()).thenThrow(new IllegalStateException("boom-rt"));
        stubCalls(rt1, rt2, rt3);
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("java.lang.IllegalStateException: boom-rt", cb.error);
    }

    @Test
    public void check_errorWithNullMessage() throws Exception {
        stubCalls(failingCall(new IOException()),
                failingCall(new IOException()),
                failingCall(new IOException()));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("java.io.IOException", cb.error);
    }

    @Test
    public void check_nonSuccessfulResponse() throws Exception {
        Response r500 = mockResponse(500, false, mockBody("{}"));
        stubCalls(okCall(r500), okCall(r500), okCall(r500));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("HTTP 500", cb.error);
    }

    @Test
    public void check_nullBodyResponse() throws Exception {
        Response noBody = mockResponse(200, true, null);
        stubCalls(okCall(noBody), okCall(noBody), okCall(noBody));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("HTTP 200", cb.error);
    }

    @Test
    public void check_responseTooLarge() throws Exception {
        Response big = mockResponse(200, true,
                mockBodyWithLength("{\"tag_name\":\"v9.9.9\"}", 2_000_000L));
        stubCalls(okCall(big), okCall(big), okCall(big));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("response too large", cb.error);
    }

    @Test
    public void check_streamedTooLarge() throws Exception {
        byte[] huge = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(huge, (byte) 'x');
        // 每条链路各自的 body+流：流读完即耗尽，不能共用
        stubCalls(okCall(streamedResponse(huge)), okCall(streamedResponse(huge)),
                okCall(streamedResponse(huge)));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("response too large", cb.error);
    }

    @Test
    public void check_badJsonResponse() throws Exception {
        stubCalls(okCall(freshOkResponse("not json")),
                okCall(freshOkResponse("not json")),
                okCall(freshOkResponse("not json")));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("bad response", cb.error);
    }

    @Test
    public void check_emptyTagResponse() throws Exception {
        stubCalls(okCall(freshOkResponse("{\"body\":\"x\"}")),
                okCall(freshOkResponse("{\"body\":\"x\"}")),
                okCall(freshOkResponse("{\"body\":\"x\"}")));
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("empty release tag", cb.error);
    }

    @Test
    public void check_timeoutPollReturnsNull() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        Call hanging = mock(Call.class);
        when(hanging.execute()).thenAnswer(invocation -> {
            never.await();
            return null;
        });
        stubCalls(hanging, hanging, hanging);
        RecordingCallback cb = runCheck();
        assertEquals(0L, cb.latch.getCount());
        assertEquals("update check failed", cb.error);
    }

    @Test
    public void isNewer_moreCases() {
        assertFalse(UpdateChecker.isNewer("0.2", "0.2.5"));
        assertTrue(UpdateChecker.isNewer("0.2.5.1", "0.2.5"));
        // 非数字段按 0 计（parsePart 的 NumberFormatException 分支）
        assertFalse(UpdateChecker.isNewer("0.2.x", "0.2.5"));
        assertTrue(UpdateChecker.isNewer("0.2.5", "0.2.x"));
    }

    @Test
    public void isNewer_majorBump() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"));
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"));
    }

    @Test
    public void isNewer_minorAndPatchBump() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.5"));
        assertTrue(UpdateChecker.isNewer("0.2.6", "0.2.5"));
    }

    @Test
    public void isNewer_equalVersionsNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.2.5", "0.2.5"));
        assertFalse(UpdateChecker.isNewer("1.0", "1.0"));
    }

    @Test
    public void isNewer_olderLatestNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.2.4", "0.2.5"));
        assertFalse(UpdateChecker.isNewer("0.1.9", "0.2.0"));
    }

    @Test
    public void isNewer_differentLengthsMissingPartIsZero() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0"));
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"));
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"));
    }

    @Test
    public void isNewer_nonNumericSuffixStripped() {
        // parsePart strips non-digits: "5-beta" parses as 5.
        assertFalse(UpdateChecker.isNewer("0.2.5-beta", "0.2.5"));
        assertTrue(UpdateChecker.isNewer("0.2.6-beta", "0.2.5"));
    }

    @Test
    public void isNewer_emptyPartTreatedAsZero() {
        assertFalse(UpdateChecker.isNewer("", ""));
        assertTrue(UpdateChecker.isNewer("1", ""));
    }

    @Test
    public void isNewer_firstDifferingPartDecides() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"));
        assertFalse(UpdateChecker.isNewer("1.0.99", "1.1.0"));
    }

    @Test
    public void shouldAutoCheck_alwaysTrue() {
        assertTrue(UpdateChecker.shouldAutoCheck(null));
    }
}
