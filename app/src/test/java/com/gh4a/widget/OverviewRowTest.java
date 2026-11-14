package com.gh4a.widget;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class OverviewRowTest {

    private FragmentActivity activity() {
        return Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private TextView label(OverviewRow row) {
        return row.findViewById(R.id.label);
    }

    private View progress(OverviewRow row) {
        return row.findViewById(R.id.progress_indicator);
    }

    private View redirect(OverviewRow row) {
        return row.findViewById(R.id.forward_notice);
    }

    private ImageView icon(OverviewRow row) {
        return row.findViewById(R.id.icon);
    }

    @Test
    public void setText_setsLabelAndHidesProgress() {
        OverviewRow row = new OverviewRow(activity());
        row.setText("some text");
        assertEquals("some text", label(row).getText().toString());
        assertEquals(View.GONE, progress(row).getVisibility());
    }

    @Test
    public void setText_null_showsProgress() {
        OverviewRow row = new OverviewRow(activity());
        row.setText("some text");
        row.setText(null);
        assertEquals(View.VISIBLE, progress(row).getVisibility());
    }

    @Test
    public void initialState_noText_showsProgress() {
        OverviewRow row = new OverviewRow(activity());
        // Constructor calls setText(null) from the (absent) rowText attribute.
        assertEquals(View.VISIBLE, progress(row).getVisibility());
    }

    @Test
    public void setClickIntent_nonNull_makesClickableAndShowsRedirect() {
        OverviewRow row = new OverviewRow(activity());
        row.setClickIntent(new Intent(Intent.ACTION_VIEW));
        assertTrue(row.isClickable());
        assertEquals(View.VISIBLE, redirect(row).getVisibility());
    }

    @Test
    public void setClickIntent_null_makesNotClickableAndHidesRedirect() {
        OverviewRow row = new OverviewRow(activity());
        row.setClickIntent(new Intent(Intent.ACTION_VIEW));
        row.setClickIntent(null);
        assertFalse(row.isClickable());
        assertEquals(View.GONE, redirect(row).getVisibility());
    }

    @Test
    public void setClickable_false_hidesRedirect() {
        OverviewRow row = new OverviewRow(activity());
        row.setClickIntent(new Intent(Intent.ACTION_VIEW));
        row.setClickable(false);
        assertEquals(View.GONE, redirect(row).getVisibility());
    }

    @Test
    public void setClickable_true_showsRedirect() {
        OverviewRow row = new OverviewRow(activity());
        row.setClickable(true);
        assertEquals(View.VISIBLE, redirect(row).getVisibility());
    }

    @Test
    public void onClick_rowWithIntent_startsActivity() {
        FragmentActivity activity = activity();
        OverviewRow row = new OverviewRow(activity);
        Intent intent = new Intent(activity, FragmentActivity.class);
        row.setClickIntent(intent);

        row.performClick();

        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void onClick_rowWithoutIntent_doesNothing() {
        FragmentActivity activity = activity();
        OverviewRow row = new OverviewRow(activity);
        row.setClickIntent(null);

        row.performClick();

        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void setIcon_null_hidesIcon() {
        OverviewRow row = new OverviewRow(activity());
        row.setIcon(new ColorDrawable(0xFFFF0000));
        assertEquals(View.VISIBLE, icon(row).getVisibility());
        row.setIcon(null);
        assertEquals(View.GONE, icon(row).getVisibility());
    }

    @Test
    public void setIcon_drawable_showsIcon() {
        OverviewRow row = new OverviewRow(activity());
        row.setIcon(new ColorDrawable(0xFFFF0000));
        assertEquals(View.VISIBLE, icon(row).getVisibility());
        assertNotNull(icon(row).getDrawable());
    }

    @Test
    public void setIconClickListener_nonNull_enablesIconAndDispatchesClick() {
        OverviewRow row = new OverviewRow(activity());
        row.setIcon(new ColorDrawable(0xFFFF0000));
        AtomicReference<OverviewRow> seen = new AtomicReference<>();
        row.setIconClickListener(seen::set);
        assertTrue(icon(row).isEnabled());

        icon(row).performClick();

        assertEquals(row, seen.get());
    }

    @Test
    public void setIconClickListener_null_disablesIcon() {
        OverviewRow row = new OverviewRow(activity());
        row.setIcon(new ColorDrawable(0xFFFF0000));
        row.setIconClickListener(r -> {});
        assertTrue(icon(row).isEnabled());
        row.setIconClickListener(null);
        assertFalse(icon(row).isEnabled());
    }

    @Test
    public void setToggleState_doesNotThrow() {
        OverviewRow row = new OverviewRow(activity());
        row.setIcon(new ColorDrawable(0xFFFF0000));
        row.setToggleState(true);
        row.setToggleState(false);
    }

    @Test
    public void drawableHotspotChanged_andDrawableStateChanged_doNotThrow() {
        OverviewRow row = new OverviewRow(activity());
        row.drawableHotspotChanged(1f, 2f);
        row.drawableStateChanged();
    }

    @Test
    public void constructors_attrsVariants_create() {
        FragmentActivity activity = activity();
        assertNotNull(new OverviewRow(activity, null));
        assertNotNull(new OverviewRow(activity, null, 0));
    }

    @Test
    public void setClickable_true_noRedirectArrowWhenDisabledInAttrs() {
        FragmentActivity activity = activity();
        OverviewRow row = new OverviewRow(activity,
                Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.displayRedirectArrowWhenClickable, "false")
                        .build());
        row.setClickIntent(new Intent(activity, FragmentActivity.class));
        row.setClickable(true);
        assertEquals(View.GONE, redirect(row).getVisibility());
    }

    @Test
    public void onClick_rowWithoutIntent_directCall_doesNothing() {
        FragmentActivity activity = activity();
        OverviewRow row = new OverviewRow(activity);
        row.setClickIntent(null);
        // performClick() never reaches onClick without a listener, so call it
        // directly to cover the no-intent branch.
        row.onClick(row);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
}
