package com.gh4a.widget;

import android.content.ActivityNotFoundException;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Selection;
import android.text.Spanned;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LinkHandlingTextViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void initAttributes_noLinkHandling_noMovementMethod() {
        LinkHandlingTextView view = new LinkHandlingTextView(activity());
        assertNull(view.getMovementMethod());
    }

    @Test
    public void initAttributes_needsLinkHandling_installsCheckingMethod() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.needsLinkHandling, "true")
                        .build());
        assertTrue(view.getMovementMethod() instanceof android.text.method.LinkMovementMethod);
    }

    @Test
    public void initAttributes_needsLinkHandlingFalse_noMovementMethod() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.needsLinkHandling, "false")
                        .build());
        assertNull(view.getMovementMethod());
    }

    @Test
    public void initAttributes_linksNotClickable() {
        // initAttributes (which calls setLinksClickable(false)) only runs
        // from the AttributeSet constructors; the Context-only constructor
        // leaves the TextView default (true). Use the attrs constructor,
        // which is also how the view is created in production (XML inflate).
        LinkHandlingTextView view = new LinkHandlingTextView(activity(),
                Robolectric.buildAttributeSet().build());
        assertFalse(view.getLinksClickable());
    }

    @Test
    public void onAttachedToWindow_selectableText_reinstallsMovementMethod() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.needsLinkHandling, "true")
                        .build());
        view.setTextIsSelectable(true);
        activity.setContentView(view);
        MovementMethod before = view.getMovementMethod();

        // Force re-attach: detaching and re-attaching runs onAttachedToWindow.
        ((android.view.ViewGroup) view.getParent()).removeView(view);
        activity.setContentView(view);

        assertEquals(before, view.getMovementMethod());
    }

    @Test
    public void dispatchTouchEvent_noSelection_delegatesToSuper() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity);
        view.setText("hello world");
        view.setTextIsSelectable(true);
        activity.setContentView(view);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 500, 200);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10, 10, 0);

        view.dispatchTouchEvent(down);

        assertEquals("hello world", view.getText().toString());
    }

    @Test
    public void dispatchTouchEvent_activeSelection_actionDown_resetsText() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity);
        view.setText("hello world");
        view.setTextIsSelectable(true);
        activity.setContentView(view);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 500, 200);
        Selection.setSelection((Spannable) view.getText(), 0, 5);
        assertEquals(0, view.getSelectionStart());
        assertEquals(5, view.getSelectionEnd());

        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 10, 10, 0);
        view.dispatchTouchEvent(down);

        assertEquals("hello world", view.getText().toString());
    }

    // X coordinate in the middle of the 0..5 span. Robolectric's font
    // metrics are coarsely quantized (getPrimaryHorizontal only returns 0/5/10
    // for this text) and getPrimaryHorizontal(2) maps back to offset 10, past
    // the span, so a touch there misses the span entirely.
    private float spanMiddleX(LinkHandlingTextView view) {
        android.text.Layout layout = view.getLayout();
        return (layout.getPrimaryHorizontal(0) + layout.getPrimaryHorizontal(5)) / 2;
    }

    @Test
    public void checkingLinkMethod_activityNotFound_showsToastAndConsumes() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.needsLinkHandling, "true")
                        .build());
        ClickableSpan throwingSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                throw new ActivityNotFoundException("no handler");
            }
        };
        SpannableString text = new SpannableString("click here");
        text.setSpan(throwingSpan, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
        activity.setContentView(view);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 500, 200);

        float x = spanMiddleX(view);
        float y = view.getLayout().getLineBaseline(0);
        long now = android.os.SystemClock.uptimeMillis();
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        view.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0));

        assertEquals(activity.getString(R.string.link_not_openable),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void checkingLinkMethod_securityException_consumesWithoutToast() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.needsLinkHandling, "true")
                        .build());
        ClickableSpan throwingSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                throw new SecurityException("non-exported activity");
            }
        };
        SpannableString text = new SpannableString("click here");
        text.setSpan(throwingSpan, 0, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(text);
        activity.setContentView(view);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 500, 200);

        float x = spanMiddleX(view);
        float y = view.getLayout().getLineBaseline(0);
        long now = android.os.SystemClock.uptimeMillis();
        boolean down = view.dispatchTouchEvent(
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
        boolean up = view.dispatchTouchEvent(
                MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0));

        assertTrue(down || up);
        assertNull(ShadowToast.getLatestToast());
    }

    @Test
    public void constructor_threeArg_createsAndDisablesLinksClickable() {
        LinkHandlingTextView view = new LinkHandlingTextView(activity(),
                Robolectric.buildAttributeSet().build(), 0);
        assertNotNull(view);
        assertFalse(view.getLinksClickable());
    }

    @Test
    public void initAttributes_autoLink_installsCheckingMethod() {
        LinkHandlingTextView view = new LinkHandlingTextView(activity(),
                Robolectric.buildAttributeSet()
                        .addAttribute(android.R.attr.autoLink, "web")
                        .build());
        assertNotNull(view.getMovementMethod());
    }

    @Test
    public void onAttachedToWindow_selectableButDisabled_keepsMovementMethod() {
        FragmentActivity activity = activity();
        LinkHandlingTextView view = new LinkHandlingTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.needsLinkHandling, "true")
                        .build());
        view.setTextIsSelectable(true);
        view.setEnabled(false);
        activity.setContentView(view);
        MovementMethod before = view.getMovementMethod();

        ((android.view.ViewGroup) view.getParent()).removeView(view);
        activity.setContentView(view);

        assertEquals(before, view.getMovementMethod());
    }
}
