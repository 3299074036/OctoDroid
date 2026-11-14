package com.gh4a;

import android.content.Context;
import android.content.DialogInterface;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ColorPickerDialogTest {

    private static Context context() {
        // AlertDialog (AppCompat) requires an AppCompat theme; the bare
        // application context throws "You need to use a Theme.AppCompat
        // theme (or descendant) with this activity".
        return new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.AppTheme);
    }

    @Test
    public void positiveButton_reportsSelectedColorAsHex() {
        List<String> received = new ArrayList<>();
        ColorPickerDialog dialog = new ColorPickerDialog(context(), "ff0000", received::add);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals(1, received.size());
        assertEquals("ff0000", received.get(0));
    }

    @Test
    public void positiveButton_greenColor_formatted() {
        List<String> received = new ArrayList<>();
        ColorPickerDialog dialog = new ColorPickerDialog(context(), "00ff00", received::add);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("00ff00", received.get(0));
    }

    @Test
    public void positiveButton_blueColor_formatted() {
        List<String> received = new ArrayList<>();
        ColorPickerDialog dialog = new ColorPickerDialog(context(), "0000ff", received::add);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("0000ff", received.get(0));
    }

    @Test
    public void positiveButton_mixedColor_lowercaseHex() {
        List<String> received = new ArrayList<>();
        ColorPickerDialog dialog = new ColorPickerDialog(context(), "1A2B3C", received::add);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("1a2b3c", received.get(0));
    }

    @Test
    public void negativeButton_doesNotCallListener() {
        List<String> received = new ArrayList<>();
        ColorPickerDialog dialog = new ColorPickerDialog(context(), "ff0000", received::add);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertTrue(received.isEmpty());
    }

    @Test
    public void nullListener_positiveButtonDoesNotCrash() {
        ColorPickerDialog dialog = new ColorPickerDialog(context(), "ff0000", null);
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        // No listener: nothing to assert other than no crash.
    }

    @Test
    public void invalidColor_throwsIllegalArgument() {
        try {
            new ColorPickerDialog(context(), "zzzzzz", color -> {
            });
            org.junit.Assert.fail("expected IllegalArgumentException from Color.parseColor");
        } catch (IllegalArgumentException expected) {
            // Color.parseColor rejects non-hex input.
        }
    }

    @Test
    public void nullColor_throws() {
        try {
            new ColorPickerDialog(context(), null, color -> {
            });
            org.junit.Assert.fail("expected exception for null color");
        } catch (Exception expected) {
            // "#" + null -> Color.parseColor throws IllegalArgumentException.
        }
    }
}
