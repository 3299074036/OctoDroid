package com.gh4a.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.gh4a.R;
import com.gh4a.utils.DarkModeScheduler;

/**
 * Lets the user pick the start/end of the scheduled dark period.
 */
public class DarkModeScheduleDialogFragment extends DialogFragment {
    public static DarkModeScheduleDialogFragment newInstance() {
        return new DarkModeScheduleDialogFragment();
    }

    public interface SettingsRefreshListener {
        void refreshDarkModeScheduleSummary();
    }

    private static int dp(android.content.Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        android.content.Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        layout.setPadding(pad, dp(context, 8), pad, 0);

        TextView startLabel = new TextView(context);
        startLabel.setText(R.string.dark_mode_start);
        TimePicker startPicker = new TimePicker(context);
        startPicker.setIs24HourView(true);
        int start = DarkModeScheduler.getStartMinutes(context);
        startPicker.setHour(start / 60);
        startPicker.setMinute(start % 60);

        TextView endLabel = new TextView(context);
        endLabel.setText(R.string.dark_mode_end);
        endLabel.setPadding(0, dp(context, 8), 0, 0);
        TimePicker endPicker = new TimePicker(context);
        endPicker.setIs24HourView(true);
        int end = DarkModeScheduler.getEndMinutes(context);
        endPicker.setHour(end / 60);
        endPicker.setMinute(end % 60);

        layout.addView(startLabel);
        layout.addView(startPicker);
        layout.addView(endLabel);
        layout.addView(endPicker);

        return new AlertDialog.Builder(context)
                .setTitle(R.string.dark_mode_schedule_time)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    DarkModeScheduler.setSchedule(requireContext(),
                            startPicker.getHour() * 60 + startPicker.getMinute(),
                            endPicker.getHour() * 60 + endPicker.getMinute());
                    if (getParentFragment() instanceof SettingsRefreshListener) {
                        ((SettingsRefreshListener) getParentFragment()).refreshDarkModeScheduleSummary();
                    } else if (getActivity() instanceof SettingsRefreshListener) {
                        ((SettingsRefreshListener) getActivity()).refreshDarkModeScheduleSummary();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }
}
