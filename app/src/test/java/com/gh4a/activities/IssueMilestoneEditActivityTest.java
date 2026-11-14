package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.meisolsson.githubsdk.model.IssueState;
import com.meisolsson.githubsdk.model.Milestone;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.Date;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.dismissCurrentSnackbarAsTimeout;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.logContains;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class IssueMilestoneEditActivityTest {
    private void authorize() {
        Gh4Application.get().addAccount(User.builder().login("tester").id(1L).build(), "tok");
    }

    private Milestone milestone(String title, IssueState state, Date dueOn) {
        Milestone.Builder b = Milestone.builder().title(title).state(state);
        if (dueOn != null) {
            b.dueOn(dueOn);
        }
        return b.build();
    }

    @Test
    public void makeCreateIntent_setsExtras() {
        Intent i = IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", true);
        assertEquals(IssueMilestoneEditActivity.class.getName(), i.getComponent().getClassName());
        assertEquals("o", i.getStringExtra("owner"));
        assertEquals("r", i.getStringExtra("repo"));
        assertTrue(i.getBooleanExtra("from_pr", false));
        assertFalse(i.hasExtra("milestone"));
    }

    @Test
    public void makeEditIntent_setsMilestoneExtra() {
        Milestone m = milestone("v1", IssueState.Open, null);
        Intent i = IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false);
        assertEquals(m, i.getParcelableExtra("milestone"));
    }

    @Test
    public void launch_unauthorized_redirectsToLoginAndFinishes() {
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", false));
        assertTrue(activity.isFinishing());
        Intent next = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(Github4AndroidActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void launch_createMode_titleIsNewMilestone() {
        authorize();
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", false));
        assertFalse(activity.isFinishing());
        assertEquals(appContext().getString(R.string.issue_milestone_new),
                activity.getSupportActionBar().getTitle().toString());
        assertEquals("o/r", activity.getSupportActionBar().getSubtitle().toString());
    }

    @Test
    public void launch_editMode_titleIsEditMilestone() {
        authorize();
        Milestone m = milestone("v1", IssueState.Open, null);
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false));
        assertEquals(appContext().getString(R.string.issue_milestone_edit),
                activity.getSupportActionBar().getTitle().toString());
    }

    @Test
    public void navigateUp_goesToMilestoneList() {
        authorize();
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", true));
        Intent up = activity.navigateUp();
        assertEquals(IssueMilestoneListActivity.class.getName(), up.getComponent().getClassName());
        assertTrue(up.getBooleanExtra("from_pr", false));
    }

    @Test
    public void onSaveInstanceState_storesDueOnAndOpen() {
        authorize();
        Calendar cal = Calendar.getInstance();
        cal.set(2026, Calendar.NOVEMBER, 10, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Milestone m = milestone("v1", IssueState.Open, cal.getTime());
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false));
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertEquals(cal.getTimeInMillis(), out.getLong("due_on", -1));
        assertTrue(out.getBoolean("is_open", false));
    }

    @Test
    public void onSaveInstanceState_closedMilestone_storesIsOpenFalse() {
        authorize();
        Milestone m = milestone("v1", IssueState.Closed, null);
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false));
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertFalse(out.containsKey("due_on"));
        assertFalse(out.getBoolean("is_open", true));
    }

    @Test
    public void onCreate_restoresDueOnAndOpenFromSavedState() {
        authorize();
        Milestone m = milestone("v1", IssueState.Closed, null);
        Intent intent = IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false);
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class, intent);
        // simulate a rotation that changed due date + reopened the milestone
        set(activity, "mMilestone", milestone("v1", IssueState.Open,
                new Date(1790000000000L)));
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        IssueMilestoneEditActivity recreated =
                com.gh4a.activities.ActivityTestHelper.controller(IssueMilestoneEditActivity.class, intent)
                        .create(out).start().resume().get();
        Milestone restored = (Milestone) get(recreated, "mMilestone");
        assertEquals(IssueState.Open, restored.state());
        assertEquals(1790000000000L, restored.dueOn().getTime());
    }

    @Test
    public void setDueOn_andResetDueOn_updateMilestone() {
        authorize();
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", false));
        call(activity, "setDueOn",
                new Class<?>[]{int.class, int.class, int.class}, new Object[]{2027, 0, 15});
        Milestone m = (Milestone) get(activity, "mMilestone");
        Calendar cal = Calendar.getInstance();
        cal.setTime(m.dueOn());
        assertEquals(2027, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
        call(activity, "resetDueOn", new Class<?>[]{}, new Object[]{});
        assertEquals(null, ((Milestone) get(activity, "mMilestone")).dueOn());
    }

    @Test
    public void onCreateOptionsMenu_editOpenMilestone_showsCloseOnly() {
        authorize();
        Milestone m = milestone("v1", IssueState.Open, null);
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(menu.findItem(R.id.milestone_close) != null);
        assertNull(menu.findItem(R.id.milestone_reopen));
    }

    @Test
    public void onCreateOptionsMenu_editClosedMilestone_showsReopenOnly() {
        authorize();
        Milestone m = milestone("v1", IssueState.Closed, null);
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertNull(menu.findItem(R.id.milestone_close));
        assertTrue(menu.findItem(R.id.milestone_reopen) != null);
    }

    @Test
    public void onCreateOptionsMenu_createMode_noMilestoneMenu() {
        authorize();
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", false));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertNull(menu.findItem(R.id.milestone_close));
        assertNull(menu.findItem(R.id.milestone_reopen));
        assertNull(menu.findItem(R.id.delete));
    }

    @Test
    public void onFocusChange_titleFocus_togglesMarkdownButtons() {
        authorize();
        IssueMilestoneEditActivity activity = launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", false));
        android.view.View title = activity.findViewById(R.id.et_title);
        android.view.View buttons = activity.findViewById(R.id.markdown_buttons);
        activity.onFocusChange(title, true);
        assertEquals(android.view.View.GONE, buttons.getVisibility());
        activity.onFocusChange(title, false);
        assertEquals(android.view.View.VISIBLE, buttons.getVisibility());
    }

    // ---------- gap closers ----------

    private Milestone milestoneWithNumber(String title, IssueState state, Date dueOn, int number) {
        Milestone.Builder b = Milestone.builder().title(title).state(state).number(number);
        if (dueOn != null) {
            b.dueOn(dueOn);
        }
        return b.build();
    }

    private org.mockito.MockedStatic<com.gh4a.ServiceFactory> mockServiceFactory(
            com.meisolsson.githubsdk.service.issues.IssueMilestoneService service) {
        org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                        org.mockito.Mockito.withSettings()
                                .defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));
        mocked.when(() -> com.gh4a.ServiceFactory.get(
                        org.mockito.ArgumentMatchers.eq(
                                com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class),
                        org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(service);
        return mocked;
    }

    private IssueMilestoneEditActivity launchCreate() {
        authorize();
        return launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeCreateIntent(appContext(), "o", "r", false));
    }

    private IssueMilestoneEditActivity launchEdit(Milestone m) {
        authorize();
        return launch(IssueMilestoneEditActivity.class,
                IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false));
    }

    private void setTitle(IssueMilestoneEditActivity activity, String title) {
        ((android.widget.EditText) activity.findViewById(R.id.et_title)).setText(title);
    }

    private IssueMilestoneEditActivity.DatePickerFragment showDatePicker(
            IssueMilestoneEditActivity activity) {
        activity.onClick(activity.findViewById(R.id.due_container));
        activity.getSupportFragmentManager().executePendingTransactions();
        return (IssueMilestoneEditActivity.DatePickerFragment) activity.getSupportFragmentManager()
                .findFragmentByTag("datePicker");
    }

    @Test
    public void onCreate_restore_openMilestoneWithDueOn() {
        authorize();
        Milestone m = milestone("v1", IssueState.Open, null);
        Intent intent = IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false);
        Bundle saved = new Bundle();
        saved.putLong("due_on", 1790000000000L);
        saved.putBoolean("is_open", true);
        IssueMilestoneEditActivity recreated =
                com.gh4a.activities.ActivityTestHelper.controller(IssueMilestoneEditActivity.class, intent)
                        .create(saved).start().resume().get();
        Milestone restored = (Milestone) get(recreated, "mMilestone");
        assertEquals(IssueState.Open, restored.state());
        assertEquals(1790000000000L, restored.dueOn().getTime());
    }

    @Test
    public void onCreate_restore_closedMilestoneNoDueOn() {
        authorize();
        Milestone m = milestone("v1", IssueState.Open, new Date(1790000000000L));
        Intent intent = IssueMilestoneEditActivity.makeEditIntent(appContext(), "o", "r", m, false);
        Bundle saved = new Bundle();
        saved.putBoolean("is_open", false);
        IssueMilestoneEditActivity recreated =
                com.gh4a.activities.ActivityTestHelper.controller(IssueMilestoneEditActivity.class, intent)
                        .create(saved).start().resume().get();
        Milestone restored = (Milestone) get(recreated, "mMilestone");
        assertEquals(IssueState.Closed, restored.state());
        assertNull(restored.dueOn());
    }

    @Test
    public void onSaveInstanceState_nullMilestone_storesNothing() {
        IssueMilestoneEditActivity activity = launchCreate();
        set(activity, "mMilestone", null);
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertFalse(out.containsKey("due_on"));
        assertFalse(out.containsKey("is_open"));
    }

    @Test
    public void onClick_dueContainer_showsDatePicker() {
        IssueMilestoneEditActivity activity = launchCreate();
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        assertNotNull(fragment);
        assertNotNull(fragment.getDialog());
    }

    @Test
    public void datePicker_onDateSet_setsDueOn() {
        IssueMilestoneEditActivity activity = launchCreate();
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        fragment.onDateSet(null, 2027, 0, 15);
        Milestone m = (Milestone) get(activity, "mMilestone");
        Calendar cal = Calendar.getInstance();
        cal.setTime(m.dueOn());
        assertEquals(2027, cal.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH));
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void datePicker_onDateSet_whenStopping_ignored() {
        IssueMilestoneEditActivity activity = launchCreate();
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        set(fragment, "mStopping", true);
        fragment.onDateSet(null, 2027, 0, 15);
        assertNull(((Milestone) get(activity, "mMilestone")).dueOn());
    }

    @Test
    public void datePicker_onClick_neutral_resetsDueOn() {
        IssueMilestoneEditActivity activity = launchCreate();
        call(activity, "setDueOn",
                new Class<?>[]{int.class, int.class, int.class}, new Object[]{2027, 0, 15});
        assertNotNull(((Milestone) get(activity, "mMilestone")).dueOn());
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        fragment.onClick(org.mockito.Mockito.mock(android.content.DialogInterface.class),
                android.content.DialogInterface.BUTTON_NEUTRAL);
        assertNull(((Milestone) get(activity, "mMilestone")).dueOn());
    }

    @Test
    public void datePicker_onClick_nonNeutral_doesNothing() {
        IssueMilestoneEditActivity activity = launchCreate();
        call(activity, "setDueOn",
                new Class<?>[]{int.class, int.class, int.class}, new Object[]{2027, 0, 15});
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        fragment.onClick(org.mockito.Mockito.mock(android.content.DialogInterface.class),
                android.content.DialogInterface.BUTTON_POSITIVE);
        assertNotNull(((Milestone) get(activity, "mMilestone")).dueOn());
    }

    @Test
    public void datePicker_onCreateDialog_withDueOn_usesDueDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(2027, Calendar.APRIL, 20, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Milestone m = milestone("v1", IssueState.Open, cal.getTime());
        IssueMilestoneEditActivity activity = launchEdit(m);
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        android.app.DatePickerDialog dialog = (android.app.DatePickerDialog) fragment.getDialog();
        assertEquals(2027, dialog.getDatePicker().getYear());
        assertEquals(Calendar.APRIL, dialog.getDatePicker().getMonth());
        assertEquals(20, dialog.getDatePicker().getDayOfMonth());
    }

    @Test
    public void datePicker_onCreateDialog_withoutDueOn_usesToday() {
        IssueMilestoneEditActivity activity = launchCreate();
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        android.app.DatePickerDialog dialog = (android.app.DatePickerDialog) fragment.getDialog();
        Calendar today = Calendar.getInstance();
        assertEquals(today.get(Calendar.YEAR), dialog.getDatePicker().getYear());
        assertEquals(today.get(Calendar.MONTH), dialog.getDatePicker().getMonth());
        assertEquals(today.get(Calendar.DAY_OF_MONTH), dialog.getDatePicker().getDayOfMonth());
    }

    @Test
    public void datePickerDialog_onStop_resetsStopping() {
        IssueMilestoneEditActivity activity = launchCreate();
        IssueMilestoneEditActivity.DatePickerFragment fragment = showDatePicker(activity);
        android.app.Dialog dialog = fragment.getDialog();
        call(dialog, "onStop", new Class<?>[]{}, new Object[]{});
        assertFalse((boolean) get(fragment, "mStopping"));
    }

    @Test
    public void onClick_saveFab_createMode_savesAndFinishes() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        Milestone saved = milestoneWithNumber("v2", IssueState.Open, null, 99);
        org.mockito.ArgumentCaptor<com.meisolsson.githubsdk.model.request.issue.CreateMilestone> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.meisolsson.githubsdk.model.request.issue.CreateMilestone.class);
        org.mockito.Mockito.when(service.createMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        captor.capture()))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(saved)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            IssueMilestoneEditActivity activity = launchCreate();
            setTitle(activity, "v2");
            activity.onClick(get(activity, "mSaveFab") instanceof android.view.View
                    ? (android.view.View) get(activity, "mSaveFab") : null);
            com.gh4a.activities.ActivityTestHelper.waitFor(activity::isFinishing, 5000);
            assertTrue(activity.isFinishing());
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            assertEquals("v2", captor.getValue().title());
            assertEquals(IssueState.Open, captor.getValue().state());
        }
    }

    @Test
    public void onClick_saveFab_editMode_editsAndFinishes() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        Milestone saved = milestoneWithNumber("v1-renamed", IssueState.Open, null, 42);
        org.mockito.Mockito.when(service.editMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(saved)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Open, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            setTitle(activity, "v1-renamed");
            activity.onClick((android.view.View) get(activity, "mSaveFab"));
            com.gh4a.activities.ActivityTestHelper.waitFor(activity::isFinishing, 5000);
            assertTrue(activity.isFinishing());
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            org.mockito.Mockito.verify(service).editMilestone(
                    org.mockito.ArgumentMatchers.eq("o"),
                    org.mockito.ArgumentMatchers.eq("r"),
                    org.mockito.ArgumentMatchers.eq(42L),
                    org.mockito.ArgumentMatchers.any());
        }
    }

    @Test
    public void onClick_saveFab_error_doesNotFinish() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        org.mockito.Mockito.when(service.createMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.error(new java.io.IOException("boom")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            IssueMilestoneEditActivity activity = launchCreate();
            setTitle(activity, "v2");
            activity.onClick((android.view.View) get(activity, "mSaveFab"));
            com.gh4a.activities.ActivityTestHelper.waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            assertNotNull(activity.findViewById(
                    com.google.android.material.R.id.snackbar_text));
            dismissCurrentSnackbarAsTimeout();
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> logContains(com.gh4a.Gh4Application.LOG_TAG, "Saving milestone failed"), 5000);
            assertTrue(logContains(com.gh4a.Gh4Application.LOG_TAG, "Saving milestone failed"));
            assertFalse(activity.isFinishing());
            assertEquals(android.app.Activity.RESULT_CANCELED,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void onClick_otherView_doesNothing() {
        IssueMilestoneEditActivity activity = launchCreate();
        activity.onClick(activity.findViewById(R.id.et_title));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onFocusChange_nonTitleView_doesNothing() {
        IssueMilestoneEditActivity activity = launchCreate();
        android.view.View buttons = activity.findViewById(R.id.markdown_buttons);
        assertEquals(android.view.View.VISIBLE, buttons.getVisibility());
        activity.onFocusChange(activity.findViewById(R.id.editor), true);
        assertEquals(android.view.View.VISIBLE, buttons.getVisibility());
    }

    @Test
    public void onOffsetChanged_setsBottomPadding() {
        IssueMilestoneEditActivity activity = launchCreate();
        com.google.android.material.appbar.AppBarLayout appBar =
                org.mockito.Mockito.mock(com.google.android.material.appbar.AppBarLayout.class);
        org.mockito.Mockito.when(appBar.getTotalScrollRange()).thenReturn(500);
        activity.onOffsetChanged(appBar, -100);
        android.view.View root = (android.view.View) get(activity, "mRootView");
        assertEquals(400, root.getPaddingBottom());
    }

    @Test
    public void onOptionsItemSelected_close_showsConfirmDialog() {
        Milestone m = milestone("v1", IssueState.Open, null);
        IssueMilestoneEditActivity activity = launchEdit(m);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.milestone_close)));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentByTag("opencloseconfirm"));
    }

    @Test
    public void onOptionsItemSelected_reopen_showsConfirmDialog() {
        Milestone m = milestone("v1", IssueState.Closed, null);
        IssueMilestoneEditActivity activity = launchEdit(m);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.milestone_reopen)));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentByTag("opencloseconfirm"));
    }

    @Test
    public void onOptionsItemSelected_delete_showsConfirmDialog() {
        Milestone m = milestone("v1", IssueState.Open, null);
        IssueMilestoneEditActivity activity = launchEdit(m);
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        activity.onCreateOptionsMenu(menu);
        assertTrue(activity.onOptionsItemSelected(menu.findItem(R.id.delete)));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager()
                .findFragmentByTag("deleteconfirm"));
    }

    @Test
    public void onOptionsItemSelected_unknown_returnsFalse() {
        IssueMilestoneEditActivity activity = launchCreate();
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(appContext());
        assertFalse(activity.onOptionsItemSelected(menu.add(0, 99999, 0, "unknown")));
    }

    @Test
    public void onConfirmed_deleteConfirm_deletesAndFinishes() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        org.mockito.Mockito.when(service.deleteMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(42L)))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(null)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Open, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            activity.onConfirmed("deleteconfirm", null);
            com.gh4a.activities.ActivityTestHelper.waitFor(activity::isFinishing, 5000);
            assertTrue(activity.isFinishing());
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
        }
    }

    @Test
    public void onConfirmed_deleteConfirm_error_doesNotFinish() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        org.mockito.Mockito.when(service.deleteMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(42L)))
                .thenReturn(io.reactivex.Single.error(new java.io.IOException("boom")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Open, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            activity.onConfirmed("deleteconfirm", null);
            com.gh4a.activities.ActivityTestHelper.waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            assertNotNull(activity.findViewById(
                    com.google.android.material.R.id.snackbar_text));
            dismissCurrentSnackbarAsTimeout();
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> logContains(com.gh4a.Gh4Application.LOG_TAG, "Deleting milestone failed"), 5000);
            assertTrue(logContains(com.gh4a.Gh4Application.LOG_TAG, "Deleting milestone failed"));
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void onConfirmed_openCloseConfirm_reopen_updatesState() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        Milestone saved = milestoneWithNumber("v1", IssueState.Open, null, 42);
        org.mockito.Mockito.when(service.editMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(saved)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Closed, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            Bundle data = new Bundle();
            data.putBoolean("reopen", true);
            activity.onConfirmed("opencloseconfirm", data);
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> ((Milestone) get(activity, "mMilestone")).state() == IssueState.Open, 5000);
            assertEquals(IssueState.Open, ((Milestone) get(activity, "mMilestone")).state());
            assertEquals(android.app.Activity.RESULT_OK,
                    org.robolectric.Shadows.shadowOf(activity).getResultCode());
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void onConfirmed_openCloseConfirm_close_updatesState() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        Milestone saved = milestoneWithNumber("v1", IssueState.Closed, null, 42);
        org.mockito.Mockito.when(service.editMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(saved)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Open, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            Bundle data = new Bundle();
            data.putBoolean("reopen", false);
            activity.onConfirmed("opencloseconfirm", data);
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> ((Milestone) get(activity, "mMilestone")).state() == IssueState.Closed, 5000);
            assertEquals(IssueState.Closed, ((Milestone) get(activity, "mMilestone")).state());
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void onConfirmed_openCloseConfirm_error_doesNotFinish() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        org.mockito.Mockito.when(service.editMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(42L),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(io.reactivex.Single.error(new java.io.IOException("boom")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Open, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            Bundle data = new Bundle();
            data.putBoolean("reopen", false);
            activity.onConfirmed("opencloseconfirm", data);
            com.gh4a.activities.ActivityTestHelper.waitFor(() -> activity.findViewById(
                    com.google.android.material.R.id.snackbar_text) != null, 10000);
            assertNotNull(activity.findViewById(
                    com.google.android.material.R.id.snackbar_text));
            dismissCurrentSnackbarAsTimeout();
            com.gh4a.activities.ActivityTestHelper.waitFor(
                    () -> logContains(com.gh4a.Gh4Application.LOG_TAG, "Updating milestone failed"), 5000);
            assertTrue(logContains(com.gh4a.Gh4Application.LOG_TAG, "Updating milestone failed"));
            assertFalse(activity.isFinishing());
            assertEquals(IssueState.Open, ((Milestone) get(activity, "mMilestone")).state());
        }
    }

    @Test
    public void onConfirmed_unknownTag_doesNothing() {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            Milestone m = milestoneWithNumber("v1", IssueState.Open, null, 42);
            IssueMilestoneEditActivity activity = launchEdit(m);
            activity.onConfirmed("bogus", null);
            org.mockito.Mockito.verifyNoInteractions(service);
            assertFalse(activity.isFinishing());
        }
    }

    @Test
    public void pagerAdapter_instantiateAndTitles() {
        IssueMilestoneEditActivity activity = launchCreate();
        androidx.viewpager.widget.PagerAdapter adapter =
                (androidx.viewpager.widget.PagerAdapter) get(activity, "mAdapter");
        assertNotNull(adapter);
        assertEquals(3, adapter.getCount());
        assertEquals(appContext().getString(R.string.issue_body), adapter.getPageTitle(0).toString());
        assertEquals(appContext().getString(R.string.preview), adapter.getPageTitle(1).toString());
        assertEquals(appContext().getString(R.string.settings), adapter.getPageTitle(2).toString());
        android.view.ViewGroup root = (android.view.ViewGroup) get(activity, "mRootView");
        android.view.View editor = (android.view.View) adapter.instantiateItem(root, 0);
        assertNotNull(editor);
        assertEquals(R.id.editor_container, editor.getId());
        assertNotNull(adapter.instantiateItem(root, 1));
        assertNotNull(adapter.instantiateItem(root, 2));
        assertTrue(adapter.isViewFromObject(editor, editor));
        assertFalse(adapter.isViewFromObject(editor, new android.view.View(appContext())));
        assertNull(adapter.instantiateItem(root, 5));
    }

    @Test
    public void onClick_saveFab_nullDescription_savesWithNullDesc() throws Exception {
        com.meisolsson.githubsdk.service.issues.IssueMilestoneService service =
                org.mockito.Mockito.mock(com.meisolsson.githubsdk.service.issues.IssueMilestoneService.class);
        Milestone saved = milestoneWithNumber("v2", IssueState.Open, null, 99);
        org.mockito.ArgumentCaptor<com.meisolsson.githubsdk.model.request.issue.CreateMilestone> captor =
                org.mockito.ArgumentCaptor.forClass(
                        com.meisolsson.githubsdk.model.request.issue.CreateMilestone.class);
        org.mockito.Mockito.when(service.createMilestone(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        captor.capture()))
                .thenReturn(io.reactivex.Single.just(retrofit2.Response.success(saved)));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked = mockServiceFactory(service)) {
            IssueMilestoneEditActivity activity = launchCreate();
            setTitle(activity, "v2");
            android.widget.EditText nullTextView = new android.widget.EditText(activity) {
                @Override
                public android.text.Editable getText() {
                    return null;
                }
            };
            set(activity, "mDescriptionView", nullTextView);
            activity.onClick((android.view.View) get(activity, "mSaveFab"));
            com.gh4a.activities.ActivityTestHelper.waitFor(activity::isFinishing, 5000);
            assertTrue(activity.isFinishing());
            assertNull(captor.getValue().description());
        }
    }
}
