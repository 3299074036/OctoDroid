package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.fragment.MeHeaderCache.Header;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** MeHeaderCache (F1): per-login header cache, 7-day TTL. */
@RunWith(RobolectricTestRunner.class)
public class MeHeaderCacheTest {

    private static final String PREFS = "me_header_cache";

    @Before
    public void clearPrefs() {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static Header header(String login) {
        return new Header(login, "Octo Cat", 1L, "https://example.com/a.png",
                1_700_000_000_000L, 12, 34, 56);
    }

    @Test
    public void ttl_is7Days() {
        assertEquals(7L * 24 * 60 * 60 * 1000, MeHeaderCache.TTL_MS);
    }

    @Test
    public void load_emptyCache_returnsNull() {
        assertNull(MeHeaderCache.load(context(), "octocat"));
    }

    @Test
    public void load_nullContextOrLogin_returnsNull() {
        assertNull(MeHeaderCache.load(null, "octocat"));
        assertNull(MeHeaderCache.load(context(), null));
    }

    @Test
    public void constructor_instantiates() {
        assertNotNull(new MeHeaderCache());
    }

    @Test
    public void saveAndLoad_roundTrip() {
        MeHeaderCache.save(context(), header("octocat"));
        Header loaded = MeHeaderCache.load(context(), "octocat");
        assertEquals("octocat", loaded.login);
        assertEquals("Octo Cat", loaded.name);
        assertEquals(1L, loaded.userId);
        assertEquals("https://example.com/a.png", loaded.avatarUrl);
        assertEquals(1_700_000_000_000L, loaded.joinedMillis);
        assertEquals(12, loaded.repos);
        assertEquals(34, loaded.followers);
        assertEquals(56, loaded.following);
    }

    @Test
    public void load_isPerLogin() {
        MeHeaderCache.save(context(), header("octocat"));
        assertNull(MeHeaderCache.load(context(), "someone-else"));
        assertEquals("octocat", MeHeaderCache.load(context(), "octocat").login);
    }

    @Test
    public void save_nullContextOrHeaderOrLogin_isNoop() {
        MeHeaderCache.save(null, header("octocat"));
        MeHeaderCache.save(context(), null);
        MeHeaderCache.save(context(), header(null));
        assertNull(MeHeaderCache.load(context(), "octocat"));
    }

    @Test
    public void load_expiredCache_returnsNull() {
        MeHeaderCache.save(context(), header("octocat"));
        // Backdate the timestamp past the 7-day TTL.
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("time_octocat",
                        System.currentTimeMillis() - MeHeaderCache.TTL_MS - 1000).apply();
        assertNull(MeHeaderCache.load(context(), "octocat"));
    }

    @Test
    public void load_cacheWithoutTimestamp_isStillReadable() {
        // Pre-F-32 caches have no timestamp: age() is -1, no expiry applies.
        MeHeaderCache.save(context(), header("octocat"));
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("time_octocat").apply();
        assertEquals("octocat", MeHeaderCache.load(context(), "octocat").login);
    }

    @Test
    public void load_headerWithEmptyLogin_fallsBackToKeyLogin() {
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("header_octocat",
                        "{\"login\":\"\",\"name\":\"N\",\"userId\":2,\"avatarUrl\":\"u\","
                                + "\"joinedMillis\":5,\"repos\":1,\"followers\":2,\"following\":3}")
                .apply();
        Header loaded = MeHeaderCache.load(context(), "octocat");
        assertEquals("octocat", loaded.login);
        assertEquals("N", loaded.name);
        assertEquals(2L, loaded.userId);
    }

    @Test
    public void clear_removesHeaderAndTimestamp() {
        MeHeaderCache.save(context(), header("octocat"));
        MeHeaderCache.clear(context(), "octocat");
        assertNull(MeHeaderCache.load(context(), "octocat"));
    }

    @Test
    public void clear_nullContextOrLogin_isNoop() {
        MeHeaderCache.save(context(), header("octocat"));
        MeHeaderCache.clear(null, "octocat");
        MeHeaderCache.clear(context(), null);
        assertEquals("octocat", MeHeaderCache.load(context(), "octocat").login);
    }

    @Test
    public void load_corruptJson_returnsNull() {
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("header_octocat", "not-json{{").apply();
        assertNull(MeHeaderCache.load(context(), "octocat"));
    }
}
