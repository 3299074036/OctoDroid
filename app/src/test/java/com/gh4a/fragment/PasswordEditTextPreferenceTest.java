package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** PasswordEditTextPreference (F1): all constructors wire the password dialog layout. */
@RunWith(RobolectricTestRunner.class)
public class PasswordEditTextPreferenceTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void constructor_contextOnly_setsPasswordDialogLayout() {
        PasswordEditTextPreference p = new PasswordEditTextPreference(context());
        assertEquals(R.layout.pref_password_dialog, p.getDialogLayoutResource());
    }

    @Test
    public void constructor_contextAndAttrs_setsPasswordDialogLayout() {
        PasswordEditTextPreference p =
                new PasswordEditTextPreference(context(), null);
        assertEquals(R.layout.pref_password_dialog, p.getDialogLayoutResource());
    }

    @Test
    public void constructor_contextAttrsDefStyle_setsPasswordDialogLayout() {
        PasswordEditTextPreference p =
                new PasswordEditTextPreference(context(), null, 0);
        assertEquals(R.layout.pref_password_dialog, p.getDialogLayoutResource());
    }

    @Test
    public void constructor_contextAttrsDefStyleDefStyleRes_setsPasswordDialogLayout() {
        PasswordEditTextPreference p =
                new PasswordEditTextPreference(context(), null, 0, 0);
        assertEquals(R.layout.pref_password_dialog, p.getDialogLayoutResource());
    }
}
