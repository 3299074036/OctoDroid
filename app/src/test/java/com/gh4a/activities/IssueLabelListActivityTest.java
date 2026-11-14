package com.gh4a.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.adapter.IssueLabelAdapter;
import com.meisolsson.githubsdk.model.Label;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.service.issues.IssueLabelService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.util.ArrayList;
import java.util.List;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.idleMain;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static com.gh4a.activities.ActivityTestHelper.waitFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.Single;
import retrofit2.Response;

@RunWith(RobolectricTestRunner.class)
public class IssueLabelListActivityTest {
    private Intent intent(boolean fromPr) {
        return IssueLabelListActivity.makeIntent(appContext(), "o", "r", fromPr);
    }

    @Test
    public void makeIntent_setsExtras() {
        Intent i = intent(true);
        assertEquals(IssueLabelListActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertTrue(i.getBooleanExtra("from_pr", false));
    }

    @Test
    public void launch_titleSubtitleAndNoSwipe() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        assertEquals(appContext().getString(R.string.issue_labels),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
        assertFalse(activity.canSwipeToRefresh());
    }

    @Test
    public void navigateUp_prMode_goesToPrList() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(true));
        Intent up = activity.navigateUp();
        assertEquals(IssueListActivity.class.getName(), up.getComponent().getClassName());
        assertTrue(up.getBooleanExtra("is_pull_request", false));
    }

    @Test
    public void navigateUp_issueMode_goesToIssueList() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        Intent up = activity.navigateUp();
        assertFalse(up.getBooleanExtra("is_pull_request", true));
    }

    @Test
    public void addOrGetNewLabelItem_createsAndReusesNewLabel() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel first = activity.addOrGetNewLabelItem();
        assertTrue(first.newlyAdded);
        // "dddddd" is the new label's color, not its name (name is null
        // until the user types one)
        assertNull(first.name());
        assertEquals("dddddd", first.color());
        IssueLabelAdapter.EditableLabel second = activity.addOrGetNewLabelItem();
        assertTrue(first == second);
    }

    @Test
    public void onItemClick_startsEditing() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        activity.onItemClick(label);
        assertTrue(get(activity, "mActionMode") != null);
        assertTrue(label.isEditing);
    }

    @Test
    public void onItemClick_whileEditing_doesNotRestart() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        activity.onItemClick(label);
        Object mode = get(activity, "mActionMode");
        activity.onItemClick(label);
        assertTrue(get(activity, "mActionMode") == mode);
    }

    @Test
    public void onClick_fab_startsEditingNewLabel() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        activity.onClick(activity.findViewById(R.id.list));
        assertTrue(get(activity, "mActionMode") != null);
    }

    @Test
    public void editActionMode_createMenu_newLabelHasSaveOnly() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        activity.onItemClick(label);
        Object mode = get(activity, "mActionMode");
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        boolean created = (boolean) com.gh4a.activities.ActivityTestHelper.call(
                mode, "onCreateActionMode",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class, android.view.Menu.class},
                new Object[]{null, menu});
        assertTrue(created);
        assertTrue(menu.findItem(android.view.Menu.FIRST) != null);
        assertNull(menu.findItem(android.view.Menu.FIRST + 1));
    }

    @Test
    public void editActionMode_createMenu_existingLabelHasSaveAndDelete() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        Label base = Label.builder().name("bug").color("d73a4a").build();
        IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(base);
        ((IssueLabelAdapter) get(activity, "mAdapter")).add(label);
        activity.onItemClick(label);
        Object mode = get(activity, "mActionMode");
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        com.gh4a.activities.ActivityTestHelper.call(mode, "onCreateActionMode",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class, android.view.Menu.class},
                new Object[]{null, menu});
        assertTrue(menu.findItem(android.view.Menu.FIRST) != null);
        assertTrue(menu.findItem(android.view.Menu.FIRST + 1) != null);
    }

    @Test
    public void editActionMode_saveEmptyName_toastsAndStaysEditing() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        label.editedName = "";
        activity.onItemClick(label);
        Object mode = get(activity, "mActionMode");
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        com.gh4a.activities.ActivityTestHelper.call(mode, "onCreateActionMode",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class, android.view.Menu.class},
                new Object[]{null, menu});
        MenuItem save = menu.findItem(android.view.Menu.FIRST);
        boolean handled = (boolean) com.gh4a.activities.ActivityTestHelper.call(
                mode, "onActionItemClicked",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class, android.view.MenuItem.class},
                new Object[]{mock(androidx.appcompat.view.ActionMode.class), save});
        assertTrue(handled);
        assertEquals(appContext().getString(R.string.issue_label_empty_name),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertTrue(get(activity, "mActionMode") != null);
    }

    @Test
    public void editActionMode_destroyRemovesNewLabel() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        activity.onItemClick(label);
        Object mode = get(activity, "mActionMode");
        com.gh4a.activities.ActivityTestHelper.call(mode, "onDestroyActionMode",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class},
                new Object[]{mock(androidx.appcompat.view.ActionMode.class)});
        assertNull(get(activity, "mActionMode"));
        assertFalse(label.isEditing);
        assertEquals(0, ((IssueLabelAdapter) get(activity, "mAdapter")).getCount());
    }

    @Test
    public void onSaveInstanceState_storesEditingLabel() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        activity.onItemClick(label);
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        IssueLabelAdapter.EditableLabel back =
                out.getParcelable("editing_label");
        assertTrue(back != null);
        assertTrue(back.newlyAdded);
        assertEquals("dddddd", back.color());
    }

    @Test
    public void onConfirmed_deleteLabel_callsService() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        when(svc.deleteLabel(eq("o"), eq("r"), eq("bug")))
                .thenReturn(Single.just(Response.success(
                        Label.builder().name("bug").color("d73a4a").build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            Label base = Label.builder().name("bug").color("d73a4a").build();
            IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(base);
            activity.onConfirmed("deleteconfirm", label);
            verify(svc, timeout(5000)).deleteLabel(eq("o"), eq("r"), eq("bug"));
        }
    }

    @Test
    public void loadLabels_mockedService_populatesAdapter() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        List<Label> page = new ArrayList<>();
        page.add(Label.builder().name("bug").color("d73a4a").build());
        page.add(Label.builder().name("enhancement").color("a2eeef").build());
        when(svc.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<Label>builder().items(page).build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> ((IssueLabelAdapter) get(activity, "mAdapter")).getCount() == 2, 10000);
            assertEquals(2, ((IssueLabelAdapter) get(activity, "mAdapter")).getCount());
        }
    }

    // ---- helpers for the gap-filling tests below ----

    private IssueLabelAdapter adapter(IssueLabelListActivity activity) {
        return (IssueLabelAdapter) get(activity, "mAdapter");
    }

    private void stubLabels(IssueLabelService svc, Label... labels) {
        List<Label> page = new ArrayList<>();
        for (Label l : labels) {
            page.add(l);
        }
        when(svc.getRepositoryLabels(eq("o"), eq("r"), anyLong()))
                .thenReturn(Single.just(Response.success(Page.<Label>builder().items(page).build())));
    }

    private Object startEditing(IssueLabelListActivity activity,
            IssueLabelAdapter.EditableLabel label) {
        activity.onItemClick(label);
        return get(activity, "mActionMode");
    }

    private androidx.appcompat.view.menu.MenuBuilder menuForMode(Object mode) {
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        call(mode, "onCreateActionMode",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class, android.view.Menu.class},
                new Object[]{null, menu});
        return menu;
    }

    private boolean clickMenuItem(Object mode, androidx.appcompat.view.ActionMode mockMode,
            MenuItem item) {
        return (boolean) call(mode, "onActionItemClicked",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class, android.view.MenuItem.class},
                new Object[]{mockMode, item});
    }

    private void authorize() {
        Gh4Application.get().addAccount(
                com.meisolsson.githubsdk.model.User.builder().login("tester").id(1L).build(), "tok");
        if (!Gh4Application.get().isAuthorized()) {
            Gh4Application.get().addAccount(
                    com.meisolsson.githubsdk.model.User.builder().login("tester").id(1L).build(),
                    "tok");
        }
    }

    @Test
    public void onCreate_withPendingNewLabel_restoresEditingAfterLoad() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc);
        IssueLabelAdapter.EditableLabel pending =
                new IssueLabelAdapter.EditableLabel("dddddd");
        pending.editedName = "mylabel";
        pending.editedColor = "ff0000";
        Bundle saved = new Bundle();
        saved.putParcelable("editing_label", pending);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = controller(IssueLabelListActivity.class, intent(false))
                    .create(saved).start().resume().get();
            waitFor(() -> get(activity, "mActionMode") != null, 10000);
            assertNull(get(activity, "mPendingEditingLabel"));
            assertEquals(1, adapter(activity).getCount());
            IssueLabelAdapter.EditableLabel item = adapter(activity).getItem(0);
            assertTrue(item.isEditing);
            assertEquals("mylabel", item.editedName);
            assertEquals("ff0000", item.editedColor);
        }
    }

    @Test
    public void onCreate_withPendingExistingLabel_restoresEditingByBaseName() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        Label bug = Label.builder().name("bug").color("d73a4a").build();
        stubLabels(svc, bug, Label.builder().name("enhancement").color("a2eeef").build());
        IssueLabelAdapter.EditableLabel pending = new IssueLabelAdapter.EditableLabel(bug);
        pending.editedName = "bugfix";
        pending.editedColor = "111111";
        Bundle saved = new Bundle();
        saved.putParcelable("editing_label", pending);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = controller(IssueLabelListActivity.class, intent(false))
                    .create(saved).start().resume().get();
            waitFor(() -> get(activity, "mActionMode") != null, 10000);
            IssueLabelAdapter.EditableLabel item = adapter(activity).getItem(0);
            assertTrue(item.isEditing);
            assertEquals("bugfix", item.editedName);
            assertEquals("111111", item.editedColor);
            assertFalse(adapter(activity).getItem(1).isEditing);
        }
    }

    @Test
    public void onCreate_withPendingLabelNoMatch_doesNotStartEditing() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        Label ghost = Label.builder().name("zzz").color("000000").build();
        IssueLabelAdapter.EditableLabel pending = new IssueLabelAdapter.EditableLabel(ghost);
        pending.editedName = "zzz2";
        Bundle saved = new Bundle();
        saved.putParcelable("editing_label", pending);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = controller(IssueLabelListActivity.class, intent(false))
                    .create(saved).start().resume().get();
            waitFor(() -> get(activity, "mPendingEditingLabel") == null
                    && adapter(activity).getCount() == 1, 10000);
            assertNull(get(activity, "mActionMode"));
        }
    }

    @Test
    public void onCreate_withPendingLabelNullBase_doesNotStartEditing() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        IssueLabelAdapter.EditableLabel pending =
                new IssueLabelAdapter.EditableLabel((Label) null);
        Bundle saved = new Bundle();
        saved.putParcelable("editing_label", pending);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = controller(IssueLabelListActivity.class, intent(false))
                    .create(saved).start().resume().get();
            waitFor(() -> get(activity, "mPendingEditingLabel") == null
                    && adapter(activity).getCount() == 1, 10000);
            assertNull(get(activity, "mActionMode"));
        }
    }

    @Test
    public void onRefresh_reloadsLabels() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build(),
                Label.builder().name("enhancement").color("a2eeef").build());
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 2, 10000);
            activity.onRefresh();
            waitFor(() -> adapter(activity).getCount() == 2, 10000);
            verify(svc, times(2)).getRepositoryLabels(eq("o"), eq("r"), anyLong());
        }
    }

    @Test
    public void onSaveInstanceState_withoutEditing_storesNothing() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertFalse(out.containsKey("editing_label"));
    }

    @Test
    public void onClick_whileEditing_doesNothing() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        Object mode = startEditing(activity, label);
        int count = adapter(activity).getCount();
        activity.onClick(activity.findViewById(R.id.list));
        assertTrue(get(activity, "mActionMode") == mode);
        assertEquals(count, adapter(activity).getCount());
    }

    @Test
    public void addOrGetNewLabelItem_skipsExistingLabels() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel first = new IssueLabelAdapter.EditableLabel(
                Label.builder().name("bug").color("d73a4a").build());
        IssueLabelAdapter.EditableLabel second = new IssueLabelAdapter.EditableLabel(
                Label.builder().name("enhancement").color("a2eeef").build());
        adapter(activity).add(first);
        adapter(activity).add(second);
        IssueLabelAdapter.EditableLabel created = activity.addOrGetNewLabelItem();
        assertTrue(created.newlyAdded);
        assertTrue(created != first && created != second);
        assertEquals(3, adapter(activity).getCount());
    }

    @Test
    public void updateFabVisibility_authorized_showsFab() {
        authorize();
        assertTrue(Gh4Application.get().isAuthorized());
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        assertEquals(View.VISIBLE, ((View) get(activity, "mFab")).getVisibility());
    }

    @Test
    public void updateFabVisibility_authorizedWhileEditing_hidesFab() {
        authorize();
        assertTrue(Gh4Application.get().isAuthorized());
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        startEditing(activity, label);
        assertEquals(View.GONE, ((View) get(activity, "mFab")).getVisibility());
    }

    @Test
    public void editActionMode_saveNewLabel_callsCreateService() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.createLabel(eq("o"), eq("r"), any()))
                .thenReturn(Single.just(Response.success(
                        Label.builder().name("newlabel").color("dddddd").build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
            label.editedName = "newlabel";
            Object mode = startEditing(activity, label);
            androidx.appcompat.view.menu.MenuBuilder menu = menuForMode(mode);
            androidx.appcompat.view.ActionMode mockMode =
                    mock(androidx.appcompat.view.ActionMode.class);
            assertTrue(clickMenuItem(mode, mockMode, menu.findItem(android.view.Menu.FIRST)));
            verify(svc, timeout(5000)).createLabel(eq("o"), eq("r"),
                    eq(Label.builder().name("newlabel").color("dddddd").build()));
            verify(mockMode, timeout(5000)).finish();
            waitFor(() -> Shadows.shadowOf(activity).getResultCode() == Activity.RESULT_OK, 10000);
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void editActionMode_saveExistingLabel_callsEditService() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.editLabel(eq("o"), eq("r"), eq("bug"), any()))
                .thenReturn(Single.just(Response.success(
                        Label.builder().name("bugfix").color("000000").build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            IssueLabelAdapter.EditableLabel label = adapter(activity).getItem(0);
            label.editedName = "bugfix";
            label.editedColor = "000000";
            Object mode = startEditing(activity, label);
            androidx.appcompat.view.menu.MenuBuilder menu = menuForMode(mode);
            androidx.appcompat.view.ActionMode mockMode =
                    mock(androidx.appcompat.view.ActionMode.class);
            assertTrue(clickMenuItem(mode, mockMode, menu.findItem(android.view.Menu.FIRST)));
            verify(svc, timeout(5000)).editLabel(eq("o"), eq("r"), eq("bug"),
                    eq(Label.builder().name("bugfix").color("000000").build()));
            verify(mockMode, timeout(5000)).finish();
            waitFor(() -> Shadows.shadowOf(activity).getResultCode() == Activity.RESULT_OK, 10000);
        }
    }

    @Test
    public void editActionMode_deleteExistingLabel_showsConfirmation() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(
                Label.builder().name("bug").color("d73a4a").build());
        adapter(activity).add(label);
        Object mode = startEditing(activity, label);
        androidx.appcompat.view.menu.MenuBuilder menu = menuForMode(mode);
        androidx.appcompat.view.ActionMode mockMode =
                mock(androidx.appcompat.view.ActionMode.class);
        assertTrue(clickMenuItem(mode, mockMode, menu.findItem(android.view.Menu.FIRST + 1)));
        idleMain(200);
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("deleteconfirm"));
        verify(mockMode).finish();
    }

    @Test
    public void editActionMode_unknownItem_finishesMode() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
        label.editedName = "x";
        Object mode = startEditing(activity, label);
        MenuItem unknown = mock(MenuItem.class);
        when(unknown.getItemId()).thenReturn(9999);
        androidx.appcompat.view.ActionMode mockMode =
                mock(androidx.appcompat.view.ActionMode.class);
        assertTrue(clickMenuItem(mode, mockMode, unknown));
        verify(mockMode).finish();
    }

    @Test
    public void editActionMode_destroyRestoresExistingLabel() {
        IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
        IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(
                Label.builder().name("bug").color("d73a4a").build());
        adapter(activity).add(label);
        label.editedName = "bugfix";
        label.editedColor = "000000";
        Object mode = startEditing(activity, label);
        call(mode, "onDestroyActionMode",
                new Class<?>[]{androidx.appcompat.view.ActionMode.class},
                new Object[]{mock(androidx.appcompat.view.ActionMode.class)});
        assertNull(get(activity, "mActionMode"));
        assertFalse(label.isEditing);
        assertEquals("bug", label.name());
        assertEquals("d73a4a", label.color());
        assertEquals(1, adapter(activity).getCount());
    }

    @Test
    public void onConfirmed_deleteLabel_successReloadsAndSetsResult() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.deleteLabel(eq("o"), eq("r"), eq("bug")))
                .thenReturn(Single.just(Response.success(
                        Label.builder().name("bug").color("d73a4a").build())));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            Label base = Label.builder().name("bug").color("d73a4a").build();
            IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(base);
            activity.onConfirmed("deleteconfirm", label);
            verify(svc, timeout(5000)).deleteLabel(eq("o"), eq("r"), eq("bug"));
            waitFor(() -> Shadows.shadowOf(activity).getResultCode() == Activity.RESULT_OK, 10000);
            assertEquals(1, adapter(activity).getCount());
        }
    }

    @Test
    public void deleteLabel_emptyBody_showsError_A1_5() throws Exception {
        // A1-5: GitHub's DELETE label endpoint answers 204 No Content, so a
        // real success has a null body; map(ApiHelpers::throwOnFailure) then
        // returns null and RxJava's map rejects it with an NPE, so the
        // success callback (reload + RESULT_OK) never runs and the user sees
        // a failure snackbar even though the label was deleted server-side.
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.deleteLabel(eq("o"), eq("r"), eq("bug")))
                .thenReturn(Single.just(Response.success(null)));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            Label base = Label.builder().name("bug").color("d73a4a").build();
            IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(base);
            activity.onConfirmed("deleteconfirm", label);
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            assertNotNull(activity.findViewById(
                    com.google.android.material.R.id.snackbar_text));
            assertEquals(Activity.RESULT_CANCELED,
                    Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void applyPendingEditedLabel_skipsNullNameItem() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name(null).color("a2eeef").build(),
                Label.builder().name("bug").color("d73a4a").build());
        Label bug = Label.builder().name("bug").color("d73a4a").build();
        IssueLabelAdapter.EditableLabel pending = new IssueLabelAdapter.EditableLabel(bug);
        pending.editedName = "bugfix";
        Bundle saved = new Bundle();
        saved.putParcelable("editing_label", pending);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = controller(IssueLabelListActivity.class, intent(false))
                    .create(saved).start().resume().get();
            waitFor(() -> get(activity, "mActionMode") != null, 10000);
            assertEquals("bugfix", adapter(activity).getItem(1).editedName);
            assertTrue(adapter(activity).getItem(1).isEditing);
        }
    }

    @Test
    public void deleteLabel_error_showsRetrySnackbarAndNoResult() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.deleteLabel(eq("o"), eq("r"), eq("bug")))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            Label base = Label.builder().name("bug").color("d73a4a").build();
            IssueLabelAdapter.EditableLabel label = new IssueLabelAdapter.EditableLabel(base);
            activity.onConfirmed("deleteconfirm", label);
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            // The retry snackbar times out, the error propagates to the
            // terminal error handler (line 310); no reload, no RESULT_OK.
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) == null, 10000);
            assertEquals(Activity.RESULT_CANCELED,
                    Shadows.shadowOf(activity).getResultCode());
            assertEquals(1, adapter(activity).getCount());
        }
    }

    @Test
    public void editLabel_error_showsRetrySnackbarAndNoResult() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.editLabel(eq("o"), eq("r"), eq("bug"), any()))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            IssueLabelAdapter.EditableLabel label = adapter(activity).getItem(0);
            label.editedName = "bugfix";
            Object mode = startEditing(activity, label);
            androidx.appcompat.view.menu.MenuBuilder menu = menuForMode(mode);
            androidx.appcompat.view.ActionMode mockMode =
                    mock(androidx.appcompat.view.ActionMode.class);
            assertTrue(clickMenuItem(mode, mockMode, menu.findItem(android.view.Menu.FIRST)));
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) == null, 10000);
            assertEquals(Activity.RESULT_CANCELED,
                    Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void addLabel_error_showsRetrySnackbarAndNoResult() throws Exception {
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, Label.builder().name("bug").color("d73a4a").build());
        when(svc.createLabel(eq("o"), eq("r"), any()))
                .thenReturn(Single.error(new java.io.IOException("boom")));
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.get(IssueLabelService.class, false)).thenReturn(svc);
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = launch(IssueLabelListActivity.class, intent(false));
            waitFor(() -> adapter(activity).getCount() == 1, 10000);
            IssueLabelAdapter.EditableLabel label = activity.addOrGetNewLabelItem();
            label.editedName = "newlabel";
            Object mode = startEditing(activity, label);
            androidx.appcompat.view.menu.MenuBuilder menu = menuForMode(mode);
            androidx.appcompat.view.ActionMode mockMode =
                    mock(androidx.appcompat.view.ActionMode.class);
            assertTrue(clickMenuItem(mode, mockMode, menu.findItem(android.view.Menu.FIRST)));
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) == null, 10000);
            assertEquals(Activity.RESULT_CANCELED,
                    Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void applyPendingEditedLabel_skipsNullBaseItem() throws Exception {
        // Degenerate page containing a null item: mapList wraps it in an
        // EditableLabel with a null base, so the match loop takes the
        // `itemBase != null` false exit (line 286) before matching "bug".
        IssueLabelService svc = mock(IssueLabelService.class);
        stubLabels(svc, null, Label.builder().name("bug").color("d73a4a").build());
        Label bug = Label.builder().name("bug").color("d73a4a").build();
        IssueLabelAdapter.EditableLabel pending = new IssueLabelAdapter.EditableLabel(bug);
        pending.editedName = "bugfix";
        Bundle saved = new Bundle();
        saved.putParcelable("editing_label", pending);
        try (MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(ServiceFactory.class)) {
            mocked.when(() -> ServiceFactory.getForFullPagedLists(IssueLabelService.class, false))
                    .thenReturn(svc);
            IssueLabelListActivity activity = controller(IssueLabelListActivity.class, intent(false))
                    .create(saved).start().resume().get();
            waitFor(() -> get(activity, "mActionMode") != null, 10000);
            assertEquals("bugfix", adapter(activity).getItem(1).editedName);
            assertTrue(adapter(activity).getItem(1).isEditing);
        }
    }
}
