package com.gh4a.widget;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class FixedSizeImageViewTest {

    private static class CountingParent extends FrameLayout {
        int requestLayoutCount = 0;

        CountingParent(FragmentActivity activity) {
            super(activity);
        }

        @Override
        public void requestLayout() {
            requestLayoutCount++;
            super.requestLayout();
        }
    }

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private FixedSizeImageView attachedView(CountingParent parent) {
        FixedSizeImageView view = new FixedSizeImageView(parent.getContext());
        parent.addView(view);
        return view;
    }

    @Test
    public void setImageDrawable_doesNotTriggerParentLayout() {
        FragmentActivity activity = activity();
        CountingParent parent = new CountingParent(activity);
        activity.setContentView(parent);
        FixedSizeImageView view = attachedView(parent);
        int before = parent.requestLayoutCount;

        view.setImageDrawable(new ColorDrawable(0xFFFF0000));

        assertEquals(before, parent.requestLayoutCount);
    }

    @Test
    public void setImageResource_doesNotTriggerParentLayout() {
        FragmentActivity activity = activity();
        CountingParent parent = new CountingParent(activity);
        activity.setContentView(parent);
        FixedSizeImageView view = attachedView(parent);
        int before = parent.requestLayoutCount;

        view.setImageResource(R.drawable.pull_request_merge_ok);

        assertEquals(before, parent.requestLayoutCount);
    }

    @Test
    public void setImageURI_null_doesNotTriggerParentLayout() {
        FragmentActivity activity = activity();
        CountingParent parent = new CountingParent(activity);
        activity.setContentView(parent);
        FixedSizeImageView view = attachedView(parent);
        int before = parent.requestLayoutCount;

        view.setImageURI(null);

        assertEquals(before, parent.requestLayoutCount);
    }

    @Test
    public void requestLayout_directCall_propagatesToParent() {
        FragmentActivity activity = activity();
        CountingParent parent = new CountingParent(activity);
        activity.setContentView(parent);
        FixedSizeImageView view = attachedView(parent);
        // Attaching leaves a layout request pending on the parent, and
        // View.requestLayout() coalesces while one is pending, so run a
        // layout pass first to clear the flag.
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 500, 500);
        int before = parent.requestLayoutCount;

        view.requestLayout();

        assertEquals(before + 1, parent.requestLayoutCount);
    }

    @Test
    public void setImageDrawable_setsDrawable() {
        FragmentActivity activity = activity();
        FixedSizeImageView view = new FixedSizeImageView(activity);
        Drawable drawable = new ColorDrawable(0xFF00FF00);

        view.setImageDrawable(drawable);

        assertEquals(drawable, view.getDrawable());
    }

    @Test
    public void setImageDrawable_null_clearsDrawable() {
        FragmentActivity activity = activity();
        FixedSizeImageView view = new FixedSizeImageView(activity);
        view.setImageDrawable(new ColorDrawable(0xFF00FF00));

        view.setImageDrawable(null);

        assertNull(view.getDrawable());
    }

    @Test
    public void constructors_attrsVariants_create() {
        FragmentActivity activity = activity();
        FixedSizeImageView a = new FixedSizeImageView(activity, null);
        FixedSizeImageView b = new FixedSizeImageView(activity, null, 0);
        assertNull(a.getDrawable());
        assertNull(b.getDrawable());
    }

    @Test
    public void suppressionEnds_afterSetImageCall_requestLayoutWorksAgain() {
        FragmentActivity activity = activity();
        CountingParent parent = new CountingParent(activity);
        activity.setContentView(parent);
        FixedSizeImageView view = attachedView(parent);
        view.setImageDrawable(new ColorDrawable(0xFFFF0000));
        // See requestLayout_directCall_propagatesToParent: clear the pending
        // layout request first, otherwise propagation is coalesced away.
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 500, 500);
        int before = parent.requestLayoutCount;

        view.requestLayout();

        assertEquals(before + 1, parent.requestLayoutCount);
    }
}
