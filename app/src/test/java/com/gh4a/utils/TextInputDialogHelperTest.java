package com.gh4a.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.R;
import com.gh4a.resolver.BrowseFilter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TextInputDialogHelper: dialog shows, OK passes the trimmed text. */
@RunWith(RobolectricTestRunner.class)
public class TextInputDialogHelperTest {

    private Activity activity() {
        return Robolectric.buildActivity(BrowseFilter.class).setup().get();
    }

    private AlertDialog shownDialog() {
        // getShownDialogs() accumulates across shows within one test,
        // so the dialog under test is always the last one.
        List<Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertFalse(dialogs.isEmpty());
        return (AlertDialog) dialogs.get(dialogs.size() - 1);
    }

    private EditText inputOf(AlertDialog dialog) {
        EditText input = findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        return input;
    }

    private static EditText findEditText(View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String titleOf(AlertDialog dialog) {
        TextView title = dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        assertNotNull(title);
        return title.getText().toString();
    }

    private void clickPositive(AlertDialog dialog) {
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        // AppCompat dispatches button clicks through a Handler; the
        // listener only runs once the (paused) main looper is idled.
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void show_withTitleRes_setsTitle() {
        Activity act = activity();
        TextInputDialogHelper.show(act, R.string.ok, null, text -> {
        });
        assertEquals(act.getString(R.string.ok), titleOf(shownDialog()));
    }

    @Test
    public void show_withCharSequenceTitle_setsTitle() {
        Activity act = activity();
        TextInputDialogHelper.show(act, "My Title", null, text -> {
        });
        assertEquals("My Title", titleOf(shownDialog()));
    }

    @Test
    public void show_okPassesTrimmedText() {
        Activity act = activity();
        List<String> received = new ArrayList<>();
        TextInputDialogHelper.show(act, "Title", "  initial  ", received::add);
        assertEquals("  initial  ", inputOf(shownDialog()).getText().toString());
        inputOf(shownDialog()).setText("  hello world  ");
        clickPositive(shownDialog());
        assertEquals(Collections.singletonList("hello world"), received);
    }

    @Test
    public void show_nullInitialLeavesInputEmpty() {
        Activity act = activity();
        List<String> received = new ArrayList<>();
        TextInputDialogHelper.show(act, "Title", null, received::add);
        assertEquals("", inputOf(shownDialog()).getText().toString());
        clickPositive(shownDialog());
        assertEquals(Collections.singletonList(""), received);
    }

    @Test
    public void show_whitespaceOnlyInputPassesEmptyString() {
        Activity act = activity();
        List<String> received = new ArrayList<>();
        TextInputDialogHelper.show(act, "Title", "   ", received::add);
        clickPositive(shownDialog());
        assertEquals(Collections.singletonList(""), received);
    }

    @Test
    public void show_nullCallbackDoesNotCrashOnOk() {
        Activity act = activity();
        TextInputDialogHelper.show(act, "Title", "abc", null);
        clickPositive(shownDialog());
    }

    @Test
    public void show_hasCancelButton() {
        Activity act = activity();
        TextInputDialogHelper.show(act, "Title", null, text -> {
        });
        assertNotNull(shownDialog().getButton(DialogInterface.BUTTON_NEGATIVE));
    }

    @Test
    public void show_inputIsSingleLineText() {
        Activity act = activity();
        TextInputDialogHelper.show(act, "Title", null, text -> {
        });
        assertEquals(android.text.InputType.TYPE_CLASS_TEXT,
                inputOf(shownDialog()).getInputType());
    }
}
