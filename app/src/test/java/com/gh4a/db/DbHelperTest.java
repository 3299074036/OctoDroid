package com.gh4a.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DbHelperTest {
    private Context mContext;
    private DbHelper mHelper;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.deleteDatabase("gh4adb.db");
        mHelper = new DbHelper(mContext);
    }

    @After
    public void tearDown() {
        mHelper.close();
        mContext.deleteDatabase("gh4adb.db");
    }

    @Test
    public void onCreate_createsSuggestionsTable() {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='suggestions'", null);
        assertTrue(c.moveToFirst());
        assertEquals("suggestions", c.getString(0));
        c.close();
    }

    @Test
    public void suggestionsTable_insertAndQueryRoundTrip() {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("type", 0);
        v.put("suggestion", "octocat/Hello-World");
        v.put("date", 12345L);
        long rowId = db.insert("suggestions", null, v);
        assertTrue(rowId > 0);

        Cursor c = db.query("suggestions", null, "type = ?", new String[]{"0"},
                null, null, null);
        assertTrue(c.moveToFirst());
        assertEquals("octocat/Hello-World", c.getString(c.getColumnIndexOrThrow("suggestion")));
        assertEquals(12345L, c.getLong(c.getColumnIndexOrThrow("date")));
        c.close();
    }

    @Test
    public void suggestionsTable_uniqueTypeSuggestionReplaces() {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        ContentValues v1 = new ContentValues();
        v1.put("type", 1);
        v1.put("suggestion", "octo");
        v1.put("date", 1L);
        ContentValues v2 = new ContentValues();
        v2.put("type", 1);
        v2.put("suggestion", "octo");
        v2.put("date", 2L);
        db.insert("suggestions", null, v1);
        db.insert("suggestions", null, v2);
        Cursor c = db.query("suggestions", null, "type = ? AND suggestion = ?",
                new String[]{"1", "octo"}, null, null, null);
        assertEquals(1, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals(2L, c.getLong(c.getColumnIndexOrThrow("date")));
        c.close();
    }

    @Test
    public void onUpgrade_fromVersion1_createsTableAndDropsBookmarks() {
        // Use a raw database, not the helper's own: getWritableDatabase()
        // would run onCreate first, and createSuggestionsTable() has no
        // IF NOT EXISTS, so a second create would throw.
        SQLiteDatabase db = mContext.openOrCreateDatabase("upgrade-v1.db",
                Context.MODE_PRIVATE, null);
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (_id integer primary key);");
            mHelper.onUpgrade(db, 1, 5);
            Cursor c = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='suggestions'", null);
            assertTrue(c.moveToFirst());
            c.close();
            Cursor b = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'", null);
            assertEquals(0, b.getCount());
            b.close();
        } finally {
            db.close();
            mContext.deleteDatabase("upgrade-v1.db");
        }
    }

    @Test
    public void onUpgrade_fromVersion2_onlyDropsBookmarks() {
        SQLiteDatabase db = mContext.openOrCreateDatabase("upgrade-v2.db",
                Context.MODE_PRIVATE, null);
        try {
            mHelper.onCreate(db);
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (_id integer primary key);");
            mHelper.onUpgrade(db, 2, 5);
            Cursor b = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'", null);
            assertEquals(0, b.getCount());
            b.close();
            // suggestions table created by onCreate above stays usable
            ContentValues v = new ContentValues();
            v.put("type", 2);
            v.put("suggestion", "code search");
            assertTrue(db.insert("suggestions", null, v) > 0);
        } finally {
            db.close();
            mContext.deleteDatabase("upgrade-v2.db");
        }
    }

    @Test
    public void onUpgrade_fromVersion4_dropsBookmarksWithoutCreatingTable() {
        SQLiteDatabase db = mContext.openOrCreateDatabase("upgrade-v4.db",
                Context.MODE_PRIVATE, null);
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (_id integer primary key);");
            // oldVersion 4: skips createSuggestionsTable (< 2 false), drops bookmarks
            mHelper.onUpgrade(db, 4, 5);
            Cursor b = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'", null);
            assertEquals(0, b.getCount());
            b.close();
            Cursor s = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='suggestions'", null);
            assertEquals(0, s.getCount());
            s.close();
        } finally {
            db.close();
            mContext.deleteDatabase("upgrade-v4.db");
        }
    }

    @Test
    public void onUpgrade_fromVersion5_isNoop() {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        mHelper.onUpgrade(db, 5, 5);
        Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='suggestions'", null);
        assertTrue(c.moveToFirst());
        c.close();
    }
}
