package com.gh4a.model;

import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationHolderTest {

    private static NotificationThread thread(Boolean unread, Repository repo) {
        NotificationThread t = mock(NotificationThread.class);
        when(t.unread()).thenReturn(unread);
        when(t.repository()).thenReturn(repo);
        return t;
    }

    @Test
    public void repositoryConstructor_notificationNullRepositoryStored() {
        Repository repo = mock(Repository.class);
        NotificationHolder holder = new NotificationHolder(repo);
        assertNull(holder.notification);
        assertSame(repo, holder.repository);
        assertFalse(holder.isRead());
        assertFalse(holder.isLastRepositoryNotification());
    }

    @Test
    public void repositoryConstructor_nullRepositoryAllowed() {
        NotificationHolder holder = new NotificationHolder((Repository) null);
        assertNull(holder.notification);
        assertNull(holder.repository);
    }

    @Test
    public void threadConstructor_unreadTrue_isNotRead() {
        Repository repo = mock(Repository.class);
        NotificationHolder holder = new NotificationHolder(thread(true, repo));
        assertFalse(holder.isRead());
        assertSame(repo, holder.repository);
    }

    @Test
    public void threadConstructor_unreadFalse_isRead() {
        NotificationHolder holder = new NotificationHolder(thread(false, null));
        assertTrue(holder.isRead());
        assertNull(holder.repository);
    }

    @Test
    public void threadConstructor_unreadNull_countsAsRead() {
        // Boolean.TRUE.equals(null) is false, so mRead = !false = true.
        // Matches HomeActivity.countUnread's null handling (see T-116 note).
        NotificationHolder holder = new NotificationHolder(thread(null, null));
        assertTrue(holder.isRead());
    }

    @Test
    public void threadConstructor_notificationStored() {
        NotificationThread t = thread(true, null);
        NotificationHolder holder = new NotificationHolder(t);
        assertSame(t, holder.notification);
    }

    @Test
    public void setIsRead_toggles() {
        NotificationHolder holder = new NotificationHolder(thread(false, null));
        assertTrue(holder.isRead());
        holder.setIsRead(false);
        assertFalse(holder.isRead());
        holder.setIsRead(true);
        assertTrue(holder.isRead());
    }

    @Test
    public void setIsLastRepositoryNotification_toggles() {
        NotificationHolder holder = new NotificationHolder((Repository) null);
        holder.setIsLastRepositoryNotification(true);
        assertTrue(holder.isLastRepositoryNotification());
        holder.setIsLastRepositoryNotification(false);
        assertFalse(holder.isLastRepositoryNotification());
    }

    @Test
    public void repositoryConstructor_setIsReadIndependent() {
        NotificationHolder holder = new NotificationHolder((Repository) null);
        holder.setIsRead(true);
        assertTrue(holder.isRead());
        assertEquals(false, holder.isLastRepositoryNotification());
    }
}
