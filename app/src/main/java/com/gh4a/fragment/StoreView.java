package com.gh4a.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.utils.DiagTrace;
import com.gh4a.utils.UiUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;

/**
 * 搜索页签的商店视图：发现／排行榜两页签（v4 原型）。
 * 由 SearchFragment 在空查询 + 仓库类型时挂出；有查询（提交）时由
 * SearchFragment 隐藏，切回正常搜索结果。顶栏标题保持“搜索”。
 *
 * 数据走后端预计算 JSON（StoreApi.loadBackend），不再实时搜 GitHub：
 * 发现按平台文件分页取（Komi 同款：每页 20 条，hasMore 翻页，滑到底
 * 加载下一页），分类 chips 只筛已加载的项（切分类不发请求）；
 * 排行榜三子榜（趋势／最新发布／最热门）各记各的数据，切回来不重拉，
 * 榜单本身是整包（Komi 的 categories 接口同款，不分页）；子榜用发现页
 * 同款胶囊 chips（0.1.55 起，替代原文字+下划线栏，后者会把后两个挤出屏幕）。
 * 平台是全局的：0.1.50 起平台按钮做到搜索顶栏右上角（白底青绿字，
 * 原型同款），点开底部抽屉选系统，切了发现和排行榜一起重载；
 * 商店区顶部就是发现／排行榜页签。
 *
 * 顶栏搜索框输入时不切走：live 筛当前页签已加载列表（SearchFragment
 * 转发），回车提交才走全局仓库搜索；清空或关闭搜索框后商店恢复。
 * 注意：搜索顶栏本身（搜索框＋类型＋排序）是 0.1.19 才稳住的布局；
 * 0.1.50 起商店可见时排序图标让位给平台按钮（商店不参与排序），
 * 商店隐藏后排序恢复，顶栏按钮由 SearchFragment 的菜单项同步。
 */
public class StoreView extends LinearLayout {
    private static final int TAB_DISCOVER = 0;
    private static final int TAB_RANK = 1;
    private static final int PAGE_SIZE = 20;

    private static final String PLAT_ALL = "all";
    private static final String CAT_ALL = "all";

    private static final String[] CHARTS = {
        "trending", "new-releases", "most-popular"
    };
    private static final int[] CHART_TITLES = {
        R.string.store_chart_trending, R.string.store_chart_releases,
        R.string.store_chart_popular
    };
    private static final String[] FEED_CATS = {
        StoreClassifier.CAT_ALL, StoreClassifier.CAT_AI, StoreClassifier.CAT_PRIVACY,
        StoreClassifier.CAT_NETWORKING, StoreClassifier.CAT_MEDIA,
        StoreClassifier.CAT_SOCIAL, StoreClassifier.CAT_READING, StoreClassifier.CAT_TOOLS
    };
    private static final int[] FEED_CAT_TITLES = {
        R.string.store_filter_all, R.string.store_cat_ai, R.string.store_cat_privacy,
        R.string.store_cat_networking, R.string.store_cat_media,
        R.string.store_cat_social, R.string.store_cat_reading, R.string.store_cat_tool
    };
    private static final String[] PLATFORM_KEYS = {
        PLAT_ALL, "android", "macos", "windows", "linux", "ios"
    };

    private final Context mContext;
    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private final TextView[] mTabLabels = new TextView[2];
    private final View[] mTabIndicators = new View[2];
    private final ScrollView[] mTabScrolls = new ScrollView[2];
    private final LinearLayout[] mTabContainers = new LinearLayout[2];
    private final boolean[] mTabLoaded = new boolean[2];

    private Runnable mPlatformChangedListener;
    private int mCurrentTab = TAB_DISCOVER;
    private int mCurrentChart = 0;
    private boolean mShown;
    private String mFilter;
    private String mPlatform = PLAT_ALL;
    private String mCategory = CAT_ALL;

    private List<StoreRepo> mDiscoverItems;
    private int mDiscoverPage;
    private boolean mDiscoverHasMore = true;
    private boolean mDiscoverLoading;
    private boolean mDiscoverOffline;
    private boolean mDiscoverMirror;
    private final StoreApi.StoreData[] mChartData = new StoreApi.StoreData[3];
    private final int[] mChartLoaded = new int[3];

    public StoreView(Context context) {
        super(context);
        mContext = context;
        setOrientation(VERTICAL);
        setBackgroundColor(StoreViews.backgroundColor(context));

        addView(buildTabBar());
        addView(StoreViews.divider(context));

        FrameLayout content = new FrameLayout(context);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < 2; i++) {
            ScrollView scroll = new ScrollView(context);
            scroll.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scroll.setFillViewport(true);
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(VERTICAL);
            scroll.addView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(scroll);
            mTabScrolls[i] = scroll;
            mTabContainers[i] = container;
            scroll.setVisibility(GONE);
        }
        addView(content);
        updateTabStyles();
        mTabScrolls[TAB_DISCOVER].setVisibility(VISIBLE);
    }

    private View buildTabBar() {
        LinearLayout tabBar = new LinearLayout(mContext);
        tabBar.setOrientation(HORIZONTAL);
        tabBar.setBackgroundColor(StoreViews.backgroundColor(mContext));
        int[] tabTitles = { R.string.store_discover, R.string.store_rank };
        for (int i = 0; i < 2; i++) {
            final int tab = i;
            LinearLayout tabCell = new LinearLayout(mContext);
            tabCell.setOrientation(VERTICAL);
            tabCell.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            tabCell.setGravity(Gravity.CENTER);
            UiUtils.setSelectableBackground(tabCell);
            tabCell.setOnClickListener(v -> selectTab(tab));
            TextView label = new TextView(mContext);
            label.setText(tabTitles[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, StoreViews.dp(mContext, 13), 0, StoreViews.dp(mContext, 11));
            tabCell.addView(label);
            View indicator = new View(mContext);
            indicator.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, StoreViews.dp(mContext, 2)));
            tabCell.addView(indicator);
            tabBar.addView(tabCell);
            mTabLabels[i] = label;
            mTabIndicators[i] = indicator;
        }
        return tabBar;
    }

    /** 首次展示时加载发现；之后切回不再重复加载（页签重选才刷新）。 */
    public void onShown() {
        if (!mShown) {
            mShown = true;
            selectTab(TAB_DISCOVER);
        }
    }

    public void onDestroy() {
        mDisposables.clear();
    }

    /** 顶栏搜索框的 live 筛选：只重绘当前页签缓存，不发请求。 */
    public void setFilter(String filter) {
        mFilter = TextUtils.isEmpty(filter) ? null : filter;
        if (mTabLoaded[mCurrentTab]) {
            renderTabFromCache(mCurrentTab);
        }
    }

    /** 重载当前页签（页签重选、商店下拉刷新入口）。 */
    public void reloadCurrentTab() {
        mTabLoaded[mCurrentTab] = false;
        if (mCurrentTab == TAB_DISCOVER) {
            resetDiscover();
        } else {
            mChartData[mCurrentChart] = null;
            mChartLoaded[mCurrentChart] = 0;
        }
        loadTab(mCurrentTab);
    }

    /** 发现分页状态复位（重载、切平台时用，第 1 页重取）。 */
    private void resetDiscover() {
        mDiscoverItems = null;
        mDiscoverPage = 0;
        mDiscoverHasMore = true;
        mDiscoverLoading = false;
        mDiscoverOffline = false;
        mDiscoverMirror = false;
    }

    private void selectTab(int tab) {
        boolean reselect = tab == mCurrentTab && mTabLoaded[tab];
        mCurrentTab = tab;
        updateTabStyles();
        for (int i = 0; i < 2; i++) {
            mTabScrolls[i].setVisibility(i == tab ? VISIBLE : GONE);
        }
        if (reselect) {
            reloadCurrentTab();
            return;
        }
        if (mTabLoaded[tab]) {
            renderTabFromCache(tab);
        } else {
            loadTab(tab);
        }
    }

    private void selectChart(int chart) {
        if (chart == mCurrentChart && mChartData[chart] != null) {
            mChartData[chart] = null;
            mChartLoaded[chart] = 0;
            loadTab(TAB_RANK);
            return;
        }
        mCurrentChart = chart;
        if (mChartData[chart] != null) {
            renderRank();
        } else {
            loadTab(TAB_RANK);
        }
    }

    private void updateTabStyles() {
        int primary = StoreViews.primaryColor(mContext);
        int secondary = StoreViews.secondaryText(mContext);
        for (int i = 0; i < 2; i++) {
            boolean selected = i == mCurrentTab;
            mTabLabels[i].setTextColor(selected ? primary : secondary);
            mTabLabels[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            mTabIndicators[i].setBackgroundColor(selected ? primary : Color.TRANSPARENT);
        }
    }

    /** 顶栏平台按钮的文案（0.1.50：按钮已移到搜索顶栏，商店区不再有平台行）。 */
    public String getPlatformLabel() {
        return platformLabel(mPlatform);
    }

    /** 平台切换后通知持有方（顶栏按钮文案刷新用）。 */
    public void setOnPlatformChangedListener(Runnable listener) {
        mPlatformChangedListener = listener;
    }

    private String platformLabel(String key) {
        switch (key) {
            case "android":
                return "Android";
            case "ios":
                return "iOS";
            case "windows":
                return getString(R.string.store_platform_windows);
            case "macos":
                return getString(R.string.store_platform_macos);
            case "linux":
                return getString(R.string.store_platform_linux);
            default:
                return getString(R.string.store_platform_all);
        }
    }

    public void showPlatformSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(mContext);
        LinearLayout sheet = new LinearLayout(mContext);
        sheet.setOrientation(VERTICAL);
        int side = StoreViews.dp(mContext, 16);
        sheet.setPadding(side, StoreViews.dp(mContext, 16), side, StoreViews.dp(mContext, 20));
        TextView title = StoreViews.text(mContext, 17, true);
        title.setText(R.string.store_platform_title);
        title.setPadding(0, 0, 0, StoreViews.dp(mContext, 6));
        sheet.addView(title);
        for (int i = 0; i < PLATFORM_KEYS.length; i++) {
            final String key = PLATFORM_KEYS[i];
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, StoreViews.dp(mContext, 14), 0, StoreViews.dp(mContext, 14));
            UiUtils.setSelectableBackground(row);
            TextView name = StoreViews.text(mContext, 15, false);
            name.setText(platformLabel(key));
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);
            if (key.equals(mPlatform)) {
                TextView check = StoreViews.text(mContext, 18, true);
                check.setText("✓");
                check.setTextColor(StoreViews.primaryColor(mContext));
                row.addView(check);
            }
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (!key.equals(mPlatform)) {
                    mPlatform = key;
                    onPlatformChanged();
                    if (mPlatformChangedListener != null) {
                        mPlatformChangedListener.run();
                    }
                }
            });
            sheet.addView(row);
            if (i < PLATFORM_KEYS.length - 1) {
                sheet.addView(StoreViews.divider(mContext));
            }
        }
        dialog.setContentView(sheet);
        dialog.show();
    }

    /** 平台是全局的：切了两边一起重载（当前页签先行，另一页签记脏）。 */
    private void onPlatformChanged() {
        mTabLoaded[TAB_DISCOVER] = false;
        mTabLoaded[TAB_RANK] = false;
        resetDiscover();
        for (int i = 0; i < 3; i++) {
            mChartData[i] = null;
            mChartLoaded[i] = 0;
        }
        loadTab(mCurrentTab);
    }

    private void loadTab(int tab) {
        LinearLayout container = mTabContainers[tab];
        container.removeAllViews();
        container.addView(StoreViews.infoText(mContext, getString(R.string.loading_msg)));
        if (tab == TAB_DISCOVER) {
            if (mDiscoverItems != null) {
                renderDiscover();
                return;
            }
            loadDiscoverPage(1);
        } else {
            final int chart = mCurrentChart;
            if (mChartData[chart] != null) {
                renderRank();
                return;
            }
            mDisposables.add(StoreApi.loadBackend(mContext, CHARTS[chart], mPlatform)
                    .subscribe(data -> {
                        mChartData[chart] = data;
                        mChartLoaded[chart] = PAGE_SIZE;
                        mTabLoaded[TAB_RANK] = true;
                        DiagTrace.log(mContext, "STORE", "rank chart=" + CHARTS[chart]
                                + " platform=" + mPlatform + " count=" + data.items.size()
                                + " offline=" + data.offline
                                + " mirror=" + data.mirror);
                        if (mCurrentTab == TAB_RANK && mCurrentChart == chart) {
                            renderRank();
                        }
                    }, error -> showTabError(TAB_RANK, error)));
        }
    }

    /** 发现取一页（Komi 同款翻页：第 1 页失败整页报错，往后页失败保留已加载项，按钮可重试）。 */
    private void loadDiscoverPage(final int page) {
        if (mDiscoverLoading) {
            return;
        }
        mDiscoverLoading = true;
        mDisposables.add(StoreApi.loadBackendFeedPage(mContext, mPlatform, page)
                .subscribe(data -> {
                    if (page == 1) {
                        mDiscoverItems = new ArrayList<>(data.items);
                    } else if (mDiscoverItems != null) {
                        mDiscoverItems.addAll(data.items);
                    } else {
                        mDiscoverItems = new ArrayList<>(data.items);
                    }
                    mDiscoverPage = page;
                    mDiscoverHasMore = data.hasMore;
                    mDiscoverLoading = false;
                    mDiscoverOffline = data.offline;
                    mDiscoverMirror = data.mirror;
                    mTabLoaded[TAB_DISCOVER] = true;
                    DiagTrace.log(mContext, "STORE", "discover platform=" + mPlatform
                            + " page=" + page + " count=" + data.items.size()
                            + " hasMore=" + data.hasMore
                            + " offline=" + data.offline
                            + " mirror=" + data.mirror);
                    if (mCurrentTab == TAB_DISCOVER) {
                        renderDiscover();
                    }
                }, error -> {
                    mDiscoverLoading = false;
                    if (page == 1) {
                        showTabError(TAB_DISCOVER, error);
                    } else {
                        DiagTrace.log(mContext, "STORE", "discover page=" + page
                                + " fail " + StoreApi.describeError(error));
                        if (mCurrentTab == TAB_DISCOVER) {
                            renderDiscover();
                        }
                    }
                }));
    }

    private void showTabError(final int tab, Throwable error) {
        DiagTrace.log(mContext, "STORE",
                "tab=" + tab + " fail " + StoreApi.describeError(error));
        LinearLayout container = mTabContainers[tab];
        container.removeAllViews();
        TextView errorView = StoreViews.infoText(mContext, getString(R.string.store_fetch_failed));
        UiUtils.setSelectableBackground(errorView);
        errorView.setOnClickListener(v -> loadTab(tab));
        container.addView(errorView);
    }

    private void renderTabFromCache(int tab) {
        if (tab == TAB_DISCOVER) {
            if (mDiscoverItems != null) {
                renderDiscover();
            } else {
                loadTab(tab);
            }
        } else {
            if (mChartData[mCurrentChart] != null) {
                renderRank();
            } else {
                loadTab(tab);
            }
        }
    }

    // ---------- 发现 ----------

    private void renderDiscover() {
        LinearLayout container = mTabContainers[TAB_DISCOVER];
        container.removeAllViews();
        StoreViews.FlowLayout chips = new StoreViews.FlowLayout(mContext);
        int side = StoreViews.dp(mContext, 16);
        chips.setPadding(side, StoreViews.dp(mContext, 12), side, 0);
        for (int i = 0; i < FEED_CATS.length; i++) {
            final String category = FEED_CATS[i];
            TextView chip = StoreViews.chip(mContext,
                    getString(FEED_CAT_TITLES[i]), category.equals(mCategory), v -> {
                        mCategory = category;
                        renderDiscover();
                    });
            StoreViews.FlowLayout.LayoutParams lp = new StoreViews.FlowLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, StoreViews.dp(mContext, 8), StoreViews.dp(mContext, 8));
            chips.addView(chip, lp);
        }
        container.addView(chips);
        if (mDiscoverMirror) {
            container.addView(mirrorStrip());
        } else if (mDiscoverOffline) {
            container.addView(offlineStrip());
        }
        List<StoreRepo> source = mDiscoverItems != null ? mDiscoverItems : new ArrayList<>();
        List<StoreRepo> visible = new ArrayList<>();
        for (StoreRepo item : source) {
            if (StorePrefs.isHidden(mContext, item.fullName())) {
                continue;
            }
            if (!CAT_ALL.equals(mCategory) && !item.topicCodes.contains(mCategory)) {
                continue;
            }
            if (!matchesFilter(item)) {
                continue;
            }
            visible.add(item);
        }
        if (visible.isEmpty()) {
            container.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
        } else {
            for (StoreRepo item : visible) {
                container.addView(rowView(item));
                container.addView(StoreViews.divider(mContext));
            }
        }
        if (mDiscoverLoading) {
            TextView loading = StoreViews.text(mContext, 13, false);
            loading.setText(R.string.loading_msg);
            loading.setTextColor(StoreViews.secondaryText(mContext));
            loading.setGravity(Gravity.CENTER);
            loading.setPadding(side, StoreViews.dp(mContext, 14),
                    side, StoreViews.dp(mContext, 18));
            container.addView(loading);
        } else if (mDiscoverHasMore) {
            TextView more = StoreViews.text(mContext, 14, true);
            more.setText(R.string.store_load_more);
            more.setTextColor(StoreViews.primaryColor(mContext));
            more.setGravity(Gravity.CENTER);
            more.setPadding(side, StoreViews.dp(mContext, 16),
                    side, StoreViews.dp(mContext, 16));
            UiUtils.setSelectableBackground(more);
            more.setOnClickListener(v -> loadDiscoverPage(mDiscoverPage + 1));
            container.addView(more);
        } else if (!source.isEmpty()) {
            TextView end = StoreViews.text(mContext, 13, false);
            end.setText(R.string.store_all_loaded);
            end.setTextColor(StoreViews.secondaryText(mContext));
            end.setGravity(Gravity.CENTER);
            end.setPadding(side, StoreViews.dp(mContext, 14),
                    side, StoreViews.dp(mContext, 18));
            container.addView(end);
        }
    }

    // ---------- 排行榜 ----------

    private void renderRank() {
        LinearLayout container = mTabContainers[TAB_RANK];
        container.removeAllViews();
        StoreApi.StoreData data = mChartData[mCurrentChart];
        if (data == null) {
            loadTab(TAB_RANK);
            return;
        }
        // 子榜 chips（0.1.55 起：发现页同款胶囊，替代原文字+下划线栏，
        // 后者第一个格子占满整行、把后两个挤出屏幕）。
        StoreViews.FlowLayout chips = new StoreViews.FlowLayout(mContext);
        chips.setPadding(StoreViews.dp(mContext, 16), StoreViews.dp(mContext, 12),
                StoreViews.dp(mContext, 16), 0);
        for (int i = 0; i < CHARTS.length; i++) {
            final int chart = i;
            TextView chip = StoreViews.chip(mContext,
                    getString(CHART_TITLES[i]), chart == mCurrentChart, v -> selectChart(chart));
            StoreViews.FlowLayout.LayoutParams lp = new StoreViews.FlowLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, StoreViews.dp(mContext, 8), StoreViews.dp(mContext, 8));
            chips.addView(chip, lp);
        }
        container.addView(chips);
        if (data.mirror) {
            container.addView(mirrorStrip());
        } else if (data.offline) {
            container.addView(offlineStrip());
        }
        List<StoreRepo> source = data.items;
        List<StoreRepo> visible = new ArrayList<>();
        int limit = Math.min(mChartLoaded[mCurrentChart], source.size());
        for (int i = 0; i < limit; i++) {
            StoreRepo item = source.get(i);
            if (StorePrefs.isHidden(mContext, item.fullName())) {
                continue;
            }
            if (!matchesFilter(item)) {
                continue;
            }
            visible.add(item);
        }
        if (visible.isEmpty()) {
            container.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
        } else {
            int rank = 0;
            for (StoreRepo item : visible) {
                container.addView(rankRowView(++rank, item));
                container.addView(StoreViews.divider(mContext));
            }
        }
        if (!source.isEmpty()) {
            int side = StoreViews.dp(mContext, 16);
            if (mChartLoaded[mCurrentChart] < source.size()) {
                TextView more = StoreViews.text(mContext, 14, true);
                more.setText(R.string.store_load_more);
                more.setTextColor(StoreViews.primaryColor(mContext));
                more.setGravity(Gravity.CENTER);
                more.setPadding(side, StoreViews.dp(mContext, 16),
                        side, StoreViews.dp(mContext, 16));
                UiUtils.setSelectableBackground(more);
                more.setOnClickListener(v -> {
                    mChartLoaded[mCurrentChart] += PAGE_SIZE;
                    renderRank();
                });
                container.addView(more);
            } else {
                TextView end = StoreViews.text(mContext, 13, false);
                end.setText(R.string.store_all_loaded);
                end.setTextColor(StoreViews.secondaryText(mContext));
                end.setGravity(Gravity.CENTER);
                end.setPadding(side, StoreViews.dp(mContext, 14),
                        side, StoreViews.dp(mContext, 18));
                container.addView(end);
            }
        }
    }

    private View rankRowView(int rank, final StoreRepo item) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int side = StoreViews.dp(mContext, 16);
        row.setPadding(side, StoreViews.dp(mContext, 12), side, StoreViews.dp(mContext, 12));
        TextView num = StoreViews.text(mContext, 18, true);
        num.setText(String.valueOf(rank));
        num.setTextColor(rank <= 3
                ? StoreViews.primaryColor(mContext) : StoreViews.secondaryText(mContext));
        num.setGravity(Gravity.CENTER);
        num.setLayoutParams(new LinearLayout.LayoutParams(
                StoreViews.dp(mContext, 28), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(num);
        LinearLayout texts = new LinearLayout(mContext);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView main = StoreViews.text(mContext, 15, true);
        main.setText(item.fullName());
        texts.addView(main);
        String language = item.language != null ? item.language : getString(R.string.unknown);
        TextView subView = StoreViews.text(mContext, 13, false);
        subView.setText("★ " + StoreViews.formatStars(item.stars())
                + " · " + language
                + (item.version().isEmpty() ? "" : " · " + item.version()));
        subView.setTextColor(StoreViews.secondaryText(mContext));
        texts.addView(subView);
        row.addView(texts);
        UiUtils.setSelectableBackground(row);
        row.setOnClickListener(v -> mContext.startActivity(
                RepositoryActivity.makeIntent(mContext, item.owner(), item.name())));
        if (StorePrefs.isSeen(mContext, item.fullName())) {
            row.setAlpha(0.55f);
        }
        row.setOnLongClickListener(v -> {
            showRowMenu(item);
            return true;
        });
        return row;
    }

    // ---------- 通用 ----------

    private View rowView(final StoreRepo item) {
        View row = StoreViews.repoRow(mContext, item, v -> mContext.startActivity(
                RepositoryActivity.makeIntent(mContext, item.owner(), item.name())));
        if (StorePrefs.isSeen(mContext, item.fullName())) {
            row.setAlpha(0.55f);
        }
        row.setOnLongClickListener(v -> {
            showRowMenu(item);
            return true;
        });
        return row;
    }

    private void showRowMenu(final StoreRepo item) {
        final String fullName = item.fullName();
        boolean seen = StorePrefs.isSeen(mContext, fullName);
        String[] actions = {
            getString(R.string.store_action_share),
            getString(R.string.store_action_open_github),
            getString(seen ? R.string.store_action_unmark_seen
                    : R.string.store_action_mark_seen),
            getString(R.string.store_action_hide),
        };
        new AlertDialog.Builder(mContext)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0: {
                            Intent send = new Intent(Intent.ACTION_SEND);
                            send.setType("text/plain");
                            send.putExtra(Intent.EXTRA_TEXT,
                                    "https://github.com/" + fullName);
                            mContext.startActivity(Intent.createChooser(send, null));
                            break;
                        }
                        case 1:
                            mContext.startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/" + fullName)));
                            break;
                        case 2:
                            StorePrefs.setSeen(mContext, fullName, !seen);
                            renderTabFromCache(mCurrentTab);
                            break;
                        default:
                            StorePrefs.hide(mContext, fullName);
                            renderTabFromCache(mCurrentTab);
                            break;
                    }
                })
                .show();
    }

    private View offlineStrip() {
        TextView strip = StoreViews.text(mContext, 13, false);
        strip.setText(R.string.store_offline_data);
        strip.setTextColor(StoreViews.secondaryText(mContext));
        int side = StoreViews.dp(mContext, 16);
        strip.setPadding(side, StoreViews.dp(mContext, 12), side, 0);
        return strip;
    }

    private View mirrorStrip() {
        TextView strip = StoreViews.text(mContext, 13, false);
        strip.setText(R.string.store_mirror_data);
        strip.setTextColor(StoreViews.secondaryText(mContext));
        int side = StoreViews.dp(mContext, 16);
        strip.setPadding(side, StoreViews.dp(mContext, 12), side, 0);
        return strip;
    }

    private boolean matchesFilter(StoreRepo item) {
        if (mFilter == null) {
            return true;
        }
        String query = mFilter.toLowerCase();
        String name = item.fullName() != null ? item.fullName().toLowerCase() : "";
        String desc = item.description != null ? item.description.toLowerCase() : "";
        return name.contains(query) || desc.contains(query);
    }

    private String getString(int resId, Object... args) {
        return mContext.getString(resId, args);
    }
}
