package com.gh4a.fragment;

import android.content.DialogInterface;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.view.MenuInflater;

import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ApplicationProvider;

import com.gh4a.R;
import com.gh4a.ServiceFactory;
import com.gh4a.db.SuggestionsProvider;
import com.gh4a.utils.ApiHelpers;
import com.meisolsson.githubsdk.model.Page;
import com.meisolsson.githubsdk.model.Repository;
import com.meisolsson.githubsdk.model.SearchCode;
import com.meisolsson.githubsdk.model.SearchPage;
import com.meisolsson.githubsdk.model.TextMatch;
import com.meisolsson.githubsdk.model.User;
import com.meisolsson.githubsdk.service.repositories.RepositoryService;
import com.meisolsson.githubsdk.service.search.SearchService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SearchFragment：搜索页（Robolectric，CREATED 态挂载；私有静态方法走反射）。 */
@RunWith(RobolectricTestRunner.class)
public class SearchFragmentTest {
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

    private static int findSortMenuItemId(String sort, String order) throws Exception {
        Method m = SearchFragment.class.getDeclaredMethod(
                "findSortMenuItemId", String.class, String.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, sort, order);
    }

    private static boolean isClearRowCursor(Cursor cursor) throws Exception {
        Method m = SearchFragment.class.getDeclaredMethod("isClearRowCursor", Cursor.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(null, cursor);
    }

    private static Object field(SearchFragment f, String name) throws Exception {
        // mRefreshPending 等字段在父类 PagedDataBaseFragment，沿继承链找
        Class<?> clazz = SearchFragment.class;
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(f);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(SearchFragment f, String name, Object value)
            throws Exception {
        Field field = SearchFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(f, value);
    }

    private static void setMockSearchView(SearchFragment f) throws Exception {
        setField(f, "mSearch", mock(SearchView.class));
    }

    private static Object call(SearchFragment f, String name, Class<?>[] types,
            Object... args) throws Exception {
        Method m = SearchFragment.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(f, args);
    }

    private static void setFragmentView(SearchFragment f, android.view.View view)
            throws Exception {
        // updateEmptyText() 直接 getView().findViewById；CREATED 态下给假视图
        Field field = androidx.fragment.app.Fragment.class.getDeclaredField("mView");
        field.setAccessible(true);
        field.set(f, view);
    }

    private android.view.View fakeViewWithEmpty() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(mActivity);
        android.widget.TextView empty = new android.widget.TextView(mActivity);
        empty.setId(android.R.id.empty);
        root.addView(empty);
        return root;
    }

    private static Repository hotRepo(String fullName, int stars) {
        String[] parts = fullName.split("/", 2);
        return Repository.builder()
                .fullName(fullName)
                .owner(User.builder().login(parts[0]).build())
                .name(parts[1])
                .stargazersCount(stars)
                .build();
    }

    @Test
    public void searchTypeConstants() {
        assertEquals(0, SearchFragment.SEARCH_TYPE_REPO);
        assertEquals(1, SearchFragment.SEARCH_TYPE_USER);
        assertEquals(2, SearchFragment.SEARCH_TYPE_CODE);
    }

    @Test
    public void newInstance_storesTypeQueryAndImmediateFlag() {
        SearchFragment f = SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, "android", true);
        assertEquals(SearchFragment.SEARCH_TYPE_USER,
                f.getArguments().getInt("search_type"));
        assertEquals("android", f.getArguments().getString("initial_search"));
        assertTrue(f.getArguments().getBoolean("do_initial_load"));
    }

    @Test
    public void newInstance_nullQueryIsKeptNull() {
        SearchFragment f = SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false);
        assertEquals(null, f.getArguments().getString("initial_search"));
        assertFalse(f.getArguments().getBoolean("do_initial_load"));
    }

    @Test
    public void onCreate_readsTypeAndQueryFromArguments() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, "kotlin", true));
        assertEquals(SearchFragment.SEARCH_TYPE_USER, field(f, "mSelectedSearchType"));
        assertEquals("kotlin", field(f, "mQuery"));
    }

    @Test
    public void onCreate_emptyQueryBecomesNull() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, "", true));
        assertEquals(null, field(f, "mQuery"));
    }

    @Test
    public void onCreate_nullQueryBecomesNull() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        assertEquals(null, field(f, "mQuery"));
        assertEquals(SearchFragment.SEARCH_TYPE_CODE, field(f, "mSelectedSearchType"));
    }

    @Test
    public void shouldDoInitialLoad_noArgsDefaultsTrue() {
        SearchFragment f = attach(new SearchFragment());
        assertTrue(f.shouldDoInitialLoad(null));
    }

    @Test
    public void shouldDoInitialLoad_explicitFalseIsHonored() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertFalse(f.shouldDoInitialLoad(null));
    }

    @Test
    public void shouldDoInitialLoad_savedStateWithQueryRestores() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, "q", false));
        // mQuery 已由 onCreate 设为 "q"；savedState 非空即恢复上次加载。
        assertTrue(f.shouldDoInitialLoad(new Bundle()));
    }

    @Test
    public void shouldDoInitialLoad_savedStateWithoutQueryDoesNotRestore() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertFalse(f.shouldDoInitialLoad(new Bundle()));
    }

    @Test
    public void getEmptyTextResId_isZeroUntilUpdated() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertEquals(0, f.getEmptyTextResId());
    }

    @Test
    public void findSortMenuItemId_knownPairs() throws Exception {
        assertEquals(R.id.search_sort_repo_stars_desc, findSortMenuItemId("stars", "desc"));
        assertEquals(R.id.search_sort_repo_stars_asc, findSortMenuItemId("stars", "asc"));
        assertEquals(R.id.search_sort_user_followers_desc,
                findSortMenuItemId("followers", "desc"));
        assertEquals(R.id.search_sort_code_indexed_asc, findSortMenuItemId("indexed", "asc"));
    }

    @Test
    public void findSortMenuItemId_nullPairMapsToBestMatch() throws Exception {
        assertEquals(R.id.search_sort_best_match, findSortMenuItemId(null, null));
    }

    @Test
    public void findSortMenuItemId_unknownPairReturnsZero() throws Exception {
        assertEquals(0, findSortMenuItemId("bogus", "desc"));
        assertEquals(0, findSortMenuItemId("stars", "sideways"));
        assertEquals(0, findSortMenuItemId(null, "desc"));
        assertEquals(0, findSortMenuItemId("stars", null));
    }

    @Test
    public void isClearRowCursor_maxValueMeansClearRow() throws Exception {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getLong(0)).thenReturn(Long.MAX_VALUE);
        assertTrue(isClearRowCursor(cursor));
        when(cursor.getLong(0)).thenReturn(5L);
        assertFalse(isClearRowCursor(cursor));
        when(cursor.getLong(0)).thenReturn(0L);
        assertFalse(isClearRowCursor(cursor));
    }

    @Test
    public void isClearRowCursor_nullCursorIsFalse() throws Exception {
        assertFalse(isClearRowCursor(null));
    }

    @Test
    public void onSaveInstanceState_storesQueryAndType() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, "q", true));
        Bundle out = new Bundle();
        f.onSaveInstanceState(out);
        assertEquals("q", out.getString("query"));
        assertEquals(SearchFragment.SEARCH_TYPE_USER, out.getInt("search_type"));
    }

    // ---- 搜索交互（CREATED 态；mSearch 未建，用 mock 顶上；onRefresh 只置位） ----

    @Test
    public void onSuggestionSelect_returnsFalse() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertFalse(f.onSuggestionSelect(0));
    }

    @Test
    public void runQuery_emptyAndNullReturnNull() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertNull(f.runQuery(""));
        assertNull(f.runQuery(null));
    }

    @Test
    public void onSearchFragmentClick_nullUrlIsNoop() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        // mock 默认 url()/repository() 均 null → openFileViewer 直接返回
        f.onSearchFragmentClick(mock(SearchCode.class), 0);
    }

    @Test
    public void onSearchFragmentClick_nullRepoIsNoop() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        SearchCode result = mock(SearchCode.class);
        when(result.url()).thenReturn("https://github.com/o/r/blob/main/f.java");
        when(result.repository()).thenReturn(null);
        f.onSearchFragmentClick(result, -1);
    }

    @Test
    public void onSearchFragmentClick_fullResultStartsFileViewer() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        User owner = mock(User.class);
        when(owner.login()).thenReturn("octo");
        Repository repo = mock(Repository.class);
        when(repo.owner()).thenReturn(owner);
        when(repo.name()).thenReturn("cat");
        SearchCode result = mock(SearchCode.class);
        when(result.url()).thenReturn(
                "https://github.com/octo/cat/blob/main/src/Main.java?ref=main");
        when(result.repository()).thenReturn(repo);
        when(result.path()).thenReturn("src/Main.java");
        f.onSearchFragmentClick(result, -1);
        android.app.Application app = (android.app.Application)
                ApplicationProvider.getApplicationContext();
        android.content.Intent intent =
                Shadows.shadowOf(app).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("FileViewerActivity"));
    }

    @Test
    public void onQueryTextSubmit_setsQueryAndMarksRefreshPending() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        assertTrue(f.onQueryTextSubmit("kotlin"));
        assertEquals("kotlin", field(f, "mQuery"));
        assertTrue((Boolean) field(f, "mRefreshPending"));
    }

    @Test
    public void onQueryTextSubmit_blankQueryStillRefreshes() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        assertTrue(f.onQueryTextSubmit(""));
        assertEquals("", field(f, "mQuery"));
        assertTrue((Boolean) field(f, "mRefreshPending"));
    }

    @Test
    public void onQueryTextChange_emptyRestoresDefaultPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertTrue(f.onQueryTextChange(""));
        assertEquals("", field(f, "mQuery"));
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onQueryTextChange_nonEmptySetsQuery() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        assertTrue(f.onQueryTextChange("kot"));
        assertEquals("kot", field(f, "mQuery"));
    }

    @Test
    public void onClose_clearsQuery() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, "kotlin", false));
        setMockSearchView(f);
        assertEquals("kotlin", field(f, "mQuery"));
        assertTrue(f.onClose());
        assertNull(field(f, "mQuery"));
    }

    @Test
    public void onSuggestionClick_noSuggestionsAdapterReturnsTrue() throws Exception {
        // mSearch 未建建议适配器 → 直接返回 true，不碰 cursor。
        // 注：mSearch 本身为 null 时直接 NPE（见缺陷记录 T-125），
        // 生产上建议点击只在 SearchView 存在时触发，故用 mock 顶上测本分支。
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        assertTrue(f.onSuggestionClick(0));
    }

    @Test
    public void onRefresh_viewNotReadyMarksPending() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        f.onRefresh();
        assertTrue((Boolean) field(f, "mRefreshPending"));
    }

    @Test
    public void canChildScrollUp_noStoreFallsBackToBase() {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        // 基类在视图未就绪时返回 false（mRecyclerView 为 null）
        assertFalse(f.canChildScrollUp());
    }

    // ---- 默认头 / 热门行（纯视图构建；最近搜索走 ContentProvider，测试环境无注册 → 空） ----

    private android.view.View inflateDefaultHeader(SearchFragment f) throws Exception {
        android.view.View header = android.view.LayoutInflater.from(mActivity)
                .inflate(com.gh4a.R.layout.search_default_header, null);
        setField(f, "mDefaultHeader", header);
        return header;
    }

    @Test
    public void loadRecentSearches_noProviderReturnsEmpty() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        java.util.List<String> recents =
                (java.util.List<String>) call(f, "loadRecentSearches", new Class<?>[]{});
        assertTrue(recents.isEmpty());
    }

    @Test
    public void refreshDefaultHeader_userTypeShowsEmptyRecentsAndHidesHot()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        android.view.View header = inflateDefaultHeader(f);
        call(f, "refreshDefaultHeader", new Class<?>[]{});
        android.widget.LinearLayout recent = header.findViewById(
                com.gh4a.R.id.search_default_recent_container);
        assertEquals(1, recent.getChildCount());
        assertEquals(mActivity.getString(com.gh4a.R.string.search_no_recent_searches),
                ((android.widget.TextView) recent.getChildAt(0)).getText().toString());
        assertEquals(android.view.View.GONE,
                header.findViewById(com.gh4a.R.id.search_default_hot_title)
                        .getVisibility());
        assertEquals(android.view.View.GONE,
                header.findViewById(com.gh4a.R.id.search_default_hot_container)
                        .getVisibility());
    }

    @Test
    public void refreshDefaultHeader_repoTypeWithHotReposFillsHotContainer()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mHotRepos", java.util.Arrays.asList(
                hotRepo("o/r", 42), hotRepo("x/y", 7)));
        android.view.View header = inflateDefaultHeader(f);
        call(f, "refreshDefaultHeader", new Class<?>[]{});
        android.widget.LinearLayout hot = header.findViewById(
                com.gh4a.R.id.search_default_hot_container);
        assertEquals(android.view.View.VISIBLE, hot.getVisibility());
        assertEquals(2, hot.getChildCount());
        android.widget.LinearLayout row =
                (android.widget.LinearLayout) hot.getChildAt(0);
        assertEquals("o/r",
                ((android.widget.TextView) row.getChildAt(0)).getText().toString());
        assertTrue(((android.widget.TextView) row.getChildAt(1))
                .getText().toString().contains("42"));
    }

    @Test
    public void showHotSkeleton_addsFiveBars() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "showHotSkeleton",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(5, container.getChildCount());
    }

    @Test
    public void fillHotContainer_withReposAddsRows() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mHotRepos", java.util.Arrays.asList(
                hotRepo("o/r", 42), hotRepo("x/y", 7)));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(2, container.getChildCount());
    }

    @Test
    public void fillHotContainer_nullReposIsNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(0, container.getChildCount());
    }

    @Test
    public void fillHotContainerFromCache_withCachedAddsRows() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainerFromCache",
                new Class<?>[]{android.widget.LinearLayout.class, java.util.List.class},
                container, java.util.Arrays.asList(
                        new HotReposCache.HotRepo("a/b", 10),
                        new HotReposCache.HotRepo("c/d", 5)));
        assertEquals(2, container.getChildCount());
        android.widget.LinearLayout row =
                (android.widget.LinearLayout) container.getChildAt(0);
        assertEquals("a/b",
                ((android.widget.TextView) row.getChildAt(0)).getText().toString());
        assertTrue(((android.widget.TextView) row.getChildAt(1))
                .getText().toString().contains("10"));
    }

    @Test
    public void makeHotRow_buildsTitleAndStars() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout row = (android.widget.LinearLayout) call(f,
                "makeHotRow",
                new Class<?>[]{String.class, int.class,
                        android.view.View.OnClickListener.class},
                "o/r", 42, null);
        assertEquals("o/r",
                ((android.widget.TextView) row.getChildAt(0)).getText().toString());
        assertEquals("★ 42",
                ((android.widget.TextView) row.getChildAt(1)).getText().toString());
    }

    // ---- 动作表 / 排序 / 类型切换 ----

    @Test
    public void showTypeSheet_showsDialog() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        call(f, "showTypeSheet", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void showSortSheet_showsDialog() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        int before = org.robolectric.shadows.ShadowDialog.getShownDialogs().size();
        call(f, "showSortSheet", new Class<?>[]{});
        assertTrue(org.robolectric.shadows.ShadowDialog.getShownDialogs().size()
                > before);
    }

    @Test
    public void onOptionsItemSelected_typeShowsTypeSheet() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(item.getItemId()).thenReturn(com.gh4a.R.id.type);
        assertTrue(f.onOptionsItemSelected(item));
        assertFalse(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
    }

    @Test
    public void onOptionsItemSelected_sortShowsSortSheet() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(item.getItemId()).thenReturn(com.gh4a.R.id.sort);
        assertTrue(f.onOptionsItemSelected(item));
        assertFalse(org.robolectric.shadows.ShadowDialog.getShownDialogs().isEmpty());
    }

    @Test
    public void onOptionsItemSelected_otherDelegatesToSuper() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(item.getItemId()).thenReturn(999999);
        assertFalse(f.onOptionsItemSelected(item));
    }

    @Test
    public void saveSortSelection_persistsSortAndOrder() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSort", "stars");
        setField(f, "mOrder", "desc");
        call(f, "saveSortSelection", new Class<?>[]{int.class},
                SearchFragment.SEARCH_TYPE_REPO);
        android.content.SharedPreferences prefs = mActivity.getSharedPreferences(
                SettingsFragment.PREF_NAME, android.content.Context.MODE_PRIVATE);
        assertEquals("stars", prefs.getString("search_sort_0_sort", null));
        assertEquals("desc", prefs.getString("search_sort_0_order", null));
    }

    @Test
    public void setSortSelection_sameSortDifferentOrder_proceeds()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        setField(f, "mSort", "created");
        setField(f, "mOrder", "desc");
        call(f, "setSortSelection",
                new Class<?>[]{String.class, String.class}, "created", "asc");
        // sort 相同、order 不同：932 的 && 不短路，照常保存并刷新。
        assertEquals("created", field(f, "mSort"));
        assertEquals("asc", field(f, "mOrder"));
        assertTrue((Boolean) field(f, "mRefreshPending"));
    }

    @Test
    public void setSortSelection_sameValuesIsNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSort", "stars");
        setField(f, "mOrder", "desc");
        call(f, "setSortSelection",
                new Class<?>[]{String.class, String.class}, "stars", "desc");
        assertEquals("stars", field(f, "mSort"));
        assertEquals("desc", field(f, "mOrder"));
    }

    @Test
    public void setSortSelection_newValuesSavesAndRefreshes() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        setField(f, "mSort", "created");
        setField(f, "mOrder", "desc");
        call(f, "setSortSelection",
                new Class<?>[]{String.class, String.class}, "stars", "asc");
        assertEquals("stars", field(f, "mSort"));
        assertEquals("asc", field(f, "mOrder"));
        assertTrue((Boolean) field(f, "mRefreshPending"));
    }

    @Test
    public void applySearchType_sameTypeIsNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        call(f, "applySearchType", new Class<?>[]{int.class},
                SearchFragment.SEARCH_TYPE_REPO);
        assertEquals(SearchFragment.SEARCH_TYPE_REPO, field(f, "mSelectedSearchType"));
    }

    @Test
    public void applySearchType_emptyQueryUserTypeLoadsDefaultPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        setMockSearchView(f);
        setFragmentView(f, fakeViewWithEmpty());
        call(f, "applySearchType", new Class<?>[]{int.class},
                SearchFragment.SEARCH_TYPE_USER);
        assertEquals(SearchFragment.SEARCH_TYPE_USER, field(f, "mSelectedSearchType"));
        org.mockito.Mockito.verify(adapter)
                .setMode(SearchFragment.SEARCH_TYPE_USER);
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void applySearchType_withQueryResubmitsQuery() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, "kotlin", false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        SearchView mockSearch = mock(SearchView.class);
        setField(f, "mSearch", mockSearch);
        setFragmentView(f, fakeViewWithEmpty());
        call(f, "applySearchType", new Class<?>[]{int.class},
                SearchFragment.SEARCH_TYPE_USER);
        org.mockito.Mockito.verify(mockSearch).setQuery("kotlin", true);
    }

    // ---- loadPage / make*SearchSingle（ServiceFactory 挡住，不发网络） ----

    private static org.mockito.MockedStatic<com.gh4a.ServiceFactory>
            mockSearchService(
                    com.meisolsson.githubsdk.service.search.SearchService service) {
        org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                org.mockito.Mockito.mockStatic(com.gh4a.ServiceFactory.class,
                        org.mockito.Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> com.gh4a.ServiceFactory.get(
                        org.mockito.ArgumentMatchers.eq(
                                com.meisolsson.githubsdk.service.search.SearchService.class),
                        org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(service);
        mocked.when(() -> com.gh4a.ServiceFactory.get(
                        org.mockito.ArgumentMatchers.eq(
                                com.meisolsson.githubsdk.service.search.SearchService.class),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(service);
        return mocked;
    }

    @Test
    public void loadPage_emptyQueryWithHeaderReturnsDummyPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mDefaultHeader", new android.view.View(mActivity));
        io.reactivex.Single<retrofit2.Response<com.meisolsson.githubsdk.model.Page<Object>>> single =
                (io.reactivex.Single<retrofit2.Response<com.meisolsson.githubsdk.model.Page<Object>>>)
                        call(f, "loadPage",
                                new Class<?>[]{int.class, boolean.class}, 1, false);
        retrofit2.Response<com.meisolsson.githubsdk.model.Page<Object>> response =
                single.blockingGet();
        assertTrue(response.body().items().isEmpty());
    }

    @Test
    public void loadPage_emptyQueryUserTypeReturnsDummyPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        io.reactivex.Single<retrofit2.Response<com.meisolsson.githubsdk.model.Page<Object>>> single =
                (io.reactivex.Single<retrofit2.Response<com.meisolsson.githubsdk.model.Page<Object>>>)
                        call(f, "loadPage",
                                new Class<?>[]{int.class, boolean.class}, 1, false);
        assertTrue(single.blockingGet().body().items().isEmpty());
    }

    @Test
    public void loadPage_repoQueryBuildsSearchSingle() throws Exception {
        com.meisolsson.githubsdk.service.search.SearchService service =
                mock(com.meisolsson.githubsdk.service.search.SearchService.class);
        when(service.searchRepositories(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.error(new RuntimeException("no net")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, "kotlin", false));
            Object single = call(f, "loadPage",
                    new Class<?>[]{int.class, boolean.class}, 1, false);
            assertNotNull(single);
            org.mockito.Mockito.verify(service).searchRepositories(
                    org.mockito.ArgumentMatchers.eq("kotlin fork:true"),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(1L));
        }
    }

    @Test
    public void loadPage_userQueryBuildsSearchSingle() throws Exception {
        com.meisolsson.githubsdk.service.search.SearchService service =
                mock(com.meisolsson.githubsdk.service.search.SearchService.class);
        when(service.searchUsers(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.error(new RuntimeException("no net")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_USER, "octo", false));
            assertNotNull(call(f, "loadPage",
                    new Class<?>[]{int.class, boolean.class}, 1, false));
            org.mockito.Mockito.verify(service).searchUsers(
                    org.mockito.ArgumentMatchers.eq("octo"),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(1L));
        }
    }

    @Test
    public void loadPage_codeQueryBuildsSearchSingle() throws Exception {
        com.meisolsson.githubsdk.service.search.SearchService service =
                mock(com.meisolsson.githubsdk.service.search.SearchService.class);
        when(service.searchCode(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(io.reactivex.Single.error(new RuntimeException("no net")));
        try (org.mockito.MockedStatic<com.gh4a.ServiceFactory> mocked =
                     mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_CODE, "println", false));
            assertNotNull(call(f, "loadPage",
                    new Class<?>[]{int.class, boolean.class}, 1, false));
            org.mockito.Mockito.verify(service).searchCode(
                    org.mockito.ArgumentMatchers.eq("println"),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(1L));
        }
    }

    // ---- onItemClick / onSuggestionClick ----

    @Test
    public void onItemClick_repositoryStartsRepositoryActivity() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        f.onItemClick(hotRepo("o/r", 42));
        android.content.Intent intent =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("RepositoryActivity"));
    }

    @Test
    public void onItemClick_searchCodeOpensFileViewer() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        SearchCode result = mock(SearchCode.class);
        Repository repo = hotRepo("o/r", 0);
        when(result.url()).thenReturn(
                "https://github.com/o/r/blob/main/src/Main.java?ref=main");
        when(result.repository()).thenReturn(repo);
        when(result.path()).thenReturn("src/Main.java");
        f.onItemClick(result);
        android.content.Intent intent =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("FileViewerActivity"));
    }

    @Test
    public void onItemClick_userStartsUserActivity() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        User user = mock(User.class);
        when(user.login()).thenReturn("octo");
        f.onItemClick(user);
        android.content.Intent intent =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("UserActivity"));
    }

    @Test
    public void onSuggestionClick_suggestionRowSetsQuery() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView mockSearch = mock(SearchView.class);
        androidx.cursoradapter.widget.CursorAdapter adapter =
                mock(androidx.cursoradapter.widget.CursorAdapter.class);
        android.database.MatrixCursor cursor = new android.database.MatrixCursor(
                new String[]{"_id", "suggestion"});
        cursor.addRow(new Object[]{1L, "kotlin"});
        cursor.moveToFirst();
        when(adapter.getCursor()).thenReturn(cursor);
        when(mockSearch.getSuggestionsAdapter()).thenReturn(adapter);
        setField(f, "mSearch", mockSearch);
        assertTrue(f.onSuggestionClick(0));
        assertEquals("kotlin", field(f, "mQuery"));
        org.mockito.Mockito.verify(mockSearch).setQuery("kotlin", true);
    }

    @Test
    public void loadDefaultPage_storeTabShowsStoreEarly() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        // do_initial_load=false → 商店页签；无视图时 showStore 直接返回
        call(f, "loadDefaultPage", new Class<?>[]{});
    }

    @Test
    public void attachDefaultHeader_withAdapterInflatesHeader() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        call(f, "attachDefaultHeader", new Class<?>[]{});
        assertNotNull(field(f, "mDefaultHeader"));
        org.mockito.Mockito.verify(adapter)
                .setHeaderView((android.view.View) field(f, "mDefaultHeader"));
    }

    // ---- 第二批：capSearchBoxWidth / verifySortVisible ----

    private static void idleMain() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    private Toolbar installToolbar() {
        Toolbar toolbar = new Toolbar(mActivity);
        toolbar.setId(com.gh4a.R.id.toolbar);
        mActivity.setContentView(toolbar);
        return toolbar;
    }

    private Toolbar installZeroWidthToolbar() {
        Toolbar toolbar = new Toolbar(mActivity);
        toolbar.setId(com.gh4a.R.id.toolbar);
        // 注意：单参 setContentView 会用 MATCH_PARENT 覆盖 LayoutParams，
        // 必须用双参版本才能保住宽 0。
        mActivity.setContentView(toolbar,
                new android.view.ViewGroup.LayoutParams(
                        0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        return toolbar;
    }

    private android.view.View findText(android.view.View root, String text) {
        if (root instanceof android.widget.TextView
                && text.equals(((android.widget.TextView) root)
                        .getText().toString())) {
            return root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View found = findText(g.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private android.widget.LinearLayout sheetContent(
            android.app.Dialog dialog, String title) {
        android.view.View titleView =
                findText(dialog.getWindow().getDecorView(), title);
        assertNotNull(titleView);
        return (android.widget.LinearLayout) titleView.getParent();
    }

    private void insertSuggestion(String suggestion, int type) {
        android.content.ContentValues cv = new android.content.ContentValues();
        cv.put(SuggestionsProvider.Columns.TYPE, type);
        cv.put(SuggestionsProvider.Columns.SUGGESTION, suggestion);
        cv.put(SuggestionsProvider.Columns.DATE, System.currentTimeMillis());
        mActivity.getContentResolver().insert(
                SuggestionsProvider.Columns.CONTENT_URI, cv);
    }

    private void clearSuggestions() {
        mActivity.getContentResolver().delete(
                SuggestionsProvider.Columns.CONTENT_URI, null, null);
    }

    private void clearHotCache() {
        mActivity.getSharedPreferences("hot_repos_cache",
                android.content.Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static MockedStatic<StoreApi> mockStoreApi() {
        MockedStatic<StoreApi> mocked =
                Mockito.mockStatic(StoreApi.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> StoreApi.loadBackend(ArgumentMatchers.any(),
                        ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenReturn(Single.just(new StoreApi.StoreData(
                        java.util.Collections.emptyList(), false, null)));
        mocked.when(() -> StoreApi.loadNewest(ArgumentMatchers.any()))
                .thenReturn(Single.just(new StoreApi.StoreData(
                        java.util.Collections.emptyList(), false, null)));
        return mocked;
    }

    private static MockedStatic<ServiceFactory> mockRepositoryService(
            RepositoryService service) {
        MockedStatic<ServiceFactory> mocked = Mockito.mockStatic(
                ServiceFactory.class, Mockito.CALLS_REAL_METHODS);
        mocked.when(() -> ServiceFactory.get(
                        ArgumentMatchers.eq(RepositoryService.class),
                        ArgumentMatchers.anyBoolean()))
                .thenReturn(service);
        return mocked;
    }

    private <T> SearchPage<T> searchPageWith(java.util.List<T> items) {
        SearchPage<T> page = mock(SearchPage.class);
        when(page.items()).thenReturn(items);
        when(page.next()).thenReturn(null);
        return page;
    }

    private CursorAdapter newSuggestionAdapter(SearchFragment f) throws Exception {
        Class<?> clazz = Class.forName(
                "com.gh4a.fragment.SearchFragment$SuggestionAdapter");
        java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor(
                SearchFragment.class, android.content.Context.class);
        ctor.setAccessible(true);
        return (CursorAdapter) ctor.newInstance(f, mActivity);
    }

    @Test
    public void onCreateOptionsMenu_buildsSearchViewAndCapsWidth() throws Exception {
        installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setFragmentView(f, fakeViewWithEmpty());
        MenuBuilder menu = new MenuBuilder(mActivity);
        f.onCreateOptionsMenu(menu,
                new androidx.appcompat.view.SupportMenuInflater(mActivity));
        idleMain();
        SearchView search = (SearchView) field(f, "mSearch");
        assertNotNull(search);
        assertTrue(search.getMaxWidth() > 0);
        assertTrue((Boolean) field(f, "mToolbarInsetsSaved"));
    }

    @Test
    public void capSearchBoxWidth_noToolbar_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSearch", new SearchView(mActivity));
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        assertFalse((Boolean) field(f, "mToolbarInsetsSaved"));
    }

    @Test
    public void capSearchBoxWidth_detachedFragment_returns() throws Exception {
        SearchFragment f = new SearchFragment();
        call(f, "capSearchBoxWidth", new Class<?>[]{});
    }

    @Test
    public void capSearchBoxWidth_zeroWidthToolbar_repostsThenGivesUp()
            throws Exception {
        installZeroWidthToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSearch", new SearchView(mActivity));
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        // 宽恒为 0：run() 走重 post 分支后返回，insets 已在宽检查前存好。
        assertTrue((Boolean) field(f, "mToolbarInsetsSaved"));
    }

    @Test
    public void capSearchBoxWidth_searchClearedBeforeRun_returns() throws Exception {
        installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSearch", new SearchView(mActivity));
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        setField(f, "mSearch", null);
        idleMain();
        assertFalse((Boolean) field(f, "mToolbarInsetsSaved"));
    }

    @Test
    public void capSearchBoxWidth_menuBlock_outOfRangeTypeFallsBackToFirst()
            throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        assertTrue(toolbar.getWidth() > 0);
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSelectedSearchType", 99);
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        ActionMenuItemView typeItem = new ActionMenuItemView(mActivity);
        menuView.addView(typeItem);
        menuView.addView(search);
        setField(f, "mSearch", search);
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        // 越界类型回落到第 0 个图标；runnable 完整跑完（padding 已存）。
        assertTrue((Boolean) field(f, "mToolbarPaddingSaved"));
        assertNotNull(typeItem.getLayoutParams());
    }

    @Test
    public void verifySortVisible_notSqueezed_returns() throws Exception {
        Toolbar toolbar = installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        ActionMenuItemView typeItem = new ActionMenuItemView(mActivity);
        menuView.addView(typeItem);
        menuView.addView(search);
        setField(f, "mSearch", search);
        int before = search.getMaxWidth();
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, 100, 0);
        idleMain();
        assertEquals(before, search.getMaxWidth());
    }

    @Test
    public void verifySortVisible_parentNotMenu_returns() throws Exception {
        Toolbar toolbar = installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        android.widget.LinearLayout parent = new android.widget.LinearLayout(mActivity);
        parent.addView(search);
        setField(f, "mSearch", search);
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, 100, 0);
        idleMain();
    }

    @Test
    public void verifySortVisible_detachedFragment_returns() throws Exception {
        Toolbar toolbar = new Toolbar(mActivity);
        SearchFragment f = new SearchFragment();
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, 100, 0);
    }

    @Test
    public void verifySortVisible_searchClearedBeforeRun_returns() throws Exception {
        Toolbar toolbar = installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        menuView.addView(search);
        setField(f, "mSearch", search);
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, 100, 0);
        setField(f, "mSearch", null);
        idleMain();
    }

    @Test
    public void verifySortVisible_squeezedBelowFloor_logsAndKeepsMax()
            throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        int floor = (int) (toolbar.getWidth() * 0.25f);
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        menuView.addView(new android.view.View(mActivity)); // 非菜单项 → 挤出
        menuView.addView(search);
        setField(f, "mSearch", search);
        int max = Math.max(1, floor - 1);
        search.setMaxWidth(max);
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, max, 0);
        idleMain();
        assertEquals(max, search.getMaxWidth());
    }

    @Test
    public void verifySortVisible_squeezed_recursesUntilFloor() throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        float density = toolbar.getResources().getDisplayMetrics().density;
        int step = (int) (24 * density + 0.5f);
        int floor = (int) (toolbar.getWidth() * 0.25f);
        int max = floor + 200;
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        menuView.addView(new android.view.View(mActivity)); // 非菜单项 → 挤出
        menuView.addView(search);
        setField(f, "mSearch", search);
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, max, 0);
        idleMain();
        // 每次缩 step，直到 next < floor 或 attempt 到 4；与生产逻辑同款算期望值。
        int expected = max;
        int attempt = 0;
        while (true) {
            int next = expected - step;
            if (next < floor || attempt >= 4) break;
            expected = next;
            attempt++;
        }
        assertEquals(expected, search.getMaxWidth());
    }

    // ---- 第二批：生命周期 / 商店 / loadPage ----

    @Test
    public void onDestroyView_disposesHotDisposable() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);
        setField(f, "mHotDisposable", disposable);
        f.onDestroyView();
        verify(disposable).dispose();
        assertNull(field(f, "mHotDisposable"));
    }

    @Test
    public void onDestroyView_alreadyDisposedDisposableIsNotDisposedAgain()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(true);
        setField(f, "mHotDisposable", disposable);
        f.onDestroyView();
        verify(disposable, Mockito.never()).dispose();
    }

    @Test
    public void onRefresh_storeVisible_reloadsCurrentTab() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            StoreView storeView = new StoreView(mActivity);
            storeView.setVisibility(android.view.View.VISIBLE);
            setField(f, "mStoreView", storeView);
            f.onRefresh();
            java.lang.reflect.Field feedField =
                    StoreView.class.getDeclaredField("mFeedData");
            feedField.setAccessible(true);
            assertNotNull(feedField.get(storeView));
        }
    }

    @Test
    public void onRefresh_withDefaultHeader_refreshesHeader() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        android.view.View header = inflateDefaultHeader(f);
        f.onRefresh();
        android.widget.LinearLayout recent = header.findViewById(
                com.gh4a.R.id.search_default_recent_container);
        assertEquals(1, recent.getChildCount());
    }

    @Test
    public void canChildScrollUp_storeVisible_delegatesToStore() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.VISIBLE);
        setField(f, "mStoreView", storeView);
        assertFalse(f.canChildScrollUp());
    }

    @Test
    public void hideStore_hidesStoreAndShowsList() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout root = new android.widget.LinearLayout(mActivity);
        android.view.View list = new android.view.View(mActivity);
        list.setId(com.gh4a.R.id.list);
        list.setVisibility(android.view.View.GONE);
        root.addView(list);
        setFragmentView(f, root);
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.VISIBLE);
        setField(f, "mStoreView", storeView);
        call(f, "hideStore", new Class<?>[]{});
        assertEquals(android.view.View.GONE, storeView.getVisibility());
        assertEquals(android.view.View.VISIBLE, list.getVisibility());
    }

    @Test
    public void loadDefaultPage_withHeader_refreshesHeader() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, true));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        android.view.View header = inflateDefaultHeader(f);
        call(f, "loadDefaultPage", new Class<?>[]{});
        android.widget.LinearLayout recent = header.findViewById(
                com.gh4a.R.id.search_default_recent_container);
        assertEquals(1, recent.getChildCount());
    }

    private android.view.View fakeViewWithListAndEmpty() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(mActivity);
        android.view.View list = new android.view.View(mActivity);
        list.setId(com.gh4a.R.id.list);
        root.addView(list);
        android.view.View empty = new android.view.View(mActivity);
        empty.setId(com.gh4a.R.id.empty_view_container);
        root.addView(empty);
        return root;
    }

    @Test
    public void updateEmptyState_withHeader_showsListHidesEmpty() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        android.view.View view = fakeViewWithListAndEmpty();
        setFragmentView(f, view);
        inflateDefaultHeader(f);
        f.updateEmptyState();
        assertEquals(android.view.View.VISIBLE,
                view.findViewById(com.gh4a.R.id.list).getVisibility());
        assertEquals(android.view.View.GONE,
                view.findViewById(com.gh4a.R.id.empty_view_container).getVisibility());
    }

    @Test
    public void updateEmptyState_storeVisible_hidesListAndEmpty() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.view.View view = fakeViewWithListAndEmpty();
        setFragmentView(f, view);
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.VISIBLE);
        setField(f, "mStoreView", storeView);
        f.updateEmptyState();
        assertEquals(android.view.View.GONE,
                view.findViewById(com.gh4a.R.id.list).getVisibility());
        assertEquals(android.view.View.GONE,
                view.findViewById(com.gh4a.R.id.empty_view_container).getVisibility());
    }

    @Test
    public void updateEmptyState_storeVisibleWithoutView_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.VISIBLE);
        setField(f, "mStoreView", storeView);
        f.updateEmptyState();
    }

    @Test
    public void loadPage_unknownType_throws() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, "x", false));
        setField(f, "mSelectedSearchType", 99);
        try {
            call(f, "loadPage", new Class<?>[]{int.class, boolean.class}, 1, false);
            assertTrue("expected IllegalStateException", false);
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }

    @Test
    public void makeDefaultRepoSingle_loggedIn_returnsUserRepos() throws Exception {
        com.gh4a.Gh4Application.get().addAccount(
                User.builder().login("octocat").id(1L).build(), "token-xyz");
        RepositoryService service = mock(RepositoryService.class);
        Page<Repository> page = mock(Page.class);
        when(page.items()).thenReturn(java.util.Collections.emptyList());
        when(service.getUserRepositories(
                ArgumentMatchers.eq("octocat"), ArgumentMatchers.any(),
                ArgumentMatchers.eq(1L)))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockRepositoryService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            Single<Response<Page<Object>>> single =
                    (Single<Response<Page<Object>>>) call(f, "makeDefaultRepoSingle",
                            new Class<?>[]{int.class, boolean.class}, 1, false);
            assertTrue(single.blockingGet().body().items().isEmpty());
            verify(service).getUserRepositories(
                    ArgumentMatchers.eq("octocat"), ArgumentMatchers.any(),
                    ArgumentMatchers.eq(1L));
        }
    }

    @Test
    public void loadPage_repoSubscribes_composeRuns() throws Exception {
        SearchService service = mock(SearchService.class);
        SearchPage<Repository> page = searchPageWith(
                java.util.Collections.singletonList(hotRepo("o/r", 42)));
        when(service.searchRepositories(
                ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, "kotlin", false));
            Single<Response<Page<Object>>> single =
                    (Single<Response<Page<Object>>>) call(f, "loadPage",
                            new Class<?>[]{int.class, boolean.class}, 1, false);
            // 1 条数据：searchPageAdapter 的 item -> item 映射才会跑到。
            assertEquals(1, single.blockingGet().body().items().size());
        }
    }

    @Test
    public void loadPage_userSubscribes_composeRuns() throws Exception {
        SearchService service = mock(SearchService.class);
        SearchPage<User> page = searchPageWith(
                java.util.Collections.singletonList(
                        User.builder().login("octo").id(1L).build()));
        when(service.searchUsers(
                ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_USER, "octo", false));
            Single<Response<Page<Object>>> single =
                    (Single<Response<Page<Object>>>) call(f, "loadPage",
                            new Class<?>[]{int.class, boolean.class}, 1, false);
            // 1 条数据：searchPageAdapter 的 item -> item 映射才会跑到。
            assertEquals(1, single.blockingGet().body().items().size());
        }
    }

    @Test
    public void loadPage_codeSubscribes_composeRuns() throws Exception {
        SearchService service = mock(SearchService.class);
        SearchPage<SearchCode> page = searchPageWith(
                java.util.Collections.singletonList(mock(SearchCode.class)));
        when(service.searchCode(
                ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_CODE, "println", false));
            Single<Response<Page<Object>>> single =
                    (Single<Response<Page<Object>>>) call(f, "loadPage",
                            new Class<?>[]{int.class, boolean.class}, 1, false);
            // 1 条数据：searchPageAdapter 的 item -> item 映射才会跑到。
            assertEquals(1, single.blockingGet().body().items().size());
        }
    }

    // ---- 第二批：onClose / 建议点击 / 类型切换 / 默认头 / 热门 ----

    @Test
    public void onClose_withAdapterAndVisibleStore_resetsStoreAndReattachesHeader()
            throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            com.gh4a.adapter.SearchAdapter adapter =
                    mock(com.gh4a.adapter.SearchAdapter.class);
            setField(f, "mAdapter", adapter);
            StoreView storeView = new StoreView(mActivity);
            storeView.setVisibility(android.view.View.VISIBLE);
            setField(f, "mStoreView", storeView);
            setFragmentView(f, fakeViewWithEmpty());
            assertTrue(f.onClose());
            verify(adapter).clear();
            assertNull(field(f, "mQuery"));
            assertEquals(android.view.View.VISIBLE, storeView.getVisibility());
        }
    }

    @Test
    public void onSuggestionClick_clearRow_deletesHistory() throws Exception {
        insertSuggestion("kotlin", 0);
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView mockSearch = mock(SearchView.class);
        CursorAdapter adapter = mock(CursorAdapter.class);
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "suggestion"});
        cursor.addRow(new Object[]{Long.MAX_VALUE, "clear"});
        cursor.moveToFirst();
        when(adapter.getCursor()).thenReturn(cursor);
        when(mockSearch.getSuggestionsAdapter()).thenReturn(adapter);
        setField(f, "mSearch", mockSearch);
        assertTrue(f.onSuggestionClick(0));
        // 后台删除是异步的：轮询等它落盘，最多 2 秒。
        long deadline = System.currentTimeMillis() + 2000;
        int count = -1;
        while (System.currentTimeMillis() < deadline) {
            Cursor c = mActivity.getContentResolver().query(
                    SuggestionsProvider.Columns.CONTENT_URI,
                    new String[]{SuggestionsProvider.Columns._ID},
                    SuggestionsProvider.Columns.TYPE + " = ?",
                    new String[]{"0"}, null);
            count = c == null ? 0 : c.getCount();
            if (c != null) c.close();
            if (count == 0) break;
            Thread.sleep(50);
        }
        assertEquals(0, count);
        clearSuggestions();
    }

    @Test
    public void onSearchFragmentClick_withMatchIndex_usesTextMatch() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        User owner = mock(User.class);
        when(owner.login()).thenReturn("octo");
        Repository repo = mock(Repository.class);
        when(repo.owner()).thenReturn(owner);
        when(repo.name()).thenReturn("cat");
        SearchCode result = mock(SearchCode.class);
        when(result.url()).thenReturn(
                "https://github.com/octo/cat/blob/main/src/Main.java?ref=main");
        when(result.repository()).thenReturn(repo);
        when(result.path()).thenReturn("src/Main.java");
        TextMatch match = mock(TextMatch.class);
        when(result.textMatches()).thenReturn(
                java.util.Collections.singletonList(match));
        f.onSearchFragmentClick(result, 0);
        android.app.Application app = ApplicationProvider.getApplicationContext();
        android.content.Intent intent =
                Shadows.shadowOf(app).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("FileViewerActivity"));
    }

    @Test
    public void applySearchType_emptyQueryStoreTabRepo_showsStore() throws Exception {
        try (MockedStatic<StoreApi> mocked = mockStoreApi()) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_USER, null, false));
            com.gh4a.adapter.SearchAdapter adapter =
                    mock(com.gh4a.adapter.SearchAdapter.class);
            setField(f, "mAdapter", adapter);
            setMockSearchView(f);
            setFragmentView(f, fakeViewWithEmpty());
            call(f, "applySearchType", new Class<?>[]{int.class},
                    SearchFragment.SEARCH_TYPE_REPO);
            assertEquals(SearchFragment.SEARCH_TYPE_REPO,
                    field(f, "mSelectedSearchType"));
            StoreView storeView = (StoreView) field(f, "mStoreView");
            assertNotNull(storeView);
            assertEquals(android.view.View.VISIBLE, storeView.getVisibility());
        }
    }

    @Test
    public void removeDefaultHeader_withHeaderAndAdapter_clearsHeader()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        inflateDefaultHeader(f);
        call(f, "removeDefaultHeader", new Class<?>[]{});
        verify(adapter).setHeaderView(null);
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void removeDefaultHeader_adapterNull_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        inflateDefaultHeader(f);
        call(f, "removeDefaultHeader", new Class<?>[]{});
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void refreshDefaultHeader_nullHeader_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        call(f, "refreshDefaultHeader", new Class<?>[]{});
    }

    @Test
    public void loadRecentSearches_detachedFragment_returnsEmpty() throws Exception {
        SearchFragment f = new SearchFragment();
        java.util.List<String> recents =
                (java.util.List<String>) call(f, "loadRecentSearches",
                        new Class<?>[]{});
        assertTrue(recents.isEmpty());
    }

    @Test
    public void loadRecentSearches_withRows_returnsDedupedOrdered() throws Exception {
        insertSuggestion("kotlin", 0);
        Thread.sleep(5);
        insertSuggestion("java", 0);
        Thread.sleep(5);
        insertSuggestion("kotlin", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            java.util.List<String> recents =
                    (java.util.List<String>) call(f, "loadRecentSearches",
                            new Class<?>[]{});
            assertEquals(java.util.Arrays.asList("kotlin", "java"), recents);
        } finally {
            clearSuggestions();
        }
    }

    @Test
    public void refreshDefaultHeader_withRecents_showsChips() throws Exception {
        insertSuggestion("kotlin", 0);
        Thread.sleep(5);
        insertSuggestion("java", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            setField(f, "mHotRepos", java.util.Collections.emptyList());
            setMockSearchView(f);
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout recent = header.findViewById(
                    com.gh4a.R.id.search_default_recent_container);
            assertEquals(2, recent.getChildCount());
            // 最近的排第一：java 后插入，kotlin 在后。
            assertEquals("java",
                    ((android.widget.TextView) recent.getChildAt(0))
                            .getText().toString());
            assertEquals("kotlin",
                    ((android.widget.TextView) recent.getChildAt(1))
                            .getText().toString());
            android.widget.LinearLayout hot = header.findViewById(
                    com.gh4a.R.id.search_default_hot_container);
            assertEquals(0, hot.getChildCount());
            // 点 chip：走 onClick lambda，mSearch 收到查询。
            recent.getChildAt(0).performClick();
            verify((SearchView) field(f, "mSearch"))
                    .setQuery("java", true);
        } finally {
            clearSuggestions();
        }
    }

    @Test
    public void refreshDefaultHeader_recentChipLongClick_detached_returnsTrue()
            throws Exception {
        insertSuggestion("kotlin", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            setField(f, "mHotRepos", java.util.Collections.emptyList());
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout recent = header.findViewById(
                    com.gh4a.R.id.search_default_recent_container);
            android.view.View chip = recent.getChildAt(0);
            // 卸掉 fragment 再长按：getActivity()==null 守卫返回 true，不弹框。
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            assertTrue(chip.performLongClick());
            assertTrue(org.robolectric.shadows.ShadowDialog
                    .getShownDialogs().isEmpty());
        } finally {
            clearSuggestions();
        }
    }

    @Test
    public void refreshDefaultHeader_recentChipLongClick_showsDeleteDialog()
            throws Exception {
        insertSuggestion("kotlin", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            setField(f, "mHotRepos", java.util.Collections.emptyList());
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout recent = header.findViewById(
                    com.gh4a.R.id.search_default_recent_container);
            android.view.View chip = recent.getChildAt(0);
            assertTrue(chip.performLongClick());
            assertFalse(org.robolectric.shadows.ShadowDialog
                    .getShownDialogs().isEmpty());
            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog
                    .getShownDialogs().get(0);
            ((androidx.appcompat.app.AlertDialog) dialog)
                    .getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            idleMain(); // AlertDialog 按钮经 Handler 派发，先放行再轮询删除结果。
            // 后台删除异步落盘：轮询等它删掉，最多 2 秒。
            long deadline = System.currentTimeMillis() + 2000;
            int count = -1;
            while (System.currentTimeMillis() < deadline) {
                Cursor c = mActivity.getContentResolver().query(
                        SuggestionsProvider.Columns.CONTENT_URI,
                        new String[]{SuggestionsProvider.Columns._ID},
                        SuggestionsProvider.Columns.TYPE + " = ?",
                        new String[]{"0"}, null);
                count = c == null ? 0 : c.getCount();
                if (c != null) c.close();
                if (count == 0) break;
                Thread.sleep(50);
            }
            assertEquals(0, count);
            idleMain();
        } finally {
            clearSuggestions();
        }
    }

    private SearchService mockHotService(
            io.reactivex.Single<Response<SearchPage<Repository>>> single) {
        SearchService service = mock(SearchService.class);
        when(service.searchRepositories(
                ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(single);
        return service;
    }

    @Test
    public void refreshDefaultHeader_repoNoCache_showsSkeletonThenLoadsHot()
            throws Exception {
        clearHotCache();
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Arrays.asList(hotRepo("o/r", 42))))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout hot = header.findViewById(
                    com.gh4a.R.id.search_default_hot_container);
            assertEquals(5, hot.getChildCount()); // 骨架屏 5 条灰条
            // doInBackground 在 IO 线程投递：轮询等它落到主线程，最多 5 秒。
            long deadline = System.currentTimeMillis() + 5000;
            while (hot.getChildCount() != 1
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            assertEquals(1, hot.getChildCount());
            assertEquals("o/r", ((android.widget.TextView)
                    ((android.widget.LinearLayout) hot.getChildAt(0))
                            .getChildAt(0)).getText().toString());
            assertEquals(1, ((java.util.List<?>) field(f, "mHotRepos")).size());
        }
    }

    @Test
    public void refreshDefaultHeader_repoWithCache_fillsFromCache() throws Exception {
        HotReposCache.save(mActivity, java.util.Arrays.asList(
                new HotReposCache.HotRepo("a/b", 10)));
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Collections.emptyList()))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout hot = header.findViewById(
                    com.gh4a.R.id.search_default_hot_container);
            assertEquals(1, hot.getChildCount());
            assertEquals("a/b", ((android.widget.TextView)
                    ((android.widget.LinearLayout) hot.getChildAt(0))
                            .getChildAt(0)).getText().toString());
            assertNotNull(field(f, "mHotDisposable"));
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void refreshDefaultHeader_repoForceRefresh_showsSkeleton() throws Exception {
        clearHotCache();
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Collections.emptyList()))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            setField(f, "mHotForceRefresh", true);
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout hot = header.findViewById(
                    com.gh4a.R.id.search_default_hot_container);
            assertEquals(5, hot.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_error_clearsContainerWhenNoCache() throws Exception {
        clearHotCache();
        SearchService service = mockHotService(
                Single.error(new RuntimeException("no net")));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout hot = header.findViewById(
                    com.gh4a.R.id.search_default_hot_container);
            assertEquals(5, hot.getChildCount()); // 先出骨架
            // doInBackground 在 IO 线程投递：轮询等错误消费者清空容器，最多 5 秒。
            long deadline = System.currentTimeMillis() + 5000;
            while (hot.getChildCount() != 0
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            assertEquals(0, hot.getChildCount()); // 失败且无缓存 → 清空
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_secondCall_disposesFirst() throws Exception {
        // Single.never()：第一个订阅不结束，第二个 loadHotRepos 才会走到 dispose 分支。
        SearchService service = mockHotService(
                Single.<Response<SearchPage<Repository>>>never());
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            // 消费者守卫要求 mDefaultHeader 非 null，先 inflate（不走 refresh）。
            inflateDefaultHeader(f);
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            Disposable first = (Disposable) field(f, "mHotDisposable");
            assertNotNull(first);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            assertTrue(first.isDisposed());
        }
    }

    @Test
    public void fillHotContainerFromCache_rowClick_opensRepository() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainerFromCache",
                new Class<?>[]{android.widget.LinearLayout.class, java.util.List.class},
                container, java.util.Arrays.asList(
                        new HotReposCache.HotRepo("o/r", 42)));
        assertEquals(1, container.getChildCount());
        container.getChildAt(0).performClick();
        android.content.Intent intent =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("RepositoryActivity"));
    }

    @Test
    public void fillHotContainer_rowClick_opensRepository() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mHotRepos", java.util.Arrays.asList(hotRepo("o/r", 42)));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(1, container.getChildCount());
        container.getChildAt(0).performClick();
        android.content.Intent intent =
                Shadows.shadowOf(mActivity).getNextStartedActivity();
        assertNotNull(intent);
        assertTrue(intent.getComponent().getClassName().contains("RepositoryActivity"));
    }

    @Test
    public void fillHotContainer_nullStars_showsZero() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        Repository repo = Repository.builder()
                .fullName("o/r")
                .owner(User.builder().login("o").build())
                .name("r")
                .build();
        setField(f, "mHotRepos", java.util.Collections.singletonList(repo));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(1, container.getChildCount());
        android.widget.LinearLayout row =
                (android.widget.LinearLayout) container.getChildAt(0);
        assertEquals("★ 0",
                ((android.widget.TextView) row.getChildAt(1)).getText().toString());
    }

    @Test
    public void showHotSkeleton_nullContainer_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        call(f, "showHotSkeleton",
                new Class<?>[]{android.widget.LinearLayout.class}, (Object) null);
    }

    @Test
    public void fillHotContainerFromCache_nullArgs_return() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container = new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainerFromCache",
                new Class<?>[]{android.widget.LinearLayout.class, java.util.List.class},
                null, java.util.Arrays.asList(new HotReposCache.HotRepo("a/b", 1)));
        call(f, "fillHotContainerFromCache",
                new Class<?>[]{android.widget.LinearLayout.class, java.util.List.class},
                container, null);
        assertEquals(0, container.getChildCount());
    }

    // ---- 第二批：SuggestionAdapter / 类型与排序动作表 ----

    private CursorAdapter adapterWithMergedCursor(SearchFragment f) throws Exception {
        CursorAdapter adapter = newSuggestionAdapter(f);
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "suggestion"});
        cursor.addRow(new Object[]{1L, "kotlin"});
        adapter.swapCursor(cursor);
        return adapter;
    }

    @Test
    public void suggestionAdapter_swapCursor_appendsClearRow() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = newSuggestionAdapter(f);
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "suggestion"});
        cursor.addRow(new Object[]{1L, "kotlin"});
        Cursor old = adapter.swapCursor(cursor);
        assertNull(old);
        assertEquals(2, adapter.getCount());
    }

    @Test
    public void suggestionAdapter_swapCursor_emptyCursorNotAppended() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = newSuggestionAdapter(f);
        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "suggestion"});
        adapter.swapCursor(cursor);
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void suggestionAdapter_getItemViewType_suggestionAndClear() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = adapterWithMergedCursor(f);
        assertEquals(0, adapter.getItemViewType(0));
        assertEquals(1, adapter.getItemViewType(1));
    }

    @Test
    public void suggestionAdapter_getItemViewType_noCursor_returnsZero()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = newSuggestionAdapter(f);
        assertEquals(0, adapter.getItemViewType(0));
    }

    @Test
    public void suggestionAdapter_getViewTypeCount_isTwo() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = newSuggestionAdapter(f);
        assertEquals(2, adapter.getViewTypeCount());
    }

    @Test
    public void suggestionAdapter_newView_inflatesBothLayouts() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = adapterWithMergedCursor(f);
        android.widget.LinearLayout parent = new android.widget.LinearLayout(mActivity);
        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(0);
        android.view.View suggestionRow = adapter.newView(mActivity, cursor, parent);
        assertNotNull(suggestionRow.findViewById(com.gh4a.R.id.suggestion_text));
        cursor.moveToPosition(1);
        android.view.View clearRow = adapter.newView(mActivity, cursor, parent);
        assertTrue(clearRow instanceof android.widget.TextView);
    }

    @Test
    public void suggestionAdapter_bindView_suggestionRow_setsTextAndSelectButton()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView mockSearch = mock(SearchView.class);
        setField(f, "mSearch", mockSearch);
        CursorAdapter adapter = adapterWithMergedCursor(f);
        android.widget.LinearLayout parent = new android.widget.LinearLayout(mActivity);
        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(0);
        android.view.View row = adapter.newView(mActivity, cursor, parent);
        adapter.bindView(row, mActivity, cursor);
        assertEquals("kotlin",
                ((android.widget.TextView) row.findViewById(
                        com.gh4a.R.id.suggestion_text)).getText().toString());
        row.findViewById(com.gh4a.R.id.select_suggestion_button).performClick();
        assertEquals("kotlin", field(f, "mQuery"));
        verify(mockSearch).setQuery("kotlin", false);
    }

    @Test
    public void suggestionAdapter_bindView_clearRow_setsText() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = adapterWithMergedCursor(f);
        android.widget.LinearLayout parent = new android.widget.LinearLayout(mActivity);
        Cursor cursor = adapter.getCursor();
        cursor.moveToPosition(1);
        android.view.View row = adapter.newView(mActivity, cursor, parent);
        adapter.bindView(row, mActivity, cursor);
        assertEquals(mActivity.getString(com.gh4a.R.string.clear_suggestions),
                ((android.widget.TextView) row).getText().toString());
    }

    @Test
    public void updateTypeMenuItem_nullItem_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.view.Menu menu = mock(android.view.Menu.class);
        when(menu.findItem(com.gh4a.R.id.type)).thenReturn(null);
        call(f, "updateTypeMenuItem",
                new Class<?>[]{android.view.Menu.class}, menu);
    }

    @Test
    public void updateTypeMenuItem_setsTitleAndIcon() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.view.Menu menu = mock(android.view.Menu.class);
        android.view.MenuItem item = mock(android.view.MenuItem.class);
        when(menu.findItem(com.gh4a.R.id.type)).thenReturn(item);
        call(f, "updateTypeMenuItem",
                new Class<?>[]{android.view.Menu.class}, menu);
        verify(item).setTitle(com.gh4a.R.string.search_type_repo);
        verify(item).setIcon(com.gh4a.R.drawable.menu_search_repos);
    }

    @Test
    public void showTypeSheet_detached_returns() throws Exception {
        SearchFragment f = new SearchFragment();
        call(f, "showTypeSheet", new Class<?>[]{});
    }

    @Test
    public void showSortSheet_detached_returns() throws Exception {
        SearchFragment f = new SearchFragment();
        call(f, "showSortSheet", new Class<?>[]{});
    }

    @Test
    public void attachDefaultHeader_noAdapter_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        call(f, "attachDefaultHeader", new Class<?>[]{});
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void showTypeSheet_rowClick_appliesType() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        setMockSearchView(f);
        setFragmentView(f, fakeViewWithEmpty());
        call(f, "showTypeSheet", new Class<?>[]{});
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog
                .getShownDialogs().get(0);
        android.widget.LinearLayout content = sheetContent(dialog,
                mActivity.getString(com.gh4a.R.string.search_type));
        content.getChildAt(2).performClick(); // 用户行
        assertEquals(SearchFragment.SEARCH_TYPE_USER, field(f, "mSelectedSearchType"));
        verify(adapter).setMode(SearchFragment.SEARCH_TYPE_USER);
    }

    @Test
    public void loadHotRepos_nullStars_savesZero() throws Exception {
        clearHotCache();
        Repository repo = Repository.builder()
                .fullName("o/r")
                .owner(User.builder().login("o").build())
                .name("r")
                .build(); // 不设 stargazersCount → null，走 sc != null 的 false 半
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Collections.singletonList(repo)))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            inflateDefaultHeader(f);
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            long deadline = System.currentTimeMillis() + 5000;
            while (field(f, "mHotRepos") == null
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            assertEquals(1, ((java.util.List<?>) field(f, "mHotRepos")).size());
            assertEquals(1, container.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_emptyPage_skipsCacheSave() throws Exception {
        clearHotCache();
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Collections.<Repository>emptyList()))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            // 消费者守卫要求 mDefaultHeader 非 null，先 inflate（不走 refresh）。
            inflateDefaultHeader(f);
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            // 等成功消费者跑完：mHotRepos 被置为空列表（非 null）。
            long deadline = System.currentTimeMillis() + 5000;
            while (field(f, "mHotRepos") == null
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            assertNotNull(field(f, "mHotRepos"));
            assertTrue(((java.util.List<?>) field(f, "mHotRepos")).isEmpty());
            assertEquals(0, container.getChildCount());
            // 第二次 loadHotRepos：上一个订阅已结束，dispose 守卫走已释放分支。
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_detachedFragment_successConsumerReturns()
            throws Exception {
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Collections.singletonList(
                        hotRepo("o/r", 42))))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            // 等消费者跑完：未 attach，守卫直接返回，mHotRepos 保持 null。
            long deadline = System.currentTimeMillis() + 5000;
            while (!((Disposable) field(f, "mHotDisposable")).isDisposed()
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            assertTrue(((Disposable) field(f, "mHotDisposable")).isDisposed());
            assertNull(field(f, "mHotRepos"));
        }
    }

    @Test
    public void loadHotRepos_noHeader_successConsumerReturns() throws Exception {
        SearchService service = mockHotService(Single.just(Response.success(
                searchPageWith(java.util.Collections.singletonList(
                        hotRepo("o/r", 42))))));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            // 等消费者跑完：mDefaultHeader 为 null，守卫直接返回。
            long deadline = System.currentTimeMillis() + 5000;
            while (!((Disposable) field(f, "mHotDisposable")).isDisposed()
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            assertTrue(((Disposable) field(f, "mHotDisposable")).isDisposed());
            assertNull(field(f, "mHotRepos"));
        }
    }

    @Test
    public void showSortSheet_rowClick_setsSort() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        setField(f, "mSort", "created");
        setField(f, "mOrder", "desc");
        call(f, "showSortSheet", new Class<?>[]{});
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog
                .getShownDialogs().get(0);
        android.widget.LinearLayout content = sheetContent(dialog,
                mActivity.getString(com.gh4a.R.string.search_sort_title));
        content.getChildAt(1).performClick(); // 首个排序选项
        com.gh4a.fragment.SearchSortOption first =
                com.gh4a.fragment.SearchSortOption.forSearchType(
                        SearchFragment.SEARCH_TYPE_REPO).get(0);
        assertEquals(first.sort, field(f, "mSort"));
        assertEquals(first.order, field(f, "mOrder"));
    }

    // ---- 第三批：分支收尾 ----

    private int suggestionCount() {
        Cursor c = mActivity.getContentResolver().query(
                SuggestionsProvider.Columns.CONTENT_URI,
                new String[]{SuggestionsProvider.Columns._ID},
                SuggestionsProvider.Columns.TYPE + " = ?",
                new String[]{"0"}, null);
        int count = c == null ? 0 : c.getCount();
        if (c != null) c.close();
        return count;
    }

    private void waitForHotDisposable(SearchFragment f) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (!((Disposable) field(f, "mHotDisposable")).isDisposed()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            idleMain();
        }
        assertTrue(((Disposable) field(f, "mHotDisposable")).isDisposed());
    }

    private ActionMenuView actionMenuViewWithTypeItem() {
        ActionMenuView menuView = new ActionMenuView(mActivity);
        ActionMenuItemView typeItem = new ActionMenuItemView(mActivity);
        menuView.addView(typeItem);
        return menuView;
    }

    @Test
    public void syncPlatformMenuItem_emptyMenuAndHiddenStore_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.GONE);
        setField(f, "mStoreView", storeView);
        // 空菜单：sort/type 项都找不到，两个 null 守卫都走跳过分支。
        call(f, "syncPlatformMenuItem",
                new Class<?>[]{android.view.Menu.class},
                new MenuBuilder(mActivity));
        // 带项菜单 + 商店隐藏：两项恢复可见。
        MenuBuilder menu = new MenuBuilder(mActivity);
        menu.add(0, com.gh4a.R.id.sort, 0, "sort");
        menu.add(0, com.gh4a.R.id.type, 0, "type");
        call(f, "syncPlatformMenuItem",
                new Class<?>[]{android.view.Menu.class}, menu);
        assertTrue(menu.findItem(com.gh4a.R.id.sort).isVisible());
        assertTrue(menu.findItem(com.gh4a.R.id.type).isVisible());
    }

    @Test
    public void capSearchBoxWidth_goneStore_runsElseBranch() throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.GONE);
        setField(f, "mStoreView", storeView);
        SearchView search = new SearchView(mActivity);
        setField(f, "mSearch", search);
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        // 商店非空但隐藏：走 else 分支，搜索框被定宽。
        assertTrue(search.getMaxWidth() > 0);
    }

    @Test
    public void capSearchBoxWidth_narrowToolbar_maxZeroSkipsSet() throws Exception {
        Toolbar toolbar = new Toolbar(mActivity);
        toolbar.setId(com.gh4a.R.id.toolbar);
        mActivity.setContentView(toolbar,
                new android.view.ViewGroup.LayoutParams(
                        2, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        idleMain();
        assertEquals(2, toolbar.getWidth());
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        int before = search.getMaxWidth();
        setField(f, "mSearch", search);
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        // 宽 2：floor=0、max=0，`if (max > 0)` 走 false 半，不定宽。
        assertEquals(before, search.getMaxWidth());
    }

    @Test
    public void capSearchBoxWidth_mockSearchView_nullInternalViewsSkipped()
            throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        ActionMenuView menuView = actionMenuViewWithTypeItem();
        SearchView mockSearch = mock(SearchView.class);
        when(mockSearch.getParent()).thenReturn(menuView);
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSearch", mockSearch);
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        // mock 的 findViewById 全返回 null：plate/button/editFrame/magIcon
        // 四个 null 守卫都走跳过分支。
        assertTrue((Boolean) field(f, "mToolbarPaddingSaved"));
    }

    @Test
    public void capSearchBoxWidth_plainLayoutParams_skipsInstanceofBranches()
            throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = actionMenuViewWithTypeItem();
        menuView.addView(search);
        // 三个内部视图换成普通 LayoutParams：instanceof 全走 false 半。
        // 菜单视图未挂进窗口，不跑布局，不会因此 ClassCastException。
        search.findViewById(androidx.appcompat.R.id.search_button)
                .setLayoutParams(new android.view.ViewGroup.LayoutParams(10, 10));
        search.findViewById(androidx.appcompat.R.id.search_edit_frame)
                .setLayoutParams(new android.view.ViewGroup.LayoutParams(10, 10));
        search.findViewById(androidx.appcompat.R.id.search_mag_icon)
                .setLayoutParams(new android.view.ViewGroup.LayoutParams(10, 10));
        setField(f, "mSearch", search);
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        assertTrue((Boolean) field(f, "mToolbarPaddingSaved"));
    }

    @Test
    public void capSearchBoxWidth_menuBlock_negativeTypeFallsBackToFirst()
            throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        assertTrue(toolbar.getWidth() > 0);
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSelectedSearchType", -1);
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = actionMenuViewWithTypeItem();
        menuView.addView(search);
        setField(f, "mSearch", search);
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        idleMain();
        // 负类型同样回落到第 0 个图标。
        assertTrue((Boolean) field(f, "mToolbarPaddingSaved"));
    }

    @Test
    public void capSearchBoxWidth_attachedNoSearch_returns() throws Exception {
        installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        // mSearch 为 null：守卫直接返回。
        assertNull(field(f, "mSearch"));
    }

    @Test
    public void capSearchBoxWidth_detachedBeforeRun_returns() throws Exception {
        installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mSearch", new SearchView(mActivity));
        call(f, "capSearchBoxWidth", new Class<?>[]{});
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        idleMain();
        // runnable 运行时已 detach：isAdded 守卫返回。
        assertFalse(f.isAdded());
    }

    @Test
    public void verifySortVisible_attachedNoSearch_returns() throws Exception {
        Toolbar toolbar = installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, 100, 0);
        // mSearch 为 null：入口守卫直接返回。
    }

    @Test
    public void verifySortVisible_detachedBeforeRun_returns() throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        menuView.addView(search);
        setField(f, "mSearch", search);
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, 100, 0);
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        idleMain();
        // runnable 运行时已 detach：isAdded 守卫返回。
        assertFalse(f.isAdded());
    }

    @Test
    public void verifySortVisible_goneMenuItem_countsAsSqueezed() throws Exception {
        Toolbar toolbar = installToolbar();
        idleMain();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView search = new SearchView(mActivity);
        ActionMenuView menuView = new ActionMenuView(mActivity);
        ActionMenuItemView typeItem = new ActionMenuItemView(mActivity);
        typeItem.setVisibility(android.view.View.GONE);
        menuView.addView(typeItem);
        menuView.addView(search);
        setField(f, "mSearch", search);
        int width = toolbar.getWidth();
        int floor = (int) (width * 0.25f);
        int initialMax = floor + 200;
        search.setMaxWidth(initialMax);
        call(f, "verifySortVisible",
                new Class<?>[]{Toolbar.class, int.class, int.class},
                toolbar, initialMax, 0);
        idleMain();
        // GONE 的菜单项同样算挤占：max 被逐轮收窄。
        assertTrue(search.getMaxWidth() < initialMax);
    }

    @Test
    public void onResume_withAdapterAndStoreTabArgs_loadsDefaultPage()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        f.onResume();
        // 空查询 + 无头 + 有 adapter + do_initial_load=false：门全开，走默认页。
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onResume_noArgs_returns() throws Exception {
        SearchFragment f = attach(new SearchFragment());
        setField(f, "mAdapter", mock(com.gh4a.adapter.SearchAdapter.class));
        f.onResume();
        // 无 arguments：getArguments()==null，门关住，不加载默认页。
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onResume_querySet_skipsLoadDefaultPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        setField(f, "mAdapter", mock(com.gh4a.adapter.SearchAdapter.class));
        setField(f, "mQuery", "octo");
        f.onResume();
        // 有查询：486 的 isEmpty(mQuery) 为 false，门关住。
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onResume_independentPage_skipsLoadDefaultPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, true));
        setField(f, "mAdapter", mock(com.gh4a.adapter.SearchAdapter.class));
        f.onResume();
        // 独立搜索页：do_initial_load=true，门关住。
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onResume_headerPresent_skipsLoadDefaultPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        setField(f, "mAdapter", mock(com.gh4a.adapter.SearchAdapter.class));
        android.view.View header = inflateDefaultHeader(f);
        f.onResume();
        // 已有默认头：486 的 mDefaultHeader==null 为 false，不重载。
        assertTrue(header == field(f, "mDefaultHeader"));
    }

    @Test
    public void onResume_adapterNull_skipsLoadDefaultPage() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        setField(f, "mAdapter", null);
        f.onResume();
        // 无 adapter：486 的 mAdapter!=null 为 false，门关住。
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onDestroyView_detachedFragment_skipsToolbarRestore()
            throws Exception {
        SearchFragment f = new SearchFragment();
        f.onDestroyView();
        // getActivity()==null：工具栏恢复整段跳过。
    }

    @Test
    public void onDestroyView_paddingSavedNoSearch_skipsMenuRestore()
            throws Exception {
        installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mToolbarPaddingSaved", true);
        f.onDestroyView();
        // mSearch 为 null：菜单 padding 恢复跳过，标记照常清掉。
        assertFalse((Boolean) field(f, "mToolbarPaddingSaved"));
    }

    @Test
    public void onDestroyView_paddingSavedPlainParent_skipsMenuPadding()
            throws Exception {
        installToolbar();
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mToolbarPaddingSaved", true);
        SearchView search = new SearchView(mActivity);
        android.widget.LinearLayout parent =
                new android.widget.LinearLayout(mActivity);
        parent.addView(search);
        setField(f, "mSearch", search);
        f.onDestroyView();
        // 父视图是 LinearLayout 不是 ActionMenuView：菜单 padding 恢复跳过。
        assertFalse((Boolean) field(f, "mToolbarPaddingSaved"));
    }

    @Test
    public void onDestroyView_parentlessStore_skipsRemove() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mStoreView", new StoreView(mActivity));
        f.onDestroyView();
        // StoreView 没有父视图：removeView 跳过，字段照常清掉。
        assertNull(field(f, "mStoreView"));
    }

    @Test
    public void onRefresh_goneStore_fallsThrough() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.GONE);
        setField(f, "mStoreView", storeView);
        f.onRefresh();
        // 商店非空但隐藏：不走商店重载，落到基类 onRefresh。
        assertEquals(android.view.View.GONE, storeView.getVisibility());
    }

    @Test
    public void canChildScrollUp_goneStore_fallsThrough() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.GONE);
        setField(f, "mStoreView", storeView);
        assertFalse(f.canChildScrollUp());
    }

    @Test
    public void updateEmptyState_goneStore_skipsStoreBranch() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        StoreView storeView = new StoreView(mActivity);
        storeView.setVisibility(android.view.View.GONE);
        setField(f, "mStoreView", storeView);
        setFragmentView(f, fakeViewWithEmpty());
        call(f, "updateEmptyState", new Class<?>[]{});
        // 商店非空但隐藏：不走商店空态分支。
        assertEquals(android.view.View.GONE, storeView.getVisibility());
    }

    @Test
    public void isStoreTab_noArgs_returnsFalse() throws Exception {
        SearchFragment f = new SearchFragment();
        assertFalse((Boolean) call(f, "isStoreTab", new Class<?>[]{}));
    }

    @Test
    public void showStore_detachedWithView_returns() throws Exception {
        SearchFragment f = new SearchFragment();
        setFragmentView(f, fakeViewWithEmpty());
        call(f, "showStore", new Class<?>[]{});
        // 有 view 但无 activity：守卫直接返回，不建 StoreView。
        assertNull(field(f, "mStoreView"));
    }

    @Test
    public void hideStore_detachedFragment_isNoop() throws Exception {
        SearchFragment f = new SearchFragment();
        call(f, "hideStore", new Class<?>[]{});
        // 无 StoreView、无 activity、无 view：三段全跳过。
    }

    @Test
    public void onQueryTextChange_storeTabRepo_keepsHeader() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        inflateDefaultHeader(f);
        assertTrue(f.onQueryTextChange("kotlin"));
        // 商店页签 + 仓库类型：输入时不拆默认头。
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onQueryTextChange_nonStoreType_removesHeader() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        inflateDefaultHeader(f);
        assertTrue(f.onQueryTextChange("octo"));
        // 商店页签但非仓库类型：输入时拆默认头。
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onQueryTextChange_emptyWithHeader_keepsHeader() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        inflateDefaultHeader(f);
        assertTrue(f.onQueryTextChange(""));
        // 清空且已有默认头：不重载默认页。
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onQueryTextChange_emptyWithoutHeader_loadsDefaultPage()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        assertTrue(f.onQueryTextChange(""));
        // 清空且无默认头：重载默认页，头挂上。
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void onClose_goneStore_skipsResetToHome() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        StoreView storeView = mock(StoreView.class);
        when(storeView.getVisibility()).thenReturn(android.view.View.GONE);
        setField(f, "mStoreView", storeView);
        setFragmentView(f, fakeViewWithEmpty());
        assertTrue(f.onClose());
        verify(adapter).clear();
        // 商店隐藏：不调 resetToHome；随后 attachDefaultHeader → showStore。
        verify(storeView, never()).resetToHome();
        verify(storeView).setVisibility(android.view.View.VISIBLE);
    }

    @Test
    public void applySearchType_independentPageRepo_hidesStore() throws Exception {
        // 从 USER 切到 REPO（同类型会提前返回，必须换类型才走下去）。
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, true));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        setField(f, "mHotRepos", java.util.Collections.emptyList());
        setMockSearchView(f);
        setFragmentView(f, fakeViewWithEmpty());
        call(f, "applySearchType", new Class<?>[]{int.class},
                SearchFragment.SEARCH_TYPE_REPO);
        // 独立搜索页不是商店页签：不挂商店，走默认头。
        assertNull(field(f, "mStoreView"));
        assertNotNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void attachDefaultHeader_independentPageRepo_attachesHeader()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, true));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        setField(f, "mHotRepos", java.util.Collections.emptyList());
        call(f, "attachDefaultHeader", new Class<?>[]{});
        // 独立搜索页不是商店页签：不走 showStore，直接挂头。
        assertNotNull(field(f, "mDefaultHeader"));
        assertNull(field(f, "mStoreView"));
    }

    @Test
    public void attachDefaultHeader_detachedWithAdapter_returns() throws Exception {
        SearchFragment f = new SearchFragment();
        setField(f, "mAdapter", mock(com.gh4a.adapter.SearchAdapter.class));
        call(f, "attachDefaultHeader", new Class<?>[]{});
        // 有 adapter 但无 activity：守卫返回，不挂头。
        assertNull(field(f, "mDefaultHeader"));
    }

    @Test
    public void attachDefaultHeader_headerPresent_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        com.gh4a.adapter.SearchAdapter adapter =
                mock(com.gh4a.adapter.SearchAdapter.class);
        setField(f, "mAdapter", adapter);
        android.view.View header = inflateDefaultHeader(f);
        call(f, "attachDefaultHeader", new Class<?>[]{});
        // 头已存在：守卫返回，还是同一块头。
        assertTrue(header == field(f, "mDefaultHeader"));
    }

    @Test
    public void refreshDefaultHeader_detachedWithHeader_returns() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_USER, null, false));
        setField(f, "mAdapter", mock(com.gh4a.adapter.SearchAdapter.class));
        android.view.View header = inflateDefaultHeader(f);
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        // onDestroyView 已把头清掉，手动恢复，再调刷新：无 activity 直接返回。
        setField(f, "mDefaultHeader", header);
        call(f, "refreshDefaultHeader", new Class<?>[]{});
    }

    @Test
    public void onSuggestionClick_nullCursor_returnsTrue() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView mockSearch = mock(SearchView.class);
        CursorAdapter adapter = mock(CursorAdapter.class);
        when(adapter.getCursor()).thenReturn(null);
        when(mockSearch.getSuggestionsAdapter()).thenReturn(adapter);
        setField(f, "mSearch", mockSearch);
        assertTrue(f.onSuggestionClick(0));
    }

    @Test
    public void onSuggestionClick_emptyCursor_returnsTrue() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        SearchView mockSearch = mock(SearchView.class);
        CursorAdapter adapter = mock(CursorAdapter.class);
        when(adapter.getCursor()).thenReturn(
                new MatrixCursor(new String[]{"_id", "suggestion"}));
        when(mockSearch.getSuggestionsAdapter()).thenReturn(adapter);
        setField(f, "mSearch", mockSearch);
        assertTrue(f.onSuggestionClick(0));
    }

    @Test
    public void onSuggestionClick_clearRowDetached_skipsDelete() throws Exception {
        insertSuggestion("kotlin", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            SearchView mockSearch = mock(SearchView.class);
            CursorAdapter adapter = mock(CursorAdapter.class);
            MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "suggestion"});
            cursor.addRow(new Object[]{Long.MAX_VALUE, "clear"});
            cursor.moveToFirst();
            when(adapter.getCursor()).thenReturn(cursor);
            when(mockSearch.getSuggestionsAdapter()).thenReturn(adapter);
            setField(f, "mSearch", mockSearch);
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            // onDestroyView 把 mSearch 清了，恢复后再点：activity 为 null 不删。
            setField(f, "mSearch", mockSearch);
            assertTrue(f.onSuggestionClick(0));
            assertEquals(1, suggestionCount());
        } finally {
            clearSuggestions();
        }
    }

    private void assertOnSearchFragmentClickNoIntent(
            SearchCode result) throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_CODE, null, false));
        f.onSearchFragmentClick(result, 0);
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void onSearchFragmentClick_nullUrl_returns() throws Exception {
        SearchCode result = mock(SearchCode.class);
        when(result.url()).thenReturn(null);
        assertOnSearchFragmentClickNoIntent(result);
    }

    @Test
    public void onSearchFragmentClick_nullRepo_returns() throws Exception {
        SearchCode result = mock(SearchCode.class);
        when(result.url()).thenReturn(
                "https://github.com/octo/cat/blob/main/src/Main.java?ref=main");
        when(result.repository()).thenReturn(null);
        assertOnSearchFragmentClickNoIntent(result);
    }

    @Test
    public void onSearchFragmentClick_nullOwner_returns() throws Exception {
        SearchCode result = mock(SearchCode.class);
        when(result.url()).thenReturn(
                "https://github.com/octo/cat/blob/main/src/Main.java?ref=main");
        Repository repo = mock(Repository.class);
        when(repo.owner()).thenReturn(null);
        when(result.repository()).thenReturn(repo);
        assertOnSearchFragmentClickNoIntent(result);
    }

    @Test
    public void showSortSheet_rowClick_currentSort_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setMockSearchView(f);
        com.gh4a.fragment.SearchSortOption first =
                com.gh4a.fragment.SearchSortOption.forSearchType(
                        SearchFragment.SEARCH_TYPE_REPO).get(0);
        setField(f, "mSort", first.sort);
        setField(f, "mOrder", first.order);
        call(f, "showSortSheet", new Class<?>[]{});
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog
                .getShownDialogs().get(0);
        android.widget.LinearLayout content = sheetContent(dialog,
                mActivity.getString(com.gh4a.R.string.search_sort_title));
        content.getChildAt(1).performClick(); // 首个排序选项＝当前项
        // 排序与顺序都相等：提前返回，不重载。
        assertEquals(first.sort, field(f, "mSort"));
        assertEquals(first.order, field(f, "mOrder"));
    }

    @Test
    public void loadRecentSearches_emptySuggestion_skipped() throws Exception {
        insertSuggestion("", 0);
        insertSuggestion("kotlin", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            java.util.List<String> recents =
                    (java.util.List<String>) call(f, "loadRecentSearches",
                            new Class<?>[]{});
            assertEquals(java.util.Collections.singletonList("kotlin"), recents);
        } finally {
            clearSuggestions();
        }
    }

    @Test
    public void loadRecentSearches_sevenRows_capsAtSix() throws Exception {
        for (int i = 0; i < 7; i++) {
            insertSuggestion("term" + i, 0);
            Thread.sleep(5);
        }
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            java.util.List<String> recents =
                    (java.util.List<String>) call(f, "loadRecentSearches",
                            new Class<?>[]{});
            assertEquals(6, recents.size());
        } finally {
            clearSuggestions();
        }
    }

    @Test
    public void refreshDefaultHeader_chipClickNoSearch_isNoop() throws Exception {
        insertSuggestion("kotlin", 0);
        try {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            setField(f, "mHotRepos", java.util.Collections.emptyList());
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout recent = header.findViewById(
                    com.gh4a.R.id.search_default_recent_container);
            assertEquals(1, recent.getChildCount());
            // mSearch 为 null：点 chip 啥也不做，不 crash。
            recent.getChildAt(0).performClick();
        } finally {
            clearSuggestions();
        }
    }

    @Test
    public void loadHotRepos_nullItems_skipsLoop() throws Exception {
        clearHotCache();
        SearchService service = mock(SearchService.class);
        SearchPage<Repository> page = mock(SearchPage.class);
        when(page.items()).thenReturn(null);
        when(page.next()).thenReturn(null);
        when(service.searchRepositories(
                ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyLong()))
                .thenReturn(Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            inflateDefaultHeader(f);
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            long deadline = System.currentTimeMillis() + 5000;
            while (field(f, "mHotRepos") == null
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            // items() 为 null：循环跳过，mHotRepos 为空列表。
            assertNotNull(field(f, "mHotRepos"));
            assertTrue(((java.util.List<?>) field(f, "mHotRepos")).isEmpty());
            assertEquals(0, container.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_manyItems_capsAtTen() throws Exception {
        clearHotCache();
        java.util.List<Object> items = new java.util.ArrayList<>();
        // 12 个纯 repo：覆盖 repos.size() < 10 的 false 半（只取前 10）。
        // 注意：instanceof Repository 的 false 半不可达——页先过
        // SearchPageAdapter.items() 的 for (Repository item : items)，
        // 非仓库项在那里就被 checkcast 抛 ClassCastException，根本到不了
        // 生产循环；真实页来自 Moshi 反序列化，本来也全是 Repository。
        for (int i = 0; i < 12; i++) {
            items.add(hotRepo("o/r" + i, i));
        }
        @SuppressWarnings("unchecked")
        SearchPage<Repository> page =
                (SearchPage<Repository>) (SearchPage<?>) searchPageWith(items);
        SearchService service = mockHotService(
                Single.just(Response.success(page)));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            inflateDefaultHeader(f);
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            long deadline = System.currentTimeMillis() + 5000;
            while (field(f, "mHotRepos") == null
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
                idleMain();
            }
            // 12 个仓库只取前 10。
            assertEquals(10, ((java.util.List<?>) field(f, "mHotRepos")).size());
            assertEquals(10, container.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_errorWithHotRepos_keepsSkeleton() throws Exception {
        clearHotCache();
        SearchService service = mockHotService(
                Single.error(new RuntimeException("no net")));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            inflateDefaultHeader(f);
            setField(f, "mHotRepos", java.util.Collections.emptyList());
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "showHotSkeleton",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            assertEquals(5, container.getChildCount());
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            waitForHotDisposable(f);
            // mHotRepos 已有值：失败守卫不成立，骨架保留。
            assertEquals(5, container.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_errorWithCache_keepsCachedRow() throws Exception {
        HotReposCache.save(mActivity, java.util.Arrays.asList(
                new HotReposCache.HotRepo("a/b", 10)));
        SearchService service = mockHotService(
                Single.error(new RuntimeException("no net")));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.view.View header = inflateDefaultHeader(f);
            call(f, "refreshDefaultHeader", new Class<?>[]{});
            android.widget.LinearLayout hot = header.findViewById(
                    com.gh4a.R.id.search_default_hot_container);
            assertEquals(1, hot.getChildCount()); // 先出缓存行
            waitForHotDisposable(f);
            // 磁盘有缓存：失败守卫不成立，缓存行保留。
            assertEquals(1, hot.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_errorNoHeader_keepsContainer() throws Exception {
        clearHotCache();
        SearchService service = mockHotService(
                Single.error(new RuntimeException("no net")));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            waitForHotDisposable(f);
            // 无默认头：失败守卫不成立，容器不动。
            assertEquals(0, container.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void loadHotRepos_errorDetached_skipsGuard() throws Exception {
        clearHotCache();
        SearchService service = mockHotService(
                Single.error(new RuntimeException("no net")));
        try (MockedStatic<ServiceFactory> mocked = mockSearchService(service)) {
            SearchFragment f = attach(SearchFragment.newInstance(
                    SearchFragment.SEARCH_TYPE_REPO, null, false));
            inflateDefaultHeader(f);
            android.widget.LinearLayout container =
                    new android.widget.LinearLayout(mActivity);
            mActivity.getSupportFragmentManager().beginTransaction()
                    .remove(f).commitNow();
            call(f, "loadHotRepos",
                    new Class<?>[]{android.widget.LinearLayout.class}, container);
            waitForHotDisposable(f);
            // 已 detach：DiagTrace.log(null) 直接返回，失败守卫不成立。
            assertEquals(0, container.getChildCount());
        } finally {
            clearHotCache();
        }
    }

    @Test
    public void fillHotContainer_nullReposAndContainer_return() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container =
                new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        assertEquals(0, container.getChildCount()); // mHotRepos 为 null
        setField(f, "mHotRepos",
                java.util.Collections.singletonList(hotRepo("o/r", 42)));
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, (Object) null);
        assertEquals(0, container.getChildCount()); // 容器为 null
    }

    @Test
    public void fillHotContainerFromCache_noSlashRowClick_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container =
                new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainerFromCache",
                new Class<?>[]{android.widget.LinearLayout.class, java.util.List.class},
                container, java.util.Collections.singletonList(
                        new HotReposCache.HotRepo("lonely", 5)));
        assertEquals(1, container.getChildCount());
        container.getChildAt(0).performClick();
        // 全名里没有 '/'：不解析、不启动仓库页。
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void fillHotContainerFromCache_rowClickDetached_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        android.widget.LinearLayout container =
                new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainerFromCache",
                new Class<?>[]{android.widget.LinearLayout.class, java.util.List.class},
                container, java.util.Collections.singletonList(
                        new HotReposCache.HotRepo("o/r", 42)));
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        container.getChildAt(0).performClick();
        // 已 detach：getActivity()==null，不启动仓库页。
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void fillHotContainer_rowClickDetached_isNoop() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        setField(f, "mHotRepos",
                java.util.Collections.singletonList(hotRepo("o/r", 42)));
        android.widget.LinearLayout container =
                new android.widget.LinearLayout(mActivity);
        call(f, "fillHotContainer",
                new Class<?>[]{android.widget.LinearLayout.class}, container);
        mActivity.getSupportFragmentManager().beginTransaction()
                .remove(f).commitNow();
        container.getChildAt(0).performClick();
        // 已 detach：getActivity()==null，不启动仓库页。
        assertNull(Shadows.shadowOf(mActivity).getNextStartedActivity());
    }

    @Test
    public void suggestionAdapter_swapCursor_null_clears() throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = newSuggestionAdapter(f);
        assertNull(adapter.swapCursor(null));
        assertEquals(0, adapter.getCount());
    }

    @Test
    public void suggestionAdapter_getItemViewType_outOfRange_returnsZero()
            throws Exception {
        SearchFragment f = attach(SearchFragment.newInstance(
                SearchFragment.SEARCH_TYPE_REPO, null, false));
        CursorAdapter adapter = adapterWithMergedCursor(f);
        assertEquals(0, adapter.getItemViewType(5));
    }
}
