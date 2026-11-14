package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.fragment.HotReposCache.HotRepo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** HotReposCache (F1): 24h hot-repo list cache over JsonCache. */
@RunWith(RobolectricTestRunner.class)
public class HotReposCacheTest {

    @Before
    public void clearPrefs() {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("hot_repos_cache", Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void ttl_is24Hours() {
        assertEquals(24L * 60 * 60 * 1000, HotReposCache.TTL_MS);
    }

    @Test
    public void load_emptyCache_returnsNull() {
        assertNull(HotReposCache.load(context()));
    }

    @Test
    public void load_nullContext_returnsNull() {
        assertNull(HotReposCache.load(null));
    }

    @Test
    public void saveAndLoad_roundTrip() {
        List<HotRepo> repos = Arrays.asList(
                new HotRepo("octocat/hello", 42),
                new HotRepo("github/docs", 7));
        HotReposCache.save(context(), repos);
        List<HotRepo> loaded = HotReposCache.load(context());
        assertEquals(2, loaded.size());
        assertEquals("octocat/hello", loaded.get(0).fullName);
        assertEquals(42, loaded.get(0).stars);
        assertEquals("github/docs", loaded.get(1).fullName);
        assertEquals(7, loaded.get(1).stars);
    }

    @Test
    public void save_nullContextOrList_isNoop() {
        HotReposCache.save(null, Arrays.asList(new HotRepo("a/b", 1)));
        HotReposCache.save(context(), null);
        assertNull(HotReposCache.load(context()));
    }

    @Test
    public void constructor_instantiates() {
        assertNotNull(new HotReposCache());
    }

    @Test
    public void save_emptyList_doesNotStoreFreshCache() {
        // F-25: an empty result must not be stored as fresh cache.
        HotReposCache.save(context(), new ArrayList<>());
        assertNull(HotReposCache.load(context()));
    }

    @Test
    public void save_emptyList_doesNotOverwriteExistingCache() {
        HotReposCache.save(context(), Arrays.asList(new HotRepo("a/b", 1)));
        HotReposCache.save(context(), new ArrayList<>());
        List<HotRepo> loaded = HotReposCache.load(context());
        assertEquals(1, loaded.size());
        assertEquals("a/b", loaded.get(0).fullName);
    }

    @Test
    public void load_corruptJson_returnsNull() {
        context().getSharedPreferences("hot_repos_cache", Context.MODE_PRIVATE)
                .edit().putString("hot_repos", "not-json{{").apply();
        assertNull(HotReposCache.load(context()));
    }

    @Test
    public void load_entryWithoutFullName_isSkipped() {
        context().getSharedPreferences("hot_repos_cache", Context.MODE_PRIVATE)
                .edit().putString("hot_repos",
                        "[{\"stars\": 5}, {\"fullName\": \"a/b\", \"stars\": 3}]").apply();
        List<HotRepo> loaded = HotReposCache.load(context());
        assertEquals(1, loaded.size());
        assertEquals("a/b", loaded.get(0).fullName);
        assertEquals(3, loaded.get(0).stars);
    }

    @Test
    public void load_allEntriesEmpty_returnsNull() {
        context().getSharedPreferences("hot_repos_cache", Context.MODE_PRIVATE)
                .edit().putString("hot_repos", "[{\"stars\": 5}]").apply();
        assertNull(HotReposCache.load(context()));
    }

    @Test
    public void hotRepo_fieldsAreStored() {
        HotRepo repo = new HotRepo("x/y", 99);
        assertEquals("x/y", repo.fullName);
        assertEquals(99, repo.stars);
    }
}
