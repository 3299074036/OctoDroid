package com.gh4a.widget;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class CommentEditorTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private User user(String login) {
        return User.builder().login(login).id((long) login.hashCode()).build();
    }

    @Test
    public void initialState_enabledAndNoHint() {
        CommentEditor editor = new CommentEditor(activity());
        assertTrue(editor.isEnabled());
        assertFalse(editor.isLocked());
        assertEquals(null, editor.getHint());
    }

    @Test
    public void setLocked_true_disablesAndShowsLockedHint() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setLocked(true, R.string.comment_editor_locked_hint);
        assertTrue(editor.isLocked());
        assertFalse(editor.isEnabled());
        assertEquals(activity().getString(R.string.comment_editor_locked_hint),
                editor.getHint().toString());
    }

    @Test
    public void setLocked_true_zeroHintResId_usesDefaultLockedHint() {
        FragmentActivity activity = activity();
        CommentEditor editor = new CommentEditor(activity);
        editor.setLocked(true, 0);
        assertEquals(activity.getString(R.string.comment_editor_locked_hint),
                editor.getHint().toString());
    }

    @Test
    public void setLocked_false_reEnablesAndClearsHint() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setLocked(true, R.string.comment_editor_locked_hint);
        editor.setLocked(false, 0);
        assertFalse(editor.isLocked());
        assertTrue(editor.isEnabled());
        assertEquals(null, editor.getHint());
    }

    @Test
    public void setCommentEditorHintResId_unlocked_showsHint() {
        FragmentActivity activity = activity();
        CommentEditor editor = new CommentEditor(activity);
        editor.setCommentEditorHintResId(R.string.comment_editor_locked_hint);
        assertEquals(activity.getString(R.string.comment_editor_locked_hint),
                editor.getHint().toString());
    }

    @Test
    public void setCommentEditorHintResId_locked_keepsLockedHint() {
        FragmentActivity activity = activity();
        CommentEditor editor = new CommentEditor(activity);
        editor.setLocked(true, R.string.comment_editor_locked_hint);
        editor.setCommentEditorHintResId(R.string.comment_editor_locked_hint);
        assertEquals(activity.getString(R.string.comment_editor_locked_hint),
                editor.getHint().toString());
    }

    @Test
    public void getLockedHintResId_returnsSetValue() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setLocked(true, R.string.comment_editor_locked_hint);
        assertEquals(R.string.comment_editor_locked_hint, editor.getLockedHintResId());
    }

    @Test
    public void getLockedHintResId_defaultZero() {
        CommentEditor editor = new CommentEditor(activity());
        assertEquals(0, editor.getLockedHintResId());
    }

    @Test
    public void addText_unlocked_insertsText() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setText("");
        editor.addText("hello");
        assertEquals("hello", editor.getText().toString());
    }

    @Test
    public void addText_locked_doesNotInsert() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setText("");
        editor.setLocked(true, 0);
        editor.addText("hello");
        assertEquals("", editor.getText().toString());
    }

    @Test
    public void addQuote_unlocked_insertsQuotedText() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setText("");
        editor.addQuote("quoted line");
        String text = editor.getText().toString();
        assertTrue(text.contains("quoted line"));
        assertTrue(text.contains(">"));
    }

    @Test
    public void addQuote_locked_doesNotInsert() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setText("");
        editor.setLocked(true, 0);
        editor.addQuote("quoted line");
        assertEquals("", editor.getText().toString());
    }

    @Test
    public void setMentionUsers_roundTrip() {
        CommentEditor editor = new CommentEditor(activity());
        Set<User> users = new HashSet<>();
        users.add(user("alice"));
        users.add(user("bob"));
        editor.setMentionUsers(users);
        assertEquals(users, editor.getMentionUsers());
    }

    @Test
    public void setMentionUsers_emptySet_clears() {
        CommentEditor editor = new CommentEditor(activity());
        Set<User> users = new HashSet<>();
        users.add(user("alice"));
        editor.setMentionUsers(users);
        editor.setMentionUsers(new HashSet<>());
        assertTrue(editor.getMentionUsers().isEmpty());
    }

    @Test
    public void onEditorAction_done_returnsTrue() {
        CommentEditor editor = new CommentEditor(activity());
        assertTrue(editor.onEditorAction(editor, EditorInfo.IME_ACTION_DONE, null));
    }

    @Test
    public void onEditorAction_otherAction_returnsFalse() {
        CommentEditor editor = new CommentEditor(activity());
        assertFalse(editor.onEditorAction(editor, EditorInfo.IME_ACTION_NEXT, null));
        assertFalse(editor.onEditorAction(editor, EditorInfo.IME_ACTION_UNSPECIFIED, null));
    }

    @Test
    public void onEditorAction_done_withKeyEvent_returnsTrue() {
        CommentEditor editor = new CommentEditor(activity());
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        assertTrue(editor.onEditorAction(editor, EditorInfo.IME_ACTION_DONE, event));
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertFalse(new CommentEditor(activity, null).isLocked());
        assertFalse(new CommentEditor(activity, null, 0).isLocked());
    }

    @Test
    public void addText_appendsAtCursor() {
        CommentEditor editor = new CommentEditor(activity());
        editor.setText("prefix ");
        editor.setSelection(editor.length());
        editor.addText("suffix");
        assertEquals("prefix suffix", editor.getText().toString());
    }
}
