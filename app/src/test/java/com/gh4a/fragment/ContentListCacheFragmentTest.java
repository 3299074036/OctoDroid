package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.meisolsson.githubsdk.model.Content;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ContentListCacheFragment: retained fragment holding an LRU-ish
 * LinkedHashMap of path → contents, capped at 100 entries (F1).
 * Content objects are built via Mockito mocks (no builder exists).
 */
@RunWith(RobolectricTestRunner.class)
public class ContentListCacheFragmentTest {

    private static List<Content> contents(String name) {
        Content c = org.mockito.Mockito.mock(Content.class);
        org.mockito.Mockito.when(c.name()).thenReturn(name);
        return Collections.singletonList(c);
    }

    @Test
    public void onCreate_setsRetainInstance() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        FragmentTestHelper.attach(f);
        assertEquals(true, f.getRetainInstance());
    }

    @Test
    public void addAndGet_roundTrip() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        f.addToCache("src/main", contents("Main.java"));
        ArrayList<Content> got = f.getFromCache("src/main");
        assertEquals(1, got.size());
        assertEquals("Main.java", got.get(0).name());
    }

    @Test
    public void get_missingPath_returnsNull() {
        assertNull(new ContentListCacheFragment().getFromCache("nope"));
    }

    @Test
    public void add_overwritesPreviousEntry() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        f.addToCache("p", contents("A.java"));
        f.addToCache("p", contents("B.java"));
        assertEquals("B.java", f.getFromCache("p").get(0).name());
    }

    @Test
    public void add_copiesListSoCallerMutationDoesNotLeak() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        List<Content> input = new ArrayList<>(contents("A.java"));
        f.addToCache("p", input);
        input.clear();
        assertEquals(1, f.getFromCache("p").size());
    }

    @Test
    public void clear_emptiesCache() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        f.addToCache("a", contents("A.java"));
        f.addToCache("b", contents("B.java"));
        f.clear();
        assertNull(f.getFromCache("a"));
        assertNull(f.getFromCache("b"));
    }

    @Test
    public void eviction_101stEntryDropsEldest() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        for (int i = 0; i < 100; i++) {
            f.addToCache("p" + i, contents("F" + i));
        }
        assertEquals("F0", f.getFromCache("p0").get(0).name());
        f.addToCache("p100", contents("F100"));
        assertNull("eldest entry must be evicted past 100 entries", f.getFromCache("p0"));
        assertEquals("F1", f.getFromCache("p1").get(0).name());
        assertEquals("F100", f.getFromCache("p100").get(0).name());
    }

    @Test
    public void eviction_atExactly100Entries_nothingEvicted() {
        ContentListCacheFragment f = new ContentListCacheFragment();
        for (int i = 0; i < 100; i++) {
            f.addToCache("p" + i, contents("F" + i));
        }
        assertEquals("F0", f.getFromCache("p0").get(0).name());
        assertEquals("F99", f.getFromCache("p99").get(0).name());
    }
}
