package com.gh4a.utils;

import android.os.Parcel;

import com.gh4a.model.NotificationListLoadResult;
import com.meisolsson.githubsdk.model.NotificationReason;
import com.meisolsson.githubsdk.model.NotificationSubject;
import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import okhttp3.OkHttpClient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * B 组试点：SingleFactory。notificationsToResult 是纯分组/排序逻辑，
 * 用 Builder（Repository）与匿名子类（NotificationThread）构造模型，
 * 走纯 JVM 测试；trendService 只断言拼装出非空服务。
 * isAppUserRepoCollaborator / getNotifications / getNotificationsPaged /
 * loadFeed / loadTrends 需 ServiceFactory 与真实网络，待 Mockito
 *（静态 mock ServiceFactory）后补。
 */
public class SingleFactoryTest {

    private static Repository repo(String fullName) {
        return Repository.builder().fullName(fullName).build();
    }

    private static NotificationThread thread(Repository repository, Boolean unread,
            Date updatedAt) {
        return new NotificationThread() {
            @Override
            public String id() {
                return "1";
            }

            @Override
            public Repository repository() {
                return repository;
            }

            @Override
            public NotificationSubject subject() {
                return null;
            }

            @Override
            public NotificationReason reason() {
                return null;
            }

            @Override
            public String url() {
                return null;
            }

            @Override
            public Boolean unread() {
                return unread;
            }

            @Override
            public Date updatedAt() {
                return updatedAt;
            }

            @Override
            public Date lastReadAt() {
                return null;
            }

            @Override
            public int describeContents() {
                return 0;
            }

            @Override
            public void writeToParcel(Parcel dest, int flags) {
            }
        };
    }

    @Test
    public void notificationsToResult_emptyList() {
        NotificationListLoadResult result =
                SingleFactory.notificationsToResult(new ArrayList<>());
        assertTrue(result.notifications.isEmpty());
        assertNotNull(result.loadTime);
    }

    @Test
    public void notificationsToResult_singleRepoGroupsAndMarksRead() {
        Repository r = repo("octocat/Hello-World");
        List<NotificationThread> input = Arrays.asList(
                thread(r, true, new Date(1000)),
                thread(r, false, new Date(3000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        // [repo 头, 较新通知, 较旧通知]：组内按 updatedAt 降序
        assertEquals(3, result.notifications.size());
        assertNull(result.notifications.get(0).notification);
        assertEquals(r, result.notifications.get(0).repository);
        assertFalse(result.notifications.get(0).isRead());
        assertEquals(new Date(3000),
                result.notifications.get(1).notification.updatedAt());
        assertEquals(new Date(1000),
                result.notifications.get(2).notification.updatedAt());
        assertFalse(result.notifications.get(1).isLastRepositoryNotification());
        assertTrue(result.notifications.get(2).isLastRepositoryNotification());
    }

    @Test
    public void notificationsToResult_allReadMarksRepoRead() {
        Repository r = repo("o/r");
        NotificationListLoadResult result = SingleFactory.notificationsToResult(
                Arrays.asList(thread(r, false, new Date(1000)),
                        thread(r, false, new Date(2000))));
        assertTrue(result.notifications.get(0).isRead());
    }

    @Test
    public void notificationsToResult_nullUnreadCountsAsRead() {
        // 注意：SingleFactory:122 的注释写"缺字段按未读计"，但实现
        //（此处、NotificationHolder、HomeActivity.countUnread 三处一致）
        // 均为 Boolean.TRUE.equals → null 按已读计，与 orFalse(null)=false
        // 同款；注释文字疑似笔误，行为本身全树一致，此处锁定实现行为。
        Repository r = repo("o/r");
        NotificationListLoadResult result = SingleFactory.notificationsToResult(
                Collections.singletonList(thread(r, null, new Date(1000))));
        assertTrue(result.notifications.get(0).isRead());
        assertTrue(result.notifications.get(1).isRead());
    }

    @Test
    public void notificationsToResult_groupsSortedByNewestFirst() {
        Repository older = repo("o/older");
        Repository newer = repo("o/newer");
        List<NotificationThread> input = Arrays.asList(
                thread(older, false, new Date(5000)),
                thread(newer, false, new Date(1000)),
                thread(newer, false, new Date(9000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        // 组按组内最新通知降序：newer(9000) 在前，older(5000) 在后
        assertEquals(5, result.notifications.size());
        assertEquals(newer, result.notifications.get(0).repository);
        assertEquals(new Date(9000),
                result.notifications.get(1).notification.updatedAt());
        assertEquals(older, result.notifications.get(3).repository);
    }

    @Test
    public void notificationsToResult_sameRepoDifferentInstancesGrouped() {
        // Repository 是 AutoValue：同 fullName 的两个实例相等，应归入同一组
        List<NotificationThread> input = Arrays.asList(
                thread(repo("o/r"), false, new Date(1000)),
                thread(repo("o/r"), false, new Date(2000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(3, result.notifications.size());
    }

    @Test
    public void notificationsToResult_nullUpdatedAtSortsLast() {
        Repository r = repo("o/r");
        List<NotificationThread> input = Arrays.asList(
                thread(r, false, null),
                thread(r, false, new Date(1000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(new Date(1000),
                result.notifications.get(1).notification.updatedAt());
        assertNull(result.notifications.get(2).notification.updatedAt());
    }

    @Test
    public void notificationsToResult_bothNullUpdatedAtKeepsBoth() {
        Repository r = repo("o/r");
        List<NotificationThread> input = Arrays.asList(
                thread(r, false, null),
                thread(r, false, null));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(3, result.notifications.size());
    }

    @Test
    public void notificationsToResult_nullRepositoryGroupedSeparately() {
        List<NotificationThread> input = Arrays.asList(
                thread(null, false, new Date(1000)),
                thread(repo("o/r"), false, new Date(2000)));
        NotificationListLoadResult result = SingleFactory.notificationsToResult(input);
        assertEquals(4, result.notifications.size());
        // o/r 组（2000）排在 null 仓库组（1000）前面
        assertEquals(repo("o/r"), result.notifications.get(0).repository);
        assertNull(result.notifications.get(2).repository);
    }

    @Test
    public void trendService_buildsNonNullService() {
        assertNotNull(SingleFactory.trendService(new OkHttpClient()));
    }
}
