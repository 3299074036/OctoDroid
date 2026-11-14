package com.gh4a.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-JVM tests for UpdateChecker.isNewer / shouldAutoCheck.
 * The network paths (check/doCheck/fetchLatestRelease) hit GitHub and
 * cannot be covered without a static mock of the OkHttp client, so they
 * are intentionally not exercised here (see report: Mockito needed).
 */
public class UpdateCheckerTest {

    @Test
    public void isNewer_majorBump() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"));
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"));
    }

    @Test
    public void isNewer_minorAndPatchBump() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.5"));
        assertTrue(UpdateChecker.isNewer("0.2.6", "0.2.5"));
    }

    @Test
    public void isNewer_equalVersionsNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.2.5", "0.2.5"));
        assertFalse(UpdateChecker.isNewer("1.0", "1.0"));
    }

    @Test
    public void isNewer_olderLatestNotNewer() {
        assertFalse(UpdateChecker.isNewer("0.2.4", "0.2.5"));
        assertFalse(UpdateChecker.isNewer("0.1.9", "0.2.0"));
    }

    @Test
    public void isNewer_differentLengthsMissingPartIsZero() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0"));
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"));
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"));
    }

    @Test
    public void isNewer_nonNumericSuffixStripped() {
        // parsePart strips non-digits: "5-beta" parses as 5.
        assertFalse(UpdateChecker.isNewer("0.2.5-beta", "0.2.5"));
        assertTrue(UpdateChecker.isNewer("0.2.6-beta", "0.2.5"));
    }

    @Test
    public void isNewer_emptyPartTreatedAsZero() {
        assertFalse(UpdateChecker.isNewer("", ""));
        assertTrue(UpdateChecker.isNewer("1", ""));
    }

    @Test
    public void isNewer_firstDifferingPartDecides() {
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"));
        assertFalse(UpdateChecker.isNewer("1.0.99", "1.1.0"));
    }

    @Test
    public void shouldAutoCheck_alwaysTrue() {
        assertTrue(UpdateChecker.shouldAutoCheck(null));
    }
}
