package com.gh4a.activities.home;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.navigation.NavigationView;

import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.gh4a.BaseFragmentPagerActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.Github4AndroidActivity;
import com.gh4a.activities.IssueEditActivity;
import com.gh4a.fragment.LoginModeChooserFragment;
import com.gh4a.fragment.NotificationListFragment;
import com.gh4a.fragment.RepositoryListContainerFragment;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.UpdateCheckUi;
import com.gh4a.utils.ActivityResultHelpers;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.DrawerManager;
import com.gh4a.utils.UiUtils;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.activity.NotificationService;
import com.meisolsson.githubsdk.service.users.UserService;

import java.util.HashMap;

public class HomeActivity extends BaseFragmentPagerActivity implements
        View.OnClickListener, RepositoryListContainerFragment.Callback,
        NotificationListFragment.ParentCallback, LoginModeChooserFragment.ParentCallback {
    public static Intent makeIntent(Context context, @IdRes int initialPageId) {
        String initialPage = START_PAGE_MAPPING.get(initialPageId);
        Intent intent = new Intent(context, HomeActivity.class);
        if (initialPage != null) {
            intent.putExtra("initial_page", initialPage);
        }
        return intent;
    }

    /**
     * 打开聚合页并定位到指定 Tab（如 pulls 链接 → 事项页的 PR Tab，
     * Release 雷达通知 → Star 页的 Release 动态 Tab）。
     */
    public static Intent makeIntent(Context context, @IdRes int initialPageId, int initialTab) {
        return makeIntent(context, initialPageId).putExtra("initial_tab", initialTab);
    }

    public static Intent makeNotificationsIntent(Context context, String repoOwner,
            String repoName) {
        return makeIntent(context, R.id.notifications)
                .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, repoOwner)
                .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, repoName);
    }

    private FragmentFactory mFactory;
    private ImageView mAvatarView;
    private TextView mUserExtraView;
    private ImageView mDrawerSwitcher;
    private String mUserLogin;
    private User mUserInfo;
    private int mSelectedFactoryId;
    private boolean mDrawerInAccountMode;
    private Menu mLeftDrawerMenu;
    private ImageView mNotificationsIndicator;
    private MenuItem mNotificationsMenuItem;
    private Drawable mNotificationsIndicatorIcon;
    private SwitchCompat mThemeSwitch;
    // 0.1.37：建页期抑制标志——重建新页时抽屉开关会在状态恢复窗口内误触发
    // 一次旧值（0.1.36 日志钉死：深色重建页开关由 true 被翻回 false，监听
    // 写回浅色）。建页时置 true、onResume 时清掉，恢复窗口内的触发一律
    // 忽略，恢复后用户真点的触发才生效；抽屉锁死后用户本就点不到它，
    // 抑制只挡恢复误触发，不挡恢复后的真点选。
    private boolean mSuppressThemeToggle = true;
    // View 全量改造（A 结构）：底部 5 Tab 主入口，抽屉退为二级（抽屉项目本身保留，见跟踪表）
    private BottomNavigationView mBottomNav;
    // 0.1.20 Observer: 角标 holder（Home 订阅、各页发布，见 BadgeState）。
    private final com.gh4a.utils.BadgeState mBadgeState = new com.gh4a.utils.BadgeState();

    /** 角标 holder：各页只向它发布计数，底栏落点只在订阅回调里。 */
    public com.gh4a.utils.BadgeState getBadgeState() {
        return mBadgeState;
    }

    /** 0.1.20 D-04: 未读通知计数循环（与我页通知行同一口径）。 */
    public static int countUnread(java.util.List<com.gh4a.model.NotificationHolder> notifications) {
        int unread = 0;
        if (notifications != null) {
            for (com.gh4a.model.NotificationHolder h : notifications) {
                // 154/115：unread() 为可空 Boolean，缺字段按未读计。
                if (h.notification != null && Boolean.TRUE.equals(h.notification.unread())) unread++;
            }
        }
        return unread;
    }
    private boolean mBottomNavSyncing;

    private final ActivityResultLauncher<Void> mSettingsLauncher = registerForActivityResult(
            new ActivityResultHelpers.StartSettingsContract(),
            themeChanged -> {
                if (themeChanged) {
                    // 0.1.25：只留 goToToplevelActivity()，不再 finish()。回调跑在
                    // HomeActivity 自身里，CLEAR_TOP 目标就是自己，系统已负责清掉当前
                    // 实例；再显式 finish() 即与主题开关监听同款双重销毁（DecorView
                    // not attached，见 setupThemeToggle 注释）。
                    goToToplevelActivity();
                }
            });

    // 0.1.23: notification permission, asked exactly once on the first
    // post-login Home entry (Android 13+ only). Denial never re-prompts;
    // the settings-page notification switch stays the manual path.
    private static final String KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked_v1";
    private final ActivityResultLauncher<String> mNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    private static final String STATE_KEY_FACTORY_ITEM = "factoryItem";

    private static final int ID_LOADER_USER = 0;
    private static final int ID_LOADER_NOTIFICATIONS_INDICATOR = 1;
    private static final int ID_LOADER_ME_BADGE = 2;

    private static final int OTHER_ACCOUNTS_GROUP_BASE_ID = 1000;

    // 账号切换模式下需要隐藏的抽屉入口：
    // 搜索/发现聚合页含公开内容（公开时间线、趋势、博客），无需登录即可看，保留；
    // 只隐藏需要登录的功能入口
    private static final int[] ACCOUNT_MODE_HIDDEN_ITEMS = {
            R.id.notifications, R.id.my_repos, R.id.star_hub, R.id.issues_prs
    };  // 0.5.0：my_gists 已下架，不再列入

    private static final SparseArray<String> START_PAGE_MAPPING = new SparseArray<>();
    static {
        START_PAGE_MAPPING.put(R.id.search, "search");
        START_PAGE_MAPPING.put(R.id.feed_hub, "feed_hub");
        START_PAGE_MAPPING.put(R.id.notifications, "notifications");
        START_PAGE_MAPPING.put(R.id.discover_hub, "discover_hub");
        START_PAGE_MAPPING.put(R.id.my_repos, "repos");
        START_PAGE_MAPPING.put(R.id.star_hub, "star_hub");
        START_PAGE_MAPPING.put(R.id.issues_prs, "my_items");
        // 0.1.0：我页成为起始页可选项（此前缺失，选“我”会回落到趋势页）
        START_PAGE_MAPPING.put(R.id.profile, "profile");
        // 0.5.0：my_gists 已下架，旧 "gists" 起始页值经 determineInitialPage 回落到趋势页
    }

    /** 抽屉合并前的旧起始页 key → 新聚合页 id，老用户升级后自动落到聚合页。 */
    private static final java.util.Map<String, Integer> LEGACY_START_PAGE_MAP;
    static {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        m.put("newsfeed", R.id.feed_hub);
        m.put("timeline", R.id.feed_hub);
        m.put("recent_history", R.id.search);
        m.put("issues", R.id.issues_prs);
        m.put("prs", R.id.issues_prs);
        m.put("bookmarks", R.id.star_hub);
        m.put("star_groups", R.id.star_hub);
        m.put("release_radar", R.id.star_hub);
        m.put("trends", R.id.discover_hub);
        m.put("topics", R.id.topic_discovery);  // 0.5.0：Topic 已从趋势页摘出为抽屉独立页
        m.put("blog", R.id.discover_hub);
        LEGACY_START_PAGE_MAP = java.util.Collections.unmodifiableMap(m);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mUserLogin = Gh4Application.get().getAuthLogin();
        if (savedInstanceState != null) {
            mSelectedFactoryId = savedInstanceState.getInt(STATE_KEY_FACTORY_ITEM);
        } else {
            mSelectedFactoryId = determineInitialPage();
            // 0.1.38：启动落页埋点（只记页 id）。
            com.gh4a.utils.DiagTrace.log(this, "INITIAL_PAGE",
                    "id=" + mSelectedFactoryId);
        }
        mFactory = getFactoryForItem(mSelectedFactoryId);
        // 196：保存的 factory id 可能来自已下架的页（旧版本状态恢复），
        // getFactoryForItem 回 null，后续 mFactory 调用即 NPE；回落到
        // determineInitialPage()（恒返回合法页，默认趋势页）。
        if (mFactory == null) {
            mSelectedFactoryId = determineInitialPage();
            mFactory = getFactoryForItem(mSelectedFactoryId);
        }

        mNotificationsIndicatorIcon =
                DrawableCompat.wrap(ContextCompat.getDrawable(this, R.drawable.circle).mutate());

        super.onCreate(savedInstanceState);

        // 0.1.2：初始页就是我页时同样关掉下拉刷新（switchTo 只在切页时走）。
        setCanSwipeToRefresh(mSelectedFactoryId != R.id.profile);

        // 0.1.0：侧边栏下架——左抽屉锁定关闭、去掉汉堡键（BaseActivity 的
        // DrawerLayout 结构本身不动，详情页不受影响；右抽屉工具栏保留）。
        setLeftDrawerLockedClosed();
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
        }

        // View 全量改造：底部导航为主入口（内容仍是原 ViewPager 聚合页，不改数据层）
        setupBottomNav();

        // 深链指定初始 Tab（如 pulls 链接 → 事项页的 PR Tab），只消费一次
        if (getIntent().hasExtra("initial_tab")) {
            int tab = getIntent().getIntExtra("initial_tab", 0);
            getIntent().removeExtra("initial_tab");
            if (getPager() != null && getPager().getAdapter() != null) {
                // 0.1.0 钳制：单页工厂（如 Star 合并页）的页数只有 1，
                // 旧深链 initial_tab=1 不得越界（此前两 Tab 时合法）
                int count = getPager().getAdapter().getCount();
                getPager().setCurrentItem(Math.max(0, Math.min(tab, count - 1)));
            }
        }

        ActionBar actionBar = getSupportActionBar();
        // 0.1.0：无抽屉后不再显示汉堡/返回键（我页为底部 Tab 内嵌页，本身也无返回箭头）
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setHomeButtonEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);

        loadUserInfo(false);
        loadNotificationIndicator(false);
        mFactory.onStartLoadingData();
        setupThemeToggle();
        // 0.1.36：诊断追踪（新页创建时刻，savedInstanceState 区分重建与全新）。
        com.gh4a.utils.ThemeTrace.logState(this, "HOME_CREATED saved=" + (savedInstanceState != null)
                + " factory=" + mSelectedFactoryId + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
        showLastCrashIfAny();

        if (savedInstanceState == null
                && SettingsFragment.isAutoCheckUpdateEnabled(this)) {
            UpdateCheckUi.checkAutomatically(this);
        }
    }

    /** Shows the previous crash's stack trace so the user can report it. */
    private void showLastCrashIfAny() {
        String trace = Gh4Application.get().takeLastCrashTrace();
        if (trace == null || trace.isEmpty()) {
            return;
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(trace);
        tv.setTextSize(11);
        tv.setPadding(32, 32, 32, 32);
        tv.setTextIsSelectable(true);
        scroll.addView(tv);
        AlertDialog crashDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.crash_dialog_title)
                .setView(scroll)
                .setPositiveButton(R.string.dialog_acknowledged, null)
                .show();
        // 0.1.23: crash dialog itself reports to the breadcrumb counter.
        com.gh4a.utils.CrashBreadcrumb.noteDialogShown();
        crashDialog.setOnDismissListener(d -> com.gh4a.utils.CrashBreadcrumb.noteDialogDismissed());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 0.1.37：恢复窗口已过，建页期抑制清掉，此后开关触发均为真点选。
        mSuppressThemeToggle = false;
        com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_UNSUPPRESSED inst="
                + com.gh4a.utils.ThemeTrace.instance(this));
        // 0.5.0：“我”已是 Home 内嵌页，不再跳 UserActivity；同步底部选中态即可
        syncBottomNav();
        // 0.1.13：回 Home 时刷新底栏“我”未读角标（读完通知回来无需重启即清掉）。
        updateMeBadge();
        // 0.1.23：首次进软件通知权限（只问一次，见 maybeRequestNotificationPermission）。
        maybeRequestNotificationPermission();
    }

    /**
     * 0.1.23: on Android 13+, the first resumed Home entry after login asks
     * for POST_NOTIFICATIONS once (flag in SettingsFragment.PREF_NAME, set
     * before launching so it never refires). Skipped below 13 (no such
     * permission), when not logged in (flag left unset, so a later post-login
     * entry still asks), when already granted, or when already asked. Never
     * gates login or Home content; never changes notification content,
     * channels, or scheduling.
     */
    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (!Gh4Application.get().isAuthorized()) {
            return;
        }
        if (getPrefs().getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        getPrefs().edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply();
        mNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // 0.1.13：趋势时间范围固定行（AppBar 内、标题栏下方、不随列表滚动）。
    // TrendingFragment 持有行视图，HomeActivity 只负责把它挂进/摘出 AppBar；
    // 切走趋势页时 Fragment 销毁会摘掉，切回来重建会重新挂上，重复挂时先摘旧行防重。
    private android.view.View mPinnedTrendRangeRow;

    public void setPinnedTrendRangeRow(android.view.View row) {
        if (mPinnedTrendRangeRow != null) {
            removeHeaderView(mPinnedTrendRangeRow);
        }
        mPinnedTrendRangeRow = row;
        if (row != null) {
            addHeaderView(row, false);
        }
    }

    public void removePinnedTrendRangeRow(android.view.View row) {
        if (row != null) {
            removeHeaderView(row);
        }
        if (mPinnedTrendRangeRow == row) {
            mPinnedTrendRangeRow = null;
        }
    }

    /** 0.1.20 D-03: 底栏角标统一落点（我/Star 两处原先约 12 行体几乎相同）。 */
    public void setBottomBadge(@IdRes int id, int count) {
        if (mBottomNav == null) return;
        if (count <= 0) {
            mBottomNav.removeBadge(id);
            return;
        }
        com.google.android.material.badge.BadgeDrawable badge =
                mBottomNav.getOrCreateBadge(id);
        badge.setNumber(count);
        badge.setVisible(true);
    }

    /** 0.1.13：底栏“我”未读角标，与我页通知行同一数据源；0 条时不显示角标。 */
    public void setMeBadge(int count) {
        setBottomBadge(R.id.profile, count);
    }

    /** 0.1.13：用 loader 生命周期的单次请求刷新“我”角标（与我页通知行同一计数口径）。 */
    public void updateMeBadge() {
        if (mBottomNav == null || mUserLogin == null) return;
        // P1：走 NotificationCountProvider——冷启动 4 次调用（setupBottomNav
        // + onResume + Me onCreateView + onResume）共享 1 次拉取；loader
        // 的 force=true 仍每次重跑 Single，但提供方在 TTL 内给缓存、不发网。
        com.gh4a.utils.NotificationCountProvider.getInstance().getNotifications()
                .compose(makeLoaderSingle(ID_LOADER_ME_BADGE, true))
                .subscribe(result -> {
                    // 0.1.20 D-04/Observer: 计数走 countUnread，结果发布到 holder，
                    // 订阅回调统一落到底栏（不再直接 setMeBadge）。
                    mBadgeState.setNotificationCount(countUnread(result.notifications));
                }, error -> {
                    // 失败时保持原角标，不显示假数字
                });
    }

    /**
     * 0.3.1 底栏修复：底栏不再放进 content_container。
     * 旧做法把 ViewPager 包进 LinearLayout wrapper 再 setContentView 回 content_container，
     * 而 content_container 在 SwipeRefreshLayout 内，SwipeRefreshLayout 带
     * AppBarLayout.ScrollingViewBehavior、随 AppBar（Toolbar + 顶部 Tab）折叠而上下移动，
     * 所以初始 AppBar 展开时底栏在屏外、往上拉（AppBar 折叠）后才滑入视野。
     * 新做法：ViewPager 原样留在 content_container；底栏直接加到 CoordinatorLayout
     *（DrawerLayout 内的根容器）作为直接子项、gravity=BOTTOM，固定在屏底不随滚动移动；
     * 同时给 SwipeLayout 留 bottomMargin（= 底栏高度，唯一数值源 R.dimen.bottom_nav_height），
     * 使 pager 内容不被底栏遮住。只动 HomeActivity 外壳，不动任何 Factory/Fragment 的数据逻辑。
     */
    private void setupBottomNav() {
        androidx.coordinatorlayout.widget.CoordinatorLayout coordinator = getRootLayout();
        if (coordinator == null) {
            return;
        }
        View existing = coordinator.findViewById(R.id.bottom_nav);
        if (existing instanceof BottomNavigationView) {
            mBottomNav = (BottomNavigationView) existing;
            // 0.1.20 Observer: Home 订阅 holder，角标唯一落点。
            mBadgeState.addListener((notifications, starUpdates) -> {
                setBottomBadge(R.id.profile, notifications);
                setBottomBadge(R.id.star_hub, starUpdates);
            });
            syncBottomNav();
            updateStarBadgeFromCache();
            updateMeBadge();
            return;
        }
        // 0.3.0 布局：菜单/着色/背景/高程/最小高均在 bottom_nav_bar.xml 里声明。
        BottomNavigationView bottom = (BottomNavigationView)
                getLayoutInflater().inflate(R.layout.bottom_nav_bar, coordinator, false);
        androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams lp =
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.BOTTOM;
        bottom.setLayoutParams(lp);
        bottom.setVisibility(android.view.View.VISIBLE);
        coordinator.addView(bottom);
        // 给滚动内容留出底栏高度，避免 pager 底部内容被固定底栏遮住。
        View swipeLayout = findViewById(R.id.swipe_container);
        if (swipeLayout != null
                && swipeLayout.getLayoutParams()
                        instanceof androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) {
            androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams swipeLp =
                    (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)
                            swipeLayout.getLayoutParams();
            swipeLp.bottomMargin =
                    getResources().getDimensionPixelSize(R.dimen.bottom_nav_height);
            swipeLayout.setLayoutParams(swipeLp);
        }
        bottom.setOnItemSelectedListener(item -> {
            if (mBottomNavSyncing) {
                return true;
            }
            int id = item.getItemId();
            // 0.1.0：底部 5 项全部走工厂（含“我”内嵌页与搜索），不再有 startActivity 跳页
            FragmentFactory factory = getFactoryForItem(id);
            if (factory != null) {
                switchTo(id, factory);
                if (mLeftDrawerMenu != null) {
                    MenuItem drawerItem = mLeftDrawerMenu.findItem(id);
                    if (drawerItem != null) {
                        drawerItem.setChecked(true);
                    }
                }
                updateNotificationIndicator(id);
                return true;
            }
            return false;
        });
        mBottomNav = bottom;
        // 0.1.20 Observer: Home 订阅 holder，角标唯一落点。
        mBadgeState.addListener((notifications, starUpdates) -> {
            setBottomBadge(R.id.profile, notifications);
            setBottomBadge(R.id.star_hub, starUpdates);
        });
        syncBottomNav();
        updateStarBadgeFromCache();
        updateMeBadge();
    }

    /**
     * 0.1.0：我页功能区/计数格的页内导航（抽屉已下架后的替代入口）。
     * 工厂为空时不做任何事、不闪退；调用方（我页）只传有工厂的 id。
     */
    public void navigateToHomePage(int menuId) {
        FragmentFactory factory = getFactoryForItem(menuId);
        if (factory != null) {
            switchTo(menuId, factory);
        }
    }

    /** 0.1.1：我页头像行的切换账号入口（底部动作表，替代 0.1.0 的 AlertDialog）。
     * 逻辑与原来一致：当前账号打勾（不可重复切换）、其他已登录账号列出、
     * 底部“添加账号”走原登录流程；不新增鉴权流程。 */
    public void showAccountSwitcher() {
        android.util.LongSparseArray<String> accounts = Gh4Application.get().getAccounts();
        java.util.List<String> others = new java.util.ArrayList<>();
        for (int i = 0; i < accounts.size(); i++) {
            String login = accounts.valueAt(i);
            if (!ApiHelpers.loginEquals(login, mUserLogin)) {
                others.add(login);
            }
        }
        // 0.1.20 D-05: 骨架走 BottomSheetListBuilder（标题/内边距/行样式一致）。
        com.gh4a.utils.BottomSheetListBuilder builder =
                new com.gh4a.utils.BottomSheetListBuilder(this, getString(R.string.switch_account));
        // 当前账号（打勾，不可点）
        builder.addTextRow(mUserLogin + "  \u2713", null);
        for (String login : others) {
            builder.addTextRow(login, v -> switchActiveUser(login));
        }
        builder.addTextRow(getString(R.string.add_account), v -> {
            LoginModeChooserFragment.newInstance()
                    .show(getSupportFragmentManager(), "loginmode");
        });
        // 0.1.57：退出登录行（红色，字号／边距与“添加账号”一致）；点后先弹确认框。
        builder.addTextRow(getString(R.string.logout),
                ContextCompat.getColor(this, R.color.accent_red),
                v -> showLogoutConfirm());
        builder.show();
    }

    /** 退出登录确认：确认后只清当前账号凭证（现有 logout() 路子），回登录页；取消留在原地。 */
    private void showLogoutConfirm() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    Gh4Application.get().logout();
                    // 清掉内存里的当前用户状态（抽屉退出同款再加 finish()，不留旧账号信息）。
                    mUserLogin = null;
                    goToToplevelActivity();
                    finish();
                })
                .show();
    }

    /** 0.1.2：底栏 Star 角标 = 未读更新数（Star 提醒行已删，角标本身保留）。
     * 有更新数时显示数字角标，无更新时清除角标；从不显示假数字。 */
    public void setStarBadge(int count) {
        setBottomBadge(R.id.star_hub, count);
    }

    /** 从 RadarCache（上次 Star 更新结果）恢复底栏角标，Home 打开即有提醒。 */
    public void updateStarBadgeFromCache() {
        if (mBottomNav == null || mUserLogin == null) return;
        java.util.List<com.gh4a.fragment.ReleaseRadarFragment.RadarItem> cached =
                com.gh4a.fragment.RadarCache.load(this, mUserLogin);
        // 0.1.2：角标只计未读（已读的更新不再计入，重启 App 也不复活）。
        mBadgeState.setStarUpdateCount(com.gh4a.fragment.StarReadState.unreadCount(cached,
                com.gh4a.fragment.StarReadState.getReadKeys(this, mUserLogin)));
    }

    /** 0.5.0：趋势页时间范围选择行回调。当前工厂为趋势页时改范围并刷新页面。 */
    public void setTrendRange(int range) {
        if (mFactory instanceof DiscoverHubFactory) {
            ((DiscoverHubFactory) mFactory).setTrendRange(range);
            invalidateFragments();
        }
    }

    /** 底部导航选中态与当前聚合页同步；非底部 5 项的聚合页（通知、仓库、事项等）不强制选中。 */
    private void syncBottomNav() {
        if (mBottomNav == null) {
            return;
        }
        int bottomId;
        switch (mSelectedFactoryId) {
            case R.id.discover_hub:
            case R.id.feed_hub:
            case R.id.search:
            case R.id.star_hub:
            case R.id.profile:
                bottomId = mSelectedFactoryId;
                break;
            default:
                // 清除全部选中，避免抽屉页误显示为底部某项
                mBottomNavSyncing = true;
                for (int i = 0; i < mBottomNav.getMenu().size(); i++) {
                    mBottomNav.getMenu().getItem(i).setChecked(false);
                }
                mBottomNavSyncing = false;
                return;
        }
        if (mBottomNav.getSelectedItemId() != bottomId) {
            mBottomNavSyncing = true;
            mBottomNav.setSelectedItemId(bottomId);
            mBottomNavSyncing = false;
        }
    }

    /**
     * View 全量改造：Home 新建入口。Issue 有真实新建屏；无仓库上下文时先填 owner/repo，
     * 不留无说明占位。PR 旧 App 无应用内新建表单，填仓库后走浏览器对比页（对话框内明说）；
     * Gist 旧 App 无新建屏，本入口不提供假的 Gist 新建（见跟踪表清单外）。
     */
    private void showCreateDialog() {
        String[] options = new String[]{
                getString(R.string.issue_create),
                getString(R.string.create_pr_web),
        };  // 0.5.0：我的 Gist 已下架，新建入口不再进 Gist 列表
        new AlertDialog.Builder(this)
                .setTitle(R.string.bottom_nav_create)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRepoInputDialog(false);
                    } else if (which == 1) {
                        showRepoInputDialog(true);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRepoInputDialog(boolean forPr) {
        EditText input = new EditText(this);
        input.setHint(R.string.create_repo_hint);
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density + 0.5f);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.bottom_nav_create)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String repo = input.getText().toString().trim();
                    int slash = repo.indexOf('/');
                    if (slash <= 0 || slash >= repo.length() - 1) {
                        Toast.makeText(this, R.string.create_repo_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String owner = repo.substring(0, slash).trim();
                    String name = repo.substring(slash + 1).trim();
                    // 128：只校验斜杠位置不够——`a b/c` 拼出的 URL 含空格，
                    // startActivity 可抛 ActivityNotFoundException；且不编码的
                    // 特殊字符会拼出非法 URL。按 GitHub 命名规则校验字符，
                    // URL 用 Uri.Builder 拼接（自动编码）。
                    if (!owner.matches("[A-Za-z0-9]([A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
                            || !name.matches("[A-Za-z0-9._-]+")
                            || name.startsWith(".") || name.endsWith(".")
                            || name.contains("..")) {
                        Toast.makeText(this, R.string.create_repo_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (forPr) {
                        Toast.makeText(this, R.string.create_pr_web_note,
                                Toast.LENGTH_LONG).show();
                        Intent browser = new Intent(Intent.ACTION_VIEW,
                                new Uri.Builder().scheme("https").authority("github.com")
                                        .appendPath(owner).appendPath(name)
                                        .appendPath("compare").build());
                        // 195：隐式浏览器 intent 在无浏览器设备上抛
                        // ActivityNotFoundException；接住并提示。
                        try {
                            startActivity(browser);
                        } catch (android.content.ActivityNotFoundException e) {
                            Toast.makeText(this, R.string.no_browser_found,
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        startActivity(IssueEditActivity.makeCreateIntent(this, owner, name));
                    }
                });
        builder.show();
    }

    private boolean isNightModeActive() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    // 0.1.31：主题开关写偏好后整栈重启、落回当前所在 tab。复用语言切换
    // 同款 NEW_TASK | CLEAR_TASK 机制（见 MeFragment.restartStackOnMeTab），
    // 绕开本机已死的 recreate()/relaunch 重建路；与 0.1.25 前 CLEAR_TOP
    // 起自身 + 显式 finish() 不同，整栈重启不存在同一实例被销毁两次。
    // mSelectedFactoryId 若不在 START_PAGE_MAPPING 里（如个别抽屉页），
    // makeIntent 不带 initial_page，落点回落到用户设置的起始页。
    // 0.1.32：改从 Application Context 发，理由同 MeFragment.restartStackOnMeTab
    // （系统侧重建可能已先销毁本 Activity，从已销毁 Activity 发的启动会被丢掉）。
    private void restartStackOnCurrentTab() {
        android.content.Intent intent = makeIntent(this, mSelectedFactoryId);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> {
                    com.gh4a.utils.ThemeTrace.logState(this, "RESTART_FIRED tab inst="
                            + com.gh4a.utils.ThemeTrace.instance(this));
                    // 0.1.38：整栈重启链埋点（只记落页 id）。
                    com.gh4a.utils.DiagTrace.log(this, "RESTART_FIRED",
                            "id=" + mSelectedFactoryId);
                    getApplicationContext().startActivity(intent);
                });
    }

    private void setupThemeToggle() {
        NavigationView leftDrawer = findViewById(R.id.left_drawer);
        if (leftDrawer == null) {
            // 0.1.36：诊断追踪。
            com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_SETUP no_drawer inst="
                    + com.gh4a.utils.ThemeTrace.instance(this));
            return;
        }
        MenuItem themeItem = leftDrawer.getMenu().findItem(R.id.theme_toggle);
        if (themeItem == null) {
            com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_SETUP no_item inst="
                    + com.gh4a.utils.ThemeTrace.instance(this));
            return;
        }
        mThemeSwitch = (SwitchCompat) themeItem.getActionView();
        // 0.1.37：建页开始即抑制，直到 onResume 才放开。
        mSuppressThemeToggle = true;
        // 0.1.36：诊断追踪（开关建起来时刻的勾选与页面昼夜）。
        com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_SETUP before_set checked="
                + mThemeSwitch.isChecked() + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
        mThemeSwitch.setChecked(isNightModeActive());
        com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_SETUP after_set checked="
                + mThemeSwitch.isChecked() + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
        mThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 0.1.36：诊断追踪（开关监听一旦跑起来，必留痕——这是复位嫌疑点）。
            com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_FIRED isChecked=" + isChecked
                    + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
            // 0.1.37：建页期抑制——恢复窗口内的误触发不写偏好、不定开关、不重启。
            if (mSuppressThemeToggle) {
                com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_IGNORED_SUPPRESSED isChecked=" + isChecked
                        + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
                return;
            }
            // Manual toggle always wins: drop out of follow-system/scheduled mode
            // 0.1.33：模式与主题同一 editor 一次 commit() 同步落盘再重启——
            // apply 只改内存，整栈重启杀进程会丢值（见我页对话框同款根因）。
            android.content.SharedPreferences.Editor editor = getPrefs().edit();
            if (!com.gh4a.utils.DarkModeScheduler.MODE_MANUAL
                    .equals(com.gh4a.utils.DarkModeScheduler.getMode(this))) {
                editor.putString(com.gh4a.utils.DarkModeScheduler.KEY_DARK_MODE,
                        com.gh4a.utils.DarkModeScheduler.MODE_MANUAL);
                android.widget.Toast.makeText(this, R.string.dark_mode_switched_manual,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            // Theme values mirror Gh4Application: THEME_DARK = 0, THEME_LIGHT = 1
            editor.putInt(SettingsFragment.KEY_THEME, isChecked ? 0 : 1);
            editor.commit();
            // 0.1.35：与我页对话框同款，开关直接定，不只靠主题监听。
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(isChecked
                    ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            com.gh4a.utils.ThemeTrace.logState(this, "TOGGLE_AFTER_SET isChecked=" + isChecked);
            // 0.1.31：写偏好后整栈重启、落回当前所在 tab。0.1.25 删掉
            // goToToplevelActivity() + finish() 后只剩 setDefaultNightMode
            // 的系统侧重建，但 0.1.27–0.1.30 取证证明 recreate()/relaunch
            // 重建路在本机已死（旧实例被销毁后交不出深色新实例），开关与
            // 我页对话框同病。整栈重启（NEW_TASK | CLEAR_TASK）与 0.1.25
            // 前 CLEAR_TOP 起自身 + 显式 finish() 不是一回事：整个任务清掉、
            // 起全新实例，不存在同一实例被销毁两次，故不复现 DecorView
            // not attached 崩溃。
            restartStackOnCurrentTab();
        });
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(mFactory.getTitleResId());
    }

    private void updateNotificationIndicator(int checkedItemId) {
        if (mNotificationsIndicator == null) {
            return;
        }

        @AttrRes int colorResId = checkedItemId == R.id.notifications
                ? androidx.appcompat.R.attr.colorAccent : android.R.attr.textColorPrimary;
        @ColorInt int tint = UiUtils.resolveColor(this, colorResId);
        DrawableCompat.setTint(mNotificationsIndicatorIcon, tint);
        mNotificationsIndicator.setImageDrawable(mNotificationsIndicatorIcon);
    }

    public void setNotificationsIndicatorVisible(boolean visible) {
        if (mNotificationsIndicator != null) {
            mNotificationsIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
            mNotificationsMenuItem.setIcon(visible
                    ? R.drawable.icon_notifications_unread
                    : R.drawable.icon_notifications);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_KEY_FACTORY_ITEM, mSelectedFactoryId);
        mFactory.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            mFactory.onRestoreInstanceState(savedInstanceState);
        }
    }

    @Override
    public void onClick(View view) {
        updateDrawerMode(!mDrawerInAccountMode);
    }

    @Override
    protected int getLeftNavigationDrawerMenuResource() {
        return R.menu.home_nav_drawer;
    }

    @Override
    protected int getInitialLeftDrawerSelection(Menu menu) {
        mLeftDrawerMenu = menu;

        // Apply user customization: reorder/hide drawer items
        applyDrawerCustomization(menu);

        // If the selected page was hidden by the user, fall back to first visible
        if (menu.findItem(mSelectedFactoryId) == null) {
            java.util.List<DrawerManager.DrawerItemDef> visible =
                    DrawerManager.getVisibleOrderedItems(this);
            if (!visible.isEmpty()) {
                mSelectedFactoryId = visible.get(0).menuId;
                mFactory = getFactoryForItem(mSelectedFactoryId);
            }
        }

        mNotificationsMenuItem = menu.findItem(R.id.notifications);
        if (mNotificationsMenuItem != null) {
            View actionView = mNotificationsMenuItem.getActionView();
            mNotificationsIndicator = actionView.findViewById(R.id.notifications_indicator);
            updateNotificationIndicator(mSelectedFactoryId);
        }

        return mSelectedFactoryId;
    }

    /**
     * Rebuilds the customizable drawer items according to the user's order
     * and visibility settings. Items are placed in a single flat group;
     * settings/theme stay at the bottom because their menu order (1000+ in
     * home_nav_drawer.xml) is greater than any order assigned here (0..n).
     */
    private void applyDrawerCustomization(Menu menu) {
        // Remove all customizable items defined in XML
        for (DrawerManager.DrawerItemDef def : DrawerManager.getDefaultItems()) {
            menu.removeItem(def.menuId);
        }
        // Re-add visible items in user order
        java.util.List<DrawerManager.DrawerItemDef> visible =
                DrawerManager.getVisibleOrderedItems(this);
        int order = 0;
        for (DrawerManager.DrawerItemDef def : visible) {
            MenuItem item = menu.add(R.id.my_items, def.menuId, order++, def.titleRes);
            item.setIcon(def.iconRes);
            if (def.menuId == R.id.notifications) {
                // Re-attach the unread indicator action view
                item.setActionView(R.layout.notifications_indicator);
            }
        }
        menu.setGroupCheckable(R.id.my_items, true, true);
    }

    @Override
    protected int[] getRightNavigationDrawerMenuResources() {
        return mFactory.getToolDrawerMenuResIds();
    }

    @Override
    protected int getInitialRightDrawerSelection() {
        return mFactory.getInitialToolDrawerSelection();
    }

    @Override
    protected void onPrepareRightNavigationDrawerMenu(Menu menu) {
        super.onPrepareRightNavigationDrawerMenu(menu);
        mFactory.prepareToolDrawerMenu(menu);
    }

    @Override
    protected void configureLeftDrawerHeader(View header) {
        super.configureLeftDrawerHeader(header);

        mAvatarView = header.findViewById(R.id.avatar);
        mUserExtraView = header.findViewById(R.id.user_extra);

        TextView userNameView = header.findViewById(R.id.user_name);
        userNameView.setText(mUserLogin);

        updateUserInfo();

        mDrawerSwitcher = header.findViewById(R.id.switcher);
        mDrawerSwitcher.setVisibility(View.VISIBLE);

        mDrawerSwitcher.setOnClickListener(this);

        View clickableBackground = header.findViewById(R.id.drawer_header);
        clickableBackground.setOnClickListener(this);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        super.onNavigationItemSelected(item);

        updateNotificationIndicator(item.getItemId());

        if (mFactory != null && mFactory.onDrawerItemSelected(item)) {
            return true;
        }

        int id = item.getItemId();
        FragmentFactory factory = getFactoryForItem(id);

        if (factory != null) {
            switchTo(id, factory);
            return true;
        }

        switch (id) {
            // 0.5.0：R.id.profile 已由上方的工厂分支处理（“我”内嵌页），不再进 UserActivity
            case R.id.logout:
                Gh4Application.get().logout();
                goToToplevelActivity();
                finish();
                return true;
            case R.id.add_account:
                LoginModeChooserFragment.newInstance().show(getSupportFragmentManager(), "loginmode");
                return true;
            case R.id.settings:
                mSettingsLauncher.launch(null);
                return true;
            case R.id.theme_toggle:
                if (mThemeSwitch != null) {
                    mThemeSwitch.toggle();
                }
                return true;
        }

        int accountCount = Gh4Application.get().getAccounts().size();
        if (id >= OTHER_ACCOUNTS_GROUP_BASE_ID && id < OTHER_ACCOUNTS_GROUP_BASE_ID + accountCount) {
            switchActiveUser(item.getTitle().toString());
            return true;
        }

        return false;
    }

    @Override
    protected void onDrawerClosed(boolean right) {
        super.onDrawerClosed(right);
        if (!right) {
            updateDrawerMode(false);
        }
    }

    private void switchActiveUser(String login) {
        Gh4Application.get().setActiveLogin(login);
        mUserLogin = login;
        onRefresh();
        closeDrawers();
        switchTo(mSelectedFactoryId, getFactoryForItem(mSelectedFactoryId));
        // 0.1.21 Item 1: 账号切换表是先 dismiss 再回调（BottomSheetListBuilder），
        // 与我页单选对话框同款竞态；post 到主线程下一轮再 recreate。
        // 其余调用点（抽屉/账号选择器回调）post 亦无害。
        // 0.1.22：执行时再判存活，已销毁/正在结束就跳过（系统侧重建可能
        // 已先销毁本实例，对它再 recreate 会复现 DecorView 崩溃）。
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (!isDestroyed() && !isFinishing()) {
                recreate();
            }
        });
    }

    private FragmentFactory getFactoryForItem(int id) {
        switch (id) {
            case R.id.feed_hub:
                return new FeedHubFactory(this, mUserLogin);
            case R.id.search:
                return new SearchHubFactory(this);
            case R.id.issues_prs:
                return new IssuesPrsFactory(this, mUserLogin, getPrefs());
            case R.id.star_hub:
                return new StarHubFactory(this, mUserLogin, getPrefs());
            case R.id.discover_hub:
                return new DiscoverHubFactory(this);
            case R.id.notifications:
                return new NotificationListFactory(this);
            case R.id.my_repos:
                return new RepositoryFactory(this, mUserLogin, getPrefs());
            case R.id.topic_discovery:
                return new TopicDiscoveryFactory(this);
            case R.id.profile:
                return new UserHubFactory(this, mUserLogin);
            case R.id.my_public_events:
                return new UserPublicEventsFactory(this, mUserLogin);
            case R.id.my_recent_history:
                return new RecentHistoryFactory(this);
        }
        return null;
    }

    // 0.1.11：动态页（FeedHubFactory）标题栏固定 + 白底 Tab 行。
    // mFactory 在 super.onCreate 之前已赋值，故 BasePagerActivity 初次
    // 建 Tab 时钩子已生效；切页时 switchTo → invalidateTabs 会重算。
    @Override
    protected boolean isToolbarFixed() {
        return mFactory instanceof FeedHubFactory;
    }

    @Override
    protected boolean useWhiteTabRow() {
        return mFactory instanceof FeedHubFactory;
    }

    // 0.1.17：首页手指切页只在多 Tab 工厂开启（动态 关注/公开，
    // 其两页本身就是 ViewPager 页，手指横滑由 ViewPager 处理）。
    // 单 Tab 工厂（趋势/Star/搜索/我）关闭手指切页：否则 ViewPager
    // 会在列表收到松手前先拦截横拖，页内左右滑（距离判定）永远
    // 算不出位移、看起来完全没反应；底栏点按与程序切页不受影响。
    @Override
    protected boolean isPagerTouchPagingEnabled() {
        int[] tabs = mFactory != null ? mFactory.getTabTitleResIds() : null;
        return tabs != null && tabs.length > 1;
    }

    @Override
    protected int[] getTabTitleResIds() {
        return mFactory.getTabTitleResIds();
    }

    @Override
    protected int[] getHeaderColorAttrs() {
        return mFactory.getHeaderColorAttrs();
    }

    @Override
    protected Fragment makeFragment(int position) {
        return mFactory.makeFragment(position);
    }

    @Override
    protected void onFragmentInstantiated(Fragment f, int position) {
        mFactory.onFragmentInstantiated(f, position);
    }

    @Override
    protected void onFragmentDestroyed(Fragment f) {
        mFactory.onFragmentDestroyed(f);
    }

    /** 当前选中的 Tab 位置，供聚合 Factory（如 FeedHubFactory）定位激活的子页面。 */
    public int getCurrentTabPosition() {
        return getPager() != null ? getPager().getCurrentItem() : 0;
    }

    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        // 0.1.38：页内 Tab 切换埋点（只记位置）。
        com.gh4a.utils.DiagTrace.log(this, "TAB_SELECTED", "pos=" + position);
        // 聚合页切 Tab 时刷新菜单：子页面的菜单项（如动态页的组织切换器、
        // 事项页的开/关切换、趋势页的时间范围）只在对应 Tab 显示
        if (mFactory != null && mFactory.refreshMenuOnTabSwitch()) {
            supportInvalidateOptionsMenu();
        }
    }

    @Override
    public void onLoginStartOauth() {
        Github4AndroidActivity.launchOauthLogin(this);
    }

    @Override
    public void onLoginFinished(String token, User user) {
        Gh4Application.get().addAccount(user, token);
        switchActiveUser(user.login());
    }

    @Override
    public void onLoginFailed(Throwable error) {
        // 抽屉添加账号 token 失败时给出提示，避免静默无反应
        Toast.makeText(this, R.string.login_failed, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onLoginCanceled() {
        // Nothing to do
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean handled = mFactory.onCreateOptionsMenu(menu);
        // 0.1.2：底栏 5 页（趋势/动态/搜索/Star/我）顶栏不再显示三个点：新建入口
        // 不再加到这些页的溢出菜单里（新建功能在事项页已有）；其他 Home 页保留。
        boolean isBottomPage = mSelectedFactoryId == R.id.discover_hub
                || mSelectedFactoryId == R.id.feed_hub
                || mSelectedFactoryId == R.id.search
                || mSelectedFactoryId == R.id.star_hub
                || mSelectedFactoryId == R.id.profile;
        if (!isBottomPage) {
            // View 全量改造：工具栏“新建”入口（Issue 真实新建 / PR 浏览器新建，见 showCreateDialog）
            menu.add(Menu.NONE, R.id.create_new, Menu.NONE, R.string.bottom_nav_create);
        }
        if (handled) {
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.create_new) {
            showCreateDialog();
            return true;
        }
        if (mFactory.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Intent navigateUp() {
        return getToplevelActivityIntent();
    }

    @Override
    public void onRefresh() {
        loadUserInfo(true);
        loadNotificationIndicator(true);
        mFactory.onRefresh();
        super.onRefresh();
    }

    @Override
    public void supportInvalidateOptionsMenu() {
        //noinspection StatementWithEmptyBody
        if (mFactory instanceof RepositoryFactory) {
            // happens when load is done; we ignore it as we don't want to close the IME in that case
        } else {
            super.supportInvalidateOptionsMenu();
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (!closeDrawers() && fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            int initialPage = determineInitialPage();
            if (mSelectedFactoryId != initialPage) {
                // 0.1.0 null 保护：工厂/抽屉项缺失时不闪退，只回退到默认页
                FragmentFactory initialFactory = getFactoryForItem(initialPage);
                if (initialFactory != null) {
                    switchTo(initialPage, initialFactory);
                }
                if (mLeftDrawerMenu != null) {
                    MenuItem drawerItem = mLeftDrawerMenu.findItem(initialPage);
                    if (drawerItem != null) {
                        drawerItem.setChecked(true);
                    }
                }
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public void initiateFilter() {
        toggleRightSideDrawer();
    }

    @Override
    protected boolean fragmentNeedsRefresh(Fragment object) {
        return true;
    }

    public void doInvalidateOptionsMenuAndToolDrawer() {
        super.supportInvalidateOptionsMenu();
        updateRightNavigationDrawer();
    }

    @Override
    public void invalidateTabs() {
        super.invalidateTabs();
    }

    @Override
    public void invalidateFragments() {
        super.invalidateFragments();
    }

    public void toggleToolDrawer() {
        toggleRightSideDrawer();
    }

    public void invalidateTitle() {
        getSupportActionBar().setTitle(mFactory.getTitleResId());
    }

    private int determineInitialPage() {
        final String initialPage;
        if (getIntent().hasExtra("initial_page")) {
            initialPage = getIntent().getStringExtra("initial_page");
            // consider initial page passed via intent only once
            getIntent().removeExtra("initial_page");
        } else {
            // 0.5.0：默认首页改为趋势页；用户在设置里设了其他起始页仍尊重设置
            final String prefPage = getPrefs().getString(SettingsFragment.KEY_START_PAGE, "discover_hub");
            initialPage = TextUtils.equals(prefPage, "last")
                    ? getPrefs().getString("last_selected_home_page", "discover_hub")
                    : prefPage;
        }
        for (int i = 0; i < START_PAGE_MAPPING.size(); i++) {
            if (TextUtils.equals(initialPage, START_PAGE_MAPPING.valueAt(i))) {
                return START_PAGE_MAPPING.keyAt(i);
            }
        }
        // 合并兼容：旧起始页 key 映射到聚合页
        Integer legacy = LEGACY_START_PAGE_MAP.get(initialPage);
        if (legacy != null) {
            return legacy;
        }
        // 0.5.0：未知旧值（含已下架的 "gists"）回落到趋势页，不崩不空页
        return R.id.discover_hub;
    }

    private void updateUserInfo() {
        if (mUserInfo == null) {
            mAvatarView.setImageDrawable(new AvatarHandler.DefaultAvatarDrawable(mUserLogin, null));
            return;
        }
        if (mAvatarView != null) {
            AvatarHandler.assignAvatar(mAvatarView, mUserInfo);
        }
        if (mUserExtraView != null) {
            if (TextUtils.isEmpty(mUserInfo.name())) {
                mUserExtraView.setVisibility(View.GONE);
            } else {
                mUserExtraView.setText(mUserInfo.name());
                mUserExtraView.setVisibility(View.VISIBLE);
            }
        }
        mFactory.setUserInfo(mUserInfo);
    }

    private void updateDrawerMode(boolean accountMode) {
        mLeftDrawerMenu.setGroupVisible(R.id.my_items, !accountMode);
        // navigation/explore 组在抽屉自定义后已被清空（item 并入 my_items），
        // 这里按实际 item 显隐 search/blog/趋势/时间线/话题，不再依赖已不存在的组
        for (int itemId : ACCOUNT_MODE_HIDDEN_ITEMS) {
            MenuItem item = mLeftDrawerMenu.findItem(itemId);
            if (item != null) {
                item.setVisible(!accountMode);
            }
        }
        mLeftDrawerMenu.setGroupVisible(R.id.settings_group, !accountMode);
        mLeftDrawerMenu.setGroupVisible(R.id.account, accountMode);
        mLeftDrawerMenu.setGroupVisible(R.id.other_accounts, accountMode);

        if (accountMode) {
            // repopulate other account list
            for (int i = 0; ; i++) {
                MenuItem item = mLeftDrawerMenu.findItem(OTHER_ACCOUNTS_GROUP_BASE_ID + i);
                if (item == null) {
                    break;
                }
                mLeftDrawerMenu.removeItem(item.getItemId());
            }

            int id = OTHER_ACCOUNTS_GROUP_BASE_ID;
            LongSparseArray<String> accounts = Gh4Application.get().getAccounts();
            for (int i = 0; i < accounts.size(); i++) {
                String login = accounts.valueAt(i);
                if (ApiHelpers.loginEquals(mUserLogin, login)) {
                    continue;
                }

                MenuItem item = mLeftDrawerMenu.add(R.id.other_accounts, id++, Menu.NONE, login);
                AvatarHandler.assignAvatar(this, item, login, accounts.keyAt(i));
            }
        }

        mDrawerSwitcher.setImageResource(accountMode
                ? R.drawable.drop_up_arrow_white : R.drawable.drop_down_arrow_white);
        mDrawerInAccountMode = accountMode;
    }

    private void switchTo(int itemId, FragmentFactory factory) {
        // 0.1.38：切页链埋点（只记页 id，不记内容）。
        com.gh4a.utils.DiagTrace.log(this, "SWITCH_TO", "id=" + itemId);
        if (mFactory != null) {
            mFactory.onDestroy();
        }
        mFactory = factory;
        mSelectedFactoryId = itemId;
        // 0.1.2：我页关掉下拉刷新（上下拉只滚动，不触发刷新）；其他页不受影响。
        setCanSwipeToRefresh(itemId != R.id.profile);
        mFactory.setUserInfo(mUserInfo);
        mFactory.onStartLoadingData();

        String pageKey = START_PAGE_MAPPING.get(mSelectedFactoryId);
        if (pageKey != null) {
            getPrefs().edit().putString("last_selected_home_page", pageKey).apply();
        }

        setErrorViewVisibility(false, null);
        updateRightNavigationDrawer();
        super.supportInvalidateOptionsMenu();
        getSupportFragmentManager().popBackStackImmediate(null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        invalidateTitle();
        invalidateTabs();
        syncBottomNav();
    }

    private void loadUserInfo(boolean force) {
        UserService service = ServiceFactory.get(UserService.class, force);
        service.getUser(mUserLogin)
                .map(ApiHelpers::throwOnFailure)
                .compose(makeLoaderSingle(ID_LOADER_USER, force))
                .subscribe(result -> {
                    Gh4Application.get().setCurrentAccountInfo(result);
                    mUserInfo = result;
                    updateUserInfo();
                }, this::handleLoadFailure);
    }

    private void loadNotificationIndicator(boolean force) {
        NotificationService service = ServiceFactory.get(NotificationService.class, force, 1);
        HashMap<String, Object> options = new HashMap<>();
        options.put("all", false);
        options.put("participating", false);

        service.getNotifications(options, 1)
                .map(ApiHelpers::throwOnFailure)
                // 133：Page.items() 可空（见 ApiHelpers.SearchPageAdapter 的 null 分支），
                // 未判空即 NPE。
                .map(result -> result != null && result.items() != null
                        && !result.items().isEmpty())
                .compose(makeLoaderSingle(ID_LOADER_NOTIFICATIONS_INDICATOR, force))
                .subscribe(this::setNotificationsIndicatorVisible, this::handleLoadFailure);
    }
}
