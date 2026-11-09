package com.gh4a.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.utils.Background;
import com.gh4a.utils.DiagTrace;
import com.gh4a.utils.ThemeTrace;
import com.gh4a.utils.UiUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 0.1.40：诊断日志页（交互稿 diagnostic-log-ui.html 落地）。
 * 入口在“我 → 关于与更新 → 检查更新”下方。实时刷新（1 秒轮询文件指纹，
 * 暂停后停止）、筛选（全部 / 链路 / 崩溃）、点条目展开崩溃全文、清空
 * （二次确认）、导出（系统分享 / 保存到下载文件夹 / 复制全部文本）。
 * 只展示写盘层已记的内容（事件与数量/类名），不记账号、凭据、内容。
 */
public class DiagnosticLogActivity extends BaseActivity {
    private static final int FILTER_ALL = 0;
    private static final int FILTER_TRACE = 1;
    private static final int FILTER_CRASH = 2;

    // 178：过滤条件与实时开关须随旋转恢复。
    private static final String STATE_FILTER = "filter";
    private static final String STATE_LIVE = "live";

    private static class Entry {
        String time;
        int kind; // 0 链路，1 崩溃
        String detail;
        String full;
        boolean expanded;
    }

    private RecyclerView mList;
    private TextView mEmpty;
    private TextView mLiveText;
    private View mLiveDot;
    private Button mLiveButton;
    private TextView mFilterAll;
    private TextView mFilterTrace;
    private TextView mFilterCrash;

    private final List<Entry> mAll = new ArrayList<>();
    private final List<Entry> mShown = new ArrayList<>();
    private final DiagAdapter mAdapter = new DiagAdapter();
    private int mFilter = FILTER_ALL;
    private boolean mLive = true;
    private String mFingerprint = "";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mPoller = new Runnable() {
        @Override
        public void run() {
            if (mLive) {
                reloadIfChanged();
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.diagnostic_log);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostic_log);

        mList = findViewById(R.id.diag_list);
        mList.setLayoutManager(new LinearLayoutManager(this));
        mList.setAdapter(mAdapter);
        mEmpty = findViewById(R.id.diag_empty);
        mLiveText = findViewById(R.id.diag_live_text);
        mLiveDot = findViewById(R.id.diag_live_dot);
        mLiveButton = findViewById(R.id.diag_live_button);
        mFilterAll = findViewById(R.id.diag_filter_all);
        mFilterTrace = findViewById(R.id.diag_filter_trace);
        mFilterCrash = findViewById(R.id.diag_filter_crash);

        mFilterAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        mFilterTrace.setOnClickListener(v -> setFilter(FILTER_TRACE));
        mFilterCrash.setOnClickListener(v -> setFilter(FILTER_CRASH));
        mLiveButton.setOnClickListener(v -> setLive(!mLive));
        findViewById(R.id.diag_clear_button).setOnClickListener(v -> confirmClear());
        findViewById(R.id.diag_export_button).setOnClickListener(v -> showExportOptions());

        // 178：旋转后恢复过滤条件与实时开关（字段 + 芯片/实时 UI 一并恢复）。
        if (savedInstanceState != null) {
            mFilter = savedInstanceState.getInt(STATE_FILTER, FILTER_ALL);
            mLive = savedInstanceState.getBoolean(STATE_LIVE, true);
        }
        reload();
        setFilter(mFilter);
        setLive(mLive);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, mFilter);
        outState.putBoolean(STATE_LIVE, mLive);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mPoller);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mPoller);
    }

    private void setFilter(int filter) {
        mFilter = filter;
        mFilterAll.setBackgroundResource(filter == FILTER_ALL
                ? R.drawable.diag_chip_selected : R.drawable.diag_chip_normal);
        mFilterTrace.setBackgroundResource(filter == FILTER_TRACE
                ? R.drawable.diag_chip_selected : R.drawable.diag_chip_normal);
        mFilterCrash.setBackgroundResource(filter == FILTER_CRASH
                ? R.drawable.diag_chip_selected : R.drawable.diag_chip_normal);
        mFilterAll.setTextColor(filter == FILTER_ALL
                ? 0xFFFFFFFF : getSecondaryTextColor());
        mFilterTrace.setTextColor(filter == FILTER_TRACE
                ? 0xFFFFFFFF : getSecondaryTextColor());
        mFilterCrash.setTextColor(filter == FILTER_CRASH
                ? 0xFFFFFFFF : getSecondaryTextColor());
        applyFilter();
    }

    private int getSecondaryTextColor() {
        return UiUtils.resolveColor(this, android.R.attr.textColorSecondary);
    }

    private void setLive(boolean live) {
        mLive = live;
        mLiveText.setText(live ? R.string.diagnostic_log_live : R.string.diagnostic_log_paused);
        mLiveButton.setText(live ? R.string.diagnostic_log_pause : R.string.diagnostic_log_resume);
        mLiveDot.setBackgroundResource(live ? R.drawable.diag_live_dot : R.drawable.diag_chip_normal);
        if (live) {
            reloadIfChanged();
        }
    }

    private String fingerprint() {
        StringBuilder sb = new StringBuilder();
        appendFileFingerprint(sb, DiagTrace.traceFile(this));
        appendFileFingerprint(sb, ThemeTrace.file(this));
        for (File f : DiagTrace.crashFiles(this)) {
            appendFileFingerprint(sb, f);
        }
        return sb.toString();
    }

    private void appendFileFingerprint(StringBuilder sb, File f) {
        sb.append(f.getName()).append(':');
        sb.append(f.exists() ? f.length() : -1).append(':');
        sb.append(f.exists() ? f.lastModified() : -1).append(';');
    }

    private void reloadIfChanged() {
        String fp = fingerprint();
        if (!fp.equals(mFingerprint)) {
            reload();
        }
    }

    // P11：主线程 1 秒轮询读文件——loadEntries() 读 trace（上限 512KB）
    // 与崩溃文件（各 64KB）并逐行拆分，放主线程会掉帧/ANR（anr-audit
    // skill Path A：执行线程是主线程 Handler，调用是整文件读 + 解析）。
    // 指纹（文件 stat，便宜）仍留主线程轮询；读文件 + 解析走 Background，
    // 结果回主线程应用；进行中不再叠加，Activity 已销毁丢弃结果。
    private boolean mReloadInFlight;

    private void reload() {
        mFingerprint = fingerprint();
        // atTop 进 lambda 必须 effectively final，先一次算好不再改。
        final boolean atTop;
        if (mList.getLayoutManager() instanceof LinearLayoutManager) {
            atTop = ((LinearLayoutManager) mList.getLayoutManager())
                    .findFirstVisibleItemPosition() <= 0;
        } else {
            atTop = true;
        }
        if (mReloadInFlight) {
            return;
        }
        mReloadInFlight = true;
        final Context appContext = getApplicationContext();
        Background.run("diag-reload", () -> {
            List<Entry> entries = loadEntries(appContext);
            mHandler.post(() -> {
                mReloadInFlight = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                mAll.clear();
                mAll.addAll(entries);
                Collections.sort(mAll, (a, b) -> b.time.compareTo(a.time));
                applyFilter();
                if (atTop && !mShown.isEmpty()) {
                    mList.scrollToPosition(0);
                }
            });
        });
    }

    private void applyFilter() {
        mShown.clear();
        for (Entry e : mAll) {
            if (mFilter == FILTER_ALL || (mFilter == FILTER_TRACE && e.kind == 0)
                    || (mFilter == FILTER_CRASH && e.kind == 1)) {
                mShown.add(e);
            }
        }
        mAdapter.notifyDataSetChanged();
        mEmpty.setVisibility(mShown.isEmpty() ? View.VISIBLE : View.GONE);
        mList.setVisibility(mShown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<Entry> loadEntries(Context context) {
        List<Entry> out = new ArrayList<>();
        loadTraceFile(out, DiagTrace.traceFile(context));
        loadTraceFile(out, ThemeTrace.file(context));
        for (File f : DiagTrace.crashFiles(context)) {
            out.add(loadCrashEntry(f));
        }
        return out;
    }

    private void loadTraceFile(List<Entry> out, File f) {
        if (f == null || !f.exists()) {
            return;
        }
        String text = DiagTrace.readText(f, 512 * 1024);
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            Entry e = new Entry();
            e.kind = 0;
            int firstSpace = line.indexOf(' ');
            int secondSpace = firstSpace >= 0 ? line.indexOf(' ', firstSpace + 1) : -1;
            if (firstSpace > 0 && secondSpace > firstSpace) {
                e.time = line.substring(0, firstSpace);
                e.detail = line.substring(secondSpace + 1);
            } else {
                e.time = "";
                e.detail = line;
            }
            e.full = e.detail;
            out.add(e);
        }
    }

    private Entry loadCrashEntry(File f) {
        Entry e = new Entry();
        e.kind = 1;
        e.time = crashTimeFromName(f.getName());
        String text = DiagTrace.readText(f, 64 * 1024);
        e.full = text != null ? text : "";
        e.detail = f.getName();
        if (text != null) {
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("[crash breadcrumb]")) {
                    continue;
                }
                if (t.contains("Exception") || t.contains("Error")) {
                    e.detail = t;
                    break;
                }
                if (e.detail.equals(f.getName())) {
                    e.detail = t;
                }
            }
        }
        return e;
    }

    private String crashTimeFromName(String name) {
        // crash-yyyyMMdd-HHmmss-SSS.txt → HH:mm:ss
        try {
            if (name.startsWith("crash-") && name.length() >= 22) {
                String stamp = name.substring(6, 21);
                return stamp.substring(9, 11) + ":" + stamp.substring(11, 13)
                        + ":" + stamp.substring(13, 15);
            }
        } catch (Exception ignored) {
        }
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.diagnostic_log_clear_confirm)
                .setPositiveButton(R.string.diagnostic_log_clear, (d, w) -> {
                    deleteQuietly(DiagTrace.traceFile(this));
                    deleteQuietly(ThemeTrace.file(this));
                    for (File f : DiagTrace.crashFiles(this)) {
                        deleteQuietly(f);
                    }
                    reload();
                    Toast.makeText(this, R.string.diagnostic_log_cleared,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteQuietly(File f) {
        try {
            if (f != null && f.exists()) {
                f.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private void showExportOptions() {
        CharSequence[] items = new CharSequence[] {
                getString(R.string.diagnostic_log_export_share),
                getString(R.string.diagnostic_log_export_download),
                getString(R.string.diagnostic_log_export_copy),
        };
        new AlertDialog.Builder(this)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        makeExportFile(this::shareExport);
                    } else if (which == 1) {
                        makeExportFile(this::saveToDownloads);
                    } else {
                        makeExportFile(this::copyExport);
                    }
                })
                .show();
    }

    /**
     * P14：导出在后台线程做（读约 1MB 再写出），完成后回主线程回调；
     * 导出期间禁用导出按钮防重复点击；Activity 已销毁时丢弃结果。
     */
    private void makeExportFile(java.util.function.Consumer<File> onReady) {
        View exportButton = findViewById(R.id.diag_export_button);
        exportButton.setEnabled(false);
        final Context appContext = getApplicationContext();
        Background.run("diag-export", () -> {
            File f = DiagTrace.exportFile(appContext);
            mHandler.post(() -> {
                exportButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                onReady.accept(f);
            });
        });
    }

    private void shareExport(File f) {
        try {
            if (f == null || !f.exists()) {
                Toast.makeText(this, R.string.diagnostic_log_export_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", f);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostic_log));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.diagnostic_log_export)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.diagnostic_log_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void saveToDownloads(File f) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(this, R.string.diagnostic_log_download_unsupported,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (f == null || !f.exists()) {
                Toast.makeText(this, R.string.diagnostic_log_export_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, "diag-export.txt");
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(this, R.string.diagnostic_log_export_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try (FileInputStream in = new FileInputStream(f);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    Toast.makeText(this, R.string.diagnostic_log_export_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                }
            }
            Toast.makeText(this, R.string.diagnostic_log_saved,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.diagnostic_log_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void copyExport(File f) {
        try {
            if (f == null || !f.exists()) {
                Toast.makeText(this, R.string.diagnostic_log_export_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String text = DiagTrace.readText(f, 512 * 1024);
            ClipboardManager cm = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(this, R.string.diagnostic_log_export_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            cm.setPrimaryClip(ClipData.newPlainText(
                    getString(R.string.diagnostic_log), text != null ? text : ""));
            Toast.makeText(this, R.string.diagnostic_log_copied,
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.diagnostic_log_export_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private class DiagAdapter extends RecyclerView.Adapter<DiagAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            View bar;
            TextView time;
            TextView tag;
            TextView detail;
            TextView full;

            Holder(View v) {
                super(v);
                bar = v.findViewById(R.id.diag_row_bar);
                time = v.findViewById(R.id.diag_row_time);
                tag = v.findViewById(R.id.diag_row_tag);
                detail = v.findViewById(R.id.diag_row_detail);
                full = v.findViewById(R.id.diag_row_full);
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_diagnostic_log, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            Entry e = mShown.get(position);
            h.time.setText(e.time);
            boolean crash = e.kind == 1;
            h.tag.setText(crash ? R.string.diagnostic_log_filter_crash
                    : R.string.diagnostic_log_filter_trace);
            int color = crash
                    ? 0xFFC62828
                    : UiUtils.resolveColor(h.itemView.getContext(),
                            androidx.appcompat.R.attr.colorPrimary);
            h.tag.setTextColor(color);
            h.bar.setBackgroundColor(color);
            h.detail.setText(e.detail);
            h.detail.setMaxLines(e.expanded ? Integer.MAX_VALUE : 2);
            if (crash && e.expanded) {
                h.full.setText(e.full);
                h.full.setVisibility(View.VISIBLE);
            } else {
                h.full.setVisibility(View.GONE);
            }
            h.itemView.setOnClickListener(v -> {
                e.expanded = !e.expanded;
                // 177：实时模式下 mShown 随时新增条目，holder 可能在点击前已被回收，
                // getAdapterPosition() 返回 NO_POSITION(-1)，notifyItemChanged(-1) 即抛错。
                // 注意：onBindViewHolder 的形参已叫 position，这里用 pos 避免重定义。
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    notifyItemChanged(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mShown.size();
        }
    }
}
