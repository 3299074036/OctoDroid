package com.gh4a.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class IntegerListPreferenceTest {

    private Context context;
    private IntegerListPreference pref;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        pref = new IntegerListPreference(context);
        pref.setKey("int_pref");
        PreferenceManager pm = new PreferenceManager(context);
        PreferenceScreen screen = pm.createPreferenceScreen(context);
        screen.addPreference(pref);
    }

    @Test
    public void constructors_contextOnly_creates() {
        IntegerListPreference p = new IntegerListPreference(context);
        // Preference defaults to persistent; it only becomes non-persistent
        // when setPersistent(false) is called explicitly.
        assertTrue(p.isPersistent());
    }

    @Test
    public void constructors_contextAndAttrs_creates() {
        IntegerListPreference p = new IntegerListPreference(context, null);
        assertTrue(p.isPersistent());
    }

    @Test
    public void persistString_null_returnsFalseAndStoresNothing() {
        assertFalse(pref.persistString(null));
        SharedPreferences prefs = pref.getSharedPreferences();
        assertFalse(prefs.contains("int_pref"));
    }

    @Test
    public void persistString_validInt_persistsIntValue() {
        assertTrue(pref.persistString("42"));
        assertEquals(42, pref.getSharedPreferences().getInt("int_pref", -1));
    }

    @Test
    public void persistString_negativeInt_persists() {
        assertTrue(pref.persistString("-7"));
        assertEquals(-7, pref.getSharedPreferences().getInt("int_pref", 0));
    }

    @Test
    public void persistString_zero_persists() {
        assertTrue(pref.persistString("0"));
        assertEquals(0, pref.getSharedPreferences().getInt("int_pref", -1));
    }

    @Test(expected = NumberFormatException.class)
    public void persistString_nonNumeric_throwsNumberFormatException() {
        pref.persistString("abc");
    }

    @Test(expected = NumberFormatException.class)
    public void persistString_empty_throwsNumberFormatException() {
        pref.persistString("");
    }

    @Test
    public void getPersistedString_noValue_returnsDefault() {
        assertEquals("def", pref.getPersistedString("def"));
    }

    @Test
    public void getPersistedString_noValue_returnsNullDefault() {
        assertEquals(null, pref.getPersistedString(null));
    }

    @Test
    public void getPersistedString_withValue_returnsPersistedAsString() {
        pref.persistString("123");
        assertEquals("123", pref.getPersistedString("def"));
    }

    @Test
    public void roundTrip_persistThenRead() {
        pref.persistString("9");
        assertEquals("9", pref.getPersistedString("0"));
        assertTrue(pref.persistString("10"));
        assertEquals("10", pref.getPersistedString("0"));
    }
}
