package com.gh4a.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class ImageViewerActivityTest {
    private Intent intent(String url) {
        return ImageViewerActivity.makeIntent(appContext(), url, "pic.png", false);
    }

    @Test
    public void makeIntent_setsExtras() {
        Intent i = ImageViewerActivity.makeIntent(appContext(), "https://x/y.png", "y.png", true);
        assertEquals(ImageViewerActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("https://x/y.png", i.getStringExtra("image_url"));
        assertEquals("y.png", i.getStringExtra("file_name"));
        assertTrue(i.getBooleanExtra("auth_required", false));
    }

    @Test
    public void makeIntent_authDefaultsFalse() {
        assertFalse(ImageViewerActivity.makeIntent(appContext(), "u", "f", false)
                .getBooleanExtra("auth_required", true));
    }

    @Test
    public void launch_setsFileNameFromIntent() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        assertEquals("pic.png", get(activity, "mFileName"));
        // Robolectric does not create the decor ActionBar, so the
        // production null-guard on getSupportActionBar() applies here
        // and the title can only be verified via the stored file name.
        assertNull(activity.getSupportActionBar());
    }

    @Test
    public void launch_unreachableUrl_showsErrorView() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        long deadline = System.currentTimeMillis() + 30000;
        while (activity.findViewById(R.id.error_view).getVisibility() != View.VISIBLE
                && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
            com.gh4a.activities.ActivityTestHelper.idleMain(100);
        }
        assertEquals(View.VISIBLE, activity.findViewById(R.id.error_view).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.progress_bar).getVisibility());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.error_text))
                .getText().toString().length() > 0);
    }

    @Test
    public void onRetainCustomNonConfigurationInstance_returnsDownloadState() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        assertNotNull(activity.onRetainCustomNonConfigurationInstance());
    }

    @Test
    public void onOptionsItemSelected_home_finishes() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, android.R.id.home, 0, "up")));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void downloadToTempFile_copiesBytes() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        byte[] data = "hello image bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        File f = (File) call(activity, "downloadToTempFile",
                new Class<?>[]{InputStream.class}, new Object[]{new ByteArrayInputStream(data)});
        try {
            assertTrue(f.exists());
            assertEquals(data.length, f.length());
            assertEquals("hello image bytes",
                    new String(java.nio.file.Files.readAllBytes(f.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            f.delete();
        }
    }

    @Test
    public void downloadToTempFile_emptyStream_throwsAndDeletesTemp() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        try {
            call(activity, "downloadToTempFile",
                    new Class<?>[]{InputStream.class}, new Object[]{new ByteArrayInputStream(new byte[0])});
            fail("expected IOException for empty image");
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof java.io.IOException);
        }
    }

    @Test
    public void downloadToTempFile_oversizeStream_throws() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        InputStream endless = new InputStream() {
            @Override
            public int read(byte[] b, int off, int len) {
                java.util.Arrays.fill(b, off, off + len, (byte) 7);
                return len;
            }
            @Override
            public int read() {
                return 7;
            }
        };
        try {
            call(activity, "downloadToTempFile",
                    new Class<?>[]{InputStream.class}, new Object[]{endless});
            fail("expected IOException for oversize image");
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof java.io.IOException);
        }
    }

    @Test
    public void decodeFileWithDownsampling_validPng_returnsBitmap() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        Bitmap source = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        File png = new File(activity.getCacheDir(), "tiny.png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(png)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, bytes));
            out.write(bytes.toByteArray());
        }
        Bitmap decoded = (Bitmap) call(activity, "decodeFileWithDownsampling",
                new Class<?>[]{File.class}, new Object[]{png});
        assertNotNull(decoded);
        assertEquals(64, decoded.getWidth());
        assertEquals(32, decoded.getHeight());
    }

    @Test
    public void decodeFileWithDownsampling_nonImage_returnsDummyBitmapUnderRobolectric() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        File junk = new File(activity.getCacheDir(), "junk.bin");
        java.nio.file.Files.write(junk.toPath(), "not an image at all".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Robolectric's ShadowBitmapFactory returns a non-null dummy bitmap
        // for any file, so the production null -> IOException branch is
        // unreachable here; it is only exercisable on a real device.
        assertNotNull(call(activity, "decodeFileWithDownsampling",
                new Class<?>[]{File.class}, new Object[]{junk}));
    }

    @Test
    public void deliverResult_withBitmap_showsImage() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        Bitmap bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        call(activity, "deliverResult",
                new Class<?>[]{Bitmap.class, Exception.class}, new Object[]{bmp, null});
        assertEquals(View.VISIBLE, activity.findViewById(R.id.image_view).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.progress_bar).getVisibility());
    }

    @Test
    public void deliverResult_withError_showsErrorView() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        call(activity, "deliverResult",
                new Class<?>[]{Bitmap.class, Exception.class},
                new Object[]{null, new RuntimeException("network down")});
        assertEquals(View.VISIBLE, activity.findViewById(R.id.error_view).getVisibility());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.error_text))
                .getText().toString().contains("network down"));
    }

    @Test
    public void deliverResult_nullError_showsUnknownMessage() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        call(activity, "deliverResult",
                new Class<?>[]{Bitmap.class, Exception.class}, new Object[]{null, null});
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.error_text))
                .getText().toString().contains(
                        appContext().getString(R.string.image_viewer_error_unknown)));
    }

    @Test
    public void createDownloadCall_withoutAuth_hasNoAuthHeader() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://example.com/x.png"));
        okhttp3.Call call = (okhttp3.Call) call(activity, "createDownloadCall",
                new Class<?>[]{}, new Object[]{});
        assertEquals("https://example.com/x.png", call.request().url().toString());
        assertNull(call.request().header("Authorization"));
    }

    @Test
    public void retryButton_click_reloadsImage() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        waitFor(() -> activity.findViewById(R.id.error_view).getVisibility() == View.VISIBLE, 30000);
        activity.findViewById(R.id.retry_button).performClick();
        // loadImage hides the error view first, then fails again
        waitFor(() -> activity.findViewById(R.id.error_view).getVisibility() == View.GONE, 10000);
        waitFor(() -> activity.findViewById(R.id.error_view).getVisibility() == View.VISIBLE, 30000);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.error_view).getVisibility());
    }

    @Test
    public void imageView_click_finishes() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        activity.findViewById(R.id.image_view).performClick();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void recreate_withDoneBitmap_reusesResult() throws Exception {
        ActivityController<ImageViewerActivity> c =
                controller(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        ImageViewerActivity activity = c.create().start().resume().get();
        Object state = newDownloadState();
        Bitmap bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        set(state, "bitmap", bmp);
        set(state, "done", true);
        set(activity, "mDownloadState", state);
        c.recreate();
        ImageViewerActivity recreated = c.get();
        assertEquals(View.VISIBLE, recreated.findViewById(R.id.image_view).getVisibility());
        assertEquals(View.GONE, recreated.findViewById(R.id.progress_bar).getVisibility());
        assertEquals(View.GONE, recreated.findViewById(R.id.error_view).getVisibility());
    }

    @Test
    public void recreate_withInFlightDownload_takesOverAndDelivers() throws Exception {
        ActivityController<ImageViewerActivity> c =
                controller(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        ImageViewerActivity activity = c.create().start().resume().get();
        // a fresh in-flight state: the pre-rotation worker thread owns the
        // original state, so hand over a state only this test controls
        Object state = newDownloadState();
        set(activity, "mDownloadState", state);
        c.recreate();
        ImageViewerActivity recreated = c.get();
        assertEquals(View.VISIBLE, recreated.findViewById(R.id.progress_bar).getVisibility());
        Bitmap bmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        set(state, "bitmap", bmp);
        set(state, "done", true);
        Object lock = get(state, "lock");
        synchronized (lock) {
            lock.notifyAll();
        }
        waitFor(() -> recreated.findViewById(R.id.image_view).getVisibility() == View.VISIBLE,
                10000);
        assertEquals(View.VISIBLE, recreated.findViewById(R.id.image_view).getVisibility());
        assertEquals(View.GONE, recreated.findViewById(R.id.progress_bar).getVisibility());
    }

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesToSuper() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 123456, 0, "unknown")));
    }

    @Test
    public void loadImage_twice_cancelsFirstAndDropsStaleResult() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        call(activity, "loadImage", new Class<?>[]{}, new Object[]{});
        call(activity, "loadImage", new Class<?>[]{}, new Object[]{});
        // the second (current-generation) load delivers the error; the first
        // load's result is dropped by the generation check
        waitFor(() -> activity.findViewById(R.id.error_view).getVisibility() == View.VISIBLE, 30000);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.error_view).getVisibility());
        waitFor(() -> get(activity, "mDownloadCall") == null, 10000);
        assertNull(get(activity, "mDownloadCall"));
    }

    @Test
    public void onDestroy_finishing_cancelsInFlightCall() {
        ActivityController<ImageViewerActivity> c =
                controller(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        ImageViewerActivity activity = c.create().start().resume().get();
        okhttp3.Call call = mock(okhttp3.Call.class);
        set(activity, "mDownloadCall", call);
        activity.finish();
        c.pause().stop().destroy();
        verify(call).cancel();
    }

    @Test
    public void createDownloadCall_authWithToken_addsAuthAndNoStoreHeaders() {
        Gh4Application.get().addAccount(User.builder().login("tester").id(1L).build(), "tok");
        ImageViewerActivity activity = launch(ImageViewerActivity.class,
                ImageViewerActivity.makeIntent(appContext(), "https://example.com/x.png", "x.png", true));
        okhttp3.Call call = (okhttp3.Call) call(activity, "createDownloadCall",
                new Class<?>[]{}, new Object[]{});
        assertEquals("Token tok", call.request().header("Authorization"));
        assertEquals("no-store", call.request().header("Cache-Control"));
    }

    @Test
    public void createDownloadCall_authWithoutToken_onlyNoStoreHeader() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class,
                ImageViewerActivity.makeIntent(appContext(), "https://example.com/x.png", "x.png", true));
        okhttp3.Call call = (okhttp3.Call) call(activity, "createDownloadCall",
                new Class<?>[]{}, new Object[]{});
        assertNull(call.request().header("Authorization"));
        assertEquals("no-store", call.request().header("Cache-Control"));
    }

    @Test
    public void downloadAndDecodeImage_httpError_throws() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        okhttp3.Call call = mock(okhttp3.Call.class);
        when(call.execute()).thenReturn(new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url("https://invalid.invalid/x.png").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body(okhttp3.ResponseBody.create(null, ""))
                .build());
        try {
            call(activity, "downloadAndDecodeImage",
                    new Class<?>[]{okhttp3.Call.class}, new Object[]{call});
            fail("expected IOException");
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof IOException);
            assertTrue(e.getCause().getMessage().contains("404"));
        }
    }

    @Test
    public void downloadAndDecodeImage_nullBody_throws() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        okhttp3.Call call = mock(okhttp3.Call.class);
        when(call.execute()).thenReturn(new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url("https://invalid.invalid/x.png").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(null)
                .build());
        try {
            call(activity, "downloadAndDecodeImage",
                    new Class<?>[]{okhttp3.Call.class}, new Object[]{call});
            fail("expected IOException");
        } catch (AssertionError e) {
            assertTrue(e.getCause() instanceof IOException);
            assertTrue(e.getCause().getMessage().contains("200"));
        }
    }

    @Test
    public void downloadAndDecodeImage_success_returnsBitmap() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        okhttp3.Call call = mock(okhttp3.Call.class);
        // a real PNG: Robolectric's BitmapFactory parses the header via ImageIO
        Bitmap source = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, bytes));
        when(call.execute()).thenReturn(new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url("https://invalid.invalid/x.png").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(okhttp3.ResponseBody.create(
                        okhttp3.MediaType.parse("image/png"), bytes.toByteArray()))
                .build());
        Bitmap result = (Bitmap) call(activity, "downloadAndDecodeImage",
                new Class<?>[]{okhttp3.Call.class}, new Object[]{call});
        assertNotNull(result);
        assertEquals(64, result.getWidth());
        assertEquals(32, result.getHeight());
    }

    @Test
    public void recreate_takeoverResultDroppedWhenFinished() throws Exception {
        ActivityController<ImageViewerActivity> c =
                controller(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        ImageViewerActivity activity = c.create().start().resume().get();
        Object state = newDownloadState();
        set(activity, "mDownloadState", state);
        c.recreate();
        ImageViewerActivity recreated = c.get();
        // the takeover thread is waiting on the state lock; finish first so
        // its delivery is dropped by the isFinishing guard
        recreated.finish();
        Object lock = get(state, "lock");
        synchronized (lock) {
            set(state, "done", true);
            lock.notifyAll();
        }
        // the takeover thread needs real time to wake up and post its
        // delivery; idleMain alone only advances the paused main looper
        Thread.sleep(1500);
        com.gh4a.activities.ActivityTestHelper.idleMain(500);
        assertTrue(recreated.isFinishing());
        assertEquals(View.GONE, recreated.findViewById(R.id.image_view).getVisibility());
        // destroy with isFinishing() true and no in-flight call: onDestroy
        // takes the no-cancel branch
        c.pause().stop().destroy();
    }

    @Test
    public void deliverResult_errorWithoutMessage_showsUnknownMessage() {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        call(activity, "deliverResult",
                new Class<?>[]{Bitmap.class, Exception.class},
                new Object[]{null, new RuntimeException()});
        assertEquals(View.VISIBLE, activity.findViewById(R.id.error_view).getVisibility());
        assertTrue(((android.widget.TextView) activity.findViewById(R.id.error_text))
                .getText().toString().contains(
                        appContext().getString(R.string.image_viewer_error_unknown)));
    }

    @Test
    public void decodeFileWithDownsampling_hugeImage_downsamplesToLimit() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        Bitmap source = Bitmap.createBitmap(5000, 100, Bitmap.Config.ARGB_8888);
        File png = new File(activity.getCacheDir(), "huge.png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(png)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, bytes));
            out.write(bytes.toByteArray());
        }
        Bitmap decoded = (Bitmap) call(activity, "decodeFileWithDownsampling",
                new Class<?>[]{File.class}, new Object[]{png});
        assertNotNull(decoded);
        // 5000 wide needs inSampleSize 2 to get under the 2048 limit
        assertEquals(2500, decoded.getWidth());
    }

    @Test
    public void recreate_takeoverResultDroppedWhenNewerLoadStarts() throws Exception {
        ActivityController<ImageViewerActivity> c =
                controller(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        ImageViewerActivity activity = c.create().start().resume().get();
        Object state = newDownloadState();
        set(activity, "mDownloadState", state);
        c.recreate();
        ImageViewerActivity recreated = c.get();
        // a newer load bumps the generation, so the takeover delivery must
        // be dropped by the generation check
        call(recreated, "loadImage", new Class<?>[]{}, new Object[]{});
        waitFor(() -> recreated.findViewById(R.id.error_view).getVisibility() == View.VISIBLE,
                30000);
        Object lock = get(state, "lock");
        synchronized (lock) {
            set(state, "done", true);
            lock.notifyAll();
        }
        Thread.sleep(1500);
        com.gh4a.activities.ActivityTestHelper.idleMain(500);
        assertEquals(View.GONE, recreated.findViewById(R.id.image_view).getVisibility());
        assertEquals(View.VISIBLE, recreated.findViewById(R.id.error_view).getVisibility());
    }

    @Test
    public void recreate_takeoverResultDroppedWhenDestroyed() throws Exception {
        ActivityController<ImageViewerActivity> c =
                controller(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        ImageViewerActivity activity = c.create().start().resume().get();
        Object state = newDownloadState();
        set(activity, "mDownloadState", state);
        c.recreate();
        ImageViewerActivity recreated = c.get();
        c.pause().stop().destroy();
        assertTrue(recreated.isDestroyed());
        Object lock = get(state, "lock");
        synchronized (lock) {
            set(state, "done", true);
            lock.notifyAll();
        }
        Thread.sleep(1500);
        com.gh4a.activities.ActivityTestHelper.idleMain(500);
        assertEquals(View.GONE, recreated.findViewById(R.id.image_view).getVisibility());
    }

    @Test
    public void decodeFileWithDownsampling_tallImage_downsamplesToLimit() throws Exception {
        ImageViewerActivity activity = launch(ImageViewerActivity.class, intent("https://invalid.invalid/x.png"));
        Bitmap source = Bitmap.createBitmap(100, 5000, Bitmap.Config.ARGB_8888);
        File png = new File(activity.getCacheDir(), "tall.png");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(png)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, bytes));
            out.write(bytes.toByteArray());
        }
        Bitmap decoded = (Bitmap) call(activity, "decodeFileWithDownsampling",
                new Class<?>[]{File.class}, new Object[]{png});
        assertNotNull(decoded);
        // 5000 tall needs inSampleSize 2 to get under the 2048 limit
        assertEquals(2500, decoded.getHeight());
    }

    private Object newDownloadState() throws Exception {
        Class<?> cls = Class.forName("com.gh4a.activities.ImageViewerActivity$DownloadState");
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
}
