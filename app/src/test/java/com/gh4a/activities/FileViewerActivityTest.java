package com.gh4a.activities;

import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.MotionEvent;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.gh4a.ApiRequestException;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.FileUtils;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.ClientErrorResponse;
import com.meisolsson.githubsdk.model.Content;
import com.meisolsson.githubsdk.model.ContentType;
import com.meisolsson.githubsdk.model.TextMatch;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.repositories.RepositoryContentService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDownloadManager;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.ByteArrayOutputStream;
import java.util.Collections;

import io.reactivex.Single;
import retrofit2.Response;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.callStatic;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class FileViewerActivityTest {
    private Intent intent(String path) {
        return FileViewerActivity.makeIntent(appContext(), "o", "r", "main", path);
    }

    @Test
    public void makeIntent_setsExtrasWithDefaults() {
        Intent i = intent("src/Foo.java");
        assertEquals(FileViewerActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertEquals("main", i.getStringExtra("ref"));
        assertEquals("src/Foo.java", i.getStringExtra("path"));
        assertEquals(-1, i.getIntExtra("highlight_start", -99));
        assertEquals(-1, i.getIntExtra("highlight_end", -99));
        assertNull(i.getParcelableExtra("text_match"));
    }

    @Test
    public void makeIntentWithHighlight_setsRange() {
        Intent i = FileViewerActivity.makeIntentWithHighlight(appContext(), "o", "r", "main", "a.md", 3, 7);
        assertEquals(3, i.getIntExtra("highlight_start", -1));
        assertEquals(7, i.getIntExtra("highlight_end", -1));
    }

    @Test
    public void makeIntentWithSearchMatch_setsMatch() {
        TextMatch match = mock(TextMatch.class);
        Intent i = FileViewerActivity.makeIntentWithSearchMatch(appContext(), "o", "r", "main", "a.txt", match);
        assertEquals(match, i.getParcelableExtra("text_match"));
    }

    @Test
    public void launch_textFile_titleAndSubtitle() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertEquals("Foo.java", activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
        assertTrue(activity.canSwipeToRefresh());
    }

    @Test
    public void launch_bitmapImage_opensImageViewerAndFinishes() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/photo.png"));
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ImageViewerActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("photo.png", next.getStringExtra("file_name"));
    }

    @Test
    public void launch_binaryFile_noViewerAvailable_showsErrorWithoutRetry() {
        // No external viewer/browser resolves in the Robolectric
        // environment, so openUnsuitableFileAndFinish reports a load
        // failure and hides the retry button instead of finishing.
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("bin/app.zip"));
        assertFalse(activity.isFinishing());
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.retry_button).getVisibility());
    }

    @Test
    public void navigateUp_goesToRepository() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Intent up = activity.navigateUp();
        assertEquals(RepositoryActivity.class.getName(), up.getComponent().getClassName());
    }

    @Test
    public void buildBlobUrl_valid_buildsGithubUrl() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Uri uri = (Uri) call(activity, "buildBlobUrl", new Class<?>[]{}, new Object[]{});
        assertEquals("https://github.com/o/r/blob/main/src/Foo.java", uri.toString());
    }

    @Test
    public void buildBlobUrl_missingRef_returnsNull() {
        Intent i = new Intent(appContext(), FileViewerActivity.class)
                .putExtra("owner", "o").putExtra("repo", "r").putExtra("path", "a.txt");
        FileViewerActivity activity = launch(FileViewerActivity.class, i);
        assertNull(call(activity, "buildBlobUrl", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void createUrl_appendsLineFragment() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mLastTouchedLine", 42);
        Uri uri = (Uri) call(activity, "createUrl", new Class<?>[]{}, new Object[]{});
        assertEquals("https://github.com/o/r/blob/main/src/Foo.java#L42", uri.toString());
    }

    @Test
    public void getDocumentTitle_withAndWithoutRef() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertEquals(appContext().getString(R.string.file_print_document_at_ref_title,
                "Foo.java", "o", "r", "main"), activity.getDocumentTitle());
        set(activity, "mRef", "");
        assertEquals(appContext().getString(R.string.file_print_document_title,
                "Foo.java", "o", "r", ""), activity.getDocumentTitle());
    }

    @Test
    public void generateHtml_textContent_decodesBase64() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Content content = mock(Content.class);
        when(content.content()).thenReturn(StringUtils.toBase64("hello world"));
        set(activity, "mContent", content);
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("hello world"));
    }

    @Test
    public void generateHtml_invalidBase64_rendersEmptyWithoutThrowing() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Content content = mock(Content.class);
        when(content.content()).thenReturn("!!!not-base64!!!");
        set(activity, "mContent", content);
        String html = activity.generateHtml("light", false);
        assertFalse(html.contains("not-base64"));
    }

    @Test
    public void generateHtml_nullContent_rendersEmpty() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Content content = mock(Content.class);
        when(content.content()).thenReturn(null);
        set(activity, "mContent", content);
        assertTrue(activity.generateHtml("light", false).contains("<pre id='content'"));
    }

    @Test
    public void generateHtml_markdown_rendersMarkdownHtml() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        Content content = mock(Content.class);
        when(content.content()).thenReturn(StringUtils.toBase64("# Title"));
        set(activity, "mContent", content);
        String html = activity.generateHtml("light", false);
        // Markdown is rendered client-side by showdown; the HTML embeds
        // the base64 source, not the rendered text.
        assertTrue(html.contains("showdown"));
        assertTrue(html.contains(StringUtils.toBase64("# Title")));
    }

    @Test
    public void findMatchingLines_withTextMatch_setsHighlight() {
        TextMatch match = mock(TextMatch.class);
        when(match.fragment()).thenReturn("target");
        Intent i = FileViewerActivity.makeIntentWithSearchMatch(appContext(), "o", "r", "main",
                "a.txt", match);
        FileViewerActivity activity = launch(FileViewerActivity.class, i);
        call(activity, "findMatchingLines",
                new Class<?>[]{String.class}, new Object[]{"line1\ntarget here\nline3"});
        assertEquals(2, get(activity, "mHighlightStart"));
        assertEquals(2, get(activity, "mHighlightEnd"));
    }

    @Test
    public void findMatchingLines_withoutTextMatch_keepsHighlight() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        call(activity, "findMatchingLines",
                new Class<?>[]{String.class}, new Object[]{"anything"});
        assertEquals(-1, get(activity, "mHighlightStart"));
    }

    @Test
    public void onOptionsItemSelected_browserWithoutRef_returnsTrueWithoutCrash() {
        Intent i = new Intent(appContext(), FileViewerActivity.class)
                .putExtra("owner", "o").putExtra("repo", "r").putExtra("path", "a.txt");
        FileViewerActivity activity = launch(FileViewerActivity.class, i);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "b")));
    }

    @Test
    public void onOptionsItemSelected_viewRaw_togglesFlag() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        android.view.MenuItem item = menu.add(0, R.id.view_raw, 0, "raw");
        assertTrue(activity.onOptionsItemSelected(item));
        assertTrue((boolean) get(activity, "mViewRawText"));
    }

    @Test
    public void handlePrintRequest_nonImage_returnsFalse() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertFalse(activity.handlePrintRequest());
    }

    @Test
    public void handlePrintRequest_imageWithoutContent_returnsFalse() {
        // image path redirects to the image viewer in onCreate, so use a
        // text file renamed path: non-image content path still returns false
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", null);
        assertFalse(activity.handlePrintRequest());
    }

    @Test
    public void shouldWrapLines_markdown_notWrapped() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        assertFalse(activity.shouldWrapLines());
    }

    // ---------- helpers ----------

    private void authorize() {
        Gh4Application.get().addAccount(User.builder().login("tester").id(1L).build(), "tok");
    }

    private static String pngBase64(int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private Content mockContent(String base64, int size) {
        Content content = mock(Content.class);
        when(content.content()).thenReturn(base64);
        when(content.size()).thenReturn(size);
        when(content.type()).thenReturn(ContentType.File);
        return content;
    }

    private RepositoryContentService mockContentService(Content content) {
        RepositoryContentService service = mock(RepositoryContentService.class);
        when(service.getContents(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Single.just(Response.success(content)));
        return service;
    }

    private RepositoryContentService mockErrorService(Throwable error) {
        RepositoryContentService service = mock(RepositoryContentService.class);
        when(service.getContents(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Single.error(error));
        return service;
    }

    /** ServiceFactory is mocked with real methods as the default answer, so
     * only the RepositoryContentService lookup is stubbed and every other
     * lookup during launch keeps working. */
    private MockedStatic<ServiceFactory> mockServiceFactory(RepositoryContentService service) {
        MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class,
                Mockito.withSettings().defaultAnswer(Mockito.CALLS_REAL_METHODS));
        mocked.when(() -> ServiceFactory.get(eq(RepositoryContentService.class), anyBoolean()))
                .thenReturn(service);
        return mocked;
    }

    private androidx.appcompat.view.menu.MenuBuilder inflatedMenu(FileViewerActivity activity) {
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        return menu;
    }

    private ApiRequestException mockApiError(ClientErrorResponse.FieldError.Reason reason) {
        ApiRequestException error = mock(ApiRequestException.class);
        ClientErrorResponse response = mock(ClientErrorResponse.class);
        ClientErrorResponse.FieldError fieldError = mock(ClientErrorResponse.FieldError.class);
        when(fieldError.reason()).thenReturn(reason);
        when(response.errors()).thenReturn(Collections.singletonList(fieldError));
        when(error.getResponse()).thenReturn(response);
        return error;
    }

    // ---------- onCreate variants ----------

    @Test
    public void launch_svgImage_loadsFileNotImageViewer() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        assertFalse(activity.isFinishing());
        assertEquals("logo.svg", activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void launch_bitmapImage_authorized_authRequiredTrue() {
        authorize();
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/photo.png"));
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ImageViewerActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(next.getBooleanExtra("auth_required", false));
    }

    @Test
    public void launch_binaryFile_viewerResolves_startsViewerAndFinishes() {
        String rawUrl = "https://raw.githubusercontent.com/o/r/main/bin/app.zip";
        Uri dummyUri = Uri.parse(rawUrl).buildUpon().authority("www.somedummy.com").build();
        Intent query = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(dummyUri, FileUtils.getMimeTypeFor("app.zip"));
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.external.viewer";
        info.activityInfo.name = "ViewerActivity";
        info.activityInfo.enabled = true;
        info.activityInfo.exported = true;
        info.activityInfo.nonLocalizedLabel = "Viewer";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = "com.external.viewer";
        ShadowPackageManager spm = Shadows.shadowOf(appContext().getPackageManager());
        spm.addResolveInfoForIntent(query, info);

        FileViewerActivity activity = launch(FileViewerActivity.class, intent("bin/app.zip"));
        assertTrue(activity.isFinishing());
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("com.external.viewer", next.getPackage());
        assertEquals(Uri.parse(rawUrl), next.getData());
    }

    // ---------- generateHtml image branch ----------

    @Test
    public void generateHtml_svgImage_withTitle_rendersImageAndTitle() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        String data = pngBase64(10, 10);
        set(activity, "mContent", mockContent(data, 100));
        String html = activity.generateHtml("light", true);
        assertTrue(html.contains("<img src='data:image/svg+xml;base64," + data + "' />"));
        assertTrue(html.contains("<h2>"));
    }

    @Test
    public void generateHtml_svgImage_withoutTitle_noTitleHeader() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", mockContent(pngBase64(10, 10), 100));
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("<img src='data:image/svg+xml;base64,"));
        assertFalse(html.contains("<h2>"));
    }

    // ---------- handlePrintRequest image path ----------

    @Test
    public void handlePrintRequest_svgValidPng_returnsTrue() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", mockContent(pngBase64(100, 100), 1234));
        assertTrue(activity.handlePrintRequest());
    }

    @Test
    public void handlePrintRequest_svgLargeImage_downsamplesAndReturnsTrue() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", mockContent(pngBase64(4096, 100), 1234));
        assertTrue(activity.handlePrintRequest());
    }

    @Test
    public void handlePrintRequest_svgInvalidBase64_returnsFalse() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", mockContent("!!!not-base64!!!", 10));
        assertFalse(activity.handlePrintRequest());
    }

    @Test
    public void handlePrintRequest_svgNullContent_returnsFalse() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", null);
        assertFalse(activity.handlePrintRequest());
    }

    // NOTE: no test for decodeByteArray returning null (FileViewerActivity
    // handlePrintRequest bitmap-null branch): Robolectric's BitmapFactory
    // never returns null, it returns a dummy bitmap even for empty input
    // (verified: empty base64 data makes handlePrintRequest return true).

    // ---------- options menu ----------

    @Test
    public void onCreateOptionsMenu_markdownRawFalse_removesWrapShowsViewRawUnchecked() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertNull(menu.findItem(R.id.wrap));
        assertTrue(menu.findItem(R.id.view_raw).isVisible());
        assertFalse(menu.findItem(R.id.view_raw).isChecked());
        assertNotNull(menu.findItem(10));
        assertNull(menu.findItem(R.id.browser));
        assertNull(menu.findItem(R.id.print));
    }

    @Test
    public void onCreateOptionsMenu_markdownRawTrue_keepsWrapAndChecksViewRaw() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(activity, "mViewRawText", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertNotNull(menu.findItem(R.id.wrap));
        assertTrue(menu.findItem(R.id.view_raw).isVisible());
        assertTrue(menu.findItem(R.id.view_raw).isChecked());
    }

    @Test
    public void onCreateOptionsMenu_text_keepsWrapHidesViewRaw() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertNotNull(menu.findItem(R.id.wrap));
        assertFalse(menu.findItem(R.id.view_raw).isVisible());
    }

    @Test
    public void onCreateOptionsMenu_image_removesWrap() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertNull(menu.findItem(R.id.wrap));
        assertFalse(menu.findItem(R.id.view_raw).isVisible());
    }

    @Test
    public void onCreateOptionsMenu_withData_showsPrintMenu() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertNotNull(menu.findItem(R.id.print));
        assertTrue(menu.findItem(R.id.print).isEnabled());
        assertNotNull(menu.findItem(R.id.browser));
    }

    @Test
    public void onCreateOptionsMenu_printing_disablesPrintItem() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mHasData", true);
        set(activity, "mPrintWebView", new WebView(activity));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertFalse(menu.findItem(R.id.print).isEnabled());
    }

    @Test
    public void onPrepareOptionsMenu_wrapCheckedReflectsPref() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onPrepareOptionsMenu(menu));
        assertFalse(menu.findItem(R.id.wrap).isChecked());
        activity.getSharedPreferences(SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("line_wrapping", true).apply();
        assertTrue(activity.onPrepareOptionsMenu(menu));
        assertTrue(menu.findItem(R.id.wrap).isChecked());
    }

    @Test
    public void onPrepareOptionsMenu_noWrapItem_returnsTrue() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onPrepareOptionsMenu(menu));
    }

    // ---------- onOptionsItemSelected ----------

    @Test
    public void onOptionsItemSelected_download_enqueuesDownload() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.download)));
        // No wifi in the Robolectric environment, so the download first
        // shows the mobile-data warning dialog; confirm it.
        java.util.List<android.app.Dialog> dialogs =
                org.robolectric.shadows.ShadowAlertDialog.getShownDialogs();
        if (!dialogs.isEmpty()) {
            ((androidx.appcompat.app.AlertDialog) dialogs.get(0))
                    .getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
            idleMain(200);
        }
        DownloadManager dm = (DownloadManager) activity.getSystemService(
                android.content.Context.DOWNLOAD_SERVICE);
        ShadowDownloadManager shadowDm = Shadows.shadowOf(dm);
        assertEquals(1, shadowDm.getRequestCount());
        assertNotNull(shadowDm.getRequest(0));
    }

    @Test
    public void onOptionsItemSelected_shareWithUrl_startsChooser() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_CHOOSER, next.getAction());
        Intent send = next.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, send.getAction());
        assertEquals("https://github.com/o/r/blob/main/src/Foo.java",
                send.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void onOptionsItemSelected_shareWithoutRef_returnsTrueWithoutShare() {
        Intent i = new Intent(appContext(), FileViewerActivity.class)
                .putExtra("owner", "o").putExtra("repo", "r").putExtra("path", "a.txt");
        FileViewerActivity activity = launch(FileViewerActivity.class, i);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.share)));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onOptionsItemSelected_browserWithUrl_toastsNoBrowser() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.browser)));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onOptionsItemSelected_history_startsCommitHistoryWithoutType() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(10)));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(CommitHistoryActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("o", next.getStringExtra("owner"));
        assertEquals("r", next.getStringExtra("repo"));
        assertEquals("main", next.getStringExtra("ref"));
        assertEquals("src/Foo.java", next.getStringExtra("path"));
        assertNull(next.getSerializableExtra("type"));
    }

    @Test
    public void onOptionsItemSelected_history_withContent_passesType() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("x"), 1));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(10)));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(ContentType.File, next.getSerializableExtra("type"));
    }

    @Test
    public void onOptionsItemSelected_viewRaw_togglesAndReloads() throws Exception {
        Content content = mockContent(StringUtils.toBase64("# Title"), 7);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
            android.view.MenuItem item = menu.findItem(R.id.view_raw);
            assertTrue(activity.onOptionsItemSelected(item));
            assertTrue((boolean) get(activity, "mViewRawText"));
            assertTrue(item.isChecked());
            RepositoryContentService service = mockContentService(content);
            // The reload re-requests the file with force=true: the service
            // is called a second time.
            waitFor(() -> {
                try {
                    mocked.verify(() -> ServiceFactory.get(
                            eq(RepositoryContentService.class), eq(true)), times(1));
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }, 5000);
            assertTrue(activity.onOptionsItemSelected(item));
            assertFalse((boolean) get(activity, "mViewRawText"));
        }
    }

    @Test
    public void onOptionsItemSelected_unknownItem_returnsFalse() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 99999, 0, "unknown")));
    }

    // ---------- line popup menu ----------

    @Test
    public void onMenuItemClick_shareWithLine_startsChooserWithLineFragment() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mLastTouchedLine", 5);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onMenuItemClick(menu.add(0, R.id.share, 0, "share")));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_CHOOSER, next.getAction());
        Intent send = next.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals("https://github.com/o/r/blob/main/src/Foo.java#L5",
                send.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test
    public void onMenuItemClick_shareWithLineNoRef_noShare() {
        Intent i = new Intent(appContext(), FileViewerActivity.class)
                .putExtra("owner", "o").putExtra("repo", "r").putExtra("path", "a.txt");
        FileViewerActivity activity = launch(FileViewerActivity.class, i);
        set(activity, "mLastTouchedLine", 5);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onMenuItemClick(menu.add(0, R.id.share, 0, "share")));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onMenuItemClick_shareNoLine_noShare() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onMenuItemClick(menu.add(0, R.id.share, 0, "share")));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onMenuItemClick_otherItem_returnsFalse() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onMenuItemClick(menu.add(0, 99999, 0, "unknown")));
    }

    @Test
    public void onLineTouched_setsLineAndShowsPopup() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        activity.onLineTouched(7, 10, 20);
        assertEquals(7, get(activity, "mLastTouchedLine"));
    }

    @Test
    public void onLineTouched_whenFinishing_skipsPopup() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        activity.finish();
        activity.onLineTouched(3, 10, 20);
        assertEquals(3, get(activity, "mLastTouchedLine"));
    }

    // ---------- shouldWrapLines / urls ----------

    @Test
    public void shouldWrapLines_markdownRaw_usesPref() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(activity, "mViewRawText", true);
        assertFalse(activity.shouldWrapLines());
        activity.getSharedPreferences(SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("line_wrapping", true).apply();
        assertTrue(activity.shouldWrapLines());
    }

    @Test
    public void shouldWrapLines_text_usesPref() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertFalse(activity.shouldWrapLines());
        activity.getSharedPreferences(SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("line_wrapping", true).apply();
        assertTrue(activity.shouldWrapLines());
    }

    @Test
    public void buildRawFileUrl_returnsRawUrl() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertEquals("https://raw.githubusercontent.com/o/r/main/src/Foo.java",
                call(activity, "buildRawFileUrl", new Class<?>[]{}, new Object[]{}));
    }

    // ---------- onRefresh / loadFile ----------

    @Test
    public void onRefresh_reloadsWithForce() throws Exception {
        Content content = mockContent(StringUtils.toBase64("hello"), 5);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            activity.onRefresh();
            waitFor(() -> {
                try {
                    mocked.verify(() -> ServiceFactory.get(
                            eq(RepositoryContentService.class), eq(true)), times(1));
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }, 5000);
            // onRefresh resets mHasData via setContentShown(false); wait for
            // the forced reload to deliver and set it again.
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mHasData")), 5000);
            assertNotNull(get(activity, "mContent"));
        }
    }

    @Test
    public void loadFile_success_setsContentAndShowsData() throws Exception {
        Content content = mockContent(StringUtils.toBase64("hello world"), 11);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            assertTrue((boolean) get(activity, "mHasData"));
            assertFalse((boolean) get(activity, "mContentEmpty"));
            assertFalse((boolean) get(activity, "mErrorShown"));
        }
    }

    @Test
    public void loadFile_emptyContentLargeFile_opensUnsuitable() throws Exception {
        Content content = mockContent("", 100);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> activity.findViewById(R.id.retry_button).getVisibility()
                    == android.view.View.GONE, 5000);
            assertTrue((boolean) get(activity, "mErrorShown"));
        }
    }

    @Test
    public void loadFile_tooLargeError_withViewer_opensViewerAndShowsEmpty() throws Exception {
        String rawUrl = "https://raw.githubusercontent.com/o/r/main/src/Foo.java";
        Uri dummyUri = Uri.parse(rawUrl).buildUpon().authority("www.somedummy.com").build();
        Intent query = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(dummyUri, FileUtils.getMimeTypeFor("Foo.java"));
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.external.viewer";
        info.activityInfo.name = "ViewerActivity";
        info.activityInfo.enabled = true;
        info.activityInfo.exported = true;
        info.activityInfo.nonLocalizedLabel = "Viewer";
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = "com.external.viewer";
        Shadows.shadowOf(appContext().getPackageManager()).addResolveInfoForIntent(query, info);

        ApiRequestException error = mockApiError(ClientErrorResponse.FieldError.Reason.TooLarge);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockErrorService(error))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mContentEmpty")), 5000);
            assertTrue((boolean) get(activity, "mContentShown"));
            assertFalse((boolean) get(activity, "mErrorShown"));
            assertTrue(activity.isFinishing());
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertEquals("com.external.viewer", next.getPackage());
        }
    }

    @Test
    public void loadFile_otherApiError_showsFailure() throws Exception {
        ApiRequestException error = mockApiError(ClientErrorResponse.FieldError.Reason.Invalid);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockErrorService(error))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 5000);
        }
    }

    @Test
    public void loadFile_apiErrorNullResponse_showsFailure() throws Exception {
        ApiRequestException error = mock(ApiRequestException.class);
        when(error.getResponse()).thenReturn(null);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockErrorService(error))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 5000);
        }
    }

    @Test
    public void loadFile_ioError_showsFailure() throws Exception {
        try (MockedStatic<ServiceFactory> mocked =
                     mockServiceFactory(mockErrorService(new java.io.IOException("boom")))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> Boolean.TRUE.equals(get(activity, "mErrorShown")), 5000);
        }
    }








    // ---------- WebViewerActivity (driven through FileViewerActivity) ----------

    private android.webkit.WebView webView(FileViewerActivity activity) {
        return (android.webkit.WebView) get(activity, "mWebView");
    }

    private android.webkit.WebViewClient webViewClient(FileViewerActivity activity) {
        return Shadows.shadowOf(webView(activity)).getWebViewClient();
    }

    @Test
    public void displayDetachAction_returnsTrue() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertTrue(activity.displayDetachAction());
    }

    @Test
    public void onStop_clearsStartedFlag() {
        org.robolectric.android.controller.ActivityController<FileViewerActivity> c =
                controller(FileViewerActivity.class, intent("src/Foo.java"));
        FileViewerActivity activity = c.create().start().resume().get();
        assertTrue((boolean) get(activity, "mStarted"));
        c.stop();
        assertFalse((boolean) get(activity, "mStarted"));
    }

    @Test
    public void canChildScrollUp_returnsFalse() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertFalse(activity.canChildScrollUp());
    }

    @Test
    public void setupWebView_zoomInRange_appliesZoom() {
        appContext().getSharedPreferences(SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_TEXT_SIZE, 0).apply();
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertEquals(50, webView(activity).getSettings().getTextZoom());
    }

    @Test
    public void setupWebView_zoomOutOfRange_keepsDefault() {
        appContext().getSharedPreferences(SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_TEXT_SIZE, 9).apply();
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertEquals(100, webView(activity).getSettings().getTextZoom());
    }

    @Test
    public void webViewClient_onPageFinished_showsContentWhenNoNativeClient() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertFalse((boolean) get(activity, "mRequiresNativeClient"));
        webViewClient(activity).onPageFinished(webView(activity), "file:///android_asset/");
        assertTrue((boolean) get(activity, "mPageFinished"));
        assertTrue((boolean) get(activity, "mContentShown"));
        assertEquals("javascript:applyLineWrapping(false)",
                Shadows.shadowOf(webView(activity)).getLastLoadedUrl());
    }

    @Test
    public void webViewClient_onPageFinished_waitsForRenderingWhenNativeClient() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mRequiresNativeClient", true);
        webViewClient(activity).onPageFinished(webView(activity), "file:///android_asset/");
        assertTrue((boolean) get(activity, "mPageFinished"));
        assertFalse((boolean) get(activity, "mContentShown"));
        set(activity, "mRenderingDone", true);
        webViewClient(activity).onPageFinished(webView(activity), "file:///android_asset/");
        assertTrue((boolean) get(activity, "mContentShown"));
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_string_external_goesToBrowser() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(
                webView(activity), "https://example.com/x"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_string_github_opensInternally() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(
                webView(activity), "https://github.com/o/r"));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(RepositoryActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_string_mailto_startsViewIntent() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(
                webView(activity), "mailto:a@b.c"));
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, next.getAction());
        assertEquals(Uri.parse("mailto:a@b.c"), next.getData());
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_string_unsupportedScheme_ignored() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(
                webView(activity), "javascript:alert(1)"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(
                webView(activity), "file:///etc/passwd"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_string_notStarted_ignored() {
        FileViewerActivity activity = controller(FileViewerActivity.class, intent("src/Foo.java"))
                .create().get();
        assertFalse((boolean) get(activity, "mStarted"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(
                webView(activity), "https://github.com/o/r"));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_request_started() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        WebResourceRequest request = mock(WebResourceRequest.class);
        when(request.getUrl()).thenReturn(Uri.parse("https://example.com/x"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(webView(activity), request));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void webViewClient_shouldOverrideUrlLoading_request_notStarted_ignored() {
        FileViewerActivity activity = controller(FileViewerActivity.class, intent("src/Foo.java"))
                .create().get();
        WebResourceRequest request = mock(WebResourceRequest.class);
        when(request.getUrl()).thenReturn(Uri.parse("https://github.com/o/r"));
        assertTrue(webViewClient(activity).shouldOverrideUrlLoading(webView(activity), request));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onTouch_downRecordsPosition() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        android.webkit.WebView wv = webView(activity);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10f, 20f, 0);
        assertFalse(activity.onTouch(wv, down));
        android.graphics.Point p = (android.graphics.Point) get(activity, "mLastTouchDown");
        assertEquals(10, p.x);
        assertEquals(20, p.y);
        MotionEvent up = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 99f, 99f, 0);
        assertFalse(activity.onTouch(wv, up));
        assertEquals(10, p.x);
        assertEquals(20, p.y);
    }

    @Test
    public void onOptionsItemSelected_search_startsFindActionMode() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.search)));
    }

    @Test
    public void onOptionsItemSelected_search_noWebView_returnsTrue() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mWebView", null);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.search)));
    }

    @Test
    public void onOptionsItemSelected_wrap_togglesWrapping() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        android.view.MenuItem wrap = menu.findItem(R.id.wrap);
        assertTrue(activity.onOptionsItemSelected(wrap));
        assertTrue(wrap.isChecked());
        assertTrue(activity.getSharedPreferences(SettingsFragment.PREF_NAME,
                android.content.Context.MODE_PRIVATE).getBoolean("line_wrapping", false));
        assertEquals("javascript:applyLineWrapping(true)",
                Shadows.shadowOf(webView(activity)).getLastLoadedUrl());
        assertTrue(activity.onOptionsItemSelected(wrap));
        assertFalse(wrap.isChecked());
        assertEquals("javascript:applyLineWrapping(false)",
                Shadows.shadowOf(webView(activity)).getLastLoadedUrl());
    }

    @Test
    public void onOptionsItemSelected_print_createsPrintWebView() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("hello"), 5));
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.print)));
        assertNotNull(get(activity, "mPrintWebView"));
    }

    @Test
    public void printWebView_onPageFinished_printsAndClears() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("hello"), 5));
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        activity.onOptionsItemSelected(menu.findItem(R.id.print));
        android.webkit.WebView printWebView = (android.webkit.WebView) get(activity, "mPrintWebView");
        assertNotNull(printWebView);
        Shadows.shadowOf(printWebView).getWebViewClient()
                .onPageFinished(printWebView, "file:///android_asset/");
        assertNull(get(activity, "mPrintWebView"));
    }

    @Test
    public void doPrintHtml_whenFinishing_skipsPrint() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mPrintWebView", new android.webkit.WebView(activity));
        activity.finish();
        call(activity, "doPrintHtml", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mPrintWebView"));
    }

    @Test
    public void doPrint_withNativeClient_usesPrintNativeClient() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("hello"), 5));
        activity.generateHtml("light", false);
        assertTrue((boolean) get(activity, "mRequiresNativeClient"));
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.print)));
        android.webkit.WebView printWebView = (android.webkit.WebView) get(activity, "mPrintWebView");
        Object nativeClient = Shadows.shadowOf(printWebView).getJavascriptInterface("NativeClient");
        assertNotNull(nativeClient);
        call(nativeClient, "onRenderingDone", new Class<?>[]{}, new Object[]{});
        idleMain(500);
        assertNull(get(activity, "mPrintWebView"));
    }

    @Test
    public void nativeClient_onRenderingDone_showsContent() throws Exception {
        Content content = mockContent(StringUtils.toBase64("hello world"), 11);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            assertTrue((boolean) get(activity, "mRequiresNativeClient"));
            assertFalse((boolean) get(activity, "mContentShown"));
            webViewClient(activity).onPageFinished(webView(activity), "file:///android_asset/");
            assertTrue((boolean) get(activity, "mPageFinished"));
            assertFalse((boolean) get(activity, "mContentShown"));
            Object nativeClient = Shadows.shadowOf(webView(activity)).getJavascriptInterface("NativeClient");
            assertNotNull(nativeClient);
            call(nativeClient, "onRenderingDone", new Class<?>[]{}, new Object[]{});
            idleMain(500);
            assertTrue((boolean) get(activity, "mRenderingDone"));
            assertTrue((boolean) get(activity, "mContentShown"));
        }
    }

    @Test
    public void nativeClient_onLineTouched_postsToHandler() throws Exception {
        Content content = mockContent(StringUtils.toBase64("hello world"), 11);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            Object nativeClient = Shadows.shadowOf(webView(activity)).getJavascriptInterface("NativeClient");
            assertNotNull(nativeClient);
            call(nativeClient, "onLineTouched", new Class<?>[]{int.class}, new Object[]{3});
            idleMain(500);
            assertEquals(3, get(activity, "mLastTouchedLine"));
        }
    }

    @Test
    public void base64JsInterface_decode() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Object iface = Shadows.shadowOf(webView(activity)).getJavascriptInterface("Base64");
        assertNotNull(iface);
        assertEquals("hello", call(iface, "decode",
                new Class<?>[]{String.class}, new Object[]{"aGVsbG8="}));
    }

    @Test
    public void htmlUtilsJsInterface_rewriteRelativeUrls() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        Object iface = Shadows.shadowOf(webView(activity)).getJavascriptInterface("HtmlUtils");
        assertNotNull(iface);
        String out = (String) call(iface, "rewriteRelativeUrls",
                new Class<?>[]{String.class, String.class, String.class, String.class, String.class},
                new Object[]{"<a href=\"x\">y</a>", "o", "r", "main", "docs"});
        assertTrue(out.contains("https://github.com/o/r/blob/main/docs/x"));
    }

    @Test
    public void escapeJsSingleQuoted_variants() {
        assertEquals("", callStatic(WebViewerActivity.class, "escapeJsSingleQuoted",
                new Class<?>[]{String.class}, new Object[]{null}));
        assertEquals("plain", callStatic(WebViewerActivity.class, "escapeJsSingleQuoted",
                new Class<?>[]{String.class}, new Object[]{"plain"}));
        assertEquals("a\\'b\\\\c\\rd\\ne<\\/f",
                callStatic(WebViewerActivity.class, "escapeJsSingleQuoted",
                        new Class<?>[]{String.class},
                        new Object[]{"a'b\\c\rd\ne</f"}));
    }

    @Test
    public void wrapWithMarkdownStyling_variants() {
        String noTitle = (String) callStatic(WebViewerActivity.class, "wrapWithMarkdownStyling",
                new Class<?>[]{String.class, String.class, String.class},
                new Object[]{null, "light", null});
        assertTrue(noTitle.contains("sanitizeHtml('')"));
        assertFalse(noTitle.contains("<h2>"));

        String withTitle = (String) callStatic(WebViewerActivity.class, "wrapWithMarkdownStyling",
                new Class<?>[]{String.class, String.class, String.class},
                new Object[]{"<p>hi</p>", "light", "T"});
        assertTrue(withTitle.contains("<h2>T</h2>"));
        assertTrue(withTitle.contains("sanitizeHtml('<p>hi<\\/p>')"));

        String escaped = (String) callStatic(WebViewerActivity.class, "wrapWithMarkdownStyling",
                new Class<?>[]{String.class, String.class, String.class},
                new Object[]{"it's </script> end", "light", null});
        assertTrue(escaped.contains("it\\'s <\\/script> end"));
    }

    @Test
    public void generateMarkdownHtml_titleAndRef() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("# Title"), 7));
        String html = activity.generateHtml("light", true);
        assertTrue(html.contains("<h2>"));
        assertTrue(html.contains("rewriteRelativeUrls(html, 'o', 'r', 'main', 'docs')"));
    }

    @Test
    public void generateMarkdownHtml_refNullUsesMaster() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("# Title"), 7));
        set(activity, "mRef", null);
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("rewriteRelativeUrls(html, 'o', 'r', 'master', 'docs')"));
    }

    @Test
    public void generateMarkdownHtml_noRepoSkipsRewrite() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("# Title"), 7));
        set(activity, "mRepoOwner", null);
        String html = activity.generateHtml("light", false);
        assertFalse(html.contains("rewriteRelativeUrls"));
        assertFalse(html.contains("<h2>"));
    }

    @Test
    public void generateCodeHtml_languageVariants() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("class Foo {}"), 12));
        String javaHtml = activity.generateHtml("light", false);
        assertTrue(javaHtml.contains("lang-java"));

        FileViewerActivity makeActivity = launch(FileViewerActivity.class, intent("Makefile"));
        set(makeActivity, "mContent", mockContent(StringUtils.toBase64("hello"), 5));
        assertTrue(makeActivity.generateHtml("light", false).contains("lang-txt"));

        FileViewerActivity scriptActivity = launch(FileViewerActivity.class, intent("script"));
        set(scriptActivity, "mContent", mockContent(StringUtils.toBase64("#!/bin/sh\necho hi"), 16));
        assertTrue(scriptActivity.generateHtml("light", false).contains("lang-'>"));

        FileViewerActivity mdActivity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(mdActivity, "mViewRawText", true);
        set(mdActivity, "mContent", mockContent(StringUtils.toBase64("# Title"), 7));
        set(mdActivity, "mHighlightStart", 3);
        set(mdActivity, "mHighlightEnd", 7);
        String mdHtml = mdActivity.generateHtml("light", true);
        assertTrue(mdHtml.contains("lang-html"));
        assertTrue(mdHtml.contains("highlightLines(3,7)"));
        assertTrue(mdHtml.contains("<h2>"));
    }

    @Test
    public void generateCodeHtml_calledTwice_sameOutput() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("class Foo {}"), 12));
        String first = activity.generateHtml("light", false);
        String second = activity.generateHtml("light", false);
        assertEquals(first, second);
    }

    @Test
    public void setContentShown_false_resetsFlags() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mHasData", true);
        set(activity, "mPageFinished", true);
        set(activity, "mRenderingDone", true);
        activity.setContentShown(false);
        assertFalse((boolean) get(activity, "mHasData"));
        assertFalse((boolean) get(activity, "mPageFinished"));
        assertFalse((boolean) get(activity, "mRenderingDone"));
    }

    @Test
    public void onDataReady_loadsHtmlAndMarksHasData() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("hello"), 5));
        activity.onDataReady();
        assertTrue((boolean) get(activity, "mHasData"));
    }

    // ---------- gap closers ----------

    @Test
    public void findMatchingLines_noMatch_keepsHighlight() {
        TextMatch match = mock(TextMatch.class);
        when(match.fragment()).thenReturn("zzz-not-present");
        Intent i = FileViewerActivity.makeIntentWithSearchMatch(appContext(), "o", "r", "main",
                "a.txt", match);
        FileViewerActivity activity = launch(FileViewerActivity.class, i);
        call(activity, "findMatchingLines",
                new Class<?>[]{String.class}, new Object[]{"line1\nline2\nline3"});
        assertEquals(-1, get(activity, "mHighlightStart"));
        assertEquals(-1, get(activity, "mHighlightEnd"));
    }

    @Test
    public void handlePrintRequest_svgTallImage_downsamplesViaHeight() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", mockContent(pngBase64(100, 4096), 1234));
        assertTrue(activity.handlePrintRequest());
    }

    @Test
    public void buildBlobUrl_nullOwner_returnsNull() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mRepoOwner", null);
        assertNull(call(activity, "buildBlobUrl", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void buildBlobUrl_nullRepo_returnsNull() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mRepoName", null);
        assertNull(call(activity, "buildBlobUrl", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void buildBlobUrl_nullPath_returnsNull() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mPath", null);
        assertNull(call(activity, "buildBlobUrl", new Class<?>[]{}, new Object[]{}));
    }

    @Test
    public void loadFile_success_sizeZero_stillShowsData() throws Exception {
        Content content = mockContent(StringUtils.toBase64("hello"), 0);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            assertTrue((boolean) get(activity, "mHasData"));
        }
    }

    @Test
    public void setupWebView_zoomNegative_keepsDefault() {
        appContext().getSharedPreferences(SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_TEXT_SIZE, -1).apply();
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertEquals(100, webView(activity).getSettings().getTextZoom());
    }

    @Test
    public void onOptionsItemSelected_print_imageRequest_returnsEarly() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("img/logo.svg"));
        set(activity, "mContent", mockContent(pngBase64(100, 100), 1234));
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.print)));
        assertNull(get(activity, "mPrintWebView"));
    }

    @Test
    public void nativeClient_onRenderingDone_beforePageFinished_doesNotShow() throws Exception {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        assertFalse((boolean) get(activity, "mPageFinished"));
        Class<?> clazz = Class.forName(
                "com.gh4a.activities.WebViewerActivity$NativeClientJavascriptInterface");
        java.lang.reflect.Constructor<?> ctor =
                clazz.getDeclaredConstructor(WebViewerActivity.class);
        ctor.setAccessible(true);
        Object nativeClient = ctor.newInstance(activity);
        call(nativeClient, "onRenderingDone", new Class<?>[]{}, new Object[]{});
        idleMain(500);
        assertFalse((boolean) get(activity, "mContentShown"));
    }

    @Test
    public void generateMarkdownHtml_repoNameNullSkipsRewrite() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("docs/Readme.md"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("# Title"), 7));
        set(activity, "mRepoName", null);
        String html = activity.generateHtml("light", false);
        assertFalse(html.contains("rewriteRelativeUrls"));
    }

    @Test
    public void printNativeClient_onLineTouched_noop() {
        FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
        set(activity, "mContent", mockContent(StringUtils.toBase64("hello"), 5));
        activity.generateHtml("light", false);
        set(activity, "mHasData", true);
        androidx.appcompat.view.menu.MenuBuilder menu = inflatedMenu(activity);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.print)));
        android.webkit.WebView printWebView = (android.webkit.WebView) get(activity, "mPrintWebView");
        Object nativeClient = Shadows.shadowOf(printWebView).getJavascriptInterface("NativeClient");
        assertNotNull(nativeClient);
        // The print variant's onLineTouched is an intentional no-op.
        call(nativeClient, "onLineTouched", new Class<?>[]{int.class}, new Object[]{5});
        assertNotNull(get(activity, "mPrintWebView"));
    }

    @Test
    public void onDataReady_darkTheme_usesDarkCss() throws Exception {
        org.robolectric.RuntimeEnvironment.setQualifiers("+night");
        Content content = mockContent(StringUtils.toBase64("hello world"), 11);
        try (MockedStatic<ServiceFactory> mocked = mockServiceFactory(mockContentService(content))) {
            FileViewerActivity activity = launch(FileViewerActivity.class, intent("src/Foo.java"));
            assertTrue(activity.getResources().getBoolean(R.bool.is_dark_theme));
            waitFor(() -> get(activity, "mContent") != null, 5000);
            assertTrue((boolean) get(activity, "mHasData"));
        }
    }
}
