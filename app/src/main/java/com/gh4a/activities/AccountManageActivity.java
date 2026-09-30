package com.gh4a.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gh4a.BaseActivity;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.fragment.ConfirmationDialogFragment;
import com.gh4a.fragment.LoginModeChooserFragment;
import com.gh4a.utils.AvatarHandler;
import com.meisolsson.githubsdk.model.User;

import java.util.ArrayList;
import java.util.List;

import android.util.LongSparseArray;

/**
 * Manage the accounts signed in to the app: switch the active account,
 * delete individual accounts, or add a new one. Deleting the last account
 * returns to the login screen.
 */
public class AccountManageActivity extends BaseActivity implements
        LoginModeChooserFragment.ParentCallback,
        ConfirmationDialogFragment.Callback {

    public static Intent makeIntent(android.content.Context context) {
        return new Intent(context, AccountManageActivity.class);
    }

    private static class AccountEntry {
        final long id;
        final String login;

        AccountEntry(long id, String login) {
            this.id = id;
            this.login = login;
        }
    }

    private final List<AccountEntry> mAccounts = new ArrayList<>();
    private AccountAdapter mAdapter;
    private String mPendingDeleteLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_manage);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new AccountAdapter();
        recyclerView.setAdapter(mAdapter);

        findViewById(R.id.add_account_button).setOnClickListener(
                v -> LoginModeChooserFragment.newInstance()
                        .show(getSupportFragmentManager(), "loginmode"));

        reloadAccounts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadAccounts();
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return getString(R.string.account_manage);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        return false;
    }

    private void reloadAccounts() {
        mAccounts.clear();
        Gh4Application app = Gh4Application.get();
        LongSparseArray<String> accounts = app.getAccounts();
        String activeLogin = app.getAuthLogin();
        for (int i = 0; i < accounts.size(); i++) {
            mAccounts.add(new AccountEntry(accounts.keyAt(i), accounts.valueAt(i)));
        }
        // Active account first
        mAccounts.sort((a, b) -> {
            boolean aActive = a.login.equals(activeLogin);
            boolean bActive = b.login.equals(activeLogin);
            return Boolean.compare(bActive, aActive);
        });
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private void switchToAccount(String login) {
        if (login.equals(Gh4Application.get().getAuthLogin())) {
            return;
        }
        Gh4Application.get().setActiveLogin(login);
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void askDeleteAccount(String login) {
        mPendingDeleteLogin = login;
        String message = getString(R.string.delete_account_confirm, login);
        ConfirmationDialogFragment.show(this, message, R.string.delete, null, "deleteaccount");
    }

    @Override
    public void onConfirmed(String tag, Parcelable data) {
        if (!"deleteaccount".equals(tag) || mPendingDeleteLogin == null) {
            return;
        }
        String login = mPendingDeleteLogin;
        mPendingDeleteLogin = null;

        boolean wasActive = login.equals(Gh4Application.get().getAuthLogin());
        String newActiveLogin = Gh4Application.get().removeAccount(login);
        if (newActiveLogin == null) {
            // Last account deleted: back to the login screen.
            goToToplevelActivity();
            finish();
        } else if (wasActive) {
            // Restart the stack so every screen reloads with the new account.
            Intent intent = new Intent(this, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            reloadAccounts();
        }
    }

    // LoginModeChooserFragment.ParentCallback

    @Override
    public void onLoginStartOauth() {
        Github4AndroidActivity.launchOauthLogin(this);
    }

    @Override
    public void onLoginFinished(String token, User user) {
        Gh4Application.get().addAccount(user, token);
        reloadAccounts();
        Toast.makeText(this, getString(R.string.account_added, user.login()),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoginFailed(Throwable error) {
        Toast.makeText(this, R.string.login_failed, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onLoginCanceled() {
        // Nothing to do
    }

    private class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView login;
            final TextView current;
            final ImageButton delete;

            ViewHolder(View v) {
                super(v);
                avatar = v.findViewById(R.id.iv_avatar);
                login = v.findViewById(R.id.tv_login);
                current = v.findViewById(R.id.tv_current);
                delete = v.findViewById(R.id.btn_delete);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_account, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AccountEntry entry = mAccounts.get(position);
            String activeLogin = Gh4Application.get().getAuthLogin();
            boolean isActive = entry.login.equals(activeLogin);

            holder.login.setText(entry.login);
            holder.current.setVisibility(isActive ? View.VISIBLE : View.GONE);
            AvatarHandler.assignAvatar(holder.avatar,
                    User.builder().login(entry.login).id(entry.id).build());

            holder.itemView.setOnClickListener(v -> switchToAccount(entry.login));
            holder.delete.setOnClickListener(v -> askDeleteAccount(entry.login));
        }

        @Override
        public int getItemCount() {
            return mAccounts.size();
        }
    }
}
