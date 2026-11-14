package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicInteger;

/** MirrorPresetPreference (F1): ListPreference whose click never opens the
 * native single-choice dialog; it forwards to the pick listener instead. */
@RunWith(RobolectricTestRunner.class)
public class MirrorPresetPreferenceTest {

    private MirrorPresetPreference pref() {
        Context context = ApplicationProvider.getApplicationContext();
        return new MirrorPresetPreference(context, null);
    }

    @Test
    public void onClick_withoutListener_doesNothing() {
        pref().onClick();
    }

    @Test
    public void onClick_withListener_callsListenerOnce() {
        MirrorPresetPreference p = pref();
        AtomicInteger calls = new AtomicInteger();
        p.setOnPickListener(calls::incrementAndGet);
        p.onClick();
        assertEquals(1, calls.get());
        p.onClick();
        assertEquals(2, calls.get());
    }

    @Test
    public void onClick_listenerReplaced_oldListenerNotCalled() {
        MirrorPresetPreference p = pref();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        p.setOnPickListener(first::incrementAndGet);
        p.setOnPickListener(second::incrementAndGet);
        p.onClick();
        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void onClick_listenerCleared_doesNothing() {
        MirrorPresetPreference p = pref();
        AtomicInteger calls = new AtomicInteger();
        p.setOnPickListener(calls::incrementAndGet);
        p.setOnPickListener(null);
        p.onClick();
        assertEquals(0, calls.get());
    }

    @Test
    public void preference_isListPreference() {
        assertTrue(pref() instanceof androidx.preference.ListPreference);
    }
}
