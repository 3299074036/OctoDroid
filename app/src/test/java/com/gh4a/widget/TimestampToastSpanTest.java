package com.gh4a.widget;

import android.content.Context;
import android.text.TextPaint;
import android.view.View;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.utils.StringUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowToast;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TimestampToastSpanTest {

    @Test
    public void updateDrawState_doesNotChangeUnderlineOrColor() {
        Date time = new Date(0L);
        TimestampToastSpan span = new TimestampToastSpan(time);
        TextPaint paint = new TextPaint();
        paint.setUnderlineText(true);
        int colorBefore = paint.getColor();
        span.updateDrawState(paint);
        assertTrue(paint.isUnderlineText());
        assertEquals(colorBefore, paint.getColor());
    }

    @Test
    public void onClick_showsToastWithExactTime() {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        Date time = new Date(1763000000000L);
        TimestampToastSpan span = new TimestampToastSpan(time);
        View widget = new View(activity);

        span.onClick(widget);

        String expected = StringUtils.formatExactTime(activity, time).toString();
        assertEquals(expected, ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onClick_epoch_showsToast() {
        Context context = ApplicationProvider.getApplicationContext();
        Date time = new Date(0L);
        TimestampToastSpan span = new TimestampToastSpan(time);
        View widget = new View(context);

        span.onClick(widget);

        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().getDuration());
    }
}
