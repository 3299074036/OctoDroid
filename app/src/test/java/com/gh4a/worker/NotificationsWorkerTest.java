package com.gh4a.worker;

import android.Manifest;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.gh4a.fragment.SettingsFragment;
import com.gh4a.model.NotificationHolder;
import com.gh4a.model.NotificationListLoadResult;
import com.gh4a.utils.SingleFactory;
import com.meisolsson.githubsdk.model.NotificationSubject;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import com.gh4a.fragment.GraphQLClient;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class NotificationsWorkerTest {
    private static final String KEY_LAST_CHECK = "last_notification_check";
    private static final String KEY_LAST_SEEN = "last_notification_seen";
    private static final String KEY_LAST_REPO_IDS = "last_notification_repo_ids";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        prefs().edit().clear().apply();
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE);
    }

    private void grantNotifications() {
        Shadows.shadowOf((Application) mContext.getApplicationContext())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private NotificationsWorker worker() {
        return new NotificationsWorker(mContext, mock(WorkerParameters.class));
    }

    private Repository repo(long id, User owner) {
        Repository r = mock(Repository.class);
        when(r.id()).thenReturn(id);
        when(r.name()).thenReturn("cat");
        when(r.owner()).thenReturn(owner);
        return r;
    }

    private User owner() {
        // id() == null: AvatarHandler returns null immediately, no network.
        User u = mock(User.class);
        when(u.id()).thenReturn(null);
        when(u.login()).thenReturn("octo");
        when(u.avatarUrl()).thenReturn(null);
        return u;
    }

    private NotificationThread thread(Repository repository, Date updatedAt) {
        NotificationThread n = mock(NotificationThread.class);
        when(n.repository()).thenReturn(repository);
        when(n.updatedAt()).thenReturn(updatedAt);
        when(n.unread()).thenReturn(true);
        NotificationSubject subject = mock(NotificationSubject.class);
        when(subject.type()).thenReturn("Issue");
        when(subject.title()).thenReturn("Some issue");
        when(n.subject()).thenReturn(subject);
        return n;
    }

    /** One repo group: header holder + one notification holder. */
    private NotificationListLoadResult oneGroupResult(Date updatedAt) {
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(thread(r, updatedAt));
        return new NotificationListLoadResult(Arrays.asList(header, item));
    }

    // ---------- schedule / cancel / channels ----------

    @Test
    public void schedule_clampsIntervalBelowMinimum() {
        grantNotifications();
        NotificationsWorker.schedule(mContext, 1);
        NotificationsWorker.schedule(mContext, 14);
    }

    @Test
    public void schedule_clampsIntervalAboveMaximum() {
        NotificationsWorker.schedule(mContext, 1441);
        NotificationsWorker.schedule(mContext, 99999);
    }

    @Test
    public void schedule_validInterval_enqueues() {
        NotificationsWorker.schedule(mContext, 60);
    }

    @Test
    public void cancel_doesNotThrow() {
        NotificationsWorker.schedule(mContext, 60);
        NotificationsWorker.cancel(mContext);
    }

    @Test
    public void createNotificationChannels_createsChannel() {
        NotificationsWorker.createNotificationChannels(mContext);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        assertNotNull(nm.getNotificationChannel("channel_notifications"));
    }

    // ---------- getLastCheckTimestamp ----------

    @Test
    public void getLastCheckTimestamp_notificationsDisabled_returnsZero() {
        prefs().edit().putBoolean(SettingsFragment.KEY_NOTIFICATIONS, false)
                .putLong(KEY_LAST_CHECK, 9999L).apply();
        assertEquals(0L, NotificationsWorker.getLastCheckTimestamp(mContext));
    }

    @Test
    public void getLastCheckTimestamp_notificationsEnabled_returnsStoredValue() {
        prefs().edit().putBoolean(SettingsFragment.KEY_NOTIFICATIONS, true)
                .putLong(KEY_LAST_CHECK, 9999L).apply();
        assertEquals(9999L, NotificationsWorker.getLastCheckTimestamp(mContext));
    }

    @Test
    public void getLastCheckTimestamp_noStoredValue_returnsZero() {
        prefs().edit().putBoolean(SettingsFragment.KEY_NOTIFICATIONS, true).apply();
        assertEquals(0L, NotificationsWorker.getLastCheckTimestamp(mContext));
    }

    // ---------- markNotificationsAsSeen ----------

    @Test
    public void markNotificationsAsSeen_updatesSeenTimestampAndClearsRepoIds() {
        prefs().edit().putStringSet(KEY_LAST_REPO_IDS, new HashSet<>(Arrays.asList("1", "2")))
                .putLong(KEY_LAST_SEEN, 0L).apply();
        long before = System.currentTimeMillis();
        NotificationsWorker.markNotificationsAsSeen(mContext);
        assertTrue(prefs().getLong(KEY_LAST_SEEN, 0) >= before);
        assertNull(prefs().getStringSet(KEY_LAST_REPO_IDS, null));
    }

    // ---------- handleNotificationDismiss ----------

    @Test
    public void handleNotificationDismiss_removesIdFromShownSet() {
        prefs().edit().putStringSet(KEY_LAST_REPO_IDS,
                new HashSet<>(Arrays.asList("7", "8"))).apply();
        NotificationsWorker.handleNotificationDismiss(mContext, 7);
        Set<String> remaining = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
        assertNotNull(remaining);
        assertEquals(Collections.singleton("8"), remaining);
    }

    @Test
    public void handleNotificationDismiss_lastIdClearsSet() {
        prefs().edit().putStringSet(KEY_LAST_REPO_IDS,
                new HashSet<>(Collections.singleton("7"))).apply();
        NotificationsWorker.handleNotificationDismiss(mContext, 7);
        assertNull(prefs().getStringSet(KEY_LAST_REPO_IDS, null));
    }

    @Test
    public void handleNotificationDismiss_unknownId_leavesSetUnchanged() {
        prefs().edit().putStringSet(KEY_LAST_REPO_IDS,
                new HashSet<>(Arrays.asList("7", "8"))).apply();
        NotificationsWorker.handleNotificationDismiss(mContext, 99);
        assertEquals(new HashSet<>(Arrays.asList("7", "8")),
                prefs().getStringSet(KEY_LAST_REPO_IDS, null));
    }

    @Test
    public void handleNotificationDismiss_noStoredSet_isNoop() {
        NotificationsWorker.handleNotificationDismiss(mContext, 7);
        assertNull(prefs().getStringSet(KEY_LAST_REPO_IDS, null));
    }

    // ---------- doWork ----------

    @Test
    public void doWork_noPermission_returnsSuccessWithoutFetching() {
        // Robolectric denies POST_NOTIFICATIONS by default.
        assertFalse(NotificationPermission.canPostNotifications(mContext));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            mocked.verify(() -> SingleFactory.getNotifications(anyBoolean(), anyBoolean(),
                    anyBoolean()), never());
        }
    }

    @Test
    public void doWork_emptyResult_returnsSuccessAndKeepsLastCheck() {
        grantNotifications();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Collections.emptyList())));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(0L, prefs().getLong(KEY_LAST_CHECK, 0));
        }
    }

    @Test
    public void doWork_oldNotification_returnsSuccessWithoutUpdatingLastCheck() {
        grantNotifications();
        Date old = new Date(System.currentTimeMillis() - 100000L);
        prefs().edit().putLong(KEY_LAST_SEEN, System.currentTimeMillis()).apply();
        NotificationListLoadResult result = oneGroupResult(old);
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(0L, prefs().getLong(KEY_LAST_CHECK, 0));
        }
    }

    @Test
    public void doWork_newUnseenNotification_updatesLastCheckAndRepoIds() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        NotificationListLoadResult result = oneGroupResult(now);
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            Set<String> ids = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
            assertNotNull(ids);
            assertTrue(ids.contains("123"));
        }
    }

    @Test
    public void doWork_notificationWithNullUpdatedAt_isNotUnseen() {
        grantNotifications();
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(thread(r, null));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(0L, prefs().getLong(KEY_LAST_CHECK, 0));
        }
    }

    @Test
    public void doWork_repoWithoutId_doesNotRecordRepoId() {
        grantNotifications();
        Repository r = mock(Repository.class);
        when(r.id()).thenReturn(null);
        when(r.name()).thenReturn("cat");
        User repoOwner = owner();
        when(r.owner()).thenReturn(repoOwner);
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(thread(r, new Date()));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            Set<String> ids = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
            assertNotNull(ids);
            assertTrue(ids.isEmpty());
        }
    }

    @Test
    public void doWork_unauthorized_returnsFailure() {
        grantNotifications();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.error(new HttpException(Response.error(401,
                            ResponseBody.create(null, new byte[0])))));
            assertEquals(ListenableWorker.Result.failure(), worker().doWork());
        }
    }

    @Test
    public void doWork_transientError_returnsRetry() {
        grantNotifications();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.error(new RuntimeException("network down")));
            assertEquals(ListenableWorker.Result.retry(), worker().doWork());
        }
    }

    @Test
    public void doWork_staleRepoId_getsCancelledFromShownSet() {
        grantNotifications();
        prefs().edit().putStringSet(KEY_LAST_REPO_IDS,
                new HashSet<>(Collections.singleton("999"))).apply();
        Date now = new Date(System.currentTimeMillis());
        NotificationListLoadResult result = oneGroupResult(now);
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            Set<String> ids = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
            assertNotNull(ids);
            assertTrue(ids.contains("123"));
            assertFalse(ids.contains("999"));
        }
    }

    private int postedNotificationCount() {
        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        return Shadows.shadowOf(nm).getAllNotifications().size();
    }

    private void denyNotifications() {
        Shadows.shadowOf((Application) mContext.getApplicationContext())
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS);
    }

    private NotificationThread threadWithSubject(Repository repository, Date updatedAt,
            String type, String title) {
        NotificationThread n = mock(NotificationThread.class);
        when(n.repository()).thenReturn(repository);
        when(n.updatedAt()).thenReturn(updatedAt);
        when(n.unread()).thenReturn(true);
        NotificationSubject subject = mock(NotificationSubject.class);
        when(subject.type()).thenReturn(type);
        when(subject.title()).thenReturn(title);
        when(n.subject()).thenReturn(subject);
        return n;
    }

    private NotificationThread threadWithNullSubject(Repository repository, Date updatedAt) {
        NotificationThread n = mock(NotificationThread.class);
        when(n.repository()).thenReturn(repository);
        when(n.updatedAt()).thenReturn(updatedAt);
        when(n.unread()).thenReturn(true);
        when(n.subject()).thenReturn(null);
        return n;
    }

    private User ownerWithId(Long id) {
        User u = mock(User.class);
        when(u.id()).thenReturn(id);
        when(u.login()).thenReturn("octo");
        when(u.avatarUrl()).thenReturn(null);
        return u;
    }

    @Test
    @Config(sdk = 25)
    public void createNotificationChannels_preO_returnsWithoutCreatingChannel() {
        NotificationsWorker.createNotificationChannels(mContext);
        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        // getNotificationChannel() is API 26+; ask the shadow instead.
        assertTrue(Shadows.shadowOf(nm).getNotificationChannels().isEmpty());
    }

    @Test
    public void doWork_oldButUnseenNotification_setsOnlyAlertOnce() {
        grantNotifications();
        long now = System.currentTimeMillis();
        Date old = new Date(now - 100000L);
        prefs().edit().putLong(KEY_LAST_CHECK, now).apply();
        NotificationListLoadResult result = oneGroupResult(old);
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            Set<String> ids = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
            assertNotNull(ids);
            assertTrue(ids.contains("123"));
            assertEquals(2, postedNotificationCount());
        }
    }

    @Test
    public void doWork_notificationWithNullUpdatedAtAndNegativeLastSeen_showsNotification() {
        grantNotifications();
        prefs().edit().putLong(KEY_LAST_SEEN, -1L).apply();
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(thread(r, null));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            assertEquals(2, postedNotificationCount());
        }
    }

    @Test
    public void doWork_allSubjectTypes_labelled() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        String[] types = {"Commit", "PullRequest", "Release", "Discussion", "Foo"};
        java.util.List<NotificationHolder> holders = new java.util.ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            Repository r = repo(201L + i, owner());
            holders.add(new NotificationHolder(r));
            holders.add(new NotificationHolder(
                    threadWithSubject(r, now, types[i], "Subject " + types[i])));
        }
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(holders)));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            // 5 repo notifications + 1 summary notification.
            assertEquals(6, postedNotificationCount());
        }
    }

    @Test
    public void doWork_subjectNull_usesEmptyLabel() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(threadWithNullSubject(r, now));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(2, postedNotificationCount());
        }
    }

    @Test
    public void doWork_subjectTitleNull_usesEmptyTitle() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(
                threadWithSubject(r, now, "Issue", null));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(2, postedNotificationCount());
        }
    }

    @Test
    public void doWork_twoNotificationsInOneGroup_showsCountInSummary() {
        grantNotifications();
        long now = System.currentTimeMillis();
        prefs().edit().putLong(KEY_LAST_CHECK, now).apply();
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        // Deliberately unsorted: older first, newer second, so the
        // hasNewNotification short-circuit sees both states.
        NotificationHolder older = new NotificationHolder(thread(r, new Date(now - 100000L)));
        NotificationHolder newer = new NotificationHolder(thread(r, new Date(now + 100000L)));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, older, newer))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(2, postedNotificationCount());
        }
    }

    @Test
    public void doWork_repoWithNullOwner_doesNotPostRepoNotification() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        Repository r = repo(123L, null);
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(thread(r, now));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            // Only the summary notification is posted, no repo notification.
            assertEquals(1, postedNotificationCount());
        }
    }

    @Test
    public void doWork_repoNull_doesNotPostRepoNotification() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        Repository headerRepo = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(headerRepo);
        NotificationHolder item = new NotificationHolder(thread(null, now));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            Set<String> ids = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
            assertNotNull(ids);
            assertTrue(ids.isEmpty());
            // Only the summary notification is posted, no repo notification.
            assertEquals(1, postedNotificationCount());
        }
    }

    @Test
    public void doWork_permissionRevokedMidFetch_skipsNotificationsButUpdatesState() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        NotificationListLoadResult result = oneGroupResult(now);
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenAnswer(invocation -> {
                        denyNotifications();
                        return Single.just(result);
                    });
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            Set<String> ids = prefs().getStringSet(KEY_LAST_REPO_IDS, null);
            assertNotNull(ids);
            assertTrue(ids.contains("123"));
            assertEquals(0, postedNotificationCount());
        }
    }

    @Test
    public void doWork_unauthorizedGraphQL_returnsFailure() {
        grantNotifications();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.error(
                            new GraphQLClient.UnauthorizedException("unauthorized")));
            assertEquals(ListenableWorker.Result.failure(), worker().doWork());
        }
    }

    @Test
    public void doWork_http500_returnsRetry() {
        grantNotifications();
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.error(new HttpException(Response.error(500,
                            ResponseBody.create(null, new byte[0])))));
            assertEquals(ListenableWorker.Result.retry(), worker().doWork());
        }
    }

    @Test(timeout = 120000)
    public void doWork_ownerWithRealAvatarId_postsNotification() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        Repository r = repo(123L, ownerWithId(1L));
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(thread(r, now));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(2, postedNotificationCount());
        }
    }


    @Test
    public void doWork_subjectTypeNull_usesEmptyLabel() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        Repository r = repo(123L, owner());
        NotificationHolder header = new NotificationHolder(r);
        NotificationHolder item = new NotificationHolder(
                threadWithSubject(r, now, null, "Some issue"));
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(new NotificationListLoadResult(
                            Arrays.asList(header, item))));
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertEquals(2, postedNotificationCount());
        }
    }

}
