package com.gh4a.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

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
        // SEC-8: APK 下载只接受 HTTPS。http:// 在此直接拒绝（见 isHttpsUrl）。
        if (!isHttpsUrl(apkUrl)) {
            Toast.makeText(appContext, R.string.download_failed, Toast.LENGTH_LONG).show();
            return;
        }
        // SEC-8: 链路级防降级——ChainDownloadHelper 会按 [自选镜像 → 默认
        // 镜像 → 直连] 展开同一条链；这里先把整条链算出来，任一环非 HTTPS
        // 即整单拒绝，不让某条镜像链把下载降级到 http。注意 DownloadManager
        // 内部跟随的重定向无法被应用层观测（无拦截接口），那一层残余风险
        // 靠 HTTPS 入口 + 下面的签名一致性检查兜底。
        for (String chainUrl : MirrorHelper.buildFallbackChain(appContext, apkUrl, false)) {
            if (!isHttpsUrl(chainUrl)) {
                Toast.makeText(appContext, R.string.download_failed, Toast.LENGTH_LONG).show();
                return;
            }
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
                    (ctx, success, file, downloadId) -> {
                        if (!success) {
                            Toast.makeText(ctx, R.string.download_failed,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        // 62：自更新同样走 verifyDownloadedFile 补记大小/SHA-256
                        // （不另发完成通知：自更新有自己的安装提示）。
                        // P7（处方一：以异步结论为准）：原先这里紧接着同步调
                        // isSignatureMatch——回调跑在 BroadcastReceiver
                        // onReceive（主线程），getPackageArchiveInfo 对
                        // 10–50MB 的 APK 做文件 IO + 签名解析全在主线程，
                        // 且与 verify 里的异步校验重复。现改为等异步结论：
                        // MATCH 才放行安装；MISMATCH/NOT_INSTALLED 按原
                        // 同步分支同样删文件、清记录、提示签名不一致（自更
                        // 新包名必为已安装的自己，NOT_INSTALLED 在此同样
                        // 视为不一致，与原行为一致）。安装提示仍在校验结论
                        // 之后出现、顺序不变，只是不再卡主线程；另旧路径同
                        // 一份 MISMATCH 会同步、异步各弹一次 toast，现只弹一次。
                        DownloadUtils.verifyDownloadedFile(ctx, file, downloadId, false,
                                check -> {
                                    if (check != ApkSignatureCheck.MATCH) {
                                        Toast.makeText(ctx, R.string.apk_signature_mismatch,
                                                Toast.LENGTH_LONG).show();
                                        file.delete();
                                        DownloadRecordManager.remove(ctx, downloadId);
                                        return;
                                    }
                                    installApk(ctx, file);
                                }, ctx.getPackageName());
                    });
        };
        // 0.2.2（S-M5）：下载改落应用私有目录，不再需要 WRITE_EXTERNAL_STORAGE，
        // 任何 API 级别都不用申请，直接开始下载。
        startDownload.run();
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

    /** SEC-8: APK 下载路径只接受 HTTPS；http:// 一律拒绝。 */
    private static boolean isHttpsUrl(String url) {
        return url != null && url.regionMatches(true, 0, "https://", 0, 8);
    }

    /** 下载到的 APK 的签名校验结果（S-M1，供 Release 附件下载复用）。 */
    public enum ApkSignatureCheck {
        /** 与已安装的同包名应用签名一致。 */
        MATCH,
        /** 该包名未安装，发布者签名无法验证。 */
        NOT_INSTALLED,
        /** 与已安装的同包名应用签名不一致，或包本身无法解析。 */
        MISMATCH
    }

    /**
     * 校验下载到的 APK：取其包名，找已安装的同包名应用比签名。
     * 未安装 → NOT_INSTALLED（无法验证发布者）；签名不一致或包无法解析
     * → MISMATCH。expectedPackage 非空时还要求包名等于它（自更新用）。
     */
    public static ApkSignatureCheck checkDownloadedApkSignature(Context context, File apkFile) {
        return checkApkSignature(context, apkFile, null);
    }

    /**
     * 带期望包名的校验：expectedPackage 非空时还要求 APK 包名等于它
     * （自更新只接受自己的包，L-3；P7 异步结论即走此口径，与原同步
     * isSignatureMatch 的判定一致）。
     */
    public static ApkSignatureCheck checkDownloadedApkSignature(Context context, File apkFile,
            String expectedPackage) {
        return checkApkSignature(context, apkFile, expectedPackage);
    }

    private static ApkSignatureCheck checkApkSignature(Context context, File apkFile,
            String expectedPackage) {
        try {
            PackageManager pm = context.getPackageManager();
            int flags = PackageManager.GET_SIGNATURES;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags |= PackageManager.GET_SIGNING_CERTIFICATES;
            }
            android.content.pm.PackageInfo archiveInfo = pm.getPackageArchiveInfo(
                    apkFile.getAbsolutePath(), flags);
            if (archiveInfo == null || archiveInfo.packageName == null) {
                return ApkSignatureCheck.MISMATCH;
            }
            if (expectedPackage != null
                    && !expectedPackage.equals(archiveInfo.packageName)) {
                return ApkSignatureCheck.MISMATCH;
            }
            android.content.pm.PackageInfo installedInfo;
            try {
                installedInfo = pm.getPackageInfo(archiveInfo.packageName, flags);
            } catch (PackageManager.NameNotFoundException e) {
                return ApkSignatureCheck.NOT_INSTALLED;
            }
            if (installedInfo == null) {
                return ApkSignatureCheck.MISMATCH;
            }
            android.content.pm.Signature[] archiveSigs = getSignatures(archiveInfo);
            android.content.pm.Signature[] installedSigs = getSignatures(installedInfo);
            if (archiveSigs == null || archiveSigs.length == 0
                    || installedSigs == null || installedSigs.length == 0) {
                return ApkSignatureCheck.MISMATCH;
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
                    return ApkSignatureCheck.MISMATCH;
                }
            }
            return ApkSignatureCheck.MATCH;
        } catch (Exception e) {
            return ApkSignatureCheck.MISMATCH;
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
