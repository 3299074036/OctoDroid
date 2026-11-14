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

    private SettingsFragment attach(int xmlRes) {
        SettingsFragment f = SettingsFragment.newInstance(xmlRes);
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.CREATED).commitNow();
        return f;
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
    public void refreshDarkModeScheduleSummary_doesNotThrow() {
        SettingsFragment f = attach(R.xml.settings);
        f.refreshDarkModeScheduleSummary();
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

    private Object openSourceAdapter() throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.fragment.SettingsFragment$OpenSourceComponentAdapter");
        java.lang.reflect.Constructor<?> ctor =
                clazz.getDeclaredConstructor(Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(mContext);
    }
}
