package com.gh4a.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Pure-JVM tests for Triplet: no Android APIs involved. */
public class TripletTest {

    @Test
    public void constructor_storesAllThreeValues() {
        Triplet<String, Integer, Boolean> t = new Triplet<>("a", 42, true);
        assertEquals("a", t.first);
        assertEquals(Integer.valueOf(42), t.second);
        assertEquals(Boolean.TRUE, t.third);
    }

    @Test
    public void constructor_acceptsNulls() {
        Triplet<String, String, String> t = new Triplet<>(null, null, null);
        assertNull(t.first);
        assertNull(t.second);
        assertNull(t.third);
    }

    @Test
    public void create_returnsEqualTriplet() {
        Triplet<String, Integer, Boolean> a = Triplet.create("x", 1, false);
        Triplet<String, Integer, Boolean> b = new Triplet<>("x", 1, false);
        assertEquals(a, b);
        assertEquals("x", a.first);
        assertEquals(Integer.valueOf(1), a.second);
        assertEquals(Boolean.FALSE, a.third);
    }

    @Test
    public void equals_sameValues() {
        assertEquals(new Triplet<>("a", 1, 2.5), new Triplet<>("a", 1, 2.5));
    }

    @Test
    public void equals_nullFieldsMatchNulls() {
        assertEquals(new Triplet<>(null, "s", null), new Triplet<>(null, "s", null));
    }

    @Test
    public void equals_differsInFirst() {
        assertNotEquals(new Triplet<>("a", 1, true), new Triplet<>("b", 1, true));
    }

    @Test
    public void equals_differsInSecond() {
        assertNotEquals(new Triplet<>("a", 1, true), new Triplet<>("a", 2, true));
    }

    @Test
    public void equals_differsInThird() {
        assertNotEquals(new Triplet<>("a", 1, true), new Triplet<>("a", 1, false));
    }

    @Test
    public void equals_nullVsNonNullField() {
        assertNotEquals(new Triplet<>(null, 1, true), new Triplet<>("a", 1, true));
        assertNotEquals(new Triplet<>("a", 1, true), new Triplet<>(null, 1, true));
    }

    @Test
    public void equals_nullOtherObject() {
        assertFalse(new Triplet<>("a", 1, true).equals(null));
    }

    @Test
    public void equals_otherType() {
        assertFalse(new Triplet<>("a", 1, true).equals("a"));
        assertFalse(new Triplet<>("a", 1, true).equals(new Object()));
    }

    @Test
    public void equals_reflexiveAndSymmetric() {
        Triplet<String, Integer, Boolean> a = new Triplet<>("a", 1, true);
        Triplet<String, Integer, Boolean> b = new Triplet<>("a", 1, true);
        assertTrue(a.equals(a));
        assertTrue(a.equals(b) && b.equals(a));
    }

    @Test
    public void hashCode_equalObjectsSameHash() {
        assertEquals(new Triplet<>("a", 1, true).hashCode(),
                new Triplet<>("a", 1, true).hashCode());
    }

    @Test
    public void hashCode_allNullIsZero() {
        assertEquals(0, new Triplet<>(null, null, null).hashCode());
    }

    @Test
    public void hashCode_differsWhenAFieldDiffers() {
        assertNotEquals(new Triplet<>("a", 1, true).hashCode(),
                new Triplet<>("b", 1, true).hashCode());
    }

    @Test
    public void mixedTypes_allowed() {
        Triplet<Integer, String, Double> t = Triplet.create(7, "s", 3.14);
        assertEquals(Integer.valueOf(7), t.first);
        assertEquals("s", t.second);
        assertEquals(Double.valueOf(3.14), t.third);
    }
}
