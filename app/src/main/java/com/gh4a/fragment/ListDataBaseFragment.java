package com.gh4a.fragment;

import java.util.List;
import android.os.Bundle;
import android.util.Log;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import com.gh4a.Gh4Application;
import com.gh4a.adapter.RootAdapter;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;

public abstract class ListDataBaseFragment<T> extends LoadingListFragmentBase {
    private RootAdapter<T, ? extends RecyclerView.ViewHolder> mAdapter;
    private Disposable mSubscription;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setContentShown(false);
        loadData(false);
    }

    @Override
    public void onRefresh() {
        setContentShown(false);
        if (mSubscription != null) {
            mSubscription.dispose();
        }
        loadData(true);
        if (mAdapter != null) {
            mAdapter.clear();
        }
    }

    @Override
    protected boolean hasDividers() {
        return mAdapter.hasDividers() && !mAdapter.isCardStyle();
    }

    protected void onAddData(RootAdapter<T, ?> adapter, List<T> data) {
        adapter.addAll(data);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onRecyclerViewInflated(RecyclerView view, LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);
        mAdapter = onCreateAdapter();
        view.setAdapter(mAdapter);
        updateEmptyState();
    }

    @Override
    protected boolean hasCards() {
        return mAdapter.isCardStyle();
    }

    private void loadData(boolean force) {
        List<T> initialData = force ? null : onGetInitialData();
        if (initialData != null) {
            handleNewData(initialData);
        } else {
            Observable<List<T>> observable = onCreateDataObservable(force);
            if (observable != null) {
                // Progressive loading: render each page as soon as it arrives
                // instead of waiting for all pages.
                mSubscription = observable
                        .compose(makeLoaderObservable(0, force))
                        .subscribe(this::handleNewPage, this::handlePagedLoadFailure,
                                this::handleAllPagesLoaded);
            } else {
                mSubscription = onCreateDataSingle(force)
                        .compose(makeLoaderSingle(0, force))
                        .subscribe(this::handleNewData, this::handleLoadFailure);
            }
        }
    }

    private void handleNewData(List<T> result) {
        mAdapter.clear();
        onAddData(mAdapter, result);
        setContentShown(true);
        updateEmptyState();
    }

    private void handleNewPage(List<T> cumulativeItems) {
        // Each emission carries the full list of items loaded so far (see
        // scan() in subclasses), so rotation replay stays correct even though
        // the underlying loader only retains the latest emission.
        mAdapter.clear();
        onAddData(mAdapter, cumulativeItems);
        setContentShown(true);
    }

    private void handleAllPagesLoaded() {
        setContentShown(true);
        updateEmptyState();
    }

    private void handlePagedLoadFailure(Throwable error) {
        if (mAdapter != null && mAdapter.getCount() > 0) {
            // Earlier pages are already visible; a later page failing
            // shouldn't wipe out the displayed list.
            Log.d(Gh4Application.LOG_TAG, "Loading further pages failed", error);
        } else {
            handleLoadFailure(error);
        }
    }

    protected Single<List<T>> onCreateDataSingle(boolean bypassCache) {
        return null;
    }

    /**
     * Optionally supplies the data as one emission per page for progressive
     * rendering. When non-null, takes precedence over
     * {@link #onCreateDataSingle(boolean)}.
     */
    protected Observable<List<T>> onCreateDataObservable(boolean bypassCache) {
        return null;
    }
    protected List<T> onGetInitialData() {
        return null;
    }
    protected abstract RootAdapter<T, ? extends RecyclerView.ViewHolder> onCreateAdapter();
}
