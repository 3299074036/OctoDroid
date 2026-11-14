package com.gh4a.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TimePicker;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gh4a.R;
import com.gh4a.utils.DarkModeScheduler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

/** DarkModeScheduleDialogFragment (F1). */
@RunWith(RobolectricTestRunner.class)
public class DarkModeScheduleDialogFragmentTest {

    public static class RefreshParent extends Fragment
            implements DarkModeScheduleDialogFragment.SettingsRefreshListener {
        int refreshCalls;

        @Override
        public void refreshDarkModeScheduleSummary() {
            refreshCalls++;
        }
    }

    @Test
    public void newInstance_returnsFragment() {
        assertNotNull(DarkModeScheduleDialogFragment.newInstance());
    }

    private static List<TimePicker> findPickers(Dialog dialog) {
        List<TimePicker> pickers = new ArrayList<>();
        collectPickers(((AlertDialog) dialog).findViewById(android.R.id.content), pickers);
        return pickers;
    }

    private static void collectPickers(android.view.View view, List<TimePicker> out) {
        if (view instanceof TimePicker) {
            out.add((TimePicker) view);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectPickers(group.getChildAt(i), out);
            }
        }
    }

    @Test
    public void onCreateDialog_showsTitleAndTwoPickersWithStoredValues() {
        DarkModeScheduleDialogFragment f = DarkModeScheduleDialogFragment.newInstance();
        FragmentTestHelper.attach(f);
        DarkModeScheduler.setSchedule(f.requireContext(), 22 * 60 + 30, 6 * 60 + 15);
        Dialog dialog = f.onCreateDialog(null);
        assertTrue(dialog instanceof AlertDialog);
        AlertDialog alert = (AlertDialog) dialog;
        alert.show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        // NOTE: ShadowAlertDialog.getTitle() only tracks framework
        // android.app.AlertDialog; this is an AppCompat dialog, so read the
        // title TextView populated by AlertDialog.onCreate instead.
        android.widget.TextView titleView =
                alert.findViewById(androidx.appcompat.R.id.alertTitle);
        assertNotNull(titleView);
        assertEquals(f.getString(R.string.dark_mode_schedule_time),
                titleView.getText().toString());
        List<TimePicker> pickers = findPickers(alert);
        assertEquals(2, pickers.size());
        assertTrue(pickers.get(0).is24HourView());
        assertEquals(22, pickers.get(0).getHour());
        assertEquals(30, pickers.get(0).getMinute());
        assertEquals(6, pickers.get(1).getHour());
        assertEquals(15, pickers.get(1).getMinute());
    }

    @Test
    public void onCreateDialog_layoutIsVertical() {
        DarkModeScheduleDialogFragment f = DarkModeScheduleDialogFragment.newInstance();
        FragmentTestHelper.attach(f);
        AlertDialog alert = (AlertDialog) f.onCreateDialog(null);
        alert.show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        List<TimePicker> pickers = findPickers(alert);
        assertEquals(2, pickers.size());
        android.view.ViewGroup parent = (android.view.ViewGroup) pickers.get(0).getParent();
        assertTrue(parent instanceof LinearLayout);
        assertEquals(LinearLayout.VERTICAL, ((LinearLayout) parent).getOrientation());
    }

    @Test
    public void positiveButton_writesScheduleFromPickers() {
        DarkModeScheduleDialogFragment f = DarkModeScheduleDialogFragment.newInstance();
        FragmentTestHelper.attach(f);
        DarkModeScheduler.setSchedule(f.requireContext(), 21 * 60, 7 * 60);
        AlertDialog alert = (AlertDialog) f.onCreateDialog(null);
        alert.show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        List<TimePicker> pickers = findPickers(alert);
        pickers.get(0).setHour(23);
        pickers.get(0).setMinute(45);
        pickers.get(1).setHour(5);
        pickers.get(1).setMinute(30);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(23 * 60 + 45,
                DarkModeScheduler.getStartMinutes(f.requireContext()));
        assertEquals(5 * 60 + 30, DarkModeScheduler.getEndMinutes(f.requireContext()));
    }

    public static class RefreshActivity extends androidx.fragment.app.FragmentActivity
            implements DarkModeScheduleDialogFragment.SettingsRefreshListener {
        int refreshCalls;

        @Override
        public void refreshDarkModeScheduleSummary() {
            refreshCalls++;
        }
    }

    @Test
    public void positiveButton_notifiesActivityListener() {
        RefreshActivity activity = org.robolectric.Robolectric
                .buildActivity(RefreshActivity.class).setup().get();
        DarkModeScheduleDialogFragment f = DarkModeScheduleDialogFragment.newInstance();
        activity.getSupportFragmentManager().beginTransaction()
                .add(f, "sched").commitNow();
        activity.getSupportFragmentManager().executePendingTransactions();
        AlertDialog alert = (AlertDialog) f.getDialog();
        assertNotNull(alert);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, activity.refreshCalls);
    }

    @Test
    public void positiveButton_notifiesParentFragmentListener() {
        RefreshParent parent = new RefreshParent();
        FragmentTestHelper.attach(parent);
        DarkModeScheduleDialogFragment f = DarkModeScheduleDialogFragment.newInstance();
        f.show(parent.getChildFragmentManager(), "sched");
        parent.getChildFragmentManager().executePendingTransactions();
        AlertDialog alert = (AlertDialog) f.getDialog();
        assertNotNull(alert);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        alert.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, parent.refreshCalls);
    }
}
