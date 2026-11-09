package com.gh4a.utils;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.worker.NotificationPermission;
import com.meisolsson.githubsdk.model.Download;
import com.meisolsson.githubsdk.model.ReleaseAsset;

import java.io.File;
import java.io.IOException;

import androidx.appcompat.app.AlertDialog;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadUtils {
    public static void enqueueDownloadWithPermissionCheck(final BaseActivity activity,
            final Download download) {
        enqueueDownloadWithPermissionCheck(activity, download.htmlUrl(), download.contentType(),
                download.name(), download.description());
    }

    public static void enqueueDownloadWithPermissionCheck(final BaseActivity activity,
            final ReleaseAsset asset) {
        handleDownloadPermissionCheck(activity, () -> enqueueDownload(activity, asset));
    }

    public static void enqueueDownloadWithPermissionCheck(final BaseActivity activity,
            final String url, final String mimeType, final String fileName, final String description) {
        enqueueDownloadWithPermissionCheck(activity, url, mimeType, fileName, description, false);
    }

    public static void enqueueDownloadWithPermissionCheck(final BaseActivity activity,
            final String url, final String mimeType, final String fileName,
            final String description, final boolean resolveAuthRedirect) {
        handleDownloadPermissionCheck(activity, () -> {
            if (resolveAuthRedirect) {
                enqueueDownloadResolvingRedirect(activity, url, fileName, description, mimeType, null);
            } else {
                enqueueDownload(activity, url, fileName, description, mimeType, null);
            }
        });
    }

    private static void handleDownloadPermissionCheck(final BaseActivity activity, Runnable func) {
        // 0.2.2（S-M5）：下载改落应用私有目录，不再需要 WRITE_EXTERNAL_STORAGE，
        // 任何 API 级别都不用申请，直接执行。
        func.run();
    }

    // Shared client for redirect resolution: built once, reused for every
    // download so the connection pool isn't thrown away each time.
    private static volatile OkHttpClient sRedirectClient;

    private static OkHttpClient getRedirectClient() {
        if (sRedirectClient == null) {
            synchronized (DownloadUtils.class) {
                if (sRedirectClient == null) {
                    sRedirectClient = ServiceFactory.getHttpClientBuilder()
                            .followRedirects(false)
                            .build();
                }
            }
        }
        return sRedirectClient;
    }

    private static void enqueueDownload(final Context context, final ReleaseAsset asset) {
        // Ugly workaround for #972 (see #976 for analysis), suggested by GH support:
        // "First, you make an API call to the endpoint for fetching an asset and you pass in the
        //  token via the Authorization header. You make this call using an HTTP library (not via
        //  the Android Download Manager) and you disable automatic following of redirects when you
        //  make that call (in case that's enabled by default). The result of that call will be a
        //  redirect response with a Location header.
        //  Second, you use the Android Download Manager to download the asset from the URL that's
        //  provided in the Location header from the response of the first step. You would not
        //  provide an Authorization header here since the required authorization is already a
        //  part of the URL."
        enqueueDownloadResolvingRedirect(context, asset.url(), asset.name(), asset.label(),
                asset.contentType(), "application/octet-stream");
    }

    // Same two-step approach as above, but for plain API URLs (e.g. the zipball/tarball
    // endpoints). This makes downloads work for private repositories as well (#1681):
    // the auth token is only sent to api.github.com, never to the redirect target.
    private static void enqueueDownloadResolvingRedirect(final Context context, final String apiUrl,
            final String fileName, final String description, final String mimeType,
            final String acceptHeader) {
        final OkHttpClient client = getRedirectClient();
        final Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl);
        if (acceptHeader != null) {
            requestBuilder.header("Accept", acceptHeader);
        }
        final String token = Gh4Application.get().getAuthToken();
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Token " + token);
        }

        final Handler handler = new Handler(Looper.getMainLooper());

        client.newCall(requestBuilder.build()).enqueue(new Callback() {
            private void completeDownload(final String url) {
                handler.post(() -> {
                    // The redirect resolution is async; the user may have left
                    // the activity while it was in flight. Showing the mobile
                    // data confirmation dialog on a dead activity would crash
                    // with BadTokenException, so bail out quietly instead.
                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                    }
                    enqueueDownload(context, url, fileName, description,
                            mimeType, acceptHeader);
                });
            }
            private void notifyDownloadFailed() {
                handler.post(() -> Toast.makeText(context,
                        R.string.download_failed, Toast.LENGTH_LONG).show());
            }
            @Override
            public void onFailure(Call call, IOException e) {
                // The resolution request itself failed (e.g. no network); the
                // DownloadManager fallback would just fail too and add its own
                // "download unsuccessful" notification on top of our toast, so
                // don't enqueue it — one clear message is enough.
                notifyDownloadFailed();
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() && !r.isRedirect()) {
                        // e.g. 401/404 from api.github.com: the DownloadManager
                        // fallback (which carries no auth header) would fail too.
                        notifyDownloadFailed();
                        return;
                    }
                    String location = r.isRedirect() ? r.header("Location") : null;
                    completeDownload(location != null ? location : call.request().url().toString());
                }
            }
        });
    }

    private static void enqueueDownload(final Context context, String url, final String fileName,
            final String description, final String mimeType,
            final String mediaType) {
        if (url == null) {
            return;
        }

        // 功能2：release 附件、源码包等下载走三级链路
        // （自选镜像 → 默认镜像 → 直连），一条失败自动换下一条；
        // 之前只是单镜像改写，镜像一挂就只能失败
        if (!downloadNeedsWarning(context)) {
            enqueueChainDownload(context, url, fileName, description,
                    mimeType, mediaType, false);
            return;
        }

        DialogInterface.OnClickListener buttonListener = (dialog, which) -> {
            boolean wifiOnly = which == DialogInterface.BUTTON_NEUTRAL;
            enqueueChainDownload(context, url, fileName, description,
                    mimeType, mediaType, wifiOnly);
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.download_mobile_warning_title)
                .setMessage(R.string.download_mobile_warning_message)
                .setPositiveButton(R.string.download_now_button, buttonListener)
                .setNeutralButton(R.string.download_wifi_button, buttonListener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 附件下载走 ChainDownloadHelper：链路失败自动换线，全失败才 toast。 */
    private static void enqueueChainDownload(Context context, String url, String fileName,
            String description, String mimeType, String mediaType, boolean wifiOnly) {
        ChainDownloadHelper.enqueueChain(context, url, fileName, description,
                request -> {
                    request.setDescription(description);
                    if (mimeType != null) {
                        request.setMimeType(mimeType);
                    }
                    if (mediaType != null) {
                        request.addRequestHeader("Accept", mediaType);
                    }
                    if (wifiOnly) {
                        request.setAllowedOverMetered(false);
                    }
                },
                (ctx, success, file, downloadId) -> {
                    if (!success) {
                        Toast.makeText(ctx, R.string.download_failed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (file != null) {
                        // 57：附件下载校验通过后发完成通知（系统通知已改为仅
                        // 下载中显示，见 ChainDownloadHelper）。
                        verifyDownloadedFile(ctx, file, downloadId, true);
                    }
                });
    }

    /**
     * 0.2.2（S-M1）：下载完成后的校验——算 SHA-256＋大小记进下载记录（供
     * 下载列表展示、人工核对）；APK 再比签名：已安装同包名应用的签名必须
     * 一致，不一致删文件＋清记录并提示；未安装则提示发布者签名无法验证。
     * 上游（GitHub Release API）不提供文件哈希，无法自动比对。
     * 哈希与签名校验在后台线程做，不卡主线程。
     * <p>
     * 57（0.2.4）：校验期间记录标为"校验中"（下载列表点开前拦截），且系统
     * 下载完成通知改为仅下载中显示——校验通过后由应用自己发完成通知，
     * 否则用户一点系统通知就装上还没验过签名的 APK。{@code notifyOnComplete}
     * 为 false 时不发通知（自更新有自己的安装提示，见 ApkUpdateDownloader）。
     * <p>
     * P7：带回调的重载——调用方以异步签名结论为准处置（删文件/提示/放行
     * 安装）时传 callback；此时本方法不做自己的签名 UI 处置（不删文件、
     * 不弹签名 toast），只在 MATCH 且要求时发完成通知，避免同一份结论
     * 两处各处置一次（旧自更新路径同步判 + 异步判各弹一次 mismatch toast）。
     * callback 在主线程回调，check 为 null 表示非 APK（无签名结论）。
     */
    public interface ApkSignatureCheckCallback {
        void onSignatureChecked(ApkUpdateDownloader.ApkSignatureCheck check);
    }

    public static void verifyDownloadedFile(Context context, File file, long downloadId,
            boolean notifyOnComplete) {
        verifyDownloadedFile(context, file, downloadId, notifyOnComplete, null);
    }

    public static void verifyDownloadedFile(Context context, File file, long downloadId,
            boolean notifyOnComplete, ApkSignatureCheckCallback callback) {
        verifyDownloadedFile(context, file, downloadId, notifyOnComplete, callback, null);
    }

    /**
     * 带期望包名的重载：expectedPackage 非空时签名校验还要求 APK 包名
     * 等于它（自更新用：只接受自己的包，见 ApkUpdateDownloader L-3）。
     */
    public static void verifyDownloadedFile(Context context, File file, long downloadId,
            boolean notifyOnComplete, ApkSignatureCheckCallback callback,
            String expectedPackage) {
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        DownloadRecordManager.setVerifying(downloadId, true);
        new Thread(() -> {
            String sha256 = computeSha256(file);
            long size = file.length();
            DownloadRecordManager.updateFileInfo(appContext, downloadId, size, sha256);
            final boolean isApk = file.getName().toLowerCase(java.util.Locale.US)
                    .endsWith(".apk");
            final ApkUpdateDownloader.ApkSignatureCheck check = isApk
                    ? (expectedPackage != null
                            ? ApkUpdateDownloader.checkDownloadedApkSignature(
                                    appContext, file, expectedPackage)
                            : ApkUpdateDownloader.checkDownloadedApkSignature(
                                    appContext, file))
                    : null;
            handler.post(() -> {
                DownloadRecordManager.setVerifying(downloadId, false);
                if (callback != null) {
                    // P7：结论交给调用方全权处置，这里只发完成通知。
                    if (check == ApkUpdateDownloader.ApkSignatureCheck.MATCH
                            && notifyOnComplete) {
                        notifyDownloadComplete(appContext, file, downloadId);
                    }
                    callback.onSignatureChecked(check);
                    return;
                }
                if (check == ApkUpdateDownloader.ApkSignatureCheck.MISMATCH) {
                    // 签名与已安装的同包名应用不一致：可能是投毒／冒名包，删掉。
                    file.delete();
                    DownloadRecordManager.remove(appContext, downloadId);
                    Toast.makeText(appContext,
                            R.string.apk_download_signature_mismatch,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (check == ApkUpdateDownloader.ApkSignatureCheck.NOT_INSTALLED) {
                    Toast.makeText(appContext, R.string.apk_signature_unverified,
                            Toast.LENGTH_LONG).show();
                }
                if (notifyOnComplete) {
                    notifyDownloadComplete(appContext, file, downloadId);
                }
            });
        }).start();
    }

    /** 下载完成通知的渠道（API 26+ 才建，低版本直接发）。 */
    private static final String CHANNEL_DOWNLOAD_COMPLETE = "download_complete";

    private static void createDownloadChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_DOWNLOAD_COMPLETE,
                context.getString(R.string.channel_download_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.channel_download_description));
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(channel);
    }

    /**
     * 57：校验通过后由应用自己发下载完成通知（点开即用 FileProvider 打开文件）。
     * 无通知权限时静默跳过（下载列表里仍有记录）。
     */
    // 22 同款：MissingPermission 压制附理由——开头已由
    // NotificationPermission.canPostNotifications() 检查，lint 不认间接检查。
    @android.annotation.SuppressLint("MissingPermission")
    private static void notifyDownloadComplete(Context context, File file, long downloadId) {
        if (!NotificationPermission.canPostNotifications(context)) {
            return;
        }
        createDownloadChannel(context);
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, getMimeTypeForFile(file.getName()))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent contentIntent = PendingIntent.getActivity(context, (int) downloadId,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification notification =
                new NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_COMPLETE)
                        .setSmallIcon(R.drawable.ic_stat_download)
                        .setContentTitle(file.getName())
                        .setContentText(context.getString(R.string.download_complete))
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .build();
        NotificationManagerCompat.from(context).notify((int) downloadId, notification);
    }

    /** 按扩展名取 MIME（与 DownloadListFragment 同款映射，APK 须显式指定）。 */
    private static String getMimeTypeForFile(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".apk")) {
            return "application/vnd.android.package-archive";
        }
        String extension = MimeTypeMap.getFileExtensionFromUrl(lower);
        String mime = extension != null
                ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) : null;
        return mime != null ? mime : "*/*";
    }

    /** 文件的 SHA-256（小写 hex）；算不出返回 null。 */
    private static String computeSha256(java.io.File file) {
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(java.util.Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean downloadNeedsWarning(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.isActiveNetworkMetered();
    }
}
