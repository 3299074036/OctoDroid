package com.gh4a.utils.translate;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Looper;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.gh4a.resolver.BrowseFilter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * TranslationUiHelper: default-provider fast path and the single-provider
 * shortcut. The multi-provider picker tests stub TranslationManager
 * statically: the real provider list depends on credential storage, and
 * Robolectric has no AndroidKeyStore provider, so setApiKey always fails
 * there (see TranslationManagerTest.credentialRoundTrip_keyStoreUnavailable).
 */
@RunWith(RobolectricTestRunner.class)
public class TranslationUiHelperTest {

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private Activity activity() {
        // The picker dialog needs an activity context: with the application
        // context the dialog theme cannot resolve its list layout
        // (Resources$NotFoundException #0x0 in AlertController.createListView).
        return org.robolectric.Robolectric.buildActivity(BrowseFilter.class).setup().get();
    }

    @Test
    public void runWithDefaultProvider_returnsTrueWithGoogleByDefault() {
        List<String> selected = new ArrayList<>();
        assertTrue(TranslationUiHelper.runWithDefaultProvider(context(), selected::add));
        assertEquals(1, selected.size());
        assertEquals("google", selected.get(0));
    }

    @Test
    public void runWithDefaultProvider_unknownSavedProviderFallsBackToFirst() {
        TranslationManager.setProvider(context(), "nope");
        List<String> selected = new ArrayList<>();
        assertTrue(TranslationUiHelper.runWithDefaultProvider(context(), selected::add));
        assertEquals("google", selected.get(0));
    }

    @Test
    public void runWithDefaultProvider_savedProviderIsUsed() {
        try (MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            mgr.when(() -> TranslationManager.getConfiguredProviders(any()))
                    .thenReturn(Arrays.asList("google", "deepl"));
            mgr.when(() -> TranslationManager.getProvider(any())).thenReturn("deepl");
            List<String> selected = new ArrayList<>();
            assertTrue(TranslationUiHelper.runWithDefaultProvider(context(), selected::add));
            assertEquals(1, selected.size());
            assertEquals("deepl", selected.get(0));
        }
    }

    @Test
    public void pickProviderAndRun_singleProviderCallsBackWithoutDialog() {
        List<String> selected = new ArrayList<>();
        TranslationUiHelper.pickProviderAndRun(context(), selected::add);
        assertEquals(1, selected.size());
        assertEquals("google", selected.get(0));
        assertTrue(ShadowAlertDialog.getShownDialogs().isEmpty());
    }

    @Test
    public void pickProviderAndRun_multiProviderShowsDialogAndPersistsChoice() {
        try (MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            stubTwoProviders(mgr);
            List<String> selected = new ArrayList<>();
            TranslationUiHelper.pickProviderAndRun(activity(), selected::add);
            assertTrue(selected.isEmpty());
            List<Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
            assertEquals(1, dialogs.size());
            AlertDialog dialog = (AlertDialog) dialogs.get(0);
            // Default selection is the current provider (google, index 0);
            // confirm without changing the selection.
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, selected.size());
            assertEquals("google", selected.get(0));
            mgr.verify(() -> TranslationManager.setProvider(any(), eq("google")));
        }
    }

    @Test
    public void pickProviderAndRun_multiProviderCanPickSecondProvider() throws Exception {
        try (MockedStatic<TranslationManager> mgr =
                     Mockito.mockStatic(TranslationManager.class)) {
            stubTwoProviders(mgr);
            List<String> selected = new ArrayList<>();
            TranslationUiHelper.pickProviderAndRun(activity(), selected::add);
            AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getShownDialogs().get(0);
            // Simulate tapping the second row: the single-choice ListView lives
            // in the AppCompat dialog layout (select_dialog_listview); fire its
            // item-click listener directly, as ListView children are not laid
            // out under Robolectric.
            ListView list = dialog.findViewById(androidx.appcompat.R.id.select_dialog_listview);
            assertNotNull(list);
            list.setItemChecked(1, true);
            java.lang.reflect.Field f =
                    AdapterView.class.getDeclaredField("mOnItemClickListener");
            f.setAccessible(true);
            AdapterView.OnItemClickListener listener =
                    (AdapterView.OnItemClickListener) f.get(list);
            assertNotNull(listener);
            listener.onItemClick(list, null, 1, 1);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(1, selected.size());
            assertEquals("deepl", selected.get(0));
            mgr.verify(() -> TranslationManager.setProvider(any(), eq("deepl")));
        }
    }

    private void stubTwoProviders(MockedStatic<TranslationManager> mgr) {
        mgr.when(() -> TranslationManager.getConfiguredProviders(any()))
                .thenReturn(Arrays.asList("google", "deepl"));
        mgr.when(() -> TranslationManager.getProvider(any())).thenReturn("google");
        mgr.when(() -> TranslationManager.getProviderDisplayName(any(), eq("google")))
                .thenReturn("Google");
        mgr.when(() -> TranslationManager.getProviderDisplayName(any(), eq("deepl")))
                .thenReturn("DeepL");
    }
}
