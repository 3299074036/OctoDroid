package com.gh4a.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.fragment.SettingsFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * B 组试点：RecentHistoryManager。登录态来自 Gh4a-pref 的 active_login
 *（Gh4Application.getAuthLogin()）；无登录时不记录、读空列表。
 */
@RunWith(RobolectricTestRunner.class)
public class RecentHistoryManagerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Before
    public void clearLogin() {
        setLogin(null);
    }

    private void setLogin(String login) {
        if (login == null) {
            mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().remove("active_login").apply();
        } else {
            mContext.getSharedPreferences(SettingsFragment.PREF_NAME, Context.MODE_PRIVATE)
                    .edit().putString("active_login", login).apply();
        }
    }

    @Test
    public void entryKey_format() {
        RecentHistoryManager.Entry e = new RecentHistoryManager.Entry(
                RecentHistoryManager.TYPE_ISSUE, "octocat", "Hello-World", 7, 123L);
        assertEquals("1/octocat/Hello-World/7", e.key());
        assertEquals(RecentHistoryManager.TYPE_ISSUE, e.type);
        assertEquals("octocat", e.owner);
        assertEquals("Hello-World", e.repo);
        assertEquals(7, e.number);
        assertEquals(123L, e.time);
        RecentHistoryManager.Entry repo = new RecentHistoryManager.Entry(
                RecentHistoryManager.TYPE_REPO, "o", "r", 0, 0L);
        assertEquals("0/o/r/0", repo.key());
        RecentHistoryManager.Entry pr = new RecentHistoryManager.Entry(
                RecentHistoryManager.TYPE_PR, "o", "r", 3, 0L);
        assertEquals("2/o/r/3", pr.key());
    }

    @Test
    public void noLogin_getEntriesEmptyAndRecordIsNoop() {
        assertTrue(RecentHistoryManager.getEntries(mContext).isEmpty());
        RecentHistoryManager.recordRepo(mContext, "o", "r");
        RecentHistoryManager.recordIssue(mContext, "o", "r", 1);
        RecentHistoryManager.recordPr(mContext, "o", "r", 2);
        assertTrue(RecentHistoryManager.getEntries(mContext).isEmpty());
        RecentHistoryManager.clear(mContext);
        assertTrue(RecentHistoryManager.getEntries(mContext).isEmpty());
    }

    @Test
    public void recordRepo_roundTrip() {
        setLogin("alice");
        RecentHistoryManager.recordRepo(mContext, "octocat", "Hello-World");
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(1, entries.size());
        assertEquals(RecentHistoryManager.TYPE_REPO, entries.get(0).type);
        assertEquals("octocat", entries.get(0).owner);
        assertEquals("Hello-World", entries.get(0).repo);
        assertEquals(0, entries.get(0).number);
        assertTrue(entries.get(0).time > 0);
    }

    @Test
    public void recordIssueAndPr_typesAndNumbers() {
        setLogin("alice");
        RecentHistoryManager.recordIssue(mContext, "o", "r", 7);
        RecentHistoryManager.recordPr(mContext, "o", "r", 8);
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(2, entries.size());
        // 最新在前：PR 后记录，排第一
        assertEquals(RecentHistoryManager.TYPE_PR, entries.get(0).type);
        assertEquals(8, entries.get(0).number);
        assertEquals(RecentHistoryManager.TYPE_ISSUE, entries.get(1).type);
        assertEquals(7, entries.get(1).number);
    }

    @Test
    public void newestFirst_ordering() {
        setLogin("alice");
        RecentHistoryManager.recordRepo(mContext, "o", "a");
        RecentHistoryManager.recordRepo(mContext, "o", "b");
        RecentHistoryManager.recordRepo(mContext, "o", "c");
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(3, entries.size());
        assertEquals("c", entries.get(0).repo);
        assertEquals("b", entries.get(1).repo);
        assertEquals("a", entries.get(2).repo);
    }

    @Test
    public void recordDuplicate_movesToFrontWithoutDuplicating() {
        setLogin("alice");
        RecentHistoryManager.recordRepo(mContext, "o", "a");
        RecentHistoryManager.recordRepo(mContext, "o", "b");
        RecentHistoryManager.recordRepo(mContext, "o", "a");
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(2, entries.size());
        assertEquals("a", entries.get(0).repo);
        assertEquals("b", entries.get(1).repo);
    }

    @Test
    public void recordDuplicateIssue_movesToFront() {
        setLogin("alice");
        RecentHistoryManager.recordIssue(mContext, "o", "r", 1);
        RecentHistoryManager.recordIssue(mContext, "o", "r", 2);
        RecentHistoryManager.recordIssue(mContext, "o", "r", 1);
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(2, entries.size());
        assertEquals(1, entries.get(0).number);
        assertEquals(2, entries.get(1).number);
    }

    @Test
    public void capAtFifty_evictsOldest() {
        setLogin("alice");
        for (int i = 0; i < 55; i++) {
            RecentHistoryManager.recordRepo(mContext, "o", "r" + i);
        }
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(50, entries.size());
        assertEquals("r54", entries.get(0).repo);
        assertEquals("r5", entries.get(49).repo);
    }

    @Test
    public void clear_emptiesHistory() {
        setLogin("alice");
        RecentHistoryManager.recordRepo(mContext, "o", "r");
        assertEquals(1, RecentHistoryManager.getEntries(mContext).size());
        RecentHistoryManager.clear(mContext);
        assertTrue(RecentHistoryManager.getEntries(mContext).isEmpty());
    }

    @Test
    public void historyIsPerAccount() {
        setLogin("alice");
        RecentHistoryManager.recordRepo(mContext, "o", "alice-repo");
        setLogin("bob");
        assertTrue(RecentHistoryManager.getEntries(mContext).isEmpty());
        RecentHistoryManager.recordRepo(mContext, "o", "bob-repo");
        assertEquals("bob-repo", RecentHistoryManager.getEntries(mContext).get(0).repo);
        setLogin("alice");
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(1, entries.size());
        assertEquals("alice-repo", entries.get(0).repo);
    }

    @Test
    public void corruptedJson_returnsEmptyList() {
        setLogin("alice");
        RecentHistoryManager.recordRepo(mContext, "o", "r");
        mContext.getSharedPreferences("recent_history_alice", Context.MODE_PRIVATE)
                .edit().putString("items", "garbage{{{").apply();
        assertTrue(RecentHistoryManager.getEntries(mContext).isEmpty());
    }

    @Test
    public void partiallyCorruptedJson_keepsGoodEntries() {
        setLogin("alice");
        // 一条缺 time 字段（fromJson 按条容错，坏条跳过）
        String json = "[{\"type\":0,\"owner\":\"o\",\"repo\":\"good\",\"number\":0,\"time\":100},"
                + "{\"type\":0,\"owner\":\"o\",\"repo\":\"bad\"},"
                + "{\"type\":1,\"owner\":\"o\",\"repo\":\"good2\",\"number\":3,\"time\":200}]";
        mContext.getSharedPreferences("recent_history_alice", Context.MODE_PRIVATE)
                .edit().putString("items", json).apply();
        List<RecentHistoryManager.Entry> entries = RecentHistoryManager.getEntries(mContext);
        assertEquals(2, entries.size());
        assertEquals("good", entries.get(0).repo);
        assertEquals("good2", entries.get(1).repo);
        assertEquals(3, entries.get(1).number);
    }
}
