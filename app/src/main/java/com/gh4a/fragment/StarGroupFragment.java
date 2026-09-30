package com.gh4a.fragment;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.gh4a.R;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.StarGroupAdapter;
import com.gh4a.utils.StarGroupManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages local groups of starred repositories. Shows the group list;
 * tapping a group drills into its repositories. Back navigates up.
 */
public class StarGroupFragment extends Fragment {
    private static final String STATE_KEY_GROUP = "group";

    public static StarGroupFragment newInstance() {
        return new StarGroupFragment();
    }

    private RecyclerView mRecyclerView;
    private TextView mEmptyView;
    private StarGroupAdapter mAdapter;
    private String mSelectedGroup; // null = group list mode

    private final OnBackPressedCallback mBackCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            mSelectedGroup = null;
            reload();
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            mSelectedGroup = savedInstanceState.getString(STATE_KEY_GROUP);
        }
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(this, mBackCallback);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_KEY_GROUP, mSelectedGroup);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_star_group, container, false);
        mRecyclerView = view.findViewById(R.id.list);
        mEmptyView = view.findViewById(R.id.empty);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdapter = new StarGroupAdapter(getActivity());
        mAdapter.setOnItemClickListener(this::onItemClick);
        mAdapter.setOnItemLongClickListener(this::onItemLongClick);
        mRecyclerView.setAdapter(mAdapter);
        reload();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        List<StarGroupAdapter.Row> rows = new ArrayList<>();
        if (mSelectedGroup == null) {
            for (String group : StarGroupManager.getGroups(getActivity())) {
                int count = StarGroupManager.getRepos(getActivity(), group).size();
                rows.add(new StarGroupAdapter.Row(
                        StarGroupAdapter.Row.KIND_GROUP, group,
                        getResources().getQuantityString(
                                R.plurals.star_group_repo_count, count, count),
                        group));
            }
            mEmptyView.setText(R.string.no_star_groups);
        } else {
            for (StarGroupManager.StarRepo repo :
                    StarGroupManager.getRepos(getActivity(), mSelectedGroup)) {
                rows.add(new StarGroupAdapter.Row(
                        StarGroupAdapter.Row.KIND_REPO, repo.fullName,
                        repo.description, repo.fullName));
            }
            mEmptyView.setText(R.string.no_star_group_repos);
        }
        mAdapter.setRows(rows);
        mEmptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        mBackCallback.setEnabled(mSelectedGroup != null);
        requireActivity().invalidateOptionsMenu();
    }

    private void onItemClick(StarGroupAdapter.Row row) {
        if (row.kind == StarGroupAdapter.Row.KIND_GROUP) {
            mSelectedGroup = row.key;
            reload();
        } else {
            String[] parts = row.key.split("/", 2);
            if (parts.length == 2) {
                startActivity(RepositoryActivity.makeIntent(getActivity(), parts[0], parts[1]));
            }
        }
    }

    private boolean onItemLongClick(StarGroupAdapter.Row row) {
        if (row.kind == StarGroupAdapter.Row.KIND_GROUP) {
            showGroupOptionsDialog(row.key);
        } else if (mSelectedGroup != null) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(getString(R.string.star_group_remove_confirm, row.key))
                    .setPositiveButton(R.string.remove, (d, w) -> {
                        StarGroupManager.removeRepo(getActivity(), mSelectedGroup, row.key);
                        reload();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
        return true;
    }

    private void showGroupOptionsDialog(String group) {
        new AlertDialog.Builder(getActivity())
                .setTitle(group)
                .setItems(new CharSequence[] {
                        getString(R.string.rename),
                        getString(R.string.delete)
                }, (d, which) -> {
                    if (which == 0) {
                        showNameInputDialog(
                                getString(R.string.star_group_rename_title),
                                group,
                                newName -> {
                                    StarGroupManager.renameGroup(getActivity(), group, newName);
                                    reload();
                                });
                    } else {
                        new AlertDialog.Builder(getActivity())
                                .setMessage(getString(R.string.star_group_delete_confirm, group))
                                .setPositiveButton(R.string.delete, (d2, w) -> {
                                    StarGroupManager.deleteGroup(getActivity(), group);
                                    reload();
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
                })
                .show();
    }

    private interface NameCallback {
        void onName(String name);
    }

    private void showNameInputDialog(String title, String initial, NameCallback callback) {
        EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(initial);
        input.setSelectAllOnFocus(true);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) ->
                        callback.onName(input.getText().toString().trim()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mSelectedGroup == null) {
            inflater.inflate(R.menu.star_group_menu, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.create_group) {
            showNameInputDialog(
                    getString(R.string.star_group_create_title), "",
                    name -> {
                        if (StarGroupManager.createGroup(getActivity(), name)) {
                            reload();
                        }
                    });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
