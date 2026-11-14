package com.gh4a.fragment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/** StarReadState：Star 更新已读状态（SharedPreferences，Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class StarReadStateTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    private static ReleaseRadarFragment.RadarItem item(String owner, String repo, long id) {
        return new ReleaseRadarFragment.RadarItem(owner, repo, id, "v1", "r1", 1000L, 900L);
    }

    @Test
    public void getReadKeys_emptyByDefault() {
        assertTrue(StarReadState.getReadKeys(mContext, "alice").isEmpty());
    }

    @Test
    public void getReadKeys_isolatedPerLogin() {
        StarReadState.markRead(mContext, "alice", "a/b#1");
        assertTrue(StarReadState.getReadKeys(mContext, "bob").isEmpty());
        assertEquals(Collections.singleton("a/b#1"),
                StarReadState.getReadKeys(mContext, "alice"));
    }

    @Test
    public void markRead_addsAndIsIdempotent() {
        StarReadState.markRead(mContext, "alice", "a/b#1");
        StarReadState.markRead(mContext, "alice", "a/b#1");
        StarReadState.markRead(mContext, "alice", "a/b#2");
        Set<String> keys = StarReadState.getReadKeys(mContext, "alice");
        assertEquals(new HashSet<>(Arrays.asList("a/b#1", "a/b#2")), keys);
    }

    @Test
    public void getReadKeys_returnsCopy() {
        StarReadState.markRead(mContext, "alice", "a/b#1");
        Set<String> keys = StarReadState.getReadKeys(mContext, "alice");
        keys.add("forged/x#9");
        assertFalse(StarReadState.getReadKeys(mContext, "alice").contains("forged/x#9"));
    }

    @Test
    public void markAllRead_nullItemsIsNoop() {
        StarReadState.markAllRead(mContext, "alice", null);
        assertTrue(StarReadState.getReadKeys(mContext, "alice").isEmpty());
    }

    @Test
    public void markAllRead_emptyListIsNoop() {
        StarReadState.markAllRead(mContext, "alice", Collections.emptyList());
        assertTrue(StarReadState.getReadKeys(mContext, "alice").isEmpty());
    }

    @Test
    public void markAllRead_addsOwnerRepoHashIdKeys() {
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("octo", "cat", 11), item("foo", "bar", 22));
        StarReadState.markAllRead(mContext, "alice", items);
        assertEquals(new HashSet<>(Arrays.asList("octo/cat#11", "foo/bar#22")),
                StarReadState.getReadKeys(mContext, "alice"));
    }

    @Test
    public void markAllRead_mergesWithExistingKeys() {
        StarReadState.markRead(mContext, "alice", "old/repo#5");
        StarReadState.markAllRead(mContext, "alice",
                Collections.singletonList(item("a", "b", 1)));
        assertEquals(new HashSet<>(Arrays.asList("old/repo#5", "a/b#1")),
                StarReadState.getReadKeys(mContext, "alice"));
    }

    @Test
    public void unreadCount_nullItemsIsZero() {
        assertEquals(0, StarReadState.unreadCount(null, null));
        assertEquals(0, StarReadState.unreadCount(null, Collections.emptySet()));
    }

    @Test
    public void unreadCount_emptyItemsIsZero() {
        assertEquals(0, StarReadState.unreadCount(
                Collections.emptyList(), Collections.emptySet()));
    }

    @Test
    public void unreadCount_nullReadKeysCountsAll() {
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("a", "b", 1), item("c", "d", 2));
        assertEquals(2, StarReadState.unreadCount(items, null));
    }

    @Test
    public void unreadCount_partialRead() {
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("a", "b", 1), item("c", "d", 2), item("e", "f", 3));
        assertEquals(2, StarReadState.unreadCount(items,
                Collections.singleton("c/d#2")));
        assertEquals(0, StarReadState.unreadCount(items,
                new HashSet<>(Arrays.asList("a/b#1", "c/d#2", "e/f#3"))));
    }

    @Test
    public void unreadCount_sameRepoDifferentReleaseIdIsDifferentKey() {
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("a", "b", 1), item("a", "b", 2));
        assertEquals(1, StarReadState.unreadCount(items,
                Collections.singleton("a/b#1")));
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<StarReadState> c = StarReadState.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }

}
