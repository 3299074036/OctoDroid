package com.gh4a.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.fragment.DownloadListFragment;
import com.gh4a.utils.DownloadRecordManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doThrow;

@RunWith(RobolectricTestRunner.class)
public class DownloadListAdapterTest {
    private Context context;
    private DownloadListAdapter adapter;
    private LayoutInflater inflater;
    private ViewGroup parent;

    @Before
    public void setUp() {
        context = AdapterTestSupport.context();
        adapter = new DownloadListAdapter(context);
        inflater = AdapterTestSupport.inflater(context);
        parent = AdapterTestSupport.parent(context);
    }

    private DownloadListAdapter.ViewHolder holder() {
        return adapter.onCreateViewHolder(inflater, parent, 2);
    }

    private DownloadListFragment.Item item(String desc, long size, String sha256, int status) {
        DownloadRecordManager.Record record = new DownloadRecordManager.Record(
                1L, "app.apk", "https://example.com/app.apk", desc,
                System.currentTimeMillis() - 1000L, size, sha256);
        return AdapterTestSupport.newInstance(DownloadListFragment.Item.class,
                new Class<?>[]{DownloadRecordManager.Record.class, int.class}, record, status);
    }

    private String text(DownloadListAdapter.ViewHolder h, int id) {
        return ((TextView) h.itemView.findViewById(id)).getText().toString();
    }

    @Test
    public void bind_successful_withDescriptionSizeAndHash() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("nightly", 2048,
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                DownloadManager.STATUS_SUCCESSFUL));
        assertEquals("app.apk", text(h, R.id.tv_title));
        String sub = text(h, R.id.tv_subtitle);
        assertTrue(sub.contains(context.getString(R.string.download_status_completed)));
        assertTrue(sub.contains("nightly"));
        assertTrue(sub.contains("SHA-256: abcdef0123456789"));
        assertTrue(text(h, R.id.tv_time).length() > 0);
    }

    @Test
    public void bind_successful_noDescriptionNoSizeNoHash() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item(null, 0, null, DownloadManager.STATUS_SUCCESSFUL));
        assertEquals(context.getString(R.string.download_status_completed),
                text(h, R.id.tv_subtitle));
    }

    @Test
    public void bind_successful_shortHashShownWhole() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("", 0, "abc", DownloadManager.STATUS_SUCCESSFUL));
        assertTrue(text(h, R.id.tv_subtitle).contains("SHA-256: abc"));
    }

    @Test
    public void bind_successful_exactly16CharHash_noEllipsis() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item(null, 0, "0123456789abcdef",
                DownloadManager.STATUS_SUCCESSFUL));
        assertTrue(text(h, R.id.tv_subtitle).contains("SHA-256: 0123456789abcdef"));
        assertTrue(!text(h, R.id.tv_subtitle).contains("…"));
    }

    @Test
    public void bind_failed() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("d", 999, "abc", DownloadManager.STATUS_FAILED));
        String sub = text(h, R.id.tv_subtitle);
        assertTrue(sub.contains(context.getString(R.string.download_status_failed)));
        // failed downloads never show size/hash
        assertTrue(!sub.contains("SHA-256"));
    }

    @Test
    public void bind_downloading_defaultBranch() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item(null, 0, null, DownloadManager.STATUS_RUNNING));
        assertEquals(context.getString(R.string.download_status_downloading),
                text(h, R.id.tv_subtitle));
    }

    @Test
    public void bind_emptyDescription_noSeparator() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item("", 0, null, DownloadManager.STATUS_RUNNING));
        assertEquals(context.getString(R.string.download_status_downloading),
                text(h, R.id.tv_subtitle));
    }

    @Test
    public void queryStatus_returnsCursorStatus() {
        Context mockContext = mock(Context.class);
        DownloadManager dm = mock(DownloadManager.class);
        Cursor cursor = mock(Cursor.class);
        when(mockContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(dm);
        when(dm.query(any(DownloadManager.Query.class))).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)).thenReturn(0);
        when(cursor.getInt(0)).thenReturn(DownloadManager.STATUS_SUCCESSFUL);
        assertEquals(DownloadManager.STATUS_SUCCESSFUL,
                DownloadListAdapter.queryStatus(mockContext, 42L));
    }

    @Test
    public void queryStatus_nullCursor_returnsFailed() {
        Context mockContext = mock(Context.class);
        DownloadManager dm = mock(DownloadManager.class);
        when(mockContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(dm);
        when(dm.query(any(DownloadManager.Query.class))).thenReturn(null);
        assertEquals(DownloadManager.STATUS_FAILED,
                DownloadListAdapter.queryStatus(mockContext, 42L));
    }

    @Test
    public void queryStatus_emptyCursor_returnsFailed() {
        Context mockContext = mock(Context.class);
        DownloadManager dm = mock(DownloadManager.class);
        Cursor cursor = mock(Cursor.class);
        when(mockContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(dm);
        when(dm.query(any(DownloadManager.Query.class))).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(false);
        assertEquals(DownloadManager.STATUS_FAILED,
                DownloadListAdapter.queryStatus(mockContext, 42L));
    }

    @Test
    public void queryStatus_exception_returnsFailed() {
        // only exceptions from dm.query() are swallowed; getSystemService
        // throwing is outside the try and propagates (see
        // queryStatus_systemServiceThrows_propagates).
        Context mockContext = mock(Context.class);
        DownloadManager dm = mock(DownloadManager.class);
        when(mockContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(dm);
        when(dm.query(any(DownloadManager.Query.class)))
                .thenThrow(new RuntimeException("boom"));
        assertEquals(DownloadManager.STATUS_FAILED,
                DownloadListAdapter.queryStatus(mockContext, 42L));
    }

    @Test
    public void queryStatus_systemServiceThrows_propagates() {
        Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.DOWNLOAD_SERVICE))
                .thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class,
                () -> DownloadListAdapter.queryStatus(mockContext, 42L));
    }

    @Test
    public void bind_successful_emptyHash_noShaSuffix() {
        DownloadListAdapter.ViewHolder h = holder();
        adapter.onBindViewHolder(h, item(null, 0, "", DownloadManager.STATUS_SUCCESSFUL));
        assertFalse(text(h, R.id.tv_subtitle).contains("SHA-256"));
    }

    @Test
    public void queryStatus_cursorCloseThrows_returnsFailed() {
        // try-with-resources: an exception from close() is caught the same way.
        Context mockContext = mock(Context.class);
        DownloadManager dm = mock(DownloadManager.class);
        Cursor cursor = mock(Cursor.class);
        when(mockContext.getSystemService(Context.DOWNLOAD_SERVICE)).thenReturn(dm);
        when(dm.query(any(DownloadManager.Query.class))).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getInt(0)).thenReturn(DownloadManager.STATUS_SUCCESSFUL);
        doThrow(new RuntimeException("close boom")).when(cursor).close();
        assertEquals(DownloadManager.STATUS_FAILED,
                DownloadListAdapter.queryStatus(mockContext, 42L));
    }
}
