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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/** RadarCache：雷达缓存 + 已读委托（Robolectric）。 */
@RunWith(RobolectricTestRunner.class)
public class RadarCacheTest {
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    private static ReleaseRadarFragment.RadarItem item(String owner, String repo, long id,
            String tag, long publishedAt, long createdAt) {
        return new ReleaseRadarFragment.RadarItem(owner, repo, id, tag, "rel-" + tag,
                publishedAt, createdAt);
    }

    @Test
    public void load_nullGuardsReturnNull() {
        assertNull(RadarCache.load(null, "alice"));
        assertNull(RadarCache.load(mContext, null));
        assertNull(RadarCache.load(null, null));
    }

    @Test
    public void load_noCacheReturnsNull() {
        assertNull(RadarCache.load(mContext, "nobody-has-this-login"));
    }

    @Test
    public void save_nullGuardsAreNoops() {
        RadarCache.save(null, "alice", Collections.emptyList());
        RadarCache.save(mContext, null, Collections.emptyList());
        RadarCache.save(mContext, "alice", null);
        assertNull(RadarCache.load(mContext, "alice"));
    }

    @Test
    public void saveAndLoad_roundtripPreservesAllFields() {
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("octo", "cat", 11, "v1.0", 1700000000000L, 1699900000000L),
                item("foo", "bar", 22, "v2.0", 0L, 0L));
        RadarCache.save(mContext, "alice", items);
        List<ReleaseRadarFragment.RadarItem> loaded = RadarCache.load(mContext, "alice");
        assertEquals(2, loaded.size());
        assertEquals("octo", loaded.get(0).owner);
        assertEquals("cat", loaded.get(0).repo);
        assertEquals(11L, loaded.get(0).releaseId);
        assertEquals("v1.0", loaded.get(0).tagName);
        assertEquals("rel-v1.0", loaded.get(0).releaseName);
        assertEquals(1700000000000L, loaded.get(0).publishedAt);
        assertEquals(1699900000000L, loaded.get(0).createdAt);
        assertEquals("foo", loaded.get(1).owner);
        assertEquals(0L, loaded.get(1).publishedAt);
    }

    @Test
    public void save_emptyListRoundtripsAsEmptyList() {
        RadarCache.save(mContext, "alice", Collections.emptyList());
        List<ReleaseRadarFragment.RadarItem> loaded = RadarCache.load(mContext, "alice");
        assertTrue(loaded != null && loaded.isEmpty());
    }

    @Test
    public void save_overwritesPreviousCache() {
        RadarCache.save(mContext, "alice",
                Collections.singletonList(item("a", "b", 1, "v1", 1L, 1L)));
        RadarCache.save(mContext, "alice",
                Collections.singletonList(item("c", "d", 2, "v2", 2L, 2L)));
        List<ReleaseRadarFragment.RadarItem> loaded = RadarCache.load(mContext, "alice");
        assertEquals(1, loaded.size());
        assertEquals("c", loaded.get(0).owner);
    }

    @Test
    public void save_isolatedPerLogin() {
        RadarCache.save(mContext, "alice",
                Collections.singletonList(item("a", "b", 1, "v1", 1L, 1L)));
        assertNull(RadarCache.load(mContext, "bob"));
        assertEquals(1, RadarCache.load(mContext, "alice").size());
    }

    @Test
    public void age_nullGuardsReturnMinusOne() {
        assertEquals(-1L, RadarCache.age(null, "alice"));
        assertEquals(-1L, RadarCache.age(mContext, null));
    }

    @Test
    public void age_noCacheReturnsMinusOne() {
        assertEquals(-1L, RadarCache.age(mContext, "nobody-has-this-login"));
    }

    @Test
    public void age_afterSaveIsSmallNonNegative() {
        RadarCache.save(mContext, "alice",
                Collections.singletonList(item("a", "b", 1, "v1", 1L, 1L)));
        long age = RadarCache.age(mContext, "alice");
        assertTrue("age=" + age, age >= 0 && age < 60_000L);
    }

    @Test
    public void readDelegates_goThroughStarReadState() {
        List<ReleaseRadarFragment.RadarItem> items = Arrays.asList(
                item("a", "b", 1, "v1", 1L, 1L),
                item("c", "d", 2, "v2", 2L, 2L));
        assertEquals(2, RadarCache.unreadCount(items, RadarCache.getReadKeys(mContext, "alice")));
        RadarCache.markRead(mContext, "alice", "a/b#1");
        assertEquals(Collections.singleton("a/b#1"),
                RadarCache.getReadKeys(mContext, "alice"));
        assertEquals(1, RadarCache.unreadCount(items, RadarCache.getReadKeys(mContext, "alice")));
        RadarCache.markAllRead(mContext, "alice", items);
        assertEquals(new HashSet<>(Arrays.asList("a/b#1", "c/d#2")),
                RadarCache.getReadKeys(mContext, "alice"));
        assertEquals(0, RadarCache.unreadCount(items, RadarCache.getReadKeys(mContext, "alice")));
    }

    @Test
    public void privateConstructor_isCallableViaReflection() throws Exception {
        java.lang.reflect.Constructor<RadarCache> c = RadarCache.class.getDeclaredConstructor();
        c.setAccessible(true);
        assertNotNull(c.newInstance());
    }

}
