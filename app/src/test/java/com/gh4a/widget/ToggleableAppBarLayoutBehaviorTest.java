package com.gh4a.widget;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.view.ViewConfiguration;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.appbar.AppBarLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ToggleableAppBarLayoutBehaviorTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private ToggleableAppBarLayoutBehavior behavior() {
        return new ToggleableAppBarLayoutBehavior();
    }

    private CoordinatorLayout parent(FragmentActivity activity) {
        return new CoordinatorLayout(activity);
    }

    private AppBarLayout child(FragmentActivity activity) {
        return new AppBarLayout(activity);
    }

    // AppBarLayout.Behavior.onStartNestedScroll (material 1.13.0) only
    // starts when the AppBarLayout has scrollable children and the
    // scrolling target leaves room for it:
    // parent.getHeight() - directTargetChild.getHeight() <= child.getHeight().
    // A bare AppBarLayout therefore never starts nested scrolling.
    private Object[] scrollableSetup(FragmentActivity activity) {
        CoordinatorLayout parent = new CoordinatorLayout(activity);
        AppBarLayout appBar = new AppBarLayout(activity);
        View toolbar = new View(activity);
        AppBarLayout.LayoutParams lp =
                new AppBarLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 200);
        lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
        toolbar.setLayoutParams(lp);
        appBar.addView(toolbar);
        View target = new View(activity);
        parent.addView(appBar);
        parent.addView(target);
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1000, 1000);
        appBar.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY));
        appBar.layout(0, 0, 1000, 200);
        target.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        target.layout(0, 0, 1000, 1000);
        return new Object[]{parent, appBar, target};
    }

    @Test
    public void onStartNestedScroll_enabled_vertical_returnsTrue() {
        FragmentActivity activity = activity();
        Object[] setup = scrollableSetup(activity);
        CoordinatorLayout parent = (CoordinatorLayout) setup[0];
        AppBarLayout appBar = (AppBarLayout) setup[1];
        View target = (View) setup[2];
        assertTrue(behavior().onStartNestedScroll(parent, appBar, target, target,
                androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,
                androidx.core.view.ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_enabled_horizontal_returnsFalse() {
        FragmentActivity activity = activity();
        assertFalse(behavior().onStartNestedScroll(parent(activity), child(activity),
                new View(activity), new View(activity),
                androidx.core.view.ViewCompat.SCROLL_AXIS_HORIZONTAL,
                androidx.core.view.ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onStartNestedScroll_disabled_vertical_returnsFalse() {
        FragmentActivity activity = activity();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        behavior.setEnabled(false);
        assertFalse(behavior.onStartNestedScroll(parent(activity), child(activity),
                new View(activity), new View(activity),
                androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,
                androidx.core.view.ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void onInterceptTouchEvent_disabled_returnsFalse() {
        FragmentActivity activity = activity();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        behavior.setEnabled(false);
        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        assertFalse(behavior.onInterceptTouchEvent(parent(activity), child(activity), ev));
    }

    @Test
    public void onInterceptTouchEvent_enabled_delegatesWithoutCrash() {
        FragmentActivity activity = activity();
        MotionEvent ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        // AppBarLayout.Behavior has no drag in progress, so no interception.
        assertFalse(behavior().onInterceptTouchEvent(parent(activity), child(activity), ev));
    }

    @Test
    public void onNestedPreScroll_disabled_doesNotConsume() {
        FragmentActivity activity = activity();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        behavior.setEnabled(false);
        int[] consumed = {0, 0};
        behavior.onNestedPreScroll(parent(activity), child(activity), new View(activity),
                0, 100, consumed, androidx.core.view.ViewCompat.TYPE_TOUCH);
        assertEquals(0, consumed[0]);
        assertEquals(0, consumed[1]);
    }

    @Test
    public void onNestedScroll_disabled_doesNotThrow() {
        FragmentActivity activity = activity();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        behavior.setEnabled(false);
        int[] consumed = {0, 0};
        behavior.onNestedScroll(parent(activity), child(activity), new View(activity),
                0, 100, 0, 0, androidx.core.view.ViewCompat.TYPE_TOUCH, consumed);
    }

    @Test
    public void onStopNestedScroll_disabled_doesNotThrow() {
        FragmentActivity activity = activity();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        behavior.setEnabled(false);
        behavior.onStopNestedScroll(parent(activity), child(activity), new View(activity),
                androidx.core.view.ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void setEnabled_reEnablesAfterDisable() {
        FragmentActivity activity = activity();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        Object[] setup = scrollableSetup(activity);
        CoordinatorLayout parent = (CoordinatorLayout) setup[0];
        AppBarLayout appBar = (AppBarLayout) setup[1];
        View target = (View) setup[2];
        behavior.setEnabled(false);
        assertFalse(behavior.onStartNestedScroll(parent, appBar, target, target,
                androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,
                androidx.core.view.ViewCompat.TYPE_TOUCH));
        behavior.setEnabled(true);
        assertTrue(behavior.onStartNestedScroll(parent, appBar, target, target,
                androidx.core.view.ViewCompat.SCROLL_AXIS_VERTICAL,
                androidx.core.view.ViewCompat.TYPE_TOUCH));
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }

    @Test
    public void onNestedPreScroll_enabled_consumesScroll() {
        FragmentActivity activity = activity();
        Object[] setup = scrollableSetup(activity);
        CoordinatorLayout parent = (CoordinatorLayout) setup[0];
        AppBarLayout appBar = (AppBarLayout) setup[1];
        View target = (View) setup[2];
        int[] consumed = {0, 0};
        behavior().onNestedPreScroll(parent, appBar, target, 0, 100, consumed,
                androidx.core.view.ViewCompat.TYPE_TOUCH);
        assertTrue(consumed[1] > 0);
    }

    @Test
    public void onNestedScroll_enabled_delegatesWithoutCrash() {
        FragmentActivity activity = activity();
        Object[] setup = scrollableSetup(activity);
        CoordinatorLayout parent = (CoordinatorLayout) setup[0];
        AppBarLayout appBar = (AppBarLayout) setup[1];
        View target = (View) setup[2];
        int[] consumed = {0, 0};
        behavior().onNestedScroll(parent, appBar, target, 0, 100, 0, 0,
                androidx.core.view.ViewCompat.TYPE_TOUCH, consumed);
    }

    @Test
    public void onStopNestedScroll_enabled_delegatesWithoutCrash() {
        FragmentActivity activity = activity();
        Object[] setup = scrollableSetup(activity);
        CoordinatorLayout parent = (CoordinatorLayout) setup[0];
        AppBarLayout appBar = (AppBarLayout) setup[1];
        View target = (View) setup[2];
        behavior().onStopNestedScroll(parent, appBar, target,
                androidx.core.view.ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onInterceptTouchEvent_enabled_dragInProgress_returnsTrue() {
        FragmentActivity activity = activity();
        Object[] setup = scrollableSetup(activity);
        CoordinatorLayout parent = (CoordinatorLayout) setup[0];
        AppBarLayout appBar = (AppBarLayout) setup[1];
        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();
        ToggleableAppBarLayoutBehavior behavior = behavior();
        // DOWN inside the app bar, then a MOVE past the touch slop: the
        // behavior starts dragging and intercepts the touch.
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 500, 100, 0);
        assertFalse(behavior.onInterceptTouchEvent(parent, appBar, down));
        MotionEvent move = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE,
                500, 100 + slop + 10, 0);
        assertTrue(behavior.onInterceptTouchEvent(parent, appBar, move));
    }
}
