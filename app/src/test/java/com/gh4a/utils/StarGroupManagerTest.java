package com.gh4a.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * B 组试点：StarGroupManager。login 显式传入，不读全局登录态；
 * 数据走 SharedPreferences（JsonCache），Robolectric 下为真实内存实现。
 */
@RunWith(RobolectricTestRunner.class)
public class StarGroupManagerTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void getGroups_nullContextOrLoginReturnsEmpty() {
        assertTrue(StarGroupManager.getGroups(null, "alice").isEmpty());
        assertTrue(StarGroupManager.getGroups(mContext, null).isEmpty());
        assertTrue(StarGroupManager.getGroups(mContext, "nobody").isEmpty());
    }

    @Test
    public void createGroup_roundTrip() {
        assertTrue(StarGroupManager.createGroup(mContext, "alice", "Work"));
        List<String> groups = StarGroupManager.getGroups(mContext, "alice");
        assertEquals(1, groups.size());
        assertEquals("Work", groups.get(0));
    }

    @Test
    public void createGroup_trimsName() {
        assertTrue(StarGroupManager.createGroup(mContext, "alice", "  Spaced  "));
        assertEquals("Spaced", StarGroupManager.getGroups(mContext, "alice").get(0));
    }

    @Test
    public void createGroup_emptyOrBlankRejected() {
        assertFalse(StarGroupManager.createGroup(mContext, "alice", ""));
        assertFalse(StarGroupManager.createGroup(mContext, "alice", "   "));
        assertTrue(StarGroupManager.getGroups(mContext, "alice").isEmpty());
    }

    @Test
    public void createGroup_nullNameReturnsFalse() {
        // T-104：null name 不再 NPE，直接返回 false。
        assertFalse(StarGroupManager.createGroup(mContext, "alice", null));
    }

    @Test
    public void createGroup_duplicateCaseInsensitiveRejected() {
        assertTrue(StarGroupManager.createGroup(mContext, "alice", "Test"));
        assertFalse(StarGroupManager.createGroup(mContext, "alice", "Test"));
        assertFalse(StarGroupManager.createGroup(mContext, "alice", "test"));
        assertFalse(StarGroupManager.createGroup(mContext, "alice", "TEST"));
        assertEquals(1, StarGroupManager.getGroups(mContext, "alice").size());
    }

    @Test
    public void createGroup_nullContextOrLoginReturnsFalse() {
        // T-104：context/login 为空不再谎报成功，直接返回 false，什么都不存。
        assertFalse(StarGroupManager.createGroup(null, "alice", "G"));
        assertFalse(StarGroupManager.createGroup(mContext, null, "G"));
        assertTrue(StarGroupManager.getGroups(mContext, "alice").isEmpty());
    }

    @Test
    public void groupsArePerAccount() {
        StarGroupManager.createGroup(mContext, "alice", "A");
        StarGroupManager.createGroup(mContext, "bob", "B");
        assertEquals(1, StarGroupManager.getGroups(mContext, "alice").size());
        assertEquals("A", StarGroupManager.getGroups(mContext, "alice").get(0));
        assertEquals("B", StarGroupManager.getGroups(mContext, "bob").get(0));
    }

    @Test
    public void assignAndGetRepos_roundTrip() {
        StarGroupManager.assignRepo(mContext, "alice", "Work",
                "octocat/Hello-World", "My first repo");
        List<StarGroupManager.StarRepo> repos =
                StarGroupManager.getRepos(mContext, "alice", "Work");
        assertEquals(1, repos.size());
        assertEquals("octocat/Hello-World", repos.get(0).fullName);
        assertEquals("My first repo", repos.get(0).description);
    }

    @Test
    public void assignRepo_nullDescriptionStoredAsEmpty() {
        StarGroupManager.assignRepo(mContext, "alice", "G", "o/r", null);
        assertEquals("", StarGroupManager.getRepos(mContext, "alice", "G")
                .get(0).description);
    }

    @Test
    public void assignRepo_movesRepoToNewGroup() {
        StarGroupManager.assignRepo(mContext, "alice", "A", "o/r", "d");
        StarGroupManager.assignRepo(mContext, "alice", "B", "o/r", "d2");
        assertTrue(StarGroupManager.getRepos(mContext, "alice", "A").isEmpty());
        List<StarGroupManager.StarRepo> repos =
                StarGroupManager.getRepos(mContext, "alice", "B");
        assertEquals(1, repos.size());
        assertEquals("d2", repos.get(0).description);
        assertEquals("B", StarGroupManager.findGroupFor(mContext, "alice", "o/r"));
    }

    @Test
    public void assignRepo_sameGroupTwiceDoesNotDuplicate() {
        StarGroupManager.assignRepo(mContext, "alice", "G", "o/r", "d");
        StarGroupManager.assignRepo(mContext, "alice", "G", "o/r", "d");
        assertEquals(1, StarGroupManager.getRepos(mContext, "alice", "G").size());
    }

    @Test
    public void getRepos_unknownGroupOrLoginReturnsEmpty() {
        assertTrue(StarGroupManager.getRepos(mContext, "alice", "Nope").isEmpty());
        assertTrue(StarGroupManager.getRepos(mContext, null, "G").isEmpty());
        assertTrue(StarGroupManager.getRepos(null, "alice", "G").isEmpty());
    }

    @Test
    public void findGroupFor_foundAndMissing() {
        StarGroupManager.assignRepo(mContext, "alice", "Work", "o/r1", "");
        StarGroupManager.assignRepo(mContext, "alice", "Fun", "o/r2", "");
        assertEquals("Work", StarGroupManager.findGroupFor(mContext, "alice", "o/r1"));
        assertEquals("Fun", StarGroupManager.findGroupFor(mContext, "alice", "o/r2"));
        assertNull(StarGroupManager.findGroupFor(mContext, "alice", "o/r3"));
        assertNull(StarGroupManager.findGroupFor(mContext, "bob", "o/r1"));
        assertNull(StarGroupManager.findGroupFor(mContext, null, "o/r1"));
    }

    @Test
    public void assignRepo_nullContextOrLoginIsNoop() {
        StarGroupManager.assignRepo(null, "alice", "G", "o/r", "d");
        StarGroupManager.assignRepo(mContext, null, "G", "o/r", "d");
        assertTrue(StarGroupManager.getRepos(mContext, "alice", "G").isEmpty());
    }

    @Test
    public void corruptedGroupsJson_returnsEmpty() {
        mContext.getSharedPreferences("star_groups_alice", Context.MODE_PRIVATE)
                .edit().putString("groups", "garbage{{").apply();
        assertTrue(StarGroupManager.getGroups(mContext, "alice").isEmpty());
    }

    @Test
    public void corruptedMembersJson_returnsEmptyRepos() {
        mContext.getSharedPreferences("star_groups_alice", Context.MODE_PRIVATE)
                .edit().putString("members", "garbage{{").apply();
        assertTrue(StarGroupManager.getRepos(mContext, "alice", "G").isEmpty());
        assertNull(StarGroupManager.findGroupFor(mContext, "alice", "o/r"));
    }

    @Test
    public void partiallyCorruptedGroupsJson_keepsGoodEntries() {
        mContext.getSharedPreferences("star_groups_alice", Context.MODE_PRIVATE)
                .edit().putString("groups", "[\"Good\", 123, \"AlsoGood\"]").apply();
        List<String> groups = StarGroupManager.getGroups(mContext, "alice");
        // T-107：非字符串条目（数字 123）跳过，只收两个好条目。
        assertEquals(2, groups.size());
        assertEquals("Good", groups.get(0));
        assertEquals("AlsoGood", groups.get(1));
    }
}
