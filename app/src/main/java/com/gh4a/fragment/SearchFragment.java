package com.gh4a.fragment;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FilterQueryProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.Gh4Application;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.FileViewerActivity;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.adapter.SearchAdapter;
import com.gh4a.db.SuggestionsProvider;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.UiUtils;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.StringUtils;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchCode;
import com.meisolsson.githubsdk.model.TextMatch;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.search.SearchService;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import java.util.HashMap;

import retrofit2.Response;

public class SearchFragment extends PagedDataBaseFragment<Object> implements
        SearchView.OnQueryTextListener, SearchView.OnCloseListener,
        SearchView.OnSuggestionListener, FilterQueryProvider, SearchAdapter.Callback {
    public static SearchFragment newInstance(int initialType, String initialQuery,
            boolean startSearchImmediately) {
        SearchFragment f = new SearchFragment();
        Bundle args = new Bundle();
        args.putInt("search_type", initialType);
        args.putString("initial_search", initialQuery);
        args.putBoolean("do_initial_load", startSearchImmediately);
        f.setArguments(args);
        return f;
    }

    public static final int SEARCH_TYPE_REPO = 0;
    public static final int SEARCH_TYPE_USER = 1;
    public static final int SEARCH_TYPE_CODE = 2;

    private static final int[][] HINT_AND_EMPTY_TEXTS = {
        { R.string.search_hint_repo, R.string.no_search_repos_found },
        { R.string.search_hint_user, R.string.no_search_users_found },
        { R.string.search_hint_code, R.string.no_search_code_found }
    };

    private static final String[] SUGGESTION_PROJECTION = {
            SuggestionsProvider.Columns._ID, SuggestionsProvider.Columns.SUGGESTION
    };
    private static final String SUGGESTION_SELECTION =
            SuggestionsProvider.Columns.TYPE + " = ? AND " +
                    SuggestionsProvider.Columns.SUGGESTION + " LIKE ?";
    private static final String SUGGESTION_ORDER = SuggestionsProvider.Columns.DATE + " DESC";

    private static final String STATE_KEY_QUERY = "query";
    private static final String STATE_KEY_SEARCH_TYPE = "search_type";

    private static final String PREF_KEY_SORT_PREFIX = "search_sort_";

    private static final int[] SORT_MENU_RES_IDS = {
        R.menu.search_sort_repo,
        R.menu.search_sort_user,
        R.menu.search_sort_code
    };

    // 0.1.20 D-06: 排序选项表见 SearchSortOption（菜单 id → 字段/方向/
    // 标题资源/适用类型，一张表替代此前的 SORT_LOOKUP + sortTitleRes switch）。

    private SearchAdapter mAdapter;

    // 商店视图：搜索页签 + 仓库类型 + 空查询时挂出，替代最近搜索/热门头。
    private StoreView mStoreView;

    private SearchView mSearch;
    // 0.1.16：搜索顶栏贴边用的工具栏原始内边距（退出搜索页时恢复，
    // 避免影响趋势等其他共用同一工具栏的页面）。
    private int mToolbarInsetStart;
    private int mToolbarInsetEnd;
    private int mToolbarInsetStartWithNav;
    private boolean mToolbarInsetsSaved;
    // 0.1.20 F-06: capSearchBoxWidth() also zeroes these paddings, so they
    // must be snapshotted and restored together with the content insets.
    private int mToolbarPadLeft, mToolbarPadTop, mToolbarPadRight, mToolbarPadBottom;
    private int mMenuPadLeft, mMenuPadTop, mMenuPadRight, mMenuPadBottom;
    private boolean mToolbarPaddingSaved;
    private int mSelectedSearchType;
    private View mDefaultHeader;
    private java.util.List<com.meisolsson.githubsdk.model.Repository> mHotRepos;
    private Disposable mHotDisposable;
    // 0.1.10：下拉刷新时强制走网络（不先显示缓存），否则 BUG-25 的
    // “刷新真的刷新”会被缓存显示盖掉。
    private boolean mHotForceRefresh;
    private String mQuery;
    private String mSort;
    private String mOrder;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            mQuery = savedInstanceState.getString(STATE_KEY_QUERY);
            mSelectedSearchType = savedInstanceState.getInt(STATE_KEY_SEARCH_TYPE, SEARCH_TYPE_REPO);
        } else {
            Bundle args = getArguments();
            if (args != null) {
                mSelectedSearchType = args.getInt("search_type", SEARCH_TYPE_REPO);
                mQuery = args.getString("initial_search");
            }
        }
        // 0.1.20 F-07: 恢复的空查询即无查询（"" 与 null 等价），否则旋转后
        // shouldDoInitialLoad 会把空查询当作“恢复上次加载”而误出个人仓库。
        if (TextUtils.isEmpty(mQuery)) {
            mQuery = null;
        }
        restoreSortSelection(mSelectedSearchType);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search, menu);

        SuggestionAdapter adapter = new SuggestionAdapter(getActivity());
        adapter.setFilterQueryProvider(this);

        mSearch = (SearchView) menu.findItem(R.id.search).getActionView();
        mSearch.setIconifiedByDefault(true);
        // 0.1.1：搜索框展开显示，但不自动聚焦、不弹输入法（此前 requestFocus
        // 会在进搜索页时立刻弹出键盘）。
        mSearch.onActionViewExpanded();
        mSearch.setIconified(false);
        mSearch.clearFocus();
        // 0.1.19：搜索框不再跟菜单区格子排布较劲。0.1.17（铺满菜单区 +
        // 48dp 定宽 + 顶栏宽 − 96dp）与 0.1.18（实测量宽 + 逐步缩 +
        // 可见校验）两套事后算宽都在真机上把排序挤没了。现改为最简单
        // 的做法：搜索框最大宽直接定为顶栏宽的 60%，类型与排序自然
        // 量宽——搜索框永远挤不到排序，排序按构造可见；类型贴左只靠
        // 顶栏内边距清零。退出搜索页时 onDestroyView 恢复原始内边距。
        capSearchBoxWidth();
        mSearch.setOnQueryTextListener(this);
        mSearch.setOnCloseListener(this);
        mSearch.setOnSuggestionListener(this);
        mSearch.setSuggestionsAdapter(adapter);
        if (mQuery != null) {
            mSearch.setQuery(mQuery, false);
        }

        updateSearchViewHint();
        updateTypeMenuItem(menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateTypeMenuItem(menu);
        syncPlatformMenuItem(menu);
        capSearchBoxWidth();
    }

    /**
     * 0.1.58：平台按钮已删除（平台改由各页的系统/平台网格切换），这里只同步
     * 排序与类型图标：商店可见时两者隐藏（商店不参与排序、本身就是仓库维度）；
     * 商店隐藏时两者恢复。
     * 0.1.51：商店可见时搜索框前面的类型图标也去掉（原型顶栏没有它，
     * 商店本身就是仓库维度）；出结果／独立搜索页时类型图标恢复，
     * 切用户／代码搜索还靠它。
     */
    private void syncPlatformMenuItem(Menu menu) {
        boolean storeVisible = mStoreView != null
                && mStoreView.getVisibility() == View.VISIBLE;
        MenuItem sortItem = menu.findItem(R.id.sort);
        if (sortItem != null) {
            sortItem.setVisible(!storeVisible);
        }
        MenuItem typeItem = menu.findItem(R.id.type);
        if (typeItem != null) {
            typeItem.setVisible(!storeVisible);
        }
    }

    /**
     * 0.1.19：搜索顶栏简单做法。搜索框最大宽 = 顶栏宽的 60%，类型与
     * 排序自然量宽，不铺满菜单区、不定宽、不做多轮缩宽校验——搜索框
     * 按构造挤不到排序，排序一定可见。类型贴左靠顶栏内边距清零与
     * 菜单区 padding 清零。幂等：可由 onCreateOptionsMenu 与
     * onPrepareOptionsMenu 重复调用。
     */
    // B-L13：ActionMenuItemView 是受限 API；公开 API 里没有能区分菜单项
    // 视图与溢出按钮的类型（两者都是 AppCompatImageButton 子类），故压制。
    @SuppressLint("RestrictedApi")
    private void capSearchBoxWidth() {
        if (!isAdded() || mSearch == null || getActivity() == null) return;
        final androidx.appcompat.widget.Toolbar toolbar =
                getActivity().findViewById(R.id.toolbar);
        if (toolbar == null) return;
        final int[] attempts = new int[1];
        toolbar.post(new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || mSearch == null) return;
                if (!mToolbarInsetsSaved) {
                    mToolbarInsetStart = toolbar.getContentInsetStart();
                    mToolbarInsetEnd = toolbar.getContentInsetEnd();
                    mToolbarInsetStartWithNav = toolbar.getContentInsetStartWithNavigation();
                    mToolbarInsetsSaved = true;
                }
                if (!mToolbarPaddingSaved) {
                    mToolbarPadLeft = toolbar.getPaddingLeft();
                    mToolbarPadTop = toolbar.getPaddingTop();
                    mToolbarPadRight = toolbar.getPaddingRight();
                    mToolbarPadBottom = toolbar.getPaddingBottom();
                    View menuParent = (View) mSearch.getParent();
                    if (menuParent instanceof androidx.appcompat.widget.ActionMenuView) {
                        mMenuPadLeft = menuParent.getPaddingLeft();
                        mMenuPadTop = menuParent.getPaddingTop();
                        mMenuPadRight = menuParent.getPaddingRight();
                        mMenuPadBottom = menuParent.getPaddingBottom();
                    }
                    mToolbarPaddingSaved = true;
                }
                if (toolbar.getWidth() == 0) {
                    if (attempts[0]++ < 20) toolbar.post(this);
                    return;
                }
                // 0.1.41：只清零右侧内边距（给搜索框与排序让宽），左侧内边距
                // 恢复为保存的原始值——搜索页顶栏标题“搜索”的左边距与趋势页
                // “趋势”同款（同一工具栏的 contentInsetStart），不再贴边。
                // 之前左右全清零会把标题也拖到 0，导致搜索标题比趋势标题贴边。
                toolbar.setContentInsetsRelative(mToolbarInsetStart, 0);
                toolbar.setContentInsetsAbsolute(mToolbarInsetStart, 0);
                toolbar.setContentInsetStartWithNavigation(mToolbarInsetStartWithNav);
                toolbar.setPadding(mToolbarPadLeft, mToolbarPadTop, 0, mToolbarPadBottom);
                // 0.1.61：② 改成紧贴标题（见下），其余三段不变；排序位置与距右沿不变。
                // 0.1.43：四段间距一次统一为趋势页同款值（= 标题左内边距
                // mToolbarInsetStart，标准 16dp），排序距右沿不动：
                // ① 左边栏→搜索标题 = 内容内边距（0.1.41 已恢复）；
                // ② 搜索标题→类型图标 = 菜单区左内边距 + 类型图标在项内
                // 的居中余量，实测项宽与图标宽算出所需菜单左内边距补齐；
                // ③ 类型图标→搜索框 = 类型项右余 + 搜索框内放大镜左偏，
                // 搜索框内板左右内边距清零、放大镜按钮缩到图标宽贴左，
                // 放大镜左偏归零；④ 搜索框→排序图标 = 内板右内边距
                // （已清零）+ 排序图标居中余量。菜单左内边距把整排菜单
                // 右推多少，搜索框上限就减多少，排序位置与距右沿不变。
                // 0.1.61：类型图标紧贴“搜索”标题（0.1.43 是等距排布，
                // 标题→图标 16dp + 图标在项内居中余量，吃掉搜索框的宽）。
                // 改法：菜单左内边距清零 + 类型项收窄到图标宽 + 两侧各 4dp，
                // 标题→图标只剩 4dp；搜索框上限相应放宽（见下）。
                float density = toolbar.getResources().getDisplayMetrics().density;
                int menuPadLeft = 0;
                View parent = (View) mSearch.getParent();
                if (parent instanceof androidx.appcompat.widget.ActionMenuView) {
                    ViewGroup menu = (ViewGroup) parent;
                    int[] typeIcons = new int[] {
                        R.drawable.menu_search_repos,
                        R.drawable.menu_search_users,
                        R.drawable.menu_search_code
                    };
                    int typeIdx = (mSelectedSearchType >= 0
                            && mSelectedSearchType < typeIcons.length)
                            ? mSelectedSearchType : 0;
                    android.graphics.drawable.Drawable typeDrawable =
                            ContextCompat.getDrawable(toolbar.getContext(), typeIcons[typeIdx]);
                    int iconW = (typeDrawable != null)
                            ? typeDrawable.getIntrinsicWidth() : 0;
                    for (int i = 0; i < menu.getChildCount(); i++) {
                        View child = menu.getChildAt(i);
                        if (child instanceof androidx.appcompat.view.menu.ActionMenuItemView) {
                            // 第一个菜单项就是类型项（orderInCategory 10 < 搜索 20
                            // < 排序 30；搜索框是 SearchView 不是菜单项视图）。
                            // 类型项定宽为图标宽 + 两侧各 4dp（默认最小宽 48dp，
                            // 定宽绕过它；排序项不动）。
                            if (iconW > 0) {
                                ViewGroup.LayoutParams lp = child.getLayoutParams();
                                if (lp != null) {
                                    lp.width = iconW + (int) (8 * density + 0.5f);
                                    child.setLayoutParams(lp);
                                }
                                child.setPadding(0, child.getPaddingTop(),
                                        0, child.getPaddingBottom());
                            }
                            break;
                        }
                    }
                    parent.setPadding(menuPadLeft, 0, 0, 0);
                }
                View plate = mSearch.findViewById(androidx.appcompat.R.id.search_plate);
                if (plate != null) {
                    plate.setPadding(0, plate.getPaddingTop(), 0, plate.getPaddingBottom());
                }
                View searchBtn = mSearch.findViewById(androidx.appcompat.R.id.search_button);
                if (searchBtn != null) {
                    searchBtn.setPadding(0, 0, 0, 0);
                    ViewGroup.LayoutParams lp = searchBtn.getLayoutParams();
                    if (lp instanceof LinearLayout.LayoutParams) {
                        int iconPx = (int) (24 * density + 0.5f);
                        LinearLayout.LayoutParams llp = (LinearLayout.LayoutParams) lp;
                        llp.width = iconPx;
                        llp.height = iconPx;
                        llp.setMargins(0, 0, 0, 0);
                        searchBtn.setLayoutParams(llp);
                    }
                }
                // 0.1.44 修正 0.1.43 的靶点错误：实测 AppCompat 1.7.1 的
                // abc_search_view.xml，展开态的放大镜不是 search_button
                // （收起态才用），而是 search_edit_frame 里的 search_mag_icon，
                // 且 edit_frame 自带左右各 8dp 外边距——这才是类型→搜索框、
                // 搜索框→排序两段间距多出来的部分。内板清零够不着它们。
                // 改法：edit_frame 左右外边距清零，mag_icon 缩到图标宽贴左，
                // 放大镜左偏与右外边距归零；0.1.43 的内板与按钮处理保留
                // （覆盖 search_button 为放大镜的情况，改动无害）。
                View editFrame = mSearch.findViewById(androidx.appcompat.R.id.search_edit_frame);
                if (editFrame != null) {
                    ViewGroup.LayoutParams elp = editFrame.getLayoutParams();
                    if (elp instanceof LinearLayout.LayoutParams) {
                        LinearLayout.LayoutParams lelp = (LinearLayout.LayoutParams) elp;
                        lelp.leftMargin = 0;
                        lelp.rightMargin = 0;
                        editFrame.setLayoutParams(lelp);
                    }
                }
                View magIcon = mSearch.findViewById(androidx.appcompat.R.id.search_mag_icon);
                if (magIcon != null) {
                    magIcon.setPadding(0, 0, 0, 0);
                    ViewGroup.LayoutParams mlp = magIcon.getLayoutParams();
                    if (mlp instanceof LinearLayout.LayoutParams) {
                        int iconPx = (int) (24 * density + 0.5f);
                        LinearLayout.LayoutParams mllp = (LinearLayout.LayoutParams) mlp;
                        mllp.width = iconPx;
                        mllp.setMargins(0, 0, 0, 0);
                        magIcon.setLayoutParams(mllp);
                    }
                }
                // 0.1.58：商店可见时排序／类型图标均隐藏、平台按钮已删除，
                // 搜索框占满剩余整行（上限放顶栏宽，ActionMenuView 会收住）；
                // 商店不可见时仍按老路取宽并做底线校验（排序须可见）。
                if (mStoreView != null
                        && mStoreView.getVisibility() == View.VISIBLE) {
                    int max = toolbar.getWidth();
                    if (max > 0) {
                        mSearch.setMaxWidth(max);
                    }
                } else {
                    // 0.1.61：类型项收窄（48dp→图标宽 + 8dp）+ 菜单左内边距
                    // 清零，预留从 200dp 降到 150dp（标题约 40 + 左内边距
                    // 16 + 类型约 32 + 排序 48 + 余量约 14）；上限从 50%
                    // 放到 70%（只是松上限，真正起作用的是预留算式；排序
                    // 挤占时仍有 verifySortVisible 继续缩）。25% 下限保搜
                    // 索框可用。
                    int reserve = (int) (150 * density + 0.5f);
                    int floor = (int) (toolbar.getWidth() * 0.25f);
                    int max = Math.max(floor, Math.min(
                            (int) (toolbar.getWidth() * 0.70f),
                            toolbar.getWidth() - reserve - menuPadLeft));
                    if (max > 0) {
                        mSearch.setMaxWidth(max);
                        // 0.1.20 F-31: 60% 是固定比例，窄屏/大字体下仍可能挤掉排序——
                        // 布局完成后做一次底线校验（见 verifySortVisible）。
                        verifySortVisible(toolbar, max, 0);
                    }
                }
            }
        });
    }

    /**
     * 0.1.20 F-31: 搜索框 60% 上限的底线校验（只做校验，不恢复 0.1.17/0.1.18
     * 的复杂算式）。菜单只有类型/搜索/排序三项且均为 always：正常时菜单区
     * 只有这三项子视图；一旦出现溢出按钮（非 ActionMenuItemView 子视图）
     * 或有菜单项不可见，即排序已被挤出。此时把搜索框上限每次缩 24dp
     * （下限为顶栏宽 25%），最多 4 步，直至溢出消失。窄屏/大字体下排序
     * 因此仍可见；宽屏下校验直接通过，不改 60% 上限。
     */
    @SuppressLint("RestrictedApi") // B-L13：见 capSearchBoxWidth 注释。
    private void verifySortVisible(final androidx.appcompat.widget.Toolbar toolbar,
            final int currentMax, final int attempt) {
        if (!isAdded() || mSearch == null) return;
        toolbar.post(() -> {
            if (!isAdded() || mSearch == null) return;
            View parent = (View) mSearch.getParent();
            if (!(parent instanceof androidx.appcompat.widget.ActionMenuView)) return;
            androidx.appcompat.widget.ActionMenuView menuView =
                    (androidx.appcompat.widget.ActionMenuView) parent;
            boolean squeezed = false;
            for (int i = 0; i < menuView.getChildCount(); i++) {
                View child = menuView.getChildAt(i);
                if (child == mSearch) continue;
                if (!(child instanceof androidx.appcompat.view.menu.ActionMenuItemView)
                        || child.getVisibility() != View.VISIBLE) {
                    squeezed = true;
                    break;
                }
            }
            if (!squeezed) return;
            int floor = (int) (toolbar.getWidth() * 0.25f);
            float density = toolbar.getResources().getDisplayMetrics().density;
            int next = currentMax - (int) (24 * density + 0.5f);
            if (next < floor || attempt >= 4) {
                android.util.Log.w("SearchFragment",
                        "Sort squeezed out even at floor, keeping max=" + currentMax);
                return;
            }
            mSearch.setMaxWidth(next);
            verifySortVisible(toolbar, next, attempt + 1);
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.type) {
            showTypeSheet();
            return true;
        }
        if (item.getItemId() == R.id.sort) {
            showSortSheet();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_KEY_SEARCH_TYPE, mSelectedSearchType);
        outState.putString(STATE_KEY_QUERY, mQuery);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 默认页：空查询且不做初始加载时（Home 搜索 Tab），挂最近搜索在上、
        // 热门建议在下的默认头；有初始查询或初始加载时不挂。
        if (TextUtils.isEmpty(mQuery) && !shouldDoInitialLoad(savedInstanceState)) {
            loadDefaultPage();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 0.1.6：切回搜索页签时默认头若不在（例如之前输过查询又被清掉），
        // 直接补上默认页加载，不用下拉才出内容。只针对 Home 搜索页签
        // （do_initial_load=false）；独立搜索页（初始加载）不走这条路。
        if (TextUtils.isEmpty(mQuery) && mDefaultHeader == null && mAdapter != null
                && getArguments() != null
                && !getArguments().getBoolean("do_initial_load", true)) {
            loadDefaultPage();
        }
    }

    @Override
    public void onDestroyView() {
        // 0.1.19：退出搜索页时恢复工具栏原始内边距，避免影响其他共用页面。
        // 0.1.20 F-06: 同样恢复 capSearchBoxWidth() 清零的顶栏 padding 与
        // 菜单区 padding，否则切到趋势/Star/我页时顶栏仍保持零 padding。
        if (getActivity() != null) {
            androidx.appcompat.widget.Toolbar toolbar =
                    getActivity().findViewById(R.id.toolbar);
            if (toolbar != null) {
                if (mToolbarInsetsSaved) {
                    toolbar.setContentInsetsRelative(mToolbarInsetStart, mToolbarInsetEnd);
                    toolbar.setContentInsetStartWithNavigation(mToolbarInsetStartWithNav);
                    mToolbarInsetsSaved = false;
                }
                if (mToolbarPaddingSaved) {
                    toolbar.setPadding(mToolbarPadLeft, mToolbarPadTop,
                            mToolbarPadRight, mToolbarPadBottom);
                    if (mSearch != null) {
                        View parent = (View) mSearch.getParent();
                        if (parent instanceof androidx.appcompat.widget.ActionMenuView) {
                            parent.setPadding(mMenuPadLeft, mMenuPadTop,
                                    mMenuPadRight, mMenuPadBottom);
                        }
                    }
                    mToolbarPaddingSaved = false;
                }
            }
        }
        // BUG-11: hot-suggestion subscription must not outlive the view.
        if (mHotDisposable != null && !mHotDisposable.isDisposed()) {
            mHotDisposable.dispose();
        }
        mHotDisposable = null;
        mSearch = null;
        mDefaultHeader = null;
        mAdapter = null;
        if (mStoreView != null) {
            mStoreView.onDestroy();
            ViewGroup parent = (ViewGroup) mStoreView.getParent();
            if (parent != null) {
                parent.removeView(mStoreView);
            }
            mStoreView = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onRefresh() {
        if (mStoreView != null && mStoreView.getVisibility() == View.VISIBLE) {
            // 商店可见时只重载当前页签；商店顶部的下拉刷新由此触发
            // （canChildScrollUp 覆盖后，商店中部下拉不再被拦截成刷新）。
            mStoreView.reloadCurrentTab();
            return;
        }
        // BUG-25: pull-to-refresh must actually refresh hot suggestions,
        // not keep the fragment-lifetime cache.
        mHotRepos = null;
        mHotForceRefresh = true;
        super.onRefresh();
        if (mDefaultHeader != null) {
            refreshDefaultHeader();
        }
    }

    /**
     * 0.1.60：下拉刷新问商店当前 ScrollView 是否能上滑——商店可见时以它为准，
     * 否则走基类（问结果列表 RecyclerView）。不覆盖时刷新布局问的是已 GONE
     * 的结果列表，恒报不能上滑，商店中部任何一下拉都被拦截成刷新重载。
     */
    @Override
    public boolean canChildScrollUp() {
        if (mStoreView != null && mStoreView.getVisibility() == View.VISIBLE) {
            return mStoreView.canScrollUp();
        }
        return super.canChildScrollUp();
    }

    // ---------- 软件商店：搜索页签的默认页 ----------

    /** 搜索页签（Home 搜索 Tab，do_initial_load=false）才挂商店；独立搜索页走老路。 */
    private boolean isStoreTab() {
        Bundle args = getArguments();
        return args != null && !args.getBoolean("do_initial_load", true);
    }

    private void showStore() {
        if (getView() == null || getActivity() == null) {
            return;
        }
        if (mStoreView == null) {
            mStoreView = new StoreView(getActivity());
            mStoreView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ((ViewGroup) getView()).addView(mStoreView);
        }
        mStoreView.setVisibility(View.VISIBLE);
        mStoreView.onShown();
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
        View list = getView().findViewById(R.id.list);
        if (list != null) {
            list.setVisibility(View.GONE);
        }
        View emptyContainer = getView().findViewById(R.id.empty_view_container);
        if (emptyContainer != null) {
            emptyContainer.setVisibility(View.GONE);
        }
    }

    private void hideStore() {
        if (mStoreView != null) {
            mStoreView.setVisibility(View.GONE);
        }
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
        if (getView() != null) {
            View list = getView().findViewById(R.id.list);
            if (list != null) {
                list.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * 0.1.6 默认页加载：只加载默认头（最近搜索 + 热门建议），不走列表加载，
     * 进搜索页签即调用，不需要下拉刷新才显示。
     */
    private void loadDefaultPage() {
        // 商店页：搜索页签 + 仓库类型时默认页就是商店，不挂最近搜索/热门头。
        if (isStoreTab() && mSelectedSearchType == SEARCH_TYPE_REPO) {
            showStore();
            return;
        }
        if (mDefaultHeader == null) {
            attachDefaultHeader();
        } else {
            refreshDefaultHeader();
        }
        // 0.1.8：默认头挂上后必须立刻纠正空态，否则头会被藏起来。
        // 基类在 onRecyclerViewInflated 里先按“列表 0 项”把 RecyclerView
        // 设成 GONE；0.1.6 又让默认页的 loadPage 返回空页，之后的
        // updateEmptyState 也会再藏一次。默认头在 RecyclerView 里，
        // 藏列表就等于藏了最近搜索 + 热门建议。
        updateEmptyState();
    }

    /**
     * 0.1.8 回归修复：只针对搜索页，不动基类对其他列表页的行为。
     * 默认头已挂上时，本页不算空——哪怕列表项数为 0，也要保持
     * RecyclerView 可见、空态容器隐藏，否则最近搜索 + 热门建议
     * 会被基类的空态逻辑连着空列表一起藏掉（0.1.6/0.1.7 的症状）。
     * 没有默认头时（输入关键词搜出 0 条）仍走基类逻辑，正常显示空态。
     */
    @Override
    protected void updateEmptyState() {
        super.updateEmptyState();
        if (mDefaultHeader != null && getView() != null) {
            View list = getView().findViewById(R.id.list);
            View emptyContainer = getView().findViewById(R.id.empty_view_container);
            if (list != null) {
                list.setVisibility(View.VISIBLE);
            }
            if (emptyContainer != null) {
                emptyContainer.setVisibility(View.GONE);
            }
        }
        if (mStoreView != null && mStoreView.getVisibility() == View.VISIBLE
                && getView() != null) {
            // 商店可见时基类的空态逻辑不得把列表/空态容器翻出来盖住商店。
            View list = getView().findViewById(R.id.list);
            View emptyContainer = getView().findViewById(R.id.empty_view_container);
            if (list != null) {
                list.setVisibility(View.GONE);
            }
            if (emptyContainer != null) {
                emptyContainer.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected RootAdapter<Object, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mAdapter = new SearchAdapter(getActivity(), this);
        mAdapter.setMode(mSelectedSearchType);
        return mAdapter;
    }

    @Override
    protected boolean shouldDoInitialLoad(Bundle savedInstanceState) {
        // 0.1.20 F-07/F-16: 空查询不算“恢复上次加载”；arguments 可能为空。
        Bundle args = getArguments();
        return (savedInstanceState != null && !TextUtils.isEmpty(mQuery)) // always restore previous load results
                || (args == null || args.getBoolean("do_initial_load", true));
    }

    @Override
    protected Single<Response<Page<Object>>> loadPage(int page, boolean bypassCache) {
        if (TextUtils.isEmpty(mQuery)) {
            // 0.1.6：默认页（默认头已挂上）不再显示个人仓库，头下列表保持为空；
            // 独立搜索页（无默认头）仍走原来的默认仓库路径。
            if (mDefaultHeader != null) {
                return Single.just(Response.success(new ApiHelpers.DummyPage<>()));
            }
            // Show user's own repositories by default when searching repos with empty query
            if (mSelectedSearchType == SEARCH_TYPE_REPO) {
                return makeDefaultRepoSingle(page, bypassCache);
            }
            return Single.just(Response.success(new ApiHelpers.DummyPage<>()));
        }
        switch (mSelectedSearchType) {
            case SEARCH_TYPE_REPO: return makeRepoSearchSingle(page, bypassCache);
            case SEARCH_TYPE_USER: return makeUserSearchSingle(page, bypassCache);
            case SEARCH_TYPE_CODE: return makeCodeSearchSingle(page, bypassCache);
        }
        throw new IllegalStateException("Unexpected search type " + mSelectedSearchType);
    }

    @SuppressWarnings("unchecked")
    private Single<Response<Page<Object>>> makeDefaultRepoSingle(int page, boolean bypassCache) {
        RepositoryService service = ServiceFactory.get(RepositoryService.class, bypassCache);
        String login = Gh4Application.get().getAuthLogin();
        if (login == null) {
            // 未登录时没有默认用户，直接返回空页（null 传给 Retrofit @Path 会崩溃）
            return Single.just(Response.success(new ApiHelpers.DummyPage<>()));
        }
        // Page<Repository> is safely readable as Page<Object>
        return (Single<Response<Page<Object>>>) (Single<?>)
                service.getUserRepositories(login, new HashMap<>(), page);
    }

    @Override
    protected int getEmptyTextResId() {
        return 0; // will be updated later
    }

    @Override
    public void onItemClick(Object item) {
        if (item instanceof Repository) {
            Repository repository = (Repository) item;
            startActivity(RepositoryActivity.makeIntent(getActivity(), repository));
        } else if (item instanceof SearchCode) {
            openFileViewer((SearchCode) item, -1);
        } else {
            User user = (User) item;
            startActivity(UserActivity.makeIntent(getActivity(), user));
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = query;
        if (!StringUtils.isBlank(query)) {
            final ContentResolver cr = getActivity().getContentResolver();
            final ContentValues cv = new ContentValues();
            cv.put(SuggestionsProvider.Columns.TYPE, mSelectedSearchType);
            cv.put(SuggestionsProvider.Columns.SUGGESTION, query);
            cv.put(SuggestionsProvider.Columns.DATE, System.currentTimeMillis());

            new Thread() {
                @Override
                public void run() {
                    cr.insert(SuggestionsProvider.Columns.CONTENT_URI, cv);
                }
            }.start();
        }
        loadResults();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        mQuery = newText;
        if (!TextUtils.isEmpty(newText)) {
            // 0.1.60：输入时商店原样不动，不再 live 筛商店列表；回车提交才
            // 走全局仓库搜索（loadResults 里 hideStore）。最近搜索联想
            // （runQuery）不受影响，仍由 SearchView 自己调。
            if (!(isStoreTab() && mSelectedSearchType == SEARCH_TYPE_REPO)) {
                removeDefaultHeader();
            }
        } else {
            // 清空立即恢复商店默认页。
            if (mDefaultHeader == null) {
                // BUG-7: clearing back to empty must restore the default page,
                // not leave a blank list.
                loadDefaultPage();
            }
        }
        return true;
    }

    @Override
    public boolean onClose() {
        if (mAdapter != null) {
            mAdapter.clear();
        }
        mQuery = null;
        if (mStoreView != null) {
            // 关闭搜索框：回商店默认页（首页）。
            if (mStoreView.getVisibility() == View.VISIBLE) {
                mStoreView.resetToHome();
            }
        }
        attachDefaultHeader();
        // BUG-7: the 0.1.8 override forces the list visible when the header
        // is attached; without this a 0-result search leaves the list GONE
        // and the newly attached header hidden behind the empty container.
        updateEmptyState();
        return true;
    }

    @Override
    public boolean onSuggestionSelect(int position) {
        return false;
    }

    @Override
    public boolean onSuggestionClick(int position) {
        // 129：getCursor() 可为 null（runQuery 在空查询时返回 null），
        // 直接 moveToPosition 即 NPE；clear 行改按 _id 判定，不再靠“末行恒为 clear 行”
        if (mSearch.getSuggestionsAdapter() == null) {
            return true;
        }
        Cursor cursor = mSearch.getSuggestionsAdapter().getCursor();
        if (cursor != null && cursor.moveToPosition(position)) {
            if (isClearRowCursor(cursor)) {
                final android.app.Activity activity = getActivity();
                if (activity != null) {
                    final int type = mSelectedSearchType;
                    final ContentResolver cr = activity.getContentResolver();
                    new Thread() {
                        @Override
                        public void run() {
                            cr.delete(SuggestionsProvider.Columns.CONTENT_URI,
                                    SuggestionsProvider.Columns.TYPE + " = ?",
                                    new String[] { String.valueOf(type) });
                        }
                    }.start();
                }
            } else {
                mQuery = cursor.getString(1);
                mSearch.setQuery(mQuery, true);
            }
        }
        return true;
    }

    /**
     * 129：clear 行是 swapCursor 时以 _id = Long.MAX_VALUE 追加的 MatrixCursor 行，
     * 按 _id 判定才不受 cursor 刷新错位影响。投影首列即 _ID。
     */
    private static boolean isClearRowCursor(Cursor cursor) {
        return cursor != null && cursor.getLong(0) == Long.MAX_VALUE;
    }

    @Override
    public void onSearchFragmentClick(SearchCode result, int matchIndex) {
        openFileViewer(result, matchIndex);
    }

    @Override
    public Cursor runQuery(CharSequence query) {
        if (TextUtils.isEmpty(query)) {
            return null;
        }
        return getContext().getContentResolver().query(SuggestionsProvider.Columns.CONTENT_URI,
                SUGGESTION_PROJECTION, SUGGESTION_SELECTION,
                new String[] { String.valueOf(mSelectedSearchType), query + "%" }, SUGGESTION_ORDER);
    }

    private void openFileViewer(SearchCode result, int matchIndex) {
        Repository repo = result.repository();
        Uri uri = Uri.parse(result.url());
        String ref = uri.getQueryParameter("ref");
        TextMatch textMatch = matchIndex >= 0 ? result.textMatches().get(matchIndex) : null;
        startActivity(FileViewerActivity.makeIntentWithSearchMatch(getActivity(),
                repo.owner().login(), repo.name(), ref, result.path(),
                textMatch));
    }

    private void loadResults() {
        // 提交搜索：先藏商店，否则 onRefresh 会看到商店可见而只重载页签。
        hideStore();
        removeDefaultHeader();
        mSearch.clearFocus();
        onRefresh();
    }

    private void updateSearchViewHint() {
        int[] hintAndEmptyTextResIds = HINT_AND_EMPTY_TEXTS[mSelectedSearchType];
        mSearch.setQueryHint(getString(hintAndEmptyTextResIds[0]));
        updateEmptyText(hintAndEmptyTextResIds[1]);
    }

    private void applySearchType(int newType) {
        if (newType == mSelectedSearchType) {
            return;
        }
        mSelectedSearchType = newType;
        mAdapter.setMode(newType);
        restoreSortSelection(newType);

        updateSearchViewHint();
        updateEmptyState();
        resetSubject();

        if (TextUtils.isEmpty(mQuery)) {
            // 0.1.20 F-23(a): 空查询路径不走 submit/onRefresh，旧订阅须在此释放，
            // 否则飞行中的默认仓库加载会在模式切换后投递旧类型数据。
            disposeCurrentSubscription();
            if (isStoreTab() && mSelectedSearchType == SEARCH_TYPE_REPO) {
                showStore();
            } else {
                hideStore();
                loadDefaultPage();
            }
        } else {
            // force re-filtering of the view
            mSearch.setQuery(mQuery, true);
        }
    }

    private void updateTypeMenuItem(Menu menu) {
        MenuItem typeItem = menu.findItem(R.id.type);
        if (typeItem == null) return;
        int[][] resources = new int[][] {
            { R.string.search_type_repo, R.drawable.menu_search_repos },
            { R.string.search_type_user, R.drawable.menu_search_users },
            { R.string.search_type_code, R.drawable.menu_search_code }
        };
        int[] res = resources[mSelectedSearchType];
        typeItem.setTitle(res[0]);
        typeItem.setIcon(res[1]);
    }

    private void updateEmptyText(@StringRes int emptyTextResId) {
        TextView emptyView = getView().findViewById(android.R.id.empty);
        emptyView.setText(emptyTextResId);
    }

    private void setSortSelection(String sort, String order) {
        if (TextUtils.equals(sort, mSort) && TextUtils.equals(order, mOrder)) {
            return;
        }
        mSort = sort;
        mOrder = order;
        saveSortSelection(mSelectedSearchType);
        loadResults();
    }

    private void restoreSortSelection(int searchType) {
        SharedPreferences prefs = getActivity()
                .getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        mSort = prefs.getString(PREF_KEY_SORT_PREFIX + searchType + "_sort", null);
        mOrder = prefs.getString(PREF_KEY_SORT_PREFIX + searchType + "_order", null);
    }

    private void saveSortSelection(int searchType) {
        getActivity().getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SORT_PREFIX + searchType + "_sort", mSort)
                .putString(PREF_KEY_SORT_PREFIX + searchType + "_order", mOrder)
                .apply();
    }

    private static int findSortMenuItemId(String sort, String order) {
        // 0.1.20 D-06: 反查走选项表（无匹配返回 0，即无单选选中，与原先一致）。
        SearchSortOption option = SearchSortOption.fromSortOrder(sort, order);
        return option != null ? option.menuId : 0;
    }

    private Single<Response<Page<Object>>> makeRepoSearchSingle(long page, boolean bypassCache) {
        SearchService service = ServiceFactory.get(SearchService.class, bypassCache);
        String params = mQuery + " fork:true";

        return service.searchRepositories(params, mSort, mOrder, page)
                .compose(result -> RxUtils.<Repository, Object>searchPageAdapter(result, item -> item))
                // With that status code, Github wants to tell us there are no
                // repositories to search in. Just pretend no error and return
                // an empty list in that case.
                .compose(RxUtils.mapFailureToValue(422, Response.success(new ApiHelpers.DummyPage<>())));
    }

    private Single<Response<Page<Object>>> makeUserSearchSingle(long page, boolean bypassCache) {
        final SearchService service = ServiceFactory.get(SearchService.class, bypassCache);
        return service.searchUsers(mQuery, mSort, mOrder, page)
                .compose(result -> RxUtils.searchPageAdapter(result, item -> item));
    }

    private Single<Response<Page<Object>>> makeCodeSearchSingle(long page, boolean bypassCache) {
        SearchService service = ServiceFactory.get(SearchService.class, bypassCache,
                "application/vnd.github.v3.text-match+json", null, null);

        return service.searchCode(mQuery, mSort, mOrder, page)
                .compose(result -> RxUtils.searchPageAdapter(result, item -> item));
    }

    // ---------- 0.1.1 底部动作表：类型 / 排序 ----------

    private void showTypeSheet() {
        if (getActivity() == null) return;
        int[][] res = new int[][] {
            { R.string.search_type_repo, R.drawable.icon_repositories, R.string.search_type_repo_desc },
            { R.string.search_type_user, R.drawable.search_users, R.string.search_type_user_desc },
            { R.string.search_type_code, R.drawable.search_code, R.string.search_type_code_desc }
        };
        // 0.1.20 D-05: 骨架走 BottomSheetListBuilder；富行构造与原来一致。
        com.gh4a.utils.BottomSheetListBuilder builder =
                new com.gh4a.utils.BottomSheetListBuilder(requireContext(),
                        getString(R.string.search_type));
        int pad = builder.pad();
        for (int i = 0; i < res.length; i++) {
            final int type = i;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, pad / 2, 0, pad / 2);
            UiUtils.setSelectableBackground(row);
            ImageView icon = new ImageView(requireContext());
            icon.setImageResource(res[i][1]);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    (int) (24 * getResources().getDisplayMetrics().density),
                    (int) (24 * getResources().getDisplayMetrics().density));
            iconLp.setMarginEnd(pad / 2);
            row.addView(icon, iconLp);
            LinearLayout texts = new LinearLayout(requireContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView main = new TextView(requireContext());
            main.setText(res[i][0]);
            main.setTextSize(15);
            TextView sub = new TextView(requireContext());
            sub.setText(res[i][2]);
            sub.setTextSize(13);
            sub.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.secondary_text_light));
            texts.addView(main);
            texts.addView(sub);
            row.addView(texts);
            if (i == mSelectedSearchType) {
                TextView check = new TextView(requireContext());
                check.setText("\u2713");
                check.setTextSize(18);
                check.setTextColor(UiUtils.resolveColor(requireContext(), androidx.appcompat.R.attr.colorPrimary));
                row.addView(check);
            }
            builder.addRow(row, v -> applySearchType(type));
        }
        builder.addCancel();
        builder.show();
    }

    private void showSortSheet() {
        if (getActivity() == null) return;
        // 0.1.20 D-06: 分类型选项直接取自选项表（顺序与原先数组一致）。
        java.util.List<SearchSortOption> options =
                SearchSortOption.forSearchType(mSelectedSearchType);
        int currentId = findSortMenuItemId(mSort, mOrder);
        // 0.1.20 D-05: 骨架走 BottomSheetListBuilder；富行构造与原来一致。
        com.gh4a.utils.BottomSheetListBuilder builder =
                new com.gh4a.utils.BottomSheetListBuilder(requireContext(),
                        getString(R.string.search_sort_title));
        int pad = builder.pad();
        for (SearchSortOption option : options) {
            final int itemId = option.menuId;
            final String sort = option.sort;
            final String order = option.order;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, pad / 2, 0, pad / 2);
            UiUtils.setSelectableBackground(row);
            RadioButton radio = new RadioButton(requireContext());
            radio.setChecked(itemId == currentId);
            radio.setClickable(false);
            radio.setFocusable(false);
            row.addView(radio);
            TextView label = new TextView(requireContext());
            label.setText(getString(option.titleRes));
            label.setTextSize(15);
            if (itemId == currentId) {
                label.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            row.addView(label);
            builder.addRow(row, v -> setSortSelection(sort, order));
        }
        builder.addCancel();
        builder.show();
    }


    // ---------- 0.1.1 默认页：最近搜索在上、热门建议在下 ----------

    private void attachDefaultHeader() {
        if (isStoreTab() && mSelectedSearchType == SEARCH_TYPE_REPO) {
            showStore();
            return;
        }
        if (mAdapter == null || getActivity() == null || mDefaultHeader != null) return;
        mDefaultHeader = getLayoutInflater().inflate(R.layout.search_default_header, null);
        // 0.1.6：默认头明确满宽，避免量成窄栏导致文案被截。
        mDefaultHeader.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mAdapter.setHeaderView(mDefaultHeader);
        refreshDefaultHeader();
    }

    private void removeDefaultHeader() {
        if (mDefaultHeader != null && mAdapter != null) {
            mAdapter.setHeaderView(null);
            mDefaultHeader = null;
        }
    }

    private void refreshDefaultHeader() {
        if (mDefaultHeader == null || getActivity() == null) return;
        LinearLayout recentContainer =
                mDefaultHeader.findViewById(R.id.search_default_recent_container);
        recentContainer.removeAllViews();
        java.util.List<String> recents = loadRecentSearches();
        // 0.1.38：搜索默认页埋点（只记条数，不记搜索词）。
        com.gh4a.utils.DiagTrace.log(getActivity(), "SEARCH",
                "default recent=" + recents.size());
        // 0.1.6：动态加的 TextView 明确给满宽参数，否则量成窄栏会把
        // “暂无最近搜索”截成“暂无最”，热门仓库名也会挤成窄栏。
        LinearLayout.LayoutParams fullWidth = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (recents.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.search_no_recent_searches);
            empty.setTextSize(14);
            empty.setTextColor(ContextCompat.getColor(requireContext(),
                    android.R.color.secondary_text_light));
            recentContainer.addView(empty, fullWidth);
        } else {
            for (String suggestion : recents) {
                TextView chip = new TextView(requireContext());
                chip.setText(suggestion);
                chip.setTextSize(15);
                int pad = (int) (8 * getResources().getDisplayMetrics().density);
                chip.setPadding(0, pad, 0, pad);
                UiUtils.setSelectableBackground(chip);
                chip.setOnClickListener(v -> {
                    if (mSearch != null) mSearch.setQuery(suggestion, true);
                });
                // 0.1.11：长按单条最近搜索 → 小确认框，确认后只删这一条
                // （当前搜索类型下、按词精确匹配），其他条目不动；点按仍是搜索。
                chip.setOnLongClickListener(v -> {
                    if (getActivity() == null) return true;
                    final String term = suggestion;
                    final int type = mSelectedSearchType;
                    new AlertDialog.Builder(requireContext())
                            .setTitle(term)
                            .setMessage(R.string.search_delete_recent_confirm)
                            .setPositiveButton(R.string.delete, (dialog, which) -> {
                                final ContentResolver cr =
                                        requireContext().getContentResolver();
                                new Thread() {
                                    @Override
                                    public void run() {
                                        cr.delete(SuggestionsProvider.Columns.CONTENT_URI,
                                                SuggestionsProvider.Columns.TYPE + " = ? AND "
                                                        + SuggestionsProvider.Columns.SUGGESTION
                                                        + " = ?",
                                                new String[] { String.valueOf(type), term });
                                        if (getActivity() != null) {
                                            getActivity().runOnUiThread(() -> {
                                                if (isAdded()) refreshDefaultHeader();
                                            });
                                        }
                                    }
                                }.start();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return true;
                });
                recentContainer.addView(chip, fullWidth);
            }
        }
        // 热门建议只在仓库类型显示（按 Star 数取真实热门仓库，非占位数据）
        View hotTitle = mDefaultHeader.findViewById(R.id.search_default_hot_title);
        LinearLayout hotContainer =
                mDefaultHeader.findViewById(R.id.search_default_hot_container);
        boolean showHot = mSelectedSearchType == SEARCH_TYPE_REPO;
        hotTitle.setVisibility(showHot ? View.VISIBLE : View.GONE);
        hotContainer.setVisibility(showHot ? View.VISIBLE : View.GONE);
        if (showHot) {
            if (mHotRepos != null) {
                fillHotContainer(hotContainer);
            } else if (!mHotForceRefresh) {
                // 0.1.10：先显示磁盘缓存（秒出），再后台静默刷新替换；
                // 无缓存时先出骨架屏，首个网络结果到货后替换。
                java.util.List<HotReposCache.HotRepo> cached =
                        HotReposCache.load(requireContext());
                if (cached != null) {
                    fillHotContainerFromCache(hotContainer, cached);
                    loadHotRepos(hotContainer);
                } else {
                    showHotSkeleton(hotContainer);
                    loadHotRepos(hotContainer);
                }
            } else {
                showHotSkeleton(hotContainer);
                loadHotRepos(hotContainer);
            }
        }
    }

    private java.util.List<String> loadRecentSearches() {
        java.util.List<String> out = new java.util.ArrayList<>();
        // 129：getContext() 可为 null；cursor.close() 不在 finally，循环抛错即泄漏
        android.content.Context context = getContext();
        if (context == null) {
            return out;
        }
        try (Cursor cursor = context.getContentResolver().query(
                SuggestionsProvider.Columns.CONTENT_URI, SUGGESTION_PROJECTION,
                SuggestionsProvider.Columns.TYPE + " = ?",
                new String[] { String.valueOf(mSelectedSearchType) }, SUGGESTION_ORDER)) {
            if (cursor != null) {
                int col = cursor.getColumnIndexOrThrow(SuggestionsProvider.Columns.SUGGESTION);
                while (cursor.moveToNext() && out.size() < 6) {
                    String s = cursor.getString(col);
                    if (!TextUtils.isEmpty(s) && !out.contains(s)) out.add(s);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void loadHotRepos(final LinearLayout hotContainer) {
        if (mHotDisposable != null && !mHotDisposable.isDisposed()) {
            mHotDisposable.dispose();
        }
        SearchService service = ServiceFactory.get(SearchService.class, false);
        mHotDisposable = service.searchRepositories("stars:>100000", "stars", "desc", 1)
                .compose(result -> RxUtils.<Repository, Object>searchPageAdapter(result, item -> item))
                .map(ApiHelpers::throwOnFailure)
                .compose(RxUtils::doInBackground)
                .subscribe(page -> {
                    if (!isAdded() || mDefaultHeader == null) return;
                    java.util.List<Repository> repos = new java.util.ArrayList<>();
                    if (page != null && page.items() != null) {
                        for (Object o : page.items()) {
                            // 0.1.6：热门建议从 5 条加到 10 条。
                            if (o instanceof Repository && repos.size() < 10) {
                                repos.add((Repository) o);
                            }
                        }
                    }
                    // 0.1.38：热门建议埋点（只记条数，不记仓库名）。
                    com.gh4a.utils.DiagTrace.log(getActivity(), "SEARCH",
                            "hot ok count=" + repos.size());
                    mHotRepos = repos;
                    mHotForceRefresh = false;
                    if (getActivity() != null) {
                        java.util.List<HotReposCache.HotRepo> toSave =
                                new java.util.ArrayList<>();
                        for (Repository r : repos) {
                            Integer sc = r.stargazersCount();
                            toSave.add(new HotReposCache.HotRepo(
                                    r.fullName(), sc != null ? sc : 0));
                        }
                        // 0.1.20 F-25: 仅非空时存（HotReposCache.save 本身亦守卫）。
                        if (!toSave.isEmpty()) {
                            HotReposCache.save(getActivity(), toSave);
                        }
                    }
                    fillHotContainer(hotContainer);
                }, error -> {
                    // 0.1.38：热门建议失败埋点（只记异常类名）。
                    com.gh4a.utils.DiagTrace.log(getActivity(), "SEARCH",
                            "hot error "
                                    + (error != null ? error.getClass().getSimpleName() : "?"));
                    // 热门建议加载失败时不显示假数据；但若已在显示缓存，
                    // 后台刷新失败不得把缓存清空（0.1.10）。
                    if (isAdded() && mDefaultHeader != null
                            && mHotRepos == null
                            && HotReposCache.load(getActivity()) == null) {
                        hotContainer.removeAllViews();
                    }
                });
    }

    /** 0.1.10：热门加载中的骨架占位（灰条），避免首次进页长时间空白。 */
    private void showHotSkeleton(LinearLayout hotContainer) {
        if (hotContainer == null) return;
        hotContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int barHeight = (int) (16 * density + 0.5f);
        int gap = (int) (12 * density + 0.5f);
        for (int i = 0; i < 5; i++) {
            View bar = new View(requireContext());
            // 132：硬编码浅灰 0xFFE0E0E0 在深色模式下刺眼；走主题的
            // colorSurfaceVariant（75 同款：硬编码色改主题色）。
            bar.setBackgroundColor(UiUtils.resolveColor(requireContext(),
                    com.google.android.material.R.attr.colorSurfaceVariant));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, barHeight);
            params.topMargin = gap;
            params.bottomMargin = gap;
            hotContainer.addView(bar, params);
        }
    }

    /** 0.1.10：用磁盘缓存的（全名，星数）渲染热门行，点击按 owner/name 打开仓库页。 */
    private void fillHotContainerFromCache(LinearLayout hotContainer,
            java.util.List<HotReposCache.HotRepo> cached) {
        if (hotContainer == null || cached == null) return;
        hotContainer.removeAllViews();
        for (HotReposCache.HotRepo repo : cached) {
            hotContainer.addView(makeHotRow(repo.fullName, repo.stars,
                    v -> {
                        if (getActivity() != null) {
                            int slash = repo.fullName.indexOf('/');
                            if (slash > 0) {
                                startActivity(RepositoryActivity.makeIntent(getActivity(),
                                        repo.fullName.substring(0, slash),
                                        repo.fullName.substring(slash + 1)));
                            }
                        }
                    }),
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private void fillHotContainer(LinearLayout hotContainer) {
        if (mHotRepos == null || hotContainer == null) return;
        hotContainer.removeAllViews();
        for (Repository repo : mHotRepos) {
            Integer sc = repo.stargazersCount();
            hotContainer.addView(makeHotRow(repo.fullName(), sc != null ? sc : 0,
                    v -> {
                        if (getActivity() != null) {
                            startActivity(RepositoryActivity.makeIntent(getActivity(), repo));
                        }
                    }),
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /**
     * 0.1.10：热门行改趋势页同款——标题用 TextAppearance.ItemTitle
     * （sans-serif-condensed、主色、单行省略），星数单独一行等宽小字
     * （TextAppearance.VerySmall + monospace），★ 与数字永不拆行，
     * 长仓库名只截断标题，不再把数字挤到下一行。
     */
    private LinearLayout makeHotRow(String fullName, int stars,
            View.OnClickListener clickListener) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density + 0.5f);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, pad, 0, pad);
        UiUtils.setSelectableBackground(row);
        row.setOnClickListener(clickListener);
        TextView title = new TextView(requireContext());
        title.setText(fullName);
        title.setTextAppearance(R.style.TextAppearance_ItemTitle);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxLines(1);
        row.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView meta = new TextView(requireContext());
        meta.setText("\u2605 " + stars);
        meta.setTextAppearance(R.style.TextAppearance_VerySmall);
        meta.setTypeface(android.graphics.Typeface.MONOSPACE);
        meta.setMaxLines(1);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = (int) (2 * density + 0.5f);
        row.addView(meta, metaParams);
        return row;
    }

    private class SuggestionAdapter extends CursorAdapter {
        private final LayoutInflater mInflater;

        public SuggestionAdapter(Context context) {
            super(context, null, false);
            mInflater = LayoutInflater.from(context);
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            if (newCursor != null && newCursor.getCount() > 0) {
                MatrixCursor clearRowCursor = new MatrixCursor(SUGGESTION_PROJECTION);
                clearRowCursor.addRow(new Object[] {
                        Long.MAX_VALUE,
                        mInflater.getContext().getString(R.string.clear_suggestions)
                });
                newCursor = new MergeCursor(new Cursor[] { newCursor, clearRowCursor });
            }
            return super.swapCursor(newCursor);
        }

        @Override
        public int getItemViewType(int position) {
            // 129：getItemViewType 时 cursor 未定位到该行，先移过去再按 _id 判定
            Cursor cursor = getCursor();
            if (cursor != null && cursor.moveToPosition(position)) {
                return isClearRow(cursor) ? 1 : 0;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            @LayoutRes int layoutResId = isClearRow(cursor)
                    ? R.layout.row_suggestion_clear : R.layout.row_suggestion;
            return mInflater.inflate(layoutResId, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            int columnIndex = cursor.getColumnIndexOrThrow(SuggestionsProvider.Columns.SUGGESTION);
            String suggestionText = cursor.getString(columnIndex);
            if (isClearRow(cursor)) {
                bindClearSuggestionsRow(view, suggestionText);
            } else {
                bindSuggestionRow(view, suggestionText);
            }
        }

        private void bindSuggestionRow(View view, String suggestionText) {
            TextView textView = (TextView) view.findViewById(R.id.suggestion_text);
            textView.setText(suggestionText);
            view.findViewById(R.id.select_suggestion_button).setOnClickListener(btn -> {
                mQuery = suggestionText;
                mSearch.setQuery(mQuery, false);
            });
        }

        private void bindClearSuggestionsRow(View view, String suggestionText) {
            TextView textView = (TextView) view;
            textView.setText(suggestionText);
        }

        // 129：不再按“末行恒为 clear 行”判定，cursor 刷新瞬间错位可误删全部历史；
        // 改按 clear 行的 _id（Long.MAX_VALUE）判定，见 isClearRowCursor
        private boolean isClearRow(Cursor cursor) {
            return isClearRowCursor(cursor);
        }
    }
}
