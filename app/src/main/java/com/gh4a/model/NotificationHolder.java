package com.gh4a.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.meisolsson.githubsdk.model.NotificationThread;
import com.meisolsson.githubsdk.model.Repository;

public class NotificationHolder {
    @Nullable
    public final NotificationThread notification;

    @Nullable
    public final Repository repository;

    private boolean mLastRepositoryNotification;
    private boolean mRead;

    public NotificationHolder(@Nullable Repository repository) {
        notification = null;
        this.repository = repository;
    }

    public NotificationHolder(@NonNull NotificationThread notification) {
        this.notification = notification;
        // 154/115：repository()/unread() 均可空；缺 repository 的通知按无仓库处理，
        // 下游 formatRepoName/assignAvatar 均为 null 安全，调用点另行判空。
        repository = notification.repository();
        mRead = !Boolean.TRUE.equals(notification.unread());
    }

    public boolean isLastRepositoryNotification() {
        return mLastRepositoryNotification;
    }

    public void setIsLastRepositoryNotification(boolean value) {
        mLastRepositoryNotification = value;
    }

    public boolean isRead() {
        return mRead;
    }

    public void setIsRead(boolean value) {
        mRead = value;
    }
}
