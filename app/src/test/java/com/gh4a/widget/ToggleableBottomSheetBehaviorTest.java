package com.gh4a.widget;

import android.view.MotionEvent;
import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ToggleableBottomSheetBehaviorTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ToggleableBottomSheetBehavior<View> behavior() {
        return new ToggleableBottomSheetBehavior<>();
    }

    private CoordinatorLayout parent(FragmentActivity activity) {
        return new CoordinatorLayout(activity);
    }

    @Test
    public void defaultState_enabled() {
        FragmentActivity activity = activity();
        // Enabled by default: onStartNestedScroll delegates to BottomSheetBehavior,
        // which accepts vertical nested scroll.
        assertTrue(behavior().onStartNestedScroll(parent(activity), new View(activity),
                new View(activity), new View(activity),
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_disabled_returnsFalse() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        assertFalse(behavior.onStartNestedScroll(parent(activity), new View(activity),
                new View(activity), new View(activity),
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_enabled_horizontal_returnsFalse() {
        FragmentActivity activity = activity();
        assertFalse(behavior().onStartNestedScroll(parent(activity), new View(activity),
                new View(activity), new View(activity),
                ViewCompat.SCROLL_AXIS_HORIZONTAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onInterceptTouchEvent_disabled_returnsFalse() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        assertFalse(behavior.onInterceptTouchEvent(parent(activity), new View(activity), ev));
    }

    @Test
    public void onTouchEvent_disabled_returnsFalse() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        assertFalse(behavior.onTouchEvent(parent(activity), new View(activity), ev));
    }

    @Test
    public void onNestedPreFling_disabled_returnsFalse() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        assertFalse(behavior.onNestedPreFling(parent(activity), new View(activity),
                new View(activity), 1000f, 1000f));
    }

    @Test
    public void onNestedPreScroll_disabled_doesNotConsume() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        int[] consumed = {0, 0};
        behavior.onNestedPreScroll(parent(activity), new View(activity), new View(activity),
                0, 100, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[0]);
        assertEquals(0, consumed[1]);
    }

    @Test
    public void onStopNestedScroll_disabled_doesNotThrow() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        behavior.onStopNestedScroll(parent(activity), new View(activity), new View(activity),
                ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void constructors_contextAttrs_createsEnabled() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior =
                new ToggleableBottomSheetBehavior<>(activity, null);
        assertTrue(behavior.onStartNestedScroll(parent(activity), new View(activity),
                new View(activity), new View(activity),
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void setEnabled_reEnablesAfterDisable() {
        FragmentActivity activity = activity();
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        behavior.setEnabled(false);
        behavior.setEnabled(true);
        assertTrue(behavior.onStartNestedScroll(parent(activity), new View(activity),
                new View(activity), new View(activity),
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }

    @Test
    public void onInterceptTouchEvent_enabled_delegatesToSuper() {
        FragmentActivity activity = activity();
        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        // No drag in progress, so BottomSheetBehavior does not intercept.
        assertFalse(behavior().onInterceptTouchEvent(parent(activity), new View(activity), ev));
    }

    @Test
    public void onTouchEvent_enabled_delegatesToSuper() {
        FragmentActivity activity = activity();
        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        assertFalse(behavior().onTouchEvent(parent(activity), new View(activity), ev));
    }

    @Test
    public void onNestedPreScroll_enabled_delegatesToSuper() {
        FragmentActivity activity = activity();
        int[] consumed = {0, 0};
        behavior().onNestedPreScroll(parent(activity), new View(activity), new View(activity),
                0, 100, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[0]);
        assertEquals(0, consumed[1]);
    }

    @Test
    public void onStopNestedScroll_enabled_delegatesToSuper() {
        FragmentActivity activity = activity();
        behavior().onStopNestedScroll(parent(activity), new View(activity), new View(activity),
                ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onNestedPreFling_enabled_delegatesToSuper() {
        FragmentActivity activity = activity();
        assertFalse(behavior().onNestedPreFling(parent(activity), new View(activity),
                new View(activity), 1000f, 1000f));
    }
}
