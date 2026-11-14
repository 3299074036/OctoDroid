package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Bundle;

import com.gh4a.R;
import com.gh4a.model.NotificationHolder;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
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
}
