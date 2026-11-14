package com.gh4a.utils;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A 组：DownloadRecordManager 持久化在 SharedPreferences（经 JsonCache），
 * 走 Robolectric。每个测试先 clear，保证记录干净。
 */
@RunWith(RobolectricTestRunner.class)
public class DownloadRecordManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        DownloadRecordManager.clear(context);
        DownloadRecordManager.setVerifying(1L, false);
        DownloadRecordManager.setVerifying(2L, false);
    }

    @Test
    public void getRecords_emptyInitially() {
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void record_singleEntryRoundTripsFields() {
        DownloadRecordManager.record(context, 42L, "file.apk",
                "https://example.com/file.apk", "desc");
        List<DownloadRecordManager.Record> records =
                DownloadRecordManager.getRecords(context);
        assertEquals(1, records.size());
        DownloadRecordManager.Record r = records.get(0);
        assertEquals(42L, r.downloadId);
        assertEquals("file.apk", r.fileName);
        assertEquals("https://example.com/file.apk", r.url);
        assertEquals("desc", r.description);
        assertTrue(r.time > 0);
        assertEquals(0L, r.size);
        assertNull(r.sha256);
    }

    @Test
    public void record_newestFirst() {
        DownloadRecordManager.record(context, 1L, "a.apk", "u1", "d1");
        DownloadRecordManager.record(context, 2L, "b.apk", "u2", "d2");
        List<DownloadRecordManager.Record> records =
                DownloadRecordManager.getRecords(context);
        assertEquals(2, records.size());
        assertEquals(2L, records.get(0).downloadId);
        assertEquals(1L, records.get(1).downloadId);
    }

    @Test
    public void record_capsAt100DroppingOldest() {
        for (long i = 1; i <= 105; i++) {
            DownloadRecordManager.record(context, i, "f" + i + ".apk", "u" + i, "d");
        }
        List<DownloadRecordManager.Record> records =
                DownloadRecordManager.getRecords(context);
        assertEquals(100, records.size());
        assertEquals(105L, records.get(0).downloadId);
        assertEquals(6L, records.get(99).downloadId);
    }

    @Test
    public void updateFileInfo_setsSizeAndSha256PreservingOtherFields() {
        DownloadRecordManager.record(context, 7L, "x.zip", "https://x/y.zip", "d");
        DownloadRecordManager.updateFileInfo(context, 7L, 12345L, "deadbeef");
        List<DownloadRecordManager.Record> records =
                DownloadRecordManager.getRecords(context);
        assertEquals(1, records.size());
        assertEquals(12345L, records.get(0).size);
        assertEquals("deadbeef", records.get(0).sha256);
        assertEquals("x.zip", records.get(0).fileName);
        assertEquals("https://x/y.zip", records.get(0).url);
    }

    @Test
    public void updateFileInfo_unknownIdChangesNothing() {
        DownloadRecordManager.record(context, 7L, "x.zip", "u", "d");
        DownloadRecordManager.updateFileInfo(context, 999L, 1L, "aa");
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
        assertEquals(0L, DownloadRecordManager.getRecords(context).get(0).size);
        assertNull(DownloadRecordManager.getRecords(context).get(0).sha256);
    }

    @Test
    public void updateFileInfo_nullSha256StoredAsNull() {
        DownloadRecordManager.record(context, 7L, "x.zip", "u", "d");
        DownloadRecordManager.updateFileInfo(context, 7L, 10L, null);
        assertEquals(10L, DownloadRecordManager.getRecords(context).get(0).size);
        assertNull(DownloadRecordManager.getRecords(context).get(0).sha256);
    }

    @Test
    public void remove_deletesOnlyMatchingId() {
        DownloadRecordManager.record(context, 1L, "a", "u1", "d");
        DownloadRecordManager.record(context, 2L, "b", "u2", "d");
        DownloadRecordManager.record(context, 1L, "a2", "u1b", "d");
        DownloadRecordManager.remove(context, 1L);
        List<DownloadRecordManager.Record> records =
                DownloadRecordManager.getRecords(context);
        assertEquals(1, records.size());
        assertEquals(2L, records.get(0).downloadId);
    }

    @Test
    public void remove_unknownIdIsNoop() {
        DownloadRecordManager.record(context, 1L, "a", "u1", "d");
        DownloadRecordManager.remove(context, 999L);
        assertEquals(1, DownloadRecordManager.getRecords(context).size());
    }

    @Test
    public void clear_emptiesRecords() {
        DownloadRecordManager.record(context, 1L, "a", "u1", "d");
        DownloadRecordManager.clear(context);
        assertTrue(DownloadRecordManager.getRecords(context).isEmpty());
    }

    @Test
    public void recordConstructor_fiveArgDefaultsSizeZeroShaNull() {
        DownloadRecordManager.Record r = new DownloadRecordManager.Record(
                3L, "n", "u", "d", 123L);
        assertEquals(0L, r.size);
        assertNull(r.sha256);
        assertEquals(123L, r.time);
    }

    @Test
    public void verifyingFlag_setAndClearedPerId() {
        assertFalse(DownloadRecordManager.isVerifying(1L));
        DownloadRecordManager.setVerifying(1L, true);
        assertTrue(DownloadRecordManager.isVerifying(1L));
        assertFalse(DownloadRecordManager.isVerifying(2L));
        DownloadRecordManager.setVerifying(1L, false);
        assertFalse(DownloadRecordManager.isVerifying(1L));
    }
}
