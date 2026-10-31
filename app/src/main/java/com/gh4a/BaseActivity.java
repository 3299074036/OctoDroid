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
package com.gh4a;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.assist.AssistContent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.IssueEditActivity;
import com.google.android.material.appbar.AppBarLayout;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;

import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.TextView;

import com.gh4a.activities.Github4AndroidActivity;
import com.gh4a.activities.SearchActivity;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.fragment.SettingsFragment;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.UiUtils;
import com.gh4a.widget.SwipeRefreshLayout;
import com.gh4a.widget.ToggleableAppBarLayoutBehavior;
import com.meisolsson.githubsdk.model.ClientErrorResponse;
import com.meisolsson.githubsdk.model.Issue;
import com.philosophicalhacker.lib.RxLoader;
import com.squareup.moshi.JsonDataException;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import io.reactivex.SingleTransformer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public abstract class BaseActivity extends AppCompatActivity implements
        SwipeRefreshLayout.OnRefreshListener,
        ActivityCompat.OnRequestPermissionsResultCallback,
        NavigationView.OnNavigationItemSelectedListener {
    public interface RefreshableChild {
        void onRefresh();
    }

    private ViewGroup mContentContainer;
    private TextView mEmptyView;
    private boolean mContentShown;
    private boolean mContentEmpty;
    private boolean mErrorShown;
    private boolean mCanSwipeToRefresh = true;

    private AppBarLayout mHeader;
    private ToggleableAppBarLayoutBehavior mHeaderBehavior;
    private SmoothProgressBar mProgress;
    private SwipeRefreshLayout mSwipeLayout;
    private DrawerLayout mDrawerLayout;
    private CoordinatorLayout mCoordinatorLayout;
    private Toolbar mToolbar;
    private NavigationView mRightDrawer;
    private View mLeftDrawerHeader;
    private View mRightDrawerHeader;
    private SupportMenuInflater mMenuInflater;

    private boolean mAppBarLocked = false;
    private boolean mAppBarScrollable = true;

    private ActivityCompat.OnRequestPermissionsResultCallback mPendingPermissionCb;

    private final List<MaterialShapeDrawable> mHeaderDrawables = new ArrayList<>();
    private final List<MaterialShapeDrawable> mStatusBarDrawables = new ArrayList<>();
    private final int[] mProgressColors = new int[2];
    private Animator mHeaderTransition;
    private final Handler mHandler = new Handler();

    private final ActivityResultLauncher<Intent> mIssueReportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Snackbar.make(mCoordinatorLayout, R.string.issue_reported, Snackbar.LENGTH_LONG)
                            .setAction(R.string.view, v -> {
                                Issue createdIssue = result.getData().getParcelableExtra("issue");
                                Intent intent = IssueActivity.makeIntent(this, createdIssue);
                                startActivity(intent);
                            })
                            .show();
                }
            }
    );

    private RxLoader mRxLoader;
    private final CompositeDisposable mDisposeOnStop = new CompositeDisposable();

    private final Runnable mUpdateTaskDescriptionRunnable = () -> {
        String label = IntentUtils.isNewTaskIntent(getIntent()) ? getActionBarTitle() : null;
        setTaskDescription(new ActivityManager.TaskDescription(label, null,
                mProgressColors[0]));
    };

    // Issue #1684: apply the user-selected global font scale to every activity
    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = getConfiguredFontScale(newBase);
        super.attachBaseContext(newBase.createConfigurationContext(config));
        // 0.1.36：诊断追踪（建页时刻的开关与页面昼夜）。
        com.gh4a.utils.ThemeTrace.logState(this, "ATTACH inst="
                + com.gh4a.utils.ThemeTrace.instance(this));
    }

    // 0.1.36：诊断追踪（AppCompat 昼夜变化回调，临时，定论后删）。
    @Override
    public void onNightModeChanged(int mode) {
        super.onNightModeChanged(mode);
        com.gh4a.utils.ThemeTrace.logState(this, "NIGHT_CHANGED mode=" + mode
                + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
    }

    private static float getConfiguredFontScale(Context context) {
        float scale = 1.0f;
        try {
            String value = context
                    .getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                    .getString(SettingsFragment.KEY_FONT_SCALE, "1.0");
            scale = Float.parseFloat(value);
        } catch (Exception e) {
            // corrupted or unexpected preference value -> fall back to default
            scale = 1.0f;
        }
        if (Float.isNaN(scale) || scale <= 0.0f || scale > 3.0f) {
            scale = 1.0f;
        }
        return scale;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyAccentTheme();
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            onInitExtras(extras);
        }
        super.onCreate(savedInstanceState);

        mRxLoader = new RxLoader(this, LoaderManager.getInstance(this));

        super.setContentView(R.layout.base_activity);

        setupSwipeToRefresh();
        setupNavigationDrawer();
        setupHeaderDrawable();

        boolean showHomeAsUp =
                // if NO_HISTORY is set, we can't navigate up, since launching a new activity
                // will kill our activity. Hide the icon in that case.
                (getIntent().getFlags() & Intent.FLAG_ACTIVITY_NO_HISTORY) == 0
                // new task intents we started ourselves also should not show home as up
                && !IntentUtils.isNewTaskIntent(getIntent());

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getActionBarTitle());
        actionBar.setSubtitle(getActionBarSubtitle());
        actionBar.setDisplayHomeAsUpEnabled(showHomeAsUp);

        scheduleTaskDescriptionUpdate();
    }

    @Nullable
    protected String getActionBarTitle() {
        return null;
    }

    @Nullable
    protected String getActionBarSubtitle() {
        return null;
    }

    public void handleLoadFailure(Throwable e) {
        handleFailure("Loading data failed", e);
        setErrorViewVisibility(true, e);
    }

    public void handleActionFailure(String text, Throwable e) {
        handleFailure(text, e);
    }

    private void handleFailure(String text, Throwable e) {
        ApiRequestException are = e instanceof ApiRequestException ? (ApiRequestException) e : null;
        boolean isAuthError = are != null && are.getStatus() == HttpURLConnection.HTTP_UNAUTHORIZED;
        if (isAuthError) {
            Snackbar.make(mCoordinatorLayout, R.string.load_auth_failure_notice, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.login, v -> goToToplevelActivity())
                    .show();
            Log.d(Gh4Application.LOG_TAG, text, e);
            return;
        }

        boolean exceptionMustBeHandled = e instanceof ApiRequestException || e instanceof JsonDataException;
        // 0.1.20 F-22: CompositeException 先拆叶子——只包良性网络错误时不走
        // 崩溃路径；仅叶子含编程错误（非 Api/Json 的 RuntimeException）时崩溃。
        // 拆法照 Gh4Application.isAllBenignDisposeErrors 的思路（递归拆复合、
        // 解包 UndeliverableException）。
        if (e instanceof io.reactivex.exceptions.CompositeException
                || e instanceof io.reactivex.exceptions.UndeliverableException) {
            exceptionMustBeHandled = !hasProgrammingErrorLeaf(e);
        }
        if (!exceptionMustBeHandled && e instanceof RuntimeException) {
            // If this happens, it means Rx catched a programming error of us. Crash the app
            // in that case, as that's what would have happened without Rx as well.
            // In doing so, don't just throw the exception (but instead delegate it right to the
            // uncaught exception handler) to make sure RX doesn't add its composite exception
            // which obscures the actual stack trace.
            Thread currentThread = Thread.currentThread();
            Thread.UncaughtExceptionHandler handler = currentThread.getUncaughtExceptionHandler();
            handler.uncaughtException(currentThread, e);
        }
        Log.e(Gh4Application.LOG_TAG, text, e);
    }

    /** 0.1.20 F-22: 递归拆复合/不可投递异常，任一叶子是编程错误即返回 true。 */
    private static boolean hasProgrammingErrorLeaf(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof io.reactivex.exceptions.UndeliverableException) {
            return hasProgrammingErrorLeaf(t.getCause());
        }
        if (t instanceof io.reactivex.exceptions.CompositeException) {
            for (Throwable leaf : ((io.reactivex.exceptions.CompositeException) t).getExceptions()) {
                if (hasProgrammingErrorLeaf(leaf)) {
                    return true;
                }
            }
            return false;
        }
        return t instanceof RuntimeException
                && !(t instanceof ApiRequestException)
                && !(t instanceof JsonDataException);
    }

    protected void registerTemporarySubscription(Disposable disposable) {
        mDisposeOnStop.add(disposable);
    }

    protected int getLeftNavigationDrawerMenuResource() {
        return 0;
    }

    @IdRes
    protected int getInitialLeftDrawerSelection(Menu menu) {
        return 0;
    }

    protected int[] getRightNavigationDrawerMenuResources() {
        return null;
    }

    @IdRes
    protected int getInitialRightDrawerSelection() {
        return 0;
    }

    protected boolean closeDrawers() {
        boolean result = false;
        if (mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerLayout.closeDrawer(Gravity.LEFT);
            result = true;
        }
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
            result = true;
        }
        return result;
    }

    protected void toggleRightSideDrawer() {
        if (mDrawerLayout.isDrawerOpen(Gravity.RIGHT)) {
            mDrawerLayout.closeDrawer(Gravity.RIGHT);
        } else {
            mDrawerLayout.openDrawer(Gravity.RIGHT);
        }
    }

    /**
     * 0.5.0 抽屉头像不再占状态栏：旧做法只给 header 设 WindowInsets 监听，
     * 但 NavigationView（ScrimInsetsFrameLayout）会拦截/消费系统栏 Insets，
     * header 子项实际拿不到 top 值，于是头像区（含绿色背景）仍从屏顶开始、
     * 压进状态栏。修法：用系统状态栏高度（android dimen，非机型硬编码）
     * 给 header 设 topMargin，使 header 本体（含背景）从状态栏下方开始；
     * Insets 监听保留作补充，取两者较大值，且不再叠加 padding（避免双倍下移）。
     */
    protected void configureLeftDrawerHeader(View header) {
        applyDrawerHeaderTopMargin(header, getSystemStatusBarHeight());
        ViewCompat.setOnApplyWindowInsetsListener(header, (view, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            applyDrawerHeaderTopMargin(view, statusBarHeight);
            return insets;
        });
        ViewCompat.requestApplyInsets(header);
    }

    private int getSystemStatusBarHeight() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
    }

    private void applyDrawerHeaderTopMargin(View header, int statusBarHeight) {
        if (header == null || statusBarHeight <= 0) {
            return;
        }
        android.view.ViewGroup.LayoutParams lp = header.getLayoutParams();
        if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams mlp =
                    (android.view.ViewGroup.MarginLayoutParams) lp;
            if (mlp.topMargin < statusBarHeight) {
                mlp.topMargin = statusBarHeight;
                header.setLayoutParams(mlp);
            }
        }
        // 背景画在 header 本体内：margin 已把本体推到状态栏下方，padding 不再叠加
        header.setPadding(header.getPaddingLeft(), 0,
                header.getPaddingRight(), header.getPaddingBottom());
    }

    protected boolean canSwipeToRefresh() {
        return mCanSwipeToRefresh;
    }

    public void setCanSwipeToRefresh(boolean canSwipeToRefresh) {
        mCanSwipeToRefresh = canSwipeToRefresh;
        updateSwipeToRefreshState();
    }

    protected void setChildScrollDelegate(SwipeRefreshLayout.ChildScrollDelegate delegate) {
        mSwipeLayout.setChildScrollDelegate(delegate);
    }

    protected void setContentShown(boolean shown) {
        mContentShown = shown;
        updateSwipeToRefreshState();
        updateViewVisibility(true);
    }

    protected void setContentEmpty(boolean isEmpty) {
        mContentEmpty = isEmpty;
        updateViewVisibility(false);
    }

    protected Intent navigateUp() {
        return null;
    }

    public CoordinatorLayout getRootLayout() {
        ensureContent();
        return mCoordinatorLayout;
    }

    protected void onInitExtras(Bundle extras) {

    }

    public int getCurrentHeaderColor() {
        return mProgressColors[0];
    }

    protected void setHeaderColor(int color, int statusBarColor) {
        cancelHeaderTransition();

        for (MaterialShapeDrawable d : mHeaderDrawables) {
            d.setTint(color);
        }
        for (MaterialShapeDrawable d : mStatusBarDrawables) {
            d.setTint(statusBarColor);
        }
        mProgressColors[0] = color;
        mProgressColors[1] = statusBarColor;
        mProgress.invalidate();
        scheduleTaskDescriptionUpdate();
    }

    protected void transitionHeaderToColor(int colorAttrId, int statusBarColorAttrId) {
        final AnimatorSet animation = new AnimatorSet();
        List<Animator> animators = new ArrayList<>();
        int color = UiUtils.resolveColor(this, colorAttrId);
        int statusBarColor = UiUtils.resolveColor(this, statusBarColorAttrId);

        for (MaterialShapeDrawable d : mHeaderDrawables) {
            animators.add(createTintTransition(d, color));
        }
        for (MaterialShapeDrawable d : mStatusBarDrawables) {
            animators.add(createTintTransition(d, statusBarColor));
        }

        final ValueAnimator progressAnimator1 = ValueAnimator.ofInt(mProgressColors[0], color);
        progressAnimator1.setEvaluator(new ArgbEvaluator());
        animators.add(progressAnimator1);
        final ValueAnimator progressAnimator2 = ValueAnimator.ofInt(mProgressColors[1], statusBarColor);
        progressAnimator2.setEvaluator(new ArgbEvaluator());
        animators.add(progressAnimator2);

        progressAnimator1.addUpdateListener(anim -> {
            mProgressColors[0] = (int) progressAnimator1.getAnimatedValue();
            mProgressColors[1] = (int) progressAnimator2.getAnimatedValue();
            mProgress.invalidate();
        });

        cancelHeaderTransition();

        animation.playTogether(animators);
        animation.setDuration(200);
        animation.start();
        mHeaderTransition = animation;
        scheduleTaskDescriptionUpdate();
    }

    private void cancelHeaderTransition() {
        if (mHeaderTransition != null && mHeaderTransition.isRunning()) {
            mHeaderTransition.cancel();
        }
        mHeaderTransition = null;
    }

    private void scheduleTaskDescriptionUpdate() {
        mHandler.removeCallbacks(mUpdateTaskDescriptionRunnable);
        mHandler.postDelayed(mUpdateTaskDescriptionRunnable, 500);
    }

    protected void updateRightNavigationDrawer() {
        mRightDrawer.getMenu().clear();
        int[] drawerMenuResIds = getRightNavigationDrawerMenuResources();
        if (drawerMenuResIds != null) {
            for (int id : drawerMenuResIds) {
                mRightDrawer.inflateMenu(id);
            }
            mRightDrawer.setNavigationItemSelectedListener(this);

            int initialRightDrawerSelection = getInitialRightDrawerSelection();
            if (initialRightDrawerSelection != 0) {
                mRightDrawer.setCheckedItem(initialRightDrawerSelection);
            }

            onPrepareRightNavigationDrawerMenu(mRightDrawer.getMenu());

            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT);
        } else {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT);
        }
    }

    protected void onPrepareRightNavigationDrawerMenu(Menu menu) {

    }

    protected void goToToplevelActivity() {
        Intent intent = getToplevelActivityIntent();
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    protected Intent getToplevelActivityIntent() {
        Gh4Application app = Gh4Application.get();
        if (app.isAuthorized()) {
            return new Intent(this, HomeActivity.class);
        } else {
            return new Intent(this, Github4AndroidActivity.class);
        }
    }

    protected SharedPreferences getPrefs() {
        return getSharedPreferences(SettingsFragment.PREF_NAME, MODE_PRIVATE);
    }

    private void applyAccentTheme() {
        // Called before super.onCreate(), so don't use getPrefs() (Activity not yet created).
        // Use application context which is safe at this point.
        String accent = getApplicationContext()
                .getSharedPreferences(SettingsFragment.PREF_NAME, MODE_PRIVATE)
                .getString(SettingsFragment.KEY_ACCENT_COLOR, "green");
        switch (accent) {
            case "blue": setTheme(R.style.AppTheme_Blue); break;
            case "red": setTheme(R.style.AppTheme_Red); break;
            case "orange": setTheme(R.style.AppTheme_Orange); break;
            case "purple": setTheme(R.style.AppTheme_Purple); break;
            case "teal": setTheme(R.style.AppTheme_Teal); break;
            default: break; // green is the default AppTheme
        }
    }

    public void addAppBarOffsetListener(AppBarLayout.OnOffsetChangedListener l) {
        mHeader.addOnOffsetChangedListener(l);
    }

    public int getAppBarTotalScrollRange() {
        return mHeader.getTotalScrollRange();
    }

    public void removeAppBarOffsetListener(AppBarLayout.OnOffsetChangedListener l) {
        mHeader.removeOnOffsetChangedListener(l);
    }

    public void collapseAppBar() {
        mHeader.setExpanded(false);
    }

    // 0.1.11：与 collapseAppBar 对称——切到顶栏固定的页（如动态页）时，
    // 把之前被其他页滚动收起的 AppBar 重新展开，否则标题仍藏在折叠状态里。
    public void expandAppBar() {
        mHeader.setExpanded(true);
    }

    public void setAppBarLocked(boolean locked) {
        mAppBarLocked = locked;
        updateAppBarEnabledState();
    }

    public void removeHeaderView(View view) {
        if (view != null) {
            mHeader.removeView(view);
        }
    }

    protected void addHeaderView(View view, boolean scrollable) {
        mHeader.addView(view, 1, new AppBarLayout.LayoutParams(
                AppBarLayout.LayoutParams.MATCH_PARENT,
                AppBarLayout.LayoutParams.WRAP_CONTENT));
        setAppBarChildScrollable(view, scrollable);
    }

    protected void setToolbarScrollable(boolean scrollable) {
        setAppBarChildScrollable(mToolbar, scrollable);
        mAppBarScrollable = scrollable;
        updateAppBarEnabledState();
    }

    private void updateAppBarEnabledState() {
        mHeaderBehavior.setEnabled(mAppBarScrollable && !mAppBarLocked);
    }

    private void setAppBarChildScrollable(View view, boolean scrollable) {
        AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams) view.getLayoutParams();
        if (scrollable) {
            lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    | AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
        } else {
            lp.setScrollFlags(0);
        }
        view.setLayoutParams(lp);
    }

    @Override
    protected void onDestroy() {
        // 0.1.36：诊断追踪（旧实例销毁，区分重建与清栈）。
        com.gh4a.utils.ThemeTrace.logState(this, "DESTROY finishing=" + isFinishing()
                + " inst=" + com.gh4a.utils.ThemeTrace.instance(this));
        // 0.1.20 F-23(d): 500ms 任务描述 runnable 在销毁时移除，避免销毁后回调。
        mHandler.removeCallbacks(mUpdateTaskDescriptionRunnable);
        super.onDestroy();
    }

    /**
     * 0.1.24 finish 取证（仅记录，不改行为）：0.1.23 取证证明 DecorView
     * 崩溃那次销毁是 finishing=true，但我页 Dark mode 行链路全程无
     * finish()，故崩溃的销毁并非深色模式切换本身的销毁。下次再崩时，
     * 崩溃头里的 lastFinish 能判定是 App 内哪处调了 finish()，还是
     * 系统清栈（CLEAR_TOP/CLEAR_TASK 不走本方法，记不到即系统侧）。
     */
    @Override
    public void finish() {
        com.gh4a.utils.CrashBreadcrumb.noteFinish(getClass().getSimpleName());
        super.finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensureContent();
        if (mContentContainer.getChildCount() == 0) {
            throw new IllegalStateException("Content view must be initialized before");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mDisposeOnStop.clear();
    }

    @NonNull
    @Override
    public MenuInflater getMenuInflater() {
        if (mMenuInflater == null) {
            mMenuInflater = new SupportMenuInflater(
                    new ContextThemeWrapper(this, R.style.HeaderTheme));
        }
        return mMenuInflater;
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!IntentUtils.isNewTaskIntent(getIntent()) && displayDetachAction()) {
            menu.add(Menu.NONE, R.id.detach, Menu.NONE, R.string.detach);
        }

        return super.onCreateOptionsMenu(menu);
    }

    public boolean displayDetachAction() {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent intent = navigateUp();
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            } else {
                finish();
            }
            return true;
        }

        if (item.getItemId() == R.id.detach) {
            IntentUtils.startNewTask(this, getIntent());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void setContentView(int layoutResId) {
        ensureContent();
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View contentView = layoutInflater.inflate(layoutResId, mContentContainer, false);
        setContentView(contentView);
    }

    @Override
    public void setContentView(View view) {
        ensureContent();
        if (view == null) {
            throw new IllegalArgumentException("Content view can't be null");
        }

        mContentContainer.removeAllViews();
        mContentContainer.addView(view);
    }

    @Override
    public boolean onSearchRequested() {
        startActivity(SearchActivity.makeIntent(this));
        return true;
    }

    @Override
    public void onRefresh() {
        supportInvalidateOptionsMenu();
        mSwipeLayout.setRefreshing(false);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        mDrawerLayout.closeDrawers();
        return false;
    }

    /**
     * 0.1.0：仅 Home 调用——左侧抽屉下架后把它锁定关闭（物理结构不动，
     * 其他详情页共用的 DrawerLayout 不受影响；右抽屉工具栏不受此影响）。
     */
    public void setLeftDrawerLockedClosed() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
        }
    }

    public void setRightDrawerLockedClosed(boolean locked) {
        if (!mRightDrawer.getMenu().hasVisibleItems()) {
            // right drawer isn't populated, so keep locked in any case
            locked = true;
        }
        mDrawerLayout.setDrawerLockMode(
                locked ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED : DrawerLayout.LOCK_MODE_UNLOCKED,
                Gravity.RIGHT);
    }

    public void requestPermission(final String permission,
            ActivityCompat.OnRequestPermissionsResultCallback cb,
            int rationaleTextResId) {
        if (mPendingPermissionCb != null) {
            throw new IllegalStateException();
        }
        int grantResult = ActivityCompat.checkSelfPermission(this, permission);
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            cb.onRequestPermissionsResult(0, new String[] { permission }, new int[] { grantResult });
        } else {
            mPendingPermissionCb = cb;
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Snackbar.make(getRootLayout(), rationaleTextResId, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.ok, v -> ActivityCompat.requestPermissions(
                                BaseActivity.this, new String[] { permission }, 0))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, new String[] { permission }, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mPendingPermissionCb != null) {
            mPendingPermissionCb.onRequestPermissionsResult(0, permissions, grantResults);
            mPendingPermissionCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (closeDrawers()) {
            return;
        }
        boolean wasStartedAsNewTask = (getIntent().getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0;
        if (wasStartedAsNewTask) {
            finishAndRemoveTask();
        } else {
            super.onBackPressed();
        }
    }

    public <T> SingleTransformer<T, T> makeLoaderSingle(int id, boolean force) {
        return upstream -> upstream
                .compose(RxUtils::doInBackground)
                .compose(mRxLoader.makeSingleTransformer(id, force));
    }

    protected void setErrorViewVisibility(boolean visible, Throwable e) {
        View content = findViewById(R.id.content);
        View error = findViewById(R.id.error);

        content.setVisibility(visible ? View.GONE : View.VISIBLE);
        mErrorShown = visible;
        updateSwipeToRefreshState();

        if (error == null) {
            if (!visible) {
                // It's not inflated yet and we don't want it
                // to be visible, so there's nothing to do
                return;
            }
            ViewStub errorStub = findViewById(R.id.error_stub);
            error = errorStub.inflate();

            error.findViewById(R.id.retry_button).setOnClickListener(v -> {
                setErrorViewVisibility(false, null);
                onRefresh();
            });
        }

        if (visible) {
            View retryButton = error.findViewById(R.id.retry_button);
            TextView messageView = error.findViewById(R.id.error_message);

            ApiRequestException re = e instanceof ApiRequestException ? (ApiRequestException) e : null;
            ClientErrorResponse.BlockReason blockReason = re != null && re.getResponse() != null
                    ? re.getResponse().blockReason() : null;

            if (blockReason != null) {
                messageView.setText(
                        getString(R.string.load_failure_explanation_dmca, blockReason.htmlUrl()));
                retryButton.setVisibility(View.GONE);
            } else if (re != null && re.getMessage() != null) {
                messageView.setText(
                        getString(R.string.load_failure_explanation_with_reason, re.getMessage()));
                retryButton.setVisibility(View.VISIBLE);
            } else if (e instanceof JsonDataException) {
                messageView.setText(getString(R.string.load_failure_explanation_parsing));
                retryButton.setVisibility(View.GONE);
                View reportIssueButton = error.findViewById(R.id.report_button);
                reportIssueButton.setVisibility(View.VISIBLE);
                reportIssueButton.setOnClickListener(v -> launchIssueCreationForError(e));
            } else {
                messageView.setText(R.string.load_failure_explanation);
                retryButton.setVisibility(View.VISIBLE);
            }
            error.setVisibility(View.VISIBLE);
        } else {
            error.setVisibility(View.GONE);
        }
    }

    private void launchIssueCreationForError(Throwable e) {
        String issueTitle = "";
        String issueBody = new StringBuilder()
                .append("<!-- Please describe here what you were doing when the error occurred -->\n\n")
                .append("### Error stack trace\n")
                .append("```\n")
                .append(Log.getStackTraceString(e))
                .append("\n```")
                .toString();
        Intent newIssueIntent = IssueEditActivity.makeCreateIntent(
                this,
                getString(R.string.my_username),
                getString(R.string.my_repo),
                issueTitle, issueBody);
        mIssueReportLauncher.launch(newIssueIntent);
    }

    protected void onDrawerOpened(boolean right) {
    }

    protected void onDrawerClosed(boolean right) {
    }

    private void updateSwipeToRefreshState() {
        ensureContent();
        mSwipeLayout.setEnabled(mContentShown && !mErrorShown && canSwipeToRefresh());
    }

    private void setupHeaderDrawable() {
        ensureContent();

        int primaryColor = UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimary);
        assignBackground(mLeftDrawerHeader, primaryColor);
        assignBackground(mRightDrawerHeader, primaryColor);
        assignBackground(mHeader, primaryColor);

        int primaryDarkColor = UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimaryDark);
        MaterialShapeDrawable d = MaterialShapeDrawable.createWithElevationOverlay(this);
        d.setTint(primaryDarkColor);
        mDrawerLayout.setStatusBarBackground(d);
        mStatusBarDrawables.add(d);
    }

    private ObjectAnimator createTintTransition(MaterialShapeDrawable drawable, int color) {
        final ObjectAnimator animation = ObjectAnimator.ofInt(drawable,
                "tint", drawable.getTintList().getDefaultColor(), color);
        animation.setEvaluator(new ArgbEvaluator());
        return animation;
    }

    private void assignBackground(View view, int color) {
        if (view == null) {
            return;
        }
        MaterialShapeDrawable background = MaterialShapeDrawable.createWithElevationOverlay(this);
        background.setTint(color);
        view.setBackground(background);
        mHeaderDrawables.add(background);
    }

    private void setupSwipeToRefresh() {
        mSwipeLayout = findViewById(R.id.swipe_container);
        if (canSwipeToRefresh()) {
            mSwipeLayout.setOnRefreshListener(this);
            mSwipeLayout.setColorSchemeColors(
                    UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimary), 0,
                    UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimaryDark), 0
            );
        }

        CoordinatorLayout.LayoutParams lp =
                (CoordinatorLayout.LayoutParams) mSwipeLayout.getLayoutParams();
        lp.setBehavior(onCreateSwipeLayoutBehavior());

        updateSwipeToRefreshState();
    }

    protected AppBarLayout.ScrollingViewBehavior onCreateSwipeLayoutBehavior() {
        return new AppBarLayout.ScrollingViewBehavior();
    }

    private void setupNavigationDrawer() {
        NavigationView leftDrawer = findViewById(R.id.left_drawer);
        applyHighlightColor(leftDrawer);
        mRightDrawer = findViewById(R.id.right_drawer);
        applyHighlightColor(mRightDrawer);

        mDrawerLayout = findViewById(R.id.drawer_container);
        mDrawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
            }
            @Override
            public void onDrawerOpened(View drawerView) {
                BaseActivity.this.onDrawerOpened(drawerView == mRightDrawer);
            }
            @Override
            public void onDrawerClosed(View drawerView) {
                BaseActivity.this.onDrawerClosed(drawerView == mRightDrawer);
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        Toolbar toolBar = findViewById(R.id.toolbar);
        setSupportActionBar(toolBar);

        int drawerMenuResId = getLeftNavigationDrawerMenuResource();
        if (drawerMenuResId != 0) {
            leftDrawer.inflateMenu(drawerMenuResId);
            leftDrawer.setNavigationItemSelectedListener(this);

            int initialLeftDrawerSelection = getInitialLeftDrawerSelection(leftDrawer.getMenu());
            if (initialLeftDrawerSelection != 0) {
                leftDrawer.setCheckedItem(initialLeftDrawerSelection);
            }

            ActionBar supportActionBar = getSupportActionBar();
            if (supportActionBar != null) {
                supportActionBar.setDisplayHomeAsUpEnabled(true);
            }

            toolBar.setNavigationIcon(new DrawerArrowDrawable(toolBar.getContext()));
            toolBar.setNavigationOnClickListener(v -> {
                int drawerLockMode = mDrawerLayout.getDrawerLockMode(GravityCompat.START);
                if (drawerLockMode != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
                    mDrawerLayout.openDrawer(GravityCompat.START);
                }
            });

            mLeftDrawerHeader = leftDrawer.inflateHeaderView(R.layout.drawer_header_left);
            configureLeftDrawerHeader(mLeftDrawerHeader);
        } else {
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT);
        }

        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow_left, Gravity.LEFT);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow_right, Gravity.RIGHT);
        mDrawerLayout.setScrimColor(ContextCompat.getColor(this, R.color.drawer_scrim));

        mRightDrawerHeader = mRightDrawer.inflateHeaderView(R.layout.drawer_header_right);

        updateRightNavigationDrawer();
        updateDrawerGestureExclusion();
    }

    /**
     * 左边缘手势冲突修复：全面屏手势导航下，从左边缘滑动既是系统返回手势，
     * 也是 DrawerLayout 的开抽屉手势，两者抢手势时常误触返回。
     * 把左边缘一条 20dp 宽的区域登记为系统手势排除区后，该区域的滑动优先
     * 交给抽屉；右边缘的系统返回手势不受影响（仍可从右侧滑回）。
     * 抽屉被锁定时（如无左抽屉的页面）不排除，系统手势恢复正常。
     * 仅 API 29+ 有效，低版本直接跳过。
     */
    private void updateDrawerGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mDrawerLayout == null) {
            return;
        }
        mDrawerLayout.post(() -> {
            if (mDrawerLayout.getDrawerLockMode(GravityCompat.START)
                    == DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
                mDrawerLayout.setSystemGestureExclusionRects(Collections.emptyList());
                return;
            }
            int width = (int) (20 * getResources().getDisplayMetrics().density + 0.5f);
            int height = mDrawerLayout.getHeight();
            if (height <= 0) {
                return;
            }
            mDrawerLayout.setSystemGestureExclusionRects(
                    Collections.singletonList(new Rect(0, 0, width, height)));
        });
    }

    private void applyHighlightColor(NavigationView view) {
        ColorStateList iconTint =
                createDefaultNavigationColorStateList(android.R.attr.textColorSecondary);
        if (iconTint != null) {
            view.setItemIconTintList(iconTint);
        }
        ColorStateList textColor =
                createDefaultNavigationColorStateList(android.R.attr.textColorPrimary);
        if (textColor != null) {
            view.setItemTextColor(textColor);
        }
    }

    // similar to what NavigationView does by default,
    // but uses accent color instead of primary color
    private ColorStateList createDefaultNavigationColorStateList(int baseColorThemeAttr) {
        TypedValue value = new TypedValue();
        if (!getTheme().resolveAttribute(baseColorThemeAttr, value, true)) {
            return null;
        }
        ColorStateList baseColor = ContextCompat.getColorStateList(this, value.resourceId);
        if (!getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, value, true)) {
            return null;
        }
        int colorAccent = value.data;
        int defaultColor = baseColor.getDefaultColor();
        final int[] disabledStateSet = { -android.R.attr.state_enabled };
        final int[] checkedStateSet = { android.R.attr.state_checked };
        final int[][] states = { disabledStateSet, checkedStateSet, { 0 } };
        final int[] colors = {
            baseColor.getColorForState(disabledStateSet, defaultColor),
            colorAccent,
            defaultColor
        };

        return new ColorStateList(states, colors);
    }

    private void updateViewVisibility(boolean animate) {
        ensureContent();
        UiUtils.updateViewVisibility(mProgress, animate, !mContentShown);
        UiUtils.updateViewVisibility(mEmptyView, animate, mContentEmpty && mContentShown);
        UiUtils.updateViewVisibility(mContentContainer, animate, !mContentEmpty && mContentShown);
    }

    private void ensureContent() {
        if (mContentContainer != null && mProgress != null) {
            return;
        }
        mProgress = findViewById(R.id.progress);
        mProgressColors[0] = UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimary);
        mProgressColors[1] = UiUtils.resolveColor(this, androidx.appcompat.R.attr.colorPrimaryDark);
        mProgress.setSmoothProgressDrawableColors(mProgressColors);

        mCoordinatorLayout = findViewById(R.id.coordinator_layout);
        mContentContainer = findViewById(R.id.content_container);
        mEmptyView = findViewById(android.R.id.empty);

        mHeader = findViewById(R.id.header);
        mToolbar = findViewById(R.id.toolbar);

        mHeaderBehavior = new ToggleableAppBarLayoutBehavior();
        CoordinatorLayout.LayoutParams lp =
                (CoordinatorLayout.LayoutParams) mHeader.getLayoutParams();
        lp.setBehavior(mHeaderBehavior);

        mSwipeLayout.setAppBarLayout(mHeader);

        mContentShown = true;
    }

    @Override
    public void onProvideAssistContent(AssistContent outContent) {
        Uri activityUri = getActivityUri();
        if (activityUri != null) {
            outContent.setWebUri(activityUri);
        }
    }

    protected @Nullable Uri getActivityUri() {
        return null;
    }
}
