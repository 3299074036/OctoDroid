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
    /** 下载完成回调：success=false 表示全部链路都失败，file 为 null。
     * downloadId 用于把完成后的文件信息（大小／哈希）补进下载记录。 */
    public interface Callback {
        void onDownloadComplete(Context context, boolean success, File file,
                long downloadId);
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

    /** 92：DM 永不广播时（如任务被外部删除）receiver 会永久挂在 application
     * context 上。登记所有待处理 receiver，下次入队时把对应下载已不存在的反注册掉。 */
    private static final List<PendingReceiver> sPendingReceivers = new ArrayList<>();

    private static final class PendingReceiver {
        final BroadcastReceiver receiver;
        final long downloadId;

        PendingReceiver(BroadcastReceiver receiver, long downloadId) {
            this.receiver = receiver;
            this.downloadId = downloadId;
        }
    }

    /** 257/261/262：入队前即失败（空链、非法文件名、存储瞬间不可用）
     * 时与 dm == null 分支同款处理：toast + 失败回调。 */
    private static void failEnqueue(Context context, Callback callback) {
        Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show();
        callback.onDownloadComplete(context, false, null, -1);
    }

    private static void enqueueNext(final Context context, final List<String> urls,
            final String fileName, final String description,
            final RequestCustomizer customizer, final Callback callback) {
        // 262：buildFallbackChain 经 addDistinctUrl 组装，url 为 null 或 ""
        // 时返回空链；空链上 urls.get(0) 即越界。空链按入队失败处理。
        if (urls == null || urls.isEmpty()) {
            failEnqueue(context, callback);
            return;
        }
        final String url = urls.get(0);
        final List<String> rest = new ArrayList<>(urls.subList(1, urls.size()));
        final DownloadManager dm =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        // 90：外置存储未挂载时 getExternalFilesDir() 返回 null，
        // setDestinationInExternalFilesDir 直接抛 IllegalStateException（主线程崩溃）。
        if (dm == null
                || context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) == null) {
            Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show();
            callback.onDownloadComplete(context, false, null, -1);
            return;
        }
        // 92：先清理上轮残留的 receiver（对应下载已不在 DM 里）。
        pruneStaleReceivers(context, dm);
        // 文件名来自网络：先 sanitize，防止路径穿越写到 Downloads 之外 (L-2)
        final String safeName;
        try {
            // 257：文件名来自服务端且未经预清洗（release 附件名仓库方
            // 自定、可含 ".."、可空；gist/仓库文件名用户自定），
            // sanitizeFileName 遇 null 或 ".." 抛 IllegalArgumentException，
            // 原先无 try/catch，主线程直接崩溃（下载未开始、无 toast）。
            safeName = FileUtils.sanitizeFileName(fileName);
        } catch (IllegalArgumentException e) {
            failEnqueue(context, callback);
            return;
        }
        final long downloadId;
        try {
            // 261：:75 的 getExternalFilesDir() == null 检查（90 号）与
            // 这里的 setDestinationInExternalFilesDir / dm.enqueue 之间
            // 存在检查-执行窗口；存储在入队瞬间卸载即抛
            // IllegalStateException / IllegalArgumentException（主线程崩溃）。
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    // 0.2.2（S-M5）：下载落到应用私有目录（Android/data/<包>/files/Download），
                    // 不再进公共 Downloads/（其他应用可读）；打开时走 FileProvider。
                    // 私有目录不需要 WRITE_EXTERNAL_STORAGE，任何 API 级别都不用申请。
                    .setDestinationInExternalFilesDir(context,
                            Environment.DIRECTORY_DOWNLOADS, safeName)
                    // 57：系统下载完成通知只在校验通过后才由应用自己发（见
                    // DownloadUtils.notifyDownloadComplete）——这里只显示下载中
                    // 进度，不在完成瞬间弹系统通知，否则用户一点就装上还没验过
                    // 签名的 APK（校验是事后异步的，见 DownloadUtils）。
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverRoaming(false);
            if (customizer != null) {
                customizer.customize(request);
            }
            downloadId = dm.enqueue(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            failEnqueue(context, callback);
            return;
        }
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
                removePendingReceiver(downloadId);
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
        synchronized (sPendingReceivers) {
            sPendingReceivers.add(new PendingReceiver(receiver, downloadId));
        }
    }

    /** 92：反注册对应下载已不在 DownloadManager 里的旧 receiver。 */
    private static void pruneStaleReceivers(Context context, DownloadManager dm) {
        synchronized (sPendingReceivers) {
            for (int i = sPendingReceivers.size() - 1; i >= 0; i--) {
                PendingReceiver pending = sPendingReceivers.get(i);
                boolean gone = false;
                try (Cursor cursor = dm.query(new DownloadManager.Query()
                        .setFilterById(pending.downloadId))) {
                    gone = !cursor.moveToFirst();
                } catch (Exception ignored) {
                    // 查不到状态时不动它，等下次入队再试
                }
                if (gone) {
                    try {
                        context.unregisterReceiver(pending.receiver);
                    } catch (IllegalArgumentException ignored) {
                        // 已反注册过
                    }
                    sPendingReceivers.remove(i);
                }
            }
        }
    }

    private static void removePendingReceiver(long downloadId) {
        synchronized (sPendingReceivers) {
            for (int i = sPendingReceivers.size() - 1; i >= 0; i--) {
                if (sPendingReceivers.get(i).downloadId == downloadId) {
                    sPendingReceivers.remove(i);
                }
            }
        }
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
            callback.onDownloadComplete(context, true, file, downloadId);
        } else if (!rest.isEmpty()) {
            // 91：失败链路的记录是死记录（重试成功后列表里多一条 FAILED），重试前删掉；
            // 只有最后一条链路失败时才保留 FAILED 记录。
            DownloadRecordManager.remove(context, downloadId);
            Toast.makeText(context, R.string.download_retry_alt, Toast.LENGTH_SHORT).show();
            enqueueNext(context, rest, fileName, description, customizer, callback);
        } else {
            callback.onDownloadComplete(context, false, null, downloadId);
        }
    }
}
