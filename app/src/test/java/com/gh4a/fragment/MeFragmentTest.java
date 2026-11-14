package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Method;

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
}
