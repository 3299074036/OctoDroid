package com.gh4a.fragment;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;

import com.gh4a.R;

/**
 * EditTextPreference whose dialog shows a password field with an
 * eye toggle to show/hide the value.
 * The dialog itself is built by {@link PasswordDialogFragment}.
 */
public class PasswordEditTextPreference extends EditTextPreference {
    public PasswordEditTextPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public PasswordEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public PasswordEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PasswordEditTextPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setDialogLayoutResource(R.layout.pref_password_dialog);
    }
}
