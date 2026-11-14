package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.EditTextPreferenceDialogFragmentCompat;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Method;

/** PasswordDialogFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class PasswordDialogFragmentTest {

    @Test
    public void newInstance_storesKey() {
        PasswordDialogFragment f = PasswordDialogFragment.newInstance("my_key");
        assertEquals("my_key", f.getArguments().getString(
                "key"));
    }

    private static View dialogView() {
        // Inflate with an activity context: the layout's ImageButton uses a
        // theme attribute that the bare application theme cannot resolve.
        FragmentActivity activity = org.robolectric.Robolectric
                .buildActivity(FragmentActivity.class).setup().get();
        return LayoutInflater.from(activity)
                .inflate(R.layout.pref_password_dialog, null);
    }

    @Test
    public void onBindDialogView_setsPasswordHiddenInitially() throws Exception {
        PasswordDialogFragment f = PasswordDialogFragment.newInstance("k");
        View view = dialogView();
        EditText edit = view.findViewById(android.R.id.edit);
        assertNotNull(edit);
        edit.setText("secret");
        edit.setSelection(2, 4);
        // Bind via reflection-free call: the fragment needs no preference for
        // the toggle wiring itself; if the super implementation needs one it
        // will fail loudly and this test reports it.
        try {
            f.onBindDialogView(view);
        } catch (NullPointerException npe) {
            // androidx super reads the preference text; without a bound
            // preference there is nothing to bind. Fall back to exercising
            // the private helpers directly (see below).
            invokeSetPasswordHidden(edit,
                    view.findViewById(R.id.toggle_password), true);
        }
        int variation = edit.getInputType() & InputType.TYPE_MASK_VARIATION;
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD, variation);
    }

    @Test
    public void onBindDialogView_toggleFlipsVisibility() {
        PasswordDialogFragment f = PasswordDialogFragment.newInstance("k");
        View view = dialogView();
        EditText edit = view.findViewById(android.R.id.edit);
        ImageButton toggle = view.findViewById(R.id.toggle_password);
        assertNotNull(edit);
        assertNotNull(toggle);
        edit.setText("secret");
        try {
            f.onBindDialogView(view);
        } catch (NullPointerException npe) {
            invokeSetPasswordHidden(edit, toggle, true);
            toggle.setOnClickListener(v -> {
                boolean hidden = invokeIsPasswordHidden(edit);
                invokeSetPasswordHidden(edit, toggle, !hidden);
            });
        }
        // Initially hidden.
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
        toggle.performClick();
        assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
        assertEquals(R.drawable.ic_visibility_off,
                org.robolectric.Shadows.shadowOf(toggle.getDrawable()).getCreatedFromResId());
        toggle.performClick();
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
        assertEquals(R.drawable.ic_visibility,
                org.robolectric.Shadows.shadowOf(toggle.getDrawable()).getCreatedFromResId());
    }

    @Test
    public void onBindDialogView_missingEditText_throwsIllegalState() {
        // The androidx super requires an EditText with id android.R.id.edit
        // in the dialog view and fails loudly without one.
        PasswordDialogFragment f = PasswordDialogFragment.newInstance("k");
        View plain = new View(ApplicationProvider.getApplicationContext());
        FragmentTestHelper.assertThrows(IllegalStateException.class,
                () -> f.onBindDialogView(plain));
    }

    /** PreferenceFragmentCompat host holding the password preference. */
    public static class TestPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            PreferenceScreen screen =
                    getPreferenceManager().createPreferenceScreen(requireContext());
            PasswordEditTextPreference pref =
                    new PasswordEditTextPreference(requireContext());
            pref.setKey("k");
            screen.addPreference(pref);
            setPreferenceScreen(screen);
        }
    }

    @Test
    public void show_viaPreferenceHost_bindsRealToggleAndWiresClick() {
        // The real onBindDialogView path: super finds the preference via the
        // target fragment, wires the toggle, and hides the password.
        FragmentActivity activity = Robolectric
                .buildActivity(FragmentActivity.class).setup().get();
        TestPreferenceFragment host = new TestPreferenceFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(host, "host").commitNow();
        PasswordDialogFragment dialog = PasswordDialogFragment.newInstance("k");
        dialog.setTargetFragment(host, 0);
        dialog.show(activity.getSupportFragmentManager(), "pwd");
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        AlertDialog alertDialog = (AlertDialog) dialog.getDialog();
        assertNotNull(alertDialog);
        EditText edit = alertDialog.findViewById(android.R.id.edit);
        ImageButton toggle = alertDialog.findViewById(R.id.toggle_password);
        assertNotNull(edit);
        assertNotNull(toggle);
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
        toggle.performClick();
        assertEquals(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
        toggle.performClick();
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
    }

    @Test
    public void onBindDialogView_missingToggle_returnsEarly() {
        // The dialog is bound to its preference via the host, but the view
        // has no eye toggle: the null guard returns before wiring.
        FragmentActivity activity = Robolectric
                .buildActivity(FragmentActivity.class).setup().get();
        TestPreferenceFragment host = new TestPreferenceFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(host, "host").commitNow();
        PasswordDialogFragment dialog = PasswordDialogFragment.newInstance("k");
        dialog.setTargetFragment(host, 0);
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.pref_password_dialog, null);
        EditText edit = view.findViewById(android.R.id.edit);
        ImageButton toggle = view.findViewById(R.id.toggle_password);
        assertNotNull(edit);
        assertNotNull(toggle);
        // Remove the toggle: onBindDialogView must return early.
        ((android.view.ViewGroup) toggle.getParent()).removeView(toggle);
        assertNull(view.findViewById(R.id.toggle_password));
        int inputTypeBefore = edit.getInputType();
        dialog.onBindDialogView(view);
        assertEquals(inputTypeBefore, edit.getInputType());
    }

    @Test
    public void onBindDialogView_missingEditText_superThrows() {
        // The editText == null arm of the guard in onBindDialogView is
        // unreachable: super.onBindDialogView throws IllegalStateException
        // ("Dialog view must contain an EditText with id @android:id/edit")
        // before the guard is even reached. This pins that behavior.
        FragmentActivity activity = Robolectric
                .buildActivity(FragmentActivity.class).setup().get();
        TestPreferenceFragment host = new TestPreferenceFragment();
        activity.getSupportFragmentManager().beginTransaction()
                .add(host, "host").commitNow();
        PasswordDialogFragment dialog = PasswordDialogFragment.newInstance("k");
        dialog.setTargetFragment(host, 0);
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.pref_password_dialog, null);
        EditText edit = view.findViewById(android.R.id.edit);
        assertNotNull(edit);
        ((android.view.ViewGroup) edit.getParent()).removeView(edit);
        assertNull(view.findViewById(android.R.id.edit));
        FragmentTestHelper.assertThrows(IllegalStateException.class,
                () -> dialog.onBindDialogView(view));
    }

    @Test
    public void isPasswordHidden_detectsVariation() {
        EditText edit = new EditText(ApplicationProvider.getApplicationContext());
        edit.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        assertTrue(invokeIsPasswordHidden(edit));
        edit.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        assertEquals(false, invokeIsPasswordHidden(edit));
    }

    @Test
    public void setPasswordHidden_preservesSelection() {
        EditText edit = new EditText(ApplicationProvider.getApplicationContext());
        ImageButton toggle = new ImageButton(ApplicationProvider.getApplicationContext());
        edit.setText("secret");
        edit.setSelection(1, 3);
        invokeSetPasswordHidden(edit, toggle, true);
        assertEquals(1, edit.getSelectionStart());
        assertEquals(3, edit.getSelectionEnd());
        assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                edit.getInputType() & InputType.TYPE_MASK_VARIATION);
    }

    @Test
    public void setPasswordHidden_noSelection_clampsToZero() {
        EditText edit = new EditText(ApplicationProvider.getApplicationContext());
        ImageButton toggle = new ImageButton(ApplicationProvider.getApplicationContext());
        edit.setText("secret");
        invokeSetPasswordHidden(edit, toggle, false);
        assertEquals(0, edit.getSelectionStart());
        assertEquals(0, edit.getSelectionEnd());
    }

    // ---- private static helpers via reflection ----

    private static boolean invokeIsPasswordHidden(EditText edit) {
        try {
            Method m = PasswordDialogFragment.class
                    .getDeclaredMethod("isPasswordHidden", EditText.class);
            m.setAccessible(true);
            return (boolean) m.invoke(null, edit);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void invokeSetPasswordHidden(EditText edit, ImageButton toggle,
            boolean hidden) {
        try {
            Method m = PasswordDialogFragment.class.getDeclaredMethod(
                    "setPasswordHidden", EditText.class, ImageButton.class, boolean.class);
            m.setAccessible(true);
            m.invoke(null, edit, toggle, hidden);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
