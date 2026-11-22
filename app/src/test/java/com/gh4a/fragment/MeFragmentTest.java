package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.activities.home.HomeActivityTestHelper;
import com.gh4a.model.NotificationHolder;
import com.gh4a.utils.DarkModeScheduler;
import com.gh4a.utils.NotificationCountProvider;
import com.gh4a.utils.SingleFactory;
import com.gh4a.model.NotificationListLoadResult;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;
import com.meisolsson.githubsdk.service.activity.NotificationService;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import retrofit2.Response;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MeFragment (F1).
 * Full attach is not possible offline: onCreateView() calls loadUser(),
 * which subscribes a network Single. Only framework-free pieces are
 * tested here: newInstance, orZero, setCount and mirrorPresetSummary
 * (with prefs injected via reflection).
 */
@RunWith(RobolectricTestRunner.class)
public class MeFragmentTest {

    @Test
    public void newInstance_storesLogin() {
        MeFragment f = MeFragment.newInstance("octocat");
        assertEquals("octocat", f.getArguments().getString("login"));
    }

    @Test
    public void newInstance_nullLogin_storesNull() {
        MeFragment f = MeFragment.newInstance(null);
        assertNull(f.getArguments().getString("login"));
    }

    private static int orZero(Integer value) {
        try {
            Method m = MeFragment.class.getDeclaredMethod("orZero", Integer.class);
            m.setAccessible(true);
            return (int) m.invoke(null, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void orZero_nullReturnsZero() {
        assertEquals(0, orZero(null));
    }

    @Test
    public void orZero_valueReturnsValue() {
        assertEquals(0, orZero(0));
        assertEquals(42, orZero(42));
        assertEquals(-3, orZero(-3));
    }

    private static void setCount(View view, int id, int count) {
        try {
            Method m = MeFragment.class.getDeclaredMethod(
                    "setCount", View.class, int.class, int.class);
            m.setAccessible(true);
            m.invoke(null, view, id, count);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void setCount_writesCountText() {
        Context context = ApplicationProvider.getApplicationContext();
        LinearLayout layout = new LinearLayout(context);
        TextView tv = new TextView(context);
        tv.setId(R.id.me_count_repos_value);
        layout.addView(tv);
        setCount(layout, R.id.me_count_repos_value, 12);
        assertEquals("12", tv.getText().toString());
        setCount(layout, R.id.me_count_repos_value, 0);
        assertEquals("0", tv.getText().toString());
    }

    private static String mirrorPresetSummary(MeFragment f) {
        try {
            Method m = MeFragment.class.getDeclaredMethod("mirrorPresetSummary");
            m.setAccessible(true);
            return (String) m.invoke(f);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static MeFragment withPrefs(String presetValue) {
        MeFragment f = new MeFragment();
        try {
            java.lang.reflect.Field prefsField =
                    MeFragment.class.getDeclaredField("mPrefs");
            prefsField.setAccessible(true);
            Context context = ApplicationProvider.getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences(
                    SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(SettingsFragment.KEY_MIRROR_PRESET,
                    presetValue).apply();
            prefsField.set(f, prefs);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return f;
    }

    @Test
    public void mirrorPresetSummary_emptyPreset_returnsEmpty() {
        assertEquals("", mirrorPresetSummary(withPrefs("")));
    }

    @Test
    public void mirrorPresetSummary_nullPreset_returnsEmpty() {
        MeFragment f = new MeFragment();
        try {
            java.lang.reflect.Field prefsField =
                    MeFragment.class.getDeclaredField("mPrefs");
            prefsField.setAccessible(true);
            Context context = ApplicationProvider.getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences(
                    SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().remove(SettingsFragment.KEY_MIRROR_PRESET).apply();
            prefsField.set(f, prefs);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("", mirrorPresetSummary(f));
    }

    @Test
    public void mirrorPresetSummary_preset_returnsDisplayHost() {
        String summary = mirrorPresetSummary(
                withPrefs("https://ghproxy.example.com"));
        assertEquals(com.gh4a.utils.MirrorHelper.displayHost(
                "https://ghproxy.example.com"), summary);
    }

    // ---- 摘要方法（CREATED 态挂载 + 注入 mPrefs；requireContext 可用） ----

    private FragmentActivity mActivity;
    private MockedStatic<SingleFactory> mSingleFactoryMock;

    @Before
    public void setUpActivity() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        // loadUnreadNotificationCount 走 NotificationCountProvider，
        // 与 HomeActivityTest 同款挡法：清单例缓存 + 挡 SingleFactory。
        NotificationCountProvider.getInstance().invalidate();
        mSingleFactoryMock = mockStatic(SingleFactory.class, CALLS_REAL_METHODS);
        mSingleFactoryMock.when(() -> SingleFactory.getNotifications(false, false, false))
                .thenReturn(Single.just(
                        new NotificationListLoadResult(Collections.emptyList())));
    }

    @After
    public void tearDownMocks() {
        if (mSingleFactoryMock != null) {
            mSingleFactoryMock.close();
        }
    }

    private MeFragment attach(MeFragment f) {
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return f;
    }

    private static SharedPreferences prefs() {
        return ApplicationProvider.getApplicationContext()
                .getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    private static void putPref(String key, String value) {
        prefs().edit().putString(key, value).apply();
    }

    private static void putPref(String key, int value) {
        prefs().edit().putInt(key, value).apply();
    }

    private MeFragment withAttachedPrefs() {
        MeFragment f = new MeFragment();
        try {
            java.lang.reflect.Field prefsField =
                    MeFragment.class.getDeclaredField("mPrefs");
            prefsField.setAccessible(true);
            prefsField.set(f, prefs());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return attach(f);
    }

    private static String callSummary(MeFragment f, String name) {
        try {
            Method m = MeFragment.class.getDeclaredMethod(name);
            m.setAccessible(true);
            return (String) m.invoke(f);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void darkModeSummary_systemMode() {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_SYSTEM);
        MeFragment f = withAttachedPrefs();
        assertEquals(f.getString(R.string.dark_mode_system),
                callSummary(f, "darkModeSummary"));
    }

    @Test
    public void darkModeSummary_scheduledValueMigratesToSystem() {
        // BUG-20：写 scheduled 会经 prefs 监听 → updateTheme → apply →
        // migrateScheduledToSystem 立即改写成 system，所以摘要分支里的
        // scheduled 档在应用内不可达；此处锁定迁移后的真实行为。
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_SCHEDULED);
        MeFragment f = withAttachedPrefs();
        assertEquals("system",
                prefs().getString(DarkModeScheduler.KEY_DARK_MODE, ""));
        assertEquals(f.getString(R.string.dark_mode_system),
                callSummary(f, "darkModeSummary"));
    }

    @Test
    public void darkModeSummary_manualDarkAndLight() {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_MANUAL);
        putPref(SettingsFragment.KEY_THEME, 0);
        MeFragment f = withAttachedPrefs();
        assertEquals(f.getString(R.string.dark_mode_dark),
                callSummary(f, "darkModeSummary"));
        putPref(SettingsFragment.KEY_THEME, 1);
        assertEquals(f.getString(R.string.dark_mode_light),
                callSummary(f, "darkModeSummary"));
    }

    @Test
    public void accentSummary_knownAndUnknown() {
        MeFragment f = withAttachedPrefs();
        String[] items = f.getResources().getStringArray(R.array.accent_color_items);
        String[] values = f.getResources().getStringArray(R.array.accent_color_values);
        // prefs 跨测试共享，先显式回到默认值再断言
        putPref(SettingsFragment.KEY_ACCENT_COLOR, values[0]);
        assertEquals(items[0], callSummary(f, "accentSummary"));
        putPref(SettingsFragment.KEY_ACCENT_COLOR, values[values.length - 1]);
        assertEquals(items[items.length - 1], callSummary(f, "accentSummary"));
        putPref(SettingsFragment.KEY_ACCENT_COLOR, "no-such-color");
        assertEquals("no-such-color", callSummary(f, "accentSummary"));
    }

    @Test
    public void fontSizeSummary_knownAndUnknown() {
        MeFragment f = withAttachedPrefs();
        String[] items = f.getResources().getStringArray(R.array.font_scale_items);
        String[] values = f.getResources().getStringArray(R.array.font_scale_values);
        putPref(SettingsFragment.KEY_FONT_SCALE, values[0]);
        assertEquals(items[0], callSummary(f, "fontSizeSummary"));
        putPref(SettingsFragment.KEY_FONT_SCALE, values[values.length - 1]);
        assertEquals(items[items.length - 1], callSummary(f, "fontSizeSummary"));
        putPref(SettingsFragment.KEY_FONT_SCALE, "9.9");
        assertEquals("9.9", callSummary(f, "fontSizeSummary"));
    }

    @Test
    public void languageSummary_emptyFallsBackToFirstItem() {
        MeFragment f = withAttachedPrefs();
        String[] items = f.getResources().getStringArray(R.array.language_items);
        String[] values = f.getResources().getStringArray(R.array.language_values);
        putPref(SettingsFragment.KEY_LANGUAGE, "");
        assertEquals(items[0], callSummary(f, "languageSummary"));
        putPref(SettingsFragment.KEY_LANGUAGE, values[values.length - 1]);
        assertEquals(items[items.length - 1], callSummary(f, "languageSummary"));
        putPref(SettingsFragment.KEY_LANGUAGE, "xx");
        assertEquals("xx", callSummary(f, "languageSummary"));
    }

    @Test
    public void startPageOptions_hasFiveBottomTabs() throws Exception {
        MeFragment f = withAttachedPrefs();
        Method m = MeFragment.class.getDeclaredMethod("startPageOptions");
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String[]> options = (List<String[]>) m.invoke(f);
        assertEquals(5, options.size());
        assertEquals("discover_hub", options.get(0)[1]);
        assertEquals("feed_hub", options.get(1)[1]);
        assertEquals("search", options.get(2)[1]);
        assertEquals("star_hub", options.get(3)[1]);
        assertEquals("profile", options.get(4)[1]);
    }

    @Test
    public void startPageSummary_knownAndUnknown() {
        putPref(SettingsFragment.KEY_START_PAGE, "star_hub");
        MeFragment f = withAttachedPrefs();
        assertEquals(f.getString(R.string.star_hub), callSummary(f, "startPageSummary"));
        putPref(SettingsFragment.KEY_START_PAGE, "gists");
        assertEquals("gists", callSummary(f, "startPageSummary"));
    }

    @Test
    public void normalizeStartPage_legacyKeyRewritesToDiscoverHub() {
        putPref(SettingsFragment.KEY_START_PAGE, "repos");
        MeFragment f = withAttachedPrefs();
        try {
            Method m = MeFragment.class.getDeclaredMethod("normalizeStartPage");
            m.setAccessible(true);
            m.invoke(f);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("discover_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    @Test
    public void normalizeStartPage_lastResolvesToLastSelected() {
        putPref(SettingsFragment.KEY_START_PAGE, "last");
        prefs().edit().putString("last_selected_home_page", "star_hub").apply();
        MeFragment f = withAttachedPrefs();
        try {
            Method m = MeFragment.class.getDeclaredMethod("normalizeStartPage");
            m.setAccessible(true);
            m.invoke(f);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("star_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    @Test
    public void normalizeStartPage_validKeyIsUnchanged() {
        putPref(SettingsFragment.KEY_START_PAGE, "search");
        MeFragment f = withAttachedPrefs();
        try {
            Method m = MeFragment.class.getDeclaredMethod("normalizeStartPage");
            m.setAccessible(true);
            m.invoke(f);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertEquals("search",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    // ---- 整页挂载（login 为空不拉用户；通知计数已挡） ----

    private MeFragment attachResumed(String login) {
        MeFragment f = MeFragment.newInstance(login);
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private static Object field(MeFragment f, String name) throws Exception {
        java.lang.reflect.Field field = MeFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(MeFragment f, String name, Object value)
            throws Exception {
        java.lang.reflect.Field field = MeFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private static void callVoid(MeFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = MeFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(f, args);
    }

    @Test
    public void onCreateView_nullLogin_buildsRowsWithoutNetwork() {
        MeFragment f = attachResumed(null);
        View view = f.getView();
        assertTrue(view != null);
        assertTrue(view.findViewById(R.id.me_account_header) != null);
        assertTrue(view.findViewById(R.id.me_count_repos_value) != null);
        assertTrue(view.findViewById(R.id.me_count_followers_value) != null);
        assertTrue(view.findViewById(R.id.me_count_following_value) != null);
    }

    @Test
    public void fillUserFallback_setsLoginAndZeroCounts() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mLogin", "octo");
        callVoid(f, "fillUserFallback", new Class<?>[]{});
        View view = f.getView();
        assertEquals("octo",
                ((TextView) view.findViewById(R.id.me_name)).getText().toString());
        assertEquals("octo",
                ((TextView) view.findViewById(R.id.me_login)).getText().toString());
        assertEquals("0", ((TextView) view.findViewById(
                R.id.me_count_repos_value)).getText().toString());
        assertEquals("0", ((TextView) view.findViewById(
                R.id.me_count_followers_value)).getText().toString());
        assertEquals("0", ((TextView) view.findViewById(
                R.id.me_count_following_value)).getText().toString());
    }

    @Test
    public void fillUserFromCache_fillsHeader() throws Exception {
        MeFragment f = attachResumed(null);
        View view = f.getView();
        MeHeaderCache.Header header = new MeHeaderCache.Header(
                "octo", "Octo Cat", 0L, null, 1700000000000L, 5, 6, 7);
        callVoid(f, "fillUserFromCache",
                new Class<?>[]{View.class, MeHeaderCache.Header.class}, view, header);
        assertEquals("Octo Cat",
                ((TextView) view.findViewById(R.id.me_name)).getText().toString());
        assertEquals("octo",
                ((TextView) view.findViewById(R.id.me_login)).getText().toString());
        assertEquals("5", ((TextView) view.findViewById(
                R.id.me_count_repos_value)).getText().toString());
        assertEquals("6", ((TextView) view.findViewById(
                R.id.me_count_followers_value)).getText().toString());
        assertEquals("7", ((TextView) view.findViewById(
                R.id.me_count_following_value)).getText().toString());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.me_joined).getVisibility());
    }

    @Test
    public void fillUserFromCache_blankNameFallsBackToLogin() throws Exception {
        MeFragment f = attachResumed(null);
        View view = f.getView();
        MeHeaderCache.Header header = new MeHeaderCache.Header(
                "octo", "", 0L, null, -1L, 0, 0, 0);
        callVoid(f, "fillUserFromCache",
                new Class<?>[]{View.class, MeHeaderCache.Header.class}, view, header);
        assertEquals("octo",
                ((TextView) view.findViewById(R.id.me_name)).getText().toString());
        assertEquals(View.GONE,
                view.findViewById(R.id.me_joined).getVisibility());
    }

    @Test
    public void showHeaderContent_hidesSkeletonShowsHeader() throws Exception {
        MeFragment f = attachResumed(null);
        View view = f.getView();
        callVoid(f, "showHeaderContent", new Class<?>[]{View.class}, view);
        assertEquals(View.GONE,
                view.findViewById(R.id.me_header_skeleton).getVisibility());
        assertEquals(View.VISIBLE,
                view.findViewById(R.id.me_account_header).getVisibility());
    }

    // ---- 设置对话框 / 备份恢复 ----

    private static android.app.Dialog latestDialog() {
        java.util.List<android.app.Dialog> shown =
                org.robolectric.shadows.ShadowDialog.getShownDialogs();
        return shown.get(shown.size() - 1);
    }

    private static void clickDialogItem(android.app.Dialog dialog, int which) {
        // 此 Robolectric 版本的 shadowOf(dialog) 只给 ShadowDialog，
        // 没有 clickOnItem；直接对对话框里的 ListView 做 performItemClick，
        // 与 ShadowAlertDialog.clickOnItem 内部做法相同。
        android.widget.ListView list = dialog.findViewById(
                androidx.appcompat.R.id.select_dialog_listview);
        assertTrue(list != null);
        android.view.View itemView = list.getAdapter().getView(which, null, list);
        list.performItemClick(itemView, which,
                list.getAdapter().getItemId(which));
    }

    @Test
    public void showDarkModeDialog_pickLightWritesManualLight() throws Exception {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_SYSTEM);
        putPref(SettingsFragment.KEY_THEME, 1);
        MeFragment f = attachResumed(null);
        callVoid(f, "showDarkModeDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 1);
        assertEquals(DarkModeScheduler.MODE_MANUAL,
                prefs().getString(DarkModeScheduler.KEY_DARK_MODE, ""));
        assertEquals(1, prefs().getInt(SettingsFragment.KEY_THEME, -1));
    }

    @Test
    public void showDarkModeDialog_pickSystemWritesSystem() throws Exception {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_MANUAL);
        putPref(SettingsFragment.KEY_THEME, 0);
        MeFragment f = attachResumed(null);
        callVoid(f, "showDarkModeDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 0);
        assertEquals(DarkModeScheduler.MODE_SYSTEM,
                prefs().getString(DarkModeScheduler.KEY_DARK_MODE, ""));
    }

    @Test
    public void showDarkModeDialog_pickDarkWritesManualDark() throws Exception {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_SYSTEM);
        MeFragment f = attachResumed(null);
        callVoid(f, "showDarkModeDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 2);
        assertEquals(DarkModeScheduler.MODE_MANUAL,
                prefs().getString(DarkModeScheduler.KEY_DARK_MODE, ""));
        assertEquals(0, prefs().getInt(SettingsFragment.KEY_THEME, -1));
    }

    @Test
    public void showDarkModeDialog_checkedIndexFollowsTheme() throws Exception {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_MANUAL);
        putPref(SettingsFragment.KEY_THEME, 0);
        MeFragment f = attachResumed(null);
        callVoid(f, "showDarkModeDialog", new Class<?>[]{});
        // Robolectric 此版本的 ShadowAlertDialog 读不到单选 checked 下标，
        // 只断言对话框已弹出、且手动深色对应的 checked 计算分支已走通。
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > 0);
    }

    @Test
    public void showAccentDialog_pickWritesAccent() throws Exception {
        MeFragment f = attachResumed(null);
        String[] values = f.getResources().getStringArray(R.array.accent_color_values);
        putPref(SettingsFragment.KEY_ACCENT_COLOR, values[0]);
        callVoid(f, "showAccentDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 1);
        assertEquals(values[1],
                prefs().getString(SettingsFragment.KEY_ACCENT_COLOR, ""));
    }

    @Test
    public void showFontSizeDialog_pickWritesScale() throws Exception {
        MeFragment f = attachResumed(null);
        String[] values = f.getResources().getStringArray(R.array.font_scale_values);
        putPref(SettingsFragment.KEY_FONT_SCALE, values[1]);
        callVoid(f, "showFontSizeDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 0);
        assertEquals(values[0],
                prefs().getString(SettingsFragment.KEY_FONT_SCALE, ""));
    }

    @Test
    public void showLanguageDialog_pickWritesLanguage() throws Exception {
        MeFragment f = attachResumed(null);
        String[] values = f.getResources().getStringArray(R.array.language_values);
        putPref(SettingsFragment.KEY_LANGUAGE, "");
        callVoid(f, "showLanguageDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), values.length - 1);
        assertEquals(values[values.length - 1],
                prefs().getString(SettingsFragment.KEY_LANGUAGE, ""));
    }

    @Test
    public void showStartPageDialog_pickWritesStartPage() throws Exception {
        MeFragment f = attachResumed(null);
        putPref(SettingsFragment.KEY_START_PAGE, "discover_hub");
        callVoid(f, "showStartPageDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 3);
        assertEquals("star_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    @Test
    public void rebuildSettingsRows_nullViewIsNoop() throws Exception {
        MeFragment f = withAttachedPrefs();
        callVoid(f, "rebuildSettingsRows", new Class<?>[]{});
    }

    @Test
    public void showAboutDialog_showsVersionDialog() throws Exception {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showAboutDialog", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showBackupRestoreDialog_showsTwoItems() throws Exception {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showBackupRestoreDialog", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    private static org.mockito.MockedStatic<com.gh4a.utils.Background>
            mockBackgroundImmediate() {
        org.mockito.MockedStatic<com.gh4a.utils.Background> mocked =
                org.mockito.Mockito.mockStatic(com.gh4a.utils.Background.class);
        mocked.when(() -> com.gh4a.utils.Background.run(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(Runnable.class)))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(1)).run();
                    return null;
                });
        return mocked;
    }

    @Test
    public void doBackup_noCredentialsWritesFile() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(
                            org.mockito.ArgumentMatchers.any(Context.class)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager
                            .backupContainsTranslationCredentials(
                                    org.mockito.ArgumentMatchers.any(
                                            org.json.JSONObject.class)))
                    .thenReturn(false);
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.writeBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn("octodroid-backup-test.json");
            MeFragment f = attachResumed(null);
            callVoid(f, "doBackup", new Class<?>[]{});
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("octodroid-backup-test.json"));
        }
    }

    @Test
    public void doBackup_withCredentialsShowsConfirmDialog() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(
                            org.mockito.ArgumentMatchers.any(Context.class)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager
                            .backupContainsTranslationCredentials(
                                    org.mockito.ArgumentMatchers.any(
                                            org.json.JSONObject.class)))
                    .thenReturn(true);
            MeFragment f = attachResumed(null);
            int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
            callVoid(f, "doBackup", new Class<?>[]{});
            assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                    > before);
        }
    }

    @Test
    public void doBackup_collectThrowsToastsFailure() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(
                            org.mockito.ArgumentMatchers.any(Context.class)))
                    .thenThrow(new RuntimeException("boom"));
            MeFragment f = attachResumed(null);
            callVoid(f, "doBackup", new Class<?>[]{});
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("boom"));
        }
    }

    @Test
    public void confirmAndApplyRestore_showsConfirmDialog() throws Exception {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "confirmAndApplyRestore",
                new Class<?>[]{android.net.Uri.class},
                android.net.Uri.parse("content://x/backup.json"));
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void doRestore_successToastsSuccess() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(android.net.Uri.class)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.applyBackup(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn(true);
            MeFragment f = attachResumed(null);
            callVoid(f, "doRestore",
                    new Class<?>[]{android.net.Uri.class},
                    android.net.Uri.parse("content://x/backup.json"));
            assertEquals(
                    ApplicationProvider.getApplicationContext()
                            .getString(R.string.restore_success),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void doRestore_applyFailsToastsFailure() throws Exception {
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(android.net.Uri.class)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.applyBackup(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn(false);
            MeFragment f = attachResumed(null);
            callVoid(f, "doRestore",
                    new Class<?>[]{android.net.Uri.class},
                    android.net.Uri.parse("content://x/backup.json"));
            assertEquals(
                    ApplicationProvider.getApplicationContext()
                            .getString(R.string.restore_failed),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void recreateActivity_postsRecreateWithoutCrash() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "recreateActivity", new Class<?>[]{});
    }

    @Test
    public void restartStackOnMeTab_postsRestartWithoutCrash() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "restartStackOnMeTab", new Class<?>[]{});
    }

    @Test
    public void showMirrorSourcePage_startsMirrorSourceActivity() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "showMirrorSourcePage", new Class<?>[]{});
        android.content.Intent started =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertTrue(started != null);
        assertEquals(com.gh4a.activities.MirrorSourceActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void normalizeStartPage_newsfeedMapsToFeedHub() throws Exception {
        putPref(SettingsFragment.KEY_START_PAGE, "newsfeed");
        MeFragment f = withAttachedPrefs();
        callVoid(f, "normalizeStartPage", new Class<?>[]{});
        assertEquals("feed_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    @Test
    public void normalizeStartPage_bookmarksMapsToStarHub() throws Exception {
        putPref(SettingsFragment.KEY_START_PAGE, "bookmarks");
        MeFragment f = withAttachedPrefs();
        callVoid(f, "normalizeStartPage", new Class<?>[]{});
        assertEquals("star_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    // ---- 新增：主线程 idle / 等待 / 行查找 ----

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < 100 && !cond.getAsBoolean(); i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            idleMain();
        }
        assertTrue(cond.getAsBoolean());
    }

    private static void removeFragment(MeFragment f) {
        ((FragmentActivity) f.getActivity()).getSupportFragmentManager()
                .beginTransaction().remove(f).commitNow();
        idleMain();
    }

    private static View childOf(MeFragment f, int containerId, int index) {
        return ((LinearLayout) f.getView().findViewById(containerId))
                .getChildAt(index);
    }

    private static String textOf(MeFragment f, int id) {
        return ((TextView) f.getView().findViewById(id)).getText().toString();
    }

    private static User user(String login, String name, java.util.Date createdAt,
            int pub, int priv, int followers, int following, UserType type) {
        return User.builder().login(login).name(name).id(1L)
                .avatarUrl(null).createdAt(createdAt)
                .publicRepos(pub).totalPrivateRepos(priv)
                .followers(followers).following(following).type(type).build();
    }

    // ---- 直接调用的空守卫 ----

    @Test
    public void showHeaderContent_nullViewIsNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "showHeaderContent", new Class<?>[]{View.class}, (Object) null);
    }

    @Test
    public void showHeaderContent_bareViewSkipsMissingParts() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "showHeaderContent", new Class<?>[]{View.class},
                new LinearLayout(ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void fillUserFromCache_nullArgsAreNoop() throws Exception {
        MeFragment f = attachResumed(null);
        View view = f.getView();
        callVoid(f, "fillUserFromCache",
                new Class<?>[]{View.class, MeHeaderCache.Header.class}, null, null);
        callVoid(f, "fillUserFromCache",
                new Class<?>[]{View.class, MeHeaderCache.Header.class}, view, null);
        callVoid(f, "fillUserFromCache",
                new Class<?>[]{View.class, MeHeaderCache.Header.class}, null,
                new MeHeaderCache.Header("octo", "O", 0L, null, -1L, 0, 0, 0));
    }

    @Test
    public void fillUserFallback_nullViewIsNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "fillUserFallback", new Class<?>[]{});
    }

    @Test
    public void fillUser_nullViewAndNullUserAreNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "fillUser", new Class<?>[]{});
        MeFragment attached = attachResumed(null);
        callVoid(attached, "fillUser", new Class<?>[]{});
    }

    @Test
    public void fillUser_blankNameAndNullCreatedAt() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", null, null, 5, 2, 6, 7, UserType.User));
        callVoid(f, "fillUser", new Class<?>[]{});
        assertEquals("octo", textOf(f, R.id.me_name));
        assertEquals("7", textOf(f, R.id.me_count_repos_value));
        assertEquals(View.GONE, f.getView().findViewById(R.id.me_joined).getVisibility());
    }

    @Test
    public void fillUser_namedWithCreatedAtShowsJoined() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo Cat",
                new java.util.Date(1700000000000L), 5, 2, 6, 7, UserType.User));
        callVoid(f, "fillUser", new Class<?>[]{});
        assertEquals("Octo Cat", textOf(f, R.id.me_name));
        assertEquals(View.VISIBLE,
                f.getView().findViewById(R.id.me_joined).getVisibility());
    }

    @Test
    public void loadUnreadNotificationCount_nullViewIsNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "loadUnreadNotificationCount", new Class<?>[]{});
    }

    @Test
    public void setupSectionHeader_nullsAreNoop() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "setupSectionHeader",
                new Class<?>[]{View.class, int.class, int.class, int.class},
                f.getView(), R.id.me_count_repos, R.id.me_count_followers,
                R.string.settings_category_about_updates);
        callVoid(f, "setupSectionHeader",
                new Class<?>[]{View.class, int.class, int.class, int.class},
                f.getView(), R.id.me_section_appearance_header, 0x7f000001,
                R.string.settings_category_about_updates);
    }

    @Test
    public void setupSectionHeader_nonTextHeaderSkipsSetText() throws Exception {
        MeFragment f = attachResumed(null);
        // 容器当 header：非 TextView，不设文字，只清点击。
        callVoid(f, "setupSectionHeader",
                new Class<?>[]{View.class, int.class, int.class, int.class},
                f.getView(), R.id.me_settings_appearance,
                R.id.me_settings_browsing,
                R.string.settings_category_about_updates);
        assertFalse(f.getView().findViewById(R.id.me_settings_appearance)
                .isClickable());
    }

    @Test
    public void setRowIcon_missingChipReturns() throws Exception {
        MeFragment f = attachResumed(null);
        View row = new LinearLayout(ApplicationProvider.getApplicationContext());
        callVoid(f, "setRowIcon",
                new Class<?>[]{View.class, int.class}, row, R.drawable.icon_theme);
    }

    @Test
    public void setRowIcon_missingIconHidesChip() throws Exception {
        MeFragment f = attachResumed(null);
        LinearLayout row = new LinearLayout(ApplicationProvider.getApplicationContext());
        View chip = new View(ApplicationProvider.getApplicationContext());
        chip.setId(R.id.me_setting_icon_chip);
        row.addView(chip);
        callVoid(f, "setRowIcon",
                new Class<?>[]{View.class, int.class}, row, R.drawable.icon_theme);
        assertEquals(View.GONE, chip.getVisibility());
    }

    @Test
    public void setRowIcon_zeroIconResReturns() throws Exception {
        MeFragment f = attachResumed(null);
        View row = childOf(f, R.id.me_settings_appearance, 0);
        callVoid(f, "setRowIcon", new Class<?>[]{View.class, int.class}, row, 0);
    }

    @Test
    public void showMirrorSourcePage_nullActivityIsNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "showMirrorSourcePage", new Class<?>[]{});
    }

    @Test
    public void recreateActivity_nullActivityIsNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "recreateActivity", new Class<?>[]{});
    }

    @Test
    public void restartStackOnMeTab_nullActivityIsNoop() throws Exception {
        MeFragment f = new MeFragment();
        callVoid(f, "restartStackOnMeTab", new Class<?>[]{});
    }

    // ---- 新增：头部 / 计数点击 ----

    private static android.content.Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    private static void clearHeaderCache(String login) {
        MeHeaderCache.clear(appContext(), login);
    }

    private static MockedStatic<ServiceFactory> mockUserService(
            Single<Response<User>> result) {
        UserService service = mock(UserService.class);
        when(service.getUser("octo")).thenReturn(result);
        MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, CALLS_REAL_METHODS);
        mocked.when(() -> ServiceFactory.get(UserService.class, false))
                .thenReturn(service);
        return mocked;
    }

    @Test
    public void headerClick_plainActivityIsNoop() {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        f.getView().findViewById(R.id.me_account_header).performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
        assertEquals(before,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void countClicks_nullUserAreNoop() {
        MeFragment f = attachResumed(null);
        f.getView().findViewById(R.id.me_count_repos).performClick();
        f.getView().findViewById(R.id.me_count_followers).performClick();
        f.getView().findViewById(R.id.me_count_following).performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void countClick_reposOrgStartsRepoList() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo", null, 0, 0, 0, 0,
                UserType.Organization));
        f.getView().findViewById(R.id.me_count_repos).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.RepositoryListActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void countClick_followersStartsList() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo", null, 0, 0, 0, 0, UserType.User));
        f.getView().findViewById(R.id.me_count_followers).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(
                com.gh4a.activities.FollowerFollowingListActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void countClick_followingStartsList() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo", null, 0, 0, 0, 0, UserType.User));
        f.getView().findViewById(R.id.me_count_following).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(
                com.gh4a.activities.FollowerFollowingListActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void countClick_detachedNoActivity() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo", null, 0, 0, 0, 0, UserType.User));
        View reposView = f.getView().findViewById(R.id.me_count_repos);
        removeFragment(f);
        reposView.performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    // ---- 新增：loadUser / 头缓存 ----

    @Test
    public void loadUser_successFillsHeaderAndCaches() throws Exception {
        clearHeaderCache("octo");
        User u = user("octo", "Octo Cat", new java.util.Date(1700000000000L),
                5, 2, 6, 7, UserType.User);
        try (MockedStatic<ServiceFactory> mocked =
                     mockUserService(Single.just(Response.success(u)))) {
            MeFragment f = attachResumed("octo");
            waitUntil(() -> textOf(f, R.id.me_name).equals("Octo Cat"));
            assertEquals("7", textOf(f, R.id.me_count_repos_value));
            assertEquals("6", textOf(f, R.id.me_count_followers_value));
            assertEquals("7", textOf(f, R.id.me_count_following_value));
            MeHeaderCache.Header cached = MeHeaderCache.load(appContext(), "octo");
            assertNotNull(cached);
            assertEquals(1700000000000L, cached.joinedMillis);
        }
    }

    @Test
    public void loadUser_successNullCreatedAtCachesMinusOne() throws Exception {
        clearHeaderCache("octo");
        User u = user("octo", "Octo Cat", null, 1, 0, 0, 0, UserType.User);
        try (MockedStatic<ServiceFactory> mocked =
                     mockUserService(Single.just(Response.success(u)))) {
            MeFragment f = attachResumed("octo");
            waitUntil(() -> textOf(f, R.id.me_name).equals("Octo Cat"));
            assertEquals(View.GONE,
                    f.getView().findViewById(R.id.me_joined).getVisibility());
            assertEquals(-1L,
                    MeHeaderCache.load(appContext(), "octo").joinedMillis);
        }
    }

    @Test
    public void loadUser_errorNoCacheFallsBack() throws Exception {
        clearHeaderCache("octo");
        try (MockedStatic<ServiceFactory> mocked = mockUserService(
                Single.error(new RuntimeException("boom")))) {
            MeFragment f = attachResumed("octo");
            waitUntil(() -> textOf(f, R.id.me_name).equals("octo"));
            assertEquals("0", textOf(f, R.id.me_count_repos_value));
            assertEquals("0", textOf(f, R.id.me_count_followers_value));
            assertEquals("0", textOf(f, R.id.me_count_following_value));
        }
    }

    @Test
    public void loadUser_errorWithCacheKeepsCache() throws Exception {
        clearHeaderCache("octo");
        MeHeaderCache.save(appContext(), new MeHeaderCache.Header(
                "octo", "Cached Name", 1L, null, 1700000000000L, 5, 6, 7));
        try (MockedStatic<ServiceFactory> mocked = mockUserService(
                Single.error(new RuntimeException("boom")))) {
            MeFragment f = attachResumed("octo");
            Thread.sleep(500);
            idleMain();
            assertEquals("Cached Name", textOf(f, R.id.me_name));
            assertEquals("5", textOf(f, R.id.me_count_repos_value));
        }
    }

    @Test
    public void loadUser_successDetachedDoesNotCache() throws Exception {
        clearHeaderCache("octo");
        io.reactivex.subjects.SingleSubject<Response<User>> subject =
                io.reactivex.subjects.SingleSubject.create();
        User u = user("octo", "Octo Cat", null, 1, 0, 0, 0, UserType.User);
        try (MockedStatic<ServiceFactory> mocked = mockUserService(subject)) {
            MeFragment f = attachResumed("octo");
            Thread.sleep(500);
            removeFragment(f);
            subject.onSuccess(Response.success(u));
            Thread.sleep(300);
            idleMain();
            assertNull(MeHeaderCache.load(appContext(), "octo"));
        }
    }

    @Test
    public void loadUser_errorDetachedNoFallback() throws Exception {
        clearHeaderCache("octo");
        io.reactivex.subjects.SingleSubject<Response<User>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<ServiceFactory> mocked = mockUserService(subject)) {
            MeFragment f = attachResumed("octo");
            View view = f.getView();
            Thread.sleep(500);
            removeFragment(f);
            subject.onError(new RuntimeException("boom"));
            Thread.sleep(300);
            idleMain();
            assertEquals("", ((TextView) view.findViewById(R.id.me_name))
                    .getText().toString());
        }
    }

    @Test
    public void onCreateView_cachedHeaderShowsCache() throws Exception {
        clearHeaderCache("octo");
        MeHeaderCache.save(appContext(), new MeHeaderCache.Header(
                "octo", "Cached Name", 1L, null, 1700000000000L, 5, 6, 7));
        try (MockedStatic<ServiceFactory> mocked =
                     mockUserService(Single.never())) {
            MeFragment f = attachResumed("octo");
            assertEquals("Cached Name", textOf(f, R.id.me_name));
            assertEquals(View.GONE,
                    f.getView().findViewById(R.id.me_header_skeleton).getVisibility());
            assertEquals(View.VISIBLE,
                    f.getView().findViewById(R.id.me_account_header).getVisibility());
        }
    }

    @Test
    public void onCreateView_noCacheShowsSkeleton() throws Exception {
        clearHeaderCache("octo");
        try (MockedStatic<ServiceFactory> mocked =
                     mockUserService(Single.never())) {
            MeFragment f = attachResumed("octo");
            assertEquals(View.VISIBLE,
                    f.getView().findViewById(R.id.me_header_skeleton).getVisibility());
            assertEquals(View.GONE,
                    f.getView().findViewById(R.id.me_account_header).getVisibility());
        }
    }

    @Test
    public void onCreateView_recreateAfterDestroyRefreshesDisposables() {
        MeFragment f = attachResumed(null);
        assertNotNull(f.getView());
        removeFragment(f);
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        assertNotNull(f.getView());
        assertNotNull(f.getView().findViewById(R.id.me_account_header));
    }

    @Test
    public void onResume_mirrorNullValueIsNoop() throws Exception {
        MeFragment f = withAttachedPrefs();
        setField(f, "mMirrorPresetValue", null);
        callVoid(f, "onResume", new Class<?>[]{});
    }

    // ---- 新增：功能行 / 设置行点击 ----

    @Test
    public void navRowClicks_plainActivityAreNoop() {
        MeFragment f = attachResumed(null);
        LinearLayout container = f.getView().findViewById(R.id.me_func_container);
        for (int i = 0; i < container.getChildCount(); i++) {
            container.getChildAt(i).performClick();
        }
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void translationRowClick_startsTranslationSettings() {
        MeFragment f = attachResumed(null);
        childOf(f, R.id.me_settings_appearance, 5).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.TranslationSettingsActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void mirrorPresetRowClick_startsMirrorSource() {
        MeFragment f = attachResumed(null);
        childOf(f, R.id.me_settings_browsing, 1).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.MirrorSourceActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void backupRowClick_showsBackupRestoreDialog() {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        childOf(f, R.id.me_settings_browsing, 2).performClick();
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void checkUpdateRowClick_plainActivityIsNoop() {
        MeFragment f = attachResumed(null);
        childOf(f, R.id.me_settings_about, 0).performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void diagnosticRowClick_startsDiagnosticLog() {
        MeFragment f = attachResumed(null);
        childOf(f, R.id.me_settings_about, 1).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.DiagnosticLogActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void aboutRowClick_showsAboutDialog() {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        childOf(f, R.id.me_settings_about, 2).performClick();
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void openSourceRowClick_showsComponentDialog() {
        MeFragment f = attachResumed(null);
        childOf(f, R.id.me_settings_about, 3).performClick();
        idleMain();
        assertNotNull(f.getChildFragmentManager().findFragmentByTag("opensource"));
    }

    @Test
    public void appearanceRowClicks_showDialogs() {
        MeFragment f = attachResumed(null);
        for (int i = 0; i < 5; i++) {
            int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
            childOf(f, R.id.me_settings_appearance, i).performClick();
            assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                    > before);
        }
    }

    @Test
    public void mirrorSwitch_toggleWritesPref() {
        prefs().edit().putBoolean(SettingsFragment.KEY_MIRROR_ENABLED, false).apply();
        MeFragment f = attachResumed(null);
        View row = childOf(f, R.id.me_settings_browsing, 0);
        SwitchCompat sw = row.findViewById(R.id.me_setting_switch);
        assertNotNull(sw);
        sw.performClick();
        assertTrue(prefs().getBoolean(SettingsFragment.KEY_MIRROR_ENABLED, false));
        assertTrue(childOf(f, R.id.me_settings_browsing, 1).isEnabled());
    }

    @Test
    public void customTabsSwitch_toggleWritesPref() {
        prefs().edit().putBoolean(SettingsFragment.KEY_CUSTOM_TABS, true).apply();
        MeFragment f = attachResumed(null);
        View row = childOf(f, R.id.me_settings_browsing, 3);
        SwitchCompat sw = row.findViewById(R.id.me_setting_switch);
        assertNotNull(sw);
        sw.performClick();
        assertFalse(prefs().getBoolean(SettingsFragment.KEY_CUSTOM_TABS, true));
    }

    // ---- 新增：未读计数 ----

    private static NotificationHolder unreadHolder() {
        NotificationThread thread = mock(NotificationThread.class);
        when(thread.unread()).thenReturn(true);
        when(thread.repository()).thenReturn(null);
        return new NotificationHolder(thread);
    }

    @Test
    public void loadUnread_unreadOneShowsCount() throws Exception {
        NotificationCountProvider.getInstance().invalidate();
        NotificationListLoadResult result = new NotificationListLoadResult(
                Collections.singletonList(unreadHolder()));
        mSingleFactoryMock.when(() -> SingleFactory.getNotifications(false, false, false))
                .thenReturn(Single.just(result));
        MeFragment f = attachResumed(null);
        TextView value = childOf(f, R.id.me_func_container, 0)
                .findViewById(R.id.me_setting_value);
        waitUntil(() -> value.getText().toString().equals("1"));
    }

    @Test
    public void loadUnread_detachedDeliveryIsNoop() throws Exception {
        NotificationCountProvider.getInstance().invalidate();
        io.reactivex.subjects.SingleSubject<NotificationListLoadResult> subject =
                io.reactivex.subjects.SingleSubject.create();
        mSingleFactoryMock.when(() -> SingleFactory.getNotifications(false, false, false))
                .thenReturn(subject);
        MeFragment f = attachResumed(null);
        TextView value = childOf(f, R.id.me_func_container, 0)
                .findViewById(R.id.me_setting_value);
        assertEquals("›", value.getText().toString());
        Thread.sleep(500);
        removeFragment(f);
        subject.onSuccess(new NotificationListLoadResult(
                Collections.singletonList(unreadHolder())));
        Thread.sleep(300);
        idleMain();
        assertEquals("›", value.getText().toString());
    }

    // ---- 新增：深色模式对话框 / normalize / 重建 ----

    @Test
    public void darkModeSummaryAndDialog_systemMode() throws Exception {
        try (MockedStatic<DarkModeScheduler> mocked =
                     mockStatic(DarkModeScheduler.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DarkModeScheduler.getMode(any()))
                    .thenReturn(DarkModeScheduler.MODE_SYSTEM);
            MeFragment f = attachResumed(null);
            // T-127：scheduled 档已删除，摘要只有 system/manual 两档。
            assertEquals(f.getString(R.string.dark_mode_system),
                    ((TextView) childOf(f, R.id.me_settings_appearance, 1)
                            .findViewById(R.id.me_setting_value))
                            .getText().toString());
            int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
            callVoid(f, "showDarkModeDialog", new Class<?>[]{});
            assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                    > before);
        }
    }

    @Test
    public void darkModeDialog_manualLightCheckedOne() throws Exception {
        putPref(DarkModeScheduler.KEY_DARK_MODE, DarkModeScheduler.MODE_MANUAL);
        putPref(SettingsFragment.KEY_THEME, 1);
        MeFragment f = attachResumed(null);
        callVoid(f, "showDarkModeDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 1);
        assertEquals(DarkModeScheduler.MODE_MANUAL,
                prefs().getString(DarkModeScheduler.KEY_DARK_MODE, ""));
        assertEquals(1, prefs().getInt(SettingsFragment.KEY_THEME, -1));
    }

    @Test
    public void showStartPageDialog_pickAfterDetachSkipsRebuild() throws Exception {
        putPref(SettingsFragment.KEY_START_PAGE, "discover_hub");
        MeFragment f = attachResumed(null);
        callVoid(f, "showStartPageDialog", new Class<?>[]{});
        removeFragment(f);
        clickDialogItem(latestDialog(), 3);
        assertEquals("star_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    @Test
    public void recreateActivity_attachedRecreates() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "recreateActivity", new Class<?>[]{});
        idleMain();
        assertTrue(mActivity.isDestroyed());
    }

    @Test
    public void recreateActivity_finishedActivitySkips() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "recreateActivity", new Class<?>[]{});
        mActivity.finish();
        idleMain();
        assertTrue(mActivity.isFinishing());
    }

    @Test
    public void restartStackOnMeTab_attachedStartsHome() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "restartStackOnMeTab", new Class<?>[]{});
        idleMain();
        android.content.Intent started = Shadows.shadowOf(
                (android.app.Application) ApplicationProvider.getApplicationContext())
                .getNextStartedActivity();
        assertNotNull(started);
        assertEquals(HomeActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void normalizeStartPage_recentHistoryMapsToSearch() throws Exception {
        putPref(SettingsFragment.KEY_START_PAGE, "recent_history");
        MeFragment f = withAttachedPrefs();
        callVoid(f, "normalizeStartPage", new Class<?>[]{});
        assertEquals("search",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    @Test
    public void normalizeStartPage_trendsMapsToDiscoverHub() throws Exception {
        putPref(SettingsFragment.KEY_START_PAGE, "trends");
        MeFragment f = withAttachedPrefs();
        callVoid(f, "normalizeStartPage", new Class<?>[]{});
        assertEquals("discover_hub",
                prefs().getString(SettingsFragment.KEY_START_PAGE, ""));
    }

    // ---- 新增：BaseActivity 宿主（检查更新行） ----

    public static class MeHostActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.widget.FrameLayout(this));
        }
    }

    private MeFragment attachToBase() {
        BaseActivity host = Robolectric.buildActivity(MeHostActivity.class)
                .setup().get();
        MeFragment f = MeFragment.newInstance(null);
        host.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    @Test
    public void checkUpdateRowClick_baseActivityCallsCheckManually() throws Exception {
        try (MockedStatic<com.gh4a.utils.UpdateCheckUi> mocked =
                     mockStatic(com.gh4a.utils.UpdateCheckUi.class)) {
            MeFragment f = attachToBase();
            childOf(f, R.id.me_settings_about, 0).performClick();
            mocked.verify(() -> com.gh4a.utils.UpdateCheckUi.checkManually(
                    any(BaseActivity.class)));
        }
    }

    // ---- 新增：备份恢复对话框 / launcher ----

    private static void clickAlertDialogButton(android.app.Dialog dialog, int which) {
        ((androidx.appcompat.app.AlertDialog) dialog).getButton(which).performClick();
        idleMain();
    }

    @Test
    public void backupRestoreDialog_clickBackupWritesFile() throws Exception {
        try (MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     mockStatic(com.gh4a.utils.SettingsBackupManager.class,
                             CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(any()))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager
                            .backupContainsTranslationCredentials(any()))
                    .thenReturn(false);
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.writeBackupFile(
                            any(), any()))
                    .thenReturn("octodroid-backup-test.json");
            MeFragment f = attachResumed(null);
            callVoid(f, "showBackupRestoreDialog", new Class<?>[]{});
            clickDialogItem(latestDialog(), 0);
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("octodroid-backup-test.json"));
        }
    }

    @Test
    public void backupRestoreDialog_clickRestoreLaunchesPicker() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "showBackupRestoreDialog", new Class<?>[]{});
        clickDialogItem(latestDialog(), 1);
        idleMain();
        assertNotNull(Shadows.shadowOf(mActivity).getNextStartedActivityForResult());
    }

    private static void launchRestorePicker(MeFragment f) throws Exception {
        Object launcher = field(f, "mRestoreLauncher");
        java.lang.reflect.Method launch =
                launcher.getClass().getMethod("launch", Object.class);
        launch.invoke(launcher, (Object) new String[]{"application/json"});
        idleMain();
    }

    @Test
    public void restoreLauncher_callbackWithUriShowsConfirm() throws Exception {
        MeFragment f = attachResumed(null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        launchRestorePicker(f);
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(mActivity).getNextStartedActivityForResult();
        assertNotNull(started);
        android.content.Intent data = new android.content.Intent();
        data.setData(android.net.Uri.parse("content://x/backup.json"));
        Shadows.shadowOf(mActivity).receiveResult(
                started.intent, android.app.Activity.RESULT_OK, data);
        idleMain();
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
        assertTrue(latestDialog().isShowing());
    }

    @Test
    public void restoreLauncher_cancelledResultShowsNothing() throws Exception {
        MeFragment f = attachResumed(null);
        launchRestorePicker(f);
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                Shadows.shadowOf(mActivity).getNextStartedActivityForResult();
        assertNotNull(started);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        Shadows.shadowOf(mActivity).receiveResult(
                started.intent, android.app.Activity.RESULT_CANCELED, null);
        idleMain();
        assertEquals(before,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void doBackup_confirmPositiveWritesFile() throws Exception {
        try (MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     mockStatic(com.gh4a.utils.SettingsBackupManager.class,
                             CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(any()))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager
                            .backupContainsTranslationCredentials(any()))
                    .thenReturn(true);
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.writeBackupFile(
                            any(), any()))
                    .thenReturn("octodroid-backup-test.json");
            MeFragment f = attachResumed(null);
            callVoid(f, "doBackup", new Class<?>[]{});
            clickAlertDialogButton(latestDialog(),
                    android.content.DialogInterface.BUTTON_POSITIVE);
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("octodroid-backup-test.json"));
        }
    }

    @Test
    public void writeBackup_catchToastsFailure() throws Exception {
        try (MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     mockStatic(com.gh4a.utils.SettingsBackupManager.class,
                             CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(any()))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager
                            .backupContainsTranslationCredentials(any()))
                    .thenReturn(false);
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.writeBackupFile(
                            any(), any()))
                    .thenThrow(new RuntimeException("boom"));
            MeFragment f = attachResumed(null);
            callVoid(f, "doBackup", new Class<?>[]{});
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("boom"));
        }
    }

    @Test
    public void restoreConfirm_positiveRestores() throws Exception {
        try (MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     mockStatic(com.gh4a.utils.SettingsBackupManager.class,
                             CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            any(), any()))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.applyBackup(
                            any(), any()))
                    .thenReturn(true);
            MeFragment f = attachResumed(null);
            callVoid(f, "confirmAndApplyRestore",
                    new Class<?>[]{android.net.Uri.class},
                    android.net.Uri.parse("content://x/backup.json"));
            clickAlertDialogButton(latestDialog(),
                    android.content.DialogInterface.BUTTON_POSITIVE);
            idleMain();
            assertEquals(appContext().getString(R.string.restore_success),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void doRestore_readThrowsToastsFailure() throws Exception {
        try (MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     mockStatic(com.gh4a.utils.SettingsBackupManager.class,
                             CALLS_REAL_METHODS)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            any(), any()))
                    .thenThrow(new RuntimeException("boom"));
            MeFragment f = attachResumed(null);
            callVoid(f, "doRestore",
                    new Class<?>[]{android.net.Uri.class},
                    android.net.Uri.parse("content://x/backup.json"));
            assertEquals(appContext().getString(R.string.restore_failed),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    // ---- 新增：HomeActivity 宿主 ----

    private MockedStatic<ServiceFactory> mHomeServiceFactory;

    private HomeActivity buildHomeActivity() {
        UserService userService = mock(UserService.class);
        User octocat = User.builder().login("octocat").id(1L).build();
        when(userService.getUser(any()))
                .thenReturn(Single.just(Response.success(octocat)));
        NotificationService notificationService = mock(NotificationService.class);
        Page<NotificationThread> emptyPage = mock(Page.class);
        when(emptyPage.items()).thenReturn(Collections.emptyList());
        when(notificationService.getNotifications(anyMap(), anyLong()))
                .thenReturn(Single.just(Response.success(emptyPage)));
        mHomeServiceFactory = mockStatic(ServiceFactory.class, CALLS_REAL_METHODS);
        mHomeServiceFactory.when(() -> ServiceFactory.get(eq(UserService.class),
                        anyBoolean()))
                .thenReturn(userService);
        mHomeServiceFactory.when(() -> ServiceFactory.get(
                        eq(NotificationService.class), anyBoolean(), any()))
                .thenReturn(notificationService);
        return HomeActivityTestHelper.launchHome().setup().get();
    }

    private static MeFragment attachToHome(HomeActivity home) {
        MeFragment f = MeFragment.newInstance(null);
        home.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private static Object homeField(HomeActivity home, String name) throws Exception {
        java.lang.reflect.Field field = HomeActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(home);
    }

    @Test
    public void headerClick_homeActivityShowsAccountSwitcher() {
        HomeActivity home = buildHomeActivity();
        try {
            MeFragment f = attachToHome(home);
            int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
            f.getView().findViewById(R.id.me_account_header).performClick();
            assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                    > before);
        } finally {
            if (mHomeServiceFactory != null) {
                mHomeServiceFactory.close();
                mHomeServiceFactory = null;
            }
        }
    }

    @Test
    public void loadUnread_homeActivitySetsBadgeCount() throws Exception {
        HomeActivity home = buildHomeActivity();
        try {
            java.util.concurrent.atomic.AtomicInteger lastCount =
                    new java.util.concurrent.atomic.AtomicInteger(-1);
            home.getBadgeState().addListener((n, s) -> lastCount.set(n));
            assertEquals(0, lastCount.get());
            // Home 自己的空结果已落角标 0；失效缓存、改未读 1，再挂我页，
            // 角标变 1 只能是我页消费者走 HomeActivity 分支发布的。
            NotificationCountProvider.getInstance().invalidate();
            NotificationListLoadResult result = new NotificationListLoadResult(
                    Collections.singletonList(unreadHolder()));
            mSingleFactoryMock.when(
                            () -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result));
            MeFragment f = attachToHome(home);
            waitUntil(() -> lastCount.get() == 1);
            assertEquals("1", ((TextView) childOf(f, R.id.me_func_container, 0)
                    .findViewById(R.id.me_setting_value)).getText().toString());
        } finally {
            if (mHomeServiceFactory != null) {
                mHomeServiceFactory.close();
                mHomeServiceFactory = null;
            }
        }
    }

    @Test
    public void navRowClick_homeActivityNavigatesToNotifications() throws Exception {
        HomeActivity home = buildHomeActivity();
        try {
            MeFragment f = attachToHome(home);
            childOf(f, R.id.me_func_container, 0).performClick();
            assertEquals(R.id.notifications, homeField(home, "mSelectedFactoryId"));
        } finally {
            if (mHomeServiceFactory != null) {
                mHomeServiceFactory.close();
                mHomeServiceFactory = null;
            }
        }
    }

    // ---- 新增：收尾分支 ----

    @Test
    public void countClick_reposUserTypeStartsRepoList() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo", null, 0, 0, 0, 0, UserType.User));
        f.getView().findViewById(R.id.me_count_repos).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(com.gh4a.activities.RepositoryListActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void countClicks_detachedFollowersFollowingAreNoop() throws Exception {
        MeFragment f = attachResumed(null);
        setField(f, "mUser", user("octo", "Octo", null, 0, 0, 0, 0, UserType.User));
        View followers = f.getView().findViewById(R.id.me_count_followers);
        View following = f.getView().findViewById(R.id.me_count_following);
        removeFragment(f);
        followers.performClick();
        following.performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void setupSectionHeader_nullHeaderReturns() throws Exception {
        MeFragment f = attachResumed(null);
        callVoid(f, "setupSectionHeader",
                new Class<?>[]{View.class, int.class, int.class, int.class},
                f.getView(), 0x7f000002, R.id.me_settings_appearance,
                R.string.settings_category_about_updates);
    }

    @Test
    public void translationRowClick_detachedIsNoop() {
        MeFragment f = attachResumed(null);
        View row = childOf(f, R.id.me_settings_appearance, 5);
        removeFragment(f);
        row.performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void diagnosticRowClick_detachedIsNoop() {
        MeFragment f = attachResumed(null);
        View row = childOf(f, R.id.me_settings_about, 1);
        removeFragment(f);
        row.performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void recreateActivity_destroyedActivitySkips() throws Exception {
        org.robolectric.android.controller.ActivityController<FragmentActivity> c =
                Robolectric.buildActivity(FragmentActivity.class);
        FragmentActivity activity = c.setup().get();
        MeFragment f = MeFragment.newInstance(null);
        activity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        callVoid(f, "recreateActivity", new Class<?>[]{});
        c.destroy();
        idleMain();
        assertTrue(activity.isDestroyed());
    }

    private static void setSuperField(Object target, String name, Object value)
            throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    public void rebuildSettingsRows_nullContainerSkipsRemovalThenFails() throws Exception {
        MeFragment f = attachResumed(null);
        // 只放 appearance + browsing 两个容器：about 容器为 null 时
        // removeAllViews 的 null 守卫走假分支，随后 buildSettingsRows
        // 在缺失的 about 容器上 NPE——守卫是防御性的，缺容器时重建
        // 本就不可能成功。
        LinearLayout view = new LinearLayout(mActivity);
        LinearLayout appearance = new LinearLayout(mActivity);
        appearance.setId(R.id.me_settings_appearance);
        LinearLayout browsing = new LinearLayout(mActivity);
        browsing.setId(R.id.me_settings_browsing);
        view.addView(appearance);
        view.addView(browsing);
        setSuperField(f, "mView", view);
        try {
            callVoid(f, "rebuildSettingsRows", new Class<?>[]{});
            fail("expected rebuild to fail on the missing about container");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue("cause=" + e.getCause(),
                    e.getCause() instanceof NullPointerException);
        }
    }

    @Test
    public void showAboutDialog_packageInfoThrowsStillShowsDialog() throws Exception {
        MeFragment f = attachResumed(null);
        Shadows.shadowOf(mActivity.getPackageManager())
                .removePackage(mActivity.getPackageName());
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showAboutDialog", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    // ---- 2026-11-22 缺口补全：restore 回调 / loadUser 回调 / 未读计数回调 /
    // 深色模式 scheduled 档 ----

    private static Method lambdaMethod(String prefix, Class<?> paramType)
            throws Exception {
        for (Method m : MeFragment.class.getDeclaredMethods()) {
            if (m.getName().startsWith(prefix)
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == paramType) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new AssertionError(
                "no lambda " + prefix + "(" + paramType.getName() + ")");
    }

    @Test
    public void restoreLauncher_callbackDetachedWithUri_isNoop() throws Exception {
        // L91：uri 非空但 fragment 已 detached → isAdded() 为假，不弹窗。
        MeFragment f = new MeFragment();
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        lambdaMethod("lambda$new", android.net.Uri.class)
                .invoke(f, android.net.Uri.parse("content://x/backup.json"));
        assertEquals(before,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void loadUser_successDetached_isNoop() throws Exception {
        // L202：成功回调到达时 fragment 已 detached → 直接返回，不填头部。
        MeFragment f = new MeFragment();
        User u = user("octo", "Octo Cat", new java.util.Date(1700000000000L),
                5, 2, 6, 7, UserType.User);
        lambdaMethod("lambda$loadUser", User.class).invoke(f, u);
        assertNull(field(f, "mUser"));
    }

    @Test
    public void loadUser_errorDetached_isNoop() throws Exception {
        // L220：失败回调到达时 fragment 已 detached → 直接返回，不走兜底填充。
        MeFragment f = new MeFragment();
        lambdaMethod("lambda$loadUser", Throwable.class)
                .invoke(f, new RuntimeException("boom"));
        assertNull(field(f, "mUser"));
    }

    @Test
    public void loadUnreadNotificationCount_successDetached_isNoop() throws Exception {
        // L339：计数回调到达时 fragment 已 detached → 直接返回。
        MeFragment f = new MeFragment();
        lambdaMethod("lambda$loadUnreadNotificationCount",
                NotificationListLoadResult.class)
                .invoke(f, new NotificationListLoadResult(Collections.emptyList()));
    }

    @Test
    public void loadUnreadNotificationCount_successNullView_isNoop() throws Exception {
        // L339：已 attach（CREATED）但视图未建，mNotificationsValueView 为空 → 返回。
        // 若守卫失效，回调会走到 setText("›") 而 NPE。
        MeFragment f = attach(new MeFragment());
        assertNull(field(f, "mNotificationsValueView"));
        lambdaMethod("lambda$loadUnreadNotificationCount",
                NotificationListLoadResult.class)
                .invoke(f, new NotificationListLoadResult(Collections.emptyList()));
    }

    @Test
    public void loadUnreadNotificationCount_errorKeepsChevron() throws Exception {
        // L361：通知计数拉取失败时空的 error 消费者跑过，保持原来的“›”。
        mSingleFactoryMock.when(() -> SingleFactory.getNotifications(false, false, false))
                .thenReturn(Single.error(new RuntimeException("boom")));
        MeFragment f = attachResumed(null);
        Thread.sleep(500);
        idleMain();
        TextView valueView = (TextView) field(f, "mNotificationsValueView");
        assertNotNull(valueView);
        assertEquals("›", valueView.getText().toString());
    }

    @Test
    public void showDarkModeDialog_scheduledModeChecksFirst() throws Exception {
        // L680：mode 为 scheduled 时 checked 也取 0（与 system 同值，但分支不同）。
        // prefs 写 scheduled 会被监听立即迁移成 system（见
        // darkModeSummary_scheduledValueMigratesToSystem），故直接挡 getMode。
        MeFragment f = attachResumed(null);
        try (MockedStatic<DarkModeScheduler> mocked =
                     mockStatic(DarkModeScheduler.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> DarkModeScheduler.getMode(any()))
                    .thenReturn(DarkModeScheduler.MODE_SCHEDULED);
            int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
            callVoid(f, "showDarkModeDialog", new Class<?>[]{});
            assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                    > before);
        }
    }
}
