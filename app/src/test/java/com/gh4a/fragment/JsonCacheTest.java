package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * JsonCache: SharedPreferences-backed JSON cache skeleton (F1).
 * Pure logic around a Context; Robolectric supplies real SharedPreferences.
 */
@RunWith(RobolectricTestRunner.class)
public class JsonCacheTest {
    private static final String PREFS = "json_cache_test_prefs";

    private final JsonCache<String> cache = new JsonCache<>(PREFS,
            value -> "json:" + value,
            json -> json.startsWith("json:") ? json.substring(5) : null);

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void load_missingKey_returnsNull() {
        assertNull(cache.load(context(), "no-such-key"));
    }

    @Test
    public void load_nullContext_returnsNull() {
        assertNull(cache.load(null, "k"));
    }

    @Test
    public void load_nullKey_returnsNull() {
        assertNull(cache.load(context(), null));
    }

    @Test
    public void saveAndLoad_roundTrip() {
        cache.save(context(), "k1", null, "hello");
        assertEquals("hello", cache.load(context(), "k1"));
    }

    @Test
    public void save_nullContextOrKeyOrValue_isNoop() {
        cache.save(null, "k", null, "v");
        cache.save(context(), null, null, "v");
        cache.save(context(), "k", null, null);
        assertNull(cache.load(context(), "k"));
    }

    @Test
    public void save_withTimeKey_recordsTimestamp() {
        long before = System.currentTimeMillis();
        cache.save(context(), "k2", "t2", "v");
        long age = cache.age(context(), "t2");
        assertTrue("age should be >= 0, was " + age, age >= 0);
        assertTrue("age should be small, was " + age,
                age < System.currentTimeMillis() - before + 5000);
        assertEquals("v", cache.load(context(), "k2"));
    }

    @Test
    public void save_toJsonThrows_isSwallowedAndLoadReturnsNull() {
        JsonCache<String> throwing = new JsonCache<>(PREFS,
                value -> { throw new RuntimeException("encode boom"); },
                json -> json);
        throwing.save(context(), "boom", null, "v");
        assertNull(throwing.load(context(), "boom"));
    }

    @Test
    public void load_fromJsonThrows_returnsNull() {
        JsonCache<String> throwing = new JsonCache<>(PREFS,
                value -> value,
                json -> { throw new RuntimeException("decode boom"); });
        throwing.save(context(), "bad", null, "v");
        assertNull(throwing.load(context(), "bad"));
    }

    @Test
    public void load_fromJsonReturnsNull_returnsNull() {
        JsonCache<String> nuller = new JsonCache<>(PREFS,
                value -> value, json -> null);
        nuller.save(context(), "n", null, "v");
        assertNull(nuller.load(context(), "n"));
    }

    @Test
    public void age_noTimestamp_returnsMinusOne() {
        assertEquals(-1, cache.age(context(), "no-time-key"));
    }

    @Test
    public void age_nullContextOrKey_returnsMinusOne() {
        assertEquals(-1, cache.age(null, "t"));
        assertEquals(-1, cache.age(context(), null));
    }

    @Test
    public void age_futureTimestamp_isNegativeOrZeroClamped() {
        // A timestamp in the future yields a negative age; age() only maps
        // stored -1 to -1, so a future value surfaces as negative.
        context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong("future", System.currentTimeMillis() + 60_000).apply();
        assertTrue(cache.age(context(), "future") < 0);
    }

    @Test
    public void clear_removesOnlyListedKeys() {
        cache.save(context(), "a", null, "1");
        cache.save(context(), "b", null, "2");
        cache.clear(context(), "a");
        assertNull(cache.load(context(), "a"));
        assertEquals("2", cache.load(context(), "b"));
    }

    @Test
    public void clear_nullContextOrKeys_isNoop() {
        cache.save(context(), "c", null, "3");
        cache.clear(null, "c");
        cache.clear(context(), (String[]) null);
        assertEquals("3", cache.load(context(), "c"));
    }

    @Test
    public void clear_multipleKeys_removesAll() {
        cache.save(context(), "m1", null, "1");
        cache.save(context(), "m2", null, "2");
        cache.clear(context(), "m1", "m2");
        assertNull(cache.load(context(), "m1"));
        assertNull(cache.load(context(), "m2"));
    }
}
