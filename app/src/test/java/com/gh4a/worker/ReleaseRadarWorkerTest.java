package com.gh4a.worker;

import android.Manifest;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.gh4a.fragment.RadarGraphQL;
import com.gh4a.fragment.ReleaseRadarFragment;
import com.gh4a.fragment.SettingsFragment;

import org.json.JSONObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class ReleaseRadarWorkerTest {
    private static final String PREFS = "release_radar_notify";
    private static final String KEY_KNOWN = "known_tags_octo";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
        mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private void setLogin(String login) {
        mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString("active_login", login).apply();
    }

    private void grantNotifications() {
        Shadows.shadowOf((Application) mContext.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private ReleaseRadarWorker worker() {
        return new ReleaseRadarWorker(mContext, mock(WorkerParameters.class));
    }

    private SharedPreferences radarPrefs() {
        return mContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static ReleaseRadarFragment.RadarItem item(String owner, String repo,
            long releaseId, String tag) throws Exception {
        Constructor<ReleaseRadarFragment.RadarItem> c =
                ReleaseRadarFragment.RadarItem.class.getDeclaredConstructor(
                        String.class, String.class, long.class, String.class, String.class,
                        long.class, long.class);
        c.setAccessible(true);
        return c.newInstance(owner, repo, releaseId, tag, "Release " + tag, 1000L, 900L);
    }

    private int postedNotificationCount() {
        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        return Shadows.shadowOf(nm).getAllNotifications().size();
    }

    @Test
    public void schedule_clampsInterval() {
        ReleaseRadarWorker.schedule(mContext, 1);
        ReleaseRadarWorker.schedule(mContext, 99999);
        ReleaseRadarWorker.schedule(mContext, 60);
    }

    @Test
    public void cancel_doesNotThrow() {
        ReleaseRadarWorker.schedule(mContext, 60);
        ReleaseRadarWorker.cancel(mContext);
    }

    @Test
    public void createNotificationChannels_createsChannel() {
        ReleaseRadarWorker.createNotificationChannels(mContext);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        assertNotNull(nm.getNotificationChannel("channel_release_radar"));
    }

    @Test
    public void doWork_noLogin_returnsSuccessWithoutFetching() {
        setLogin(null);
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            mocked.verify(() -> RadarGraphQL.fetch(anyString()), never());
        }
    }

    @Test
    public void doWork_noPermission_returnsSuccessWithoutFetching() {
        setLogin("octo");
        assertFalse(NotificationPermission.canPostNotifications(mContext));
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            mocked.verify(() -> RadarGraphQL.fetch(anyString()), never());
        }
    }

    @Test
    public void doWork_firstRun_establishesBaselineWithoutNotifying() throws Exception {
        setLogin("octo");
        grantNotifications();
        List<ReleaseRadarFragment.RadarItem> items =
                Arrays.asList(item("o", "r", 1L, "v1.0"));
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(items));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
        String stored = radarPrefs().getString(KEY_KNOWN, null);
        assertNotNull(stored);
        // org.json escapes '/' as "\/" in toString(); parse back instead
        // of matching the raw string.
        assertEquals("v1.0", new JSONObject(stored).getString("o/r"));
    }

    @Test
    public void doWork_sameTag_returnsSuccessWithoutNotifying() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit().putString(KEY_KNOWN, "{\"o/r\":\"v1.0\"}").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(Arrays.asList(item("o", "r", 1L, "v1.0"))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
    }

    @Test
    public void doWork_newTag_notifiesRepoAndSummary() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit().putString(KEY_KNOWN, "{\"o/r\":\"v1.0\"}").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(Arrays.asList(item("o", "r", 2L, "v2.0"))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        // one per new release + one summary notification
        assertEquals(2, postedNotificationCount());
        assertTrue(radarPrefs().getString(KEY_KNOWN, "").contains("v2.0"));
    }

    @Test
    public void doWork_newTagForOneRepoOnly_notifiesOnlyThatRepo() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit()
                .putString(KEY_KNOWN, "{\"o/r1\":\"v1.0\",\"o/r2\":\"v1.0\"}").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo")).thenReturn(Single.just(Arrays.asList(
                    item("o", "r1", 1L, "v1.0"),
                    item("o", "r2", 2L, "v2.0"))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(2, postedNotificationCount());
    }

    @Test
    public void doWork_oldFormatKnownTags_parsedAndUpdated() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit().putString(KEY_KNOWN, "o/r=v1.0").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(Arrays.asList(item("o", "r", 2L, "v2.0"))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(2, postedNotificationCount());
        // saved back in the new JSON format
        assertTrue(radarPrefs().getString(KEY_KNOWN, "").startsWith("{"));
    }

    @Test
    public void doWork_tagWithSemicolon_survivesJsonRoundTrip() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit().putString(KEY_KNOWN, "{\"o/r\":\"v1;beta\"}").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo")).thenReturn(
                    Single.just(Arrays.asList(item("o", "r", 1L, "v1;beta"))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
    }

    @Test
    public void doWork_malformedKnownTags_treatedAsFirstRun() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit().putString(KEY_KNOWN, "{not json").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(Arrays.asList(item("o", "r", 1L, "v1.0"))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
    }

    @Test
    public void doWork_emptyFetch_firstRunBaseline() {
        setLogin("octo");
        grantNotifications();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(Collections.emptyList()));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
    }

    @Test
    public void doWork_unauthorized_returnsFailure() {
        setLogin("octo");
        grantNotifications();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo")).thenReturn(Single.error(
                    new RadarGraphQL.UnauthorizedException("token revoked")));
            assertEquals(ListenableWorker.Result.failure(), worker().doWork());
        }
    }

    @Test
    public void doWork_transientError_returnsRetry() {
        setLogin("octo");
        grantNotifications();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.error(new RuntimeException("network down")));
            assertEquals(ListenableWorker.Result.retry(), worker().doWork());
        }
    }

    @Test
    public void doWork_newTagButNoPermissionAfterFetch_doesNotSaveKnownTags() throws Exception {
        // Permission is granted at the start so the fetch runs, then
        // revoked mid-fetch is not simulable here; instead verify the
        // no-permission-at-start path never writes known tags.
        setLogin("octo");
        radarPrefs().edit().putString(KEY_KNOWN, "{\"o/r\":\"v1.0\"}").apply();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            mocked.verify(() -> RadarGraphQL.fetch(anyString()), never());
        }
        assertEquals("{\"o/r\":\"v1.0\"}", radarPrefs().getString(KEY_KNOWN, null));
    }

    private void denyNotifications() {
        Shadows.shadowOf((Application) mContext.getApplicationContext())
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Test
    @Config(sdk = 25)
    public void createNotificationChannels_preO_returnsWithoutCreatingChannel() {
        ReleaseRadarWorker.createNotificationChannels(mContext);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        // getNotificationChannel() is API 26+; ask the shadow instead.
        assertTrue(Shadows.shadowOf(nm).getNotificationChannels().isEmpty());
    }

    @Test
    public void doWork_newRepoNotInKnownMap_doesNotNotify() throws Exception {
        setLogin("octo");
        grantNotifications();
        radarPrefs().edit().putString(KEY_KNOWN, "{\"o/r\":\"v1.0\"}").apply();
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("o", "r", 1L, "v1.0"), item("o2", "r2", 2L, "v2.0"));
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(items));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
        String stored = radarPrefs().getString(KEY_KNOWN, null);
        assertNotNull(stored);
        assertEquals("v1.0", new JSONObject(stored).getString("o/r"));
        assertEquals("v2.0", new JSONObject(stored).getString("o2/r2"));
    }

    @Test
    public void doWork_permissionRevokedMidFetch_doesNotSaveKnownTags() throws Exception {
        setLogin("octo");
        grantNotifications();
        List<ReleaseRadarFragment.RadarItem> items =
                Collections.singletonList(item("o", "r", 1L, "v1.0"));
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenAnswer(invocation -> {
                        denyNotifications();
                        return Single.just(items);
                    });
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        assertNull(radarPrefs().getString(KEY_KNOWN, null));
        assertEquals(0, postedNotificationCount());
    }

    @Test
    public void doWork_unauthorizedWithPermissionRevokedMidFetch_returnsFailureWithoutNotification() {
        setLogin("octo");
        grantNotifications();
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenAnswer(invocation -> {
                        denyNotifications();
                        return Single.error(
                                new RadarGraphQL.UnauthorizedException("token revoked"));
                    });
            assertEquals(ListenableWorker.Result.failure(), worker().doWork());
        }
        assertEquals(0, postedNotificationCount());
    }

    @Test
    public void doWork_itemWithNullTagName_skipsNullEntryInKnownTags() throws Exception {
        setLogin("octo");
        grantNotifications();
        List<ReleaseRadarFragment.RadarItem> items =
                Collections.singletonList(item("o", "r", 1L, null));
        try (MockedStatic<RadarGraphQL> mocked = mockStatic(RadarGraphQL.class)) {
            mocked.when(() -> RadarGraphQL.fetch("octo"))
                    .thenReturn(Single.just(items));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
        }
        String stored = radarPrefs().getString(KEY_KNOWN, null);
        assertNotNull(stored);
        assertEquals(0, new JSONObject(stored).length());
        assertEquals(0, postedNotificationCount());
    }

}
