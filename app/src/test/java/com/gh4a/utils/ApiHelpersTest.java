package com.gh4a.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 269：ApiHelpers 的空守卫路径（115 系）此前零单测覆盖。
 * orZero/orFalse 是全树拆箱的收敛点，先锁定其 null/非 null 行为。
 */
public class ApiHelpersTest {

    @Test
    public void orZero_integerNullReturnsZero() {
        assertEquals(0, ApiHelpers.orZero((Integer) null));
    }

    @Test
    public void orZero_integerValuePassesThrough() {
        assertEquals(5, ApiHelpers.orZero(Integer.valueOf(5)));
        assertEquals(-3, ApiHelpers.orZero(Integer.valueOf(-3)));
    }

    @Test
    public void orZero_longNullReturnsZero() {
        assertEquals(0L, ApiHelpers.orZero((Long) null));
    }

    @Test
    public void orZero_longValuePassesThrough() {
        assertEquals(7L, ApiHelpers.orZero(Long.valueOf(7L)));
    }

    @Test
    public void orFalse_nullReturnsFalse() {
        assertFalse(ApiHelpers.orFalse(null));
    }

    @Test
    public void orFalse_valuePassesThrough() {
        assertTrue(ApiHelpers.orFalse(Boolean.TRUE));
        assertFalse(ApiHelpers.orFalse(Boolean.FALSE));
    }
}
