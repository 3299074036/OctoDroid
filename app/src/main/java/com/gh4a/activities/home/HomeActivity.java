package com.gh4a.activities.home;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.gh4a.BaseFragmentPagerActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.Github4AndroidActivity;
import com.gh4a.activities.UserActivity;
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

    private final ActivityResultLauncher<Void> mSettingsLauncher = registerForActivityResult(
            new ActivityResultHelpers.StartSettingsContract(),
            themeChanged -> {
                if (themeChanged) {
                    goToToplevelActivity();
                    finish();
                }
            });

    private static final String STATE_KEY_FACTORY_ITEM = "factoryItem";

    private static final int ID_LOADER_USER = 0;
    private static final int ID_LOADER_NOTIFICATIONS_INDICATOR = 1;

    private static final int OTHER_ACCOUNTS_GROUP_BASE_ID = 1000;

    private static final SparseArray<String> START_PAGE_MAPPING = new SparseArray<>();
    static {
        START_PAGE_MAPPING.put(R.id.news_feed, "newsfeed");
        START_PAGE_MAPPING.put(R.id.notifications, "notifications");
        START_PAGE_MAPPING.put(R.id.my_repos, "repos");
        START_PAGE_MAPPING.put(R.id.my_issues, "issues");
        START_PAGE_MAPPING.put(R.id.my_prs, "prs");
        START_PAGE_MAPPING.put(R.id.my_gists, "gists");
        START_PAGE_MAPPING.put(R.id.pub_timeline, "timeline");
        START_PAGE_MAPPING.put(R.id.trend, "trends");
        START_PAGE_MAPPING.put(R.id.topic_discovery, "topics");
        START_PAGE_MAPPING.put(R.id.release_radar, "release_radar");
        START_PAGE_MAPPING.put(R.id.recent_history, "recent_history");
        START_PAGE_MAPPING.put(R.id.star_groups, "star_groups");
        START_PAGE_MAPPING.put(R.id.blog, "blog");
        START_PAGE_MAPPING.put(R.id.bookmarks, "bookmarks");
        START_PAGE_MAPPING.put(R.id.search, "search");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mUserLogin = Gh4Application.get().getAuthLogin();
        if (savedInstanceState != null) {
            mSelectedFactoryId = savedInstanceState.getInt(STATE_KEY_FACTORY_ITEM);
        } else {
            mSelectedFactoryId = determineInitialPage();
        }
        mFactory = getFactoryForItem(mSelectedFactoryId);

        mNotificationsIndicatorIcon =
                DrawableCompat.wrap(ContextCompat.getDrawable(this, R.drawable.circle).mutate());

        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        loadUserInfo(false);
        loadNotificationIndicator(false);
        mFactory.onStartLoadingData();
        setupThemeToggle();
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
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("上次闪退的错误信息（截图发给开发者）")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }

    private boolean isNightModeActive() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void setupThemeToggle() {
        NavigationView leftDrawer = findViewById(R.id.left_drawer);
        if (leftDrawer == null) {
            return;
        }
        MenuItem themeItem = leftDrawer.getMenu().findItem(R.id.theme_toggle);
        if (themeItem == null) {
            return;
        }
        mThemeSwitch = (SwitchCompat) themeItem.getActionView();
        mThemeSwitch.setChecked(isNightModeActive());
        mThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Manual toggle always wins: drop out of follow-system/scheduled mode
            if (!com.gh4a.utils.DarkModeScheduler.MODE_MANUAL
                    .equals(com.gh4a.utils.DarkModeScheduler.getMode(this))) {
                getPrefs().edit().putString(com.gh4a.utils.DarkModeScheduler.KEY_DARK_MODE,
                        com.gh4a.utils.DarkModeScheduler.MODE_MANUAL).apply();
                android.widget.Toast.makeText(this, R.string.dark_mode_switched_manual,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            // Theme values mirror Gh4Application: THEME_DARK = 0, THEME_LIGHT = 1
            getPrefs().edit().putInt(SettingsFragment.KEY_THEME, isChecked ? 0 : 1).apply();
            goToToplevelActivity();
            finish();
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
     * the bottom fixed area (settings, theme) is left untouched.
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
            case R.id.profile:
                startActivity(UserActivity.makeIntent(this, mUserLogin));
                return true;
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
        recreate();
    }

    private FragmentFactory getFactoryForItem(int id) {
        switch (id) {
            case R.id.news_feed:
                return new NewsFeedFactory(this, mUserLogin);
            case R.id.notifications:
                return new NotificationListFactory(this);
            case R.id.my_repos:
                return new RepositoryFactory(this, mUserLogin, getPrefs());
            case R.id.my_issues:
                return new IssueListFactory(this, mUserLogin, false, getPrefs());
            case R.id.my_prs:
                return new IssueListFactory(this, mUserLogin, true, getPrefs());
            case R.id.my_gists:
                return new GistFactory(this, mUserLogin);
            case R.id.search:
                return new SearchFactory(this);
            case R.id.bookmarks:
                return new BookmarkFactory(this, mUserLogin, getPrefs());
            case R.id.pub_timeline:
                return new TimelineFactory(this);
            case R.id.blog:
                return new BlogFactory(this);
            case R.id.trend:
                return new TrendingFactory(this);
            case R.id.topic_discovery:
                return new TopicDiscoveryFactory(this);
            case R.id.release_radar:
                return new ReleaseRadarFactory(this, mUserLogin);
            case R.id.recent_history:
                return new RecentHistoryFactory(this);
            case R.id.download_manager:
                return new DownloadListFactory(this);
            case R.id.star_groups:
                return new StarGroupFactory(this);
        }
        return null;
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
        // TODO
    }

    @Override
    public void onLoginCanceled() {
        // Nothing to do
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mFactory.onCreateOptionsMenu(menu)) {
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
                switchTo(initialPage, getFactoryForItem(initialPage));
                if (mLeftDrawerMenu != null) {
                    mLeftDrawerMenu.findItem(initialPage).setChecked(true);
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
            final String prefPage = getPrefs().getString(SettingsFragment.KEY_START_PAGE, "newsfeed");
            initialPage = TextUtils.equals(prefPage, "last")
                    ? getPrefs().getString("last_selected_home_page", "newsfeed")
                    : prefPage;
        }
        for (int i = 0; i < START_PAGE_MAPPING.size(); i++) {
            if (TextUtils.equals(initialPage, START_PAGE_MAPPING.valueAt(i))) {
                return START_PAGE_MAPPING.keyAt(i);
            }
        }
        return R.id.news_feed;
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
        mLeftDrawerMenu.setGroupVisible(R.id.navigation, !accountMode);
        mLeftDrawerMenu.setGroupVisible(R.id.explore, !accountMode);
        mLeftDrawerMenu.setGroupVisible(R.id.settings, !accountMode);
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
        if (mFactory != null) {
            mFactory.onDestroy();
        }
        mFactory = factory;
        mSelectedFactoryId = itemId;
        mFactory.setUserInfo(mUserInfo);
        mFactory.onStartLoadingData();

        getPrefs().edit()
                .putString("last_selected_home_page", START_PAGE_MAPPING.get(mSelectedFactoryId))
                .apply();

        setErrorViewVisibility(false, null);
        updateRightNavigationDrawer();
        super.supportInvalidateOptionsMenu();
        getSupportFragmentManager().popBackStackImmediate(null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        invalidateTitle();
        invalidateTabs();
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
                .map(result -> !result.items().isEmpty())
                .compose(makeLoaderSingle(ID_LOADER_NOTIFICATIONS_INDICATOR, force))
                .subscribe(this::setNotificationsIndicatorVisible, this::handleLoadFailure);
    }
}
