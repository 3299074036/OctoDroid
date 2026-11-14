package com.gh4a.utils;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A 组：BottomSheetListBuilder 建 BottomSheetDialog，走 Robolectric。
 * show() 需要 Activity context，用 FragmentActivity；每次 show 都会动
 * CrashBreadcrumb 计数器，@Before/@After 排空，避免测试间干扰。
 */
@RunWith(RobolectricTestRunner.class)
public class BottomSheetListBuilderTest {

    private FragmentActivity activity;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
        drainBreadcrumb();
    }

    @After
    public void tearDown() {
        drainBreadcrumb();
    }

    private void drainBreadcrumb() {
        while (CrashBreadcrumb.isAnyDialogShowing()) {
            CrashBreadcrumb.noteDialogDismissed();
        }
    }

    @Test
    public void pad_isSixteenDpInPixels() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        int expected = (int) (16 * activity.getResources()
                .getDisplayMetrics().density);
        assertEquals(expected, builder.pad());
    }

    @Test
    public void sheet_returnsNonNullDialog() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        assertNotNull(builder.sheet());
    }

    @Test
    public void addTextRow_returnsRowWithTextSizeAndPadding() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        TextView row = builder.addTextRow("Row text", null);
        assertEquals("Row text", row.getText().toString());
        assertEquals(15f, row.getTextSize()
                / activity.getResources().getDisplayMetrics().scaledDensity, 0.01f);
        assertEquals(builder.pad() / 2, row.getPaddingTop());
        assertEquals(builder.pad() / 2, row.getPaddingBottom());
    }

    @Test
    public void addTextRow_withColorSetsTextColor() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        TextView row = builder.addTextRow("Red row", 0xFFFF0000, null);
        assertEquals(0xFFFF0000, row.getCurrentTextColor());
    }

    @Test
    public void addTextRow_withNullActionClickDoesNothing() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        TextView row = builder.addTextRow("Current account", null);
        row.performClick();
        assertFalse(CrashBreadcrumb.isAnyDialogShowing());
    }

    @Test
    public void addTextRow_clickRunsAction() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        AtomicBoolean ran = new AtomicBoolean(false);
        TextView row = builder.addTextRow("Do it", v -> ran.set(true));
        row.performClick();
        assertTrue(ran.get());
    }

    @Test
    public void addRow_clickRunsAction() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        AtomicBoolean ran = new AtomicBoolean(false);
        TextView row = new TextView(activity);
        row.setText("Rich row");
        builder.addRow(row, v -> ran.set(true));
        row.performClick();
        assertTrue(ran.get());
    }

    @Test
    public void addRow_nullActionClickDoesNothing() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        AtomicBoolean ran = new AtomicBoolean(false);
        TextView row = new TextView(activity);
        builder.addRow(row, null);
        row.performClick();
        assertFalse(ran.get());
    }

    @Test
    public void show_marksBreadcrumbAndCancelRowExists() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        builder.addTextRow("Row", null);
        builder.addCancel();
        builder.show();
        try {
            assertTrue(CrashBreadcrumb.isAnyDialogShowing());
            assertTrue(findCancelRow(builder.sheet().getWindow().getDecorView()));
        } finally {
            builder.sheet().dismiss();
        }
    }

    @Test
    public void show_dismissClearsBreadcrumb() {
        BottomSheetListBuilder builder =
                new BottomSheetListBuilder(activity, "Title");
        builder.show();
        assertTrue(CrashBreadcrumb.isAnyDialogShowing());
        builder.sheet().dismiss();
        // dismiss 的 OnDismissListener 经主线程派发，先 idle 再断言。
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(CrashBreadcrumb.isAnyDialogShowing());
    }

    private boolean findCancelRow(View view) {
        if (view instanceof TextView) {
            String cancel = activity.getString(android.R.string.cancel);
            if (cancel.equals(((TextView) view).getText().toString())) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (findCancelRow(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }
}
