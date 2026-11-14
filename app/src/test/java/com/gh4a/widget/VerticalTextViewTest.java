package com.gh4a.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class VerticalTextViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    // Robolectric's Paint.measureText returns ~1px per character (no real
    // font), so a short text is about as wide as it is tall; only a long
    // text is unambiguously wider than tall after the dimension swap.
    private String longText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("hello world ");
        }
        return sb.toString();
    }

    @Test
    public void onMeasure_swapsWidthAndHeightForHorizontalText() {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity, null);
        view.setText(longText());

        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));

        // Horizontal text is wider than tall; after the swap the measured
        // width is the text height and the measured height is the text width.
        assertTrue(view.getMeasuredWidth() < view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_exactSpecs_keepRequestedSize() {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity, null);
        view.setText("hi");

        view.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));

        assertEquals(200, view.getMeasuredWidth());
        assertEquals(100, view.getMeasuredHeight());
    }

    @Test
    public void onDraw_noLayout_returnsWithoutCrash() {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity, null);
        Canvas canvas = new Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));

        view.onDraw(canvas);
        // No layout yet -> early return, nothing drawn, no crash.
    }

    @Test
    public void onDraw_topDown_withLayout_drawsWithoutCrash() {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity, null);
        view.setText("hello");
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Canvas canvas = new Canvas(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888));

        view.onDraw(canvas);
    }

    @Test
    public void constructor_gravityBottom_usesBottomUpDrawPath() throws Exception {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(android.R.attr.gravity, "bottom")
                        .build());
        // Gravity.BOTTOM is converted to TOP in the constructor and the view
        // draws bottom-up; the gravity itself must have been rewritten.
        assertEquals(Gravity.TOP, view.getGravity() & Gravity.VERTICAL_GRAVITY_MASK);
        java.lang.reflect.Field topDownField =
                VerticalTextView.class.getDeclaredField("mTopDown");
        topDownField.setAccessible(true);
        assertFalse((boolean) topDownField.get(view));
        view.setText("hello");
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Canvas canvas = new Canvas(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888));
        view.onDraw(canvas);
    }

    @Test
    public void constructor_defaultGravity_isTopDown() throws Exception {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity, null);
        java.lang.reflect.Field topDownField =
                VerticalTextView.class.getDeclaredField("mTopDown");
        topDownField.setAccessible(true);
        assertTrue((boolean) topDownField.get(view));
        // Default gravity is not BOTTOM, so the view stays top-down; measuring
        // still swaps dimensions the same way.
        view.setText(longText());
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        assertTrue(view.getMeasuredWidth() < view.getMeasuredHeight());
    }

    @Test
    public void constructor_gravityCenterHorizontal_isTopDownAndGravityUnchanged() throws Exception {
        FragmentActivity activity = activity();
        VerticalTextView view = new VerticalTextView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(android.R.attr.gravity, "center_horizontal")
                        .build());
        java.lang.reflect.Field topDownField =
                VerticalTextView.class.getDeclaredField("mTopDown");
        topDownField.setAccessible(true);
        assertTrue((boolean) topDownField.get(view));
        // No vertical gravity component, so the constructor leaves gravity alone.
        assertEquals(Gravity.CENTER_HORIZONTAL,
                view.getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);
    }
}
