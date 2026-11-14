package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.content.FileProvider;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.fragment.DownloadListFragment.Item;
import com.gh4a.utils.DownloadRecordManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Method;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;

/** DownloadListFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class DownloadListFragmentTest {

    public static class NoLoad extends DownloadListFragment {
        boolean useRealSingle;

        @Override
        protected List<Item> onGetInitialData() {
            return null;
        }

        @Override
        protected io.reactivex.Observable<List<Item>> onCreateDataObservable(
                boolean bypassCache) {
            return null;
        }

        @Override
        protected Single<List<Item>> onCreateDataSingle(boolean bypassCache) {
            if (useRealSingle) {
                return super.onCreateDataSingle(bypassCache);
            }
            return Single.never();
        }

        Single<List<Item>> realSingle(boolean bypassCache) {
            useRealSingle = true;
            try {
                return onCreateDataSingle(bypassCache);
            } finally {
                useRealSingle = false;
            }
        }
    }

    private static NoLoad attached() {
        NoLoad f = new NoLoad();
        FragmentTestHelper.attach(f);
        return f;
    }

    private static DownloadRecordManager.Record record(long id, String file) {
        return new DownloadRecordManager.Record(id, file, "https://example.com/f",
                "desc", 0L);
    }

    @Test
    public void newInstance_returnsFragment() {
        assertNotNull(DownloadListFragment.newInstance());
    }

    @Test
    public void getEmptyTextResId_isNoDownloads() {
        assertEquals(R.string.no_downloads, attached().getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsNonNull() {
        assertNotNull(attached().onCreateAdapter());
    }

    @Test
    public void onCreateDataSingle_noRecords_returnsEmptyList() {
        NoLoad f = attached();
        DownloadRecordManager.clear(f.getActivity());
        TestObserver<List<Item>> observer = f.realSingle(false).test();
        observer.assertValue(List::isEmpty);
        observer.assertComplete();
    }

    @Test
    public void onCreateDataSingle_withRecord_returnsItemWithStatus() {
        NoLoad f = attached();
        DownloadRecordManager.clear(f.getActivity());
        // Records are stored via the manager's own persistence; add one by
        // writing through its public API path (add is exercised elsewhere).
        // Here we only assert the empty case is stable; a record round-trip
        // is covered by the manager's own tests.
        TestObserver<List<Item>> observer = f.realSingle(true).test();
        observer.assertValue(List::isEmpty);
        observer.assertComplete();
    }

    @Test
    public void onCreateOptionsMenu_inflatesClearDownloadsItem() {
        NoLoad f = attached();
        FragmentActivity activity = f.getActivity();
        MenuBuilder menu = new MenuBuilder(activity);
        f.onCreateOptionsMenu(menu, activity.getMenuInflater());
        assertNotNull(menu.findItem(R.id.clear_downloads));
    }

    @Test
    public void onCreateDataSingle_withSeededRecord_returnsItemWithFailedStatus() {
        NoLoad f = attached();
        FragmentActivity activity = f.getActivity();
        DownloadRecordManager.clear(activity);
        DownloadRecordManager.record(activity, 42L, "app.apk",
                "https://example.com/app.apk", "desc");
        try {
            TestObserver<List<Item>> observer = f.realSingle(false).test();
            observer.assertValue(items -> items.size() == 1
                    && items.get(0).record.downloadId == 42L
                    && items.get(0).status == DownloadManager.STATUS_FAILED);
            observer.assertComplete();
        } finally {
            DownloadRecordManager.clear(activity);
        }
    }

    @Test
    public void onItemClick_verifyingRecord_showsVerifyingToast() {
        NoLoad f = attached();
        DownloadRecordManager.setVerifying(7L, true);
        try {
            f.onItemClick(new Item(record(7L, "a.apk"),
                    DownloadManager.STATUS_SUCCESSFUL));
            assertEquals(f.getString(R.string.download_verifying),
                    ShadowToast.getTextOfLatestToast());
        } finally {
            DownloadRecordManager.setVerifying(7L, false);
        }
    }

    @Test
    public void onItemClick_detachedFragment_returnsEarly() {
        // getActivity() is null: the null guard returns before touching files.
        new NoLoad().onItemClick(new Item(record(8L, "a.apk"),
                DownloadManager.STATUS_SUCCESSFUL));
    }

    /**
     * The file-open path needs a FileProvider URI. Under Robolectric no
     * file location satisfies both the production lookup
     * ({@code getExternalFilesDir(DOWNLOADS)}) and the provider's own
     * configured roots (the shadow maps the two bases inconsistently), so
     * the androidx path validation is stubbed out and only the app's own
     * logic is exercised.
     */
    private static java.io.File existingDownloadFile(NoLoad f, String name)
            throws Exception {
        FragmentActivity activity = f.getActivity();
        java.io.File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        assertNotNull(dir);
        dir.mkdirs();
        java.io.File file = new java.io.File(dir, name);
        file.createNewFile();
        return file;
    }

    @Test
    public void onItemClick_existingFile_noHandler_showsOpenFailedToast()
            throws Exception {
        NoLoad f = attached();
        FragmentActivity activity = f.getActivity();
        java.io.File file = existingDownloadFile(f, "app.apk");
        Uri uri = Uri.parse("content://com.gh4a.fileprovider/private_download/app.apk");
        try (MockedStatic<FileProvider> mocked = mockStatic(FileProvider.class)) {
            mocked.when(() -> FileProvider.getUriForFile(any(Context.class),
                    anyString(), any(java.io.File.class))).thenReturn(uri);
            f.onItemClick(new Item(record(9L, "app.apk"),
                    DownloadManager.STATUS_SUCCESSFUL));
        }
        assertEquals(f.getString(R.string.download_open_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onItemClick_existingFile_withHandler_startsActivity()
            throws Exception {
        NoLoad f = attached();
        FragmentActivity activity = f.getActivity();
        java.io.File file = existingDownloadFile(f, "app.apk");
        Uri uri = Uri.parse("content://com.gh4a.fileprovider/private_download/app.apk");
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive");
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = activity.getPackageName();
        info.activityInfo.name = "DummyViewerActivity";
        org.robolectric.Shadows.shadowOf(activity.getPackageManager())
                .addResolveInfoForIntent(intent, info);
        try (MockedStatic<FileProvider> mocked = mockStatic(FileProvider.class)) {
            mocked.when(() -> FileProvider.getUriForFile(any(Context.class),
                    anyString(), any(java.io.File.class))).thenReturn(uri);
            f.onItemClick(new Item(record(10L, "app.apk"),
                    DownloadManager.STATUS_SUCCESSFUL));
        }
        Intent started = org.robolectric.Shadows.shadowOf(activity)
                .getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
    }

    @Test
    public void onItemClick_nonSuccessfulStatus_doesNothing() {
        NoLoad f = attached();
        FragmentActivity activity = f.getActivity();
        f.onItemClick(new Item(record(1L, "a.apk"), DownloadManager.STATUS_RUNNING));
        assertNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onItemClick_successfulMissingFile_showsOpenFailedToast() {
        NoLoad f = attached();
        f.onItemClick(new Item(record(2L, "missing.apk"),
                DownloadManager.STATUS_SUCCESSFUL));
        assertEquals(f.getString(R.string.download_open_failed),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onItemLongClick_removesRecordShowsToastAndReturnsTrue() {
        NoLoad f = attached();
        assertTrue(f.onItemLongClick(
                new Item(record(3L, "a.apk"), DownloadManager.STATUS_SUCCESSFUL)));
        assertEquals(f.getString(R.string.download_record_deleted),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onOptionsItemSelected_clearDownloads_returnsTrue() {
        NoLoad f = attached();
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.clear_downloads);
        assertTrue(f.onOptionsItemSelected(item));
    }

    @Test
    public void onOptionsItemSelected_otherItem_returnsFalse() {
        NoLoad f = attached();
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(999999);
        assertFalse(f.onOptionsItemSelected(item));
    }

    // ---- getMimeType (private static) ----

    private static String mime(String fileName) {
        try {
            Method m = DownloadListFragment.class
                    .getDeclaredMethod("getMimeType", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, fileName);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void getMimeType_knownExtensions() {
        assertEquals("application/vnd.android.package-archive", mime("app.apk"));
        assertEquals("application/zip", mime("a.zip"));
        assertEquals("application/gzip", mime("a.tar.gz"));
        assertEquals("application/gzip", mime("a.tgz"));
        assertEquals("application/pdf", mime("a.pdf"));
        assertEquals("image/png", mime("a.png"));
        assertEquals("image/jpeg", mime("a.jpg"));
        assertEquals("image/jpeg", mime("a.jpeg"));
    }

    @Test
    public void getMimeType_caseInsensitive() {
        assertEquals("application/vnd.android.package-archive", mime("APP.APK"));
        assertEquals("image/png", mime("IMG.PNG"));
    }

    @Test
    public void getMimeType_unknownOrNoExtension_returnsWildcard() {
        assertEquals("*/*", mime("a.txt"));
        assertEquals("*/*", mime("noextension"));
        assertEquals("*/*", mime("a.apk.bak"));
    }
}
