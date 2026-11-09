/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.fragment;

import android.os.Bundle;

import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.RxUtils;
import com.meisolsson.githubsdk.model.Page;
import com.philosophicalhacker.lib.RxLoader;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.BehaviorSubject;
import retrofit2.Response;

public abstract class PagedDataBaseFragment<T> extends LoadingListFragmentBase implements
        RootAdapter.OnItemClickListener<T>, RootAdapter.OnScrolledToFooterListener {
    private RootAdapter<T, ? extends RecyclerView.ViewHolder> mAdapter;
    private RxLoader mRxLoader;
    private BehaviorSubject<Integer> mPageSubject;
    private Integer mNextPage;
    private View mLoadingView;
    private Disposable mSubscription;
    // Page that failed while data was already on screen (BUG-1 footer retry).
    private Integer mFailedPage;
    // 0.1.20 F-19: 视图创建前收到的刷新请求，视图就绪后补执行而非丢弃。
    private boolean mRefreshPending;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRxLoader = new RxLoader(getActivity(), LoaderManager.getInstance(this));
        resetSubject();
        if (mRefreshPending) {
            mRefreshPending = false;
            setContentShown(false);
            load(true);
        } else if (shouldDoInitialLoad(savedInstanceState)) {
            setContentShown(false);
            load(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (mSubscription != null) {
            mSubscription.dispose();
            mSubscription = null;
        }
        mFailedPage = null;
        mAdapter = null;
        mLoadingView = null;
        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        mFailedPage = null;
        if (mAdapter != null) {
            mAdapter.clear();
        }
        if (mSubscription != null) {
            mSubscription.dispose();
            mSubscription = null;
        }
        if (mRxLoader != null) {
            resetSubject();
            setContentShown(false);
            load(true);
        } else {
            // 0.1.20 F-19: loader 尚未创建（视图未就绪），记下请求待 onViewCreated 补执行。
            mRefreshPending = true;
        }
    }

    /** 0.1.20 F-23(a) 配套：释放当前页加载订阅（搜索页类型切换空查询路径用）。 */
    protected void disposeCurrentSubscription() {
        if (mSubscription != null) {
            mSubscription.dispose();
            mSubscription = null;
        }
    }

    @Override
    protected void onRecyclerViewInflated(RecyclerView view, LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);
        mAdapter = onCreateAdapter();

        mLoadingView = inflater.inflate(R.layout.list_loading_view, view, false);
        // 0.1.20 F-10: 页脚点按重试失败页——此前重试只在页脚重新绑定时触发，
        // 停在底部的用户无处可点。无失败页时点按不做任何事。
        mLoadingView.setOnClickListener(v -> retryFailedPage());
        mAdapter.setFooterView(mLoadingView, this);
        mAdapter.setOnItemClickListener(this);
        view.setAdapter(mAdapter);
        updateEmptyState();
    }

    @Override
    protected boolean hasDividers() {
        return mAdapter.hasDividers() && !mAdapter.isCardStyle();
    }

    @Override
    protected boolean hasCards() {
        return mAdapter.isCardStyle();
    }

    protected void resetSubject() {
        mNextPage = null;
        mPageSubject = BehaviorSubject.createDefault(1);
        mPageSubject.onNext(1);
    }

    protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
        return true;
    }

    private void load(boolean force) {
        mSubscription = mPageSubject
                .flatMap(page -> loadPage(page, force)
                        .map(response -> {
                            if (response.code() == HttpURLConnection.HTTP_NO_CONTENT) {
                                return Response.success(new ApiHelpers.DummyPage<T>());
                            }
                            return response;
                        })
                        .map(ApiHelpers::throwOnFailure)
                        .compose(RxUtils::doInBackground)
                        .toObservable())
                .scan(Pair.create(new ArrayList<T>(), 0), (pair, page) -> {
                    // 169：page.items() 可空（缺字段的分页响应），addAll(null) 即 NPE；
                    // 缺失按空页处理（204 已由 DummyPage 兜底为空列表）。
                    if (page.items() != null) {
                        pair.first.addAll(page.items());
                    }
                    return Pair.create(pair.first, page.next());
                })
                // filter out initial value
                .filter(pair -> pair.second == null || pair.second != 0)
                .compose(mRxLoader.makeObservableTransformer(0, force))
                .subscribe(result -> {
                    fillData(result.first, result.second);
                    setContentShown(true);
                    updateEmptyState();
                }, this::handlePagedLoadFailure);
    }

    // BUG-1: mirror ListDataBaseFragment.handlePagedLoadFailure. A page-2+
    // failure must not wipe page 1: keep the list, log, and let the footer
    // retry the failed page; only a failure with no data yet shows the
    // full-screen error.
    private void handlePagedLoadFailure(Throwable error) {
        if (mAdapter != null && mAdapter.getCount() > 0) {
            Log.d(Gh4Application.LOG_TAG, "Loading further pages failed", error);
            if (mPageSubject != null) {
                mFailedPage = mPageSubject.getValue();
            }
            if (mLoadingView != null) {
                mLoadingView.setVisibility(View.VISIBLE);
            }
            setContentShown(true);
            updateEmptyState();
        } else {
            handleLoadFailure(error);
        }
    }

    private void fillData(List<T> data, Integer nextPage) {
        if (mAdapter == null || mLoadingView == null) {
            return;
        }
        mNextPage = nextPage;
        mFailedPage = null;
        mLoadingView.setVisibility(nextPage != null ? View.VISIBLE : View.GONE);

        if (mAdapter.getCount() > 0 && !data.isEmpty() && data.iterator().next() == mAdapter.getItem(0)) {
            // there are common items, preserve them in order to keep the scroll position
            ArrayList<T> newData = new ArrayList<>();
            int index = 0, count = mAdapter.getCount();
            for (T item : data) {
                if (index < count && item == mAdapter.getItem(index++)) {
                    // we already know about the item
                    continue;
                }
                newData.add(item);
            }
            onAddData(mAdapter, newData);
        } else {
            mAdapter.clear();
            onAddData(mAdapter, data);
        }
    }

    protected void onAddData(RootAdapter<T, ? extends RecyclerView.ViewHolder> adapter, Collection<T> data) {
        adapter.addAll(data);
    }

    /** 0.1.20 F-10: 页脚点按与滚动到底共用的失败页重试。 */
    private void retryFailedPage() {
        if (mFailedPage != null && mPageSubject != null) {
            int page = mFailedPage;
            mFailedPage = null;
            if (mPageSubject.hasObservers()) {
                mPageSubject.onNext(page);
            } else {
                load(true);
            }
        }
    }

    @Override
    public void onScrolledToFooter() {
        if (mFailedPage != null) {
            retryFailedPage();
            return;
        }
        if (mLoadingView == null) {
            return;
        }
        if (mNextPage != null && mLoadingView.getVisibility() == View.VISIBLE) {
            // Even if our subscription above is active, the page subject might not be subscribed
            // to yet - this is the case if the data comes from the RX loader's cache. In that case,
            // updating the page subject is pointless at this point. Force a load to subscribe to
            // the page subject, which will cause the data to be delivered again, after which
            // onScrolledToFooter will be called again.
            if (mPageSubject.hasObservers()) {
                mPageSubject.onNext(mNextPage);
            } else {
                load(true);
            }
            mNextPage = null;
        }
    }

    protected abstract RootAdapter<T, ? extends RecyclerView.ViewHolder> onCreateAdapter();
    protected abstract Single<Response<Page<T>>> loadPage(int page, boolean bypassCache);
    public abstract void onItemClick(T item);
}
