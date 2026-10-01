package com.gh4a.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import com.gh4a.fragment.SettingsFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exports/imports app settings as a JSON file in the Downloads directory.
 *
 * Backed up: everything in "Gh4a-pref" except account credentials
 * (tokens, login list), the drawer customization prefs, and the
 * translation_* keys from the default shared preferences.
 * Account logins/tokens are deliberately never included.
 */
public class SettingsBackupManager {
    private static final int BACKUP_VERSION = 1;

    private static final String[] EXACT_EXCLUDED_KEYS = {
            "active_login", "logins",
    };

    private static boolean isExcluded(String key) {
        String lower = key.toLowerCase(Locale.US);
        if (lower.contains("token")) {
            return true;
        }
        if (key.startsWith("user_id_")) {
            return true;
        }
        for (String excluded : EXACT_EXCLUDED_KEYS) {
            if (excluded.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject prefsToJson(SharedPreferences prefs, boolean excludeAccounts)
            throws JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            if (excludeAccounts && isExcluded(key)) {
                continue;
            }
            JSONObject holder = new JSONObject();
            if (!putTypedValue(holder, entry.getValue())) {
                continue;
            }
            json.put(key, holder);
        }
        return json;
    }

    /**
     * 把一个偏好值按真实类型写入 holder；不支持的类型返回 false。
     * 注意 translation_* 的备份也要走这里，不能一律按 String 存，
     * 否则恢复后 boolean/int 键会被读出类型抛 ClassCastException。
     */
    private static boolean putTypedValue(JSONObject holder, Object value) throws JSONException {
        if (value instanceof String) {
            holder.put("t", "s");
            holder.put("v", value);
        } else if (value instanceof Integer) {
            holder.put("t", "i");
            holder.put("v", value);
        } else if (value instanceof Long) {
            holder.put("t", "l");
            holder.put("v", value);
        } else if (value instanceof Float) {
            holder.put("t", "f");
            holder.put("v", (double) (Float) value);
        } else if (value instanceof Boolean) {
            holder.put("t", "b");
            holder.put("v", value);
        } else if (value instanceof Set) {
            holder.put("t", "set");
            JSONArray array = new JSONArray();
            // noinspection unchecked
            for (String s : (Set<String>) value) {
                array.put(s);
            }
            holder.put("v", array);
        } else {
            return false;
        }
        return true;
    }

    private static void jsonToPrefs(JSONObject json, SharedPreferences prefs) throws JSONException {
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray names = json.names();
        if (names == null) {
            return;
        }
        for (int i = 0; i < names.length(); i++) {
            String key = names.getString(i);
            if (isExcluded(key)) {
                continue;
            }
            JSONObject holder = json.getJSONObject(key);
            String type = holder.getString("t");
            switch (type) {
                case "s": editor.putString(key, holder.getString("v")); break;
                case "i": editor.putInt(key, holder.getInt("v")); break;
                case "l": editor.putLong(key, holder.getLong("v")); break;
                case "f": editor.putFloat(key, (float) holder.getDouble("v")); break;
                case "b": editor.putBoolean(key, holder.getBoolean("v")); break;
                case "set": {
                    JSONArray array = holder.getJSONArray("v");
                    Set<String> set = new HashSet<>();
                    for (int j = 0; j < array.length(); j++) {
                        set.add(array.getString(j));
                    }
                    editor.putStringSet(key, set);
                    break;
                }
            }
        }
        editor.apply();
    }

    /** Collects all backed-up settings into a single JSON object. */
    public static JSONObject collectBackup(Context context) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", "OctoDroid");
        root.put("version", BACKUP_VERSION);
        root.put("date", System.currentTimeMillis());

        JSONObject prefs = new JSONObject();
        SharedPreferences main = context.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        prefs.put("Gh4a-pref", prefsToJson(main, true));

        SharedPreferences drawer = context.getSharedPreferences(
                "drawer_config", Context.MODE_PRIVATE);
        prefs.put("drawer_config", prefsToJson(drawer, false));

        // Translation provider + keys live in the default shared preferences
        SharedPreferences def = PreferenceManager.getDefaultSharedPreferences(context);
        JSONObject translation = new JSONObject();
        for (Map.Entry<String, ?> entry : def.getAll().entrySet()) {
            if (entry.getKey().startsWith("translation_")) {
                JSONObject single = new JSONObject();
                if (putTypedValue(single, entry.getValue())) {
                    translation.put(entry.getKey(), single);
                }
            }
        }
        prefs.put("default", translation);

        root.put("prefs", prefs);
        return root;
    }

    /** Writes the backup JSON into the public Downloads directory. Returns the file name. */
    public static String writeBackupFile(Context context, JSONObject backup) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "OctoDroid_backup_" + stamp + ".json";
        byte[] data = backup.toString().getBytes(StandardCharsets.UTF_8);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("MediaStore insert failed");
            }
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    throw new IOException("openOutputStream failed");
                }
                out.write(data);
            }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("mkdirs failed");
            }
            File file = new File(dir, fileName);
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
        }
        return fileName;
    }

    /** Reads a backup JSON object from a document Uri. */
    public static JSONObject readBackupFile(Context context, Uri uri)
            throws IOException, JSONException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("openInputStream failed");
            }
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                buffer.write(chunk, 0, n);
            }
            return new JSONObject(buffer.toString(StandardCharsets.UTF_8.name()));
        }
    }

    /** Applies a backup created by {@link #collectBackup}. Returns false on bad format. */
    public static boolean applyBackup(Context context, JSONObject backup) {
        try {
            if (!"OctoDroid".equals(backup.optString("app"))) {
                return false;
            }
            JSONObject prefs = backup.getJSONObject("prefs");
            if (prefs.has("Gh4a-pref")) {
                jsonToPrefs(prefs.getJSONObject("Gh4a-pref"), context.getSharedPreferences(
                        SettingsFragment.PREF_NAME, Context.MODE_PRIVATE));
            }
            if (prefs.has("drawer_config")) {
                jsonToPrefs(prefs.getJSONObject("drawer_config"),
                        context.getSharedPreferences("drawer_config", Context.MODE_PRIVATE));
            }
            if (prefs.has("default")) {
                jsonToPrefs(prefs.getJSONObject("default"),
                        PreferenceManager.getDefaultSharedPreferences(context));
            }
            return true;
        } catch (JSONException e) {
            return false;
        }
    }
}
