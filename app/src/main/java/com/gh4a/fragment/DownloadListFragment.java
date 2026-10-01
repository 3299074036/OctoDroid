package com.gh4a.fragment;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.R;
import com.gh4a.adapter.DownloadListAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.DownloadRecordManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;

/**
 * Shows files downloaded through the app (release assets, source zips, …),
 * newest first, with live status from DownloadManager.
 * Tap a completed file to open it; long-press to delete the record.
 */
public class DownloadListFragment extends ListDataBaseFragment<DownloadListFragment.Item>
        implements RootAdapter.OnItemClickListener<DownloadListFragment.Item>,
        RootAdapter.OnItemLongClickListener<DownloadListFragment.Item> {

    public static class Item {
        public final DownloadRecordManager.Record record;
        public final int status;

        Item(DownloadRecordManager.Record record, int status) {
            this.record = record;
            this.status = status;
        }
    }

    public static DownloadListFragment newInstance() {
        return new DownloadListFragment();
    }

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.download_list_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.clear_downloads) {
            DownloadRecordManager.clear(getActivity());
            onRefresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected RootAdapter<Item, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        DownloadListAdapter adapter = new DownloadListAdapter(getActivity());
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        return adapter;
    }

    @Override
    protected Single<List<Item>> onCreateDataSingle(boolean bypassCache) {
        return Single.fromCallable(() -> {
            List<Item> items = new ArrayList<>();
            for (DownloadRecordManager.Record record
                    : DownloadRecordManager.getRecords(getActivity())) {
                int status = DownloadListAdapter.queryStatus(getActivity(), record.downloadId);
                items.add(new Item(record, status));
            }
            return items;
        });
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_downloads;
    }

    @Override
    public void onItemClick(Item item) {
        if (item.status != DownloadManager.STATUS_SUCCESSFUL) {
            return;
        }
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), item.record.fileName);
        if (!file.exists()) {
            Toast.makeText(getActivity(), R.string.download_open_failed,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(getActivity(),
                getActivity().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, getMimeType(item.record.fileName))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(getActivity(), R.string.download_open_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onItemLongClick(Item item) {
        DownloadRecordManager.remove(getActivity(), item.record.downloadId);
        Toast.makeText(getActivity(), R.string.download_record_deleted,
                Toast.LENGTH_SHORT).show();
        onRefresh();
        return true;
    }

    private static String getMimeType(String fileName) {
        String lower = fileName.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return "application/gzip";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "*/*";
    }
}
