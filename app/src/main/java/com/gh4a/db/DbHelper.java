package com.gh4a.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 本地数据库：只保留搜索建议表（suggestions）。
 * 书签功能已剔除，bookmarks 表在升级时删除。
 */
public class DbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "gh4adb.db";
    private static final int DATABASE_VERSION = 5;

    static final String SUGGESTIONS_TABLE = "suggestions";

    public DbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createSuggestionsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createSuggestionsTable(db);
        }
        if (oldVersion < 5) {
            // 书签功能已剔除，删表
            db.execSQL("DROP TABLE IF EXISTS bookmarks;");
        }
    }

    private void createSuggestionsTable(SQLiteDatabase db) {
        db.execSQL("create table " + SUGGESTIONS_TABLE + " ("
                + "_id integer primary key autoincrement, "
                + "type integer not null, "
                + "suggestion text, "
                + "date long, "
                + "unique (type, suggestion) on conflict replace);");
    }
}
