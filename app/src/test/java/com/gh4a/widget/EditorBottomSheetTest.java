package com.gh4a.widget;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.gh4a.utils.UiUtils;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.meisolsson.githubsdk.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Single;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class EditorBottomSheetTest {

    private FragmentActivity activity;
    private CoordinatorLayout root;
    private EditorBottomSheet sheet;
    private ToggleableBottomSheetBehavior<View> behavior;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        root = new CoordinatorLayout(activity);
        activity.setContentView(root);
        sheet = new EditorBottomSheet(activity);
        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        behavior = new ToggleableBottomSheetBehavior<>();
        lp.setBehavior(behavior);
        sheet.setLayoutParams(lp);
        root.addView(sheet);
        idleMainLooper();
    }

    private void idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private CommentEditor basicEditor() {
        return sheet.findViewById(R.id.et_basic_editor);
    }

    private CommentEditor advancedEditor() {
        return sheet.findViewById(R.id.editor);
    }

    private View sendButton() {
        return sheet.findViewById(R.id.send_button);
    }

    private View toggle() {
        return sheet.findViewById(R.id.iv_advanced_editor_toggle);
    }

    private int basicPeek() {
        return activity.getResources()
                .getDimensionPixelSize(R.dimen.comment_editor_peek_height);
    }

    private int advancedPeek() {
        return activity.getResources()
                .getDimensionPixelSize(R.dimen.comment_advanced_editor_peek_height);
    }

    private int topShadow() {
        return activity.getResources()
                .getDimensionPixelSize(R.dimen.bottom_sheet_top_shadow_height);
    }

    private EditorBottomSheet.Callback callbackReturning(Single<?> result) {
        EditorBottomSheet.Callback cb = mock(EditorBottomSheet.Callback.class);
        when(cb.getCommentEditorHintResId()).thenReturn(R.string.comment_editor_locked_hint);
        when(cb.getEditorErrorMessageResId()).thenReturn(R.string.issue_error_comment);
        doReturn(result).when(cb).onEditorDoSend(anyString());
        when(cb.getActivity()).thenReturn(activity);
        when(cb.getRootLayout()).thenReturn(root);
        return cb;
    }

    private boolean behaviorEnabled() throws Exception {
        Field f = ToggleableBottomSheetBehavior.class.getDeclaredField("mEnabled");
        f.setAccessible(true);
        return (boolean) f.get(behavior);
    }

    private MotionEvent event(int action) {
        return MotionEvent.obtain(0, 0, action, 10, 10, 0);
    }

    // ---------- construction / attrs ----------

    @Test
    public void defaults_collapsibleAndSendDisabledWhenEmpty() {
        assertTrue(toggle().getVisibility() == View.VISIBLE);
        assertFalse(sendButton().isEnabled());
        assertFalse(sheet.isInAdvancedMode());
    }

    @Test
    public void attrs_nonCollapsible_autoEntersAdvancedMode() {
        FragmentActivity a = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        CoordinatorLayout r = new CoordinatorLayout(a);
        a.setContentView(r);
        EditorBottomSheet s = new EditorBottomSheet(a,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.collapsible, "false")
                        .addAttribute(R.attr.allowEmpty, "true")
                        .build());
        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setBehavior(new ToggleableBottomSheetBehavior<>());
        s.setLayoutParams(lp);
        r.addView(s);
        idleMainLooper();

        assertTrue(s.isInAdvancedMode());
        assertEquals(View.GONE, s.findViewById(R.id.iv_advanced_editor_toggle).getVisibility());
        assertTrue(s.findViewById(R.id.send_button).isEnabled());
    }

    // ---------- header / callback / hint ----------

    @Test
    public void addHeaderView_showsContainerWithView() {
        TextView header = new TextView(activity);
        sheet.addHeaderView(header);
        ViewGroup container = sheet.findViewById(R.id.bottom_sheet_header_container);
        assertEquals(View.VISIBLE, container.getVisibility());
        assertEquals(header, container.getChildAt(0));
    }

    @Test
    public void setCallback_null_doesNotThrow() {
        sheet.setCallback(null);
        sheet.updateHint();
    }

    @Test
    public void setCallback_setsEditorHint() {
        EditorBottomSheet.Callback cb = callbackReturning(Single.just("ok"));
        sheet.setCallback(cb);
        assertEquals(activity.getString(R.string.comment_editor_locked_hint),
                basicEditor().getHint().toString());
    }

    @Test
    public void updateHint_withoutCallback_doesNotThrow() {
        sheet.updateHint();
    }

    // ---------- lock ----------

    @Test
    public void setLocked_true_locksBasicEditorAndHidesToggle() {
        sheet.setLocked(true, R.string.comment_editor_locked_hint);
        assertTrue(basicEditor().isLocked());
        assertEquals(View.GONE, toggle().getVisibility());
    }

    @Test
    public void setLocked_false_unlocksAndShowsToggle() {
        sheet.setLocked(true, R.string.comment_editor_locked_hint);
        sheet.setLocked(false, 0);
        assertFalse(basicEditor().isLocked());
        assertEquals(View.VISIBLE, toggle().getVisibility());
    }

    // ---------- mentions / highlight ----------

    @Test
    public void setMentionUsers_basicOnlyBeforeAdvanced() {
        Set<User> users = new HashSet<>();
        users.add(User.builder().login("alice").id(0L).build());
        sheet.setMentionUsers(users);
        assertEquals(users, basicEditor().getMentionUsers());
    }

    @Test
    public void setMentionUsers_afterAdvanced_setsBothEditors() {
        sheet.setAdvancedMode(true);
        Set<User> users = new HashSet<>();
        users.add(User.builder().login("alice").id(0L).build());
        sheet.setMentionUsers(users);
        assertEquals(users, basicEditor().getMentionUsers());
        assertEquals(users, advancedEditor().getMentionUsers());
    }

    @Test
    public void setHighlightColor_afterAdvanced_setsButtonsBackground() {
        sheet.setAdvancedMode(true);
        sheet.setHighlightColor(android.R.attr.textColorPrimary);
        int expected = UiUtils.resolveColor(activity, android.R.attr.textColorPrimary);
        View container = sheet.findViewById(R.id.buttons_container);
        if (container == null) {
            container = sheet.findViewById(R.id.buttons_container_landscape);
        }
        assertNotNull(container);
        assertEquals(expected, ((ColorDrawable) container.getBackground()).getColor());
    }

    @Test
    public void setHighlightColor_beforeAdvanced_doesNotThrow() {
        sheet.setHighlightColor(android.R.attr.textColorPrimary);
    }

    // ---------- text: quote / add / set ----------

    @Test
    public void addText_basicMode_insertsIntoBasicEditor() {
        sheet.addText("hello");
        assertEquals("hello", basicEditor().getText().toString());
    }

    @Test
    public void addQuote_basicMode_insertsQuotedText() {
        sheet.addQuote("quoted");
        String text = basicEditor().getText().toString();
        assertTrue(text.contains("quoted"));
        assertTrue(text.contains(">"));
    }

    @Test
    public void addText_advancedMode_insertsIntoBothEditors() {
        sheet.setAdvancedMode(true);
        sheet.addText("hello");
        assertTrue(advancedEditor().getText().toString().contains("hello"));
        assertTrue(basicEditor().getText().toString().contains("hello"));
    }

    @Test
    public void addText_locked_doesNotInsert() {
        sheet.setLocked(true, 0);
        sheet.addText("hello");
        assertEquals("", basicEditor().getText().toString());
    }

    @Test
    public void setCommentText_basicMode_setsBasicText() {
        sheet.setCommentText("hello", false);
        assertEquals("hello", basicEditor().getText().toString());
    }

    @Test
    public void setCommentText_advancedMode_setsAdvancedText() {
        sheet.setAdvancedMode(true);
        sheet.setCommentText("adv", false);
        assertEquals("adv", advancedEditor().getText().toString());
    }

    // ---------- send button state ----------

    @Test
    public void sendButton_emptyText_disabled() {
        assertFalse(sendButton().isEnabled());
    }

    @Test
    public void sendButton_withText_enabled() {
        basicEditor().setText("x");
        assertTrue(sendButton().isEnabled());
    }

    @Test
    public void sendButton_emptyTextCleared_disabledAgain() {
        basicEditor().setText("x");
        assertTrue(sendButton().isEnabled());
        basicEditor().setText("");
        assertFalse(sendButton().isEnabled());
    }

    @Test
    public void setAllowEmpty_true_enablesSendWithEmptyText() {
        sheet.setAllowEmpty(true);
        assertTrue(sendButton().isEnabled());
    }

    @Test
    public void setAllowEmpty_false_disablesSendWithEmptyText() {
        basicEditor().setText("x");
        sheet.setAllowEmpty(true);
        assertTrue(sendButton().isEnabled());
        sheet.setAllowEmpty(false);
        basicEditor().setText("");
        assertFalse(sendButton().isEnabled());
    }

    // ---------- touch ----------

    @Test
    public void onTouch_basicEditorDown_notifiesListenerScrolling() {
        EditorBottomSheet.Listener listener = mock(EditorBottomSheet.Listener.class);
        sheet.setListener(listener);
        sheet.onTouch(basicEditor(), event(MotionEvent.ACTION_DOWN));
        verify(listener).onScrollingInBasicEditor(true);
    }

    @Test
    public void onTouch_basicEditorUp_notifiesListenerNotScrolling() {
        EditorBottomSheet.Listener listener = mock(EditorBottomSheet.Listener.class);
        sheet.setListener(listener);
        sheet.onTouch(basicEditor(), event(MotionEvent.ACTION_UP));
        verify(listener).onScrollingInBasicEditor(false);
    }

    @Test
    public void onTouch_basicScrollViewDown_notifiesListener() {
        EditorBottomSheet.Listener listener = mock(EditorBottomSheet.Listener.class);
        sheet.setListener(listener);
        View scrollView = sheet.findViewById(R.id.basic_editor_scroll);
        sheet.onTouch(scrollView, event(MotionEvent.ACTION_DOWN));
        verify(listener).onScrollingInBasicEditor(true);
    }

    @Test
    public void onTouch_moveAction_returnsFalseWithoutCallbacks() {
        EditorBottomSheet.Listener listener = mock(EditorBottomSheet.Listener.class);
        sheet.setListener(listener);
        assertFalse(sheet.onTouch(basicEditor(), event(MotionEvent.ACTION_MOVE)));
        verify(listener, never()).onScrollingInBasicEditor(true);
        verify(listener, never()).onScrollingInBasicEditor(false);
    }

    @Test
    public void onTouch_otherViewDown_disablesBehavior() throws Exception {
        sheet.onTouch(new View(activity), event(MotionEvent.ACTION_DOWN));
        assertFalse(behaviorEnabled());
    }

    @Test
    public void onTouch_otherViewUp_enablesBehavior() throws Exception {
        sheet.onTouch(new View(activity), event(MotionEvent.ACTION_DOWN));
        assertFalse(behaviorEnabled());
        sheet.onTouch(new View(activity), event(MotionEvent.ACTION_UP));
        assertTrue(behaviorEnabled());
    }

    @Test
    public void onTouch_noListener_doesNotThrow() {
        sheet.setListener(null);
        sheet.onTouch(basicEditor(), event(MotionEvent.ACTION_DOWN));
    }

    // ---------- advanced mode ----------

    @Test
    public void onClick_toggle_entersAdvancedMode() {
        toggle().performClick();
        assertTrue(sheet.isInAdvancedMode());
        assertNotNull(advancedEditor());
    }

    @Test
    public void onClick_toggleTwice_leavesAdvancedModeViaCallback() throws Exception {
        toggle().performClick();
        assertTrue(sheet.isInAdvancedMode());
        // Hiding runs through the BottomSheetCallback when the sheet settles
        // to COLLAPSED; drive it directly for determinism.
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onStateChanged(sheet, BottomSheetBehavior.STATE_COLLAPSED);
        assertFalse(sheet.isInAdvancedMode());
    }

    @Test
    public void constructor_threeArg_creates() {
        EditorBottomSheet s = new EditorBottomSheet(activity, null, 0);
        assertNotNull(s);
        assertNotNull(s.findViewById(R.id.et_basic_editor));
    }

    @Test
    public void onClick_toggleTwice_leavesAdvancedMode() throws Exception {
        toggle().performClick();
        assertTrue(sheet.isInAdvancedMode());
        // Second click while advanced: setAdvancedMode(false).
        toggle().performClick();
        idleMainLooper();
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onStateChanged(sheet, BottomSheetBehavior.STATE_COLLAPSED);
        assertFalse(sheet.isInAdvancedMode());
    }

    @Test
    public void setAdvancedMode_falseWithAdvancedEditor_collapsesSheet() {
        sheet.setAdvancedMode(true);
        idleMainLooper();
        assertEquals(BottomSheetBehavior.STATE_EXPANDED, behavior.getState());
        sheet.setAdvancedMode(false);
        idleMainLooper();
        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, behavior.getState());
    }

    @Test
    public void setCallback_afterAdvanced_setsAdvancedHint() {
        sheet.setAdvancedMode(true);
        EditorBottomSheet.Callback cb = callbackReturning(Single.just("ok"));
        sheet.setCallback(cb);
        assertEquals(activity.getString(R.string.comment_editor_locked_hint),
                advancedEditor().getHint().toString());
    }

    @Test
    public void setLocked_afterAdvanced_locksAdvancedEditor() {
        sheet.setAdvancedMode(true);
        sheet.setLocked(true, R.string.comment_editor_locked_hint);
        assertTrue(advancedEditor().isLocked());
        assertEquals(View.GONE, toggle().getVisibility());
    }

    @Test
    public void addQuote_advancedMode_insertsIntoBothEditors() {
        sheet.setAdvancedMode(true);
        sheet.addQuote("quoted");
        assertTrue(advancedEditor().getText().toString().contains("quoted"));
        assertTrue(basicEditor().getText().toString().contains("quoted"));
    }

    @Test
    public void onClick_unknownView_doesNothing() {
        sheet.onClick(new View(activity));
    }

    @Test
    public void isExpanded_peekEqualsHeight_returnsFalse() {
        sheet.setAdvancedMode(true);
        idleMainLooper();
        assertTrue(sheet.isExpanded());
        behavior.setPeekHeight(sheet.getHeight());
        assertFalse(sheet.isExpanded());
    }

    @Test
    public void attrs_nonCollapsible_alreadyAdvanced_skipsAutoEnter() {
        FragmentActivity a = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        CoordinatorLayout r = new CoordinatorLayout(a);
        a.setContentView(r);
        EditorBottomSheet s = new EditorBottomSheet(a,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.collapsible, "false")
                        .addAttribute(R.attr.allowEmpty, "true")
                        .build());
        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setBehavior(new ToggleableBottomSheetBehavior<>());
        s.setLayoutParams(lp);
        r.addView(s);
        // Enter advanced mode before the posted init runnable runs: the
        // runnable's auto-enter condition is then false.
        s.setAdvancedMode(true);
        idleMainLooper();
        assertTrue(s.isInAdvancedMode());
    }

    @Test
    public void setHighlightColor_beforeAdvanced_appliesOnEnter() {
        sheet.setHighlightColor(android.R.attr.textColorPrimary);
        sheet.setAdvancedMode(true);
        int expected = UiUtils.resolveColor(activity, android.R.attr.textColorPrimary);
        View container = sheet.findViewById(R.id.buttons_container);
        if (container == null) {
            container = sheet.findViewById(R.id.buttons_container_landscape);
        }
        assertNotNull(container);
        assertEquals(expected, ((ColorDrawable) container.getBackground()).getColor());
    }

    @Test
    public void onClick_send_successWithNullActivity_skipsNotify() throws Exception {
        EditorBottomSheet.Callback cb = mock(EditorBottomSheet.Callback.class);
        when(cb.getCommentEditorHintResId()).thenReturn(R.string.comment_editor_locked_hint);
        when(cb.getEditorErrorMessageResId()).thenReturn(R.string.issue_error_comment);
        doReturn(Single.just("ok")).when(cb).onEditorDoSend(anyString());
        // send() uses the activity, onClick uses it again for hideIme, and
        // only the async success callback sees null.
        when(cb.getActivity()).thenReturn(activity, activity, null);
        when(cb.getRootLayout()).thenReturn(root);
        sheet.setCallback(cb);
        basicEditor().setText("hello");

        sendButton().performClick();

        verify(cb).onEditorDoSend("hello");
        long deadline = System.currentTimeMillis() + 10000;
        while (!basicEditor().getText().toString().isEmpty()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMainLooper();
        }
        assertEquals("", basicEditor().getText().toString());
        verify(cb, never()).onEditorTextSent();
    }

    @Test
    public void savedState_creator_newArray_returnsSizedArray() throws Exception {
        Class<?> ssClass = Class.forName("com.gh4a.widget.EditorBottomSheet$SavedState");
        Parcelable.Creator<?> creator =
                (Parcelable.Creator<?>) ssClass.getField("CREATOR").get(null);
        assertEquals(3, creator.newArray(3).length);
    }

    @Test
    public void behaviorCallback_onStateChanged_expanded_doesNothing() throws Exception {
        toggle().performClick();
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onStateChanged(sheet, BottomSheetBehavior.STATE_EXPANDED);
        assertTrue(sheet.isInAdvancedMode());
    }

    @Test
    public void behaviorCallback_onSlide_doesNothing() throws Exception {
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onSlide(sheet, 0.5f);
    }

    @Test
    public void behaviorCallback_collapsedWhenNotAdvanced_doesNothing() throws Exception {
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onStateChanged(sheet, BottomSheetBehavior.STATE_COLLAPSED);
        assertFalse(sheet.isInAdvancedMode());
    }

    @Test
    public void setAdvancedMode_falseWithoutAdvancedEditor_returnsEarly() {
        sheet.setAdvancedMode(false);
        assertFalse(sheet.isInAdvancedMode());
    }

    @Test
    public void setAdvancedMode_true_copiesBasicTextIntoAdvanced() {
        basicEditor().setText("hello");
        sheet.setAdvancedMode(true);
        assertEquals("hello", advancedEditor().getText().toString());
    }

    @Test
    public void setAdvancedMode_hideViaCallback_copiesAdvancedTextBack() throws Exception {
        sheet.setAdvancedMode(true);
        advancedEditor().setText("adv text");
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onStateChanged(sheet, BottomSheetBehavior.STATE_COLLAPSED);
        assertEquals("adv text", basicEditor().getText().toString());
    }

    @Test
    public void advancedMode_listenerNotifiedOnToggle() throws Exception {
        EditorBottomSheet.Listener listener = mock(EditorBottomSheet.Listener.class);
        sheet.setListener(listener);
        sheet.setAdvancedMode(true);
        verify(listener).onToggleAdvancedMode(true);
        Field f = EditorBottomSheet.class.getDeclaredField("mBehaviorCallback");
        f.setAccessible(true);
        BottomSheetBehavior.BottomSheetCallback cb =
                (BottomSheetBehavior.BottomSheetCallback) f.get(sheet);
        cb.onStateChanged(sheet, BottomSheetBehavior.STATE_COLLAPSED);
        verify(listener).onToggleAdvancedMode(false);
    }

    // ---------- offset / peek / expand ----------

    @Test
    public void onOffsetChanged_positiveOffset_increasesPeekHeight() {
        sheet.onOffsetChanged(new AppBarLayout(activity), 10);
        assertEquals(basicPeek() + 10, behavior.getPeekHeight());
    }

    @Test
    public void onOffsetChanged_negativeOffsetBeyondRange_skipsUpdate() {
        sheet.onOffsetChanged(new AppBarLayout(activity), 10);
        int peek = behavior.getPeekHeight();
        sheet.onOffsetChanged(new AppBarLayout(activity), -100);
        assertEquals(peek, behavior.getPeekHeight());
    }

    @Test
    public void resetPeekHeight_setsPeekToBasicPlusOffset() {
        sheet.resetPeekHeight(50);
        assertEquals(basicPeek() + 50, behavior.getPeekHeight());
    }

    @Test
    public void setResizingView_visible_setsBottomPadding() {
        View resizing = new View(activity);
        sheet.setResizingView(resizing);
        sheet.resetPeekHeight(0);
        assertEquals(basicPeek() - topShadow(), resizing.getPaddingBottom());
    }

    @Test
    public void setResizingView_goneSheet_zeroBottomPadding() {
        View resizing = new View(activity);
        sheet.setResizingView(resizing);
        sheet.setVisibility(View.GONE);
        sheet.resetPeekHeight(0);
        assertEquals(0, resizing.getPaddingBottom());
    }

    @Test
    public void isExpanded_falseWhenCollapsed() {
        assertFalse(sheet.isExpanded());
    }

    @Test
    public void isExpanded_trueAfterAdvancedModeAndIdle() {
        sheet.setAdvancedMode(true);
        idleMainLooper();
        assertTrue(sheet.isExpanded());
    }

    // ---------- send ----------

    @Test
    public void onClick_send_success_clearsTextAndNotifies() throws Exception {
        AtomicBoolean textSent = new AtomicBoolean(false);
        // Wrap the mock so we can observe onEditorTextSent.
        EditorBottomSheet.Callback observing = mock(EditorBottomSheet.Callback.class);
        when(observing.getCommentEditorHintResId())
                .thenReturn(R.string.comment_editor_locked_hint);
        when(observing.getEditorErrorMessageResId()).thenReturn(R.string.issue_error_comment);
        doReturn(Single.just("ok")).when(observing).onEditorDoSend(anyString());
        when(observing.getActivity()).thenReturn(activity);
        when(observing.getRootLayout()).thenReturn(root);
        org.mockito.Mockito.doAnswer(inv -> {
            textSent.set(true);
            return null;
        }).when(observing).onEditorTextSent();
        sheet.setCallback(observing);
        basicEditor().setText("hello");

        sendButton().performClick();

        verify(observing).onEditorDoSend("hello");
        long deadline = System.currentTimeMillis() + 10000;
        while (!textSent.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMainLooper();
        }
        assertTrue("onEditorTextSent was not called", textSent.get());
        assertEquals("", basicEditor().getText().toString());
    }

    @Test
    public void onClick_send_error_keepsTextAndDoesNotNotify() throws Exception {
        EditorBottomSheet.Callback cb = mock(EditorBottomSheet.Callback.class);
        when(cb.getCommentEditorHintResId()).thenReturn(R.string.comment_editor_locked_hint);
        when(cb.getEditorErrorMessageResId()).thenReturn(R.string.issue_error_comment);
        when(cb.onEditorDoSend(anyString()))
                .thenReturn(Single.error(new RuntimeException("boom")));
        when(cb.getActivity()).thenReturn(activity);
        when(cb.getRootLayout()).thenReturn(root);
        sheet.setCallback(cb);
        basicEditor().setText("hello");

        sendButton().performClick();

        verify(cb).onEditorDoSend("hello");
        Thread.sleep(500);
        idleMainLooper();
        verify(cb, never()).onEditorTextSent();
        assertEquals("hello", basicEditor().getText().toString());
    }

    // ---------- text watcher ----------

    @Test
    public void buttonEnableTextWatcher_callbacks_doNotThrow() throws Exception {
        Field f = EditorBottomSheet.class.getDeclaredField("mButtonEnableTextWatcher");
        f.setAccessible(true);
        android.text.TextWatcher watcher = (android.text.TextWatcher) f.get(sheet);
        watcher.beforeTextChanged("x", 0, 0, 1);
        watcher.onTextChanged("x", 0, 0, 1);
        basicEditor().setText("x");
        watcher.afterTextChanged(basicEditor().getText());
        assertTrue(sendButton().isEnabled());
    }

    // ---------- save / restore ----------

    private Parcelable parcelRoundTrip(Parcelable state) throws Exception {
        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Class<?> ssClass = Class.forName("com.gh4a.widget.EditorBottomSheet$SavedState");
        Parcelable.Creator<?> creator =
                (Parcelable.Creator<?>) ssClass.getField("CREATOR").get(null);
        return (Parcelable) creator.createFromParcel(parcel);
    }

    private EditorBottomSheet freshSheet() {
        FragmentActivity a = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        CoordinatorLayout r = new CoordinatorLayout(a);
        a.setContentView(r);
        EditorBottomSheet s = new EditorBottomSheet(a);
        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setBehavior(new ToggleableBottomSheetBehavior<>());
        s.setLayoutParams(lp);
        r.addView(s);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return s;
    }

    @Test
    public void saveRestore_basicMode_restoresCommentText() throws Exception {
        sheet.setCommentText("hello", false);
        Parcelable saved = parcelRoundTrip(sheet.onSaveInstanceState());

        EditorBottomSheet restored = freshSheet();
        restored.onRestoreInstanceState(saved);

        assertFalse(restored.isInAdvancedMode());
        assertEquals("hello",
                ((CommentEditor) restored.findViewById(R.id.et_basic_editor)).getText().toString());
    }

    @Test
    public void saveRestore_advancedMode_restoresModeAndText() throws Exception {
        sheet.setAdvancedMode(true);
        sheet.setCommentText("adv text", false);
        Parcelable saved = parcelRoundTrip(sheet.onSaveInstanceState());

        EditorBottomSheet restored = freshSheet();
        restored.onRestoreInstanceState(saved);

        assertTrue(restored.isInAdvancedMode());
        assertEquals("adv text",
                ((CommentEditor) restored.findViewById(R.id.editor)).getText().toString());
    }

    @Test
    public void restore_nullCommentText_skipsSetCommentText() throws Exception {
        Class<?> ssClass = Class.forName("com.gh4a.widget.EditorBottomSheet$SavedState");
        java.lang.reflect.Constructor<?> ctor =
                ssClass.getDeclaredConstructor(Parcelable.class);
        ctor.setAccessible(true);
        // Build a SavedState whose commentText stays null.
        Object ss = ctor.newInstance(android.view.View.BaseSavedState.EMPTY_STATE);
        Field modeField = ssClass.getDeclaredField("isInAdvancedMode");
        modeField.setAccessible(true);
        modeField.set(ss, false);

        EditorBottomSheet restored = freshSheet();
        restored.onRestoreInstanceState((Parcelable) ss);

        assertFalse(restored.isInAdvancedMode());
        assertEquals("",
                ((CommentEditor) restored.findViewById(R.id.et_basic_editor)).getText().toString());
    }

    @Test
    public void restore_nonSavedState_delegatesToSuper() {
        sheet.setCommentText("hello", false);
        sheet.onRestoreInstanceState(android.view.View.BaseSavedState.EMPTY_STATE);
        assertEquals("hello", basicEditor().getText().toString());
    }

    // ---------- pager adapter ----------

    private Object pagerAdapter() throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.widget.EditorBottomSheet$AdvancedEditorPagerAdapter");
        java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor(android.content.Context.class);
        ctor.setAccessible(true);
        return ctor.newInstance(activity);
    }

    private Method accessibleMethod(Object target, String name, Class<?>... types)
            throws Exception {
        // The adapter class is private, so its public methods need
        // setAccessible before invoke.
        Method method = target.getClass().getMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    @Test
    public void pagerAdapter_countIsTwo() throws Exception {
        Object adapter = pagerAdapter();
        Method getCount = accessibleMethod(adapter, "getCount");
        assertEquals(2, getCount.invoke(adapter));
    }

    @Test
    public void pagerAdapter_titles() throws Exception {
        Object adapter = pagerAdapter();
        Method getPageTitle = accessibleMethod(adapter, "getPageTitle", int.class);
        assertEquals(activity.getString(R.string.edit), getPageTitle.invoke(adapter, 0));
        assertEquals(activity.getString(R.string.preview), getPageTitle.invoke(adapter, 1));
    }

    @Test
    public void pagerAdapter_instantiateItem_returnsPageViews() throws Exception {
        sheet.setAdvancedMode(true);
        Object adapter = pagerAdapter();
        Method instantiateItem = accessibleMethod(adapter, "instantiateItem",
                ViewGroup.class, int.class);
        ViewGroup pager = sheet.findViewById(R.id.view_pager);
        assertEquals(sheet.findViewById(R.id.comment_container),
                instantiateItem.invoke(adapter, pager, 0));
        assertEquals(sheet.findViewById(R.id.preview),
                instantiateItem.invoke(adapter, pager, 1));
    }

    @Test
    public void pagerAdapter_instantiateItem_unknownPosition_returnsNull() throws Exception {
        sheet.setAdvancedMode(true);
        Object adapter = pagerAdapter();
        Method instantiateItem = accessibleMethod(adapter, "instantiateItem",
                ViewGroup.class, int.class);
        ViewGroup pager = sheet.findViewById(R.id.view_pager);
        assertEquals(null, instantiateItem.invoke(adapter, pager, 5));
    }

    @Test
    public void pagerAdapter_isViewFromObject() throws Exception {
        Object adapter = pagerAdapter();
        Method isViewFromObject = accessibleMethod(adapter, "isViewFromObject",
                View.class, Object.class);
        View view = new View(activity);
        assertTrue((boolean) isViewFromObject.invoke(adapter, view, view));
        assertFalse((boolean) isViewFromObject.invoke(adapter, view, new View(activity)));
    }
}
