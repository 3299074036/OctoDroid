package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.adapter.NotificationAdapter;
import com.gh4a.model.NotificationHolder;
import com.gh4a.model.NotificationListLoadResult;
import com.gh4a.resolver.BrowseFilter;
import com.gh4a.utils.SingleFactory;
import com.gh4a.worker.NotificationsWorker;
import com.meisolsson.githubsdk.model.NotificationSubject;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.Subscription;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.model.request.NotificationReadRequest;
import com.meisolsson.githubsdk.model.request.activity.SubscriptionRequest;
import com.meisolsson.githubsdk.service.activity.NotificationService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowToast;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.subjects.PublishSubject;
import retrofit2.Response;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * NotificationListFragment (F1): newInstance, empty text, attach guard and
 * the NotificationDiffCallback.
 * Loading/mark-as-read/unsubscribe paths subscribe network Singles and are
 * intentionally not exercised (see report).
 */
@RunWith(RobolectricTestRunner.class)
public class NotificationListFragmentTest {

    @Test
    public void newInstance_returnsFragment() {
        assertNotNull(NotificationListFragment.newInstance());
    }

    @Test
    public void getEmptyTextResId_isNoNotifications() {
        assertEquals(R.string.no_notifications_found,
                new NotificationListFragment().getEmptyTextResId());
    }

    @Test
    public void onAttach_withoutParentCallback_throws() {
        NotificationListFragment f = NotificationListFragment.newInstance();
        FragmentTestHelper.assertThrows(IllegalStateException.class,
                () -> FragmentTestHelper.attach(f));
    }

    @Test
    public void onSaveInstanceState_bareInstance_persistsEmptyState() {
        NotificationListFragment f = NotificationListFragment.newInstance();
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertTrue(out.containsKey("loaded_notifications"));
        assertEquals(0, out.getParcelableArrayList("loaded_notifications").size());
        assertEquals(false, out.getBoolean("list_truncated"));
        assertEquals(0L, out.getLong("notifications_load_time"));
        assertEquals(false, out.getBoolean("load_in_progress"));
    }

    // ---- NotificationDiffCallback (private static nested class) ----

    private static Object diffCallback(List<NotificationHolder> oldItems,
            List<NotificationHolder> newItems) {
        try {
            Class<?> callbackClass = null;
            for (Class<?> c : NotificationListFragment.class.getDeclaredClasses()) {
                if (c.getSimpleName().equals("NotificationDiffCallback")) {
                    callbackClass = c;
                }
            }
            assertNotNull(callbackClass);
            Constructor<?> ctor = callbackClass
                    .getDeclaredConstructor(List.class, List.class);
            ctor.setAccessible(true);
            return ctor.newInstance(oldItems, newItems);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static NotificationHolder holderWithRepo(String fullName) {
        Repository repo = Repository.builder()
                .owner(User.builder().login("o").build())
                .name(fullName.contains("/") ? fullName.split("/")[1] : fullName)
                .fullName(fullName).build();
        return new NotificationHolder(repo);
    }

    private static NotificationHolder holderWithNotification(String id, boolean unread) {
        NotificationThread thread = mock(NotificationThread.class);
        when(thread.id()).thenReturn(id);
        when(thread.unread()).thenReturn(unread);
        when(thread.repository()).thenReturn(null);
        return new NotificationHolder(thread);
    }

    private static int callInt(Object target, String name, int a, int b) {
        try {
            java.lang.reflect.Method m = target.getClass()
                    .getDeclaredMethod(name, int.class, int.class);
            m.setAccessible(true);
            return (int) m.invoke(target, a, b);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static boolean callBool(Object target, String name, int a, int b) {
        try {
            java.lang.reflect.Method m = target.getClass()
                    .getDeclaredMethod(name, int.class, int.class);
            m.setAccessible(true);
            return (boolean) m.invoke(target, a, b);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static int callSize(Object target, String name) {
        try {
            java.lang.reflect.Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return (int) m.invoke(target);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void diffCallback_sizes() {
        Object cb = diffCallback(
                Arrays.asList(holderWithRepo("o/r1"), holderWithRepo("o/r2")),
                Arrays.asList(holderWithRepo("o/r1")));
        assertEquals(2, callSize(cb, "getOldListSize"));
        assertEquals(1, callSize(cb, "getNewListSize"));
    }

    @Test
    public void diffCallback_areItemsTheSame_nullNotifications_sameRepo() {
        Object cb = diffCallback(
                Arrays.asList(holderWithRepo("o/r1")),
                Arrays.asList(holderWithRepo("o/r1")));
        assertTrue(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areItemsTheSame_nullNotifications_differentRepo() {
        Object cb = diffCallback(
                Arrays.asList(holderWithRepo("o/r1")),
                Arrays.asList(holderWithRepo("o/r2")));
        assertFalse(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areItemsTheSame_nullNotifications_bothReposNull() {
        Object cb = diffCallback(
                Arrays.asList(new NotificationHolder((Repository) null)),
                Arrays.asList(new NotificationHolder((Repository) null)));
        assertTrue(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areItemsTheSame_nullNotifications_oneRepoNull() {
        Object cb = diffCallback(
                Arrays.asList(new NotificationHolder((Repository) null)),
                Arrays.asList(holderWithRepo("o/r1")));
        assertFalse(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areItemsTheSame_withNotifications_sameId() {
        Object cb = diffCallback(
                Arrays.asList(holderWithNotification("5", true)),
                Arrays.asList(holderWithNotification("5", false)));
        assertTrue(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areItemsTheSame_withNotifications_differentId() {
        Object cb = diffCallback(
                Arrays.asList(holderWithNotification("5", true)),
                Arrays.asList(holderWithNotification("6", true)));
        assertFalse(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areItemsTheSame_mixedNullAndNonNullNotification() {
        // One side has a notification, the other only a repo holder:
        // the null-notification branch requires BOTH null, so this is false.
        Object cb = diffCallback(
                Arrays.asList(holderWithNotification("5", true)),
                Arrays.asList(holderWithRepo("o/r1")));
        assertFalse(callBool(cb, "areItemsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areContentsTheSame_identicalFlags() {
        NotificationHolder a = holderWithNotification("1", true);
        NotificationHolder b = holderWithNotification("1", true);
        Object cb = diffCallback(Arrays.asList(a), Arrays.asList(b));
        assertTrue(callBool(cb, "areContentsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areContentsTheSame_readDiffers() {
        NotificationHolder a = holderWithNotification("1", true);
        NotificationHolder b = holderWithNotification("1", false);
        Object cb = diffCallback(Arrays.asList(a), Arrays.asList(b));
        assertFalse(callBool(cb, "areContentsTheSame", 0, 0));
    }

    @Test
    public void diffCallback_areContentsTheSame_lastFlagDiffers() {
        NotificationHolder a = holderWithNotification("1", true);
        NotificationHolder b = holderWithNotification("1", true);
        b.setIsLastRepositoryNotification(true);
        Object cb = diffCallback(Arrays.asList(a), Arrays.asList(b));
        assertFalse(callBool(cb, "areContentsTheSame", 0, 0));
    }

    @Test
    public void notificationHolder_repoConstructor_fields() {
        Repository repo = Repository.builder().fullName("o/r").build();
        NotificationHolder h = new NotificationHolder(repo);
        assertEquals(null, h.notification);
        assertEquals(repo, h.repository);
        assertEquals(false, h.isRead());
        assertEquals(false, h.isLastRepositoryNotification());
    }

    @Test
    public void notificationHolder_threadConstructor_unreadFalseMeansRead() {
        NotificationThread thread = mock(NotificationThread.class);
        when(thread.unread()).thenReturn(false);
        when(thread.repository()).thenReturn(null);
        NotificationHolder h = new NotificationHolder(thread);
        assertTrue(h.isRead());
        h.setIsRead(false);
        assertFalse(h.isRead());
        h.setIsLastRepositoryNotification(true);
        assertTrue(h.isLastRepositoryNotification());
    }

    // ---- harness：ParentCallback 宿主 + 反射 + 服务 mock ----

    /** 实现 ParentCallback 的轻量宿主（HomeActivity 太重，这里只记 indicator）。 */
    public static class CallbackHostActivity extends FragmentActivity
            implements NotificationListFragment.ParentCallback {
        boolean indicatorVisible = false;

        @Override
        public void setNotificationsIndicatorVisible(boolean visible) {
            indicatorVisible = visible;
        }
    }

    private static CallbackHostActivity attachResumed(NotificationListFragment f) {
        return FragmentTestHelper.attachTo(CallbackHostActivity.class, f);
    }

    /** 带 intent 的宿主：scroll 定位的 extras 必须在 attach 前就进 intent。 */
    private static CallbackHostActivity attachWithIntent(NotificationListFragment f,
            Intent intent) {
        CallbackHostActivity activity =
                Robolectric.buildActivity(CallbackHostActivity.class, intent).setup().get();
        // 挂进 android.R.id.content：view 挂到窗口，View.postDelayed 才能走
        // 主 looper；无容器 add 时 view 不 attach，延迟任务进 RunQueue 永不跑。
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        return activity;
    }

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void idleForMs(long millis) {
        Shadows.shadowOf(android.os.Looper.getMainLooper())
                .idleFor(java.time.Duration.ofMillis(millis));
    }

    // RootAdapter.highlight 写进的字段在重绑时被消费（置回 -1），不是稳定断言；
    // 稳定可观测量是滚动本身：findFirstVisibleItemPosition()。
    private static int firstVisiblePosition(NotificationListFragment f) throws Exception {
        return ((androidx.recyclerview.widget.LinearLayoutManager)
                field(f, "mLayoutManager")).findFirstVisibleItemPosition();
    }

    private static Object field(NotificationListFragment f, String name) throws Exception {
        return field((Object) f, name);
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field fld = c.getDeclaredField(name);
                fld.setAccessible(true);
                return fld.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(NotificationListFragment f, String name, Object value)
            throws Exception {
        Class<?> c = f.getClass();
        while (c != null) {
            try {
                Field fld = c.getDeclaredField(name);
                fld.setAccessible(true);
                fld.set(f, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(NotificationListFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Class<?> c = NotificationListFragment.class;
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(f, args);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMain();
        }
    }

    private static Repository repo(String fullName) {
        String[] parts = fullName.split("/", 2);
        return Repository.builder()
                .owner(User.builder().login(parts[0]).build())
                .name(parts[1]).fullName(fullName).build();
    }

    private static NotificationThread thread(String id, boolean unread, Repository repository) {
        return thread(id, unread, repository, new Date(1700000000000L));
    }

    private static NotificationThread thread(String id, boolean unread, Repository repository,
            Date updatedAt) {
        NotificationThread t = mock(NotificationThread.class);
        when(t.id()).thenReturn(id);
        when(t.unread()).thenReturn(unread);
        when(t.repository()).thenReturn(repository);
        when(t.updatedAt()).thenReturn(updatedAt);
        // binder 要 subject.type()/title()：挂载后 RecyclerView 真 bind holder，
        // 不 stub 就 NPE（NotificationAdapter:162）。
        NotificationSubject subject = mock(NotificationSubject.class);
        when(subject.type()).thenReturn("Issue");
        when(subject.title()).thenReturn("title");
        when(t.subject()).thenReturn(subject);
        return t;
    }

    @SuppressWarnings("unchecked")
    private static List<NotificationThread> loaded(NotificationListFragment f) throws Exception {
        return (List<NotificationThread>) field(f, "mLoadedNotifications");
    }

    /** 桩 SingleFactory.getNotificationsPaged：字面量组合（mockStatic 里 anyBoolean 会炸）。 */
    private static MockedStatic<SingleFactory> mockPaged(
            Observable<List<NotificationThread>> result) {
        MockedStatic<SingleFactory> mocked =
                mockStatic(SingleFactory.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> SingleFactory.getNotificationsPaged(false, false, false))
                .thenReturn(result);
        mocked.when(() -> SingleFactory.getNotificationsPaged(false, false, true))
                .thenReturn(result);
        mocked.when(() -> SingleFactory.getNotificationsPaged(true, false, true))
                .thenReturn(result);
        mocked.when(() -> SingleFactory.getNotificationsPaged(false, true, true))
                .thenReturn(result);
        return mocked;
    }

    private static MockedStatic<NotificationsWorker> mockWorker(long lastCheck) {
        MockedStatic<NotificationsWorker> mocked = mockStatic(NotificationsWorker.class);
        mocked.when(() -> NotificationsWorker.getLastCheckTimestamp(any()))
                .thenReturn(lastCheck);
        return mocked;
    }

    private static MockedStatic<ServiceFactory> mockNotificationService(
            NotificationService service) {
        MockedStatic<ServiceFactory> mocked =
                mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> ServiceFactory.get(NotificationService.class, false))
                .thenReturn(service);
        mocked.when(() -> ServiceFactory.get(NotificationService.class, true))
                .thenReturn(service);
        return mocked;
    }

    private static void waitForLoad(NotificationListFragment f) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (Boolean.TRUE.equals(field(f, "mLoadInProgress"))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMain();
        }
    }

    // ---- Chunk A：onCreate 恢复 / onSaveInstanceState / onStart / onRefresh ----

    private static androidx.fragment.app.Fragment.SavedState savedState(Bundle bundle) {
        try {
            Constructor<androidx.fragment.app.Fragment.SavedState> ctor =
                    androidx.fragment.app.Fragment.SavedState.class
                            .getDeclaredConstructor(Bundle.class);
            ctor.setAccessible(true);
            return ctor.newInstance(bundle);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Bundle restoreBundle(List<NotificationThread> restored, long loadTime,
            boolean loadInProgress, boolean truncated) {
        Bundle b = new Bundle();
        if (restored != null) {
            b.putParcelableArrayList("loaded_notifications",
                    new ArrayList<>(restored));
        }
        b.putLong("notifications_load_time", loadTime);
        b.putBoolean("load_in_progress", loadInProgress);
        b.putBoolean("list_truncated", truncated);
        return b;
    }

    @Test
    public void onCreate_restoredInProgress_discardsData() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    Arrays.asList(thread("1", true, repo("o/r"))),
                    1700000000000L, true, false)));
            attachResumed(f);
            assertTrue(loaded(f).isEmpty());
            assertNull(field(f, "mNotificationsLoadTime"));
            assertFalse((Boolean) field(f, "mHasRestoredData"));
            assertEquals(0, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void onCreate_restoredTruncated_discardsData() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    Arrays.asList(thread("1", true, repo("o/r"))),
                    1700000000000L, false, true)));
            attachResumed(f);
            assertTrue(loaded(f).isEmpty());
            assertNull(field(f, "mNotificationsLoadTime"));
            assertFalse((Boolean) field(f, "mHasRestoredData"));
            assertEquals(0, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void onCreate_restoredList_restoresData() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    Arrays.asList(thread("1", true, repo("o/r"))),
                    1700000000000L, false, false)));
            CallbackHostActivity activity = attachResumed(f);
            // onStart 不加载（lastCheck==0）：恢复的数据原样保留并进了 adapter
            //（仓库头 + 通知行 = 2 项），indicator 亮起。
            assertEquals(1, loaded(f).size());
            assertEquals(new Date(1700000000000L), field(f, "mNotificationsLoadTime"));
            assertEquals(2, ((NotificationAdapter) field(f, "mAdapter")).getCount());
            assertTrue(activity.indicatorVisible);
        }
    }

    @Test
    public void onCreate_restoredList_allFilter_skipsIndicator() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    Arrays.asList(thread("1", true, repo("o/r"))),
                    1700000000000L, false, false)));
            setField(f, "mAll", true);
            CallbackHostActivity activity = attachResumed(f);
            // 恢复的数据进了 adapter，但 mAll 时不碰 indicator。
            assertEquals(2, ((NotificationAdapter) field(f, "mAdapter")).getCount());
            assertFalse(activity.indicatorVisible);
        }
    }

    @Test
    public void onCreate_restoredList_participatingFilter_skipsIndicator() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    Arrays.asList(thread("1", true, repo("o/r"))),
                    1700000000000L, false, false)));
            setField(f, "mParticipating", true);
            CallbackHostActivity activity = attachResumed(f);
            assertEquals(2, ((NotificationAdapter) field(f, "mAdapter")).getCount());
            assertFalse(activity.indicatorVisible);
        }
    }

    @Test
    public void onCreate_restoredListZeroLoadTime_restoresNullTime() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    Arrays.asList(thread("1", true, repo("o/r"))), 0L, false, false)));
            attachResumed(f);
            // loadTime==0 → 恢复时间 null；恢复的数据在 onStart 加载前已同步进
            // adapter（2 项），加载本身 never() 不投递。
            assertNull(field(f, "mNotificationsLoadTime"));
            assertEquals(2, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void onCreate_restoredEmpty_skipsRestore() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    new ArrayList<>(), 1700000000000L, false, false)));
            attachResumed(f);
            assertTrue(loaded(f).isEmpty());
            assertNull(field(f, "mNotificationsLoadTime"));
            assertFalse((Boolean) field(f, "mHasRestoredData"));
            assertEquals(0, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void onCreate_restoredNull_skipsRestore() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never());
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            f.setInitialSavedState(savedState(restoreBundle(
                    null, 1700000000000L, false, false)));
            attachResumed(f);
            assertTrue(loaded(f).isEmpty());
            assertNull(field(f, "mNotificationsLoadTime"));
            assertFalse((Boolean) field(f, "mHasRestoredData"));
            assertEquals(0, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void onSaveInstanceState_withItems_truncatesAt200() throws Exception {
        NotificationListFragment f = NotificationListFragment.newInstance();
        for (int i = 0; i < 201; i++) {
            loaded(f).add(thread(String.valueOf(i), true, repo("o/r")));
        }
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals(200, out.getParcelableArrayList("loaded_notifications").size());
        assertTrue(out.getBoolean("list_truncated"));
        assertEquals(0L, out.getLong("notifications_load_time"));
        assertFalse(out.getBoolean("load_in_progress"));
    }

    @Test
    public void onSaveInstanceState_withLoadTimeAndInProgress_persistsBoth() throws Exception {
        NotificationListFragment f = NotificationListFragment.newInstance();
        setField(f, "mNotificationsLoadTime", new Date(1700000000000L));
        setField(f, "mLoadInProgress", true);
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals(1700000000000L, out.getLong("notifications_load_time"));
        assertTrue(out.getBoolean("load_in_progress"));
        assertFalse(out.getBoolean("list_truncated"));
    }

    @Test
    public void onStart_freshLoad_loadsWithoutForce() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            assertFalse((Boolean) field(f, "mLoadInProgress"));
            assertNotNull(field(f, "mNotificationsLoadTime"));
            assertFalse(activity.indicatorVisible);
            worker.verify(() -> NotificationsWorker.markNotificationsAsSeen(any()));
        }
    }

    @Test
    public void onStart_staleLoad_loadsWithForce() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(Arrays.asList(t)));
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            assertEquals(1, loaded(f).size());
            // 旧的 fetch 时间 + 更新的 check 时间：第二次 onStart 走强制重载。
            setField(f, "mNotificationsLoadTime", new Date(1700000000000L));
            worker.when(() -> NotificationsWorker.getLastCheckTimestamp(any()))
                    .thenReturn(1700000001000L);
            f.onStart();
            waitForLoad(f);
            assertEquals(1, loaded(f).size());
            worker.verify(() -> NotificationsWorker.markNotificationsAsSeen(any()), times(2));
        }
    }

    @Test
    public void onStart_freshCheckOlder_skipsLoad() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            // 已有 fetch 时间且 check 更旧：onStart 不加载，已加载列表原样保留。
            loaded(f).add(thread("9", true, repo("o/r")));
            setField(f, "mNotificationsLoadTime", new Date(1700000002000L));
            worker.when(() -> NotificationsWorker.getLastCheckTimestamp(any()))
                    .thenReturn(1700000001000L);
            f.onStart();
            idleMain();
            assertEquals(1, loaded(f).size());
            worker.verify(() -> NotificationsWorker.markNotificationsAsSeen(any()), times(1));
        }
    }

    @Test
    public void onStart_lastCheckZero_skipsLoad() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<NotificationsWorker> worker = mockWorker(0L)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            loaded(f).add(thread("9", true, repo("o/r")));
            setField(f, "mNotificationsLoadTime", new Date(1700000002000L));
            // lastCheck==0：条件里的 lastCheck!=0 为 false，不加载。
            worker.when(() -> NotificationsWorker.getLastCheckTimestamp(any()))
                    .thenReturn(0L);
            f.onStart();
            idleMain();
            assertEquals(1, loaded(f).size());
        }
    }

    @Test
    public void onRefresh_adapterNull_loadsWithoutClear() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            setField(f, "mAdapter", null);
            f.onRefresh();
            waitForLoad(f);
            assertFalse((Boolean) field(f, "mLoadInProgress"));
        }
    }

    @Test
    public void onRefresh_adapterPresent_clearsAndLoads() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            adapter.addAll(Arrays.asList(new NotificationHolder(repo("o/r"))));
            assertEquals(1, adapter.getCount());
            f.onRefresh();
            waitForLoad(f);
            assertEquals(0, adapter.getCount());
        }
    }

    // ---- Chunk B：菜单 / 点击 / 已读确认 ----

    private static MenuItem menuItem(int id) {
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(id);
        return item;
    }

    private static Menu createOptionsMenu(NotificationListFragment f,
            CallbackHostActivity activity) throws Exception {
        Menu menu = new MenuBuilder(activity);
        f.onCreateOptionsMenu(menu, new SupportMenuInflater(activity));
        return menu;
    }

    @Test
    public void onCreateOptionsMenu_inflatesMenuAndHidesMarkAllWhenEmpty() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            Menu menu = createOptionsMenu(f, activity);
            assertNotNull(field(f, "mMarkAllAsReadMenuItem"));
            // 空列表、无未读：全部已读按钮隐藏。
            assertFalse(menu.findItem(com.gh4a.R.id.mark_all_as_read).isVisible());
        }
    }

    @Test
    public void updateMenuItemVisibility_withUnread_showsItem() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            Menu menu = createOptionsMenu(f, activity);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            adapter.addAll(Arrays.asList(
                    new NotificationHolder(thread("1", true, repo("o/r")))));
            call(f, "updateMenuItemVisibility", new Class<?>[]{});
            assertTrue(menu.findItem(com.gh4a.R.id.mark_all_as_read).isVisible());
        }
    }

    @Test
    public void updateMenuItemVisibility_contentHidden_hidesItem() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never())) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            Menu menu = createOptionsMenu(f, activity);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            adapter.addAll(Arrays.asList(
                    new NotificationHolder(thread("1", true, repo("o/r")))));
            call(f, "updateMenuItemVisibility", new Class<?>[]{});
            // 内容未显示（加载中）：即使有未读也不显示。
            assertFalse(menu.findItem(com.gh4a.R.id.mark_all_as_read).isVisible());
        }
    }

    @Test
    public void onOptionsItemSelected_markAll_showsConfirmDialog() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            assertTrue(f.onOptionsItemSelected(menuItem(com.gh4a.R.id.mark_all_as_read)));
            activity.getSupportFragmentManager().executePendingTransactions();
            assertNotNull(activity.getSupportFragmentManager()
                    .findFragmentByTag("markallreadconfirm"));
        }
    }

    @Test
    public void onOptionsItemSelected_filterAll_setsFlagsAndRefreshes() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            MenuItem item = menuItem(com.gh4a.R.id.notification_filter_all);
            assertTrue(f.onOptionsItemSelected(item));
            verify(item).setChecked(true);
            assertTrue((Boolean) field(f, "mAll"));
            assertFalse((Boolean) field(f, "mParticipating"));
            waitForLoad(f);
        }
    }

    @Test
    public void onOptionsItemSelected_filterParticipating_setsFlags() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            MenuItem item = menuItem(com.gh4a.R.id.notification_filter_participating);
            assertTrue(f.onOptionsItemSelected(item));
            verify(item).setChecked(true);
            assertFalse((Boolean) field(f, "mAll"));
            assertTrue((Boolean) field(f, "mParticipating"));
            waitForLoad(f);
        }
    }

    @Test
    public void onOptionsItemSelected_filterUnread_clearsFlags() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            setField(f, "mAll", true);
            setField(f, "mParticipating", true);
            MenuItem item = menuItem(com.gh4a.R.id.notification_filter_unread);
            assertTrue(f.onOptionsItemSelected(item));
            verify(item).setChecked(true);
            assertFalse((Boolean) field(f, "mAll"));
            assertFalse((Boolean) field(f, "mParticipating"));
            waitForLoad(f);
        }
    }

    @Test
    public void onOptionsItemSelected_unknown_delegatesToSuper() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            assertFalse(f.onOptionsItemSelected(menuItem(12345)));
        }
    }

    @Test
    public void onItemClick_repoHolder_opensRepository() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            f.onItemClick(new NotificationHolder(repo("o/r")));
            Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(started);
            assertTrue(started.getComponent().getClassName()
                    .contains("RepositoryActivity"));
        }
    }

    @Test
    public void onItemClick_notificationWithUrl_marksReadAndOpens() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationSubject subject = mock(NotificationSubject.class);
        when(subject.url()).thenReturn("https://api.github.com/repos/o/r/issues/1");
        when(t.subject()).thenReturn(subject);
        NotificationService service = mock(NotificationService.class);
        when(service.markNotificationRead("1"))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service);
             MockedStatic<BrowseFilter> browse = mockStatic(BrowseFilter.class)) {
            browse.when(() -> BrowseFilter.makeRedirectionIntent(any(), any(), any()))
                    .thenReturn(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/o/r/issues/1")));
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            NotificationHolder holder = new NotificationHolder(t);
            adapter.addAll(Arrays.asList(holder));
            f.onItemClick(holder);
            assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
            verify(service).markNotificationRead("1");
            waitUntil(holder::isRead);
            assertTrue(holder.isRead());
        }
    }

    @Test
    public void onItemClick_notificationWithoutUrl_doesNothing() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationSubject subject = mock(NotificationSubject.class);
        when(subject.url()).thenReturn(null);
        when(t.subject()).thenReturn(subject);
        NotificationService service = mock(NotificationService.class);
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            f.onItemClick(new NotificationHolder(t));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
            verify(service, never()).markNotificationRead(any());
        }
    }

    @Test
    public void markAsRead_repoHolder_showsConfirmDialog() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            f.markAsRead(new NotificationHolder(repo("o/r")));
            activity.getSupportFragmentManager().executePendingTransactions();
            assertNotNull(activity.getSupportFragmentManager()
                    .findFragmentByTag("markreadconfirm"));
        }
    }

    @Test
    public void markAsRead_threadHolder_marksSingleRead() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        when(service.markNotificationRead("1"))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            NotificationHolder holder = new NotificationHolder(t);
            adapter.addAll(Arrays.asList(holder));
            f.markAsRead(holder);
            verify(service).markNotificationRead("1");
            waitUntil(holder::isRead);
            assertTrue(holder.isRead());
        }
    }

    @Test
    public void onConfirmed_callsMarkAsReadForRepo() throws Exception {
        NotificationService service = mock(NotificationService.class);
        when(service.markAllRepositoryNotificationsRead(any(), any(), any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            f.onConfirmed("markreadconfirm", repo("o/r"));
            verify(service).markAllRepositoryNotificationsRead(any(), any(), any());
        }
    }

    // ---- Chunk C：取消订阅 / 已读分支 / 滚动定位 / 加载成败 / 销毁 ----

    @Test
    public void unsubscribe_success_showsToast() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        when(service.setNotificationThreadSubscription(any(), any()))
                .thenReturn(Single.just(Response.success(mock(Subscription.class))));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            f.unsubscribe(new NotificationHolder(t));
            verify(service).setNotificationThreadSubscription(any(), any());
            waitUntil(() -> ShadowToast.getTextOfLatestToast() != null);
            assertEquals(activity.getString(com.gh4a.R.string.unsubscribe_success),
                    ShadowToast.getTextOfLatestToast().toString());
        }
    }

    @Test
    public void unsubscribe_error_isSwallowed() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        java.util.concurrent.atomic.AtomicBoolean errorEmitted =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        when(service.setNotificationThreadSubscription(any(), any()))
                .thenReturn(Single.<Response<Subscription>>error(new RuntimeException("boom"))
                        .doOnError(e -> errorEmitted.set(true)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            // 宿主不是 BaseActivity：handleActionFailure 静默跳过，只不断言不崩。
            // 单次 idleMain 不够：IO 线程还没发 error，等发射后再 drain 主 looper，
            // 否则 error 消费者（279 行）跑不到。
            f.unsubscribe(new NotificationHolder(t));
            waitUntil(errorEmitted::get);
            idleMain();
            verify(service).setNotificationThreadSubscription(any(), any());
        }
    }

    @Test
    public void markAsRead_alreadyReadNotification_returnsEarly() throws Exception {
        NotificationThread t = thread("1", false, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            f.markAsRead(new NotificationHolder(t));
            idleMain();
            verify(service, never()).markNotificationRead(any());
        }
    }

    @Test
    public void markAsRead_repoMarksAllRepoRead() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        when(service.markAllRepositoryNotificationsRead(any(), any(), any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            NotificationHolder holder = new NotificationHolder(t);
            adapter.addAll(Arrays.asList(new NotificationHolder(repo("o/r")), holder));
            activity.indicatorVisible = true;
            call(f, "markAsRead", new Class<?>[]{Repository.class, NotificationThread.class},
                    repo("o/r"), null);
            verify(service).markAllRepositoryNotificationsRead(any(), any(), any());
            waitUntil(holder::isRead);
            assertTrue(holder.isRead());
            // adapter 标完已读：indicator 熄灭。
            waitUntil(() -> !activity.indicatorVisible);
            assertFalse(activity.indicatorVisible);
        }
    }

    @Test
    public void markAsRead_allMarksEverythingRead() throws Exception {
        NotificationThread t1 = thread("1", true, repo("o/r1"));
        NotificationThread t2 = thread("2", true, repo("o/r2"));
        NotificationService service = mock(NotificationService.class);
        when(service.markAllNotificationsRead(any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            NotificationHolder h1 = new NotificationHolder(t1);
            NotificationHolder h2 = new NotificationHolder(t2);
            adapter.addAll(Arrays.asList(h1, h2));
            call(f, "markAsRead", new Class<?>[]{Repository.class, NotificationThread.class},
                    null, null);
            verify(service).markAllNotificationsRead(any());
            waitUntil(() -> h1.isRead() && h2.isRead());
            assertTrue(h1.isRead());
            assertTrue(h2.isRead());
        }
    }

    @Test
    public void handleMarkAsRead_allFilter_keepsIndicator() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        when(service.markAllRepositoryNotificationsRead(any(), any(), any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            setField(f, "mAll", true);
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            NotificationHolder holder = new NotificationHolder(t);
            adapter.addAll(Arrays.asList(new NotificationHolder(repo("o/r")), holder));
            activity.indicatorVisible = true;
            call(f, "markAsRead", new Class<?>[]{Repository.class, NotificationThread.class},
                    repo("o/r"), null);
            waitUntil(holder::isRead);
            // mAll 时标完已读也不熄 indicator。
            assertTrue(activity.indicatorVisible);
        }
    }

    @Test
    public void handleMarkAsRead_participatingFilter_keepsIndicator() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        when(service.markAllRepositoryNotificationsRead(any(), any(), any()))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            setField(f, "mParticipating", true);
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            NotificationAdapter adapter = (NotificationAdapter) field(f, "mAdapter");
            NotificationHolder holder = new NotificationHolder(t);
            adapter.addAll(Arrays.asList(new NotificationHolder(repo("o/r")), holder));
            activity.indicatorVisible = true;
            call(f, "markAsRead", new Class<?>[]{Repository.class, NotificationThread.class},
                    repo("o/r"), null);
            waitUntil(holder::isRead);
            assertTrue(activity.indicatorVisible);
        }
    }

    @Test
    public void markAsRead_error_isSwallowed() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        java.util.concurrent.atomic.AtomicBoolean errorEmitted =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        when(service.markNotificationRead("1"))
                .thenReturn(Single.<Response<Void>>error(new RuntimeException("boom"))
                        .doOnError(e -> errorEmitted.set(true)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            f.markAsRead(new NotificationHolder(t));
            // 同 unsubscribe_error：等 IO 发射后再 drain，否则 error 消费者
            //（398 行）跑不到。
            waitUntil(errorEmitted::get);
            idleMain();
            verify(service).markNotificationRead("1");
        }
    }

    @Test
    public void handleMarkAsRead_adapterReturnsFalse_keepsIndicator() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        NotificationService service = mock(NotificationService.class);
        when(service.markNotificationRead("1"))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(new ArrayList<>()));
             MockedStatic<ServiceFactory> services = mockNotificationService(service)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            waitForLoad(f);
            // adapter 桩成 markAsRead 返回 false：消费者跑完后 indicator 不熄灭。
            NotificationAdapter mockAdapter = mock(NotificationAdapter.class);
            java.util.concurrent.atomic.AtomicBoolean called =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            when(mockAdapter.markAsRead(any(), any())).thenAnswer(inv -> {
                called.set(true);
                return false;
            });
            setField(f, "mAdapter", mockAdapter);
            activity.indicatorVisible = true;
            f.markAsRead(new NotificationHolder(t));
            // timeout() 不 pump 主 looper，用 waitUntil 等消费者跑完再断言。
            waitUntil(called::get);
            verify(mockAdapter).markAsRead(any(), any());
            assertTrue(activity.indicatorVisible);
        }
    }

    // 注意：Robolectric 的 Intent.getExtras() 每次返回拷贝，生产里 extras.remove()
    // 的效果在此不可观测；能观测的是滚动本身。匹配的仓库头在位置 2（第二组）。
    @Test
    public void scrollToInitialNotification_match_scrollsToRepoHeader() throws Exception {
        // 目标组 20 条线程：内容远超 470px 视口，滚动才看得见（4 项全在视口内时
        // scrollToPositionWithOffset 会被回填抵消，首个可见项仍是 0）。
        // 两组 updatedAt 必须不同：notificationsToResult 用 HashMap 分组、按
        // 最新时间排组，等时间戳时组顺序任意，匹配头可能落在位置 0。
        List<NotificationThread> threads = new ArrayList<>();
        threads.add(thread("1", true, repo("o/other"), new Date(1700000001000L)));
        for (int i = 2; i <= 21; i++) {
            threads.add(thread(String.valueOf(i), true, repo("o/r")));
        }
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(threads))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, "o")
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, "r");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            // [头 other, t1, 头 r, t2..t21]：滚动到 o/r 的头（位置 2）。
            assertEquals(2, firstVisiblePosition(f));
            assertEquals(23, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void scrollToInitialNotification_noMatch_noScroll() throws Exception {
        List<NotificationThread> threads = new ArrayList<>();
        threads.add(thread("1", true, repo("o/other"), new Date(1700000001000L)));
        for (int i = 2; i <= 21; i++) {
            threads.add(thread(String.valueOf(i), true, repo("o/r")));
        }
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(threads))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, "o")
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, "nonexistent");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            // 没匹配：不滚动，首个可见项仍是位置 0。
            assertEquals(0, firstVisiblePosition(f));
            assertEquals(23, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void scrollToInitialNotification_noExtras_noScroll() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(Arrays.asList(t)))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            // 默认 intent 无 extras：直接返回，不崩、不滚动。
            attachWithIntent(f, new Intent());
            waitForLoad(f);
            idleForMs(700);
            assertEquals(0, firstVisiblePosition(f));
            assertEquals(2, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void scrollToInitialNotification_ownerNull_noScroll() throws Exception {
        NotificationThread t = thread("1", true, repo("o/r"));
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(Arrays.asList(t)))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, "r");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            // 只有仓库名、没有 owner：读到 null 后返回，不滚动。
            assertEquals(0, firstVisiblePosition(f));
        }
    }

    @Test
    public void scrollToInitialNotification_nameNull_noScroll() throws Exception {
        List<NotificationThread> threads = new ArrayList<>();
        threads.add(thread("1", true, repo("o/other"), new Date(1700000001000L)));
        for (int i = 2; i <= 21; i++) {
            threads.add(thread(String.valueOf(i), true, repo("o/r")));
        }
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(threads))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            // 只有 owner、没有仓库名：读到 null 后返回，不滚动。
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, "o");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            assertEquals(0, firstVisiblePosition(f));
            assertEquals(23, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void scrollToInitialNotification_nullOwnerRepo_skipsAndScrolls() throws Exception {
        // 第一组仓库没有 owner：循环里 repo.owner() == null 跳过，继续匹配 o/r。
        Repository nullOwnerRepo = Repository.builder()
                .name("other").fullName("x/other").build();
        List<NotificationThread> threads = new ArrayList<>();
        threads.add(thread("1", true, nullOwnerRepo, new Date(1700000001000L)));
        for (int i = 2; i <= 21; i++) {
            threads.add(thread(String.valueOf(i), true, repo("o/r")));
        }
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(threads))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, "o")
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, "r");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            assertEquals(2, firstVisiblePosition(f));
            assertEquals(23, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void scrollToInitialNotification_ownerMismatch_noScroll() throws Exception {
        List<NotificationThread> threads = new ArrayList<>();
        threads.add(thread("1", true, repo("o/other"), new Date(1700000001000L)));
        for (int i = 2; i <= 21; i++) {
            threads.add(thread(String.valueOf(i), true, repo("o/r")));
        }
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(threads))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            // owner 对不上（仓库名对得上）：循环跳过全部头，不滚动。
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, "x")
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, "r");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            assertEquals(0, firstVisiblePosition(f));
            assertEquals(23, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void scrollToInitialNotification_nullRepoHeader_skipsAndScrolls() throws Exception {
        // L364：第一组仓库为 null：循环里 repo == null 跳过，继续匹配 o/r。
        List<NotificationThread> threads = new ArrayList<>();
        threads.add(thread("1", true, null, new Date(1700000001000L)));
        for (int i = 2; i <= 21; i++) {
            threads.add(thread(String.valueOf(i), true, repo("o/r")));
        }
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(threads))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            Intent intent = new Intent()
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_OWNER, "o")
                    .putExtra(NotificationListFragment.EXTRA_INITIAL_REPO_NAME, "r");
            attachWithIntent(f, intent);
            waitForLoad(f);
            idleForMs(700);
            assertEquals(2, firstVisiblePosition(f));
            assertEquals(23, ((NotificationAdapter) field(f, "mAdapter")).getCount());
        }
    }

    @Test
    public void loadNotifications_secondPage_appendsItems() throws Exception {
        NotificationThread t1 = thread("1", true, repo("o/r1"));
        NotificationThread t2 = thread("2", true, repo("o/r2"));
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.just(
                Arrays.asList(t1), Arrays.asList(t2)))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            assertEquals(2, loaded(f).size());
            // 两页、两个仓库：每组一个头 + 一行 = 4 项。
            assertEquals(4, ((NotificationAdapter) field(f, "mAdapter")).getCount());
            assertFalse((Boolean) field(f, "mFirstPagePending"));
            assertNotNull(field(f, "mNotificationsLoadTime"));
        }
    }

    @Test
    public void loadNotifications_errorAfterFirstPage_keepsList() throws Exception {
        // 注意：不能用 Observable.concat(just([t]), error) 模拟“第一页到了、第二页
        // 才失败”——实测证明 RxJava2 的 observeOn（delayError=false）会在 error
        // 到达时先清队列再发 error，排队中的 onNext 被丢弃，订阅端只收到 error。
        // 生产里分页是先后两次投递，所以用 PublishSubject 分步发。
        // ReplaySubject：订阅前发的页也会回放（PublishSubject 在订阅前 onNext
        // 会直接丢掉——attach 后 IO 线程订阅有延迟，测试线程先 onNext 就丢了）。
        NotificationThread t = thread("1", true, repo("o/r"));
        io.reactivex.subjects.ReplaySubject<List<NotificationThread>> subject =
                io.reactivex.subjects.ReplaySubject.create();
        try (MockedStatic<SingleFactory> paged = mockPaged(subject)) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            subject.onNext(Arrays.asList(t));
            waitUntil(() -> {
                try {
                    return loaded(f).size() == 1;
                } catch (Exception e) {
                    return false;
                }
            });
            subject.onError(new RuntimeException("boom"));
            waitForLoad(f);
            // 首页面已显示：后续页失败只记日志，不清空列表。
            assertEquals(1, loaded(f).size());
            assertEquals(2, ((NotificationAdapter) field(f, "mAdapter")).getCount());
            assertFalse((Boolean) field(f, "mLoadInProgress"));
        }
    }

    @Test
    public void loadNotifications_errorEmpty_callsHandleLoadFailure() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.error(new RuntimeException("boom")))) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            attachResumed(f);
            waitForLoad(f);
            // 空列表 + 失败：handleLoadFailure（宿主非 BaseActivity，静默）。
            assertTrue(loaded(f).isEmpty());
            assertFalse((Boolean) field(f, "mLoadInProgress"));
        }
    }

    @Test
    public void loadNotifications_detachedFragment_skipsConsumer() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(
                Observable.just(Arrays.asList(thread("1", true, repo("o/r")))))) {
            // 裸实例（未 attach）：消费者 isAdded 为 false，直接返回。
            NotificationListFragment f = NotificationListFragment.newInstance();
            call(f, "loadNotifications", new Class<?>[]{boolean.class}, false);
            waitForLoad(f);
            assertTrue(loaded(f).isEmpty());
            assertFalse((Boolean) field(f, "mLoadInProgress"));
        }
    }

    @Test
    public void onDestroyView_clearsDisposables() throws Exception {
        try (MockedStatic<SingleFactory> paged = mockPaged(Observable.never())) {
            NotificationListFragment f = NotificationListFragment.newInstance();
            CallbackHostActivity activity = attachResumed(f);
            CompositeDisposable disposables =
                    (CompositeDisposable) field(f, "mLoadDisposables");
            Disposable probe = Disposables.empty();
            disposables.add(probe);
            assertFalse(probe.isDisposed());
            activity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            assertTrue(probe.isDisposed());
        }
    }
}
