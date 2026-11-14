package com.gh4a.widget;

import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
public class ScrollAwareFloatingActionButtonBehaviorTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ScrollAwareFloatingActionButtonBehavior behavior(FragmentActivity activity) {
        return new ScrollAwareFloatingActionButtonBehavior(activity, null);
    }

    @Test
    public void onStartNestedScroll_tagSet_vertical_returnsTrue() {
        FragmentActivity activity = activity();
        View target = new View(activity);
        target.setTag(R.id.FloatingActionButtonScrollEnabled, Boolean.TRUE);

        assertTrue(behavior(activity).onStartNestedScroll(new CoordinatorLayout(activity),
                new FloatingActionButton(activity), new View(activity), target,
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_tagSet_horizontal_returnsFalse() {
        FragmentActivity activity = activity();
        View target = new View(activity);
        target.setTag(R.id.FloatingActionButtonScrollEnabled, Boolean.TRUE);

        assertFalse(behavior(activity).onStartNestedScroll(new CoordinatorLayout(activity),
                new FloatingActionButton(activity), new View(activity), target,
                ViewCompat.SCROLL_AXIS_HORIZONTAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_tagNull_vertical_returnsFalse() {
        FragmentActivity activity = activity();
        View target = new View(activity);

        // Without the tag the behavior delegates to FloatingActionButton's
        // Behavior, which in material 1.13.0 has no onStartNestedScroll
        // override at all, so CoordinatorLayout.Behavior returns false:
        // the FAB is not scroll-aware for this target.
        assertFalse(behavior(activity).onStartNestedScroll(new CoordinatorLayout(activity),
                new FloatingActionButton(activity), new View(activity), target,
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_tagNull_horizontal_returnsFalse() {
        FragmentActivity activity = activity();
        View target = new View(activity);

        assertFalse(behavior(activity).onStartNestedScroll(new CoordinatorLayout(activity),
                new FloatingActionButton(activity), new View(activity), target,
                ViewCompat.SCROLL_AXIS_HORIZONTAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onNestedScroll_scrollingDown_visibleFab_hidesFab() {
        FragmentActivity activity = activity();
        FloatingActionButton fab = spy(new FloatingActionButton(activity));
        fab.setVisibility(View.VISIBLE);

        behavior(activity).onNestedScroll(new CoordinatorLayout(activity), fab,
                new View(activity), 0, 10, 0, 0, ViewCompat.TYPE_TOUCH, new int[2]);

        verify(fab, times(1)).hide(any(FloatingActionButton.OnVisibilityChangedListener.class));
    }

    @Test
    public void onNestedScroll_scrollingDown_hiddenFab_doesNotHideAgain() {
        FragmentActivity activity = activity();
        FloatingActionButton fab = spy(new FloatingActionButton(activity));
        fab.setVisibility(View.INVISIBLE);

        behavior(activity).onNestedScroll(new CoordinatorLayout(activity), fab,
                new View(activity), 0, 10, 0, 0, ViewCompat.TYPE_TOUCH, new int[2]);

        verify(fab, Mockito.never()).hide(any(FloatingActionButton.OnVisibilityChangedListener.class));
    }

    @Test
    public void onNestedScroll_scrollingUp_hiddenFab_showsFab() {
        FragmentActivity activity = activity();
        FloatingActionButton fab = spy(new FloatingActionButton(activity));
        fab.setVisibility(View.INVISIBLE);

        behavior(activity).onNestedScroll(new CoordinatorLayout(activity), fab,
                new View(activity), 0, -10, 0, 0, ViewCompat.TYPE_TOUCH, new int[2]);

        verify(fab, times(1)).show();
    }

    @Test
    public void onNestedScroll_scrollingUp_visibleFab_doesNotShowAgain() {
        FragmentActivity activity = activity();
        FloatingActionButton fab = spy(new FloatingActionButton(activity));
        fab.setVisibility(View.VISIBLE);

        behavior(activity).onNestedScroll(new CoordinatorLayout(activity), fab,
                new View(activity), 0, -10, 0, 0, ViewCompat.TYPE_TOUCH, new int[2]);

        verify(fab, Mockito.never()).show();
    }

    @Test
    public void onNestedScroll_noVerticalScroll_doesNothing() {
        FragmentActivity activity = activity();
        FloatingActionButton fab = spy(new FloatingActionButton(activity));
        fab.setVisibility(View.VISIBLE);

        behavior(activity).onNestedScroll(new CoordinatorLayout(activity), fab,
                new View(activity), 5, 0, 0, 0, ViewCompat.TYPE_TOUCH, new int[2]);

        verify(fab, Mockito.never()).hide(any(FloatingActionButton.OnVisibilityChangedListener.class));
        verify(fab, Mockito.never()).show();
    }

    @Test
    public void visibilityChangedListener_onHidden_setsInvisible() throws Exception {
        FragmentActivity activity = activity();
        FloatingActionButton fab = new FloatingActionButton(activity);
        fab.setVisibility(View.VISIBLE);
        Field field = ScrollAwareFloatingActionButtonBehavior.class
                .getDeclaredField("mVisibilityChangedListener");
        field.setAccessible(true);
        FloatingActionButton.OnVisibilityChangedListener listener =
                (FloatingActionButton.OnVisibilityChangedListener) field.get(behavior(activity));

        listener.onHidden(fab);

        assertEquals(View.INVISIBLE, fab.getVisibility());
    }
}
