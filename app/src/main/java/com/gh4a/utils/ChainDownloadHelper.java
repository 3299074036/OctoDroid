package com.gh4a.utils;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.gh4a.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 多链路下载（功能2）：按 [自选镜像 → 默认镜像 → 直连] 顺序尝试，
 * 一条失败自动换下一条。自更新 APK 与 Release 附件/源码包共用这一套，
 * 不再各写一份 BroadcastReceiver 重试。
 */
public class ChainDownloadHelper {
    /** 下载完成回调：success=false 表示全部链路都失败，file 为 null。 */
    public interface Callback {
        void onDownloadComplete(Context context, boolean success, File file);
    }

    /** 每条链路拼装 Request 时的定制点（标题、mime、请求头等），可为 null。 */
    public interface RequestCustomizer {
        void customize(DownloadManager.Request request);
    }

    /**
     * 从 url 出发走三级链路下载。fileName 会过 sanitize；每条链路的入队
     * 都会写下载记录（与 DownloadUtils 单链路行为一致）。
     */
    public static void enqueueChain(Context context, String url, String fileName,
            String description, RequestCustomizer customizer, Callback callback) {
        final Context appContext = context.getApplicationContext();
        enqueueNext(appContext,
                MirrorHelper.buildFallbackChain(context, url, false),
                fileName, description, customizer, callback);
    }

    private static void enqueueNext(final Context context, final List<String> urls,
            final String fileName, final String description,
            final RequestCustomizer customizer, final Callback callback) {
        final String url = urls.get(0);
        final List<String> rest = new ArrayList<>(urls.subList(1, urls.size()));
        final DownloadManager dm =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        // 文件名来自网络：先 sanitize，防止路径穿越写到 Downloads 之外 (L-2)
        final String safeName = FileUtils.sanitizeFileName(fileName);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false);
        if (customizer != null) {
            customizer.customize(request);
        }
        final long downloadId = dm.enqueue(request);
        // 下载记录里的 URL 剥离 query：签名 URL 的 query 可能含 token，
        // 不能让它进备份/下载记录 (L-17)
        String recordUrl = Uri.parse(url).buildUpon().clearQuery().build().toString();
        DownloadRecordManager.record(context, downloadId, safeName, recordUrl, description);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) {
                    return;
                }
                ctx.unregisterReceiver(this);
                resolveResult(ctx, dm, downloadId, rest,
                        fileName, description, customizer, callback);
            }
        };
        // 用 application context 注册，避免 Activity 销毁后 receiver 泄漏；
        // onReceive 里用传入的 context 反注册，两边一致
        // targetSdk 34+ 必须显式声明 receiver 是否 exported，否则抛 SecurityException
        ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private static void resolveResult(Context context, DownloadManager dm, long downloadId,
            List<String> rest, String fileName, String description,
            RequestCustomizer customizer, Callback callback) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        File file = null;
        try (Cursor cursor = dm.query(query)) {
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    String localUri = cursor.getString(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                    if (localUri != null) {
                        File f = new File(Uri.parse(localUri).getPath());
                        if (f.exists()) {
                            file = f;
                        }
                    }
                }
            }
        }
        if (file != null) {
            callback.onDownloadComplete(context, true, file);
        } else if (!rest.isEmpty()) {
            Toast.makeText(context, R.string.download_retry_alt, Toast.LENGTH_SHORT).show();
            enqueueNext(context, rest, fileName, description, customizer, callback);
        } else {
            callback.onDownloadComplete(context, false, null);
        }
    }
}
