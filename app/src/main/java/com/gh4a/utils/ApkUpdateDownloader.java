package com.gh4a.utils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
        // M-NEW-1：apkUrl/fileName 来自远端 release JSON（可能经不可信镜像），
        // 先校验：非 http(s) 直接拒绝，避免 DownloadManager.Request 抛异常导致主线程崩溃
        if (!isHttpUrl(apkUrl)) {
            Toast.makeText(appContext, R.string.download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // 文件名二次清洗：恶意 tag 拼出的非法名在这里拦下，转失败 toast，不抛异常
        final String safeName;
        try {
            safeName = FileUtils.sanitizeFileName(fileName);
        } catch (IllegalArgumentException e) {
            Toast.makeText(appContext, R.string.download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // 下载链路（按顺序尝试，一条失败自动换下一条）：
        // 自选镜像（开了镜像加速）→ 默认镜像 → 直连。
        // VPN 下直连可用；无 VPN 时走镜像；自选镜像挂了还有默认镜像兜底。
        // 任何一条通就能下到包，两种网络下都成立。
        // 重试机走 ChainDownloadHelper（与 Release 附件下载共用）。
        Runnable startDownload = () -> {
            Toast.makeText(appContext, R.string.downloading_update, Toast.LENGTH_SHORT).show();
            ChainDownloadHelper.enqueueChain(appContext, apkUrl, safeName,
                    appContext.getString(R.string.downloading_update),
                    request -> request.setMimeType("application/vnd.android.package-archive"),
                    (ctx, success, file) -> {
                        if (!success) {
                            Toast.makeText(ctx, R.string.download_failed,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        // 自更新安全门：APK 签名必须与当前已安装应用一致，
                        // 否则可能是镜像站被投毒，拒绝安装并删除文件 (L-3)
                        if (!isSignatureMatch(ctx, file)) {
                            Toast.makeText(ctx, R.string.apk_signature_mismatch,
                                    Toast.LENGTH_LONG).show();
                            file.delete();
                            return;
                        }
                        installApk(ctx, file);
                    });
        };
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
     * release 版本号拼下载文件名：白名单清洗，防止恶意 tag 注入路径穿越或特殊字符
     * （M-NEW-1）。GitHub 真实 tag 不可能含这些字符，清洗不影响正常版本。
     */
    static String buildApkFileName(String latestVersion) {
        String v = latestVersion == null ? "" : latestVersion.replaceAll("[^A-Za-z0-9._-]", "_");
        while (v.contains("..")) {
            v = v.replace("..", "_");
        }
        v = v.replaceAll("^\\.+", "");
        if (v.isEmpty()) {
            v = "update";
        }
        return "OctoDroid_" + v + ".apk";
    }

    private static boolean isHttpUrl(String url) {
        return url != null
                && (url.regionMatches(true, 0, "http://", 0, 7)
                        || url.regionMatches(true, 0, "https://", 0, 8));
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
