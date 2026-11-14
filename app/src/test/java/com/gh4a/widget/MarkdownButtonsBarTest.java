package com.gh4a.widget;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@RunWith(RobolectricTestRunner.class)
public class MarkdownButtonsBarTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private MarkdownButtonsBar bar(FragmentActivity activity) {
        return new MarkdownButtonsBar(activity);
    }

    private EditText editorWithSelection(FragmentActivity activity, String text) {
        EditText editor = new EditText(activity);
        editor.setText(text);
        editor.setSelection(0, text.length());
        return editor;
    }

    @Test
    public void onClick_noEditText_doesNotThrow() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        bar.findViewById(R.id.md_bold).performClick();
        bar.findViewById(R.id.md_h1).performClick();
    }

    @Test
    public void onClick_bold_wrapsSelection() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_bold).performClick();

        assertEquals("**hello**", editor.getText().toString());
    }

    @Test
    public void onClick_italic_wrapsSelection() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_italic).performClick();

        assertEquals("_hello_", editor.getText().toString());
    }

    @Test
    public void onClick_strikethrough_wrapsSelection() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_strikethrough).performClick();

        assertEquals("~~hello~~", editor.getText().toString());
    }

    @Test
    public void onClick_h1_prependsHash() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_h1).performClick();

        assertTrue(editor.getText().toString().contains("# hello"));
    }

    @Test
    public void onClick_h2_prependsTwoHashes() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_h2).performClick();

        assertTrue(editor.getText().toString().contains("## hello"));
    }

    @Test
    public void onClick_h3_prependsThreeHashes() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_h3).performClick();

        assertTrue(editor.getText().toString().contains("### hello"));
    }

    @Test
    public void onClick_bulletList_prefixesDash() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_bullet_list).performClick();

        assertTrue(editor.getText().toString().contains("- hello"));
    }

    @Test
    public void onClick_numberList_prefixesNumber() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_number_list).performClick();

        assertTrue(editor.getText().toString().contains("1. hello"));
    }

    @Test
    public void onClick_taskList_prefixesCheckbox() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_task_list).performClick();

        assertTrue(editor.getText().toString().contains("- [ ] hello"));
    }

    @Test
    public void onClick_divider_insertsDivider() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_divider).performClick();

        assertTrue(editor.getText().toString().contains("___"));
    }

    @Test
    public void onClick_code_wrapsSelectionInBackticks() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_code).performClick();

        assertEquals("`hello`", editor.getText().toString());
    }

    @Test
    public void onClick_quote_prefixesAngleBracket() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_quote).performClick();

        assertTrue(editor.getText().toString().contains("> hello"));
    }

    @Test
    public void onClick_link_wrapsSelectionAsLink() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_link).performClick();

        assertEquals("[hello](url)", editor.getText().toString());
    }

    @Test
    public void onClick_image_wrapsSelectionAsImage() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);

        bar.findViewById(R.id.md_image).performClick();

        assertEquals("![hello](url)", editor.getText().toString());
    }

    @Test
    public void onTouch_noBehavior_returnsFalse() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        View button = bar.findViewById(R.id.md_bold);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);

        assertFalse(bar.onTouch(button, down));
    }

    @Test
    public void onTouch_withBehavior_actionDown_disablesBehavior() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        ToggleableBottomSheetBehavior<View> behavior = mock(ToggleableBottomSheetBehavior.class);
        bar.setBottomSheetBehavior(behavior);
        View button = bar.findViewById(R.id.md_bold);

        bar.onTouch(button, MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0));

        verify(behavior).setEnabled(false);
    }

    @Test
    public void onTouch_withBehavior_actionUp_enablesBehavior() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        ToggleableBottomSheetBehavior<View> behavior = mock(ToggleableBottomSheetBehavior.class);
        bar.setBottomSheetBehavior(behavior);
        View button = bar.findViewById(R.id.md_bold);

        bar.onTouch(button, MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0, 0, 0));

        verify(behavior).setEnabled(true);
    }

    @Test
    public void onTouch_withBehavior_actionCancel_enablesBehavior() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        ToggleableBottomSheetBehavior<View> behavior = mock(ToggleableBottomSheetBehavior.class);
        bar.setBottomSheetBehavior(behavior);
        View button = bar.findViewById(R.id.md_bold);

        bar.onTouch(button, MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));

        verify(behavior).setEnabled(true);
    }

    @Test
    public void onTouch_withBehavior_actionMove_doesNotTouchBehavior() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        ToggleableBottomSheetBehavior<View> behavior = mock(ToggleableBottomSheetBehavior.class);
        bar.setBottomSheetBehavior(behavior);
        View button = bar.findViewById(R.id.md_bold);

        boolean result = bar.onTouch(button,
                MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0, 0, 0));

        assertFalse(result);
        verifyNoInteractions(behavior);
    }

    @Test
    public void setButtonsBackgroundColor_setsContainerBackground() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);

        bar.setButtonsBackgroundColor(Color.RED);

        View container = bar.findViewById(R.id.buttons_container);
        if (container == null) {
            container = bar.findViewById(R.id.buttons_container_landscape);
        }
        assertEquals(Color.RED, ((ColorDrawable) container.getBackground()).getColor());
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertTrue(new MarkdownButtonsBar(activity, null).findViewById(R.id.md_bold) != null);
        assertTrue(new MarkdownButtonsBar(activity, null, 0).findViewById(R.id.md_bold) != null);
    }

    @Test
    @Config(qualifiers = "land")
    public void initialize_landscape_usesLandscapeContainer() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        assertNotNull(bar.findViewById(R.id.md_bold));
        // The landscape layout has no portrait container.
        assertNull(bar.findViewById(R.id.buttons_container));
        assertNotNull(bar.findViewById(R.id.buttons_container_landscape));
    }

    @Test
    public void onClick_unknownButtonId_doesNothing() {
        FragmentActivity activity = activity();
        MarkdownButtonsBar bar = bar(activity);
        EditText editor = editorWithSelection(activity, "hello");
        bar.setEditText(editor);
        bar.onClick(new View(activity));
        assertEquals("hello", editor.getText().toString());
    }
}
