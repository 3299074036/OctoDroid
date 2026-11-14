package com.gh4a.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Label;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class LabelBadgeViewTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private Label label(String color) {
        return Label.builder().color(color).build();
    }

    private int badgeSize(FragmentActivity activity) {
        return activity.getResources()
                .getDimensionPixelSize(R.dimen.default_label_badge_size);
    }

    private int badgeSpacing(FragmentActivity activity) {
        return activity.getResources()
                .getDimensionPixelSize(R.dimen.default_label_badge_spacing);
    }

    @Test
    public void onMeasure_noLabels_singleBadgeSlot() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        assertEquals(badgeSize(activity), view.getMeasuredWidth());
        assertEquals(badgeSize(activity), view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_nullLabels_singleBadgeSlot() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(null);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        assertEquals(badgeSize(activity), view.getMeasuredWidth());
        assertEquals(badgeSize(activity), view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_emptyLabels_singleBadgeSlot() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(Collections.emptyList());
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        assertEquals(badgeSize(activity), view.getMeasuredWidth());
        assertEquals(badgeSize(activity), view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_threeLabelsWide_singleRow() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(Arrays.asList(label("ff0000"), label("00ff00"), label("0000ff")));
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        int expectedWidth = 3 * badgeSize(activity) + 2 * badgeSpacing(activity);
        assertEquals(expectedWidth, view.getMeasuredWidth());
        assertEquals(badgeSize(activity), view.getMeasuredHeight());
    }

    @Test
    public void onMeasure_threeLabelsNarrow_threeRows() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(Arrays.asList(label("ff0000"), label("00ff00"), label("0000ff")));
        int narrow = badgeSize(activity) + badgeSpacing(activity);
        view.measure(
                View.MeasureSpec.makeMeasureSpec(narrow, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        int expectedHeight = 3 * badgeSize(activity) + 2 * badgeSpacing(activity);
        assertEquals(expectedHeight, view.getMeasuredHeight());
        assertEquals(narrow, view.getMeasuredWidth());
    }

    @Test
    public void onMeasure_unspecifiedWidth_allBadgesInOneRow() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(Arrays.asList(label("ff0000"), label("00ff00")));
        view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        int expectedWidth = 2 * badgeSize(activity) + badgeSpacing(activity);
        assertEquals(expectedWidth, view.getMeasuredWidth());
        assertEquals(badgeSize(activity), view.getMeasuredHeight());
    }

    @Test
    public void dispatchDraw_noLabels_returnsWithoutCrash() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        Canvas canvas = new Canvas(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
        view.dispatchDraw(canvas);
    }

    @Test
    public void dispatchDraw_withLabels_drawsBadgesWithoutCrash() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(Arrays.asList(label("ff0000"), label("00ff00"), label("0000ff")));
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        Canvas canvas = new Canvas(Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888));

        view.dispatchDraw(canvas);
    }

    @Test
    public void constructors_threeArg_creates() {
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null, 0);
        assertNotNull(view);
    }

    @Test
    public void onMeasure_unknownWidthMode_fallsBackToUnlimitedWidth() {
        // MeasureSpec mode is a 2-bit field; mode 3 matches no case in the
        // onMeasure switch, so maxWidth stays unlimited and all badges fit
        // in a single row.
        FragmentActivity activity = activity();
        LabelBadgeView view = new LabelBadgeView(activity, null);
        view.setLabels(Arrays.asList(label("ff0000"), label("00ff00")));
        view.measure(
                View.MeasureSpec.makeMeasureSpec(0, 0xC0000000),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        int expectedWidth = 2 * badgeSize(activity) + badgeSpacing(activity);
        assertEquals(expectedWidth, view.getMeasuredWidth());
        assertEquals(badgeSize(activity), view.getMeasuredHeight());
    }
}
