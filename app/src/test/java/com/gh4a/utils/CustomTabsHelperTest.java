package com.gh4a.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.PatternMatcher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.browser.customtabs.CustomTabsService;
import androidx.test.core.app.ApplicationProvider;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    // ---- 多包选择链 ----

    private ResolveInfo viewInfo(String pkg) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = pkg;
        info.activityInfo.name = pkg + ".Main";
        return info;
    }

    private void addViewApp(String pkg) {
        shadowPm.addResolveInfoForIntent(viewIntent(), viewInfo(pkg));
    }

    private void addViewApp(String pkg, IntentFilter filter) {
        ResolveInfo info = viewInfo(pkg);
        info.filter = filter;
        shadowPm.addResolveInfoForIntent(viewIntent(), info);
    }

    private void addServiceApp(String pkg) {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION);
        serviceIntent.setPackage(pkg);
        ResolveInfo serviceInfo = new ResolveInfo();
        serviceInfo.serviceInfo = new ServiceInfo();
        serviceInfo.serviceInfo.packageName = pkg;
        serviceInfo.serviceInfo.name = pkg + ".CustomTabsService";
        shadowPm.addResolveInfoForIntent(serviceIntent, serviceInfo);
    }

    private void addBrowserWithService(String pkg) {
        addViewApp(pkg);
        addServiceApp(pkg);
    }

    private static IntentFilter specializingFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addDataAuthority("www.example.com", null);
        filter.addDataPath("/x", PatternMatcher.PATTERN_LITERAL);
        return filter;
    }

    @Test
    public void getPackageNameToUse_twoPackages_defaultPickedWhenNoSpecializedHandler() {
        addBrowserWithService("com.android.chrome");
        addBrowserWithService("com.chrome.beta");
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_twoPackages_stablePickedWhenDefaultNotInList() {
        // plain 先注入成默认 VIEW 处理者，但它没有 Custom Tabs 服务，
        // 不进候选；默认包不在候选里时走 STABLE 兜底
        addViewApp("com.example.plain");
        addBrowserWithService("com.android.chrome");
        addBrowserWithService("com.chrome.beta");
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_twoPackages_betaPickedWhenNoStable() {
        addViewApp("com.example.plain");
        addBrowserWithService("com.chrome.beta");
        addBrowserWithService("com.chrome.dev");
        assertEquals("com.chrome.beta",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_twoPackages_devPickedWhenNoStableOrBeta() {
        addViewApp("com.example.plain");
        addBrowserWithService("com.chrome.dev");
        addBrowserWithService("com.google.android.apps.chrome");
        assertEquals("com.chrome.dev",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_twoPackages_localPickedWhenOnlyLocal() {
        addViewApp("com.example.plain");
        addBrowserWithService("com.google.android.apps.chrome");
        addBrowserWithService("com.example.other");
        assertEquals("com.google.android.apps.chrome",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_twoPackages_noKnownPackageReturnsNull() {
        addViewApp("com.example.plain");
        addBrowserWithService("com.example.other1");
        addBrowserWithService("com.example.other2");
        assertNull(CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_twoPackages_specializedHandlerSkipsDefault() {
        // 默认包带 specialized filter（authority＋path），默认分支被跳过，
        // 兜底选 beta；若 specialization 没起作用会错选默认包
        addViewApp("com.example.other", specializingFilter());
        addServiceApp("com.example.other");
        addBrowserWithService("com.chrome.beta");
        assertEquals("com.chrome.beta",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_filterWithoutPathsIsSkipped() {
        IntentFilter filter = new IntentFilter();
        filter.addDataAuthority("www.example.com", null);
        addViewApp("com.example.other", filter);
        addServiceApp("com.example.other");
        addBrowserWithService("com.chrome.beta");
        assertEquals("com.example.other",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    @Test
    public void getPackageNameToUse_filterWithoutAuthoritiesIsSkipped() {
        IntentFilter filter = new IntentFilter();
        filter.addDataPath("/x", PatternMatcher.PATTERN_LITERAL);
        addViewApp("com.example.other", filter);
        addServiceApp("com.example.other");
        addBrowserWithService("com.chrome.beta");
        assertEquals("com.example.other",
                CustomTabsHelper.getPackageNameToUse(context));
    }

    // ---- hasSpecializedHandlerIntents：mock PackageManager ----

    private Context mockContextWithTwoBrowsers() {
        PackageManager mockPm = mock(PackageManager.class);
        ResolveInfo chrome = viewInfo("com.android.chrome");
        ResolveInfo beta = viewInfo("com.chrome.beta");
        when(mockPm.resolveActivity(any(Intent.class), eq(0)))
                .thenReturn(chrome);
        when(mockPm.queryIntentActivities(any(Intent.class), eq(0)))
                .thenReturn(Arrays.asList(chrome, beta));
        ResolveInfo service = new ResolveInfo();
        service.serviceInfo = new ServiceInfo();
        when(mockPm.resolveService(any(Intent.class), eq(0)))
                .thenReturn(service);
        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mockPm);
        return mockContext;
    }

    private PackageManager mockPmOf(Context mockContext) {
        return mockContext.getPackageManager();
    }

    @Test
    public void getPackageNameToUse_twoPackages_noDefaultHandlerFallsBackToStable() {
        // 真机上 resolveActivity 可能为 null（无 DEFAULT 匹配），而
        // queryIntentActivities 仍有候选：默认分支被短路，走 STABLE 兜底
        PackageManager mockPm = mock(PackageManager.class);
        ResolveInfo chrome = viewInfo("com.android.chrome");
        ResolveInfo beta = viewInfo("com.chrome.beta");
        when(mockPm.resolveActivity(any(Intent.class), eq(0)))
                .thenReturn(null);
        when(mockPm.queryIntentActivities(any(Intent.class), eq(0)))
                .thenReturn(Arrays.asList(chrome, beta));
        ResolveInfo service = new ResolveInfo();
        service.serviceInfo = new ServiceInfo();
        when(mockPm.resolveService(any(Intent.class), eq(0)))
                .thenReturn(service);
        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mockPm);
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(mockContext));
    }

    @Test
    public void hasSpecialized_nullHandlersReturnsFalse() {
        Context mockContext = mockContextWithTwoBrowsers();
        when(mockPmOf(mockContext).queryIntentActivities(any(Intent.class),
                eq(PackageManager.GET_RESOLVED_FILTER))).thenReturn(null);
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(mockContext));
    }

    @Test
    public void hasSpecialized_emptyHandlersReturnsFalse() {
        Context mockContext = mockContextWithTwoBrowsers();
        when(mockPmOf(mockContext).queryIntentActivities(any(Intent.class),
                eq(PackageManager.GET_RESOLVED_FILTER)))
                .thenReturn(java.util.Collections.emptyList());
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(mockContext));
    }

    @Test
    public void hasSpecialized_runtimeExceptionReturnsFalse() {
        Context mockContext = mockContextWithTwoBrowsers();
        when(mockPmOf(mockContext).queryIntentActivities(any(Intent.class),
                eq(PackageManager.GET_RESOLVED_FILTER)))
                .thenThrow(new RuntimeException("boom"));
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(mockContext));
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
    @Test
    public void hasSpecialized_nullActivityInfoIsSkipped() {
        // L129：filter 含 authority+path 但 activityInfo 为 null 的候选被
        // continue 跳过，不抛 NPE、也不算专门处理。
        PackageManager mockPm = mock(PackageManager.class);
        ResolveInfo chrome = viewInfo("com.android.chrome");
        ResolveInfo beta = viewInfo("com.chrome.beta");
        when(mockPm.resolveActivity(any(Intent.class), eq(0)))
                .thenReturn(chrome);
        when(mockPm.queryIntentActivities(any(Intent.class), eq(0)))
                .thenReturn(Arrays.asList(chrome, beta));
        ResolveInfo service = new ResolveInfo();
        service.serviceInfo = new ServiceInfo();
        when(mockPm.resolveService(any(Intent.class), eq(0)))
                .thenReturn(service);
        ResolveInfo specialized = new ResolveInfo();
        specialized.filter = new IntentFilter();
        specialized.filter.addDataAuthority("example.com", null);
        specialized.filter.addDataPath("/x", PatternMatcher.PATTERN_LITERAL);
        specialized.activityInfo = null;
        when(mockPm.queryIntentActivities(any(Intent.class),
                eq(PackageManager.GET_RESOLVED_FILTER)))
                .thenReturn(java.util.Collections.singletonList(specialized));
        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(mockPm);
        assertEquals("com.android.chrome",
                CustomTabsHelper.getPackageNameToUse(mockContext));
    }

}
