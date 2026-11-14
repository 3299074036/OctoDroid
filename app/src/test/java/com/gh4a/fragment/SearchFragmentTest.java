package com.gh4a.fragment;

import android.database.Cursor;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;

import com.gh4a.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
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
        Field field = SearchFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(f);
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
}
