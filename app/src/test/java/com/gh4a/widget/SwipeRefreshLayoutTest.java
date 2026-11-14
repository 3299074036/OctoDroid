package com.gh4a.widget;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.appbar.AppBarLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class SwipeRefreshLayoutTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private SwipeRefreshLayout layout(FragmentActivity activity) {
        SwipeRefreshLayout layout = new SwipeRefreshLayout(activity, null);
        // AndroidX SwipeRefreshLayout resolves its target (mTarget) during
        // layout; without a child view super.canChildScrollUp() NPEs on the
        // null target. Give it a plain (non-scrollable) child and lay out.
        View child = new View(activity);
        layout.addView(child);
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
        layout.layout(0, 0, 500, 500);
        return layout;
    }

    private MotionEvent event(int action, float x, float y) {
        return MotionEvent.obtain(0, 0, action, x, y, 0);
    }

    @Test
    public void canChildScrollUp_appBarExpanded_returnsFalse() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        layout.onOffsetChanged(new AppBarLayout(activity), 0);
        assertFalse(layout.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_appBarScrolled_returnsTrue() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        layout.onOffsetChanged(new AppBarLayout(activity), -100);
        assertTrue(layout.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_appBarOffsetZeroAfterScroll_returnsFalse() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        layout.onOffsetChanged(new AppBarLayout(activity), -100);
        layout.onOffsetChanged(new AppBarLayout(activity), 0);
        assertFalse(layout.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_delegateTrue_returnsTrue() {
        SwipeRefreshLayout layout = layout(activity());
        layout.setChildScrollDelegate(() -> true);
        assertTrue(layout.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_delegateFalse_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        layout.setChildScrollDelegate(() -> false);
        assertFalse(layout.canChildScrollUp());
    }

    @Test
    public void canChildScrollUp_appBarBeatsDelegate() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        layout.setChildScrollDelegate(() -> false);
        layout.onOffsetChanged(new AppBarLayout(activity), -50);
        assertTrue(layout.canChildScrollUp());
    }

    @Test
    public void setAppBarLayout_registersListener() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        AppBarLayout abl = new AppBarLayout(activity);
        layout.setAppBarLayout(abl);
        // No direct observable; the offset callback path is covered by
        // onOffsetChanged tests, this only asserts registration doesn't crash.
        layout.onOffsetChanged(abl, -10);
        assertTrue(layout.canChildScrollUp());
    }

    @Test
    public void onStartNestedScroll_alwaysReturnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.onStartNestedScroll(new View(activity()),
                new View(activity()), 0));
    }

    @Test
    public void onInterceptTouchEvent_downThenUpSwipe_preventsRefresh() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        layout.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 200));
        layout.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 100, 200 - slop - 10));
        // A later MOVE while the up-swipe flag is set must not intercept.
        assertFalse(layout.onInterceptTouchEvent(
                event(MotionEvent.ACTION_MOVE, 100, 200 - slop - 20)));
    }

    @Test
    public void onInterceptTouchEvent_downThenHorizontalSwipe_returnsFalse() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        layout.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 200));
        assertFalse(layout.onInterceptTouchEvent(
                event(MotionEvent.ACTION_MOVE, 100 + slop + 10, 200)));
    }

    @Test
    public void onInterceptTouchEvent_downThenSmallMove_delegatesToSuper() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);

        layout.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 200));
        boolean result = layout.onInterceptTouchEvent(
                event(MotionEvent.ACTION_MOVE, 101, 201));

        // Small move, no horizontal swipe, no up swipe: super decides
        // (no child, not refreshing -> false).
        assertFalse(result);
    }

    @Test
    public void nestedScrollingEnabled_roundTrip() {
        SwipeRefreshLayout layout = layout(activity());
        assertTrue(layout.isNestedScrollingEnabled());
        layout.setNestedScrollingEnabled(false);
        assertFalse(layout.isNestedScrollingEnabled());
        layout.setNestedScrollingEnabled(true);
        assertTrue(layout.isNestedScrollingEnabled());
    }

    @Test
    public void startNestedScroll_noParent_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.startNestedScroll(0));
    }

    @Test
    public void stopNestedScroll_noParent_doesNotThrow() {
        SwipeRefreshLayout layout = layout(activity());
        layout.stopNestedScroll();
    }

    @Test
    public void hasNestedScrollingParent_noParent_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.hasNestedScrollingParent());
    }

    @Test
    public void dispatchNestedScroll_noParent_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.dispatchNestedScroll(0, 0, 0, 0, null));
    }

    @Test
    public void dispatchNestedPreScroll_noParent_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        int[] consumed = {0, 0};
        assertFalse(layout.dispatchNestedPreScroll(0, 0, consumed, null));
        assertEquals(0, consumed[0]);
    }

    @Test
    public void dispatchNestedFling_noParent_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.dispatchNestedFling(0f, 0f, false));
    }

    @Test
    public void dispatchNestedPreFling_noParent_returnsFalse() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.dispatchNestedPreFling(0f, 0f));
    }

    @Test
    public void onNestedScroll_preventRefresh_dispatchesWithoutCrash() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        layout.setChildScrollDelegate(() -> true);
        layout.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 200));
        View target = new View(activity);
        layout.onNestedScroll(target, 0, 10, 0, 0);
    }

    @Test
    public void onNestedScroll_noPreventRefresh_delegatesToSuper() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        View target = new View(activity);
        layout.onNestedScroll(target, 0, 10, 0, 0);
    }

    @Test
    public void onInterceptTouchEvent_actionUp_delegatesToSuper() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.onInterceptTouchEvent(event(MotionEvent.ACTION_UP, 100, 200)));
    }

    @Test
    public void onInterceptTouchEvent_actionCancel_delegatesToSuper() {
        SwipeRefreshLayout layout = layout(activity());
        assertFalse(layout.onInterceptTouchEvent(event(MotionEvent.ACTION_CANCEL, 100, 200)));
    }

    @Test
    public void onInterceptTouchEvent_horizontalSwipeThenSmallMove_returnsFalse() {
        FragmentActivity activity = activity();
        SwipeRefreshLayout layout = layout(activity);
        int slop = ViewConfiguration.get(activity).getScaledTouchSlop();

        layout.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN, 100, 200));
        assertFalse(layout.onInterceptTouchEvent(
                event(MotionEvent.ACTION_MOVE, 100 + slop + 10, 200)));
        // mHorizontalSwipe is sticky: a later small MOVE is still rejected.
        assertFalse(layout.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE, 101, 200)));
    }
}
