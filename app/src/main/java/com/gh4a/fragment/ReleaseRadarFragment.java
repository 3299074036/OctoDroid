package com.gh4a.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.ReleaseInfoActivity;
import com.gh4a.adapter.ReleaseRadarAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.RxUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

/**
 * "Release radar": the latest releases of the user's starred repositories,
 * newest first.
 *
 * Loading strategy (4.6.30+):
 * - One GitHub GraphQL request fetches the 30 most recently starred repos
 *   with their latest release each (was: 1 + 30 REST calls).
 * - The last successful result is cached per account; the page renders the
 *   cache instantly and refreshes in the background.
 *
 * Note: loading deliberately bypasses the legacy Loader framework used by
 * ListDataBaseFragment (see onCreateDataSingle): the Loader's Observable
 * path swallows onComplete and can stash a result without ever delivering
 * it, which left this page on an endless progress bar. The list UI
 * (adapter, empty view, error view) is still the base class machinery.
 */
public class ReleaseRadarFragment extends ListDataBaseFragment<ReleaseRadarFragment.RadarItem> {
    /** Number of starred repos (most recently starred first) to check. */
    private static final int MAX_REPOS_TO_CHECK = 30;
    private static final int MAX_ITEMS = 30;

    public static class RadarItem {
        public final String owner;
        public final String repo;
        public final long releaseId;
        public final String tagName;
        public final String releaseName;
        public final long publishedAt; // epoch millis, 0 if unknown
        public final long createdAt;   // epoch millis, 0 if unknown

        RadarItem(String owner, String repo, long releaseId,
                String tagName, String releaseName,
                long publishedAt, long createdAt) {
            this.owner = owner;
            this.repo = repo;
            this.releaseId = releaseId;
            this.tagName = tagName;
            this.releaseName = releaseName;
            this.publishedAt = publishedAt;
            this.createdAt = createdAt;
        }

        long sortTime() {
            return publishedAt != 0 ? publishedAt : createdAt;
        }
    }

    public static ReleaseRadarFragment newInstance(String login) {
        ReleaseRadarFragment f = new ReleaseRadarFragment();
        Bundle args = new Bundle();
        args.putString("user", login);
        f.setArguments(args);
        return f;
    }

    private String mLogin;
    private ReleaseRadarAdapter mAdapter;
    private Disposable mDirectLoad;
    private List<RadarItem> mLatestItems; // latest items (for rotation)
    private boolean mLoadComplete;
    private boolean mShowingCache; // currently displaying cached data

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLogin = getArguments().getString("user");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // super.onViewCreated() hooks the no-op Single below into the Loader;
        // the actual load runs directly so its result always reaches the UI.
        if (mLoadComplete && mLatestItems != null) {
            renderItems(mLatestItems);
            setContentShown(true);
            updateEmptyState();
        } else {
            // Cache first: show the last result instantly (if any), then
            // refresh in the background. No more staring at a spinner.
            List<RadarItem> cached = getContext() != null
                    ? RadarCache.load(getContext(), mLogin) : null;
            if (cached != null && !cached.isEmpty()) {
                mShowingCache = true;
                renderItems(cached);
                setContentShown(true);
                updateEmptyState();
                setStage("显示缓存数据，后台刷新中…");
            } else {
                setContentShown(true);
                updateEmptyState();
                setStage("正在加载…");
            }
            startDirectLoad(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (mDirectLoad != null) {
            mDirectLoad.dispose();
            mDirectLoad = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        // Bypass the Loader-based loadData(); reload directly instead.
        startDirectLoad(true);
    }

    @Override
    protected RootAdapter<RadarItem, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mAdapter = new ReleaseRadarAdapter(getActivity());
        mAdapter.setOnItemClickListener(this::onItemClick);
        return mAdapter;
    }

    @Override
    protected Single<List<RadarItem>> onCreateDataSingle(boolean bypassCache) {
        // Kept idle: the real load is done directly in startDirectLoad().
        return Single.never();
    }

    private void startDirectLoad(boolean bypassCache) {
        if (mDirectLoad != null) {
            mDirectLoad.dispose();
        }
        mLoadComplete = false;
        // Don't clear the adapter: if we're showing cache, keep it visible
        // while the background refresh runs.
        if (!mShowingCache) {
            mLatestItems = null;
            if (mAdapter != null) {
                mAdapter.clear();
            }
            setContentShown(true);
            updateEmptyState();
            setStage("正在加载…");
        }
        mDirectLoad = RxUtils.doInBackground(RadarGraphQL.fetch(mLogin))
                // A single request now; 30s is plenty, then surface the error.
                .timeout(30, TimeUnit.SECONDS)
                .subscribe(this::onFreshData, this::renderError);
    }

    /** Write a visible stage marker onto the empty view (main thread). */
    private void setStage(String stage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isAdded()) {
                return;
            }
            View root = getView();
            if (root != null) {
                TextView emptyView = root.findViewById(android.R.id.empty);
                if (emptyView != null) {
                    emptyView.setText(stage);
                }
            }
        });
    }

    private void renderItems(List<RadarItem> items) {
        if (!isAdded() || mAdapter == null) {
            return;
        }
        mLatestItems = items;
        mAdapter.clear();
        onAddData(mAdapter, items);
        setContentShown(true);
        updateEmptyState();
    }

    /** Fresh GraphQL data arrived: replace cache view, persist. */
    private void onFreshData(List<RadarItem> items) {
        if (!isAdded()) {
            return;
        }
        mLoadComplete = true;
        mShowingCache = false;
        if (getContext() != null) {
            RadarCache.save(getContext(), mLogin, items);
        }
        renderItems(items);
        if (items.isEmpty()) {
            showDiagnostic();
        }
    }

    private void onLoadComplete() {
        // Unused now (Single path); kept for clarity.
    }

    private void showDiagnostic() {
        if (mAdapter.getItemCount() > 0 || getContext() == null) {
            return;
        }
        String msg = "Star 的仓库最近没有新版本";
        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        setStage(msg);
    }

    private void renderError(Throwable error) {
        if (!isAdded()) {
            return;
        }
        mLoadComplete = true;
        if (mAdapter != null && mAdapter.getItemCount() > 0) {
            // Cache is already visible; the refresh just failed quietly.
            Log.d(Gh4Application.LOG_TAG, "Radar refresh failed", error);
            mShowingCache = false;
            if (getContext() != null) {
                Toast.makeText(getContext(), "刷新失败，显示的是缓存数据",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            setContentShown(true);
            updateEmptyState();
            String msg = error.getClass().getSimpleName()
                    + (error.getMessage() != null ? ": " + error.getMessage() : "");
            if (getContext() != null) {
                Toast.makeText(getContext(), "加载失败 " + msg, Toast.LENGTH_LONG).show();
            }
            // Stamp the error on the empty view too: a Toast can be missed,
            // this stays until the next load.
            setStage("加载失败 " + msg);
            handleLoadFailure(error);
        }
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_release_radar_items;
    }

    private void onItemClick(RadarItem item) {
        startActivity(ReleaseInfoActivity.makeIntent(getActivity(),
                item.owner, item.repo, item.releaseId));
    }
}
