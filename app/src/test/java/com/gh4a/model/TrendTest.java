package com.gh4a.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TrendTest {

    @Test
    public void constructor_storesAllFields() {
        Trend trend = new Trend("octocat", "hello", "A desc", "Java", 10, 5, 2);
        assertEquals("octocat", trend.getRepoOwner());
        assertEquals("hello", trend.getRepoName());
        assertEquals("A desc", trend.getDescription());
        assertEquals("Java", trend.getLanguage());
        assertEquals(10, trend.getStars());
        assertEquals(5, trend.getNewStars());
        assertEquals(2, trend.getForks());
    }

    @Test
    public void constructor_nullStringsAllowed() {
        Trend trend = new Trend(null, null, null, null, 0, 0, 0);
        assertNull(trend.getRepoOwner());
        assertNull(trend.getRepoName());
        assertNull(trend.getDescription());
        assertNull(trend.getLanguage());
        assertEquals(0, trend.getStars());
        assertEquals(0, trend.getNewStars());
        assertEquals(0, trend.getForks());
    }

    @Test
    public void constructor_negativeCountsPreserved() {
        Trend trend = new Trend("o", "r", "d", "Kotlin", -1, -3, -2);
        assertEquals(-1, trend.getStars());
        assertEquals(-3, trend.getNewStars());
        assertEquals(-2, trend.getForks());
    }
}
