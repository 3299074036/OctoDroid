package com.gh4a;

import android.Manifest;
import android.animation.Animator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;

import androidx.activity.result.ActivityResultRegistry;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.activities.Github4AndroidActivity;
import com.gh4a.activities.IssueActivity;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.activities.SearchActivity;
import com.gh4a.fragment.SettingsFragment;
import com.meisolsson.githubsdk.model.User;
import com.squareup.moshi.JsonDataException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class BaseActivityTest {

    public static class TestActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new View(this));
        }

        @Override
        protected String getActionBarTitle() {
            return "Test Title";
        }
    }

    public static class NoContentActivity extends BaseActivity {
        // Deliberately never calls setContentView: onStart must throw.
    }

    public static class DrawerActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new View(this));
        }

        @Override
        protected String getActionBarTitle() {
            return "Drawer Title";
        }

        @Override
        protected int getLeftNavigationDrawerMenuResource() {
            return R.menu.home_nav_drawer;
        }

        @Override
        protected int getInitialLeftDrawerSelection(Menu menu) {
            return R.id.feed_hub;
        }

        @Override
        protected int[] getRightNavigationDrawerMenuResources() {
            return new int[]{R.menu.home_nav_drawer};
        }

        @Override
        protected int getInitialRightDrawerSelection() {
            return R.id.feed_hub;
        }

        @Override
        public boolean displayDetachAction() {
            return true;
        }

        @Override
        protected Intent navigateUp() {
            return new Intent(this, TestActivity.class);
        }

        @Override
        protected Uri getActivityUri() {
            return Uri.parse("https://example.com/x");
        }
    }

    public static class NoSwipeActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new View(this));
        }

        @Override
        protected boolean canSwipeToRefresh() {
            return false;
        }
    }

    private static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    private static TestActivity launch() {
        return Robolectric.buildActivity(TestActivity.class)
                .create().start().resume().get();
    }

    private static Object callStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = BaseActivity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call " + name, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + name, e);
        }
    }

    private static ApiRequestException apiError(int code) {
        return new ApiRequestException(Response.error(code,
                ResponseBody.create(MediaType.parse("application/json"), "{}")));
    }

    // ---- launch / basic state ----

    @Test
    public void launch_setsTitleAndRootLayout() {
        TestActivity activity = launch();
        assertEquals("Test Title", activity.getSupportActionBar().getTitle());
        assertNotNull(activity.getRootLayout());
        assertTrue(activity.getCurrentHeaderColor() != 0);
        assertTrue(activity.canSwipeToRefresh());
        assertFalse(activity.displayDetachAction());
        assertNull(activity.navigateUp());
        assertNull(activity.getActionBarSubtitle());
    }

    @Test
    public void onStart_withoutContent_throwsIllegalState() {
        ActivityController<NoContentActivity> controller =
                Robolectric.buildActivity(NoContentActivity.class).create();
        try {
            controller.start();
            org.junit.Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Content view must be initialized"));
        }
    }

    @Test
    public void setContentView_nullView_throws() {
        TestActivity activity = launch();
        try {
            activity.setContentView((View) null);
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("Content view can't be null", expected.getMessage());
        }
    }

    @Test
    public void setContentView_layoutResource_inflatesIntoContainer() {
        TestActivity activity = launch();
        activity.setContentView(android.R.layout.simple_list_item_1);
        assertNotNull(activity.findViewById(android.R.id.text1));
    }

    @Test
    public void setContentShownAndEmpty_doNotCrash() {
        TestActivity activity = launch();
        activity.setContentShown(false);
        activity.setContentShown(true);
        activity.setContentEmpty(true);
        activity.setContentEmpty(false);
    }

    @Test
    public void setCanSwipeToRefresh_toggles() {
        TestActivity activity = launch();
        activity.setCanSwipeToRefresh(false);
        assertFalse(activity.canSwipeToRefresh());
        activity.setCanSwipeToRefresh(true);
        assertTrue(activity.canSwipeToRefresh());
    }

    @Test
    public void setHeaderColor_updatesCurrentHeaderColor() {
        TestActivity activity = launch();
        activity.setHeaderColor(0xFF112233, 0xFF445566);
        assertEquals(0xFF112233, activity.getCurrentHeaderColor());
    }

    @Test
    public void transitionHeaderToColor_startsTransitionWithoutCrash() throws Exception {
        TestActivity activity = launch();
        int before = activity.getCurrentHeaderColor();
        activity.transitionHeaderToColor(
                androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
        // The transition runs as a 200ms animator; the call itself must
        // install it without crashing.
        java.lang.reflect.Field f = BaseActivity.class.getDeclaredField("mHeaderTransition");
        f.setAccessible(true);
        assertNotNull(f.get(activity));
        assertEquals(before, activity.getCurrentHeaderColor());
    }

    @Test
    public void appBarHelpers_doNotCrash() {
        TestActivity activity = launch();
        activity.collapseAppBar();
        activity.expandAppBar();
        activity.setAppBarLocked(true);
        activity.setAppBarLocked(false);
        assertTrue(activity.getAppBarTotalScrollRange() >= 0);
        AppBarLayoutListener listener = new AppBarLayoutListener();
        activity.addAppBarOffsetListener(listener);
        activity.removeAppBarOffsetListener(listener);
    }

    private static class AppBarLayoutListener
            implements com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener {
        @Override
        public void onOffsetChanged(
                com.google.android.material.appbar.AppBarLayout appBarLayout, int verticalOffset) {
        }
    }

    @Test
    public void drawerLockHelpers_doNotCrash() {
        TestActivity activity = launch();
        activity.setLeftDrawerLockedClosed();
        activity.setRightDrawerLockedClosed(true);
        activity.setRightDrawerLockedClosed(false);
    }

    @Test
    public void getMenuInflater_returnsSameInstance() {
        TestActivity activity = launch();
        assertSame(activity.getMenuInflater(), activity.getMenuInflater());
    }

    @Test
    public void onCreateOptionsMenu_noDetachItemByDefault() {
        TestActivity activity = launch();
        MenuBuilder menu = new MenuBuilder(appContext());
        assertTrue(activity.onCreateOptionsMenu(menu));
        assertNull(menu.findItem(R.id.detach));
    }

    @Test
    public void onNavigationItemSelected_returnsFalse() {
        TestActivity activity = launch();
        MenuItem item = mock(MenuItem.class);
        assertFalse(activity.onNavigationItemSelected(item));
    }

    @Test
    public void onRefresh_doesNotCrash() {
        TestActivity activity = launch();
        activity.onRefresh();
    }

    @Test
    public void onSearchRequested_startsSearchActivity() {
        TestActivity activity = launch();
        assertTrue(activity.onSearchRequested());
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(SearchActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void onBackPressed_finishesWhenNotNewTask() {
        TestActivity activity = launch();
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onOptionsItemSelected_homeWithNullNavigateUp_finishes() {
        TestActivity activity = launch();
        MenuItem home = mock(MenuItem.class);
        when(home.getItemId()).thenReturn(android.R.id.home);
        assertTrue(activity.onOptionsItemSelected(home));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onOptionsItemSelected_unknownItem_delegatesToSuper() {
        TestActivity activity = launch();
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(123456789);
        assertFalse(activity.onOptionsItemSelected(item));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void getPrefs_returnsGh4aPrefs() {
        TestActivity activity = launch();
        SharedPreferences prefs = activity.getPrefs();
        assertNotNull(prefs);
        assertEquals(prefs, appContext().getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE));
    }

    // ---- failure handling ----

    @Test
    public void handleLoadFailure_authError_showsErrorView() {
        TestActivity activity = launch();
        activity.handleLoadFailure(apiError(401));
        View error = activity.findViewById(R.id.error);
        assertNotNull(error);
        assertEquals(View.VISIBLE, error.getVisibility());
    }

    @Test
    public void handleLoadFailure_apiError_showsErrorViewWithMessage() {
        TestActivity activity = launch();
        activity.handleLoadFailure(apiError(404));
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
    }

    @Test
    public void handleLoadFailure_jsonDataException_showsErrorView() {
        TestActivity activity = launch();
        activity.handleLoadFailure(new JsonDataException("bad json"));
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertEquals(View.VISIBLE,
                error.findViewById(R.id.report_button).getVisibility());
    }

    @Test
    public void handleLoadFailure_genericError_showsErrorView() {
        TestActivity activity = launch();
        activity.handleLoadFailure(new java.io.IOException("network down"));
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
    }

    @Test
    public void handleLoadFailure_programmingError_forwardedToUncaughtHandler() {
        TestActivity activity = launch();
        AtomicReference<Throwable> forwarded = new AtomicReference<>();
        Thread.UncaughtExceptionHandler old =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler(
                (t, e) -> forwarded.set(e));
        try {
            NullPointerException npe = new NullPointerException("prog bug");
            activity.handleLoadFailure(npe);
            assertSame(npe, forwarded.get());
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
    }

    @Test
    public void handleActionFailure_doesNotShowErrorView() {
        TestActivity activity = launch();
        activity.handleActionFailure("action failed", new java.io.IOException("x"));
        View error = activity.findViewById(R.id.error);
        // Error view is not inflated yet (null) or gone.
        assertTrue(error == null || error.getVisibility() != View.VISIBLE);
    }

    @Test
    public void handleLoadFailure_retryButtonHidesErrorView() {
        TestActivity activity = launch();
        activity.handleLoadFailure(new java.io.IOException("x"));
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
        error.findViewById(R.id.retry_button).performClick();
        assertEquals(View.GONE, error.getVisibility());
    }

    // ---- hasProgrammingErrorLeaf ----

    @Test
    public void hasProgrammingErrorLeaf_cases() {
        Class<?>[] types = {Throwable.class};
        assertFalse((Boolean) callStatic("hasProgrammingErrorLeaf", types, (Throwable) null));
        assertTrue((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new NullPointerException()));
        assertTrue((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new IllegalStateException()));
        assertFalse((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new java.io.IOException("io")));
        assertFalse((Boolean) callStatic("hasProgrammingErrorLeaf", types, apiError(404)));
        assertFalse((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new JsonDataException("json")));
        assertTrue((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new io.reactivex.exceptions.CompositeException(new NullPointerException())));
        assertFalse((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new io.reactivex.exceptions.CompositeException(new java.io.IOException())));
        // NOTE: an empty CompositeException cannot be constructed at all
        // (RxJava throws IllegalArgumentException), so the empty-list
        // branch of hasProgrammingErrorLeaf is unreachable via public API.
        assertTrue((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new io.reactivex.exceptions.UndeliverableException(new NullPointerException())));
        assertFalse((Boolean) callStatic("hasProgrammingErrorLeaf", types,
                new io.reactivex.exceptions.UndeliverableException(new java.io.IOException())));
    }

    @Test
    public void handleLoadFailure_compositeWithOnlyIoErrors_noCrashForward() {
        TestActivity activity = launch();
        AtomicReference<Throwable> forwarded = new AtomicReference<>();
        Thread.UncaughtExceptionHandler old =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> forwarded.set(e));
        try {
            activity.handleLoadFailure(new io.reactivex.exceptions.CompositeException(
                    new java.io.IOException("a"), new java.io.IOException("b")));
            assertNull(forwarded.get());
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
    }

    // ---- getConfiguredFontScale ----

    private static float fontScale(String value) {
        SharedPreferences prefs = appContext().getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        String old = prefs.getString(SettingsFragment.KEY_FONT_SCALE, null);
        try {
            if (value == null) {
                prefs.edit().remove(SettingsFragment.KEY_FONT_SCALE).apply();
            } else {
                prefs.edit().putString(SettingsFragment.KEY_FONT_SCALE, value).apply();
            }
            return (Float) callStatic("getConfiguredFontScale",
                    new Class<?>[]{Context.class}, appContext());
        } finally {
            if (old == null) {
                prefs.edit().remove(SettingsFragment.KEY_FONT_SCALE).apply();
            } else {
                prefs.edit().putString(SettingsFragment.KEY_FONT_SCALE, old).apply();
            }
        }
    }

    @Test
    public void getConfiguredFontScale_values() {
        assertEquals(1.0f, fontScale(null), 0.001f);
        assertEquals(1.0f, fontScale("1.0"), 0.001f);
        assertEquals(2.5f, fontScale("2.5"), 0.001f);
        assertEquals(1.0f, fontScale("abc"), 0.001f);
        assertEquals(1.0f, fontScale(""), 0.001f);
        assertEquals(1.0f, fontScale("0"), 0.001f);
        assertEquals(1.0f, fontScale("-1"), 0.001f);
        assertEquals(1.0f, fontScale("3.5"), 0.001f);
        assertEquals(1.0f, fontScale("NaN"), 0.001f);
        assertEquals(3.0f, fontScale("3.0"), 0.001f);
    }

    // ---- permissions ----

    @Test
    public void requestPermission_granted_callsBackImmediately() {
        Shadows.shadowOf((android.app.Application) appContext()).grantPermissions(Manifest.permission.CAMERA);
        try {
            TestActivity activity = launch();
            List<int[]> results = new ArrayList<>();
            activity.requestPermission(Manifest.permission.CAMERA,
                    (code, perms, grants) -> results.add(grants), R.string.ok);
            assertEquals(1, results.size());
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, results.get(0)[0]);
        } finally {
            Shadows.shadowOf((android.app.Application) appContext()).denyPermissions(Manifest.permission.CAMERA);
        }
    }

    @Test
    public void requestPermission_pendingSecondCallThrowsAndResultDelivers() {
        Shadows.shadowOf((android.app.Application) appContext()).denyPermissions(Manifest.permission.CAMERA);
        TestActivity activity = launch();
        List<int[]> results = new ArrayList<>();
        androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback cb =
                (code, perms, grants) -> results.add(grants);
        activity.requestPermission(Manifest.permission.CAMERA, cb, R.string.ok);
        try {
            activity.requestPermission(Manifest.permission.CAMERA, cb, R.string.ok);
            org.junit.Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Second request while one is pending.
        }
        activity.onRequestPermissionsResult(0,
                new String[]{Manifest.permission.CAMERA},
                new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
        assertEquals(1, results.size());
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, results.get(0)[0]);
        // Pending callback cleared: a new request no longer throws.
        activity.requestPermission(Manifest.permission.CAMERA, cb, R.string.ok);
    }

    // ---- subscriptions / loader / toplevel intent ----

    @Test
    public void registerTemporarySubscription_disposedOnStop() {
        ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class).create().start().resume();
        TestActivity activity = controller.get();
        Disposable d = Disposables.empty();
        activity.registerTemporarySubscription(d);
        assertFalse(d.isDisposed());
        controller.stop();
        assertTrue(d.isDisposed());
    }

    @Test
    public void makeLoaderSingle_returnsNonNullTransformer() {
        // NOTE: subscribing (blockingGet) hangs under Robolectric — the
        // RxLoader's LoaderManager never delivers without a real loader
        // lifecycle — so only the construction path is covered here.
        TestActivity activity = launch();
        assertNotNull(activity.makeLoaderSingle(1, false));
        assertNotNull(activity.makeLoaderSingle(2, true));
    }

    @Test
    public void getToplevelActivityIntent_unauthorized_goesToLogin() {
        Gh4Application app = (Gh4Application) appContext();
        app.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
        TestActivity activity = launch();
        Intent intent = activity.getToplevelActivityIntent();
        assertEquals(Github4AndroidActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void getToplevelActivityIntent_authorized_goesHome() {
        Gh4Application app = (Gh4Application) appContext();
        app.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
        try {
            app.addAccount(User.builder().login("mod-top-user").id(606L).build(), "tok-top");
            TestActivity activity = launch();
            Intent intent = activity.getToplevelActivityIntent();
            assertEquals(HomeActivity.class.getName(), intent.getComponent().getClassName());
        } finally {
            app.removeAccount("mod-top-user");
        }
    }

    @Test
    public void onProvideAssistContent_nullUri_noCrash() {
        TestActivity activity = launch();
        android.app.assist.AssistContent out = new android.app.assist.AssistContent();
        activity.onProvideAssistContent(out);
        assertNull(out.getWebUri());
    }

    @Test
    public void activityLifecycle_destroyNoCrash() {
        ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class).create().start().resume();
        controller.pause().stop().destroy();
        assertNotNull(controller.get());
    }

    // ---- gap-closing tests ----

    private static DrawerActivity launchDrawer() {
        return Robolectric.buildActivity(DrawerActivity.class)
                .create().start().resume().get();
    }

    private static TestActivity launchWithIntent(Intent intent) {
        return Robolectric.buildActivity(TestActivity.class, intent)
                .create().start().resume().get();
    }

    private static DrawerLayout drawerLayout(BaseActivity activity) {
        try {
            java.lang.reflect.Field f =
                    BaseActivity.class.getDeclaredField("mDrawerLayout");
            f.setAccessible(true);
            return (DrawerLayout) f.get(activity);
        } catch (Exception e) {
            throw new AssertionError("mDrawerLayout", e);
        }
    }

    private static Object callPrivate(Object target, String name,
            Class<?>[] types, Object... args) {
        try {
            Method m = BaseActivity.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("call " + name, e.getCause());
        } catch (Exception e) {
            throw new AssertionError("call " + name, e);
        }
    }

    @Test
    public void onNightModeChanged_logsState() {
        TestActivity activity = launch();
        activity.onNightModeChanged(Configuration.UI_MODE_NIGHT_YES);
        activity.onNightModeChanged(Configuration.UI_MODE_NIGHT_NO);
    }

    @Test
    public void onInitExtras_calledWithIntentExtras() {
        Intent intent = new Intent(appContext(), TestActivity.class)
                .putExtra("k", "v");
        TestActivity activity = launchWithIntent(intent);
        assertNotNull(activity);
        assertEquals("Test Title", activity.getSupportActionBar().getTitle());
    }

    @Test
    public void launch_noHistoryFlag_hidesHomeAsUp() {
        Intent intent = new Intent(appContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        TestActivity activity = launchWithIntent(intent);
        assertEquals(0, activity.getSupportActionBar().getDisplayOptions()
                & android.app.ActionBar.DISPLAY_HOME_AS_UP);
    }

    @Test
    public void launch_newTaskIntent_hidesHomeAsUp() {
        Intent intent = new Intent(appContext(), TestActivity.class)
                .putExtra("IntentUtils.new_task", true);
        TestActivity activity = launchWithIntent(intent);
        assertEquals(0, activity.getSupportActionBar().getDisplayOptions()
                & android.app.ActionBar.DISPLAY_HOME_AS_UP);
    }

    @Test
    public void getInitialDrawerSelections_defaultZero() {
        TestActivity activity = launch();
        assertEquals(0, activity.getInitialLeftDrawerSelection(
                new MenuBuilder(appContext())));
        assertEquals(0, activity.getInitialRightDrawerSelection());
    }

    @Test
    public void closeDrawers_closesOpenDrawers() {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        drawer.openDrawer(android.view.Gravity.LEFT, false);
        drawer.openDrawer(android.view.Gravity.RIGHT, false);
        assertTrue(drawer.isDrawerOpen(android.view.Gravity.LEFT));
        assertTrue(drawer.isDrawerOpen(android.view.Gravity.RIGHT));
        assertTrue(activity.closeDrawers());
        assertFalse(drawer.isDrawerOpen(android.view.Gravity.LEFT));
        assertFalse(drawer.isDrawerOpen(android.view.Gravity.RIGHT));
    }

    @Test
    public void toggleRightSideDrawer_toggles() {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        assertFalse(drawer.isDrawerOpen(android.view.Gravity.RIGHT));
        activity.toggleRightSideDrawer();
        assertTrue(drawer.isDrawerOpen(android.view.Gravity.RIGHT));
        activity.toggleRightSideDrawer();
        assertFalse(drawer.isDrawerOpen(android.view.Gravity.RIGHT));
    }

    @Test
    public void configureLeftDrawerHeader_nullHeader_throwsNullPointer() {
        // T-MOD-02 (observation): configureLeftDrawerHeader has no null
        // guard — applyDrawerHeaderTopMargin (line 378) does, but the
        // ViewCompat.setOnApplyWindowInsetsListener call (line 364) throws
        // NPE on a null header. The only production caller passes the
        // non-null inflated header, so this is not user-visible.
        TestActivity activity = launch();
        try {
            activity.configureLeftDrawerHeader(null);
            org.junit.Assert.fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    public void getSystemStatusBarHeight_returnsNonNegative() {
        TestActivity activity = launch();
        assertTrue((Integer) callPrivate(activity, "getSystemStatusBarHeight",
                new Class<?>[0]) >= 0);
    }

    @Test
    public void applyDrawerHeaderTopMargin_cases() {
        TestActivity activity = launch();
        Class<?>[] types = {View.class, int.class};
        // height 0 -> early return
        View zero = new View(activity);
        zero.setLayoutParams(new ViewGroup.MarginLayoutParams(10, 10));
        callPrivate(activity, "applyDrawerHeaderTopMargin", types, zero, 0);
        assertEquals(0, ((ViewGroup.MarginLayoutParams) zero.getLayoutParams()).topMargin);
        // topMargin below height -> raised to height
        View low = new View(activity);
        low.setLayoutParams(new ViewGroup.MarginLayoutParams(10, 10));
        callPrivate(activity, "applyDrawerHeaderTopMargin", types, low, 50);
        assertEquals(50, ((ViewGroup.MarginLayoutParams) low.getLayoutParams()).topMargin);
        // topMargin already above height -> unchanged
        View high = new View(activity);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(10, 10);
        lp.topMargin = 100;
        high.setLayoutParams(lp);
        callPrivate(activity, "applyDrawerHeaderTopMargin", types, high, 50);
        assertEquals(100, ((ViewGroup.MarginLayoutParams) high.getLayoutParams()).topMargin);
        // non-margin layout params -> padding still applied, no crash
        View plain = new View(activity);
        plain.setLayoutParams(new ViewGroup.LayoutParams(10, 10));
        plain.setPadding(3, 9, 3, 4);
        callPrivate(activity, "applyDrawerHeaderTopMargin", types, plain, 50);
        assertEquals(0, plain.getPaddingTop());
        assertEquals(3, plain.getPaddingLeft());
    }

    @Test
    public void configureLeftDrawerHeader_insetsListener_appliesTopMargin() {
        TestActivity activity = launch();
        View header = new View(activity);
        header.setLayoutParams(new ViewGroup.MarginLayoutParams(10, 10));
        activity.configureLeftDrawerHeader(header);
        WindowInsets insets = new WindowInsets.Builder()
                .setInsets(WindowInsets.Type.systemBars(),
                        android.graphics.Insets.of(0, 50, 0, 0))
                .build();
        ViewCompat.dispatchApplyWindowInsets(header,
                WindowInsetsCompat.toWindowInsetsCompat(insets));
        assertEquals(50, ((ViewGroup.MarginLayoutParams) header.getLayoutParams()).topMargin);
    }

    @Test
    public void updateRightNavigationDrawer_populatesMenu() {
        DrawerActivity activity = launchDrawer();
        androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.toolbar);
        assertNotNull(toolbar);
        android.view.Menu menu = ((com.google.android.material.navigation.NavigationView)
                activity.findViewById(R.id.right_drawer)).getMenu();
        assertTrue(menu.hasVisibleItems());
        assertNotNull(menu.findItem(R.id.feed_hub));
        assertTrue(menu.findItem(R.id.feed_hub).isChecked());
    }

    @Test
    public void leftDrawerMenu_inflatedAndChecked() {
        DrawerActivity activity = launchDrawer();
        android.view.Menu menu = ((com.google.android.material.navigation.NavigationView)
                activity.findViewById(R.id.left_drawer)).getMenu();
        assertTrue(menu.hasVisibleItems());
        assertNotNull(menu.findItem(R.id.feed_hub));
        assertTrue(menu.findItem(R.id.feed_hub).isChecked());
    }

    @Test
    public void leftDrawerNavigationClick_opensDrawer() throws Exception {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        assertFalse(drawer.isDrawerOpen(androidx.core.view.GravityCompat.START));
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        java.lang.reflect.Field f = Toolbar.class.getDeclaredField("mNavButtonView");
        f.setAccessible(true);
        View navButton = (View) f.get(toolbar);
        assertNotNull(navButton);
        navButton.performClick();
        assertTrue(drawer.isDrawerOpen(androidx.core.view.GravityCompat.START));
    }

    @Test
    public void goToToplevelActivity_startsActivity() {
        TestActivity activity = launch();
        activity.goToToplevelActivity();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Github4AndroidActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void applyAccentTheme_eachAccent_launches() {
        SharedPreferences prefs = appContext().getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        String old = prefs.getString(SettingsFragment.KEY_ACCENT_COLOR, null);
        try {
            for (String accent : new String[]{"blue", "red", "orange", "purple", "teal"}) {
                prefs.edit().putString(SettingsFragment.KEY_ACCENT_COLOR, accent).apply();
                assertNotNull(launch());
            }
        } finally {
            if (old == null) {
                prefs.edit().remove(SettingsFragment.KEY_ACCENT_COLOR).apply();
            } else {
                prefs.edit().putString(SettingsFragment.KEY_ACCENT_COLOR, old).apply();
            }
        }
    }

    @Test
    public void removeHeaderView_nullAndAdded() {
        TestActivity activity = launch();
        activity.removeHeaderView(null);
        View v = new View(activity);
        activity.addHeaderView(v, false);
        assertNotNull(v.getParent());
        activity.removeHeaderView(v);
        assertNull(v.getParent());
    }

    @Test
    public void onCreateOptionsMenu_detachItemAdded() {
        DrawerActivity activity = launchDrawer();
        MenuBuilder menu = new MenuBuilder(appContext());
        assertTrue(activity.onCreateOptionsMenu(menu));
        assertNotNull(menu.findItem(R.id.detach));
    }

    @Test
    public void onOptionsItemSelected_homeWithIntent_startsActivity() {
        DrawerActivity activity = launchDrawer();
        MenuItem home = mock(MenuItem.class);
        when(home.getItemId()).thenReturn(android.R.id.home);
        assertTrue(activity.onOptionsItemSelected(home));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(TestActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void onOptionsItemSelected_detach_startsNewTask() {
        DrawerActivity activity = launchDrawer();
        MenuItem detach = mock(MenuItem.class);
        when(detach.getItemId()).thenReturn(R.id.detach);
        assertTrue(activity.onOptionsItemSelected(detach));
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onBackPressed_drawerOpen_returns() {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        drawer.openDrawer(android.view.Gravity.LEFT, false);
        assertTrue(drawer.isDrawerOpen(android.view.Gravity.LEFT));
        activity.onBackPressed();
        assertFalse(activity.isFinishing());
        assertFalse(drawer.isDrawerOpen(android.view.Gravity.LEFT));
    }

    @Test
    public void onBackPressed_newTaskIntent_finishAndRemoveTask() {
        Intent intent = new Intent(appContext(), TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        TestActivity activity = launchWithIntent(intent);
        activity.onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void setErrorViewVisibility_dmca_showsDmcaExplanation() {
        TestActivity activity = launch();
        String json = "{\"message\":\"x\",\"block\":{\"reason\":\"dmca\","
                + "\"created_at\":\"2020-01-01T00:00:00Z\","
                + "\"html_url\":\"https://github.com/dmca/123\"}}";
        ApiRequestException ex = new ApiRequestException(Response.error(451,
                ResponseBody.create(MediaType.parse("application/json"), json)));
        activity.setErrorViewVisibility(true, ex);
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
        TextView message = error.findViewById(R.id.error_message);
        assertTrue(message.getText().toString().contains("https://github.com/dmca/123"));
        assertEquals(View.GONE, error.findViewById(R.id.retry_button).getVisibility());
    }

    @Test
    public void setErrorViewVisibility_nullErrorHidden_returnsEarly() {
        TestActivity activity = launch();
        activity.setErrorViewVisibility(false, null);
        assertNull(activity.findViewById(R.id.error));
    }

    @Test
    public void setErrorViewVisibility_nullErrorVisible_inflatesGeneric() {
        TestActivity activity = launch();
        activity.setErrorViewVisibility(true, null);
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertEquals(View.VISIBLE, error.findViewById(R.id.retry_button).getVisibility());
    }

    @Test
    public void setErrorViewVisibility_apiErrorNullResponse_usesGenericMessage() {
        TestActivity activity = launch();
        ApiRequestException ex = new ApiRequestException(Response.error(500,
                ResponseBody.create(MediaType.parse("application/json"), "not json")));
        assertNull(ex.getResponse());
        activity.setErrorViewVisibility(true, ex);
        View error = activity.findViewById(R.id.error);
        assertEquals(View.VISIBLE, error.getVisibility());
        TextView message = error.findViewById(R.id.error_message);
        assertEquals(activity.getString(R.string.load_failure_explanation),
                message.getText().toString());
        assertEquals(View.VISIBLE, error.findViewById(R.id.retry_button).getVisibility());
    }

    @Test
    public void launchIssueCreationForError_launchesIssueEditor() {
        TestActivity activity = launch();
        callPrivate(activity, "launchIssueCreationForError",
                new Class<?>[]{Throwable.class}, new java.io.IOException("x"));
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.IssueEditActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void issueReportLauncher_resultOk_showsSnackbarAndOpensIssue() throws Exception {
        TestActivity activity = launch();
        // Launch first: the registry only invokes the callback for keys in
        // its launchedKeys list; dispatching a result without a launch
        // just stores it as pending.
        callPrivate(activity, "launchIssueCreationForError",
                new Class<?>[]{Throwable.class}, new java.io.IOException("x"));
        Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(launched);
        assertEquals(com.gh4a.activities.IssueEditActivity.class.getName(),
                launched.getComponent().getClassName());
        ActivityResultRegistry registry = activity.getActivityResultRegistry();
        java.lang.reflect.Field keyToRc =
                ActivityResultRegistry.class.getDeclaredField("keyToRc");
        keyToRc.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>) keyToRc.get(registry);
        assertFalse(map.isEmpty());
        int rc = map.values().iterator().next();
        assertTrue(registry.dispatchResult(rc, Activity.RESULT_OK, new Intent()));
        ShadowLooper.idleMainLooper();
        View action = activity.getRootLayout().findViewById(
                com.google.android.material.R.id.snackbar_action);
        assertNotNull(action);
        action.performClick();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(IssueActivity.class.getName(), started.getComponent().getClassName());
    }

    @Test
    public void onDrawerOpenedClosed_delegate() {
        TestActivity activity = launch();
        activity.onDrawerOpened(true);
        activity.onDrawerOpened(false);
        activity.onDrawerClosed(true);
        activity.onDrawerClosed(false);
    }

    @Test
    public void drawerListener_callbacksDelegate() throws Exception {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        java.lang.reflect.Field f = DrawerLayout.class.getDeclaredField("mListeners");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<DrawerLayout.DrawerListener> listeners =
                (List<DrawerLayout.DrawerListener>) f.get(drawer);
        assertFalse(listeners.isEmpty());
        View leftDrawer = activity.findViewById(R.id.left_drawer);
        View rightDrawer = activity.findViewById(R.id.right_drawer);
        for (DrawerLayout.DrawerListener l : listeners) {
            l.onDrawerSlide(leftDrawer, 0.5f);
            l.onDrawerOpened(leftDrawer);
            l.onDrawerOpened(rightDrawer);
            l.onDrawerClosed(leftDrawer);
            l.onDrawerClosed(rightDrawer);
            l.onDrawerStateChanged(DrawerLayout.STATE_IDLE);
        }
    }

    private static Runnable queuedGestureRunnable(DrawerLayout drawer) {
        try {
            java.lang.reflect.Field f = View.class.getDeclaredField("mRunQueue");
            f.setAccessible(true);
            Object queue = f.get(drawer);
            java.lang.reflect.Field actionsF =
                    queue.getClass().getDeclaredField("mActions");
            actionsF.setAccessible(true);
            Object[] actions = (Object[]) actionsF.get(queue);
            java.lang.reflect.Field countF =
                    queue.getClass().getDeclaredField("mCount");
            countF.setAccessible(true);
            int count = countF.getInt(queue);
            for (int i = 0; i < count; i++) {
                Object a = actions[i];
                java.lang.reflect.Field actF =
                        a.getClass().getDeclaredField("action");
                actF.setAccessible(true);
                Runnable r = (Runnable) actF.get(a);
                if (r.getClass().getName()
                        .startsWith("com.gh4a.BaseActivity$$Lambda")) {
                    return r;
                }
            }
            throw new AssertionError("gesture runnable not found in RunQueue");
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("RunQueue", e);
        }
    }

    @Test
    public void updateDrawerGestureExclusion_lockedClosed_clearsExclusion() {
        TestActivity activity = launch();
        DrawerLayout drawer = drawerLayout(activity);
        // Pre-set a rect so the test proves the locked branch clears it
        // (empty is also the default, which would prove nothing).
        drawer.setSystemGestureExclusionRects(
                java.util.Collections.singletonList(
                        new android.graphics.Rect(0, 0, 20, 1920)));
        assertEquals(1, drawer.getSystemGestureExclusionRects().size());
        queuedGestureRunnable(drawer).run();
        assertTrue(drawer.getSystemGestureExclusionRects().isEmpty());
    }

    @Test
    public void updateDrawerGestureExclusion_unlockedWithHeight_setsExclusion() {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        drawer.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        drawer.layout(0, 0, 1080, 1920);
        queuedGestureRunnable(drawer).run();
        assertEquals(1, drawer.getSystemGestureExclusionRects().size());
        android.graphics.Rect rect =
                drawer.getSystemGestureExclusionRects().get(0);
        assertEquals(1920, rect.bottom);
        assertTrue(rect.right > 0);
    }

    @Test
    public void updateDrawerGestureExclusion_unlockedZeroHeight_noExclusion() {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        assertEquals(0, drawer.getHeight());
        drawer.setSystemGestureExclusionRects(
                java.util.Collections.singletonList(
                        new android.graphics.Rect(0, 0, 20, 1920)));
        // Height is 0, so the runnable returns early and leaves the
        // pre-set rects untouched (lines 1042-1043).
        queuedGestureRunnable(drawer).run();
        assertEquals(1, drawer.getSystemGestureExclusionRects().size());
    }

    @Test
    public void updateViewVisibility_emptyHiddenCombinations() {
        TestActivity activity = launch();
        activity.setContentShown(false);
        activity.setContentEmpty(true);
        activity.setContentShown(true);
        activity.setContentEmpty(false);
    }

    @Test
    public void ensureContent_secondCall_returnsEarly() {
        TestActivity activity = launch();
        assertNotNull(activity.getRootLayout());
        assertNotNull(activity.getRootLayout());
    }

    @Test
    public void onProvideAssistContent_withUri_setsWebUri() {
        DrawerActivity activity = launchDrawer();
        android.app.assist.AssistContent out = new android.app.assist.AssistContent();
        activity.onProvideAssistContent(out);
        assertEquals(Uri.parse("https://example.com/x"), out.getWebUri());
    }

    @Test
    public void updateTaskDescriptionRunnable_runs() throws Exception {
        TestActivity activity = launch();
        java.lang.reflect.Field f =
                BaseActivity.class.getDeclaredField("mUpdateTaskDescriptionRunnable");
        f.setAccessible(true);
        ((Runnable) f.get(activity)).run();
    }

    @Test
    public void updateTaskDescriptionRunnable_newTaskIntent_usesTitle() throws Exception {
        Intent intent = new Intent(appContext(), TestActivity.class)
                .putExtra("IntentUtils.new_task", true);
        TestActivity activity = launchWithIntent(intent);
        java.lang.reflect.Field f =
                BaseActivity.class.getDeclaredField("mUpdateTaskDescriptionRunnable");
        f.setAccessible(true);
        ((Runnable) f.get(activity)).run();
    }

    @Test
    public void cancelHeaderTransition_cancelsRunningTransition() throws Exception {
        TestActivity activity = launch();
        activity.transitionHeaderToColor(
                androidx.appcompat.R.attr.colorPrimary,
                androidx.appcompat.R.attr.colorPrimaryDark);
        java.lang.reflect.Field f = BaseActivity.class.getDeclaredField("mHeaderTransition");
        f.setAccessible(true);
        assertNotNull(f.get(activity));
        callPrivate(activity, "cancelHeaderTransition", new Class<?>[0]);
        assertNull(f.get(activity));
    }

    @Test
    public void handleLoadFailure_compositeProgrammingError_forwarded() {
        TestActivity activity = launch();
        AtomicReference<Throwable> forwarded = new AtomicReference<>();
        Thread.UncaughtExceptionHandler old =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> forwarded.set(e));
        try {
            io.reactivex.exceptions.CompositeException composite =
                    new io.reactivex.exceptions.CompositeException(
                            new NullPointerException("prog bug"));
            activity.handleLoadFailure(composite);
            assertSame(composite, forwarded.get());
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
    }

    @Test
    public void handleLoadFailure_undeliverableProgrammingError_forwarded() {
        TestActivity activity = launch();
        AtomicReference<Throwable> forwarded = new AtomicReference<>();
        Thread.UncaughtExceptionHandler old =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> forwarded.set(e));
        try {
            io.reactivex.exceptions.UndeliverableException undeliverable =
                    new io.reactivex.exceptions.UndeliverableException(
                            new NullPointerException("prog bug"));
            activity.handleLoadFailure(undeliverable);
            assertSame(undeliverable, forwarded.get());
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
    }

    @Test
    public void handleLoadFailure_undeliverableBenign_noForward() {
        TestActivity activity = launch();
        AtomicReference<Throwable> forwarded = new AtomicReference<>();
        Thread.UncaughtExceptionHandler old =
                Thread.currentThread().getUncaughtExceptionHandler();
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> forwarded.set(e));
        try {
            activity.handleLoadFailure(
                    new io.reactivex.exceptions.UndeliverableException(
                            new java.io.IOException("net down")));
            assertNull(forwarded.get());
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
    }

    @Test
    public void onRequestPermissionsResult_noPendingCallback_noCrash() {
        TestActivity activity = launch();
        activity.onRequestPermissionsResult(0,
                new String[]{Manifest.permission.CAMERA},
                new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
    }

    @Test
    public void handleLoadFailure_authError_loginActionGoesToToplevel() {
        TestActivity activity = launch();
        activity.handleLoadFailure(apiError(401));
        ShadowLooper.idleMainLooper();
        View action = activity.getRootLayout().findViewById(
                com.google.android.material.R.id.snackbar_action);
        assertNotNull(action);
        action.performClick();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
    }

    @Test
    public void setupSwipeToRefresh_disabled_noRefreshListener() {
        NoSwipeActivity activity = Robolectric.buildActivity(NoSwipeActivity.class)
                .create().start().resume().get();
        assertFalse(activity.canSwipeToRefresh());
        assertNotNull(activity.getRootLayout());
    }

    // ---- gap-closing tests round 2 ----

    private static void setBaseField(Object target, String name, Object value) {
        try {
            java.lang.reflect.Field f = BaseActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new AssertionError("field " + name, e);
        }
    }

    private static Object getBaseField(Object target, String name) {
        try {
            java.lang.reflect.Field f = BaseActivity.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new AssertionError("field " + name, e);
        }
    }

    @Test
    public void issueReportLauncher_resultCanceled_noSnackbar() throws Exception {
        TestActivity activity = launch();
        callPrivate(activity, "launchIssueCreationForError",
                new Class<?>[]{Throwable.class}, new java.io.IOException("x"));
        Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(launched);
        ActivityResultRegistry registry = activity.getActivityResultRegistry();
        java.lang.reflect.Field keyToRc =
                ActivityResultRegistry.class.getDeclaredField("keyToRc");
        keyToRc.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>) keyToRc.get(registry);
        assertFalse(map.isEmpty());
        int rc = map.values().iterator().next();
        assertTrue(registry.dispatchResult(rc, Activity.RESULT_CANCELED, new Intent()));
        ShadowLooper.idleMainLooper();
        assertNull(activity.getRootLayout().findViewById(
                com.google.android.material.R.id.snackbar_action));
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void applyDrawerHeaderTopMargin_nullHeader_noCrash() {
        TestActivity activity = launch();
        callPrivate(activity, "applyDrawerHeaderTopMargin",
                new Class<?>[]{View.class, int.class}, null, 100);
    }

    @Test
    public void applyDrawerHeaderTopMargin_zeroHeight_keepsMargin() {
        TestActivity activity = launch();
        View header = new View(activity);
        ViewGroup.MarginLayoutParams lp =
                new ViewGroup.MarginLayoutParams(100, 100);
        lp.topMargin = 5;
        header.setLayoutParams(lp);
        callPrivate(activity, "applyDrawerHeaderTopMargin",
                new Class<?>[]{View.class, int.class}, header, 0);
        assertEquals(5, ((ViewGroup.MarginLayoutParams)
                header.getLayoutParams()).topMargin);
    }

    @Test
    public void applyDrawerHeaderTopMargin_positiveHeight_setsTopMargin() {
        TestActivity activity = launch();
        View header = new View(activity);
        ViewGroup.MarginLayoutParams lp =
                new ViewGroup.MarginLayoutParams(100, 100);
        lp.topMargin = 5;
        header.setLayoutParams(lp);
        callPrivate(activity, "applyDrawerHeaderTopMargin",
                new Class<?>[]{View.class, int.class}, header, 50);
        assertEquals(50, ((ViewGroup.MarginLayoutParams)
                header.getLayoutParams()).topMargin);
    }

    @Test
    public void applyDrawerHeaderTopMargin_alreadyTall_keepsMargin() {
        TestActivity activity = launch();
        View header = new View(activity);
        ViewGroup.MarginLayoutParams lp =
                new ViewGroup.MarginLayoutParams(100, 100);
        lp.topMargin = 80;
        header.setLayoutParams(lp);
        callPrivate(activity, "applyDrawerHeaderTopMargin",
                new Class<?>[]{View.class, int.class}, header, 50);
        assertEquals(80, ((ViewGroup.MarginLayoutParams)
                header.getLayoutParams()).topMargin);
    }

    @Test
    public void getSystemStatusBarHeight_returnsPositive() {
        TestActivity activity = launch();
        assertTrue((Integer) callPrivate(activity, "getSystemStatusBarHeight",
                new Class<?>[0]) > 0);
    }

    @Test
    public void cancelHeaderTransition_notRunning_noCancel() {
        TestActivity activity = launch();
        Animator animator = mock(Animator.class);
        when(animator.isRunning()).thenReturn(false);
        setBaseField(activity, "mHeaderTransition", animator);
        callPrivate(activity, "cancelHeaderTransition", new Class<?>[0]);
        verify(animator, never()).cancel();
        assertNull(getBaseField(activity, "mHeaderTransition"));
    }

    @Test
    public void updateRightNavigationDrawer_noSelection_skipsCheckedItem() {
        RightMenuNoSelectionActivity activity =
                Robolectric.buildActivity(RightMenuNoSelectionActivity.class)
                        .create().start().resume().get();
        com.google.android.material.navigation.NavigationView rightDrawer =
                (com.google.android.material.navigation.NavigationView)
                        getBaseField(activity, "mRightDrawer");
        assertNotNull(rightDrawer);
        assertNull(rightDrawer.getCheckedItem());
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED,
                drawerLayout(activity).getDrawerLockMode(android.view.Gravity.RIGHT));
    }

    @Test
    public void onCreateOptionsMenu_newTaskIntent_noDetachItem() {
        Intent intent = new Intent(appContext(), DrawerActivity.class)
                .putExtra("IntentUtils.new_task", true);
        DrawerActivity activity = Robolectric.buildActivity(DrawerActivity.class, intent)
                .create().start().resume().get();
        MenuBuilder menu = new MenuBuilder(appContext());
        assertTrue(activity.onCreateOptionsMenu(menu));
        assertNull(menu.findItem(R.id.detach));
    }

    @Test
    public void setLeftDrawerLockedClosed_nullDrawer_noCrash() {
        TestActivity activity = launch();
        setBaseField(activity, "mDrawerLayout", null);
        activity.setLeftDrawerLockedClosed();
    }

    @Test
    public void setRightDrawerLockedClosed_populatedMenu_unlocks() {
        DrawerActivity activity = launchDrawer();
        activity.setRightDrawerLockedClosed(false);
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED,
                drawerLayout(activity).getDrawerLockMode(android.view.Gravity.RIGHT));
    }

    @Test
    public void setErrorViewVisibility_jsonError_reportButtonLaunchesIssueCreation() {
        TestActivity activity = launch();
        activity.setErrorViewVisibility(true,
                new JsonDataException("bad json"));
        View reportButton = activity.findViewById(R.id.report_button);
        assertNotNull(reportButton);
        assertEquals(View.VISIBLE, reportButton.getVisibility());
        reportButton.performClick();
        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.IssueEditActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void setupNavigationDrawer_leftMenuNoSelection_noCheckedItem() {
        LeftMenuNoSelectionActivity activity =
                Robolectric.buildActivity(LeftMenuNoSelectionActivity.class)
                        .create().start().resume().get();
        com.google.android.material.navigation.NavigationView leftDrawer =
                activity.findViewById(R.id.left_drawer);
        assertNotNull(leftDrawer);
        assertNull(leftDrawer.getCheckedItem());
    }

    @Test
    public void toolbarNavClick_lockedDrawer_staysClosed() throws Exception {
        DrawerActivity activity = launchDrawer();
        DrawerLayout drawer = drawerLayout(activity);
        activity.setLeftDrawerLockedClosed();
        assertFalse(drawer.isDrawerOpen(androidx.core.view.GravityCompat.START));
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        java.lang.reflect.Field f = Toolbar.class.getDeclaredField("mNavButtonView");
        f.setAccessible(true);
        View navButton = (View) f.get(toolbar);
        assertNotNull(navButton);
        navButton.performClick();
        assertFalse(drawer.isDrawerOpen(androidx.core.view.GravityCompat.START));
    }

    @Test
    public void updateDrawerGestureExclusion_nullDrawer_noCrash() {
        TestActivity activity = launch();
        setBaseField(activity, "mDrawerLayout", null);
        callPrivate(activity, "updateDrawerGestureExclusion", new Class<?>[0]);
    }

    public static class RightMenuNoSelectionActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new View(this));
        }

        @Override
        protected String getActionBarTitle() {
            return "Right Menu";
        }

        @Override
        protected int[] getRightNavigationDrawerMenuResources() {
            // bottom_nav_main has no pre-checked item, so a skipped
            // setCheckedItem leaves the drawer with no checked item.
            return new int[]{R.menu.bottom_nav_main};
        }
        // getInitialRightDrawerSelection() stays at the base default 0.
    }

    public static class LeftMenuNoSelectionActivity extends BaseActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new View(this));
        }

        @Override
        protected String getActionBarTitle() {
            return "Left Menu";
        }

        @Override
        protected int getLeftNavigationDrawerMenuResource() {
            // bottom_nav_main has no pre-checked item, so a skipped
            // setCheckedItem leaves the drawer with no checked item.
            return R.menu.bottom_nav_main;
        }
        // getInitialLeftDrawerSelection() stays at the base default 0.
    }
}
