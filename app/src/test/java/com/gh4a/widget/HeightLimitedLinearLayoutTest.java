package com.gh4a.widget;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class HeightLimitedLinearLayoutTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private int widthSpec(int size, int mode) {
        return View.MeasureSpec.makeMeasureSpec(size, mode);
    }

    @Test
    public void onMeasure_noMaxHeight_usesGivenHeightSpec() {
        HeightLimitedLinearLayout view = new HeightLimitedLinearLayout(activity());
        view.measure(widthSpec(300, View.MeasureSpec.EXACTLY),
                widthSpec(1000, View.MeasureSpec.EXACTLY));
        assertEquals(1000, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_maxHeightAttr_capsExactlyHeight() {
        FragmentActivity activity = activity();
        HeightLimitedLinearLayout view = new HeightLimitedLinearLayout(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        view.measure(widthSpec(300, View.MeasureSpec.EXACTLY),
                widthSpec(1000, View.MeasureSpec.EXACTLY));
        // 100dp in px at the test density
        int expected = (int) (100 * activity.getResources().getDisplayMetrics().density);
        assertEquals(expected, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_maxHeightAttr_smallerSpec_keepsSmallerSpec() {
        FragmentActivity activity = activity();
        HeightLimitedLinearLayout view = new HeightLimitedLinearLayout(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        view.measure(widthSpec(300, View.MeasureSpec.EXACTLY),
                widthSpec(50, View.MeasureSpec.EXACTLY));
        assertEquals(50, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_maxHeightAttr_unspecifiedHeight_capsAtMax() {
        FragmentActivity activity = activity();
        HeightLimitedLinearLayout view = new HeightLimitedLinearLayout(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        // An empty layout measures 0 under AT_MOST/UNSPECIFIED, so the cap
        // is only observable with a child taller than the max height.
        View child = new View(activity);
        child.setLayoutParams(new android.widget.LinearLayout.LayoutParams(300, 500));
        view.addView(child);
        view.measure(widthSpec(300, View.MeasureSpec.EXACTLY),
                widthSpec(0, View.MeasureSpec.UNSPECIFIED));
        int expected = (int) (100 * activity.getResources().getDisplayMetrics().density);
        assertEquals(expected, view.getMeasuredHeight());
    }

    @Test
    public void constructors_threeArg_noAttrs_usesGivenHeightSpec() {
        HeightLimitedLinearLayout view = new HeightLimitedLinearLayout(activity(), null, 0);
        view.measure(widthSpec(300, View.MeasureSpec.EXACTLY),
                widthSpec(777, View.MeasureSpec.EXACTLY));
        assertEquals(777, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_atMostHeight_cappedByMaxHeight() {
        FragmentActivity activity = activity();
        HeightLimitedLinearLayout view = new HeightLimitedLinearLayout(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        // An empty layout measures 0 under AT_MOST, so the cap is only
        // observable with a child taller than the max height.
        View child = new View(activity);
        child.setLayoutParams(new android.widget.LinearLayout.LayoutParams(300, 500));
        view.addView(child);
        view.measure(widthSpec(300, View.MeasureSpec.EXACTLY),
                widthSpec(1000, View.MeasureSpec.AT_MOST));
        int expected = (int) (100 * activity.getResources().getDisplayMetrics().density);
        assertEquals(expected, view.getMeasuredHeight());
    }
}
