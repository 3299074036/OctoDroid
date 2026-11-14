package com.gh4a.widget;

import android.view.View;
import android.view.ViewGroup;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
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
public class NestedCoordinatorLayoutTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    @Test
    public void constructors_allVariants_create() {
        FragmentActivity activity = activity();
        assertTrue(new NestedCoordinatorLayout(activity).isNestedScrollingEnabled());
        assertTrue(new NestedCoordinatorLayout(activity, null).isNestedScrollingEnabled());
        assertTrue(new NestedCoordinatorLayout(activity, null, 0).isNestedScrollingEnabled());
    }

    @Test
    public void setNestedScrollingEnabled_roundTrip() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        layout.setNestedScrollingEnabled(false);
        assertFalse(layout.isNestedScrollingEnabled());
        layout.setNestedScrollingEnabled(true);
        assertTrue(layout.isNestedScrollingEnabled());
    }

    @Test
    public void startNestedScroll_noParent_returnsFalse() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        assertFalse(layout.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL,
                ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void hasNestedScrollingParent_noParent_returnsFalse() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        assertFalse(layout.hasNestedScrollingParent(ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void dispatchNestedScroll_noParent_returnsFalse() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        assertFalse(layout.dispatchNestedScroll(0, 10, 0, 0, null, ViewCompat.TYPE_TOUCH));
    }

    @Test
    public void dispatchNestedPreScroll_noParent_returnsFalse() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        int[] consumed = {0, 0};
        assertFalse(layout.dispatchNestedPreScroll(0, 10, consumed, null, ViewCompat.TYPE_TOUCH));
        assertEquals(0, consumed[0]);
        assertEquals(0, consumed[1]);
    }

    @Test
    public void dispatchNestedFling_noParent_returnsFalse() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        assertFalse(layout.dispatchNestedFling(100f, 100f, false));
    }

    @Test
    public void dispatchNestedPreFling_noParent_returnsFalse() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        assertFalse(layout.dispatchNestedPreFling(100f, 100f));
    }

    @Test
    public void stopNestedScroll_noParent_doesNotThrow() {
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity());
        layout.stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onNestedScrollAccepted_startsNestedScrollWithoutCrash() {
        FragmentActivity activity = activity();
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity);
        View child = new View(activity);
        View target = new View(activity);
        layout.addView(child);
        layout.onNestedScrollAccepted(child, target,
                ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onStopNestedScroll_doesNotThrow() {
        FragmentActivity activity = activity();
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity);
        View target = new View(activity);
        layout.addView(target);
        layout.onStopNestedScroll(target, ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onNestedScroll_dispatchesWithoutCrash() {
        FragmentActivity activity = activity();
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity);
        View target = new View(activity);
        layout.addView(target);
        layout.onNestedScroll(target, 0, 10, 0, 0, ViewCompat.TYPE_TOUCH);
    }

    @Test
    public void onNestedPreScroll_noParent_consumesNothing() {
        FragmentActivity activity = activity();
        NestedCoordinatorLayout layout = new NestedCoordinatorLayout(activity);
        View target = new View(activity);
        layout.addView(target);
        int[] consumed = {3, 4};
        layout.onNestedPreScroll(target, 0, 10, consumed, ViewCompat.TYPE_TOUCH);
        // CoordinatorLayout.onNestedPreScroll resets the array and only adds
        // what child behaviors consume; with no behaviors and no nested
        // parent nothing is consumed.
        assertEquals(0, consumed[0]);
        assertEquals(0, consumed[1]);
    }

    @Test
    public void onNestedPreScroll_withConsumingParent_addsParentConsumption() {
        FragmentActivity activity = activity();
        // Outer layout with an AppBarLayout whose behavior consumes scroll.
        NestedCoordinatorLayout outer = new NestedCoordinatorLayout(activity);
        AppBarLayout appBar = new AppBarLayout(activity);
        View toolbar = new View(activity);
        AppBarLayout.LayoutParams lp = new AppBarLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 200);
        lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
        toolbar.setLayoutParams(lp);
        appBar.addView(toolbar);
        outer.addView(appBar);
        NestedCoordinatorLayout inner = new NestedCoordinatorLayout(activity);
        CoordinatorLayout.LayoutParams innerLp = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        innerLp.setBehavior(new AppBarLayout.ScrollingViewBehavior());
        inner.setLayoutParams(innerLp);
        outer.addView(inner);
        View target = new View(activity);
        inner.addView(target);
        activity.setContentView(outer);
        outer.measure(
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        outer.layout(0, 0, 1000, 1000);

        assertTrue(inner.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL,
                ViewCompat.TYPE_TOUCH));
        int[] consumed = {0, 0};
        inner.onNestedPreScroll(target, 0, 100, consumed, ViewCompat.TYPE_TOUCH);
        assertTrue(consumed[1] > 0);
    }
}
