package com.gh4a.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.model.Download;
import com.meisolsson.githubsdk.model.ReleaseAsset;

import java.io.IOException;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ doesn't require WRITE_EXTERNAL_STORAGE permission anymore,
            // since we're writing to a pre-defined public directory only
            func.run();
            return;
        }
        final ActivityCompat.OnRequestPermissionsResultCallback cb =
                (requestCode, permissions, grantResults) -> {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        func.run();
                    }
                };
        activity.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, cb,
                R.string.download_permission_rationale);
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
                (ctx, success, file) -> {
                    if (!success) {
                        Toast.makeText(ctx, R.string.download_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean downloadNeedsWarning(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.isActiveNetworkMetered();
    }
}
