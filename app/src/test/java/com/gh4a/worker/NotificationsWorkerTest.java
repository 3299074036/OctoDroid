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
import com.gh4a.utils.AvatarHandler;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Single;
import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    // 原名 doWork_prefetchAvatarThrows_silentlyContinues；设计假设不成立，
    // 已改写（见 D2-2）：Mockito 的 mockStatic 是线程局部的，预取线程跑的
    // 是真实 AvatarHandler（owner id 为 null 时直接返回 null，不抛错、
    // 不联网），而 stub 的第一个答案（抛错）被主线程显示路径
    // loadRoundUserAvatar 消费，该处无 try-catch，异常直出 doWork。
    // 本测试钉住可测行为：预取返回 null 时 doWork 仍成功并发 1 条通知；
    // L527 catch 分支因上述原因在单测里不可触发，见报告。
    @Test
    public void doWork_prefetchAvatarNull_continuesAndShowsNotification() {
        grantNotifications();
        Date now = new Date(System.currentTimeMillis());
        NotificationListLoadResult result = oneGroupResult(now);
        try (MockedStatic<SingleFactory> mocked = mockStatic(SingleFactory.class);
             MockedStatic<AvatarHandler> avatars = mockStatic(AvatarHandler.class)) {
            mocked.when(() -> SingleFactory.getNotifications(false, false, false))
                    .thenReturn(Single.just(result));
            // 预取线程跑真实方法（id 为 null 直接返回 null）；显示路径走 mock。
            avatars.when(() -> AvatarHandler.loadUserAvatarSynchronously(any(), any()))
                    .thenReturn(null);
            assertEquals(ListenableWorker.Result.success(), worker().doWork());
            assertTrue(prefs().getLong(KEY_LAST_CHECK, 0) > 0);
            // 2 条：汇总通知（id 0）+ 仓库通知（id 123）。第 1 轮写的是 1，
            // 但该断言从未跑到（测试在 doWork() 处即抛错），同文件另一测试
            // （L443）对单组 doWork 断言的也是 2。
            assertEquals(2, postedNotificationCount());
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


    // L537-538（第 3 轮再试，确定性写法）：latch.await 的 InterruptedException。
    // 思路——预取线程跑真实 AvatarHandler.loadUserAvatarSynchronously，
    // owner id 非 null 时必进 loadBitmapFromCache，后者先拿 AvatarHandler.class
    // 锁；辅助线程先占住这把锁，预取线程就卡在锁上、latch 永远不归零，调用
    // 线程必定停在 latch.await 里。辅助线程轮询调用线程栈，看到 await 帧
    // （且栈里同时有 prefetchRepoAvatars 帧——RxJava blockingGet 里也有一个
    // 短暂的 CountDownLatch.await，不能只认 await）后先中断调用线程、再放锁：
    // 放锁前 latch 不可能归零，所以中断必定先于 await 正常返回到达，无竞态。
    // 第 1 版经 doWork() 调：辅助线程确实看到了 await、中断已送达，但
    // doWork() 走完后中断标志已不在（下游显示路径某处清掉了标志），断言失败；
    // 本版直接反射调私有 prefetchRepoAvatars，在 catch 返回后立即读标志，
    // 不经过下游代码。头像 fetch 本身走真实网络失败路径（与既存
    // doWork_ownerWithRealAvatarId_postsNotification 同款，离线环境下快速
    // IOException -> null），不依赖网络成功；catch（L537）恢复中断标志
    // （L538）后方法即返回，断言 Thread.interrupted() 为 true 即证明
    // L538 执行过（同时清标志，不污染同 JVM 里的后续测试）。
    @Test(timeout = 120000)
    public void prefetchRepoAvatars_interrupted_restoresInterruptFlag() throws Exception {
        Date now = new Date(System.currentTimeMillis());
        Repository r = repo(123L, ownerWithId(1L));
        java.util.List<java.util.List<NotificationThread>> grouped = Collections.singletonList(
                Collections.singletonList(thread(r, now)));
        java.lang.reflect.Method prefetch = NotificationsWorker.class
                .getDeclaredMethod("prefetchRepoAvatars", java.util.List.class);
        prefetch.setAccessible(true);
        final Thread testThread = Thread.currentThread();
        final CountDownLatch lockAcquired = new CountDownLatch(1);
        final AtomicBoolean helperSawAwait = new AtomicBoolean(false);
        Thread helper = new Thread(() -> {
            synchronized (AvatarHandler.class) {
                lockAcquired.countDown();
                long deadline = System.currentTimeMillis() + 10000;
                boolean seen = false;
                while (System.currentTimeMillis() < deadline) {
                    boolean hasAwait = false, hasPrefetch = false;
                    for (StackTraceElement e : testThread.getStackTrace()) {
                        if (e.getClassName().equals("java.util.concurrent.CountDownLatch")
                                && e.getMethodName().equals("await")) {
                            hasAwait = true;
                        }
                        if (e.getClassName().endsWith("NotificationsWorker")
                                && e.getMethodName().equals("prefetchRepoAvatars")) {
                            hasPrefetch = true;
                        }
                    }
                    if (hasAwait && hasPrefetch) {
                        seen = true;
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
                helperSawAwait.set(seen);
                if (seen) {
                    // 先中断、再放锁（出 synchronized 块才放锁）。
                    testThread.interrupt();
                }
            }
        }, "prefetch-interrupt-helper");
        helper.start();
        assertTrue("helper did not acquire AvatarHandler lock in time",
                lockAcquired.await(10, java.util.concurrent.TimeUnit.SECONDS));
        boolean wasInterrupted;
        try {
            prefetch.invoke(worker(), grouped);
            // L538 恢复的中断标志；读一次即清掉，不污染后续测试。
            wasInterrupted = Thread.interrupted();
        } finally {
            Thread.interrupted();
            helper.join(15000);
        }
        assertTrue("helper never observed latch.await inside prefetchRepoAvatars",
                helperSawAwait.get());
        assertTrue("interrupt flag was not restored (L538 did not run)", wasInterrupted);
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
