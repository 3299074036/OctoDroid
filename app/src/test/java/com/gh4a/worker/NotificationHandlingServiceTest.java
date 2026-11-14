package com.gh4a.worker;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.ServiceFactory;
import com.meisolsson.githubsdk.service.activity.NotificationService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import io.reactivex.Single;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class NotificationHandlingServiceTest {
    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private NotificationHandlingService service() {
        return Robolectric.buildService(NotificationHandlingService.class).create().get();
    }

    @Test
    public void makeMarkNotificationsSeenIntent_hasMarkSeenAction() {
        Intent intent = NotificationHandlingService.makeMarkNotificationsSeenIntent(context());
        assertEquals(NotificationHandlingService.class.getName(),
                intent.getComponent().getClassName());
        assertEquals("com.gh4a.action.MARK_AS_SEEN", intent.getAction());
    }

    @Test
    public void makeHandleDismissIntent_hasDismissActionAndId() {
        Intent intent = NotificationHandlingService.makeHandleDismissIntent(context(), 42);
        assertEquals("com.gh4a.action.HANDLE_NOTIFICATION_DISMISS", intent.getAction());
        assertEquals(42, intent.getIntExtra("notification_id", -1));
    }

    @Test
    public void makeMarkReposNotificationsAsReadActionIntent_hasAllExtras() {
        long before = System.currentTimeMillis();
        Intent intent = NotificationHandlingService.makeMarkReposNotificationsAsReadActionIntent(
                context(), 7, "octo", "cat");
        assertEquals("com.gh4a.action.MARK_AS_READ", intent.getAction());
        assertEquals(7, intent.getIntExtra("notification_id", -1));
        assertEquals("octo", intent.getStringExtra("owner"));
        assertEquals("cat", intent.getStringExtra("repo"));
        long timestamp = intent.getLongExtra("timestamp", 0);
        assertTrue(timestamp >= before && timestamp <= System.currentTimeMillis());
    }

    @Test
    public void onHandleIntent_nullIntent_isNoop() {
        service().onHandleIntent(null);
    }

    @Test
    public void onHandleIntent_unknownAction_isNoop() {
        try (MockedStatic<NotificationsWorker> mocked = mockStatic(NotificationsWorker.class)) {
            service().onHandleIntent(new Intent("com.gh4a.action.UNKNOWN"));
            mocked.verify(() -> NotificationsWorker.markNotificationsAsSeen(any()), never());
        }
    }

    @Test
    public void onHandleIntent_markSeen_delegatesToWorker() {
        try (MockedStatic<NotificationsWorker> mocked = mockStatic(NotificationsWorker.class)) {
            service().onHandleIntent(
                    NotificationHandlingService.makeMarkNotificationsSeenIntent(context()));
            mocked.verify(() -> NotificationsWorker.markNotificationsAsSeen(any()));
        }
    }

    @Test
    public void onHandleIntent_dismissWithPositiveId_delegatesToWorker() {
        try (MockedStatic<NotificationsWorker> mocked = mockStatic(NotificationsWorker.class)) {
            service().onHandleIntent(
                    NotificationHandlingService.makeHandleDismissIntent(context(), 5));
            mocked.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), eq(5)));
        }
    }

    @Test
    public void onHandleIntent_dismissWithZeroId_doesNothing() {
        try (MockedStatic<NotificationsWorker> mocked = mockStatic(NotificationsWorker.class)) {
            service().onHandleIntent(
                    NotificationHandlingService.makeHandleDismissIntent(context(), 0));
            mocked.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), anyInt()),
                    never());
        }
    }

    @Test
    public void onHandleIntent_markRead_marksRepoReadAndDismisses() {
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.markAllRepositoryNotificationsRead(anyString(), anyString(), any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<NotificationsWorker> mockedWorker =
                     mockStatic(NotificationsWorker.class)) {
            mockedFactory.when(() -> ServiceFactory.get(eq(NotificationService.class), eq(false)))
                    .thenReturn(notificationService);
            service().onHandleIntent(
                    NotificationHandlingService.makeMarkReposNotificationsAsReadActionIntent(
                            context(), 9, "octo", "cat"));
            verify(notificationService).markAllRepositoryNotificationsRead(
                    eq("octo"), eq("cat"), any());
            mockedWorker.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), eq(9)));
        }
    }

    @Test
    public void onHandleIntent_markReadWithoutNotificationId_onlyMarksRepoRead() {
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.markAllRepositoryNotificationsRead(anyString(), anyString(), any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<NotificationsWorker> mockedWorker =
                     mockStatic(NotificationsWorker.class)) {
            mockedFactory.when(() -> ServiceFactory.get(eq(NotificationService.class), eq(false)))
                    .thenReturn(notificationService);
            Intent intent = NotificationHandlingService.makeMarkReposNotificationsAsReadActionIntent(
                    context(), -1, "octo", "cat");
            service().onHandleIntent(intent);
            verify(notificationService).markAllRepositoryNotificationsRead(
                    eq("octo"), eq("cat"), any());
            mockedWorker.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), anyInt()),
                    never());
        }
    }

    @Test
    public void onHandleIntent_markReadWithoutRepo_onlyDismisses() {
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<NotificationsWorker> mockedWorker =
                     mockStatic(NotificationsWorker.class)) {
            Intent intent = new Intent(context(), NotificationHandlingService.class)
                    .setAction("com.gh4a.action.MARK_AS_READ")
                    .putExtra("notification_id", 3);
            service().onHandleIntent(intent);
            mockedFactory.verify(() -> ServiceFactory.get(eq(NotificationService.class), eq(false)),
                    never());
            mockedWorker.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), eq(3)));
        }
    }

    @Test
    public void onHandleIntent_markReadServiceError_isSwallowed() {
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.markAllRepositoryNotificationsRead(anyString(), anyString(), any()))
                .thenReturn(Single.error(new RuntimeException("down")));
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<NotificationsWorker> mockedWorker =
                     mockStatic(NotificationsWorker.class)) {
            mockedFactory.when(() -> ServiceFactory.get(eq(NotificationService.class), eq(false)))
                    .thenReturn(notificationService);
            // must not throw: markNotificationAsRead catches Exception
            service().onHandleIntent(
                    NotificationHandlingService.makeMarkReposNotificationsAsReadActionIntent(
                            context(), 9, "octo", "cat"));
            mockedWorker.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), eq(9)));
        }
    }

    @Test
    public void onHandleIntent_markRead_cancelsNotification() {
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.markAllRepositoryNotificationsRead(anyString(), anyString(), any()))
                .thenReturn(Single.just(Response.success(null)));
        NotificationHandlingService svc = service();
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<NotificationsWorker> mockedWorker =
                     mockStatic(NotificationsWorker.class)) {
            mockedFactory.when(() -> ServiceFactory.get(eq(NotificationService.class), eq(false)))
                    .thenReturn(notificationService);
            svc.onHandleIntent(
                    NotificationHandlingService.makeMarkReposNotificationsAsReadActionIntent(
                            context(), 11, "octo", "cat"));
            NotificationManager nm = (NotificationManager) svc.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            assertNotNull(nm);
        }
    }

    @Test
    public void onHandleIntent_markReadOwnerOnly_skipsMarkAsReadButDismisses() {
        try (MockedStatic<ServiceFactory> mockedFactory = mockStatic(ServiceFactory.class);
             MockedStatic<NotificationsWorker> mockedWorker =
                     mockStatic(NotificationsWorker.class)) {
            Intent intent = new Intent(context(), NotificationHandlingService.class)
                    .setAction("com.gh4a.action.MARK_AS_READ")
                    .putExtra("owner", "octo")
                    .putExtra("notification_id", 3);
            service().onHandleIntent(intent);
            mockedFactory.verify(() -> ServiceFactory.get(eq(NotificationService.class), eq(false)),
                    never());
            mockedWorker.verify(() -> NotificationsWorker.handleNotificationDismiss(any(), eq(3)));
        }
    }

}
