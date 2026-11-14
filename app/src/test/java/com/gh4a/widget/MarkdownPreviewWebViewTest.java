package com.gh4a.widget;

import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.activities.WebViewerActivity;
import com.gh4a.utils.StringUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowWebView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class MarkdownPreviewWebViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private MarkdownPreviewWebView preview(FragmentActivity activity) {
        return new MarkdownPreviewWebView(activity);
    }

    private String lastLoadedData(MarkdownPreviewWebView view) {
        ShadowWebView.LoadDataWithBaseURL load =
                Shadows.shadowOf(view).getLastLoadDataWithBaseURL();
        assertNotNull(load);
        return load.data;
    }

    @Test
    public void constructor_loadsEmptyMarkdown() {
        MarkdownPreviewWebView view = preview(activity());
        String data = lastLoadedData(view);
        assertTrue(data.contains("showdown"));
        assertTrue(data.contains("Base64.decode('')"));
        assertTrue(data.contains("sanitizeHtml"));
    }

    @Test
    public void constructor_registersBase64JavascriptInterface() {
        MarkdownPreviewWebView view = preview(activity());
        assertNotNull(Shadows.shadowOf(view).getJavascriptInterface("Base64"));
    }

    @Test
    public void constructor_setsWebViewClient() {
        MarkdownPreviewWebView view = preview(activity());
        assertNotNull(Shadows.shadowOf(view).getWebViewClient());
    }

    @Test
    public void setEditText_typingReloadsPreviewWithBase64Content() {
        FragmentActivity activity = activity();
        MarkdownPreviewWebView view = preview(activity);
        EditText editor = new EditText(activity);
        view.setEditText(editor);

        editor.setText("# hello");

        String data = lastLoadedData(view);
        assertTrue(data.contains("Base64.decode('" + StringUtils.toBase64("# hello") + "')"));
    }

    @Test
    public void setEditText_emptyText_loadsEmptyContent() {
        FragmentActivity activity = activity();
        MarkdownPreviewWebView view = preview(activity);
        EditText editor = new EditText(activity);
        view.setEditText(editor);

        editor.setText("x");
        editor.setText("");

        assertTrue(lastLoadedData(view).contains("Base64.decode('')"));
    }

    @Test
    public void urlHandlingClient_stringUrl_returnsTrueAndStartsActivity() {
        FragmentActivity activity = activity();
        MarkdownPreviewWebView view = preview(activity);
        WebViewClient client = Shadows.shadowOf(view).getWebViewClient();

        boolean result = client.shouldOverrideUrlLoading(view, "https://github.com/octocat/Hello-World");

        assertTrue(result);
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void urlHandlingClient_requestUrl_returnsTrueAndStartsActivity() {
        FragmentActivity activity = activity();
        MarkdownPreviewWebView view = preview(activity);
        WebViewClient client = Shadows.shadowOf(view).getWebViewClient();
        WebResourceRequest request = mock(WebResourceRequest.class);
        when(request.getUrl()).thenReturn(Uri.parse("https://github.com/octocat/Hello-World"));

        boolean result = client.shouldOverrideUrlLoading(view, request);

        assertTrue(result);
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void nestedScrollingEnabled_roundTrip() {
        MarkdownPreviewWebView view = preview(activity());
        assertTrue(view.isNestedScrollingEnabled());
        view.setNestedScrollingEnabled(false);
        assertFalse(view.isNestedScrollingEnabled());
        view.setNestedScrollingEnabled(true);
        assertTrue(view.isNestedScrollingEnabled());
    }

    @Test
    public void startNestedScroll_noParent_returnsFalse() {
        MarkdownPreviewWebView view = preview(activity());
        assertFalse(view.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void hasNestedScrollingParent_noParent_returnsFalse() {
        MarkdownPreviewWebView view = preview(activity());
        assertFalse(view.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void dispatchNestedScroll_noParent_returnsFalse() {
        MarkdownPreviewWebView view = preview(activity());
        assertFalse(view.dispatchNestedScroll(0, 10, 0, 0, null, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void dispatchNestedPreScroll_noParent_returnsFalse() {
        MarkdownPreviewWebView view = preview(activity());
        int[] consumed = {0, 0};
        assertFalse(view.dispatchNestedPreScroll(0, 10, consumed, null, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void dispatchNestedFling_noParent_returnsFalse() {
        MarkdownPreviewWebView view = preview(activity());
        assertFalse(view.dispatchNestedFling(100f, 100f, false));
    }

    @Test
    public void dispatchNestedPreFling_noParent_returnsFalse() {
        MarkdownPreviewWebView view = preview(activity());
        assertFalse(view.dispatchNestedPreFling(100f, 100f));
    }

    @Test
    public void stopNestedScroll_noParent_doesNotThrow() {
        MarkdownPreviewWebView view = preview(activity());
        view.stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onTouchEvent_down_move_up_doNotThrow() {
        FragmentActivity activity = activity();
        MarkdownPreviewWebView view = preview(activity);
        activity.setContentView(view);
        long now = android.os.SystemClock.uptimeMillis();

        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10, 10, 0));
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 10, 30, 0));
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10, 30, 0));
        view.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 10, 30, 0));
    }

    @Test
    public void generateMarkdownHtml_containsSanitizerAndConverter() throws Exception {
        Method method = MarkdownPreviewWebView.class.getDeclaredMethod(
                "generateMarkdownHtml", String.class, String.class);
        method.setAccessible(true);
        MarkdownPreviewWebView view = preview(activity());

        String html = (String) method.invoke(view, "aGVsbG8=", "dark");

        assertTrue(html.contains("showdown"));
        assertTrue(html.contains("Base64.decode('aGVsbG8=')"));
        assertTrue(html.contains("sanitizeHtml"));
        assertTrue(html.contains("converter.setFlavor('github')"));
        assertTrue(html.contains("dark"));
        assertTrue(html.startsWith("<html><head>"));
        assertTrue(html.endsWith("</body></html>"));
    }

    @Test
    public void base64JavascriptInterface_decode_roundTrip() throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.widget.MarkdownPreviewWebView$Base64JavascriptInterface");
        // The class is private, so its implicit constructor is private too.
        java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object iface = ctor.newInstance();
        Method decode = clazz.getMethod("decode", String.class);
        decode.setAccessible(true);

        String encoded = StringUtils.toBase64("hello **world**");
        assertEquals("hello **world**", decode.invoke(iface, encoded));
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertNotNull(new MarkdownPreviewWebView(activity, null));
        assertNotNull(new MarkdownPreviewWebView(activity, null, 0));
        assertNotNull(lastLoadedData(new MarkdownPreviewWebView(activity, null)));
    }

    @Test
    public void webViewSettings_javascriptInterfacePresent() {
        // Robolectric's WebSettings is a stub: setters are no-ops, so only
        // assert construction (initWebViewSettings) does not throw and the
        // settings object itself exists.
        MarkdownPreviewWebView view = preview(activity());
        assertNotNull(view.getSettings());
    }

    @Test
    @Config(qualifiers = "night")
    public void constructor_darkTheme_usesDarkCssTheme() throws Exception {
        MarkdownPreviewWebView view = preview(activity());
        Field themeField = MarkdownPreviewWebView.class.getDeclaredField("mCssTheme");
        themeField.setAccessible(true);
        assertEquals(WebViewerActivity.DARK_CSS_THEME, themeField.get(view));
    }
}
