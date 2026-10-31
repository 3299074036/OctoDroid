package com.gh4a.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import com.gh4a.fragment.SettingsFragment;

/**
 * 0.1.12：桌面图标背景跟主题色走。做法是每种主题色一个 activity-alias
 * （LauncherGreen/Blue/Red/Orange/Purple/Teal，图标前景相同、背景为该主题色），
 * 切换主题时只启用对应别名、禁用其余别名。主 Activity 本身不挂 LAUNCHER 入口，
 * 只保留 gh4a://oauth 深链，故深链始终解析到主 Activity，不受别名开关影响。
 * 状态已是目标状态时不重复调用，避免图标闪烁；切换后图标由桌面刷新
 * （部分桌面需回到主屏/重进桌面才刷新，属桌面行为，非本应用可控）。
 */
public class LauncherIcon {
    private static final String[] ACCENTS = {
            "green", "blue", "red", "orange", "purple", "teal"
    };
    private static final String[] ALIASES = {
            "com.gh4a.LauncherGreen", "com.gh4a.LauncherBlue", "com.gh4a.LauncherRed",
            "com.gh4a.LauncherOrange", "com.gh4a.LauncherPurple", "com.gh4a.LauncherTeal"
    };

    public static void apply(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
        apply(context, prefs.getString(SettingsFragment.KEY_ACCENT_COLOR, "green"));
    }

    public static void apply(Context context, String accent) {
        int target = 0;
        boolean known = false;
        for (int i = 0; i < ACCENTS.length; i++) {
            if (ACCENTS[i].equals(accent)) {
                target = i;
                known = true;
                break;
            }
        }
        // 0.1.20 F-27: 未知主题色（如经备份恢复注入）不再静默回落绿色——
        // 显式回落并记日志，非法值来源不再被掩盖。
        if (!known) {
            android.util.Log.w("LauncherIcon",
                    "Unknown accent '" + accent + "', falling back to green");
            target = 0;
        }
        PackageManager pm = context.getPackageManager();
        for (int i = 0; i < ALIASES.length; i++) {
            ComponentName component = new ComponentName(context, ALIASES[i]);
            int current = pm.getComponentEnabledSetting(component);
            // 未显式设置过时返回 DEFAULT：按 Manifest 默认值理解
            // （绿色别名默认启用，其余默认禁用）。
            boolean currentlyEnabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0);
            boolean wantEnabled = i == target;
            if (currentlyEnabled != wantEnabled) {
                pm.setComponentEnabledSetting(component,
                        wantEnabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }
    }
}
