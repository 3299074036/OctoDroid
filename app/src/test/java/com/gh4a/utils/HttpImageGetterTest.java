package com.gh4a.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.widget.TextView;

import androidx.appcompat.graphics.drawable.DrawableWrapperCompat;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.ServiceFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.text.SpannableString;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import pl.droidsonroids.gif.GifDrawable;

/**
 * B 组试点：HttpImageGetter。
 *
 * 可测部分：构造、encode/bind（无图 HTML，不触发网络加载）、pause/
 * resume/clearHtmlCache/destroy/rebind 的状态安全。真实图片加载
 *（loadImageForUrl，私有方法）需 OkHttp mock 注入 mClient，
 * mClient 是 final 且构造时取自 ServiceFactory，无法从外部替换，
 * 待 Mockito（mock ServiceFactory / OkHttpClient）后补。
 */
@RunWith(RobolectricTestRunner.class)
public class HttpImageGetterTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void constructor_doesNotThrow() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.destroy();
    }

    @Test
    public void encode_plainHtmlSetsNoImageSpans() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.encode(mContext, "id1", "<b>hello</b> world");
        getter.destroy();
    }

    @Test
    public void bind_plainHtmlSetsTextOnView() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "hello world", "id1");
        assertEquals("hello world", view.getText().toString());
        assertEquals(android.view.View.VISIBLE, view.getVisibility());
        getter.destroy();
    }

    @Test
    public void bind_emptyHtmlHidesView() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "", "id1");
        assertEquals(android.view.View.GONE, view.getVisibility());
        getter.destroy();
    }

    @Test
    public void bind_boldHtmlKeepsText() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<b>hi</b> there", "id1");
        assertEquals("hi there", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void bind_sameIdReusesCachedHtml() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "first", "id1");
        // 同一 id 再次 bind：ObjectInfo 已有 mHtml，不重新 encode，
        // 仍显示首次绑定的内容
        getter.bind(view, "second", "id1");
        assertEquals("first", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void rebind_replacesCachedHtmlForSameId() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "first", "id1");
        getter.rebind(mContext, view, "second", "id1");
        assertEquals("second", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void rebind_differentViewWorks() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view1 = new TextView(mContext);
        TextView view2 = new TextView(mContext);
        getter.bind(view1, "first", "id1");
        getter.rebind(mContext, view2, "second", "id1");
        assertEquals("second", view2.getText().toString());
        getter.destroy();
    }

    @Test
    public void lifecycle_pauseResumeClearDestroyAreSafe() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "hello", "id1");
        getter.pause();
        getter.resume();
        getter.clearHtmlCache();
        // 清缓存后再次 bind 会重新 encode
        getter.bind(view, "hello", "id1");
        assertEquals("hello", view.getText().toString());
        getter.destroy();
        // destroy 后再调生命周期方法不抛异常
        getter.pause();
        getter.resume();
        getter.clearHtmlCache();
        getter.destroy();
    }

    @Test
    public void destroy_beforeBindIsSafe() {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.destroy();
        getter.encode(mContext, "id1", "late");
        getter.destroy();
    }

    // ---- 真实加载路径（ServiceFactory.getImageHttpClient 挡成 mock OkHttp） ----

    // 1x1 透明 PNG
    private static final String PNG_1X1_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private static final String SVG_10X10 =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\">"
                    + "<rect width=\"10\" height=\"10\" fill=\"red\"/></svg>";

    private MockedStatic<ServiceFactory> mServiceFactoryMock;
    private OkHttpClient mClient;
    private Call mCall;
    private Response mResponse;
    private ResponseBody mBody;

    @Before
    public void mockImageHttpClient() {
        mServiceFactoryMock = mockStatic(ServiceFactory.class, CALLS_REAL_METHODS);
        mClient = mock(OkHttpClient.class);
        mCall = mock(Call.class);
        mResponse = mock(Response.class);
        mBody = mock(ResponseBody.class);
        mServiceFactoryMock.when(ServiceFactory::getImageHttpClient)
                .thenReturn(mClient);
        when(mClient.newCall(any())).thenReturn(mCall);
    }

    @After
    public void closeImageHttpClientMock() {
        if (mServiceFactoryMock != null) {
            mServiceFactoryMock.close();
        }
    }

    private void stubPngBody(byte[] pngBytes, String contentType) throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn((long) pngBytes.length);
        when(mBody.contentType()).thenReturn(MediaType.parse(contentType));
        when(mBody.byteStream()).thenReturn(new ByteArrayInputStream(pngBytes));
    }

    // AsyncTask 走 THREAD_POOL_EXECUTOR，在 Robolectric 下跑在真实后台线程；
    // 轮询等包住的 drawable 换掉（加载图 → 已加载图/错误图），最多等 5 秒。
    private Drawable awaitUnwrapped(ImageSpan span, HttpImageGetter getter)
            throws Exception {
        Drawable loading = (Drawable) field(getter, "mLoadingDrawable");
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Drawable inner = unwrappedDrawable(span);
            if (inner != loading) {
                return inner;
            }
            Thread.sleep(20);
        }
        return unwrappedDrawable(span);
    }

    private static ImageSpan[] imageSpans(TextView view) {
        Spanned text = (Spanned) view.getText();
        return text.getSpans(0, text.length(), ImageSpan.class);
    }

    private static Object field(HttpImageGetter getter, String name) throws Exception {
        Field f = HttpImageGetter.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(getter);
    }

    // ImageSpan 挂的始终是 PlaceholderDrawable（它包住加载图/已加载图/
    // 错误图）；run() 只换包住的内容，不换 span 上的对象，所以要拆包断言。
    private static Drawable unwrappedDrawable(ImageSpan span) {
        return ((DrawableWrapperCompat) span.getDrawable()).getDrawable();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_pngDecodesToBitmapDrawable() throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        stubPngBody(png, "image/png");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        Drawable d = awaitUnwrapped(spans[0], getter);
        assertTrue(d instanceof BitmapDrawable);
        Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
        assertNotNull(bitmap);
        assertEquals(1, bitmap.getWidth());
        assertEquals(1, bitmap.getHeight());
        getter.destroy();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_contentLengthOverLimitShowsError() throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn(21L * 1024 * 1024);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        assertSame(field(getter, "mErrorDrawable"),
                awaitUnwrapped(spans[0], getter));
        getter.destroy();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_ioExceptionShowsError() throws Exception {
        when(mCall.execute()).thenThrow(new IOException("boom"));
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        assertSame(field(getter, "mErrorDrawable"),
                awaitUnwrapped(spans[0], getter));
        getter.destroy();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_nullBodyShowsError() throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(null);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        assertSame(field(getter, "mErrorDrawable"),
                awaitUnwrapped(spans[0], getter));
        getter.destroy();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_imgWithoutSrcShowsError() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        assertSame(field(getter, "mErrorDrawable"),
                awaitUnwrapped(spans[0], getter));
        getter.destroy();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_svgRendersToBitmap() throws Exception {
        byte[] svg = SVG_10X10.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubPngBody(svg, "image/svg+xml");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.svg\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        Drawable d = awaitUnwrapped(spans[0], getter);
        assertTrue(d instanceof BitmapDrawable);
        assertNotNull(((BitmapDrawable) d).getBitmap());
        getter.destroy();
    }

    // ---- GifCallback（private 类，反射构造） ----

    @Test
    public void gifCallback_invalidateScheduleUnscheduleAreSafe() throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.utils.HttpImageGetter$GifCallback");
        Constructor<?> ctor = clazz.getDeclaredConstructor(List.class);
        ctor.setAccessible(true);
        TextView view = new TextView(mContext);
        List<WeakReference<TextView>> refs = new ArrayList<>();
        refs.add(new WeakReference<>(view));
        Object callback = ctor.newInstance(refs);
        Drawable d = mock(Drawable.class);
        clazz.getMethod("invalidateDrawable", Drawable.class)
                .invoke(callback, d);
        final boolean[] ran = {false};
        Runnable task = () -> ran[0] = true;
        clazz.getMethod("scheduleDrawable", Drawable.class, Runnable.class, long.class)
                .invoke(callback, d, task, 0L);
        ShadowLooper.idleMainLooper();
        assertTrue(ran[0]);
        clazz.getMethod("unscheduleDrawable", Drawable.class, Runnable.class)
                .invoke(callback, d, task);
    }

    // ---- 通用反射 helper ----

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String name, Object value)
            throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object invoke(Object target, String name,
            Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object newObjectInfo(HttpImageGetter getter) throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.utils.HttpImageGetter$ObjectInfo");
        Constructor<?> ctor = clazz.getDeclaredConstructor(HttpImageGetter.class);
        ctor.setAccessible(true);
        return ctor.newInstance(getter);
    }

    private static Object newGifInfo(GifDrawable d,
            List<WeakReference<TextView>> refs) throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.utils.HttpImageGetter$GifInfo");
        Constructor<?> ctor = clazz.getDeclaredConstructor(
                GifDrawable.class, List.class);
        ctor.setAccessible(true);
        return ctor.newInstance(d, refs);
    }

    private static SpannableString spannedWithImage(Drawable d) {
        SpannableString spanned = new SpannableString("x");
        spanned.setSpan(new ImageSpan(d), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spanned;
    }

    // ---- GifCallback：stale 引用与 disabled view ----

    @Test
    public void gifCallback_nullRefAndDisabledViewAreSkippedSafely()
            throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.utils.HttpImageGetter$GifCallback");
        Constructor<?> ctor = clazz.getDeclaredConstructor(List.class);
        ctor.setAccessible(true);
        TextView disabled = new TextView(mContext);
        disabled.setEnabled(false);
        List<WeakReference<TextView>> refs = new ArrayList<>();
        refs.add(new WeakReference<>(null));
        refs.add(new WeakReference<>(disabled));
        Object callback = ctor.newInstance(refs);
        clazz.getMethod("invalidateDrawable", Drawable.class)
                .invoke(callback, mock(Drawable.class));
        assertFalse(disabled.isEnabled());
    }

    // ---- GifInfo（mock GifDrawable） ----

    @Test
    public void gifInfo_destroyStopsAndRecyclesDrawable() throws Exception {
        GifDrawable mockGif = mock(GifDrawable.class);
        Object info = newGifInfo(mockGif, new ArrayList<>());
        invoke(info, "destroy", new Class<?>[0]);
        verify(mockGif).setCallback(null);
        verify(mockGif).stop();
        verify(mockGif).recycle();
    }

    @Test
    public void gifInfo_destroyWithNullDrawableDoesNothing() throws Exception {
        GifDrawable mockGif = mock(GifDrawable.class);
        Object info = newGifInfo(mockGif, new ArrayList<>());
        setField(info, "mDrawable", new WeakReference<>(null));
        invoke(info, "destroy", new Class<?>[0]);
        verify(mockGif, times(1)).setCallback(any());
        verify(mockGif, never()).stop();
        verify(mockGif, never()).recycle();
    }

    // ---- ObjectInfo：onImageLoadDone 的三条 drawable 分支 ----

    @Test
    public void objectInfo_onImageLoadDone_nonPlaceholderSpanAddsNothing()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        setField(info, "mHtml", spannedWithImage(new BitmapDrawable(
                mContext.getResources(), bitmap)));
        invoke(info, "onImageLoadDone", new Class<?>[0]);
        assertTrue(((List<?>) getField(info, "mGifs")).isEmpty());
        assertTrue(((List<?>) getField(info, "mBitmaps")).isEmpty());
        getter.destroy();
    }

    @Test
    public void objectInfo_onImageLoadDone_gifSpanStartsWhenResumed()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        GifDrawable mockGif = mock(GifDrawable.class);
        setField(info, "mHtml", spannedWithImage(mockGif));
        invoke(info, "onImageLoadDone", new Class<?>[0]);
        verify(mockGif).start();
        assertEquals(1, ((List<?>) getField(info, "mGifs")).size());
        getter.destroy();
    }

    @Test
    public void objectInfo_onImageLoadDone_gifSpanSkipsStartWhenPaused()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        setField(info, "mResumed", false);
        GifDrawable mockGif = mock(GifDrawable.class);
        setField(info, "mHtml", spannedWithImage(mockGif));
        invoke(info, "onImageLoadDone", new Class<?>[0]);
        verify(mockGif, never()).start();
        assertEquals(1, ((List<?>) getField(info, "mGifs")).size());
        getter.destroy();
    }

    @Test
    public void objectInfo_onImageLoadDone_loadedBitmapSpanIsTracked()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        Class<?> lbdClass = Class.forName(
                "com.gh4a.utils.HttpImageGetter$LoadedBitmapDrawable");
        Constructor<?> ctor = lbdClass.getDeclaredConstructor(
                Resources.class, Bitmap.class);
        ctor.setAccessible(true);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Drawable loaded = (Drawable) ctor.newInstance(
                mContext.getResources(), bitmap);
        setField(info, "mHtml", spannedWithImage(loaded));
        invoke(info, "onImageLoadDone", new Class<?>[0]);
        List<?> bitmaps = (List<?>) getField(info, "mBitmaps");
        assertEquals(1, bitmaps.size());
        assertSame(bitmap, ((WeakReference<Bitmap>) bitmaps.get(0)).get());
        getter.destroy();
    }

    // ---- ObjectInfo：bind / setResumed / view 列表管理 ----

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void objectInfo_bind_nonPlaceholderSpanSkipsLoad() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        setField(info, "mHtml", spannedWithImage(new BitmapDrawable(
                mContext.getResources(), bitmap)));
        TextView view = new TextView(mContext);
        invoke(info, "bind", new Class<?>[]{TextView.class, String.class},
                view, "ignored");
        assertEquals("x", view.getText().toString());
        ShadowLooper.idleMainLooper();
        getter.destroy();
    }

    @Test
    public void objectInfo_setResumed_startsStopsAndSkipsNull()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        GifDrawable mockGif = mock(GifDrawable.class);
        Object live = newGifInfo(mockGif, new ArrayList<>());
        Object stale = newGifInfo(mock(GifDrawable.class), new ArrayList<>());
        setField(stale, "mDrawable", new WeakReference<>(null));
        List<Object> gifs = new ArrayList<>();
        gifs.add(live);
        gifs.add(stale);
        setField(info, "mGifs", gifs);
        invoke(info, "setResumed", new Class<?>[]{boolean.class}, true);
        verify(mockGif).start();
        invoke(info, "setResumed", new Class<?>[]{boolean.class}, false);
        verify(mockGif).stop();
        getter.destroy();
    }

    @Test
    public void objectInfo_addView_removesStaleAndDetectsDuplicate()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        TextView view1 = new TextView(mContext);
        TextView view2 = new TextView(mContext);
        TextView view3 = new TextView(mContext);
        List<WeakReference<TextView>> refs = new ArrayList<>();
        refs.add(new WeakReference<>(null));
        refs.add(new WeakReference<>(null));
        refs.add(new WeakReference<>(view1));
        refs.add(new WeakReference<>(view2));
        setField(info, "mViewRefs", refs);
        invoke(info, "addView", new Class<?>[]{TextView.class}, view2);
        assertEquals(2, ((List<?>) getField(info, "mViewRefs")).size());
        invoke(info, "addView", new Class<?>[]{TextView.class}, view3);
        assertEquals(3, ((List<?>) getField(info, "mViewRefs")).size());
        getter.destroy();
    }

    @Test
    public void objectInfo_removeView_removesNullAndTargetKeepsOther()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        TextView view1 = new TextView(mContext);
        TextView view2 = new TextView(mContext);
        List<WeakReference<TextView>> refs = new ArrayList<>();
        refs.add(new WeakReference<>(null));
        refs.add(new WeakReference<>(view1));
        refs.add(new WeakReference<>(view2));
        setField(info, "mViewRefs", refs);
        invoke(info, "removeView", new Class<?>[]{TextView.class}, view1);
        List<?> after = (List<?>) getField(info, "mViewRefs");
        assertEquals(1, after.size());
        assertSame(view2, ((WeakReference<TextView>) after.get(0)).get());
        getter.destroy();
    }

    @Test
    public void objectInfo_apply_skipsNullRef() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        TextView view = new TextView(mContext);
        List<WeakReference<TextView>> refs = new ArrayList<>();
        refs.add(new WeakReference<>(null));
        refs.add(new WeakReference<>(view));
        setField(info, "mViewRefs", refs);
        invoke(info, "apply", new Class<?>[]{CharSequence.class}, "hi");
        assertEquals("hi", view.getText().toString());
        assertEquals(android.view.View.VISIBLE, view.getVisibility());
        getter.destroy();
    }

    @Test
    public void objectInfo_invalidateViews_skipsNullRef() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        TextView view = new TextView(mContext);
        view.setText("hi");
        List<WeakReference<TextView>> refs = new ArrayList<>();
        refs.add(new WeakReference<>(null));
        refs.add(new WeakReference<>(view));
        setField(info, "mViewRefs", refs);
        invoke(info, "invalidateViewsForNewDrawable", new Class<?>[0]);
        assertEquals("hi", view.getText().toString());
        getter.destroy();
    }

    @Test
    public void objectInfo_discardLoadedImages_recyclesLiveSkipsNull()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        List<WeakReference<Bitmap>> bitmaps = new ArrayList<>();
        bitmaps.add(new WeakReference<>(null));
        bitmaps.add(new WeakReference<>(bitmap));
        setField(info, "mBitmaps", bitmaps);
        GifDrawable mockGif = mock(GifDrawable.class);
        List<Object> gifs = new ArrayList<>();
        gifs.add(newGifInfo(mockGif, new ArrayList<>()));
        setField(info, "mGifs", gifs);
        setField(info, "mHasStartedImageLoad", true);
        invoke(info, "discardLoadedImages", new Class<?>[0]);
        verify(mockGif).recycle();
        assertTrue(bitmap.isRecycled());
        assertTrue(((List<?>) getField(info, "mBitmaps")).isEmpty());
        assertTrue(((List<?>) getField(info, "mGifs")).isEmpty());
        assertFalse((Boolean) getField(info, "mHasStartedImageLoad"));
        getter.destroy();
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void clearHtmlCache_cancelsRunningTask() throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        stubPngBody(png, "image/png");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        assertTrue(awaitUnwrapped(spans[0], getter) instanceof BitmapDrawable);
        Map<?, ?> infos = (Map<?, ?>) field(getter, "mObjectInfos");
        Object info = infos.get("id1");
        assertNotNull(getField(info, "mTask"));
        getter.clearHtmlCache();
        assertNull(getField(info, "mTask"));
        assertNull(getField(info, "mHtml"));
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        assertEquals(1, imageSpans(view).length);
        getter.destroy();
    }

    // ---- loadImageForUrl 直调（反射，同步，无 AsyncTask 线程问题） ----

    private static final String GIF_1X1_BASE64 =
            "R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==";

    private Drawable loadImage(HttpImageGetter getter, String url)
            throws Exception {
        Method m = HttpImageGetter.class.getDeclaredMethod(
                "loadImageForUrl", String.class);
        m.setAccessible(true);
        try {
            return (Drawable) m.invoke(getter, url);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw (Exception) cause;
        }
    }

    private void stubBody(byte[] bytes, String contentType) throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn((long) bytes.length);
        when(mBody.contentType()).thenReturn(
                contentType == null ? null : MediaType.parse(contentType));
        when(mBody.byteStream()).thenReturn(new ByteArrayInputStream(bytes));
    }

    @Test
    public void loadImage_gifHugeIntrinsicHalvesBounds() throws Exception {
        byte[] gif = Base64.decode(GIF_1X1_BASE64, Base64.DEFAULT);
        stubBody(gif, "image/gif");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try (MockedConstruction<GifDrawable> mocked = mockConstruction(
                GifDrawable.class,
                (mock, context) -> {
                    when(mock.getIntrinsicWidth()).thenReturn(100000);
                    when(mock.getIntrinsicHeight()).thenReturn(50000);
                })) {
            Drawable d = loadImage(getter, "https://example.com/x.gif");
            assertEquals(1, mocked.constructed().size());
            GifDrawable mockGif = mocked.constructed().get(0);
            assertSame(mockGif, d);
            ArgumentCaptor<Integer> w = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> h = ArgumentCaptor.forClass(Integer.class);
            verify(mockGif).setBounds(eq(0), eq(0), w.capture(), h.capture());
            assertTrue(w.getValue() > 0 && w.getValue() < 2000);
            assertTrue(h.getValue() > 0 && h.getValue() < 1000);
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_gifSmallIntrinsicKeepsBounds() throws Exception {
        byte[] gif = Base64.decode(GIF_1X1_BASE64, Base64.DEFAULT);
        stubBody(gif, "image/gif");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try (MockedConstruction<GifDrawable> mocked = mockConstruction(
                GifDrawable.class,
                (mock, context) -> {
                    when(mock.getIntrinsicWidth()).thenReturn(10);
                    when(mock.getIntrinsicHeight()).thenReturn(10);
                })) {
            Drawable d = loadImage(getter, "https://example.com/x.gif");
            assertSame(mocked.constructed().get(0), d);
            verify(mocked.constructed().get(0)).setBounds(0, 0, 10, 10);
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_gifMimeFromName() throws Exception {
        byte[] gif = Base64.decode(GIF_1X1_BASE64, Base64.DEFAULT);
        stubBody(gif, null);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try (MockedConstruction<GifDrawable> mocked = mockConstruction(
                GifDrawable.class,
                (mock, context) -> {
                    when(mock.getIntrinsicWidth()).thenReturn(10);
                    when(mock.getIntrinsicHeight()).thenReturn(10);
                })) {
            Drawable d = loadImage(getter, "https://example.com/x.gif");
            assertSame(mocked.constructed().get(0), d);
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_contentTypeNullFallsBackToName() throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        stubBody(png, null);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadImage(getter, "https://example.com/x.png");
            assertTrue(d instanceof BitmapDrawable);
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_contentTypeAndNameNullFallsBackToStream()
            throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        stubBody(png, null);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadImage(getter, "https://example.com/image");
            assertTrue(d instanceof BitmapDrawable);
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_allMimeGuessesNullStillDecodes() throws Exception {
        byte[] garbage = "not an image at all".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        stubBody(garbage, null);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadImage(getter, "https://example.com/image");
            assertNotNull(d);
            assertTrue(d instanceof BitmapDrawable
                    || d == field(getter, "mErrorDrawable"));
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_byteStreamThrowsShowsError() throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn(100L);
        when(mBody.contentType()).thenReturn(MediaType.parse("image/png"));
        when(mBody.byteStream()).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read boom");
            }
        });
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            assertSame(field(getter, "mErrorDrawable"),
                    loadImage(getter, "https://example.com/x.png"));
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_overLimitStreamShowsError() throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn(100L);
        when(mBody.contentType()).thenReturn(MediaType.parse("image/png"));
        when(mBody.byteStream()).thenReturn(endlessBytes(21L * 1024 * 1024));
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            assertSame(field(getter, "mErrorDrawable"),
                    loadImage(getter, "https://example.com/x.png"));
        } finally {
            getter.destroy();
        }
    }

    private static InputStream endlessBytes(final long total) {
        return new InputStream() {
            private long remaining = total;
            @Override
            public int read(byte[] b, int off, int len) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = (int) Math.min(len, remaining);
                Arrays.fill(b, off, off + n, (byte) 'a');
                remaining -= n;
                return n;
            }
            @Override
            public int read() {
                if (remaining <= 0) {
                    return -1;
                }
                remaining--;
                return 'a';
            }
        };
    }

    @Test
    public void loadImage_nullResponseThrowsNpe() throws Exception {
        when(mCall.execute()).thenReturn(null);
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            assertThrows(NullPointerException.class,
                    () -> loadImage(getter, "https://example.com/x.png"));
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_closeThrowsWhileReadingSuppressed() throws Exception {
        when(mCall.execute()).thenReturn(mResponse);
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn(100L);
        when(mBody.contentType()).thenReturn(MediaType.parse("image/png"));
        when(mBody.byteStream()).thenReturn(new InputStream() {
            @Override
            public int read() {
                throw new RuntimeException("read boom");
            }
        });
        doThrow(new RuntimeException("close boom")).when(mResponse).close();
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> loadImage(getter, "https://example.com/x.png"));
            assertEquals("read boom", thrown.getMessage());
            assertEquals(1, thrown.getSuppressed().length);
            assertEquals("close boom",
                    thrown.getSuppressed()[0].getMessage());
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void readCappedBytes_overLimitReturnsNull() throws Exception {
        Method m = HttpImageGetter.class.getDeclaredMethod(
                "readCappedBytes", InputStream.class, int.class);
        m.setAccessible(true);
        assertNull(m.invoke(null, new ByteArrayInputStream(new byte[11]), 10));
        assertEquals(10, ((byte[]) m.invoke(null,
                new ByteArrayInputStream(new byte[10]), 10)).length);
        assertEquals(0, ((byte[]) m.invoke(null,
                new ByteArrayInputStream(new byte[0]), 10)).length);
    }

    // ---- SVG：尺寸缺失 / 宽高比 / 超大 ----

    private static final String SVG_VIEWBOX_WIDE =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 200 100\">"
                    + "<rect width=\"200\" height=\"100\" fill=\"red\"/></svg>";
    private static final String SVG_VIEWBOX_TALL =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 200\">"
                    + "<rect width=\"100\" height=\"200\" fill=\"red\"/></svg>";
    private static final String SVG_NO_SIZE_NO_VIEWBOX =
            "<svg xmlns=\"http://www.w3.org/2000/svg\">"
                    + "<rect width=\"10\" height=\"10\" fill=\"red\"/></svg>";
    private static final String SVG_WIDTH_ONLY =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\">"
                    + "<rect width=\"10\" height=\"10\" fill=\"red\"/></svg>";
    private static final String SVG_HUGE =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10000\" height=\"10000\">"
                    + "<rect width=\"10000\" height=\"10000\" fill=\"red\"/></svg>";

    private Drawable loadSvg(HttpImageGetter getter, String svg)
            throws Exception {
        stubBody(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "image/svg+xml");
        return loadImage(getter, "https://example.com/x.svg");
    }

    @Test
    public void loadImage_svgViewBoxWideUsesMaxWidth() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadSvg(getter, SVG_VIEWBOX_WIDE);
            assertTrue(d instanceof BitmapDrawable);
            assertNotNull(((BitmapDrawable) d).getBitmap());
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_svgViewBoxTallUsesMaxHeight() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadSvg(getter, SVG_VIEWBOX_TALL);
            assertTrue(d instanceof BitmapDrawable);
            assertNotNull(((BitmapDrawable) d).getBitmap());
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_svgNoSizeNoViewBoxUsesMaxDimensions()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadSvg(getter, SVG_NO_SIZE_NO_VIEWBOX);
            assertTrue(d instanceof BitmapDrawable);
            assertNotNull(((BitmapDrawable) d).getBitmap());
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_svgWidthOnlyCoversNegativeHeight()
            throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadSvg(getter, SVG_WIDTH_ONLY);
            assertNotNull(d);
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_svgHugeHalvesWithDensity() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            Drawable d = loadSvg(getter, SVG_HUGE);
            assertTrue(d instanceof BitmapDrawable);
            assertNotNull(((BitmapDrawable) d).getBitmap());
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_svgInvalidShowsError() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            stubBody("this is not svg".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8),
                    "image/svg+xml");
            assertSame(field(getter, "mErrorDrawable"),
                    loadImage(getter, "https://example.com/x.svg"));
        } finally {
            getter.destroy();
        }
    }

    @Test
    public void loadImage_svgEmptyShowsError() throws Exception {
        HttpImageGetter getter = new HttpImageGetter(mContext);
        try {
            stubBody(new byte[0], "image/svg+xml");
            assertSame(field(getter, "mErrorDrawable"),
                    loadImage(getter, "https://example.com/x.svg"));
        } finally {
            getter.destroy();
        }
    }

    // ---- 宽图降采样 ----

    // 最小 PNG 编码器：全黑 RGB 图（AWT 不在 Android 测试 classpath 内）
    private static byte[] blackPngBytes(int width, int height)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        writePngChunk(out, "IHDR", new byte[]{
                (byte) (width >> 24), (byte) (width >> 16),
                (byte) (width >> 8), (byte) width,
                (byte) (height >> 24), (byte) (height >> 16),
                (byte) (height >> 8), (byte) height,
                8, 2, 0, 0, 0});
        byte[] raw = new byte[height * (1 + width * 3)];
        Deflater deflater = new Deflater();
        deflater.setInput(raw);
        deflater.finish();
        byte[] compressed = new byte[raw.length + 64];
        int len = deflater.deflate(compressed);
        deflater.end();
        writePngChunk(out, "IDAT", Arrays.copyOf(compressed, len));
        writePngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream out,
            String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(new byte[]{(byte) (data.length >> 24), (byte) (data.length >> 16),
                (byte) (data.length >> 8), (byte) data.length});
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        long value = crc.getValue();
        out.write(new byte[]{(byte) (value >> 24), (byte) (value >> 16),
                (byte) (value >> 8), (byte) value});
    }

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_widePngDownscales() throws Exception {
        stubPngBody(blackPngBytes(4000, 10), "image/png");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        Drawable d = awaitUnwrapped(spans[0], getter);
        assertTrue(d instanceof BitmapDrawable);
        Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
        assertNotNull(bitmap);
        assertTrue(bitmap.getWidth() < 4000);
        getter.destroy();
    }

    // ---- destroy / cancel 的竞态路径（自控线程，不靠 AsyncTask 调度） ----

    @Test
    @LooperMode(LooperMode.Mode.LEGACY)
    public void loadImage_destroyedBeforeLoadShowsError() throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        stubPngBody(png, "image/png");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        getter.destroy();
        TextView view = new TextView(mContext);
        getter.bind(view, "<img src=\"https://example.com/x.png\"/>", "id1");
        ImageSpan[] spans = imageSpans(view);
        assertEquals(1, spans.length);
        assertSame(field(getter, "mErrorDrawable"),
                awaitUnwrapped(spans[0], getter));
        getter.destroy();
    }

    @Test
    public void loadImage_destroyedMidLoadRecyclesBitmap() throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(mCall.execute()).thenAnswer(inv -> {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return mResponse;
        });
        when(mResponse.body()).thenReturn(mBody);
        when(mBody.contentLength()).thenReturn((long) png.length);
        when(mBody.contentType()).thenReturn(MediaType.parse("image/png"));
        when(mBody.byteStream()).thenReturn(new ByteArrayInputStream(png));
        HttpImageGetter getter = new HttpImageGetter(mContext);
        AtomicReference<Drawable> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(loadImage(getter, "https://example.com/x.png"));
            } catch (Throwable e) {
                error.set(e);
            }
        });
        t.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        getter.destroy();
        release.countDown();
        t.join(5000);
        assertNull(error.get());
        assertSame(field(getter, "mErrorDrawable"), result.get());
        getter.destroy();
    }

    @Test
    public void asyncTask_onPostExecuteSkippedWhenCancelled() throws Exception {
        byte[] png = Base64.decode(PNG_1X1_BASE64, Base64.DEFAULT);
        stubPngBody(png, "image/png");
        HttpImageGetter getter = new HttpImageGetter(mContext);
        Object info = newObjectInfo(getter);
        Class<?> phdClass = Class.forName(
                "com.gh4a.utils.HttpImageGetter$PlaceholderDrawable");
        Class<?> infoClass = Class.forName(
                "com.gh4a.utils.HttpImageGetter$ObjectInfo");
        Constructor<?> phdCtor = phdClass.getDeclaredConstructor(
                String.class, infoClass, Drawable.class);
        phdCtor.setAccessible(true);
        Drawable loading = (Drawable) field(getter, "mLoadingDrawable");
        Object placeholder = phdCtor.newInstance(
                "https://example.com/x.png", info, loading);
        Class<?> taskClass = Class.forName(
                "com.gh4a.utils.HttpImageGetter$ImageGetterAsyncTask");
        Constructor<?> taskCtor = taskClass.getDeclaredConstructor(
                HttpImageGetter.class, infoClass);
        taskCtor.setAccessible(true);
        Object task = taskCtor.newInstance(getter, info);
        Method doInBackground = taskClass.getDeclaredMethod(
                "doInBackground", phdClass.arrayType());
        doInBackground.setAccessible(true);
        Object params = java.lang.reflect.Array.newInstance(phdClass, 1);
        java.lang.reflect.Array.set(params, 0, placeholder);
        doInBackground.invoke(task, params);
        ShadowLooper.idleMainLooper();
        assertTrue(((DrawableWrapperCompat) placeholder).getDrawable()
                instanceof BitmapDrawable);
        boolean cancelled = (Boolean) taskClass.getMethod(
                "cancel", boolean.class).invoke(task, true);
        assertTrue(cancelled);
        Method onPostExecute = taskClass.getDeclaredMethod(
                "onPostExecute", Void.class);
        onPostExecute.setAccessible(true);
        onPostExecute.invoke(task, (Object) null);
        // onImageLoadDone 被跳过：位图未登记
        assertTrue(((List<?>) getField(info, "mBitmaps")).isEmpty());
        getter.destroy();
    }
}
