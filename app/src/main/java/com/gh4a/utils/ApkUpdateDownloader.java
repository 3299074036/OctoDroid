package com.gh4a.utils;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.gh4a.BaseActivity;
import com.gh4a.R;

import java.io.File;

/**
 * Downloads an APK via DownloadManager and launches the system installer
 * once the download completes.
 */
public class ApkUpdateDownloader {
    public static void downloadAndInstall(BaseActivity activity, String apkUrl, String fileName) {
        // Mirror the APK host when the user enabled network acceleration.
        final String url = MirrorHelper.rewriteUrl(activity, apkUrl);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            activity.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    (requestCode, permissions, grantResults) -> {
                        if (grantResults.length > 0
                                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            enqueue(activity, url, fileName);
                        }
                    },
                    R.string.download_permission_rationale);
        } else {
            enqueue(activity, url, fileName);
        }
    }

    private static void enqueue(BaseActivity activity, String url, String fileName) {
        DownloadManager dm =
                (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setTitle(activity.getString(R.string.downloading_update))
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setMimeType("application/vnd.android.package-archive");
        final long downloadId = dm.enqueue(request);
        Toast.makeText(activity, R.string.downloading_update, Toast.LENGTH_SHORT).show();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) {
                    return;
                }
                context.unregisterReceiver(this);
                onDownloadComplete(context, dm, downloadId);
            }
        };
        activity.registerReceiver(receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private static void onDownloadComplete(Context context, DownloadManager dm, long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = dm.query(query)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            int status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show();
                return;
            }
            String localUri = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            installApk(context, new File(Uri.parse(localUri).getPath()));
        }
    }

    private static void installApk(Context context, File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            // Ask the user to allow installing unknown apps first.
            Toast.makeText(context, R.string.allow_install_unknown_apps, Toast.LENGTH_LONG).show();
            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(installIntent);
    }
}
