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
        final Context appContext = activity.getApplicationContext();
        // 主线路：开了镜像加速走镜像，否则直连
        final String primaryUrl = MirrorHelper.rewriteUrl(activity, apkUrl);
        // 备线路：主用镜像失败则回落直连（VPN 下直连可用）；主用直连失败则试默认镜像
        // （无 VPN 时直连不可用）。两条都失败才报错，两种网络下都能下到包。
        final String fallbackUrl = computeFallbackUrl(activity, apkUrl, primaryUrl);
        Runnable startDownload = () -> enqueue(appContext, primaryUrl, fallbackUrl, fileName);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            activity.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    (requestCode, permissions, grantResults) -> {
                        if (grantResults.length > 0
                                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            startDownload.run();
                        }
                    },
                    R.string.download_permission_rationale);
        } else {
            startDownload.run();
        }
    }

    /**
     * 计算备选下载地址：与主线路不同才有意义，相同（如不可镜像的 host）返回 null。
     */
    private static String computeFallbackUrl(Context context, String originalUrl, String primaryUrl) {
        String fallback;
        if (!primaryUrl.equals(originalUrl)) {
            fallback = originalUrl; // 主用镜像 → 备选直连
        } else {
            fallback = MirrorHelper.rewriteUrlWithBase(
                    MirrorHelper.DEFAULT_PRESET, originalUrl); // 主用直连 → 备选默认镜像
        }
        return fallback != null && !fallback.equals(primaryUrl) ? fallback : null;
    }

    private static void enqueue(Context context, String url, String fallbackUrl, String fileName) {
        DownloadManager dm =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        // 文件名来自更新检查的网络响应：sanitize 防止路径穿越 (L-2)
        final String safeName = sanitizeFileName(fileName);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                .setTitle(context.getString(R.string.downloading_update))
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setMimeType("application/vnd.android.package-archive");
        final long downloadId = dm.enqueue(request);
        Toast.makeText(context, R.string.downloading_update, Toast.LENGTH_SHORT).show();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) {
                    return;
                }
                context.unregisterReceiver(this);
                onDownloadComplete(context, dm, downloadId, fallbackUrl, fileName);
            }
        };
        // 用 application context 注册，避免 Activity 销毁后 receiver 泄漏；
        // onReceive 里用传入的 context 反注册，两边一致
        // targetSdk 34+ 必须显式声明 receiver 是否 exported，否则抛 SecurityException；
        // 用 ContextCompat 兼容低版本（内部做版本判断），lint 也能识别
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private static void onDownloadComplete(Context context, DownloadManager dm, long downloadId,
            String fallbackUrl, String fileName) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = dm.query(query)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            int status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                if (fallbackUrl != null) {
                    // 主线路失败，换备线路重试一次
                    Toast.makeText(context, R.string.download_retry_alt, Toast.LENGTH_SHORT).show();
                    enqueue(context, fallbackUrl, null, fileName);
                } else {
                    Toast.makeText(context, R.string.download_failed, Toast.LENGTH_LONG).show();
                }
                return;
            }
            String localUri = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            File apkFile = new File(Uri.parse(localUri).getPath());
            // 自更新安全门：APK 签名必须与当前已安装应用一致，
            // 否则可能是镜像站被投毒，拒绝安装并删除文件 (L-3)
            if (!isSignatureMatch(context, apkFile)) {
                Toast.makeText(context, R.string.apk_signature_mismatch, Toast.LENGTH_LONG).show();
                apkFile.delete();
                return;
            }
            installApk(context, apkFile);
        }
    }

    /**
     * 清洗下载文件名：只取 basename，拒绝包含 ".." 的文件名 (L-2)。
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("fileName must not be null");
        }
        int cut = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = cut >= 0 ? fileName.substring(cut + 1) : fileName;
        if (base.isEmpty() || base.contains("..")) {
            throw new IllegalArgumentException("Unsafe download file name: " + fileName);
        }
        return base;
    }

    /**
     * 校验下载到的 APK 签名证书与当前已安装应用一致。
     * API 28+ 用 signingInfo，低版本用 signatures，兼容 targetSdk 33 (L-3)。
     */
    private static boolean isSignatureMatch(Context context, File apkFile) {
        try {
            PackageManager pm = context.getPackageManager();
            int flags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            }
            android.content.pm.PackageInfo archiveInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), flags);
            android.content.pm.PackageInfo installedInfo =
                    pm.getPackageInfo(context.getPackageName(), flags);
            if (archiveInfo == null || installedInfo == null) {
                return false;
            }
            android.content.pm.Signature[] archiveSigs = getSignatures(archiveInfo);
            android.content.pm.Signature[] installedSigs = getSignatures(installedInfo);
            if (archiveSigs == null || archiveSigs.length == 0
                    || installedSigs == null || installedSigs.length == 0) {
                return false;
            }
            // 已安装应用的每个签名都必须在下载包中找到
            for (android.content.pm.Signature installed : installedSigs) {
                boolean found = false;
                for (android.content.pm.Signature archive : archiveSigs) {
                    if (installed.equals(archive)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static android.content.pm.Signature[] getSignatures(
            android.content.pm.PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            if (info.signingInfo.hasMultipleSigners()) {
                return info.signingInfo.getApkContentsSigners();
            }
            return info.signingInfo.getSigningCertificateHistory();
        }
        return info.signatures;
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
