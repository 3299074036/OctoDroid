package com.gh4a.widget;

import android.content.Context;
import android.content.Intent;
import android.text.TextPaint;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.utils.UiUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class IntentSpanTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void onClick_nullIntent_doesNotStartActivity() {
        FragmentActivity activity = activity();
        IntentSpan span = new IntentSpan(activity, context -> null);
        View widget = new View(activity);

        span.onClick(widget);

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_nonNullIntent_startsActivity() {
        FragmentActivity activity = activity();
        Intent expected = new Intent(Intent.ACTION_VIEW);
        IntentSpan span = new IntentSpan(activity, context -> expected);
        View widget = new View(activity);

        span.onClick(widget);

        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
    }

    @Test
    public void onClick_callbackReceivesSpanContext() {
        FragmentActivity activity = activity();
        AtomicReference<Context> seen = new AtomicReference<>();
        IntentSpan span = new IntentSpan(activity, context -> {
            seen.set(context);
            return null;
        });

        span.onClick(new View(activity));

        assertEquals(activity, seen.get());
    }

    @Test
    public void updateDrawState_removesUnderlineAndSetsLinkColor() {
        Context context = ApplicationProvider.getApplicationContext();
        IntentSpan span = new IntentSpan(context, c -> null);
        TextPaint paint = new TextPaint();
        paint.setUnderlineText(true);

        span.updateDrawState(paint);

        assertFalse(paint.isUnderlineText());
        assertEquals(UiUtils.resolveColor(context, android.R.attr.textColorLink), paint.getColor());
    }

    @Test
    public void onClick_callbackInvokedOnce() {
        FragmentActivity activity = activity();
        AtomicBoolean called = new AtomicBoolean(false);
        IntentSpan span = new IntentSpan(activity, context -> {
            called.set(true);
            return null;
        });

        span.onClick(new View(activity));

        assertTrue(called.get());
    }
}
