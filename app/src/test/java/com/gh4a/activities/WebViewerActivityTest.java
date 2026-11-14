package com.gh4a.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import com.gh4a.R;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.robolectric.shadows.ShadowWebView;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

/**
 * WebViewerActivity 是抽象类：用同包内的具体子类测它的 protected/static 逻辑。
 * 打印流程（doPrint → PrintManager）与外链分发（handleUrlLoad）需系统
 * 服务/真机，离线覆盖不到，见未覆盖清单。
 */
@RunWith(RobolectricTestRunner.class)
public class WebViewerActivityTest {

    public static class TestWebViewerActivity extends WebViewerActivity {
        private String mHtml = "<p>hello</p>";
        private String mTitle = "Doc Title";

        public void setHtml(String html) {
            mHtml = html;
        }

        public void setTitle(String title) {
            mTitle = title;
        }

        @Nullable
        @Override
        protected String getActionBarTitle() {
            return "Test Viewer";
        }

        @Override
        protected String generateHtml(String cssTheme, boolean addTitleHeader) {
            return wrapWithMarkdownStyling(mHtml, cssTheme,
                    addTitleHeader ? mTitle : null);
        }

        @Override
        protected String getDocumentTitle() {
            return mTitle;
        }

        @Override
        protected boolean canSwipeToRefresh() {
            return true;
        }

        // 把 protected 方法暴露给测试
        public String callGenerateMarkdownHtml(String base64, String owner, String repo,
                String ref, String folder, String theme, boolean addTitle) {
            return generateMarkdownHtml(base64, owner, repo, ref, folder, theme, addTitle);
        }

        public android.content.SharedPreferences prefsForTest() {
            return getPrefs();
        }

        public String callGenerateCodeHtml(String data, String fileName,
                int start, int end, String theme, boolean addTitle) {
            return generateCodeHtml(data, fileName, start, end, theme, addTitle);
        }

        public void callOnDataReady() {
            onDataReady();
        }
    }

    private ActivityController<TestWebViewerActivity> launch() {
        return Robolectric.buildActivity(TestWebViewerActivity.class,
                new Intent(androidx.test.core.app.ApplicationProvider.getApplicationContext(),
                        TestWebViewerActivity.class));
    }

    // ---- wrapWithMarkdownStyling（静态） ----

    @Test
    public void wrapWithMarkdownStyling_nullHtml_emptyContentWithSanitizer() {
        String html = WebViewerActivity.wrapWithMarkdownStyling(null, "light", null);
        assertTrue(html.contains("sanitizeHtml"));
        assertTrue(html.contains("sanitizeHtml('');"));
        assertFalse(html.contains("<h2>"));
    }

    @Test
    public void wrapWithMarkdownStyling_withTitle_addsH2() {
        String html = WebViewerActivity.wrapWithMarkdownStyling("<b>x</b>", "dark", "My Doc");
        assertTrue(html.contains("<h2>My Doc</h2>"));
        assertTrue(html.contains("markdown"));
    }

    @Test
    public void wrapWithMarkdownStyling_escapesScriptCloseTag() {
        String html = WebViewerActivity.wrapWithMarkdownStyling(
                "a</script><script>alert(1)</script>", "light", null);
        assertFalse(html.contains("</script><script>"));
        assertTrue(html.contains("<\\/script>"));
        // 原始 HTML 本身不直接拼进页面（只进转义后的 JS 字符串）
        assertFalse(html.contains("alert(1)</script>"));
    }

    @Test
    public void wrapWithMarkdownStyling_escapesQuotesAndBackslashes() {
        String html = WebViewerActivity.wrapWithMarkdownStyling("it's \\ path", "light", null);
        assertTrue(html.contains("it\\'s \\\\ path"));
    }

    @Test
    public void wrapWithMarkdownStyling_escapesNewlines() {
        String html = WebViewerActivity.wrapWithMarkdownStyling("a\nb\rc", "light", null);
        assertTrue(html.contains("a\\nb\\rc"));
    }

    @Test
    public void wrapWithMarkdownStyling_titleHtmlEncoded() {
        String html = WebViewerActivity.wrapWithMarkdownStyling("x", "light", "<b>&\"");
        assertTrue(html.contains("&lt;b&gt;&amp;&quot;"));
    }

    // ---- generateMarkdownHtml ----

    @Test
    public void generateMarkdownHtml_noRepo_noRewrite() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateMarkdownHtml("aGVsbG8=", null, null,
                    null, null, "light", false);
            assertTrue(html.contains("Base64.decode('aGVsbG8=')"));
            assertFalse(html.contains("rewriteRelativeUrls"));
            assertFalse(html.contains("<h2>"));
        }
    }

    @Test
    public void generateMarkdownHtml_withRepoAndNullRef_defaultsToMaster() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateMarkdownHtml("aGVsbG8=", "octocat", "hello",
                    null, "docs", "light", true);
            assertTrue(html.contains(
                    "HtmlUtils.rewriteRelativeUrls(html, 'octocat', 'hello', 'master', 'docs');"));
            assertTrue(html.contains("<h2>Doc Title</h2>"));
        }
    }

    @Test
    public void generateMarkdownHtml_base64NewlinesStripped() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateMarkdownHtml("aGVs\nbG8=\n", "o", "r",
                    "main", "", "light", false);
            assertTrue(html.contains("Base64.decode('aGVsbG8=')"));
        }
    }

    @Test
    public void generateMarkdownHtml_quotesInNamesEscaped() {
        // 148：分支/文件夹名含 ' 时必须转义，否则突破 JS 字符串。
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateMarkdownHtml("eA==", "o'clock", "r",
                    "a'b", "d'e", "light", false);
            assertTrue(html.contains("o\\'clock"));
            assertTrue(html.contains("a\\'b"));
            assertTrue(html.contains("d\\'e"));
        }
    }

    @Test
    public void generateMarkdownHtml_titleHtmlEncoded() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a.setTitle("<Title>&");
            String html = a.callGenerateMarkdownHtml("eA==", "o", "r",
                    "main", "", "light", true);
            assertTrue(html.contains("<title>&lt;Title&gt;&amp;</title>"));
        }
    }

    // ---- generateCodeHtml ----

    @Test
    public void generateCodeHtml_markdownFile_usesHtmlLang() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateCodeHtml("# hi", "README.md", 0, 0, "light", false);
            assertTrue(html.contains("lang-html"));
        }
    }

    @Test
    public void generateCodeHtml_extensionUsedAsLang() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateCodeHtml("class A {}", "A.java", 1, 2, "dark", false);
            assertTrue(html.contains("lang-java"));
            assertTrue(html.contains("highlightLines(1,2)"));
        }
    }

    @Test
    public void generateCodeHtml_noExtensionShebang_usesDefaultLang() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateCodeHtml("#!/bin/sh\necho hi", "script", 0, 0,
                    "light", false);
            assertTrue(html.contains("linenums lang-'>"));
        }
    }

    @Test
    public void generateCodeHtml_noExtensionNoShebang_usesTxtLang() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateCodeHtml("plain text", "notes", 0, 0, "light", false);
            assertTrue(html.contains("lang-txt"));
        }
    }

    @Test
    public void generateCodeHtml_dataHtmlEncoded() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateCodeHtml("<script>alert(1)</script>", "x.html",
                    0, 0, "light", false);
            assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
            assertFalse(html.contains("<script>alert(1)</script>"));
        }
    }

    @Test
    public void generateCodeHtml_withTitle_addsH2AndTitle() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            String html = a.callGenerateCodeHtml("x", "x.txt", 0, 0, "light", true);
            assertTrue(html.contains("<title>Doc Title</title>"));
            assertTrue(html.contains("<h2>Doc Title</h2>"));
        }
    }

    // ---- 杂项行为 ----

    @Test
    public void shouldWrapLines_defaultFalse() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            assertFalse(a.shouldWrapLines());
        }
    }

    @Test
    public void shouldWrapLines_readsPrefs() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a.prefsForTest().edit().putBoolean("line_wrapping", true).apply();
            assertTrue(a.shouldWrapLines());
        }
    }

    @Test
    public void onTouch_actionDown_recordsPositionAndReturnsFalse() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 12f, 34f, 0);
            assertFalse(a.onTouch(null, down));
            assertEquals(12, a.mLastTouchDown.x);
            assertEquals(34, a.mLastTouchDown.y);
            down.recycle();
            MotionEvent up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 99f, 99f, 0);
            assertFalse(a.onTouch(null, up));
            assertEquals(12, a.mLastTouchDown.x);
            up.recycle();
        }
    }

    @Test
    public void onCreateOptionsMenu_noData_removesBrowserItem() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            MenuBuilder menu = new MenuBuilder(a);
            menu.add(Menu.NONE, R.id.browser, Menu.NONE, "browser");
            a.onCreateOptionsMenu(menu);
            assertNull(menu.findItem(R.id.browser));
            assertNotNull(menu.findItem(R.id.detach));
        }
    }

    @Test
    public void onCreateOptionsMenu_withData_inflatesPrintMenu() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a.callOnDataReady();
            MenuBuilder menu = new MenuBuilder(a);
            a.onCreateOptionsMenu(menu);
            assertNotNull(menu.findItem(R.id.print));
        }
    }

    @Test
    public void onPrepareOptionsMenu_wrapItemReflectsPrefs() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a.prefsForTest().edit().putBoolean("line_wrapping", true).apply();
            MenuBuilder menu = new MenuBuilder(a);
            menu.add(Menu.NONE, R.id.wrap, Menu.NONE, "wrap");
            a.onPrepareOptionsMenu(menu);
            assertTrue(menu.findItem(R.id.wrap).isChecked());
        }
    }

    @Test
    public void displayDetachAction_isTrue() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            assertTrue(a.displayDetachAction());
        }
    }

    @Test
    public void constants_haveExpectedValues() {
        assertEquals("dark", WebViewerActivity.DARK_CSS_THEME);
        assertEquals("light", WebViewerActivity.LIGHT_CSS_THEME);
        assertEquals("print", WebViewerActivity.PRINT_CSS_THEME);
    }

    private static WebView webView(TestWebViewerActivity activity) throws Exception {
        Field f = WebViewerActivity.class.getDeclaredField("mWebView");
        f.setAccessible(true);
        return (WebView) f.get(activity);
    }

    private static WebViewClient webViewClient(TestWebViewerActivity activity) throws Exception {
        Field f = WebViewerActivity.class.getDeclaredField("mWebViewClient");
        f.setAccessible(true);
        return (WebViewClient) f.get(activity);
    }

    private static boolean boolField(Object target, String name) throws Exception {
        Field f = WebViewerActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(target);
    }

    @Test
    public void onPageFinished_marksPageFinished() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            webViewClient(activity).onPageFinished(webView(activity), "file:///android_asset/");
            assertTrue(boolField(activity, "mPageFinished"));
        }
    }

    @Test
    public void shouldOverrideUrlLoading_externalUrl_returnsTrueAndToasts() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            assertTrue(webViewClient(activity)
                    .shouldOverrideUrlLoading(webView(activity), "https://example.com/x"));
            assertEquals(activity.getString(R.string.no_browser_found),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void shouldOverrideUrlLoading_githubUrl_opensRepositoryActivity() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            assertTrue(webViewClient(activity).shouldOverrideUrlLoading(webView(activity),
                    "https://github.com/octocat/hello"));
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(RepositoryActivity.class.getName(),
                    next.getComponent().getClassName());
        }
    }

    @Test
    public void onOptionsItemSelected_wrap_togglesLineWrappingPref() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.wrap, 0, "Wrap");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.wrap)));
            assertTrue(activity.prefsForTest().getBoolean("line_wrapping", false));
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.wrap)));
            assertFalse(activity.prefsForTest().getBoolean("line_wrapping", true));
        }
    }

    @Test
    public void onOptionsItemSelected_search_opensFindActionMode() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            MenuBuilder menu = new MenuBuilder(activity);
            menu.add(0, R.id.search, 0, "Search");
            assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.search)));
            assertNotNull(activity.findViewById(androidx.appcompat.R.id.action_mode_bar));
        }
    }

    @Test
    public void setContentShown_false_resetsFlags() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            activity.setContentShown(false);
            assertFalse(boolField(activity, "mHasData"));
            assertFalse(boolField(activity, "mRenderingDone"));
            assertFalse(boolField(activity, "mPageFinished"));
        }
    }

    @Test
    public void nativeClient_onRenderingDone_marksRenderingDone() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            Class<?> cls = Class.forName(
                    "com.gh4a.activities.WebViewerActivity$NativeClientJavascriptInterface");
            java.lang.reflect.Constructor<?> ctor =
                    cls.getDeclaredConstructor(WebViewerActivity.class);
            ctor.setAccessible(true);
            Object iface = ctor.newInstance(activity);
            cls.getMethod("onRenderingDone").invoke(iface);
            ShadowLooper.idleMainLooper();
            assertTrue(boolField(activity, "mRenderingDone"));
        }
    }

    @Test
    public void base64Interface_decode_returnsDecoded() throws Exception {
        Class<?> cls = Class.forName(
                "com.gh4a.activities.WebViewerActivity$Base64JavascriptInterface");
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object iface = ctor.newInstance();
        assertEquals("hello", cls.getMethod("decode", String.class)
                .invoke(iface, "aGVsbG8="));
    }

    @Test
    public void htmlUtilsInterface_rewriteRelativeUrls_rewrites() throws Exception {
        Class<?> cls = Class.forName(
                "com.gh4a.activities.WebViewerActivity$HtmlUtilsJavascriptInterface");
        java.lang.reflect.Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object iface = ctor.newInstance();
        String out = (String) cls.getMethod("rewriteRelativeUrls", String.class,
                String.class, String.class, String.class, String.class)
                .invoke(iface, "<a href=\"/x\">", "octocat", "hello", "main", "");
        assertTrue(out.contains("octocat/hello"));
    }

    @Test
    public void onStop_setsStartedFalse() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity activity = c.setup().get();
            assertTrue(boolField(activity, "mStarted"));
            c.stop();
            assertFalse(boolField(activity, "mStarted"));
        }
    }

    // ---- A2 第二轮补缺口：WebViewerActivity ----

    private static java.lang.reflect.Field a2FindField(Class<?> cls, String name) throws Exception {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object a2GetField(Object target, String name) throws Exception {
        java.lang.reflect.Field f = a2FindField(target.getClass(), name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void a2SetField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field f = a2FindField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    public void shouldOverrideUrlLoading_requestOverload_notStarted_returnsTrueWithoutHandling()
            throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.create().get();
            assertFalse(boolField(a, "mStarted"));
            android.webkit.WebView webView = (android.webkit.WebView) a2GetField(a, "mWebView");
            android.webkit.WebViewClient client =
                    (android.webkit.WebViewClient) a2GetField(a, "mWebViewClient");
            android.webkit.WebResourceRequest request =
                    mock(android.webkit.WebResourceRequest.class);
            when(request.getUrl()).thenReturn(android.net.Uri.parse("https://example.com/x"));
            assertTrue(client.shouldOverrideUrlLoading(webView, request));
            assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        }
    }

    @Test
    public void canChildScrollUp_noContent_returnsFalse() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            assertFalse(a.canChildScrollUp());
        }
    }

    @Test
    public void onCreateOptionsMenu_noData_doesNotInflatePrintMenu() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            assertFalse(boolField(a, "mHasData"));
            MenuBuilder menu = new MenuBuilder(a);
            a.onCreateOptionsMenu(menu);
            assertNull(menu.findItem(R.id.print));
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            MenuBuilder menu = new MenuBuilder(a);
            menu.add(0, 999999, 0, "Unknown");
            assertFalse(a.onOptionsItemSelected(menu.findItem(999999)));
        }
    }

    @Test
    public void onOptionsItemSelected_search_nullWebView_returnsTrueWithoutActionMode()
            throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a2SetField(a, "mWebView", null);
            MenuBuilder menu = new MenuBuilder(a);
            menu.add(0, R.id.search, 0, "Search");
            assertTrue(a.onOptionsItemSelected(menu.findItem(R.id.search)));
            assertNull(a.findViewById(androidx.appcompat.R.id.action_mode_bar));
        }
    }

    @Test
    public void escapeJsSingleQuoted_nullAndSpecialChars() throws Exception {
        java.lang.reflect.Method m = WebViewerActivity.class.getDeclaredMethod(
                "escapeJsSingleQuoted", String.class);
        m.setAccessible(true);
        assertEquals("", m.invoke(null, new Object[]{null}));
        assertEquals("a\\'b\\nc", m.invoke(null, "a'b\nc"));
        assertEquals("plain", m.invoke(null, "plain"));
    }

    @Test
    public void onDataReady_requiresNativeClient_registersInterface() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a2SetField(a, "mRequiresNativeClient", true);
            a.callOnDataReady();
            assertTrue(boolField(a, "mHasData"));
            android.webkit.WebView webView = (android.webkit.WebView) a2GetField(a, "mWebView");
            assertNotNull(Shadows.shadowOf(webView).getJavascriptInterface("NativeClient"));
        }
    }

    @Test
    public void onLineTouched_baseMethod_doesNotCrash() {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a.onLineTouched(3, 10, 20);
        }
    }

    @Test
    public void nativeClient_onLineTouched_postsToHandler() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            a2SetField(a, "mRequiresNativeClient", true);
            a.callOnDataReady();
            android.webkit.WebView webView = (android.webkit.WebView) a2GetField(a, "mWebView");
            Object nativeClient =
                    Shadows.shadowOf(webView).getJavascriptInterface("NativeClient");
            assertNotNull(nativeClient);
            java.lang.reflect.Method onLineTouched =
                    nativeClient.getClass().getDeclaredMethod("onLineTouched", int.class);
            onLineTouched.setAccessible(true);
            onLineTouched.invoke(nativeClient, 5);
            ShadowLooper.idleMainLooper();
        }
    }


    // ---- A2 第三轮补缺口：WebViewerActivity ----

    @Test
    public void shouldOverrideUrlLoading_requestOverload_started_handlesUrl() throws Exception {
        try (ActivityController<TestWebViewerActivity> c = launch()) {
            TestWebViewerActivity a = c.setup().get();
            assertTrue(boolField(a, "mStarted"));
            android.webkit.WebView webView = (android.webkit.WebView) a2GetField(a, "mWebView");
            android.webkit.WebViewClient client =
                    (android.webkit.WebViewClient) a2GetField(a, "mWebViewClient");
            android.webkit.WebResourceRequest request =
                    mock(android.webkit.WebResourceRequest.class);
            when(request.getUrl()).thenReturn(android.net.Uri.parse("https://example.com/x"));
            assertTrue(client.shouldOverrideUrlLoading(webView, request));
        }
    }

}
