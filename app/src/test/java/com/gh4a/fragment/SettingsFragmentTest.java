package com.gh4a.fragment;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** SettingsFragment：设置页（Robolectric；偏好屏在 CREATED 态挂载后加载）。 */
@RunWith(RobolectricTestRunner.class)
public class SettingsFragmentTest {

    /** 实现 OnStateChangeListener 的宿主 Activity（onAttach 强制要求）。 */
    public static class TestActivity extends FragmentActivity
            implements SettingsFragment.OnStateChangeListener {
        int themeChangedCalls;
        int fontScaleChangedCalls;

        @Override
        public void onThemeChanged() {
            themeChangedCalls++;
        }

        @Override
        public void onFontScaleChanged() {
            fontScaleChangedCalls++;
        }
    }

    private TestActivity mActivity;
    private Context mContext;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(TestActivity.class).setup().get();
        mContext = ApplicationProvider.getApplicationContext();
    }

    private SettingsFragment attachResumed(int xmlRes) {
        mActivity.setTheme(R.style.AppTheme);
        SettingsFragment f = SettingsFragment.newInstance(xmlRes);
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private SettingsFragment attach(int xmlRes) {
        SettingsFragment f = SettingsFragment.newInstance(xmlRes);
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return f;
    }

    private static Object field(SettingsFragment f, String name) throws Exception {
        java.lang.reflect.Field fld = SettingsFragment.class.getDeclaredField(name);
        fld.setAccessible(true);
        return fld.get(f);
    }

    private static void setField(SettingsFragment f, String name, Object value)
            throws Exception {
        java.lang.reflect.Field fld = SettingsFragment.class.getDeclaredField(name);
        fld.setAccessible(true);
        fld.set(f, value);
    }

    private static Object call(SettingsFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        java.lang.reflect.Method m = SettingsFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(f, args);
    }

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static androidx.appcompat.app.AlertDialog latestDialog() {
        java.util.List<android.app.Dialog> shown =
                org.robolectric.shadows.ShadowDialog.getShownDialogs();
        return (androidx.appcompat.app.AlertDialog) shown.get(shown.size() - 1);
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

    private static void prefs(String key, Object value) {
        android.content.SharedPreferences.Editor e = ApplicationProvider
                .getApplicationContext().getSharedPreferences(
                        SettingsFragment.PREF_NAME, Context.MODE_PRIVATE).edit();
        if (value instanceof String) e.putString(key, (String) value);
        else if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) e.putInt(key, (Integer) value);
        e.apply();
    }

    @Test
    public void onAttach_nonListenerActivityThrows() {
        // 123 行：宿主未实现 OnStateChangeListener 时 onAttach 抛错
        FragmentActivity plain =
                Robolectric.buildActivity(FragmentActivity.class).setup().get();
        SettingsFragment f = new SettingsFragment();
        try {
            plain.getSupportFragmentManager().beginTransaction().add(f, "test")
                    .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
            org.junit.Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("OnStateChangeListener"));
        }
    }

    @Test
    public void onResume_mirrorPresetSyncsWhenPrefsChanged() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        // 偏好屏绑定的内存值是旧地址；直接改 prefs 造成不一致，onResume 应同步
        androidx.preference.Preference pref =
                f.findPreference(SettingsFragment.KEY_MIRROR_PRESET);
        assertNotNull(pref);
        String oldValue = ((androidx.preference.ListPreference) pref).getValue();
        String other = "https://gh-proxy.com".equals(oldValue)
                ? "https://mirror.example.com" : "https://gh-proxy.com";
        prefs(SettingsFragment.KEY_MIRROR_PRESET, other);
        f.onResume();
        assertEquals(other, ((androidx.preference.ListPreference) pref).getValue());
    }

    @Test
    public void onResume_translationXmlNullMirrorPrefIsNoop() {
        SettingsFragment f = attach(R.xml.translation_settings);
        assertNotNull(f.getPreferenceScreen());
        f.onResume();
    }

    @Test
    public void onCreatePreferences_translationXmlNullPrefsAreSkipped()
            throws Exception {
        // 该 xml 没有大多数字段：各 findPreference 均为 null，早退且不崩
        SettingsFragment f = attach(R.xml.translation_settings);
        assertNotNull(f.getPreferenceScreen());
        call(f, "refreshMirrorPresetSummary", new Class<?>[]{});
    }

    @Test
    public void onPreferenceChange_fontScaleNotifiesListener() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference(SettingsFragment.KEY_FONT_SCALE);
        assertNotNull(pref);
        assertTrue(f.onPreferenceChange(pref, "1.2"));
        assertEquals(1, mActivity.fontScaleChangedCalls);
    }

    @Test
    public void onPreferenceChange_notificationsOnSchedules() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.ListPreference interval =
                f.findPreference(SettingsFragment.KEY_NOTIFICATION_INTERVAL);
        assertNotNull(interval);
        interval.setValue("60");
        Preference pref = f.findPreference(SettingsFragment.KEY_NOTIFICATIONS);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.NotificationsWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.NotificationsWorker.class);
             org.mockito.MockedStatic<com.gh4a.worker.NotificationPermission> perm =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.NotificationPermission.class)) {
            assertTrue(f.onPreferenceChange(pref, true));
            worker.verify(() -> com.gh4a.worker.NotificationsWorker
                    .createNotificationChannels(
                            org.mockito.ArgumentMatchers.any()));
            worker.verify(() -> com.gh4a.worker.NotificationsWorker.schedule(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(60)));
            perm.verify(() -> com.gh4a.worker.NotificationPermission.requestIfNeeded(
                    org.mockito.ArgumentMatchers.any()));
        }
    }

    @Test
    public void onPreferenceChange_notificationsOffCancels() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference(SettingsFragment.KEY_NOTIFICATIONS);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.NotificationsWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.NotificationsWorker.class)) {
            assertTrue(f.onPreferenceChange(pref, false));
            worker.verify(() -> com.gh4a.worker.NotificationsWorker.cancel(
                    org.mockito.ArgumentMatchers.any()));
        }
    }

    @Test
    public void onPreferenceChange_notificationIntervalReschedulesWhenChecked() {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.TwoStatePreference notif =
                f.findPreference(SettingsFragment.KEY_NOTIFICATIONS);
        notif.setChecked(true);
        Preference pref = f.findPreference(SettingsFragment.KEY_NOTIFICATION_INTERVAL);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.NotificationsWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.NotificationsWorker.class)) {
            assertTrue(f.onPreferenceChange(pref, "120"));
            worker.verify(() -> com.gh4a.worker.NotificationsWorker.schedule(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(120)));
        }
    }

    @Test
    public void onPreferenceChange_notificationIntervalSkipsWhenUnchecked() {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.TwoStatePreference notif =
                f.findPreference(SettingsFragment.KEY_NOTIFICATIONS);
        notif.setChecked(false);
        Preference pref = f.findPreference(SettingsFragment.KEY_NOTIFICATION_INTERVAL);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.NotificationsWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.NotificationsWorker.class)) {
            assertTrue(f.onPreferenceChange(pref, "120"));
            worker.verifyNoInteractions();
        }
    }

    @Test
    public void onPreferenceChange_releaseRadarOnSchedules() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.ListPreference interval =
                f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL);
        assertNotNull(interval);
        interval.setValue("720");
        Preference pref = f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.ReleaseRadarWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.ReleaseRadarWorker.class);
             org.mockito.MockedStatic<com.gh4a.worker.NotificationPermission> perm =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.NotificationPermission.class)) {
            assertTrue(f.onPreferenceChange(pref, true));
            worker.verify(() -> com.gh4a.worker.ReleaseRadarWorker
                    .createNotificationChannels(
                            org.mockito.ArgumentMatchers.any()));
            worker.verify(() -> com.gh4a.worker.ReleaseRadarWorker.schedule(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(720)));
            perm.verify(() -> com.gh4a.worker.NotificationPermission.requestIfNeeded(
                    org.mockito.ArgumentMatchers.any()));
        }
    }

    @Test
    public void onPreferenceChange_releaseRadarOffCancels() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.ReleaseRadarWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.ReleaseRadarWorker.class)) {
            assertTrue(f.onPreferenceChange(pref, false));
            worker.verify(() -> com.gh4a.worker.ReleaseRadarWorker.cancel(
                    org.mockito.ArgumentMatchers.any()));
        }
    }

    @Test
    public void onPreferenceChange_releaseRadarIntervalReschedulesWhenChecked() {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.TwoStatePreference radar =
                f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS);
        radar.setChecked(true);
        Preference pref = f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.ReleaseRadarWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.ReleaseRadarWorker.class)) {
            assertTrue(f.onPreferenceChange(pref, "360"));
            worker.verify(() -> com.gh4a.worker.ReleaseRadarWorker.schedule(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(360)));
        }
    }

    @Test
    public void onPreferenceChange_releaseRadarIntervalSkipsWhenUnchecked() {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.TwoStatePreference radar =
                f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_NOTIFICATIONS);
        radar.setChecked(false);
        Preference pref = f.findPreference(SettingsFragment.KEY_RELEASE_RADAR_INTERVAL);
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.worker.ReleaseRadarWorker> worker =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.worker.ReleaseRadarWorker.class)) {
            assertTrue(f.onPreferenceChange(pref, "360"));
            worker.verifyNoInteractions();
        }
    }

    @Test
    public void onPreferenceChange_darkModeAppliesScheduler() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = mock(Preference.class);
        when(pref.getKey()).thenReturn("dark_mode");
        try (org.mockito.MockedStatic<com.gh4a.utils.DarkModeScheduler> sched =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.DarkModeScheduler.class)) {
            assertTrue(f.onPreferenceChange(pref, "scheduled"));
            sched.verify(() -> com.gh4a.utils.DarkModeScheduler.apply(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq("scheduled")));
            assertEquals(1, mActivity.themeChangedCalls);
        }
    }

    @Test
    public void newInstance_storesXmlRes() {
        SettingsFragment f = SettingsFragment.newInstance(R.xml.settings);
        assertEquals(R.xml.settings, f.getArguments().getInt("xml_res"));
    }

    @Test
    public void isAutoCheckUpdateEnabled_defaultTrue() {
        assertTrue(SettingsFragment.isAutoCheckUpdateEnabled(mContext));
    }

    @Test
    public void isAutoCheckUpdateEnabled_falseWhenDisabled() {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(SettingsFragment.KEY_AUTO_CHECK_UPDATE, false).apply();
        assertFalse(SettingsFragment.isAutoCheckUpdateEnabled(mContext));
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(SettingsFragment.KEY_AUTO_CHECK_UPDATE, true).apply();
        assertTrue(SettingsFragment.isAutoCheckUpdateEnabled(mContext));
    }

    @Test
    public void applyLanguage_emptyAndNullDoNotThrow() {
        SettingsFragment.applyLanguage("");
        SettingsFragment.applyLanguage(null);
        SettingsFragment.applyLanguage("en");
        SettingsFragment.applyLanguage("zh-CN");
    }

    @Test
    public void onCreatePreferences_loadsPreferenceScreen() {
        SettingsFragment f = attach(R.xml.settings);
        assertNotNull(f.getPreferenceScreen());
        // settings.xml 里没有 "theme" 键（主题在别处管），用实际存在的键断言。
        assertNotNull(f.findPreference(SettingsFragment.KEY_ACCENT_COLOR));
        assertNotNull(f.findPreference(SettingsFragment.KEY_LANGUAGE));
        assertNotNull(f.findPreference(SettingsFragment.KEY_AUTO_CHECK_UPDATE));
    }

    @Test
    public void onCreatePreferences_repairsMistypedLanguagePref() {
        // 错型值（非 String）会让 ListPreference 绑定抛 ClassCastException；
        // onCreatePreferences 前先清掉，设置页能正常打开。
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putInt(SettingsFragment.KEY_LANGUAGE, 42).apply();
        SettingsFragment f = attach(R.xml.settings);
        assertNotNull(f.getPreferenceScreen());
        // 错型 int 已被清掉；ListPreference 绑定时可能按默认值重新持久化一个
        // String，故不断言键不存在，只断言残留值不再是错型的 Integer。
        Object raw = mContext.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .getAll().get(SettingsFragment.KEY_LANGUAGE);
        assertFalse(raw instanceof Integer);
    }

    @Test
    public void onPreferenceChange_accentColorNotifiesListener() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = mock(Preference.class);
        when(pref.getKey()).thenReturn(SettingsFragment.KEY_ACCENT_COLOR);
        assertTrue(f.onPreferenceChange(pref, "#00ff00"));
        assertEquals(1, mActivity.themeChangedCalls);
    }

    @Test
    public void onPreferenceChange_languageAppliesAndNotifiesListener() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = mock(Preference.class);
        when(pref.getKey()).thenReturn(SettingsFragment.KEY_LANGUAGE);
        assertTrue(f.onPreferenceChange(pref, "en"));
        assertEquals(1, mActivity.themeChangedCalls);
    }

    @Test
    public void onPreferenceChange_unknownKeyReturnsFalse() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = mock(Preference.class);
        when(pref.getKey()).thenReturn("some_unknown_key");
        assertFalse(f.onPreferenceChange(pref, "x"));
        assertEquals(0, mActivity.themeChangedCalls);
    }

    @Test
    public void aboutDialogFragment_newInstanceStoresArgs() {
        SettingsFragment.AboutDialogFragment f =
                SettingsFragment.AboutDialogFragment.newInstance("About", true);
        assertEquals("About", f.getArguments().getString("title"));
        assertTrue(f.getArguments().getBoolean("loggedIn"));
        SettingsFragment.AboutDialogFragment f2 =
                SettingsFragment.AboutDialogFragment.newInstance("About", false);
        assertFalse(f2.getArguments().getBoolean("loggedIn"));
    }

    @Test
    public void openSourceComponentAdapter_countMatchesStaticList() throws Exception {
        // OpenSourceComponentAdapter 是私有内部类：反射构造与调用。
        Object adapter = openSourceAdapter();
        java.lang.reflect.Method count =
                adapter.getClass().getMethod("getItemCount");
        assertTrue((Integer) count.invoke(adapter) > 0);
    }

    // 注：onCreateViewHolder/onBindViewHolder 不测——布局
    // open_source_component_item 在 Robolectric 默认主题下 inflate 失败
    //（line #11 缺 layout_width，主题解析限制），与生产代码无关。
    // getItemCount 已覆盖静态组件表。

    @Test
    public void showMirrorSourcePage_startsActivity() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        call(f, "showMirrorSourcePage", new Class<?>[]{});
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.MirrorSourceActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void showMirrorSourcePage_detachedIsNoop() throws Exception {
        // 378-379 行：未挂载时直接返回
        SettingsFragment f = new SettingsFragment();
        call(f, "showMirrorSourcePage", new Class<?>[]{});
    }

    @Test
    public void refreshMirrorPresetSummary_showsHost() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.ListPreference pref =
                f.findPreference(SettingsFragment.KEY_MIRROR_PRESET);
        assertNotNull(pref);
        pref.setValue("https://gh-proxy.com/some/path");
        call(f, "refreshMirrorPresetSummary", new Class<?>[]{});
        assertTrue(pref.getSummary().toString().contains("gh-proxy.com"));
    }

    @Test
    public void refreshMirrorPresetSummary_emptyValueClearsSummary() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.ListPreference pref =
                f.findPreference(SettingsFragment.KEY_MIRROR_PRESET);
        assertNotNull(pref);
        pref.setValue("");
        call(f, "refreshMirrorPresetSummary", new Class<?>[]{});
        assertEquals("", pref.getSummary().toString());
    }

    @Test
    public void refreshMirrorPresetSummary_nullValueClearsSummary() throws Exception {
        // L363：v == null 真臂——直接 setValue(null) 驱动（新鲜挂载拿不到
        // null：同一 JVM 内别的测试已把值持久化进 SharedPreferences，
        // ListPreference 绑定时会读回它）。与空串臂互补。
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.ListPreference pref =
                f.findPreference(SettingsFragment.KEY_MIRROR_PRESET);
        assertNotNull(pref);
        pref.setValue(null);
        assertNull(pref.getValue());
        call(f, "refreshMirrorPresetSummary", new Class<?>[]{});
        assertEquals("", pref.getSummary().toString());
    }

    @Test
    public void mirrorPresetPref_clickOpensMirrorPage() {
        SettingsFragment f = attach(R.xml.settings);
        androidx.preference.Preference pref =
                f.findPreference(SettingsFragment.KEY_MIRROR_PRESET);
        assertNotNull(pref);
        pref.performClick();
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.MirrorSourceActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void showBackupRestoreDialog_backupItemWritesFile() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
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
            call(f, "showBackupRestoreDialog", new Class<?>[]{});
            androidx.appcompat.app.AlertDialog dialog = latestDialog();
            android.widget.ListView listView =
                    dialog.findViewById(androidx.appcompat.R.id.select_dialog_listview);
            assertNotNull(listView);
            listView.performItemClick(listView.getAdapter().getView(0, null, listView),
                    0, 0);
            idle();
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("octodroid-backup-test.json"));
        }
    }

    @Test
    public void showBackupRestoreDialog_restoreItemLaunchesPicker() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        call(f, "showBackupRestoreDialog", new Class<?>[]{});
        androidx.appcompat.app.AlertDialog dialog = latestDialog();
        android.widget.ListView listView = dialog.findViewById(androidx.appcompat.R.id.select_dialog_listview);
        assertNotNull(listView);
        listView.performItemClick(listView.getAdapter().getView(1, null, listView),
                1, 1);
        idle();
        // 恢复入口已发起（无结果返回，不崩即可）
        assertNotNull(org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivityForResult());
    }

    @Test
    public void doBackup_collectThrowsShowsFailedToast() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        try (org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.collectBackup(
                            org.mockito.ArgumentMatchers.any(Context.class)))
                    .thenThrow(new RuntimeException("boom"));
            call(f, "doBackup", new Class<?>[]{});
            idle();
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("boom"));
        }
    }

    @Test
    public void doBackup_withCredentialsShowsConfirmDialog() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
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
                    .thenReturn(true);
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.writeBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn("octodroid-backup-test.json");
            call(f, "doBackup", new Class<?>[]{});
            androidx.appcompat.app.AlertDialog confirm = latestDialog();
            assertNotNull(confirm);
            confirm.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .performClick();
            idle();
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("octodroid-backup-test.json"));
        }
    }

    @Test
    public void doBackup_writeThrowsShowsFailedToast() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
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
                    .thenThrow(new java.io.IOException("disk full"));
            call(f, "doBackup", new Class<?>[]{});
            idle();
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
                    .contains("disk full"));
        }
    }

    @Test
    public void confirmAndApplyRestore_positiveRestores() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        android.net.Uri uri = android.net.Uri.parse("content://com.example/backup.json");
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class);
             org.mockito.MockedStatic<com.gh4a.utils.DarkModeScheduler> sched =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.DarkModeScheduler.class)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.eq(uri)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.applyBackup(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn(true);
            call(f, "confirmAndApplyRestore",
                    new Class<?>[]{android.net.Uri.class}, uri);
            androidx.appcompat.app.AlertDialog dialog = latestDialog();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .performClick();
            idle();
            assertEquals(mContext.getString(R.string.restore_success),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertEquals(1, mActivity.themeChangedCalls);
        }
    }

    @Test
    public void confirmAndApplyRestore_negativeIsNoop() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        android.net.Uri uri = android.net.Uri.parse("content://com.example/backup.json");
        try (org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class)) {
            call(f, "confirmAndApplyRestore",
                    new Class<?>[]{android.net.Uri.class}, uri);
            androidx.appcompat.app.AlertDialog dialog = latestDialog();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                    .performClick();
            idle();
            mgr.verifyNoInteractions();
        }
    }

    @Test
    public void doRestore_applyFailsShowsFailedToast() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        android.net.Uri uri = android.net.Uri.parse("content://com.example/backup.json");
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.eq(uri)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.applyBackup(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn(false);
            call(f, "doRestore", new Class<?>[]{android.net.Uri.class}, uri);
            idle();
            assertEquals(mContext.getString(R.string.restore_failed),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertEquals(0, mActivity.themeChangedCalls);
        }
    }

    @Test
    public void doRestore_readThrowsShowsFailedToast() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        android.net.Uri uri = android.net.Uri.parse("content://com.example/backup.json");
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.eq(uri)))
                    .thenThrow(new java.io.IOException("no file"));
            call(f, "doRestore", new Class<?>[]{android.net.Uri.class}, uri);
            idle();
            assertEquals(mContext.getString(R.string.restore_failed),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        }
    }

    private void deliverActivityResult(
            org.robolectric.shadows.ShadowActivity.IntentForResult started,
            int resultCode, android.content.Intent data) {
        org.robolectric.Shadows.shadowOf(mActivity)
                .receiveResult(started.intent, resultCode, data);
    }

    @Test
    public void restoreLauncher_callbackWithUriShowsConfirm() throws Exception {
        // 114-115 行：文档选择器返回 uri 且 fragment 仍挂载时弹恢复确认框
        // 挂载须到 RESUMED：ActivityResult 回调在 STARTED 态才向 registry 注册
        SettingsFragment f = attachResumed(R.xml.settings);
        Object launcher = field(f, "mRestoreLauncher");
        java.lang.reflect.Method launch = launcher.getClass()
                .getMethod("launch", Object.class);
        launch.invoke(launcher, (Object) new String[]{"application/json"});
        idle();
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                org.robolectric.Shadows.shadowOf(mActivity)
                        .getNextStartedActivityForResult();
        assertNotNull(started);
        android.content.Intent data = new android.content.Intent();
        data.setData(android.net.Uri.parse("content://com.example/backup.json"));
        deliverActivityResult(started, android.app.Activity.RESULT_OK, data);
        idle();
        androidx.appcompat.app.AlertDialog dialog = latestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
    }

    @Test
    public void restoreLauncher_cancelledResultShowsNothing() throws Exception {
        // 114 行：选择器取消（uri 为 null）时不弹确认框
        SettingsFragment f = attachResumed(R.xml.settings);
        Object launcher = field(f, "mRestoreLauncher");
        java.lang.reflect.Method launch = launcher.getClass()
                .getMethod("launch", Object.class);
        launch.invoke(launcher, (Object) new String[]{"application/json"});
        idle();
        org.robolectric.shadows.ShadowActivity.IntentForResult started =
                org.robolectric.Shadows.shadowOf(mActivity)
                        .getNextStartedActivityForResult();
        assertNotNull(started);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        deliverActivityResult(started, android.app.Activity.RESULT_CANCELED, null);
        idle();
        assertEquals(before,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void onPreferenceClick_aboutShowsDialogLoggedOut() {
        SettingsFragment f = attach(R.xml.settings);
        Preference about = f.findPreference("about");
        assertNotNull(about);
        assertTrue(f.onPreferenceClick(about));
        f.getChildFragmentManager().executePendingTransactions();
        assertNotNull(f.getChildFragmentManager().findFragmentByTag("about"));
    }

    @Test
    public void aboutDialog_loggedOutHidesIssueButton() {
        // 对话框的创建在子 Fragment 的 onCreate 里：父 Fragment 须到 RESUMED 态
        SettingsFragment f = attachResumed(R.xml.settings);
        Preference about = f.findPreference("about");
        assertTrue(f.onPreferenceClick(about));
        f.getChildFragmentManager().executePendingTransactions();
        SettingsFragment.AboutDialogFragment dialogFragment =
                (SettingsFragment.AboutDialogFragment)
                        f.getChildFragmentManager().findFragmentByTag("about");
        assertNotNull(dialogFragment);
        android.app.Dialog dialog = dialogFragment.getDialog();
        assertNotNull(dialog);
        assertEquals(android.view.View.GONE,
                dialog.findViewById(R.id.btn_by_gh4a).getVisibility());
    }

    @Test
    public void aboutDialog_clickEmailStartsChooser() {
        SettingsFragment f = attachResumed(R.xml.settings);
        Preference about = f.findPreference("about");
        assertTrue(f.onPreferenceClick(about));
        f.getChildFragmentManager().executePendingTransactions();
        SettingsFragment.AboutDialogFragment dialogFragment =
                (SettingsFragment.AboutDialogFragment)
                        f.getChildFragmentManager().findFragmentByTag("about");
        android.app.Dialog dialog = dialogFragment.getDialog();
        dialog.findViewById(R.id.btn_by_email).performClick();
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(android.content.Intent.ACTION_CHOOSER, intent.getAction());
    }

    @Test
    public void aboutDialog_loggedInClicksOpenIssuesAndRepo() {
        com.gh4a.Gh4Application.get().addAccount(
                com.meisolsson.githubsdk.model.User.builder()
                        .login("octocat").id(1L).build(), "token-xyz");
        SettingsFragment f = attachResumed(R.xml.settings);
        Preference about = f.findPreference("about");
        assertTrue(f.onPreferenceClick(about));
        f.getChildFragmentManager().executePendingTransactions();
        SettingsFragment.AboutDialogFragment dialogFragment =
                (SettingsFragment.AboutDialogFragment)
                        f.getChildFragmentManager().findFragmentByTag("about");
        android.app.Dialog dialog = dialogFragment.getDialog();
        assertEquals(android.view.View.VISIBLE,
                dialog.findViewById(R.id.btn_by_gh4a).getVisibility());
        dialog.findViewById(R.id.btn_by_gh4a).performClick();
        android.content.Intent issueIntent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(issueIntent);
        assertEquals(com.gh4a.activities.IssueListActivity.class.getName(),
                issueIntent.getComponent().getClassName());
        dialog.findViewById(R.id.btn_gh4a).performClick();
        android.content.Intent repoIntent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(repoIntent);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                repoIntent.getComponent().getClassName());
    }

    @Test
    public void onPreferenceClick_openSourceShowsDialog() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference("open_source_components");
        assertNotNull(pref);
        assertTrue(f.onPreferenceClick(pref));
        f.getChildFragmentManager().executePendingTransactions();
        assertNotNull(f.getChildFragmentManager().findFragmentByTag("opensource"));
    }

    @Test
    public void openSourceDialogFragment_onCreateDialogBuildsList() {
        // 不经 show（RecyclerView 布局会触发布局 inflate）：先挂载再直接调
        // onCreateDialog，覆盖对话框创建与 adapter 装配
        SettingsFragment.OpenSourceComponentListDialogFragment frag =
                new SettingsFragment.OpenSourceComponentListDialogFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(frag, "opensource-test")
                .setMaxLifecycle(frag, Lifecycle.State.CREATED).commitNow();
        android.app.Dialog dialog = frag.onCreateDialog(null);
        assertNotNull(dialog);
        assertTrue(dialog instanceof androidx.appcompat.app.AlertDialog);
    }

    @Test
    public void openSourceAdapter_viewHolderBindsTitleAndUrl() throws Exception {
        // AppTheme 下 item 布局可 inflate（CardView 的 layout_width 由
        // CardView.DayNight 样式提供，须用带 AppTheme 的 Activity 上下文，
        // 不能用 Application 上下文）：覆盖 onCreateViewHolder/onBindViewHolder
        mActivity.setTheme(R.style.AppTheme);
        Class<?> clazz = Class.forName(
                "com.gh4a.fragment.SettingsFragment$OpenSourceComponentAdapter");
        java.lang.reflect.Constructor<?> ctor =
                clazz.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        Object adapter = ctor.newInstance(mActivity);
        androidx.recyclerview.widget.RecyclerView parent =
                new androidx.recyclerview.widget.RecyclerView(mActivity);
        parent.setLayoutManager(
                new androidx.recyclerview.widget.LinearLayoutManager(mActivity));
        java.lang.reflect.Method create = adapter.getClass().getMethod(
                "onCreateViewHolder", android.view.ViewGroup.class, int.class);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                (androidx.recyclerview.widget.RecyclerView.ViewHolder)
                        create.invoke(adapter, parent, 0);
        java.lang.reflect.Method bind = adapter.getClass().getMethod(
                "onBindViewHolder",
                androidx.recyclerview.widget.RecyclerView.ViewHolder.class, int.class);
        bind.invoke(adapter, holder, 0);
        assertEquals("android-gif-drawable",
                ((android.widget.TextView) holder.itemView.findViewById(R.id.title))
                        .getText().toString());
        assertEquals("https://github.com/koral--/android-gif-drawable",
                ((android.widget.TextView) holder.itemView.findViewById(R.id.url))
                        .getText().toString());
    }

    @Test
    public void onPreferenceClick_customizeDrawerStartsActivity() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference("customize_drawer");
        assertNotNull(pref);
        assertTrue(f.onPreferenceClick(pref));
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.DrawerEditActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void onPreferenceClick_accountManageStartsActivity() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference("account_manage");
        assertNotNull(pref);
        assertTrue(f.onPreferenceClick(pref));
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.AccountManageActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void onPreferenceClick_translationSettingsStartsActivity() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference("translation_settings");
        assertNotNull(pref);
        assertTrue(f.onPreferenceClick(pref));
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.TranslationSettingsActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void onPreferenceClick_backupRestoreShowsDialog() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference("backup_restore");
        assertNotNull(pref);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        assertTrue(f.onPreferenceClick(pref));
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onPreferenceClick_downloadManagerStartsActivity() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = f.findPreference("download_manager");
        assertNotNull(pref);
        assertTrue(f.onPreferenceClick(pref));
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.DownloadListActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void onPreferenceClick_unknownPrefReturnsFalse() {
        SettingsFragment f = attach(R.xml.settings);
        Preference pref = mock(Preference.class);
        when(pref.getKey()).thenReturn("some_unknown_key");
        assertFalse(f.onPreferenceClick(pref));
    }

    @Test
    public void onPreferenceClick_checkUpdateCallsUi() throws Exception {
        // check-update 分支把宿主强转为 BaseActivity：须用真实 SettingsActivity
        com.gh4a.activities.SettingsActivity activity =
                Robolectric.buildActivity(com.gh4a.activities.SettingsActivity.class)
                        .setup().get();
        idle();
        activity.getSupportFragmentManager().executePendingTransactions();
        SettingsFragment f = (SettingsFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.content_container);
        assertNotNull(f);
        Preference pref = f.findPreference("check_update");
        assertNotNull(pref);
        try (org.mockito.MockedStatic<com.gh4a.utils.UpdateCheckUi> mocked =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.UpdateCheckUi.class)) {
            assertTrue(f.onPreferenceClick(pref));
            mocked.verify(() -> com.gh4a.utils.UpdateCheckUi.checkManually(
                    org.mockito.ArgumentMatchers.eq(activity)));
        }
    }

    @Test
    public void getAppVersion_returnsString() throws Exception {
        SettingsFragment f = attach(R.xml.settings);
        Object version = call(f, "getAppVersion", new Class<?>[]{});
        assertNotNull(version);
    }

    @Test
    public void getAppVersion_packageNotFoundReturnsEmpty() throws Exception {
        // 539-541 行：自家包名查不到时返回 ""（ShadowPackageManager 移除本包）
        SettingsFragment f = attach(R.xml.settings);
        org.robolectric.shadows.ShadowPackageManager spm =
                org.robolectric.Shadows.shadowOf(mActivity.getPackageManager());
        spm.removePackage(mActivity.getPackageName());
        Object version = call(f, "getAppVersion", new Class<?>[]{});
        assertEquals("", version);
    }

    @Test
    public void onPreferenceChange_nullPrefLogsThenThrows() {
        // 280 行：pref 为 null 时 DiagTrace 用 "?" 记键，随后 pref.getKey() 抛 NPE
        SettingsFragment f = attach(R.xml.settings);
        try {
            f.onPreferenceChange(null, "x");
            org.junit.Assert.fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // 预期行为：空偏好无键可比，下一行即抛错
        }
    }

    @Test
    public void restoreLauncher_callbackDetachedWithUri_isNoop() throws Exception {
        // L111：文档选择器返回 uri 但 fragment 已脱离时不弹恢复确认框。
        // 回调在 onDestroy 时随 registry 注销，直接调 lambda 本身：
        // 唯一单参 Uri 的 lambda 就是 mRestoreLauncher 的回调。
        SettingsFragment f = attachResumed(R.xml.settings);
        mActivity.getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        assertFalse(f.isAdded());
        java.lang.reflect.Method lambda = null;
        for (java.lang.reflect.Method m : SettingsFragment.class.getDeclaredMethods()) {
            if (m.getName().startsWith("lambda$")
                    && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0] == android.net.Uri.class) {
                lambda = m;
                break;
            }
        }
        assertNotNull(lambda);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        lambda.setAccessible(true);
        lambda.invoke(f, android.net.Uri.parse("content://com.example/backup.json"));
        idle();
        assertEquals(before,
                org.robolectric.shadows.ShadowDialog.getShownDialogs().size());
    }

    @Test
    public void refreshMirrorPresetSummary_withUrlValue_showsHost() throws Exception {
        // L363：镜像预设值非空时摘要显示 displayHost(v)。
        SettingsFragment f = attach(R.xml.settings);
        MirrorPresetPreference pref =
                f.findPreference(SettingsFragment.KEY_MIRROR_PRESET);
        assertNotNull(pref);
        pref.setValue("https://mirror.example.com/api");
        call(f, "refreshMirrorPresetSummary", new Class<?>[]{});
        assertEquals("mirror.example.com", pref.getSummary().toString());
    }

    @Test
    public void doRestore_successWithNullListener_skipsThemeRefresh() throws Exception {
        // L450：恢复成功但 mListener 为 null 时不回调 onThemeChanged。
        SettingsFragment f = attach(R.xml.settings);
        setField(f, "mListener", null);
        android.net.Uri uri = android.net.Uri.parse("content://com.example/backup.json");
        try (org.mockito.MockedStatic<com.gh4a.utils.Background> bg =
                     mockBackgroundImmediate();
             org.mockito.MockedStatic<com.gh4a.utils.SettingsBackupManager> mgr =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.SettingsBackupManager.class);
             org.mockito.MockedStatic<com.gh4a.utils.DarkModeScheduler> sched =
                     org.mockito.Mockito.mockStatic(
                             com.gh4a.utils.DarkModeScheduler.class)) {
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.readBackupFile(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.eq(uri)))
                    .thenReturn(new org.json.JSONObject());
            mgr.when(() -> com.gh4a.utils.SettingsBackupManager.applyBackup(
                            org.mockito.ArgumentMatchers.any(Context.class),
                            org.mockito.ArgumentMatchers.any(
                                    org.json.JSONObject.class)))
                    .thenReturn(true);
            call(f, "doRestore", new Class<?>[]{android.net.Uri.class}, uri);
            idle();
            assertEquals(mContext.getString(R.string.restore_success),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            assertEquals(0, mActivity.themeChangedCalls);
        }
    }

    @Test
    public void aboutDialog_repoButtonStartsRepositoryActivity() {
        // L573：关于对话框的 btn_gh4a 打开仓库页。
        com.gh4a.Gh4Application.get().addAccount(
                com.meisolsson.githubsdk.model.User.builder()
                        .login("octocat").id(1L).build(), "token-xyz");
        SettingsFragment f = attachResumed(R.xml.settings);
        Preference about = f.findPreference("about");
        assertTrue(f.onPreferenceClick(about));
        f.getChildFragmentManager().executePendingTransactions();
        SettingsFragment.AboutDialogFragment dialogFragment =
                (SettingsFragment.AboutDialogFragment)
                        f.getChildFragmentManager().findFragmentByTag("about");
        assertNotNull(dialogFragment);
        android.app.Dialog dialog = dialogFragment.getDialog();
        assertNotNull(dialog);
        dialog.findViewById(R.id.btn_gh4a).performClick();
        android.content.Intent repoIntent =
                org.robolectric.Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(repoIntent);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                repoIntent.getComponent().getClassName());
    }

    @Test
    public void aboutDialog_unmatchedButtonId_doesNothing() {
        // L573：onClick 的三个 id 分支都不匹配时静默返回（else-if 链末尾的
        // fall-through 臂）。对话框只给三个按钮挂了监听，只能直接调其
        // onClick（AboutDialog 实现 View.OnClickListener）。
        com.gh4a.Gh4Application.get().addAccount(
                com.meisolsson.githubsdk.model.User.builder()
                        .login("octocat").id(1L).build(), "token-xyz");
        SettingsFragment f = attachResumed(R.xml.settings);
        Preference about = f.findPreference("about");
        assertTrue(f.onPreferenceClick(about));
        f.getChildFragmentManager().executePendingTransactions();
        SettingsFragment.AboutDialogFragment dialogFragment =
                (SettingsFragment.AboutDialogFragment)
                        f.getChildFragmentManager().findFragmentByTag("about");
        assertNotNull(dialogFragment);
        android.app.Dialog dialog = dialogFragment.getDialog();
        assertNotNull(dialog);
        android.view.View other = mock(android.view.View.class);
        when(other.getId()).thenReturn(R.id.copyright);
        ((android.view.View.OnClickListener) dialog).onClick(other);
        assertNull(org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity());
    }

    private Object openSourceAdapter() throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.fragment.SettingsFragment$OpenSourceComponentAdapter");
        java.lang.reflect.Constructor<?> ctor =
                clazz.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(mContext);
    }
}
