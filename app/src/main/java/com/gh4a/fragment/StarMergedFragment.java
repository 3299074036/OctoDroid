package com.gh4a.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.ReleaseInfoActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.UiUtils;
import com.gh4a.utils.UserListDialogHelper;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.service.activity.StarringService;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gh4a.utils.RxUtils;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;

/**
 * 0.1.2 Star 分段 3 页：顶部一段式分段控件（选中段主色填充白字，未选中灰字）
 * 点段即切换到对应独立页，不再单页滚动：①分组（官方 GraphQL 列表，可展开；
 * 全部走官方接口，无本机分组）②列表（全部已加星标，REST 分页，排序菜单复用）
 * ③更新（GraphQL，一次取最近 30 个 Star 仓库的最新 Release，与
 * ReleaseRadarFragment 同一数据源 RadarGraphQL.fetch）。默认页为我的列表。
 * 更新数变化时同步到底栏 Star 角标（HomeActivity.setStarBadge）。
 * 0.1.64 Star 分组：组头 ⋮ 菜单（修改分组名 / 删除分组）、+ 新增分组
 * （名称 + 设为私密开关）、未分组区、长按仓库行多选移组（批量逐个调官方
 * 接口，带进度与失败重试）、长按组头排序（顺序只存本机，按账号记列表 ID
 * 顺序）。
 * 0.1.3（性能）：①官方列表一次嵌套 GraphQL 查询取全量，不再 1+N 串行；
 * ②进页只加载当前页，列表/更新首次切过去才加载，下拉刷新也只刷当前页；
 * ③官方列表按账号本地缓存，进分组先即时显示缓存，后台刷新到后再替换显示。
 * 0.1.7（异步预取）：进页/切页后在后台并行预取其余两页，不阻塞当前页；
 * 预取结果只缓存，切页直接显示，不重复拉取；下拉刷新只刷当前页，刷完
 * 后台再把其余两页重新预取一遍以保持新鲜。
 */
public class StarMergedFragment extends Fragment {
    public static StarMergedFragment newInstance(String login) {
        StarMergedFragment f = new StarMergedFragment();
        Bundle args = new Bundle();
        args.putString("user", login);
        f.setArguments(args);
        return f;
    }

    private static final int TYPE_SECTION = 0;
    private static final int TYPE_LIST = 1;
    private static final int TYPE_LIST_REPO = 2;
    private static final int TYPE_STATUS = 3;
    private static final int TYPE_STARRED_REPO = 4;
    private static final int TYPE_UPDATE = 5;
    // 0.1.64：未分组区头、+ 新增分组、多选顶栏、排序提示、底部操作栏、排序行
    private static final int TYPE_UNGROUPED = 6;
    private static final int TYPE_ADD_GROUP = 7;
    private static final int TYPE_SELECTION_HEADER = 8;
    private static final int TYPE_SORT_HEADER = 9;
    private static final int TYPE_ACTION_BAR = 10;
    private static final int TYPE_SORT_ROW = 11;

    // 0.1.64：底部操作栏种类
    private static final int ACTION_BAR_MOVE = 0;
    private static final int ACTION_BAR_SORT = 1;

    // 0.1.64：组头 ⋮ 菜单项
    private static final int MENU_RENAME = 1;
    private static final int MENU_DELETE = 2;

    private static class Item {
        int type;
        String title;
        String subtitle;
        String key; // list id, for expand state
        boolean expanded;
        Repository repo;
        ReleaseRadarFragment.RadarItem radar;
        String fullName;
        String description;
        boolean markAllRead; // 更新页顶部的“全部已读”行
        boolean retryLists; // 缓存过期提示行：点按重试官方列表
        boolean retryUpdates; // 更新失败行：点按重试更新
        boolean retryStarred; // 列表首屏失败行：点按重试列表
        int actionKind; // TYPE_ACTION_BAR：ACTION_BAR_MOVE / ACTION_BAR_SORT
    }

    private String mLogin;
    private static final int TAB_LISTS = 0;
    private static final int TAB_STARRED = 1;
    private static final int TAB_UPDATES = 2;
    private int mCurrentTab = TAB_LISTS;
    private TextView[] mSegViews;
    // 0.1.16：分段行左右滑改距离判定（见 HorizontalSwipeListener），
    // 不再用 GestureDetector.onFling（慢速拖动不产生 fling，真机上没反应）。
    private RecyclerView mList;
    private SwipeRefreshLayout mSwipe;
    private MergedAdapter mAdapter;
    private final List<Item> mItems = new ArrayList<>();
    private final CompositeDisposable mDisposables = new CompositeDisposable();
    private RepositoryListContainerFragment.SortDrawerHelper mSortHelper;
    private String mSortOrder = "created";
    private String mSortDirection = "desc";

    // Lists state (same semantics as StarredRepositoryListFragment header)
    private List<UserListsGraphQL.UserList> mOfficialLists;
    private boolean mListsFailed;
    private boolean mListsLoaded; // BUG-18：与另两页一致的已加载门控
    private String mExpandedOfficialId;
    private boolean mUngroupedExpanded;
    // 0.1.64：多选模式（长按仓库行进入）与排序模式（长按组头进入）
    private boolean mMultiSelect;
    private final Set<String> mSelectedRepos = new LinkedHashSet<>();
    private boolean mSortMode;
    private List<UserListsGraphQL.UserList> mSortLists;
    private ItemTouchHelper mSortTouchHelper;
    private OnBackPressedCallback mBackCallback;
    // 0.1.64：批量移动进度条（dialog_move_progress.xml 的 pb_move）。
    private ProgressBar mMoveProgressBar;
    // 0.2.2：批量移组状态机（B-M7）——进度与失败存在 ViewModel 里，旋转
    // 屏幕时 Rx 链不中断；两个对话框由 Fragment 按状态重建，视图销毁时拆掉。
    private StarBatchMoveViewModel mMoveViewModel;
    private AlertDialog mMoveProgressDialog;
    private AlertDialog mMoveResultDialog;
    // Starred state
    private final List<Repository> mStarred = new ArrayList<>();
    private int mStarredPage = 1;
    private boolean mStarredLoading;
    private boolean mStarredHasMore = true;
    private boolean mStarredLoaded; // 0.1.3：懒加载，首次切到列表页才置真
    private boolean mStarredFailed; // BUG-3：失败不置 loaded，靠此标志触发切回重试
    // Updates state
    private boolean mListsLoading; // 0.1.7：预取防重复——官方列表正在加载时不再重复拉取
    private boolean mUpdatesLoading; // 0.1.7：预取防重复——更新正在加载时不再重复拉取
    private boolean mRefreshPendingPrefetch; // 0.1.7：下拉刷新完成后要重新预取其余页
    private int mRefreshTab = TAB_LISTS; // 0.1.7：下拉刷新发起时的页签
    private boolean mRefreshActive; // BUG-15：只有发起刷新的页完成时才停转圈
    private boolean mUpdatesLoaded; // 0.1.3：懒加载，首次切到更新页才置真
    private List<ReleaseRadarFragment.RadarItem> mUpdates;
    private java.util.Set<String> mReadUpdateKeys = new java.util.HashSet<>();
    private boolean mUpdatesFailed;

    // BUG-2：每页一个可取消的 Disposable + 世代计数。排序/刷新/归属时先
    // 取消在途请求（dispose 拦住未送达的回调，世代拦住已在主线程排队的
    // 回调），且只有新请求真正开始时才清空列表，旧请求绝不覆盖新状态。
    private io.reactivex.disposables.Disposable mListsDisposable;
    private io.reactivex.disposables.Disposable mStarredDisposable;
    private io.reactivex.disposables.Disposable mUpdatesDisposable;
    private int mListsGeneration;
    private int mStarredGeneration;
    private int mUpdatesGeneration;

    // BUG-17：缓存超过该年龄才算“过期”并提示（1 小时）。
    private static final long STALE_CACHE_AGE_MS = 60L * 60L * 1000L;

    private static final String KEY_SORT_ORDER = "star_sort_order";
    private static final String KEY_SORT_DIRECTION = "star_sort_direction";
    private static final String KEY_CURRENT_TAB = "star_current_tab";
    private static final String KEY_LISTS_LOADED = "star_lists_loaded";
    private static final String KEY_UPDATES_LOADED = "star_updates_loaded";
    private static final String KEY_EXPANDED_OFFICIAL = "star_expanded_official";
    // 0.2.4（97）：多选状态（多选模式＋已选仓库）进 onSaveInstanceState，
    // 旋转后在 onCreate 恢复，不再静默退出多选模式丢掉已勾选的仓库。
    private static final String KEY_MULTI_SELECT = "star_multi_select";
    private static final String KEY_SELECTED_REPOS = "star_selected_repos";

    // 0.1.64：列表显示顺序只存本机（官方接口无排序能力），按账号记一份
    // 列表 ID 的顺序；拉取后按此顺序展示，新列表排最后，删除的去掉。
    private static final String PREFS_LIST_ORDER = "star_list_order";
    private static final String KEY_LIST_ORDER_IDS = "order_ids";

    // BUG-8/BUG-27：旋转恢复用。Starred REST 列表无磁盘缓存，loaded 标志
    // 即使存了也不能恢复（否则空列表 + 永不重拉），故只恢复排序与页签，
    // Starred 旋转后按新排序重拉第 1 页；列表/更新有磁盘缓存才恢复 loaded。
    private boolean mRestoredListsLoaded;
    private boolean mRestoredUpdatesLoaded;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLogin = getArguments() != null ? getArguments().getString("user") : null;
        mSortHelper = new RepositoryListContainerFragment.SortDrawerHelper();
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            // BUG-8/BUG-27：恢复排序与页签；loaded 标志先记下来，
            // onCreateView 里只有磁盘缓存能接住数据时才真正恢复。
            mSortOrder = savedInstanceState.getString(KEY_SORT_ORDER, mSortOrder);
            mSortDirection = savedInstanceState.getString(KEY_SORT_DIRECTION, mSortDirection);
            mCurrentTab = savedInstanceState.getInt(KEY_CURRENT_TAB, mCurrentTab);
            mRestoredListsLoaded = savedInstanceState.getBoolean(KEY_LISTS_LOADED, false);
            mRestoredUpdatesLoaded = savedInstanceState.getBoolean(KEY_UPDATES_LOADED, false);
            mExpandedOfficialId = savedInstanceState.getString(KEY_EXPANDED_OFFICIAL);
            // 0.2.4（97）：恢复多选状态（onDestroyView 会清掉旧实例的这份
            // 状态，但新实例在 onCreate 里从 bundle 拿回来；视图重建时
            // rebuild() 按它画出多选顶栏与勾选框）。
            mMultiSelect = savedInstanceState.getBoolean(KEY_MULTI_SELECT, false);
            ArrayList<String> selected =
                    savedInstanceState.getStringArrayList(KEY_SELECTED_REPOS);
            if (selected != null) {
                mSelectedRepos.addAll(selected);
            }
            if (mMultiSelect && mSelectedRepos.isEmpty()) {
                mMultiSelect = false;
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SORT_ORDER, mSortOrder);
        outState.putString(KEY_SORT_DIRECTION, mSortDirection);
        outState.putInt(KEY_CURRENT_TAB, mCurrentTab);
        outState.putBoolean(KEY_LISTS_LOADED, mListsLoaded);
        outState.putBoolean(KEY_UPDATES_LOADED, mUpdatesLoaded);
        outState.putString(KEY_EXPANDED_OFFICIAL, mExpandedOfficialId);
        // 0.2.4（97）：多选状态进 saved state；只在多选模式开启时存，
        // 否则存空集合即可（恢复时空集合会把模式关掉，与上文一致）。
        outState.putBoolean(KEY_MULTI_SELECT, mMultiSelect);
        outState.putStringArrayList(KEY_SELECTED_REPOS, new ArrayList<>(mSelectedRepos));
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_star_merged, container, false);
        mList = view.findViewById(R.id.list);
        mSwipe = view.findViewById(R.id.swipe);
        LinearLayoutManager lm = new LinearLayoutManager(getActivity());
        mList.setLayoutManager(lm);
        mAdapter = new MergedAdapter();
        mList.setAdapter(mAdapter);
        mList.setClipToPadding(false);
        mList.setPadding(mList.getPaddingLeft(), mList.getPaddingTop(), mList.getPaddingRight(),
                getResources().getDimensionPixelSize(R.dimen.bottom_nav_height));
        mList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (mCurrentTab != TAB_STARRED) return;
                int last = lm.findLastVisibleItemPosition();
                if (last >= mItems.size() - 8 && !mStarredLoading && mStarredHasMore) {
                    loadStarredPage(false, false);
                }
            }
        });
        mSwipe.setOnRefreshListener(this::refreshCurrentTab);
        // 0.1.11：分段左右滑。RecyclerView 的 OnItemTouchListener 在子项
        // 之前看到全部触摸，且永不消费事件，故点击、纵向滚动、下拉刷新
        // 均不受影响；首末段不回绕。
        // 0.1.12：左右滑只切页内分段，永不切底栏（底栏只点按切换）。
        // 0.1.16：fling 判定改距离判定——慢速拖动松手不产生 fling，真机上
        // 原判定完全没反应；现松手位移 >= 100dp 且水平大于竖直即切。
        mList.addOnItemTouchListener(
                new com.gh4a.utils.HorizontalSwipeListener.ForRecyclerView(
                        new com.gh4a.utils.HorizontalSwipeListener.Callback() {
                            @Override
                            public void onSwipeLeft() {
                                switchStarTab(1);
                            }

                            @Override
                            public void onSwipeRight() {
                                switchStarTab(-1);
                            }
                        }));
        mSegViews = new TextView[] {
                view.findViewById(R.id.seg_lists),
                view.findViewById(R.id.seg_starred),
                view.findViewById(R.id.seg_updates) };
        for (int i = 0; i < mSegViews.length; i++) {
            final int tab = i;
            mSegViews[i].setOnClickListener(v -> selectTab(tab));
        }
        // 0.1.13：分段行本身也支持左右滑（与列表区同款距离判定、永不消费事件，
        // 点按仍走上面的 OnClickListener）。直接在分段行上滑也能切段，不只列表区。
        android.view.View.OnTouchListener segSwipe =
                new com.gh4a.utils.HorizontalSwipeListener(
                        new com.gh4a.utils.HorizontalSwipeListener.Callback() {
                            @Override
                            public void onSwipeLeft() {
                                switchStarTab(1);
                            }

                            @Override
                            public void onSwipeRight() {
                                switchStarTab(-1);
                            }
                        });
        android.view.View segContainer = view.findViewById(R.id.star_segmented);
        if (segContainer != null) {
            segContainer.setOnTouchListener(segSwipe);
        }
        for (TextView seg : mSegViews) {
            seg.setOnTouchListener(segSwipe);
        }
        if (getActivity() != null && mLogin != null) {
            mReadUpdateKeys = StarReadState.getReadKeys(getActivity(), mLogin);
            // P3：官方列表缓存 JSON 解析（全部列表＋全部成员仓库）移出主线程。
            // 之前在 onCreateView 同步解析，列表 500+ 项时首开卡顿；现在后台
            // 解析、主线程回填。回填时若网络拉取已完成（mListsLoaded 已真）
            // 则不再用旧缓存覆盖新鲜数据；视图销毁时订阅随 mDisposables
            // 释放，回调里仍判 isAdded() 设防。解析失败不影响主流程，
            // loadCurrentTab 会走网络拉取。
            final android.content.Context appContext =
                    getActivity().getApplicationContext();
            final String login = mLogin;
            mDisposables.add(Single.fromCallable(
                            () -> UserListsCache.load(appContext, login))
                    .compose(RxUtils::doInBackground)
                    .subscribe(cached -> {
                        if (!isAdded() || cached == null || mListsLoaded) return;
                        // 0.1.38：Star 数据链埋点（只记条数，不记列表与仓库名）。
                        com.gh4a.utils.DiagTrace.log(getActivity(), "STAR",
                                "lists=cached count=" + cached.size());
                        mOfficialLists = cached;
                        // 0.1.64：按本机保存的顺序展示（新列表排最后）。
                        applySavedListOrder();
                        mListsFailed = false;
                        // BUG-18/BUG-27：旋转恢复时缓存能接住列表数据，才恢复 loaded
                        // 标志（否则仍按未加载处理，loadCurrentTab 会重拉）。
                        if (mRestoredListsLoaded) {
                            mListsLoaded = true;
                        }
                        if (mCurrentTab == TAB_LISTS) {
                            rebuild();
                        }
                    }, error -> {
                        // 缓存解析失败：不置失败态，网络拉取会接手。
                    }));
            // BUG-27：更新同样有 RadarCache，旋转恢复时能接住才恢复 loaded。
            if (mRestoredUpdatesLoaded) {
                List<ReleaseRadarFragment.RadarItem> cachedUpdates =
                        RadarCache.load(getActivity(), mLogin);
                if (cachedUpdates != null) {
                    mUpdates = cachedUpdates;
                    mUpdatesFailed = false;
                    mUpdatesLoaded = true;
                    updateStarBadge();
                }
            }
        }
        // 0.1.64：返回键先退出多选/排序模式（仅模式开启时生效）。
        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (mSortMode) {
                    exitSortMode(false);
                } else if (mMultiSelect) {
                    exitMultiSelect();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), mBackCallback);
        // 0.2.4（97）：恢复的多选／排序模式要同步到返回键回调——onCreate
        // 里恢复的 mMultiSelect=true 时，不调这里返回键就退不出多选模式。
        // 非恢复路径下两者均为 false，与回调构造时的默认值一致，无行为变化。
        updateBackCallback();
        updateSegmented();
        rebuild();
        // 0.1.3：进页只加载当前页（默认分组），不把三页全拉一遍。
        loadCurrentTab();
        // 0.1.7：当前页开始加载后，立即在后台并行预取其余两页（不阻塞当前页）。
        prefetchOtherTabs(false);
        // 0.2.2：观察批量移组状态（B-M7）——ViewModel 在旋转时保留，Fragment
        // 重建后按最新状态把进度框／结果框重建出来。
        // 0.2.4（59）：必须用 getViewLifecycleOwner() 观察——Fragment 的生命
        // 周期长于视图，视图销毁后、Fragment 销毁前的窗口内观察者仍可收到
        // 投递，onMoveStateChanged 操作已销毁的视图／Activity 会崩溃。
        mMoveViewModel = new ViewModelProvider(this)
                .get(StarBatchMoveViewModel.class);
        mMoveViewModel.getState().observe(getViewLifecycleOwner(), this::onMoveStateChanged);
        // 0.2.4（58）：单发操作（入组单发／改名／删组）结果，同上用
        // getViewLifecycleOwner() 观察。
        mMoveViewModel.getSingleOp().observe(getViewLifecycleOwner(), this::onSingleOpChanged);
        return view;
    }

    @Override
    public void onDestroyView() {
        // BUG-11：直接走 Schedulers.io() 的订阅在视图销毁时就释放，不等
        // onDestroy；同时重置加载标志（BUG-16），避免标志卡住。
        // 注意用 clear() 而不用 dispose()：同一实例的视图重建时
        // CompositeDisposable 仍可用，dispose() 后再 add 会被立即丢弃。
        cancelListsRequest();
        cancelStarredRequest();
        cancelUpdatesRequest();
        mDisposables.clear();
        mListsLoading = false;
        mStarredLoading = false;
        mUpdatesLoading = false;
        // 0.1.64：视图销毁时拆掉排序拖动，模式状态随视图重建而丢弃。
        if (mSortTouchHelper != null) {
            mSortTouchHelper.attachToRecyclerView(null);
            mSortTouchHelper = null;
        }
        mSortMode = false;
        mMultiSelect = false;
        mSelectedRepos.clear();
        mSortLists = null;
        // 0.2.2：视图销毁时拆掉批量移组的两个对话框（避免窗口泄漏）；
        // 移动本身不停，ViewModel 里的 Rx 链继续跑，重建后按状态重建对话框。
        dismissMoveProgressDialog();
        dismissMoveResultDialog();
        mList = null;
        mSwipe = null;
        mAdapter = null;
        mSegViews = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        mDisposables.dispose();
        super.onDestroy();
    }

    /** BUG-2：取消某页在途请求（dispose + 世代 +1，迟到回调一律丢弃）。 */
    private void cancelListsRequest() {
        mListsGeneration++;
        if (mListsDisposable != null && !mListsDisposable.isDisposed()) {
            mListsDisposable.dispose();
        }
        mListsDisposable = null;
        mListsLoading = false;
    }

    private void cancelStarredRequest() {
        mStarredGeneration++;
        if (mStarredDisposable != null && !mStarredDisposable.isDisposed()) {
            mStarredDisposable.dispose();
        }
        mStarredDisposable = null;
        mStarredLoading = false;
    }

    private void cancelUpdatesRequest() {
        mUpdatesGeneration++;
        if (mUpdatesDisposable != null && !mUpdatesDisposable.isDisposed()) {
            mUpdatesDisposable.dispose();
        }
        mUpdatesDisposable = null;
        mUpdatesLoading = false;
    }

    /** BUG-15：只有发起刷新的页完成（或已无任何页在加载）时才停转圈。 */
    private void stopRefreshingFor(int completedTab) {
        if (mSwipe == null || !mRefreshActive) return;
        if (completedTab == mRefreshTab
                || (!mListsLoading && !mStarredLoading && !mUpdatesLoading)) {
            mSwipe.setRefreshing(false);
            mRefreshActive = false;
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.repo_starred_list_menu, menu);
        mSortHelper.selectSortType(menu, mSortOrder, mSortDirection, true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        String[] order = mSortHelper.handleSelectionAndGetSortOrder(item);
        if (order == null) {
            return super.onOptionsItemSelected(item);
        }
        mSortOrder = order[0];
        mSortDirection = order[1];
        item.setChecked(true);
        // BUG-2：不先清空——loadStarredPage(force=true) 会先取消在途请求，
        // 新请求真正开始时才清空列表，旧请求绝无机会把旧排序数据追加进来。
        loadStarredPage(true, true);
        return true;
    }

    // 0.1.16：左右滑切段（手指向左滑 → 下一段，向右滑 → 上一段），首末段不回绕。
    private void switchStarTab(int step) {
        if (!isAdded()) return;
        int next = mCurrentTab + step;
        if (next < TAB_LISTS || next > TAB_UPDATES) return;
        selectTab(next);
    }

    private void selectTab(int tab) {
        if (tab == mCurrentTab) return;
        // 0.1.64：切段先静默退出多选/排序模式（不重建，selectTab 自会重建）。
        clearMultiSelectState();
        clearSortModeState();
        mCurrentTab = tab;
        updateSegmented();
        rebuild();
        if (mList != null) mList.scrollToPosition(0);
        // 0.1.3：懒加载——列表/更新首次切过去才加载，之后复用已加载数据。
        loadCurrentTab();
        // 0.1.7：切页后也把剩下那一页在后台预取掉，下次再切就直接显示。
        prefetchOtherTabs(false);
    }

    /** 0.1.3：只加载当前页；已加载过的页不再重复拉取（下拉刷新除外）。
     *  BUG-3/BUG-18：失败不置 loaded，切回时 loadCurrentTab 会重试。 */
    private void loadCurrentTab() {
        if (mCurrentTab == TAB_LISTS) {
            if (!mListsLoaded) {
                loadOfficialLists(false);
            }
        } else if (mCurrentTab == TAB_STARRED) {
            if (!mStarredLoaded) {
                loadStarredPage(true, false);
            }
        } else if (mCurrentTab == TAB_UPDATES) {
            if (!mUpdatesLoaded) {
                loadUpdates(false);
            }
        }
    }

    private void updateSegmented() {
        if (mSegViews == null) return;
        for (int i = 0; i < mSegViews.length; i++) {
            TextView seg = mSegViews[i];
            if (i == mCurrentTab) {
                seg.setBackgroundResource(R.drawable.segmented_selected);
                seg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
                seg.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                seg.setBackground(null);
                seg.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.secondary_text_light));
                seg.setTypeface(null, android.graphics.Typeface.NORMAL);
            }
        }
    }

    /** 0.1.3：下拉刷新只刷当前页，不再三页全刷。
     *  0.1.7：当前页刷完后，后台把其余两页重新预取一遍以保持新鲜。 */
    private void refreshCurrentTab() {
        mRefreshTab = mCurrentTab;
        mRefreshActive = true;
        mRefreshPendingPrefetch = true;
        if (mCurrentTab == TAB_LISTS) {
            mListsLoaded = false;
            loadOfficialLists(true);
        } else if (mCurrentTab == TAB_STARRED) {
            // BUG-2：用户自己的下拉刷新可以整页重来（清空发生在新请求
            // 开始时，见 loadStarredPage 的 force 分支），与 BUG-14 的
            // 强制预取是两回事。
            loadStarredPage(true, true);
        } else if (mCurrentTab == TAB_UPDATES) {
            loadUpdates(true);
        }
    }

    /** 0.1.7：异步预取其余页（并行、不阻塞当前页、不显示当前页的加载态）。
     *  forceRefresh 为真时先重置其余页的已加载态再拉取（下拉刷新后用）；
     *  否则只拉取从未加载且不在加载中的页，绝不重复拉取同一页。
     *  BUG-14：强制预取绝不清空用户已翻页的 Star 列表——已翻到第 2 页
     *  之后（mStarredPage > 2）直接跳过该页的强制刷新，让它自己的下拉
     *  刷新处理；只加载了第 1 页时仅原地刷新第 1 页。 */
    private void prefetchOtherTabs(boolean forceRefresh) {
        if (mCurrentTab != TAB_LISTS) {
            if (forceRefresh) {
                mListsLoaded = false;
                loadOfficialLists(true);
            } else if (!mListsLoaded && !mListsLoading) {
                loadOfficialLists(false);
            }
        }
        if (mCurrentTab != TAB_STARRED) {
            if (forceRefresh) {
                if (mStarredLoaded && mStarredPage > 2) {
                    // 用户已翻页：保留已加载的各页，不强制刷新。
                } else {
                    mStarredLoaded = false;
                    loadStarredPage(true, true);
                }
            } else if (!mStarredLoaded && !mStarredLoading) {
                loadStarredPage(true, false);
            }
        }
        if (mCurrentTab != TAB_UPDATES) {
            if (forceRefresh) {
                mUpdatesLoaded = false;
                loadUpdates(true);
            } else if (!mUpdatesLoaded && !mUpdatesLoading) {
                loadUpdates(false);
            }
        }
    }

    /** 0.1.7：某页加载完成时，若它正是下拉刷新发起的那一页，就把其余页重新预取一遍。 */
    private void maybePrefetchAfterRefresh(int completedTab) {
        if (mRefreshPendingPrefetch && completedTab == mRefreshTab) {
            mRefreshPendingPrefetch = false;
            prefetchOtherTabs(true);
        }
    }

    private void loadOfficialLists(boolean force) {
        // 0.1.20 F-17: mLogin 为空（newInstance(null)/缺参）时不发网络请求，直接失败态。
        if (mLogin == null) {
            mListsLoading = false;
            if (mOfficialLists == null) {
                mOfficialLists = new ArrayList<>();
            }
            mListsFailed = true;
            if (mCurrentTab == TAB_LISTS) {
                rebuild();
            }
            return;
        }
        if (mListsLoading) {
            if (!force) return;
            cancelListsRequest();
        }
        mListsLoading = true;
        final int generation = ++mListsGeneration;
        mListsDisposable = UserListsGraphQL.fetchLists(mLogin)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lists -> {
                    // BUG-16：先重置标志，再做 isAdded/世代检查，标志永不卡住。
                    mListsLoading = false;
                    mListsDisposable = null;
                    if (generation != mListsGeneration || !isAdded()) return;
                    stopRefreshingFor(TAB_LISTS);
                    // 0.1.38：Star 数据链埋点（只记条数）。
                    com.gh4a.utils.DiagTrace.log(getActivity(), "STAR",
                            "lists=graphql count=" + (lists != null ? lists.size() : 0));
                    mOfficialLists = lists;
                    mListsFailed = false;
                    mListsLoaded = true;
                    // 0.1.64：按本机保存的顺序展示（新列表排最后）。
                    applySavedListOrder();
                    // 成功拉取即覆盖本地缓存；只在仍停留在分组页时替换显示，
                    // 用户已切走的页不碰（切回分组时 rebuild 会显示新数据）。
                    if (getActivity() != null && mLogin != null) {
                        UserListsCache.save(getActivity(), mLogin, mOfficialLists);
                    }
                    if (mCurrentTab == TAB_LISTS) {
                        rebuild();
                    }
                    maybePrefetchAfterRefresh(TAB_LISTS);
                }, error -> {
                    mListsLoading = false;
                    mListsDisposable = null;
                    if (generation != mListsGeneration || !isAdded()) return;
                    stopRefreshingFor(TAB_LISTS);
                    // 失败不清空缓存：有缓存时继续显示缓存；缓存为空时
                    // 显示失败提示；失败本身不覆盖缓存。
                    // BUG-3/BUG-18：失败不置 mListsLoaded，切回/重试会再拉。
                    if (mOfficialLists == null) {
                        mOfficialLists = new ArrayList<>();
                    }
                    // 0.1.38：Star 数据链埋点（只记异常类名，失败时显示缓存不变）。
                    com.gh4a.utils.DiagTrace.log(getActivity(), "STAR",
                            "lists=graphql_error "
                                    + (error != null ? error.getClass().getSimpleName() : "?"));
                    mListsFailed = true;
                    if (mCurrentTab == TAB_LISTS) {
                        rebuild();
                    }
                    maybePrefetchAfterRefresh(TAB_LISTS);
                });
        mDisposables.add(mListsDisposable);
    }

    private void loadStarredPage(boolean firstPage, boolean force) {
        // 0.1.20 F-17: 见 loadOfficialLists 同款守卫。
        if (mLogin == null) {
            mStarredLoading = false;
            mStarredFailed = true;
            stopRefreshingFor(TAB_STARRED);
            if (mCurrentTab == TAB_STARRED) {
                rebuild();
            }
            return;
        }
        if (mStarredLoading) {
            if (!force) return;
            cancelStarredRequest();
        }
        // BUG-2：新请求真正开始时才清空/复位——在此之前旧列表原样显示。
        if (firstPage) {
            mStarred.clear();
            mStarredPage = 1;
            mStarredHasMore = true;
        }
        mStarredLoading = true;
        final int generation = ++mStarredGeneration;
        final int requestPage = mStarredPage;
        final StarringService service = ServiceFactory.get(StarringService.class, firstPage);
        final HashMap<String, String> filterData = new HashMap<>();
        filterData.put("sort", mSortOrder);
        filterData.put("direction", mSortDirection);
        mStarredDisposable = service.getStarredRepositories(mLogin, filterData, mStarredPage)
                .map(ApiHelpers::throwOnFailure)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(page -> {
                    mStarredLoading = false;
                    mStarredDisposable = null;
                    if (generation != mStarredGeneration || !isAdded()) return;
                    mStarredLoaded = true;
                    mStarredFailed = false;
                    stopRefreshingFor(TAB_STARRED);
                    List<Repository> items = page != null ? page.items() : null;
                    if (items == null || items.isEmpty()) {
                        mStarredHasMore = false;
                    } else {
                        // 世代已保证：这一定是当前排序/页码的请求，不会把旧页
                        // 数据追加到新清空的列表里；页码只按本次请求推进。
                        mStarred.addAll(items);
                        mStarredPage = requestPage + 1;
                        // BUG-30：不再用“<30 条即末页”猜测——恰好 30 条的
                        // 末页会被误判为还有下页而多发一次空请求。改用
                        // Page.next()（Link 头的 rel=next），与
                        // PagedDataBaseFragment:144 / CommitListFragment:229
                        // 同一约定：无 next 即末页。
                        mStarredHasMore = page.next() != null;
                    }
                    // 0.1.7：预取完成时不碰当前页显示——切过去时 selectTab 的 rebuild 会显示缓存数据。
                    if (mCurrentTab == TAB_STARRED) {
                        rebuild();
                    }
                    maybePrefetchAfterRefresh(TAB_STARRED);
                }, error -> {
                    mStarredLoading = false;
                    mStarredDisposable = null;
                    if (generation != mStarredGeneration || !isAdded()) return;
                    // BUG-3：失败不置 loaded，切回时 loadCurrentTab 会重试；
                    // 首屏失败才关 hasMore，后续页失败保留 hasMore 以便滚动重试。
                    mStarredFailed = true;
                    stopRefreshingFor(TAB_STARRED);
                    if (requestPage <= 1) {
                        mStarredHasMore = false;
                    }
                    if (mCurrentTab == TAB_STARRED) {
                        rebuild();
                    }
                    maybePrefetchAfterRefresh(TAB_STARRED);
                });
        mDisposables.add(mStarredDisposable);
    }

    private void loadUpdates(boolean force) {
        // 0.1.20 F-17: 见 loadOfficialLists 同款守卫。
        if (mLogin == null) {
            mUpdatesLoading = false;
            if (mUpdates == null) {
                mUpdates = new ArrayList<>();
            }
            mUpdatesFailed = true;
            stopRefreshingFor(TAB_UPDATES);
            if (mCurrentTab == TAB_UPDATES) {
                rebuild();
            }
            return;
        }
        if (mUpdatesLoading) {
            if (!force) return;
            cancelUpdatesRequest();
        }
        mUpdatesLoading = true;
        final int generation = ++mUpdatesGeneration;
        mUpdatesDisposable = RadarGraphQL.fetch(mLogin)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    mUpdatesLoading = false;
                    mUpdatesDisposable = null;
                    if (generation != mUpdatesGeneration || !isAdded()) return;
                    mUpdatesLoaded = true;
                    stopRefreshingFor(TAB_UPDATES);
                    mUpdates = items;
                    mUpdatesFailed = false;
                    // BUG-5：成功即写 RadarCache，角标重启后不回旧值。
                    if (getActivity() != null && mLogin != null && items != null) {
                        RadarCache.save(getActivity(), mLogin, items);
                    }
                    updateStarBadge();
                    // 0.1.7：预取完成时不碰当前页显示；角标照常更新（全局提示）。
                    if (mCurrentTab == TAB_UPDATES) {
                        rebuild();
                    }
                    maybePrefetchAfterRefresh(TAB_UPDATES);
                }, error -> {
                    mUpdatesLoading = false;
                    mUpdatesDisposable = null;
                    if (generation != mUpdatesGeneration || !isAdded()) return;
                    // BUG-3：失败不置 loaded，切回时 loadCurrentTab 会重试。
                    // 0.1.20 F-08: 失败不清空已显示列表——旧值非空则保留，
                    // 仅此前为 null 时用空列表，并置失败标志供失败行重试。
                    stopRefreshingFor(TAB_UPDATES);
                    if (mUpdates == null) {
                        mUpdates = new ArrayList<>();
                    }
                    mUpdatesFailed = true;
                    if (mCurrentTab == TAB_UPDATES) {
                        rebuild();
                    }
                    maybePrefetchAfterRefresh(TAB_UPDATES);
                });
        mDisposables.add(mUpdatesDisposable);
    }

    private void updateStarBadge() {
        // 0.1.20 Observer: Star 页只向 holder 发布未读数，底栏落点在 Home 订阅回调。
        if (getActivity() instanceof com.gh4a.activities.home.HomeActivity) {
            ((com.gh4a.activities.home.HomeActivity) getActivity())
                    .getBadgeState()
                    .setStarUpdateCount(StarReadState.unreadCount(mUpdates, mReadUpdateKeys));
        }
    }

    private static String updateKey(ReleaseRadarFragment.RadarItem radar) {
        return radar.owner + "/" + radar.repo + "#" + radar.releaseId;
    }

    private void markUpdateRead(ReleaseRadarFragment.RadarItem radar) {
        if (getActivity() == null || mLogin == null || radar == null) return;
        if (mReadUpdateKeys.add(updateKey(radar))) {
            StarReadState.markRead(getActivity(), mLogin, updateKey(radar));
        }
        updateStarBadge();
        rebuild();
    }

    private void markAllUpdatesRead() {
        if (getActivity() == null || mLogin == null || mUpdates == null) return;
        for (ReleaseRadarFragment.RadarItem radar : mUpdates) {
            mReadUpdateKeys.add(updateKey(radar));
        }
        StarReadState.markAllRead(getActivity(), mLogin, mUpdates);
        updateStarBadge();
        rebuild();
    }

    private boolean isUpdateRead(ReleaseRadarFragment.RadarItem radar) {
        return radar != null && mReadUpdateKeys.contains(updateKey(radar));
    }

    private void rebuild() {
        if (mAdapter == null) return;
        mItems.clear();
        // 0.1.1：只建当前分段页的内容；列表数据未到时不放任何提示行
        // （“正在加载官方列表”提示已删，数据直接出现）。
        if (mCurrentTab == TAB_LISTS) {
            buildListsSection();
        } else if (mCurrentTab == TAB_STARRED) {
            buildStarredSection();
        } else {
            buildUpdatesSection();
        }
        mAdapter.notifyDataSetChanged();
    }

    private void buildListsSection() {
        if (mOfficialLists == null) {
            return;
        }
        // 0.1.64：排序模式只画排序列表（提示行 + 可拖行 + 底部栏）。
        if (mSortMode) {
            buildSortSection();
            return;
        }
        // 0.1.64：多选模式顶栏（主色底 + 已选 N 个 + 取消）。
        if (mMultiSelect) {
            Item head = new Item();
            head.type = TYPE_SELECTION_HEADER;
            head.title = getString(R.string.star_list_selected, mSelectedRepos.size());
            mItems.add(head);
        }
        if (mOfficialLists.isEmpty()) {
            // 0.1.64：无本机分组回退——空即空状态（失败/为空文案）。
            Item st = new Item();
            st.type = TYPE_STATUS;
            st.title = getString(mListsFailed ? R.string.my_lists_failed : R.string.my_lists_empty);
            mItems.add(st);
            // 0.2.1：空态也要有“+ 新增分组”入口（多选时不显示），否则首次使用
            // 在分组页内没有任何办法创建第一个列表。
            if (!mMultiSelect) {
                Item add = new Item();
                add.type = TYPE_ADD_GROUP;
                add.title = getString(R.string.star_list_add_group);
                mItems.add(add);
            }
        } else {
            // BUG-17：失败后仍显示缓存时，按缓存年龄判断是否过期；
            // 超过阈值（或年龄未知）才在顶部放一行可点按重试的提示，
            // 新鲜缓存的短暂失败不打扰用户。
            // 注：文案暂用字面量，未新增 strings.xml 条目（不在本次改动范围）。
            if (mListsFailed) {
                long age = getActivity() != null
                        ? UserListsCache.age(getActivity(), mLogin) : -1;
                if (age < 0 || age > STALE_CACHE_AGE_MS) {
                    Item stale = new Item();
                    stale.type = TYPE_STATUS;
                    stale.title = getString(R.string.star_list_stale_retry);
                    stale.retryLists = true;
                    mItems.add(stale);
                }
            }
            for (UserListsGraphQL.UserList list : mOfficialLists) {
                Item row = new Item();
                row.type = TYPE_LIST;
                row.title = list.name;
                row.subtitle = getResources().getQuantityString(
                        R.plurals.star_group_repo_count, list.repos.size(), list.repos.size());
                // 0.1.10：组头副标题改放官方列表简介（数量只放右侧胶囊）。
                row.description = list.description;
                row.key = list.id;
                row.expanded = TextUtils.equals(mExpandedOfficialId, list.id);
                mItems.add(row);
                if (row.expanded) {
                    addListRepoItems(list.repos);
                }
            }
            // 0.1.64：未分组区——已加载的 Star 仓库中不在任何官方列表里的。
            List<UserListsGraphQL.Repo> ungrouped = computeUngrouped();
            if (!ungrouped.isEmpty()) {
                Item head = new Item();
                head.type = TYPE_UNGROUPED;
                head.title = getString(R.string.star_list_ungrouped);
                head.subtitle = getResources().getQuantityString(
                        R.plurals.star_group_repo_count, ungrouped.size(), ungrouped.size());
                // 0.2.2：Star 列表未加载完时，未分组区只反映已加载部分，
                // 副标题提示“仅显示已加载的 N 条”；加载完则不显示。
                if (mStarredHasMore) {
                    head.description = getString(
                            R.string.star_list_ungrouped_partial, mStarred.size());
                }
                head.expanded = mUngroupedExpanded;
                mItems.add(head);
                if (mUngroupedExpanded) {
                    addListRepoItems(ungrouped);
                }
            }
            // 0.1.64：+ 新增分组（多选时不显示）。
            if (!mMultiSelect) {
                Item add = new Item();
                add.type = TYPE_ADD_GROUP;
                add.title = getString(R.string.star_list_add_group);
                mItems.add(add);
            }
        }
        // 0.1.64：多选模式底部栏（已选 N 个 + 取消 + 移动至分组）。
        if (mMultiSelect) {
            Item bar = new Item();
            bar.type = TYPE_ACTION_BAR;
            bar.actionKind = ACTION_BAR_MOVE;
            bar.title = getString(R.string.star_list_selected, mSelectedRepos.size());
            mItems.add(bar);
        }
    }

    /** 0.1.64：排序模式的列表内容（提示行 + 可拖行 + 底部栏）。 */
    private void buildSortSection() {
        Item hint = new Item();
        hint.type = TYPE_SORT_HEADER;
        hint.title = getString(R.string.star_list_sort_hint);
        mItems.add(hint);
        if (mSortLists != null) {
            for (UserListsGraphQL.UserList list : mSortLists) {
                Item row = new Item();
                row.type = TYPE_SORT_ROW;
                row.title = list.name;
                row.key = list.id;
                mItems.add(row);
            }
        }
        Item bar = new Item();
        bar.type = TYPE_ACTION_BAR;
        bar.actionKind = ACTION_BAR_SORT;
        bar.title = getString(R.string.star_list_done);
        mItems.add(bar);
    }

    private void addListRepoItems(List<UserListsGraphQL.Repo> repos) {
        for (UserListsGraphQL.Repo repo : repos) {
            Item r = new Item();
            r.type = TYPE_LIST_REPO;
            r.title = repo.fullName;
            r.subtitle = repo.description;
            r.fullName = repo.fullName;
            mItems.add(r);
        }
    }

    /**
     * 0.1.64：未分组仓库 = 已加载的 Star 列表（mStarred，列表页懒加载、
     * 后台预取第 1 页）中不在任何官方列表里的仓库。Star 列表未加载或
     * 只加载了部分页时，未分组区只反映已加载部分。
     */
    private List<UserListsGraphQL.Repo> computeUngrouped() {
        Set<String> grouped = new java.util.HashSet<>();
        for (UserListsGraphQL.UserList list : mOfficialLists) {
            for (UserListsGraphQL.Repo repo : list.repos) {
                grouped.add(repo.fullName);
            }
        }
        List<UserListsGraphQL.Repo> out = new ArrayList<>();
        for (Repository repo : mStarred) {
            if (!grouped.contains(repo.fullName())) {
                out.add(new UserListsGraphQL.Repo(repo.fullName(),
                        repo.description() != null ? repo.description() : ""));
            }
        }
        return out;
    }

    private void buildStarredSection() {
        if (mStarred.isEmpty() && !mStarredLoading) {
            Item st = new Item();
            st.type = TYPE_STATUS;
            // BUG-3：首屏失败不冒充“无加星”，显示失败文案并可点按重试
            // （切回页签时 loadCurrentTab 也会因 loaded 未置真而重试）。
            st.title = getString(mStarredFailed
                    ? R.string.my_lists_failed : R.string.no_starred_repos_found);
            st.retryStarred = mStarredFailed;
            mItems.add(st);
        } else {
            for (Repository repo : mStarred) {
                Item r = new Item();
                r.type = TYPE_STARRED_REPO;
                r.repo = repo;
                mItems.add(r);
            }
        }
    }

    private void buildUpdatesSection() {
        if (mUpdates != null && StarReadState.unreadCount(mUpdates, mReadUpdateKeys) > 0) {
            Item all = new Item();
            all.type = TYPE_SECTION;
            all.title = getString(R.string.mark_all_read);
            all.markAllRead = true;
            mItems.add(all);
        }
        if (mUpdates == null) {
            Item st = new Item();
            st.type = TYPE_STATUS;
            st.title = getString(R.string.loading);
            mItems.add(st);
        } else if (mUpdates.isEmpty()) {
            Item st = new Item();
            st.type = TYPE_STATUS;
            st.title = getString(mUpdatesFailed
                    ? R.string.my_lists_failed : R.string.no_release_radar_items);
            // BUG-3：更新失败行可点按重试（与列表页的失败处理对齐）。
            st.retryUpdates = mUpdatesFailed;
            mItems.add(st);
        } else {
            for (ReleaseRadarFragment.RadarItem radar : mUpdates) {
                Item r = new Item();
                r.type = TYPE_UPDATE;
                r.radar = radar;
                mItems.add(r);
            }
        }
        // B-L6：这里不调 notifyDataSetChanged——唯一调用方 rebuild() 末尾已调一次。
    }

    private void openRepo(String fullName) {
        String[] parts = fullName.split("/", 2);
        if (parts.length == 2 && getActivity() != null) {
            startActivity(RepositoryActivity.makeIntent(getActivity(), parts[0], parts[1]));
        }
    }

    // ---- long-press assign (same semantics as StarredRepositoryListFragment) ----
    private void showAssignToGroupDialog(Repository repository) {
        if (mOfficialLists != null && !mOfficialLists.isEmpty()) {
            showAssignToOfficialListsDialog(repository);
        } else {
            // 0.1.64：无任何官方列表时直接新建（无本机分组回退）。
            showCreateListDialogForAssign(repository);
        }
    }

    private void showAssignToOfficialListsDialog(Repository repository) {
        final String fullName = repository.fullName();
        Set<String> checked = new java.util.HashSet<>();
        for (UserListsGraphQL.UserList list : mOfficialLists) {
            for (UserListsGraphQL.Repo repo : list.repos) {
                if (fullName.equals(repo.fullName)) {
                    checked.add(list.id);
                    break;
                }
            }
        }
        UserListDialogHelper.showAssignDialog(requireActivity(), mOfficialLists,
                checked, null,
                () -> showCreateListDialogForAssign(repository),
                selectedIds -> assignToOfficialLists(fullName, selectedIds));
    }

    /** 0.1.64：新建官方列表后直接把该仓库归属到新列表。 */
    private void showCreateListDialogForAssign(Repository repository) {
        // 215：建组请求原挂 Fragment 的 mDisposables，旋转即 dispose 中断；
        // 改走只收名称的对话框 + ViewModel 的 createListAndAssign（126
        // 同款，RepositoryFragment 已在用），请求挂 ViewModel，旋转不中断。
        UserListDialogHelper.showCreateListNameDialog(requireActivity(),
                mOfficialLists, (name, isPrivate) -> {
                    if (mMoveViewModel == null) {
                        return;
                    }
                    mMoveViewModel.createListAndAssign(
                            repository.fullName(), name, isPrivate);
                });
    }

    private void assignToOfficialLists(String fullName, List<String> listIds) {
        // 0.2.4（58）：单发入组走 ViewModel，旋转不中断；结果经 LiveData
        // 回到重建后的 Fragment（Toast＋重拉），不再挂 Fragment 的 mDisposables。
        if (mMoveViewModel == null) {
            return;
        }
        mMoveViewModel.runSingleOp(UserListsGraphQL.assignRepo(fullName, listIds),
                R.string.assign_official_success, R.string.assign_official_failed, null);
    }

    /** 0.1.64：无条件重拉官方列表（复用 BUG-4 的 force 语义）。 */
    private void refetchLists() {
        mListsLoaded = false;
        loadOfficialLists(true);
    }

    // ---- 0.1.64：分组管理（官方列表）----
    /** 组头 ⋮ 菜单：修改分组名 / 删除分组。 */
    private void showListMenu(View anchor, UserListsGraphQL.UserList list) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add(Menu.NONE, MENU_RENAME, Menu.NONE,
                R.string.star_list_menu_rename);
        menu.getMenu().add(Menu.NONE, MENU_DELETE, Menu.NONE,
                R.string.star_list_menu_delete);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_RENAME) {
                showRenameListDialog(list);
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                showDeleteListDialog(list);
                return true;
            }
            return false;
        });
        menu.show();
    }

    /** 0.1.64：+ 新增分组（名称 + 设为私密开关，默认公开）。 */
    private void showCreateListDialog() {
        // 215：同上；创建走 ViewModel 单发操作，成功后 onSingleOpChanged
        // Toast + 重拉（与旧回调同款）。
        UserListDialogHelper.showCreateListNameDialog(requireActivity(),
                mOfficialLists, (name, isPrivate) -> {
                    if (mMoveViewModel == null) {
                        return;
                    }
                    mMoveViewModel.runSingleOp(
                            UserListsGraphQL.createList(name, isPrivate),
                            R.string.star_list_created,
                            R.string.star_list_create_failed, null);
                });
    }

    /** 0.1.64：修改分组名（官方 updateUserList，组内仓库自动跟随）。 */
    private void showRenameListDialog(UserListsGraphQL.UserList list) {
        com.gh4a.utils.TextInputDialogHelper.show(requireActivity(),
                R.string.star_list_rename_title, list.name, name -> {
                    if (name.isEmpty() || name.equals(list.name)) return;
                    // 0.2.4（58）：同 assignToOfficialLists，改名走 ViewModel。
                    if (mMoveViewModel == null) {
                        return;
                    }
                    mMoveViewModel.runSingleOp(UserListsGraphQL.renameList(list.id, name),
                            R.string.star_list_renamed, R.string.star_list_rename_failed, null);
                });
    }

    /** 0.1.64：删除分组（官方 deleteUserList；只解散列表，仓库仍保留 Star）。 */
    private void showDeleteListDialog(UserListsGraphQL.UserList list) {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.star_list_delete_title)
                .setMessage(getResources().getQuantityString(
                        R.plurals.star_list_delete_message,
                        list.repos.size(), list.repos.size()))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    // 0.2.4（58）：同 assignToOfficialLists，删组走 ViewModel；
                    // 成功后清掉已展开的该组 id（在结果回调里做，见 onSingleOpChanged）。
                    if (mMoveViewModel == null) {
                        return;
                    }
                    mMoveViewModel.runSingleOp(UserListsGraphQL.deleteList(list.id),
                            R.string.star_list_deleted, R.string.star_list_delete_failed, list.id);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- 0.1.64：多选移组 ----
    /** 长按仓库行：进入多选模式并选中该仓库；已在多选模式时再长按加选。 */
    private void onRepoLongPressed(String fullName) {
        if (mSortMode) return;
        if (!mMultiSelect) {
            mMultiSelect = true;
            updateBackCallback();
        }
        mSelectedRepos.add(fullName);
        rebuild();
    }

    /** 多选模式点行：切换勾选；勾选全空时自动退出多选模式。 */
    private void onRepoClickedInMultiSelect(String fullName) {
        if (mSelectedRepos.contains(fullName)) {
            mSelectedRepos.remove(fullName);
        } else {
            mSelectedRepos.add(fullName);
        }
        if (mSelectedRepos.isEmpty()) {
            exitMultiSelect();
        } else {
            rebuild();
        }
    }

    private void exitMultiSelect() {
        clearMultiSelectState();
        rebuild();
    }

    private void clearMultiSelectState() {
        mMultiSelect = false;
        mSelectedRepos.clear();
        updateBackCallback();
    }

    private void clearSortModeState() {
        if (mSortTouchHelper != null) {
            mSortTouchHelper.attachToRecyclerView(null);
            mSortTouchHelper = null;
        }
        mSortMode = false;
        mSortLists = null;
        updateBackCallback();
    }

    private void updateBackCallback() {
        if (mBackCallback != null) {
            mBackCallback.setEnabled(mMultiSelect || mSortMode);
        }
    }

    /**
     * 0.1.64：移动至分组——与列表页长按同一份“加入列表”多选框。
     * 预勾选规则（用户已批）：勾选所有已选仓库共有的列表（交集）；已选仓库
     * 分属不同列表时交集为空 → 全不勾选。全不勾选 + 确定 = 移出全部列表，
     * 回到未分组，Star 保留。
     */
    private void showMoveDialog() {
        if (mOfficialLists == null || mOfficialLists.isEmpty() || mSelectedRepos.isEmpty()) {
            return;
        }
        Set<String> common = null;
        for (UserListsGraphQL.UserList list : mOfficialLists) {
            Set<String> inList = new java.util.HashSet<>();
            for (UserListsGraphQL.Repo repo : list.repos) {
                inList.add(repo.fullName);
            }
            Set<String> selectedInList = new java.util.HashSet<>(inList);
            selectedInList.retainAll(mSelectedRepos);
            if (selectedInList.size() == mSelectedRepos.size()) {
                if (common == null) {
                    common = new java.util.HashSet<>();
                    common.add(list.id);
                } else {
                    common.add(list.id);
                }
            }
        }
        final Set<String> checked = common != null ? common : new java.util.HashSet<>();
        UserListDialogHelper.showAssignDialog(requireActivity(), mOfficialLists,
                checked, null,
                () -> showCreateListDialogForMove(),
                selectedIds -> startBatchMove(selectedIds));
    }

    /** 0.1.64：移组框里新建列表后按当前选中集合批量移入新列表。 */
    private void showCreateListDialogForMove() {
        // 215：同上；建组 + 批量移入整条链走 ViewModel
        //（createListAndStartMove），旋转不中断。
        UserListDialogHelper.showCreateListNameDialog(requireActivity(),
                mOfficialLists, (name, isPrivate) -> {
                    if (mMoveViewModel == null) {
                        return;
                    }
                    mMoveViewModel.createListAndStartMove(
                            new ArrayList<>(mSelectedRepos), name, isPrivate);
                });
    }

    /**
     * 0.1.64：批量移组——官方无批量接口，N 个仓库 = N 次顺序调用
     * （每仓先取 node id 再 updateListsForItem，见 assignRepo），
     * 带进度与失败重试（只重试失败的）。
     * 0.2.2：进度与失败存在 ViewModel 里（B-M7），旋转屏幕时 Rx 链不中断；
     * 这里只把任务交给 ViewModel，对话框由状态观察者按最新状态重建。
     */
    private void startBatchMove(List<String> listIds) {
        if (mMoveViewModel == null) {
            return;
        }
        mMoveViewModel.start(new ArrayList<>(mSelectedRepos), listIds);
    }

    /** 0.1.64：批量移动进度框（dialog_move_progress.xml，标题随进度更新）。 */
    private AlertDialog buildBatchMoveDialog(int total) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_move_progress, null);
        mMoveProgressBar = view.findViewById(R.id.pb_move);
        mMoveProgressBar.setMax(total);
        mMoveProgressBar.setProgress(0);
        return new AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.star_list_moving, 0, total))
                .setView(view)
                .setCancelable(false)
                .create();
    }

    /**
     * 0.2.2：批量移组状态观察者——按 ViewModel 的最新状态重建对话框。
     * 进行中：进度框不存在就新建，存在就更新标题与进度条；跑完：拆进度框，
     * 全部成功弹 Toast，失败弹结果框（完成／只重试失败的）；结果已处理过
     * （旋转重建）就不再重复弹框。
     */
    private void onMoveStateChanged(StarBatchMoveViewModel.State state) {
        if (state == null) {
            dismissMoveProgressDialog();
            return;
        }
        if (!state.finished) {
            if (mMoveProgressDialog == null || !mMoveProgressDialog.isShowing()) {
                mMoveProgressDialog = buildBatchMoveDialog(state.total);
                mMoveProgressDialog.show();
            }
            // 0.1.64：标题与进度条随进度更新（见 dialog_move_progress.xml 注释）。
            mMoveProgressDialog.setTitle(
                    getString(R.string.star_list_moving, state.done, state.total));
            if (mMoveProgressBar != null) {
                mMoveProgressBar.setProgress(state.done);
            }
            return;
        }
        dismissMoveProgressDialog();
        if (mMoveViewModel == null || mMoveViewModel.isResultConsumed(state)) {
            return;
        }
        int moved = state.total - state.failed.size();
        if (state.failed.isEmpty()) {
            Toast.makeText(getActivity(), getString(R.string.star_list_moved_title,
                    moved, state.total), Toast.LENGTH_SHORT).show();
            mMoveViewModel.markResultConsumed(state);
            exitMultiSelect();
            refetchLists();
            return;
        }
        showBatchMoveResult(state, moved);
    }

    /**
     * 0.2.4（58）：单发操作（入组单发／改名／删组）结果——ViewModel 里的 Rx 链
     * 旋转不中断，重建后按结果 Toast＋重拉；已消费的不重复弹。
     */
    private void onSingleOpChanged(StarBatchMoveViewModel.SingleOp op) {
        if (op == null || mMoveViewModel == null || mMoveViewModel.isSingleOpConsumed(op)) {
            return;
        }
        mMoveViewModel.markSingleOpConsumed(op);
        Toast.makeText(getActivity(), op.messageRes,
                op.success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        if (!op.success) {
            return;
        }
        if (op.clearExpandedId != null
                && TextUtils.equals(mExpandedOfficialId, op.clearExpandedId)) {
            mExpandedOfficialId = null;
        }
        refetchLists();
    }

    private void showBatchMoveResult(StarBatchMoveViewModel.State state, int moved) {
        List<String> failed = state.failed;
        List<String> targetIds = state.targetIds;
        StringBuilder message = new StringBuilder();
        message.append(getString(targetIds.isEmpty()
                ? R.string.star_list_move_out_note : R.string.star_list_move_done_note, moved));
        // 失败的仓库名（最多列 3 个）。
        List<String> names = failed.size() > 3 ? failed.subList(0, 3) : failed;
        message.append("\n").append(getString(R.string.star_list_move_failed, failed.size(),
                TextUtils.join("、", names) + (failed.size() > 3 ? "…" : "")));
        dismissMoveResultDialog();
        mMoveResultDialog = new AlertDialog.Builder(requireActivity())
                .setTitle(getString(R.string.star_list_moved_title, moved, state.total))
                .setMessage(message.toString())
                .setPositiveButton(R.string.star_list_done, (d, w) -> {
                    mMoveViewModel.markResultConsumed(state);
                    exitMultiSelect();
                    refetchLists();
                })
                .setNegativeButton(R.string.star_list_retry_failed, (d, w) -> {
                    // 只重试失败的仓库（失败列表存在 ViewModel 里，不依赖
                    // 已被 onDestroyView 清掉的 mSelectedRepos）。
                    mMoveViewModel.retryFailed();
                })
                .setCancelable(false)
                .create();
        mMoveResultDialog.show();
    }

    private void dismissMoveProgressDialog() {
        if (mMoveProgressDialog != null) {
            if (mMoveProgressDialog.isShowing()) {
                mMoveProgressDialog.dismiss();
            }
            mMoveProgressDialog = null;
        }
        mMoveProgressBar = null;
    }

    private void dismissMoveResultDialog() {
        if (mMoveResultDialog != null) {
            if (mMoveResultDialog.isShowing()) {
                mMoveResultDialog.dismiss();
            }
            mMoveResultDialog = null;
        }
    }

    // ---- 0.1.64：长按排序（顺序只存本机）----
    /** 长按组头 → 排序模式（拖动手柄 ≡ 拖动，底部 完成/取消）。 */
    private void enterSortMode() {
        if (mOfficialLists == null || mOfficialLists.size() < 2) {
            return;
        }
        mSortMode = true;
        mSortLists = new ArrayList<>(mOfficialLists);
        updateBackCallback();
        rebuild();
        attachSortTouchHelper();
    }

    private void exitSortMode(boolean save) {
        if (save && mSortLists != null) {
            saveListOrder(mSortLists);
            mOfficialLists = mSortLists;
        }
        clearSortModeState();
        rebuild();
    }

    private UserListsGraphQL.UserList findListById(String id) {
        if (mOfficialLists == null) return null;
        for (UserListsGraphQL.UserList list : mOfficialLists) {
            if (TextUtils.equals(list.id, id)) return list;
        }
        return null;
    }

    /** 0.1.64：排序模式的拖动——只允许排序行之间换位（提示行/底部栏不可拖入）。 */
    private void attachSortTouchHelper() {
        if (mList == null) return;
        if (mSortTouchHelper != null) {
            mSortTouchHelper.attachToRecyclerView(null);
        }
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false;
                }
                if (mItems.get(from).type != TYPE_SORT_ROW
                        || mItems.get(to).type != TYPE_SORT_ROW) {
                    return false;
                }
                if (mSortLists != null) {
                    // 第 0 行是排序提示行，排序行下标 = 适配器下标 - 1。
                    int listFrom = from - 1;
                    int listTo = to - 1;
                    if (listFrom >= 0 && listFrom < mSortLists.size()
                            && listTo >= 0 && listTo < mSortLists.size()) {
                        Collections.swap(mSortLists, listFrom, listTo);
                        Collections.swap(mItems, from, to);
                        mAdapter.notifyItemMoved(from, to);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false; // 0.1.64：只用手柄拖动，长按不触发。
            }
        };
        mSortTouchHelper = new ItemTouchHelper(callback);
        mSortTouchHelper.attachToRecyclerView(mList);
    }

    /** 0.1.64：列表显示顺序只存本机（官方接口无排序能力），按账号存 ID 顺序。 */
    private SharedPreferences listOrderPrefs() {
        String login = mLogin != null ? mLogin
                : com.gh4a.Gh4Application.get().getAuthLogin();
        return requireActivity().getSharedPreferences(
                PREFS_LIST_ORDER + "_" + login, Context.MODE_PRIVATE);
    }

    private void saveListOrder(List<UserListsGraphQL.UserList> lists) {
        JSONArray ids = new JSONArray();
        for (UserListsGraphQL.UserList list : lists) {
            ids.put(list.id);
        }
        listOrderPrefs().edit().putString(KEY_LIST_ORDER_IDS, ids.toString()).apply();
    }

    /** 0.1.64：拉取/缓存后按保存顺序重排；新列表排最后，已删除的去掉。 */
    private void applySavedListOrder() {
        if (mOfficialLists == null || mOfficialLists.isEmpty()) {
            return;
        }
        String saved = listOrderPrefs().getString(KEY_LIST_ORDER_IDS, null);
        if (saved == null) {
            return;
        }
        List<String> order = new ArrayList<>();
        try {
            JSONArray ids = new JSONArray(saved);
            for (int i = 0; i < ids.length(); i++) {
                order.add(ids.getString(i));
            }
        } catch (JSONException e) {
            return;
        }
        Map<String, UserListsGraphQL.UserList> byId = new LinkedHashMap<>();
        for (UserListsGraphQL.UserList list : mOfficialLists) {
            byId.put(list.id, list);
        }
        List<UserListsGraphQL.UserList> sorted = new ArrayList<>();
        for (String id : order) {
            UserListsGraphQL.UserList list = byId.remove(id);
            if (list != null) {
                sorted.add(list);
            }
        }
        // 新列表（不在保存顺序里）排最后。
        sorted.addAll(byId.values());
        mOfficialLists = sorted;
    }

    private class MergedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case TYPE_SECTION:
                case TYPE_STATUS:
                case TYPE_SORT_HEADER: {
                    TextView tv = new TextView(parent.getContext());
                    int pad = (int) (12 * getResources().getDisplayMetrics().density + 0.5f);
                    tv.setPadding(pad, pad, pad, pad);
                    if (viewType == TYPE_SECTION) {
                        tv.setTextSize(12);
                        tv.setTypeface(null, android.graphics.Typeface.BOLD);
                        tv.setTextColor(UiUtils.resolveColor(parent.getContext(), androidx.appcompat.R.attr.colorPrimary));
                    } else {
                        tv.setTextColor(ContextCompat.getColor(parent.getContext(),
                                android.R.color.secondary_text_light));
                    }
                    return new RecyclerView.ViewHolder(tv) {};
                }
                case TYPE_LIST:
                case TYPE_LIST_REPO:
                case TYPE_UNGROUPED:
                case TYPE_SORT_ROW:
                    return new GroupHolder(inflater.inflate(R.layout.row_star_group, parent, false));
                case TYPE_ADD_GROUP: {
                    // 0.1.65：+ 新增分组——主色文字的纯文字行，无边框无背景。
                    TextView tv = new TextView(parent.getContext());
                    int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
                    tv.setPadding(pad, pad, pad, pad);
                    tv.setTextColor(UiUtils.resolveColor(parent.getContext(),
                            androidx.appcompat.R.attr.colorPrimary));
                    return new RecyclerView.ViewHolder(tv) {};
                }
                case TYPE_SELECTION_HEADER:
                    return new SelectionHeaderHolder(inflater.inflate(
                            R.layout.row_star_list_selection_header, parent, false));
                case TYPE_ACTION_BAR:
                    return new ActionBarHolder(inflater.inflate(
                            R.layout.row_star_actionbar, parent, false));
                case TYPE_STARRED_REPO:
                    return new RepoHolder(inflater.inflate(R.layout.row_repo, parent, false));
                default:
                    return new UpdateHolder(inflater.inflate(R.layout.row_release_radar, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = mItems.get(position);
            if (holder instanceof GroupHolder) {
                GroupHolder h = (GroupHolder) holder;
                if (item.type == TYPE_SORT_ROW) {
                    bindSortRow(h, item);
                    return;
                }
                float density = getResources().getDisplayMetrics().density;
                int indent = (int) (32 * density + 0.5f);
                // 0.1.2：组头与仓库行不再显示书形图标（分组名/仓库名 + 简介即可）。
                h.icon.setVisibility(View.GONE);
                // 标题统一用趋势页同款字体（ItemTitle，sans-serif-condensed）。
                h.title.setTextAppearance(R.style.TextAppearance_ItemTitle);
                h.title.setText(item.title);
                if (item.type == TYPE_LIST || item.type == TYPE_UNGROUPED) {
                    // 0.1.10：组头改卡片式——圆角卡片背景 + 阴影 + 左彩条 +
                    // 首字母圆标 + 数量胶囊 + 箭头（展开时箭头转 90°）。
                    // 副标题只显示分组简介（官方列表自带简介，无简介时隐藏）；
                    // 数量只放右侧胶囊，不再与副标题重复。
                    // 0.1.64：未分组区复用同一卡片（无身份色、无 ⋮ 菜单）。
                    h.itemView.setBackgroundResource(R.drawable.star_group_card);
                    h.itemView.setElevation(4 * density);
                    setGroupCardMargins(h.itemView, (int) (12 * density + 0.5f),
                            (int) (6 * density + 0.5f));
                    h.itemView.setPadding(h.basePaddingLeft,
                            h.itemView.getPaddingTop(), h.itemView.getPaddingRight(),
                            h.itemView.getPaddingBottom());
                    if (item.type == TYPE_UNGROUPED) {
                        // 0.1.64：未分组区无身份色（无彩条/首字母圆标/简介）。
                        h.colorBar.setVisibility(View.GONE);
                        h.letter.setVisibility(View.GONE);
                        // 0.2.2：Star 未加载完时副标题显示“仅显示已加载的 N 条”，
                        // 加载完则隐藏（与 TYPE_LIST 的简介共用同一行）。
                        h.subtitle.setText(item.description);
                        h.subtitle.setVisibility(TextUtils.isEmpty(item.description)
                                ? View.GONE : View.VISIBLE);
                    } else {
                        // 0.1.20 F-24: hashCode()==MIN_VALUE 时 Math.abs 仍为负会越界。
                        int color = GROUP_COLORS[Math.floorMod(item.title.hashCode(),
                                GROUP_COLORS.length)];
                        h.colorBar.setVisibility(View.VISIBLE);
                        h.colorBar.setBackgroundColor(color);
                        h.letter.setVisibility(View.VISIBLE);
                        h.letter.setText(!TextUtils.isEmpty(item.title)
                                ? item.title.substring(0, 1).toUpperCase() : "?");
                        android.graphics.drawable.GradientDrawable circle =
                                new android.graphics.drawable.GradientDrawable();
                        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        circle.setColor(color);
                        h.letter.setBackground(circle);
                        h.subtitle.setText(item.description);
                        h.subtitle.setVisibility(TextUtils.isEmpty(item.description)
                                ? View.GONE : View.VISIBLE);
                        h.subtitle.setTypeface(android.graphics.Typeface.DEFAULT);
                    }
                    h.pill.setVisibility(View.VISIBLE);
                    h.pill.setText(item.subtitle);
                    // 0.1.12：数量胶囊跟主题走（主色浅底 + 深主色文字），不再是固定浅绿；
                    // 组头左彩条/首字母圆标是按组名取的分组身份色，保持不变。
                    {
                        int primary = UiUtils.resolveColor(h.itemView.getContext(),
                                androidx.appcompat.R.attr.colorPrimary);
                        int primaryDark = UiUtils.resolveColor(h.itemView.getContext(),
                                androidx.appcompat.R.attr.colorPrimaryDark);
                        android.graphics.drawable.GradientDrawable pillBg =
                                new android.graphics.drawable.GradientDrawable();
                        pillBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                        pillBg.setCornerRadius(12 * density);
                        pillBg.setColor((primary & 0x00FFFFFF) | 0x14000000);
                        h.pill.setBackground(pillBg);
                        h.pill.setTextColor(primaryDark);
                    }
                    if (h.divider != null) h.divider.setVisibility(View.GONE);
                    // 0.1.64：多选时组头不显示箭头；组头行无复选框。
                    h.chevron.setVisibility(mMultiSelect ? View.GONE : View.VISIBLE);
                    h.chevron.setRotation(item.expanded ? 90 : 0);
                    h.checkbox.setVisibility(View.GONE);
                    // 0.1.64：组头 ⋮ 菜单（多选时隐藏；未分组区无菜单）。
                    if (item.type == TYPE_LIST && !mMultiSelect) {
                        h.menu.setVisibility(View.VISIBLE);
                        h.menu.setOnClickListener(v -> {
                            UserListsGraphQL.UserList list = findListById(item.key);
                            if (list != null) {
                                showListMenu(v, list);
                            }
                        });
                    } else {
                        h.menu.setVisibility(View.GONE);
                        h.menu.setOnClickListener(null);
                    }
                } else {
                    // 组下仓库行：恢复平面行（无卡片、无阴影、无装饰），
                    // 标题趋势字体 + 简介正常小字（非等宽）。
                    h.itemView.setElevation(0);
                    com.gh4a.utils.UiUtils.setSelectableBackground(h.itemView);
                    setGroupCardMargins(h.itemView, 0, 0);
                    h.itemView.setPadding(h.basePaddingLeft + indent,
                            h.itemView.getPaddingTop(), h.itemView.getPaddingRight(),
                            h.itemView.getPaddingBottom());
                    h.colorBar.setVisibility(View.GONE);
                    h.letter.setVisibility(View.GONE);
                    h.pill.setVisibility(View.GONE);
                    h.chevron.setVisibility(View.GONE);
                    h.subtitle.setText(item.subtitle);
                    h.subtitle.setVisibility(item.subtitle == null || item.subtitle.isEmpty()
                            ? View.GONE : View.VISIBLE);
                    h.subtitle.setTypeface(android.graphics.Typeface.DEFAULT);
                    // 0.1.12：组下仓库行之间加分隔线（与趋势页同款 listDivider）；
                    // 同组最后一个仓库行、组头卡片不显示分隔线。
                    if (h.divider != null) {
                        int pos = holder.getBindingAdapterPosition();
                        boolean nextIsRepo = pos != RecyclerView.NO_POSITION
                                && pos + 1 < mItems.size()
                                && mItems.get(pos + 1).type == TYPE_LIST_REPO;
                        h.divider.setVisibility(nextIsRepo ? View.VISIBLE : View.GONE);
                    }
                    h.menu.setVisibility(View.GONE);
                    h.menu.setOnClickListener(null);
                    // 0.1.64：多选模式显示复选框（主色勾选）；点框即切换勾选。
                    boolean checked = mMultiSelect && mSelectedRepos.contains(item.fullName);
                    h.checkbox.setVisibility(mMultiSelect ? View.VISIBLE : View.GONE);
                    h.checkbox.setChecked(checked);
                    h.checkbox.setOnClickListener(v ->
                            onRepoClickedInMultiSelect(item.fullName));
                }
                holder.itemView.setOnClickListener(v -> {
                    if (item.type == TYPE_LIST) {
                        mExpandedOfficialId = TextUtils.equals(mExpandedOfficialId, item.key)
                                ? null : item.key;
                        rebuild();
                    } else if (item.type == TYPE_UNGROUPED) {
                        mUngroupedExpanded = !mUngroupedExpanded;
                        rebuild();
                    } else if (mMultiSelect) {
                        onRepoClickedInMultiSelect(item.fullName);
                    } else {
                        openRepo(item.fullName);
                    }
                });
                holder.itemView.setOnLongClickListener(v -> {
                    if (item.type == TYPE_LIST) {
                        // 0.1.64：长按组头 → 排序模式（多选时不进排序）。
                        if (!mMultiSelect) {
                            enterSortMode();
                            return true;
                        }
                        return false;
                    }
                    if (item.type == TYPE_LIST_REPO) {
                        // 0.1.64：长按仓库行 → 多选模式（排序时不进多选）。
                        onRepoLongPressed(item.fullName);
                        return true;
                    }
                    return false;
                });
            } else if (holder instanceof SelectionHeaderHolder) {
                SelectionHeaderHolder h = (SelectionHeaderHolder) holder;
                h.label.setText(item.title);
                h.cancel.setOnClickListener(v -> exitMultiSelect());
            } else if (holder instanceof ActionBarHolder) {
                ActionBarHolder h = (ActionBarHolder) holder;
                if (item.actionKind == ACTION_BAR_MOVE) {
                    h.count.setVisibility(View.VISIBLE);
                    h.count.setText(item.title);
                    h.cancel.setOnClickListener(v -> exitMultiSelect());
                    h.primary.setText(R.string.star_list_move_to);
                    h.primary.setOnClickListener(v -> showMoveDialog());
                } else {
                    // 排序模式底部栏：无左标签，只有 取消 / 完成。
                    h.count.setVisibility(View.GONE);
                    h.cancel.setOnClickListener(v -> exitSortMode(false));
                    h.primary.setText(R.string.star_list_done);
                    h.primary.setOnClickListener(v -> exitSortMode(true));
                }
            } else if (holder instanceof RepoHolder) {
                RepoHolder h = (RepoHolder) holder;
                Repository repo = item.repo;
                h.title.setText(ApiHelpers.formatRepoName(holder.itemView.getContext(), repo));
                if (!StringUtils.isBlank(repo.description())) {
                    h.desc.setVisibility(View.VISIBLE);
                    h.desc.setText(StringUtils.parseEmojiToUnicode(repo.description()));
                } else {
                    h.desc.setVisibility(View.GONE);
                }
                StringBuilder meta = new StringBuilder();
                if (repo.isPrivate()) {
                    meta.append(holder.itemView.getContext().getString(R.string.repo_type_private)).append("  ·  ");
                }
                if (repo.isFork()) {
                    meta.append(holder.itemView.getContext().getString(R.string.fork)).append("  ·  ");
                }
                meta.append(repo.language() != null
                        ? repo.language() : holder.itemView.getContext().getString(R.string.unknown));
                meta.append("  ★ ").append(repo.stargazersCount());
                meta.append("  ⑂ ").append(repo.forksCount());
                meta.append("  ").append(Formatter.formatFileSize(holder.itemView.getContext(),
                        1024L * repo.size()));
                h.meta.setText(meta.toString());
                holder.itemView.setOnClickListener(v -> {
                    if (getActivity() != null) {
                        startActivity(RepositoryActivity.makeIntent(getActivity(), repo));
                    }
                });
                holder.itemView.setOnLongClickListener(v -> {
                    showAssignToGroupDialog(repo);
                    return true;
                });
            } else if (holder instanceof UpdateHolder) {
                UpdateHolder h = (UpdateHolder) holder;
                ReleaseRadarFragment.RadarItem radar = item.radar;
                String name = TextUtils.isEmpty(radar.releaseName) ? radar.tagName : radar.releaseName;
                h.release.setText(name);
                h.repo.setText(radar.owner + "/" + radar.repo);
                h.tag.setText(radar.tagName);
                long time = radar.publishedAt != 0 ? radar.publishedAt : radar.createdAt;
                h.time.setText(com.gh4a.utils.StringUtils.formatRelativeTime(
                        holder.itemView.getContext(), new java.util.Date(time), true));
                holder.itemView.setAlpha(isUpdateRead(radar) ? 0.45f : 1.0f);
                holder.itemView.setOnClickListener(v -> {
                    markUpdateRead(radar);
                    if (getActivity() != null) {
                        startActivity(ReleaseInfoActivity.makeIntent(getActivity(),
                                radar.owner, radar.repo, radar.releaseId));
                    }
                });
            } else {
                ((TextView) holder.itemView).setText(item.title);
                if (item.markAllRead) {
                    ((TextView) holder.itemView).setTextColor(UiUtils.resolveColor(holder.itemView.getContext(),
                            androidx.appcompat.R.attr.colorPrimary));
                    holder.itemView.setOnClickListener(v -> markAllUpdatesRead());
                } else if (item.retryLists) {
                    ((TextView) holder.itemView).setTextColor(UiUtils.resolveColor(holder.itemView.getContext(),
                            androidx.appcompat.R.attr.colorPrimary));
                    holder.itemView.setOnClickListener(v -> {
                        mListsLoaded = false;
                        loadOfficialLists(true);
                    });
                } else if (item.retryUpdates) {
                    ((TextView) holder.itemView).setTextColor(UiUtils.resolveColor(holder.itemView.getContext(),
                            androidx.appcompat.R.attr.colorPrimary));
                    holder.itemView.setOnClickListener(v -> loadUpdates(true));
                } else if (item.retryStarred) {
                    ((TextView) holder.itemView).setTextColor(UiUtils.resolveColor(holder.itemView.getContext(),
                            androidx.appcompat.R.attr.colorPrimary));
                    holder.itemView.setOnClickListener(v -> loadStarredPage(true, true));
                } else if (item.type == TYPE_ADD_GROUP) {
                    // 0.1.64：+ 新增分组（主色行，点击新建官方列表）。
                    holder.itemView.setOnClickListener(v -> showCreateListDialog());
                } else {
                    // 回收复用时恢复状态行默认色，避免“全部已读”的主色串到状态行。
                    ((TextView) holder.itemView).setTextColor(ContextCompat.getColor(
                            holder.itemView.getContext(),
                            android.R.color.secondary_text_light));
                    holder.itemView.setOnClickListener(null);
                }
            }
        }

        // 0.1.64：排序行绑定——拖动手柄 ≡ 仅用于拖动，无点击动作；
        // setOnTouchListener 是 ItemTouchHelper.startDrag 的标准写法，
        // 故抑制 ClickableViewAccessibility。
        @SuppressLint("ClickableViewAccessibility")
        private void bindSortRow(GroupHolder h, Item item) {
            // 排序行——复用组行布局，平面行，只留标题 + 拖动手柄 ≡。
            h.itemView.setElevation(0);
            com.gh4a.utils.UiUtils.setSelectableBackground(h.itemView);
            setGroupCardMargins(h.itemView, 0, 0);
            h.itemView.setPadding(h.basePaddingLeft,
                    h.itemView.getPaddingTop(), h.itemView.getPaddingRight(),
                    h.itemView.getPaddingBottom());
            h.icon.setVisibility(View.GONE);
            h.colorBar.setVisibility(View.GONE);
            h.letter.setVisibility(View.GONE);
            h.pill.setVisibility(View.GONE);
            h.chevron.setVisibility(View.GONE);
            h.checkbox.setVisibility(View.GONE);
            h.menu.setVisibility(View.GONE);
            h.menu.setOnClickListener(null);
            if (h.divider != null) h.divider.setVisibility(View.GONE);
            h.title.setTextAppearance(R.style.TextAppearance_ItemTitle);
            h.title.setText(item.title);
            h.subtitle.setVisibility(View.GONE);
            h.drag.setVisibility(View.VISIBLE);
            // 拖动手柄按住即开始拖动（ItemTouchHelper.startDrag）。
            h.drag.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                        && mSortTouchHelper != null) {
                    mSortTouchHelper.startDrag(h);
                    return true;
                }
                return false;
            });
            h.itemView.setOnClickListener(null);
            h.itemView.setOnLongClickListener(null);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).type;
        }
    }

    private static class GroupHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView subtitle;
        final View colorBar;
        final TextView letter;
        final TextView pill;
        final View divider;
        final TextView chevron;
        final CheckBox checkbox;
        final TextView menu;
        final TextView drag;
        final int basePaddingLeft;
        GroupHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.iv_icon);
            title = v.findViewById(R.id.tv_title);
            subtitle = v.findViewById(R.id.tv_subtitle);
            colorBar = v.findViewById(R.id.v_color_bar);
            letter = v.findViewById(R.id.tv_letter);
            pill = v.findViewById(R.id.tv_count_pill);
            divider = v.findViewById(R.id.v_divider);
            chevron = v.findViewById(R.id.tv_chevron);
            checkbox = v.findViewById(R.id.cb_select);
            menu = v.findViewById(R.id.tv_more);
            drag = v.findViewById(R.id.tv_drag);
            basePaddingLeft = v.getPaddingLeft();
        }
    }

    private static class SelectionHeaderHolder extends RecyclerView.ViewHolder {
        final TextView label;
        final TextView cancel;
        SelectionHeaderHolder(View v) {
            super(v);
            label = v.findViewById(R.id.tv_label);
            cancel = v.findViewById(R.id.tv_cancel);
        }
    }

    private static class ActionBarHolder extends RecyclerView.ViewHolder {
        final TextView count;
        final Button cancel;
        final Button primary;
        ActionBarHolder(View v) {
            super(v);
            count = v.findViewById(R.id.tv_count);
            cancel = v.findViewById(R.id.btn_cancel);
            primary = v.findViewById(R.id.btn_primary);
        }
    }

    // 0.1.10：分组卡片配色（按组名哈希取色，同一分组始终同一颜色）。
    private static final int[] GROUP_COLORS = {
            0xFF65B345, 0xFF559639, 0xFF6945B3, 0xFFB34545,
            0xFF2A7A8A, 0xFFFB7299, 0xFF8A8A8A,
    };

    /** 0.1.10：组头卡片外边距；仓库行复用时必须清零，否则复用会串边距。 */
    private static void setGroupCardMargins(View itemView, int side, int vertical) {
        if (itemView.getLayoutParams() instanceof RecyclerView.LayoutParams) {
            RecyclerView.LayoutParams params =
                    (RecyclerView.LayoutParams) itemView.getLayoutParams();
            params.setMargins(side, vertical, side, vertical);
            itemView.setLayoutParams(params);
        }
    }

    private static class RepoHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView desc;
        final TextView meta;
        RepoHolder(View v) {
            super(v);
            title = v.findViewById(R.id.tv_title);
            desc = v.findViewById(R.id.tv_desc);
            meta = v.findViewById(R.id.tv_meta);
        }
    }

    private static class UpdateHolder extends RecyclerView.ViewHolder {
        final TextView release;
        final TextView repo;
        final TextView tag;
        final TextView time;
        UpdateHolder(View v) {
            super(v);
            release = v.findViewById(R.id.tv_release);
            repo = v.findViewById(R.id.tv_repo);
            tag = v.findViewById(R.id.tv_tag);
            time = v.findViewById(R.id.tv_time);
        }
    }
}
