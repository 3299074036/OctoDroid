package com.gh4a.utils;

import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.BaseActivity;
import com.gh4a.BuildConfig;
import com.gh4a.R;

/**
 * UI around {@link UpdateChecker}: progress and result dialogs for the manual
 * settings entry, and a silent daily check on startup.
 */
public class UpdateCheckUi {
    private static final int MAX_NOTES_LENGTH = 2000;

    /** Manual check from settings: progress dialog, then result dialog or toast. */
    public static void checkManually(BaseActivity activity) {
        AlertDialog progress = new AlertDialog.Builder(activity)
                .setMessage(R.string.checking_update)
                .setCancelable(false)
                .create();
        progress.show();

        UpdateChecker.check(activity, new UpdateChecker.Callback() {
            @Override
            public void onResult(boolean hasUpdate, String latestVersion,
                    String releaseNotes, String apkUrl) {
                // 先 dismiss：Activity 若已销毁，直接 return 会泄漏窗口
                progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (hasUpdate) {
                    showUpdateDialog(activity, latestVersion, releaseNotes, apkUrl);
                } else {
                    Toast.makeText(activity, R.string.already_latest,
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                Toast.makeText(activity,
                        activity.getString(R.string.update_check_failed, message),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Silent startup check: runs on every app startup when enabled,
     * only speaks up when an update is actually found.
     */
    public static void checkAutomatically(BaseActivity activity) {
        if (!UpdateChecker.shouldAutoCheck(activity)) {
            return;
        }
        UpdateChecker.check(activity, new UpdateChecker.Callback() {
            @Override
            public void onResult(boolean hasUpdate, String latestVersion,
                    String releaseNotes, String apkUrl) {
                if (hasUpdate && !activity.isFinishing() && !activity.isDestroyed()) {
                    showUpdateDialog(activity, latestVersion, releaseNotes, apkUrl);
                }
            }

            @Override
            public void onError(String message) {
                // Silent: the user didn't ask, don't nag about failures.
            }
        });
    }

    private static void showUpdateDialog(BaseActivity activity, String latestVersion,
            String releaseNotes, String apkUrl) {
        String notes = releaseNotes != null ? releaseNotes.trim() : "";
        if (notes.length() > MAX_NOTES_LENGTH) {
            notes = notes.substring(0, MAX_NOTES_LENGTH) + "…";
        }
        String message = activity.getString(R.string.update_available_message,
                latestVersion, BuildConfig.VERSION_NAME, notes);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available)
                .setMessage(message)
                .setPositiveButton(R.string.download_update, (dialog, which) ->
                        ApkUpdateDownloader.downloadAndInstall(activity, apkUrl,
                                "OctoDroid_" + latestVersion + ".apk"))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
