package com.gh4a.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.activities.RepositoryActivity;
import com.gh4a.utils.StarGroupManager;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.activity.StarringService;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;

import io.reactivex.Single;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** StarredRepositoryListFragment：星标仓库列表（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class StarredRepositoryListFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    @Test
    public void newInstance_storesLogin() {
        StarredRepositoryListFragment f = StarredRepositoryListFragment.newInstance("alice");
        assertEquals("alice", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLoginAndDefaults() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Field login = StarredRepositoryListFragment.class.getDeclaredField("mLogin");
        login.setAccessible(true);
        assertEquals("alice", login.get(f));
        assertEquals("created", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
        assertNotNull(StarredRepositoryListFragment.class
                .getDeclaredField("mMoveViewModel"));
    }

    @Test
    public void onCreate_initializesSortHelper() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Field helper = StarredRepositoryListFragment.class.getDeclaredField("mSortHelper");
        helper.setAccessible(true);
        assertNotNull(helper.get(f));
    }

    @Test
    public void onSaveInstanceState_persistsSortOrderAndDirection() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("created", out.getString("sort_order"));
        assertEquals("desc", out.getString("sort_direction"));
    }

    @Test
    public void getEmptyTextResId() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        assertEquals(R.string.no_starred_repos_found, f.getEmptyTextResId());
    }

    @Test
    public void onCreateAdapter_returnsAdapter() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        assertNotNull(f.onCreateAdapter());
    }

    @Test
    public void loadPage_returnsLazySingleWithoutSubscribing() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        assertNotNull(f.loadPage(1, false));
    }

    @Test
    public void onOptionsItemSelected_unknownItemReturnsFalse() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.search);
        assertFalse(f.onOptionsItemSelected(item));
        // 未触发刷新：排序保持默认。
        assertEquals("created", f.getSortOrder());
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onItemClick_startsRepositoryActivity() {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        Repository item = Repository.builder()
                .fullName("octo/cat")
                .owner(User.builder().login("octo").build())
                .name("cat")
                .build();
        f.onItemClick(item);
        Intent started = Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("octo", started.getStringExtra("owner"));
        assertEquals("cat", started.getStringExtra("repo"));
    }

    // ---- 分组头渲染 / 对话框 / 关注态回调 ----

    private static Object field(StarredRepositoryListFragment f, String name)
            throws Exception {
        Field field = StarredRepositoryListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(StarredRepositoryListFragment f, String name, Object value)
            throws Exception {
        Field field = StarredRepositoryListFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private static void callVoid(StarredRepositoryListFragment f, String name,
            Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m =
                StarredRepositoryListFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        m.invoke(f, args);
    }

    private static UserListsGraphQL.UserList list(String id, String name,
            java.util.List<UserListsGraphQL.Repo> repos) {
        return new UserListsGraphQL.UserList(id, name, "desc-" + id, repos);
    }

    private static UserListsGraphQL.Repo listRepo(String fullName) {
        return new UserListsGraphQL.Repo(fullName, "d-" + fullName);
    }

    private StarredRepositoryListFragment attachWithHeader() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        android.view.View header = android.view.LayoutInflater.from(mActivity)
                .inflate(R.layout.star_lists_header, null);
        setField(f, "mListsContainer", header.findViewById(R.id.lists_container));
        setField(f, "mListsTitle", header.findViewById(R.id.lists_title));
        setField(f, "mListsStatus", header.findViewById(R.id.lists_status));
        return f;
    }

    @Test
    public void renderListsHeader_nullListsShowsLoading() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.widget.TextView status =
                (android.widget.TextView) field(f, "mListsStatus");
        assertEquals(android.view.View.VISIBLE, status.getVisibility());
        assertEquals(mActivity.getString(R.string.my_lists_loading),
                status.getText().toString());
    }

    @Test
    public void renderListsHeader_emptyListsFailedShowsFailedStatus() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", true);
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.widget.TextView status =
                (android.widget.TextView) field(f, "mListsStatus");
        assertEquals(mActivity.getString(R.string.my_lists_failed),
                status.getText().toString());
    }

    @Test
    public void renderListsHeader_emptyListsShowsEmptyStatus() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", false);
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.widget.TextView status =
                (android.widget.TextView) field(f, "mListsStatus");
        assertEquals(mActivity.getString(R.string.my_lists_empty),
                status.getText().toString());
    }

    @Test
    public void renderListsHeader_withListsAddsGroupRows() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.widget.TextView status =
                (android.widget.TextView) field(f, "mListsStatus");
        assertEquals(android.view.View.GONE, status.getVisibility());
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(1, container.getChildCount());
        assertEquals("Group 1",
                ((android.widget.TextView) container.getChildAt(0)
                        .findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void renderListsHeader_expandedListAddsRepoRows() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        setField(f, "mExpandedOfficialId", "l1");
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(3, container.getChildCount());
        assertEquals("a/b",
                ((android.widget.TextView) container.getChildAt(1)
                        .findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void renderListsHeader_nullContainerIsNoop() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        callVoid(f, "renderListsHeader", new Class<?>[]{});
    }

    @Test
    public void addGroupRow_buildsRowWithTitleAndSubtitle() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addGroupRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class, boolean.class, Runnable.class},
                inflater, "Group 1", "2 个仓库", false, (Runnable) () -> {});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(1, container.getChildCount());
        assertEquals("Group 1",
                ((android.widget.TextView) container.getChildAt(0)
                        .findViewById(R.id.tv_title)).getText().toString());
        assertEquals("2 个仓库",
                ((android.widget.TextView) container.getChildAt(0)
                        .findViewById(R.id.tv_subtitle)).getText().toString());
    }

    @Test
    public void addRepoRow_buildsRowWithFullName() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addRepoRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class},
                inflater, "a/b", "desc");
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(1, container.getChildCount());
        assertEquals("a/b",
                ((android.widget.TextView) container.getChildAt(0)
                        .findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void addRepoRow_emptyDescriptionHidesSubtitle() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addRepoRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class},
                inflater, "a/b", "");
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(android.view.View.GONE,
                container.getChildAt(0).findViewById(R.id.tv_subtitle)
                        .getVisibility());
    }

    private static org.mockito.MockedStatic<UserListsGraphQL> mockFetchLists(
            io.reactivex.Single<java.util.List<UserListsGraphQL.UserList>> result) {
        org.mockito.MockedStatic<UserListsGraphQL> mocked =
                org.mockito.Mockito.mockStatic(UserListsGraphQL.class,
                        org.mockito.Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> UserListsGraphQL.fetchLists(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result);
        return mocked;
    }

    private static void awaitListsLoaded(StarredRepositoryListFragment f)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (field(f, "mOfficialLists") == null) {
            org.robolectric.Shadows.shadowOf(
                    android.os.Looper.getMainLooper()).idle();
            if (System.currentTimeMillis() > deadline) break;
            Thread.sleep(20);
        }
    }

    @Test
    public void loadOfficialLists_successRendersHeader() throws Exception {
        try (org.mockito.MockedStatic<UserListsGraphQL> mocked = mockFetchLists(
                io.reactivex.Single.just(java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Arrays.asList(
                                listRepo("a/b"))))))) {
            StarredRepositoryListFragment f = attachWithHeader();
            callVoid(f, "loadOfficialLists", new Class<?>[]{});
            awaitListsLoaded(f);
            assertEquals(1, ((java.util.List<?>) field(f, "mOfficialLists")).size());
            assertFalse((Boolean) field(f, "mListsFailed"));
            android.view.ViewGroup container =
                    (android.view.ViewGroup) field(f, "mListsContainer");
            assertEquals(1, container.getChildCount());
        }
    }

    @Test
    public void loadOfficialLists_errorFallsBackToEmpty() throws Exception {
        try (org.mockito.MockedStatic<UserListsGraphQL> mocked = mockFetchLists(
                io.reactivex.Single.error(new RuntimeException("no net")))) {
            StarredRepositoryListFragment f = attachWithHeader();
            callVoid(f, "loadOfficialLists", new Class<?>[]{});
            long deadline = System.currentTimeMillis() + 5000;
            while (!Boolean.TRUE.equals(field(f, "mListsFailed"))) {
                org.robolectric.Shadows.shadowOf(
                        android.os.Looper.getMainLooper()).idle();
                if (System.currentTimeMillis() > deadline) break;
                Thread.sleep(20);
            }
            assertTrue((Boolean) field(f, "mListsFailed"));
            assertTrue(((java.util.List<?>) field(f, "mOfficialLists")).isEmpty());
        }
    }

    @Test
    public void showAssignToOfficialListsDialog_showsMultiChoiceDialog() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b"))),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        Repository repo = Repository.builder()
                .fullName("a/b")
                .owner(User.builder().login("a").build())
                .name("b")
                .build();
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showAssignToOfficialListsDialog",
                new Class<?>[]{Repository.class}, repo);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showAssignToGroupDialog_withListsShowsOfficialDialog() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        Repository repo = Repository.builder()
                .fullName("a/b")
                .owner(User.builder().login("a").build())
                .name("b")
                .build();
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showAssignToGroupDialog", new Class<?>[]{Repository.class}, repo);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showAssignToGroupDialog_emptyListsShowsLocalDialog() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        Repository repo = Repository.builder()
                .fullName("a/b")
                .owner(User.builder().login("a").build())
                .name("b")
                .build();
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showAssignToGroupDialog", new Class<?>[]{Repository.class}, repo);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showCreateOfficialListDialog_showsInputDialog() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        Repository repo = Repository.builder()
                .fullName("a/b")
                .owner(User.builder().login("a").build())
                .name("b")
                .build();
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showCreateOfficialListDialog",
                new Class<?>[]{Repository.class}, repo);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showCreateAndAssignDialog_showsInputDialog() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        Repository repo = Repository.builder()
                .fullName("a/b")
                .owner(User.builder().login("a").build())
                .name("b")
                .build();
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showCreateAndAssignDialog",
                new Class<?>[]{Repository.class}, repo);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onSingleOpChanged_nullOpIsNoop() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, (Object) null);
    }

    @Test
    public void onSingleOpChanged_consumedOpIsNoop() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(true);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markSingleOpConsumed(org.mockito.Mockito.any());
    }

    @Test
    public void onSingleOpChanged_failedOpToastsWithoutReload() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, false, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm).markSingleOpConsumed(op);
        assertEquals(mActivity.getString(R.string.app_name),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertEquals(null, field(f, "mOfficialLists"));
    }

    @Test
    public void onOptionsItemSelected_sortItemSetsSortAndRefreshes() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        MenuItem item = mock(MenuItem.class);
        when(item.getItemId()).thenReturn(R.id.sort_updated_desc);
        assertTrue(f.onOptionsItemSelected(item));
        assertEquals("updated", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
        org.mockito.Mockito.verify(item).setChecked(true);
    }

    @Test
    public void onDestroyView_clearsListsViews() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        f.onDestroyView();
        assertEquals(null, field(f, "mListsContainer"));
        assertEquals(null, field(f, "mListsTitle"));
        assertEquals(null, field(f, "mListsStatus"));
        assertEquals(null, field(f, "mAdapter"));
    }

    @Test
    public void onDestroy_disposesListsDisposable() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        io.reactivex.disposables.Disposable disposable =
                mock(io.reactivex.disposables.Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);
        setField(f, "mListsDisposable", disposable);
        f.onDestroy();
        org.mockito.Mockito.verify(disposable).dispose();
    }

    @Test
    public void onCreateOptionsMenu_inflatesMenu() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        androidx.appcompat.view.menu.MenuBuilder menu =
                new androidx.appcompat.view.menu.MenuBuilder(mActivity);
        android.view.MenuInflater inflater =
                new android.view.MenuInflater(mActivity);
        f.onCreateOptionsMenu(menu, inflater);
        assertNotNull(menu.findItem(R.id.sort_created_desc));
    }

    // ---- 缺口补齐：保存态 / dispose 守卫 / 完整挂载 / 对话框交互 ----

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private StarredRepositoryListFragment attachResumed(
            StarredRepositoryListFragment f) {
        // mActivity 已 setup（RESUMED）：加进 content 容器即走完整视图生命周期。
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, f, "test").commitNow();
        idleMain();
        return f;
    }

    private static Repository repo(String fullName) {
        String[] parts = fullName.split("/", 2);
        return Repository.builder()
                .fullName(fullName)
                .owner(User.builder().login(parts[0]).build())
                .name(parts.length > 1 ? parts[1] : fullName)
                .build();
    }

    private static MockedStatic<ServiceFactory> mockStarringServiceEmpty() {
        MockedStatic<ServiceFactory> mocked =
                Mockito.mockStatic(ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        StarringService service = mock(StarringService.class);
        Page<Repository> page = mock(Page.class);
        when(page.items()).thenReturn(new java.util.ArrayList<>());
        when(page.next()).thenReturn(null);
        mocked.when(() -> ServiceFactory.get(StarringService.class, false))
                .thenReturn(service);
        when(service.getStarredRepositories(eq("alice"), any(), eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        return mocked;
    }

    private static MockedStatic<GraphQLClient> mockGraphQL() {
        MockedStatic<GraphQLClient> mocked = Mockito.mockStatic(GraphQLClient.class);
        mocked.when(() -> GraphQLClient.execute(any(), any(), any()))
                .thenAnswer(inv -> {
                    String query = inv.getArgument(0);
                    if (query.contains("createUserList")) {
                        return "{\"data\":{\"createUserList\":{\"list\":"
                                + "{\"id\":\"L1\",\"name\":\"My List\","
                                + "\"description\":\"\"}}}}";
                    }
                    if (query.contains("repository(owner:")) {
                        return "{\"data\":{\"repository\":{\"id\":\"R1\"}}}";
                    }
                    return "{}";
                });
        return mocked;
    }

    private static android.app.Dialog latestDialog() {
        java.util.List<android.app.Dialog> shown =
                org.robolectric.shadows.ShadowDialog.getShownDialogs();
        return shown.get(shown.size() - 1);
    }

    private static android.widget.EditText findEditText(android.view.View root) {
        if (root instanceof android.widget.EditText) {
            return (android.widget.EditText) root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.EditText found = findEditText(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void clickListItem(AlertDialog dialog, int position) {
        android.widget.ListView listView = dialog.getListView();
        listView.setItemChecked(position, true);
        listView.getOnItemClickListener().onItemClick(
                listView, listView.getChildAt(position), position, position);
        idleMain();
    }

    private static void clickDialogButton(AlertDialog dialog, int which) {
        dialog.getButton(which).performClick();
        idleMain();
    }

    private void seedLocalGroups() {
        StarGroupManager.createGroup(mActivity, "alice", "Work");
        StarGroupManager.assignRepo(mActivity, "alice", "Work", "a/b", "desc a/b");
        StarGroupManager.createGroup(mActivity, "alice", "Side");
    }

    private static Fragment.SavedState savedState(Bundle state) throws Exception {
        java.lang.reflect.Constructor<Fragment.SavedState> ctor =
                Fragment.SavedState.class.getDeclaredConstructor(Bundle.class);
        ctor.setAccessible(true);
        return ctor.newInstance(state);
    }

    @Test
    public void onCreate_restoresSortFromSavedState() throws Exception {
        Bundle state = new Bundle();
        state.putString("sort_order", "updated");
        state.putString("sort_direction", "asc");
        StarredRepositoryListFragment f =
                StarredRepositoryListFragment.newInstance("alice");
        f.setInitialSavedState(savedState(state));
        attach(f);
        assertEquals("updated", f.getSortOrder());
        assertEquals("asc", f.getSortDirection());
    }

    @Test
    public void onCreate_savedStateWithoutSortKeys_keepsDefaults() throws Exception {
        StarredRepositoryListFragment f =
                StarredRepositoryListFragment.newInstance("alice");
        f.setInitialSavedState(savedState(new Bundle()));
        attach(f);
        assertEquals("created", f.getSortOrder());
        assertEquals("desc", f.getSortDirection());
    }

    @Test
    public void onDestroyView_disposesActiveDisposable() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        io.reactivex.disposables.Disposable disposable =
                mock(io.reactivex.disposables.Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);
        setField(f, "mListsDisposable", disposable);
        f.onDestroyView();
        Mockito.verify(disposable).dispose();
        assertNull(field(f, "mListsDisposable"));
    }

    @Test
    public void onDestroyView_alreadyDisposedDisposable_skipsDispose() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        io.reactivex.disposables.Disposable disposable =
                mock(io.reactivex.disposables.Disposable.class);
        when(disposable.isDisposed()).thenReturn(true);
        setField(f, "mListsDisposable", disposable);
        f.onDestroyView();
        Mockito.verify(disposable, Mockito.never()).dispose();
    }

    @Test
    public void onDestroy_nullDisposable_isNoop() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        setField(f, "mListsDisposable", null);
        f.onDestroy();
    }

    @Test
    public void onDestroy_alreadyDisposedDisposable_skipsDispose() throws Exception {
        StarredRepositoryListFragment f =
                attach(StarredRepositoryListFragment.newInstance("alice"));
        io.reactivex.disposables.Disposable disposable =
                mock(io.reactivex.disposables.Disposable.class);
        when(disposable.isDisposed()).thenReturn(true);
        setField(f, "mListsDisposable", disposable);
        f.onDestroy();
        Mockito.verify(disposable, Mockito.never()).dispose();
    }

    @Test
    public void onViewCreatedAndRecyclerViewInflated_buildsHeaderAndLoadsLists()
            throws Exception {
        try (MockedStatic<UserListsGraphQL> listsMock = mockFetchLists(
                Single.just(java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Arrays.asList(
                                listRepo("a/b"))))));
             MockedStatic<ServiceFactory> serviceMock = mockStarringServiceEmpty()) {
            StarredRepositoryListFragment f =
                    attachResumed(StarredRepositoryListFragment.newInstance("alice"));
            awaitListsLoaded(f);
            assertNotNull(field(f, "mListsContainer"));
            assertNotNull(field(f, "mAdapter"));
            assertEquals(1, ((java.util.List<?>) field(f, "mOfficialLists")).size());
            android.view.ViewGroup container =
                    (android.view.ViewGroup) field(f, "mListsContainer");
            assertEquals(1, container.getChildCount());
        }
    }

    @Test
    public void loadOfficialLists_secondCallAfterCompleted_skipsDispose() throws Exception {
        try (MockedStatic<UserListsGraphQL> mocked = mockFetchLists(
                Single.just(java.util.Collections.emptyList()))) {
            StarredRepositoryListFragment f = attachWithHeader();
            callVoid(f, "loadOfficialLists", new Class<?>[]{});
            awaitListsLoaded(f);
            io.reactivex.disposables.Disposable first =
                    (io.reactivex.disposables.Disposable) field(f, "mListsDisposable");
            assertNotNull(first);
            // 首个 Single 已完成、订阅自动 dispose：再调不重复 dispose。
            assertTrue(first.isDisposed());
            callVoid(f, "loadOfficialLists", new Class<?>[]{});
            assertTrue(first.isDisposed());
            assertTrue(field(f, "mListsDisposable") != first);
        }
    }

    @Test
    public void loadOfficialLists_secondCall_disposesFirst() throws Exception {
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockFetchLists(Single.never())) {
            StarredRepositoryListFragment f = attachWithHeader();
            callVoid(f, "loadOfficialLists", new Class<?>[]{});
            io.reactivex.disposables.Disposable first =
                    (io.reactivex.disposables.Disposable) field(f, "mListsDisposable");
            assertNotNull(first);
            assertFalse(first.isDisposed());
            callVoid(f, "loadOfficialLists", new Class<?>[]{});
            assertTrue(first.isDisposed());
            assertNotNull(field(f, "mListsDisposable"));
        }
    }

    @Test
    public void longClickListener_showsAssignDialog() throws Exception {
        try (MockedStatic<UserListsGraphQL> listsMock = mockFetchLists(
                Single.just(java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))));
             MockedStatic<ServiceFactory> serviceMock = mockStarringServiceEmpty()) {
            StarredRepositoryListFragment f =
                    attachResumed(StarredRepositoryListFragment.newInstance("alice"));
            awaitListsLoaded(f);
            Object adapter = field(f, "mAdapter");
            java.lang.reflect.Field listenerField =
                    com.gh4a.adapter.RootAdapter.class
                            .getDeclaredField("mItemLongClickListener");
            listenerField.setAccessible(true);
            @SuppressWarnings("unchecked")
            com.gh4a.adapter.RootAdapter.OnItemLongClickListener<Repository> listener =
                    (com.gh4a.adapter.RootAdapter.OnItemLongClickListener<Repository>)
                            listenerField.get(adapter);
            assertNotNull(listener);
            int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
            assertTrue(listener.onItemLongClick(repo("a/b")));
            assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                    > before);
        }
    }

    @Test
    public void renderListsHeader_nullActivityIsNoop() throws Exception {
        StarredRepositoryListFragment f =
                StarredRepositoryListFragment.newInstance("alice");
        android.view.View header = android.view.LayoutInflater.from(mActivity)
                .inflate(R.layout.star_lists_header, null);
        setField(f, "mListsContainer", header.findViewById(R.id.lists_container));
        callVoid(f, "renderListsHeader", new Class<?>[]{});
    }

    @Test
    public void renderListsHeader_groupRowClick_expandsAndCollapses() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(1, container.getChildCount());
        container.getChildAt(0).performClick();
        assertEquals("l1", field(f, "mExpandedOfficialId"));
        assertEquals(3, container.getChildCount());
        assertEquals("a/b",
                ((android.widget.TextView) container.getChildAt(1)
                        .findViewById(R.id.tv_title)).getText().toString());
        container.getChildAt(0).performClick();
        assertNull(field(f, "mExpandedOfficialId"));
        assertEquals(1, container.getChildCount());
    }

    @Test
    public void renderListsHeader_localGroups_showsGroupsWithFailedStatus()
            throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", true);
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        assertEquals(mActivity.getString(R.string.local_groups),
                ((android.widget.TextView) field(f, "mListsTitle")).getText().toString());
        android.widget.TextView status =
                (android.widget.TextView) field(f, "mListsStatus");
        assertEquals(android.view.View.VISIBLE, status.getVisibility());
        assertEquals(mActivity.getString(R.string.my_lists_failed),
                status.getText().toString());
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void renderListsHeader_localGroups_hidesStatusWhenNotFailed()
            throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", false);
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        assertEquals(mActivity.getString(R.string.local_groups),
                ((android.widget.TextView) field(f, "mListsTitle")).getText().toString());
        assertEquals(android.view.View.GONE,
                ((android.widget.TextView) field(f, "mListsStatus")).getVisibility());
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void renderListsHeader_expandedLocalGroup_addsRepoRows() throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", false);
        setField(f, "mExpandedLocalGroup", "Work");
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(3, container.getChildCount());
        assertEquals("a/b",
                ((android.widget.TextView) container.getChildAt(1)
                        .findViewById(R.id.tv_title)).getText().toString());
    }

    @Test
    public void renderListsHeader_localGroupRowClick_togglesExpansion() throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", false);
        callVoid(f, "renderListsHeader", new Class<?>[]{});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(2, container.getChildCount());
        container.getChildAt(0).performClick();
        assertEquals("Work", field(f, "mExpandedLocalGroup"));
        assertEquals(3, container.getChildCount());
        container.getChildAt(0).performClick();
        assertNull(field(f, "mExpandedLocalGroup"));
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void addGroupRow_nullSubtitleHidesSubtitle() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addGroupRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class, boolean.class, Runnable.class},
                inflater, "Group 1", null, false, (Runnable) () -> {});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(android.view.View.GONE,
                container.getChildAt(0).findViewById(R.id.tv_subtitle)
                        .getVisibility());
    }

    @Test
    public void addGroupRow_emptySubtitleHidesSubtitle() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addGroupRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class, boolean.class, Runnable.class},
                inflater, "Group 1", "", false, (Runnable) () -> {});
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(android.view.View.GONE,
                container.getChildAt(0).findViewById(R.id.tv_subtitle)
                        .getVisibility());
    }

    @Test
    public void addGroupRow_clickRunsCallback() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        java.util.concurrent.atomic.AtomicInteger clicks =
                new java.util.concurrent.atomic.AtomicInteger();
        callVoid(f, "addGroupRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class, boolean.class, Runnable.class},
                inflater, "Group 1", "1 个仓库", false,
                (Runnable) clicks::incrementAndGet);
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        container.getChildAt(0).performClick();
        assertEquals(1, clicks.get());
    }

    @Test
    public void addRepoRow_nullDescriptionHidesSubtitle() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addRepoRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class},
                inflater, "a/b", null);
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        assertEquals(android.view.View.GONE,
                container.getChildAt(0).findViewById(R.id.tv_subtitle)
                        .getVisibility());
    }

    @Test
    public void addRepoRow_clickWithSlash_startsRepositoryActivity() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addRepoRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class},
                inflater, "a/b", "desc");
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        container.getChildAt(0).performClick();
        android.content.Intent started =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(RepositoryActivity.class.getName(),
                started.getComponent().getClassName());
        assertEquals("a", started.getStringExtra("owner"));
        assertEquals("b", started.getStringExtra("repo"));
    }

    @Test
    public void addRepoRow_clickWithoutSlash_doesNothing() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        android.view.LayoutInflater inflater =
                android.view.LayoutInflater.from(mActivity);
        callVoid(f, "addRepoRow",
                new Class<?>[]{android.view.LayoutInflater.class, String.class,
                        String.class},
                inflater, "noslash", "desc");
        android.view.ViewGroup container =
                (android.view.ViewGroup) field(f, "mListsContainer");
        container.getChildAt(0).performClick();
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void showAssignToGroupDialog_nullListsShowsLocalDialog() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", null);
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        callVoid(f, "showAssignToGroupDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showAssignToOfficialListsDialog_mismatchedRepoLeavesUnchecked()
            throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("x/y"), listRepo("a/b"))),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "showAssignToOfficialListsDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        // 不动任何勾选直接点确定：a/b 在 l1 里（第二轮才命中，365 假半边
        // 与 369 回边都走过），l2 未勾——归属的 listIds 应只有 l1。
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
        ArgumentCaptor<Single<?>> captor = ArgumentCaptor.forClass(Single.class);
        Mockito.verify(vm).runSingleOp(captor.capture(),
                eq(R.string.assign_official_success),
                eq(R.string.assign_official_failed), Mockito.isNull());
        try (MockedStatic<GraphQLClient> gql = mockGraphQL()) {
            @SuppressWarnings("unchecked")
            Single<Boolean> assigned = (Single<Boolean>) captor.getValue();
            assigned.test().assertValue(true);
            ArgumentCaptor<JSONObject> varsCaptor =
                    ArgumentCaptor.forClass(JSONObject.class);
            gql.verify(() -> GraphQLClient.execute(any(), varsCaptor.capture(), any()),
                    Mockito.atLeastOnce());
            boolean found = false;
            for (JSONObject vars : varsCaptor.getAllValues()) {
                if (vars.has("listIds")) {
                    assertEquals("[\"l1\"]",
                            vars.getJSONArray("listIds").toString());
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void showAssignToOfficialListsDialog_toggleAndConfirm_assignsSelected()
            throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b"))),
                list("l2", "Group 2", java.util.Collections.emptyList()),
                list("l3", "Group 3", java.util.Collections.emptyList())));
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "showAssignToOfficialListsDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        // l2 本来未勾：点一下勾上；l3 保持未勾。
        clickListItem(dialog, 1);
        assertTrue(dialog.getListView().isItemChecked(1));
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
        ArgumentCaptor<Single<?>> captor = ArgumentCaptor.forClass(Single.class);
        Mockito.verify(vm).runSingleOp(captor.capture(),
                eq(R.string.assign_official_success),
                eq(R.string.assign_official_failed), Mockito.isNull());
        try (MockedStatic<GraphQLClient> gql = mockGraphQL()) {
            @SuppressWarnings("unchecked")
            Single<Boolean> assigned = (Single<Boolean>) captor.getValue();
            assigned.test().assertValue(true);
            // 归属 mutation 的 listIds 应为 l1、l2（l3 未勾不入选）。
            ArgumentCaptor<JSONObject> varsCaptor =
                    ArgumentCaptor.forClass(JSONObject.class);
            gql.verify(() -> GraphQLClient.execute(any(), varsCaptor.capture(), any()),
                    Mockito.atLeastOnce());
            boolean found = false;
            for (JSONObject vars : varsCaptor.getAllValues()) {
                if (vars.has("listIds")) {
                    assertEquals("[\"l1\",\"l2\"]",
                            vars.getJSONArray("listIds").toString());
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void showAssignToOfficialListsDialog_neutralButton_opensCreateDialog()
            throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        callVoid(f, "showAssignToOfficialListsDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        AlertDialog dialog = (AlertDialog) latestDialog();
        clickDialogButton(dialog, AlertDialog.BUTTON_NEUTRAL);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showCreateOfficialListDialog_confirmWithName_runsCreateAndAssign()
            throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "showCreateOfficialListDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        android.widget.EditText input =
                findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("My List");
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
        ArgumentCaptor<Single<?>> captor = ArgumentCaptor.forClass(Single.class);
        Mockito.verify(vm).runSingleOp(captor.capture(),
                eq(R.string.assign_official_success),
                eq(R.string.assign_official_failed), Mockito.isNull());
        try (MockedStatic<GraphQLClient> gql = mockGraphQL()) {
            @SuppressWarnings("unchecked")
            Single<Boolean> created = (Single<Boolean>) captor.getValue();
            created.test().assertValue(true);
            // 建组 mutation 的 name 应为输入值。
            ArgumentCaptor<JSONObject> varsCaptor =
                    ArgumentCaptor.forClass(JSONObject.class);
            gql.verify(() -> GraphQLClient.execute(any(), varsCaptor.capture(), any()),
                    Mockito.atLeastOnce());
            boolean found = false;
            for (JSONObject vars : varsCaptor.getAllValues()) {
                if ("My List".equals(vars.optString("name"))) {
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void showCreateOfficialListDialog_emptyName_doesNothing() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "showCreateOfficialListDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        android.widget.EditText input =
                findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("");
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
        Mockito.verify(vm, Mockito.never()).runSingleOp(
                any(), Mockito.anyInt(), Mockito.anyInt(), any());
    }

    @Test
    public void showCreateOfficialListDialog_nullViewModel_doesNothing() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mMoveViewModel", null);
        callVoid(f, "showCreateOfficialListDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        android.widget.EditText input =
                findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("X");
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
    }

    @Test
    public void assignToOfficialLists_nullViewModel_isNoop() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mMoveViewModel", null);
        callVoid(f, "assignToOfficialLists",
                new Class<?>[]{String.class, java.util.List.class},
                "a/b", java.util.Arrays.asList("l1"));
    }

    @Test
    public void onSingleOpChanged_nullViewModel_isNoop() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        setField(f, "mMoveViewModel", null);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, R.string.app_name, null);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        assertNull(field(f, "mOfficialLists"));
    }

    @Test
    public void onSingleOpChanged_detachedFragment_isNoop() throws Exception {
        StarredRepositoryListFragment f =
                StarredRepositoryListFragment.newInstance("alice");
        StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, R.string.app_name, null);
        when(vm.isSingleOpConsumed(op)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        callVoid(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        Mockito.verify(vm, Mockito.never()).markSingleOpConsumed(any());
    }

    @Test
    public void onSingleOpChanged_successOp_toastsShortAndReloads() throws Exception {
        try (MockedStatic<UserListsGraphQL> mocked = mockFetchLists(
                Single.just(java.util.Collections.emptyList()))) {
            StarredRepositoryListFragment f = attachWithHeader();
            StarBatchMoveViewModel vm = mock(StarBatchMoveViewModel.class);
            StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                    1L, true, R.string.app_name, null);
            when(vm.isSingleOpConsumed(op)).thenReturn(false);
            setField(f, "mMoveViewModel", vm);
            setField(f, "mOfficialLists", null);
            callVoid(f, "onSingleOpChanged",
                    new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
            Mockito.verify(vm).markSingleOpConsumed(op);
            assertEquals(mActivity.getString(R.string.app_name),
                    org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
            awaitListsLoaded(f);
            assertEquals(0, ((java.util.List<?>) field(f, "mOfficialLists")).size());
        }
    }

    @Test
    public void showAssignToLocalGroupDialog_currentGroupChecked() throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "showAssignToLocalGroupDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        android.widget.ListView listView = dialog.getListView();
        assertTrue(listView.isItemChecked(0));
        assertFalse(listView.isItemChecked(1));
        assertFalse(listView.isItemChecked(2));
    }

    @Test
    public void showAssignToLocalGroupDialog_pickGroup_assignsRepo() throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "showAssignToLocalGroupDialog",
                new Class<?>[]{Repository.class}, repo("c/d"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        clickListItem(dialog, 0);
        assertEquals("Work",
                StarGroupManager.findGroupFor(mActivity, "alice", "c/d"));
    }

    @Test
    public void showAssignToLocalGroupDialog_pickCreate_opensCreateDialog()
            throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "showAssignToLocalGroupDialog",
                new Class<?>[]{Repository.class}, repo("c/d"));
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        AlertDialog dialog = (AlertDialog) latestDialog();
        clickListItem(dialog, 2);
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showCreateAndAssignDialog_confirmNewGroup_assignsRepo() throws Exception {
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "showCreateAndAssignDialog",
                new Class<?>[]{Repository.class}, repo("e/f"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        android.widget.EditText input =
                findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("NewG");
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
        assertEquals("NewG",
                StarGroupManager.findGroupFor(mActivity, "alice", "e/f"));
    }

    @Test
    public void showCreateAndAssignDialog_existingName_doesNotAssign() throws Exception {
        seedLocalGroups();
        StarredRepositoryListFragment f = attachWithHeader();
        callVoid(f, "showCreateAndAssignDialog",
                new Class<?>[]{Repository.class}, repo("g/h"));
        AlertDialog dialog = (AlertDialog) latestDialog();
        android.widget.EditText input =
                findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("Work");
        clickDialogButton(dialog, AlertDialog.BUTTON_POSITIVE);
        assertNull(StarGroupManager.findGroupFor(mActivity, "alice", "g/h"));
    }
}
