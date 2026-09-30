/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.fragment;

import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.adapter.RepositoryAdapter;
import com.gh4a.adapter.RootAdapter;
import com.gh4a.utils.StarGroupManager;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.service.activity.StarringService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.reactivex.Single;
import retrofit2.Response;

public class StarredRepositoryListFragment extends PagedDataBaseFragment<Repository> {
    private static final String STATE_KEY_SORT_ORDER = "sort_order";
    private static final String STATE_KEY_SORT_DIRECTION = "sort_direction";

    public static StarredRepositoryListFragment newInstance(String login) {
        StarredRepositoryListFragment f = new StarredRepositoryListFragment();

        Bundle args = new Bundle();
        args.putString("user", login);
        f.setArguments(args);

        return f;
    }

    private String mLogin;
    private String mSortOrder = "created";
    private String mSortDirection = "desc";
    private RepositoryListContainerFragment.SortDrawerHelper mSortHelper;
    private RepositoryAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLogin = getArguments().getString("user");

        mSortHelper = new RepositoryListContainerFragment.SortDrawerHelper();

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_KEY_SORT_ORDER)) {
            mSortOrder = savedInstanceState.getString(STATE_KEY_SORT_ORDER);
            mSortDirection = savedInstanceState.getString(STATE_KEY_SORT_DIRECTION);
        }
        setHasOptionsMenu(true);
    }

    public void setSortOrderAndDirection(String sortOrder, String direction) {
        if (!TextUtils.equals(sortOrder, mSortOrder) || !TextUtils.equals(mSortDirection, direction)) {
            mSortOrder = sortOrder;
            mSortDirection = direction;
            if (isAdded()) {
                getActivity().invalidateOptionsMenu();
            }
            onRefresh();
        }
    }

    public String getSortOrder() {
        return mSortOrder;
    }

    public String getSortDirection() {
        return mSortDirection;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_KEY_SORT_ORDER, mSortOrder);
        outState.putString(STATE_KEY_SORT_DIRECTION, mSortDirection);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.repo_starred_list_menu, menu);
        mSortHelper.selectSortType(menu, mSortOrder, mSortDirection, true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String[] sortOrderAndDirection = mSortHelper.handleSelectionAndGetSortOrder(item);
        if (sortOrderAndDirection == null) {
            return false;
        }
        mSortOrder = sortOrderAndDirection[0];
        mSortDirection = sortOrderAndDirection[1];
        item.setChecked(true);
        onRefresh();
        return true;
    }

    @Override
    protected RootAdapter<Repository, ? extends RecyclerView.ViewHolder> onCreateAdapter() {
        mAdapter = new RepositoryAdapter(getActivity());
        return mAdapter;
    }

    @Override
    protected void onRecyclerViewInflated(RecyclerView view, android.view.LayoutInflater inflater) {
        super.onRecyclerViewInflated(view, inflater);
        mAdapter.setOnItemLongClickListener(item -> {
            showAssignToGroupDialog(item);
            return true;
        });
    }

    private void showAssignToGroupDialog(Repository repository) {
        final List<String> groups = StarGroupManager.getGroups(getActivity());
        final String currentGroup =
                StarGroupManager.findGroupFor(getActivity(), repository.fullName());
        final List<String> options = new ArrayList<>(groups);
        options.add(getString(R.string.star_group_create_new));

        int checkedIndex = currentGroup != null ? groups.indexOf(currentGroup) : -1;
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.star_group_assign_title)
                .setSingleChoiceItems(options.toArray(new CharSequence[0]), checkedIndex,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which < groups.size()) {
                                StarGroupManager.assignRepo(getActivity(), groups.get(which),
                                        repository.fullName(), repository.description());
                            } else {
                                showCreateAndAssignDialog(repository);
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCreateAndAssignDialog(Repository repository) {
        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.star_group_create_title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (StarGroupManager.createGroup(getActivity(), name)) {
                        StarGroupManager.assignRepo(getActivity(), name,
                                repository.fullName(), repository.description());
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.no_starred_repos_found;
    }

    @Override
    public void onItemClick(Repository repository) {
        startActivity(RepositoryActivity.makeIntent(getActivity(), repository));
    }

    @Override
    protected Single<Response<Page<Repository>>> loadPage(int page, boolean bypassCache) {
        final StarringService service = ServiceFactory.get(StarringService.class, bypassCache);
        final HashMap<String, String> filterData = new HashMap<>();
        filterData.put("sort", mSortOrder);
        filterData.put("direction", mSortDirection);

        return service.getStarredRepositories(mLogin, filterData, page);
    }
}