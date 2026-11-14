package com.gh4a.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPackageManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A 组：CustomTabsHelper 查 PackageManager，走 Robolectric。
 * 沙箱里没有任何浏览器：默认返回 null；再用 ShadowPackageManager 注入
 * 一个支持 Custom Tabs 的 Chrome，校验选取与缓存逻辑。
 */
@RunWith(RobolectricTestRunner.class)
public class CustomTabsHelperTest {

    private Context context;
    private ShadowPackageManager shadowPm;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        CustomTabsHelper.clearPackageNameCache();
        shadowPm = Shadows.shadowOf(context.getPackageManager());
    }

    private Intent viewIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
    }

    private ResolveInfo chromeResolveInfo() {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.android.chrome";
        info.activityInfo.name = "com.google.android.apps.chrome.Main";
        return info;
    }

    /** 同时注册 VIEW activity 与 Custom Tabs warmup service 两个解析项。 */
    private void addChromeWithCustomTabsService() {
        shadowPm.addResolveInfoForIntent(viewIntent(), chromeResolveInfo());
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(
                androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
        serviceIntent.setPackage("com.android.chrome");
        ResolveInfo serviceInfo = new ResolveInfo();
        serviceInfo.serviceInfo = new android.content.pm.ServiceInfo();
        serviceInfo.serviceInfo.packageName = "com.android.chrome";
        serviceInfo.serviceInfo.name =
                "com.google.android.apps.chrome.CustomTabsService";
        shadowPm.addResolveInfoForIntent(serviceIntent, serviceInfo);
    }

    @Test
    public void getPackageNameToUse_noBrowsersReturnsNull() {
        assertNull(CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void clearPackageNameCache_doesNotThrowAndStillNull() {
        CustomTabsHelper.getPackageNameToUse(context);
        CustomTabsHelper.clearPackageNameCache();
        assertNull(CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_singleSupportingBrowserIsPicked() {
        addChromeWithCustomTabsService();
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_resultIsCached() {
        addChromeWithCustomTabsService();
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
        // 注入信息撤掉后仍返回缓存值，不再重新解析
        shadowPm.removeResolveInfosForIntent(viewIntent(), "com.android.chrome");
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void clearPackageNameCache_afterCacheClearedReResolves() {
        addChromeWithCustomTabsService();
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
        CustomTabsHelper.clearPackageNameCache();
        shadowPm.removeResolveInfosForIntent(viewIntent(), "com.android.chrome");
        assertNull(CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_activityWithoutCustomTabsServiceIsIgnored() {
        // 只有 VIEW、没有 Custom Tabs warmup service 的应用不计入候选
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.example.plainbrowser";
        info.activityInfo.name = "com.example.plainbrowser.Main";
        shadowPm.addResolveInfoForIntent(viewIntent(), info);
        assertNull(CustomTabsHelper.getPackageNameToUse(context));
    }
}
