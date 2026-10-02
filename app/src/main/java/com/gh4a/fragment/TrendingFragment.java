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
import com.meisolsson.githubsdk.core.ServiceGenerator;

import java.util.List;

import io.reactivex.Single;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

public class TrendingFragment extends ListDataBaseFragment<Trend> implements
        RootAdapter.OnItemClickListener<Trend> {
    public static final String TYPE_DAILY = "daily";
    public static final String TYPE_WEEKLY = "weekly";
    public static final String TYPE_MONTHLY = "monthly";

    private String mType;
    private @StringRes int mStarsTemplate;

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
    // 注意：构造参数与 SingleFactory.RetrofitHelper 中的趋势 service 保持一致，
    // 若后者变更（baseUrl/转换器/镜像拦截器），这里需要同步修改。
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
                    service = new Retrofit.Builder()
                            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                            .addConverterFactory(MoshiConverterFactory.create(ServiceGenerator.moshi))
                            .baseUrl("https://raw.githubusercontent.com/Unpublished/GithubTrending/")
                            .client(client)
                            .build()
                            .create(TrendService.class);
                    sNoCacheTrendService = service;
                }
            }
        }
        return service;
    }
}
