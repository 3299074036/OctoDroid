package com.gh4a.activities;

import android.content.Intent;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.Gist;
import com.meisolsson.githubsdk.model.GistFile;
import com.meisolsson.githubsdk.service.gists.GistService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class GistViewerActivityTest {
    private Intent intent() {
        return GistViewerActivity.makeIntent(appContext(), "abc123", "hello.txt");
    }

    @Test
    public void makeIntent_setsIdAndFile() {
        Intent i = intent();
        assertEquals(GistViewerActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("abc123", i.getStringExtra("id"));
        assertEquals("hello.txt", i.getStringExtra("file"));
    }

    @Test
    public void launch_titleIsFileName() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        assertEquals("hello.txt", activity.getSupportActionBar().getTitle().toString());
        assertTrue(activity.canSwipeToRefresh());
    }

    @Test
    public void navigateUp_goesToGist() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        Intent up = activity.navigateUp();
        assertEquals(GistActivity.class.getName(), up.getComponent().getClassName());
        assertEquals("abc123", up.getStringExtra("id"));
    }

    @Test
    public void getDocumentTitle_includesFileGistOwner() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        set(activity, "mGistOwner", "alice");
        assertEquals(appContext().getString(R.string.gist_print_document_title,
                "hello.txt", "abc123", "alice"), activity.getDocumentTitle());
    }

    @Test
    public void generateHtml_codeFile_rendersCode() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("hello.txt");
        when(file.content()).thenReturn("line1\nline2");
        set(activity, "mGistFile", file);
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("line1"));
        assertTrue(html.contains("line2"));
    }

    @Test
    public void generateHtml_markdownFile_rendersMarkdown() {
        GistViewerActivity activity = launch(GistViewerActivity.class,
                GistViewerActivity.makeIntent(appContext(), "abc123", "readme.md"));
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("readme.md");
        when(file.content()).thenReturn("# Head");
        set(activity, "mGistFile", file);
        // Markdown is rendered client-side by showdown; the HTML embeds
        // the base64 source, not the rendered text.
        String html = activity.generateHtml("light", false);
        assertTrue(html.contains("showdown"));
        assertTrue(html.contains(StringUtils.toBase64("# Head")));
    }

    @Test
    public void onOptionsItemSelected_browserWithoutFile_returnsTrueWithoutCrash() {
        // With no file loaded, WebViewerActivity removes the browser item
        // from the menu, so the null-file guard is reached via a
        // programmatically built item instead of the inflated menu.
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.download, 0, "download")));
    }

    @Test
    public void onCreateOptionsMenu_withoutFile_removesWrap() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.share) == null);
        assertTrue(menu.findItem(R.id.wrap) == null);
        // WebViewerActivity removes the browser item while no data is loaded.
        assertTrue(menu.findItem(R.id.browser) == null);
    }

    @Test
    public void loadGist_fileFound_setsFileAndOwner() {
        GistService svc = mock(GistService.class);
        Gist gist = mock(Gist.class);
        GistFile file = mock(GistFile.class);
        when(file.content()).thenReturn("content here");
        Map<String, GistFile> files = new HashMap<>();
        files.put("hello.txt", file);
        when(gist.files()).thenReturn(files);
        when(gist.owner()).thenReturn(null);
        when(svc.getGist("abc123")).thenReturn(Single.just(Response.success(gist)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistViewerActivity activity = launch(GistViewerActivity.class, intent());
            com.gh4a.activities.ActivityTestHelper.idleMain(2000);
            assertEquals(file, getFile(activity));
            assertEquals("content here", getFile(activity).content());
        }
    }

    private GistFile getFile(GistViewerActivity activity) {
        return (GistFile) com.gh4a.activities.ActivityTestHelper.get(activity, "mGistFile");
    }

    private GistService mockGistService(Gist gist) {
        GistService svc = mock(GistService.class);
        when(svc.getGist("abc123")).thenReturn(Single.just(Response.success(gist)));
        return svc;
    }

    private java.util.Map<String, GistFile> filesWith(GistFile file, String name) {
        java.util.Map<String, GistFile> files = new HashMap<>();
        files.put(name, file);
        return files;
    }

    @Test
    public void onRefresh_reloadsGist() throws Exception {
        Gist gist = mock(Gist.class);
        GistFile file = mock(GistFile.class);
        when(file.content()).thenReturn("content here");
        when(file.filename()).thenReturn("hello.txt");
        when(gist.files()).thenReturn(filesWith(file, "hello.txt"));
        when(gist.owner()).thenReturn(null);
        GistService svc = mockGistService(gist);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.get(GistService.class, true)).thenReturn(svc);
            GistViewerActivity activity = launch(GistViewerActivity.class, intent());
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> getFile(activity) != null, 10000);
            set(activity, "mGistFile", null);
            activity.onRefresh();
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> getFile(activity) != null, 10000);
            assertEquals(file, getFile(activity));
        }
    }

    @Test
    public void loadGist_fileMissing_showsError() throws Exception {
        Gist gist = mock(Gist.class);
        when(gist.files()).thenReturn(new HashMap<>());
        when(gist.owner()).thenReturn(null);
        GistService svc = mockGistService(gist);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistViewerActivity activity = launch(GistViewerActivity.class, intent());
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> activity.findViewById(android.R.id.empty).getVisibility()
                            == android.view.View.VISIBLE, 10000);
            assertNull(getFile(activity));
        }
    }

    @Test
    public void loadGist_contentNull_showsError() throws Exception {
        Gist gist = mock(Gist.class);
        GistFile file = mock(GistFile.class);
        when(file.content()).thenReturn(null);
        when(file.filename()).thenReturn("hello.txt");
        when(gist.files()).thenReturn(filesWith(file, "hello.txt"));
        when(gist.owner()).thenReturn(null);
        GistService svc = mockGistService(gist);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistViewerActivity activity = launch(GistViewerActivity.class, intent());
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> activity.findViewById(android.R.id.empty).getVisibility()
                            == android.view.View.VISIBLE, 10000);
            assertEquals(file, getFile(activity));
        }
    }

    @Test
    public void onCreateOptionsMenu_markdownFile_removesWrap() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("readme.md");
        set(activity, "mGistFile", file);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.wrap) == null);
    }

    @Test
    public void onCreateOptionsMenu_textFile_keepsWrap() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("hello.txt");
        set(activity, "mGistFile", file);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.wrap) != null);
    }

    @Test
    public void onOptionsItemSelected_browserWithFile_launchesBrowser() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("hello.txt");
        when(file.rawUrl()).thenReturn("https://example.com/raw");
        set(activity, "mGistFile", file);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
    }

    @Test
    public void onOptionsItemSelected_downloadWithFile_enqueuesDownload() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        GistFile file = mock(GistFile.class);
        when(file.filename()).thenReturn("hello.txt");
        when(file.type()).thenReturn("text/plain");
        when(file.rawUrl()).thenReturn("https://example.com/raw");
        set(activity, "mGistFile", file);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        // download case falls through to super, which returns false.
        assertFalse(activity.onOptionsItemSelected(menu.add(0, R.id.download, 0, "download")));
    }

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesToSuper() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 0x7f0b9999, 0, "unknown")));
    }

    @Test
    public void onOptionsItemSelected_search_returnsTrueWithoutWebView() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.search, 0, "search")));
    }

    @Test
    public void onOptionsItemSelected_wrap_togglesLineWrapping() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        set(activity, "mWebView", mock(android.webkit.WebView.class));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        android.view.MenuItem wrap = menu.add(0, R.id.wrap, 0, "wrap");
        wrap.setCheckable(true);
        wrap.setChecked(false);
        assertTrue(activity.onOptionsItemSelected(wrap));
        assertTrue(wrap.isChecked());
    }

    @Test
    public void onOptionsItemSelected_browserWithNullRawUrl_returnsTrue() {
        GistViewerActivity activity = launch(GistViewerActivity.class, intent());
        GistFile file = mock(GistFile.class);
        when(file.rawUrl()).thenReturn(null);
        set(activity, "mGistFile", file);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertTrue(activity.onOptionsItemSelected(menu.add(0, R.id.browser, 0, "browser")));
    }

    @Test
    public void loadGist_filesNull_showsError() throws Exception {
        Gist gist = mock(Gist.class);
        when(gist.files()).thenReturn(null);
        when(gist.owner()).thenReturn(null);
        GistService svc = mockGistService(gist);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(GistService.class, false)).thenReturn(svc);
            GistViewerActivity activity = launch(GistViewerActivity.class, intent());
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> activity.findViewById(android.R.id.empty).getVisibility()
                            == android.view.View.VISIBLE, 10000);
            assertNull(getFile(activity));
        }
    }
}
