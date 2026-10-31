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
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.TrendAdapter;
import com.gh4a.model.Trend;
import com.gh4a.model.TrendService;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.MirrorHelper;
import com.gh4a.utils.SingleFactory;

import java.util.List;

import io.reactivex.Single;
import okhttp3.OkHttpClient;

public class TrendingFragment extends ListDataBaseFragment<Trend> implements
        RootAdapter.OnItemClickListener<Trend> {
    public static final String TYPE_DAILY = "daily";
    public static final String TYPE_WEEKLY = "weekly";
    public static final String TYPE_MONTHLY = "monthly";

    private String mType;
    private @StringRes int mStarsTemplate;
    // 0.1.13：固定在 AppBar 内的时间范围行（切走趋势页时摘掉，切回来重挂）。
    private android.view.View mPinnedRangeRow;

    public static TrendingFragment newInstance(String type) {
        if (type == null) {
            return null;
        }

        TrendingFragment f = new TrendingFragment();
        Bundle args = new Bundle();
        args.putString("type", type);
        switch (type) {
            case TYPE_DAILY: args.putInt("stars_template", R.string.trend_stars_today); break;
            case TYPE_WEEKLY: args.putInt("stars_template", R.string.trend_stars_week); break;
            case TYPE_MONTHLY: args.putInt("stars_template", R.string.trend_stars_month); break;
            default: throw new IllegalArgumentException();
        }
        f.setArguments(args);

        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mType = getArguments().getString("type");
        mStarsTemplate = getArguments().getInt("stars_template", 0);
    }

    @Override
    protected RootAdapter<Trend, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        TrendAdapter adapter = new TrendAdapter(getActivity(), mStarsTemplate);
        adapter.setOnItemClickListener(this);
        return adapter;
    }

    /**
     * 0.5.0 时间范围选择行：仅在 Home 内显示（独立 TrendingActivity 仍用原 3 Tab，
     * 不加选择行）。点击经 HomeActivity.setTrendRange() 回到 TrendingFactory 改范围并刷新，
     * 筛选逻辑与原右上角对话框的三选项完全一致（今日/本周/本月）。
     * 0.1.13：不再以 Adapter header 挂在列表顶部（会随列表滑走），改为固定挂进
     * AppBar（标题栏下方、不滚动），与动态白 Tab 行同款；切走趋势页时在
     * onDestroyView 里摘掉。
     */
    @Override
    protected void onRecyclerViewInflated(RecyclerView view, android.view.LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);
        if (!(getActivity() instanceof com.gh4a.activities.home.HomeActivity)) {
            return;
        }
        android.view.View header = inflater.inflate(R.layout.trend_range_selector, view, false);
        android.widget.TextView today = header.findViewById(R.id.trend_range_today);
        android.widget.TextView week = header.findViewById(R.id.trend_range_week);
        android.widget.TextView month = header.findViewById(R.id.trend_range_month);
        android.widget.TextView[] buttons = new android.widget.TextView[] { today, week, month };
        int selected = TYPE_WEEKLY.equals(mType) ? 1 : TYPE_MONTHLY.equals(mType) ? 2 : 0;
        for (int i = 0; i < buttons.length; i++) {
            final int range = i;
            boolean isSelected = i == selected;
            // 0.1.20 D-09: 选中颜色走 UiUtils.rankOrSelectedColor。
            buttons[i].setTextColor(
                    com.gh4a.utils.UiUtils.rankOrSelectedColor(requireContext(), isSelected));
            buttons[i].setTypeface(null, isSelected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            buttons[i].setOnClickListener(v -> {
                if (range != selected) {
                    ((com.gh4a.activities.home.HomeActivity) requireActivity())
                            .setTrendRange(range);
                }
            });
        }
        // 0.1.13：固定行挂进 AppBar（标题栏下方、不随列表滚动），不再挂 Adapter header。
        // 行本身及其三个按钮都接左右滑监听（OnTouchListener 永不消费，点按仍有效），
        // 所以直接在标签行上滑也能切范围，不只在列表区滑才行。
        android.view.View.OnTouchListener rangeSwipe = makeRangeSwipeListener();
        header.setOnTouchListener(rangeSwipe);
        for (android.widget.TextView b : buttons) {
            b.setOnTouchListener(rangeSwipe);
        }
        mPinnedRangeRow = header;
        ((com.gh4a.activities.home.HomeActivity) requireActivity())
                .setPinnedTrendRangeRow(header);
        // 0.1.12：左右滑只切页内时间范围（今日/本周/本月），永不切底栏；
        // 0.1.16：fling 判定改距离判定——慢速拖动松手不产生 fling，真机上
        // 原判定完全没反应；现松手位移 >= 100dp 且水平大于竖直即切，首末不回绕。
        view.addOnItemTouchListener(
                new com.gh4a.utils.HorizontalSwipeListener.ForRecyclerView(
                        new com.gh4a.utils.HorizontalSwipeListener.Callback() {
                            @Override
                            public void onSwipeLeft() {
                                switchTrendRange(1);
                            }

                            @Override
                            public void onSwipeRight() {
                                switchTrendRange(-1);
                            }
                        }));
    }

    private void switchTrendRange(int step) {
        if (!isAdded()) return;
        int cur = TYPE_WEEKLY.equals(mType) ? 1
                : TYPE_MONTHLY.equals(mType) ? 2 : 0;
        // 手指向左滑 → 下一范围；向右滑 → 上一范围。
        int next = cur + step;
        if (next < 0 || next > 2) return;
        ((com.gh4a.activities.home.HomeActivity) requireActivity())
                .setTrendRange(next);
    }

    // 0.1.13：标签行左右滑监听。监听器永不消费事件（返回 false），点按仍走
    // 按钮的 OnClickListener；0.1.16 起与列表区同款距离判定，首末不回绕。
    private android.view.View.OnTouchListener makeRangeSwipeListener() {
        return new com.gh4a.utils.HorizontalSwipeListener(
                new com.gh4a.utils.HorizontalSwipeListener.Callback() {
                    @Override
                    public void onSwipeLeft() {
                        switchTrendRange(1);
                    }

                    @Override
                    public void onSwipeRight() {
                        switchTrendRange(-1);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        // 切走趋势页时把固定行从 AppBar 摘掉，避免残留在其他页顶栏下方。
        if (mPinnedRangeRow != null
                && getActivity() instanceof com.gh4a.activities.home.HomeActivity) {
            ((com.gh4a.activities.home.HomeActivity) getActivity())
                    .removePinnedTrendRangeRow(mPinnedRangeRow);
            mPinnedRangeRow = null;
        }
        super.onDestroyView();
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_trends_found;
    }

    @Override
    public void onItemClick(Trend trend) {
        String owner = trend.getRepoOwner();
        String name = trend.getRepoName();
        if (owner != null && name != null) {
            startActivity(RepositoryActivity.makeIntent(getActivity(), owner, name));
        }
    }

    @Override
    protected Single<List<Trend>> onCreateDataSingle(boolean bypassCache) {
        if (!bypassCache) {
            return SingleFactory.loadTrends(mType);
        }
        // 下拉刷新时绕过 OkHttp 的 300s 缓存，强制走网络拿最新趋势
        return noCacheTrendService()
                .getTrends(mType)
                .map(ApiHelpers::throwOnFailure);
    }

    // 趋势接口走独立的 Retrofit service（不在 ServiceFactory 的 service 缓存里），
    // 这里为下拉刷新单独建一个带 no-cache 请求头的实例。
    // R-1：Builder 拼装收敛到 SingleFactory.trendService()，这里只定制 client。
    private static volatile TrendService sNoCacheTrendService;

    private static TrendService noCacheTrendService() {
        TrendService service = sNoCacheTrendService;
        if (service == null) {
            synchronized (TrendingFragment.class) {
                service = sNoCacheTrendService;
                if (service == null) {
                    OkHttpClient client = ServiceFactory.getHttpClientBuilder()
                            .addInterceptor(MirrorHelper.mirrorInterceptor(Gh4Application.get()))
                            .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                                    .addHeader("Cache-Control", "no-cache")
                                    .build()))
                            .build();
                    service = SingleFactory.trendService(client);
                    sNoCacheTrendService = service;
                }
            }
        }
        return service;
    }
}
