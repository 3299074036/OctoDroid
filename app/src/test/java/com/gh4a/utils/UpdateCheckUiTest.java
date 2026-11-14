package com.gh4a.utils;

import android.app.Dialog;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.BaseActivity;
import com.gh4a.BuildConfig;
import com.gh4a.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * UpdateCheckUi with UpdateChecker.check statically stubbed, so no real
 * network is involved: the captured callback is driven by hand to cover
 * the result / no-apk / already-latest / error / cancelled branches and
 * the silent auto-check. ApkUpdateDownloader is also statically stubbed
 * so clicking "Download" never touches the real downloader.
 */
@RunWith(RobolectricTestRunner.class)
public class UpdateCheckUiTest {

    /** Minimal concrete BaseActivity; onCreate is intentionally not run. */
    public static class TestBaseActivity extends BaseActivity {
    }

    private BaseActivity activity() {
        return Robolectric.buildActivity(TestBaseActivity.class).get();
    }

    private AtomicReference<UpdateChecker.Callback> stubCheck(
            MockedStatic<UpdateChecker> mocked) {
        AtomicReference<UpdateChecker.Callback> cb = new AtomicReference<>();
        mocked.when(() -> UpdateChecker.check(any(), any())).thenAnswer(inv -> {
            cb.set(inv.getArgument(1));
            return null;
        });
        return cb;
    }

    private AlertDialog lastShownDialog() {
        List<Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertFalse(dialogs.isEmpty());
        return (AlertDialog) dialogs.get(dialogs.size() - 1);
    }

    private static String titleOf(AlertDialog dialog) {
        android.widget.TextView title =
                dialog.findViewById(androidx.appcompat.R.id.alertTitle);
        assertNotNull(title);
        return title.getText().toString();
    }

    private static String messageOf(android.app.Dialog dialog) {
        android.widget.TextView message =
                dialog.findViewById(android.R.id.message);
        assertNotNull(message);
        return message.getText().toString();
    }

    /** No dialog in the shown list is currently showing. */
    private static void assertNoDialogShowing() {
        for (android.app.Dialog d : ShadowAlertDialog.getShownDialogs()) {
            assertFalse("dialog still showing: " + d, d.isShowing());
        }
    }

    @Test
    public void checkManually_showsProgressDialog() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            assertNotNull(cb.get());
            assertEquals(1, ShadowAlertDialog.getShownDialogs().size());
            assertEquals(act.getString(R.string.checking_update),
                    messageOf(ShadowAlertDialog.getShownDialogs().get(0)));
        }
    }

    @Test
    public void checkManually_updateFoundShowsUpdateDialog() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class);
             MockedStatic<ApkUpdateDownloader> dl =
                     Mockito.mockStatic(ApkUpdateDownloader.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            AlertDialog progress = lastShownDialog();
            cb.get().onResult(true, "9.9.9", "Some notes", "https://example.test/app.apk");
            // Progress dialog dismissed, update dialog shown instead.
            assertFalse(progress.isShowing());
            AlertDialog dialog = lastShownDialog();
            assertFalse(dialog == progress);
            assertEquals(act.getString(R.string.update_available),
                    titleOf(dialog));
            String message = messageOf(dialog);
            assertTrue(message.contains("9.9.9"));
            assertTrue(message.contains(BuildConfig.VERSION_NAME));
            assertTrue(message.contains("Some notes"));
            // Clicking Download hands the apk URL to the downloader.
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            dl.verify(() -> ApkUpdateDownloader.downloadAndInstall(eq(act),
                    eq("https://example.test/app.apk"), any()));
        }
    }

    @Test
    public void checkManually_updateFoundWithoutApkShowsToast() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            cb.get().onResult(true, "9.9.9", "notes", null);
            assertNoDialogShowing();
            assertEquals(act.getString(R.string.update_no_apk, "9.9.9"),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void checkManually_noUpdateShowsAlreadyLatestToast() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            cb.get().onResult(false, "0.2.5", "", null);
            assertNoDialogShowing();
            assertEquals(act.getString(R.string.already_latest),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void checkManually_errorShowsFailureToast() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            cb.get().onError("boom");
            assertNoDialogShowing();
            assertEquals(act.getString(R.string.update_check_failed, "boom"),
                    ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void checkManually_cancelledProgressSuppressesResult() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            AlertDialog progress = (AlertDialog) ShadowAlertDialog.getShownDialogs().get(0);
            progress.cancel();
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            cb.get().onResult(true, "9.9.9", "notes", "https://example.test/app.apk");
            assertNoDialogShowing();
            assertNull(ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void checkManually_longNotesAreTruncated() {
        BaseActivity act = activity();
        StringBuilder longNotes = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            longNotes.append('x');
        }
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            cb.get().onResult(true, "9.9.9", longNotes.toString(),
                    "https://example.test/app.apk");
            String message = messageOf(lastShownDialog());
            assertTrue(message.contains("…"));
            assertFalse(message.contains(longNotes.toString()));
        }
    }

    @Test
    public void checkManually_nullNotesDoNotCrash() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkManually(act);
            cb.get().onResult(true, "9.9.9", null, "https://example.test/app.apk");
            AlertDialog dialog = lastShownDialog();
            assertTrue(dialog.isShowing());
            assertEquals(act.getString(R.string.update_available), titleOf(dialog));
        }
    }

    @Test
    public void checkAutomatically_updateFoundShowsDialog() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            mocked.when(() -> UpdateChecker.shouldAutoCheck(any())).thenReturn(true);
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkAutomatically(act);
            assertNotNull(cb.get());
            cb.get().onResult(true, "9.9.9", "notes", "https://example.test/app.apk");
            assertEquals(1, ShadowAlertDialog.getShownDialogs().size());
        }
    }

    @Test
    public void checkAutomatically_noUpdateOrErrorStaysSilent() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            mocked.when(() -> UpdateChecker.shouldAutoCheck(any())).thenReturn(true);
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkAutomatically(act);
            cb.get().onResult(false, "0.2.5", "", null);
            assertNoDialogShowing();
            assertNull(ShadowToast.getTextOfLatestToast());
            cb.get().onError("boom");
            assertNoDialogShowing();
            assertNull(ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void checkAutomatically_updateWithoutApkStaysSilent() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            mocked.when(() -> UpdateChecker.shouldAutoCheck(any())).thenReturn(true);
            AtomicReference<UpdateChecker.Callback> cb = stubCheck(mocked);
            UpdateCheckUi.checkAutomatically(act);
            cb.get().onResult(true, "9.9.9", "notes", null);
            assertNoDialogShowing();
            assertNull(ShadowToast.getTextOfLatestToast());
        }
    }

    @Test
    public void checkAutomatically_disabledPolicyDoesNotCheck() {
        BaseActivity act = activity();
        try (MockedStatic<UpdateChecker> mocked = Mockito.mockStatic(UpdateChecker.class)) {
            mocked.when(() -> UpdateChecker.shouldAutoCheck(any())).thenReturn(false);
            UpdateCheckUi.checkAutomatically(act);
            mocked.verify(() -> UpdateChecker.check(any(), any()), Mockito.times(0));
        }
    }
}
