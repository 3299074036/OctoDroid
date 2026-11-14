package com.gh4a.db;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.db.SuggestionsProvider.Columns;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SuggestionsProviderTest {
    private SuggestionsProvider mProvider;
    private Context mContext;

    private static final Uri BAD_URI = Uri.parse("content://com.gh4a.unknown/suggestions");
    private static final String SEL_TYPE = "type = ?";
    private static final String SEL_TYPE_SUGGESTION_LIKE = "type = ? AND suggestion LIKE ?";
    private static final String SEL_TYPE_SUGGESTION_EQ = "type = ? AND suggestion = ?";

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.deleteDatabase("gh4adb.db");
        mProvider = Robolectric.buildContentProvider(SuggestionsProvider.class).create().get();
    }

    @After
    public void tearDown() {
        mContext.deleteDatabase("gh4adb.db");
    }

    private ContentValues values(int type, String suggestion) {
        ContentValues v = new ContentValues();
        v.put(Columns.TYPE, type);
        v.put(Columns.SUGGESTION, suggestion);
        v.put(Columns.DATE, 1000L);
        return v;
    }

    private Uri insertOne(int type, String suggestion) {
        Uri uri = mProvider.insert(Columns.CONTENT_URI, values(type, suggestion));
        assertNotNull(uri);
        return uri;
    }

    @Test
    public void onCreate_returnsTrue() {
        assertTrue(mProvider.onCreate());
    }

    @Test
    public void getType_alwaysNull() {
        assertNull(mProvider.getType(Columns.CONTENT_URI));
        assertNull(mProvider.getType(BAD_URI));
    }

    @Test
    public void insert_valid_returnsAppendedIdUri() {
        Uri uri = insertOne(Columns.TYPE_REPO, "octo/repo");
        assertEquals(Columns.CONTENT_URI.toString() + "/1", uri.toString());
        assertEquals(1L, ContentUris.parseId(uri));
    }

    @Test
    public void insert_nullValues_returnsNull() {
        assertNull(mProvider.insert(Columns.CONTENT_URI, null));
    }

    @Test
    public void insert_unknownUri_returnsNull() {
        assertNull(mProvider.insert(BAD_URI, values(0, "x")));
    }

    @Test
    public void insert_conflictingTypeSuggestion_replacesRow() {
        insertOne(0, "dup");
        Uri second = insertOne(0, "dup");
        assertNotNull(second);
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE_SUGGESTION_EQ,
                new String[]{"0", "dup"}, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void query_allRows_noSelection() {
        insertOne(0, "a");
        insertOne(1, "b");
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, null, null, null);
        assertNotNull(c);
        assertEquals(2, c.getCount());
        c.close();
    }

    @Test
    public void query_typeSelection_filtersRows() {
        insertOne(0, "a");
        insertOne(1, "b");
        insertOne(0, "c");
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE,
                new String[]{"0"}, null);
        assertNotNull(c);
        assertEquals(2, c.getCount());
        c.close();
    }

    @Test
    public void query_likeSelection_matchesPrefix() {
        insertOne(0, "octocat/repo");
        insertOne(0, "other/repo");
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE_SUGGESTION_LIKE,
                new String[]{"0", "octo%"}, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void query_invalidSelection_returnsNull() {
        insertOne(0, "a");
        assertNull(mProvider.query(Columns.CONTENT_URI, null,
                "suggestion = 'a' OR 1=1", null, null));
        assertNull(mProvider.query(Columns.CONTENT_URI, null,
                "type = ? OR suggestion = ?", new String[]{"0", "a"}, null));
    }

    @Test
    public void query_selectionArgsMismatch_returnsNull() {
        insertOne(0, "a");
        // selection has 1 placeholder but no args
        assertNull(mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE, null, null));
        // selection has 1 placeholder but 2 args
        assertNull(mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE,
                new String[]{"0", "extra"}, null));
        // null selection but args present
        assertNull(mProvider.query(Columns.CONTENT_URI, null, null,
                new String[]{"0"}, null));
    }

    @Test
    public void query_unknownUri_returnsNull() {
        assertNull(mProvider.query(BAD_URI, null, null, null, null));
    }

    @Test
    public void query_sortOrder_validColumnRespected() {
        insertOne(0, "b");
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI,
                new String[]{Columns.SUGGESTION}, SEL_TYPE, new String[]{"0"}, "suggestion asc");
        assertNotNull(c);
        assertTrue(c.moveToFirst());
        assertEquals("a", c.getString(0));
        assertTrue(c.moveToNext());
        assertEquals("b", c.getString(0));
        c.close();
    }

    @Test
    public void query_sortOrder_injectionFallsBackToDateDesc() {
        insertOne(0, "a");
        // malicious sortOrder must not crash and must fall back to default
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE,
                new String[]{"0"}, "date desc; DROP TABLE suggestions");
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
        // bad direction / bad column / too many tokens all fall back
        for (String bad : new String[]{"nonsense", "date sideways", "date desc extra token",
                "date, suggestion desc, type asc extra"}) {
            Cursor c2 = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE,
                    new String[]{"0"}, bad);
            assertNotNull("sortOrder " + bad, c2);
            assertEquals(1, c2.getCount());
            c2.close();
        }
    }

    @Test
    public void query_sortOrder_multiColumnValid() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE,
                new String[]{"0"}, "type asc, date desc");
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void query_projection_unknownColumnsDropped() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI,
                new String[]{Columns.SUGGESTION, "evil_column", null, Columns.TYPE},
                SEL_TYPE, new String[]{"0"}, null);
        assertNotNull(c);
        assertEquals(2, c.getColumnCount());
        assertTrue(c.moveToFirst());
        assertEquals("a", c.getString(c.getColumnIndexOrThrow(Columns.SUGGESTION)));
        c.close();
    }

    @Test
    public void query_projection_allUnknownColumns_returnsAllColumns() {
        insertOne(0, "a");
        // sanitizeProjection drops every unknown column, leaving an empty
        // array; SQLiteQueryBuilder treats an empty projection as SELECT *.
        Cursor c = mProvider.query(Columns.CONTENT_URI,
                new String[]{"evil"}, SEL_TYPE, new String[]{"0"}, null);
        assertNotNull(c);
        assertEquals(4, c.getColumnCount());
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void update_validSelection_updatesAndReturnsCount() {
        insertOne(0, "old");
        ContentValues v = new ContentValues();
        v.put(Columns.SUGGESTION, "new");
        int count = mProvider.update(Columns.CONTENT_URI, v, SEL_TYPE_SUGGESTION_EQ,
                new String[]{"0", "old"});
        assertEquals(1, count);
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE_SUGGESTION_EQ,
                new String[]{"0", "new"}, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void update_invalidSelection_returnsZero() {
        insertOne(0, "old");
        ContentValues v = new ContentValues();
        v.put(Columns.SUGGESTION, "new");
        assertEquals(0, mProvider.update(Columns.CONTENT_URI, v, "1=1", null));
        assertEquals(0, mProvider.update(Columns.CONTENT_URI, v, SEL_TYPE, null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void update_unknownUri_throws() {
        mProvider.update(BAD_URI, values(0, "x"), null, null);
    }

    @Test
    public void delete_validSelection_deletesAndReturnsCount() {
        insertOne(0, "a");
        insertOne(0, "b");
        int count = mProvider.delete(Columns.CONTENT_URI, SEL_TYPE, new String[]{"0"});
        assertEquals(2, count);
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, null, null, null);
        assertNotNull(c);
        assertEquals(0, c.getCount());
        c.close();
    }

    @Test
    public void delete_invalidSelection_returnsZero() {
        insertOne(0, "a");
        assertEquals(0, mProvider.delete(Columns.CONTENT_URI, "1=1", null));
        assertEquals(0, mProvider.delete(Columns.CONTENT_URI, SEL_TYPE, null));
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, null, null, null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void delete_unknownUri_throws() {
        mProvider.delete(BAD_URI, null, null);
    }

    @Test
    public void columns_typeConstants() {
        assertEquals(0, Columns.TYPE_REPO);
        assertEquals(1, Columns.TYPE_USER);
        assertEquals(2, Columns.TYPE_CODE);
        assertEquals("type", Columns.TYPE);
        assertEquals("suggestion", Columns.SUGGESTION);
        assertEquals("date", Columns.DATE);
    }

    @Test
    public void query_nullSelectionWithEmptyArgs_returnsAllRows() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, null, new String[0], null);
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void query_sortOrder_threeTokens_returnsDefaultOrder() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI, null, SEL_TYPE,
                new String[]{"0"}, "suggestion asc extra");
        assertNotNull(c);
        assertEquals(1, c.getCount());
        c.close();
    }

    @Test
    public void query_projection_typeColumnReturned() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI, new String[]{Columns.TYPE},
                SEL_TYPE, new String[]{"0"}, null);
        assertNotNull(c);
        assertEquals(1, c.getColumnCount());
        assertEquals(Columns.TYPE, c.getColumnName(0));
        assertTrue(c.moveToFirst());
        assertEquals("0", c.getString(0));
        c.close();
    }

    @Test
    public void query_projection_dateColumnReturned() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI, new String[]{Columns.DATE},
                SEL_TYPE, new String[]{"0"}, null);
        assertNotNull(c);
        assertEquals(1, c.getColumnCount());
        assertEquals(Columns.DATE, c.getColumnName(0));
        c.close();
    }

    @Test
    public void insert_missingNotNullType_returnsNull() {
        ContentValues v = new ContentValues();
        v.put(Columns.SUGGESTION, "x");
        assertNull(mProvider.insert(Columns.CONTENT_URI, v));
    }

    @Test
    public void update_noMatchingRows_returnsZero() {
        insertOne(0, "a");
        ContentValues v = new ContentValues();
        v.put(Columns.SUGGESTION, "b");
        assertEquals(0, mProvider.update(Columns.CONTENT_URI, v, SEL_TYPE,
                new String[]{"nope"}));
    }

    @Test
    public void delete_noMatchingRows_returnsZero() {
        insertOne(0, "a");
        assertEquals(0, mProvider.delete(Columns.CONTENT_URI, SEL_TYPE,
                new String[]{"nope"}));
    }


    @Test
    public void query_projection_idColumnReturned() {
        insertOne(0, "a");
        Cursor c = mProvider.query(Columns.CONTENT_URI, new String[]{Columns._ID},
                SEL_TYPE, new String[]{"0"}, null);
        assertNotNull(c);
        assertEquals(1, c.getColumnCount());
        assertEquals(Columns._ID, c.getColumnName(0));
        c.close();
    }

}
