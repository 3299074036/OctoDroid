package com.gh4a.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A 组：BadgeState 是纯内存角标 holder，无 Android 依赖，走纯 JVM 测试。
 */
public class BadgeStateTest {

    private static class RecordingListener implements BadgeState.Listener {
        final List<int[]> calls = new ArrayList<>();

        @Override
        public void onBadgeCountsChanged(int notificationCount, int starUpdateCount) {
            calls.add(new int[]{notificationCount, starUpdateCount});
        }
    }

    @Test
    public void addListener_immediatelyReceivesCurrentCounts() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        assertEquals(1, listener.calls.size());
        assertEquals(0, listener.calls.get(0)[0]);
        assertEquals(0, listener.calls.get(0)[1]);
    }

    @Test
    public void addListener_afterCountsSet_receivesLatestCounts() {
        BadgeState state = new BadgeState();
        state.setNotificationCount(3);
        state.setStarUpdateCount(7);
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        assertEquals(1, listener.calls.size());
        assertEquals(3, listener.calls.get(0)[0]);
        assertEquals(7, listener.calls.get(0)[1]);
    }

    @Test
    public void addListener_nullIgnored() {
        BadgeState state = new BadgeState();
        state.addListener(null);
        state.setNotificationCount(1);
        // 无监听者：不断言回调，只断言不抛异常且状态照常更新
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        assertEquals(1, listener.calls.size());
        assertEquals(1, listener.calls.get(0)[0]);
    }

    @Test
    public void addListener_sameListenerTwice_onlyNotifiedOnce() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        state.addListener(listener);
        assertEquals(1, listener.calls.size());
        state.setNotificationCount(2);
        assertEquals(2, listener.calls.size());
    }

    @Test
    public void setNotificationCount_changeNotifiesWithBothCounts() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        state.setStarUpdateCount(5);
        state.setNotificationCount(2);
        assertEquals(3, listener.calls.size());
        assertEquals(2, listener.calls.get(2)[0]);
        assertEquals(5, listener.calls.get(2)[1]);
    }

    @Test
    public void setNotificationCount_sameValueDoesNotNotify() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        state.setNotificationCount(0);
        assertEquals(1, listener.calls.size());
        state.setNotificationCount(4);
        state.setNotificationCount(4);
        assertEquals(2, listener.calls.size());
    }

    @Test
    public void setStarUpdateCount_sameValueDoesNotNotify() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        state.setStarUpdateCount(0);
        assertEquals(1, listener.calls.size());
        state.setStarUpdateCount(9);
        state.setStarUpdateCount(9);
        assertEquals(2, listener.calls.size());
    }

    @Test
    public void multipleListeners_allNotifiedInOrder() {
        BadgeState state = new BadgeState();
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        state.addListener(first);
        state.addListener(second);
        state.setNotificationCount(1);
        state.setStarUpdateCount(2);
        assertEquals(3, first.calls.size());
        assertEquals(3, second.calls.size());
        assertEquals(1, first.calls.get(2)[0]);
        assertEquals(2, first.calls.get(2)[1]);
        assertEquals(1, second.calls.get(2)[0]);
        assertEquals(2, second.calls.get(2)[1]);
    }

    @Test
    public void setCounts_negativeValuesStoredAsIs() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        state.setNotificationCount(-1);
        state.setStarUpdateCount(-2);
        assertEquals(3, listener.calls.size());
        assertEquals(-1, listener.calls.get(2)[0]);
        assertEquals(-2, listener.calls.get(2)[1]);
    }

    @Test
    public void setCounts_zeroAfterNonZeroNotifies() {
        BadgeState state = new BadgeState();
        RecordingListener listener = new RecordingListener();
        state.addListener(listener);
        state.setNotificationCount(3);
        state.setNotificationCount(0);
        assertEquals(3, listener.calls.size());
        assertEquals(0, listener.calls.get(2)[0]);
        assertTrue(listener.calls.get(2)[1] == 0);
    }
}
