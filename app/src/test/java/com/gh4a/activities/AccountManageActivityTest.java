package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.meisolsson.githubsdk.model.User;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static com.gh4a.activities.ActivityTestHelper.appContext;
import static com.gh4a.activities.ActivityTestHelper.call;
import static com.gh4a.activities.ActivityTestHelper.controller;
import static com.gh4a.activities.ActivityTestHelper.get;
import static com.gh4a.activities.ActivityTestHelper.launch;
import static com.gh4a.activities.ActivityTestHelper.set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static com.gh4a.activities.ActivityTestHelper.call;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class AccountManageActivityTest {
    @Test
    public void makeIntent_setsComponent() {
        Intent intent = AccountManageActivity.makeIntent(appContext());
        assertEquals(AccountManageActivity.class.getName(), intent.getComponent().getClassName());
    }

    @Test
    public void launch_titleAndNoSwipeRefresh() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        assertEquals(appContext().getString(R.string.account_manage),
                activity.getSupportActionBar().getTitle().toString());
        assertFalse(activity.canSwipeToRefresh());
    }

    @Test
    public void launch_noAccounts_adapterIsEmpty() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        Object adapter = get(activity, "mAdapter");
        assertEquals(0, ((androidx.recyclerview.widget.RecyclerView.Adapter<?>) adapter).getItemCount());
    }

    @Test
    public void launch_withOneAccount_adapterHasOneRow() {
        Gh4Application.get().addAccount(User.builder().login("tester").id(123L).build(), "tok");
        AccountManageActivity activity = launch(AccountManageActivity.class);
        Object adapter = get(activity, "mAdapter");
        assertEquals(1, ((androidx.recyclerview.widget.RecyclerView.Adapter<?>) adapter).getItemCount());
    }

    @Test
    public void onSaveInstanceState_roundTripsPendingDeleteLogin() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        set(activity, "mPendingDeleteLogin", "victim");
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertEquals("victim", out.getString("account_manage:pending_delete_login"));
    }

    @Test
    public void onSaveInstanceState_nullPending_storesNull() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        assertNull(out.getString("account_manage:pending_delete_login"));
    }

    @Test
    public void onConfirmed_wrongTag_doesNothing() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        set(activity, "mPendingDeleteLogin", "victim");
        activity.onConfirmed("othertag", null);
        assertEquals("victim", get(activity, "mPendingDeleteLogin"));
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onConfirmed_noPending_doesNothing() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        activity.onConfirmed("deleteaccount", null);
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onConfirmed_lastAccountDeleted_finishes() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        set(activity, "mPendingDeleteLogin", "ghost");
        activity.onConfirmed("deleteaccount", null);
        assertNull(get(activity, "mPendingDeleteLogin"));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void onLoginFailed_andCanceled_doNotCrash() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        activity.onLoginFailed(new RuntimeException("boom"));
        activity.onLoginCanceled();
        assertFalse(activity.isFinishing());
    }

    @Test
    public void onLoginFinished_addsAccountAndReloads() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        activity.onLoginFinished("tok2", User.builder().login("newbie").id(7L).build());
        assertEquals("newbie", Gh4Application.get().getAuthLogin());
        Object adapter = get(activity, "mAdapter");
        assertEquals(1, ((androidx.recyclerview.widget.RecyclerView.Adapter<?>) adapter).getItemCount());
    }

    private void addTwoAccounts() {
        Gh4Application app = Gh4Application.get();
        app.addAccount(User.builder().login("alice").id(1L).build(), "tok-alice");
        app.addAccount(User.builder().login("bob").id(2L).build(), "tok-bob");
        app.setActiveLogin("alice");
    }

    private java.util.List<?> accountsOf(AccountManageActivity activity) {
        return (java.util.List<?>) get(activity, "mAccounts");
    }

    private String loginOf(Object entry) {
        return (String) get(entry, "login");
    }

    @Test
    public void onCreate_restoresPendingDeleteLogin() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        set(activity, "mPendingDeleteLogin", "victim");
        Bundle out = new Bundle();
        activity.onSaveInstanceState(out);
        AccountManageActivity recreated = org.robolectric.Robolectric
                .buildActivity(AccountManageActivity.class).create(out).start().resume().get();
        assertEquals("victim", get(recreated, "mPendingDeleteLogin"));
    }

    @Test
    public void addAccountButton_click_showsLoginModeChooser() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        activity.findViewById(R.id.add_account_button).performClick();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("loginmode"));
    }

    @Test
    public void reloadAccounts_twoAccounts_activeFirst() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        java.util.List<?> accounts = accountsOf(activity);
        assertEquals(2, accounts.size());
        assertEquals("alice", loginOf(accounts.get(0)));
        assertEquals("bob", loginOf(accounts.get(1)));
    }

    @Test
    public void switchToAccount_activeAccount_doesNothing() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        call(activity, "switchToAccount", new Class<?>[]{String.class}, new Object[]{"alice"});
        assertFalse(activity.isFinishing());
    }

    @Test
    public void switchToAccount_otherAccount_restartsHome() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        call(activity, "switchToAccount", new Class<?>[]{String.class}, new Object[]{"bob"});
        assertTrue(activity.isFinishing());
        assertEquals("bob", Gh4Application.get().getAuthLogin());
    }

    @Test
    public void askDeleteAccount_showsConfirmDialog() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        call(activity, "askDeleteAccount", new Class<?>[]{String.class}, new Object[]{"alice"});
        assertEquals("alice", get(activity, "mPendingDeleteLogin"));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("deleteaccount"));
    }

    @Test
    public void onConfirmed_deleteActiveWithSuccessor_restartsHome() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        set(activity, "mPendingDeleteLogin", "alice");
        activity.onConfirmed("deleteaccount", null);
        assertTrue(activity.isFinishing());
        assertEquals("bob", Gh4Application.get().getAuthLogin());
    }

    @Test
    public void onConfirmed_deleteInactive_reloadsAccounts() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        set(activity, "mPendingDeleteLogin", "bob");
        activity.onConfirmed("deleteaccount", null);
        assertFalse(activity.isFinishing());
        assertEquals("alice", Gh4Application.get().getAuthLogin());
        assertEquals(1, accountsOf(activity).size());
        assertEquals("alice", loginOf(accountsOf(activity).get(0)));
    }

    @Test
    public void onLoginStartOauth_storesStateAndVerifier() {
        AccountManageActivity activity = launch(AccountManageActivity.class);
        activity.onLoginStartOauth();
        assertNotNull(Gh4Application.securePrefs(activity)
                .getString("oauth_state_pending", null));
        assertNotNull(Gh4Application.securePrefs(activity)
                .getString("oauth_verifier_pending", null));
    }

    @Test
    public void adapter_bindsActiveAndInactiveRows() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        @SuppressWarnings("rawtypes")
        androidx.recyclerview.widget.RecyclerView.Adapter adapter =
                (androidx.recyclerview.widget.RecyclerView.Adapter) get(activity, "mAdapter");
        assertEquals(2, adapter.getItemCount());
        android.view.ViewGroup parent = new android.widget.FrameLayout(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                adapter.onCreateViewHolder(parent, 0);
        assertNotNull(holder);
        adapter.onBindViewHolder(holder, 0);
        android.widget.TextView login = (android.widget.TextView) get(holder, "login");
        android.widget.TextView current = (android.widget.TextView) get(holder, "current");
        assertEquals("alice", login.getText().toString());
        assertEquals(android.view.View.VISIBLE, current.getVisibility());
        adapter.onBindViewHolder(holder, 1);
        assertEquals("bob", login.getText().toString());
        assertEquals(android.view.View.GONE, current.getVisibility());
    }

    @Test
    public void adapter_rowClick_switchesAccount() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        @SuppressWarnings("rawtypes")
        androidx.recyclerview.widget.RecyclerView.Adapter adapter =
                (androidx.recyclerview.widget.RecyclerView.Adapter) get(activity, "mAdapter");
        android.view.ViewGroup parent = new android.widget.FrameLayout(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 1);
        holder.itemView.performClick();
        assertTrue(activity.isFinishing());
        assertEquals("bob", Gh4Application.get().getAuthLogin());
    }

    @Test
    public void adapter_deleteClick_asksDelete() {
        addTwoAccounts();
        AccountManageActivity activity = launch(AccountManageActivity.class);
        @SuppressWarnings("rawtypes")
        androidx.recyclerview.widget.RecyclerView.Adapter adapter =
                (androidx.recyclerview.widget.RecyclerView.Adapter) get(activity, "mAdapter");
        android.view.ViewGroup parent = new android.widget.FrameLayout(activity);
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder =
                adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        ((android.widget.ImageButton) get(holder, "delete")).performClick();
        assertEquals("alice", get(activity, "mPendingDeleteLogin"));
        activity.getSupportFragmentManager().executePendingTransactions();
        assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("deleteaccount"));
    }

    @Test
    public void reloadAccounts_beforeAdapterCreated_skipsNotify() {
        // defensive null guard in reloadAccounts(): only reachable before
        // onCreate creates the adapter
        AccountManageActivity activity = controller(AccountManageActivity.class, null).get();
        assertNull(get(activity, "mAdapter"));
        call(activity, "reloadAccounts", new Class<?>[]{}, new Object[]{});
        assertNull(get(activity, "mAdapter"));
    }
}
