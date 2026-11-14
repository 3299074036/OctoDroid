package com.gh4a.fragment;

import com.gh4a.R;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** SearchSortOption：排序选项表（纯 JVM，无 Android 依赖）。 */
public class SearchSortOptionTest {

    @Test
    public void typeConstants_coverAllSearchTypes() {
        assertEquals(1, SearchSortOption.TYPE_REPO);
        assertEquals(2, SearchSortOption.TYPE_USER);
        assertEquals(4, SearchSortOption.TYPE_CODE);
        assertEquals(7, SearchSortOption.TYPE_ALL);
        assertEquals(SearchSortOption.TYPE_REPO | SearchSortOption.TYPE_USER
                | SearchSortOption.TYPE_CODE, SearchSortOption.TYPE_ALL);
    }

    @Test
    public void values_countAndOrder() {
        SearchSortOption[] values = SearchSortOption.values();
        assertEquals(15, values.length);
        assertEquals(SearchSortOption.BEST_MATCH, values[0]);
        assertEquals(SearchSortOption.REPO_STARS_DESC, values[1]);
        assertEquals(SearchSortOption.CODE_INDEXED_ASC, values[14]);
    }

    @Test
    public void bestMatch_hasNullSortAndOrderAndAppliesToAll() {
        SearchSortOption o = SearchSortOption.BEST_MATCH;
        assertEquals(R.id.search_sort_best_match, o.menuId);
        assertNull(o.sort);
        assertNull(o.order);
        assertEquals(R.string.search_sort_best_match, o.titleRes);
        assertTrue(o.appliesTo(SearchFragment.SEARCH_TYPE_REPO));
        assertTrue(o.appliesTo(SearchFragment.SEARCH_TYPE_USER));
        assertTrue(o.appliesTo(SearchFragment.SEARCH_TYPE_CODE));
    }

    @Test
    public void repoOptions_applyOnlyToRepo() {
        SearchSortOption o = SearchSortOption.REPO_FORKS_ASC;
        assertEquals("forks", o.sort);
        assertEquals("asc", o.order);
        assertEquals(R.string.search_sort_repo_forks_asc, o.titleRes);
        assertTrue(o.appliesTo(SearchFragment.SEARCH_TYPE_REPO));
        assertFalse(o.appliesTo(SearchFragment.SEARCH_TYPE_USER));
        assertFalse(o.appliesTo(SearchFragment.SEARCH_TYPE_CODE));
    }

    @Test
    public void userOptions_applyOnlyToUser() {
        SearchSortOption o = SearchSortOption.USER_JOINED_DESC;
        assertEquals("joined", o.sort);
        assertEquals("desc", o.order);
        assertTrue(o.appliesTo(SearchFragment.SEARCH_TYPE_USER));
        assertFalse(o.appliesTo(SearchFragment.SEARCH_TYPE_REPO));
        assertFalse(o.appliesTo(SearchFragment.SEARCH_TYPE_CODE));
    }

    @Test
    public void codeOptions_applyOnlyToCode() {
        SearchSortOption o = SearchSortOption.CODE_INDEXED_DESC;
        assertEquals("indexed", o.sort);
        assertEquals("desc", o.order);
        assertTrue(o.appliesTo(SearchFragment.SEARCH_TYPE_CODE));
        assertFalse(o.appliesTo(SearchFragment.SEARCH_TYPE_REPO));
        assertFalse(o.appliesTo(SearchFragment.SEARCH_TYPE_USER));
    }

    @Test
    public void appliesTo_unknownTypeDefaultsToRepoFlag() {
        // switch default 分支走 TYPE_REPO：仓库选项适用，用户/代码选项不适用。
        assertTrue(SearchSortOption.REPO_STARS_DESC.appliesTo(99));
        assertTrue(SearchSortOption.BEST_MATCH.appliesTo(99));
        assertFalse(SearchSortOption.USER_FOLLOWERS_DESC.appliesTo(99));
        assertFalse(SearchSortOption.CODE_INDEXED_DESC.appliesTo(99));
        assertTrue(SearchSortOption.REPO_UPDATED_ASC.appliesTo(-1));
    }

    @Test
    public void forSearchType_repoReturnsBestMatchPlusSixRepoOptions() {
        List<SearchSortOption> opts =
                SearchSortOption.forSearchType(SearchFragment.SEARCH_TYPE_REPO);
        assertEquals(7, opts.size());
        assertEquals(SearchSortOption.BEST_MATCH, opts.get(0));
        assertEquals(SearchSortOption.REPO_STARS_DESC, opts.get(1));
        assertEquals(SearchSortOption.REPO_UPDATED_ASC, opts.get(6));
    }

    @Test
    public void forSearchType_userReturnsBestMatchPlusSixUserOptions() {
        List<SearchSortOption> opts =
                SearchSortOption.forSearchType(SearchFragment.SEARCH_TYPE_USER);
        assertEquals(7, opts.size());
        assertEquals(SearchSortOption.BEST_MATCH, opts.get(0));
        assertEquals(SearchSortOption.USER_FOLLOWERS_DESC, opts.get(1));
        assertEquals(SearchSortOption.USER_JOINED_ASC, opts.get(6));
    }

    @Test
    public void forSearchType_codeReturnsBestMatchPlusTwoCodeOptions() {
        List<SearchSortOption> opts =
                SearchSortOption.forSearchType(SearchFragment.SEARCH_TYPE_CODE);
        assertEquals(3, opts.size());
        assertEquals(SearchSortOption.BEST_MATCH, opts.get(0));
        assertEquals(SearchSortOption.CODE_INDEXED_DESC, opts.get(1));
        assertEquals(SearchSortOption.CODE_INDEXED_ASC, opts.get(2));
    }

    @Test
    public void forSearchType_unknownTypeDefaultsToRepoList() {
        List<SearchSortOption> opts = SearchSortOption.forSearchType(99);
        assertEquals(7, opts.size());
        assertEquals(SearchSortOption.REPO_STARS_DESC, opts.get(1));
    }

    @Test
    public void fromSortOrder_matchesAllPairs() {
        assertEquals(SearchSortOption.REPO_STARS_DESC,
                SearchSortOption.fromSortOrder("stars", "desc"));
        assertEquals(SearchSortOption.REPO_STARS_ASC,
                SearchSortOption.fromSortOrder("stars", "asc"));
        assertEquals(SearchSortOption.USER_REPOSITORIES_DESC,
                SearchSortOption.fromSortOrder("repositories", "desc"));
        assertEquals(SearchSortOption.CODE_INDEXED_ASC,
                SearchSortOption.fromSortOrder("indexed", "asc"));
        assertEquals(SearchSortOption.BEST_MATCH,
                SearchSortOption.fromSortOrder(null, null));
    }

    @Test
    public void fromSortOrder_noMatchReturnsNull() {
        assertNull(SearchSortOption.fromSortOrder("stars", "sideways"));
        assertNull(SearchSortOption.fromSortOrder("bogus", "desc"));
        assertNull(SearchSortOption.fromSortOrder(null, "desc"));
        assertNull(SearchSortOption.fromSortOrder("stars", null));
        assertNull(SearchSortOption.fromSortOrder("", ""));
    }

    @Test
    public void everyOption_hasDistinctMenuIdAndTitleRes() {
        java.util.Set<Integer> menuIds = new java.util.HashSet<>();
        java.util.Set<Integer> titleRes = new java.util.HashSet<>();
        for (SearchSortOption o : SearchSortOption.values()) {
            assertTrue("duplicate menuId " + o, menuIds.add(o.menuId));
            assertTrue("duplicate titleRes " + o, titleRes.add(o.titleRes));
            assertTrue("menuId must be nonzero " + o, o.menuId != 0);
            assertTrue("titleRes must be nonzero " + o, o.titleRes != 0);
        }
    }
}
