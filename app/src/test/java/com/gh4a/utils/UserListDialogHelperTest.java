package com.gh4a.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.gh4a.R;
import com.gh4a.fragment.UserListsGraphQL;
import com.gh4a.resolver.BrowseFilter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * UserListDialogHelper: assign dialog (multi-choice + create-new + cancel)
 * and create-list name dialog (empty/dup validation, confirm path) run
 * against Robolectric dialogs. The network-backed createList path of
 * showCreateListDialog is stubbed with a static mock of
 * UserListsGraphQL.createList.
 */
@RunWith(RobolectricTestRunner.class)
public class UserListDialogHelperTest {

    private Activity activity() {
        return Robolectric.buildActivity(BrowseFilter.class).setup().get();
    }

    private AlertDialog shownDialog() {
        // getShownDialogs() accumulates across shows within one test,
        // so the dialog under test is always the last one.
        List<Dialog> dialogs = ShadowAlertDialog.getShownDialogs();
        assertFalse(dialogs.isEmpty());
        return (AlertDialog) dialogs.get(dialogs.size() - 1);
    }

    private void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    /** UserList's constructor is package-private; build instances via reflection. */
    private static UserListsGraphQL.UserList makeList(String id, String name) throws Exception {
        Constructor<UserListsGraphQL.UserList> c = UserListsGraphQL.UserList.class
                .getDeclaredConstructor(String.class, String.class, String.class, List.class);
        c.setAccessible(true);
        return c.newInstance(id, name, "", Collections.emptyList());
    }

    private List<UserListsGraphQL.UserList> twoLists() throws Exception {
        return Arrays.asList(makeList("id-1", "First"), makeList("id-2", "Second"));
    }

    // ---------- showAssignDialog ----------

    @Test
    public void assignDialog_showsMessageAndPreCheckedItems() throws Exception {
        Activity act = activity();
        UserListDialogHelper.showAssignDialog(act, twoLists(),
                new HashSet<>(Collections.singletonList("id-2")),
                "Pick lists", null, null);
        AlertDialog dialog = shownDialog();
        TextView subtitle = dialog.findViewById(R.id.tv_subtitle);
        assertNotNull(subtitle);
        assertEquals("Pick lists", subtitle.getText().toString());
        assertEquals(View.VISIBLE, subtitle.getVisibility());
        ListView list = dialog.findViewById(R.id.list);
        assertNotNull(list);
        assertFalse(list.isItemChecked(0));
        assertTrue(list.isItemChecked(1));
    }

    @Test
    public void assignDialog_nullMessageHidesSubtitle() throws Exception {
        Activity act = activity();
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, null);
        TextView subtitle = shownDialog().findViewById(R.id.tv_subtitle);
        assertEquals(View.GONE, subtitle.getVisibility());
    }

    @Test
    public void assignDialog_okReturnsCheckedIdsInOrder() throws Exception {
        Activity act = activity();
        AtomicReference<List<String>> received = new AtomicReference<>();
        UserListDialogHelper.showAssignDialog(act, twoLists(),
                new HashSet<>(Collections.singletonList("id-1")),
                null, null, received::set);
        AlertDialog dialog = shownDialog();
        ListView list = dialog.findViewById(R.id.list);
        // Simulate tapping row 1: mark it checked, then fire the item-click
        // listener directly (ListView children are not laid out in Robolectric,
        // so performItemClick with a child view is unreliable).
        list.setItemChecked(1, true);
        java.lang.reflect.Field f = AdapterView.class.getDeclaredField("mOnItemClickListener");
        f.setAccessible(true);
        AdapterView.OnItemClickListener listener =
                (AdapterView.OnItemClickListener) f.get(list);
        assertNotNull(listener);
        listener.onItemClick(list, null, 1, 1);
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idleMain();
        assertEquals(Arrays.asList("id-1", "id-2"), received.get());
    }

    @Test
    public void assignDialog_okWithNothingCheckedReturnsEmptyList() throws Exception {
        Activity act = activity();
        AtomicReference<List<String>> received = new AtomicReference<>();
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, received::set);
        shownDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idleMain();
        assertEquals(Collections.emptyList(), received.get());
    }

    @Test
    public void assignDialog_createNewRunsCallback() throws Exception {
        Activity act = activity();
        AtomicBoolean created = new AtomicBoolean(false);
        AtomicBoolean confirmed = new AtomicBoolean(false);
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null,
                () -> created.set(true), ids -> confirmed.set(true));
        shownDialog().getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        idleMain();
        assertTrue(created.get());
        assertFalse(confirmed.get());
    }

    @Test
    public void assignDialog_cancelButtonRunsOnCancel() throws Exception {
        Activity act = activity();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, null,
                () -> cancelled.set(true));
        shownDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        idleMain();
        assertTrue(cancelled.get());
    }

    @Test
    public void assignDialog_nullCallbacksDoNotCrash() throws Exception {
        Activity act = activity();
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, null, null);
        shownDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idleMain();
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, null, null);
        shownDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        idleMain();
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, null, null);
        shownDialog().getButton(DialogInterface.BUTTON_NEUTRAL).performClick();
        idleMain();
    }

    @Test
    public void assignDialog_emptyListOkReturnsEmpty() {
        Activity act = activity();
        AtomicReference<List<String>> received = new AtomicReference<>();
        UserListDialogHelper.showAssignDialog(act, Collections.emptyList(),
                null, null, null, received::set);
        shownDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idleMain();
        assertEquals(Collections.emptyList(), received.get());
    }

    @Test
    public void assignDialog_cancelViaDialogCancelRunsOnCancel() throws Exception {
        Activity act = activity();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        UserListDialogHelper.showAssignDialog(act, twoLists(), null, null, null, null,
                () -> cancelled.set(true));
        shownDialog().cancel();
        idleMain();
        assertTrue(cancelled.get());
    }

    // ---------- showCreateListNameDialog ----------

    private AlertDialog showNameDialog(Activity act, List<UserListsGraphQL.UserList> existing,
            UserListDialogHelper.OnCreateNameConfirmed cb, Runnable onCancel) {
        UserListDialogHelper.showCreateListNameDialog(act, existing, cb, onCancel);
        idleMain();
        return shownDialog();
    }

    private void clickSave(AlertDialog dialog) {
        idleMain();
        Button save = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        assertNotNull(save);
        save.performClick();
        idleMain();
    }

    @Test
    public void createNameDialog_validNameConfirmsTrimmedWithPrivacyFlag() throws Exception {
        Activity act = activity();
        AtomicReference<String> name = new AtomicReference<>();
        AtomicBoolean priv = new AtomicBoolean(false);
        AlertDialog dialog = showNameDialog(act, twoLists(),
                (n, p) -> {
                    name.set(n);
                    priv.set(p);
                }, null);
        EditText input = dialog.findViewById(R.id.et_list_name);
        SwitchCompat sw = dialog.findViewById(R.id.sw_private);
        assertNotNull(input);
        assertNotNull(sw);
        input.setText("  My New List  ");
        sw.setChecked(true);
        clickSave(dialog);
        assertEquals("My New List", name.get());
        assertTrue(priv.get());
        assertFalse(dialog.isShowing());
    }

    @Test
    public void createNameDialog_emptyNameShowsToastAndStaysOpen() throws Exception {
        Activity act = activity();
        AtomicBoolean called = new AtomicBoolean(false);
        AlertDialog dialog = showNameDialog(act, twoLists(),
                (n, p) -> called.set(true), null);
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("   ");
        clickSave(dialog);
        assertFalse(called.get());
        assertTrue(dialog.isShowing());
        assertEquals(act.getString(R.string.star_list_name_empty),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void createNameDialog_duplicateNameCaseInsensitiveShowsToast() throws Exception {
        Activity act = activity();
        AtomicBoolean called = new AtomicBoolean(false);
        AlertDialog dialog = showNameDialog(act, twoLists(),
                (n, p) -> called.set(true), null);
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("first");
        clickSave(dialog);
        assertFalse(called.get());
        assertTrue(dialog.isShowing());
        assertEquals(act.getString(R.string.star_list_name_dup),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void createNameDialog_nullExistingListsSkipsDupCheck() {
        Activity act = activity();
        AtomicReference<String> name = new AtomicReference<>();
        AlertDialog dialog = showNameDialog(act, null, (n, p) -> name.set(n), null);
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("Anything");
        clickSave(dialog);
        assertEquals("Anything", name.get());
    }

    @Test
    public void createNameDialog_nullCallbackDoesNotCrash() {
        Activity act = activity();
        AlertDialog dialog = showNameDialog(act, Collections.emptyList(), null, null);
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("Name");
        clickSave(dialog);
        assertFalse(dialog.isShowing());
    }

    @Test
    public void createNameDialog_cancelButtonRunsOnCancel() {
        Activity act = activity();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog dialog = showNameDialog(act, Collections.emptyList(),
                (n, p) -> {
                }, () -> cancelled.set(true));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        idleMain();
        assertTrue(cancelled.get());
    }

    @Test
    public void createNameDialog_dialogCancelRunsOnCancel() {
        Activity act = activity();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog dialog = showNameDialog(act, Collections.emptyList(),
                (n, p) -> {
                }, () -> cancelled.set(true));
        dialog.cancel();
        idleMain();
        assertTrue(cancelled.get());
    }

    // ---------- showCreateListDialog (network path stubbed) ----------

    @Test
    public void createListDialog_emptyNameShowsToastAndStaysOpen() throws Exception {
        Activity act = activity();
        UserListDialogHelper.showCreateListDialog(act, twoLists(),
                new CompositeDisposable(), null);
        idleMain();
        AlertDialog dialog = shownDialog();
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("");
        idleMain();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idleMain();
        assertTrue(dialog.isShowing());
        assertEquals(act.getString(R.string.star_list_name_empty),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void createListDialog_duplicateNameCaseInsensitiveShowsToast() throws Exception {
        Activity act = activity();
        UserListDialogHelper.showCreateListDialog(act, twoLists(),
                new CompositeDisposable(), null);
        idleMain();
        AlertDialog dialog = shownDialog();
        EditText input = dialog.findViewById(R.id.et_list_name);
        input.setText("SECOND");
        idleMain();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        idleMain();
        assertTrue(dialog.isShowing());
        assertEquals(act.getString(R.string.star_list_name_dup),
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void createListDialog_successDismissesAndCallsOnCreated() throws Exception {
        Activity act = activity();
        UserListsGraphQL.UserList created = makeList("id-9", "Brand New");
        CompositeDisposable disposables = new CompositeDisposable();
        AtomicReference<UserListsGraphQL.UserList> received = new AtomicReference<>();
        try (MockedStatic<UserListsGraphQL> mocked = Mockito.mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList("Brand New", false))
                    .thenReturn(Single.just(created));
            UserListDialogHelper.showCreateListDialog(act, twoLists(), disposables,
                    received::set);
            idleMain();
            AlertDialog dialog = shownDialog();
            EditText input = dialog.findViewById(R.id.et_list_name);
            input.setText("Brand New");
            idleMain();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            // Schedulers.io() runs on a real thread; pump the main looper
            // until the callback lands.
            for (int i = 0; i < 100 && received.get() == null; i++) {
                idleMain();
                Thread.sleep(50);
            }
            assertEquals(created, received.get());
            assertFalse(dialog.isShowing());
            assertEquals(1, disposables.size());
        }
    }

    @Test
    public void createListDialog_failureReenablesSaveAndKeepsDialogOpen() throws Exception {
        Activity act = activity();
        AtomicBoolean createdCalled = new AtomicBoolean(false);
        try (MockedStatic<UserListsGraphQL> mocked = Mockito.mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList("Brand New", false))
                    .thenReturn(Single.error(new java.io.IOException("test failure")));
            UserListDialogHelper.showCreateListDialog(act, twoLists(),
                    new CompositeDisposable(), list -> createdCalled.set(true));
            idleMain();
            AlertDialog dialog = shownDialog();
            EditText input = dialog.findViewById(R.id.et_list_name);
            input.setText("Brand New");
            idleMain();
            Button save = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            save.performClick();
            assertFalse(save.isEnabled());
            for (int i = 0; i < 100 && !save.isEnabled(); i++) {
                idleMain();
                Thread.sleep(50);
            }
            assertTrue(save.isEnabled());
            assertTrue(dialog.isShowing());
            assertFalse(createdCalled.get());
            assertEquals(act.getString(R.string.star_list_create_failed),
                    ShadowToast.getTextOfLatestToast());
        }
    }
}
