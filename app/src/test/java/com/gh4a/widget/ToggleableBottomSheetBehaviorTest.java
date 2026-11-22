package com.gh4a.widget;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

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

    // A sheet laid out in a CoordinatorLayout, with a small nested-scrolling
    // child at its top-left corner. Laying it out lets BottomSheetBehavior
    // find the nested child and create its drag helper.
    private static class SheetLayout {
        final CoordinatorLayout parent;
        final LinearLayout sheet;
        final NestedScrollView nested;
        final ToggleableBottomSheetBehavior<View> behavior;

        SheetLayout(CoordinatorLayout parent, LinearLayout sheet,
                NestedScrollView nested, ToggleableBottomSheetBehavior<View> behavior) {
            this.parent = parent;
            this.sheet = sheet;
            this.nested = nested;
            this.behavior = behavior;
        }
    }

    private SheetLayout layoutSheet(FragmentActivity activity, int state) {
        CoordinatorLayout parent = parent(activity);
        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        NestedScrollView nested = new NestedScrollView(activity);
        nested.setLayoutParams(new LinearLayout.LayoutParams(100, 100));
        sheet.addView(nested);
        ToggleableBottomSheetBehavior<View> behavior = behavior();
        if (state != BottomSheetBehavior.STATE_COLLAPSED) {
            behavior.setState(state);
        }
        CoordinatorLayout.LayoutParams lp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1000);
        lp.setBehavior(behavior);
        sheet.setLayoutParams(lp);
        parent.addView(sheet);
        activity.setContentView(parent);
        parent.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1080, 1920);
        return new SheetLayout(parent, sheet, nested, behavior);
    }

    @Test
    public void onInterceptTouchEvent_enabled_dragOutsideNestedChild_intercepts() {
        FragmentActivity activity = activity();
        SheetLayout layout = layoutSheet(activity, BottomSheetBehavior.STATE_EXPANDED);
        // 展开态 sheet 顶在 y = 1920 - 1000 = 920（fitToContents 时
        // expandedOffset = parentHeight - childHeight，material 1.13.0
        // BottomSheetBehavior.getExpandedOffset 字节码已核实），占 y 920..1920；
        // 100x100 的 NestedScrollView 在 sheet 左上角（全局 y 920..1020）。
        // 触点须落在 sheet 内、NestedScrollView 外：(500,1500) 满足该条件，
        // 原坐标 (500,500)/(500,700) 在 sheet 上方，根本碰不到 sheet。
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 500, 1500, 0);
        assertFalse(layout.behavior.onInterceptTouchEvent(layout.parent, layout.sheet, down));
        MotionEvent move = MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 500, 1700, 0);
        assertTrue(layout.behavior.onInterceptTouchEvent(layout.parent, layout.sheet, move));
    }

    @Test
    public void onNestedPreFling_enabled_withNestedChild_returnsTrue() {
        FragmentActivity activity = activity();
        SheetLayout layout = layoutSheet(activity, BottomSheetBehavior.STATE_COLLAPSED);
        assertTrue(layout.behavior.onNestedPreFling(layout.parent, layout.sheet,
                layout.nested, 0f, 1000f));
    }
}
