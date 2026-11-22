package com.gh4a.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.utils.DiagTrace;
import com.gh4a.utils.UiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.disposables.CompositeDisposable;

/**
 * 搜索页签的商店视图：首页／排行榜／标签云三页签（hiwiki 结构，0.1.56 起；
 * 2026-11-22 起旧首页下架，探索改名首页，新增标签云）。
 * 由 SearchFragment 在空查询 + 仓库类型时挂出；有查询（提交）时由
 * SearchFragment 隐藏，切回正常搜索结果。顶栏标题保持“搜索”。
 *
 * 数据走后端预计算 JSON（StoreApi.loadBackend 的 feed/all.json 整包，不新增后端
 * 文件）：三页签共用同一份已加载 feed，全部派生、排序、筛选都在内存里
 * 算，不发请求。首页取 feed/all.json 整包进内存，顶部统计块＋四种排序＋
 * 系统／用途筛选，20 条一页，滑到底自动加载下一页（0.1.68 起，无“加载更多”
 * 按钮）；排行榜 98 榜由 StoreRankGroups 分组（0.1.66 起
 * 后端给 ranks，旧数据回落本地），空榜默认如实留空，勾选「只看有收录
 * 的榜单」时隐藏空卡片；标签云取条目 topics 在内存里聚合计数，默认只
 * 展示项目数超 10 个的热门标签，框内搜全部标签，点标签进该标签的仓库
 * 列表（按星标排）。平台是全局的：首页的系统网格点格切平台，
 * 三页一起重载（0.1.58 起顶栏右上角不再放平台按钮）；商店区顶部就是
 * 首页／排行榜／标签云页签，默认首页。最新入库是独立子屏（不在页签里）：
 * 读 store/feed/newest.json，24h 缓存，与 feed 同款回落链路。
 *
 * 系统网格 0.1.66 起 9 格（全部／Android／iOS／Windows／MacOS／Linux／
 * 鸿蒙／浏览器扩展／其他）：后端只发 6 个分平台 feed 文件，9 格放不下，
 * 所以 feed 固定取 all.json，平台筛选全在内存里做；“其他”指无命名平台
 * 的条目（平台为空或只有压缩包）。用途网格 0.1.66 起读条目 category
 * （hiwiki 7 类＋其他），不再用 Komi topicCodes。
 *
 * 顶栏搜索框 0.1.60 起只做全局仓库搜索：输入时商店原样不动，回车提交
 * 才走全局仓库搜索；清空或关闭搜索框后商店回默认页（首页）。注意：
 * 搜索顶栏本身（搜索框＋类型＋排序）是 0.1.19 才稳住的布局；0.1.50 起
 * 商店可见时排序图标让位给平台按钮（商店不参与顶栏排序），商店隐藏后
 * 排序恢复，顶栏按钮由 SearchFragment 的菜单项同步。
 */
public class StoreView extends LinearLayout {
    // 2026-11-22：旧首页下架，探索改名首页（内容不变：统计块＋4 排序＋
    // 系统／用途网格＋列表），排行榜顺移，新增标签云。
    private static final int TAB_HOME = 0;
    private static final int TAB_RANK = 1;
    private static final int TAB_TAGS = 2;
    private static final int TAB_COUNT = 3;
    private static final int PAGE_SIZE = 20;

    private static final int SORT_STARS = 0;
    private static final int SORT_UPDATED = 1;
    private static final int SORT_DOWNLOADS = 2;
    private static final int SORT_HOT = 3;

    private static final String PLAT_ALL = "all";
    private static final String PLAT_OTHER = "other";
    private static final String CAT_ALL = "all";

    private static final int[] SORT_TITLES = {
        R.string.store_sort_stars, R.string.store_sort_updated,
        R.string.store_sort_downloads, R.string.store_sort_hot
    };
    private static final int[] SORT_NOTES = {
        R.string.store_sort_note_stars, R.string.store_sort_note_updated,
        R.string.store_sort_note_downloads, R.string.store_sort_note_hot
    };
    // 0.1.66：用途网格换成 hiwiki 的 7 类＋其他，读条目 category（设计稿顺序）。
    private static final String[] FEED_CATS = {
        CAT_ALL, "ai", "media", "network", "tools",
        "productivity", "development", "game", "other"
    };
    private static final int[] FEED_CAT_TITLES = {
        R.string.store_filter_all, R.string.store_cat_ai, R.string.store_cat_media,
        R.string.store_cat_networking, R.string.store_cat_tool,
        R.string.store_cat_efficiency, R.string.store_cat_dev,
        R.string.store_cat_game, R.string.store_cat_other
    };
    // 0.1.66：系统顺序改成 全部／Android／iOS／Windows／MacOS／Linux／鸿蒙／
    // 浏览器扩展／其他（首页／探索／排行榜三处共用）。
    private static final String[] PLATFORM_KEYS = {
        PLAT_ALL, "android", "ios", "windows", "macos", "linux",
        "harmony", "extension", PLAT_OTHER
    };

    private final Context mContext;
    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private final TextView[] mTabLabels = new TextView[TAB_COUNT];
    private final View[] mTabIndicators = new View[TAB_COUNT];
    private final ScrollView[] mTabScrolls = new ScrollView[TAB_COUNT];
    private final LinearLayout[] mTabContainers = new LinearLayout[TAB_COUNT];
    private final boolean[] mTabLoaded = new boolean[TAB_COUNT];

    // 0.1.59：首页／标签云的静态头与列表区拆分（排行榜沿用整容器）：
    // 筛时只重建列表区，标题、排序、筛选 chips 组成的静态头不动。
    private final LinearLayout[] mTabHeaderContainers = new LinearLayout[TAB_COUNT];
    private final LinearLayout[] mTabListContainers = new LinearLayout[TAB_COUNT];

    // 0.1.59：feed 级计数缓存——平台／用途数量与统计行在 feed 加载完
    // 算一次，渲染时直接取；feed 重载时失效重算。
    private int[] mPlatformCounts;
    private int[] mCategoryCounts;
    private int mStatsAssets;
    private boolean mCountsReady;

    private int mCurrentTab = TAB_HOME;
    private boolean mShown;
    private String mPlatform = PLAT_ALL;

    // 三页签共用的 feed 整包（feed/all.json，一次取完，3h 缓存）。
    private StoreApi.StoreData mFeedData;
    private boolean mFeedLoading;
    private boolean mFeedOffline;
    private boolean mFeedMirror;
    private List<StoreRankGroups.Group> mRankGroups;

    // 最新入库子屏（0.1.66）：独立 ScrollView，不占页签；数据读
    // store/feed/newest.json，24h 缓存；加载失败时屏内显示空态／重试。
    private ScrollView mNewestScroll;
    private LinearLayout mNewestContainer;
    private boolean mShowingNewest;
    private StoreApi.StoreData mNewestData;
    // 0.2.2：首页统计块里“最新入库”的数字 TextView（renderHomeHeader 时记下；
    // 最新入库数据到了只改这一格的数字，不重建整个首页头，避免滚动时抖动）。
    private TextView mNewestCountView;
    private boolean mNewestLoading;
    private boolean mNewestFailed;

    // 最新入库的系统筛选（StorePrefs 持久化，默认全部；只筛这 60 条，不动全局平台）。
    private String mNewestPlatform = PLAT_ALL;

    // 首页状态：排序偏好持久化（StorePrefs，键名仍叫 explore sort，值沿用），
    // 分类只筛已加载项。
    private int mSort;
    private String mCategory = CAT_ALL;
    private int mExploreShown = PAGE_SIZE;
    // 0.1.68：首页当前可见总数（renderHomeList 时记；滑到底监听直接读，
    // 避免每次滚动事件都重算筛选＋排序）。
    private int mExploreVisibleSize;

    // 标签云状态（2026-11-22）：标签计数由 feed 条目 topics 聚合，feed 重载
    // 时失效；mTagQuery 为框内搜索词（空＝只看热门），mTagDetail 非空时为
    // 该标签的详情列表（按星标排，20 条一页，与首页同款滑到底加载）。
    private static final int TAG_HOT_MIN = 10;
    private List<TagCount> mTagCounts;
    private boolean mTagCountsReady;
    private String mTagQuery = "";
    private String mTagDetail;
    private int mTagShown = PAGE_SIZE;
    private int mTagVisibleSize;
    private LinearLayout mTagCloudRows;

    /** 标签云一行的名字与收录数（按收录数降序、名字升序排）。 */
    private static class TagCount {
        final String name;
        final int count;

        TagCount(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    // 排行榜子视图：完整榜单展开时记组／榜下标，-1 表示概览。
    private int mRankGroup = -1;
    private int mRankList = -1;

    // 0.2.2：完整榜单分页（与探索页同款 20 条/页）：当前已展示条数、
    // 当前榜单总条数（滑到底监听直接读，避免每次滚动都重算分组），
    // 以及当前展示的组/榜下标（换榜时自动回到第 1 页）。
    private int mRankShown = PAGE_SIZE;
    private int mRankFullTotal;
    private int mRankShownGroup = -1;
    private int mRankShownList = -1;

    // 排行榜“只看有收录的榜单”复选框（StorePrefs 持久化，默认不勾选）。
    private boolean mRankOnlyNonEmpty;

    // 排行榜的系统筛选（StorePrefs 持久化，默认全部；只筛榜单池子，不动全局平台）。
    private String mRankPlatform = PLAT_ALL;

    public StoreView(Context context) {
        super(context);
        mContext = context;
        mSort = StorePrefs.getExploreSort(context);
        mRankOnlyNonEmpty = StorePrefs.getRankOnlyNonEmpty(context);
        mRankPlatform = StorePrefs.getRankPlatform(context);
        mNewestPlatform = StorePrefs.getNewestPlatform(context);
        mPlatform = StorePrefs.getPlatform(context);
        mCategory = StorePrefs.getCategory(context);
        setOrientation(VERTICAL);
        setBackgroundColor(StoreViews.backgroundColor(context));

        addView(buildTabBar());
        addView(StoreViews.divider(context));

        FrameLayout content = new FrameLayout(context);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        for (int i = 0; i < TAB_COUNT; i++) {
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
            if (i != TAB_RANK) {
                // 0.1.59：首页／标签云拆静态头＋列表区两个子容器（排行榜不用）。
                LinearLayout header = new LinearLayout(context);
                header.setOrientation(VERTICAL);
                container.addView(header, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout list = new LinearLayout(context);
                list.setOrientation(VERTICAL);
                container.addView(list, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                mTabHeaderContainers[i] = header;
                mTabListContainers[i] = list;
            }
            scroll.setVisibility(GONE);
        }
        // 0.1.68：首页滑到底自动加载下一页（取代“加载更多”按钮）。
        // 只在真实下滑到底时追加 20 条：scrollY 未增大（重建列表时的位置
        // 调整）不触发；追加后内容变长、canScrollVertically(1) 恢复 true，
        // 同一次滑动不会重复触发；mExploreShown 严格递增且以可见总数为界，
        // 不会循环。minSdk 23，setOnScrollChangeListener 可用。
        mTabScrolls[TAB_HOME].setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY <= oldScrollY || mFeedData == null) {
                return;
            }
            if (mExploreShown >= mExploreVisibleSize) {
                return;
            }
            if (!mTabScrolls[TAB_HOME].canScrollVertically(1)) {
                mExploreShown += PAGE_SIZE;
                // 0.2.2：追加只重建列表区，不重建静态头（之前调 renderHome()
                // 连统计块＋排序 chips＋筛选网格一起重建，滚动时会抖）。
                renderHomeList();
            }
        });
        // 0.2.2：完整榜单滑到底自动加载下一页（与首页同款 20 条/页）。
        // 只在完整榜单页追加，概览页不动；mRankShown 严格递增且以当前榜单
        // 总数为界，不会循环。
        mTabScrolls[TAB_RANK].setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY <= oldScrollY || mFeedData == null) {
                return;
            }
            if (mRankGroup < 0 || mRankList < 0) {
                return;
            }
            if (mRankShown >= mRankFullTotal) {
                return;
            }
            if (!mTabScrolls[TAB_RANK].canScrollVertically(1)) {
                mRankShown += PAGE_SIZE;
                renderRank();
            }
        });
        // 2026-11-22：标签详情页滑到底自动加载下一页（与首页同款 20 条/页）。
        // 只在详情态追加，云态不动；mTagShown 严格递增且以当前标签总数为界。
        mTabScrolls[TAB_TAGS].setOnScrollChangeListener(
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY <= oldScrollY || mFeedData == null || mTagDetail == null) {
                return;
            }
            if (mTagShown >= mTagVisibleSize) {
                return;
            }
            if (!mTabScrolls[TAB_TAGS].canScrollVertically(1)) {
                mTagShown += PAGE_SIZE;
                renderTagDetail();
            }
        });
        // 0.1.66：最新入库子屏（独立 ScrollView，与三页签同处一个 FrameLayout）。
        ScrollView newestScroll = new ScrollView(context);
        newestScroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        newestScroll.setFillViewport(true);
        LinearLayout newestContainer = new LinearLayout(context);
        newestContainer.setOrientation(VERTICAL);
        newestScroll.addView(newestContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(newestScroll);
        mNewestScroll = newestScroll;
        mNewestContainer = newestContainer;
        newestScroll.setVisibility(GONE);
        addView(content);
        updateTabStyles();
        mTabScrolls[TAB_HOME].setVisibility(VISIBLE);
    }

    private View buildTabBar() {
        LinearLayout tabBar = new LinearLayout(mContext);
        tabBar.setOrientation(HORIZONTAL);
        tabBar.setBackgroundColor(StoreViews.backgroundColor(mContext));
        int[] tabTitles = {
            R.string.store_home, R.string.store_rank, R.string.store_tags
        };
        for (int i = 0; i < TAB_COUNT; i++) {
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

    /** 首次展示时加载首页；之后切回不再重复加载（页签重选才刷新）。 */
    public void onShown() {
        if (!mShown) {
            mShown = true;
            selectTab(TAB_HOME);
        }
    }

    public void onDestroy() {
        mDisposables.clear();
    }

    /** 当前页签的 ScrollView 是否能上滑（供 SearchFragment 的下拉刷新判断）。 */
    public boolean canScrollUp() {
        if (mShowingNewest) {
            return mNewestScroll != null && mNewestScroll.canScrollVertically(-1);
        }
        ScrollView scroll = mTabScrolls[mCurrentTab];
        return scroll != null && scroll.canScrollVertically(-1);
    }

    /** 关闭搜索框时回默认页（首页），由 SearchFragment.onClose 调。 */
    public void resetToHome() {
        mShowingNewest = false;
        mRankGroup = -1;
        mRankList = -1;
        if (mCurrentTab != TAB_HOME) {
            selectTab(TAB_HOME);
        } else if (mFeedData != null) {
            renderHome();
        }
    }

    /** 重载当前页签（页签重选、商店下拉刷新入口）。 */
    public void reloadCurrentTab() {
        if (mShowingNewest) {
            mNewestData = null;
            mNewestFailed = false;
            loadNewest();
            return;
        }
        resetFeed();
        loadFeed();
    }

    /** feed 状态复位（重载、切平台时用）。 */
    private void resetFeed() {
        mFeedData = null;
        mFeedLoading = false;
        mFeedOffline = false;
        mFeedMirror = false;
        mRankGroups = null;
        // 0.1.59：feed 换了，计数缓存失效。
        mCountsReady = false;
        mPlatformCounts = null;
        mCategoryCounts = null;
        mExploreShown = PAGE_SIZE;
        mRankGroup = -1;
        mRankList = -1;
        // 标签云状态同样复位：计数重算，退出详情态，清掉框内搜索词
        //（搜索框随头重建，不清会框空词在）。
        mTagCountsReady = false;
        mTagCounts = null;
        mTagDetail = null;
        mTagQuery = "";
        mTagShown = PAGE_SIZE;
        // 63：排行榜分页状态同样复位——否则重选页签/下拉刷新后 mRankShown
        // 停在旧页数，再进同一榜单直接展开多页，违背"进榜从第 1 页看"。
        mRankShown = PAGE_SIZE;
        mRankShownGroup = -1;
        mRankShownList = -1;
        for (int i = 0; i < TAB_COUNT; i++) {
            mTabLoaded[i] = false;
        }
    }

    private void selectTab(int tab) {
        boolean reselect = tab == mCurrentTab && mTabLoaded[tab] && !mShowingNewest;
        mCurrentTab = tab;
        mShowingNewest = false;
        // 0.2.2：切回首页／排行榜从第 1 页重看（与 applyPlatform/
        // applyCategory 一致）；否则 mExploreShown/
        // mRankShown 停在上次滑到的页数，切回来直接从半截开始。
        if (tab == TAB_HOME) {
            mExploreShown = PAGE_SIZE;
        }
        if (tab == TAB_RANK) {
            mRankShown = PAGE_SIZE;
        }
        if (tab == TAB_TAGS) {
            mTagShown = PAGE_SIZE;
        }
        // 离开标签云时退出详情态（回云）；在云内切详情不走这里。
        if (tab != TAB_TAGS) {
            mTagDetail = null;
        }
        updateTabStyles();
        for (int i = 0; i < TAB_COUNT; i++) {
            mTabScrolls[i].setVisibility(i == tab ? VISIBLE : GONE);
        }
        mNewestScroll.setVisibility(GONE);
        if (reselect) {
            reloadCurrentTab();
            return;
        }
        if (mTabLoaded[tab]) {
            renderTabFromCache(tab);
        } else {
            loadFeed();
        }
    }

    private void updateTabStyles() {
        int primary = StoreViews.primaryColor(mContext);
        int secondary = StoreViews.secondaryText(mContext);
        for (int i = 0; i < TAB_COUNT; i++) {
            boolean selected = !mShowingNewest && i == mCurrentTab;
            mTabLabels[i].setTextColor(selected ? primary : secondary);
            mTabLabels[i].setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            mTabIndicators[i].setBackgroundColor(selected ? primary : Color.TRANSPARENT);
        }
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
            case "harmony":
                return getString(R.string.store_platform_harmony);
            case "extension":
                return getString(R.string.store_platform_extension);
            case PLAT_OTHER:
                return getString(R.string.store_platform_other);
            default:
                return getString(R.string.store_platform_all);
        }
    }

    private StoreClassifier.Platform platformForKey(String key) {
        switch (key) {
            case "android":
                return StoreClassifier.Platform.ANDROID;
            case "ios":
                return StoreClassifier.Platform.IOS;
            case "windows":
                return StoreClassifier.Platform.WINDOWS;
            case "macos":
                return StoreClassifier.Platform.MACOS;
            case "linux":
                return StoreClassifier.Platform.LINUX;
            case "harmony":
                return StoreClassifier.Platform.HARMONY;
            case "extension":
                return StoreClassifier.Platform.EXTENSION;
            default:
                return null;
        }
    }

    /** 仓库是否属于该平台（all 匹配全部；“其他”指无命名平台：平台为空或只有压缩包）。 */
    private boolean matchesPlatform(StoreRepo item, String key) {
        if (PLAT_ALL.equals(key)) {
            return true;
        }
        if (PLAT_OTHER.equals(key)) {
            return item.platforms.isEmpty()
                    || (item.platforms.size() == 1
                            && item.platforms.contains(StoreClassifier.Platform.ARCHIVE));
        }
        StoreClassifier.Platform platform = platformForKey(key);
        return platform != null && item.platforms.contains(platform);
    }

    /** 首页的系统网格：0.1.66 起只改内存筛选，不再重载 feed
     *（feed 固定取 all.json，9 格里鸿蒙／扩展／其他没有分平台文件）。 */
    private void applyPlatform(String key) {
        if (!key.equals(mPlatform)) {
            mPlatform = key;
            StorePrefs.setPlatform(mContext, key);
            mExploreShown = PAGE_SIZE;
            renderTabFromCache(mCurrentTab);
        }
    }

    /** 首页的用途网格：只筛已加载项，不发请求，留在当前页重绘。 */
    private void applyCategory(String category) {
        mCategory = category;
        StorePrefs.setCategory(mContext, category);
        mExploreShown = PAGE_SIZE;
        renderHome();
    }

    /** 首页内切排序（点当前排序则刷新）。 */
    private void applyExploreSort(int sort) {
        if (sort == mSort && mFeedData != null) {
            reloadCurrentTab();
            return;
        }
        mSort = sort;
        StorePrefs.setExploreSort(mContext, sort);
        mExploreShown = PAGE_SIZE;
        if (mFeedData != null) {
            renderHome();
        } else {
            loadFeed();
        }
    }

    // ---------- 取数：三页签共用一份 feed 整包 ----------

    private void loadFeed() {
        // 100：失败回调要落在发起页签，而不是回调时刻的当前页签
        final int requestTab = mCurrentTab;
        if (mFeedData != null) {
            renderTabFromCache(mCurrentTab);
            return;
        }
        if (mFeedLoading) {
            return;
        }
        mFeedLoading = true;
        // 0.1.59：加载中提示只放进列表区（首页／探索的静态头容器不动，
        // 排行榜沿用整容器）。
        LinearLayout target = listTarget(mCurrentTab);
        target.removeAllViews();
        target.addView(StoreViews.infoText(mContext, getString(R.string.loading_msg)));
        mDisposables.add(StoreApi.loadBackend(mContext, "feed", PLAT_ALL)
                .subscribe(data -> {
                    mFeedData = data;
                    mFeedLoading = false;
                    mFeedOffline = data.offline;
                    mFeedMirror = data.mirror;
                    mRankGroups = null;
                    for (int i = 0; i < TAB_COUNT; i++) {
                        mTabLoaded[i] = true;
                    }
                    DiagTrace.log(mContext, "STORE", "feed all count=" + data.items.size()
                            + " filter=" + mPlatform
                            + " offline=" + data.offline
                            + " mirror=" + data.mirror);
                    renderTabFromCache(mCurrentTab);
                    // 0.1.66：首页统计块要最新入库条数，后台拉 newest（24h 缓存）。
                    loadNewestIfNeeded();
                }, error -> {
                    mFeedLoading = false;
                    showTabError(requestTab, error);
                }));
    }

    private void showTabError(final int tab, Throwable error) {
        DiagTrace.log(mContext, "STORE",
                "tab=" + tab + " fail " + StoreApi.describeError(error));
        LinearLayout container = listTarget(tab);
        container.removeAllViews();
        TextView errorView = StoreViews.infoText(mContext, getString(R.string.store_fetch_failed));
        UiUtils.setSelectableBackground(errorView);
        errorView.setOnClickListener(v -> loadFeed());
        container.addView(errorView);
    }

    private void renderTabFromCache(int tab) {
        if (mFeedData == null) {
            loadFeed();
            return;
        }
        switch (tab) {
            case TAB_HOME:
                renderHome();
                break;
            case TAB_TAGS:
                renderTags();
                break;
            default:
                renderRank();
                break;
        }
    }

    /** 0.1.59：列表区落点——首页／探索用拆分出的列表容器，排行榜沿用整容器。 */
    private LinearLayout listTarget(int tab) {
        if (mTabListContainers[tab] != null) {
            return mTabListContainers[tab];
        }
        return mTabContainers[tab];
    }

    /** 0.1.59：feed 级计数缓存——平台／用途数量与统计行在 feed 加载完
     * 算一次，渲染时直接取；feed 重载时失效重算。 */
    private void ensureCounts() {
        if (!mCountsReady && mFeedData != null) {
            computeCounts();
        }
    }

    private void computeCounts() {
        List<StoreRepo> pool = pool();
        mPlatformCounts = new int[PLATFORM_KEYS.length];
        mCategoryCounts = new int[FEED_CATS.length];
        mStatsAssets = 0;
        for (StoreRepo item : pool) {
            mStatsAssets += item.assetCount;
            for (int i = 0; i < PLATFORM_KEYS.length; i++) {
                if (matchesPlatform(item, PLATFORM_KEYS[i])) {
                    mPlatformCounts[i]++;
                }
            }
            // 0.1.66：用途计数读条目 category（hiwiki 7 类＋其他），不再用 topicCodes。
            String category = item.categoryOrOther();
            for (int i = 0; i < FEED_CATS.length; i++) {
                if (CAT_ALL.equals(FEED_CATS[i]) || FEED_CATS[i].equals(category)) {
                    mCategoryCounts[i]++;
                }
            }
        }
        mCountsReady = true;
    }

    /** 0.1.69：联动计数——系统网格各格 = 该系统且符合当前用途的数；
     * 用途“全部”格即当前用途下的总数。每次渲染现算，不进 mCountsReady 缓存
     *（计数依赖 mPlatform／mCategory 的当前选择，全池缓存不能复用）。 */
    private int[] linkedPlatformCounts() {
        int[] counts = new int[PLATFORM_KEYS.length];
        for (StoreRepo item : pool()) {
            if (!CAT_ALL.equals(mCategory) && !mCategory.equals(item.categoryOrOther())) {
                continue;
            }
            for (int i = 0; i < PLATFORM_KEYS.length; i++) {
                if (matchesPlatform(item, PLATFORM_KEYS[i])) {
                    counts[i]++;
                }
            }
        }
        return counts;
    }

    /** 0.1.69：联动计数——用途网格各格 = 该用途且符合当前系统的数；
     * 系统“全部”格即当前系统下的总数。每次渲染现算，不进 mCountsReady 缓存。 */
    private int[] linkedCategoryCounts() {
        int[] counts = new int[FEED_CATS.length];
        for (StoreRepo item : pool()) {
            if (!matchesPlatform(item, mPlatform)) {
                continue;
            }
            String category = item.categoryOrOther();
            for (int i = 0; i < FEED_CATS.length; i++) {
                if (CAT_ALL.equals(FEED_CATS[i]) || FEED_CATS[i].equals(category)) {
                    counts[i]++;
                }
            }
        }
        return counts;
    }

    /** 已加载 feed；三页签的派生都从这里取，不再各取各的。 */
    private List<StoreRepo> pool() {
        List<StoreRepo> out = new ArrayList<>();
        if (mFeedData == null) {
            return out;
        }
        for (StoreRepo item : mFeedData.items) {
            out.add(item);
        }
        return out;
    }

    private List<StoreRankGroups.Group> rankGroups() {
        if (mRankGroups == null) {
            mRankGroups = StoreRankGroups.build(mContext, rankPool(),
                    mFeedData != null ? mFeedData.boards : null);
        }
        return mRankGroups;
    }

    /** 排行榜池子里是否有后端给的 ranks（任一条带 ranks 字段即算）。 */
    private boolean rankGroupsFromBackend() {
        for (StoreRepo item : rankPool()) {
            if (item.ranks != null) {
                return true;
            }
        }
        return false;
    }

    /** 排行榜池子：先按排行榜的系统筛选筛一遍，再分组榜（卡片数与第 1 名都按筛后算）。 */
    private List<StoreRepo> rankPool() {
        if (PLAT_ALL.equals(mRankPlatform)) {
            return pool();
        }
        List<StoreRepo> out = new ArrayList<>();
        for (StoreRepo item : pool()) {
            if (matchesPlatform(item, mRankPlatform)) {
                out.add(item);
            }
        }
        return out;
    }

    /** 排行榜的系统网格：只筛榜单池子，不动全局平台，状态持久化。 */
    private void applyRankPlatform(String key) {
        if (key.equals(mRankPlatform)) {
            return;
        }
        mRankPlatform = key;
        StorePrefs.setRankPlatform(mContext, key);
        mRankGroups = null;
        // 0.2.2：榜单池子换了，完整榜单回到第 1 页。
        mRankShown = PAGE_SIZE;
        renderRank();
    }

    private static void sortForExplore(List<StoreRepo> items, int sort) {
        switch (sort) {
            case SORT_UPDATED:
                Collections.sort(items, (a, b) -> {
                    int byDate = Long.compare(b.publishedAt, a.publishedAt);
                    return byDate != 0 ? byDate : Integer.compare(b.stars(), a.stars());
                });
                break;
            case SORT_DOWNLOADS:
                Collections.sort(items, (a, b) -> {
                    int byDl = Long.compare(b.downloadCount, a.downloadCount);
                    return byDl != 0 ? byDl : Integer.compare(b.stars(), a.stars());
                });
                break;
            case SORT_HOT:
                // 0.1.57 公式（比较器在 StoreRepo.compareHot，便于单测）：
                // 有 trendingScore 的镜像条目按该分降序在前；无该字段的
                // 生成器条目按 stars_per_day 降序在其后；两者都缺（旧缓存／
                // 实时接口）回落按星标，不得崩溃。
                Collections.sort(items, (a, b) -> StoreRepo.compareHot(a, b));
                break;
            default:
                Collections.sort(items, (a, b) -> Integer.compare(b.stars(), a.stars()));
                break;
        }
    }

    // ---------- 首页 ----------

    private void renderHome() {
        renderHomeHeader();
        renderHomeList();
    }

    /** 2026-11-22：首页静态头（统计块＋排序 chips＋系统／用途网格）——
     * 原探索页的头，顶部加旧首页的统计块；标题行（“探索开源软件”＋副标题）
     * 删掉，ⓘ（当前排序说明）挪到 chips 行末。 */
    private void renderHomeHeader() {
        LinearLayout header = mTabHeaderContainers[TAB_HOME];
        header.removeAllViews();
        // 0.2.2：头重建后旧的数字引用失效，先清掉，statsRow 会重新记。
        mNewestCountView = null;
        ensureCounts();

        // 统计块（hiwiki 首页 2x2：收录／安装包／最新入库／排行榜，四项都能点；
        // 数字取 live feed／newest 数据，不写死；排行榜数为 feed 的 boards
        // 结构计数，旧 feed 无 boards 时按池子里实际出现的榜名数回落）。
        header.addView(statsRow(mPlatformCounts[0], mStatsAssets,
                mNewestData != null ? mNewestData.items.size() : -1,
                StoreRankGroups.totalListCount(
                        mFeedData != null ? mFeedData.boards : null, rankPool())));

        if (mFeedMirror) {
            header.addView(mirrorStrip());
        } else if (mFeedOffline) {
            header.addView(offlineStrip());
        }

        // 4 个排序 chips 一行等权（点切排序、点当前排序刷新；切换时保留平台与分类）。
        // 0.1.61：去图标只留文字——真机上带图标时“最近更新”“近期热门”会被截断。
        int side = StoreViews.dp(mContext, 16);
        LinearLayout sortChips = new LinearLayout(mContext);
        sortChips.setOrientation(LinearLayout.HORIZONTAL);
        sortChips.setGravity(Gravity.CENTER_VERTICAL);
        sortChips.setPadding(side, StoreViews.dp(mContext, 4), side, 0);
        for (int i = 0; i < SORT_TITLES.length; i++) {
            final int sort = i;
            View chip = StoreViews.textChip(mContext,
                    getString(SORT_TITLES[i]), sort == mSort,
                    v -> applyExploreSort(sort));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int gap = StoreViews.dp(mContext, 4);
            lp.setMargins(gap, 0, gap, 0);
            sortChips.addView(chip, lp);
        }
        // 2026-11-22：标题行删掉后，ⓘ（当前排序说明）挪到 chips 行末。
        // 0.1.61：ⓘ 只显示当前排序的说明（旧小字文案）。之前是收录口径 + "\n" +
        // 排序说明两行拼进 Toast，真机上 Toast 只显示第一行还带省略号，
        // 所以点哪个排序看起来都一样，第二行从来出不来。
        ImageView info = new ImageView(mContext);
        info.setImageResource(R.drawable.ic_store_info);
        info.setColorFilter(StoreViews.secondaryText(mContext), PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                StoreViews.dp(mContext, 18), StoreViews.dp(mContext, 18));
        infoLp.setMargins(StoreViews.dp(mContext, 8), 0, 0, 0);
        info.setLayoutParams(infoLp);
        info.setOnClickListener(v -> Toast.makeText(mContext,
                getString(SORT_NOTES[mSort]),
                Toast.LENGTH_LONG).show());
        sortChips.addView(info);
        header.addView(sortChips);

        // 系统／用途两行筛选（图标网格；只筛已加载项，不发请求）。
        header.addView(filterLabel(getString(R.string.store_filter_platform)));
        header.addView(platformGrid());

        header.addView(filterLabel(getString(R.string.store_filter_category)));
        header.addView(categoryGrid());
    }

    /** 首页列表区（结果数＋行＋底栏）——筛时只重建这里。 */
    private void renderHomeList() {
        renderHomeList(pool());
    }

    private void renderHomeList(List<StoreRepo> pool) {
        LinearLayout list = mTabListContainers[TAB_HOME];
        list.removeAllViews();
        int side = StoreViews.dp(mContext, 16);

        List<StoreRepo> visible = exploreFiltered(pool);
        mExploreVisibleSize = visible.size();
        TextView count = StoreViews.text(mContext, 13, false);
        count.setText(getString(R.string.store_result_count, visible.size()));
        count.setTextColor(StoreViews.secondaryText(mContext));
        count.setPadding(side, StoreViews.dp(mContext, 8), side, StoreViews.dp(mContext, 4));
        list.addView(count);

        if (visible.isEmpty()) {
            list.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
            return;
        }
        int limit = Math.min(mExploreShown, visible.size());
        for (int i = 0; i < limit; i++) {
            list.addView(rowView(visible.get(i)));
            list.addView(StoreViews.divider(mContext));
        }
        // 0.1.68：“加载更多”按钮已删，滑到底由首页 ScrollView 的
        // OnScrollChangeListener 自动追加下一页；全部展完才显示底栏。
        if (mExploreShown >= visible.size()) {
            TextView end = StoreViews.text(mContext, 13, false);
            end.setText(R.string.store_all_loaded);
            end.setTextColor(StoreViews.secondaryText(mContext));
            end.setGravity(Gravity.CENTER);
            end.setPadding(side, StoreViews.dp(mContext, 14),
                    side, StoreViews.dp(mContext, 18));
            list.addView(end);
        }
        // 0.2.2：大屏/横屏下 20 条可能塞不满一屏、滑不到底就触发不了自动
        // 加载；渲染后若内容仍不可滚动且还有未展示的条目，再追加一页。
        ensureHomeFillViewport();
    }

    /** 0.2.2：首页列表塞不满一屏时自动追加，直到能滚动或全部展完。 */
    private void ensureHomeFillViewport() {
        ScrollView scroll = mTabScrolls[TAB_HOME];
        if (scroll == null) {
            return;
        }
        scroll.post(() -> {
            if (mFeedData == null || mCurrentTab != TAB_HOME) {
                return;
            }
            if (!scroll.canScrollVertically(1) && mExploreShown < mExploreVisibleSize) {
                mExploreShown += PAGE_SIZE;
                renderHomeList();
            }
        });
    }

    /** 首页统计块（hiwiki 首页 2x2 居中两行：图标＋粗体数字＋灰字；四个都能点；
     * 只有最新入库／排行榜的标签带虚线下划线。newestCount 为 -1 表示最新入库
     * 尚未取到，显示“–”。数字带千分位。 */
    private View statsRow(int repos, int assets, int newestCount, int ranks) {
        LinearLayout rows = new LinearLayout(mContext);
        rows.setOrientation(VERTICAL);
        // 0.1.67：两行定宽居中（hiwiki 的居中 2×2）。之前 rows／first／second 都是
        // WRAP_CONTENT、未定宽，行靠左贴边，第二行只剩一个格子时更明显。
        rows.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int side = StoreViews.dp(mContext, 16);
        rows.setPadding(side, StoreViews.dp(mContext, 16), side, StoreViews.dp(mContext, 4));
        LinearLayout first = new LinearLayout(mContext);
        first.setOrientation(HORIZONTAL);
        first.setGravity(Gravity.CENTER);
        first.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        first.addView(statCell(R.drawable.ic_stat_package,
                StoreViews.formatStars(repos), getString(R.string.store_stat_repos),
                false, false, v -> selectTab(TAB_HOME)));
        first.addView(statGap());
        first.addView(statCell(R.drawable.ic_stat_download,
                StoreViews.formatStars(assets), getString(R.string.store_stat_assets),
                false, false, v -> selectTab(TAB_HOME)));
        rows.addView(first);
        LinearLayout second = new LinearLayout(mContext);
        second.setOrientation(HORIZONTAL);
        second.setGravity(Gravity.CENTER);
        second.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        second.addView(statCell(R.drawable.ic_stat_clock,
                newestCount >= 0 ? StoreViews.formatStars(newestCount) : "–",
                getString(R.string.store_newest),
                true, true, v -> showNewest()));
        second.addView(statGap());
        second.addView(statCell(R.drawable.ic_stat_chart,
                StoreViews.formatStars(ranks), getString(R.string.store_stat_ranks),
                true, false, v -> selectTab(TAB_RANK)));
        rows.addView(second);
        return rows;
    }

    /** 统计块两行内两项之间的间距（设计稿 42px）。 */
    private View statGap() {
        View gap = new View(mContext);
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                StoreViews.dp(mContext, 42), 0));
        return gap;
    }

    /** 统计块一格：图标＋粗体数字＋灰字标签；link 时标签下加虚线下划线。
     * isNewest 时把数字 TextView 记到 mNewestCountView，供最新入库数据到
     * 达时只改数字（见 loadNewest）。 */
    private View statCell(int iconRes, String number, String label,
            boolean link, boolean isNewest, View.OnClickListener listener) {
        LinearLayout cell = new LinearLayout(mContext);
        cell.setOrientation(HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(0, StoreViews.dp(mContext, 7), 0, StoreViews.dp(mContext, 7));
        ImageView icon = new ImageView(mContext);
        icon.setImageResource(iconRes);
        icon.setColorFilter(StoreViews.primaryText(mContext), PorterDuff.Mode.SRC_IN);
        icon.setLayoutParams(new LinearLayout.LayoutParams(
                StoreViews.dp(mContext, 20), StoreViews.dp(mContext, 20)));
        cell.addView(icon);
        View gap = new View(mContext);
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                StoreViews.dp(mContext, 7), 0));
        cell.addView(gap);
        TextView num = StoreViews.text(mContext, 20, true);
        num.setText(number);
        cell.addView(num);
        if (isNewest) {
            mNewestCountView = num;
        }
        View gap2 = new View(mContext);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(
                StoreViews.dp(mContext, 6), 0));
        cell.addView(gap2);
        LinearLayout labelWrap = new LinearLayout(mContext);
        labelWrap.setOrientation(VERTICAL);
        TextView lbl = StoreViews.text(mContext, 13, false);
        lbl.setText(label);
        lbl.setTextColor(StoreViews.secondaryText(mContext));
        labelWrap.addView(lbl);
        if (link) {
            // 0.1.67：虚线宽度跟标签一致。之前这里用 MATCH_PARENT，在 WRAP_CONTENT
            // 的 labelWrap 里会把格子撑到一屏宽，第二行总宽约两屏，排行榜格子被顶出
            // 屏幕外。标签单行，直接用 Paint 量出文本宽，不留 MATCH_PARENT 套 WRAP_CONTENT。
            int labelW = (int) Math.ceil(lbl.getPaint().measureText(label));
            View underline = new View(mContext);
            underline.setBackgroundResource(R.drawable.store_dotted_underline);
            underline.setLayoutParams(new LinearLayout.LayoutParams(
                    labelW, StoreViews.dp(mContext, 1)));
            labelWrap.addView(underline);
        }
        cell.addView(labelWrap);
        if (listener != null) {
            UiUtils.setSelectableBackground(cell);
            cell.setOnClickListener(listener);
        }
        return cell;
    }

    /** 系统／平台图标网格（三列；点格切全局平台，留在当前页重载）。
     * 0.1.59：数量取计数缓存，不再每渲染扫全池。
     * 0.1.69：数字改联动计数（linkedPlatformCounts）——各格 = 该系统且符合
     * 当前用途的数；首页／探索共用同一网格，行为一致。 */
    private LinearLayout platformGrid() {
        ensureCounts();
        int[] counts = linkedPlatformCounts();
        List<StoreViews.IconCell> cells = new ArrayList<>();
        for (int i = 0; i < PLATFORM_KEYS.length; i++) {
            final String platformKey = PLATFORM_KEYS[i];
            cells.add(new StoreViews.IconCell(platformGridIcon(platformKey),
                    platformLabel(platformKey), counts[i],
                    platformKey.equals(mPlatform),
                    v -> applyPlatform(platformKey), platformGridTint(platformKey)));
        }
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        int side = StoreViews.dp(mContext, 16);
        grid.setPadding(side, 0, side, 0);
        return grid;
    }

    /** 排行榜的系统网格（三列；点格只筛榜单池子，不动全局平台）。 */
    private LinearLayout rankPlatformGrid() {
        ensureCounts();
        List<StoreViews.IconCell> cells = new ArrayList<>();
        for (int i = 0; i < PLATFORM_KEYS.length; i++) {
            final String platformKey = PLATFORM_KEYS[i];
            cells.add(new StoreViews.IconCell(platformGridIcon(platformKey),
                    platformLabel(platformKey), mPlatformCounts[i],
                    platformKey.equals(mRankPlatform),
                    v -> applyRankPlatform(platformKey), platformGridTint(platformKey)));
        }
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        int side = StoreViews.dp(mContext, 16);
        grid.setPadding(side, 0, side, 0);
        return grid;
    }

    /** 用途分类图标网格（三列；点格只筛已加载项，留在当前页重绘）。
     * 0.1.59：数量取计数缓存，不再每渲染扫全池。
     * 0.1.69：数字改联动计数（linkedCategoryCounts）——各格 = 该用途且符合
     * 当前系统的数；选 Android 时“全部”格即 718，不再是全池 2450。 */
    private LinearLayout categoryGrid() {
        ensureCounts();
        int[] counts = linkedCategoryCounts();
        List<StoreViews.IconCell> cells = new ArrayList<>();
        for (int i = 0; i < FEED_CATS.length; i++) {
            final String category = FEED_CATS[i];
            cells.add(new StoreViews.IconCell(categoryIcon(category),
                    getString(FEED_CAT_TITLES[i]), counts[i],
                    category.equals(mCategory),
                    v -> applyCategory(category), StoreViews.TINT_AUTO));
        }
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        int side = StoreViews.dp(mContext, 16);
        grid.setPadding(side, 0, side, 0);
        return grid;
    }

    private int platformGridIcon(String key) {
        switch (key) {
            case "android":
                return R.drawable.ic_store_android;
            case "ios":
                return R.drawable.ic_store_ios;
            case "windows":
                return R.drawable.ic_store_windows;
            case "macos":
                return R.drawable.ic_store_apple;
            case "linux":
                return R.drawable.ic_store_linux;
            case "harmony":
                return R.drawable.ic_store_harmony;
            case "extension":
                return R.drawable.ic_store_extension;
            case PLAT_OTHER:
                return R.drawable.ic_store_other;
            default:
                return R.drawable.ic_store_grid;
        }
    }

    /** 网格图标着色：Android 机器人常绿（品牌色），其余随选中态。
     * 0.1.66：企鹅换成 hiwiki sprite 的单色剪影，随选中态着色（不再免 tint）。 */
    private int platformGridTint(String key) {
        if ("android".equals(key)) {
            return StoreViews.TINT_BRAND;
        }
        return StoreViews.TINT_AUTO;
    }

    private int categoryIcon(String category) {
        switch (category) {
            case "ai":
                return R.drawable.ic_store_cat_ai;
            case "development":
                // 0.2.2：开发不再与 AI 共用图标——用现成的代码标签图标（md_code）。
                return R.drawable.md_code;
            case "media":
                return R.drawable.ic_store_cat_media;
            case "tools":
                // 0.2.2：工具不再与媒体共用图标——用现成的齿轮图标（icon_settings）。
                return R.drawable.icon_settings;
            case "network":
                return R.drawable.ic_store_cat_network;
            case "productivity":
                return R.drawable.ic_store_cat_efficiency;
            case "game":
                return R.drawable.ic_store_cat_game;
            case "other":
                return R.drawable.ic_store_cat_other;
            default:
                return R.drawable.ic_store_cat_media;
        }
    }

    // ---------- 探索 ----------

    // ---------- 标签云 ----------

    /** 标签云页签：详情态进标签列表，云态显示标签云。 */
    private void renderTags() {
        if (mTagDetail != null) {
            renderTagDetail();
            return;
        }
        renderTagCloud();
    }

    /** 标签云（默认只展示项目数超 10 个的热门标签；框内搜全部标签）。 */
    private void renderTagCloud() {
        LinearLayout header = mTabHeaderContainers[TAB_TAGS];
        header.setVisibility(VISIBLE);
        header.removeAllViews();
        mTabListContainers[TAB_TAGS].removeAllViews();
        ensureTagCounts();
        int side = StoreViews.dp(mContext, 16);

        if (mFeedMirror) {
            header.addView(mirrorStrip());
        } else if (mFeedOffline) {
            header.addView(offlineStrip());
        }

        int hot = 0;
        for (TagCount tag : mTagCounts) {
            if (tag.count > TAG_HOT_MIN) {
                hot++;
            }
        }
        TextView stats = StoreViews.text(mContext, 14, false);
        stats.setText(getString(R.string.store_tags_stats, hot, mTagCounts.size()));
        stats.setTextColor(StoreViews.secondaryText(mContext));
        stats.setPadding(side, StoreViews.dp(mContext, 14), side, 0);
        header.addView(stats);

        // 搜索框：输入只重建云的行，不重建整个头（否则输入时丢焦点）。
        EditText search = new EditText(mContext);
        search.setHint(R.string.store_tags_search_hint);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setSingleLine(true);
        search.setText(mTagQuery);
        search.setPadding(side, StoreViews.dp(mContext, 10),
                side, StoreViews.dp(mContext, 10));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(side, StoreViews.dp(mContext, 8), side, 0);
        search.setLayoutParams(searchLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mTagQuery = s.toString();
                rebuildTagCloudRows();
            }
        });
        header.addView(search);

        TextView note = StoreViews.text(mContext, 13, false);
        note.setText(R.string.store_tags_note);
        note.setTextColor(StoreViews.secondaryText(mContext));
        note.setPadding(side, StoreViews.dp(mContext, 8), side, 0);
        header.addView(note);

        mTagCloudRows = new LinearLayout(mContext);
        mTagCloudRows.setOrientation(LinearLayout.VERTICAL);
        mTagCloudRows.setPadding(side, StoreViews.dp(mContext, 10), side,
                StoreViews.dp(mContext, 16));
        header.addView(mTagCloudRows);
        rebuildTagCloudRows();
    }

    /** 云的行：按收录数取当前要展示的标签（空词只看热门，有词全库搜），
     * 只重建行容器，搜索框本身不动（保焦点）。 */
    private void rebuildTagCloudRows() {
        if (mTagCloudRows == null) {
            return;
        }
        mTagCloudRows.removeAllViews();
        List<TagCount> tags = tagCloudTags();
        if (tags.isEmpty()) {
            mTagCloudRows.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_tags_empty)));
            return;
        }
        int avail = tagCloudAvailWidth();
        LinearLayout row = newTagRow();
        int rowWidth = 0;
        for (TagCount tag : tags) {
            int w = measureTagView(tag);
            if (rowWidth > 0 && rowWidth + w > avail) {
                mTagCloudRows.addView(row);
                row = newTagRow();
                rowWidth = 0;
            }
            row.addView(buildTagView(tag));
            rowWidth += w;
        }
        if (row.getChildCount() > 0) {
            mTagCloudRows.addView(row);
        }
    }

    /** 当前要展示的标签：空词只看热门（收录数 > 10），有词在全库里按名字搜，
     * 都按收录数降序；搜的结果最多 300 个（云是浏览用的，不一次全摊开）。 */
    private List<TagCount> tagCloudTags() {
        ensureTagCounts();
        List<TagCount> out = new ArrayList<>();
        String query = mTagQuery.trim().toLowerCase(Locale.ROOT);
        for (TagCount tag : mTagCounts) {
            if (query.isEmpty()) {
                if (tag.count > TAG_HOT_MIN) {
                    out.add(tag);
                }
            } else if (tag.name.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(tag);
            }
            if (out.size() >= 300) {
                break;
            }
        }
        return out;
    }

    /** 标签计数：feed 条目 topics 聚合，收录数降序、名字升序；feed 未到不缓存。 */
    private void ensureTagCounts() {
        if (mTagCountsReady) {
            return;
        }
        if (mFeedData == null) {
            mTagCounts = new ArrayList<>();
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (StoreRepo item : pool()) {
            for (String topic : item.topics) {
                if (TextUtils.isEmpty(topic)) {
                    continue;
                }
                Integer n = counts.get(topic);
                counts.put(topic, n == null ? 1 : n + 1);
            }
        }
        List<TagCount> tags = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            tags.add(new TagCount(e.getKey(), e.getValue()));
        }
        Collections.sort(tags, (a, b) -> {
            int byCount = Integer.compare(b.count, a.count);
            return byCount != 0 ? byCount : a.name.compareToIgnoreCase(b.name);
        });
        mTagCounts = tags;
        mTagCountsReady = true;
    }

    /** 云的一行：横向居中，标签按实测宽度贪心换行。 */
    private LinearLayout newTagRow() {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    /** 云一行可用的宽度：头容器实测宽（未布局时回落屏幕宽）减两侧边距。 */
    private int tagCloudAvailWidth() {
        int width = mTabHeaderContainers[TAB_TAGS].getWidth();
        if (width <= 0) {
            width = mContext.getResources().getDisplayMetrics().widthPixels;
        }
        return Math.max(0, width - StoreViews.dp(mContext, 32));
    }

    /** 标签字号档（收录数越大字越大，与 hiwiki 标签云同款按量分档）。 */
    private static int tagSizeSp(int count) {
        if (count >= 200) {
            return 26;
        }
        if (count >= 100) {
            return 22;
        }
        if (count >= 80) {
            return 19;
        }
        if (count >= 60) {
            return 17;
        }
        return 15;
    }

    /** 云里的一个标签：名字按档放大＋收录数小灰字，点进该标签的详情列表。 */
    private View buildTagView(final TagCount tag) {
        int sizeSp = tagSizeSp(tag.count);
        TextView view = new TextView(mContext);
        String countText = " " + tag.count;
        SpannableString text = new SpannableString(tag.name + countText);
        text.setSpan(new AbsoluteSizeSpan(sizeSp, true), 0, tag.name.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new AbsoluteSizeSpan(12, true), tag.name.length(), text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(StoreViews.secondaryText(mContext)),
                tag.name.length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
        view.setTextColor(tag.count >= 100
                ? StoreViews.primaryColor(mContext) : StoreViews.primaryText(mContext));
        int side = StoreViews.dp(mContext, 10);
        view.setPadding(side, StoreViews.dp(mContext, 6),
                side, StoreViews.dp(mContext, 6));
        UiUtils.setSelectableBackground(view);
        view.setOnClickListener(v -> showTagDetail(tag.name));
        return view;
    }

    /** 云里一个标签占的宽：名字＋收录数字的实测宽＋两侧内边距（与换行同口径）。 */
    private int measureTagView(TagCount tag) {
        float density = mContext.getResources().getDisplayMetrics().density;
        float scaledDensity = mContext.getResources().getDisplayMetrics().scaledDensity;
        Paint namePaint = new Paint();
        namePaint.setTextSize(tagSizeSp(tag.count) * scaledDensity);
        Paint countPaint = new Paint();
        countPaint.setTextSize(12 * scaledDensity);
        float w = namePaint.measureText(tag.name)
                + countPaint.measureText(" " + tag.count)
                + 20 * density + 4 * density;
        return (int) Math.ceil(w);
    }

    /** 点标签进详情：该标签的仓库按星标排，20 条一页。 */
    private void showTagDetail(String tag) {
        mTagDetail = tag;
        mTagShown = PAGE_SIZE;
        renderTags();
    }

    /** 标签详情（子屏，不占页签）：回云＋标题＋条数＋仓库行。 */
    private void renderTagDetail() {
        LinearLayout header = mTabHeaderContainers[TAB_TAGS];
        header.setVisibility(GONE);
        LinearLayout list = mTabListContainers[TAB_TAGS];
        list.removeAllViews();
        int side = StoreViews.dp(mContext, 16);

        TextView back = StoreViews.text(mContext, 14, true);
        back.setText(R.string.store_tags_back);
        back.setTextColor(StoreViews.primaryColor(mContext));
        back.setPadding(side, StoreViews.dp(mContext, 14), side, 0);
        UiUtils.setSelectableBackground(back);
        back.setOnClickListener(v -> {
            mTagDetail = null;
            renderTags();
        });
        list.addView(back);

        List<StoreRepo> visible = tagDetailRepos();
        mTagVisibleSize = visible.size();
        TextView title = StoreViews.text(mContext, 18, true);
        title.setText(mTagDetail);
        title.setPadding(side, StoreViews.dp(mContext, 8), side, 0);
        list.addView(title);
        TextView sub = StoreViews.text(mContext, 13, false);
        sub.setText(getString(R.string.store_tags_detail_sub, visible.size()));
        sub.setTextColor(StoreViews.secondaryText(mContext));
        sub.setPadding(side, StoreViews.dp(mContext, 2), side, StoreViews.dp(mContext, 8));
        list.addView(sub);
        TextView count = StoreViews.text(mContext, 13, false);
        count.setText(getString(R.string.store_result_count, visible.size()));
        count.setTextColor(StoreViews.secondaryText(mContext));
        count.setPadding(side, StoreViews.dp(mContext, 4), side, StoreViews.dp(mContext, 4));
        list.addView(count);

        if (visible.isEmpty()) {
            list.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
            return;
        }
        int limit = Math.min(mTagShown, visible.size());
        for (int i = 0; i < limit; i++) {
            list.addView(rowView(visible.get(i)));
            list.addView(StoreViews.divider(mContext));
        }
        if (mTagShown >= visible.size()) {
            TextView end = StoreViews.text(mContext, 13, false);
            end.setText(R.string.store_all_loaded);
            end.setTextColor(StoreViews.secondaryText(mContext));
            end.setGravity(Gravity.CENTER);
            end.setPadding(side, StoreViews.dp(mContext, 14),
                    side, StoreViews.dp(mContext, 18));
            list.addView(end);
        }
        ensureTagFillViewport();
    }

    /** 该标签的仓库（topics 含该标签），按星标降序。 */
    private List<StoreRepo> tagDetailRepos() {
        List<StoreRepo> out = new ArrayList<>();
        if (mTagDetail == null) {
            return out;
        }
        for (StoreRepo item : pool()) {
            if (item.topics.contains(mTagDetail)) {
                out.add(item);
            }
        }
        sortForExplore(out, SORT_STARS);
        return out;
    }

    /** 标签详情塞不满一屏时自动追加，直到能滚动或全部展完。 */
    private void ensureTagFillViewport() {
        ScrollView scroll = mTabScrolls[TAB_TAGS];
        if (scroll == null) {
            return;
        }
        scroll.post(() -> {
            if (mFeedData == null || mCurrentTab != TAB_TAGS || mTagDetail == null) {
                return;
            }
            if (!scroll.canScrollVertically(1) && mTagShown < mTagVisibleSize) {
                mTagShown += PAGE_SIZE;
                renderTagDetail();
            }
        });
    }

    /** 0.2.2：完整榜单塞不满一屏时自动追加，直到能滚动或全部展完。 */
    private void ensureRankFillViewport() {
        ScrollView scroll = mTabScrolls[TAB_RANK];
        if (scroll == null) {
            return;
        }
        scroll.post(() -> {
            if (mFeedData == null || mCurrentTab != TAB_RANK) {
                return;
            }
            if (mRankGroup < 0 || mRankList < 0) {
                return;
            }
            if (!scroll.canScrollVertically(1) && mRankShown < mRankFullTotal) {
                mRankShown += PAGE_SIZE;
                renderRank();
            }
        });
    }

    private TextView filterLabel(String label) {
        TextView view = StoreViews.text(mContext, 13, true);
        view.setText(label);
        view.setTextColor(StoreViews.secondaryText(mContext));
        int side = StoreViews.dp(mContext, 16);
        view.setPadding(side, StoreViews.dp(mContext, 10), side, StoreViews.dp(mContext, 4));
        return view;
    }

    /** 探索的内存内筛选＋排序：平台＋分类筛选都在这里算。 */
    private List<StoreRepo> exploreFiltered(List<StoreRepo> pool) {
        List<StoreRepo> out = new ArrayList<>();
        for (StoreRepo item : pool) {
            if (!matchesPlatform(item, mPlatform)) {
                continue;
            }
            if (!CAT_ALL.equals(mCategory) && !mCategory.equals(item.categoryOrOther())) {
                continue;
            }
            // 0.1.67：近期热门不再按 7 天过滤。hiwiki 探索页的近期热门只是对全池子
            // 的排序（总数 2457 不随排序变），7 天分档是 /zh-CN/recent“最近更新”
            // 页的事。这里删掉过滤，全池子按 compareHot 排；“共 X 条”即全池子。
            out.add(item);
        }
        sortForExplore(out, mSort);
        return out;
    }

    // ---------- 排行榜 ----------

    private void renderRank() {
        LinearLayout container = mTabContainers[TAB_RANK];
        container.removeAllViews();
        if (mFeedMirror) {
            container.addView(mirrorStrip());
        } else if (mFeedOffline) {
            container.addView(offlineStrip());
        }
        if (mRankGroup >= 0 && mRankList >= 0) {
            renderRankFullList(container);
            return;
        }
        renderRankOverview(container);
    }

    private void renderRankOverview(LinearLayout container) {
        List<StoreRankGroups.Group> groups = rankGroups();
        TextView title = StoreViews.text(mContext, 18, true);
        title.setText(R.string.store_rank_title);
        int side = StoreViews.dp(mContext, 16);
        title.setPadding(side, StoreViews.dp(mContext, 16), side, 0);
        container.addView(title);
        TextView sub = StoreViews.text(mContext, 13, false);
        sub.setText(R.string.store_rank_sub);
        sub.setTextColor(StoreViews.secondaryText(mContext));
        sub.setPadding(side, StoreViews.dp(mContext, 2), side, StoreViews.dp(mContext, 8));
        container.addView(sub);
        // 0.1.61：排行榜的系统筛选（同款图标网格；先筛池子再分组榜）。
        container.addView(filterLabel(getString(R.string.store_filter_platform)));
        container.addView(rankPlatformGrid());
        // 0.1.60：只看有收录的榜单（默认不勾选；勾选隐藏空卡片，状态持久化）。
        // 0.1.61：CheckBox 的按钮画在视图左沿、无视左 padding（之前设了 16dp
        // padding 也贴边），改用外边距对齐 16dp 内容边距。
        CheckBox onlyNonEmpty = new CheckBox(mContext);
        onlyNonEmpty.setText(R.string.store_rank_only_nonempty);
        onlyNonEmpty.setChecked(mRankOnlyNonEmpty);
        onlyNonEmpty.setOnCheckedChangeListener((button, checked) -> {
            mRankOnlyNonEmpty = checked;
            StorePrefs.setRankOnlyNonEmpty(mContext, checked);
            // 0.2.2：榜单池子换了，完整榜单回到第 1 页。
            mRankShown = PAGE_SIZE;
            renderRank();
        });
        LinearLayout.LayoutParams onlyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        onlyLp.setMargins(side, 0, side, 0);
        container.addView(onlyNonEmpty, onlyLp);

        boolean anyVisible = false;
        for (int gi = 0; gi < groups.size(); gi++) {
            StoreRankGroups.Group group = groups.get(gi);
            List<StoreRankGroups.RankList> kept = new ArrayList<>();
            for (StoreRankGroups.RankList list : group.lists) {
                if (!mRankOnlyNonEmpty || !list.items.isEmpty()) {
                    kept.add(list);
                }
            }
            if (kept.isEmpty()) {
                continue;
            }
            anyVisible = true;
            int filled = 0;
            for (StoreRankGroups.RankList list : group.lists) {
                if (!list.items.isEmpty()) {
                    filled++;
                }
            }
            TextView header = StoreViews.text(mContext, 16, true);
            header.setText(group.name);
            header.setPadding(side, StoreViews.dp(mContext, 14), side, 0);
            container.addView(header);
            TextView gnote = StoreViews.text(mContext, 13, false);
            gnote.setText(getString(R.string.store_rank_group_sub,
                    group.lists.size(), filled));
            gnote.setTextColor(StoreViews.secondaryText(mContext));
            gnote.setPadding(side, StoreViews.dp(mContext, 2), side, StoreViews.dp(mContext, 8));
            container.addView(gnote);
            container.addView(rankCardGrid(gi, group, kept));
        }
        if (!anyVisible) {
            container.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
        }
    }

    /** 排行榜小类卡片（两列；卡片 = 榜名 + 收录数，点进完整榜单）。
     * 0.1.66：去掉“第 1 名”那行，只留榜名＋收录数两行。 */
    private LinearLayout rankCardGrid(int gi, StoreRankGroups.Group group,
            List<StoreRankGroups.RankList> kept) {
        LinearLayout grid = new LinearLayout(mContext);
        grid.setOrientation(LinearLayout.VERTICAL);
        int side = StoreViews.dp(mContext, 16);
        grid.setPadding(side, StoreViews.dp(mContext, 4), side, StoreViews.dp(mContext, 8));
        LinearLayout row = null;
        for (int i = 0; i < kept.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(mContext);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            final StoreRankGroups.RankList list = kept.get(i);
            final int li = group.lists.indexOf(list);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int gap = StoreViews.dp(mContext, 5);
            lp.setMargins(gap, gap, gap, gap);
            row.addView(rankCard(gi, li, group, list), lp);
            if (i % 2 == 0 && i == kept.size() - 1) {
                // 奇数个卡片时末行补一个空位，保持两列对齐。
                View spacer = new View(mContext);
                row.addView(spacer, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
        }
        return grid;
    }

    private LinearLayout rankCard(int gi, int li, StoreRankGroups.Group group,
            StoreRankGroups.RankList list) {
        LinearLayout card = new LinearLayout(mContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(StoreViews.dp(mContext, 12), StoreViews.dp(mContext, 12),
                StoreViews.dp(mContext, 12), StoreViews.dp(mContext, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(StoreViews.dp(mContext, 12));
        bg.setStroke(StoreViews.dp(mContext, 1), StoreViews.dividerColor(mContext));
        bg.setColor(StoreViews.backgroundColor(mContext));
        card.setBackground(bg);
        card.setForeground(StoreViews.rippleDrawable(mContext));
        card.setClickable(true);
        final int groupIndex = gi;
        final int listIndex = li;
        card.setOnClickListener(v -> {
            mRankGroup = groupIndex;
            mRankList = listIndex;
            renderRank();
        });
        TextView name = StoreViews.text(mContext, 15, true);
        name.setText(list.name);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name);
        TextView count = StoreViews.text(mContext, 13, false);
        count.setText(getString(R.string.store_rank_list_count, list.items.size()));
        count.setTextColor(StoreViews.secondaryText(mContext));
        card.addView(count);
        return card;
    }

    /** 完整榜单：同一榜单的完整数组就地展开，按星标排；
     *（0.1.68：去掉 24 条上限，数据本身全在，不再截断；
     * 0.2.2：分页展示，20 条/页，滑到底自动追加）。 */
    private void renderRankFullList(LinearLayout container) {
        List<StoreRankGroups.Group> groups = rankGroups();
        if (mRankGroup < 0 || mRankGroup >= groups.size()) {
            mRankGroup = -1;
            mRankList = -1;
            renderRankOverview(container);
            return;
        }
        StoreRankGroups.Group group = groups.get(mRankGroup);
        if (mRankList < 0 || mRankList >= group.lists.size()) {
            mRankGroup = -1;
            mRankList = -1;
            renderRankOverview(container);
            return;
        }
        StoreRankGroups.RankList list = group.lists.get(mRankList);
        int side = StoreViews.dp(mContext, 16);
        TextView back = StoreViews.text(mContext, 14, true);
        back.setText(R.string.store_back);
        back.setTextColor(StoreViews.primaryColor(mContext));
        back.setPadding(side, StoreViews.dp(mContext, 14), side, 0);
        UiUtils.setSelectableBackground(back);
        back.setOnClickListener(v -> {
            mRankGroup = -1;
            mRankList = -1;
            // 0.2.2：回到概览后重进同一榜单也要从第 1 页开始。
            mRankShownGroup = -1;
            mRankShownList = -1;
            renderRank();
        });
        container.addView(back);
        TextView title = StoreViews.text(mContext, 18, true);
        title.setText(list.name);
        title.setPadding(side, StoreViews.dp(mContext, 8), side, 0);
        container.addView(title);
        List<StoreRepo> rows = list.items;
        // 0.2.2：换了榜单（组/榜下标变了）回到第 1 页；同榜内追加不重置。
        if (mRankGroup != mRankShownGroup || mRankList != mRankShownList) {
            mRankShown = PAGE_SIZE;
            mRankShownGroup = mRankGroup;
            mRankShownList = mRankList;
        }
        mRankFullTotal = rows.size();
        // 0.1.68：副标题照设计稿“收录 N 条 · 按星标从高到低（后端给榜）”；
        // 旧数据（本地分组）不带括号。一次格式化，不拼接（SetTextI18n）。
        String subText = getString(R.string.store_rank_full_sub, rows.size(),
                rankGroupsFromBackend() ? getString(R.string.store_rank_backend) : "");
        TextView sub = StoreViews.text(mContext, 13, false);
        sub.setText(subText);
        sub.setTextColor(StoreViews.secondaryText(mContext));
        sub.setPadding(side, StoreViews.dp(mContext, 2), side, StoreViews.dp(mContext, 8));
        container.addView(sub);
        if (rows.isEmpty()) {
            container.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
            return;
        }
        // 0.2.2：完整榜单分页（20 条/页），不再一次建全部行（最多 195 行）；
        // 滑到底由排行榜 ScrollView 的 OnScrollChangeListener 自动追加。
        int limit = Math.min(mRankShown, rows.size());
        for (int i = 0; i < limit; i++) {
            container.addView(rankRowView(i + 1, rows.get(i)));
            container.addView(StoreViews.divider(mContext));
        }
        // 全部展完才显示底栏（与探索页一致）。
        if (mRankShown >= rows.size()) {
            TextView end = StoreViews.text(mContext, 13, false);
            end.setText(R.string.store_all_loaded);
            end.setTextColor(StoreViews.secondaryText(mContext));
            end.setGravity(Gravity.CENTER);
            end.setPadding(side, StoreViews.dp(mContext, 14),
                    side, StoreViews.dp(mContext, 18));
            container.addView(end);
        }
        // 0.2.2：大屏/横屏下 20 条可能塞不满一屏、滑不到底就触发不了自动
        // 加载；渲染后若内容仍不可滚动且还有未展示的条目，再追加一页。
        ensureRankFillViewport();
    }

    /** 完整榜单的行：名次＋名＋简介＋★／语言／版本＋平台图标（设计稿样式）。 */
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
        if (!TextUtils.isEmpty(item.description)) {
            TextView desc = StoreViews.text(mContext, 14, false);
            desc.setText(item.description);
            desc.setTextColor(StoreViews.secondaryText(mContext));
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(desc);
        }
        String language = item.language != null ? item.language : getString(R.string.unknown);
        TextView subView = StoreViews.text(mContext, 13, false);
        subView.setText("★ " + StoreViews.formatStars(item.stars())
                + " · " + language
                + (item.version().isEmpty() ? "" : " · " + item.version()));
        subView.setTextColor(StoreViews.secondaryText(mContext));
        texts.addView(subView);
        if (!item.platforms.isEmpty()) {
            LinearLayout icons = new LinearLayout(mContext);
            icons.setOrientation(LinearLayout.HORIZONTAL);
            icons.setPadding(0, StoreViews.dp(mContext, 8), 0, 0);
            boolean first = true;
            for (StoreClassifier.Platform platform : item.platforms) {
                if (!first) {
                    View gap = new View(mContext);
                    gap.setLayoutParams(new LinearLayout.LayoutParams(
                            StoreViews.dp(mContext, 10), 0));
                    icons.addView(gap);
                }
                first = false;
                icons.addView(StoreViews.platformIcon(mContext, platform));
            }
            texts.addView(icons);
        }
        row.addView(texts);
        UiUtils.setSelectableBackground(row);
        row.setOnClickListener(v -> mContext.startActivity(
                RepositoryActivity.makeIntent(mContext, item.owner(), item.name())));
        row.setOnLongClickListener(v -> {
            showRowMenu(item);
            return true;
        });
        return row;
    }

    // ---------- 最新入库 ----------

    /** 打开最新入库子屏（首页统计块“最新入库”入口）。 */
    private void showNewest() {
        mShowingNewest = true;
        mRankGroup = -1;
        mRankList = -1;
        updateTabStyles();
        for (int i = 0; i < TAB_COUNT; i++) {
            mTabScrolls[i].setVisibility(GONE);
        }
        mNewestScroll.setVisibility(VISIBLE);
        if (mNewestData != null) {
            renderNewestList();
        } else {
            loadNewest();
        }
    }

    private void loadNewestIfNeeded() {
        if (mNewestData != null || mNewestLoading) {
            return;
        }
        loadNewest();
    }

    private void loadNewest() {
        if (mNewestLoading) {
            return;
        }
        mNewestLoading = true;
        mNewestFailed = false;
        if (mShowingNewest) {
            mNewestContainer.removeAllViews();
            mNewestContainer.addView(StoreViews.infoText(mContext,
                    getString(R.string.loading_msg)));
        }
        mDisposables.add(StoreApi.loadNewest(mContext)
                .subscribe(data -> {
                    mNewestData = data;
                    mNewestLoading = false;
                    mNewestFailed = false;
                    DiagTrace.log(mContext, "STORE", "newest count=" + data.items.size()
                            + " offline=" + data.offline
                            + " mirror=" + data.mirror);
                    if (mShowingNewest) {
                        renderNewestList();
                    } else if (mCurrentTab == TAB_HOME && mFeedData != null) {
                        // 0.2.2：统计块的最新入库数字到了，只改那一格的数字，
                        // 不重建整个首页头（之前调 renderHomeHeader()，滚动时
                        // 会抖）。不在首页时什么也不做，切回首页重绘时会带上
                        // 新数字（renderTabFromCache）。
                        if (mNewestCountView != null) {
                            mNewestCountView.setText(
                                    StoreViews.formatStars(data.items.size()));
                        }
                    }
                }, error -> {
                    mNewestLoading = false;
                    mNewestFailed = true;
                    DiagTrace.log(mContext, "STORE",
                            "newest fail " + StoreApi.describeError(error));
                    if (mShowingNewest) {
                        renderNewestList();
                    }
                }));
    }

    /** 最新入库列表（60 条，按入库时间排；文件未发布时显示失败重试，绝不崩溃）。 */
    private void renderNewestList() {
        LinearLayout container = mNewestContainer;
        container.removeAllViews();
        int side = StoreViews.dp(mContext, 16);
        TextView back = StoreViews.text(mContext, 14, true);
        back.setText(R.string.store_newest_back);
        back.setTextColor(StoreViews.primaryColor(mContext));
        back.setPadding(side, StoreViews.dp(mContext, 14), side, 0);
        UiUtils.setSelectableBackground(back);
        back.setOnClickListener(v -> {
            mShowingNewest = false;
            selectTab(TAB_HOME);
        });
        container.addView(back);
        TextView title = StoreViews.text(mContext, 18, true);
        title.setText(R.string.store_newest);
        title.setPadding(side, StoreViews.dp(mContext, 8), side, 0);
        container.addView(title);
        if (mNewestFailed || mNewestData == null) {
            TextView errorView = StoreViews.infoText(mContext,
                    getString(R.string.store_fetch_failed));
            UiUtils.setSelectableBackground(errorView);
            errorView.setOnClickListener(v -> {
                mNewestData = null;
                mNewestFailed = false;
                loadNewest();
            });
            container.addView(errorView);
            return;
        }
        List<StoreRepo> items = newestPool();
        TextView sub = StoreViews.text(mContext, 13, false);
        // B-L3：副标题与结果数用同一口径（筛选后条数），之前副标题是未筛的 60。
        sub.setText(mContext.getResources().getQuantityString(
                R.plurals.store_newest_sub, items.size(),
                items.size()));
        sub.setTextColor(StoreViews.secondaryText(mContext));
        sub.setPadding(side, StoreViews.dp(mContext, 2), side, StoreViews.dp(mContext, 8));
        container.addView(sub);
        TextView count = StoreViews.text(mContext, 13, false);
        count.setText(getString(R.string.store_result_count, items.size()));
        count.setTextColor(StoreViews.secondaryText(mContext));
        count.setPadding(side, StoreViews.dp(mContext, 4), side, StoreViews.dp(mContext, 4));
        container.addView(count);
        // 0.1.68：最新入库的系统筛选（与排行榜同款 9 格；只筛这 60 条）。
        container.addView(filterLabel(getString(R.string.store_filter_platform)));
        container.addView(newestPlatformGrid());
        if (items.isEmpty()) {
            container.addView(StoreViews.infoText(mContext,
                    getString(R.string.store_empty_result)));
            return;
        }
        for (StoreRepo item : items) {
            container.addView(newestRowView(item));
            container.addView(StoreViews.divider(mContext));
        }
    }

    /** 最新入库池子（系统筛选后，顺序保持 newest.json 的入库顺序，不重排）。 */
    private List<StoreRepo> newestPool() {
        List<StoreRepo> out = new ArrayList<>();
        if (mNewestData == null) {
            return out;
        }
        for (StoreRepo item : mNewestData.items) {
            // 0.1.68：最新入库的系统筛选（与排行榜同款网格，只筛这 60 条）。
            if (!matchesPlatform(item, mNewestPlatform)) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    /** 最新入库 60 条内的各平台计数（网格数字；不筛平台）。 */
    private int[] newestPlatformCounts() {
        int[] counts = new int[PLATFORM_KEYS.length];
        if (mNewestData == null) {
            return counts;
        }
        for (StoreRepo item : mNewestData.items) {
            for (int i = 0; i < PLATFORM_KEYS.length; i++) {
                if (matchesPlatform(item, PLATFORM_KEYS[i])) {
                    counts[i]++;
                }
            }
        }
        return counts;
    }

    /** 最新入库的系统网格（与排行榜 rankPlatformGrid 同款 9 格；数字按这 60 条算）。 */
    private LinearLayout newestPlatformGrid() {
        int[] counts = newestPlatformCounts();
        List<StoreViews.IconCell> cells = new ArrayList<>();
        for (int i = 0; i < PLATFORM_KEYS.length; i++) {
            final String platformKey = PLATFORM_KEYS[i];
            cells.add(new StoreViews.IconCell(platformGridIcon(platformKey),
                    platformLabel(platformKey), counts[i],
                    platformKey.equals(mNewestPlatform),
                    v -> applyNewestPlatform(platformKey), platformGridTint(platformKey)));
        }
        LinearLayout grid = StoreViews.iconGrid(mContext, cells);
        int side = StoreViews.dp(mContext, 16);
        grid.setPadding(side, 0, side, 0);
        return grid;
    }

    /** 最新入库的系统网格：只筛这 60 条，不动全局平台，状态持久化。 */
    private void applyNewestPlatform(String key) {
        if (key.equals(mNewestPlatform)) {
            return;
        }
        mNewestPlatform = key;
        StorePrefs.setNewestPlatform(mContext, key);
        renderNewestList();
    }

    /** 最新入库的行：名＋简介＋★／语言／版本＋平台图标＋“作者更新于 X 天前”。 */
    private View newestRowView(final StoreRepo item) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.VERTICAL);
        int side = StoreViews.dp(mContext, 16);
        row.setPadding(side, StoreViews.dp(mContext, 12), side, StoreViews.dp(mContext, 12));
        TextView name = StoreViews.text(mContext, 16, true);
        name.setText(item.fullName());
        row.addView(name);
        if (!TextUtils.isEmpty(item.description)) {
            TextView desc = StoreViews.text(mContext, 14, false);
            desc.setText(item.description);
            desc.setTextColor(StoreViews.secondaryText(mContext));
            desc.setMaxLines(2);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(desc);
        }
        // 设计稿：星标为 0 时不显示“★ 0”，语言为空时显示“—”。
        StringBuilder meta = new StringBuilder();
        if (item.stars() > 0) {
            meta.append("★ ").append(StoreViews.formatStars(item.stars())).append(" · ");
        }
        meta.append(item.language != null && !item.language.isEmpty() ? item.language : "—");
        if (!item.version().isEmpty()) {
            meta.append(" · ").append(item.version());
        }
        TextView metaView = StoreViews.text(mContext, 13, false);
        metaView.setText(meta.toString());
        metaView.setTextColor(StoreViews.secondaryText(mContext));
        row.addView(metaView);
        String updated = newestUpdatedText(item);
        if (!item.platforms.isEmpty() || updated != null) {
            LinearLayout bottom = new LinearLayout(mContext);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            bottom.setPadding(0, StoreViews.dp(mContext, 9), 0, 0);
            LinearLayout icons = new LinearLayout(mContext);
            icons.setOrientation(LinearLayout.HORIZONTAL);
            boolean first = true;
            for (StoreClassifier.Platform platform : item.platforms) {
                if (!first) {
                    View gap = new View(mContext);
                    gap.setLayoutParams(new LinearLayout.LayoutParams(
                            StoreViews.dp(mContext, 10), 0));
                    icons.addView(gap);
                }
                first = false;
                icons.addView(StoreViews.platformIcon(mContext, platform));
            }
            bottom.addView(icons);
            if (updated != null) {
                View spacer = new View(mContext);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                bottom.addView(spacer);
                ImageView clock = new ImageView(mContext);
                clock.setImageResource(R.drawable.ic_stat_clock);
                clock.setColorFilter(StoreViews.secondaryText(mContext), PorterDuff.Mode.SRC_IN);
                clock.setLayoutParams(new LinearLayout.LayoutParams(
                        StoreViews.dp(mContext, 13), StoreViews.dp(mContext, 13)));
                bottom.addView(clock);
                View gap = new View(mContext);
                gap.setLayoutParams(new LinearLayout.LayoutParams(
                        StoreViews.dp(mContext, 4), 0));
                bottom.addView(gap);
                TextView upd = StoreViews.text(mContext, 13, false);
                upd.setText(updated);
                upd.setTextColor(StoreViews.secondaryText(mContext));
                bottom.addView(upd);
            }
            row.addView(bottom);
        }
        UiUtils.setSelectableBackground(row);
        row.setOnClickListener(v -> mContext.startActivity(
                RepositoryActivity.makeIntent(mContext, item.owner(), item.name())));
        row.setOnLongClickListener(v -> {
            showRowMenu(item);
            return true;
        });
        return row;
    }

    /**
     * “作者更新于 X 天前”：按发版／推送时间到今天的天数（向下取整）算；
     * 无时间返回 null。注：hiwiki 显示 aquan-duanju 为 1 天前，App 按
     * lastReleaseTime 算是 2 天前——hiwiki 用的字段 App 没有，不硬凑。
     */
    private String newestUpdatedText(StoreRepo item) {
        if (item.publishedAt <= 0) {
            return null;
        }
        long days = (System.currentTimeMillis() - item.publishedAt)
                / (24L * 60 * 60 * 1000);
        if (days <= 0) {
            return getString(R.string.store_newest_updated_today);
        }
        // B-L4：量词与格式参数统一用 int（先钳制到 int 范围，避免 (int) 截断；
        // %1$d 的实参类型与量词一致）。
        int d = (int) Math.min(days, Integer.MAX_VALUE);
        return mContext.getResources().getQuantityString(
                R.plurals.store_newest_updated_days, d, d);
    }

    // ---------- 通用 ----------

    private View rowView(final StoreRepo item) {
        View row = StoreViews.repoRow(mContext, item, v -> mContext.startActivity(
                RepositoryActivity.makeIntent(mContext, item.owner(), item.name())));
        row.setOnLongClickListener(v -> {
            showRowMenu(item);
            return true;
        });
        return row;
    }

    private void showRowMenu(final StoreRepo item) {
        final String fullName = item.fullName();
        String[] actions = {
            getString(R.string.store_action_share),
            getString(R.string.store_action_open_github),
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
                        default:
                            mContext.startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/" + fullName)));
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

    private String getString(int resId, Object... args) {
        return mContext.getString(resId, args);
    }
}
