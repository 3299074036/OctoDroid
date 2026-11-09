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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;

/**
 * Merged starred page: the paged "all starred" list (REST, unchanged)
 * plus a header showing the official GitHub lists (GraphQL, see
 * UserListsGraphQL) aggregated on top of it. The old standalone
 * "STAR 分组" tab was removed (StarHubFactory); local groups are kept
 * only as a fallback section when official lists fail to load or are
 * empty, and are never auto-uploaded (official lists are public by
 * default, so uploading local groups silently would publish them).
 */
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

    // Official-lists header state (null = still loading)
    private LinearLayout mListsContainer;
    private TextView mListsTitle;
    private TextView mListsStatus;
    private List<UserListsGraphQL.UserList> mOfficialLists;
    private boolean mListsFailed;
    private String mExpandedOfficialId;
    private String mExpandedLocalGroup;
    private Disposable mListsDisposable;
    // 0.2.4（87）：入组／建组状态机（与 58 同款）——Rx 链挂 ViewModel，
    // 旋转不中断；结果经 LiveData 回到重建后的 Fragment（Toast＋重拉）。
    private StarBatchMoveViewModel mMoveViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLogin = getArguments().getString("user");

        mSortHelper = new RepositoryListContainerFragment.SortDrawerHelper();

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_KEY_SORT_ORDER)) {
            mSortOrder = savedInstanceState.getString(STATE_KEY_SORT_ORDER);
            mSortDirection = savedInstanceState.getString(STATE_KEY_SORT_DIRECTION);
        }
        // 0.2.4（87）：ViewModel 随 FragmentManager 在配置变更时保留，
        // 在 onCreate 里取——视图重建不影响它持有的 Rx 链。
        mMoveViewModel = new ViewModelProvider(this)
                .get(StarBatchMoveViewModel.class);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 0.2.4（87）：用 getViewLifecycleOwner() 观察（59 同款）——视图
        // 销毁后、Fragment 销毁前的窗口内不再投递结果，不碰已销毁的视图。
        mMoveViewModel.getSingleOp().observe(getViewLifecycleOwner(), this::onSingleOpChanged);
    }

    @Override
    public void onDestroyView() {
        // BUG-11: direct Schedulers.io() subscription must not outlive
        // the view (onDestroy alone is too late; isAdded() stays true
        // after onDestroyView). loadOfficialLists() reassigns it when
        // the view is recreated.
        if (mListsDisposable != null && !mListsDisposable.isDisposed()) {
            mListsDisposable.dispose();
        }
        mListsDisposable = null;
        mListsContainer = null;
        mListsTitle = null;
        mListsStatus = null;
        mAdapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (mListsDisposable != null && !mListsDisposable.isDisposed()) {
            mListsDisposable.dispose();
        }
        super.onDestroy();
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
        // 0.5.0 Star 页重排：给列表留出底栏高度的底边距并关闭 clip，
        // 使最后一行描述能完整滚到固定底栏上方，不再被截断。
        view.setClipToPadding(false);
        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                view.getPaddingRight(),
                view.getResources().getDimensionPixelSize(R.dimen.bottom_nav_height));
        View header = inflater.inflate(R.layout.star_lists_header, view, false);
        mListsContainer = header.findViewById(R.id.lists_container);
        mListsTitle = header.findViewById(R.id.lists_title);
        mListsStatus = header.findViewById(R.id.lists_status);
        mAdapter.setHeaderView(header);
        mAdapter.setOnItemLongClickListener(item -> {
            showAssignToGroupDialog(item);
            return true;
        });
        loadOfficialLists();
    }

    private void loadOfficialLists() {
        if (mListsDisposable != null && !mListsDisposable.isDisposed()) {
            mListsDisposable.dispose();
        }
        mListsDisposable = UserListsGraphQL.fetchLists(mLogin)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lists -> {
                    if (!isAdded()) {
                        return;
                    }
                    mOfficialLists = lists;
                    mListsFailed = false;
                    renderListsHeader();
                }, error -> {
                    if (!isAdded()) {
                        return;
                    }
                    // Official lists unavailable (network / scope): fall back
                    // to local groups instead of breaking the starred page.
                    mOfficialLists = new ArrayList<>();
                    mListsFailed = true;
                    renderListsHeader();
                });
    }

    private void renderListsHeader() {
        if (mListsContainer == null || getActivity() == null) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        mListsContainer.removeAllViews();

        if (mOfficialLists == null) {
            mListsStatus.setVisibility(View.VISIBLE);
            mListsStatus.setText(R.string.my_lists_loading);
            return;
        }

        if (!mOfficialLists.isEmpty()) {
            mListsTitle.setText(R.string.my_lists);
            mListsStatus.setVisibility(View.GONE);
            for (UserListsGraphQL.UserList list : mOfficialLists) {
                addGroupRow(inflater, list.name,
                        getResources().getQuantityString(
                                R.plurals.star_group_repo_count, list.repos.size(), list.repos.size()),
                        TextUtils.equals(mExpandedOfficialId, list.id),
                        () -> {
                            mExpandedOfficialId = TextUtils.equals(mExpandedOfficialId, list.id)
                                    ? null : list.id;
                            renderListsHeader();
                        });
                if (TextUtils.equals(mExpandedOfficialId, list.id)) {
                    for (UserListsGraphQL.Repo repo : list.repos) {
                        addRepoRow(inflater, repo.fullName, repo.description);
                    }
                }
            }
            return;
        }

        // No official lists: show local groups (if any) under their own title.
        List<String> groups = StarGroupManager.getGroups(getActivity(), mLogin);
        if (groups.isEmpty()) {
            mListsTitle.setText(R.string.my_lists);
            mListsStatus.setVisibility(View.VISIBLE);
            mListsStatus.setText(mListsFailed
                    ? R.string.my_lists_failed : R.string.my_lists_empty);
            return;
        }
        mListsTitle.setText(R.string.local_groups);
        mListsStatus.setVisibility(View.VISIBLE);
        mListsStatus.setText(mListsFailed
                ? R.string.my_lists_failed : R.string.my_lists_empty);
        // Keep the status line only as a hint when it failed; otherwise hide it
        // so local groups are not pushed down by an empty-list message.
        if (!mListsFailed) {
            mListsStatus.setVisibility(View.GONE);
        }
        for (String group : groups) {
            List<StarGroupManager.StarRepo> repos = StarGroupManager.getRepos(getActivity(), mLogin, group);
            addGroupRow(inflater, group,
                    getResources().getQuantityString(
                            R.plurals.star_group_repo_count, repos.size(), repos.size()),
                    TextUtils.equals(mExpandedLocalGroup, group),
                    () -> {
                        mExpandedLocalGroup = TextUtils.equals(mExpandedLocalGroup, group)
                                ? null : group;
                        renderListsHeader();
                    });
            if (TextUtils.equals(mExpandedLocalGroup, group)) {
                for (StarGroupManager.StarRepo repo : repos) {
                    addRepoRow(inflater, repo.fullName, repo.description);
                }
            }
        }
    }

    private void addGroupRow(LayoutInflater inflater, String title, String subtitle,
            boolean expanded, Runnable onClick) {
        View row = inflater.inflate(R.layout.row_star_group, mListsContainer, false);
        ImageView icon = row.findViewById(R.id.iv_icon);
        icon.setImageResource(R.drawable.icon_bookmark);
        // 0.5.0：书签图标改用现行主色（不再是纯黑大块），尺寸已在布局里缩到 24dp
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                com.gh4a.utils.UiUtils.resolveColor(getActivity(),
                        androidx.appcompat.R.attr.colorPrimary)));
        ((TextView) row.findViewById(R.id.tv_title)).setText(title);
        TextView sub = row.findViewById(R.id.tv_subtitle);
        sub.setTypeface(android.graphics.Typeface.MONOSPACE);
        sub.setText(subtitle);
        sub.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        row.setOnClickListener(v -> onClick.run());
        mListsContainer.addView(row);
    }

    private void addRepoRow(LayoutInflater inflater, String fullName, String description) {
        View row = inflater.inflate(R.layout.row_star_group, mListsContainer, false);
        int indent = (int) (32 * getResources().getDisplayMetrics().density + 0.5f);
        row.setPadding(row.getPaddingLeft() + indent, row.getPaddingTop(),
                row.getPaddingRight(), row.getPaddingBottom());
        ((ImageView) row.findViewById(R.id.iv_icon))
                .setImageResource(R.drawable.icon_repositories);
        ((TextView) row.findViewById(R.id.tv_title)).setText(fullName);
        TextView sub = row.findViewById(R.id.tv_subtitle);
        sub.setTypeface(android.graphics.Typeface.MONOSPACE);
        sub.setText(description);
        sub.setVisibility(description == null || description.isEmpty() ? View.GONE : View.VISIBLE);
        row.setOnClickListener(v -> {
            String[] parts = fullName.split("/", 2);
            if (parts.length == 2) {
                startActivity(RepositoryActivity.makeIntent(getActivity(), parts[0], parts[1]));
            }
        });
        mListsContainer.addView(row);
    }

    private void showAssignToGroupDialog(Repository repository) {
        if (mOfficialLists != null && !mOfficialLists.isEmpty()) {
            showAssignToOfficialListsDialog(repository);
        } else {
            showAssignToLocalGroupDialog(repository);
        }
    }

    /** Official lists: a repo may belong to several lists (unlike local groups). */
    private void showAssignToOfficialListsDialog(Repository repository) {
        final String fullName = repository.fullName();
        final List<String> names = new ArrayList<>();
        final boolean[] checked = new boolean[mOfficialLists.size()];
        for (int i = 0; i < mOfficialLists.size(); i++) {
            UserListsGraphQL.UserList list = mOfficialLists.get(i);
            names.add(list.name);
            for (UserListsGraphQL.Repo repo : list.repos) {
                if (fullName.equals(repo.fullName)) {
                    checked[i] = true;
                    break;
                }
            }
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.assign_official_title)
                .setMultiChoiceItems(names.toArray(new CharSequence[0]), checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    List<String> selectedIds = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            selectedIds.add(mOfficialLists.get(i).id);
                        }
                    }
                    assignToOfficialLists(fullName, selectedIds);
                })
                .setNeutralButton(R.string.assign_official_create_new, (dialog, which) ->
                        showCreateOfficialListDialog(repository))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCreateOfficialListDialog(Repository repository) {
        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.star_group_create_title)
                .setView(input)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    // 0.2.4（87）：建组→入组整条链走 ViewModel（58 同款），
                    // 旋转不中断；列表已建好、仓库没归属进去的中间态不再出现。
                    if (mMoveViewModel == null) {
                        return;
                    }
                    mMoveViewModel.runSingleOp(UserListsGraphQL.createList(name)
                            .flatMap(list -> {
                                List<String> ids = new ArrayList<>();
                                ids.add(list.id);
                                return UserListsGraphQL.assignRepo(
                                        repository.fullName(), ids);
                            }),
                            R.string.assign_official_success,
                            R.string.assign_official_failed, null);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void assignToOfficialLists(String fullName, List<String> listIds) {
        // 0.2.4（87）：取 node id → 更新列表链走 ViewModel（58 同款），
        // 不再挂 Fragment 的 mDialogDisposables；结果经 LiveData 回到
        // 重建后的 Fragment（Toast＋重拉）。
        if (mMoveViewModel == null) {
            return;
        }
        mMoveViewModel.runSingleOp(UserListsGraphQL.assignRepo(fullName, listIds),
                R.string.assign_official_success, R.string.assign_official_failed, null);
    }

    /**
     * 0.2.4（87）：单发操作（入组／建组→入组）结果——ViewModel 里的 Rx 链
     * 旋转不中断，重建后按结果 Toast＋重拉；已消费的不重复弹。
     */
    private void onSingleOpChanged(StarBatchMoveViewModel.SingleOp op) {
        if (op == null || mMoveViewModel == null || mMoveViewModel.isSingleOpConsumed(op)) {
            return;
        }
        if (!isAdded() || getActivity() == null) {
            return;
        }
        mMoveViewModel.markSingleOpConsumed(op);
        Toast.makeText(getActivity(), op.messageRes,
                op.success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        if (!op.success) {
            return;
        }
        loadOfficialLists();
    }

    /** Pre-existing local-group dialog, kept for the fallback path. */
    private void showAssignToLocalGroupDialog(Repository repository) {
        final List<String> groups = StarGroupManager.getGroups(getActivity(), mLogin);
        final String currentGroup =
                StarGroupManager.findGroupFor(getActivity(), mLogin, repository.fullName());
        final List<String> options = new ArrayList<>(groups);
        options.add(getString(R.string.star_group_create_new));

        int checkedIndex = currentGroup != null ? groups.indexOf(currentGroup) : -1;
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.star_group_assign_title)
                .setSingleChoiceItems(options.toArray(new CharSequence[0]), checkedIndex,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which < groups.size()) {
                                StarGroupManager.assignRepo(getActivity(), mLogin, groups.get(which),
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
                    if (StarGroupManager.createGroup(getActivity(), mLogin, name)) {
                        StarGroupManager.assignRepo(getActivity(), mLogin, name,
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
