package com.gh4a.db;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import android.util.Log;

import com.gh4a.BuildConfig;
import com.gh4a.Gh4Application;

import java.util.Locale;

public class SuggestionsProvider extends ContentProvider {
    private static final String TAG = "SuggestionsProvider";

    public interface Columns extends BaseColumns {
        Uri CONTENT_URI = Uri.parse(String.format(Locale.US,
                "content://%s.SuggestionsProvider/suggestions", BuildConfig.APPLICATION_ID));

        String TYPE = "type";
        String SUGGESTION = "suggestion";
        String DATE = "date";

        int TYPE_REPO = 0;
        int TYPE_USER = 1;
        int TYPE_CODE = 2;
    }

    private static final int MATCH_ALL = 0;

    // S-L4：调用方传入的 selection/sortOrder 只接受白名单（Provider 虽然
    // exported=false，但原样进 SQLiteQueryBuilder 仍是注入面；实际调用方
    // 只有 SearchFragment 的三组常量模板）。
    private static final String SELECTION_TYPE =
            Columns.TYPE + " = ?";
    private static final String SELECTION_TYPE_AND_SUGGESTION_LIKE =
            Columns.TYPE + " = ? AND " + Columns.SUGGESTION + " LIKE ?";
    private static final String SELECTION_TYPE_AND_SUGGESTION_EQ =
            Columns.TYPE + " = ? AND " + Columns.SUGGESTION + " = ?";
    private static final String[] SORT_COLUMNS = {"date", "suggestion", "type", "_id"};
    private static final String DEFAULT_SORT_ORDER = Columns.DATE + " desc";

    private static boolean isAllowedSelection(String selection) {
        return selection == null
                || SELECTION_TYPE.equals(selection)
                || SELECTION_TYPE_AND_SUGGESTION_LIKE.equals(selection)
                || SELECTION_TYPE_AND_SUGGESTION_EQ.equals(selection);
    }

    /** sortOrder 只接受白名单列名 + asc/desc；不合法时回落默认 date desc。 */
    private static String sanitizeSortOrder(String sortOrder) {
        if (sortOrder == null) {
            return DEFAULT_SORT_ORDER;
        }
        StringBuilder out = new StringBuilder();
        for (String part : sortOrder.split(",")) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length == 0 || tokens.length > 2) {
                return DEFAULT_SORT_ORDER;
            }
            String col = tokens[0].toLowerCase(Locale.US);
            boolean okCol = false;
            for (String c : SORT_COLUMNS) {
                if (c.equals(col)) {
                    okCol = true;
                    break;
                }
            }
            if (!okCol) {
                return DEFAULT_SORT_ORDER;
            }
            String dir = "asc";
            if (tokens.length == 2) {
                if ("asc".equalsIgnoreCase(tokens[1])) {
                    dir = "asc";
                } else if ("desc".equalsIgnoreCase(tokens[1])) {
                    dir = "desc";
                } else {
                    return DEFAULT_SORT_ORDER;
                }
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(col).append(' ').append(dir);
        }
        return out.length() == 0 ? DEFAULT_SORT_ORDER : out.toString();
    }

    /** projection 只留已知列；null 表示全部列（本表只有这 4 列），原样放行。 */
    private static String[] sanitizeProjection(String[] projection) {
        if (projection == null) {
            return null;
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String col : projection) {
            if (col == null) {
                continue;
            }
            String c = col.trim();
            if (Columns._ID.equals(c) || Columns.TYPE.equals(c)
                    || Columns.SUGGESTION.equals(c) || Columns.DATE.equals(c)) {
                out.add(c);
            }
        }
        return out.toArray(new String[0]);
    }

    private static final UriMatcher
            sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(
                String.format(Locale.US, "%s.SuggestionsProvider", BuildConfig.APPLICATION_ID),
                "suggestions", MATCH_ALL);
    }

    private DbHelper mDbHelper;

    @Override
    public boolean onCreate() {
        mDbHelper = new DbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        int match = sURIMatcher.match(uri);

        qb.setTables(DbHelper.SUGGESTIONS_TABLE);

        switch (match) {
            case MATCH_ALL:
                break;
            default:
                // S-L1：URI 去 query 再记（与 URL 日志同款处理）。
                Log.e(TAG, "query: invalid request: "
                        + Gh4Application.redactUrlForLog(uri.toString()));
                return null;
        }

        if (!isAllowedSelection(selection)) {
            Log.e(TAG, "query: invalid selection");
            return null;
        }
        sortOrder = sanitizeSortOrder(sortOrder);
        projection = sanitizeProjection(projection);

        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor ret = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        ret.setNotificationUri(getContext().getContentResolver(), uri);

        return ret;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        if (sURIMatcher.match(uri) != MATCH_ALL) {
            return null;
        }

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        long rowID = db.insert(DbHelper.SUGGESTIONS_TABLE, null, values);
        if (rowID <= 0) {
            return null;
        }

        getContext().getContentResolver().notifyChange(Columns.CONTENT_URI, null);

        return ContentUris.withAppendedId(Columns.CONTENT_URI, rowID);
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count;
        int match = sURIMatcher.match(uri);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        switch (match) {
            case MATCH_ALL:
                // S-L4：与 query() 同款 selection 白名单。
                if (!isAllowedSelection(selection)) {
                    Log.e(TAG, "update: invalid selection");
                    return 0;
                }
                count = db.update(DbHelper.SUGGESTIONS_TABLE, values, selection, selectionArgs);
                break;
            default:
                throw new UnsupportedOperationException("Cannot update that URI: " + uri);
        }

        if (count > 0) {
            getContext().getContentResolver().notifyChange(Columns.CONTENT_URI, null);
        }

        return count;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        int match = sURIMatcher.match(uri);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        switch (match) {
            case MATCH_ALL:
                break;
            default:
                throw new UnsupportedOperationException("Cannot delete the URI " + uri);
        }

        // S-L4：与 query() 同款 selection 白名单。
        if (!isAllowedSelection(selection)) {
            Log.e(TAG, "delete: invalid selection");
            return 0;
        }

        int count = db.delete(DbHelper.SUGGESTIONS_TABLE, selection, selectionArgs);

        if (count > 0) {
            getContext().getContentResolver().notifyChange(Columns.CONTENT_URI, null);
        }

        return count;
    }
}
