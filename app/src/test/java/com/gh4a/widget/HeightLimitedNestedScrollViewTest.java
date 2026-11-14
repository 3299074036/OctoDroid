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
public class HeightLimitedNestedScrollViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private int spec(int size, int mode) {
        return View.MeasureSpec.makeMeasureSpec(size, mode);
    }

    private int dp100(FragmentActivity activity) {
        return (int) (100 * activity.getResources().getDisplayMetrics().density);
    }

    @Test
    public void onMeasure_noMaxHeight_usesGivenHeightSpec() {
        HeightLimitedNestedScrollView view = new HeightLimitedNestedScrollView(activity());
        view.measure(spec(300, View.MeasureSpec.EXACTLY),
                spec(1000, View.MeasureSpec.EXACTLY));
        assertEquals(1000, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_maxHeightAttr_capsExactlyHeight() {
        FragmentActivity activity = activity();
        HeightLimitedNestedScrollView view = new HeightLimitedNestedScrollView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        view.measure(spec(300, View.MeasureSpec.EXACTLY),
                spec(1000, View.MeasureSpec.EXACTLY));
        assertEquals(dp100(activity), view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_maxHeightAttr_smallerSpec_keepsSmallerSpec() {
        FragmentActivity activity = activity();
        HeightLimitedNestedScrollView view = new HeightLimitedNestedScrollView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        view.measure(spec(300, View.MeasureSpec.EXACTLY),
                spec(50, View.MeasureSpec.EXACTLY));
        assertEquals(50, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_maxHeightAttr_unspecifiedHeight_capsAtMax() {
        FragmentActivity activity = activity();
        HeightLimitedNestedScrollView view = new HeightLimitedNestedScrollView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        // NestedScrollView measures its child with an UNSPECIFIED height
        // spec, so the child's layout height is ignored: only an intrinsic
        // height (minimum height) makes it tall. An empty layout measures 0
        // under AT_MOST/UNSPECIFIED, so the cap is only observable with a
        // child taller than the max height.
        View child = new View(activity);
        child.setLayoutParams(new android.view.ViewGroup.LayoutParams(300, 500));
        child.setMinimumHeight(500);
        view.addView(child);
        view.measure(spec(300, View.MeasureSpec.EXACTLY),
                spec(0, View.MeasureSpec.UNSPECIFIED));
        assertEquals(dp100(activity), view.getMeasuredHeight());
    }

    @Test
    public void constructors_threeArg_noAttrs_usesGivenHeightSpec() {
        HeightLimitedNestedScrollView view = new HeightLimitedNestedScrollView(activity(), null, 0);
        view.measure(spec(300, View.MeasureSpec.EXACTLY),
                spec(777, View.MeasureSpec.EXACTLY));
        assertEquals(777, view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_atMostHeight_cappedByMaxHeight() {
        FragmentActivity activity = activity();
        HeightLimitedNestedScrollView view = new HeightLimitedNestedScrollView(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.maxHeight, "100dp")
                        .build());
        // NestedScrollView measures its child with an UNSPECIFIED height
        // spec, so the child's layout height is ignored: only an intrinsic
        // height (minimum height) makes it tall. An empty scroll view
        // measures 0 under AT_MOST, so the cap is only observable with a
        // child taller than the max height.
        View child = new View(activity);
        child.setLayoutParams(new android.view.ViewGroup.LayoutParams(300, 500));
        child.setMinimumHeight(500);
        view.addView(child);
        view.measure(spec(300, View.MeasureSpec.EXACTLY),
                spec(1000, View.MeasureSpec.AT_MOST));
        assertEquals(dp100(activity), view.getMeasuredHeight());
    }
}
