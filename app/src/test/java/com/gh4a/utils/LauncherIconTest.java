package com.gh4a.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.fragment.SettingsFragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B 组试点：LauncherIcon。
 *
 * 注意：Robolectric 的 ShadowPackageManager 没有实现
 * setComponentEnabledSetting（只有 getComponentEnabledSettingFlags），
 * 真实调用在 shadow 下是空操作、get 恒返回 DEFAULT，故不能用真实
 * PackageManager 验状态。改用 Mockito mock 的 PackageManager（以
 * Map 模拟已设置的状态），经 ContextWrapper 注入被测代码。
 */
@RunWith(RobolectricTestRunner.class)
public class LauncherIconTest {

    private static final String[] ALIASES = {
            "com.gh4a.LauncherGreen", "com.gh4a.LauncherBlue", "com.gh4a.LauncherRed",
            "com.gh4a.LauncherOrange", "com.gh4a.LauncherPurple", "com.gh4a.LauncherTeal"
    };

    private static final int ENABLED = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    private static final int DISABLED = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    private static final int DEFAULT = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;

    /** 以 Map 模拟 PackageManager 的已设置状态；未设置过返回 DEFAULT。 */
    private static class FakePackageManager {
        final PackageManager mock = mock(PackageManager.class);
        final Map<ComponentName, Integer> states = new HashMap<>();

        FakePackageManager() {
            when(mock.getComponentEnabledSetting(any(ComponentName.class)))
                    .thenAnswer(inv -> states.getOrDefault(inv.getArgument(0), DEFAULT));
            doAnswer(inv -> {
                states.put(inv.getArgument(0), inv.getArgument(1));
                return null;
            }).when(mock).setComponentEnabledSetting(any(ComponentName.class), anyInt(), anyInt());
        }
    }

    private final Context mBaseContext = ApplicationProvider.getApplicationContext();

    private Context contextWithPm(PackageManager pm) {
        return new ContextWrapper(mBaseContext) {
            @Override
            public PackageManager getPackageManager() {
                return pm;
            }
        };
    }

    private static ComponentName component(Context context, String alias) {
        return new ComponentName(context, alias);
    }

    private void assertOnlyEnabled(FakePackageManager fake, String alias) {
        for (String a : ALIASES) {
            int expected = a.equals(alias) ? ENABLED : DISABLED;
            // DEFAULT 只出现在绿色未显式设置时（按生产代码的解读即启用）；
            // 其余别名的 DEFAULT 即禁用。
            int actual = fake.states.getOrDefault(component(mBaseContext, a), DEFAULT);
            if (a.equals("com.gh4a.LauncherGreen") && actual == DEFAULT) {
                actual = ENABLED;
            } else if (actual == DEFAULT) {
                actual = DISABLED;
            }
            assertEquals(a, expected, actual);
        }
    }

    @Test
    public void apply_blueEnablesOnlyBlue() {
        FakePackageManager fake = new FakePackageManager();
        LauncherIcon.apply(contextWithPm(fake.mock), "blue");
        assertOnlyEnabled(fake, "com.gh4a.LauncherBlue");
        // 全 DEFAULT 出发：只应改绿色（禁用）与蓝色（启用）两处
        verify(fake.mock, times(2)).setComponentEnabledSetting(any(ComponentName.class),
                anyInt(), anyInt());
        verify(fake.mock).setComponentEnabledSetting(
                component(mBaseContext, "com.gh4a.LauncherGreen"),
                DISABLED, PackageManager.DONT_KILL_APP);
        verify(fake.mock).setComponentEnabledSetting(
                component(mBaseContext, "com.gh4a.LauncherBlue"),
                ENABLED, PackageManager.DONT_KILL_APP);
    }

    @Test
    public void apply_eachAccentEnablesOnlyItself() {
        String[][] cases = {
                {"green", "com.gh4a.LauncherGreen"},
                {"red", "com.gh4a.LauncherRed"},
                {"orange", "com.gh4a.LauncherOrange"},
                {"purple", "com.gh4a.LauncherPurple"},
                {"teal", "com.gh4a.LauncherTeal"},
        };
        for (String[] c : cases) {
            FakePackageManager fake = new FakePackageManager();
            LauncherIcon.apply(contextWithPm(fake.mock), c[0]);
            assertOnlyEnabled(fake, c[1]);
        }
    }

    @Test
    public void apply_unknownAccentFallsBackToGreen() {
        FakePackageManager fake = new FakePackageManager();
        Context context = contextWithPm(fake.mock);
        LauncherIcon.apply(context, "blue");
        LauncherIcon.apply(context, "not-a-color");
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
    }

    @Test
    public void apply_nullAccentFallsBackToGreen() {
        FakePackageManager fake = new FakePackageManager();
        Context context = contextWithPm(fake.mock);
        LauncherIcon.apply(context, "blue");
        LauncherIcon.apply(context, null);
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
    }

    @Test
    public void apply_emptyAccentFallsBackToGreen() {
        FakePackageManager fake = new FakePackageManager();
        LauncherIcon.apply(contextWithPm(fake.mock), "");
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
    }

    @Test
    public void apply_caseSensitiveAccentFallsBackToGreen() {
        // ACCENTS 比较是 equals（大小写敏感），"Blue" 不是合法值
        FakePackageManager fake = new FakePackageManager();
        LauncherIcon.apply(contextWithPm(fake.mock), "Blue");
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
    }

    @Test
    public void apply_idempotent() {
        FakePackageManager fake = new FakePackageManager();
        Context context = contextWithPm(fake.mock);
        LauncherIcon.apply(context, "red");
        LauncherIcon.apply(context, "red");
        assertOnlyEnabled(fake, "com.gh4a.LauncherRed");
        // 第二次 apply 不应再调 set（共 2 次调用：绿色禁、红色启）
        verify(fake.mock, times(2)).setComponentEnabledSetting(any(ComponentName.class),
                anyInt(), anyInt());
    }

    @Test
    public void apply_readsAccentFromPrefs() {
        FakePackageManager fake = new FakePackageManager();
        mBaseContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(SettingsFragment.KEY_ACCENT_COLOR, "purple").apply();
        LauncherIcon.apply(contextWithPm(fake.mock));
        assertOnlyEnabled(fake, "com.gh4a.LauncherPurple");
    }

    @Test
    public void apply_defaultPrefIsGreen() {
        // 全 DEFAULT 时绿色按"默认启用"理解：一次 set 都不应发生
        FakePackageManager fake = new FakePackageManager();
        LauncherIcon.apply(contextWithPm(fake.mock));
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
        verify(fake.mock, never()).setComponentEnabledSetting(any(ComponentName.class),
                anyInt(), anyInt());
    }

    @Test
    public void apply_unknownPrefValueFallsBackToGreen() {
        FakePackageManager fake = new FakePackageManager();
        mBaseContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(SettingsFragment.KEY_ACCENT_COLOR, "pink").apply();
        LauncherIcon.apply(contextWithPm(fake.mock));
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
        verify(fake.mock, never()).setComponentEnabledSetting(any(ComponentName.class),
                anyInt(), anyInt());
    }

    @Test
    public void apply_skipsAlreadyCorrectState() {
        // 目标状态已是 teal（显式设置过）时再 apply：一次 set 都不应发生
        FakePackageManager fake = new FakePackageManager();
        for (String a : ALIASES) {
            fake.states.put(component(mBaseContext, a),
                    a.equals("com.gh4a.LauncherTeal") ? ENABLED : DISABLED);
        }
        LauncherIcon.apply(contextWithPm(fake.mock), "teal");
        assertOnlyEnabled(fake, "com.gh4a.LauncherTeal");
        verify(fake.mock, never()).setComponentEnabledSetting(any(ComponentName.class),
                anyInt(), anyInt());
    }

    @Test
    public void apply_explicitlyDisabledGreenGetsEnabled() {
        // 绿色被显式禁用、其余 DEFAULT 时切回绿色：只应启用绿色一次
        FakePackageManager fake = new FakePackageManager();
        fake.states.put(component(mBaseContext, "com.gh4a.LauncherGreen"), DISABLED);
        LauncherIcon.apply(contextWithPm(fake.mock), "green");
        assertOnlyEnabled(fake, "com.gh4a.LauncherGreen");
        verify(fake.mock, times(1)).setComponentEnabledSetting(any(ComponentName.class),
                anyInt(), anyInt());
        verify(fake.mock).setComponentEnabledSetting(
                component(mBaseContext, "com.gh4a.LauncherGreen"),
                ENABLED, PackageManager.DONT_KILL_APP);
    }
}
