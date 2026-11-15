package com.gh4a.fragment;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.service.activity.StarringService;

import io.reactivex.Single;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import retrofit2.Response;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** StarMergedFragment：Star 合并页（Robolectric，CREATED 态挂载）。 */
@RunWith(RobolectricTestRunner.class)
public class StarMergedFragmentTest {
    private FragmentActivity mActivity;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).setup().get();
    }

    // Fragment.SavedState(Bundle) 在此 androidx 版本是包私有构造，
    // 只能反射创建；用于给未挂载的 Fragment 预置恢复状态。
    private static androidx.fragment.app.Fragment.SavedState savedState(
            android.os.Bundle bundle) throws Exception {
        java.lang.reflect.Constructor<androidx.fragment.app.Fragment.SavedState> c =
                androidx.fragment.app.Fragment.SavedState.class
                        .getDeclaredConstructor(android.os.Bundle.class);
        c.setAccessible(true);
        return c.newInstance(bundle);
    }

    private <F extends Fragment> F attach(F fragment) {
        mActivity.getSupportFragmentManager().beginTransaction().add(fragment, "test")
                .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow();
        return fragment;
    }

    private static String updateKey(ReleaseRadarFragment.RadarItem radar) throws Exception {
        Method m = StarMergedFragment.class.getDeclaredMethod(
                "updateKey", ReleaseRadarFragment.RadarItem.class);
        m.setAccessible(true);
        return (String) m.invoke(null, radar);
    }

    private static Object field(StarMergedFragment f, String name) throws Exception {
        Field field = StarMergedFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
    }

    private static void setField(StarMergedFragment f, String name, Object value)
            throws Exception {
        Field field = StarMergedFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private static Object call(StarMergedFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = StarMergedFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(f, args);
    }

    private static ReleaseRadarFragment.RadarItem radar(String owner, String repo, long id) {
        return new ReleaseRadarFragment.RadarItem(
                owner, repo, id, "v1", "r1", 1000L, 900L);
    }

    @Test
    public void newInstance_storesLogin() {
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        assertEquals("alice", f.getArguments().getString("user"));
    }

    @Test
    public void onCreate_readsLoginAndDefaults() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        assertEquals("alice", field(f, "mLogin"));
        assertEquals("created", field(f, "mSortOrder"));
        assertEquals("desc", field(f, "mSortDirection"));
        assertEquals(0, field(f, "mCurrentTab"));
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void onCreate_restoresSortTabAndMultiSelectFromSavedState() throws Exception {
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        Bundle saved = new Bundle();
        saved.putString("star_sort_order", "updated");
        saved.putString("star_sort_direction", "asc");
        saved.putInt("star_current_tab", 2);
        saved.putBoolean("star_multi_select", true);
        saved.putStringArrayList("star_selected_repos",
                new java.util.ArrayList<>(java.util.Arrays.asList("a/b", "c/d")));
        // 未挂载的 Fragment 直接调 onCreate(saved) 会在 Fragment.onCreate 内
        // 触 mNonConfig 抛 NPE；正确做法是 setInitialSavedState 后挂载。
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertEquals("updated", field(f, "mSortOrder"));
        assertEquals("asc", field(f, "mSortDirection"));
        assertEquals(2, field(f, "mCurrentTab"));
        assertTrue((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void onCreate_multiSelectWithEmptySelectionIsCleared() throws Exception {
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        Bundle saved = new Bundle();
        saved.putBoolean("star_multi_select", true);
        saved.putStringArrayList("star_selected_repos", new java.util.ArrayList<>());
        f.setInitialSavedState(savedState(saved));
        attach(f);
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void onSaveInstanceState_persistsSortTabAndSelection() {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("created", out.getString("star_sort_order"));
        assertEquals("desc", out.getString("star_sort_direction"));
        assertEquals(0, out.getInt("star_current_tab"));
        assertTrue(out.containsKey("star_lists_loaded"));
        assertTrue(out.containsKey("star_updates_loaded"));
        assertFalse(out.getBoolean("star_multi_select"));
        assertTrue(out.getStringArrayList("star_selected_repos").isEmpty());
    }

    @Test
    public void updateKey_formatIsOwnerRepoHashReleaseId() throws Exception {
        ReleaseRadarFragment.RadarItem radar = new ReleaseRadarFragment.RadarItem(
                "octo", "cat", 99L, "v1", "r1", 1000L, 900L);
        assertEquals("octo/cat#99", updateKey(radar));
    }

    @Test
    public void isUpdateRead_emptyReadSetIsFalse() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        Method m = StarMergedFragment.class.getDeclaredMethod(
                "isUpdateRead", ReleaseRadarFragment.RadarItem.class);
        m.setAccessible(true);
        ReleaseRadarFragment.RadarItem radar = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "r1", 1L, 1L);
        assertFalse((Boolean) m.invoke(f, radar));
        assertFalse((Boolean) m.invoke(f, (Object) null));
    }

    // ---- 已读标记（StarReadState 落 prefs；mAdapter 为 null 时 rebuild 直接返回） ----

    @Test
    public void markUpdateRead_addsKey() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        ReleaseRadarFragment.RadarItem item = radar("octo", "cat", 99L);
        call(f, "markUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, item);
        assertTrue((Boolean) call(f, "isUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, item));
        // 重复标记不重复写
        call(f, "markUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, item);
        assertTrue((Boolean) call(f, "isUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, item));
    }

    @Test
    public void markUpdateRead_nullRadarIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "markUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, (Object) null);
    }

    @Test
    public void markAllUpdatesRead_marksEveryUpdate() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        ReleaseRadarFragment.RadarItem a = radar("o", "r1", 1L);
        ReleaseRadarFragment.RadarItem b = radar("o", "r2", 2L);
        setField(f, "mUpdates", new java.util.ArrayList<>(
                java.util.Arrays.asList(a, b)));
        call(f, "markAllUpdatesRead", new Class<?>[]{});
        assertTrue((Boolean) call(f, "isUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, a));
        assertTrue((Boolean) call(f, "isUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, b));
    }

    @Test
    public void markAllUpdatesRead_nullUpdatesIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mUpdates", null);
        call(f, "markAllUpdatesRead", new Class<?>[]{});
    }

    // ---- 取消请求与切段（三页已加载门控全开时不发网络） ----

    @Test
    public void cancelRequests_clearFlagsAndBumpGeneration() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mListsLoading", true);
        setField(f, "mStarredLoading", true);
        setField(f, "mUpdatesLoading", true);
        int listsGen = (int) field(f, "mListsGeneration");
        int starredGen = (int) field(f, "mStarredGeneration");
        int updatesGen = (int) field(f, "mUpdatesGeneration");
        call(f, "cancelListsRequest", new Class<?>[]{});
        call(f, "cancelStarredRequest", new Class<?>[]{});
        call(f, "cancelUpdatesRequest", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mListsLoading"));
        assertFalse((Boolean) field(f, "mStarredLoading"));
        assertFalse((Boolean) field(f, "mUpdatesLoading"));
        assertEquals(listsGen + 1, (int) field(f, "mListsGeneration"));
        assertEquals(starredGen + 1, (int) field(f, "mStarredGeneration"));
        assertEquals(updatesGen + 1, (int) field(f, "mUpdatesGeneration"));
    }

    @Test
    public void selectTab_allLoaded_switchesWithoutNetwork() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mListsLoaded", true);
        setField(f, "mStarredLoaded", true);
        setField(f, "mUpdatesLoaded", true);
        call(f, "selectTab", new Class<?>[]{int.class}, 1);
        assertEquals(1, field(f, "mCurrentTab"));
        call(f, "selectTab", new Class<?>[]{int.class}, 2);
        assertEquals(2, field(f, "mCurrentTab"));
        // 同页重选是 no-op
        call(f, "selectTab", new Class<?>[]{int.class}, 2);
        assertEquals(2, field(f, "mCurrentTab"));
    }

    @Test
    public void switchStarTab_stepsWithinBounds() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mListsLoaded", true);
        setField(f, "mStarredLoaded", true);
        setField(f, "mUpdatesLoaded", true);
        call(f, "switchStarTab", new Class<?>[]{int.class}, 1);
        assertEquals(1, field(f, "mCurrentTab"));
        call(f, "switchStarTab", new Class<?>[]{int.class}, 1);
        assertEquals(2, field(f, "mCurrentTab"));
        // 末段不再前进
        call(f, "switchStarTab", new Class<?>[]{int.class}, 1);
        assertEquals(2, field(f, "mCurrentTab"));
        call(f, "switchStarTab", new Class<?>[]{int.class}, -1);
        assertEquals(1, field(f, "mCurrentTab"));
        call(f, "switchStarTab", new Class<?>[]{int.class}, -1);
        assertEquals(0, field(f, "mCurrentTab"));
        // 首段不再后退
        call(f, "switchStarTab", new Class<?>[]{int.class}, -1);
        assertEquals(0, field(f, "mCurrentTab"));
    }

    @Test
    public void stopRefreshingFor_noSwipeIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mRefreshActive", true);
        call(f, "stopRefreshingFor", new Class<?>[]{int.class}, 0);
        assertTrue((Boolean) field(f, "mRefreshActive"));
    }

    // ---- build*Section / computeUngrouped / applySavedListOrder（纯字段逻辑） ----

    private static int typeConst(String name) throws Exception {
        Field f = StarMergedFragment.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> items(StarMergedFragment f) throws Exception {
        return (java.util.List<Object>) field(f, "mItems");
    }

    private static int itemType(Object item) throws Exception {
        Field f = item.getClass().getDeclaredField("type");
        f.setAccessible(true);
        return f.getInt(item);
    }

    private static String itemStr(Object item, String name) throws Exception {
        Field f = item.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (String) f.get(item);
    }

    private static boolean itemBool(Object item, String name) throws Exception {
        Field f = item.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(item);
    }

    private static java.util.List<Integer> itemTypes(StarMergedFragment f) throws Exception {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (Object item : items(f)) {
            out.add(itemType(item));
        }
        return out;
    }

    private static com.meisolsson.githubsdk.model.Repository repo(String fullName) {
        String[] parts = fullName.split("/", 2);
        return com.meisolsson.githubsdk.model.Repository.builder()
                .fullName(fullName)
                .owner(com.meisolsson.githubsdk.model.User.builder().login(parts[0]).build())
                .name(parts[1])
                .isPrivate(false)
                .isFork(false)
                .stargazersCount(0)
                .forksCount(0)
                .size(0)
                .build();
    }

    private static UserListsGraphQL.UserList list(String id, String name,
            java.util.List<UserListsGraphQL.Repo> repos) {
        return new UserListsGraphQL.UserList(id, name, "desc-" + id, repos);
    }

    private static UserListsGraphQL.Repo listRepo(String fullName) {
        return new UserListsGraphQL.Repo(fullName, "d-" + fullName);
    }

    @Test
    public void buildListsSection_nullListsAddsNothing() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "buildListsSection", new Class<?>[]{});
        assertTrue(items(f).isEmpty());
    }

    @Test
    public void buildListsSection_emptyListsShowsEmptyStatusAndAddGroup() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_STATUS"), typeConst("TYPE_ADD_GROUP")), itemTypes(f));
        assertEquals(mActivity.getString(com.gh4a.R.string.my_lists_empty),
                itemStr(items(f).get(0), "title"));
    }

    @Test
    public void buildListsSection_emptyListsFailedShowsFailedStatus() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mListsFailed", true);
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(mActivity.getString(com.gh4a.R.string.my_lists_failed),
                itemStr(items(f).get(0), "title"));
    }

    @Test
    public void buildListsSection_sortModeDelegatesToSortSection() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_SORT_HEADER"), typeConst("TYPE_SORT_ROW"),
                typeConst("TYPE_SORT_ROW"), typeConst("TYPE_ACTION_BAR")), itemTypes(f));
        assertEquals("l1", itemStr(items(f).get(1), "key"));
        assertEquals("l2", itemStr(items(f).get(2), "key"));
    }

    @Test
    public void buildListsSection_multiSelectAddsHeaderAndActionBarNoAddGroup()
            throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_SELECTION_HEADER"), typeConst("TYPE_STATUS"),
                typeConst("TYPE_ACTION_BAR")), itemTypes(f));
    }

    @Test
    public void buildListsSection_expandedGroupAddsRepoItems() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        setField(f, "mExpandedOfficialId", "l1");
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_LIST"), typeConst("TYPE_LIST_REPO"),
                typeConst("TYPE_LIST_REPO"), typeConst("TYPE_ADD_GROUP")), itemTypes(f));
        assertEquals("l1", itemStr(items(f).get(0), "key"));
        assertTrue(itemBool(items(f).get(0), "expanded"));
        assertEquals("a/b", itemStr(items(f).get(1), "fullName"));
    }

    @Test
    public void buildListsSection_ungroupedSectionAppearsForUnlistedStarred() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("a/b"));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("x/y"));
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_LIST"), typeConst("TYPE_UNGROUPED"),
                typeConst("TYPE_ADD_GROUP")), itemTypes(f));
        // 未分组区副标题为 1 条的复数文案
        assertTrue(itemStr(items(f).get(1), "subtitle").contains("1"));
    }

    @Test
    public void buildListsSection_ungroupedExpandedAddsRepoItemsAndPartialNote()
            throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("a/b"));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("x/y"));
        setField(f, "mUngroupedExpanded", true);
        setField(f, "mStarredHasMore", true);
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_LIST"), typeConst("TYPE_UNGROUPED"),
                typeConst("TYPE_LIST_REPO"), typeConst("TYPE_ADD_GROUP")), itemTypes(f));
        // 未加载完时未分组区有“仅显示已加载”说明
        assertTrue(itemStr(items(f).get(1), "description") != null
                && itemStr(items(f).get(1), "description").contains("2"));
    }

    @Test
    public void buildListsSection_failedWithNoCacheShowsRetryRow() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        setField(f, "mListsFailed", true);
        call(f, "buildListsSection", new Class<?>[]{});
        // 无缓存时年龄 -1 → 顶部放重试提示行
        assertEquals(typeConst("TYPE_STATUS"), itemType(items(f).get(0)));
        assertTrue(itemBool(items(f).get(0), "retryLists"));
    }

    @Test
    public void buildStarredSection_emptyNotLoadingShowsEmptyStatus() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "buildStarredSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(typeConst("TYPE_STATUS")), itemTypes(f));
        assertFalse(itemBool(items(f).get(0), "retryStarred"));
    }

    @Test
    public void buildStarredSection_emptyFailedShowsRetryStatus() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mStarredFailed", true);
        call(f, "buildStarredSection", new Class<?>[]{});
        assertTrue(itemBool(items(f).get(0), "retryStarred"));
    }

    @Test
    public void buildStarredSection_withReposAddsRepoItems() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("a/b"));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("c/d"));
        call(f, "buildStarredSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_STARRED_REPO"), typeConst("TYPE_STARRED_REPO")),
                itemTypes(f));
    }

    @Test
    public void buildUpdatesSection_nullShowsLoading() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(typeConst("TYPE_STATUS")), itemTypes(f));
        assertEquals(mActivity.getString(com.gh4a.R.string.loading),
                itemStr(items(f).get(0), "title"));
    }

    @Test
    public void buildUpdatesSection_emptyShowsEmptyStatus() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mUpdates", new java.util.ArrayList<>());
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(typeConst("TYPE_STATUS")), itemTypes(f));
        assertFalse(itemBool(items(f).get(0), "retryUpdates"));
    }

    @Test
    public void buildUpdatesSection_emptyFailedShowsRetryStatus() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mUpdates", new java.util.ArrayList<>());
        setField(f, "mUpdatesFailed", true);
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertTrue(itemBool(items(f).get(0), "retryUpdates"));
    }

    @Test
    public void buildUpdatesSection_withUnreadShowsMarkAllReadHeader() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mUpdates", java.util.Arrays.asList(radar("octo", "cat", 99L)));
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(
                typeConst("TYPE_SECTION"), typeConst("TYPE_UPDATE")), itemTypes(f));
        assertTrue(itemBool(items(f).get(0), "markAllRead"));
    }

    @Test
    public void buildUpdatesSection_allReadHidesMarkAllReadHeader() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mUpdates", java.util.Arrays.asList(radar("octo", "cat", 99L)));
        ((java.util.Set<String>) field(f, "mReadUpdateKeys")).add("octo/cat#99");
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertEquals(java.util.Arrays.asList(typeConst("TYPE_UPDATE")), itemTypes(f));
    }

    @Test
    public void computeUngrouped_excludesGroupedRepos() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("a/b"));
        ((java.util.List<com.meisolsson.githubsdk.model.Repository>) field(f, "mStarred"))
                .add(repo("x/y"));
        java.util.List<UserListsGraphQL.Repo> out =
                (java.util.List<UserListsGraphQL.Repo>) call(
                        f, "computeUngrouped", new Class<?>[]{});
        assertEquals(1, out.size());
        assertEquals("x/y", out.get(0).fullName);
    }

    @Test
    public void applySavedListOrder_reordersBySavedIds() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("a", "A", java.util.Collections.emptyList()),
                list("b", "B", java.util.Collections.emptyList()),
                list("c", "C", java.util.Collections.emptyList()))));
        mActivity.getSharedPreferences("star_list_order_alice",
                        android.content.Context.MODE_PRIVATE)
                .edit().putString("order_ids", "[\"b\",\"a\"]").apply();
        call(f, "applySavedListOrder", new Class<?>[]{});
        java.util.List<UserListsGraphQL.UserList> ordered =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mOfficialLists");
        assertEquals("b", ordered.get(0).id);
        assertEquals("a", ordered.get(1).id);
        // 不在保存顺序里的新列表排最后
        assertEquals("c", ordered.get(2).id);
    }

    @Test
    public void applySavedListOrder_invalidJsonKeepsOrder() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("a", "A", java.util.Collections.emptyList()),
                list("b", "B", java.util.Collections.emptyList()))));
        mActivity.getSharedPreferences("star_list_order_alice",
                        android.content.Context.MODE_PRIVATE)
                .edit().putString("order_ids", "not-json").apply();
        call(f, "applySavedListOrder", new Class<?>[]{});
        java.util.List<UserListsGraphQL.UserList> ordered =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mOfficialLists");
        assertEquals("a", ordered.get(0).id);
        assertEquals("b", ordered.get(1).id);
    }

    @Test
    public void applySavedListOrder_noSavedKeepsOrder() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("a", "A", java.util.Collections.emptyList()),
                list("b", "B", java.util.Collections.emptyList()))));
        call(f, "applySavedListOrder", new Class<?>[]{});
        java.util.List<UserListsGraphQL.UserList> ordered =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mOfficialLists");
        assertEquals("a", ordered.get(0).id);
        assertEquals("b", ordered.get(1).id);
    }

    // ---- load* 空登录守卫 / refresh / prefetch / 移组状态机（无网络） ----

    private static StarBatchMoveViewModel.State moveState(int total, int done,
            boolean finished, java.util.List<String> failed,
            java.util.List<String> targetIds) {
        return new StarBatchMoveViewModel.State(
                total, done, finished, failed, targetIds, 1L);
    }

    @Test
    public void loadOfficialLists_nullLoginFailsFast() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
        assertTrue((Boolean) field(f, "mListsFailed"));
        assertFalse((Boolean) field(f, "mListsLoading"));
        assertTrue(((java.util.List<?>) field(f, "mOfficialLists")).isEmpty());
    }

    @Test
    public void loadStarredPage_nullLoginFailsFast() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        call(f, "loadStarredPage",
                new Class<?>[]{boolean.class, boolean.class}, true, true);
        assertTrue((Boolean) field(f, "mStarredFailed"));
        assertFalse((Boolean) field(f, "mStarredLoading"));
    }

    @Test
    public void loadUpdates_nullLoginFailsFast() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
        assertTrue((Boolean) field(f, "mUpdatesFailed"));
        assertFalse((Boolean) field(f, "mUpdatesLoading"));
        assertTrue(((java.util.List<?>) field(f, "mUpdates")).isEmpty());
    }

    @Test
    public void refreshCurrentTab_listsTabResetsAndReloads() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 0);
        setField(f, "mListsLoaded", true);
        call(f, "refreshCurrentTab", new Class<?>[]{});
        assertEquals(0, field(f, "mRefreshTab"));
        assertTrue((Boolean) field(f, "mRefreshActive"));
        assertTrue((Boolean) field(f, "mRefreshPendingPrefetch"));
        assertFalse((Boolean) field(f, "mListsLoaded"));
        assertTrue((Boolean) field(f, "mListsFailed"));
    }

    @Test
    public void refreshCurrentTab_starredTabReloads() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 1);
        call(f, "refreshCurrentTab", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mStarredFailed"));
    }

    @Test
    public void refreshCurrentTab_updatesTabReloads() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 2);
        call(f, "refreshCurrentTab", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mUpdatesFailed"));
    }

    @Test
    public void prefetchOtherTabs_noForceSkipsLoadedPages() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mCurrentTab", 0);
        setField(f, "mStarredLoaded", true);
        setField(f, "mUpdatesLoaded", true);
        call(f, "prefetchOtherTabs", new Class<?>[]{boolean.class}, false);
        assertTrue((Boolean) field(f, "mStarredLoaded"));
        assertTrue((Boolean) field(f, "mUpdatesLoaded"));
    }

    @Test
    public void prefetchOtherTabs_noForceLoadsUnloadedPagesWithNullLogin() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 0);
        call(f, "prefetchOtherTabs", new Class<?>[]{boolean.class}, false);
        assertTrue((Boolean) field(f, "mStarredFailed"));
        assertTrue((Boolean) field(f, "mUpdatesFailed"));
    }

    @Test
    public void prefetchOtherTabs_forceSkipsPagedStarredList() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 0);
        setField(f, "mStarredLoaded", true);
        setField(f, "mStarredPage", 3);
        setField(f, "mUpdatesLoaded", true);
        call(f, "prefetchOtherTabs", new Class<?>[]{boolean.class}, true);
        // BUG-14：已翻到第 2 页之后强制预取不碰 Star 列表
        assertEquals(3, field(f, "mStarredPage"));
        assertTrue((Boolean) field(f, "mStarredLoaded"));
        assertFalse((Boolean) field(f, "mListsLoaded"));
    }

    @Test
    public void onMoveStateChanged_nullStateDismisses() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class}, (Object) null);
        assertEquals(null, field(f, "mMoveProgressDialog"));
    }

    @Test
    public void onMoveStateChanged_unfinishedShowsProgressDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class},
                moveState(3, 1, false, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList()));
        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog) field(f, "mMoveProgressDialog");
        assertTrue(dialog.isShowing());
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_moving, 1, 3),
                org.robolectric.Shadows.shadowOf(dialog).getTitle());
        assertEquals(1, ((android.widget.ProgressBar) field(f, "mMoveProgressBar"))
                .getProgress());
    }

    @Test
    public void onMoveStateChanged_finishedWithNullViewModelReturns() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class},
                moveState(3, 3, true, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList()));
        assertEquals(null, field(f, "mMoveProgressDialog"));
    }

    @Test
    public void onMoveStateChanged_finishedConsumedResultReturns() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm = org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.State state = moveState(3, 3, true,
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        org.mockito.Mockito.when(vm.isResultConsumed(state)).thenReturn(true);
        setField(f, "mMoveViewModel", vm);
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class}, state);
        assertEquals(null, field(f, "mMoveProgressDialog"));
        assertEquals(null, org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onMoveStateChanged_finishedAllSuccessToastsAndRefetches() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        StarBatchMoveViewModel vm = org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.State state = moveState(3, 3, true,
                java.util.Collections.emptyList(), java.util.Collections.emptyList());
        org.mockito.Mockito.when(vm.isResultConsumed(state)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        setField(f, "mMultiSelect", true);
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class}, state);
        org.mockito.Mockito.verify(vm).markResultConsumed(state);
        assertFalse((Boolean) field(f, "mMultiSelect"));
        // refetchLists 在空登录下直接失败态，不发网络
        assertTrue((Boolean) field(f, "mListsFailed"));
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_moved_title, 3, 3),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onSingleOpChanged_nullOpIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, (Object) null);
    }

    @Test
    public void onSingleOpChanged_consumedOpIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm = org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, com.gh4a.R.string.app_name, null);
        org.mockito.Mockito.when(vm.isSingleOpConsumed(op)).thenReturn(true);
        setField(f, "mMoveViewModel", vm);
        call(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .markSingleOpConsumed(org.mockito.Mockito.any());
    }

    @Test
    public void onSingleOpChanged_failedOpToastsWithoutRefetch() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm = org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, false, com.gh4a.R.string.app_name, null);
        org.mockito.Mockito.when(vm.isSingleOpConsumed(op)).thenReturn(false);
        setField(f, "mMoveViewModel", vm);
        call(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm).markSingleOpConsumed(op);
        assertEquals(mActivity.getString(com.gh4a.R.string.app_name),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        // 失败即返回，不触发 refetchLists
        assertEquals(null, field(f, "mOfficialLists"));
    }

    @Test
    public void showBatchMoveResult_failedShowsResultDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel.State state = moveState(3, 1, true,
                java.util.Arrays.asList("a/b", "c/d"),
                java.util.Arrays.asList("l1"));
        call(f, "showBatchMoveResult",
                new Class<?>[]{StarBatchMoveViewModel.State.class, int.class}, state, 1);
        androidx.appcompat.app.AlertDialog dialog =
                (androidx.appcompat.app.AlertDialog) field(f, "mMoveResultDialog");
        assertTrue(dialog.isShowing());
        // 标题经 ShadowAlertDialog 读不到（空串），断言消息体含失败仓库名
        android.widget.TextView messageView =
                dialog.findViewById(android.R.id.message);
        assertTrue(messageView.getText().toString().contains("a/b"));
        assertTrue(messageView.getText().toString().contains("c/d"));
    }

    @Test
    public void onRepoLongPressed_entersMultiSelectAndRebuilds() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        call(f, "onRepoLongPressed", new Class<?>[]{String.class}, "a/b");
        assertTrue((Boolean) field(f, "mMultiSelect"));
        assertTrue(((java.util.Set<String>) field(f, "mSelectedRepos")).contains("a/b"));
        // rebuild() 在 mAdapter 为 null（CREATED 态）时直接返回；直接建段验证输出
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(typeConst("TYPE_SELECTION_HEADER"), itemType(items(f).get(0)));
    }

    @Test
    public void enterSortMode_requiresTwoLists() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "enterSortMode", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mSortMode"));
    }

    @Test
    public void enterSortMode_withTwoListsEntersSortMode() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        call(f, "enterSortMode", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mSortMode"));
        assertEquals(2, ((java.util.List<?>) field(f, "mSortLists")).size());
    }

    @Test
    public void onOptionsItemSelected_nullOrderDelegatesToSuper() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        RepositoryListContainerFragment.SortDrawerHelper helper =
                org.mockito.Mockito.mock(
                        RepositoryListContainerFragment.SortDrawerHelper.class);
        android.view.MenuItem item = org.mockito.Mockito.mock(android.view.MenuItem.class);
        org.mockito.Mockito.when(helper.handleSelectionAndGetSortOrder(item))
                .thenReturn(null);
        setField(f, "mSortHelper", helper);
        assertFalse(f.onOptionsItemSelected(item));
    }

    @Test
    public void onOptionsItemSelected_validOrderSetsSortAndReloads() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        RepositoryListContainerFragment.SortDrawerHelper helper =
                org.mockito.Mockito.mock(
                        RepositoryListContainerFragment.SortDrawerHelper.class);
        android.view.MenuItem item = org.mockito.Mockito.mock(android.view.MenuItem.class);
        org.mockito.Mockito.when(helper.handleSelectionAndGetSortOrder(item))
                .thenReturn(new String[]{"updated", "asc"});
        setField(f, "mSortHelper", helper);
        assertTrue(f.onOptionsItemSelected(item));
        assertEquals("updated", field(f, "mSortOrder"));
        assertEquals("asc", field(f, "mSortDirection"));
        org.mockito.Mockito.verify(item).setChecked(true);
        // 空登录下 loadStarredPage 直接失败态，不发网络
        assertTrue((Boolean) field(f, "mStarredFailed"));
    }

    // ---- 第二批：网络加载 / 刷新 / 分段 / 杂项（mock 网络） ----

    private static void idle() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private static void awaitCondition(java.util.concurrent.Callable<Boolean> cond)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!cond.call()) {
            idle();
            if (System.currentTimeMillis() > deadline) break;
            Thread.sleep(20);
        }
    }

    private static AlertDialog latestDialog() {
        java.util.List<android.app.Dialog> shown =
                org.robolectric.shadows.ShadowDialog.getShownDialogs();
        return (AlertDialog) shown.get(shown.size() - 1);
    }

    private static MockedStatic<UserListsGraphQL> mockListsFetch(
            Single<java.util.List<UserListsGraphQL.UserList>> result) {
        MockedStatic<UserListsGraphQL> mocked =
                org.mockito.Mockito.mockStatic(UserListsGraphQL.class);
        mocked.when(() -> UserListsGraphQL.fetchLists(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result);
        return mocked;
    }

    private static MockedStatic<RadarGraphQL> mockRadarFetch(
            Single<java.util.List<ReleaseRadarFragment.RadarItem>> result) {
        MockedStatic<RadarGraphQL> mocked =
                org.mockito.Mockito.mockStatic(RadarGraphQL.class);
        mocked.when(() -> RadarGraphQL.fetch(
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(result);
        return mocked;
    }

    private static MockedStatic<com.gh4a.ServiceFactory> mockStarring(
            StarringService service) {
        MockedStatic<com.gh4a.ServiceFactory> mocked =
                org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                        org.mockito.Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> com.gh4a.ServiceFactory.get(
                        org.mockito.ArgumentMatchers.eq(StarringService.class),
                        org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(service);
        return mocked;
    }

    @SafeVarargs
    private static StarringService starringService(
            Single<Response<Page<Repository>>>... results) {
        StarringService service = org.mockito.Mockito.mock(StarringService.class);
        org.mockito.Mockito.when(service.getStarredRepositories(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(results[0],
                        java.util.Arrays.copyOfRange(results, 1, results.length));
        return service;
    }

    @SuppressWarnings("unchecked")
    private static Page<Repository> starredPage(
            java.util.List<Repository> repos, Integer next) {
        Page<Repository> page = org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(page.items()).thenReturn(repos);
        org.mockito.Mockito.when(page.next()).thenReturn(next);
        return page;
    }

    @Test
    public void loadOfficialLists_successSetsListsAndLoaded() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        java.util.List<UserListsGraphQL.UserList> lists = java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList()));
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockListsFetch(Single.just(lists))) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            assertFalse((Boolean) field(f, "mListsFailed"));
            assertEquals(2, ((java.util.List<?>) field(f, "mOfficialLists")).size());
            assertFalse((Boolean) field(f, "mListsLoading"));
        }
    }

    @Test
    public void loadOfficialLists_successDuringRefreshPrefetchesOthers() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        // stopRefreshingFor 需要非空 mSwipe 才会停转圈
        setField(f, "mSwipe", org.mockito.Mockito.mock(SwipeRefreshLayout.class));
        setField(f, "mRefreshActive", true);
        setField(f, "mRefreshTab", 0);
        setField(f, "mRefreshPendingPrefetch", true);
        java.util.List<UserListsGraphQL.UserList> lists = java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()));
        try (MockedStatic<UserListsGraphQL> mockedLists =
                     mockListsFetch(Single.just(lists));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.just(Response.success(
                             starredPage(java.util.Arrays.asList(repo("a/b")), null)))));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, true);
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
            // stopRefreshingFor 在列表页完成时停转圈；预取标记被消费
            assertFalse((Boolean) field(f, "mRefreshActive"));
            assertFalse((Boolean) field(f, "mRefreshPendingPrefetch"));
        }
    }

    @Test
    public void loadOfficialLists_errorSetsFailed() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockListsFetch(Single.error(new RuntimeException("boom")))) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mListsFailed"));
            assertFalse((Boolean) field(f, "mListsLoaded"));
            assertTrue(((java.util.List<?>) field(f, "mOfficialLists")).isEmpty());
            assertFalse((Boolean) field(f, "mListsLoading"));
        }
    }

    @Test
    public void loadOfficialLists_errorKeepsCachedLists() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()))));
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockListsFetch(Single.error(new RuntimeException("boom")))) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mListsFailed"));
            assertEquals(1, ((java.util.List<?>) field(f, "mOfficialLists")).size());
        }
    }

    @Test
    public void loadOfficialLists_alreadyLoadingNoForceSkips() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mListsLoading", true);
        try (MockedStatic<UserListsGraphQL> mocked =
                     mockListsFetch(Single.just(java.util.Collections.emptyList()))) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
            mocked.verify(() -> UserListsGraphQL.fetchLists(
                            org.mockito.ArgumentMatchers.anyString()),
                    org.mockito.Mockito.times(0));
        }
    }

    @Test
    public void loadOfficialLists_forceWhileLoadingCancelsAndReloads() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.disposables.Disposable disposable =
                org.mockito.Mockito.mock(io.reactivex.disposables.Disposable.class);
        org.mockito.Mockito.when(disposable.isDisposed()).thenReturn(false);
        setField(f, "mListsLoading", true);
        setField(f, "mListsDisposable", disposable);
        try (MockedStatic<UserListsGraphQL> mocked = mockListsFetch(
                Single.just(java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))))) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, true);
            org.mockito.Mockito.verify(disposable).dispose();
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
        }
    }

    @Test
    public void loadStarredPage_successFirstPageAppendsAndHasMore() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        // 784 行：成功回调里当前页是 Starred 才 rebuild
        setField(f, "mCurrentTab", 1);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(starredPage(
                        java.util.Arrays.asList(repo("a/b"), repo("c/d")), 2)))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertEquals(2, ((java.util.List<?>) field(f, "mStarred")).size());
            assertEquals(2, field(f, "mStarredPage"));
            assertTrue((Boolean) field(f, "mStarredHasMore"));
            assertFalse((Boolean) field(f, "mStarredFailed"));
        }
    }

    @Test
    public void loadStarredPage_successEmptyPageEndsList() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(starredPage(
                        java.util.Collections.emptyList(), null)))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertTrue(((java.util.List<?>) field(f, "mStarred")).isEmpty());
            assertFalse((Boolean) field(f, "mStarredHasMore"));
            assertEquals(1, field(f, "mStarredPage"));
        }
    }

    @Test
    public void loadStarredPage_successNullBodyEndsList() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(null))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertFalse((Boolean) field(f, "mStarredHasMore"));
        }
    }

    @Test
    public void loadStarredPage_errorFirstPageEndsList() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        // 784 行：失败回调里当前页是 Starred 才 rebuild
        setField(f, "mCurrentTab", 1);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.error(new RuntimeException("boom"))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredFailed"));
            assertFalse((Boolean) field(f, "mStarredHasMore"));
            assertFalse((Boolean) field(f, "mStarredLoaded"));
        }
    }

    @Test
    public void loadStarredPage_errorLaterPageKeepsHasMore() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mStarredPage", 2);
        setField(f, "mStarredHasMore", true);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.error(new RuntimeException("boom"))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, false, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredFailed"));
            assertTrue((Boolean) field(f, "mStarredHasMore"));
            assertEquals(2, field(f, "mStarredPage"));
        }
    }

    @Test
    public void loadStarredPage_secondPageAppends() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("a/b"));
        setField(f, "mStarredPage", 2);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(starredPage(
                        java.util.Arrays.asList(repo("c/d")), null)))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, false, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertEquals(2, ((java.util.List<?>) field(f, "mStarred")).size());
            assertEquals(3, field(f, "mStarredPage"));
            assertFalse((Boolean) field(f, "mStarredHasMore"));
        }
    }

    @Test
    public void loadStarredPage_forceWhileLoadingCancels() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.disposables.Disposable disposable =
                org.mockito.Mockito.mock(io.reactivex.disposables.Disposable.class);
        org.mockito.Mockito.when(disposable.isDisposed()).thenReturn(false);
        setField(f, "mStarredLoading", true);
        setField(f, "mStarredDisposable", disposable);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(starredPage(
                        java.util.Arrays.asList(repo("a/b")), null)))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, true);
            org.mockito.Mockito.verify(disposable).dispose();
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
        }
    }

    @Test
    public void loadUpdates_successSetsUpdates() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        // 829 行：成功回调里当前页是 Updates 才 rebuild
        setField(f, "mCurrentTab", 2);
        try (MockedStatic<RadarGraphQL> mocked = mockRadarFetch(Single.just(
                java.util.Arrays.asList(radar("o", "r1", 1L), radar("o", "r2", 2L))))) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
            assertEquals(2, ((java.util.List<?>) field(f, "mUpdates")).size());
            assertFalse((Boolean) field(f, "mUpdatesFailed"));
        }
    }

    @Test
    public void loadUpdates_errorKeepsOldList() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        // 845 行：失败回调里当前页是 Updates 才 rebuild
        setField(f, "mCurrentTab", 2);
        setField(f, "mUpdates", new java.util.ArrayList<>(java.util.Arrays.asList(
                radar("o", "r1", 1L))));
        try (MockedStatic<RadarGraphQL> mocked =
                     mockRadarFetch(Single.error(new RuntimeException("boom")))) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mUpdatesFailed"));
            assertEquals(1, ((java.util.List<?>) field(f, "mUpdates")).size());
            assertFalse((Boolean) field(f, "mUpdatesLoaded"));
        }
    }

    @Test
    public void loadUpdates_errorNullCreatesEmpty() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        try (MockedStatic<RadarGraphQL> mocked =
                     mockRadarFetch(Single.error(new RuntimeException("boom")))) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mUpdatesFailed"));
            assertTrue(((java.util.List<?>) field(f, "mUpdates")).isEmpty());
        }
    }

    @Test
    public void loadCurrentTab_starredTabLoadsWhenUnloaded() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 1);
        call(f, "loadCurrentTab", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mStarredFailed"));
    }

    @Test
    public void loadCurrentTab_updatesTabLoadsWhenUnloaded() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 2);
        call(f, "loadCurrentTab", new Class<?>[]{});
        assertTrue((Boolean) field(f, "mUpdatesFailed"));
    }

    @Test
    public void loadCurrentTab_skipsLoadedLists() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        setField(f, "mCurrentTab", 0);
        setField(f, "mListsLoaded", true);
        call(f, "loadCurrentTab", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mListsFailed"));
    }

    @Test
    public void prefetchOtherTabs_forceRefreshesAllWithMocks() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mCurrentTab", 0);
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.just(Response.success(
                             starredPage(java.util.Arrays.asList(repo("a/b")), null)))));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            call(f, "prefetchOtherTabs", new Class<?>[]{boolean.class}, true);
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
        }
    }

    @Test
    public void refreshCurrentTab_starredReloadsWithMocks() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mSwipe", org.mockito.Mockito.mock(SwipeRefreshLayout.class));
        setField(f, "mCurrentTab", 1);
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Collections.emptyList()));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.just(Response.success(
                             starredPage(java.util.Arrays.asList(repo("a/b")), null)))));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            call(f, "refreshCurrentTab", new Class<?>[]{});
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
            assertEquals(1, ((java.util.List<?>) field(f, "mStarred")).size());
            assertEquals(2, field(f, "mStarredPage"));
            assertFalse((Boolean) field(f, "mRefreshActive"));
            assertFalse((Boolean) field(f, "mRefreshPendingPrefetch"));
        }
    }

    @Test
    public void stopRefreshingFor_completedTabStops() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        SwipeRefreshLayout swipe =
                org.mockito.Mockito.mock(SwipeRefreshLayout.class);
        setField(f, "mSwipe", swipe);
        setField(f, "mRefreshActive", true);
        setField(f, "mRefreshTab", 0);
        call(f, "stopRefreshingFor", new Class<?>[]{int.class}, 0);
        org.mockito.Mockito.verify(swipe).setRefreshing(false);
        assertFalse((Boolean) field(f, "mRefreshActive"));
    }

    @Test
    public void stopRefreshingFor_otherTabWithLoadsRunningKeepsSpinning() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        SwipeRefreshLayout swipe =
                org.mockito.Mockito.mock(SwipeRefreshLayout.class);
        setField(f, "mSwipe", swipe);
        setField(f, "mRefreshActive", true);
        setField(f, "mRefreshTab", 0);
        setField(f, "mListsLoading", true);
        call(f, "stopRefreshingFor", new Class<?>[]{int.class}, 1);
        org.mockito.Mockito.verify(swipe, org.mockito.Mockito.never())
                .setRefreshing(org.mockito.ArgumentMatchers.anyBoolean());
        assertTrue((Boolean) field(f, "mRefreshActive"));
    }

    @Test
    public void stopRefreshingFor_otherTabAllIdleStops() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        SwipeRefreshLayout swipe =
                org.mockito.Mockito.mock(SwipeRefreshLayout.class);
        setField(f, "mSwipe", swipe);
        setField(f, "mRefreshActive", true);
        setField(f, "mRefreshTab", 0);
        call(f, "stopRefreshingFor", new Class<?>[]{int.class}, 1);
        org.mockito.Mockito.verify(swipe).setRefreshing(false);
        assertFalse((Boolean) field(f, "mRefreshActive"));
    }

    @Test
    public void updateSegmented_marksCurrentTabBold() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        TextView[] segs = new TextView[]{
                new TextView(mActivity), new TextView(mActivity),
                new TextView(mActivity)};
        setField(f, "mSegViews", segs);
        setField(f, "mCurrentTab", 1);
        call(f, "updateSegmented", new Class<?>[]{});
        // Robolectric 下 getTypeface() 读不到 setTypeface(null, BOLD) 的结果；
        // 改断言背景与文字色：选中段有选中背景＋白字，未选中段无背景
        assertNotNull(segs[1].getBackground());
        assertNull(segs[0].getBackground());
        assertNull(segs[2].getBackground());
        assertEquals(android.graphics.Color.WHITE, segs[1].getCurrentTextColor());
        assertFalse(android.graphics.Color.WHITE == segs[0].getCurrentTextColor());
    }

    @Test
    public void openRepo_validStartsRepositoryActivity() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "openRepo", new Class<?>[]{String.class}, "octo/cat");
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void openRepo_badNameIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "openRepo", new Class<?>[]{String.class}, "noslash");
        assertNull(org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity());
    }

    @Test
    public void findListById_findsAndMisses() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        Object found = call(f, "findListById", new Class<?>[]{String.class}, "l2");
        assertNotNull(found);
        assertEquals("l2", ((UserListsGraphQL.UserList) found).id);
        assertNull(call(f, "findListById", new Class<?>[]{String.class}, "nope"));
        setField(f, "mOfficialLists", null);
        assertNull(call(f, "findListById", new Class<?>[]{String.class}, "l1"));
    }

    @Test
    public void saveListOrder_roundTripThroughApply() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        java.util.List<UserListsGraphQL.UserList> lists = java.util.Arrays.asList(
                list("b", "B", java.util.Collections.emptyList()),
                list("a", "A", java.util.Collections.emptyList()));
        call(f, "saveListOrder", new Class<?>[]{java.util.List.class}, lists);
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("a", "A", java.util.Collections.emptyList()),
                list("b", "B", java.util.Collections.emptyList()),
                list("c", "C", java.util.Collections.emptyList()))));
        call(f, "applySavedListOrder", new Class<?>[]{});
        java.util.List<UserListsGraphQL.UserList> ordered =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mOfficialLists");
        assertEquals("b", ordered.get(0).id);
        assertEquals("a", ordered.get(1).id);
        assertEquals("c", ordered.get(2).id);
    }

    @Test
    public void exitSortMode_saveAppliesAndPersists() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        java.util.List<UserListsGraphQL.UserList> sorted = java.util.Arrays.asList(
                list("b", "B", java.util.Collections.emptyList()),
                list("a", "A", java.util.Collections.emptyList()));
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", new java.util.ArrayList<>(sorted));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("a", "A", java.util.Collections.emptyList()),
                list("b", "B", java.util.Collections.emptyList()))));
        call(f, "exitSortMode", new Class<?>[]{boolean.class}, true);
        assertFalse((Boolean) field(f, "mSortMode"));
        java.util.List<UserListsGraphQL.UserList> official =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mOfficialLists");
        assertEquals("b", official.get(0).id);
        assertEquals("[\"b\",\"a\"]", mActivity.getSharedPreferences("star_list_order_alice",
                        android.content.Context.MODE_PRIVATE)
                .getString("order_ids", null));
    }

    @Test
    public void exitSortMode_cancelKeepsOrder() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("b", "B", java.util.Collections.emptyList()))));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(java.util.Arrays.asList(
                list("a", "A", java.util.Collections.emptyList()))));
        call(f, "exitSortMode", new Class<?>[]{boolean.class}, false);
        assertFalse((Boolean) field(f, "mSortMode"));
        java.util.List<UserListsGraphQL.UserList> official =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mOfficialLists");
        assertEquals("a", official.get(0).id);
        assertNull(mActivity.getSharedPreferences("star_list_order_alice",
                        android.content.Context.MODE_PRIVATE)
                .getString("order_ids", null));
    }

    @Test
    public void onRepoClickedInMultiSelect_togglesAndExitsWhenEmpty() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "onRepoClickedInMultiSelect", new Class<?>[]{String.class}, "a/b");
        assertFalse((Boolean) field(f, "mMultiSelect"));
        assertTrue(((java.util.Set<?>) field(f, "mSelectedRepos")).isEmpty());
        setField(f, "mMultiSelect", true);
        call(f, "onRepoClickedInMultiSelect", new Class<?>[]{String.class}, "c/d");
        assertTrue((Boolean) field(f, "mMultiSelect"));
        assertTrue(((java.util.Set<?>) field(f, "mSelectedRepos")).contains("c/d"));
    }

    @Test
    public void clearSortModeState_detachesHelper() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        ItemTouchHelper helper = org.mockito.Mockito.mock(ItemTouchHelper.class);
        setField(f, "mSortTouchHelper", helper);
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "clearSortModeState", new Class<?>[]{});
        org.mockito.Mockito.verify(helper).attachToRecyclerView(null);
        assertFalse((Boolean) field(f, "mSortMode"));
        assertNull(field(f, "mSortLists"));
        assertNull(field(f, "mSortTouchHelper"));
    }

    @Test
    public void dismissMoveProgressDialog_dismissesShowing() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class},
                moveState(3, 1, false, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList()));
        assertNotNull(field(f, "mMoveProgressDialog"));
        call(f, "dismissMoveProgressDialog", new Class<?>[]{});
        assertNull(field(f, "mMoveProgressDialog"));
        assertNull(field(f, "mMoveProgressBar"));
        // 空字段再调一次是 no-op
        call(f, "dismissMoveProgressDialog", new Class<?>[]{});
    }

    @Test
    public void dismissMoveResultDialog_dismissesShowing() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "showBatchMoveResult",
                new Class<?>[]{StarBatchMoveViewModel.State.class, int.class},
                moveState(3, 1, true, java.util.Arrays.asList("a/b"),
                        java.util.Arrays.asList("l1")), 1);
        assertNotNull(field(f, "mMoveResultDialog"));
        call(f, "dismissMoveResultDialog", new Class<?>[]{});
        assertNull(field(f, "mMoveResultDialog"));
        call(f, "dismissMoveResultDialog", new Class<?>[]{});
    }

    // ---- 对话框 / 移组状态机（真实对话框 + mock ViewModel） ----

    private static EditText findEditText(View root) {
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void showDeleteListDialog_positiveRunsSingleOp() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        UserListsGraphQL.UserList l1 = list("l1", "Group 1",
                java.util.Arrays.asList(listRepo("a/b")));
        try (MockedStatic<UserListsGraphQL> mocked =
                     org.mockito.Mockito.mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.deleteList(
                            org.mockito.ArgumentMatchers.eq("l1")))
                    .thenReturn(Single.just(true));
            call(f, "showDeleteListDialog",
                    new Class<?>[]{UserListsGraphQL.UserList.class}, l1);
            AlertDialog dialog = latestDialog();
            assertTrue(dialog.isShowing());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();
            org.mockito.Mockito.verify(vm).runSingleOp(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(com.gh4a.R.string.star_list_deleted),
                    org.mockito.ArgumentMatchers.eq(
                            com.gh4a.R.string.star_list_delete_failed),
                    org.mockito.ArgumentMatchers.eq("l1"));
        }
    }

    @Test
    public void showDeleteListDialog_positiveWithNullViewModelIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        UserListsGraphQL.UserList l1 = list("l1", "Group 1",
                java.util.Collections.emptyList());
        call(f, "showDeleteListDialog",
                new Class<?>[]{UserListsGraphQL.UserList.class}, l1);
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void showAssignToOfficialListsDialog_positiveAssignsChecked() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b"))),
                list("l2", "Group 2", java.util.Arrays.asList(listRepo("c/d")))));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        try (MockedStatic<UserListsGraphQL> mocked =
                     org.mockito.Mockito.mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.assignRepo(
                            org.mockito.ArgumentMatchers.eq("a/b"),
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Single.just(true));
            call(f, "showAssignToOfficialListsDialog",
                    new Class<?>[]{Repository.class}, repo("a/b"));
            AlertDialog dialog = latestDialog();
            ListView listView = dialog.findViewById(com.gh4a.R.id.list);
            assertNotNull(listView);
            // a/b 已在 l1：预勾选 l1，未勾选 l2
            assertTrue(listView.isItemChecked(0));
            assertFalse(listView.isItemChecked(1));
            // 真实点按会先翻转勾选态再调监听；直接调监听需先手动翻转
            listView.setItemChecked(1, true);
            listView.getOnItemClickListener().onItemClick(listView, null, 1, 1);
            assertTrue(listView.isItemChecked(1));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();
            mocked.verify(() -> UserListsGraphQL.assignRepo(
                    org.mockito.ArgumentMatchers.eq("a/b"),
                    org.mockito.ArgumentMatchers.eq(
                            java.util.Arrays.asList("l1", "l2"))));
            org.mockito.Mockito.verify(vm).runSingleOp(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(
                            com.gh4a.R.string.assign_official_success),
                    org.mockito.ArgumentMatchers.eq(
                            com.gh4a.R.string.assign_official_failed),
                    org.mockito.ArgumentMatchers.isNull());
        }
    }

    @Test
    public void showAssignToOfficialListsDialog_neutralOpensCreateDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "showAssignToOfficialListsDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = latestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        AlertDialog second = latestDialog();
        assertTrue(second.isShowing());
        assertNotNull(second.findViewById(com.gh4a.R.id.et_list_name));
    }

    @Test
    public void showAssignToGroupDialog_emptyListsOpensCreateDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        call(f, "showAssignToGroupDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        assertNotNull(latestDialog().findViewById(com.gh4a.R.id.et_list_name));
    }

    @Test
    public void showCreateListDialog_confirmRunsSingleOp() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        try (MockedStatic<UserListsGraphQL> mocked =
                     org.mockito.Mockito.mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.createList(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(Single.just(
                            list("l9", "New Group", java.util.Collections.emptyList())));
            call(f, "showCreateListDialog", new Class<?>[]{});
            AlertDialog dialog = latestDialog();
            idle(); // OnShowListener 里装 save 按钮监听
            EditText nameInput = dialog.findViewById(com.gh4a.R.id.et_list_name);
            assertNotNull(nameInput);
            nameInput.setText("New Group");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();
            org.mockito.Mockito.verify(vm).runSingleOp(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(com.gh4a.R.string.star_list_created),
                    org.mockito.ArgumentMatchers.eq(
                            com.gh4a.R.string.star_list_create_failed),
                    org.mockito.ArgumentMatchers.isNull());
        }
    }

    @Test
    public void showCreateListDialog_emptyNameShowsToast() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "showCreateListDialog", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        idle();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_name_empty),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertTrue(dialog.isShowing());
    }

    @Test
    public void showCreateListDialog_duplicateNameShowsToast() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "My List", java.util.Collections.emptyList())));
        call(f, "showCreateListDialog", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        idle();
        ((EditText) dialog.findViewById(com.gh4a.R.id.et_list_name))
                .setText("my list");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_name_dup),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void showCreateListDialogForAssign_confirmCallsCreateListAndAssign()
            throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        call(f, "showCreateListDialogForAssign",
                new Class<?>[]{String.class}, "a/b");
        AlertDialog dialog = latestDialog();
        idle();
        ((EditText) dialog.findViewById(com.gh4a.R.id.et_list_name))
                .setText("New Group");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        org.mockito.Mockito.verify(vm).createListAndAssign(
                org.mockito.ArgumentMatchers.eq("a/b"),
                org.mockito.ArgumentMatchers.eq("New Group"),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    public void showCreateListDialogForMove_confirmCallsCreateListAndStartMove()
            throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("c/d");
        call(f, "showCreateListDialogForMove", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        idle();
        ((EditText) dialog.findViewById(com.gh4a.R.id.et_list_name))
                .setText("New Group");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        org.mockito.Mockito.verify(vm).createListAndStartMove(
                org.mockito.ArgumentMatchers.eq(
                        java.util.Arrays.asList("a/b", "c/d")),
                org.mockito.ArgumentMatchers.eq("New Group"),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    public void showRenameListDialog_confirmRenames() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        UserListsGraphQL.UserList l1 = list("l1", "Old Name",
                java.util.Collections.emptyList());
        try (MockedStatic<UserListsGraphQL> mocked =
                     org.mockito.Mockito.mockStatic(UserListsGraphQL.class)) {
            mocked.when(() -> UserListsGraphQL.renameList(
                            org.mockito.ArgumentMatchers.eq("l1"),
                            org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(Single.just(
                            list("l1", "Renamed", java.util.Collections.emptyList())));
            call(f, "showRenameListDialog",
                    new Class<?>[]{UserListsGraphQL.UserList.class}, l1);
            AlertDialog dialog = latestDialog();
            EditText input = findEditText(dialog.getWindow().getDecorView());
            assertNotNull(input);
            input.setText("Renamed");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            idle();
            org.mockito.Mockito.verify(vm).runSingleOp(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(com.gh4a.R.string.star_list_renamed),
                    org.mockito.ArgumentMatchers.eq(
                            com.gh4a.R.string.star_list_rename_failed),
                    org.mockito.ArgumentMatchers.isNull());
        }
    }

    @Test
    public void showRenameListDialog_sameNameIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        UserListsGraphQL.UserList l1 = list("l1", "Old Name",
                java.util.Collections.emptyList());
        call(f, "showRenameListDialog",
                new Class<?>[]{UserListsGraphQL.UserList.class}, l1);
        AlertDialog dialog = latestDialog();
        EditText input = findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("Old Name");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        org.mockito.Mockito.verify(vm, org.mockito.Mockito.never())
                .runSingleOp(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void showListMenu_renameOpensRenameDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        UserListsGraphQL.UserList l1 = list("l1", "Group 1",
                java.util.Collections.emptyList());
        android.view.Menu menu = org.mockito.Mockito.mock(android.view.Menu.class);
        MockedConstruction.MockInitializer<PopupMenu> init = (mock, ctx) -> {
            org.mockito.Mockito.when(mock.getMenu()).thenReturn(menu);
        };
        try (MockedConstruction<PopupMenu> mocked =
                     org.mockito.Mockito.mockConstruction(PopupMenu.class, init)) {
            call(f, "showListMenu",
                    new Class<?>[]{View.class, UserListsGraphQL.UserList.class},
                    new TextView(mActivity), l1);
            assertEquals(1, mocked.constructed().size());
            ArgumentCaptor<PopupMenu.OnMenuItemClickListener> captor =
                    ArgumentCaptor.forClass(PopupMenu.OnMenuItemClickListener.class);
            org.mockito.Mockito.verify(mocked.constructed().get(0))
                    .setOnMenuItemClickListener(captor.capture());
            android.view.MenuItem item =
                    org.mockito.Mockito.mock(android.view.MenuItem.class);
            org.mockito.Mockito.when(item.getItemId()).thenReturn(1); // MENU_RENAME
            assertTrue(captor.getValue().onMenuItemClick(item));
            assertNotNull(findEditText(
                    latestDialog().getWindow().getDecorView()));
        }
    }

    @Test
    public void showListMenu_deleteOpensDeleteDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        UserListsGraphQL.UserList l1 = list("l1", "Group 1",
                java.util.Collections.emptyList());
        android.view.Menu menu = org.mockito.Mockito.mock(android.view.Menu.class);
        MockedConstruction.MockInitializer<PopupMenu> init = (mock, ctx) -> {
            org.mockito.Mockito.when(mock.getMenu()).thenReturn(menu);
        };
        try (MockedConstruction<PopupMenu> mocked =
                     org.mockito.Mockito.mockConstruction(PopupMenu.class, init)) {
            call(f, "showListMenu",
                    new Class<?>[]{View.class, UserListsGraphQL.UserList.class},
                    new TextView(mActivity), l1);
            ArgumentCaptor<PopupMenu.OnMenuItemClickListener> captor =
                    ArgumentCaptor.forClass(PopupMenu.OnMenuItemClickListener.class);
            org.mockito.Mockito.verify(mocked.constructed().get(0))
                    .setOnMenuItemClickListener(captor.capture());
            android.view.MenuItem item =
                    org.mockito.Mockito.mock(android.view.MenuItem.class);
            org.mockito.Mockito.when(item.getItemId()).thenReturn(2); // MENU_DELETE
            assertTrue(captor.getValue().onMenuItemClick(item));
            assertNotNull(latestDialog().findViewById(android.R.id.message));
        }
    }

    @Test
    public void showMoveDialog_guardsReturnWhenNothingToMove() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "showMoveDialog", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        call(f, "showMoveDialog", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "showMoveDialog", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
    }

    @Test
    public void showMoveDialog_positiveStartsBatchMove() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b"))),
                list("l2", "Group 2", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        call(f, "showMoveDialog", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        ListView listView = dialog.findViewById(com.gh4a.R.id.list);
        assertTrue(listView.isItemChecked(0));
        assertTrue(listView.isItemChecked(1));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        org.mockito.Mockito.verify(vm).start(
                org.mockito.ArgumentMatchers.eq(java.util.Arrays.asList("a/b")),
                org.mockito.ArgumentMatchers.eq(java.util.Arrays.asList("l1", "l2")));
    }

    @Test
    public void showMoveDialog_partialSelectionChecksNothing() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b"))),
                list("l2", "Group 2", java.util.Arrays.asList(listRepo("c/d")))));
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("x/y");
        call(f, "showMoveDialog", new Class<?>[]{});
        ListView listView = latestDialog().findViewById(com.gh4a.R.id.list);
        assertFalse(listView.isItemChecked(0));
        assertFalse(listView.isItemChecked(1));
    }

    @Test
    public void showBatchMoveResult_positiveConsumesExitsAndRefetches() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        setField(f, "mLogin", null);
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        StarBatchMoveViewModel.State state = moveState(3, 1, true,
                java.util.Arrays.asList("a/b", "c/d"),
                java.util.Arrays.asList("l1"));
        call(f, "showBatchMoveResult",
                new Class<?>[]{StarBatchMoveViewModel.State.class, int.class}, state, 1);
        AlertDialog dialog = (AlertDialog) field(f, "mMoveResultDialog");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
        org.mockito.Mockito.verify(vm).markResultConsumed(state);
        assertFalse((Boolean) field(f, "mMultiSelect"));
        // refetchLists 在空登录下直接失败态，不发网络
        assertTrue((Boolean) field(f, "mListsFailed"));
    }

    @Test
    public void showBatchMoveResult_negativeRetriesFailed() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        StarBatchMoveViewModel.State state = moveState(3, 1, true,
                java.util.Arrays.asList("a/b"),
                java.util.Arrays.asList("l1"));
        call(f, "showBatchMoveResult",
                new Class<?>[]{StarBatchMoveViewModel.State.class, int.class}, state, 1);
        AlertDialog dialog = (AlertDialog) field(f, "mMoveResultDialog");
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        idle();
        org.mockito.Mockito.verify(vm).retryFailed();
    }

    @Test
    public void showBatchMoveResult_moreThanThreeFailedTruncatesNames() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel.State state = moveState(5, 0, true,
                java.util.Arrays.asList("a/a", "b/b", "c/c", "d/d", "e/e"),
                java.util.Arrays.asList("l1"));
        call(f, "showBatchMoveResult",
                new Class<?>[]{StarBatchMoveViewModel.State.class, int.class}, state, 0);
        AlertDialog dialog = (AlertDialog) field(f, "mMoveResultDialog");
        String message = ((TextView) dialog.findViewById(android.R.id.message))
                .getText().toString();
        assertTrue(message.contains("a/a"));
        assertTrue(message.contains("c/c"));
        assertTrue(message.contains("…"));
        assertFalse(message.contains("d/d"));
        assertFalse(message.contains("e/e"));
    }

    @Test
    public void showBatchMoveResult_emptyTargetIdsShowsMoveOutNote() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel.State state = moveState(2, 1, true,
                java.util.Arrays.asList("a/b"),
                java.util.Collections.emptyList());
        call(f, "showBatchMoveResult",
                new Class<?>[]{StarBatchMoveViewModel.State.class, int.class}, state, 1);
        AlertDialog dialog = (AlertDialog) field(f, "mMoveResultDialog");
        String message = ((TextView) dialog.findViewById(android.R.id.message))
                .getText().toString();
        assertTrue(message.contains(
                mActivity.getString(com.gh4a.R.string.star_list_move_out_note, 1)));
        assertFalse(message.contains(
                mActivity.getString(com.gh4a.R.string.star_list_move_done_note, 1)));
    }

    @Test
    public void onMoveStateChanged_unfinishedUpdatesExistingDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class},
                moveState(3, 1, false, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList()));
        AlertDialog dialog = (AlertDialog) field(f, "mMoveProgressDialog");
        assertTrue(dialog.isShowing());
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class},
                moveState(3, 2, false, java.util.Collections.emptyList(),
                        java.util.Collections.emptyList()));
        assertEquals(dialog, field(f, "mMoveProgressDialog"));
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_moving, 2, 3),
                org.robolectric.Shadows.shadowOf(dialog).getTitle());
        assertEquals(2, ((android.widget.ProgressBar) field(f, "mMoveProgressBar"))
                .getProgress());
    }

    @Test
    public void onMoveStateChanged_finishedFailedShowsResultDialog() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mMoveViewModel", new StarBatchMoveViewModel());
        call(f, "onMoveStateChanged",
                new Class<?>[]{StarBatchMoveViewModel.State.class},
                moveState(3, 1, true, java.util.Arrays.asList("a/b"),
                        java.util.Arrays.asList("l1")));
        AlertDialog result = (AlertDialog) field(f, "mMoveResultDialog");
        assertNotNull(result);
        assertTrue(result.isShowing());
    }

    @Test
    public void onSingleOpChanged_successClearsExpandedAndRefetches() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        setField(f, "mLogin", null);
        setField(f, "mExpandedOfficialId", "l1");
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, com.gh4a.R.string.app_name, "l1");
        org.mockito.Mockito.when(vm.isSingleOpConsumed(op)).thenReturn(false);
        call(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        org.mockito.Mockito.verify(vm).markSingleOpConsumed(op);
        assertEquals(mActivity.getString(com.gh4a.R.string.app_name),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
        assertNull(field(f, "mExpandedOfficialId"));
        assertTrue((Boolean) field(f, "mListsFailed"));
    }

    @Test
    public void onSingleOpChanged_successKeepsOtherExpanded() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        StarBatchMoveViewModel vm =
                org.mockito.Mockito.mock(StarBatchMoveViewModel.class);
        setField(f, "mMoveViewModel", vm);
        setField(f, "mLogin", null);
        setField(f, "mExpandedOfficialId", "l1");
        StarBatchMoveViewModel.SingleOp op = new StarBatchMoveViewModel.SingleOp(
                1L, true, com.gh4a.R.string.app_name, "l2");
        org.mockito.Mockito.when(vm.isSingleOpConsumed(op)).thenReturn(false);
        call(f, "onSingleOpChanged",
                new Class<?>[]{StarBatchMoveViewModel.SingleOp.class}, op);
        assertEquals("l1", field(f, "mExpandedOfficialId"));
    }

    // ---- MergedAdapter 绑定（真实适配器 + 反射创建） ----

    private static Object newAdapter(StarMergedFragment f) throws Exception {
        Class<?> c = Class.forName("com.gh4a.fragment.StarMergedFragment$MergedAdapter");
        java.lang.reflect.Constructor<?> ctor =
                c.getDeclaredConstructor(StarMergedFragment.class);
        ctor.setAccessible(true);
        return ctor.newInstance(f);
    }

    private RecyclerView adapterParent() {
        RecyclerView parent = new RecyclerView(mActivity);
        parent.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(mActivity));
        return parent;
    }

    private StarMergedFragment attachWithAdapter() throws Exception {
        mActivity.setTheme(com.gh4a.R.style.AppTheme);
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mAdapter", newAdapter(f));
        return f;
    }

    private static Object invokeAdapter(Object adapter, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = adapter.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(adapter, args);
    }

    private static RecyclerView.ViewHolder createHolder(
            Object adapter, ViewGroup parent, int viewType) throws Exception {
        return (RecyclerView.ViewHolder) invokeAdapter(adapter, "onCreateViewHolder",
                new Class<?>[]{ViewGroup.class, int.class}, parent, viewType);
    }

    private static void bindHolder(Object adapter, RecyclerView.ViewHolder holder,
            int position) throws Exception {
        invokeAdapter(adapter, "onBindViewHolder",
                new Class<?>[]{RecyclerView.ViewHolder.class, int.class}, holder, position);
    }

    private static int adapterCount(Object adapter) throws Exception {
        return (int) invokeAdapter(adapter, "getItemCount", new Class<?>[]{});
    }

    private static int adapterViewType(Object adapter, int position) throws Exception {
        return (int) invokeAdapter(adapter, "getItemViewType",
                new Class<?>[]{int.class}, position);
    }

    private static RecyclerView.ViewHolder boundHolder(StarMergedFragment f, Object adapter,
            RecyclerView parent, int position) throws Exception {
        RecyclerView.ViewHolder holder =
                createHolder(adapter, parent, adapterViewType(adapter, position));
        bindHolder(adapter, holder, position);
        return holder;
    }

    private static Repository richRepo() {
        return Repository.builder()
                .fullName("octo/cat")
                .owner(com.meisolsson.githubsdk.model.User.builder().login("octo").build())
                .name("cat")
                .description("A cat repo")
                .language("Java")
                .isPrivate(true)
                .isFork(true)
                .stargazersCount(5)
                .forksCount(2)
                .size(100)
                .build();
    }

    @Test
    public void adapter_getItemCountAndViewType() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(2, adapterCount(adapter));
        assertEquals(typeConst("TYPE_LIST"), adapterViewType(adapter, 0));
        assertEquals(typeConst("TYPE_ADD_GROUP"), adapterViewType(adapter, 1));
    }

    @Test
    public void onCreateViewHolder_textRowTypesAreTextViews() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        for (String type : new String[]{"TYPE_SECTION", "TYPE_STATUS", "TYPE_SORT_HEADER"}) {
            RecyclerView.ViewHolder holder =
                    createHolder(adapter, parent, typeConst(type));
            assertTrue(holder.itemView instanceof TextView);
        }
    }

    @Test
    public void onCreateViewHolder_groupTypesInflateRowLayout() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        for (String type : new String[]{"TYPE_LIST", "TYPE_LIST_REPO",
                "TYPE_UNGROUPED", "TYPE_SORT_ROW"}) {
            RecyclerView.ViewHolder holder =
                    createHolder(adapter, parent, typeConst(type));
            assertNotNull(holder.itemView.findViewById(com.gh4a.R.id.tv_title));
        }
    }

    @Test
    public void onCreateViewHolder_otherTypesInflateTheirLayouts() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        assertNotNull(createHolder(adapter, parent, typeConst("TYPE_SELECTION_HEADER"))
                .itemView.findViewById(com.gh4a.R.id.tv_label));
        assertNotNull(createHolder(adapter, parent, typeConst("TYPE_ACTION_BAR"))
                .itemView.findViewById(com.gh4a.R.id.btn_primary));
        assertNotNull(createHolder(adapter, parent, typeConst("TYPE_STARRED_REPO"))
                .itemView.findViewById(com.gh4a.R.id.tv_meta));
        assertNotNull(createHolder(adapter, parent, typeConst("TYPE_UPDATE"))
                .itemView.findViewById(com.gh4a.R.id.tv_release));
    }

    @Test
    public void bindGroupHeader_setsCardTitleCapsuleAndMenu() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertEquals("Group 1",
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_title))
                        .getText().toString());
        TextView subtitle = holder.itemView.findViewById(com.gh4a.R.id.tv_subtitle);
        assertEquals(View.VISIBLE, subtitle.getVisibility());
        assertEquals("desc-l1", subtitle.getText().toString());
        TextView pill = holder.itemView.findViewById(com.gh4a.R.id.tv_count_pill);
        assertEquals(View.VISIBLE, pill.getVisibility());
        assertTrue(pill.getText().toString().contains("2"));
        assertEquals(View.VISIBLE,
                holder.itemView.findViewById(com.gh4a.R.id.v_color_bar).getVisibility());
        assertEquals("G",
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_letter))
                        .getText().toString());
        assertEquals(View.VISIBLE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_chevron).getVisibility());
        assertEquals(View.VISIBLE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_more).getVisibility());
    }

    @Test
    public void bindGroupHeader_noDescriptionHidesSubtitle() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                new UserListsGraphQL.UserList("l1", "Group 1", "",
                        java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_subtitle).getVisibility());
    }

    @Test
    public void bindGroupHeader_ungroupedHidesIdentity() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("a/b"));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("x/y"));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.v_color_bar).getVisibility());
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_letter).getVisibility());
        assertEquals(View.VISIBLE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_count_pill).getVisibility());
        TextView subtitle = holder.itemView.findViewById(com.gh4a.R.id.tv_subtitle);
        assertEquals(View.VISIBLE, subtitle.getVisibility());
        assertTrue(subtitle.getText().toString().contains("2"));
    }

    @Test
    public void bindGroupHeader_multiSelectHidesMenuAndChevron() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        setField(f, "mMultiSelect", true);
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_more).getVisibility());
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_chevron).getVisibility());
    }

    @Test
    public void bindListRepoRow_dividerFollowsNextRepo() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        setField(f, "mExpandedOfficialId", "l1");
        call(f, "buildListsSection", new Class<?>[]{});
        // 分隔线依赖 holder.getBindingAdapterPosition()，独立创建的 holder
        // 位置是 NO_POSITION；走真实 RecyclerView 布局拿带位置的 holder。
        parent.setAdapter((RecyclerView.Adapter) adapter);
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1080, 1920);
        RecyclerView.ViewHolder first = parent.findViewHolderForAdapterPosition(1);
        assertNotNull(first);
        assertEquals("a/b",
                ((TextView) first.itemView.findViewById(com.gh4a.R.id.tv_title))
                        .getText().toString());
        assertEquals("d-a/b",
                ((TextView) first.itemView.findViewById(com.gh4a.R.id.tv_subtitle))
                        .getText().toString());
        assertEquals(View.VISIBLE,
                first.itemView.findViewById(com.gh4a.R.id.v_divider).getVisibility());
        RecyclerView.ViewHolder second = parent.findViewHolderForAdapterPosition(2);
        assertNotNull(second);
        assertEquals(View.GONE,
                second.itemView.findViewById(com.gh4a.R.id.v_divider).getVisibility());
    }

    @Test
    public void bindListRepoRow_multiSelectCheckboxReflectsSelection() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        setField(f, "mExpandedOfficialId", "l1");
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder first = boundHolder(f, adapter, parent, 2);
        CheckBox firstBox = first.itemView.findViewById(com.gh4a.R.id.cb_select);
        assertEquals(View.VISIBLE, firstBox.getVisibility());
        assertTrue(firstBox.isChecked());
        RecyclerView.ViewHolder second = boundHolder(f, adapter, parent, 3);
        CheckBox secondBox = second.itemView.findViewById(com.gh4a.R.id.cb_select);
        assertEquals(View.VISIBLE, secondBox.getVisibility());
        assertFalse(secondBox.isChecked());
        // 点框取消勾选 a/b，选集为空即退出多选
        firstBox.performClick();
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void bindGroupHeader_clickTogglesExpand() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        holder.itemView.performClick();
        assertEquals("l1", field(f, "mExpandedOfficialId"));
        // 重建后行序变了（组下多出仓库行），重新绑定组头再点一次收起
        RecyclerView.ViewHolder again = boundHolder(f, adapter, parent, 0);
        again.itemView.performClick();
        assertNull(field(f, "mExpandedOfficialId"));
    }

    @Test
    public void bindUngroupedHeader_clickTogglesExpand() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("x/y"));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        holder.itemView.performClick();
        assertTrue((Boolean) field(f, "mUngroupedExpanded"));
        RecyclerView.ViewHolder again = boundHolder(f, adapter, parent, 1);
        again.itemView.performClick();
        assertFalse((Boolean) field(f, "mUngroupedExpanded"));
    }

    @Test
    public void bindGroupHeader_longClickEntersSortMode() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertTrue(holder.itemView.performLongClick());
        assertTrue((Boolean) field(f, "mSortMode"));
    }

    @Test
    public void bindListRepoRow_longClickEntersMultiSelect() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        setField(f, "mExpandedOfficialId", "l1");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertTrue(holder.itemView.performLongClick());
        assertTrue((Boolean) field(f, "mMultiSelect"));
        assertTrue(((java.util.Set<?>) field(f, "mSelectedRepos")).contains("a/b"));
    }

    @Test
    public void bindListRepoRow_clickInMultiSelectToggles() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        setField(f, "mExpandedOfficialId", "l1");
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 2);
        holder.itemView.performClick();
        assertFalse((Boolean) field(f, "mMultiSelect"));
        // 再进多选点行加选（rebuild 先清 mItems，避免二次 buildListsSection 叠加）
        setField(f, "mMultiSelect", true);
        call(f, "rebuild", new Class<?>[]{});
        RecyclerView.ViewHolder again = boundHolder(f, adapter, parent, 2);
        again.itemView.performClick();
        assertTrue((Boolean) field(f, "mMultiSelect"));
        assertTrue(((java.util.Set<?>) field(f, "mSelectedRepos")).contains("a/b"));
    }

    @Test
    public void bindSortRow_showsDragHandleAndClearsClick() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertEquals(View.VISIBLE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_drag).getVisibility());
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_subtitle).getVisibility());
        assertEquals("Group 1",
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_title))
                        .getText().toString());
        holder.itemView.performClick();
        assertTrue((Boolean) field(f, "mSortMode"));
    }

    @Test
    public void bindSortRow_dragTouchStartsDragWhenHelperPresent() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        View drag = holder.itemView.findViewById(com.gh4a.R.id.tv_drag);
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        // 无 helper 时不启动拖动
        assertFalse(drag.dispatchTouchEvent(down));
        ItemTouchHelper helper = org.mockito.Mockito.mock(ItemTouchHelper.class);
        setField(f, "mSortTouchHelper", helper);
        // 重新绑定以拿到带 helper 的监听（监听读的是字段，非绑定时快照）
        assertTrue(drag.dispatchTouchEvent(down));
        org.mockito.Mockito.verify(helper).startDrag(
                org.mockito.ArgumentMatchers.eq(holder));
    }

    @Test
    public void bindSelectionHeader_labelAndCancel() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_selected, 1),
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_label))
                        .getText().toString());
        holder.itemView.findViewById(com.gh4a.R.id.tv_cancel).performClick();
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void bindActionBar_moveShowsCountAndCancelExits() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 2);
        TextView count = holder.itemView.findViewById(com.gh4a.R.id.tv_count);
        assertEquals(View.VISIBLE, count.getVisibility());
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_selected, 1),
                count.getText().toString());
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_move_to),
                ((android.widget.Button) holder.itemView
                        .findViewById(com.gh4a.R.id.btn_primary))
                        .getText().toString());
        holder.itemView.findViewById(com.gh4a.R.id.btn_cancel).performClick();
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void bindActionBar_movePrimaryOpensMoveDialog() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        setField(f, "mMultiSelect", true);
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 2);
        holder.itemView.findViewById(com.gh4a.R.id.btn_primary).performClick();
        assertNotNull(latestDialog().findViewById(com.gh4a.R.id.list));
    }

    @Test
    public void bindActionBar_sortPrimarySavesAndCancelDiscards() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        java.util.List<UserListsGraphQL.UserList> lists = java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList()));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(lists));
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", new java.util.ArrayList<>(lists));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 3);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_count).getVisibility());
        assertEquals(mActivity.getString(com.gh4a.R.string.star_list_done),
                ((android.widget.Button) holder.itemView
                        .findViewById(com.gh4a.R.id.btn_primary))
                        .getText().toString());
        holder.itemView.findViewById(com.gh4a.R.id.btn_primary).performClick();
        assertFalse((Boolean) field(f, "mSortMode"));
        assertEquals("[\"l1\",\"l2\"]", mActivity.getSharedPreferences(
                        "star_list_order_alice", android.content.Context.MODE_PRIVATE)
                .getString("order_ids", null));
        // 取消分支：重新进排序后点取消，不写顺序
        StarMergedFragment f2 = attachWithAdapter();
        Object adapter2 = field(f2, "mAdapter");
        setField(f2, "mOfficialLists", new java.util.ArrayList<>(lists));
        setField(f2, "mSortMode", true);
        setField(f2, "mSortLists", new java.util.ArrayList<>(lists));
        call(f2, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder2 = boundHolder(f2, adapter2, parent, 3);
        holder2.itemView.findViewById(com.gh4a.R.id.btn_cancel).performClick();
        assertFalse((Boolean) field(f2, "mSortMode"));
    }

    @Test
    public void bindStarredRepo_setsMetaClickAndLongClick() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        ((java.util.List<Repository>) field(f, "mStarred")).add(richRepo());
        call(f, "buildStarredSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertTrue(((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_title))
                .getText().toString().contains("cat"));
        TextView desc = holder.itemView.findViewById(com.gh4a.R.id.tv_desc);
        assertEquals(View.VISIBLE, desc.getVisibility());
        assertEquals("A cat repo", desc.getText().toString());
        String meta = ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_meta))
                .getText().toString();
        assertTrue(meta.contains("Java"));
        assertTrue(meta.contains("★ 5"));
        assertTrue(meta.contains("⑂ 2"));
        holder.itemView.performClick();
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                intent.getComponent().getClassName());
        assertTrue(holder.itemView.performLongClick());
        assertNotNull(latestDialog().findViewById(com.gh4a.R.id.et_list_name));
    }

    @Test
    public void bindStarredRepo_blankDescriptionAndUnknownLanguage() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("a/b"));
        call(f, "buildStarredSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_desc).getVisibility());
        String meta = ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_meta))
                .getText().toString();
        assertTrue(meta.contains(mActivity.getString(com.gh4a.R.string.unknown)));
    }

    @Test
    public void bindUpdateRow_fallbackNameAndReadAlpha() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        ReleaseRadarFragment.RadarItem radar = new ReleaseRadarFragment.RadarItem(
                "o", "r", 1L, "v1", "", 1000L, 900L);
        setField(f, "mUpdates", java.util.Arrays.asList(radar));
        call(f, "buildUpdatesSection", new Class<?>[]{});
        // 未读：首行是“全部已读”，更新行在位置 1
        assertEquals(2, adapterCount(adapter));
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertEquals("v1",
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_release))
                        .getText().toString());
        assertEquals("o/r",
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_repo))
                        .getText().toString());
        assertEquals(1.0f, holder.itemView.getAlpha(), 0.001f);
        // 标记已读后重绑，行变淡
        ((java.util.Set<String>) field(f, "mReadUpdateKeys")).add("o/r#1");
        RecyclerView.ViewHolder again = boundHolder(f, adapter, parent, 1);
        assertEquals(0.45f, again.itemView.getAlpha(), 0.001f);
        again.itemView.performClick();
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.ReleaseInfoActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void bindTextRow_markAllReadClickMarksAll() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        ReleaseRadarFragment.RadarItem a = radar("o", "r1", 1L);
        ReleaseRadarFragment.RadarItem b = radar("o", "r2", 2L);
        setField(f, "mUpdates", java.util.Arrays.asList(a, b));
        call(f, "buildUpdatesSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        holder.itemView.performClick();
        assertTrue((Boolean) call(f, "isUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, a));
        assertTrue((Boolean) call(f, "isUpdateRead",
                new Class<?>[]{ReleaseRadarFragment.RadarItem.class}, b));
    }

    @Test
    public void bindTextRow_retryListsClickReloads() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        setField(f, "mListsFailed", true);
        call(f, "buildListsSection", new Class<?>[]{});
        assertTrue(itemBool(items(f).get(0), "retryLists"));
        try (MockedStatic<UserListsGraphQL> mocked = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))))) {
            RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
            holder.itemView.performClick();
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            assertFalse((Boolean) field(f, "mListsFailed"));
        }
    }

    @Test
    public void bindTextRow_retryUpdatesClickReloads() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mUpdates", new java.util.ArrayList<>());
        setField(f, "mUpdatesFailed", true);
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertTrue(itemBool(items(f).get(0), "retryUpdates"));
        try (MockedStatic<RadarGraphQL> mocked = mockRadarFetch(
                Single.just(java.util.Collections.emptyList()))) {
            RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
            holder.itemView.performClick();
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
            assertFalse((Boolean) field(f, "mUpdatesFailed"));
        }
    }

    @Test
    public void bindTextRow_retryStarredClickReloads() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mStarredFailed", true);
        call(f, "buildStarredSection", new Class<?>[]{});
        assertTrue(itemBool(items(f).get(0), "retryStarred"));
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(starredPage(
                        java.util.Arrays.asList(repo("a/b")), null)))))) {
            RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
            holder.itemView.performClick();
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertFalse((Boolean) field(f, "mStarredFailed"));
        }
    }

    @Test
    public void bindTextRow_addGroupClickOpensCreateDialog() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        holder.itemView.performClick();
        assertNotNull(latestDialog().findViewById(com.gh4a.R.id.et_list_name));
    }

    @Test
    public void bindTextRow_plainStatusHasNoClick() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        call(f, "buildStarredSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        holder.itemView.performClick();
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
        assertNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void rebuild_withAdapterNotifies() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "rebuild", new Class<?>[]{});
        assertEquals(2, adapterCount(adapter));
    }

    @Test
    public void setGroupCardMargins_setsMarginsForRecyclerParams() throws Exception {
        TextView v = new TextView(mActivity);
        v.setLayoutParams(new RecyclerView.LayoutParams(100, 100));
        Method m = StarMergedFragment.class.getDeclaredMethod(
                "setGroupCardMargins", View.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(null, v, 12, 6);
        RecyclerView.LayoutParams params =
                (RecyclerView.LayoutParams) v.getLayoutParams();
        assertEquals(12, params.leftMargin);
        assertEquals(6, params.topMargin);
        assertEquals(12, params.rightMargin);
        assertEquals(6, params.bottomMargin);
    }

    @Test
    public void setGroupCardMargins_ignoresNonRecyclerParams() throws Exception {
        TextView v = new TextView(mActivity);
        v.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
        Method m = StarMergedFragment.class.getDeclaredMethod(
                "setGroupCardMargins", View.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(null, v, 12, 6);
        assertTrue(v.getLayoutParams() instanceof ViewGroup.LayoutParams);
    }

    // ---- 排序拖动回调 ----

    @Test
    public void sortTouchHelper_onMoveSwapsSortRows() throws Exception {
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        java.util.List<UserListsGraphQL.UserList> lists = java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList()),
                list("l3", "Group 3", java.util.Collections.emptyList()));
        setField(f, "mOfficialLists", new java.util.ArrayList<>(lists));
        setField(f, "mSortMode", true);
        setField(f, "mSortLists", new java.util.ArrayList<>(lists));
        RecyclerView rv = adapterParent();
        setField(f, "mList", rv);
        call(f, "buildListsSection", new Class<?>[]{});
        call(f, "attachSortTouchHelper", new Class<?>[]{});
        ItemTouchHelper helper = (ItemTouchHelper) field(f, "mSortTouchHelper");
        assertNotNull(helper);
        java.lang.reflect.Field cbField =
                ItemTouchHelper.class.getDeclaredField("mCallback");
        cbField.setAccessible(true);
        ItemTouchHelper.Callback cb =
                (ItemTouchHelper.Callback) cbField.get(helper);
        assertFalse(cb.isLongPressDragEnabled());
        RecyclerView.ViewHolder from =
                org.mockito.Mockito.mock(RecyclerView.ViewHolder.class);
        RecyclerView.ViewHolder to =
                org.mockito.Mockito.mock(RecyclerView.ViewHolder.class);
        org.mockito.Mockito.when(from.getBindingAdapterPosition()).thenReturn(1);
        org.mockito.Mockito.when(to.getBindingAdapterPosition()).thenReturn(2);
        assertTrue(cb.onMove(rv, from, to));
        java.util.List<UserListsGraphQL.UserList> sorted =
                (java.util.List<UserListsGraphQL.UserList>) field(f, "mSortLists");
        assertEquals("l2", sorted.get(0).id);
        assertEquals("l1", sorted.get(1).id);
        assertEquals("l3", sorted.get(2).id);
        // 提示行不可拖入
        org.mockito.Mockito.when(from.getBindingAdapterPosition()).thenReturn(0);
        org.mockito.Mockito.when(to.getBindingAdapterPosition()).thenReturn(1);
        assertFalse(cb.onMove(rv, from, to));
        // NO_POSITION 拒绝
        org.mockito.Mockito.when(from.getBindingAdapterPosition())
                .thenReturn(RecyclerView.NO_POSITION);
        assertFalse(cb.onMove(rv, from, to));
        // mSortLists 为空时拒绝
        setField(f, "mSortLists", null);
        org.mockito.Mockito.when(from.getBindingAdapterPosition()).thenReturn(1);
        org.mockito.Mockito.when(to.getBindingAdapterPosition()).thenReturn(2);
        assertFalse(cb.onMove(rv, from, to));
        cb.onSwiped(from, ItemTouchHelper.LEFT);
    }

    @Test
    public void attachSortTouchHelper_nullListIsNoop() throws Exception {
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "attachSortTouchHelper", new Class<?>[]{});
        assertNull(field(f, "mSortTouchHelper"));
    }

    // ---- 完整视图（RESUMED 挂载，onCreateView 真跑） ----

    private StarMergedFragment attachResumed() {
        mActivity.setTheme(com.gh4a.R.style.AppTheme);
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    private static Object fieldOf(Object obj, String name) throws Exception {
        Field fld = obj.getClass().getDeclaredField(name);
        fld.setAccessible(true);
        return fld.get(obj);
    }

    private static void invokeCallback(Object callback, String method) throws Exception {
        Method m = callback.getClass().getMethod(method);
        m.setAccessible(true);
        m.invoke(callback);
    }

    @Test
    public void onCreateView_loadsCachedListsAndWiresView() throws Exception {
        java.util.List<UserListsGraphQL.UserList> cached = java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList()),
                list("l2", "Group 2", java.util.Collections.emptyList()));
        // 缓存 Single 在 IO 线程读盘（静态 mock 只在主线程生效），
        // 故直接用真实 save() 把缓存文件预置好，再让真实 load() 读回。
        UserListsCache.save(mActivity, "alice", cached);
        try (MockedStatic<UserListsGraphQL> mockedLists =
                     mockListsFetch(Single.never());
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.never()));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.never())) {
            StarMergedFragment f = attachResumed();
            awaitCondition(() -> {
                java.util.List<?> lists =
                        (java.util.List<?>) field(f, "mOfficialLists");
                return lists != null && lists.size() == 2;
            });
            assertNotNull(field(f, "mList"));
            assertNotNull(field(f, "mSwipe"));
            assertEquals(3, ((TextView[]) field(f, "mSegViews")).length);
            assertNotNull(field(f, "mMoveViewModel"));
            assertNotNull(field(f, "mBackCallback"));
            assertFalse((Boolean) field(f, "mListsFailed"));
            assertTrue(itemTypes(f).contains(typeConst("TYPE_LIST")));
        }
    }

    @Test
    public void onCreateView_scrollListenerTriggersStarredLoad() throws Exception {
        Single<Response<Page<Repository>>> page1 = Single.just(Response.success(
                starredPage(java.util.Arrays.asList(repo("a/b"), repo("c/d")), 2)));
        Single<Response<Page<Repository>>> page2 = Single.just(Response.success(
                starredPage(java.util.Arrays.asList(repo("e/f")), null)));
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(page1, page2));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            StarMergedFragment f = attachResumed();
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertEquals(2, ((java.util.List<?>) field(f, "mStarred")).size());
            setField(f, "mCurrentTab", 1);
            ((java.util.List<?>) field(f, "mItems")).clear();
            setField(f, "mStarredLoading", false);
            setField(f, "mStarredHasMore", true);
            RecyclerView rv = (RecyclerView) field(f, "mList");
            Field listenersField = RecyclerView.class.getDeclaredField("mScrollListeners");
            listenersField.setAccessible(true);
            java.util.List<RecyclerView.OnScrollListener> listeners =
                    (java.util.List<RecyclerView.OnScrollListener>)
                            listenersField.get(rv);
            assertEquals(1, listeners.size());
            listeners.get(0).onScrolled(rv, 0, 10);
            awaitCondition(() -> ((java.util.List<?>) field(f, "mStarred")).size() == 3);
            assertFalse((Boolean) field(f, "mStarredHasMore"));
            // 已无下页：再滑不发请求
            setField(f, "mStarredLoading", false);
            listeners.get(0).onScrolled(rv, 0, 10);
            Thread.sleep(300);
            idle();
            assertEquals(3, ((java.util.List<?>) field(f, "mStarred")).size());
            // 非 Star 页：早退
            setField(f, "mCurrentTab", 0);
            setField(f, "mStarredHasMore", true);
            listeners.get(0).onScrolled(rv, 0, 10);
            Thread.sleep(300);
            idle();
            assertEquals(3, ((java.util.List<?>) field(f, "mStarred")).size());
        }
    }

    @Test
    public void onCreateView_swipeCallbacksSwitchTabs() throws Exception {
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()),
                        list("l2", "Group 2", java.util.Collections.emptyList()))));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.just(Response.success(
                             starredPage(java.util.Collections.emptyList(), null)))));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            StarMergedFragment f = attachResumed();
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            RecyclerView rv = (RecyclerView) field(f, "mList");
            Field touchField =
                    RecyclerView.class.getDeclaredField("mOnItemTouchListeners");
            touchField.setAccessible(true);
            java.util.List<RecyclerView.OnItemTouchListener> touchListeners =
                    (java.util.List<RecyclerView.OnItemTouchListener>)
                            touchField.get(rv);
            assertEquals(1, touchListeners.size());
            Object listCb = fieldOf(fieldOf(touchListeners.get(0), "mListener"),
                    "mCallback");
            invokeCallback(listCb, "onSwipeLeft");
            assertEquals(1, field(f, "mCurrentTab"));
            invokeCallback(listCb, "onSwipeRight");
            assertEquals(0, field(f, "mCurrentTab"));
            // 分段行上的左右滑是同一回调的另一实例
            View segContainer = f.getView().findViewById(com.gh4a.R.id.star_segmented);
            assertNotNull(segContainer);
            Method getInfo = View.class.getDeclaredMethod("getListenerInfo");
            getInfo.setAccessible(true);
            Object info = getInfo.invoke(segContainer);
            Field touchListenerField =
                    info.getClass().getDeclaredField("mOnTouchListener");
            touchListenerField.setAccessible(true);
            Object segListener = touchListenerField.get(info);
            Object segCb = fieldOf(segListener, "mCallback");
            invokeCallback(segCb, "onSwipeLeft");
            assertEquals(1, field(f, "mCurrentTab"));
            invokeCallback(segCb, "onSwipeRight");
            assertEquals(0, field(f, "mCurrentTab"));
            // 317 行：分段标题点按走同一 selectTab
            TextView[] segs = (TextView[]) field(f, "mSegViews");
            segs[1].performClick();
            assertEquals(1, field(f, "mCurrentTab"));
            segs[0].performClick();
            assertEquals(0, field(f, "mCurrentTab"));
        }
    }

    @Test
    public void onCreateView_backCallbackExitsModes() throws Exception {
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()),
                        list("l2", "Group 2", java.util.Collections.emptyList()))));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.just(Response.success(
                             starredPage(java.util.Collections.emptyList(), null)))));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            StarMergedFragment f = attachResumed();
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            call(f, "enterSortMode", new Class<?>[]{});
            assertTrue((Boolean) field(f, "mSortMode"));
            Object backCb = field(f, "mBackCallback");
            assertNotNull(backCb);
            Method handle = backCb.getClass().getMethod("handleOnBackPressed");
            handle.setAccessible(true);
            handle.invoke(backCb);
            assertFalse((Boolean) field(f, "mSortMode"));
            call(f, "onRepoLongPressed", new Class<?>[]{String.class}, "a/b");
            assertTrue((Boolean) field(f, "mMultiSelect"));
            handle.invoke(backCb);
            assertFalse((Boolean) field(f, "mMultiSelect"));
            handle.invoke(backCb);
        }
    }

    @Test
    public void onDestroyView_viaRemoveClearsState() throws Exception {
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()),
                        list("l2", "Group 2", java.util.Collections.emptyList()))));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.just(Response.success(
                             starredPage(java.util.Collections.emptyList(), null)))));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.just(java.util.Collections.emptyList()))) {
            StarMergedFragment f = attachResumed();
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            call(f, "enterSortMode", new Class<?>[]{});
            assertNotNull(field(f, "mSortTouchHelper"));
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            assertNull(field(f, "mList"));
            assertNull(field(f, "mAdapter"));
            assertNull(field(f, "mSortTouchHelper"));
            assertFalse((Boolean) field(f, "mSortMode"));
            assertFalse((Boolean) field(f, "mMultiSelect"));
        }
    }

    // ---- 残留行补齐（2026-11-15，覆盖 JaCoCo 余量） ----

    @Test
    public void loadUpdates_alreadyLoadingNoForceSkips() throws Exception {
        // 806 行：已加载中且不强制时直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mUpdatesLoading", true);
        try (MockedStatic<RadarGraphQL> mocked = mockRadarFetch(Single.just(
                java.util.Collections.emptyList()))) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
            idle();
            assertTrue((Boolean) field(f, "mUpdatesLoading"));
            assertFalse((Boolean) field(f, "mUpdatesLoaded"));
        }
    }

    @Test
    public void loadStarredPage_successNullItemsEndsList() throws Exception {
        // 752 行：page 非空但 items() 为 null 时按空页处理
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        Page<Repository> page = org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(page.items()).thenReturn(null);
        org.mockito.Mockito.when(page.next()).thenReturn(null);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(page))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            awaitCondition(() -> (Boolean) field(f, "mStarredLoaded"));
            assertFalse((Boolean) field(f, "mStarredHasMore"));
        }
    }

    @Test
    public void onCreate_nullArgumentsLoginIsNull() throws Exception {
        // 226 行：getArguments() == null → mLogin 为 null
        StarMergedFragment f = attach(new StarMergedFragment());
        assertNull(field(f, "mLogin"));
    }

    @Test
    public void switchStarTab_unattachedIsNoop() throws Exception {
        // 525 行：未挂载时 switchStarTab 直接返回
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        call(f, "switchStarTab", new Class<?>[]{int.class}, 1);
        assertEquals(0, field(f, "mCurrentTab"));
    }

    @Test
    public void prefetchOtherTabs_noForcePrefetchesListsFromStarredTab() throws Exception {
        // 611 行：从 Starred 页预取时走 loadOfficialLists(false)
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mCurrentTab", 1);
        setField(f, "mStarredLoaded", true);
        setField(f, "mUpdatesLoaded", true);
        try (MockedStatic<UserListsGraphQL> mocked = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))))) {
            call(f, "prefetchOtherTabs", new Class<?>[]{boolean.class}, false);
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
        }
    }

    @Test
    public void prefetchOtherTabs_noForceSkipsListsWhileLoading() throws Exception {
        // 610 行：列表正在加载时不重复预取
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mCurrentTab", 1);
        setField(f, "mListsLoading", true);
        setField(f, "mStarredLoaded", true);
        setField(f, "mUpdatesLoaded", true);
        try (MockedStatic<UserListsGraphQL> mocked = mockListsFetch(
                Single.just(java.util.Collections.emptyList()))) {
            call(f, "prefetchOtherTabs", new Class<?>[]{boolean.class}, false);
            idle();
            mocked.verifyNoInteractions();
            assertFalse((Boolean) field(f, "mListsLoaded"));
        }
    }

    @Test
    public void loadStarredPage_alreadyLoadingNoForceSkips() throws Exception {
        // 725 行：已加载中且不强制时直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mStarredLoading", true);
        try (MockedStatic<com.gh4a.ServiceFactory> mocked = mockStarring(
                starringService(Single.just(Response.success(starredPage(
                        java.util.Arrays.asList(repo("a/b")), null)))))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, false, false);
            idle();
            assertTrue((Boolean) field(f, "mStarredLoading"));
            assertFalse((Boolean) field(f, "mStarredLoaded"));
        }
    }

    @Test
    public void loadUpdates_forceWhileLoadingCancels() throws Exception {
        // 806-807 行：加载中强制刷新先取消旧请求再重拉
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.disposables.Disposable disposable =
                org.mockito.Mockito.mock(io.reactivex.disposables.Disposable.class);
        org.mockito.Mockito.when(disposable.isDisposed()).thenReturn(false);
        setField(f, "mUpdatesLoading", true);
        setField(f, "mUpdatesDisposable", disposable);
        try (MockedStatic<RadarGraphQL> mocked = mockRadarFetch(Single.just(
                java.util.Arrays.asList(radar("o", "r1", 1L))))) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, true);
            org.mockito.Mockito.verify(disposable).dispose();
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
        }
    }

    @Test
    public void loadOfficialLists_staleGenerationSuccessIsDropped() throws Exception {
        // 670 行：代际已变时迟到的成功回调直接丢弃（反射 bump 代际，不 dispose）
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.subjects.SingleSubject<
                java.util.List<UserListsGraphQL.UserList>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<UserListsGraphQL> mocked = mockListsFetch(subject)) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
            setField(f, "mListsGeneration", (int) field(f, "mListsGeneration") + 1);
            subject.onSuccess(java.util.Arrays.asList(
                    list("l1", "Group 1", java.util.Collections.emptyList())));
            idle();
            assertFalse((Boolean) field(f, "mListsLoaded"));
            assertNull(field(f, "mOfficialLists"));
        }
    }

    @Test
    public void loadOfficialLists_staleGenerationErrorIsDropped() throws Exception {
        // 692 行：代际已变时迟到的失败回调直接丢弃
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.subjects.SingleSubject<
                java.util.List<UserListsGraphQL.UserList>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<UserListsGraphQL> mocked = mockListsFetch(subject)) {
            call(f, "loadOfficialLists", new Class<?>[]{boolean.class}, false);
            setField(f, "mListsGeneration", (int) field(f, "mListsGeneration") + 1);
            subject.onError(new RuntimeException("boom"));
            idle();
            assertFalse((Boolean) field(f, "mListsFailed"));
        }
    }

    @Test
    public void loadStarredPage_staleGenerationSuccessIsDropped() throws Exception {
        // 748 行：代际已变时迟到的成功回调直接丢弃
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.subjects.SingleSubject<Response<Page<Repository>>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockStarring(starringService(subject))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            setField(f, "mStarredGeneration", (int) field(f, "mStarredGeneration") + 1);
            subject.onSuccess(Response.success(starredPage(
                    java.util.Arrays.asList(repo("a/b")), null)));
            idle();
            assertFalse((Boolean) field(f, "mStarredLoaded"));
            assertTrue(((java.util.List<?>) field(f, "mStarred")).isEmpty());
        }
    }

    @Test
    public void loadStarredPage_staleGenerationErrorIsDropped() throws Exception {
        // 775 行：代际已变时迟到的失败回调直接丢弃
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.subjects.SingleSubject<Response<Page<Repository>>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockStarring(starringService(subject))) {
            call(f, "loadStarredPage",
                    new Class<?>[]{boolean.class, boolean.class}, true, false);
            setField(f, "mStarredGeneration", (int) field(f, "mStarredGeneration") + 1);
            subject.onError(new RuntimeException("boom"));
            idle();
            assertFalse((Boolean) field(f, "mStarredFailed"));
        }
    }

    @Test
    public void loadUpdates_staleGenerationSuccessIsDropped() throws Exception {
        // 817 行：代际已变时迟到的成功回调直接丢弃
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.subjects.SingleSubject<
                java.util.List<ReleaseRadarFragment.RadarItem>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<RadarGraphQL> mocked = mockRadarFetch(subject)) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
            setField(f, "mUpdatesGeneration", (int) field(f, "mUpdatesGeneration") + 1);
            subject.onSuccess(java.util.Arrays.asList(radar("o", "r1", 1L)));
            idle();
            assertFalse((Boolean) field(f, "mUpdatesLoaded"));
        }
    }

    @Test
    public void loadUpdates_staleGenerationErrorIsDropped() throws Exception {
        // 835 行：代际已变时迟到的失败回调直接丢弃
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        io.reactivex.subjects.SingleSubject<
                java.util.List<ReleaseRadarFragment.RadarItem>> subject =
                io.reactivex.subjects.SingleSubject.create();
        try (MockedStatic<RadarGraphQL> mocked = mockRadarFetch(subject)) {
            call(f, "loadUpdates", new Class<?>[]{boolean.class}, false);
            setField(f, "mUpdatesGeneration", (int) field(f, "mUpdatesGeneration") + 1);
            subject.onError(new RuntimeException("boom"));
            idle();
            assertFalse((Boolean) field(f, "mUpdatesFailed"));
        }
    }

    @Test
    public void rebuild_updatesTabBuildsUpdatesSection() throws Exception {
        // 898 行：rebuild 在 Updates 页走 buildUpdatesSection
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        setField(f, "mCurrentTab", 2);
        setField(f, "mUpdates", new java.util.ArrayList<>(java.util.Arrays.asList(
                radar("o", "r1", 1L))));
        setField(f, "mUpdatesLoaded", true);
        call(f, "rebuild", new Class<?>[]{});
        assertEquals(2, adapterCount(adapter));
        assertEquals(typeConst("TYPE_SECTION"), adapterViewType(adapter, 0));
        assertEquals(typeConst("TYPE_UPDATE"), adapterViewType(adapter, 1));
    }

    @Test
    public void showAssignToGroupDialog_nonEmptyListsShowsAssignDialog() throws Exception {
        // 1119 行：分组非空时打开归属对话框
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "showAssignToGroupDialog",
                new Class<?>[]{Repository.class}, repo("a/b"));
        AlertDialog dialog = latestDialog();
        assertNotNull(dialog.findViewById(com.gh4a.R.id.list));
    }

    @Test
    public void assignToOfficialLists_nullViewModelIsNoop() throws Exception {
        // 1162 行：ViewModel 为空时 assignToOfficialLists 直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "assignToOfficialLists",
                new Class<?>[]{String.class, java.util.List.class}, "a/b",
                java.util.Arrays.asList("l1"));
        idle();
    }

    @Test
    public void startBatchMove_nullViewModelIsNoop() throws Exception {
        // 1356 行：ViewModel 为空时 startBatchMove 直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "startBatchMove", new Class<?>[]{java.util.List.class},
                java.util.Arrays.asList("l1"));
        idle();
    }

    @Test
    public void showListMenu_unknownItemReturnsFalse() throws Exception {
        // 1191 行：菜单点击未知 id 返回 false
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        UserListsGraphQL.UserList l1 = list("l1", "Group 1",
                java.util.Collections.emptyList());
        android.view.Menu menu = org.mockito.Mockito.mock(android.view.Menu.class);
        MockedConstruction.MockInitializer<PopupMenu> init = (mock, ctx) -> {
            org.mockito.Mockito.when(mock.getMenu()).thenReturn(menu);
        };
        try (MockedConstruction<PopupMenu> mocked =
                     org.mockito.Mockito.mockConstruction(PopupMenu.class, init)) {
            call(f, "showListMenu",
                    new Class<?>[]{View.class, UserListsGraphQL.UserList.class},
                    new TextView(mActivity), l1);
            ArgumentCaptor<PopupMenu.OnMenuItemClickListener> captor =
                    ArgumentCaptor.forClass(PopupMenu.OnMenuItemClickListener.class);
            org.mockito.Mockito.verify(mocked.constructed().get(0))
                    .setOnMenuItemClickListener(captor.capture());
            android.view.MenuItem item =
                    org.mockito.Mockito.mock(android.view.MenuItem.class);
            org.mockito.Mockito.when(item.getItemId()).thenReturn(999);
            assertFalse(captor.getValue().onMenuItemClick(item));
        }
    }

    @Test
    public void showCreateListDialogForAssign_nullViewModelConfirmIsNoop()
            throws Exception {
        // 1151 行：建组确认时 ViewModel 为空直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "showCreateListDialogForAssign",
                new Class<?>[]{String.class}, "a/b");
        AlertDialog dialog = latestDialog();
        idle();
        ((EditText) dialog.findViewById(com.gh4a.R.id.et_list_name))
                .setText("New Group");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    @Test
    public void showCreateListDialogForMove_nullViewModelConfirmIsNoop()
            throws Exception {
        // 1340 行：移组建组确认时 ViewModel 为空直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "showCreateListDialogForMove", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        idle();
        ((EditText) dialog.findViewById(com.gh4a.R.id.et_list_name))
                .setText("New Group");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    @Test
    public void showCreateListDialog_nullViewModelConfirmIsNoop() throws Exception {
        // 1203 行：建组确认时 ViewModel 为空直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        call(f, "showCreateListDialog", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        idle(); // OnShowListener 里装 save 按钮监听
        ((EditText) dialog.findViewById(com.gh4a.R.id.et_list_name))
                .setText("New Group");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    @Test
    public void showRenameListDialog_nullViewModelConfirmIsNoop() throws Exception {
        // 1219 行：改名确认时 ViewModel 为空直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        UserListsGraphQL.UserList l1 = list("l1", "Old Name",
                java.util.Collections.emptyList());
        call(f, "showRenameListDialog",
                new Class<?>[]{UserListsGraphQL.UserList.class}, l1);
        AlertDialog dialog = latestDialog();
        EditText input = findEditText(dialog.getWindow().getDecorView());
        assertNotNull(input);
        input.setText("Renamed");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    @Test
    public void onRepoLongPressed_sortModeIsNoop() throws Exception {
        // 1249 行：排序模式下长按仓库直接返回
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mSortMode", true);
        call(f, "onRepoLongPressed", new Class<?>[]{String.class}, "a/b");
        assertFalse((Boolean) field(f, "mMultiSelect"));
    }

    @Test
    public void listOrderPrefs_nullLoginFallsBackToAuthLogin() throws Exception {
        // 1566 行：mLogin 为空时用 Gh4Application 的登录账号作 prefs 后缀
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mLogin", null);
        call(f, "saveListOrder", new Class<?>[]{java.util.List.class},
                java.util.Arrays.asList(
                        list("l2", "Group 2", java.util.Collections.emptyList()),
                        list("l1", "Group 1", java.util.Collections.emptyList())));
        String prefsName = "star_list_order_"
                + com.gh4a.Gh4Application.get().getAuthLogin();
        String saved = mActivity.getSharedPreferences(prefsName,
                android.content.Context.MODE_PRIVATE)
                .getString("order_ids", null);
        assertEquals("[\"l2\",\"l1\"]", saved);
    }

    @Test
    public void attachSortTouchHelper_secondAttachDetachesPrevious() throws Exception {
        // 1518 行：再次挂载时先把旧 helper 从 RecyclerView 解绑
        StarMergedFragment f = attachWithAdapter();
        RecyclerView rv = adapterParent();
        setField(f, "mList", rv);
        call(f, "attachSortTouchHelper", new Class<?>[]{});
        Object first = field(f, "mSortTouchHelper");
        assertNotNull(first);
        call(f, "attachSortTouchHelper", new Class<?>[]{});
        Object second = field(f, "mSortTouchHelper");
        assertNotNull(second);
        assertTrue(second != first);
    }

    @Test
    public void showMoveDialog_neutralOpensCreateDialog() throws Exception {
        // 1329 行：移组对话框点“新建”走 showCreateListDialogForMove
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b"))),
                list("l2", "Group 2", java.util.Arrays.asList(
                        listRepo("a/b"), listRepo("c/d")))));
        ((java.util.Set<String>) field(f, "mSelectedRepos")).add("a/b");
        call(f, "showMoveDialog", new Class<?>[]{});
        AlertDialog dialog = latestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        idle();
        AlertDialog second = latestDialog();
        assertTrue(second.isShowing());
        assertNotNull(second.findViewById(com.gh4a.R.id.et_list_name));
    }

    @Test
    public void bindGroupHeader_menuClickOpensListMenu() throws Exception {
        // 1745-1749 行：分组卡片 ⋮ 菜单点按 → findListById → showListMenu；
        // 列表被移除后点按则 findListById 为空，不弹菜单
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        android.view.Menu menu = org.mockito.Mockito.mock(android.view.Menu.class);
        MockedConstruction.MockInitializer<PopupMenu> init = (mock, ctx) -> {
            org.mockito.Mockito.when(mock.getMenu()).thenReturn(menu);
        };
        try (MockedConstruction<PopupMenu> mocked =
                     org.mockito.Mockito.mockConstruction(PopupMenu.class, init)) {
            RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
            holder.itemView.findViewById(com.gh4a.R.id.tv_more).performClick();
            assertEquals(1, mocked.constructed().size());
        }
        setField(f, "mOfficialLists", new java.util.ArrayList<>());
        try (MockedConstruction<PopupMenu> mocked =
                     org.mockito.Mockito.mockConstruction(PopupMenu.class, init)) {
            RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
            holder.itemView.findViewById(com.gh4a.R.id.tv_more).performClick();
            assertEquals(0, mocked.constructed().size());
        }
    }

    @Test
    public void bindGroupHeader_emptyTitleShowsQuestionMark() throws Exception {
        // 1708 行：分组名为空时首字母位显示 ?
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "", java.util.Collections.emptyList())));
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 0);
        assertEquals("?",
                ((TextView) holder.itemView.findViewById(com.gh4a.R.id.tv_letter))
                        .getText().toString());
    }

    @Test
    public void bindUngroupedHeader_noDescriptionHidesSubtitle() throws Exception {
        // 1699 行：未分组头无描述时副标题隐藏（mStarredHasMore 为 false）
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("x/y"));
        // mStarredHasMore 默认 true（乐观分页），须显式置 false 才会隐藏副标题
        setField(f, "mStarredHasMore", false);
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_subtitle).getVisibility());
    }

    @Test
    public void bindListRepoRow_nullSubtitleHidesSubtitle() throws Exception {
        // 1769 行：组内仓库无描述时副标题隐藏
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                new UserListsGraphQL.UserList("l1", "Group 1", "desc-l1",
                        java.util.Arrays.asList(
                                new UserListsGraphQL.Repo("x/y", null)))));
        setField(f, "mExpandedOfficialId", "l1");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        assertEquals(View.GONE,
                holder.itemView.findViewById(com.gh4a.R.id.tv_subtitle).getVisibility());
    }

    @Test
    public void bindListRepoRow_clickOpensRepoWhenNotMultiSelect() throws Exception {
        // 1800 行：非多选态点组内仓库行打开仓库页
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        setField(f, "mExpandedOfficialId", "l1");
        call(f, "buildListsSection", new Class<?>[]{});
        RecyclerView.ViewHolder holder = boundHolder(f, adapter, parent, 1);
        holder.itemView.performClick();
        android.content.Intent intent = org.robolectric.Shadows.shadowOf(mActivity)
                .getNextStartedActivity();
        assertNotNull(intent);
        assertEquals(com.gh4a.activities.RepositoryActivity.class.getName(),
                intent.getComponent().getClassName());
    }

    @Test
    public void bindGroupHeader_longClickInMultiSelectReturnsFalse() throws Exception {
        // 1810 行：多选态长按分组头返回 false，不进排序模式
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        setField(f, "mMultiSelect", true);
        call(f, "buildListsSection", new Class<?>[]{});
        // 长按监听返回 false 时 View 会调 showContextMenu，需真实父容器
        parent.setAdapter((RecyclerView.Adapter) adapter);
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1080, 1920);
        // 多选模式 0 号位是 SELECTION_HEADER，分组行在 1 号位
        RecyclerView.ViewHolder holder = parent.findViewHolderForAdapterPosition(1);
        assertNotNull(holder);
        assertFalse(holder.itemView.performLongClick());
        assertFalse((Boolean) field(f, "mSortMode"));
    }

    @Test
    public void bindUngroupedHeader_longClickReturnsFalse() throws Exception {
        // 1817 行：长按未分组头返回 false
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("x/y"));
        call(f, "buildListsSection", new Class<?>[]{});
        // 长按监听返回 false 时 View 会调 showContextMenu，需真实父容器
        parent.setAdapter((RecyclerView.Adapter) adapter);
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, 1080, 1920);
        RecyclerView.ViewHolder holder = parent.findViewHolderForAdapterPosition(1);
        assertNotNull(holder);
        assertFalse(holder.itemView.performLongClick());
    }

    @Test
    public void bindUpdateRow_releaseNameAndCreatedAtFallback() throws Exception {
        // 1874 行：releaseName 非空时用它；1878 行：publishedAt 为 0 时用 createdAt
        StarMergedFragment f = attachWithAdapter();
        Object adapter = field(f, "mAdapter");
        RecyclerView parent = adapterParent();
        setField(f, "mUpdates", java.util.Arrays.asList(
                new ReleaseRadarFragment.RadarItem(
                        "o", "r1", 1L, "v1", "Release 1", 1000L, 900L),
                new ReleaseRadarFragment.RadarItem(
                        "o", "r2", 2L, "v2", "", 0L, 900L)));
        call(f, "buildUpdatesSection", new Class<?>[]{});
        assertEquals(3, adapterCount(adapter));
        RecyclerView.ViewHolder first = boundHolder(f, adapter, parent, 1);
        assertEquals("Release 1",
                ((TextView) first.itemView.findViewById(com.gh4a.R.id.tv_release))
                        .getText().toString());
        RecyclerView.ViewHolder second = boundHolder(f, adapter, parent, 2);
        assertEquals("v2",
                ((TextView) second.itemView.findViewById(com.gh4a.R.id.tv_release))
                        .getText().toString());
    }

    @Test
    public void computeUngrouped_usesDescriptionWhenPresent() throws Exception {
        // 1051 行：未分组仓库有描述时保留描述
        StarMergedFragment f = attach(StarMergedFragment.newInstance("alice"));
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Arrays.asList(listRepo("a/b")))));
        ((java.util.List<Repository>) field(f, "mStarred")).add(repo("a/b"));
        ((java.util.List<Repository>) field(f, "mStarred")).add(richRepo());
        java.util.List<?> out =
                (java.util.List<?>) call(f, "computeUngrouped", new Class<?>[]{});
        assertEquals(1, out.size());
        assertEquals("A cat repo", fieldOf(out.get(0), "description"));
    }

    @Test
    public void buildListsSection_failedWithStaleCacheShowsRetryRow() throws Exception {
        // 941 行：缓存过期（年龄超 STALE_CACHE_AGE_MS）时失败行也显示重试
        StarMergedFragment f = attachWithAdapter();
        UserListsCache.save(mActivity, "alice", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        mActivity.getSharedPreferences("user_lists_cache",
                android.content.Context.MODE_PRIVATE)
                .edit().putLong("time_alice",
                        System.currentTimeMillis() - 2 * 3600 * 1000).apply();
        setField(f, "mOfficialLists", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        setField(f, "mListsFailed", true);
        call(f, "buildListsSection", new Class<?>[]{});
        assertEquals(typeConst("TYPE_STATUS"), itemType(items(f).get(0)));
        assertTrue(itemBool(items(f).get(0), "retryLists"));
    }

    private StarMergedFragment attachResumedWithSavedState(Bundle saved) throws Exception {
        mActivity.setTheme(com.gh4a.R.style.AppTheme);
        StarMergedFragment f = StarMergedFragment.newInstance("alice");
        f.setInitialSavedState(savedState(saved));
        mActivity.getSupportFragmentManager().beginTransaction().add(f, "test")
                .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
        return f;
    }

    @Test
    public void onCreateView_noCacheLoadsListsFromNetwork() throws Exception {
        // 356 行：缓存 Single 取到 null 时直接返回，网络加载随后完成
        try (MockedStatic<UserListsGraphQL> mockedLists = mockListsFetch(Single.just(
                java.util.Arrays.asList(
                        list("l1", "Group 1", java.util.Collections.emptyList()))));
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.never()));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.never())) {
            StarMergedFragment f = attachResumed();
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            assertEquals(1, ((java.util.List<?>) field(f, "mOfficialLists")).size());
        }
    }

    @Test
    public void onCreateView_restoredListsLoadedRestoresFlagFromCache() throws Exception {
        // 367 行：恢复态 star_lists_loaded=true 时用缓存置 mListsLoaded
        UserListsCache.save(mActivity, "alice", java.util.Arrays.asList(
                list("l1", "Group 1", java.util.Collections.emptyList())));
        Bundle saved = new Bundle();
        saved.putBoolean("star_lists_loaded", true);
        try (MockedStatic<UserListsGraphQL> mockedLists =
                     mockListsFetch(Single.never());
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.never()));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.never())) {
            StarMergedFragment f = attachResumedWithSavedState(saved);
            awaitCondition(() -> (Boolean) field(f, "mListsLoaded"));
            assertEquals(1, ((java.util.List<?>) field(f, "mOfficialLists")).size());
        }
    }

    @Test
    public void onCreateView_restoredUpdatesLoadedRestoresFromRadarCache() throws Exception {
        // 377-383 行：恢复态 star_updates_loaded=true 时从 RadarCache 恢复更新
        RadarCache.save(mActivity, "alice",
                java.util.Arrays.asList(radar("o", "r1", 1L)));
        Bundle saved = new Bundle();
        saved.putBoolean("star_updates_loaded", true);
        try (MockedStatic<UserListsGraphQL> mockedLists =
                     mockListsFetch(Single.never());
             MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     mockStarring(starringService(Single.never()));
             MockedStatic<RadarGraphQL> mockedRadar =
                     mockRadarFetch(Single.never())) {
            StarMergedFragment f = attachResumedWithSavedState(saved);
            assertTrue((Boolean) field(f, "mUpdatesLoaded"));
            assertEquals(1, ((java.util.List<?>) field(f, "mUpdates")).size());
            assertFalse((Boolean) field(f, "mUpdatesFailed"));
        }
    }

    @Test
    public void updateStarBadge_homeActivityHostSetsBadgeCount() throws Exception {
        // 855-857 行：挂在真实 HomeActivity 上时更新角标
        com.gh4a.utils.NotificationCountProvider.getInstance().invalidate();
        com.meisolsson.githubsdk.service.users.UserService userService =
                org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.users.UserService.class);
        com.meisolsson.githubsdk.model.User octocat =
                com.meisolsson.githubsdk.model.User.builder()
                        .login("octocat").id(1L).build();
        org.mockito.Mockito.when(userService.getUser(
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Single.just(Response.success(octocat)));
        com.meisolsson.githubsdk.service.activity.NotificationService
                notificationService = org.mockito.Mockito.mock(
                        com.meisolsson.githubsdk.service.activity.NotificationService.class);
        Page<com.meisolsson.githubsdk.model.NotificationThread> emptyPage =
                org.mockito.Mockito.mock(Page.class);
        org.mockito.Mockito.when(emptyPage.items())
                .thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.when(notificationService.getNotifications(
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(emptyPage)));
        StarringService starring = starringService(Single.never());
        try (MockedStatic<com.gh4a.ServiceFactory> mockedFactory =
                     org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<com.gh4a.utils.SingleFactory> mockedSingle =
                     org.mockito.Mockito.mockStatic(com.gh4a.utils.SingleFactory.class,
                             org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<UserListsGraphQL> mockedLists =
                     mockListsFetch(Single.never());
             MockedStatic<RadarGraphQL> mockedRadar = mockRadarFetch(Single.just(
                     java.util.Arrays.asList(radar("o", "r1", 1L))))) {
            mockedFactory.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.users.UserService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(userService);
            mockedFactory.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(
                                    com.meisolsson.githubsdk.service.activity.NotificationService.class),
                            org.mockito.ArgumentMatchers.anyBoolean(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenReturn(notificationService);
            mockedFactory.when(() -> com.gh4a.ServiceFactory.get(
                            org.mockito.ArgumentMatchers.eq(StarringService.class),
                            org.mockito.ArgumentMatchers.anyBoolean()))
                    .thenReturn(starring);
            mockedSingle.when(() -> com.gh4a.utils.SingleFactory.getNotifications(
                            false, false, false))
                    .thenReturn(Single.just(
                            new com.gh4a.model.NotificationListLoadResult(
                                    java.util.Collections.emptyList())));
            org.robolectric.android.controller.ActivityController<
                    com.gh4a.activities.home.HomeActivity> controller =
                    com.gh4a.activities.home.HomeActivityTestHelper.launchHome();
            com.gh4a.activities.home.HomeActivity home = controller.setup().get();
            int[] starCount = {-1};
            home.getBadgeState().addListener((n, s) -> starCount[0] = s);
            StarMergedFragment f = StarMergedFragment.newInstance("alice");
            home.getSupportFragmentManager().beginTransaction().add(f, "test")
                    .setMaxLifecycle(f, Lifecycle.State.RESUMED).commitNow();
            awaitCondition(() -> (Boolean) field(f, "mUpdatesLoaded"));
            assertEquals(1, starCount[0]);
        }
    }
}
