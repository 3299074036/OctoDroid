package com.gh4a.fragment;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import com.gh4a.R;

/**
 * Dialog for {@link PasswordEditTextPreference}: same save behavior as the
 * standard EditTextPreference dialog, plus an eye toggle to show/hide
 * the password.
 */
public class PasswordDialogFragment extends EditTextPreferenceDialogFragmentCompat {
    public static PasswordDialogFragment newInstance(String key) {
        PasswordDialogFragment fragment = new PasswordDialogFragment();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        EditText editText = view.findViewById(android.R.id.edit);
        ImageButton toggle = view.findViewById(R.id.toggle_password);
        if (editText == null || toggle == null) {
            return;
        }
        setPasswordHidden(editText, toggle, true);
        toggle.setOnClickListener(v -> {
            boolean hidden = isPasswordHidden(editText);
            setPasswordHidden(editText, toggle, !hidden);
        });
    }

    private static boolean isPasswordHidden(EditText editText) {
        int variation = editText.getInputType() & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD;
    }

    private static void setPasswordHidden(EditText editText, ImageButton toggle, boolean hidden) {
        int selStart = Math.max(editText.getSelectionStart(), 0);
        int selEnd = Math.max(editText.getSelectionEnd(), 0);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | (hidden ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                        : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        toggle.setImageResource(
                hidden ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        editText.setSelection(selStart, selEnd);
    }
}
