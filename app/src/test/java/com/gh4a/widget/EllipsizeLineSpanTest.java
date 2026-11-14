package com.gh4a.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class EllipsizeLineSpanTest {

    private Canvas canvasOf(int width, int height) {
        return new Canvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888));
    }

    @Test
    public void getSize_noClipBounds_returnsZero() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);

        int size = span.getSize(paint, "hello", 0, 5, null);

        assertEquals(0, size);
    }

    @Test
    public void getSize_shortText_returnsTextWidth() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);
        Canvas canvas = canvasOf(500, 50);
        span.drawBackground(canvas, paint, 0, 500, 0, 0, 50, "hello", 0, 5, 0);

        int size = span.getSize(paint, "hello", 0, 5, null);

        int textWidth = (int) Math.ceil(paint.measureText("hello", 0, 5));
        assertEquals(Math.min(textWidth, 500), size);
        assertTrue(size > 0);
    }

    // Robolectric's Paint.measureText returns ~1px per character (no real
    // font), so a "long" text must be far longer than the clip width for the
    // clamp to be observable. Build it programmatically.
    private String longText(int repeats) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < repeats; i++) {
            sb.append("hello world ");
        }
        return sb.toString();
    }

    @Test
    public void getSize_longText_clampedToClipWidth() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);
        Canvas canvas = canvasOf(40, 50);
        span.drawBackground(canvas, paint, 0, 40, 0, 0, 50, "hello", 0, 5, 0);

        String text = longText(20); // 240 chars, wider than the 40px clip
        int size = span.getSize(paint, text, 0, text.length(), null);

        assertEquals(40, size);
    }

    @Test
    public void getSize_withFontMetrics_populatesMetrics() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);
        Canvas canvas = canvasOf(500, 50);
        span.drawBackground(canvas, paint, 0, 500, 0, 0, 50, "hello", 0, 5, 0);
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();

        span.getSize(paint, "hello", 0, 5, fm);

        assertTrue(fm.ascent < 0);
        assertTrue(fm.descent > 0);
    }

    @Test
    public void draw_textFits_drawsWithoutCrash() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);
        Canvas canvas = canvasOf(500, 50);
        span.drawBackground(canvas, paint, 0, 500, 0, 0, 50, "hi", 0, 2, 0);

        span.draw(canvas, "hi", 0, 2, 0, 0, 20, 50, paint);
    }

    @Test
    public void draw_textTooWide_drawsEllipsisWithoutCrash() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);
        Canvas canvas = canvasOf(30, 50);
        span.drawBackground(canvas, paint, 0, 30, 0, 0, 50, "hello", 0, 5, 0);

        // Robolectric's Paint measures ~1px per character, so the 23-char text
        // fits the 30px clip; use the 240-char text so the ellipsis branch runs.
        String text = longText(20);
        span.draw(canvas, text, 0, text.length(), 0, 0, 20, 50, paint);
    }

    @Test
    public void drawBackground_recordsClipBounds() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(4);
        Paint paint = new Paint();
        paint.setTextSize(20);
        Canvas canvas = canvasOf(123, 50);
        span.drawBackground(canvas, paint, 0, 123, 0, 0, 50, "hello", 0, 5, 0);

        // getSize is clamped by the recorded clip width (123).
        String longText = longText(20); // 240 chars, wider than the 123px clip
        assertEquals(123, span.getSize(paint, longText, 0, longText.length(), null));
    }

    @Test
    public void chooseHeight_addsBottomMarginToDescentAndBottom() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(7);
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        fm.descent = 5;
        fm.bottom = 6;

        span.chooseHeight("x", 0, 1, 0, 0, fm);

        assertEquals(12, fm.descent);
        assertEquals(13, fm.bottom);
    }

    @Test
    public void chooseHeight_zeroMargin_leavesMetricsUnchanged() {
        EllipsizeLineSpan span = new EllipsizeLineSpan(0);
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        fm.descent = 5;
        fm.bottom = 6;

        span.chooseHeight("x", 0, 1, 0, 0, fm);

        assertEquals(5, fm.descent);
        assertEquals(6, fm.bottom);
    }
}
