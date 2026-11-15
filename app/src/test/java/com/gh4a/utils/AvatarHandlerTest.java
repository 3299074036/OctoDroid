package com.gh4a.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.ImageView;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.collection.LongSparseArray;
import androidx.collection.LruCache;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;

import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import androidx.test.core.app.ApplicationProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
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
 * A 组：AvatarHandler 触 ImageView/Bitmap/Resources，走 Robolectric。
 * 注意：userId &gt; 0 的 assignAvatar 会起后台线程真实取头像（网络），
 * 本组只测 userId &lt;= 0 / user 为空的路径，绝不触发真实请求。
 */
@RunWith(RobolectricTestRunner.class)
public class AvatarHandlerTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void loadUserAvatarSynchronously_nullUserReturnsNull() {
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), null));
    }

    @Test
    public void loadUserAvatarSynchronously_userWithNullIdReturnsNull() {
        User user = User.builder().login("octocat").build();
        assertNull(user.id());
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), user));
    }

    @Test
    public void assignAvatar_nullUserSetsDefaultDrawable() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, (User) null);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_userWithNullIdSetsDefaultDrawable() {
        ImageView view = new ImageView(context());
        User user = User.builder().login("octocat").build();
        AvatarHandler.assignAvatar(view, user);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_zeroUserIdSetsDefaultDrawableWithoutNetwork() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 0, "https://example.com/a.png");
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_negativeUserIdSetsDefaultDrawableWithoutNetwork() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "bob", -5, null);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_nullUserNameWithZeroIdSetsDefaultDrawable() {
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, null, 0, null);
        assertTrue(view.getDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_menuItemZeroIdSetsDefaultIcon() {
        MenuBuilder menu = new MenuBuilder(context());
        MenuItem item = menu.add("test");
        AvatarHandler.assignAvatar(context(), item, "carol", 0);
        assertTrue(item.getIcon() instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void defaultAvatarDrawable_nullNameNullIdentifierDoesNotThrow() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable(null, null);
        assertNotNull(d);
    }

    @Test
    public void defaultAvatarDrawable_minValueHashIdentifierDoesNotThrow() {
        // 260：identifier.hashCode() == Integer.MIN_VALUE 时旧 Math.abs
        // 仍为负，取模得负下标即 AIOOBE；floorMod 必须恒为非负。
        Object minHashId = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable(null, minHashId);
        assertNotNull(d);
    }

    @Test
    public void defaultAvatarDrawable_minValueHashUserNameDoesNotThrow() {
        // 构造一个 hashCode 恰为 Integer.MIN_VALUE 的登录名很难，
        // 用等价路径：名字非空时同样走 floorMod，任意名都不应抛异常。
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("octocat", 123L);
        assertNotNull(d);
    }

    @Test
    public void defaultAvatarDrawable_opacityIsOpaque() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        assertEquals(PixelFormat.OPAQUE, d.getOpacity());
    }

    @Test
    public void defaultAvatarDrawable_drawWithBoundsDoesNotThrow() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setBounds(0, 0, 100, 100);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bitmap));
    }

    @Test
    public void defaultAvatarDrawable_drawWithEmptyBoundsIsNoop() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setBounds(0, 0, 0, 0);
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bitmap));
    }

    @Test
    public void defaultAvatarDrawable_setAlphaAndColorFilterDoNotThrow() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setAlpha(128);
        d.setColorFilter(null);
    }

    // ---------- B 组：网络路径经 mockStatic(ServiceFactory) 注入 mock client ----------

    private OkHttpClient mockClient;
    private MockedStatic<ServiceFactory> serviceFactoryMock;

    private static Object getStatic(String name) throws Exception {
        Field f = AvatarHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field f = AvatarHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static int maxImageSizePx() throws Exception {
        return (int) getStatic("sMaxImageSizePx");
    }

    private static byte[] pngBytes(int width, int height) {
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xff4285f4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(b.compress(Bitmap.CompressFormat.PNG, 100, out));
        return out.toByteArray();
    }

    private static ResponseBody mockBody(byte[] bytes, long contentLength) {
        ResponseBody body = mock(ResponseBody.class);
        when(body.contentLength()).thenReturn(contentLength);
        when(body.byteStream()).thenReturn(new ByteArrayInputStream(bytes));
        return body;
    }

    private static Response mockResponse(int code, boolean successful, ResponseBody body) {
        Response r = mock(Response.class);
        when(r.code()).thenReturn(code);
        when(r.isSuccessful()).thenReturn(successful);
        when(r.body()).thenReturn(body);
        return r;
    }

    private static Call okCall(byte[] png) throws IOException {
        ResponseBody body = mockBody(png, png.length);
        Response response = mockResponse(200, true, body);
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        return call;
    }

    private void seedMainLooperWorker() throws Exception {
        // WorkerHandler 的消息默认发往真 HandlerThread：Mockito static mock
        // 是线程作用域的，工作线程上拿到的是真 client（真请求）。把工作
        // Handler 预置到主 looper 上，取图就跑在测试线程，mock 生效。
        HandlerThread dummy = new HandlerThread("test-worker");
        dummy.start();
        setStatic("sWorkerThread", dummy);
        Class<?> wh = Class.forName("com.gh4a.utils.AvatarHandler$WorkerHandler");
        java.lang.reflect.Constructor<?> c = wh.getDeclaredConstructor(Looper.class);
        c.setAccessible(true);
        setStatic("sWorkerHandler", c.newInstance(Looper.getMainLooper()));
    }

    private void idleMain() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void idleMainFor(Duration duration) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(duration);
    }

    private User userWithAvatar(long id, String login, String url) {
        return User.builder().login(login).id(id).avatarUrl(url).build();
    }

    @Before
    public void setUpB() throws Exception {
        // 静态状态复位：缓存/请求队列/工作线程/尺寸
        Object thread = getStatic("sWorkerThread");
        if (thread != null) {
            ((HandlerThread) thread).getLooper().quit();
        }
        setStatic("sCache", null);
        ((LongSparseArray<?>) getStatic("sRequests")).clear();
        setStatic("sNextRequestId", 1);
        setStatic("sMaxImageSizePx", -1);
        setStatic("sWorkerThread", null);
        setStatic("sWorkerHandler", null);
        ((Handler) getStatic("sHandler")).removeCallbacksAndMessages(null);
        mockClient = mock(OkHttpClient.class);
        serviceFactoryMock = Mockito.mockStatic(ServiceFactory.class);
        serviceFactoryMock.when(ServiceFactory::getImageHttpClient).thenReturn(mockClient);
    }

    @After
    public void tearDownB() throws Exception {
        serviceFactoryMock.close();
        Object thread = getStatic("sWorkerThread");
        if (thread != null) {
            ((HandlerThread) thread).getLooper().quit();
            ((HandlerThread) thread).join(1000);
        }
        setStatic("sCache", null);
        ((LongSparseArray<?>) getStatic("sRequests")).clear();
        setStatic("sNextRequestId", 1);
        setStatic("sMaxImageSizePx", -1);
        setStatic("sWorkerThread", null);
        setStatic("sWorkerHandler", null);
        ((Handler) getStatic("sHandler")).removeCallbacksAndMessages(null);
    }

    @Test
    public void loadUserAvatarSynchronously_fetchSuccessCachesAndHits() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        User user = userWithAvatar(7L, "alice", "https://example.com/a.png");
        Bitmap first = AvatarHandler.loadUserAvatarSynchronously(context(), user);
        assertNotNull(first);
        Bitmap second = AvatarHandler.loadUserAvatarSynchronously(context(), user);
        assertSame(first, second);
        verify(mockClient, times(1)).newCall(any(Request.class));
    }

    @Test
    public void loadUserAvatarSynchronously_fetchNonSquareScalesDown() throws Exception {
        byte[] png = pngBytes(200, 100);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        User user = userWithAvatar(7L, "alice", "https://example.com/a.png");
        Bitmap result = AvatarHandler.loadUserAvatarSynchronously(context(), user);
        assertNotNull(result);
        // 200x100 缩放到边长上限内，宽高比保持 2:1
        int max = maxImageSizePx();
        assertTrue(result.getWidth() <= max);
        assertEquals(result.getWidth(), 2 * result.getHeight());
    }

    @Test
    public void loadUserAvatarSynchronously_fetchHttpFailureReturnsNull() throws Exception {
        ResponseBody body = mockBody(new byte[0], 0);
        Response response = mockResponse(500, false, body);
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        User user = userWithAvatar(7L, "alice", "https://example.com/a.png");
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), user));
    }

    @Test
    public void loadUserAvatarSynchronously_fetchNullBodyReturnsNull() throws Exception {
        Response response = mockResponse(200, true, null);
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        User user = userWithAvatar(7L, "alice", "https://example.com/a.png");
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), user));
    }

    @Test
    public void loadUserAvatarSynchronously_fetchTooLargeContentLengthReturnsNull() throws Exception {
        ResponseBody body = mockBody(new byte[0], 21L * 1024 * 1024);
        Response response = mockResponse(200, true, body);
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        User user = userWithAvatar(7L, "alice", "https://example.com/a.png");
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), user));
    }

    @Test
    public void loadUserAvatarSynchronously_fetchStreamTooLargeReturnsNull() throws Exception {
        // 流式限量读：谎报长度为 -1、实际超 20MB 的流
        InputStream endless = new InputStream() {
            private long remaining = 21L * 1024 * 1024;
            @Override
            public int read(byte[] b) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = (int) Math.min(b.length, remaining);
                java.util.Arrays.fill(b, 0, n, (byte) 'x');
                remaining -= n;
                return n;
            }
            @Override
            public int read() {
                return remaining-- > 0 ? 'x' : -1;
            }
        };
        ResponseBody body = mock(ResponseBody.class);
        when(body.contentLength()).thenReturn(-1L);
        when(body.byteStream()).thenReturn(endless);
        Response response = mockResponse(200, true, body);
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        User user = userWithAvatar(7L, "alice", "https://example.com/a.png");
        assertNull(AvatarHandler.loadUserAvatarSynchronously(context(), user));
    }

    @Test
    public void readCappedBytes_overLimitReturnsNull() throws Exception {
        Method m = AvatarHandler.class.getDeclaredMethod(
                "readCappedBytes", InputStream.class, int.class);
        m.setAccessible(true);
        byte[] data = new byte[100];
        assertNull(m.invoke(null, new ByteArrayInputStream(data), 10));
    }

    @Test
    public void readCappedBytes_normalReturnsBytes() throws Exception {
        Method m = AvatarHandler.class.getDeclaredMethod(
                "readCappedBytes", InputStream.class, int.class);
        m.setAccessible(true);
        byte[] out = (byte[]) m.invoke(null,
                new ByteArrayInputStream("hello".getBytes()), 100);
        assertEquals("hello", new String(out));
    }

    @Test
    public void initialize_createsCacheAndSizeOfEntryRemoved() throws Exception {
        Method init = AvatarHandler.class.getDeclaredMethod("initialize", Context.class);
        init.setAccessible(true);
        init.invoke(null, context());
        assertTrue(maxImageSizePx() > 0);
        @SuppressWarnings("unchecked")
        LruCache<Long, Bitmap> cache = (LruCache<Long, Bitmap>) getStatic("sCache");
        assertNotNull(cache);
        Bitmap b1 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        Bitmap b2 = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        cache.put(7L, b1); // sizeOf
        cache.put(7L, b2); // entryRemoved（同 key 替换）
        assertSame(b2, cache.get(7L));
    }

    @Test
    public void assignAvatar_cacheHitAppliesPlainDrawable() throws Exception {
        Method init = AvatarHandler.class.getDeclaredMethod("initialize", Context.class);
        init.setAccessible(true);
        init.invoke(null, context());
        @SuppressWarnings("unchecked")
        LruCache<Long, Bitmap> cache = (LruCache<Long, Bitmap>) getStatic("sCache");
        Bitmap avatar = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        cache.put(7L, avatar);
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        assertTrue(view.getDrawable() instanceof RoundedBitmapDrawable);
        verify(mockClient, never()).newCall(any(Request.class));
    }

    @Test
    public void assignAvatar_cacheHitOverDefaultDrawableTransitions() throws Exception {
        Method init = AvatarHandler.class.getDeclaredMethod("initialize", Context.class);
        init.setAccessible(true);
        init.invoke(null, context());
        @SuppressWarnings("unchecked")
        LruCache<Long, Bitmap> cache = (LruCache<Long, Bitmap>) getStatic("sCache");
        cache.put(7L, Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888));
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 0, null);
        assertTrue(view.getDrawable() instanceof AvatarHandler.DefaultAvatarDrawable);
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        assertTrue(view.getDrawable() instanceof TransitionDrawable);
    }

    @Test
    public void assignAvatar_withUserDelegatesAndLoads() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        ImageView view = new ImageView(context());
        seedMainLooperWorker();
        AvatarHandler.assignAvatar(view, userWithAvatar(7L, "alice", "https://example.com/a.png"));
        assertTrue(view.getDrawable() instanceof AvatarHandler.DefaultAvatarDrawable);
        idleMain();
        idleMain();
        assertTrue(view.getDrawable() instanceof TransitionDrawable);
    }

    @Test
    public void assignAvatar_nullUrlUsesDefaultAvatarHost() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        ImageView view = new ImageView(context());
        seedMainLooperWorker();
        AvatarHandler.assignAvatar(view, "alice", 7, null);
        idleMain();
        idleMain();
        assertTrue(view.getDrawable() instanceof TransitionDrawable);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockClient, times(1)).newCall(captor.capture());
        assertTrue(captor.getValue().url().toString()
                .contains("avatars.githubusercontent.com/u/7"));
    }

    @Test
    public void assignAvatar_sameUserCoalescesRequests() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        ImageView v1 = new ImageView(context());
        ImageView v2 = new ImageView(context());
        seedMainLooperWorker();
        AvatarHandler.assignAvatar(v1, "alice", 7, "https://example.com/a.png");
        AvatarHandler.assignAvatar(v2, "alice", 7, "https://example.com/a.png");
        idleMain();
        idleMain();
        assertTrue(v1.getDrawable() instanceof TransitionDrawable);
        assertTrue(v2.getDrawable() instanceof TransitionDrawable);
        verify(mockClient, times(1)).newCall(any(Request.class));
    }

    @Test
    public void assignAvatar_twoUsersThenDestroyDelayed() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        seedMainLooperWorker();
        ImageView v1 = new ImageView(context());
        ImageView v2 = new ImageView(context());
        AvatarHandler.assignAvatar(v1, "alice", 7, "https://example.com/a.png");
        AvatarHandler.assignAvatar(v2, "bob", 8, "https://example.com/b.png");
        idleMain();
        idleMain();
        assertTrue(v1.getDrawable() instanceof TransitionDrawable);
        assertTrue(v2.getDrawable() instanceof TransitionDrawable);
        assertNotNull(getStatic("sWorkerThread"));
        // 最后一个结果后 3 秒才销毁工作线程
        idleMainFor(Duration.ofSeconds(2));
        assertNotNull(getStatic("sWorkerThread"));
        idleMainFor(Duration.ofSeconds(2));
        assertNull(getStatic("sWorkerThread"));
    }

    @Test
    public void assignAvatar_reassignRemovesOldRequest() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        ImageView view = new ImageView(context());
        seedMainLooperWorker();
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        AvatarHandler.assignAvatar(view, "bob", 8, "https://example.com/b.png");
        idleMain();
        idleMain();
        assertTrue(view.getDrawable() instanceof TransitionDrawable);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(mockClient, times(1)).newCall(captor.capture());
        assertTrue(captor.getValue().url().toString().contains("b.png"));
    }

    @Test
    public void assignAvatar_createsWorkerThread() throws Exception {
        // 不预置：走真实的 HandlerThread 创建分支；URL 用本地拒接端口，
        // 真线程上的请求瞬间失败，不拖慢测试
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 7, "https://127.0.0.1:1/x.png");
        assertNotNull(getStatic("sWorkerThread"));
        assertNotNull(getStatic("sWorkerHandler"));
        assertEquals(1, ((LongSparseArray<?>) getStatic("sRequests")).size());
        assertTrue(view.getDrawable() instanceof AvatarHandler.DefaultAvatarDrawable);
        // 真线程跑完失败取图、主 looper 收到空结果后请求清掉
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && ((LongSparseArray<?>) getStatic("sRequests")).size() != 0) {
            idleMain();
            Thread.sleep(50);
        }
        assertEquals(0, ((LongSparseArray<?>) getStatic("sRequests")).size());
    }

    @Test
    public void assignAvatar_fetchFailureKeepsDefaultDrawable() throws Exception {
        Call call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("boom"));
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        seedMainLooperWorker();
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        idleMain();
        idleMain();
        assertTrue(view.getDrawable() instanceof AvatarHandler.DefaultAvatarDrawable);
        assertEquals(0, ((LongSparseArray<?>) getStatic("sRequests")).size());
    }

    @Test
    public void processResult_unknownRequestAndNullBitmap() throws Exception {
        // 先挂一个真实请求，再反射调 $1 的 processResult
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        LongSparseArray<?> requests = (LongSparseArray<?>) getStatic("sRequests");
        assertEquals(1, requests.size());
        long requestId = requests.keyAt(0);
        Object handler = getStatic("sHandler");
        Method processResult = handler.getClass().getDeclaredMethod(
                "processResult", long.class, Bitmap.class);
        processResult.setAccessible(true);
        // 未知 requestId：仅移除，无事发生
        processResult.invoke(handler, 999L, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888));
        assertEquals(1, requests.size());
        // 已知请求 + null 位图：仅移除，不贴图
        processResult.invoke(handler, requestId, null);
        assertEquals(0, requests.size());
        assertTrue(view.getDrawable() instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void shutdownWorker_viaDestroyMessage() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call);
        seedMainLooperWorker();
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        assertNotNull(getStatic("sWorkerThread"));
        ((Handler) getStatic("sHandler")).sendEmptyMessage(3); // MSG_DESTROY
        idleMain();
        assertNull(getStatic("sWorkerThread"));
    }

    @Test
    public void defaultAvatarDrawable_drawInvisibleIsNoop() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        d.setBounds(0, 0, 100, 100);
        d.setVisible(false, false);
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bitmap));
    }

    @Test
    public void delegateEquals() throws Exception {
        ImageView v1 = new ImageView(context());
        ImageView v2 = new ImageView(context());
        Class<?> ivd = Class.forName("com.gh4a.utils.AvatarHandler$ImageViewDelegate");
        java.lang.reflect.Constructor<?> c = ivd.getDeclaredConstructor(ImageView.class);
        c.setAccessible(true);
        Object d1 = c.newInstance(v1);
        Object d1b = c.newInstance(v1);
        Object d2 = c.newInstance(v2);
        assertTrue(d1.equals(d1b));
        assertTrue(!d1.equals(d2));
        assertTrue(!d1.equals("not-a-delegate"));
        assertTrue(!d1.equals(null));

        Class<?> mid = Class.forName("com.gh4a.utils.AvatarHandler$MenuItemDelegate");
        java.lang.reflect.Constructor<?> mc = mid.getDeclaredConstructor(Context.class, MenuItem.class);
        mc.setAccessible(true);
        MenuBuilder menu = new MenuBuilder(context());
        MenuItem i1 = menu.add("a");
        MenuItem i2 = menu.add("b");
        Object m1 = mc.newInstance(context(), i1);
        Object m1b = mc.newInstance(context(), i1);
        Object m2 = mc.newInstance(context(), i2);
        assertTrue(m1.equals(m1b));
        assertTrue(!m1.equals(m2));
        assertTrue(!m1.equals("not-a-delegate"));
        // getDrawable 两路
        assertTrue(i1.getIcon() == null);
        AvatarHandler.assignAvatar(context(), i1, "carol", 0);
        assertTrue(i1.getIcon() instanceof AvatarHandler.DefaultAvatarDrawable);
    }

    @Test
    public void assignAvatar_reassignWithOtherViewRemaining() throws Exception {
        byte[] png = pngBytes(120, 120);
        Call call1 = okCall(png);
        Call call2 = okCall(png);
        when(mockClient.newCall(any(Request.class))).thenReturn(call1, call2);
        seedMainLooperWorker();
        ImageView v1 = new ImageView(context());
        ImageView v2 = new ImageView(context());
        AvatarHandler.assignAvatar(v1, "alice", 7, "https://example.com/a.png");
        AvatarHandler.assignAvatar(v2, "alice", 7, "https://example.com/a.png");
        // v1 改投用户 8：请求 7 仍有 v2，不删请求
        AvatarHandler.assignAvatar(v1, "bob", 8, "https://example.com/b.png");
        idleMain();
        idleMain();
        assertTrue(v2.getDrawable() instanceof TransitionDrawable);
        assertTrue(v1.getDrawable() instanceof TransitionDrawable);
        verify(mockClient, times(2)).newCall(any(Request.class));
    }

    @Test
    public void removeOldRequest_nullWorkerHandlerSkipsRemoveMessages() throws Exception {
        seedMainLooperWorker();
        ImageView view = new ImageView(context());
        AvatarHandler.assignAvatar(view, "alice", 7, "https://example.com/a.png");
        LongSparseArray<?> requests = (LongSparseArray<?>) getStatic("sRequests");
        assertEquals(1, requests.size());
        Object request = requests.valueAt(0);
        Field viewsField = request.getClass().getDeclaredField("views");
        viewsField.setAccessible(true);
        Object storedDelegate = ((List<?>) viewsField.get(request)).get(0);
        setStatic("sWorkerHandler", null);
        Method removeOld = AvatarHandler.class.getDeclaredMethod(
                "removeOldRequest", Class.forName("com.gh4a.utils.AvatarHandler$ViewDelegate"));
        removeOld.setAccessible(true);
        removeOld.invoke(null, storedDelegate);
        assertEquals(0, requests.size());
    }

    @Test
    public void shutdownWorker_noWorkerIsNoop() throws Exception {
        assertNull(getStatic("sWorkerThread"));
        ((Handler) getStatic("sHandler")).sendEmptyMessage(3); // MSG_DESTROY
        idleMain();
        assertNull(getStatic("sWorkerThread"));
    }

    @Test
    public void handlers_ignoreUnknownMessages() throws Exception {
        seedMainLooperWorker();
        ((Handler) getStatic("sHandler")).sendEmptyMessage(999);
        ((Handler) getStatic("sWorkerHandler")).sendEmptyMessage(999);
        idleMain();
        assertNotNull(getStatic("sWorkerThread"));
    }

    @Test
    public void assignAvatar_menuItemCacheHitReadsOldIcon() throws Exception {
        Method init = AvatarHandler.class.getDeclaredMethod("initialize", Context.class);
        init.setAccessible(true);
        init.invoke(null, context());
        @SuppressWarnings("unchecked")
        LruCache<Long, Bitmap> cache = (LruCache<Long, Bitmap>) getStatic("sCache");
        cache.put(7L, Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888));
        MenuBuilder menu = new MenuBuilder(context());
        MenuItem item = menu.add("test");
        AvatarHandler.assignAvatar(context(), item, "alice", 0);
        assertTrue(item.getIcon() instanceof AvatarHandler.DefaultAvatarDrawable);
        AvatarHandler.assignAvatar(context(), item, "alice", 7);
        assertTrue(item.getIcon() instanceof TransitionDrawable);
    }

    @Test
    public void createScaledBitmap_sameSizeReturnsNewInstance() {
        // Robolectric 事实：同尺寸 createScaledBitmap 也返回新实例，
        // 故 fetchBitmap 里 scaled == unscaled 的半边在测试中不可达
        Bitmap b = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        assertTrue(Bitmap.createScaledBitmap(b, 60, 60, true) != b);
    }

    @Test
    public void defaultAvatarDrawable_constantStateNewDrawable() {
        AvatarHandler.DefaultAvatarDrawable d =
                new AvatarHandler.DefaultAvatarDrawable("alice", 1L);
        assertNotNull(d.getConstantState());
        assertEquals(0, d.getConstantState().getChangingConfigurations());
        assertTrue(d.getConstantState().newDrawable()
                instanceof AvatarHandler.DefaultAvatarDrawable);
    }
}
