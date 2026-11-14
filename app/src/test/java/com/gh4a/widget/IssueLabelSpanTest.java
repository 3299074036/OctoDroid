package com.gh4a.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.meisolsson.githubsdk.model.Label;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class IssueLabelSpanTest {

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Test
    public void getSize_nullFontMetrics_returnsTextPlusPaddingAndMargin() {
        Context context = context();
        IssueLabelSpan span = new IssueLabelSpan(context,
                Label.builder().color("ff0000").build(), true);
        Paint paint = new Paint();
        paint.setTextSize(20);
        int padding = context.getResources().getDimensionPixelSize(R.dimen.issue_label_padding);
        int margin = context.getResources().getDimensionPixelSize(R.dimen.issue_label_margin);

        int size = span.getSize(paint, "bug", 0, 3, null);

        paint.setTextSize(context.getResources().getDimension(R.dimen.issue_label_text_size));
        int expected = (int) Math.ceil(paint.measureText("bug", 0, 3)) + 2 * padding + margin;
        assertEquals(expected, size);
    }

    @Test
    public void getSize_noMargin_omitsMargin() {
        Context context = context();
        IssueLabelSpan span = new IssueLabelSpan(context,
                Label.builder().color("ff0000").build(), false);
        Paint paint = new Paint();
        int padding = context.getResources().getDimensionPixelSize(R.dimen.issue_label_padding);

        int size = span.getSize(paint, "bug", 0, 3, null);

        paint.setTextSize(context.getResources().getDimension(R.dimen.issue_label_text_size));
        int expected = (int) Math.ceil(paint.measureText("bug", 0, 3)) + 2 * padding;
        assertEquals(expected, size);
    }

    @Test
    public void getSize_withFontMetrics_expandsMetricsByPaddingAndMargin() {
        Context context = context();
        IssueLabelSpan span = new IssueLabelSpan(context,
                Label.builder().color("00ff00").build(), true);
        Paint paint = new Paint();
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        int padding = context.getResources().getDimensionPixelSize(R.dimen.issue_label_padding);
        int margin = context.getResources().getDimensionPixelSize(R.dimen.issue_label_margin);

        span.getSize(paint, "bug", 0, 3, fm);

        // fm was filled by getFontMetricsInt then shifted: top/ascent -= padding,
        // bottom/descent += padding + margin.
        Paint.FontMetricsInt plain = new Paint.FontMetricsInt();
        paint.setTextSize(context.getResources().getDimension(R.dimen.issue_label_text_size));
        paint.getFontMetricsInt(plain);
        assertEquals(plain.top - padding, fm.top);
        assertEquals(plain.ascent - padding, fm.ascent);
        assertEquals(plain.bottom + padding + margin, fm.bottom);
        assertEquals(plain.descent + padding + margin, fm.descent);
    }

    @Test
    public void getSize_nullLabelColor_usesDefaultGray() {
        Context context = context();
        IssueLabelSpan span = new IssueLabelSpan(context,
                Label.builder().color(null).build(), false);
        Paint paint = new Paint();

        int size = span.getSize(paint, "x", 0, 1, null);

        assertTrue(size > 0);
    }

    @Test
    public void draw_afterGetSize_drawsBadgeWithoutCrash() {
        Context context = context();
        IssueLabelSpan span = new IssueLabelSpan(context,
                Label.builder().color("0000ff").build(), true);
        Paint paint = new Paint();
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        span.getSize(paint, "bug", 0, 3, fm);
        Canvas canvas = new Canvas(Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888));

        span.draw(canvas, "bug", 0, 3, 10, 0, 40, 60, paint);
    }

    @Test
    public void draw_withoutPriorGetSize_drawsWithZeroAscentDescent() {
        Context context = context();
        IssueLabelSpan span = new IssueLabelSpan(context,
                Label.builder().color("0000ff").build(), false);
        Paint paint = new Paint();
        Canvas canvas = new Canvas(Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888));

        span.draw(canvas, "bug", 0, 3, 10, 0, 40, 60, paint);
    }
}
