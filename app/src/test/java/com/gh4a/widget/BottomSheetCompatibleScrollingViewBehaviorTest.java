package com.gh4a.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.FragmentActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BottomSheetCompatibleScrollingViewBehaviorTest {

    @Test
    public void onRequestChildRectangleOnScreen_alwaysReturnsTrue() {
        BottomSheetCompatibleScrollingViewBehavior behavior =
                new BottomSheetCompatibleScrollingViewBehavior();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        CoordinatorLayout parent = new CoordinatorLayout(activity);
        View child = new View(activity);

        assertTrue(behavior.onRequestChildRectangleOnScreen(parent, child, new Rect(), false));
    }

    @Test
    public void onRequestChildRectangleOnScreen_immediateTrue_returnsTrue() {
        BottomSheetCompatibleScrollingViewBehavior behavior =
                new BottomSheetCompatibleScrollingViewBehavior();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        CoordinatorLayout parent = new CoordinatorLayout(activity);
        View child = new View(activity);

        assertTrue(behavior.onRequestChildRectangleOnScreen(parent, child, new Rect(), true));
    }

    @Test
    public void onRequestChildRectangleOnScreen_nullRect_returnsTrue() {
        BottomSheetCompatibleScrollingViewBehavior behavior =
                new BottomSheetCompatibleScrollingViewBehavior();
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        CoordinatorLayout parent = new CoordinatorLayout(activity);
        View child = new View(activity);

        assertTrue(behavior.onRequestChildRectangleOnScreen(parent, child, null, false));
    }
}
