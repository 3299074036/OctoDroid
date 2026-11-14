package com.gh4a.widget;

import android.content.Intent;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class LinkSpanTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void onClick_mailtoLink_startsViewIntent() {
        FragmentActivity activity = activity();
        LinkSpan span = new LinkSpan("mailto:someone@example.com");

        span.onClick(new View(activity));

        Intent started = Shadows.shadowOf(activity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_VIEW, started.getAction());
        assertEquals("mailto:someone@example.com", started.getDataString());
    }

    @Test
    public void onClick_fileScheme_doesNothing() {
        FragmentActivity activity = activity();
        LinkSpan span = new LinkSpan("file:///sdcard/x.html");

        span.onClick(new View(activity));

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_javascriptScheme_doesNothing() {
        FragmentActivity activity = activity();
        LinkSpan span = new LinkSpan("javascript:alert(1)");

        span.onClick(new View(activity));

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_intentScheme_doesNothing() {
        FragmentActivity activity = activity();
        LinkSpan span = new LinkSpan("intent://example.com#Intent;scheme=https;end");

        span.onClick(new View(activity));

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_githubHttpsLink_startsAnActivity() {
        FragmentActivity activity = activity();
        LinkSpan span = new LinkSpan("https://github.com/octocat/Hello-World");

        span.onClick(new View(activity));

        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_relativeUrl_doesNothing() {
        FragmentActivity activity = activity();
        LinkSpan span = new LinkSpan("/octocat/Hello-World");

        span.onClick(new View(activity));

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
}
