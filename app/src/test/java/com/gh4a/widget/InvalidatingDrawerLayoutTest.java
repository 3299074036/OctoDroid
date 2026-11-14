package com.gh4a.widget;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class InvalidatingDrawerLayoutTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        InvalidatingDrawerLayout a = new InvalidatingDrawerLayout(activity);
        InvalidatingDrawerLayout b = new InvalidatingDrawerLayout(activity, null);
        InvalidatingDrawerLayout c = new InvalidatingDrawerLayout(activity, null, 0);
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(c);
    }

    @Test
    public void verifyDrawable_noBackgroundSet_returnsFalseForForeignDrawable() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        assertFalse(layout.verifyDrawable(new ColorDrawable(Color.RED)));
    }

    @Test
    public void setStatusBarBackground_drawable_verifyDrawableReturnsTrue() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        Drawable bg = new ColorDrawable(Color.BLUE);

        layout.setStatusBarBackground(bg);

        assertTrue(layout.verifyDrawable(bg));
        assertEquals(bg, layout.getStatusBarBackgroundDrawable());
    }

    @Test
    public void setStatusBarBackground_drawable_setsCallbackOnDrawable() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        Drawable bg = mock(Drawable.class);

        layout.setStatusBarBackground(bg);

        verify(bg, times(1)).setCallback(layout);
    }

    @Test
    public void setStatusBarBackground_newDrawable_clearsOldCallback() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        Drawable first = mock(Drawable.class);
        Drawable second = mock(Drawable.class);

        layout.setStatusBarBackground(first);
        layout.setStatusBarBackground(second);

        verify(first, times(1)).setCallback(null);
        verify(second, times(1)).setCallback(layout);
        assertTrue(layout.verifyDrawable(second));
        assertFalse(layout.verifyDrawable(first));
    }

    @Test
    public void setStatusBarBackground_sameDrawableTwice_doesNotRebindCallback() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        Drawable bg = mock(Drawable.class);

        layout.setStatusBarBackground(bg);
        layout.setStatusBarBackground(bg);

        verify(bg, times(1)).setCallback(layout);
        verify(bg, Mockito.never()).setCallback(null);
    }

    @Test
    public void setStatusBarBackground_null_clearsOldCallback() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        Drawable bg = mock(Drawable.class);
        layout.setStatusBarBackground(bg);

        layout.setStatusBarBackground((Drawable) null);

        verify(bg, times(1)).setCallback(null);
        assertFalse(layout.verifyDrawable(bg));
    }

    @Test
    public void setStatusBarBackground_resourceId_verifyDrawableReturnsTrue() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());

        layout.setStatusBarBackground(R.drawable.pull_request_merge_ok);

        assertNotNull(layout.getStatusBarBackgroundDrawable());
        assertTrue(layout.verifyDrawable(layout.getStatusBarBackgroundDrawable()));
    }

    @Test
    public void setStatusBarBackgroundColor_verifyDrawableReturnsTrue() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());

        layout.setStatusBarBackgroundColor(Color.GREEN);

        assertNotNull(layout.getStatusBarBackgroundDrawable());
        assertTrue(layout.verifyDrawable(layout.getStatusBarBackgroundDrawable()));
    }

    @Test
    public void verifyDrawable_viewBackground_returnsTrueViaSuper() {
        InvalidatingDrawerLayout layout = new InvalidatingDrawerLayout(activity());
        Drawable bg = new ColorDrawable(Color.GREEN);
        layout.setBackground(bg);
        // The background itself verifies through the superclass branch,
        // with no status-bar background set.
        assertTrue(layout.verifyDrawable(bg));
    }
}
