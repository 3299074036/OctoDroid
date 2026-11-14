package com.gh4a.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** StarMergedFragment：Star 合并页（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class StarMergedFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    // Fragment.SavedState(Bundle) 在此 androidx 版本是包私有构造，
    // 只能反射创建；用于给未挂载的 Fragment 预置恢复状态。
    private static androidx.fragment.app.Fragment.SavedState savedState(
            android.os.Bundle bundle) throws Exception {
        java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> c =
                androidx.fragment.app.Fragment.SavedState.class
                        .getDeclaredConstructor(android.os.Bundle.class);
        c.setAccessible(true);
        return c.newInstance(bundle);
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    private static String updateKey(ReleaseRadarFragment.RadarItem radar) throws Exception {
        Method m = StarMergedFragment.class.getDeclaredMethod(
                "updateKey", ReleaseRadarFragment.RadarItem.class);
        m.setAccessible(true);
        return (String) m.invoke(null, radar);
    }

    private static Object field(StarMergedFragment f, String name) throws Exception {
        Field field = StarMergedFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    @Test
    public void newInstance_storesLogin() {
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        assertEquals("alice", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLoginAndDefaults() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        assertEquals("alice", field(f, "mLogin"));
        assertEquals("created", field(f, "mSortOrder"));
        assertEquals("desc", field(f, "mSortDirection"));
        assertEquals(0, field(f, "mCurrentTab"));
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void onCreate_restoresSortTabAndMultiSelectFromSavedState() throws Exception {
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        Bundle saved = new Bundle();
        saved.putString("star_sort_order", "updated");
        saved.putString("star_sort_direction", "asc");
        saved.putInt("star_current_tab", 2);
        saved.putBoolean("star_multi_select", true);
        saved.putStringArrayList("star_selected_repos",
                new java.util.ArrayList<>(java.util.Arrays.asList("a/b", "c/d")));
        // 未挂载的 Fragment 直接调 onCreate(saved) 会在 Fragment.onCreate 内
        // 触 mNonConfig 抛 NPE；正确做法是 setInitialSavedState 后挂载。
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertEquals("updated", field(f, "mSortOrder"));
        assertEquals("asc", field(f, "mSortDirection"));
        assertEquals(2, field(f, "mCurrentTab"));
        assertTrue((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void onCreate_multiSelectWithEmptySelectionIsCleared() throws Exception {
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        Bundle saved = new Bundle();
        saved.putBoolean("star_multi_select", true);
        saved.putStringArrayList("star_selected_repos", new java.util.ArrayList<>());
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void onSaveInstanceState_persistsSortTabAndSelection() {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("created", out.getString("star_sort_order"));
        assertEquals("desc", out.getString("star_sort_direction"));
        assertEquals(0, out.getInt("star_current_tab"));
        assertTrue(out.containsKey("star_lists_loaded"));
        assertTrue(out.containsKey("star_updates_loaded"));
        assertFalse(out.getBoolean("star_multi_select"));
        assertTrue(out.getStringArrayList("star_selected_repos").isEmpty());
    }

    @Test
    public void updateKey_formatIsOwnerRepoHashReleaseId() throws Exception {
        ReleaseRadarFragment.RadarItem radar = new ReleaseRadarFragment.RadarItem(
                "octo", "cat", 99L, "v1", "r1", 1000L, 900L);
        assertEquals("octo/cat#99", updateKey(radar));
    }

    @Test
    public void isUpdateRead_emptyReadSetIsFalse() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        Method m = StarMergedFragment.class.getDeclaredMethod(
                "isUpdateRead", ReleaseRadarFragment.RadarItem.class);
        m.setAccessible(true);
        ReleaseRadarFragment.RadarItem radar = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 1L, 1L);
        assertFalse((Boolean) m.invoke(f, radar));
        assertFalse((Boolean) m.invoke(f, (Object) null));
    }
}
