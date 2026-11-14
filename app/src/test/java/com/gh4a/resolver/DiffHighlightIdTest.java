package com.gh4a.resolver;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DiffHighlightIdTest {
    @Test
    public void constructor_storesAllFields() {
        DiffHighlightId id = new DiffHighlightId("abc123", 10, 20, true);
        assertEquals("abc123", id.fileHash);
        assertEquals(10, id.startLine);
        assertEquals(20, id.endLine);
        assertTrue(id.right);
    }

    @Test
    public void constructor_leftSideAndNegativeLines() {
        DiffHighlightId id = new DiffHighlightId("hash", -1, -1, false);
        assertEquals("hash", id.fileHash);
        assertEquals(-1, id.startLine);
        assertEquals(-1, id.endLine);
        assertFalse(id.right);
    }

    @Test
    public void constructor_nullFileHashAllowed() {
        DiffHighlightId id = new DiffHighlightId(null, 0, 0, false);
        assertNull(id.fileHash);
        assertEquals(0, id.startLine);
        assertEquals(0, id.endLine);
    }

    @Test
    public void constructor_sameLineRange() {
        DiffHighlightId id = new DiffHighlightId("h", 5, 5, true);
        assertEquals(5, id.startLine);
        assertEquals(5, id.endLine);
    }
}
