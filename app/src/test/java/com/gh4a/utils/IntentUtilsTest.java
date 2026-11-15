package com.gh4a.utils;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Looper;
import android.os.Parcel;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.BaseActivity;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.activities.RepositoryListActivity;
import com.gh4a.activities.UserActivity;
import com.gh4a.fragment.SettingsFragment;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.UserType;
import com.meisolsson.githubsdk.service.users.UserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.Date;

import io.reactivex.Single;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * B 组试点：IntentUtils。只覆盖不依赖 Activity/PackageManager 解析的
 * 公共方法；openLinkInternallyOrExternally / openInCustomTabOrBrowser 需
 * 真实 Activity 与 CustomTabs 解析，待 Mockito 后补。
 */
@RunWith(RobolectricTestRunner.class)
public class IntentUtilsTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private ShadowPackageManager shadowPm;

    public static class TestActivity extends BaseActivity {
        @Override
        protected void onCreate(android.os.Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new android.view.View(this));
        }
    }

    @Before
    public void setUp() {
        shadowPm = Shadows.shadowOf(mContext.getPackageManager());
        CustomTabsHelper.clearPackageNameCache();
        customTabsPrefs().edit().remove(SettingsFragment.KEY_CUSTOM_TABS).apply();
    }

    @After
    public void tearDown() {
        CustomTabsHelper.clearPackageNameCache();
        customTabsPrefs().edit().remove(SettingsFragment.KEY_CUSTOM_TABS).apply();
    }

    private SharedPreferences customTabsPrefs() {
        return mContext.getSharedPreferences(SettingsFragment.PREF_NAME,
                Context.MODE_PRIVATE);
    }

    private void setCustomTabsEnabled(boolean enabled) {
        customTabsPrefs().edit()
                .putBoolean(SettingsFragment.KEY_CUSTOM_TABS, enabled).apply();
    }

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private TestActivity baseActivity() {
        return Robolectric.buildActivity(TestActivity.class).setup().get();
    }

    private static void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private Intent startedIntent(FragmentActivity activity) {
        return Shadows.shadowOf(activity).getNextStartedActivity();
    }

    private Intent startedIntentOnAppContext() {
        return Shadows.shadowOf((Application) mContext).getNextStartedActivity();
    }

    private String latestToast() {
        return ShadowToast.getTextOfLatestToast();
    }

    private ResolveInfo browserInfo(String pkg, String label,
            boolean enabled, boolean exported) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = pkg;
        info.activityInfo.name = pkg + ".Main";
        info.activityInfo.enabled = enabled;
        info.activityInfo.exported = exported;
        info.activityInfo.nonLocalizedLabel = label;
        return info;
    }

    /** createBrowserIntent 查的 intent：ACTION_VIEW + dummy authority + BROWSABLE。 */
    private Intent browserQueryIntent(Uri uri) {
        Uri dummy = uri.buildUpon().authority("www.somedummy.com").build();
        return new Intent(Intent.ACTION_VIEW, dummy)
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }

    /** CustomTabsHelperTest 同款：同时注册 VIEW activity 与 Custom Tabs 服务。 */
    private void addChromeWithCustomTabsService() {
        Intent viewIntent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("http://www.example.com"));
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = "com.android.chrome";
        info.activityInfo.name = "com.google.android.apps.chrome.Main";
        shadowPm.addResolveInfoForIntent(viewIntent, info);
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
    public void createBaseUriForUser_exact() {
        assertEquals("https://github.com/octocat",
                IntentUtils.createBaseUriForUser("octocat").build().toString());
    }

    @Test
    public void createBaseUriForRepo_exact() {
        assertEquals("https://github.com/octocat/Hello-World",
                IntentUtils.createBaseUriForRepo("octocat", "Hello-World").build().toString());
    }

    @Test
    public void createRawFileUrl_exact() {
        assertEquals("https://raw.githubusercontent.com/octocat/Hello-World/main/README.md",
                IntentUtils.createRawFileUrl("octocat", "Hello-World", "main", "README.md"));
        assertEquals("https://raw.githubusercontent.com/o/r/v1.0/docs/a/b.txt",
                IntentUtils.createRawFileUrl("o", "r", "v1.0", "docs/a/b.txt"));
    }

    @Test
    public void isNewTaskIntent_nullAndPlain() {
        assertFalse(IntentUtils.isNewTaskIntent(null));
        assertFalse(IntentUtils.isNewTaskIntent(new Intent()));
    }

    @Test
    public void startNewTask_setsFlagsAndExtra() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/"));
        IntentUtils.startNewTask(mContext, intent);
        assertTrue(IntentUtils.isNewTaskIntent(intent));
        int flags = intent.getFlags();
        assertTrue((flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0);
        assertTrue((flags & Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
        // Robolectric 下 startActivity 被 shadow 接住：发出的是同一 intent
        Intent started = Shadows.shadowOf((Application) mContext).getNextStartedActivity();
        assertEquals(intent, started);
    }

    @Test
    public void putAndGetCompressedExtra_stringRoundTrip() {
        Intent intent = new Intent();
        IntentUtils.putCompressedExtra(intent, "k", "hello world");
        assertEquals("hello world", IntentUtils.<String>getCompressedExtra(intent, "k"));
    }

    @Test
    public void putAndGetCompressedExtra_intAndLongRoundTrip() {
        Intent intent = new Intent();
        IntentUtils.putCompressedExtra(intent, "i", 42);
        IntentUtils.putCompressedExtra(intent, "l", 1234567890123L);
        assertEquals(Integer.valueOf(42), IntentUtils.<Integer>getCompressedExtra(intent, "i"));
        assertEquals(Long.valueOf(1234567890123L),
                IntentUtils.<Long>getCompressedExtra(intent, "l"));
    }

    @Test
    public void putAndGetCompressedExtra_listRoundTrip() {
        ArrayList<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        Intent intent = new Intent();
        IntentUtils.putCompressedExtra(intent, "list", list);
        assertEquals(list, IntentUtils.<ArrayList<String>>getCompressedExtra(intent, "list"));
    }

    @Test
    public void getCompressedExtra_missingKeyAndNoExtras() {
        assertNull(IntentUtils.<String>getCompressedExtra(new Intent(), "absent"));
        Intent withOther = new Intent();
        IntentUtils.putCompressedExtra(withOther, "a", "x");
        assertNull(IntentUtils.<String>getCompressedExtra(withOther, "b"));
    }

    @Test
    public void putCompressedValueToBundle_directRoundTrip() {
        android.os.Bundle bundle = new android.os.Bundle();
        IntentUtils.putCompressedValueToBundle(bundle, "k", "bundle-value");
        assertEquals("bundle-value",
                IntentUtils.<String>readCompressedValueFromBundle(bundle, "k"));
        assertNull(IntentUtils.<String>readCompressedValueFromBundle(bundle, "nope"));
    }

    @Test
    public void copyToClipboard_setsPrimaryClip() {
        IntentUtils.copyToClipboard(mContext, "label", "copied text");
        ClipboardManager cm = (ClipboardManager) mContext.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = cm.getPrimaryClip();
        assertEquals("label", clip.getDescription().getLabel().toString());
        assertEquals("copied text", clip.getItemAt(0).getText().toString());
    }

    @Test
    public void launchBrowser_nullUriReturnsSilently() {
        IntentUtils.launchBrowser(mContext, null);
        IntentUtils.launchBrowser(mContext, null, 0);
    }

    @Test
    public void createViewerOrBrowserIntent_noResolverReturnsNull() {
        // Robolectric 下 PackageManager 查不到任何浏览器/查看器，返回 null。
        assertNull(IntentUtils.createViewerOrBrowserIntent(mContext,
                Uri.parse("https://example.com/x.png"), "image/png"));
    }

    @Test
    public void initialCommentMarker_matchesById() {
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(123L);
        assertTrue(marker.matches(123L, null));
        assertFalse(marker.matches(124L, null));
        assertFalse(marker.matches(-1, null));
        assertEquals(0, marker.describeContents());
    }

    @Test
    public void initialCommentMarker_matchesByDateWindow() {
        Date markerDate = new Date(1793872117000L);
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(markerDate);
        // 通知时间戳比事件早 ~10 秒：marker 日期在后 10 秒内 → 匹配
        assertTrue(marker.matches(-1, new Date(1793872117000L - 10_000)));
        assertTrue(marker.matches(-1, new Date(1793872117000L)));
        // 29 秒内仍匹配，30 秒整不匹配（dateDiff < 30_000）
        assertTrue(marker.matches(-1, new Date(1793872117000L - 29_000)));
        assertFalse(marker.matches(-1, new Date(1793872117000L - 30_000)));
        // marker 日期在待匹配日期之前 → 不匹配
        assertFalse(marker.matches(-1, new Date(1793872117000L + 1_000)));
        assertFalse(marker.matches(-1, null));
    }

    @Test
    public void initialCommentMarker_idTakesPrecedenceOverDate() {
        Date markerDate = new Date(1793872117000L);
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(7L);
        assertTrue(marker.matches(7L, new Date(0)));
        assertFalse(marker.matches(8L, markerDate));
    }

    @Test
    public void initialCommentMarker_parcelRoundTrip() {
        Date date = new Date(1793872117000L);
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(99L);
        Parcel parcel = Parcel.obtain();
        marker.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        IntentUtils.InitialCommentMarker restored =
                IntentUtils.InitialCommentMarker.CREATOR.createFromParcel(parcel);
        assertEquals(99L, restored.commentId);
        assertNull(restored.date);
        parcel.recycle();

        IntentUtils.InitialCommentMarker dateMarker =
                new IntentUtils.InitialCommentMarker(date);
        Parcel p2 = Parcel.obtain();
        dateMarker.writeToParcel(p2, 0);
        p2.setDataPosition(0);
        IntentUtils.InitialCommentMarker restored2 =
                IntentUtils.InitialCommentMarker.CREATOR.createFromParcel(p2);
        assertEquals(-1L, restored2.commentId);
        assertEquals(date, restored2.date);
        p2.recycle();

        assertEquals(2, IntentUtils.InitialCommentMarker.CREATOR.newArray(2).length);
    }

    @Test
    public void privateConstructor_instantiatesReflectively() throws Exception {
        java.lang.reflect.Constructor<IntentUtils> ctor =
                IntentUtils.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertNotNull(ctor.newInstance());
    }

    // ---- openLinkInternallyOrExternally ----

    @Test
    public void openLink_nullSchemeReturnsSilently() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity, Uri.parse("octo/cat"));
        assertNull(startedIntent(activity));
        assertNull(latestToast());
    }

    @Test
    public void openLink_fileSchemeReturnsSilently() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("file:///sdcard/x.apk"));
        assertNull(startedIntent(activity));
    }

    @Test
    public void openLink_contentSchemeReturnsSilently() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("content://com.example.provider/x"));
        assertNull(startedIntent(activity));
    }

    @Test
    public void openLink_mailtoStartsViewIntent() {
        FragmentActivity activity = activity();
        Uri mailto = Uri.parse("mailto:foo@bar.com");
        IntentUtils.openLinkInternallyOrExternally(activity, mailto);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals(mailto, started.getData());
    }

    @Test
    public void openLink_javascriptSchemeReturnsSilently() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("javascript:alert(1)"));
        assertNull(startedIntent(activity));
    }

    @Test
    public void openLink_intentSchemeReturnsSilently() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("intent://example.com/#Intent;scheme=https;end"));
        assertNull(startedIntent(activity));
    }

    @Test
    public void openLink_githubUserStartsUserActivity() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("https://github.com/octocat"));
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals(UserActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void openLink_githubRepoStartsRepoActivity() {
        FragmentActivity activity = activity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("https://github.com/octocat/Hello-World"));
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(),
                started.getComponent().getClassName());
    }

    @Test
    public void openLink_nonGithubHttpFallsBackToBrowserToast() {
        // BaseActivity 宿主：headerColor 走 instanceof-true 半边
        TestActivity activity = baseActivity();
        IntentUtils.openLinkInternallyOrExternally(activity,
                Uri.parse("https://example.com/x"));
        assertEquals(mContext.getString(R.string.no_browser_found), latestToast());
    }

    @Test(timeout = 120000)
    @LooperMode(LooperMode.Mode.LEGACY)
    public void openLink_userReposTabExecutesLoadTask() {
        // BrowseFilterTest 同款：LEGACY 下 AsyncTask 在调用线程同步跑，
        // mockStatic(ServiceFactory) 在该线程可见，不发真请求。
        UserService service = mock(UserService.class);
        User user = mock(User.class);
        when(user.login()).thenReturn("octo");
        when(user.type()).thenReturn(UserType.User);
        when(service.getUser(anyString()))
                .thenReturn(Single.just(Response.success(user)));
        try (MockedStatic<ServiceFactory> mocked = mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(eq(UserService.class), eq(false)))
                    .thenReturn(service);
            FragmentActivity activity = activity();
            IntentUtils.openLinkInternallyOrExternally(activity,
                    Uri.parse("https://github.com/octo?tab=repositories"));
            idleMainLooper();
            Intent started = startedIntent(activity);
            assertNotNull(started);
            assertEquals(RepositoryListActivity.class.getName(),
                    started.getComponent().getClassName());
        }
    }

    // ---- launchBrowser / createActivityChooserIntent ----

    @Test
    public void launchBrowser_noResolverShowsToast() {
        IntentUtils.launchBrowser(mContext, Uri.parse("https://example.com/x"));
        assertEquals(mContext.getString(R.string.no_browser_found), latestToast());
    }

    @Test
    public void launchBrowser_seededBrowserStartsIt() {
        Uri uri = Uri.parse("https://example.com/x.apk");
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.browser", "Example Browser", true, true));
        FragmentActivity activity = activity();
        IntentUtils.launchBrowser(activity, uri);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals("com.example.browser", started.getPackage());
        assertEquals(uri, started.getData());
    }

    @Test
    public void launchBrowser_seededBrowserWithFlags() {
        Uri uri = Uri.parse("https://example.com/x.apk");
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.browser", "Example Browser", true, true));
        FragmentActivity activity = activity();
        IntentUtils.launchBrowser(activity, uri, Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
    }

    @Test
    public void launchBrowser_ourPackageSkipped() {
        Uri uri = Uri.parse("https://example.com/x.apk");
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo(mContext.getPackageName(), "OctoDroid", true, true));
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.browser", "Example Browser", true, true));
        FragmentActivity activity = activity();
        IntentUtils.launchBrowser(activity, uri);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals("com.example.browser", started.getPackage());
    }

    @Test
    public void launchBrowser_disabledAndNotExportedSkipped() {
        Uri uri = Uri.parse("https://example.com/x.apk");
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.disabled", "Disabled", false, true));
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.hidden", "Hidden", true, false));
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.browser", "Valid", true, true));
        FragmentActivity activity = activity();
        IntentUtils.launchBrowser(activity, uri);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals("com.example.browser", started.getPackage());
    }

    @Test
    public void launchBrowser_twoResolversShowsChooser() {
        Uri uri = Uri.parse("https://example.com/x.apk");
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.alpha", "Alpha Browser", true, true));
        shadowPm.addResolveInfoForIntent(browserQueryIntent(uri),
                browserInfo("com.example.beta", "Beta Browser", true, true));
        FragmentActivity activity = activity();
        IntentUtils.launchBrowser(activity, uri);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
        Intent target = started.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(target);
        // 按 label 排序后最后一个（Beta）做主 intent，其余进 alternates
        assertEquals("com.example.beta", target.getPackage());
        android.os.Parcelable[] alternates =
                started.getParcelableArrayExtra(Intent.EXTRA_ALTERNATE_INTENTS);
        assertNotNull(alternates);
        assertEquals(1, alternates.length);
        assertEquals("com.example.alpha",
                ((Intent) alternates[0]).getPackage());
    }

    @Test
    public void createViewerOrBrowserIntent_seededViewerReturnsIt() {
        Uri uri = Uri.parse("https://example.com/x.png");
        Uri dummy = uri.buildUpon().authority("www.somedummy.com").build();
        Intent query = new Intent(Intent.ACTION_VIEW).setDataAndType(dummy, "image/png");
        shadowPm.addResolveInfoForIntent(query,
                browserInfo("com.example.viewer", "Viewer", true, true));
        Intent result = IntentUtils.createViewerOrBrowserIntent(mContext, uri, "image/png");
        assertNotNull(result);
        assertEquals("com.example.viewer", result.getPackage());
        assertEquals(uri, result.getData());
        assertEquals("image/png", result.getType());
    }

    // ---- openInCustomTabOrBrowser ----

    @Test
    public void openInCustomTab_chromeSeededLaunchesCustomTab() {
        addChromeWithCustomTabsService();
        FragmentActivity activity = activity();
        IntentUtils.openInCustomTabOrBrowser(activity,
                Uri.parse("https://example.com/x"), 0xFF112233);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals("com.android.chrome", started.getPackage());
        assertEquals(Uri.parse("https://example.com/x"), started.getData());
    }

    @Test
    public void openInCustomTab_chromeSeededZeroHeaderColorResolvesTheme() {
        addChromeWithCustomTabsService();
        TestActivity activity = baseActivity();
        IntentUtils.openInCustomTabOrBrowser(activity, Uri.parse("https://example.com/x"));
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals("com.android.chrome", started.getPackage());
    }

    @Test
    public void openInCustomTab_disabledPrefFallsBackToBrowser() {
        addChromeWithCustomTabsService();
        setCustomTabsEnabled(false);
        FragmentActivity activity = activity();
        IntentUtils.openInCustomTabOrBrowser(activity, Uri.parse("https://example.com/x"));
        assertEquals(mContext.getString(R.string.no_browser_found), latestToast());
    }

    @Test
    public void openInCustomTab_noPackageFallsBackToBrowser() {
        FragmentActivity activity = activity();
        IntentUtils.openInCustomTabOrBrowser(activity,
                Uri.parse("https://example.com/x"), 0xFF112233);
        assertEquals(mContext.getString(R.string.no_browser_found), latestToast());
    }

    // ---- share ----

    @Test
    public void share_startsChooserWithSendIntent() {
        Uri uri = Uri.parse("https://github.com/octocat/Hello-World");
        FragmentActivity activity = activity();
        IntentUtils.share(activity, "subject", uri);
        Intent started = startedIntent(activity);
        assertNotNull(started);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
        Intent inner = started.getParcelableExtra(Intent.EXTRA_INTENT);
        assertNotNull(inner);
        assertEquals(Intent.ACTION_SEND, inner.getAction());
        assertEquals("text/plain", inner.getType());
        assertEquals("subject", inner.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals(uri.toString(), inner.getStringExtra(Intent.EXTRA_TEXT));
    }

    // ---- InitialCommentMarker residuals ----

    @Test
    public void initialCommentMarker_idMarkerWithDateReturnsFalse() {
        IntentUtils.InitialCommentMarker marker =
                new IntentUtils.InitialCommentMarker(7L);
        assertFalse(marker.matches(-1, new Date(1793872117000L)));
    }

    @Test
    public void initialCommentMarker_creatorWithClassLoader() {
        Parcel parcel = Parcel.obtain();
        new IntentUtils.InitialCommentMarker(42L).writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        IntentUtils.InitialCommentMarker restored =
                ((android.os.Parcelable.ClassLoaderCreator<IntentUtils.InitialCommentMarker>)
                        IntentUtils.InitialCommentMarker.CREATOR)
                        .createFromParcel(parcel,
                                IntentUtilsTest.class.getClassLoader());
        assertEquals(42L, restored.commentId);
        assertNull(restored.date);
        parcel.recycle();
    }
}
